/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Frozen viewing projection using the real, isolated native replay renderer. */
final class ReplayFrameCapture {
    static final int DEFAULT_BOARD_SIZE=1024;
    private static final int MAX_IMAGE_SIZE=4096, MAX_PNG_BYTES=8*1024*1024;

    static final class View {
        final String title,description,notice;
        View(String title,String description,String notice){this.title=title;this.description=description;this.notice=notice;}
    }

    static final class Capture {
        final List<Map<String,Object>> display;
        final byte[] previewPng;
        final String description;
        final int width,height,boardHeight;
        Capture(byte[] png,View view,int width,int height,int boardHeight)throws IOException {
            this.previewPng=png;this.description=view.description;this.width=width;this.height=height;this.boardHeight=boardHeight;
            display=displayMarker(png);
        }
    }

    private ReplayFrameCapture(){}

    static List<Map<String,Object>> displayMarker(){
        Map<String,Object> marker=new LinkedHashMap<String,Object>();
        marker.put("id","mac-native-view");marker.put("type","hodoku-native-view");
        marker.put("required",Boolean.FALSE);marker.put("important",Boolean.TRUE);marker.put("z",0L);
        return Collections.singletonList(Collections.unmodifiableMap(marker));
    }
    static List<Map<String,Object>> displayMarker(byte[] png)throws IOException {
        Map<String,Object> marker=new LinkedHashMap<String,Object>(displayMarker().get(0));
        // No vector geometry is invented. The optional marker requests the complete
        // fallback and binds the specific captured pixels into the frame digest.
        marker.put("imageSha256",sha256(png));
        return Collections.singletonList(Collections.unmodifiableMap(marker));
    }
    static String describe(ReplaySession session,int index)throws IOException {
        List<ReplayFrame> frames=session.frames();
        if(index<0||index>=frames.size())throw new IOException("Replay frame index outside session");
        ReplayFrame frame=frames.get(index);SolutionStep proof=ReplayEvidence.proof(frame.evidence());
        if(proof==null&&"apply".equals(frame.kind))
            for(int prior=index-1;prior>=0&&frames.get(prior).operationId==frame.operationId;prior--){proof=ReplayEvidence.proof(frames.get(prior).evidence());if(proof!=null)break;}
        return describe(frame,proof);
    }
    private static String describe(ReplayFrame frame,SolutionStep proof){
        String annotationText=frame.annotations.description().trim();
        String commonText=annotationText.isEmpty()||annotationText.equals(frame.label.trim())?frame.label:frame.label+"\n"+annotationText;
        return frame.annotations.doodleStep!=null?frame.annotations.doodleStep.description():proof==null?commonText:proof.toString(2);
    }

