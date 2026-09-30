package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
public final class ProjectedDeletionAndBoxPriorityProbe {
 static Object call(String n,Class<?>[] types,Object...a)throws Exception{Method m=SudokuPanel.class.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(p,a);}
 static BufferedImage render(){BufferedImage im=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();return im;}
 static void mark(int c,int d,int kind,Color color)throws Exception{call("toggleCandidateHypothesisMark",new Class[]{int.class,int.class,int.class,int.class,Color.class},c,d,kind,p.getCellZoomPanel().getPaletteGroup(),color);}
 static boolean removed(int c,int d)throws Exception{return (Boolean)call("thoughtPreviewRemovesCandidate",new Class[]{int.class,int.class},c,d);}
 public static void main(String[] args)throws Exception{
  System.setProperty("apple.awt.UIElement","true");
  try{edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setSize(810,810);p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(2);render();
   mark(0,1,DoodleStroke.MARK_FALSE_CROSS,Color.BLUE);mark(10,2,DoodleStroke.MARK_TRUE_CIRCLE,Color.BLUE);
   String signature=TechniqueStepCatalog.createSignature(p.getSudoku());event(MouseEvent.MOUSE_PRESSED,2,0,new Point(100,100));render();
   check(removed(0,1)&&!removed(1,2),"explicit deletion or unwanted propagation");check((Integer)call("valueForThoughtPreviewCell",new Class[]{int.class},10)==2,"circle projection missing");
   Color actual=(Color)call("thoughtPreviewCircleColor",new Class[]{int.class,int.class},10,2);check(actual.equals(Options.getInstance().getColoringColors()[4]),"preview kept original blue instead of current group dark color");
   BufferedImage ink=new BufferedImage(810,810,BufferedImage.TYPE_INT_ARGB);Graphics2D g=ink.createGraphics();call("drawDoodles",new Class[]{Graphics2D.class,int.class,int.class,boolean.class},g,810,810,false);g.dispose();int pixels=0;for(int y=0;y<810;y++)for(int x=0;x<810;x++)if((ink.getRGB(x,y)>>>24)!=0)pixels++;check(pixels==0,"preview still draws valid circle/cross ink");
   BufferedImage projected=render();javax.imageio.ImageIO.write(projected,"png",new java.io.File(System.getProperty("java.io.tmpdir"), "preview-light.png"));
   Point digit=ChainOriginProbe.candidate(0,1);int dark=0;for(int y=digit.y-6;y<=digit.y+6;y++)for(int x=digit.x-5;x<=digit.x+5;x++){Color c=new Color(projected.getRGB(x,y));if(c.getRed()<130&&c.getGreen()<130&&c.getBlue()<130)dark++;}check(dark==0,"crossed candidate glyph still painted");
   ApplicationAppearance.initialize(AppearanceMode.DARK);javax.imageio.ImageIO.write(render(),"png",new java.io.File(System.getProperty("java.io.tmpdir"), "preview-dark.png"));ApplicationAppearance.initialize(AppearanceMode.LIGHT);
   event(MouseEvent.MOUSE_RELEASED,2,0,new Point(100,100));check(!removed(0,1)&&p.getSudoku().isCandidate(0,1),"release did not restore");check(signature.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"preview changed board");
   mark(0,1,DoodleStroke.MARK_TRUE_CIRCLE,Color.BLUE);event(MouseEvent.MOUSE_PRESSED,2,0,new Point(100,100));render();check(!removed(0,1)&&(Integer)call("valueForThoughtPreviewCell",new Class[]{int.class},0)==0,"conflicting assumption applied");event(MouseEvent.MOUSE_RELEASED,2,0,new Point(100,100));
   Sudoku2 board=new Sudoku2();board.setSudoku("489030251671285394253000678394000527865372149712000863030000412547123986028040735");
   solver.SudokuSolver nativeSolver=solver.SudokuSolverFactory.getDefaultSolverInstance();java.util.List<SolutionStep> steps=new ArrayList<>();steps.addAll(nativeSolver.getStepFinder().findAllLockedCandidates(board));steps.addAll(nativeSolver.getStepFinder().findAllNakedXle(board));steps.addAll(nativeSolver.getStepFinder().findAllHiddenXle(board));
   SudokuSet selected=new SudokuSet();selected.add(75);selected.add(77);NativeReasoningMatcher.Match match=NativeReasoningMatcher.matchBox(steps,board,selected);check(match!=null,"screenshot pair not matched");SolutionStep step=match.getStep();check(step.getType()==SolutionType.LOCKED_PAIR||step.getType()==SolutionType.NAKED_PAIR,"narrow claiming outranked pair: "+step.getType());Set<Integer> digits=new HashSet<>();for(Candidate c:step.getCandidatesToDelete())digits.add(c.getValue());check(digits.contains(6)&&digits.contains(9),"pair did not delete both 6 and 9");nativeSolver.doStep(board,step);for(Candidate c:step.getCandidatesToDelete())check(!board.isCandidate(c.getIndex(),c.getValue()),"native step missed deletion");
   System.out.println("PASS: projected candidate and cross disappear, deep group color, release restores, no propagation/mutation, conflicts preserved; reconstructed screenshot pair="+step.getType()+", deletes="+step.getCandidatesToDelete());return null;
  });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
 }
}
