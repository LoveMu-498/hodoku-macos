package sudoku;
import java.awt.*;
import java.awt.event.*;
import javax.swing.Timer;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
public final class AnnotationDispatchDeadlineProbe {
 static void input(int id,int button,int mods,Point pt,long when){p.dispatchEvent(new MouseEvent(p,id,when,mods,pt.x,pt.y,1,false,button));}
 public static void main(String[] args)throws Exception{
  System.setProperty("apple.awt.UIElement","true");
  try{edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setSize(810,810);p.setAnnotationTool(AnnotationTool.DOODLE);p.paint(new java.awt.image.BufferedImage(810,810,1).getGraphics());Point a=ChainOriginProbe.candidate(0,1);
   long first=System.currentTimeMillis()-150;input(MouseEvent.MOUSE_PRESSED,1,0,a,first);input(MouseEvent.MOUSE_RELEASED,1,0,a,first+10);
   int remaining=((Timer)read("mappedClickTimer")).getInitialDelay();check(remaining<=50,"EDT backlog added another wait: "+remaining);
   input(MouseEvent.MOUSE_PRESSED,1,0,a,first+170);input(MouseEvent.MOUSE_RELEASED,1,0,a,first+171);check(p.getDoodleStrokeCount()==1,"timestamp-valid double lost");
   p.clearDoodlesWithUndo();long now=System.currentTimeMillis();input(MouseEvent.MOUSE_PRESSED,3,0,a,now);input(MouseEvent.MOUSE_RELEASED,3,0,a,now+1);input(MouseEvent.MOUSE_PRESSED,3,0,a,now+150);input(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,new Point(a.x+100,a.y+100),now+160);check(read("doodleGesture").toString().equals("RECT_ERASER"),"right double-drag lost rectangle");p.handleEscapeVisualReset();
   DoodleStroke horizontal=new DoodleStroke(Color.BLUE,.002f);horizontal.getPoints().add(new DoodlePoint(.1,.5));horizontal.getPoints().add(new DoodlePoint(.9,.5));
   java.util.List<DoodleStroke> cut=DoodleGeometry.subtract(java.util.Collections.singletonList(horizontal),new Rectangle(400,400,100,100),900,900);check(cut.size()==2,"bounds culling skipped horizontal hit");
   DoodleStroke vertical=new DoodleStroke(Color.BLUE,.002f);vertical.getPoints().add(new DoodlePoint(.5,.1));vertical.getPoints().add(new DoodlePoint(.5,.9));check(DoodleGeometry.subtract(java.util.Collections.singletonList(vertical),new Rectangle(400,400,100,100),900,900).size()==2,"bounds culling skipped vertical hit");
   System.out.println("PASS: 150ms queued input leaves <=50ms wait; 170ms double accepted; second-right drag enters rectangle; horizontal/vertical erase preserved");return null;});}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
 }
}
