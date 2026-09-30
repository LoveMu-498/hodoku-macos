package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import javax.swing.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Actual Swing listener coverage for continuous eraser wheels and toolbar help. */
public final class ContinuousEraserWheelProbe {
 static void wheel(double delta,long time){p.dispatchEvent(new MouseWheelEvent(p,MouseEvent.MOUSE_WHEEL,time,
  InputEvent.BUTTON3_DOWN_MASK,160,160,160,160,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,1,(int)delta,delta));}
 static void near(float actual,double expected){check(Math.abs(actual-expected)<.00001,"radius "+actual+" expected "+expected);}
 public static void main(String[] args)throws Exception{
  System.setProperty("apple.awt.UIElement","true");
  try {edt(()->{
   f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setSize(810,810);
   for(AnnotationTool tool:new AnnotationTool[]{AnnotationTool.DOODLE,AnnotationTool.CANDIDATE_COLORING}){
    p.setAnnotationTool(tool);p.getCellZoomPanel().selectPaletteGroup(0);set("doodleEraserRadius",.04f);
    event(MouseEvent.MOUSE_PRESSED,3,0,new Point(100,100));event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,new Point(160,160));
    for(int i=1;i<=12;i++){wheel(1,1000+i*10);near(p.getDoodleEraserRadius(),.04+i*.005);}
    wheel(-1,1130);near(p.getDoodleEraserRadius(),.095);
    wheel(.25,1140);wheel(.25,1150);near(p.getDoodleEraserRadius(),.0975);
    wheel(3,1160);near(p.getDoodleEraserRadius(),.1125);
    wheel(100,1170);near(p.getDoodleEraserRadius(),.20);wheel(-100,1180);near(p.getDoodleEraserRadius(),.006);
    wheel(1,1190);near(p.getDoodleEraserRadius(),.011);check(p.getCellZoomPanel().getPaletteGroup()==0,"eraser changed palette");
    if(tool==AnnotationTool.DOODLE){Field m=ToolbarColorPalette.class.getDeclaredField("modeState");m.setAccessible(true);check(((JButton)m.get(p.getCellZoomPanel().getToolbarPalette())).getToolTipText().contains("2.2%"),"toolbar size stale");}
    p.handleEscapeVisualReset();
   }
   Field buttons=MainFrame.class.getDeclaredField("annotationToolButtons");buttons.setAccessible(true);JToggleButton[] all=(JToggleButton[])buttons.get(f);
   for(AnnotationTool tool:new AnnotationTool[]{AnnotationTool.CANDIDATE_COLORING,AnnotationTool.DOODLE,AnnotationTool.FREE_CHAIN,AnnotationTool.BOX_SELECTION}){
    JToggleButton b=all[tool.ordinal()];check(b.getToolTipText().contains("Command + Z")&&b.getToolTipText().contains("<br>"),"incomplete tooltip "+tool);
    JToolTip tip=b.createToolTip();tip.setTipText(b.getToolTipText());Dimension d=tip.getPreferredSize();check(d.width<800&&d.height<650,"tooltip too large "+d);
   }
   p.setNextUserChainStrong(true);String strong=all[AnnotationTool.FREE_CHAIN.ordinal()].getToolTipText();p.setNextUserChainStrong(false);String weak=all[AnnotationTool.FREE_CHAIN.ordinal()].getToolTipText();
   check(!strong.equals(weak)&&weak.contains("Backspace")&&weak.contains("Command + Z"),"strength refresh lost help");
   System.out.println("PASS: continuous 10ms ticks, reversal, fractional and multi-notch deltas, limits/reversal at limit, T/P palette isolation, toolbar size, bounded tooltips and dynamic chain help");return null;
  });}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
 }
}
