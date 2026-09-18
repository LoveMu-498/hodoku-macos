package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.beans.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class AnchoredDoodleProbe {
 static Object read(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static Object call(Object o,String n,Class<?>[] types,Object... args)throws Exception{Method m=o.getClass().getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(o,args);}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static BufferedImage paint(SudokuPanel p){BufferedImage im=new BufferedImage(p.getWidth(),p.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();return im;}
 static Point2D center(SudokuPanel p,int cell,int d)throws Exception{return (Point2D)call(p,"getCandKoord",new Class[]{int.class,int.class,int.class},cell,d,(int)read(p,"cellSize"));}
 static AffineTransform transform(SudokuPanel p,DoodleStroke s)throws Exception{return (AffineTransform)call(p,"doodleProjection",new Class[]{DoodleStroke.class,int.class,int.class},s,p.getWidth(),p.getHeight());}
 static List<DoodleStroke> strokes(SudokuPanel p)throws Exception{return (List<DoodleStroke>)read(p,"doodleStrokes");}
 static void click(SudokuPanel p,Point pt){for(int id:new int[]{MouseEvent.MOUSE_PRESSED,MouseEvent.MOUSE_RELEASED})p.dispatchEvent(new MouseEvent(p,id,System.currentTimeMillis(),0,pt.x,pt.y,1,false,MouseEvent.BUTTON3));}
 public static void main(String[] args)throws Exception{
  Throwable[] failure={null};SwingUtilities.invokeAndWait(()->{MainFrame f=null;try{
   UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
   f=new MainFrame(null);f.setSize(1100,850);f.setVisible(true);SudokuPanel p=f.getSudokuPanel();p.setSudoku((String)null);p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().setPrimaryColor(Color.MAGENTA); // palette may snap; use distinct recorded color below
   paint(p);Point2D c=center(p,2,3);click(p,new Point((int)c.getX(),(int)c.getY()));
   check(strokes(p).size()==1,"right click did not create a circle");DoodleStroke circle=strokes(p).get(0);circle.setColor(Color.MAGENTA);
   check(circle.getAnchorCell()==2&&circle.getAnchorDigit()==3,"candidate identity missing");
   DoodleStroke free=new DoodleStroke(Color.BLUE,.005f);free.getPoints().add(new DoodlePoint(.03,.04));free.getPoints().add(new DoodlePoint(.04,.05));strokes(p).add(free);
   for(int w:new int[]{760,940,1300,1650,1000,780,1420,900}){
    f.setSize(w,850+(w%3)*70);f.validate();BufferedImage im=paint(p);c=center(p,2,3);
    Point2D actual=transform(p,circle).transform(new Point2D.Double(0,0),null);check(c.distance(actual)<.01,"anchor drift at "+w);
    int minx=im.getWidth(),maxx=-1,miny=im.getHeight(),maxy=-1;
    for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++)if((im.getRGB(x,y)&0xffffff)==0xff00ff){minx=Math.min(minx,x);maxx=Math.max(maxx,x);miny=Math.min(miny,y);maxy=Math.max(maxy,y);}
    check(maxx>=0&&Math.abs((minx+maxx)/2.0-c.getX())<=1.5&&Math.abs((miny+maxy)/2.0-c.getY())<=1.5,"rendered circle drift at "+w);
    if(w==760||w==1650)ImageIO.write(im,"png",new File(System.getProperty("hodoku.probe.output"),"board-"+w+".png"));
    check(Math.abs(transform(p,free).getScaleX()-p.getWidth())<.01,"free stroke changed coordinates");
   }
   call(f,"setSplitPane",new Class[]{JPanel.class},p.getCellZoomPanel());f.validate();paint(p);
   check(center(p,2,3).distance(transform(p,circle).transform(new Point2D.Double(),null))<.01,"sidebar moved anchor");
   List<DoodleStroke> untouched=DoodleGeometry.subtract(strokes(p),new Rectangle(0,0,1,1),p.getWidth(),p.getHeight(),s0->{try{return transform(p,s0);}catch(Exception e){throw new RuntimeException(e);}});
   check(untouched.get(0).isCandidateAnchored(),"missed eraser detached circle");
   // Erasing a part detaches only the affected circle into ordinary ink.
   c=center(p,2,3);Rectangle2D cut=new Rectangle2D.Double(c.getX()-100,c.getY()-100,100,200);
   List<DoodleStroke> fragments=DoodleGeometry.subtract(strokes(p),cut,p.getWidth(),p.getHeight(),s->{try{return transform(p,s);}catch(Exception e){throw new RuntimeException(e);}});
   check(!fragments.isEmpty()&&!fragments.get(0).isCandidateAnchored(),"partial erasure retained tracking");
   DoodleStroke arc=fragments.get(0);check(arc.getPoints().size()<circle.getPoints().size(),"eraser did not cut circle");
   for(DoodlePoint q:arc.getPoints())check(q.getX()*p.getWidth()>=c.getX()-1e-6,"wrong side survived");
   call(p,"pushDoodleUndo",new Class[]{});strokes(p).clear();strokes(p).addAll(fragments);
   p.undoCurrentAnnotation();check(strokes(p).get(0).getPoints().size()==49&&strokes(p).get(0).isCandidateAnchored(),"undo lost bound circle");
   p.redoCurrentAnnotation();check(!strokes(p).get(0).isCandidateAnchored(),"redo retained tracking");
   f.setSize(1600,700);f.validate();paint(p);check(transform(p,arc).getScaleX()==p.getWidth(),"fragment not freehand after resize");
   p.getSudoku().delCandidate(2,3);check(transform(p,circle)==null,"removed candidate still circled");p.getSudoku().setCandidate(2,3,true);check(transform(p,circle)!=null,"restored candidate missing circle");
   p.undoCurrentAnnotation();
   GuiState state=new GuiState();state.setIncludeAnnotations(true);p.getState(state,true);
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(XMLEncoder encoder=new XMLEncoder(bytes)){encoder.setExceptionListener(e->{throw new AssertionError(e);});encoder.writeObject(state);}
   GuiState restored;try(XMLDecoder decoder=new XMLDecoder(new ByteArrayInputStream(bytes.toByteArray()))){restored=(GuiState)decoder.readObject();}
   check(restored.getDoodleStrokes().get(0).isCandidateAnchored(),"saved state dropped anchor");
   check(!restored.getDoodleStrokes().get(restored.getDoodleStrokes().size()-1).isCandidateAnchored(),"legacy free stroke rebound");
   check(!restored.getDoodleRedoHistory().get(0).get(0).isCandidateAnchored(),"saved history changed detached arc");
   SessionSnapshot snapshot=new SessionSnapshot();snapshot.setGuiState(restored);snapshot.setActiveRow(0);snapshot.setActiveCol(2);
   SessionStore store=new SessionStore(new File(System.getProperty("hodoku.probe.output"),"session.xml"));store.save(snapshot);
   p.setState(store.load().getGuiState());f.setSize(1250,900);f.validate();paint(p);
   check(strokes(p).get(0).isCandidateAnchored()&&center(p,2,3).distance(transform(p,strokes(p).get(0)).transform(new Point2D.Double(),null))<.01,"reopened circle drifted");
   System.out.println("PASS: real right-click candidate identity, eight window sizes with rendered pixel alignment, free stroke scaling, sidebar, partial eraser detachment/undo/redo, disappearance/reappearance, state+history XML and SessionStore reopen");
  }catch(Throwable t){failure[0]=t;}finally{if(f!=null)f.dispose();}});
  if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}System.exit(0);
 }
}
