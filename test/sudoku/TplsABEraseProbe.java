package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
public final class TplsABEraseProbe {
 static Map<Integer,Color> cells()throws Exception{return (Map<Integer,Color>)read("coloringMap");}
 static void fixture()throws Exception{p.clearColoringWithUndo();for(int c:new int[]{0,40,13})p.handleColoring(c/9,c%9,-1,Color.RED);for(int c:new int[]{40,13})p.handleColoring(c/9,c%9,5,Color.BLUE);}
 static BufferedImage overlay(String method)throws Exception {
  BufferedImage im=new BufferedImage(810,810,BufferedImage.TYPE_INT_ARGB);Graphics2D g=im.createGraphics();
  Method m=SudokuPanel.class.getDeclaredMethod(method,Graphics2D.class);m.setAccessible(true);m.invoke(p,g);g.dispose();return im;
 }
 static boolean near(BufferedImage im,int x,int y){for(int a=x-3;a<=x+3;a++)for(int b=y-3;b<=y+3;b++)if((im.getRGB(a,b)>>>24)!=0)return true;return false;}
 public static void main(String[] args)throws Exception {
  boolean variant=true;
  try{edt(()->{
   f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setSize(810,810);p.setShowCandidates(true);TplsInputMappingProbe.paintPanel();
   p.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);fixture();
   Point from=ChainOriginProbe.candidate(0,5),to=ChainOriginProbe.candidate(80,5),blank=new Point(1,1);
   event(MouseEvent.MOUSE_PRESSED,1,0,from);BufferedImage cue=overlay("drawDeletionGesture");
   for(int y=0;y<810;y++)for(int x=0;x<810;x++)check((cue.getRGB(x,y)>>>24)==0,"click cue still rendered");
   p.handleEscapeVisualReset();
   drag(3,0,from,to);check(!cells().containsKey(40),"path did not erase diagonal cell");
   check(cells().containsKey(13)==variant,"A rectangle / B sweep distinction");
   Map<Integer,Color> candidateColors=(Map<Integer,Color>)read("coloringCandidateMap");
   check(!candidateColors.containsKey(405)&&candidateColors.containsKey(135)==variant,"mixed candidate sweep/rectangle hit");
   check(p.getSudoku().isCandidate(40,5)&&p.getSudoku().isCandidate(13,5),"eraser deleted Sudoku candidates");
   p.undoCurrentAnnotation();check(cells().size()==3,"one gesture must be one undo");
   if(variant){
    click(3,0,blank);check(cells().size()==3,"first blank click deleted before double drag");
    event(MouseEvent.MOUSE_PRESSED,3,0,blank);event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,new Point(809,809));
    event(MouseEvent.MOUSE_RELEASED,3,0,new Point(809,809));check(cells().isEmpty(),"B double-drag rectangle");
    p.undoCurrentAnnotation();check(cells().size()==3,"B double drag created extra transaction");
    event(MouseEvent.MOUSE_PRESSED,3,0,from);event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,to);
    p.handleEscapeVisualReset();event(MouseEvent.MOUSE_RELEASED,3,0,to);check(cells().size()==3,"cancel committed sweep");
   }
   p.setAnnotationTool(AnnotationTool.DOODLE);p.clearDoodlesWithUndo();
   Point inkA=new Point(150,300),inkB=new Point(450,300),rectA=new Point(100,100),rectB=new Point(500,500);
   drag(1,0,inkA,inkB);check(p.getDoodleStrokeCount()==1,"P fixture");
   drag(3,0,rectA,rectB);check(p.getDoodleStrokeCount()==(variant?2:0),"P right drag did not use correct A/B eraser");
   p.undoCurrentAnnotation();check(p.getDoodleStrokeCount()==1,"P eraser atomic undo");
   if(variant){
    Point onInk=new Point(250,300);
    click(3,0,onInk);check(p.getDoodleStrokeCount()==1,"P first click prematurely erased ink");
    event(MouseEvent.MOUSE_PRESSED,3,0,onInk);
    event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,new Point(500,500));
    check(read("doodleGesture").toString().equals("RECT_ERASER"),"P double second hold did not select rectangle");
    event(MouseEvent.MOUSE_RELEASED,3,0,new Point(500,500));p.undoCurrentAnnotation();
    check(p.getDoodleStrokeCount()==1,"P double rectangle not one transaction");
    java.lang.reflect.Field field=ToolbarColorPalette.class.getDeclaredField("freeEraser");field.setAccessible(true);
    check(!((javax.swing.JToggleButton)field.get(p.getCellZoomPanel().getToolbarPalette())).isVisible(),"B still presents toolbar eraser switch");
   }
   for(AnnotationTool tool:new AnnotationTool[]{AnnotationTool.CANDIDATE_COLORING,AnnotationTool.DOODLE}){
    p.setAnnotationTool(tool);Point start=new Point(100,100),end=new Point(300,300);
    event(MouseEvent.MOUSE_PRESSED,3,0,start);event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,end);
    float radius=p.getDoodleEraserRadius();int group=p.getCellZoomPanel().getPaletteGroup();
    BufferedImage ring=overlay("drawDeletionGesture");check(near(ring,300+(int)(radius*810),300),"sweep footprint missing");
    p.dispatchEvent(new MouseWheelEvent(p,MouseEvent.MOUSE_WHEEL,1000L*(tool.ordinal()+1),InputEvent.BUTTON3_DOWN_MASK,300,300,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,1,1));
    check(p.getDoodleEraserRadius()>radius,"sweep wheel did not resize");check(p.getCellZoomPanel().getPaletteGroup()==group,"sweep wheel changed color");
    ring=overlay("drawDeletionGesture");check(near(ring,300+(int)(p.getDoodleEraserRadius()*810),300),"resized footprint missing");
    event(MouseEvent.MOUSE_RELEASED,3,0,end);ring=overlay("drawDeletionGesture");
    check(!near(ring,300+(int)(p.getDoodleEraserRadius()*810),300),"released sweep left footprint");
   }
   for(AnnotationTool tool:new AnnotationTool[]{AnnotationTool.CANDIDATE_COLORING,AnnotationTool.DOODLE,AnnotationTool.FREE_CHAIN,AnnotationTool.BOX_SELECTION}){
    p.setAnnotationTool(tool);Point a=new Point(100,100),b=new Point(300,300);
    // Ctrl-left remains the same rectangular delete alias in both variants.
    event(MouseEvent.MOUSE_PRESSED,1,InputEvent.CTRL_DOWN_MASK,a);
    event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.CTRL_DOWN_MASK|InputEvent.BUTTON1_DOWN_MASK,b);
    BufferedImage im=overlay(tool==AnnotationTool.BOX_SELECTION?"drawBoxReasoningDragRubberBand":"drawDeletionGesture");
    check(near(im,150,150)&&near(im,150,250),tool+" missing a deletion diagonal");p.handleEscapeVisualReset();
   }
   System.out.println("PASS "+(variant?"B":"A")+": no click circle, four deletion rectangles crossed, sweep/rectangle distinction, double-drag no first deletion, atomic undo/cancel");return null;
  });
  if(variant){
   edt(()->{p.setAnnotationTool(AnnotationTool.DOODLE);p.clearDoodlesWithUndo();f.setVisible(true);f.toFront();f.validate();p.requestFocusInWindow();return null;});
   Robot robot=new Robot();robot.setAutoDelay(25);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);robot.delay(150);robot.waitForIdle();
   Point origin=edt(()->p.getLocationOnScreen());
   robot.mouseMove(origin.x+150,origin.y+300);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
   robot.mouseMove(origin.x+450,origin.y+300);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();
   edt(()->{check(p.getDoodleStrokeCount()==1,"native P pen fixture");return null;});
   robot.mouseMove(origin.x+100,origin.y+100);robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
   robot.mouseMove(origin.x+500,origin.y+500);robot.waitForIdle();
   edt(()->{p.addMouseWheelListener(e->System.out.println("native wheel delta="+e.getPreciseWheelRotation()+" radius="+p.getDoodleEraserRadius()));return null;});
   float nativeRadius=edt(()->p.getDoodleEraserRadius());int nativeGroup=edt(()->p.getCellZoomPanel().getPaletteGroup());
   robot.mouseWheel(10);robot.delay(150);robot.waitForIdle();
   edt(()->{check(p.getDoodleEraserRadius()!=nativeRadius,"native held-right wheel radius: before="+nativeRadius+" after="+p.getDoodleEraserRadius());check(p.getCellZoomPanel().getPaletteGroup()==nativeGroup,"native held-right wheel changed color");
    BufferedImage image=new BufferedImage(f.getWidth(),f.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();f.paint(g);g.dispose();
    javax.imageio.ImageIO.write(image,"png",new java.io.File(System.getProperty("java.io.tmpdir"), "sweep-radius.png"));return null;});
   robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);robot.waitForIdle();
   edt(()->{check(p.getDoodleStrokeCount()==2,"native P right drag is not sweep");p.undoCurrentAnnotation();return null;});
   robot.mouseMove(origin.x+250,origin.y+300);robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
   robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseMove(origin.x+500,origin.y+500);robot.waitForIdle();
   edt(()->{check(read("doodleGesture").toString().equals("RECT_ERASER"),"native P second-right hold is not rectangle");return null;});
   robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);robot.waitForIdle();
   System.out.println("PASS B native P: right sweep and double-right second hold rectangle without any toolbar interaction");
  }
  }catch(Throwable t){Robot cleanup=new Robot();cleanup.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);cleanup.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
 }
}
