package sudoku;
import java.awt.*;import java.awt.image.*;import java.nio.file.*;import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;
/** Start overlays preserve original circle geometry and all four brush widths; contour replaces prior corner-box cue. */
public final class DoodleOutlineLegibilityProbe {
 static BufferedImage paint(){BufferedImage im=new BufferedImage(p.getWidth(),p.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();return im;}
 static boolean green(int rgb){Color c=new Color(rgb);return c.getGreen()>110&&c.getRed()<90&&c.getBlue()<120;}
 public static void main(String[]args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{edt(()->{
  initialize();Path output=Paths.get(System.getProperty("hodoku.probe.output","/tmp/hodoku-outline-legibility"));Files.createDirectories(output);
  java.lang.reflect.Method projected=SudokuPanel.class.getDeclaredMethod("projectedDoodlePath",DoodleStroke.class,int.class,int.class);projected.setAccessible(true);
  java.lang.reflect.Method width=SudokuPanel.class.getDeclaredMethod("doodleStrokePixelWidth",DoodleStroke.class,int.class,int.class);width.setAccessible(true);
  for(int size:new int[]{630,1134,1620}){
   float previous=0;
   for(int level=0;level<4;level++){
    p.clearDoodlesWithUndo();p.setSize(size,size);paint();while(p.getDoodleWidthIndex()!=level)p.cycleDoodleWidthByClick();
    mark(29,4,1,0);DoodleStroke source=find(29,4,1,0);
    float actual=(Float)width.invoke(p,source,size,size);check(actual>previous,"circle width did not increase: "+size+" level "+level);previous=actual;
    Shape path=(Shape)projected.invoke(p,source,size,size);Rectangle bounds=path.getBounds();
    BufferedImage start=paint();source.setHypothesisStart(false);BufferedImage ordinary=paint();source.setHypothesisStart(true);
    for(int y=bounds.y;y<=bounds.y+bounds.height;y++)for(int x=bounds.x;x<=bounds.x+bounds.width;x++)
     {
      Color original=new Color(ordinary.getRGB(x,y));
      if((ordinary.getRGB(x,y)&0xffffff)==0xff0000 || original.getRed()<125&&original.getGreen()<145&&original.getBlue()<170)
       check(start.getRGB(x,y)==ordinary.getRGB(x,y),"start cue changed original ink or numeral: "+size+" level "+level);
     }
    outline(29,4);BufferedImage selected=paint();ImageIO.write(selected,"png",output.resolve("selected-"+size+"-width-"+(level+1)+".png").toFile());
    System.out.println("PASS circle width "+(level+1)+" at "+size+": "+actual+" px; start preserves original pixels");
   }
  }return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
