package sudoku;
import java.awt.*;import java.awt.event.*;import java.awt.image.*;import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
public final class ImmediateMappedClickProbe {
 static String platformName=System.getProperty("os.name");
 // These queued input events describe a fast double click. Their input timestamps
 // must not include time spent processing the first handler on a busy CI runner.
 static long inputWhen=System.currentTimeMillis();
 static void event(int id,int button,int mods,Point pt){inputWhen+=10;p.dispatchEvent(new MouseEvent(p,id,inputWhen,mods,pt.x,pt.y,1,false,button));}
 static void click(int button,int mods,Point pt){event(MouseEvent.MOUSE_PRESSED,button,mods,pt);event(MouseEvent.MOUSE_RELEASED,button,mods,pt);}
 static java.util.List<DoodleStroke> ink()throws Exception{return (java.util.List<DoodleStroke>)read("doodleStrokes");}
 static int history(String n)throws Exception{return ((Stack<?>)read(n)).size();}
 static void mode(AnnotationTool t)throws Exception{p.flushMappedClick();p.setAnnotationTool(t);}
 public static void main(String[] args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{edt(()->{
  f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setSize(810,810);p.paint(new BufferedImage(810,810,1).getGraphics());Point a=ChainOriginProbe.candidate(0,1),b=ChainOriginProbe.candidate(12,1),c=ChainOriginProbe.candidate(24,1);
  // AWT has initialized for the real host. From here exercise macOS modifier
  // semantics only; this is functional Swing coverage, not native Mac input.
  System.setProperty("os.name","Mac OS X");
  mode(AnnotationTool.FREE_CHAIN);long t=System.nanoTime();click(1,0,a);check(active()!=null&&active().getNodes().size()==1,"first node delayed");click(1,0,b);check(active().getNodes().size()==2,"edge delayed");System.out.printf("Immediate node + edge %.2fms%n",(System.nanoTime()-t)/1e6);p.flushMappedClick();
  int undo=history("userChainUndoStack");click(1,0,c);check(active().getNodes().size()==3,"single not visible");click(1,0,c);check(active().getNodes().size()==2&&history("userChainUndoStack")==undo,"double left kept single edge/history");p.handleEscapeVisualReset();
  install(GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0),GroupedChainProbe.node(1,12)},true));undo=history("userChainUndoStack");click(3,0,a);check(active().getNodes().size()==1,"right delete delayed");click(3,0,a);check(active().getNodes().size()==2&&history("userChainUndoStack")==undo,"right precise left deletion/history");check((Integer)read("preciseChainCandidate")==1,"right precise endpoint missing");call("clearPreciseChainGesture");
  click(3,0,a);event(MouseEvent.MOUSE_PRESSED,3,0,a);event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,new Point(a.x+5,a.y+5));check(active().getNodes().size()==2,"second drag did not restore first delete");p.handleEscapeVisualReset();
  mode(AnnotationTool.CANDIDATE_COLORING);click(1,0,a);check(((Map<?,?>)read("coloringMap")).containsKey(0),"T cell delayed");click(1,0,a);check(!((Map<?,?>)read("coloringMap")).containsKey(0)&&((Map<?,?>)read("coloringCandidateMap")).containsKey(1),"T double leaked cell");p.undoCurrentAnnotation();check(((Map<?,?>)read("coloringCandidateMap")).isEmpty(),"T double undo");
  mode(AnnotationTool.DOODLE);click(1,0,a);check(ink().size()==1&&ink().get(0).getCandidateMarkKind()==1,"P circle delayed");click(1,0,a);check(ink().size()==1&&ink().get(0).getCandidateMarkKind()==2,"P double leaked circle");p.undoCurrentAnnotation();check(ink().isEmpty(),"P atomic undo");int redo=history("doodleRedoStack");click(1,0,a);p.handleEscapeVisualReset();check(ink().isEmpty()&&history("doodleRedoStack")==redo,"cancel lost prior redo");
  click(1,0,a);event(MouseEvent.MOUSE_PRESSED,1,0,a);event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON1_DOWN_MASK,new Point(a.x+100,a.y+60));check(ink().isEmpty()&&read("doodleGesture").toString().equals("ELLIPSE"),"double-drag circle leak");event(MouseEvent.MOUSE_RELEASED,1,0,new Point(a.x+100,a.y+60));check(ink().size()==1&&!ink().get(0).isStandardCandidateMark(),"ellipse commit");
  click(3,0,new Point(400,400));event(MouseEvent.MOUSE_PRESSED,3,0,new Point(400,400));event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,new Point(500,500));check(read("doodleGesture").toString().equals("RECT_ERASER"),"right double drag mode");p.handleEscapeVisualReset();
  mode(AnnotationTool.DOODLE);p.clearDoodlesWithUndo();click(1,0,a);p.cancelAnnotationToolInteractionOnDeactivation();check(ink().size()==1,"focus loss rolled back visible single");mode(AnnotationTool.CANDIDATE_COLORING);click(1,0,b);p.setAnnotationTool(AnnotationTool.DOODLE);check(((Map<?,?>)read("coloringMap")).containsKey(12),"tool switch rolled back visible single");
  System.out.println("PASS: immediate T/P/L, double rollback before precise/ellipse/rectangle, atomic undo and preserved redo on cancel");return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{System.setProperty("os.name",platformName);if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
