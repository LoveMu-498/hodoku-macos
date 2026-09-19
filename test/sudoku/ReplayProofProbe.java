package sudoku;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
/** Native solver proof, true Enter application, disk data and native proof renderer. */
public final class ReplayProofProbe {
 static MainFrame f;static SudokuPanel p;static ReplayController c;static SolutionStep displayed;
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void edt(Runnable r)throws Exception{SwingUtilities.invokeAndWait(r);}
 public static void main(String[]args)throws Exception{
  Path data=Files.createTempDirectory("replay-proof-");System.setProperty("hodoku.data.dir",data.toString());
  try{
   edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();c=f.getReplayController();c.suspend();
    p.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");c.beginSession(new ReplayBoard(p.getSudoku()));c.resume();
    displayed=p.getNextStep(false);check(displayed!=null,"real hint");f.setSolutionStep(displayed,true);check(c.session().frames().size()==1,"display-only recorded");f.setSize(1000,800);f.setVisible(true);f.toFront();p.requestFocusInWindow();});
   Robot r=new Robot();r.setAutoDelay(80);r.waitForIdle();Thread.sleep(350);
   AtomicReference<Point> pt=new AtomicReference<Point>();edt(()->{Point q=p.getLocationOnScreen();q.translate(p.getWidth()/2,p.getHeight()/2);pt.set(q);});r.mouseMove(pt.get().x,pt.get().y);r.mousePress(InputEvent.BUTTON1_DOWN_MASK);r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);r.waitForIdle();edt(()->p.requestFocusInWindow());r.waitForIdle();
   for(int attempt=0;attempt<20;attempt++){AtomicReference<Boolean> focused=new AtomicReference<Boolean>();edt(()->focused.set(p.isFocusOwner()));if(focused.get())break;Thread.sleep(100);r.waitForIdle();}
   edt(()->check(p.isFocusOwner(),"native proof test requires board keyboard focus"));r.keyPress(KeyEvent.VK_ENTER);r.keyRelease(KeyEvent.VK_ENTER);r.waitForIdle();
   edt(()->{check(c.session().frames().size()==3,"expected initial/proof/apply; actual="+c.session().frames().size());ReplayFrame before=c.session().frames().get(1),after=c.session().frames().get(2);check(before.operationId==after.operationId,"split operation");check(!before.board.equals(after.board),"missing actual change");byte[] immutable=before.evidence();displayed.getIndices().clear();displayed.getValues().clear();check(Arrays.equals(immutable,before.evidence()),"mutable proof");c.openViewer(c.session());c.viewer().showFrame(1);check(c.viewer().boardPanel().getStep()!=null,"native proof absent");java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(1000,800,1);f.paint(image.getGraphics());try{javax.imageio.ImageIO.write(image,"png",data.resolve("proof.png").toFile());}catch(Exception e){throw new RuntimeException(e);}c.closeViewer();});
   ReplaySession saved=ReplayStore.read(c.directory().resolve(c.session().id+".hrep"));SolutionStep restored=ReplayProof.decode(saved.frames().get(1).evidence());check(!restored.getIndices().isEmpty(),"persisted proof incomplete");
   // Exercise complete deep schema with real native generated solution corpus.
   Sudoku2 board=new Sudoku2();board.setSudoku("100007090030020008009600500005300900010080002600004000300000010040000007007000300");solver.SudokuSolver solver=new solver.SudokuSolver();solver.setSudoku(board);solver.solve();int count=0;
   for(SolutionStep step:solver.getSteps()){byte[] bytes=ReplayProof.encode(step);SolutionStep copy=ReplayProof.decode(bytes);check(Arrays.equals(bytes,ReplayProof.encode(copy)),"noncanonical full proof");count++;}
   edt(()->{c.beginSession(new ReplayBoard(p.getSudoku()));p.setAllSingles();check(c.session().last().operationId==1,"batch must remain one operation");});
   System.out.println("Native Enter two frames, isolated proof paint, immutable disk roundtrip, batch grouping and "+count+" generated proof codecs passed: "+data);
  }catch(Throwable e){e.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->f.dispose());}System.exit(0);
 }
}
