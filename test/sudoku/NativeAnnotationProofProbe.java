package sudoku;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import solver.NativeProofCollector;
/** Exercises pre-dedup proof retention, deep ownership and cancellation. */
public final class NativeAnnotationProofProbe {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  Sudoku2 board=new Sudoku2();board.setSudoku(CurrentReasoningProbe.PUZZLE);
  TechniqueStepCatalog catalog=new TechniqueStepCatalog();
  Set<Integer> cells=new HashSet<>(Arrays.asList(2));
  java.util.function.Predicate<SolutionStep> match=s->ReasoningStepIndex.from(s,board).role(cells,true)>=0;
  List<SolutionStep> raw=catalog.findAllRawSteps(board,null);
  Set<String> ordinary=SearchCancellationProbe.keys(raw);
  long start=System.nanoTime();List<SolutionStep> found=catalog.findMatchingSteps(board,match);
  Set<String> keys=SearchCancellationProbe.keys(found);
  for(SolutionStep s:raw)if(match.test(s))check(keys.contains(ReasoningStepIndex.identity(s)),"lost existing proof "+s.getType());
  Map<SolutionType,Integer> extra=new TreeMap<>();
  for(SolutionStep s:found){check(match.test(s),"unrelated proof");check(SudokuPanel.isNativeConclusionExecutable(s,board),"unexecutable proof");if(!ordinary.contains(ReasoningStepIndex.identity(s)))extra.put(s.getType(),extra.getOrDefault(s.getType(),0)+1);}
  check(extra.containsKey(SolutionType.X_CHAIN) && extra.containsKey(SolutionType.ALS_XY_CHAIN)
      && extra.containsKey(SolutionType.AIC),"missing chain/ALS/AIC alternatives");
  String solution="534678912672195348198342567859761423426853791713924856961537284287419635345286179";
  for(SolutionStep s:found) {
   for(Candidate c:s.getCandidatesToDelete())check(solution.charAt(c.getIndex())-'0'!=c.getValue(),"wrong native elimination");
   if(s.getAnzSet()>0)for(int i=0;i<s.getIndices().size();i++)
    check(solution.charAt(s.getIndices().get(i))-'0'==s.getValues().get(Math.min(i,s.getValues().size()-1)),"wrong native placement");
  }
  // Later scans must not mutate retained chain arrays or leak request-local observation.
  catalog.findMatchingSteps(board,s->false);
  check(keys.equals(SearchCancellationProbe.keys(found)),"proof mutated after solver reuse");
  check(!NativeProofCollector.active(),"scope leaked");
  check(ordinary.equals(SearchCancellationProbe.keys(new TechniqueStepCatalog().findAllRawSteps(board,null))),"normal search changed");
  AtomicReference<List<SolutionStep>> canceled=new AtomicReference<>();
  Thread worker=new Thread(()->canceled.set(catalog.findMatchingSteps(board,match)));
  worker.start();SearchCancellationProbe.interruptInside(worker,"FishSolver");
  check(canceled.get()!=null&&canceled.get().isEmpty(),"canceled partial result exposed");
  check(keys.equals(SearchCancellationProbe.keys(catalog.findMatchingSteps(board,match))),"canceled restart differs");
  System.out.println("Matching proofs="+found.size()+", extra="+extra+", elapsed="+(System.nanoTime()-start)/1000000+"ms; ownership, normal parity, cancellation and restart passed");
  System.exit(0);
 }
}
