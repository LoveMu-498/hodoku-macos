/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Exercises actual native proof/ink rendering, not a codec-created bitmap stub. */
public final class ReplayFrameCaptureProbe {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static BufferedImage paint(SudokuPanel panel,int size){
        panel.setSize(size,size);BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();try{panel.paint(graphics);}finally{graphics.dispose();}return image;
    }
    private static int different(BufferedImage first,BufferedImage second){
        int n=0;for(int y=0;y<first.getHeight();y++)for(int x=0;x<first.getWidth();x++)if(first.getRGB(x,y)!=second.getRGB(x,y))n++;return n;
    }
    private static void pngProfile(byte[] bytes)throws Exception{
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){
            check(in.readLong()==0x89504e470d0a1a0aL,"PNG signature");int count=0;boolean idat=false;
            while(in.available()>0){int length=in.readInt();byte[] type=new byte[4];in.readFully(type);byte[] data=new byte[length];in.readFully(data);long crc=in.readInt()&0xffffffffL;
                CRC32 actual=new CRC32();actual.update(type);actual.update(data);check(actual.getValue()==crc,"PNG CRC");String name=new String(type,"US-ASCII");
                check(name.equals("IHDR")||name.equals("IDAT")||name.equals("IEND"),"PNG has non-profile metadata");
                if(count++==0){check(name.equals("IHDR")&&data.length==13,"PNG IHDR");check(data[8]==8&&data[9]==6&&data[12]==0,"RGBA8 noninterlaced profile");}
                if(name.equals("IDAT"))idat=true;if(name.equals("IEND"))check(idat&&in.available()==0,"PNG complete ending");
            }
        }
    }
    private static ReplaySession doodleSession()throws Exception{
        Sudoku2 before=DoodleReplayStepProbe.blank(),after=before.clone();after.delCandidate(80,5);
        List<DoodleStroke> marks=new ArrayList<DoodleStroke>();marks.add(DoodleReplayStepProbe.mark(805,1,true,false,false));
        for(int cell=0;cell<9;cell++)marks.add(DoodleReplayStepProbe.mark(cell*10+3,2,false,false,false));
        marks.add(DoodleReplayStepProbe.mark(805,2,false,true,true));
        DoodleStroke other=DoodleReplayStepProbe.mark(447,1,false,true,false);other.setThoughtGroup(2);other.setColor(new Color(30,170,120));marks.add(other);
        ReplayAnnotations input=DoodleReplayStepProbe.annotations(marks);DoodleReplayStep frozen=DoodleReplayStep.capture(before,after,input,false);
        ReplayAnnotations post=new ReplayAnnotations("",new byte[0],Collections.emptyMap(),Collections.emptyMap(),Collections.singletonList(other),-1);
        ReplaySession session=new ReplaySession(new ReplayBoard(before),1000);session.elapsedMillis=200;
        session.append(Arrays.asList(new ReplayFrame(1,1100,100,"doodle-conclusion-input",frozen.sharedLabel(),new ReplayBoard(before),null,input.withDoodleStep(frozen,"")),
                new ReplayFrame(1,1200,200,"doodle-conclusion-result",frozen.atApplication().sharedLabel(),new ReplayBoard(after),null,post.withDoodleStep(frozen.atApplication(),""))));
        return session;
    }
    private static ReplaySession proofSession()throws Exception{
        Sudoku2 before=DoodleReplayStepProbe.blank(),after=before.clone();after.delCandidate(2,1);after.delCandidate(2,2);
        SolutionStep proof=new SolutionStep(SolutionType.NAKED_PAIR);proof.addValue(1);proof.addValue(2);proof.addIndex(0);proof.addIndex(1);proof.addCandidateToDelete(2,1);proof.addCandidateToDelete(2,2);
        ReplaySession session=new ReplaySession(new ReplayBoard(before),1000);session.elapsedMillis=200;
        session.append(Arrays.asList(new ReplayFrame(1,1100,100,"proof","本地双值证明",new ReplayBoard(before),ReplayProof.encode(proof)),
                new ReplayFrame(1,1200,200,"apply","应用到真实盘面",new ReplayBoard(after),null)));
        return session;
    }
    public static void main(String[] args)throws Exception{
        Path output=Files.createDirectories(Paths.get(args[0]));System.setProperty("hodoku.data.dir",output.resolve("data").toString());Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        SwingUtilities.invokeAndWait(()->ApplicationAppearance.initialize(AppearanceMode.LIGHT));
        ReplaySession doodle=doodleSession(),proof=proofSession();
        ReplayStore.write(output.resolve("native-frozen-doodle.hrep"),doodle);ReplayStore.write(output.resolve("native-frozen-proof.hrep"),proof);
        doodle=ReplayStore.read(output.resolve("native-frozen-doodle.hrep"));proof=ReplayStore.read(output.resolve("native-frozen-proof.hrep"));
        final ReplaySession inkSession=doodle,proofFrames=proof;Throwable[] failure={null};
        SwingUtilities.invokeAndWait(()->{try{
            for(int size:new int[]{420,810}){
                ReplayFrameCapture.Capture ink=ReplayFrameCapture.capture(inkSession,2,size),nativeProof=ReplayFrameCapture.capture(proofFrames,2,size);
                pngProfile(ink.previewPng);check(ink.description.contains("删除候选 R9C9(5)"),"frozen native explanation omitted actual application");
                check(ink.display.size()==1&&Boolean.TRUE.equals(ink.display.get(0).get("important"))&&Boolean.FALSE.equals(ink.display.get(0).get("required")),"fallback trigger missing");
                BufferedImage actual=ImageIO.read(new ByteArrayInputStream(ink.previewPng));ImageIO.write(actual,"png",output.resolve("native-result-"+size+".png").toFile());
                ReplayFrame input=inkSession.frames().get(1),result=inkSession.frames().get(2);List<DoodleStroke> expectedInk=result.annotations.ink();
                for(DoodleStroke mark:input.annotations.ink())if(mark.getThoughtGroup()==0)expectedInk.add(mark);
                SudokuPanel expected=new SudokuPanel(null,true);expected.displayReplayFrame(result.withAnnotations(result.annotations.withViewingInk(expectedInk,0)));
                check(different(actual,paint(expected,size))==0,"capture omitted input proof or another color's post-state ink");
                check(!expected.getSudoku().isCandidate(80,5),"historical candidate proof restored an editable candidate");
                SudokuPanel boardOnly=new SudokuPanel(null,true);boardOnly.displayReplayBoard(result.board,null);check(different(actual,paint(boardOnly,size))>50,"native ink not rendered");
                ReplayFrame applied=proofFrames.last();expected.displayReplayFrame(applied,ReplayEvidence.proof(proofFrames.frames().get(1).evidence()),proofFrames.frames().get(1).board);
                BufferedImage proofImage=ImageIO.read(new ByteArrayInputStream(nativeProof.previewPng));check(different(proofImage,paint(expected,size))==0,"apply frame lost prior native proof");
                check(!expected.getSudoku().isCandidate(2,1)&&!expected.getSudoku().isCandidate(2,2),"native proof changed applied board");
                ImageIO.write(proofImage,"png",output.resolve("native-proof-"+size+".png").toFile());
            }
            StringBuilder longText=new StringBuilder();for(int i=0;i<4000;i++)longText.append("变化见下一帧。😀");
            ReplaySession longSession=new ReplaySession("capture-long",1000);longSession.loadFrame(new ReplayFrame(0,1000,0,"manual",longText.toString(),inkSession.frames().get(0).board,null));
            check(ReplayFrameCapture.capture(longSession,0,420).description.equals(longText.toString()),"long caption truncated by bitmap limits");
            ReplaySession partial=new ReplaySession("capture-partial",1000);partial.loadFrame(new ReplayFrame(0,1000,0,"foreign-method","旧历史",inkSession.frames().get(0).board,null,ReplayAnnotations.fromWindows("MT 1\n")));
            try{ReplayFrameCapture.capture(partial,0,420);throw new AssertionError("missing MT evidence advertised as full preview");}catch(IOException expected){check(expected.getMessage().contains("incomplete historical display"),"wrong partial-view error");}
            // A frozen historical role may not satisfy today's live composition rules.
            // Painting must retain its exact recorded roles without analyzing it again.
            DoodleStroke savedOutline=DoodleReplayStepProbe.mark(335,1,false,true,false);savedOutline.setConclusionSourceMask(3);
            ReplayAnnotations historical=DoodleReplayStepProbe.annotations(Arrays.asList(DoodleReplayStepProbe.mark(805,1,true,false,false),DoodleReplayStepProbe.mark(805,2,false,false,false),savedOutline));
            SudokuPanel readonly=new SudokuPanel(null,true);readonly.displayReplayFrame(new ReplayFrame(0,1000,0,"manual","历史显示",inkSession.frames().get(0).board,null,historical));paint(readonly,420);
            java.lang.reflect.Field cache=SudokuPanel.class.getDeclaredField("doodleContradictionCache");cache.setAccessible(true);
            check(cache.get(readonly)==null,"read-only replay recomputed mathematical evidence while painting");
            java.lang.reflect.Field strokes=SudokuPanel.class.getDeclaredField("doodleStrokes");strokes.setAccessible(true);
            @SuppressWarnings("unchecked") List<DoodleStroke> frozenInk=(List<DoodleStroke>)strokes.get(readonly);
            check(frozenInk.get(2).isConclusionOutlined()&&frozenInk.get(2).getConclusionSourceMask()==3,"read-only replay rewrote historical conclusion roles");
        }catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError(failure[0]);
        // The non-EDT entry marshals painting to the EDT and never changes source frames.
        check(ReplayFrameCapture.capture(proof,2,420).previewPng.length>100,"background export did not capture native paint");
        check(doodle.frameCount()==3&&doodle.last().annotations.ink().size()==1,"capture changed stored native annotation source");
        if(args.length>1&&args[1].equals("--gui")){
            final MainFrame[] window={null};final ReplayBoard[] live={null};
            SwingUtilities.invokeAndWait(()->{try{
                window[0]=new MainFrame(null);window[0].setSize(1000,850);window[0].setVisible(true);live[0]=new ReplayBoard(window[0].getSudokuPanel().getSudoku());
                window[0].getReplayController().openViewer(inkSession);window[0].getReplayController().viewer().showFrame(2);
            }catch(Throwable error){failure[0]=error;}});
            java.awt.Robot robot=new java.awt.Robot();robot.waitForIdle();
            SwingUtilities.invokeAndWait(()->{try{
                MainFrame owner=window[0];ReplayViewer viewer=owner.getReplayController().viewer();
                BufferedImage real=paint(viewer.boardPanel(),810),frozen=ImageIO.read(new ByteArrayInputStream(ReplayFrameCapture.capture(inkSession,2,810).previewPng));
                check(different(real,frozen)==0,"actual native ReplayViewer and capture differ");
                check(viewer.hintPanel().getText().equals(ReplayFrameCapture.capture(inkSession,2,810).description),"actual native viewer explanation differs");
                check(new ReplayBoard(owner.getSudokuPanel().getSudoku()).equals(live[0]),"capture/viewer mutated live puzzle");
                owner.getGlassPane().doLayout();
            }catch(Throwable error){failure[0]=error;}});
            robot.waitForIdle();
            try{
                if(window[0]!=null){java.awt.Point location=window[0].getLocationOnScreen();ImageIO.write(robot.createScreenCapture(new java.awt.Rectangle(location.x,location.y,window[0].getWidth(),window[0].getHeight())),"png",output.resolve("native-viewer-window.png").toFile());}
            }finally{SwingUtilities.invokeAndWait(()->{if(window[0]!=null)window[0].dispose();});}
            if(failure[0]!=null)throw new AssertionError(failure[0]);
        }
        System.out.println("PASS "+checks+" actual native frame-capture checks: proof/apply context, multicolor result/input ink, real board isolation, RGBA PNG, long caption and incomplete-evidence rejection");System.exit(0);
    }
}
