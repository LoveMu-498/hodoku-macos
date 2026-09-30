package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Real Swing listener and renderer checks; no native input injection required. */
public final class DoodleFeedbackPolishProbe {
 static Object invoke(String name,Class<?>[] types,Object... args)throws Exception{
  Method m=SudokuPanel.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(p,args);
 }
 static BufferedImage render(){BufferedImage im=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();return im;}
 static BufferedImage ink()throws Exception{BufferedImage im=new BufferedImage(810,810,BufferedImage.TYPE_INT_ARGB);Graphics2D g=im.createGraphics();invoke("drawDoodles",new Class[]{Graphics2D.class,int.class,int.class,boolean.class},g,810,810,false);g.dispose();return im;}
 static java.util.List<DoodleStroke> marks()throws Exception{return (java.util.List<DoodleStroke>)read("doodleStrokes");}
 static void mark(int c,int d,int kind)throws Exception{invoke("toggleCandidateHypothesisMark",new Class[]{int.class,int.class,int.class,int.class,Color.class},c,d,kind,p.getCellZoomPanel().getPaletteGroup(),Color.BLUE);}
 static int pixels(BufferedImage im){int n=0;for(int y=0;y<810;y++)for(int x=0;x<810;x++)if((im.getRGB(x,y)>>>24)>0)n++;return n;}
 static void key(int code,boolean down){KeyEvent e=new KeyEvent(p,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,System.currentTimeMillis(),down?InputEvent.ALT_DOWN_MASK:0,code,KeyEvent.CHAR_UNDEFINED);if(down)p.handleAnnotationKeyPressed(e);else p.handleAnnotationToolKeyReleased(e);}
 public static void main(String[] args)throws Exception{
  System.setProperty("apple.awt.UIElement","true");
  try{edt(()->{
   ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setSize(810,810);p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(0);render();
   String board=TechniqueStepCatalog.createSignature(p.getSudoku());Point a=ChainOriginProbe.candidate(0,1);
   click(1,0,a);check(marks().size()==1,"single did not apply immediately");check(pixels(ink())>20,"first click has no immediate mark");
   click(1,0,a);check(marks().size()==1&&marks().get(0).getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS,"double left stray circle");
   check(((Integer)invoke("mappedClickInterval",new Class[]{}))==200,"double window not 200");
   float scale=(Float)invoke("candidateMarkScale",new Class[]{DoodleStroke.class},marks().get(0));check(Math.abs(scale-.62)<.001,"cross scale");
   BufferedImage faded=ink();int max=0;for(int y=0;y<810;y++)for(int x=0;x<810;x++)max=Math.max(max,faded.getRGB(x,y)>>>24);check(max>80&&max<170,"cross not translucent: "+max);
   check((Boolean)invoke("fadeCurrentDoodleCross",new Class[]{int.class,int.class},0,1),"candidate not faded");
   set("lastMousePosition",new Point(1,1));key(KeyEvent.VK_ALT,true);render();
   check(read("doodleThoughtPreviewProjection")!=null,"cross-only group cannot preview");key(KeyEvent.VK_ALT,false);
   p.getCellZoomPanel().selectPaletteGroup(2);key(KeyEvent.VK_ALT,true);render();
   check((Integer)invoke("valueForThoughtPreviewCell",new Class[]{int.class},0)==0,"empty preview fabricated value");key(KeyEvent.VK_ALT,false);
   p.getCellZoomPanel().selectPaletteGroup(1);check(!(Boolean)invoke("fadeCurrentDoodleCross",new Class[]{int.class,int.class},0,1),"other group candidate faded");
   mark(10,2,DoodleStroke.MARK_TRUE_CIRCLE);p.getCellZoomPanel().selectPaletteGroup(0);mark(20,3,DoodleStroke.MARK_TRUE_CIRCLE);
   set("lastMousePosition",new Point(1,1));key(KeyEvent.VK_ALT,true);render();
   Object projection=read("doodleThoughtPreviewProjection");check(projection!=null,"preview requires circle hover");
   Field group=projection.getClass().getDeclaredField("group");group.setAccessible(true);check((Integer)group.get(projection)==0,"preview wrong group");
   Point other=ChainOriginProbe.candidate(10,2);invoke("updateDoodleThoughtPreviewHover",new Class[]{Point.class},other);render();
   projection=read("doodleThoughtPreviewProjection");check((Integer)group.get(projection)==0,"hover switched preview group");
   check(pixels(ink())>0,"preview hid the cross");check(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"display mutated candidates");
   javax.imageio.ImageIO.write(render(),"png",new java.io.File(System.getProperty("java.io.tmpdir"), "current-group-preview.png"));
   ApplicationAppearance.initialize(AppearanceMode.DARK);
   javax.imageio.ImageIO.write(render(),"png",new java.io.File(System.getProperty("java.io.tmpdir"), "current-group-preview-dark.png"));
   ApplicationAppearance.initialize(AppearanceMode.LIGHT);
   key(KeyEvent.VK_ALT,false);render();check(read("doodleThoughtPreviewProjection")==null,"preview stuck after release");
   Field state=ToolbarColorPalette.class.getDeclaredField("modeState");state.setAccessible(true);javax.swing.JButton stateButton=(javax.swing.JButton)state.get(p.getCellZoomPanel().getToolbarPalette());
   Cursor beforeErase=p.getCursor();event(MouseEvent.MOUSE_PRESSED,3,0,a);check(p.isDoodleErasing()&&stateButton.getToolTipText().contains("橡皮擦"),"held right shows brush state");
   event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,new Point(a.x+30,a.y+30));
   float radius=p.getDoodleEraserRadius();p.dispatchEvent(new MouseWheelEvent(p,MouseEvent.MOUSE_WHEEL,1000,InputEvent.BUTTON3_DOWN_MASK,a.x+30,a.y+30,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,1,1));
   check(p.getDoodleEraserRadius()>radius,"eraser wheel");check(stateButton.getToolTipText().contains(String.format(java.util.Locale.ROOT,"%.1f",p.getDoodleEraserRadius()*200)),"toolbar radius stale");
   p.handleEscapeVisualReset();check(p.getCursor().equals(beforeErase),"cancel kept eraser cursor");check(!p.isDoodleErasing()&&stateButton.getToolTipText().contains("粗细"),"cancel kept eraser state");
   p.clearDoodlesWithUndo();click(1,0,a);p.handleEscapeVisualReset();check(marks().isEmpty()&&pixels(ink())==0,"cancel kept pending glyph");
   System.out.println("PASS: immediate single with rollback snapshot, exclusive double, 200ms, smaller faded cross/candidate, current-group preview from blank, release/cancel, held eraser toolbar and wheel size");return null;
  });
  // Use event timestamps for the double-click boundary: Thread.sleep(170) can
  // resume after 200ms under desktop/EDT load and is not an input interval.
  edt(()->{p.setAnnotationTool(AnnotationTool.DOODLE);p.clearDoodlesWithUndo();Point pt=ChainOriginProbe.candidate(0,1);
   long start=System.currentTimeMillis();
   for(long time:new long[]{start,start+170}) {
    p.dispatchEvent(new MouseEvent(p,MouseEvent.MOUSE_PRESSED,time,0,pt.x,pt.y,1,false,MouseEvent.BUTTON1));
    p.dispatchEvent(new MouseEvent(p,MouseEvent.MOUSE_RELEASED,time+1,0,pt.x,pt.y,1,false,MouseEvent.BUTTON1));
   }
   check(marks().size()==1&&marks().get(0).getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS,"170ms input interval not accepted");return null;
  });
  Thread.sleep(240);edt(()->{check(marks().size()==1,"delayed circle after 170ms double");return null;});
  System.out.println("PASS: cross-only and empty-group preview; 170ms timestamped double remains exclusive");
  }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
 }
}
