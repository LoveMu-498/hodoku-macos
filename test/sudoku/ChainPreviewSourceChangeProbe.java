package sudoku;
import java.util.*;
import java.lang.reflect.*;
import static sudoku.GroupedChainTransactionProbe.*;

/** Edits revoke only previews owned by authored chains; restored input never revives an old proof. */
public final class ChainPreviewSourceChangeProbe {
 static void setup()throws Exception{edt(()->{if(f==null){f=new MainFrame(null);p=f.getSudokuPanel();}p.setSudoku((String)null);p.getSudoku().set(ChainAdvisoryProbe.board(false));p.setAnnotationTool(AnnotationTool.FREE_CHAIN);ChainEditingProbe.install(ChainAdvisoryProbe.ring());call("handleReasoningEnter");return null;});await(true);}
 static void gone()throws Exception{check(p.getStep()==null&&read("reasoningProposal")==null,"stale source preview remains");check(((Set<?>)read("invalidUserChainRelations")).isEmpty(),"stale diagnostics remain");check(p.getSudoku().getValue(0)==0,"edit applied conclusion");}
 public static void main(String[] args)throws Exception{try{
  setup();edt(()->{Object old=read("reasoningProposal");int red=((Set<?>)read("invalidUserChainRelations")).size();p.toggleNextUserChainStrong();check(read("reasoningProposal")==old&&p.getStep()!=null,"next-edge setting canceled preview");check(((Set<?>)read("invalidUserChainRelations")).size()==red,"next-edge setting erased diagnostics");UserChain c=ChainEditingProbe.done().get(0);c.getNodes().get(0).setColor(java.awt.Color.MAGENTA);call("noteUserChainReasoningChanged");check(read("reasoningProposal")==old,"color canceled preview");Collections.reverse(c.getNodes());Collections.reverse(c.getStrongRelations());Collections.rotate(c.getStrongRelations(),-1);call("noteUserChainReasoningChanged");check(read("reasoningProposal")==old,"equivalent cycle orientation canceled preview");return null;});
  setup();edt(()->{UserChain c=ChainEditingProbe.done().get(0);Map<UserChain,Set<Integer>> hits=new IdentityHashMap<>();hits.put(c,Collections.singleton(0));Method m=SudokuPanel.class.getDeclaredMethod("flipChainEdges",Map.class);m.setAccessible(true);m.invoke(p,hits);gone();p.undoCurrentAnnotation();check(p.getStep()==null,"undo resurrected old proof");p.redoCurrentAnnotation();check(p.getStep()==null,"redo resurrected old proof");return null;});
  setup();edt(()->{p.clearUserChainsWithUndo();gone();check(p.currentReasoningChains().isEmpty(),"clear left chains");p.undoCurrentAnnotation();check(p.getStep()==null&&!p.currentReasoningChains().isEmpty(),"undo clear revived preview or lost chain");return null;});
  setup();edt(()->{p.undoCurrentAnnotation();gone();return null;});
  setup();edt(()->{UserChain c=ChainEditingProbe.done().get(0);Map<UserChain,Set<Integer>> hits=new IdentityHashMap<>();hits.put(c,Collections.singleton(1));Method m=SudokuPanel.class.getDeclaredMethod("deleteExactChainEdges",Map.class);m.setAccessible(true);m.invoke(p,hits);gone();return null;});
  setup();edt(()->{UserChain c=ChainEditingProbe.done().get(0);c.getNodes().set(0,GroupedChainProbe.node(1,0,2));call("noteUserChainReasoningChanged");gone();return null;});
  setup();edt(()->{p.getSudoku().delCandidate(40,2);f.sudokuStateChanged();gone();return null;});
  setup();final Thread[] worker=new Thread[1];edt(()->{call("handleReasoningEnter");worker[0]=(Thread)read("reasoningWorker");p.clearUserChainsWithUndo();gone();return null;});if(worker[0]!=null){worker[0].join(5000);check(!worker[0].isAlive(),"canceled apply worker still running");}edt(()->{gone();return null;});
  setup();edt(()->{SolutionStep hint=new SolutionStep(SolutionType.NAKED_SINGLE);hint.addIndex(40);hint.addValue(2);f.setSolutionStep(hint,true);p.clearUserChainsWithUndo();check(p.getStep()!=null,"unrelated F12 hint canceled by chain edit");return null;});
  setup();edt(()->{call("handleReasoningEnter");return null;});await(false);edt(()->{check(p.getSudoku().getValue(0)==1,"confirmed Enter application broken");return null;});
  System.out.println("Chain preview source changes, next strength/color/equivalence, undo/redo, clear, edge/member edit, board mutation and apply passed");
 }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
