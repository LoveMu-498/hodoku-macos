/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;

/** The actual asynchronous S Analyze/Apply record, persisted native proof and candidate pixels. */
public final class ReplayBoxCandidatesProbe {
    static int checks;
    static Path output;
    static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static BufferedImage render(SudokuPanel panel,String name)throws Exception{
        panel.setSize(810,810);BufferedImage image=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();panel.paint(g);g.dispose();ImageIO.write(image,"png",output.resolve(name+".png").toFile());return image;
    }
    static int red(BufferedImage image,Point point){int count=0;for(int y=point.y-9;y<=point.y+9;y++)for(int x=point.x-9;x<=point.x+9;x++){Color c=new Color(image.getRGB(x,y));if(c.getRed()>c.getGreen()+20&&c.getRed()>c.getBlue()+10)count++;}return count;}
    static int green(BufferedImage image,Point point){int count=0;for(int y=point.y-9;y<=point.y+9;y++)for(int x=point.x-9;x<=point.x+9;x++){Color c=new Color(image.getRGB(x,y));if(c.getGreen()>c.getRed()+20&&c.getGreen()>c.getBlue()+5)count++;}return count;}
    static void legacyFile(Path file,ReplaySession source)throws Exception{
        try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(file))){
            out.writeInt(0x4852504c);out.writeInt(3);out.writeUTF(source.id);out.writeLong(source.startedAt);out.writeLong(source.elapsedMillis);out.writeLong(source.endedAt);out.writeBoolean(source.completed);out.writeInt(source.frames().size());
            for(ReplayFrame frame:source.frames()){
                out.writeLong(frame.operationId);out.writeLong(frame.wallTimeMillis);out.writeLong(frame.elapsedMillis);out.writeUTF(frame.label);out.writeUTF(frame.annotations.overlay);out.writeBoolean(frame.methodStep());
                int[] v=frame.board.values();boolean[] fixed=frame.board.fixed();short[] c=frame.board.candidates(),u=frame.board.userCandidates();for(int i=0;i<81;i++){out.writeByte(v[i]);out.writeBoolean(fixed[i]);out.writeShort(c[i]);out.writeShort(u[i]);}
            }
            out.writeInt(0x484d5831);out.writeInt(4);out.writeBoolean(false);out.writeBoolean(source.retained);out.writeBoolean(source.pinned);out.writeUTF(source.interruption);out.writeInt(source.frames().size());
            for(ReplayFrame frame:source.frames()){out.writeUTF(frame.kind);ReplayAnnotations.writeBytes(out,frame.evidence());frame.annotations.write(out,4);}
            out.writeInt(0);out.writeUTF("");out.writeInt(-1);ReplayAnnotations.writeBytes(out,new byte[0]);
        }
    }
    public static void main(String[] args)throws Exception{
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);System.setProperty("apple.awt.UIElement","true");output=Files.createDirectories(Paths.get(args[0]));
        try{
            edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku("489030251671285394253000678394000527865372149712000863030000412547123986028040735");p.setShowCandidates(true);render(p,"live-before");p.setAnnotationTool(AnnotationTool.BOX_SELECTION);
                for(int cell:new int[]{75,77})ChainEditingProbe.click(MouseEvent.BUTTON1,0,ChainEditingProbe.point(cell));
                require(p.getBoxReasoningFootprint().size()==2,"real S selection failed");f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));call("handleReasoningEnter");return null;});await(true);
            final SolutionStep selected=edt(()->(SolutionStep)p.getStep().clone());require(selected.getType()==SolutionType.NAKED_PAIR||selected.getType()==SolutionType.LOCKED_PAIR,"wrong native pair: "+selected.getType());
            edt(()->{require(f.getReplayController().session().frames().size()==1,"preview prematurely recorded");call("handleReasoningEnter");return null;});await(false);
            edt(()->{ReplayController controller=f.getReplayController();ReplaySession saved=controller.session();require(saved.frames().size()==4,"missing authored triple");require(ReplayEvidence.proof(saved.frames().get(2).evidence())!=null,"record lost complete native proof");
                ReplayFiles.exportFile(output.resolve("box-native.hrep"),saved,controller.directory());ReplaySession reopened=ReplayFiles.importFile(output.resolve("box-native.hrep"));ReplayBoard live=new ReplayBoard(p.getSudoku());
                controller.openViewer(reopened);ReplayViewer viewer=controller.viewer();Candidate target=selected.getCandidatesToDelete().get(0);
                viewer.showFrame(2);BufferedImage result=render(viewer.boardPanel(),"result-before");Point point=DoodleReplayGuiProbe.point(viewer.boardPanel(),target.getIndex(),target.getValue());require(red(result,point)>10,"result frame has no red candidate proof");
                viewer.showFrame(3);BufferedImage applied=render(viewer.boardPanel(),"applied");point=DoodleReplayGuiProbe.point(viewer.boardPanel(),target.getIndex(),target.getValue());
                require(!viewer.boardPanel().getSudoku().isCandidate(target.getIndex(),target.getValue()),"applied board rolled back");
                require(viewer.boardPanel().getStep()!=null&&red(applied,point)>10,"applied frame hides the native step and its removed candidate");
                require(green(applied,DoodleReplayGuiProbe.point(viewer.boardPanel(),75,6))>10,"applied frame lost pair premise highlight");
                require(viewer.hintPanel().getText().equals(selected.toString(2)),"applied technique explanation changed");
                viewer.boardPanel().setShowCandidates(false);BufferedImage hidden=render(viewer.boardPanel(),"applied-candidates-off");require(red(hidden,point)>10&&green(hidden,DoodleReplayGuiProbe.point(viewer.boardPanel(),75,6))>10,"proof hidden with user candidates off");
                viewer.showFrame(1);BufferedImage input=render(viewer.boardPanel(),"input-candidates-off");require(viewer.boardPanel().getStep()!=null&&red(input,point)>10,"BOX source frame omitted complete saved proof");
                viewer.showFrame(0);require(viewer.boardPanel().getStep()==null,"proof leaked into initial frame");
                require(new ReplayBoard(p.getSudoku()).equals(live),"viewer changed live board");controller.closeViewer();
                Path legacy=output.resolve("box-extension4.hrep");legacyFile(legacy,reopened);ReplaySession old=ReplayFiles.importFile(legacy);
                controller.openViewer(old);controller.viewer().showFrame(3);SudokuPanel oldPanel=controller.viewer().boardPanel();BufferedImage oldImage=render(oldPanel,"legacy-applied");require(oldPanel.getStep()!=null&&red(oldImage,DoodleReplayGuiProbe.point(oldPanel,target.getIndex(),target.getValue()))>10,"existing extension4 proof not recovered");controller.closeViewer();
                controller.branchFromFrame(old,3);require(new ReplayBoard(p.getSudoku()).equals(live)&&p.getStep()==null,"readonly historical proof became executable branch state");
                ReplayAnnotations initial=controller.session().frames().get(0).annotations;require(initial.proofCandidateAtoms().isEmpty()&&initial.description().isEmpty(),"branch public text inherited historical proof marks");
                // Also cover the ordinary native proof/apply pair, whose result has no embedded proof bytes.
                p.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");controller.beginSession(new ReplayBoard(p.getSudoku()));SolutionStep single=p.getNextStep(false);require(single!=null&&single.getAnzSet()==1,"native single fixture missing");
                f.setSolutionStep(single,true);require(p.doStep(),"native single did not apply");ReplaySession nativeRecord=controller.session();require(nativeRecord.frames().size()==3&&nativeRecord.last().evidence().length==0,"native pair changed evidence schema");
                require(nativeRecord.last().annotations.overlay.contains("CD ")&&nativeRecord.last().annotations.overlay.contains("G "),"native applied frame omitted common technique and candidate marks");
                ReplayFiles.exportFile(output.resolve("single-native.hrep"),nativeRecord,controller.directory());controller.openViewer(nativeRecord);controller.viewer().showFrame(2);
                SudokuPanel filledPanel=controller.viewer().boardPanel();BufferedImage filledImage=render(filledPanel,"single-applied");int filledCell=single.getIndices().get(0);Point center=ChainEditingProbe.point(filledCell);center.translate(30,30);
                require(filledPanel.getStep()!=null&&filledPanel.getSudoku().getValue(filledCell)==single.getValues().get(0),"native result lost proof or actual filled value");
                require(green(filledImage,center)>10,"actual filled value not highlighted");controller.viewer().showFrame(0);require(controller.viewer().boardPanel().getStep()==null,"native proof leaked backwards");controller.closeViewer();
                return null;});
            System.out.println("PASS ReplayBoxCandidatesProbe: "+checks+" actual S Enter, stored proof and applied candidate checks; "+output);
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
