package sudoku;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
/** A future unsupported proof may lose evidence, never the ability to solve. */
public final class ReplayEvidenceFailureProbe {
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[]args)throws Exception{
  System.setProperty("hodoku.data.dir",Files.createTempDirectory("replay-proof-failure-").toString());
  final MainFrame[] owner=new MainFrame[1];
  try{SwingUtilities.invokeAndWait(()->{
   MainFrame f=owner[0]=new MainFrame(null);SudokuPanel p=f.getSudokuPanel();ReplayController c=f.getReplayController();
   c.suspend();p.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");c.beginSession(new ReplayBoard(p.getSudoku()));c.resume();
   SolutionStep original=p.getNextStep(true);SolutionStep unsupported=new SolutionStep(original.getType()){
    @Override public int getProgressScoreSinglesOnly(){throw new IllegalArgumentException("future proof encoding unavailable");}
   };
   unsupported.setIndices(new ArrayList<Integer>(original.getIndices()));unsupported.setValues(new ArrayList<Integer>(original.getValues()));
   ReplayBoard before=new ReplayBoard(p.getSudoku());p.setStep(unsupported);require(p.doStep(),"codec failure blocked native solve");require(!before.equals(new ReplayBoard(p.getSudoku())),"board did not change");
   require(c.session().frames().size()==2&&c.session().last().kind.equals("evidence-unavailable"),"must explicitly persist missing proof instead of claiming complete");
   try{ReplaySession loaded=ReplayStore.read(c.directory().resolve(c.session().id+".hrep"));require(loaded.last().kind.equals("evidence-unavailable"),"failure not persisted");}catch(Exception e){throw new RuntimeException(e);}
  });}finally{if(owner[0]!=null)SwingUtilities.invokeAndWait(()->owner[0].dispose());}
  SudokuSet a=new SudokuSet(),b=new SudokuSet();a.add(0);a.add(1);b.add(9);b.add(10);
  UserChain c=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0,1),GroupedChainProbe.node(1,2)},true);c.setSourceId(42);
  byte[] raw=ReplayEvidence.input("BOX",Arrays.asList(a,new SudokuSet(),b),Arrays.asList(c));a.clear();c.getNodes().clear();ReplayEvidence decoded=ReplayEvidence.decode(raw);
  require(decoded.boxes().size()==3&&decoded.boxes().get(0).size()==2&&decoded.boxes().get(1).isEmpty()&&decoded.boxes().get(2).contains(10),"group identities collapsed");
  require(decoded.chains().get(0).getSourceId()==42&&decoded.chains().get(0).getNodes().get(0).cells().length==2,"grouped chain lost");
  byte[] trailing=Arrays.copyOf(raw,raw.length+1);boolean rejected=false;try{ReplayEvidence.decode(trailing);}catch(java.io.IOException e){rejected=true;}require(rejected,"trailing data accepted");
  Sudoku2 corpus=new Sudoku2();corpus.setSudoku("100007090030020008009600500005300900010080002600004000300000010040000007007000300");solver.SudokuSolver solver=new solver.SudokuSolver();solver.setSudoku(corpus);solver.solve();int count=0;
  for(SolutionStep step:solver.getSteps()){byte[] proof=ReplayProof.encode(step);require(Arrays.equals(proof,ReplayProof.encode(ReplayProof.decode(proof))),"native corpus roundtrip");count++;}require(count>0,"empty corpus");
  System.out.println("Native proof corpus steps="+count);
  System.out.println("Explicit persisted proof failure preserves native solve; raw box group identity, grouped nodes, deep snapshot and corruption checks passed");System.exit(0);
 }
}
