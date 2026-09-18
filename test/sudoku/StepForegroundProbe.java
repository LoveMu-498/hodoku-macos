package sudoku;
import java.awt.*;import java.awt.geom.*;import java.awt.image.BufferedImage;import java.lang.reflect.*;import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;
public final class StepForegroundProbe {
 static BufferedImage render(String name)throws Exception {BufferedImage im=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();ImageIO.write(im,"png",new java.io.File("/tmp/hodoku-layer-fix/"+name+".png"));return im;}
 public static void main(String[] args)throws Exception{try{edt(()->{
  f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().setSudoku(new String(new char[81]).replace('\0','0'));p.setShowCandidates(true);p.setSize(810,810);
  int[] cells={9,63,55};String[] digits={"23","26","36"};for(int i=0;i<cells.length;i++)for(int d=1;d<=9;d++)if(!digits[i].contains(""+d))p.getSudoku().delCandidate(cells[i],d);
  SolutionStep step=new SolutionStep(SolutionType.XY_WING);for(int c:cells)step.addIndex(c);for(int d:new int[]{2,6,3})step.addValue(d);step.addCandidateToDelete(54,3);

  UserChain chain=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(3,9),GroupedChainProbe.node(3,54),GroupedChainProbe.node(3,55)},true,false);chain.setActive(true);
  Field a=SudokuPanel.class.getDeclaredField("activeUserChain");a.setAccessible(true);a.set(p,chain);p.setStep(step);p.setUserChainsVisible(false);BufferedImage clean=render("step-clean");p.setUserChainsVisible(true);
  BufferedImage annotated=render("step-with-chain");
  Method center=SudokuPanel.class.getDeclaredMethod("getCandKoord",int.class,int.class,int.class);center.setAccessible(true);
  int size=(Integer)read("cellSize");int compared=0;
  for(int c:new int[]{9,54,55}) {Point2D pos=(Point2D)center.invoke(p,c,3,size);int x=(int)Math.round(pos.getX()),y=(int)Math.round(pos.getY());for(int dx=-6;dx<=6;dx++)for(int dy=-6;dy<=6;dy++){check(clean.getRGB(x+dx,y+dy)==annotated.getRGB(x+dx,y+dy),"native hint covered at "+c);compared++;}}
  p.setStep(null);render("chain-restored");check(p.getStep()==null&&read("activeUserChain")==chain,"display altered annotations");System.out.println("XY-Wing r28c1,r7c2 -> r7c1<>3: "+compared+" foreground pixels unchanged under overlapping chain; clearing step preserves chain");return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
