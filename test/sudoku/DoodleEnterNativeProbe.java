package sudoku;
import java.awt.*;import java.awt.event.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;
/** Actual native Enter applies Command-selected P conclusions; Backspace and modifiers do not. */
public final class DoodleEnterNativeProbe {
 static KeyEvent key(int code,int mods){return new KeyEvent(p,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),mods,code,KeyEvent.CHAR_UNDEFINED);}
 public static void main(String[]args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{
  edt(()->{initialize();mark(12,2,2,0);outline(12,2);String before=TechniqueStepCatalog.createSignature(p.getSudoku());
   p.handleAnnotationKeyPressed(key(KeyEvent.VK_BACK_SPACE,0));check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"Backspace applied P conclusion");
   p.handleAnnotationKeyPressed(key(KeyEvent.VK_ENTER,InputEvent.SHIFT_DOWN_MASK));check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"modified Enter applied P conclusion");
   p.setReplayReadOnly(true);p.handleAnnotationKeyPressed(key(KeyEvent.VK_ENTER,0));check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"read-only Enter applied P conclusion");p.setReplayReadOnly(false);
   f.setSize(1100,950);f.setLocation(70,70);f.setVisible(true);f.toFront();p.requestFocusInWindow();return null;});
  Process activation=new ProcessBuilder("osascript","-e","tell application \"System Events\" to set frontmost of first process whose unix id is "+java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]+" to true").start();check(activation.waitFor()==0,"probe activation failed");
  Robot robot=new Robot();robot.setAutoDelay(90);robot.delay(400);edt(()->{p.requestFocusInWindow();return null;});robot.delay(200);robot.keyPress(KeyEvent.VK_ENTER);robot.keyRelease(KeyEvent.VK_ENTER);robot.waitForIdle();
  edt(()->{check(!p.getSudoku().isCandidate(12,2),"native Enter did not apply selected cross");check(p.getSudoku().isCandidate(22,3),"native Enter changed an unrelated candidate");p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);p.undo();check(p.getSudoku().isCandidate(12,2),"board undo failed");return null;});
  System.out.println("PASS actual native Enter selected P application; Backspace/modified Enter/read-only guard; unaffected candidate; board undo");
 }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
