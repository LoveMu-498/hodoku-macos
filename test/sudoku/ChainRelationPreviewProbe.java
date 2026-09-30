package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Real Swing listeners and rendering, isolated candidate board. */
public final class ChainRelationPreviewProbe {
 static void click(int button,int mods,Point point) {ChainEditingProbe.click(button,mods,point);p.flushMappedClick();}
 static void key(int code, boolean down) {
  KeyEvent e=new KeyEvent(p,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,code,KeyEvent.CHAR_UNDEFINED);
  if(down)p.handleAnnotationKeyPressed(e);else p.handleAnnotationToolKeyReleased(e);
 }
 static void preview(boolean down){event(down?MouseEvent.MOUSE_PRESSED:MouseEvent.MOUSE_RELEASED,2,0,new Point(100,100));}
 static UserChainNode node(int c,int d){return new UserChainNode(c,d,Color.ORANGE);}
 static void reset()throws Exception {
  p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);
  p.setAnnotationTool(AnnotationTool.FREE_CHAIN);p.setSize(810,810);ChainOriginProbe.render(null);
  click(1,0,ChainOriginProbe.candidate(0,1));
 }
 public static void main(String[] args)throws Exception {
  try {edt(()->{
   ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();
   reset();String before=TechniqueStepCatalog.createSignature(p.getSudoku());
   check(p.chainPreviewRelation(node(1,1))==1,"row weak");check(p.chainPreviewRelation(node(9,1))==1,"column weak");
   check(p.chainPreviewRelation(node(10,1))==1,"box weak");check(p.chainPreviewRelation(node(0,2))==1,"cell weak");
   check(p.chainPreviewRelation(node(40,1))==0,"unrelated");check(p.chainPreviewRelation(node(0,1))==0,"self");
   BufferedImage off=ChainOriginProbe.render(null);preview(true);preview(true);
   BufferedImage on=ChainOriginProbe.render("chain-relation-preview");int diff=0;
   for(int y=0;y<810;y++)for(int x=0;x<810;x++)if(off.getRGB(x,y)!=on.getRGB(x,y))diff++;
   check(diff>100,"preview did not render");p.setNextUserChainStrong(true);
   click(1,0,ChainOriginProbe.candidate(1,1));check(active().getNodes().size()==1,"weak accepted as strong");
   key(KeyEvent.VK_SPACE,true);click(1,0,ChainOriginProbe.candidate(1,1));
   check(active().getNodes().size()==2&&!active().getStrongRelations().get(0),"manual weak append");
   check(active().getNodes().get(1).contains(1,1),"tail did not advance");
   click(1,InputEvent.ALT_DOWN_MASK,ChainOriginProbe.candidate(40,1));check(active().getNodes().size()==2,"unrelated modifier bypass");
   key(KeyEvent.VK_ALT,false);preview(false);check(!(Boolean)read("chainRelationPreviewHeld"),"release stuck");
   check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"board changed");
   reset();for(int c:new int[]{2,9,10,11,18,19,20})p.getSudoku().delCandidate(c,1);
   check(p.chainPreviewRelation(node(1,1))==2,"missed box strong when row weak");
   preview(true);p.setNextUserChainStrong(false);click(1,0,ChainOriginProbe.candidate(1,1));
   check(!active().getStrongRelations().get(0),"strong target forced strong edge");
   preview(false);p.undoCurrentAnnotation();preview(true);p.setNextUserChainStrong(true);
   click(1,0,ChainOriginProbe.candidate(1,1));check(active().getStrongRelations().get(0),"strong edge rejected");
   p.cancelAnnotationToolInteractionOnDeactivation();check(!(Boolean)read("chainRelationPreviewHeld"),"focus loss stuck");
   preview(true);p.handleEscapeVisualReset();check(!(Boolean)read("chainRelationPreviewHeld")&&active()!=null,"escape destroyed chain");
   preview(true);p.setAnnotationTool(AnnotationTool.DOODLE);check(!(Boolean)read("chainRelationPreviewHeld"),"tool change stuck");
   preview(true);check(!(Boolean)read("chainRelationPreviewHeld"),"preview outside L");
   reset();click(1,InputEvent.SHIFT_DOWN_MASK,ChainOriginProbe.candidate(1,1));
   check(active().getNodes().get(0).grouped(),"group fixture");
   check(p.chainPreviewRelation(node(9,2))==0,"group unrelated member");
   check(p.chainPreviewRelation(node(2,1))==1,"group weak target");
   for(int c:new int[]{9,10,11,18,19,20})p.getSudoku().delCandidate(c,1);
   check(p.chainPreviewRelation(node(2,1))==2,"group strong target");
   reset();p.setNextUserChainStrong(false);click(1,0,ChainOriginProbe.candidate(1,1));click(1,0,ChainOriginProbe.candidate(9,1));
   check(p.chainPreviewRelation(node(0,1))==1,"closure origin missing");
   check(p.chainPreviewRelation(node(1,1))==0,"intermediate node allowed");
   preview(true);p.setNextUserChainStrong(false);click(1,0,ChainOriginProbe.candidate(0,1));
   check(active().isClosed()&&p.chainPreviewRelation(node(2,1))==0,"closed chain targets");
   reset();f.setVisible(true);f.toFront();p.requestFocusInWindow();
   System.out.println("PASS: relation scope, rendering, weak-to-strong gate, manual Space/weak/strong, tail, release, focus, Escape, tool scope, unchanged board");return null;
  });
   Robot robot=new Robot();robot.setAutoDelay(100);robot.waitForIdle();
   Point target=edt(()->{Point q=p.getLocationOnScreen();q.translate(100,100);return q;});robot.mouseMove(target.x,target.y);
   robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);robot.waitForIdle();edt(()->{check((Boolean)read("chainRelationPreviewHeld"),"native middle press");return null;});
   robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);robot.waitForIdle();edt(()->{check(!(Boolean)read("chainRelationPreviewHeld"),"native middle release");return null;});
   System.out.println("PASS: native Robot middle press/release through MainFrame dispatcher");
  }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
 }
}
