/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.*;

/** Coverage of explicitly authored single-premise branches. Never searches an inference. */
final class DoodleConsensusBasis {
    enum Coverage { CANDIDATE_TRUE_FALSE, BIVALUE_BOTH_TRUE, BIVALUE_BOTH_FALSE }
    static final class Pair {
        final int firstGroup, secondGroup, firstAtom, secondAtom, firstKind, secondKind;
        final Coverage coverage;
        final Set<Integer> truths, falses;
        Pair(int a,int b,int aa,int ba,int ak,int bk,Coverage coverage,Set<Integer> truths,Set<Integer> falses) {
            firstGroup=a;secondGroup=b;firstAtom=aa;secondAtom=ba;firstKind=ak;secondKind=bk;this.coverage=coverage;
            this.truths=Collections.unmodifiableSet(new TreeSet<Integer>(truths));
            this.falses=Collections.unmodifiableSet(new TreeSet<Integer>(falses));
        }
    }
    static final class Analysis {
        final int group;
        final Set<Integer> truths=new TreeSet<Integer>(), falses=new TreeSet<Integer>();
        final List<Pair> pairs=new ArrayList<Pair>();
        Analysis(int group){this.group=group;}
        boolean hasConclusions(){return !truths.isEmpty()||!falses.isEmpty();}
        boolean selects(DoodleStroke mark){return mark.isStandardCandidateMark()
                &&(mark.getCandidateMarkKind()==1?truths:falses).contains(atom(mark));}
        int sourceMask(int atom,int kind){int mask=0;for(Pair pair:pairs)if((kind==1?pair.truths:pair.falses).contains(atom))mask|=1<<pair.firstGroup|1<<pair.secondGroup;return mask;}
        Set<Integer> protectedAtoms(){Set<Integer> result=new HashSet<Integer>(truths);result.addAll(falses);for(Pair pair:pairs){result.add(pair.firstAtom);result.add(pair.secondAtom);}return result;}
    }
    private static final class Branch {
        DoodleStroke start;int startCount;boolean invalid;
        final Set<Integer> truths=new TreeSet<Integer>(),falses=new TreeSet<Integer>();
    }
    static Analysis analyze(Sudoku2 board,Iterable<DoodleStroke> source,int group){
        Analysis result=new Analysis(group);
        if(board==null||source==null||group<0||group>5)return result;
        Branch[] branches=new Branch[6];for(int i=0;i<6;i++)branches[i]=new Branch();
        List<DoodleStroke> raw=new ArrayList<DoodleStroke>();
        for(DoodleStroke mark:source){
            if(mark==null||mark.isProofOnly()||mark.isRefutationResult()||mark.getThoughtGroup()<0)continue;
            raw.add(mark);Branch branch=branches[mark.getThoughtGroup()];
            if(mark.isHypothesisStart()){
                branch.startCount++;branch.start=mark;
                if(!mark.isStandardCandidateMark()||!current(board,mark)
                        ||mark.getHypothesisGroupKind()!=0||mark.getHypothesisConditionId()!=0)branch.invalid=true;
            }
            if(mark.isStandardCandidateMark()&&current(board,mark)){
                if(mark.getCandidateMarkKind()==2)branch.falses.add(atom(mark));
                else if(mark.getHypothesisGroupKind()!=1)branch.truths.add(atom(mark));
            }
        }
        for(int i=0;i<6;i++)if(branches[i].startCount!=1||DoodleContradictionBasis.analyze(board,raw,i).hasConflict())branches[i].invalid=true;
        Branch active=branches[group];if(active.invalid)return result;
        for(int other=0;other<6;other++){
            if(other==group||branches[other].invalid)continue;
            Branch next=branches[other];Coverage coverage=coverage(board,active.start,next.start);if(coverage==null)continue;
            Set<Integer> truths=new TreeSet<Integer>(active.truths),falses=new TreeSet<Integer>(active.falses);
            truths.retainAll(next.truths);falses.retainAll(next.falses);
            if(truths.isEmpty()&&falses.isEmpty())continue;
            result.pairs.add(new Pair(group,other,atom(active.start),atom(next.start),active.start.getCandidateMarkKind(),next.start.getCandidateMarkKind(),coverage,truths,falses));
            result.truths.addAll(truths);result.falses.addAll(falses);
        }
        if(!compatible(board,result.truths,result.falses)){result.truths.clear();result.falses.clear();result.pairs.clear();}
        return result;
    }
    static Coverage coverage(Sudoku2 board,DoodleStroke a,DoodleStroke b){
        if(a.getAnchorCell()!=b.getAnchorCell())return null;
        int ak=a.getCandidateMarkKind(),bk=b.getCandidateMarkKind();
        if(a.getAnchorDigit()==b.getAnchorDigit())return ak!=bk?Coverage.CANDIDATE_TRUE_FALSE:null;
        int[] candidates=board.getAllCandidates(a.getAnchorCell());
        if(ak!=bk||candidates.length!=2)return null;
        return ak==1?Coverage.BIVALUE_BOTH_TRUE:Coverage.BIVALUE_BOTH_FALSE;
    }
    private static boolean peers(int a,int b){return Sudoku2.getRow(a)==Sudoku2.getRow(b)||Sudoku2.getCol(a)==Sudoku2.getCol(b)||Sudoku2.getBlock(a)==Sudoku2.getBlock(b);}
    private static int unitCell(int unit,int i){return unit<9?unit*9+i:unit<18?i*9+unit-9:(unit-18)/3*27+(unit-18)%3*3+i/3*9+i%3;}
    private static int atom(DoodleStroke mark){return mark.getAnchorCell()*10+mark.getAnchorDigit();}
    private static boolean current(Sudoku2 board,DoodleStroke mark){return DoodleContradictionBasis.currentCandidate(board,mark.getAnchorCell(),mark.getAnchorDigit());}
    /** Check only immediate batch constraints, including ordinary peer effects of the fills. */
    private static boolean compatible(Sudoku2 board,Set<Integer> truths,Set<Integer> falses){
        for(int atom:truths)if(falses.contains(atom))return false;
        int[] values=new int[81],remaining=new int[81];
        for(int cell=0;cell<81;cell++){values[cell]=board.getValue(cell);for(int digit:board.getAllCandidates(cell))remaining[cell]|=1<<digit;}
        for(int atom:falses)remaining[atom/10]&=~(1<<(atom%10));
        for(int atom:truths){int cell=atom/10,digit=atom%10;if(values[cell]!=0&&values[cell]!=digit)return false;values[cell]=digit;}
        for(int cell=0;cell<81;cell++)if(values[cell]!=0)for(int peer=0;peer<81;peer++)if(peer!=cell&&peers(cell,peer)){
            if(values[peer]==values[cell])return false;remaining[peer]&=~(1<<values[cell]);
        }
        for(int cell=0;cell<81;cell++)if(values[cell]==0&&remaining[cell]==0&&board.getAllCandidates(cell).length>0)return false;
        for(int unit=0;unit<27;unit++)for(int digit=1;digit<=9;digit++){
            boolean before=false,after=false;
            for(int i=0;i<9;i++){int cell=unitCell(unit,i);before|=board.getValue(cell)==digit||board.isCandidate(cell,digit);after|=values[cell]==digit||values[cell]==0&&(remaining[cell]&(1<<digit))!=0;}
            if(before&&!after)return false;
        }
        return true;
    }
}
