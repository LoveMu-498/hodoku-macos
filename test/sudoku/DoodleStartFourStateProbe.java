/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.*;import java.awt.image.BufferedImage;import java.nio.file.*;import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;
/** R5C6=1: teal source ink with no/start/Command/both states, rendered through the real board. */
public final class DoodleStartFourStateProbe {
 static final int DENSITY=2; // Actual Retina pixel density; geometry stays in logical coordinates.
 static BufferedImage paint(){BufferedImage im=new BufferedImage(p.getWidth()*DENSITY,p.getHeight()*DENSITY,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();g.scale(DENSITY,DENSITY);p.paint(g);g.dispose();return im;}
 static boolean green(int rgb){Color c=new Color(rgb);return c.getRed()<95&&c.getGreen()>180&&c.getBlue()<130&&c.getGreen()-c.getBlue()>80;}
 static boolean blue(int rgb){Color c=new Color(rgb);return c.getRed()<120&&c.getGreen()>40&&c.getGreen()<175&&c.getBlue()>160&&c.getBlue()-c.getGreen()>45;}
 static int count(BufferedImage im,Point q,int radius,boolean start){int n=0;for(int y=(q.y-radius)*DENSITY;y<=(q.y+radius)*DENSITY;y++)for(int x=(q.x-radius)*DENSITY;x<=(q.x+radius)*DENSITY;x++)if(start?green(im.getRGB(x,y)):blue(im.getRGB(x,y)))n++;return n;}
 public static void main(String[]args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{edt(()->{
  initialize();Path output=Paths.get(System.getProperty("hodoku.probe.output","/tmp/hodoku-four-states"));Files.createDirectories(output);
  for(int size:new int[]{630,1134,1620})for(int kind:new int[]{1,2})for(int level=0;level<4;level++){
   p.clearDoodlesWithUndo();p.setSize(size,size);p.setDoodleWidthIndex(level);p.getCellZoomPanel().selectPaletteGroup(0);BufferedImage bare=paint();
   DoodleOrthogonalUiProbe.colored(41,1,kind,0,new Color(0,140,148));DoodleStroke ink=find(41,1,kind,0);
   Point q=ChainOriginProbe.candidate(41,1);int cell=(Integer)read("cellSize"),radius=cell/3;
   p.setDoodlesVisible(false);BufferedImage glyphOnly=paint();p.setDoodlesVisible(true);
   BufferedImage[] states=new BufferedImage[4];
   for(int state=0;state<4;state++){
    ink.setHypothesisStart((state&1)!=0);ink.setConclusionOutlined((state&2)!=0);states[state]=paint();
    int g=count(states[state],q,radius,true),b=count(states[state],q,radius,false);
    check((state&1)!=0?g>35:g==0,"R5C6 bright start visibility state="+state+" size="+size+" kind="+kind+" level="+level+" green="+g);
    check((state&2)!=0?b>3:b==0,"R5C6 blue selection visibility state="+state);
    // The complete circle attachment should remain visible around all directions, including border-facing arcs.
    if(kind==1&&(state&1)!=0){boolean[] sectors=new boolean[24];
     for(int y=(q.y-radius)*DENSITY;y<=(q.y+radius)*DENSITY;y++)for(int x=(q.x-radius)*DENSITY;x<=(q.x+radius)*DENSITY;x++)if(green(states[state].getRGB(x,y))){double angle=Math.atan2(y-q.y*DENSITY,x-q.x*DENSITY)+Math.PI;int bin=Math.min(23,(int)(angle/(2*Math.PI)*24));sectors[bin]=true;}
     int covered=0;for(boolean seen:sectors)if(seen)covered++;check(covered>=22,"clipped/incomplete start ring: "+covered+"/24 sectors");
    }
   }
   // Source ink stays unchanged. Numerals are measured against the unannotated board,
   // never against dark pixels belonging to the new neutral pen separator.
   for(int state=0;state<4;state++){
    int glyphPixels=0,visibleGlyphPixels=0;
    for(int y=(q.y-radius)*DENSITY;y<=(q.y+radius)*DENSITY;y++)for(int x=(q.x-radius)*DENSITY;x<=(q.x+radius)*DENSITY;x++){
     if((states[0].getRGB(x,y)&0xffffff)==0x008c94)
      check(states[0].getRGB(x,y)==states[state].getRGB(x,y),"state altered opaque source ink: size="+size+" kind="+kind+" level="+level+" state="+state);
     Color c=new Color(bare.getRGB(x,y));
     if(Math.abs(x-q.x*DENSITY)<cell*DENSITY/12&&Math.abs(y-q.y*DENSITY)<cell*DENSITY/9
       &&c.getRed()<125&&c.getGreen()<145&&c.getBlue()<170&&Math.abs(c.getRed()-c.getGreen())<40&&!(c.getBlue()>c.getGreen()+30)){
      glyphPixels++;
      if(states[state].getRGB(x,y)==glyphOnly.getRGB(x,y))visibleGlyphPixels++;
      if(kind==1)check(states[state].getRGB(x,y)==states[0].getRGB(x,y),"circle identity rim changed numeral");
     }
    }
    // A centered cross necessarily covers some numeral pixels; the agreed larger X and
    // identity rims must still leave a substantial part of the actual faded glyph intact.
    check(glyphPixels>8&&visibleGlyphPixels*3>=glyphPixels,"insufficient unobscured numeral: size="+size+" kind="+kind+" level="+level+" state="+state+" visible="+visibleGlyphPixels+"/"+glyphPixels);
   }
   // Render the four actual-board states side by side, at their native pixel scale.
   if(size==1134&&level==1){int crop=cell*DENSITY/2;BufferedImage sheet=new BufferedImage(crop*2*4,crop*2+30,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
    String[] labels={"None","Start","Command","Both"};g.setFont(new Font("Dialog",Font.PLAIN,14));
    for(int i=0;i<4;i++){g.drawImage(states[i],i*crop*2,30,(i+1)*crop*2,30+crop*2,q.x*DENSITY-crop,q.y*DENSITY-crop,q.x*DENSITY+crop,q.y*DENSITY+crop,null);g.setColor(Color.BLACK);g.drawString(labels[i],i*crop*2+8,20);}g.dispose();ImageIO.write(sheet,"png",output.resolve("R5C6-1-"+(kind==1?"circle":"cross")+"-four-states.png").toFile());
   }
   System.out.println("PASS R5C6 four states size="+size+" kind="+kind+" width="+(level+1));
  }return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
