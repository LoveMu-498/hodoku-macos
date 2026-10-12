/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Set;
import javax.imageio.ImageIO;
import java.io.File;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;
import static sudoku.ChainEditingProbe.*;
/** Held preview edits and peer intersection against the live candidate board. */
public final class DoodlePreviewEditingProbe {
 @SuppressWarnings("unchecked") static Set<Integer> peers() throws Exception {
  Object projection=read("doodleThoughtPreviewProjection");Field field=projection.getClass().getDeclaredField("peerCandidates");field.setAccessible(true);return (Set<Integer>)field.get(projection);
 }
 static BufferedImage paint(){BufferedImage image=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=image.createGraphics();p.paint(g);g.dispose();return image;}
 public static void main(String[] args)throws Exception {try {edt(()->{
  initialize();mark(0,1,1,0);mark(1,1,1,0);
  for(DoodleStroke m:ink()){m.setHypothesisStart(true);m.setHypothesisConditionId(17);m.setHypothesisGroupKind(1);}
  String before=TechniqueStepCatalog.createSignature(p.getSudoku());call("beginDoodleThoughtPreview");paint();
  check(peers().contains(21),"OR start common row peer missing");
  check(!peers().contains(271),"peer seen by only one OR member was tinted");
  check(!peers().contains(11)&&!peers().contains(1)&&!peers().contains(22),"origin or other digit tinted");
  click(1,0,ChainOriginProbe.candidate(40,4));p.flushMappedClick();check(find(40,4,1,0)!=null,"held preview swallowed single click");
  mappedDouble(1,0,ChainOriginProbe.candidate(50,5));p.flushMappedClick();check(find(50,5,2,0)!=null,"held preview swallowed double click");
  check((Boolean)read("doodleThoughtPreviewHeld"),"edit ended held preview");
  BufferedImage image=paint();check(peers().contains(314),"new circle did not refresh peer tint");
  check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"annotation preview changed board");
  File out=new File(System.getProperty("hodoku.probe.output","/tmp/hodoku-preview-editing"));out.mkdirs();ImageIO.write(image,"png",new File(out,"preview-editing.png"));
  call("clearDoodleThoughtPreview");paint();check(read("doodleThoughtPreviewProjection")==null,"release retained tint projection");
  p.clearDoodlesWithUndo();p.getCellZoomPanel().selectPaletteGroup(0);
  mark(10,1,1,0); // R2C2=1 must see R6C2 candidate 1.
  java.awt.Point target=ChainOriginProbe.candidate(46,1);
  call("beginDoodleThoughtPreview");BufferedImage example=paint();
  check(peers().contains(461),"R2C2 circle did not see R6C2 candidate 1");
  int orange=0;
  for(int y=target.y-9;y<=target.y+9;y++)for(int x=target.x-9;x<=target.x+9;x++){
   java.awt.Color color=new java.awt.Color(example.getRGB(x,y));
   if(color.getRed()>color.getGreen()+25 && color.getGreen()>color.getBlue()+20)orange++;
  }
  check(orange>3,"R6C2 candidate 1 was not rendered translucent orange");
  ImageIO.write(example,"png",new File(out,"r2c2-sees-r6c2.png"));
  call("clearDoodleThoughtPreview");paint();
  System.out.println("PASS: held-preview singles/doubles, refreshed peer tint, OR common-peer intersection, board unchanged, release clears tint");return null;
 });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
