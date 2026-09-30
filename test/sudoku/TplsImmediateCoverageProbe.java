package sudoku;
import java.awt.*;import java.awt.event.*;import java.awt.image.*;import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
public final class TplsImmediateCoverageProbe {
 static Map<Integer,Color> cells()throws Exception{return (Map<Integer,Color>)read("coloringMap");}
 static Map<Integer,Color> candidates()throws Exception{return (Map<Integer,Color>)read("coloringCandidateMap");}
 static java.util.List<DoodleStroke> ink()throws Exception{return (java.util.List<DoodleStroke>)read("doodleStrokes");}
 static void commit(){p.flushMappedClick();}
 public static void main(String[] args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{edt(()->{
  f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setSize(810,810);p.paint(new BufferedImage(810,810,1).getGraphics());Point a=ChainOriginProbe.candidate(0,1),b=ChainOriginProbe.candidate(12,1),blank=new Point(2,2);
  p.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);click(1,0,a);check(cells().containsKey(0),"T left cell waits");commit();click(1,InputEvent.SHIFT_DOWN_MASK,a);check(candidates().containsKey(1),"T direct candidate waits");
  click(3,0,a);check(!cells().containsKey(0)&&candidates().containsKey(1),"T right cell waits");click(3,0,a);check(cells().containsKey(0)&&!candidates().containsKey(1),"T double did not replace first local clear");
  p.clearColoringWithUndo();click(1,0,a);commit();click(1,InputEvent.SHIFT_DOWN_MASK,b);check(candidates().containsKey(121),"candidate fixture");click(3,0,blank);check(!candidates().containsKey(121)&&cells().containsKey(0),"T recent mixed chronology waits");click(3,0,blank);check(candidates().isEmpty()&&cells().isEmpty(),"T blank double did not clear two recent items");
  click(1,0,a);commit();click(3,0,a);event(MouseEvent.MOUSE_PRESSED,3,0,a);check(cells().containsKey(0),"T second press did not restore first clear");event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,b);check(read("coloringSweep")==null&&read("coloringPress")!=null,"T right double drag did not enter rectangle");p.handleEscapeVisualReset();check(cells().containsKey(0),"T cancelled rectangle retained first click erase");
  p.setAnnotationTool(AnnotationTool.DOODLE);click(1,0,a);check(ink().size()==1,"P circle waits");commit();click(1,0,a);check(ink().isEmpty(),"P circle removal waits");commit();click(1,InputEvent.SHIFT_DOWN_MASK,a);check(ink().size()==1&&ink().get(0).getCandidateMarkKind()==2,"P direct cross waits");click(3,0,a);check(ink().isEmpty(),"P right eraser waits");commit();
  p.setAnnotationTool(AnnotationTool.FREE_CHAIN);install(GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0),GroupedChainProbe.node(1,12)},true));boolean before=(Boolean)read("nextUserChainStrong");click(1,0,blank);check((Boolean)read("nextUserChainStrong")!=before,"L next strength waits");click(1,0,blank);check(!active().getStrongRelations().get(0),"L double blank failed last-edge switch");
  p.setAnnotationTool(AnnotationTool.BOX_SELECTION);p.getCellZoomPanel().selectPaletteGroup(0);click(1,0,a);check(p.getBoxReasoningGroupsSnapshot().get(0).contains(0)&&read("pendingMappedClick")==null,"S add waits");click(1,0,a);check(p.getBoxReasoningGroupsSnapshot().get(0).contains(0),"S repeated add became toggle");click(3,0,a);check(!p.getBoxReasoningGroupsSnapshot().get(0).contains(0)&&read("pendingMappedClick")==null,"S removal waits");
  System.out.println("PASS: T immediate add/local delete/mixed recent removal, exclusive double and rectangle rollback; P immediate circle toggle/cross/erase; L next/last strength; S immediate idempotent add/remove without double timer");return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
