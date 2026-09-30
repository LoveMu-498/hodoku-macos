package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

public final class ChainEndpointCueProbe {
 static Point at(int c,int d)throws Exception{return ChainOriginProbe.candidate(c,d);}
 static void reset()throws Exception{p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);ChainOriginProbe.render(null);}
 static void kind(ChainEndpointState.Kind k){check(p.chainEndpointState().kind==k,"state: "+p.chainEndpointState().kind+" expected "+k);}
 static int blue(BufferedImage im){int n=0;for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){Color c=new Color(im.getRGB(x,y));if(c.getBlue()>180&&c.getBlue()>c.getRed()+45&&c.getBlue()>c.getGreen()+25)n++;}return n;}
 public static void main(String[] args)throws Exception{try{edt(()->{
  ApplicationAppearance.initialize(args.length>0?AppearanceMode.DARK:AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();reset();kind(ChainEndpointState.Kind.EMPTY);
  click(1,0,at(10,3));kind(ChainEndpointState.Kind.SINGLE);check(p.chainEndpointState().start==null,"single has two endpoints");
  click(1,InputEvent.SHIFT_DOWN_MASK,at(11,3));click(1,0,at(40,3));click(1,0,at(60,3));
  kind(ChainEndpointState.Kind.OPEN);check(p.chainEndpointState().start.atoms().length==2&&p.chainEndpointState().end.contains(60,3),"group start/end");
  BufferedImage before=ChainOriginProbe.render("endpoints-open"+(args.length>0?"-dark":"-light"));int count=blue(before);
  event(MouseEvent.MOUSE_MOVED,0,0,at(20,5));check(blue(ChainOriginProbe.render(null))==count,"candidate hover added blue marker");
  p.getSudoku().delCandidate(20,5);event(MouseEvent.MOUSE_MOVED,0,0,at(20,5));check(blue(ChainOriginProbe.render(null))==count,"missing candidate hover added marker");
  p.getSudoku().setCell(20,5);event(MouseEvent.MOUSE_MOVED,0,0,at(20,5));BufferedImage solvedHover=ChainOriginProbe.render(null);check(ChainOriginProbe.bluePixels(solvedHover,at(60,3))==ChainOriginProbe.bluePixels(before,at(60,3)),"filled cell hover changed terminal");
  click(1,0,at(10,3));kind(ChainEndpointState.Kind.CLOSED);check(p.chainEndpointState().end==null,"closed loop claims continuation");ChainOriginProbe.render("endpoints-closed"+(args.length>0?"-dark":"-light"));
  call("removeLastUserChainNode");kind(ChainEndpointState.Kind.OPEN);check(p.chainEndpointState().end.contains(60,3),"open loop wrong terminal");
  Map<UserChain,Set<Integer>> cuts=new IdentityHashMap<>();cuts.put(active(),Collections.singleton(0));
  java.lang.reflect.Method delete=SudokuPanel.class.getDeclaredMethod("deleteExactChainEdges",Map.class);delete.setAccessible(true);delete.invoke(p,cuts);
  kind(ChainEndpointState.Kind.OPEN);check(p.chainEndpointState().start.contains(40,3),"cut did not remove isolated group endpoint");p.undoCurrentAnnotation();check(p.chainEndpointState().start.atoms().length==2,"undo lost group start");
  click(1,0,at(70,3));cuts.clear();cuts.put(active(),Collections.singleton(1));delete.invoke(p,cuts);kind(ChainEndpointState.Kind.DISCONNECTED);check(p.chainEndpointState().start==null&&p.chainEndpointState().end==null,"split still marks pair");ChainOriginProbe.render("endpoints-split");
  p.undoCurrentAnnotation();kind(ChainEndpointState.Kind.OPEN);p.redoCurrentAnnotation();kind(ChainEndpointState.Kind.DISCONNECTED);p.undoCurrentAnnotation();
  done().add(GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(3,40),GroupedChainProbe.node(3,75)},true));kind(ChainEndpointState.Kind.BRANCHED);check(p.currentChainOrigin()==null,"branched origin falsely shown");ChainOriginProbe.render("endpoints-branched");done().clear();kind(ChainEndpointState.Kind.OPEN);
  reset();click(1,0,at(10,1));click(1,0,at(20,1));p.finishCurrentUserChain();click(1,0,at(40,1));click(1,0,at(20,1));kind(ChainEndpointState.Kind.OPEN);check(p.chainEndpointState().start.contains(40,1)&&p.chainEndpointState().end.contains(10,1),"reconnected orientation wrong");
  Map<UserChain,Set<Integer>> flips=new IdentityHashMap<>();flips.put(active(),Collections.singleton(0));java.lang.reflect.Method flip=SudokuPanel.class.getDeclaredMethod("flipChainEdges",Map.class);flip.setAccessible(true);flip.invoke(p,flips);check(p.chainEndpointState().end.contains(10,1),"flip moved endpoint");
  System.out.println("Endpoint states/pixels: empty, single, grouped, pointer absence, open/closed, cut, branch, undo/redo, reconnect, flip passed");return null;
 });}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
