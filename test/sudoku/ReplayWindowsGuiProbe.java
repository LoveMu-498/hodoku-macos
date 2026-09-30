package sudoku;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;

/** Native renderer and recorder acceptance against the actual Windows fixture. */
public final class ReplayWindowsGuiProbe {
 static MainFrame frame;
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  Path data=Files.createTempDirectory("replay-windows-gui-");System.setProperty("hodoku.data.dir",data.toString());
  ReplaySession imported=ReplayFiles.importFile(Paths.get(args[0]));
  try{
   SwingUtilities.invokeAndWait(()->{
    try{
     frame=new MainFrame(null);frame.setSize(1000,900);frame.setVisible(true);ReplayController c=frame.getReplayController();SudokuPanel live=frame.getSudokuPanel();ReplayBoard original=new ReplayBoard(live.getSudoku());
     c.openViewer(imported);ReplayViewer v=c.viewer();v.setSize(980,800);v.doLayout();
     for(int i=0;i<imported.frames().size();i++){
      v.showFrame(i);SudokuPanel panel=v.boardPanel();panel.setSize(800,650);check(panel.isVisible(),"frame failed "+i);check(new ReplayBoard(panel.getSudoku()).equals(imported.frames().get(i).board),"wrong board "+i);
      BufferedImage image=new BufferedImage(800,650,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();panel.paint(g);g.dispose();
      if(i==0||i==51)javax.imageio.ImageIO.write(image,"png",data.resolve("frame-"+i+".png").toFile());
     }
     check(new ReplayBoard(live.getSudoku()).equals(original),"view mutated live");c.closeViewer();
     AtomicLong time=new AtomicLong();ReplayViewer clock=new ReplayViewer(c,imported,time::get);clock.setPlaying(true);time.set(1000000000L);clock.advancePlayback();check(clock.playbackMillis()>imported.frames().get(0).elapsedMillis,"cursor did not tick");clock.disposeViewer();
     int selected=0;for(int i=0;i<imported.frames().size();i++)if(!ReplayEvidence.decode(imported.frames().get(i).annotations.geometry()).chains().isEmpty()){selected=i;break;}
     c.branchFromFrame(imported,selected);check(live.currentReasoningChains().size()>0,"branch lost imported chains");check(live.getStep()==null,"branch kept executable historical proof");
     int count=c.session().frames().size();ReplayAnnotations snapshot=live.captureReplayAnnotations();c.afterAction();check(c.session().frames().size()==count,"annotation-only action recorded");
     Sudoku2 board=live.getSudoku();int cell=-1,digit=-1;for(int i=0;i<81&&cell<0;i++)if(board.getValue(i)==0)for(int n=1;n<=9;n++)if(board.isCandidate(i,n)){cell=i;digit=n;break;}
     check(cell>=0,"no mutable fixture cell");board.delCandidate(cell,digit);c.afterAction();check(c.session().frames().size()==count+1,"board operation not recorded");check(!c.session().last().annotations.overlay.isEmpty(),"manual edit lost overlays");
     Path exported=data.resolve("branch.hrep");ReplayFiles.exportFile(exported,c.session(),c.directory());ReplaySession read=ReplayFiles.importFile(exported);check(!read.last().annotations.overlay.isEmpty(),"new recording lost annotations on disk");
     c.openViewer(read);c.viewer().showFrame(read.frames().size()-1);check(c.viewer().boardPanel().isVisible(),"native replay failed");c.closeViewer();
    }catch(Exception ex){throw new RuntimeException(ex);}
   });
   System.out.println("PASS 128 native-rendered foreign frames, independent live state, ticking time, editable chain branch, operation-only annotation snapshots, export/import: "+data);
  }catch(Throwable ex){ex.printStackTrace();System.exit(1);}finally{if(frame!=null)SwingUtilities.invokeAndWait(()->frame.dispose());}System.exit(0);
 }
}
