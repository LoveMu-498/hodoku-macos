package sudoku;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.GroupedChainProbe.*;
/** Real asynchronous authored proofs, condition diagnostics and frozen original-source history. */
public final class ReplayAuthoredProbe {
 static ReplayController replay;
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[]args)throws Exception{
  Path data=Files.createTempDirectory("replay-authored-");System.setProperty("hodoku.data.dir",data.toString());
  try{
   for(boolean certified:new boolean[]{true,false}){
    edt(()->{if(f==null){f=new MainFrame(null);p=f.getSudokuPanel();replay=f.getReplayController();}replay.suspend();p.setSudoku((String)null);p.getSudoku().set(ChainAdvisoryProbe.board(certified));p.setAnnotationTool(AnnotationTool.FREE_CHAIN);UserChain chain=ChainAdvisoryProbe.ring();chain.setActive(true);Field a=SudokuPanel.class.getDeclaredField("activeUserChain");a.setAccessible(true);a.set(p,chain);call("noteUserChainReasoningChanged");replay.beginSession(new ReplayBoard(p.getSudoku()));replay.resume();call("handleReasoningEnter");return null;});await(true);
    edt(()->{require(replay.session().frames().size()==1,"preview committed");
     // Later edits must not alter the frozen proof source (existing allowed native behavior).
     @SuppressWarnings("unchecked") List<UserChain> chains=(List<UserChain>)read("userChains");chains.get(0).getNodes().get(0).setColor(java.awt.Color.MAGENTA);chains.get(0).getNodes().add(node(2,80));call("noteUserChainReasoningChanged");call("handleReasoningEnter");return null;});await(false);
    edt(()->{require(p.getSudoku().getValue(0)==1,"native apply missing");List<ReplayFrame> frames=replay.session().frames();require(frames.size()==4,"need exactly three linked frames: "+frames.size());
     ReplayEvidence e=ReplayEvidence.decode(frames.get(1).evidence());require(e.chains().get(0).getNodes().size()==3,"source changed after freeze");require(e.chains().get(0).isActive(),"first Enter active source lost");require(e.status.equals(certified?"PROVEN":"ASSUMED"),"wrong proof status "+e.status);require(e.invalidRelations().size()==(certified?0:2),"diagnostic edges missing");
     require(frames.get(1).board.equals(frames.get(2).board)&&!frames.get(2).board.equals(frames.get(3).board),"three-frame boards");require(frames.get(1).wallTimeMillis<=frames.get(2).wallTimeMillis&&frames.get(2).wallTimeMillis<=frames.get(3).wallTimeMillis,"real times order");
     for(int n=1;n<4;n++){require(frames.get(n).operationId==1,"multiple operations");require(Arrays.equals(frames.get(1).evidence(),frames.get(n).evidence()),"branch source missing");}
     replay.openViewer(replay.session());for(int n=1;n<4;n++){replay.viewer().showFrame(n);SudokuPanel renderer=replay.viewer().boardPanel();renderer.setSize(720,720);java.awt.image.BufferedImage im=new java.awt.image.BufferedImage(720,720,1);renderer.paint(im.getGraphics());javax.imageio.ImageIO.write(im,"png",data.resolve("authored-"+certified+"-"+n+".png").toFile());require((renderer.getStep()!=null)==(n==2),"wrong visual layer");}replay.closeViewer();return null;});
    ReplaySession loaded=ReplayStore.read(replay.directory().resolve(replay.session().id+".hrep"));require(ReplayEvidence.decode(loaded.frames().get(3).evidence()).chains().get(0).getNodes().size()==3,"disk lost branch source");
   }
   // No-match failure then unrelated genuine edit must never become authored third frame.
   edt(()->{replay.suspend();p.setSudoku((String)null);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);replay.beginSession(new ReplayBoard(p.getSudoku()));replay.resume();call("handleReasoningEnter");p.getSudoku().delCandidate(0,1);replay.capture("manual","手动删数");require(replay.session().frames().size()==2,"failed request linked to manual action");require(!ReplayEvidence.authored(replay.session().last().evidence()),"false provenance");return null;});
   System.out.println("Authored async proven/assumed three frames, first Enter original source, later edit isolation, native layers, diagnostics, disk branch source and failed-unrelated edit passed: "+data);
  }catch(Throwable e){e.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
 }
}
