package sudoku;
import java.awt.*;import java.awt.image.BufferedImage;import java.nio.file.*;import java.lang.reflect.*;import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;
/** R2C4 regression: OR start identity never overpaints actual source segments. */
public final class DoodleOrthogonalUiProbe {
 static BufferedImage paint(){BufferedImage image=new BufferedImage(p.getWidth(),p.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();p.paint(g);g.dispose();return image;}
 static BufferedImage isolatedComposite(DoodleStroke base)throws Exception{
  BufferedImage image=new BufferedImage(p.getWidth(),p.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();
  g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  Method m=SudokuPanel.class.getDeclaredMethod("drawDoodleComposite",Graphics2D.class,DoodleStroke.class,java.util.List.class,int.class,int.class);m.setAccessible(true);m.invoke(p,g,base,ink(),image.getWidth(),image.getHeight());g.dispose();return image;
 }
 static void colored(int cell,int digit,int kind,int group,Color color)throws Exception{
  Method m=SudokuPanel.class.getDeclaredMethod("toggleCandidateHypothesisMark",int.class,int.class,int.class,int.class,Color.class);m.setAccessible(true);m.invoke(p,cell,digit,kind,group,color);
 }
 static boolean green(int rgb){Color c=new Color(rgb);return c.getGreen()>120&&c.getGreen()-c.getRed()>35&&c.getGreen()-c.getBlue()>25;}
 static boolean blueSelection(int rgb){Color c=new Color(rgb);return c.getRed()<120&&c.getGreen()>40&&c.getGreen()<175&&c.getBlue()>160;}
 static int count(BufferedImage im,Point q,Color color,int radius){int n=0;for(int y=q.y-radius;y<=q.y+radius;y++)for(int x=q.x-radius;x<=q.x+radius;x++)if(color.equals(new Color(0,215,45))?green(im.getRGB(x,y)):color.equals(new Color(0x1769ff))?blueSelection(im.getRGB(x,y)):(im.getRGB(x,y)&0xffffff)==(color.getRGB()&0xffffff))n++;return n;}
 public static void main(String[]args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{edt(()->{
  initialize();Path out=Paths.get(System.getProperty("hodoku.probe.output","/tmp/hodoku-orthogonal-ui"));Files.createDirectories(out);
  for(int size:new int[]{630,1134,1620})for(int kind:new int[]{1,2})for(int level=0;level<4;level++){
   p.clearDoodlesWithUndo();p.setSize(size,size);p.setDoodleWidthIndex(level);p.getCellZoomPanel().selectPaletteGroup(0);paint();
   colored(12,7,kind,0,Color.RED);colored(12,7,kind,1,Color.BLUE);
   p.getCellZoomPanel().selectPaletteGroup(2);colored(22,9,kind,2,Color.MAGENTA);p.toggleDoodleHypothesisEntry();colored(12,7,kind,2,Color.MAGENTA);p.finishDoodleHypothesisEntry();
   DoodleStroke start=find(12,7,kind,2);check(start.isHypothesisStart()&&start.getHypothesisGroupKind()==(kind==1?1:2),"fixture did not create homogeneous multi-start condition");
   Point q=ChainOriginProbe.candidate(12,7);int cell=(Integer)read("cellSize"),half=cell/6;BufferedImage before=paint();
   for(Color c:new Color[]{Color.RED,Color.BLUE,Color.MAGENTA})check(count(before,q,c,half+5)>2,"OR/start ink overpainted source color: size="+size+" width="+level+" kind="+kind+" color="+c);
   // Command selection remains available with the current OR/start identity.
   outline(12,7);BufferedImage selected=paint();
   check(ink().stream().anyMatch(m -> m.getAnchorCell()==12&&m.getAnchorDigit()==7&&m.isConclusionOutlined()),"Command selection missing");
   for(Color c:new Color[]{Color.RED,Color.BLUE,Color.MAGENTA,new Color(0,215,45),new Color(0x1769ff)})
    check(count(selected,q,c,half+5)>2,"orthogonal state color missing: "+c+" size="+size+" level="+level+" kind="+kind);
   int newlyBlue=0;for(int y=q.y-half-5;y<=q.y+half+5;y++)for(int x=q.x-half-5;x<=q.x+half+5;x++)
    if(blueSelection(selected.getRGB(x,y))&&!blueSelection(before.getRGB(x,y)))newlyBlue++;
   check(newlyBlue>2,"Command did not add distinguishable blue pixels");
   // Only start state is toggled; source colors and selection must remain pixel-identical within the ink.
   BufferedImage isolated=isolatedComposite(find(12,7,kind,0));start.setHypothesisStart(false);BufferedImage without=isolatedComposite(find(12,7,kind,0));start.setHypothesisStart(true);
   int greenChanged=0;for(int y=q.y-half-8;y<=q.y+half+8;y++)for(int x=q.x-half-8;x<=q.x+half+8;x++){
    if(green(isolated.getRGB(x,y))&&!green(without.getRGB(x,y))){greenChanged++;check(Math.abs(x-q.x)<=half+8&&Math.abs(y-q.y)<=half+8,"start contour exceeded close-fitting ink envelope");}
    for(Color c:new Color[]{Color.RED,Color.BLUE,Color.MAGENTA,new Color(0x1769ff)})
     if((without.getRGB(x,y)&0xffffff)==(c.getRGB()&0xffffff))check(isolated.getRGB(x,y)==without.getRGB(x,y),"start outline covered ink or selected blue");
   }
   check(greenChanged>2,"start state invisible");
   if(size==1134&&level==1)ImageIO.write(selected,"png",out.resolve("R2C4-"+(kind==1?"circle":"cross")+"-all-three-states.png").toFile());
   System.out.println("PASS three independent states size="+size+" width="+(level+1)+" kind="+kind);
  }return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
