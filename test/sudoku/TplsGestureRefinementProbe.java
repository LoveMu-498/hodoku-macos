package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import javax.swing.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
public final class TplsGestureRefinementProbe {
 static void key(int code,boolean down){KeyEvent e=new KeyEvent(p,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,System.currentTimeMillis(),code==KeyEvent.VK_ALT&&down?InputEvent.ALT_DOWN_MASK:0,code,KeyEvent.CHAR_UNDEFINED);if(down)p.handleAnnotationKeyPressed(e);else p.handleAnnotationToolKeyReleased(e);}
 static Object call(String n,Class<?>[] types,Object...v)throws Exception{Method m=SudokuPanel.class.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(p,v);}
 static void flush()throws Exception{call("flushMappedClick",new Class[]{});}
 static java.util.List<DoodleStroke> marks()throws Exception{return (java.util.List<DoodleStroke>)read("doodleStrokes");}
 static BufferedImage render(){BufferedImage im=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();return im;}
 static String status()throws Exception{Field x=MainFrame.class.getDeclaredField("statusLabelCellSelection");x.setAccessible(true);return ((JLabel)x.get(f)).getText();}
 public static void main(String[] args)throws Exception{
  System.setProperty("apple.awt.UIElement","true");
  try{edt(()->{
   ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setSize(810,810);p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(0);render();
   String board=TechniqueStepCatalog.createSignature(p.getSudoku());Point a=ChainOriginProbe.candidate(0,1);
   click(1,0,a);flush();check(marks().size()==1,"circle add");click(1,0,a);flush();check(marks().isEmpty(),"circle toggle off");p.undoCurrentAnnotation();check(marks().size()==1,"circle remove undo");p.clearDoodlesWithUndo();
   click(1,0,a);click(1,0,a);check(marks().size()==1&&marks().get(0).getCandidateMarkKind()==2,"cross add exclusive");
   click(1,0,a);click(1,0,a);check(marks().isEmpty(),"cross toggle off exclusive");p.undoCurrentAnnotation();check(marks().size()==1&&marks().get(0).getCandidateMarkKind()==2,"cross undo");p.clearDoodlesWithUndo();
   for(Point start:new Point[]{new Point(3,3),a}){
    click(1,0,start);event(MouseEvent.MOUSE_PRESSED,1,0,start);Point end=new Point(start.x+100,start.y+65);event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON1_DOWN_MASK,end);
    check(read("doodleGesture").toString().equals("ELLIPSE"),"double drag not ellipse");event(MouseEvent.MOUSE_RELEASED,1,0,end);check(marks().size()==1&&!marks().get(0).isStandardCandidateMark(),"ellipse left candidate or circle");p.undoCurrentAnnotation();check(marks().isEmpty(),"ellipse undo not atomic");
   }
   event(MouseEvent.MOUSE_PRESSED,1,InputEvent.SHIFT_DOWN_MASK,a);event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.SHIFT_DOWN_MASK|InputEvent.BUTTON1_DOWN_MASK,new Point(a.x+80,a.y+60));check(read("doodleGesture").toString().equals("ELLIPSE"),"Shift ellipse lost");p.handleEscapeVisualReset();check(marks().isEmpty(),"cancel ellipse");
   p.setAnnotationTool(AnnotationTool.BOX_SELECTION);p.getCellZoomPanel().selectPaletteGroup(0);Point center=new Point(p.getX(0,0)+20,p.getY(0,0)+20);click(1,0,center);String normal=status();key(KeyEvent.VK_ALT,true);check(p.getInspectedBoxGroup()==0&&p.getBoxReasoningCounts(0)[0]==1&&!status().equals(normal),"S option status: group="+p.getInspectedBoxGroup()+" count="+p.getBoxReasoningCounts(0)[0]+" before="+normal+" after="+status());
   event(MouseEvent.MOUSE_PRESSED,2,InputEvent.ALT_DOWN_MASK,center);key(KeyEvent.VK_ALT,false);check(p.getInspectedBoxGroup()==0,"mixed preview released early");event(MouseEvent.MOUSE_RELEASED,2,0,center);check(p.getInspectedBoxGroup()==-1&&status().equals(normal),"S release status");
   p.getCellZoomPanel().selectPaletteGroup(1);event(MouseEvent.MOUSE_PRESSED,2,0,center);check(p.getInspectedBoxGroup()==1&&p.getBoxReasoningCounts(1)[0]==0,"empty group preview");p.handleEscapeVisualReset();check(p.getInspectedBoxGroup()==-1,"S Escape stuck");
   p.setAnnotationTool(AnnotationTool.FREE_CHAIN);key(KeyEvent.VK_H,true);check(!(Boolean)read("chainRelationPreviewHeld"),"H still previews");key(KeyEvent.VK_H,false);
   key(KeyEvent.VK_ALT,true);check((Boolean)read("chainRelationPreviewHeld"),"Option preview lost");key(KeyEvent.VK_ALT,false);
   Color strong=(Color)call("chainPreviewColor",new Class[]{SudokuAppearancePalette.class,int.class},SudokuAppearancePalette.forRendering(false),2),weak=(Color)call("chainPreviewColor",new Class[]{SudokuAppearancePalette.class,int.class},SudokuAppearancePalette.forRendering(false),1);check(!strong.equals(weak)&&strong.getGreen()>strong.getRed(),"preview colors indistinguishable");
   install(GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0),GroupedChainProbe.node(1,12)},true));render();click(1,0,a);click(1,0,a);check((Integer)read("preciseChainCandidate")==1,"precise pick missing");
   javax.imageio.ImageIO.write(render(),"png",new java.io.File(System.getProperty("java.io.tmpdir"), "precise-green.png"));p.handleEscapeVisualReset();done().clear();UserChain previewChain=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0)});previewChain.setActive(true);set("activeUserChain",previewChain);for(int c=2;c<9;c++)p.getSudoku().delCandidate(c,1);key(KeyEvent.VK_ALT,true);check(p.chainPreviewRelation(new UserChainNode(1,1,Color.GREEN))==2 && p.chainPreviewRelation(new UserChainNode(9,1,Color.ORANGE))==1,"preview color fixture relations");javax.imageio.ImageIO.write(render(),"png",new java.io.File(System.getProperty("java.io.tmpdir"), "preview-colors.png"));key(KeyEvent.VK_ALT,false);for(int c=2;c<9;c++)p.getSudoku().setCandidate(c,1,true);
   check(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"annotation changed Sudoku");
   System.out.println("PASS: circle/cross toggles and undo, double-hold ellipse at blank/candidate, Shift ellipse/cancel, S status press/release/mixed/empty/Escape, H removed, Option retained, distinct relation colors, precise selection renderer");return null;
  });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
 }
}
