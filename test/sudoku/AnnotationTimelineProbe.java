package sudoku;
import java.awt.*;import java.awt.event.*;import java.awt.image.*;import java.lang.reflect.*;import java.util.*;import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;
/** Render chronology and actual frozen-proof transaction, with isolated settings. */
public final class AnnotationTimelineProbe {
 static java.util.List<DoodleStroke> ink()throws Exception{return (java.util.List<DoodleStroke>)read("doodleStrokes");}
 static DoodleStroke line(int y,Color color){DoodleStroke s=new DoodleStroke(color,.009f);s.getPoints().add(new DoodlePoint(.1,y/810.0));s.getPoints().add(new DoodlePoint(.9,y/810.0));return s;}
 static BufferedImage paint(String name)throws Exception{BufferedImage im=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();ImageIO.write(im,"png",new java.io.File("/tmp/hodoku-timeline/"+name+".png"));return im;}
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void shortcuts()throws Exception{
  ChainEditingProbe.install(ChainEditingProbe.fixture());
  ChainEditingProbe.click(3,InputEvent.CTRL_DOWN_MASK,new Point(750,750));require(ChainEditingProbe.active().getStrongRelations().size()==1,"Control-right did not retreat");p.undoCurrentAnnotation();require(ChainEditingProbe.active().getStrongRelations().size()==2,"retreat undo");
  ChainEditingProbe.drag(3,InputEvent.CTRL_DOWN_MASK,new Point(50,50),new Point(700,700));require(ChainEditingProbe.active().getStrongRelations().equals(Arrays.asList(true,false)),"Control-right drag modified");
  ChainEditingProbe.event(MouseEvent.MOUSE_PRESSED,3,InputEvent.CTRL_DOWN_MASK,new Point(50,50));ChainEditingProbe.event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.CTRL_DOWN_MASK|InputEvent.BUTTON3_DOWN_MASK,new Point(300,300));ChainEditingProbe.event(MouseEvent.MOUSE_RELEASED,3,InputEvent.CTRL_DOWN_MASK,new Point(50,50));require(ChainEditingProbe.active().getStrongRelations().size()==2,"out-and-back drag deleted");
  ChainEditingProbe.click(3,InputEvent.SHIFT_DOWN_MASK,new Point(50,50));require(ChainEditingProbe.active()==null,"finish segment");call("removeLastUserChainNode");require(ChainEditingProbe.done().get(0).getStrongRelations().size()==1,"ended chain Backspace");p.undoCurrentAnnotation();require(ChainEditingProbe.done().get(0).getStrongRelations().size()==2,"ended undo");
 }
 public static void main(String[] args)throws Exception{try{
  edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(ChainAdvisoryProbe.board(true));p.setShowCandidates(true);p.setSize(810,810);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);paint("initial");shortcuts();
   ink().clear();ink().add(line(200,Color.MAGENTA));SolutionStep s=new SolutionStep(SolutionType.NAKED_PAIR);s.addIndex(40);s.addValue(2);s.addCandidateToDelete(41,2);p.setStep(s);BufferedImage old=paint("old");require(old.getRGB(450,200)!=Color.MAGENTA.getRGB(),"old ink not faded");
   p.setAnnotationTool(AnnotationTool.DOODLE);int count=ink().size();ChainEditingProbe.drag(1,0,new Point(100,250),new Point(600,250));require(ink().size()==count+1&&p.getStep()==s,"real doodle canceled step");
   ink().add(line(300,Color.RED));BufferedImage recent=paint("recent");require(recent.getRGB(450,300)==Color.RED.getRGB(),"new ink faded");p.setStep((SolutionStep)s.clone());require(paint("same-step").getRGB(450,300)==Color.RED.getRGB(),"same step reset boundary");
   SolutionStep another=(SolutionStep)s.clone();another.addCandidateToDelete(42,2);p.setStep(another);BufferedImage next=paint("new-step");require(next.getRGB(450,300)!=Color.RED.getRGB(),"new proof did not fade prior ink");require(old.getRGB(450,200)==next.getRGB(450,200),"opacity compounded");p.setStep(null);require(paint("cleared").getRGB(450,200)==Color.MAGENTA.getRGB(),"clear did not restore");
   p.setStep(another);p.getSudoku().delCandidate(80,2);p.reasoningBoardChanged();require(p.getStep()==null,"board mutation kept stale ordinary step");
   p.setSudoku((String)null);p.getSudoku().set(ChainAdvisoryProbe.board(true));p.setAnnotationTool(AnnotationTool.FREE_CHAIN);ChainEditingProbe.install(ChainAdvisoryProbe.ring());p.selectReasoningHint(new SolutionStep(SolutionType.HIDDEN_RECTANGLE),null,null,false);call("handleReasoningEnter");return null;
  });await(true);
  edt(()->{Object proposal=read("reasoningProposal");SolutionStep displayed=p.getStep();
   // Editing the displayed proof's source must not cancel its frozen transaction.
   Method flip=SudokuPanel.class.getDeclaredMethod("flipChainEdges",Map.class);flip.setAccessible(true);UserChain source=ChainEditingProbe.done().get(0);Map<UserChain,Set<Integer>> targets=new IdentityHashMap<>();targets.put(source,Collections.singleton(0));flip.invoke(p,targets);
   require(read("reasoningProposal")==proposal && p.getStep()==displayed,"source edit canceled displayed proof");ink().add(line(350,Color.BLUE));paint("frozen-research");call("handleReasoningEnter");return null;
  });await(false);
  edt(()->{require(p.getSudoku().getValue(0)==1,"frozen original conclusion not applied");require(!ChainEditingProbe.done().isEmpty(),"later chain edits consumed");require(!ink().isEmpty(),"research ink consumed");return null;});
  edt(()->{
   String[] candidates=SwordfishBoxReasoningProbe.CANDIDATES.split(" ");StringBuilder values=new StringBuilder();for(String c:candidates)values.append(c.length()==1?c:"0");p.setSudoku(values.toString());for(int c=0;c<81;c++)if(candidates[c].length()>1)for(int d=1;d<=9;d++)if(!candidates[c].contains(""+d))p.getSudoku().delCandidate(c,d);
   p.setAnnotationTool(AnnotationTool.BOX_SELECTION);SwordfishBoxReasoningProbe.boxes();p.selectReasoningHint(new SolutionStep(SolutionType.HIDDEN_RECTANGLE),null,null,false);call("handleReasoningEnter");return null;
  });await(true);
  edt(()->{Object proof=read("reasoningProposal");java.util.List<SudokuSet> boxes=(java.util.List<SudokuSet>)read("boxReasoningGroups");boxes.get(0).add(26);Field revision=SudokuPanel.class.getDeclaredField("boxReasoningRevision");revision.setAccessible(true);revision.setLong(p,revision.getLong(p)+1);call("noteBoxReasoningChanged");require(proof==read("reasoningProposal"),"new box canceled proof");call("handleReasoningEnter");return null;});await(false);
  edt(()->{for(int c:new int[]{18,26,36,41,54,62})require(!p.getSudoku().isCandidate(c,2),"frozen box proof not applied");require(p.getBoxReasoningFootprint().contains(26),"later box consumed");return null;});
  System.out.println("Timeline fade/no compounding, same/new proof, cancellation restore, frozen edited-source apply, research retention, Control-right and ended Backspace passed");
 }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
