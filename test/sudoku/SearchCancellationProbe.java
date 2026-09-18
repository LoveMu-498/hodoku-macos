package sudoku;
import java.util.*;import java.util.concurrent.atomic.*;import java.lang.reflect.*;import java.util.concurrent.locks.*;
import solver.*;
public final class SearchCancellationProbe {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static Sudoku2 board(){Sudoku2 b=new Sudoku2();b.setSudoku(CurrentReasoningProbe.PUZZLE);return b;}
 static void interruptInside(Thread worker,String solver)throws Exception {
  long end=System.nanoTime()+5_000_000_000L;boolean inside=false;
  while(worker.isAlive()&&System.nanoTime()<end){for(StackTraceElement e:worker.getStackTrace())if(e.getClassName().equals("solver."+solver) && !e.getMethodName().startsWith("getAll") && !e.getMethodName().equals("getStep")){inside=true;break;}if(inside)break;Thread.sleep(1);}
  check(inside,"fixture did not enter "+solver);int originalFins=Options.getInstance().getMaxFins();
  if(solver.equals("FishSolver"))Options.getInstance().setMaxFins(originalFins+1);
  long start=System.nanoTime();worker.interrupt();worker.join(1500);check(!worker.isAlive(),solver+" did not stop");
  if(solver.equals("FishSolver")){check(Options.getInstance().getMaxFins()==originalFins+1,"search overwrote newer option");Options.getInstance().setMaxFins(originalFins);}System.out.println(solver+" interrupt-to-exit "+((System.nanoTime()-start)/1_000_000)+"ms");
 }
 static Set<String> keys(List<SolutionStep> steps){Set<String> k=new TreeSet<String>();for(SolutionStep s:steps)k.add(ReasoningStepIndex.identity(s));return k;}
 public static void main(String[] args)throws Exception {
  int fins=Options.getInstance().getMaxFins(),endo=Options.getInstance().getMaxEndoFins();boolean templates=Options.getInstance().isCheckTemplates();
  for(String type:new String[]{"FishSolver","ChainSolver","AlsSolver","TablingSolver"}) {
   SudokuSolver instance=SudokuSolverFactory.getInstance();AtomicReference<Throwable> error=new AtomicReference<Throwable>();AtomicBoolean canceled=new AtomicBoolean();
   Thread worker=new Thread(()->{try(SearchCancellation.Scope scope=SearchCancellation.enable()){
    SudokuStepFinder f=instance.getStepFinder();Sudoku2 b=board();
    if(type.equals("FishSolver"))f.getAllFishes(b,2,7,5,2,null,-1,2);
    else if(type.equals("ChainSolver"))f.getAllChains(b);
    else if(type.equals("AlsSolver"))f.getAllAlses(b,true,true,true);
    else f.getAllForcingNets(b);
   }catch(java.util.concurrent.CancellationException e){canceled.set(true);}catch(Throwable e){error.set(e);}finally{SudokuSolverFactory.discard(instance);}});
   worker.start();interruptInside(worker,type);check(error.get()==null,"unexpected failure "+error.get());check(canceled.get(),"no cooperative cancellation "+type);
   check(Options.getInstance().getMaxFins()==fins&&Options.getInstance().getMaxEndoFins()==endo&&Options.getInstance().isCheckTemplates()==templates,"global options changed");
   SudokuSolver replacement=SudokuSolverFactory.getInstance();check(replacement!=instance,"canceled instance reused");SudokuSolverFactory.giveBack(replacement);
  }
  TechniqueStepCatalog catalog=new TechniqueStepCatalog();AtomicReference<List<SolutionStep>> partial=new AtomicReference<List<SolutionStep>>();
  Thread scan=new Thread(()->partial.set(catalog.findAllRawSteps(board(),null)));scan.start();interruptInside(scan,"FishSolver");check(partial.get()!=null&&partial.get().isEmpty(),"partial results published");
  int runs=catalog.getEnumerationRunCount();Set<String> restarted=keys(catalog.findAllRawSteps(board(),null));check(catalog.getEnumerationRunCount()>runs,"canceled result cached");check(restarted.equals(keys(new TechniqueStepCatalog().findAllRawSteps(board(),null))),"restart differs from clean search");
  Field lockField=TechniqueStepCatalog.class.getDeclaredField("scanLock");lockField.setAccessible(true);ReentrantLock lock=(ReentrantLock)lockField.get(catalog);lock.lock();
  Thread waiting=new Thread(()->partial.set(catalog.findBoxSteps(board(),Collections.singleton(0))));try{waiting.start();long end=System.nanoTime()+1_000_000_000L;while(!lock.hasQueuedThread(waiting)&&System.nanoTime()<end)Thread.sleep(1);check(lock.hasQueuedThread(waiting),"not waiting on lock");waiting.interrupt();waiting.join(1000);check(!waiting.isAlive()&&partial.get().isEmpty(),"waiting cancellation failed");}finally{lock.unlock();}
  System.out.println("Options, solver discard, canceled cache, clean restart and lock cancellation passed; "+restarted.size()+" proofs unchanged");System.exit(0);
 }
}
