package sudoku;
import java.awt.*;import java.awt.event.*;import java.lang.reflect.*;import java.util.concurrent.*;import javax.swing.*;
public class AnnotationMenuKeyProbe {
 static MainFrame f;static SudokuPanel p;
 static <T>T edt(Callable<T> c)throws Exception{FutureTask<T> t=new FutureTask<T>(c);SwingUtilities.invokeAndWait(t);return t.get();}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static CurrentReasoningMenu menu()throws Exception{Field x=MainFrame.class.getDeclaredField("currentReasoningMenu");x.setAccessible(true);return (CurrentReasoningMenu)x.get(f);}
 static void tapT(long time)throws Exception{Method down=SudokuPanel.class.getDeclaredMethod("handleAnnotationToolKeyPressed",KeyEvent.class),up=SudokuPanel.class.getDeclaredMethod("handleAnnotationToolKeyReleased",KeyEvent.class);down.setAccessible(true);up.setAccessible(true);down.invoke(p,new KeyEvent(p,KeyEvent.KEY_PRESSED,time,0,KeyEvent.VK_T,'T'));up.invoke(p,new KeyEvent(p,KeyEvent.KEY_RELEASED,time+10,0,KeyEvent.VK_T,'T'));}
 static JRadioButton radio(Container c,String name){for(Component x:c.getComponents()){if(x instanceof JRadioButton&&name.equals(x.getName()))return (JRadioButton)x;if(x instanceof Container){JRadioButton r=radio((Container)x,name);if(r!=null)return r;}}return null;}
 public static void main(String[] args)throws Exception{try{
 edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku("7.8.495............34.5..7..5..7...13..8.6..96...9..3..7..8.42............543.1.6");p.setShowCandidates(true);f.setVisible(true);p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);p.setShowHintCellValue(2);
 long time=System.currentTimeMillis();tapT(time);check(p.getAnnotationTool()==AnnotationTool.CANDIDATE_COLORING,"T enter");tapT(time+1000);check(p.getAnnotationTool()==AnnotationTool.DEFAULT_MOUSE,"T exit");
 check(p.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS).isEmpty(),"Tab still traverses board");check(!new JTextField().getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS).isEmpty(),"input traversal lost");
 Field dispatcher=MainFrame.class.getDeclaredField("annotationKeyDispatcher");dispatcher.setAccessible(true);KeyEventDispatcher d=(KeyEventDispatcher)dispatcher.get(f);
 check(d.dispatchKeyEvent(new KeyEvent(f,KeyEvent.KEY_PRESSED,time+2000,0,KeyEvent.VK_TAB,'\t')),"frame Tab not handled before traversal");return null;});
 edt(()->{check(menu()!=null&&menu().isVisible(),"Tab menu");return null;});
 long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end&&!edt(()->menu().selector()!=null))Thread.sleep(30);
 edt(()->{check(menu().isQuick(),"Tab must be scoped");check(radio(menu().modeHeader(),"reasoningFilter0")==null,"Tab exposes all scope");menu().setVisible(false);f.showCurrentReasoning();check(!menu().isQuick(),"ShiftF12 must be full");check(radio(menu().modeHeader(),"reasoningFilter0").isSelected(),"full menu default");radio(menu().modeHeader(),"reasoningFilter3").doClick();return null;});
 edt(()->{check(menu().isVisible(),"filter closed popup");check(radio(menu().modeHeader(),"reasoningFilter3").isSelected(),"filter not selected");menu().setVisible(false);f.showCurrentReasoning();check(radio(menu().modeHeader(),"reasoningFilter0").isSelected(),"filter persists across openings");return null;});
 System.out.println("T toggle, board-only Tab entry, list filter and reopen default passed");
 }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
