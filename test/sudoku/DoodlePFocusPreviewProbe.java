/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.*;import java.awt.image.BufferedImage;import java.lang.reflect.*;import java.nio.file.*;import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;
/** Real board rendering: M-normal source visibility, candidate restoration, and original-shape error preview. */
public final class DoodlePFocusPreviewProbe {
 static final int DENSITY=2; // Match the native Retina display; all anchors stay logical.
 static BufferedImage paint(){BufferedImage im=new BufferedImage(p.getWidth()*DENSITY,p.getHeight()*DENSITY,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();g.scale(DENSITY,DENSITY);p.paint(g);g.dispose();return im;}
 static int exact(BufferedImage im,Point q,int radius,Color color){int n=0;for(int y=(q.y-radius)*DENSITY;y<=(q.y+radius)*DENSITY;y++)for(int x=(q.x-radius)*DENSITY;x<=(q.x+radius)*DENSITY;x++)if((im.getRGB(x,y)&0xffffff)==(color.getRGB()&0xffffff))n++;return n;}
 static int sourceHue(BufferedImage im,Point q,int radius,Color color){int n=0;for(int y=(q.y-radius)*DENSITY;y<=(q.y+radius)*DENSITY;y++)for(int x=(q.x-radius)*DENSITY;x<=(q.x+radius)*DENSITY;x++){Color c=new Color(im.getRGB(x,y));if(Color.BLUE.equals(color)?c.getBlue()>c.getRed()+60&&c.getBlue()>c.getGreen()+60:c.getRed()>150&&c.getRed()>c.getGreen()+25&&c.getGreen()>c.getBlue()+40)n++;}return n;}
 static BufferedImage glyphOnly(){p.setDoodlesVisible(false);try{return paint();}finally{p.setDoodlesVisible(true);}}
 static BufferedImage mouseNormal(){AnnotationTool tool=p.getAnnotationTool();p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);try{return paint();}finally{p.setAnnotationTool(tool);}}
 static boolean sameRegion(BufferedImage a,BufferedImage b,Point q,int radius){for(int y=(q.y-radius)*DENSITY;y<=(q.y+radius)*DENSITY;y++)for(int x=(q.x-radius)*DENSITY;x<=(q.x+radius)*DENSITY;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))return false;return true;}
 static void sourceMatchesMouse(BufferedImage reference,BufferedImage actual,Point q,int radius,Color source){int n=0;for(int y=(q.y-radius)*DENSITY;y<=(q.y+radius)*DENSITY;y++)for(int x=(q.x-radius)*DENSITY;x<=(q.x+radius)*DENSITY;x++)if((reference.getRGB(x,y)&0xffffff)==(source.getRGB()&0xffffff)){n++;check(actual.getRGB(x,y)==reference.getRGB(x,y),"source core changed from M normal: "+source);}check(n>3,"M reference lacks visible source core: "+source);}
 static boolean red(int rgb){Color c=new Color(rgb);return c.getRed()>230&&c.getGreen()<45&&c.getBlue()<45;}
 public static void main(String[] args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{edt(()->{
  initialize();Path out=Paths.get(System.getProperty("hodoku.probe.output","/tmp/hodoku-p-focus"));Files.createDirectories(out);
  p.setSize(1134,1134);BufferedImage baseline=paint();
  DoodleOrthogonalUiProbe.colored(0,1,2,0,Color.ORANGE);DoodleOrthogonalUiProbe.colored(10,2,1,0,Color.ORANGE);
  Point cross=ChainOriginProbe.candidate(0,1),circle=ChainOriginProbe.candidate(10,2);BufferedImage own=paint(),ownGlyph=glyphOnly();
  Method fade=SudokuPanel.class.getDeclaredMethod("fadeCurrentDoodleCross",int.class,int.class);fade.setAccessible(true);
  check((Boolean)fade.invoke(p,0,1),"active single cross candidate did not retain its prior dimming");
  p.getCellZoomPanel().selectPaletteGroup(1);BufferedImage foreign=paint(),foreignGlyph=glyphOnly();
  check(!(Boolean)fade.invoke(p,0,1),"foreign cross continued dimming candidate");
  BufferedImage foreignMouse=mouseNormal();
  check(exact(own,circle,25,Color.ORANGE)>10&&exact(foreign,circle,25,Color.ORANGE)>10,"ordinary circle source lost its normal ink");
  for(Point q:new Point[]{cross,circle})check(sameRegion(foreignMouse,foreign,q,25),"noncurrent P source differs from actual M-normal rendering");
  check(sourceHue(foreign,circle,25,Color.ORANGE)>10,"foreign circle source became indistinguishable");
  int glyphPixels=0,visibleForeign=0,dimmedOwn=0;
  for(int y=(cross.y-12)*DENSITY;y<=(cross.y+12)*DENSITY;y++)for(int x=(cross.x-8)*DENSITY;x<=(cross.x+8)*DENSITY;x++){
   int rgb=baseline.getRGB(x,y);Color c=new Color(rgb);if(c.getRed()<125&&c.getGreen()<145&&c.getBlue()<170){
    glyphPixels++;
    check(foreignGlyph.getRGB(x,y)==rgb,"foreign mark still dims the underlying candidate");
    if(new Color(ownGlyph.getRGB(x,y)).getRed()>c.getRed()+40)dimmedOwn++;
    if(foreign.getRGB(x,y)==rgb)visibleForeign++;
   }
  }
  check(glyphPixels>10&&dimmedOwn*10>=glyphPixels*9,"current-only false candidate lost its actual glyph dimming");
  check(visibleForeign*3>=glyphPixels,"foreign cross leaves insufficient original numeral: "+visibleForeign+"/"+glyphPixels);
  DoodleOrthogonalUiProbe.colored(0,1,2,1,Color.BLUE);DoodleOrthogonalUiProbe.colored(10,2,1,1,Color.BLUE);BufferedImage shared=paint();
  check(!(Boolean)fade.invoke(p,0,1),"shared cross candidate was dimmed");
  BufferedImage sharedMouse=mouseNormal();
  for(Point q:new Point[]{cross,circle}){
   for(Color source:new Color[]{Color.ORANGE,Color.BLUE})sourceMatchesMouse(sharedMouse,shared,q,25,source);
  }
  p.getCellZoomPanel().selectPaletteGroup(0);BufferedImage sharedOrange=paint();
  for(Point q:new Point[]{cross,circle})for(Color source:new Color[]{Color.ORANGE,Color.BLUE})sourceMatchesMouse(sharedMouse,sharedOrange,q,25,source);
  p.getCellZoomPanel().selectPaletteGroup(2);BufferedImage sharedOther=paint();
  for(Point q:new Point[]{cross,circle}){
   check(sameRegion(sharedMouse,sharedOther,q,25),"third palette changed shared source rendering from M normal");
   for(Color source:new Color[]{Color.ORANGE,Color.BLUE})sourceMatchesMouse(sharedMouse,sharedOther,q,25,source);
  }
  call("beginDoodleThoughtPreview");BufferedImage heldShared=paint();
  for(Point sharedPoint:new Point[]{cross,circle})for(Color source:new Color[]{Color.ORANGE,Color.BLUE})
   check(exact(heldShared,sharedPoint,25,source)>3,"shared mark faded/overpainted during third-group held preview");
  call("clearDoodleThoughtPreview");
  // Opposite types stay independent; an active single cross still owns false-candidate dimming.
  DoodleOrthogonalUiProbe.colored(22,3,1,0,Color.ORANGE);DoodleOrthogonalUiProbe.colored(22,3,2,1,Color.BLUE);
  p.getCellZoomPanel().selectPaletteGroup(1);BufferedImage opposite=paint();Point q=ChainOriginProbe.candidate(22,3);
  check(p.getDoodleMarkSourceMask(22,3,1)==1&&p.getDoodleMarkSourceMask(22,3,2)==2&&(Boolean)fade.invoke(p,22,3),"opposite statuses were treated as shared");
  sourceMatchesMouse(mouseNormal(),opposite,q,25,Color.ORANGE);
  ImageIO.write(own,"png",out.resolve("current-group.png").toFile());ImageIO.write(foreign,"png",out.resolve("foreign-group.png").toFile());ImageIO.write(sharedOther,"png",out.resolve("shared-from-third-group.png").toFile());
  p.clearDoodlesWithUndo();p.getCellZoomPanel().selectPaletteGroup(0);
  DoodleOrthogonalUiProbe.colored(0,1,1,0,Color.ORANGE);DoodleOrthogonalUiProbe.colored(1,1,1,0,Color.ORANGE);
  DoodleOrthogonalUiProbe.colored(12,2,1,0,Color.ORANGE);DoodleOrthogonalUiProbe.colored(12,2,2,0,Color.ORANGE);
  String signature=TechniqueStepCatalog.createSignature(p.getSudoku());int undo=((java.util.Stack<?>)read("doodleUndoStack")).size();
  call("beginDoodleThoughtPreview");BufferedImage error=paint();
  Point a=ChainOriginProbe.candidate(0,1);int centerRed=0;for(int y=(a.y-2)*DENSITY;y<=(a.y+2)*DENSITY;y++)for(int x=(a.x-2)*DENSITY;x<=(a.x+2)*DENSITY;x++)if(red(error.getRGB(x,y)))centerRed++;
  check(centerRed==0,"circle conflict was replaced by a red cross");
  boolean[] sectors=new boolean[24];for(int y=(a.y-25)*DENSITY;y<=(a.y+25)*DENSITY;y++)for(int x=(a.x-25)*DENSITY;x<=(a.x+25)*DENSITY;x++)if(red(error.getRGB(x,y)))sectors[Math.min(23,(int)((Math.atan2(y-a.y*DENSITY,x-a.x*DENSITY)+Math.PI)/(2*Math.PI)*24))]=true;
  int coverage=0;for(boolean b:sectors)if(b)coverage++;check(coverage>=22,"conflict circle did not retain its full red ring");
  Point both=ChainOriginProbe.candidate(12,2);check(red(error.getRGB(both.x*DENSITY,both.y*DENSITY)),"conflicting original cross missing");
  check(signature.equals(TechniqueStepCatalog.createSignature(p.getSudoku()))&&undo==((java.util.Stack<?>)read("doodleUndoStack")).size(),"preview changed board/undo");
  for(DoodleStroke s:ink())check(s.getColor().equals(Color.ORANGE),"preview overwrote stored source color");
  ImageIO.write(error,"png",out.resolve("original-shape-red-conflicts.png").toFile());call("clearDoodleThoughtPreview");
  check(signature.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"preview release changed board");
  System.out.println("PASS M-normal foreign/shared sources, true-glyph restoration/dimming, held shared visibility, opposite-type separation, original-shape bright red conflicts, non-mutating preview");return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
