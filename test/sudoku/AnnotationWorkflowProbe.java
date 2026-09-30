package sudoku;
import java.awt.*;import java.awt.event.*;import java.awt.geom.*;import java.awt.image.*;import java.lang.reflect.*;import java.util.*;import javax.swing.*;import javax.imageio.ImageIO;import java.io.File;
/** Isolated panel-event tests for the unified annotation workflow. */
public final class AnnotationWorkflowProbe {
 static SudokuPanel p;static MainFrame f;
 static void check(boolean x,String m){if(!x)throw new AssertionError(m);}
 static Object read(String n)throws Exception{Field x=SudokuPanel.class.getDeclaredField(n);x.setAccessible(true);return x.get(p);}
 static void call(String n)throws Exception{Method m=SudokuPanel.class.getDeclaredMethod(n);m.setAccessible(true);m.invoke(p);}
 static Point cand(int c,int d)throws Exception{Method m=SudokuPanel.class.getDeclaredMethod("getCandKoord",int.class,int.class,int.class);m.setAccessible(true);Point2D a=(Point2D)m.invoke(p,c,d,(Integer)read("cellSize"));return new Point((int)Math.round(a.getX()),(int)Math.round(a.getY()));}
 static Point cell(int c)throws Exception{int z=(Integer)read("cellSize");return new Point(p.getX(c/9,c%9)+z/2,p.getY(c/9,c%9)+z/2);}
 static void event(int id,Point q,int button,int mods){MouseEvent e=new MouseEvent(p,id,System.currentTimeMillis(),mods,q.x,q.y,1,false,button);if(id==MouseEvent.MOUSE_DRAGGED){for(MouseMotionListener l:p.getMouseMotionListeners())l.mouseDragged(e);}else for(MouseListener l:p.getMouseListeners()){if(id==MouseEvent.MOUSE_PRESSED)l.mousePressed(e);else l.mouseReleased(e);}}
 static void click(Point q,int button,int mods){event(MouseEvent.MOUSE_PRESSED,q,button,mods);event(MouseEvent.MOUSE_RELEASED,q,button,mods);}
 static void rectangle(Point q,int button,int half){int mods=InputEvent.CTRL_DOWN_MASK;Point a=new Point(q.x-half,q.y-half),b=new Point(q.x+half,q.y+half);event(MouseEvent.MOUSE_PRESSED,a,button,mods);event(MouseEvent.MOUSE_DRAGGED,b,button,mods);event(MouseEvent.MOUSE_RELEASED,b,button,mods);}
 static BufferedImage paint(){BufferedImage im=new BufferedImage(720,720,1);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();return im;}
 public static void main(String[] args)throws Exception{SwingUtilities.invokeAndWait(()->{try{
  ApplicationAppearance.initialize(args.length>0&&args[0].equals("dark")?AppearanceMode.DARK:AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSize(720,720);p.setSudoku((String)null);p.getSudoku().setSudoku(new String(new char[81]).replace('\0','0'));p.setShowCandidates(true);paint();
  String before=TechniqueStepCatalog.createSignature(p.getSudoku());
  p.setAnnotationTool(AnnotationTool.CELL_COLORING);check(p.getAnnotationTool()==AnnotationTool.CANDIDATE_COLORING,"legacy coloring identity not unified");
  click(cell(0),1,0);click(cand(0,3),3,0);
  Map<?,?> cells=(Map<?,?>)read("coloringMap"),nums=(Map<?,?>)read("coloringCandidateMap");check(cells.size()==1&&nums.size()==1,"left/right coloring");
  rectangle(cell(0),1,5);check(cells.isEmpty()&&nums.size()==1,"cell-only erase");call("undoColoring");
  cells=(Map<?,?>)read("coloringMap");nums=(Map<?,?>)read("coloringCandidateMap");check(cells.size()==1,"erase undo");rectangle(cand(0,3),3,5);check(cells.size()==1&&nums.isEmpty(),"candidate-only erase");
  p.setAnnotationTool(AnnotationTool.BOX_SELECTION);
  @SuppressWarnings("unchecked") java.util.List<SudokuSet> groups=(java.util.List<SudokuSet>)read("boxReasoningGroups");groups.get(0).add(0);groups.get(1).add(0);
  click(cell(0),3,0);check(p.getBoxReasoningFootprint().contains(0),"right inspection preserves boxes");click(cell(0),1,0);check(p.getBoxReasoningFootprint().isEmpty(),"left removes all colors");click(cell(0),1,0);check(p.getBoxReasoningFootprint().contains(0),"left adds");
  p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(0);click(cand(10,3),3,0);check(p.getDoodleStrokeCount()==1,"candidate circle missing");
  @SuppressWarnings("unchecked") java.util.List<DoodleStroke> strokes=(java.util.List<DoodleStroke>)read("doodleStrokes");check(strokes.get(0).getPoints().size()==49&&strokes.get(0).getCandidateMarkKind()==DoodleStroke.MARK_TRUE_CIRCLE&&strokes.get(0).getThoughtGroup()==0,"circle semantics missing");
  click(cand(10,3),3,InputEvent.SHIFT_DOWN_MASK);check(strokes.size()==2&&strokes.get(1).getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS&&strokes.get(1).getPoints().size()==4,"Shift-right-click cross missing");
  click(cand(10,3),3,InputEvent.SHIFT_DOWN_MASK);check(strokes.size()==1,"repeated cross did not toggle off");
  click(cand(10,3),3,InputEvent.SHIFT_DOWN_MASK);check(strokes.size()==2,"circle and cross did not coexist");
  click(cand(10,3),3,0);check(strokes.size()==1&&strokes.get(0).getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS,"repeated circle did not toggle independently");
  int markGroup=p.getCellZoomPanel().getPaletteGroup();int otherGroup=(markGroup+1)%6;p.getCellZoomPanel().selectPaletteGroup(otherGroup);click(cand(10,3),3,0);
  check(strokes.size()==2&&strokes.get(1).getThoughtGroup()==otherGroup,"other thought group did not retain its mark");click(cand(10,3),3,0);
  check(strokes.size()==1&&strokes.get(0).getThoughtGroup()==markGroup,"other-group toggle changed the original mark");p.getCellZoomPanel().selectPaletteGroup(markGroup);
  p.setActiveCell(1,2);KeyEvent circleKey=new KeyEvent(p,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,KeyEvent.VK_7,'7');check(p.handleAnnotationKeyPressed(circleKey),"Doodle number shortcut not consumed");
  check(strokes.size()==2&&strokes.get(1).getAnchorCell()==11&&strokes.get(1).getAnchorDigit()==7&&strokes.get(1).getCandidateMarkKind()==DoodleStroke.MARK_TRUE_CIRCLE,"number key did not circle selected candidate");
  KeyEvent crossKey=new KeyEvent(p,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),InputEvent.SHIFT_DOWN_MASK,KeyEvent.VK_7,'7');check(p.handleAnnotationKeyPressed(crossKey),"Doodle shifted number shortcut not consumed");
  check(strokes.size()==3&&strokes.get(2).getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS,"Shift-number did not cross selected candidate");
  KeyEvent repeatedCrossKey=new KeyEvent(p,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),InputEvent.SHIFT_DOWN_MASK,KeyEvent.VK_7,'7');p.handleAnnotationKeyPressed(repeatedCrossKey);check(strokes.size()==2,"repeated keyboard cross did not toggle off");
  call("undoDoodle");check(strokes.size()==3,"candidate-mark toggle was not one undoable action");call("redoDoodle");check(strokes.size()==2,"candidate-mark redo failed");
  ImageIO.write(paint(),"png",new File("/tmp/hodoku-annotation-build/candidate-marks.png"));
  Point ellipseStart=cell(0),ellipseEnd=new Point(ellipseStart.x+40,ellipseStart.y+28);event(MouseEvent.MOUSE_PRESSED,ellipseStart,3,0);event(MouseEvent.MOUSE_DRAGGED,ellipseEnd,3,0);event(MouseEvent.MOUSE_RELEASED,ellipseEnd,3,0);
  check(strokes.size()==3&&!strokes.get(2).isCandidateAnchored(),"right-drag ellipse lost its freehand behavior");call("undoDoodle");check(strokes.size()==2,"free ellipse undo failed");
  check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"candidate marks changed Sudoku");
  p.setAnnotationTool(AnnotationTool.FREE_CHAIN);UserChain c=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(3,9,10,11),GroupedChainProbe.node(3,22)},true);c.setActive(true);Field ac=SudokuPanel.class.getDeclaredField("activeUserChain");ac.setAccessible(true);ac.set(p,c);
  rectangle(cand(10,3),1,5);UserChain next=(UserChain)read("activeUserChain");check(next.getNodes().get(0).cells().length==2,"member removal");check(next.getStrongRelations().size()==1,"untouched edge lost");call("undoUserChains");next=(UserChain)read("activeUserChain");check(next.getNodes().get(0).cells().length==3,"member undo");
  Point a=cand(9,3),b=cand(10,3);rectangle(new Point((a.x+b.x)/2,a.y),1,2);check(((UserChain)read("activeUserChain")).getNodes().get(0).cells().length==3,"band-only changed members");
  p.showUserChainValidation(UserChainValidator.validate(p.getSudoku(),next));ImageIO.write(paint(),"png",new File("/tmp/hodoku-annotation-build/workflow-"+(args.length>0?args[0]:"light")+".png"));
  check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"annotation mutated Sudoku");
 }catch(Exception e){throw new RuntimeException(e);}finally{if(f!=null)f.dispose();}});System.out.println("Unified coloring, typed erase, box toggle, candidate circle, member cuts and rendering passed");System.exit(0);}
}
