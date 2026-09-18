package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;

/** Actual panel mouse listeners; isolated board, no desktop input injection. */
public final class ChainEditingProbe {
 static void set(String name,Object value)throws Exception{Field x=SudokuPanel.class.getDeclaredField(name);x.setAccessible(true);x.set(p,value);}
 static Point point(int c)throws Exception{Method m=SudokuPanel.class.getDeclaredMethod("getCandKoord",int.class,int.class,int.class);m.setAccessible(true);Point2D a=(Point2D)m.invoke(p,c,1,(Integer)read("cellSize"));return new Point((int)a.getX(),(int)a.getY());}
 static UserChain active()throws Exception{return (UserChain)read("activeUserChain");}
 static java.util.List<UserChain> done()throws Exception{return (java.util.List<UserChain>)read("userChains");}
 static void event(int id,int button,int mods,Point pt){p.dispatchEvent(new MouseEvent(p,id,System.currentTimeMillis(),mods,pt.x,pt.y,1,false,button));}
 static void click(int button,int mods,Point pt){event(MouseEvent.MOUSE_PRESSED,button,mods,pt);event(MouseEvent.MOUSE_RELEASED,button,mods,pt);}
 static void drag(int button,int mods,Point a,Point b){event(MouseEvent.MOUSE_PRESSED,button,mods,a);event(MouseEvent.MOUSE_DRAGGED,MouseEvent.NOBUTTON,mods|(button==1?InputEvent.BUTTON1_DOWN_MASK:InputEvent.BUTTON3_DOWN_MASK),b);event(MouseEvent.MOUSE_RELEASED,button,mods,b);}
 static void paint(String name)throws Exception{BufferedImage im=new BufferedImage(p.getWidth(),p.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();ImageIO.write(im,"png",new java.io.File("/tmp/hodoku-chain-edit/"+name+".png"));}
 static void install(UserChain c)throws Exception{done().clear();c.setActive(true);c.setNextStrong(!c.getStrongRelations().get(c.getStrongRelations().size()-1));set("activeUserChain",c);set("nextUserChainStrong",c.isNextStrong());call("noteUserChainReasoningChanged");}
 static UserChain fixture(){return GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0,1),GroupedChainProbe.node(1,12),GroupedChainProbe.node(1,30)},true,false);}
 public static void main(String[] args)throws Exception{try{edt(()->{
  f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().setSudoku(new String(new char[81]).replace('\0','0'));p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);p.setSize(810,810);install(fixture());paint("open");
  click(3,0,new Point(750,750));check(active().getStrongRelations().equals(Arrays.asList(true,true)),"last flip");check(!(Boolean)read("nextUserChainStrong"),"next mode");p.undoCurrentAnnotation();check(active().getStrongRelations().equals(Arrays.asList(true,false)),"undo flip");check((Boolean)read("nextUserChainStrong"),"undo next");
  drag(3,0,new Point(0,0),new Point(800,800));check(active().getStrongRelations().equals(Arrays.asList(false,true)),"batch flip");p.undoCurrentAnnotation();check(active().getStrongRelations().equals(Arrays.asList(true,false)),"single undo batch");
  click(3,InputEvent.ALT_DOWN_MASK,point(1));check((Integer)read("preciseChainCandidate")>=0,"group first endpoint");paint("precise");click(3,InputEvent.ALT_DOWN_MASK,point(12));check(active().getStrongRelations().equals(Arrays.asList(false,false)),"precise group edge");check((Boolean)read("nextUserChainStrong"),"middle changes next mode");
  click(3,InputEvent.ALT_DOWN_MASK,point(1));p.updateDeletionModifier(new KeyEvent(p,KeyEvent.KEY_RELEASED,1,0,KeyEvent.VK_ALT,KeyEvent.CHAR_UNDEFINED));check((Integer)read("preciseChainCandidate")==-1,"modifier release");
  install(fixture());click(3,InputEvent.SHIFT_DOWN_MASK,point(12));check(active()==null&&done().size()==1,"finish segment");click(3,0,new Point(750,750));check(done().get(0).getStrongRelations().get(1),"finished tail flip");check(!(Boolean)read("nextUserChainStrong"),"finished next");p.undoCurrentAnnotation();check((Boolean)read("nextUserChainStrong"),"finished undo next");
  install(fixture());click(1,InputEvent.ALT_DOWN_MASK,point(50));check(done().size()==1&&active().getNodes().size()==1&&active().getNodes().get(0).contains(50,1),"new segment");p.undoCurrentAnnotation();check(done().isEmpty()&&active().getNodes().size()==3,"atomic restart undo");
  install(fixture());int precise=InputEvent.ALT_DOWN_MASK|InputEvent.CTRL_DOWN_MASK;click(1,precise,point(1));click(1,precise,point(12));check(active()!=null&&active().getStrongRelations().size()==1,"precise delete");boolean group=false;for(UserChain c:done())for(UserChainNode n:c.getNodes())group|=n.grouped();check(!group,"isolated group retained");p.undoCurrentAnnotation();check(active().getStrongRelations().size()==2,"delete undo");
  install(fixture());UserChain shared=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0,1),GroupedChainProbe.node(1,60)},false);done().add(shared);
  String before=TechniqueStepCatalog.createSignature(p.getSudoku());
  click(1,precise,point(1));click(1,precise,point(12));check(done().contains(shared)&&shared.getNodes().get(0).grouped(),"shared group removed");check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"candidate data changed");
  install(GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0),GroupedChainProbe.node(1,12)},true));click(1,precise,point(0));click(1,precise,point(12));check(active()==null&&done().isEmpty(),"single edge left orphan highlights");p.undoCurrentAnnotation();check(active()!=null&&active().getNodes().size()==2,"single-edge undo");
  UserChain closed=fixture();closed.setClosed(true);closed.getStrongRelations().add(true);install(closed);paint("closed");click(1,precise,point(30));click(1,precise,point(1));check(active()!=null&&!active().isClosed()&&active().getStrongRelations().size()==2,"open cycle");p.undoCurrentAnnotation();check(active().isClosed(),"closed undo");
  install(fixture());UserChain duplicate=fixture();duplicate.setActive(false);done().add(duplicate);click(3,InputEvent.ALT_DOWN_MASK,point(1));click(3,InputEvent.ALT_DOWN_MASK,point(12));check(active().getStrongRelations().get(0)&&duplicate.getStrongRelations().get(0),"ambiguous changed");
  install(fixture());click(3,InputEvent.SHIFT_DOWN_MASK,point(12));
  UserChain newer=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,50),GroupedChainProbe.node(1,60)},true);done().add(newer);
  click(1,precise,point(1));click(1,precise,point(12));click(3,0,new Point(750,750));check(!newer.getStrongRelations().get(0),"old deletion changed latest ordering");
  install(fixture());event(MouseEvent.MOUSE_PRESSED,3,0,new Point(100,100));p.handleEscapeVisualReset();event(MouseEvent.MOUSE_RELEASED,3,0,new Point(700,700));check(active().getStrongRelations().equals(Arrays.asList(true,false)),"Escape drag changed chain");
  click(3,InputEvent.ALT_DOWN_MASK,point(1));p.cancelAnnotationToolInteractionOnDeactivation();check((Integer)read("preciseChainCandidate")==-1,"focus loss kept endpoint");
  install(fixture());p.setAnnotationTool(AnnotationTool.DOODLE);event(MouseEvent.MOUSE_PRESSED,3,InputEvent.CTRL_DOWN_MASK,new Point(100,100));event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.CTRL_DOWN_MASK|InputEvent.BUTTON3_DOWN_MASK,new Point(350,250));paint("crossed-erase");event(MouseEvent.MOUSE_RELEASED,3,InputEvent.CTRL_DOWN_MASK,new Point(350,250));
  return null;});System.out.println("Right click/batch, precise grouped flip/delete, modifier release, new segment, loop opening, ambiguity and undo passed; rendered open/closed/precise/crossed erase images");}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
