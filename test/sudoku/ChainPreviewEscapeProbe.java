package sudoku;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;

/** Preview diagnostics are temporary; Escape preserves authored grouped geometry. */
public final class ChainPreviewEscapeProbe {
 static int redPixels(){p.setSize(810,810);BufferedImage image=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();p.paint(g);g.dispose();int count=0;for(int y=0;y<810;y++)for(int x=0;x<810;x++){Color c=new Color(image.getRGB(x,y));if(c.getRed()>160&&c.getRed()>c.getGreen()*1.5&&c.getRed()>c.getBlue()*1.3)count++;}return count;}
 public static void main(String[] args)throws Exception{try{
  edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
   UserChain c=GroupedChainProbe.chain(true,new UserChainNode[]{GroupedChainProbe.node(1,0,1),GroupedChainProbe.node(1,9,10),GroupedChainProbe.node(1,18,19)},true,false,true);c.setActive(true);ChainEditingProbe.set("activeUserChain",c);call("noteUserChainReasoningChanged");call("handleReasoningEnter");return null;});await(true);
  edt(()->{check(!((Set<?>)read("invalidUserChainRelations")).isEmpty(),"preview has no diagnostics");int before=redPixels();String text=p.copyChainText();p.handleEscapeVisualReset();check(p.getStep()==null,"preview remains");check(((Set<?>)read("invalidUserChainRelations")).isEmpty(),"diagnostics remain");check(redPixels()<before-20,"red overlay still drawn after Escape");check(p.currentReasoningChains().get(0).isClosed(),"Escape opened/deleted chain");check(p.getAnnotationTool()==AnnotationTool.FREE_CHAIN,"Escape exited chain tool");call("handleReasoningEnter");return null;});await(true);
  edt(()->{check(!((Set<?>)read("invalidUserChainRelations")).isEmpty(),"reanalysis failed to restore diagnostics");p.cancelReasoningFromUi();check(((Set<?>)read("invalidUserChainRelations")).isEmpty(),"cancel button differs from Escape");return null;});
  System.out.println("Grouped preview Escape pixels, retained chain/tool, reanalysis and cancel button passed");
 }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
