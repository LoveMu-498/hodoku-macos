package sudoku;
import java.util.*;
public class ReasoningFilterProbe {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args){Sudoku2 b=new Sudoku2();b.setSudoku("7.8.495............34.5..7..5..7...13..8.6..96...9..3..7..8.42............543.1.6");
 Set<Integer> cells=new HashSet<Integer>(Arrays.asList(72,73,79));int triple=0;
 for(SolutionStep s:new TechniqueStepCatalog().findAllRawSteps(b,null)){
  if(s.getType()==SolutionType.NAKED_TRIPLE&&s.getIndices().contains(72)){
   check(CurrentReasoningMenu.acceptFilter(s,b,cells,0,1),"triple excluded by boxes");
   check(!CurrentReasoningMenu.acceptFilter(s,b,Collections.singleton(0),0,1),"unrelated box accepted");triple++;
  }
 }
 check(triple>0,"missing native fixture");SolutionStep fish=new SolutionStep(SolutionType.X_WING);fish.addCandidateToDelete(0,2);
 check(CurrentReasoningMenu.acceptFilter(fish,b,cells,2,2),"fish digit missing");check(!CurrentReasoningMenu.acceptFilter(fish,b,cells,3,2),"wrong digit");fish.addCandidateToDelete(1,3);check(!CurrentReasoningMenu.acceptFilter(fish,b,cells,2,2),"mixed digit accepted");
 check(CurrentReasoningMenu.acceptFilter(new SolutionStep(SolutionType.XYZ_WING),b,cells,0,3),"XY category missing");check(!CurrentReasoningMenu.acceptFilter(new SolutionStep(SolutionType.X_WING),b,cells,0,3),"XY category too broad");
 check(!CurrentReasoningMenu.acceptFilter(fish,b,Collections.emptySet(),0,1),"missing boxes");
 int accepted=0,rejected=0;b.setSudoku(CurrentReasoningProbe.PUZZLE);
 for(SolutionStep s:new TechniqueStepCatalog().findSteps(b,null,false)){
  if(CurrentReasoningMenu.acceptContext(s,b,cells,Collections.emptySet(),Collections.singleton(72),2,2)){
   accepted++;for(Candidate c:s.getCandidatesToDelete())check(c.getValue()==2,"Tab unrelated conclusion");
   if(!ReasoningStepIndex.SINGLE_DIGIT.contains(s.getType()))for(int n:ReasoningStepIndex.from(s,b).premiseNodes)check(n%10==2,"Tab uses hidden digits");
  }else rejected++;
 }
 check(accepted>0&&rejected>0,"quick fixture did not narrow results");
 System.out.println("Quick digit: "+accepted+" accepted, "+rejected+" excluded");
 System.out.println("Native box containment, single-digit fish, XY categories and empty-input filters passed");System.exit(0);}
}