    /** Shared with ReplayViewer: materializes stored evidence without running a solver. */
    static View applyFrame(SudokuPanel board,ReplaySession session,int index)throws IOException {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Replay display must run on the EDT");
        List<ReplayFrame> frames=session.frames();
        if(index<0||index>=frames.size())throw new IOException("Replay frame index outside session");
        ReplayFrame frame=frames.get(index);
        ReplayFrame rendered=index==0&&session.initialAnnotations().length>0
                ?new ReplayFrame(frame.operationId,frame.wallTimeMillis,frame.elapsedMillis,"authored-input",frame.label,frame.board,session.initialAnnotations(),frame.annotations):frame;
        board.displayReplayFrame(rendered);
        SolutionStep proof=ReplayEvidence.proof(frame.evidence());
        if(proof==null&&"apply".equals(frame.kind))
            for(int prior=index-1;prior>=0&&frames.get(prior).operationId==frame.operationId;prior--){
                proof=ReplayEvidence.proof(frames.get(prior).evidence());if(proof!=null)break;
            }
        if((proof!=null&&("apply".equals(frame.kind)||"authored-apply".equals(frame.kind)))
                ||"foreign-action".equals(frame.kind)&&!frame.annotations.proofCandidateAtoms().isEmpty()){
            ReplayBoard proofBoard=frame.board;
            for(int prior=index-1;prior>=0&&frames.get(prior).operationId==frame.operationId;prior--){
                ReplayFrame source=frames.get(prior);
                if("proof".equals(source.kind)||"authored-result".equals(source.kind)||"foreign-method".equals(source.kind)){
                    proofBoard=source.board;break;
                }
            }
            board.displayReplayFrame(frame,proof,proofBoard);
        }
        DoodleReplayStep doodleStep=frame.annotations.doodleStep;
        if("doodle-conclusion-result".equals(frame.kind)||"doodle-or-result".equals(frame.kind)){
            for(int prior=index-1;prior>=0&&frames.get(prior).operationId==frame.operationId;prior--){
                ReplayFrame input=frames.get(prior);
                if(!"doodle-conclusion-input".equals(input.kind)&&!"doodle-or-input".equals(input.kind))continue;
                int group=input.annotations.outlineVisibleGroup;if(group<0)break;
                List<DoodleStroke> displayInk=frame.annotations.ink();
                displayInk.removeIf(mark->mark.getThoughtGroup()==group);
                for(DoodleStroke mark:input.annotations.ink())if(mark.getThoughtGroup()==group)displayInk.add(mark);
                board.displayReplayFrame(frame.withAnnotations(frame.annotations.withViewingInk(displayInk,group)));break;
            }
        }
        return new View(doodleStep==null?frame.label:doodleStep.compactTitle(),
                describe(frame,proof),frame.annotations.notice());
    }

    static Capture capture(ReplaySession session,int index)throws IOException {
        return capture(session,index,DEFAULT_BOARD_SIZE);
    }

    /** The size overload supports actual two-size renderer validation. */
    static Capture capture(ReplaySession session,int index,int boardSize)throws IOException {
        if(boardSize<160||boardSize>MAX_IMAGE_SIZE)throw new IOException("Invalid replay capture size");
        if(SwingUtilities.isEventDispatchThread())return captureOnEdt(session,index,boardSize);
        final Capture[] result={null};final Throwable[] failure={null};
        try{
            SwingUtilities.invokeAndWait(()->{try{result[0]=captureOnEdt(session,index,boardSize);}catch(Throwable error){failure[0]=error;}});
        }catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("Replay capture interrupted",error);}
        catch(java.lang.reflect.InvocationTargetException error){throw new IOException("Replay capture failed",error.getCause());}
        if(failure[0] instanceof IOException)throw (IOException)failure[0];
        if(failure[0]!=null)throw new IOException("Replay capture failed",failure[0]);
        return result[0];
    }

    private static Capture captureOnEdt(ReplaySession session,int index,int size)throws IOException {
        SudokuPanel panel=new SudokuPanel(null,true);panel.setSize(size,size);
        View view=applyFrame(panel,session,index);
        if(view.notice.contains(ReplayText.text("foreignInkHidden"))||view.notice.contains(ReplayText.text("foreignMultiMissing")))
            throw new IOException("Frame "+(index+1)+" has incomplete historical display: "+view.notice);
        BufferedImage board=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D nativeGraphics=board.createGraphics();
        try{panel.paint(nativeGraphics);}finally{nativeGraphics.dispose();}
        if(!new ReplayBoard(panel.getSudoku()).equals(session.frames().get(index).board))
            throw new IOException("Replay rendering changed the editable board snapshot");
        // Text remains complete and scrollable in the common phaseLabel. The PNG
        // freezes the full native board/canvas, including its important geometry.
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        if(!ImageIO.write(board,"png",bytes))throw new IOException("PNG encoder unavailable");
        if(bytes.size()>MAX_PNG_BYTES)throw new IOException("Frozen replay PNG exceeds 8 MiB; native replay remains intact");
        return new Capture(bytes.toByteArray(),view,size,size,size);
    }
    private static String sha256(byte[] bytes)throws IOException {
        try{StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hex.toString();}
        catch(NoSuchAlgorithmException error){throw new IOException(error);}
    }
}
