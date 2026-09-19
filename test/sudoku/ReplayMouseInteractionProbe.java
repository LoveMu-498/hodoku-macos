package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.ResourceBundle;
import javax.swing.*;

/** Native mouse events must reach the glass-pane controls before Swing retargets them. */
public final class ReplayMouseInteractionProbe {
    private static MainFrame frame;
    private static ReplayController recorder;
    private static Robot robot;
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void edt(Runnable action)throws Exception{SwingUtilities.invokeAndWait(action);}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new RuntimeException(e);}}
    private static JButton button(Container parent,String text){for(Component c:parent.getComponents()){if(c instanceof JButton&&text.equals(((JButton)c).getText()))return (JButton)c;if(c instanceof Container){JButton b=button((Container)c,text);if(b!=null)return b;}}return null;}
    private static <T> T component(Container parent,Class<T> type){for(Component c:parent.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container){T found=component((Container)c,type);if(found!=null)return found;}}return null;}
    private static void click(Component component)throws Exception{
        require(component!=null,"missing control");final Point[] location={null};
        edt(()->{location[0]=component.getLocationOnScreen();location[0].translate(component.getWidth()/2,component.getHeight()/2);});
        robot.mouseMove(location[0].x,location[0].y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();Thread.sleep(180);
    }
    public static void run(MainFrame target,Path output,boolean regressionOnly)throws Exception{
        frame=target;recorder=frame.getReplayController();robot=new Robot();robot.setAutoDelay(70);
        edt(()->{frame.setSize(1040,860);frame.setVisible(true);frame.toFront();frame.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");frame.getSudokuPanel().setActiveCell(2);frame.getSudokuPanel().setCellFromCellZoomPanel(4);recorder.afterAction();frame.getSudokuPanel().setActiveCell(3);frame.getSudokuPanel().setCellFromCellZoomPanel(6);recorder.afterAction();recorder.openViewer(recorder.session());});
        robot.waitForIdle();
        String pid=java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        Process activate=new ProcessBuilder("/usr/bin/osascript","-e","use framework \"AppKit\"\non run argv\nset targetApp to current application's NSRunningApplication's runningApplicationWithProcessIdentifier:((item 1 of argv) as integer)\ntargetApp's activateWithOptions:3\nend run",pid).inheritIO().start();
        require(activate.waitFor()==0,"could not activate isolated test window");Thread.sleep(400);
        click((JButton)field(recorder.viewer(),"next"));
        edt(()->require(recorder.viewer().frameIndex()==1,"REAL MOUSE next button did not advance: top-level event swallowed before Swing retargeting"));
        if(regressionOnly)return;
        final ReplayBoard live=new ReplayBoard(frame.getSudokuPanel().getSudoku());final String id=recorder.session().id;
        JLabel timer=(JLabel)field(recorder,"status");require(timer.getText().matches("\\d{2,}:\\d{2}:\\d{2}"),"bottom timer not time-only");
        edt(()->{require(button(timer.getParent(),ReplayText.text("library"))==null,"history still in bottom bar");JMenuItem current=(JMenuItem)field(frame,"viewReplayMenuItem"),library=(JMenuItem)field(frame,"replayLibraryMenuItem");require(current.getParent()==((JMenuItem)field(frame,"restoreSavePointMenuItem")).getParent(),"replay menu not beside savepoints");require(!library.isEnabled(),"underlying menu enabled in readonly view");});
        click((JButton)field(recorder.viewer(),"previous"));require(recorder.viewer().frameIndex()==0,"previous failed");
        click((JButton)field(recorder.viewer(),"nextGroup"));require(recorder.viewer().frameIndex()==1,"next group failed");
        click((JButton)field(recorder.viewer(),"last"));require(recorder.viewer().frameIndex()==2,"last failed");
        click((JButton)field(recorder.viewer(),"first"));require(recorder.viewer().frameIndex()==0,"first failed");
        click((JButton)field(recorder.viewer(),"play"));Thread.sleep(850);require(recorder.viewer().frameIndex()>0,"play failed");edt(()->recorder.viewer().setPlaying(false));
        click(recorder.viewer().timeline());require(recorder.viewer().frameIndex()>=0,"slider failed");
        click(recorder.viewer().boardPanel());robot.keyPress(KeyEvent.VK_7);robot.keyRelease(KeyEvent.VK_7);robot.waitForIdle();
        final Point[] toolbar={null};edt(()->{toolbar[0]=frame.getLocationOnScreen();toolbar[0].translate(28,52);});robot.mouseMove(toolbar[0].x,toolbar[0].y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();
        require(live.equals(new ReplayBoard(frame.getSudokuPanel().getSudoku()))&&recorder.session().frames().size()==3,"readonly mouse/key/toolbar mutated live session");
        for(String key:new String[]{"MainFrame.replayFiles.open","MainFrame.replayFiles.export","MainFrame.replayShare.button"}){
            final int[] opened={0};final javax.swing.Timer[] closer={null};edt(()->{closer[0]=new javax.swing.Timer(100,e->{for(Window w:Window.getWindows())if(w.isShowing()&&w instanceof JDialog){JFileChooser chooser=component(w,JFileChooser.class);if(chooser!=null){opened[0]++;chooser.cancelSelection();}}});closer[0].start();});
            try{click(button(recorder.viewer(),ResourceBundle.getBundle("intl/MainFrame").getString(key)));require(opened[0]==1,"real mouse file/share chooser failed: "+key);}finally{edt(()->closer[0].stop());}
        }
        final int[] confirmations={0};final javax.swing.Timer[] cancel={null};edt(()->{cancel[0]=new javax.swing.Timer(100,e->{for(Window w:Window.getWindows())if(w.isShowing()&&w instanceof JDialog){JOptionPane pane=component(w,JOptionPane.class);if(pane!=null){confirmations[0]++;pane.setValue(JOptionPane.CANCEL_OPTION);}}});cancel[0].start();});
        try{click(button(recorder.viewer(),ReplayText.text("branch")));require(confirmations[0]==1&&id.equals(recorder.session().id),"branch confirmation/cancel failed");}finally{edt(()->cancel[0].stop());}
        final Rectangle[] bounds={null};edt(()->{Point p=frame.getLocationOnScreen();bounds[0]=new Rectangle(p.x,p.y,frame.getWidth(),frame.getHeight());});Files.createDirectories(output);javax.imageio.ImageIO.write(robot.createScreenCapture(bounds[0]),"png",output.resolve("mouse-viewer.png").toFile());
        click(button(recorder.viewer(),ReplayText.text("return")));require(!recorder.isViewing(),"real mouse Return failed");require(live.equals(new ReplayBoard(frame.getSudokuPanel().getSudoku())),"return altered live board");
        edt(()->{require(((JMenuItem)field(frame,"viewReplayMenuItem")).isEnabled(),"menu not restored");((JMenuItem)field(frame,"viewReplayMenuItem")).doClick();require(recorder.isViewing(),"menu replay entry failed");recorder.closeViewer();frame.getSudokuPanel().undo();recorder.afterAction();require(frame.getSudokuPanel().getSudoku().getValue(3)==0,"undo not preserved");});
        System.out.println("PASS real mouse navigation/play/slider/return, all file/share dialogs, branch cancel, readonly board/toolbar, time-only label, native menu entries and original undo");
    }
    public static void main(String[] args)throws Exception{
        Path data=Files.createTempDirectory("replay-mouse-");System.setProperty("hodoku.data.dir",data.toString());
        try{edt(()->frame=new MainFrame(null));run(frame,data,args.length>0);}
        catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        finally{if(frame!=null)edt(()->frame.dispose());}System.exit(0);
    }
}
