/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.*;
import static sudoku.DoodleContradictionBasisProbe.*;

/** Actual candidate coverage and explicit raw witnesses, independent of any display filter. */
public final class DoodleConsensusBasisProbe {
    private static int checks;
    private static void verify(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static List<DoodleStroke> branches(int a,int ak,int b,int bk){
        return new ArrayList<DoodleStroke>(Arrays.asList(mark(a/10,a%10,ak,true,0),mark(b/10,b%10,bk,true,1),
                mark(0,3,2,false,0),mark(0,3,2,false,1),mark(10,4,1,false,0),mark(10,4,1,false,1)));
    }
    private static DoodleConsensusBasis.Analysis analyze(Sudoku2 b,List<DoodleStroke> marks,int group){return DoodleConsensusBasis.analyze(b,marks,group);}
    public static void main(String[] args)throws Exception{
        Sudoku2 board=board();List<DoodleStroke> marks=branches(805,1,805,2);
        for(int group:new int[]{0,1}){DoodleConsensusBasis.Analysis a=analyze(board,marks,group);
            verify(a.truths.equals(Collections.singleton(104))&&a.falses.equals(Collections.singleton(3)),"complement did not find explicit common truth and false");
            verify(a.pairs.get(0).coverage==DoodleConsensusBasis.Coverage.CANDIDATE_TRUE_FALSE,"candidate complement misclassified");}
        verify(!analyze(board,marks,2).hasConclusions(),"unrelated group got an automatic result");
        candidates(board,80,5,7);
        for(int kind:new int[]{1,2}){marks=branches(805,kind,807,kind);DoodleConsensusBasis.Analysis a=analyze(board,marks,0);
            verify(a.hasConclusions()&&a.pairs.get(0).coverage==(kind==1?DoodleConsensusBasis.Coverage.BIVALUE_BOTH_TRUE:DoodleConsensusBasis.Coverage.BIVALUE_BOTH_FALSE),"actual bivalue coverage failed");}
        for(int[] roots:new int[][]{{805,1,805,1},{805,2,805,2},{805,1,807,2},{805,2,807,1},{805,1,795,2}})
            verify(!analyze(board,branches(roots[0],roots[1],roots[2],roots[3]),0).hasConclusions(),"noncomplementary pair promoted");
        Sudoku2 triple=board();candidates(triple,80,5,7,9);triple.setCandidate(80,9,false,true);triple.setCandidate(80,9,true,false);
        for(int kind:new int[]{1,2})verify(!analyze(triple,branches(805,kind,807,kind),0).hasConclusions(),"pencil notes replaced complete candidate set");
        marks=branches(805,1,805,2);board.delCandidate(80,5);verify(!analyze(board,marks,0).hasConclusions(),"stale origin promoted");board=board();
        for(int mode=0;mode<6;mode++){
            marks=branches(805,1,805,2);
            if(mode==0)marks.get(1).setHypothesisStart(false);
            if(mode==1)marks.add(mark(79,4,2,true,1));
            if(mode==2)marks.get(1).setHypothesisGroupKind(2);
            if(mode==3)marks.get(1).setHypothesisConditionId(4);
            if(mode==4)marks.get(1).setProofOnly(true);
            if(mode==5)marks.get(1).setRefutationResult(true);
            verify(!analyze(board,marks,0).hasConclusions(),"non-single authoritative start accepted: "+mode);
        }
        for(int mode=0;mode<3;mode++){
            marks=branches(805,1,805,2);
            if(mode==0)marks.get(3).setProofOnly(true);
            if(mode==1)marks.get(3).setRefutationResult(true);
            if(mode==2)marks.remove(3);
            DoodleConsensusBasis.Analysis a=analyze(board,marks,0);
            verify(a.falses.isEmpty()&&a.truths.size()==1,"hidden/absent common false became evidence");
        }
        marks=branches(805,1,805,2);marks.get(5).setHypothesisGroupKind(1);
        verify(analyze(board,marks,0).truths.isEmpty(),"OR member promoted as individually true");
        marks=branches(805,1,805,2);marks.add(mark(0,3,1,false,1));
        verify(!analyze(board,marks,0).hasConclusions(),"contradictory branch promoted");
        marks=branches(805,1,805,2);marks.add(mark(80,5,2,true,2));marks.add(mark(0,3,2,false,2));marks.add(mark(10,4,1,false,2));
        verify(analyze(board,marks,0).pairs.size()==2&&analyze(board,marks,0).sourceMask(3,2)==7,"alternative supports were lost");
        marks.get(1).setHypothesisStart(false);verify(analyze(board,marks,0).pairs.size()==1,"loss of one alternative revoked remaining support");
        marks.get(6).setHypothesisStart(false);verify(!analyze(board,marks,0).hasConclusions(),"loss of all origins retained result");
        // A batch can empty a peer cell only when placement and deletion are combined.
        board=board();candidates(board,1,3,4);marks=branches(805,1,805,2);marks.remove(3);marks.remove(2);
        for(int group=0;group<2;group++){marks.add(mark(0,3,1,false,group));marks.add(mark(1,4,2,false,group));}
        verify(!analyze(board,marks,0).hasConclusions(),"incompatible batch promoted");
        System.out.println("PASS DoodleConsensusBasisProbe: "+checks+" actual candidate/source/coverage checks");
    }
}
