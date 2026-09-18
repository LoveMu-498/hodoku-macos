package sudoku;
import java.awt.*;import java.awt.event.*;import java.util.concurrent.*;import javax.swing.*;
public class MissingCandidateMouseProbe {
 static MainFrame f;static <T>T edt(Callable<T> c)throws Exception{FutureTask<T> t=new FutureTask<T>(c);SwingUtilities.invokeAndWait(t);return t.get();}
 public static void main(String[] a)throws Exception{try{
 Robot r=new Robot();r.setAutoDelay(70);
 edt(()->{f=new MainFrame(null);f.setSize(1000,700);f.setVisible(true);SudokuPanel p=f.getSudokuPanel();p.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");p.setShowCandidates(true);p.setShowDeviations(true);f.toFront();f.fixFocus();return null;});r.waitForIdle();
 Point q=edt(()->{SudokuPanel p=f.getSudokuPanel();Point v=p.getLocationOnScreen();int s=p.getX(0,1)-p.getX(0,0);v.translate(p.getX(0,2)+s/6,p.getY(0,2)+s/2);return v;});
 r.mouseMove(q.x,q.y);r.mousePress(InputEvent.BUTTON1_DOWN_MASK);r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);r.waitForIdle();
 edt(()->{f.getSudokuPanel().setShowHintCellValue(4);return null;});
 r.keyPress(KeyEvent.VK_SPACE);r.keyRelease(KeyEvent.VK_SPACE);r.waitForIdle();
 if(edt(()->f.getSudokuPanel().getSudoku().isCandidate(2,4)))throw new AssertionError("Space did not delete 4");
 Thread.sleep(600);
 for(int i=0;i<2;i++){r.mousePress(InputEvent.BUTTON1_DOWN_MASK);r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);}r.waitForIdle();
 if(edt(()->f.getSudokuPanel().getSudoku().getValue(2))!=4)throw new AssertionError("real double click did not enter 4");
 edt(()->{f.getSudokuPanel().undo();return null;});
 if(edt(()->f.getSudokuPanel().getSudoku().getValue(2))!=0 || edt(()->f.getSudokuPanel().getSudoku().isCandidate(2,4)))throw new AssertionError("undo did not restore missing candidate");
 System.out.println("Real mouse + Space + double click + undo passed");
 }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
