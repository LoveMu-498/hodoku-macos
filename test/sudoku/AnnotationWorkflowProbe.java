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
  click(cell(0),3,0);check(p.getBoxReasoningFootprint().isEmpty(),"right remove all colors");click(cell(0),3,0);check(p.getBoxReasoningFootprint().contains(0),"right add");
  p.setAnnotationTool(AnnotationTool.DOODLE);click(cand(10,3),3,0);check(p.getDoodleStrokeCount()==1,"candidate circle missing");
  @SuppressWarnings("unchecked") java.util.List<DoodleStroke> strokes=(java.util.List<DoodleStroke>)read("doodleStrokes");check(strokes.get(0).getPoints().size()==49,"circle sample shape");call("undoDoodle");check(p.getDoodleStrokeCount()==0,"circle undo");call("redoDoodle");
  p.setAnnotationTool(AnnotationTool.FREE_CHAIN);UserChain c=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(3,9,10,11),GroupedChainProbe.node(3,22)},true);c.setActive(true);Field ac=SudokuPanel.class.getDeclaredField("activeUserChain");ac.setAccessible(true);ac.set(p,c);
  rectangle(cand(10,3),1,5);UserChain next=(UserChain)read("activeUserChain");check(next.getNodes().get(0).cells().length==2,"member removal");check(next.getStrongRelations().size()==1,"untouched edge lost");call("undoUserChains");next=(UserChain)read("activeUserChain");check(next.getNodes().get(0).cells().length==3,"member undo");
  Point a=cand(9,3),b=cand(10,3);rectangle(new Point((a.x+b.x)/2,a.y),1,2);check(((UserChain)read("activeUserChain")).getNodes().get(0).cells().length==3,"band-only changed members");
  p.showUserChainValidation(UserChainValidator.validate(p.getSudoku(),next));ImageIO.write(paint(),"png",new File("/tmp/hodoku-annotation-build/workflow-"+(args.length>0?args[0]:"light")+".png"));
  check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"annotation mutated Sudoku");
 }catch(Exception e){throw new RuntimeException(e);}finally{if(f!=null)f.dispose();}});System.out.println("Unified coloring, typed erase, box toggle, candidate circle, member cuts and rendering passed");System.exit(0);}
}
