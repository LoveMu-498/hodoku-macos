package sudoku;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

/** Actual keyboard input against a visible isolated window, followed by persisted native replay. */
public final class ReplayNativeInputProbe {
 static MainFrame frame; static SudokuPanel panel; static ReplayController controller;
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void edt(Runnable r)throws Exception{SwingUtilities.invokeAndWait(r);}
 public static void main(String[] args)throws Exception{
  Path data=Files.createTempDirectory("replay-native-input-");System.setProperty("hodoku.data.dir",data.toString());
  try{
   edt(()->{frame=new MainFrame(null);panel=frame.getSudokuPanel();controller=frame.getReplayController();controller.suspend();
    panel.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
    controller.beginSession(new ReplayBoard(panel.getSudoku()));controller.resume();frame.setSize(1000,800);frame.setVisible(true);frame.toFront();panel.setActiveCell(2);panel.requestFocusInWindow();});
   Robot robot=new Robot();robot.setAutoDelay(80);robot.waitForIdle();Thread.sleep(350);
   AtomicReference<Point> click=new AtomicReference<Point>();edt(()->{Point p=panel.getLocationOnScreen();p.translate(panel.getWidth()/2,panel.getHeight()/2);click.set(p);});robot.mouseMove(click.get().x,click.get().y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();edt(()->{panel.setActiveCell(2);panel.requestFocusInWindow();});robot.waitForIdle();
   robot.keyPress(KeyEvent.VK_4);robot.keyRelease(KeyEvent.VK_4);robot.waitForIdle();
   edt(()->{require(panel.getSudoku().getValue(2)==4,"native key did not fill selected cell");require(controller.session().frames().size()==2,"one input must yield one operation");});
   AtomicReference<ReplayBoard> original=new AtomicReference<ReplayBoard>();
   edt(()->{original.set(new ReplayBoard(panel.getSudoku()));controller.openViewer(controller.session());controller.viewer().showFrame(0);require(controller.viewer().boardPanel().getSudoku().getValue(2)==0,"initial native view");});
   robot.keyPress(KeyEvent.VK_7);robot.keyRelease(KeyEvent.VK_7);robot.waitForIdle();
   edt(()->{require(new ReplayBoard(panel.getSudoku()).equals(original.get()),"replay key mutated live board");require(controller.session().frames().size()==2,"browsing was recorded");controller.viewer().showFrame(1);require(controller.viewer().boardPanel().getSudoku().getValue(2)==4,"applied native view");
    java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(1000,800,java.awt.image.BufferedImage.TYPE_INT_RGB);frame.paint(image.getGraphics());try{javax.imageio.ImageIO.write(image,"png",data.resolve("viewer.png").toFile());}catch(Exception e){throw new RuntimeException(e);}controller.closeViewer();panel.undo();});
   robot.waitForIdle();edt(()->{require(panel.getSudoku().getValue(2)==0,"original undo state not preserved");require(controller.session().frames().size()==3,"undo did not append");});
   ReplaySession loaded=ReplayStore.read(controller.directory().resolve(controller.session().id+".hrep"));require(loaded.frames().size()==3,"real storage not current");
   System.out.println("Replay native actual key, complete action, native read-only viewer, undo preservation and disk roundtrip passed: "+data);
  }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(frame!=null)edt(()->frame.dispose());}
  System.exit(0);
 }
}
