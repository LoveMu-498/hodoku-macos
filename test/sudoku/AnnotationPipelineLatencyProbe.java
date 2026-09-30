package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
public final class AnnotationPipelineLatencyProbe {
 static final Map<String,java.util.List<Double>> times=new TreeMap<>();
 interface Op{void run()throws Exception;}
 static void measure(String name,Op op)throws Exception{long n=System.nanoTime();op.run();times.computeIfAbsent(name,k->new ArrayList<>()).add((System.nanoTime()-n)/1e6);}
 static BufferedImage im=new BufferedImage(900,900,BufferedImage.TYPE_INT_RGB);
 static void paint(){Graphics2D g=im.createGraphics();p.paint(g);g.dispose();}
 public static void main(String[] args)throws Exception{
  System.setProperty("apple.awt.UIElement","true");
  try{edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setSize(900,900);p.setAnnotationTool(AnnotationTool.DOODLE);paint();
   Point a=new Point(150,150),b=new Point(500,400);
   if(args.length>0)return null;
   for(int i=0;i<80;i++){
    measure("P-press",()->event(MouseEvent.MOUSE_PRESSED,1,0,a));measure("P-release",()->event(MouseEvent.MOUSE_RELEASED,1,0,a));
    measure("P-pending-paint",()->paint());measure("P-commit",()->p.flushMappedClick());measure("P-committed-paint",()->paint());
    click(3,0,a);event(MouseEvent.MOUSE_PRESSED,3,0,a);
    for(int j=0;j<12;j++){final Point pt=new Point(150+j*25,150+j*15);measure("P-double-right-drag",()->event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,pt));measure("P-rectangle-paint",()->paint());}
    measure("P-double-right-release",()->event(MouseEvent.MOUSE_RELEASED,3,0,b));
   }
   java.util.List<DoodleStroke> strokes=(java.util.List<DoodleStroke>)read("doodleStrokes");strokes.clear();
   for(int i=0;i<80;i++){DoodleStroke s=new DoodleStroke(Color.BLUE,.002f);for(int j=0;j<300;j++)s.getPoints().add(new DoodlePoint((j%100)/120.0,(i%40)/50.0+(j%7)/1000.0));strokes.add(s);}
   for(int i=0;i<20;i++)measure("P-dense-paint",()->paint());
   for(int i=0;i<20;i++)measure("P-dense-eraser-miss",()->DoodleGeometry.subtract(strokes,new java.awt.geom.Rectangle2D.Double(840,840,20,20),900,900));
   SolutionStep hint=new SolutionStep(SolutionType.LOCKED_CANDIDATES_2);hint.addIndex(0);hint.addIndex(1);hint.addValue(1);hint.addCandidateToDelete(9,1);p.setStep(hint);
   for(int i=0;i<20;i++)measure("P-dense-with-hint-paint",()->paint());
   strokes.clear();for(int i=0;i<30;i++)measure("P-hint-paint",()->paint());
   return null;});
   if(args.length>0){
    edt(()->{f.setVisible(true);p.setAnnotationTool(AnnotationTool.DOODLE);return null;});
    for(int i=0;i<300;i++){final int frame=i;long posted=System.nanoTime();edt(()->{
     times.computeIfAbsent("queue-wait",k->new ArrayList<>()).add((System.nanoTime()-posted)/1e6);
     Point a=new Point(150,150);
     if(frame%60==0){click(3,0,a);event(MouseEvent.MOUSE_PRESSED,3,0,a);}
     if(frame%60<50)measure("queue-drag-handler",()->event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,new Point(150+frame%60*3,160+frame%60)));
     if(frame%60==50)event(MouseEvent.MOUSE_RELEASED,3,0,new Point(300,200));
     return null;
    });Thread.sleep(16);}
   }
   for(Map.Entry<String,java.util.List<Double>> e:times.entrySet()){Collections.sort(e.getValue());int n=e.getValue().size();System.out.printf(java.util.Locale.ROOT,"%s n=%d p50=%.2fms p95=%.2fms max=%.2fms%n",e.getKey(),n,e.getValue().get(n/2),e.getValue().get((int)(n*.95)),e.getValue().get(n-1));}
  }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
 }
}
