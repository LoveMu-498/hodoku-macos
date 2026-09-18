package sudoku;

import java.beans.*;
import java.io.*;
import java.util.*;

/** Group relation counterexamples, native proof encoding, and legacy bean round trips. */
public final class GroupedChainProbe {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static UserChainNode node(int d,int... cells){UserChainNode n=new UserChainNode(cells[0],d);if(cells.length>1)n.setGroupCells(cells);return n;}
    static Sudoku2 blank(){Sudoku2 b=new Sudoku2();b.setSudoku(new String(new char[81]).replace('\0','0'));return b;}
    static UserChain chain(boolean closed,UserChainNode[] nodes,boolean... strong){UserChain c=new UserChain();c.setClosed(closed);c.getNodes().addAll(Arrays.asList(nodes));for(boolean b:strong)c.getStrongRelations().add(b);return c;}
    static void refuteDigitPlacements(Sudoku2 board,List<SolutionStep> steps) {
        Set<Integer> forbidden=new HashSet<Integer>();for(SolutionStep s:steps)for(Candidate c:s.getCandidatesToDelete()) {
            check(c.getValue()==1,"unexpected test digit");forbidden.add(c.getIndex());
        }
        check(enumerate(board,0,0,0,new int[9],forbidden)>0,"vacuous board fixture");
    }
    static int enumerate(Sudoku2 b,int row,int cols,int boxes,int[] chosen,Set<Integer> deleted) {
        if(row==9){for(int c:chosen)check(!deleted.contains(c),"independent digit placement refutes deletion "+c);return 1;}
        int count=0;for(int col=0;col<9;col++) {
            int cell=row*9+col,box=row/3*3+col/3;
            if((cols&(1<<col))!=0 || (boxes&(1<<box))!=0 || !b.isCandidate(cell,1))continue;
            chosen[row]=cell;count+=enumerate(b,row+1,cols|(1<<col),boxes|(1<<box),chosen,deleted);
        }
        return count;
    }
    public static void main(String[] args) throws Exception {
        UserChainNode a=node(1,0,1), b=node(1,2), far=node(1,9);
        check(a.validShape() && node(1,0,1,2).validShape(),"valid group shape");
        check(!node(1,0,1,2,3).validShape() && !node(1,0,10).validShape() && !node(1,0,3).validShape(),"invalid group admitted");
        check(!UserChainValidator.weak(a,node(1,27)),"only first member sees target");
        check(!UserChainValidator.weak(a,node(1,1,2)),"overlap accepted");
        check(a.identity()==node(1,1,0).identity(),"order changes identity");
        check(Chain.getSNodeType(a.encoded(false))==Chain.GROUP_NODE && Chain.getSCellIndex2(a.encoded(false))==1,"native group lost");
        Sudoku2 board=blank();check(!UserChainValidator.strong(board,a,b),"extra candidates ignored");
        for(int c:new int[]{9,10,11,18,19,20})board.delCandidate(c,1);
        check(UserChainValidator.strong(board,a,b),"box strong missed after row fails");
        UserChain proof=chain(false,new UserChainNode[]{a,b},true);
        UserChainValidator.Result result=UserChainValidator.validate(board,proof);
        check(result.status==UserChainValidator.Status.PROVEN,"group strong should eliminate row remainder");
        Set<Integer> deleted=new HashSet<Integer>();
        for(SolutionStep step:result.steps){check(step.getValues().isEmpty(),"group truth became placement");for(Candidate c:step.getCandidatesToDelete())deleted.add(c.getIndex());}
        check(deleted.equals(new HashSet<Integer>(Arrays.asList(3,4,5,6,7,8))),"wrong group conclusions: "+deleted);
        UserChain invalid=chain(false,new UserChainNode[]{a,node(1,27),node(1,40)},false,false);
        result=UserChainValidator.validate(blank(),invalid);check(result.invalidRelations.size()==2,"not all invalid edges reported");
        UserChainAssembly.Result assembled=UserChainAssembly.assemble(Arrays.asList(proof));
        check(assembled.chain!=null && assembled.chain.getNodes().stream().anyMatch(UserChainNode::grouped),"assembly drops group");
        UserChain extended=chain(false,new UserChainNode[]{a,b,node(1,3)},true,false);
        check(UserChainCuts.removeEdges(extended,Collections.singleton(1)).get(0).getNodes().get(0).grouped(),"cut drops group");
        Sudoku2 loopBoard=blank();for(int c:new int[]{20,29,38,47,56,65,74})loopBoard.delCandidate(c,1);
        UserChain loop=chain(true,new UserChainNode[]{a,node(1,2),node(1,11)},false,true,false);
        UserChainValidator.Result loopResult=UserChainValidator.validate(loopBoard,loop);
        Set<Integer> loopDeleted=new HashSet<Integer>();
        for(SolutionStep step:loopResult.steps) {
            check(step.getType()==SolutionType.GROUPED_DISCONTINUOUS_NICE_LOOP,"group loop type");
            for(Candidate c:step.getCandidatesToDelete())loopDeleted.add(c.getIndex());
        }
        check(loopDeleted.contains(0)&&loopDeleted.contains(1),"false group not fully deleted");
        Sudoku2 continuous=blank();
        for(int c:new int[]{1,10,18,19,20,28,37,45,46,47})continuous.delCandidate(c,1);
        UserChain ring=chain(true,new UserChainNode[]{node(1,0,9),node(1,2,11),node(1,29,38),node(1,27,36)},true,false,true,false);
        UserChainValidator.Result ringResult=UserChainValidator.validate(continuous,ring);
        check(ringResult.status!=UserChainValidator.Status.INVALID,"continuous grouped ring rejected");
        refuteDigitPlacements(board,UserChainValidator.validate(board,proof).steps);
        refuteDigitPlacements(loopBoard,loopResult.steps);refuteDigitPlacements(continuous,ringResult.steps);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();XMLEncoder e=new XMLEncoder(bytes);e.setExceptionListener(x->{throw new AssertionError(x);});e.writeObject(a);e.writeObject(b);e.close();
        XMLDecoder d=new XMLDecoder(new ByteArrayInputStream(bytes.toByteArray()));UserChainNode copy=(UserChainNode)d.readObject(),old=(UserChainNode)d.readObject();d.close();
        check(copy.identity()==a.identity() && !old.grouped() && old.identity()==b.identity(),"bean round trip");
        int[] arr=copy.getGroupCells();arr[0]=80;check(copy.identity()==a.identity(),"mutable member alias");
        System.out.println("Grouped chain relation, conclusion, identity, cut, native encoding and bean checks passed");
    }
}
