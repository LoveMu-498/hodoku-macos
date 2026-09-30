package sudoku;
import java.awt.*;import java.awt.event.*;import java.awt.image.*;import java.lang.reflect.*;import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
public final class DoodleCommandPolarityProbe {
 static java.util.List<DoodleStroke> ink()throws Exception{return (java.util.List<DoodleStroke>)read("doodleStrokes");}
 static void mark(int c,int d,int k,int group)throws Exception{Method m=SudokuPanel.class.getDeclaredMethod("toggleCandidateHypothesisMark",int.class,int.class,int.class,int.class,Color.class);m.setAccessible(true);m.invoke(p,c,d,k,group,Color.BLUE);}
 static boolean outlined(int c,int d,int k,int group)throws Exception{for(DoodleStroke s:ink())if(s.getAnchorCell()==c&&s.getAnchorDigit()==d&&s.getCandidateMarkKind()==k&&s.getThoughtGroup()==group)return s.isConclusionOutlined();return false;}
 static void state(boolean circle,boolean cross)throws Exception{check(outlined(0,1,1,0)==circle&&outlined(0,1,2,0)==cross,"wrong selected polarity "+circle+"/"+cross);}
 public static void main(String[] args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{edt(()->{
  f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(0);p.setSize(810,810);p.paint(new BufferedImage(810,810,1).getGraphics());Point a=ChainOriginProbe.candidate(0,1);int cmd=InputEvent.META_DOWN_MASK;
  mark(0,1,1,0);mark(0,1,2,0);String board=TechniqueStepCatalog.createSignature(p.getSudoku());
  click(1,cmd,a);state(true,false);p.flushMappedClick();click(1,cmd,a);state(false,false);p.flushMappedClick();
  int before=((Stack<?>)read("doodleUndoStack")).size();click(1,cmd,a);state(true,false);click(1,cmd,a);state(false,true);check(((Stack<?>)read("doodleUndoStack")).size()==before+1,"double left extra undo");
  p.undoCurrentAnnotation();state(false,false);p.redoCurrentAnnotation();state(false,true);
  click(1,cmd,a);click(1,cmd,a);state(false,false);
  click(1,cmd,a);p.flushMappedClick();state(true,false);click(1,cmd,a);click(1,cmd,a);state(false,true);
  click(1,cmd,a);state(true,false);p.flushMappedClick();check(ink().size()==2,"switch deleted marks");
  mark(12,2,2,0);Point b=ChainOriginProbe.candidate(12,2);click(1,cmd,b);check(outlined(12,2,2,0),"cross-only single command regressed");click(1,cmd,b);check(!outlined(12,2,2,0),"cross-only cancellation");
  p.getCellZoomPanel().selectPaletteGroup(1);click(1,cmd,a);check(!outlined(0,1,1,1)&&ink().size()==3,"other group touched or new mark created");
  check(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"selection edited puzzle");p.getCellZoomPanel().selectPaletteGroup(0);click(1,cmd,a);click(1,cmd,a);state(false,true);
  Method apply=SudokuPanel.class.getDeclaredMethod("applyOutlinedDoodleConclusions");apply.setAccessible(true);apply.invoke(p);check(!p.getSudoku().isCandidate(0,1)&&p.getSudoku().getValue(0)==0,"selected cross failed to apply or applied circle instead");
  System.out.println("PASS: coexisting circle/cross Command single/double toggle and replacement, immediate first response, atomic undo/redo, singleton compatibility, group isolation, no Sudoku mutation");return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
