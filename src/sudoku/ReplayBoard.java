package sudoku;

import java.util.Arrays;

/** Immutable board payload; never includes transient selection or annotation state. */
public final class ReplayBoard {
    private final int[] values;
    private final boolean[] fixed;
    private final short[] candidates, userCandidates;
    public ReplayBoard(Sudoku2 sudoku) {
        this(sudoku.getValues(), sudoku.getFixed(), sudoku.getCells(), sudoku.getUserCells());
    }
    public ReplayBoard(int[] values, boolean[] fixed, short[] candidates, short[] userCandidates) {
        if (values.length != 81 || fixed.length != 81 || candidates.length != 81 || userCandidates.length != 81)
            throw new IllegalArgumentException("Invalid board dimensions");
        for (int i=0;i<81;i++) if(values[i]<0||values[i]>9||(candidates[i]&~511)!=0||(userCandidates[i]&~511)!=0 || (fixed[i]&&values[i]==0) || (values[i]!=0&&candidates[i]!=0))
            throw new IllegalArgumentException("Invalid board cell");
        this.values=values.clone(); this.fixed=fixed.clone(); this.candidates=candidates.clone(); this.userCandidates=userCandidates.clone();
    }
    public int[] values(){return values.clone();}
    public boolean[] fixed(){return fixed.clone();}
    public short[] candidates(){return candidates.clone();}
    public short[] userCandidates(){return userCandidates.clone();}
    public Sudoku2 toSudoku() {
        Sudoku2 result=new Sudoku2(); result.clearSudoku();
        for(int i=0;i<81;i++) if(values[i]!=0) result.setCell(i,values[i],fixed[i],false);
        result.setCells(candidates.clone()); result.setUserCells(userCandidates.clone());
        result.setFixed(fixed.clone()); result.rebuildInternalData();
        return result;
    }
    public boolean samePuzzle(ReplayBoard other){
        for(int i=0;i<81;i++) if(fixed[i]!=other.fixed[i] || (fixed[i] && values[i]!=other.values[i])) return false;
        return true;
    }
    @Override public boolean equals(Object obj){
        if(!(obj instanceof ReplayBoard))return false; ReplayBoard b=(ReplayBoard)obj;
        return Arrays.equals(values,b.values)&&Arrays.equals(fixed,b.fixed)&&Arrays.equals(candidates,b.candidates)&&Arrays.equals(userCandidates,b.userCandidates);
    }
    @Override public int hashCode(){return Arrays.hashCode(values)*31+Arrays.hashCode(candidates);}
}
