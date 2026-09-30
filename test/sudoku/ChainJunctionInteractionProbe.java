package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javax.swing.*;
public final class ChainJunctionInteractionProbe {
 static MainFrame frame;static SudokuPanel board;static JButton state;
 static <T>T edt(Callable<T> work)throws Exception{java.util.concurrent.FutureTask<T> t=new java.util.concurrent.FutureTask<T>(work);SwingUtilities.invokeAndWait(t);return t.get();}
 static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static void counts(int strong,int weak){ChainJunctionSummary s=board.chainJunctionSummary();check(s.strong.size()==strong&&s.weak.size()==weak,"counts "+strong+"/"+weak);check(state.getToolTipText().contains("双强 "+strong+" 处，双弱 "+weak+" 处"),"stale tooltip");}
 static void click(int cell,int button,int modifiers){
  cell=new int[]{2,6,7}[cell];
  int size=board.getX(0,1)-board.getX(0,0);int x=board.getX(cell/9,cell%9)+size/6,y=board.getY(cell/9,cell%9)+size/6;
  board.dispatchEvent(new MouseEvent(board,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),modifiers,x,y,1,false,button));
  board.dispatchEvent(new MouseEvent(board,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),modifiers,x,y,1,false,button));
 }
 static BufferedImage paint(Component c){BufferedImage img=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=img.createGraphics();c.paint(g);g.dispose();return img;}
 public static void main(String[] args)throws Exception{
  Robot robot=new Robot();robot.setAutoDelay(80);
  try{
   edt(()->{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());ApplicationAppearance.initialize(Boolean.getBoolean("hodoku.probe.dark")?AppearanceMode.DARK:AppearanceMode.LIGHT);
    frame=new MainFrame(null);frame.setSize(1000,820);frame.setLocation(40,40);frame.setVisible(true);frame.toFront();
    board=frame.getSudokuPanel();board.setSudoku(CurrentReasoningProbe.PUZZLE);board.setShowCandidates(true);board.setAnnotationTool(AnnotationTool.FREE_CHAIN);
    state=(JButton)field(board.getCellZoomPanel().getToolbarPalette(),"modeState");return null;});robot.waitForIdle();
   edt(()->{click(0,MouseEvent.BUTTON1,0);click(1,MouseEvent.BUTTON1,0);board.setNextUserChainStrong(true);click(2,MouseEvent.BUTTON1,0);counts(1,0);
    click(2,MouseEvent.BUTTON3,0);counts(0,0);board.undoCurrentAnnotation();counts(1,0);board.redoCurrentAnnotation();counts(0,0);
    board.setNextUserChainStrong(true);click(0,MouseEvent.BUTTON1,0);counts(1,0);check(board.chainJunctionSummary().closed,"closure missing");
    board.undoCurrentAnnotation();counts(0,0);board.redoCurrentAnnotation();counts(1,0);
    click(0,MouseEvent.BUTTON3,InputEvent.CTRL_DOWN_MASK);board.updateDeletionModifier(new KeyEvent(board,KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,KeyEvent.VK_CONTROL,KeyEvent.CHAR_UNDEFINED));counts(0,0);check(!board.chainJunctionSummary().closed,"delete closure stale");board.undoCurrentAnnotation();counts(1,0);
    board.clearUserChainsWithUndo();counts(0,0);board.undoCurrentAnnotation();counts(1,0);return null;});
   Point target=edt(()->{Point p=state.getLocationOnScreen();p.translate(state.getWidth()/2,state.getHeight()/2);return p;});
   BufferedImage before=edt(()->paint(board));robot.mouseMove(target.x,target.y);robot.waitForIdle();
   edt(()->{check((Boolean)field(board,"chainJunctionHover"),"real hover on disabled status did not enter");return null;});
   BufferedImage after=edt(()->paint(board));int changed=0;for(int y=0;y<before.getHeight();y++)for(int x=0;x<before.getWidth();x++)if(before.getRGB(x,y)!=after.getRGB(x,y))changed++;
   check(changed>20,"hover did not paint node rings");
   File out=new File(System.getProperty("hodoku.probe.output"));out.mkdirs();
   edt(()->{ImageIO.write(paint(frame),"png",new File(out,"hover.png"));ImageIO.write(paint(state),"png",new File(out,"counter.png"));return null;});
   robot.mouseMove(target.x+100,target.y+100);robot.waitForIdle();edt(()->{check(!(Boolean)field(board,"chainJunctionHover"),"hover did not exit");
    board.setAnnotationTool(AnnotationTool.DOODLE);check(state.getToolTipText().contains("涂鸦粗细"),"doodle attribute regressed");board.setAnnotationTool(AnnotationTool.FREE_CHAIN);counts(1,0);
    board.cancelAnnotationToolInteractionOnDeactivation();check(!(Boolean)field(board,"chainJunctionHover"),"deactivation retained hover");return null;});
   System.out.println("PASS: drawing, flip, closure seam, delete closure, clear, undo/redo, live tooltip, Robot hover rings/exit, tool switching");
  }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(frame!=null)edt(()->{frame.dispose();return null;});}System.exit(0);
 }
}
