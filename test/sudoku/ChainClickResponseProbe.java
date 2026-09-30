package sudoku;
import java.awt.*;import java.awt.image.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
public final class ChainClickResponseProbe {
 public static void main(String[] args)throws Exception{System.setProperty("apple.awt.UIElement","true");try{
  edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);p.setSize(810,810);p.paint(new BufferedImage(810,810,1).getGraphics());return null;});
  long start=System.nanoTime();edt(()->{long t=System.nanoTime();click(1,0,ChainOriginProbe.candidate(0,1));System.out.printf("release handler %.2fms; immediate nodes=%d%n",(System.nanoTime()-t)/1e6,active()==null?0:active().getNodes().size());return null;});
  while(edt(()->active()==null)&&System.nanoTime()-start<1000000000L)Thread.sleep(5);
  System.out.printf("first node visible in model after %.2fms%n",(System.nanoTime()-start)/1e6);
 }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
