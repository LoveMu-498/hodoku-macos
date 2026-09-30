package sudoku;
import java.awt.event.*;
import java.lang.reflect.Method;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;
/** Applies a displayed composite from a palette with no source, through the real keyboard transaction. */
public final class DoodleCompositionApplyProbe {
 public static void main(String[]args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{edt(()->{
  initialize();mark(12,2,2,0);mark(12,2,2,1);mark(22,3,2,0);
  p.getCellZoomPanel().selectPaletteGroup(2);String before=TechniqueStepCatalog.createSignature(p.getSudoku());
  outline(12,2);check(find(12,2,2,0).isConclusionOutlined()&&find(12,2,2,0).getConclusionSourceMask()==3,"visible composite not selectable from another palette");
  check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"selection changed board");
  p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,KeyEvent.VK_ENTER,'\n'));
  check(!p.getSudoku().isCandidate(12,2)&&p.getSudoku().getValue(12)==0,"composite cross did not apply");
  check(p.getSudoku().isCandidate(22,3),"unselected candidate changed");
  String after=TechniqueStepCatalog.createSignature(p.getSudoku());
  p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,KeyEvent.VK_ENTER,'\n'));
  check(after.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"repeat applied an absent result");
  p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);p.undo();check(p.getSudoku().isCandidate(12,2),"board undo did not restore candidate");
  p.setAnnotationTool(AnnotationTool.DOODLE);p.clearDoodlesWithUndo();
  mark(0,1,1,0);mark(0,1,1,1);mark(1,1,1,1);mark(1,1,1,2);
  p.getCellZoomPanel().selectPaletteGroup(3);outline(0,1);outline(1,1);
  check(find(0,1,1,0).isConclusionOutlined() && !find(1,1,1,1).isConclusionOutlined(),"compound peer true conflict bypassed existing guard");
  System.out.println("PASS: cross-palette compound hit, explicit Enter transaction, unaffected candidate, no-op repeat and board undo");return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
