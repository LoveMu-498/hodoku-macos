package sudoku;
import java.awt.*;import java.awt.event.*;import java.lang.reflect.*;
import static sudoku.AnnotationMenuKeyProbe.*;
public final class DisplayedStepEnterProbe {
 public static void main(String[] args)throws Exception{try{edt(()->{
 f=new MainFrame(null);p=f.getSudokuPanel();f.setVisible(true);
 Field field=MainFrame.class.getDeclaredField("annotationKeyDispatcher");field.setAccessible(true);KeyEventDispatcher dispatcher=(KeyEventDispatcher)field.get(f);
 Method hint=MainFrame.class.getDeclaredMethod("getHint",int.class);hint.setAccessible(true);
 for(boolean selected:new boolean[]{false,true})for(boolean frameFocus:new boolean[]{false,true}) {
 p.setSudoku(CurrentReasoningProbe.PUZZLE);p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
 if(selected){SolutionStep chosen=p.getNextStep(false);Field type=MainFrame.class.getDeclaredField("selectedHintTechnique"),step=MainFrame.class.getDeclaredField("selectedHintStep");type.setAccessible(true);step.setAccessible(true);type.set(f,chosen.getType());step.set(f,chosen);}
 String before=TechniqueStepCatalog.createSignature(p.getSudoku());hint.invoke(f,2);check(p.getStep()!=null,"F12 missing display");check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"F12 mutated board");
 Component source=frameFocus?f:p;long time=System.currentTimeMillis();
 check(dispatcher.dispatchKeyEvent(new KeyEvent(source,KeyEvent.KEY_PRESSED,time,0,KeyEvent.VK_ENTER,'\n')),"Enter not consumed");
 check(!before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"Enter failed to apply displayed step");check(p.getStep()==null,"display not cleared");
 String after=TechniqueStepCatalog.createSignature(p.getSudoku());dispatcher.dispatchKeyEvent(new KeyEvent(source,KeyEvent.KEY_PRESSED,time+1,0,KeyEvent.VK_ENTER,'\n'));check(after.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"held Enter applied twice");dispatcher.dispatchKeyEvent(new KeyEvent(source,KeyEvent.KEY_RELEASED,time+2,0,KeyEvent.VK_ENTER,'\n'));
 p.undo();check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"undo failed");
 }
 return null;});System.out.println("Default/selected F12 -> Enter, board/frame focus, repeat suppression and undo passed");}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
