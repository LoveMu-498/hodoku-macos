package sudoku;

import java.util.*;
import solver.Als;
import solver.SearchCancellation;
import solver.SudokuStepFinder;

/** Read-only bridge from authored digit propositions to native ALS premises.
 * An ALS link proves at least one endpoint, never mutual exclusion.
 */
final class NativeAlsStrongLinks {
    private static final ThreadLocal<NativeAlsStrongLinks> CACHE = new ThreadLocal<>();
    private final String signature;
    private final List<Als> alses;
    private NativeAlsStrongLinks(Sudoku2 board,String signature) {
        this.signature=signature;
        SudokuStepFinder finder=new SudokuStepFinder(true);
        try(SearchCancellation.Scope scope=SearchCancellation.enable()) {
            finder.setSudoku(board.clone());
            alses=new ArrayList<>(finder.getAlses(true));
        }
    }
    static NativeAlsStrongLinks forBoard(Sudoku2 board) {
        StringBuilder key=new StringBuilder();
        for(int c=0;c<81;c++)key.append(board.getValue(c)).append(':').append(board.getCell(c)).append(';');
        String signature=key.toString();NativeAlsStrongLinks cached=CACHE.get();
        if(cached==null || !cached.signature.equals(signature)) {
            cached=new NativeAlsStrongLinks(board,signature);CACHE.set(cached);
        }
        return cached;
    }
    Als find(UserChainNode a,UserChainNode b) {
        if(a.getCandidate()==b.getCandidate() || !a.validShape() || !b.validShape())return null;
        Als best=null;
        for(Als als:alses) {
            if(matches(als.indicesPerCandidat[a.getCandidate()],a.cells())
                    && matches(als.indicesPerCandidat[b.getCandidate()],b.cells())
                    && (best==null||als.indices.size()<best.indices.size()))best=als;
        }
        return best;
    }
    private static boolean matches(SudokuSet positions,int[] cells) {
        if(positions==null||positions.size()!=cells.length)return false;
        for(int cell:cells)if(!positions.contains(cell))return false;
        return true;
    }
}
