/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleContradictionWorkflowProbe.*;

/** Native Enter, replay pixels/text and post-state branching with isolated user data. */
public final class DoodleReplayGuiProbe {
    static int checks;
    static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Point point(SudokuPanel panel,int cell,int digit)throws Exception{
        Field size=SudokuPanel.class.getDeclaredField("cellSize");size.setAccessible(true);
        Method coordinates=SudokuPanel.class.getDeclaredMethod("getCandKoord",int.class,int.class,int.class);coordinates.setAccessible(true);
        java.awt.geom.Point2D pt=(java.awt.geom.Point2D)coordinates.invoke(panel,cell,digit,size.getInt(panel));return new Point((int)Math.round(pt.getX()),(int)Math.round(pt.getY()));
    }
    static BufferedImage render(SudokuPanel panel,String name)throws Exception{panel.setSize(810,810);BufferedImage im=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();panel.paint(g);g.dispose();if(name!=null)ImageIO.write(im,"png",output.resolve(name+".png").toFile());return im;}
    static ReplaySession operation()throws Exception{
        initialize(1);ReplayController replay=f.getReplayController();replay.beginSession(new ReplayBoard(p.getSudoku()));
        mark(31,7,1,1);emptyCell(1);paint(null);enter();
        return savedReplay();
    }
    static void legacyFile(Path file,ReplaySession source)throws Exception{
        // Fixture writer for the previous optional extension only; no retired Mac envelope.
        try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(file))){
            out.writeInt(0x4852504c);out.writeInt(3);out.writeUTF(source.id);out.writeLong(source.startedAt);out.writeLong(source.elapsedMillis);out.writeLong(source.endedAt);out.writeBoolean(source.completed);out.writeInt(source.frames().size());
            for(ReplayFrame frame:source.frames()){
                out.writeLong(frame.operationId);out.writeLong(frame.wallTimeMillis);out.writeLong(frame.elapsedMillis);out.writeUTF(frame.label);out.writeUTF(frame.annotations.overlay);out.writeBoolean(false);
                int[] v=frame.board.values();boolean[] fixed=frame.board.fixed();short[] c=frame.board.candidates(),u=frame.board.userCandidates();for(int i=0;i<81;i++){out.writeByte(v[i]);out.writeBoolean(fixed[i]);out.writeShort(c[i]);out.writeShort(u[i]);}
            }
            out.writeInt(0x484d5831);out.writeInt(4);out.writeBoolean(false);out.writeBoolean(source.retained);out.writeBoolean(source.pinned);out.writeUTF(source.interruption);out.writeInt(source.frames().size());
            for(ReplayFrame frame:source.frames()){out.writeUTF(frame.kind);ReplayAnnotations.writeBytes(out,frame.evidence());frame.annotations.write(out,4);}
            out.writeInt(0);out.writeUTF("");out.writeInt(-1);ReplayAnnotations.writeBytes(out,new byte[0]);
        }
    }
    public static void main(String[] args)throws Exception{
        java.util.Locale.setDefault(java.util.Locale.SIMPLIFIED_CHINESE);
        System.setProperty("apple.awt.UIElement","true");output=Files.createDirectories(Paths.get(args[0]));
        try{edt(()->{
            ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();ReplayController controller=f.getReplayController();
            ReplaySession record=operation();int input=record.frames().size()-2,result=record.frames().size()-1;
            require(record.frames().get(input).annotations.doodleStep!=null,"native Enter omitted typed reasoning");
            require(f.getHintTextArea().getText().contains("已应用到盘面：删除候选 R4C5(7)"),"live applied hint omitted final action");
            require(record.last().annotations.doodleStep.operation==DoodleReplayStep.Operation.ASSUMPTION_REFUTATION,"wrong native operation type");
            require(record.last().annotations.ink().stream().noneMatch(s->s.getThoughtGroup()==1),"live group not cleared");
            controller.openViewer(record);ReplayViewer viewer=controller.viewer();viewer.showFrame(input);
            BufferedImage before=render(viewer.boardPanel(),"evidence");Point target=point(viewer.boardPanel(),31,7);
            require(ChainOriginProbe.bluePixels(before,target)>3,"before glyph selection missing");
            require(viewer.hintPanel().getText().contains("R4C6≠1")&&viewer.hintPanel().getText().contains("R4C6≠7")&&viewer.hintPanel().getText().contains("R4C6≠9"),"native hint omitted exact all-false candidates");
            viewer.showFrame(result);BufferedImage applied=render(viewer.boardPanel(),"applied");target=point(viewer.boardPanel(),31,7);
            require(!viewer.boardPanel().getSudoku().isCandidate(31,7),"result board was rolled back to evidence");
            require(ChainOriginProbe.bluePixels(applied,target)>3,"applied blue target vanished with deleted candidate");
            require(viewer.hintPanel().getText().contains("已应用到盘面：删除候选 R4C5(7)"),"last applied item not explained");
            Field ink=SudokuPanel.class.getDeclaredField("doodleStrokes");ink.setAccessible(true);require(((java.util.List<?>)ink.get(viewer.boardPanel())).size()>=4,"result view lost prior circle/cross evidence");
            require(p.getSudoku().isCandidate(31,7)==false&&((java.util.List<?>)ink.get(p)).isEmpty(),"view changed live ink or candidates");
            viewer.boardPanel().setShowCandidates(false);BufferedImage hidden=render(viewer.boardPanel(),"applied-without-candidate-display");require(ChainOriginProbe.bluePixels(hidden,point(viewer.boardPanel(),31,7))>3,"replay glyph wrongly tied to candidate visibility");controller.closeViewer();
            Path legacy=output.resolve("legacy-extension4.hrep");legacyFile(legacy,record);ReplaySession old=ReplayFiles.importFile(legacy);require(old.last().annotations.doodleStep==null,"invented historical technique metadata");controller.openViewer(old);controller.viewer().showFrame(result);BufferedImage oldImage=render(controller.viewer().boardPanel(),"legacy-result");require(ChainOriginProbe.bluePixels(oldImage,point(controller.viewer().boardPanel(),31,7))>3,"previous-format result remains missing glyphs");controller.closeViewer();
            Path exported=output.resolve("native-refutation.hrep");ReplayFiles.exportFile(exported,record,controller.directory());
            controller.branchFromFrame(ReplayFiles.importFile(exported),result);require(!p.getSudoku().isCandidate(31,7)&&((java.util.List<?>)ink.get(p)).isEmpty(),"readonly proof became editable result state");
            ReplaySession branched=controller.session();require(branched.frames().get(0).annotations.doodleStep==null,"new attempt inherited the past applied operation");
            ReplayAnnotations branchAnnotations=branched.frames().get(0).annotations;
            require(!branchAnnotations.overlay.contains("DT ")&&!branchAnnotations.overlay.contains("DM1 ")&&branchAnnotations.description().isEmpty(),"new attempt inherited readonly evidence in its common Windows overlay");
            ReplayFiles.exportFile(output.resolve("branch-from-result.hrep"),branched,controller.directory());
            System.out.println("PASS DoodleReplayGuiProbe: "+checks+" native Enter/type, before/result glyphs, blue actual target, coordinate explanation, old-extension display and editable-state isolation checks; "+output);return null;
        });}catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
