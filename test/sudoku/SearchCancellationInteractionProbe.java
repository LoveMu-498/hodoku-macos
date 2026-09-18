package sudoku;
import static sudoku.GroupedChainTransactionProbe.*;
public final class SearchCancellationInteractionProbe {
 public static void main(String[] args)throws Exception{try{
 edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku(CurrentReasoningProbe.PUZZLE);p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.BOX_SELECTION);java.lang.reflect.Field x=SudokuPanel.class.getDeclaredField("boxReasoningGroups");x.setAccessible(true);((java.util.List<SudokuSet>)x.get(p)).get(0).add(2);call("handleReasoningEnter");return null;});
 Thread worker=edt(()->(Thread)read("reasoningWorker"));long end=System.nanoTime()+3_000_000_000L;boolean entered=false;while(worker.isAlive()&&System.nanoTime()<end){for(StackTraceElement e:worker.getStackTrace())if(e.getClassName().equals("solver.FishSolver"))entered=true;if(entered)break;Thread.sleep(1);}check(entered,"fixture missed active search");
 long start=System.nanoTime();edt(()->{p.getSudoku().delCandidate(2,1);p.reasoningBoardChanged();return null;});worker.join(1500);check(!worker.isAlive(),"board change did not stop CPU worker");
 edt(()->{check(read("reasoningRequest")==null&&read("reasoningProposal")==null&&p.getStep()==null,"stale result republished");return null;});
 System.out.println("Real board mutation stopped analysis in "+((System.nanoTime()-start)/1000000)+"ms; no stale proposal");
 }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
