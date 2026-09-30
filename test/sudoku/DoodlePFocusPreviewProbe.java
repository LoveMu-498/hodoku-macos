package sudoku;
import java.awt.*;import java.awt.image.BufferedImage;import java.lang.reflect.*;import java.nio.file.*;import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;
/** Real board rendering: palette focus opacity/candidate restoration and original-shape error preview. */
public final class DoodlePFocusPreviewProbe {
 static BufferedImage paint(){BufferedImage im=new BufferedImage(p.getWidth(),p.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();return im;}
 static int exact(BufferedImage im,Point q,int radius,Color color){int n=0;for(int y=q.y-radius;y<=q.y+radius;y++)for(int x=q.x-radius;x<=q.x+radius;x++)if((im.getRGB(x,y)&0xffffff)==(color.getRGB()&0xffffff))n++;return n;}
 static boolean red(int rgb){Color c=new Color(rgb);return c.getRed()>230&&c.getGreen()<45&&c.getBlue()<45;}
 public static void main(String[] args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{edt(()->{
  initialize();Path out=Paths.get(System.getProperty("hodoku.probe.output","/tmp/hodoku-p-focus"));Files.createDirectories(out);
  p.setSize(1134,1134);BufferedImage baseline=paint();
  DoodleOrthogonalUiProbe.colored(0,1,2,0,Color.ORANGE);DoodleOrthogonalUiProbe.colored(10,2,1,0,Color.ORANGE);
  Point cross=ChainOriginProbe.candidate(0,1),circle=ChainOriginProbe.candidate(10,2);BufferedImage own=paint();
  Method fade=SudokuPanel.class.getDeclaredMethod("fadeCurrentDoodleCross",int.class,int.class);fade.setAccessible(true);
  check((Boolean)fade.invoke(p,0,1),"active single cross candidate did not retain its prior dimming");
  p.getCellZoomPanel().selectPaletteGroup(1);BufferedImage foreign=paint();
  check(!(Boolean)fade.invoke(p,0,1),"foreign cross continued dimming candidate");
  check(exact(own,circle,25,Color.ORANGE)>10&&exact(foreign,circle,25,Color.ORANGE)==0,"foreign circle not faded");
  for(int y=cross.y-8;y<=cross.y+8;y++)for(int x=cross.x-6;x<=cross.x+6;x++){
   int rgb=baseline.getRGB(x,y);Color c=new Color(rgb);if(c.getRed()<125&&c.getGreen()<145&&c.getBlue()<170)
    check(Math.abs(new Color(foreign.getRGB(x,y)).getRed()-c.getRed())<55,"foreign cross left candidate too faded");
  }
  DoodleOrthogonalUiProbe.colored(0,1,2,1,Color.BLUE);DoodleOrthogonalUiProbe.colored(10,2,1,1,Color.BLUE);BufferedImage shared=paint();
  check(!(Boolean)fade.invoke(p,0,1),"shared cross candidate was dimmed");
  for(Point q:new Point[]{cross,circle})for(Color color:new Color[]{Color.ORANGE,Color.BLUE})check(exact(shared,q,25,color)>3,"shared mark lost clear source color");
  p.getCellZoomPanel().selectPaletteGroup(2);BufferedImage sharedOther=paint();
  for(Point q:new Point[]{cross,circle})for(Color color:new Color[]{Color.ORANGE,Color.BLUE})check(exact(sharedOther,q,25,color)>3,"shared mark dimmed from third palette");
  call("beginDoodleThoughtPreview");BufferedImage heldShared=paint();
  for(Point sharedPoint:new Point[]{cross,circle})for(Color source:new Color[]{Color.ORANGE,Color.BLUE})
   check(exact(heldShared,sharedPoint,25,source)>3,"shared mark faded/overpainted during third-group held preview");
  call("clearDoodleThoughtPreview");
  // Opposite types in different groups must not be composed or exempted from focus dimming.
  DoodleOrthogonalUiProbe.colored(22,3,1,0,Color.ORANGE);DoodleOrthogonalUiProbe.colored(22,3,2,1,Color.BLUE);
  p.getCellZoomPanel().selectPaletteGroup(1);BufferedImage opposite=paint();Point q=ChainOriginProbe.candidate(22,3);
  check(exact(opposite,q,25,Color.ORANGE)==0&&p.getDoodleMarkSourceMask(22,3,1)==1&&p.getDoodleMarkSourceMask(22,3,2)==2,"opposite statuses were treated as shared");
  ImageIO.write(own,"png",out.resolve("current-group.png").toFile());ImageIO.write(foreign,"png",out.resolve("foreign-group.png").toFile());ImageIO.write(sharedOther,"png",out.resolve("shared-from-third-group.png").toFile());
  p.clearDoodlesWithUndo();p.getCellZoomPanel().selectPaletteGroup(0);
  DoodleOrthogonalUiProbe.colored(0,1,1,0,Color.ORANGE);DoodleOrthogonalUiProbe.colored(1,1,1,0,Color.ORANGE);
  DoodleOrthogonalUiProbe.colored(12,2,1,0,Color.ORANGE);DoodleOrthogonalUiProbe.colored(12,2,2,0,Color.ORANGE);
  String signature=TechniqueStepCatalog.createSignature(p.getSudoku());int undo=((java.util.Stack<?>)read("doodleUndoStack")).size();
  call("beginDoodleThoughtPreview");BufferedImage error=paint();
  Point a=ChainOriginProbe.candidate(0,1);int centerRed=0;for(int y=a.y-2;y<=a.y+2;y++)for(int x=a.x-2;x<=a.x+2;x++)if(red(error.getRGB(x,y)))centerRed++;
  check(centerRed==0,"circle conflict was replaced by a red cross");
  boolean[] sectors=new boolean[24];for(int y=a.y-25;y<=a.y+25;y++)for(int x=a.x-25;x<=a.x+25;x++)if(red(error.getRGB(x,y)))sectors[Math.min(23,(int)((Math.atan2(y-a.y,x-a.x)+Math.PI)/(2*Math.PI)*24))]=true;
  int coverage=0;for(boolean b:sectors)if(b)coverage++;check(coverage>=22,"conflict circle did not retain its full red ring");
  Point both=ChainOriginProbe.candidate(12,2);check(red(error.getRGB(both.x,both.y)),"conflicting original cross missing");
  check(signature.equals(TechniqueStepCatalog.createSignature(p.getSudoku()))&&undo==((java.util.Stack<?>)read("doodleUndoStack")).size(),"preview changed board/undo");
  for(DoodleStroke s:ink())check(s.getColor().equals(Color.ORANGE),"preview overwrote stored source color");
  ImageIO.write(error,"png",out.resolve("original-shape-red-conflicts.png").toFile());call("clearDoodleThoughtPreview");
  check(signature.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"preview release changed board");
  System.out.println("PASS current/foreign focus, restored candidate, shared same-type exemption, opposite-type separation, original-shape bright red conflicts, non-mutating preview");return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
