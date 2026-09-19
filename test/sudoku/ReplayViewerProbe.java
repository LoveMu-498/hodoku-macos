package sudoku;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Visible native view, meaningful navigation boundaries, actual forbidden-key input and screenshots. */
public final class ReplayViewerProbe {
 static MainFrame frame;static ReplayController controller;static ReplayViewer view;static SudokuPanel live;
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void edt(Runnable task)throws Exception{SwingUtilities.invokeAndWait(task);}
 static java.lang.reflect.Field field(String name)throws Exception{java.lang.reflect.Field f=MainFrame.class.getDeclaredField(name);f.setAccessible(true);return f;}
 public static void main(String[] args)throws Exception{
  Path directory=Files.createTempDirectory("replay-viewer-");System.setProperty("hodoku.data.dir",directory.toString());
  AtomicReference<ReplayBoard> original=new AtomicReference<ReplayBoard>();AtomicReference<Rectangle> bounds=new AtomicReference<Rectangle>();
  try{
   edt(()->{frame=new MainFrame(null);frame.setSize(1000,850);frame.setVisible(true);controller=frame.getReplayController();live=frame.getSudokuPanel();frame.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");live.setActiveCell(2);live.setCellFromCellZoomPanel(4);controller.afterAction();original.set(new ReplayBoard(live.getSudoku()));
    ReplayBoard initial=controller.session().frames().get(0).board;ReplaySession sample=new ReplaySession(initial,1000);
    Sudoku2 b=initial.toSudoku();SolutionStep hint=live.getSolver().getHint(b,false);require(hint!=null,"real solver hint absent");byte[] proof=ReplayProof.encode(hint);
    sample.append(Arrays.asList(new ReplayFrame(1,2000,1000,"proof","Native proof",initial,proof),new ReplayFrame(1,2000,1000,"apply","Applied",original.get(),null)));
    ReplayViewer boundary=new ReplayViewer(controller,sample);boundary.showFrame(1);boundary.jumpOperation(1);require(boundary.frameIndex()==1,"last multi-frame operation must not jump internally");try{java.lang.reflect.Field next=ReplayViewer.class.getDeclaredField("nextGroup");next.setAccessible(true);require(!((JButton)next.get(boundary)).isEnabled(),"last operation next group enabled");}catch(Exception ex){throw new RuntimeException(ex);}boundary.disposeViewer();
    sample.append(Collections.singletonList(new ReplayFrame(2,62000,61000,"manual","After long interval",initial,null)));sample.addBookmark(new ReplayBookmark(1,"Proof start",2000,1000));
    controller.openViewer(sample);view=controller.viewer();view.showFrame(2);view.jumpOperation(-1);require(view.frameIndex()==0,"previous group not prior operation");view.jumpOperation(1);require(view.frameIndex()==1,"next group not first frame");view.showFrame(2);view.snapToTime(31000);require(view.frameIndex()==1,"drag tie must choose earlier frame");view.snapToTime(61000);require(view.frameIndex()==3,"end snap");view.showFrame(1);require(view.boardPanel().getStep()!=null,"native proof not drawn");
    Point location=frame.getLocationOnScreen();bounds.set(new Rectangle(location.x,location.y,frame.getWidth(),frame.getHeight()));});
   Robot robot=new Robot();robot.setAutoDelay(65);robot.waitForIdle();
   AtomicReference<Point> click=new AtomicReference<Point>();edt(()->{Point p=view.getLocationOnScreen();p.translate(30,100);click.set(p);view.requestFocusInWindow();});robot.mouseMove(click.get().x,click.get().y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);edt(()->view.requestFocusInWindow());robot.waitForIdle();
   for(int key:new int[]{KeyEvent.VK_7,KeyEvent.VK_ENTER,KeyEvent.VK_F11}){robot.keyPress(key);robot.keyRelease(key);}robot.keyPress(KeyEvent.VK_META);robot.keyPress(KeyEvent.VK_Z);robot.keyRelease(KeyEvent.VK_Z);robot.keyRelease(KeyEvent.VK_META);robot.waitForIdle();
   edt(()->{require(new ReplayBoard(live.getSudoku()).equals(original.get()),"readonly shortcut mutated live board");require(controller.session().frames().size()==2,"viewer events recorded");try{JMenuItem newItem=(JMenuItem)field("newMenuItem").get(frame);require(!newItem.isEnabled(),"menu not disabled");newItem.doClick();}catch(Exception e){throw new RuntimeException(e);}require(new ReplayBoard(live.getSudoku()).equals(original.get()),"menu mutated board");
    Component area=frame.replayArea();Rectangle expected=SwingUtilities.convertRectangle(area.getParent(),area.getBounds(),frame.getGlassPane());require(view.getBounds().equals(expected),"viewer does not cover solve area");require(view.getBounds().y+view.getHeight()<frame.getGlassPane().getHeight(),"bottom status hidden");});
   javax.imageio.ImageIO.write(robot.createScreenCapture(bounds.get()),"png",directory.resolve("viewer-normal.png").toFile());
   edt(()->{frame.setSize(700,650);view.showFrame(2);});robot.waitForIdle();Thread.sleep(300);edt(()->{require(view.getWidth()==frame.replayArea().getWidth(),"resize did not reflow viewer");Point p=frame.getLocationOnScreen();bounds.set(new Rectangle(p.x,p.y,frame.getWidth(),frame.getHeight()));});javax.imageio.ImageIO.write(robot.createScreenCapture(bounds.get()),"png",directory.resolve("viewer-narrow.png").toFile());
   edt(()->{view.showFrame(1);view.setPlaying(true);});Thread.sleep(850);edt(()->{require(view.frameIndex()==2,"same-time ordered frame did not play");require(view.isPlaying(),"long interval skipped to end");view.setPlaying(false);require(!view.isPlaying(),"pause failed");controller.closeViewer();require(!view.isPlaying(),"close left timer active");require(new ReplayBoard(live.getSudoku()).equals(original.get()),"return changed board");live.undo();controller.afterAction();require(live.getSudoku().getValue(2)==0,"return lost undo");});
   System.out.println("Native replay navigation, long-gap snap, same-time frames, native proof, readonly keyboard/menu, live status, narrow layout, playback pause, return undo passed: "+directory);
  }catch(Throwable e){e.printStackTrace();System.exit(1);}finally{if(frame!=null)edt(()->frame.dispose());}System.exit(0);
 }
}
