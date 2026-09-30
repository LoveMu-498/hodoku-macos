package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
public final class ChainBlockedPreviewProbe {
 static Color tone(int relation)throws Exception{Method m=SudokuPanel.class.getDeclaredMethod("chainPreviewColor",SudokuAppearancePalette.class,int.class);m.setAccessible(true);return (Color)m.invoke(p,SudokuAppearancePalette.forRendering(false),relation);}
 static void picture(String name)throws Exception{BufferedImage im=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();javax.imageio.ImageIO.write(im,"png",new java.io.File(System.getProperty("java.io.tmpdir"), name+".png"));}
 public static void main(String[] args)throws Exception{
  System.setProperty("apple.awt.UIElement","true");
  try{edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setSize(810,810);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
   UserChain c=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0)});c.setActive(true);set("activeUserChain",c);for(int i=2;i<9;i++)p.getSudoku().delCandidate(i,1);
   String board=TechniqueStepCatalog.createSignature(p.getSudoku());p.setNextUserChainStrong(true);event(MouseEvent.MOUSE_PRESSED,2,0,new Point(100,100));picture("strong-mode-blocked");
   Color blocked=tone(1),strong=tone(2);check(blocked.getRed()>blocked.getGreen(),"blocked not red");Point target=ChainOriginProbe.candidate(9,1);click(1,0,target);check(c.getNodes().size()==1,"blocked weak target appended strong edge");
   p.setNextUserChainStrong(false);Color allowed=tone(1);check(!allowed.equals(blocked)&&tone(2).equals(strong),"strength toggle colors stale");picture("weak-mode-allowed");
   click(1,0,target);check(c.getNodes().size()==2&&!c.getStrongRelations().get(0),"switching strength did not allow weak append");check(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"preview modified puzzle");event(MouseEvent.MOUSE_RELEASED,2,0,target);
   System.out.println("PASS: strong-mode weak target red and rejected; switch to weak changes cue and permits connection; strong color stable; puzzle unchanged");return null;});}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
 }
}
