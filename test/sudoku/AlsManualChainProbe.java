package sudoku;

import java.util.*;

/** ALS propositions are inclusive strong links, not conjugate pairs. */
public final class AlsManualChainProbe {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void restrict(Sudoku2 b,int c,String digits){for(int d=1;d<=9;d++)if(digits.indexOf((char)('0'+d))<0)b.delCandidate(c,d);}
    public static void main(String[] args) {
        Sudoku2 board=GroupedChainProbe.blank();
        restrict(board,29,"35");restrict(board,38,"135");restrict(board,42,"13");
        UserChainNode five=GroupedChainProbe.node(5,38), threes=GroupedChainProbe.node(3,38,42);
        check(threes.validShape(),"cross-box row group rejected");
        boolean original=Options.getInstance().isAllowAlsInTablingChains();
        Options.getInstance().setAllowAlsInTablingChains(false);
        check(UserChainValidator.strong(board,five,threes),"manual ALS disabled by automatic-search preference");
        check(!Options.getInstance().isAllowAlsInTablingChains(),"manual validation changed preference");
        Options.getInstance().setAllowAlsInTablingChains(original);
        check(UserChainValidator.strong(board,five,threes),"native ALS strong missed");
        check(!UserChainValidator.weak(five,threes),"ALS incorrectly implies mutual exclusion");
        check(!UserChainValidator.strong(board,five,GroupedChainProbe.node(3,38)),"partial ALS digit accepted");
        UserChain isolated=GroupedChainProbe.chain(false,new UserChainNode[]{five,threes},true);
        UserChainValidator.Result alone=UserChainValidator.validate(board,isolated);
        for(SolutionStep s:alone.steps)check(s.getValues().isEmpty(),"inclusive link produced arbitrary placement");
        UserChain chain=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(3,29),GroupedChainProbe.node(5,29),five,threes},true,false,true);
        UserChainValidator.Result result=UserChainValidator.validate(board,chain);
        check(result.status==UserChainValidator.Status.PROVEN,"ALS chain did not prove eliminations");
        Set<Integer> deleted=new HashSet<Integer>();boolean premise=false;
        for(SolutionStep s:result.steps){
            for(Candidate c:s.getCandidatesToDelete()){check(c.getValue()==3,"unexpected deleted digit");deleted.add(c.getIndex());}
            for(AlsInSolutionStep als:s.getAlses())if(als.getIndices().containsAll(Arrays.asList(38,42)))premise=true;
        }
        check(deleted.contains(36)&&deleted.contains(37)&&!deleted.contains(38)&&!deleted.contains(42),"wrong ALS deletions: "+deleted);
        check(premise,"actual ALS premise absent from proof");
        Sudoku2 changed=board.clone();changed.setCandidate(42,2,true);
        check(!UserChainValidator.strong(changed,five,threes),"stale ALS cached after board mutation");
        check(UserChainValidator.strong(board,five,threes),"cache failed when original board restored");
        String fixture="8 1237 123 135 139 4 123 1579 6 125 4 9 7 1368 1356 1238 1358 138 157 13 6 1358 2 1359 348 13589 13478 126 9 7 1234 1346 1236 5 1368 138 4 356 135 9 7 8 13 136 2 126 1236 8 1235 136 12356 7 4 9 1267 1268 124 12348 5 123 9 138 13478 9 158 145 1348 1348 7 6 2 1348 3 1278 124 6 1489 129 148 178 5";
        String[] cells=fixture.split(" ");Sudoku2 user=GroupedChainProbe.blank();
        for(int i=0;i<81;i++)if(cells[i].length()==1)user.setCell(i,Integer.parseInt(cells[i]));
        for(int i=0;i<81;i++)if(cells[i].length()>1)restrict(user,i,cells[i]);
        check(UserChainValidator.strong(user,five,threes),"user fixture ALS missed");
        check(!UserChainValidator.weak(five,threes),"user fixture accepted weak relation");
        // Exhaust every local ALS assignment: neither endpoint false together; both true allowed.
        int both=0, assignments=0;
        for(int a:new int[]{1,3,5})for(int b:new int[]{1,3})if(a!=b){
            assignments++;check(a==5||a==3||b==3,"ALS strong counterexample");if(a==5&&b==3)both++;
        }
        check(assignments==4&&both==1,"ALS witness enumeration");
        System.out.println("ALS manual-chain fixture, inclusive semantics, complete propositions, conclusions, premises and cache invalidation passed");
    }
}
