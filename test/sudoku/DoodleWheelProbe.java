package sudoku;
import java.awt.event.*;
import java.lang.reflect.Field;
import javax.swing.*;
public final class DoodleWheelProbe {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  AnnotationWheelGate gate=new AnnotationWheelGate();
  check(gate.step(.4,1000)==0 && gate.step(.5,1010)==0 && gate.step(.2,1020)==1,"fraction threshold");
  check(gate.step(8,1200)==0 && gate.step(8,1400)==0,"momentum latch");
  check(gate.step(-1,1800)==-1,"pause rearm");
  final Throwable[] failure={null};
  SwingUtilities.invokeAndWait(()->{MainFrame f=null;try{
   f=new MainFrame(null); SudokuPanel p=f.getSudokuPanel();CellZoomPanel z=p.getCellZoomPanel();
   p.setAnnotationTool(AnnotationTool.DOODLE);p.setDoodleWidthIndex(0);z.selectPaletteGroup(0);
   Field field=ToolbarColorPalette.class.getDeclaredField("modeState");field.setAccessible(true);
   JButton button=(JButton)field.get(z.getToolbarPalette());
   for(int i=1;i<=4;i++){button.doClick();check(p.getDoodleWidthIndex()==i%4,"click cycle");}
   wheel(p,0,1000);check(z.getPaletteGroup()==1 && p.getDoodleWidthIndex()==0,"default color wheel");
   wheel(p,InputEvent.META_DOWN_MASK,2000);check(p.getDoodleWidthIndex()==1 && z.getPaletteGroup()==1,"command width");
   wheel(p,InputEvent.META_DOWN_MASK,2010);check(p.getDoodleWidthIndex()==1,"width overshoot");
   wheel(p,InputEvent.META_DOWN_MASK,2400);check(p.getDoodleWidthIndex()==2,"width rearm");
   p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);check(!button.isEnabled(),"inactive click");
  }catch(Throwable t){failure[0]=t;}finally{if(f!=null)f.dispose();}});
  if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}
  System.out.println("PASS: fractional threshold, momentum latch, pause, four click levels, default palette, Command width, inactive mode");System.exit(0);
 }
 static void wheel(SudokuPanel p,int mod,long time){p.dispatchEvent(new MouseWheelEvent(p,MouseEvent.MOUSE_WHEEL,time,mod,30,30,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,1,1));}
}
