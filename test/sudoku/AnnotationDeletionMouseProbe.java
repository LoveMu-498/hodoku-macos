package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Native macOS Control/right-button events, cursor, wheel and independent undo. */
public final class AnnotationDeletionMouseProbe {
    private static MainFrame frame;private static Robot robot;
    public static void main(String[] args)throws Exception {
        try {
            robot=new Robot();robot.setAutoDelay(75);
            edt(()->{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());frame=new MainFrame(null);frame.setSize(1000,700);frame.setLocation(10,30);frame.setVisible(true);frame.getSudokuPanel().setSudoku(CurrentReasoningProbe.PUZZLE);frame.toFront();frame.fixFocus();return null;});
            robot.waitForIdle();Point origin=edt(()->frame.getSudokuPanel().getLocationOnScreen());
            robot.mouseMove(origin.x+100,origin.y+60);click(InputEvent.BUTTON1_DOWN_MASK);
            edt(()->{frame.getSudokuPanel().setAnnotationTool(AnnotationTool.DOODLE);return null;});
            drag(origin,80,90,480,90,InputEvent.BUTTON1_DOWN_MASK);
            require(edt(()->frame.getSudokuPanel().getDoodleStrokeCount())==1,"native pen");
            robot.keyPress(KeyEvent.VK_CONTROL);robot.waitForIdle();
            float before=edt(()->(Float)read("doodleEraserRadius"));robot.mouseWheel(1);robot.waitForIdle();
            require(edt(()->(Float)read("doodleEraserRadius"))>before,"Control wheel did not resize eraser");
            require(edt(()->frame.getSudokuPanel().getCursor().getType())==Cursor.CUSTOM_CURSOR,"Control eraser cursor");
            drag(origin,280,60,280,120,InputEvent.BUTTON1_DOWN_MASK);
            require(edt(()->frame.getSudokuPanel().getDoodleStrokeCount())==2,"native Control-left did not cut pen stroke");
            capture("circle-erase.png");robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_META);robot.keyPress(KeyEvent.VK_Z);robot.keyRelease(KeyEvent.VK_Z);robot.keyRelease(KeyEvent.VK_META);robot.waitForIdle();
            require(edt(()->frame.getSudokuPanel().getDoodleStrokeCount())==1,"Command-Z did not undo erasure");
            robot.keyPress(KeyEvent.VK_CONTROL);drag(origin,240,60,320,120,InputEvent.BUTTON3_DOWN_MASK);robot.keyRelease(KeyEvent.VK_CONTROL);
            require(edt(()->frame.getSudokuPanel().getDoodleStrokeCount())==2,"native Control-right did not cut rectangle");
            drag(origin,100,180,350,290,InputEvent.BUTTON3_DOWN_MASK);
            require(edt(()->frame.getSudokuPanel().getDoodleStrokeCount())==3,"native right drag did not add ellipse");capture("ellipse-and-cuts.png");
            System.out.println("Native annotation mouse checks passed: Control-left/right, wheel, cursor, Command undo, right ellipse");
        }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(frame!=null)edt(()->{frame.dispose();return null;});}System.exit(0);
    }
    private static void drag(Point o,int x,int y,int ex,int ey,int button)throws Exception{robot.mouseMove(o.x+x,o.y+y);robot.mousePress(button);for(int i=1;i<=12;i++)robot.mouseMove(o.x+x+(ex-x)*i/12,o.y+y+(ey-y)*i/12);robot.mouseRelease(button);robot.waitForIdle();}
    private static void click(int button){robot.mousePress(button);robot.mouseRelease(button);robot.waitForIdle();}
    private static Object read(String name)throws Exception{Field f=SudokuPanel.class.getDeclaredField(name);f.setAccessible(true);return f.get(frame.getSudokuPanel());}
    private static void capture(String name)throws Exception{File file=new File(System.getProperty("hodoku.probe.output","/tmp/hodoku-control-build/evidence"),name);file.getParentFile().mkdirs();ImageIO.write(robot.createScreenCapture(edt(()->frame.getBounds())),"png",file);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static <T>T edt(Callable<T> c)throws Exception{FutureTask<T> task=new FutureTask<T>(c);SwingUtilities.invokeAndWait(task);return task.get();}
}
