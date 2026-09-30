package sudoku;

import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

/** User's exact pencilmarks: single-cell Box Analyze must find r4c4=6. */
public final class BoxSingleReasoningProbe {
    static final String PM =
        "3457 2 9 358 3458 1 57 4578 6\n"+
        "3457 478 1 9 34568 368 2 4578 457\n"+
        "45 48 6 58 7 2 1 3 459\n"+
        "2 5 4 13678 3689 36789 379 79 179\n"+
        "8 9 37 137 2 4 357 6 157\n"+
        "167 167 37 357 359 379 4 2 8\n"+
        "467 3 8 2 1 679 579 4579 4579\n"+
        "147 147 2 78 89 5 6 479 3\n"+
        "9 67 5 4 36 367 8 1 2";
    static Sudoku2 board() {
        String[] cells=PM.split("\\s+");StringBuilder values=new StringBuilder();
        for(String c:cells)values.append(c.length()==1?c:"0");
        Sudoku2 b=new Sudoku2();b.setSudoku(values.toString());
        for(int i=0;i<81;i++)if(cells[i].length()>1)for(int d=1;d<=9;d++)
            b.setCandidate(i,d,cells[i].indexOf((char)('0'+d))>=0);
        for(int i=0;i<81;i++)if(cells[i].length()>1) {
            StringBuilder actual=new StringBuilder();for(int d:b.getAllCandidates(i))actual.append(d);
            require(cells[i].equals(actual.toString()),"fixture candidates "+i);
        }
        return b;
    }
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static <T>T edt(Callable<T> task)throws Exception{FutureTask<T> f=new FutureTask<>(task);SwingUtilities.invokeAndWait(f);return f.get();}
    static void enter(SudokuPanel panel) {
        for(KeyListener l:panel.getKeyListeners()) l.keyPressed(new KeyEvent(panel,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,KeyEvent.VK_ENTER,KeyEvent.CHAR_UNDEFINED));
        for(KeyListener l:panel.getKeyListeners()) l.keyReleased(new KeyEvent(panel,KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,KeyEvent.VK_ENTER,KeyEvent.CHAR_UNDEFINED));
    }
    static void verifyMatchingBoundaries(Sudoku2 board) {
        solver.SudokuStepFinder finder=solver.SudokuSolverFactory.getDefaultSolverInstance().getStepFinder();
        Sudoku2 last=new Sudoku2();
        last.setSudoku("034678912672195348198342567859761423426853791713924856961537284287419635345286179");
        List<SolutionStep> singles=new ArrayList<>();
        singles.addAll(finder.findAllFullHouses(last));singles.addAll(finder.findAllNakedSingles(last));singles.addAll(finder.findAllHiddenSingles(last));
        Set<SolutionType> types=new HashSet<>();
        for(SolutionStep step:singles) {
            types.add(step.getType());
            require(NativeReasoningMatcher.matchesBoxSelection(step,last,Collections.singleton(0)),"single rejected "+step.getType());
            require(!NativeReasoningMatcher.matchesBoxSelection(step,last,new HashSet<>(Arrays.asList(0,1))),"partial multi-cell single accepted");
            require(!NativeReasoningMatcher.matchesBoxSelection(step,last,Collections.singleton(1)),"unrelated cell accepted");
            Sudoku2 filled=last.clone();filled.setCell(0,5);
            require(!NativeReasoningMatcher.matchesBoxSelection(step,filled,Collections.singleton(0)),"stale filled single accepted");
        }
        require(types.containsAll(Arrays.asList(SolutionType.FULL_HOUSE,SolutionType.NAKED_SINGLE,SolutionType.HIDDEN_SINGLE)),"single coverage");
        int checked=0;
        for(SolutionStep step:finder.findAllLockedCandidates(board)) {
            ReasoningStepIndex index=ReasoningStepIndex.from(step,board);
            require(NativeReasoningMatcher.matchesBoxSelection(step,board,index.premiseCells),"full premise no longer accepted");
            for(int c:index.conclusionCells)if(!index.premiseCells.contains(c)) {
                require(!NativeReasoningMatcher.matchesBoxSelection(step,board,Collections.singleton(c)),"deletion target became proof");checked++;
            }
        }
        require(checked>0,"no deletion boundary checked");
        System.out.println("PASS: three single types, unrelated/multi-cell/stale selection rejection, unchanged exact deletion premise matching");
    }
    static void await(SudokuPanel panel)throws Exception {
        long end=System.currentTimeMillis()+60000;
        while(edt(()->panel.isReasoningAnalysisInProgress())){
            if(System.currentTimeMillis()>end)throw new AssertionError("analysis timeout");Thread.sleep(30);
        }
    }
    public static void main(String[]args)throws Exception {
        MainFrame frame=null;
        try {
            Sudoku2 b=board();
            verifyMatchingBoundaries(b);
            List<SolutionStep> singles=solver.SudokuSolverFactory.getDefaultSolverInstance().getStepFinder().findAllHiddenSingles(b);
            require(singles.stream().anyMatch(s->s.getIndices().contains(30)&&s.getValues().contains(6)),"native hidden single missing");
            System.out.println("Native solver confirms hidden single r4c4=6");
            frame=edt(()->new MainFrame(null));final MainFrame f=frame;
            SudokuPanel p=edt(()->{
                SudokuPanel panel=f.getSudokuPanel();panel.setSudoku((String)null);panel.getSudoku().set(b);
                panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
                panel.setSize(600,600);
                java.awt.Graphics2D graphics=new BufferedImage(600,600,BufferedImage.TYPE_INT_ARGB).createGraphics();
                panel.paint(graphics);graphics.dispose();
                int size=panel.getX(3,4)-panel.getX(3,3),x=panel.getX(3,3)+size/2,y=panel.getY(3,3)+size/2;
                panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,x,y,1,false,MouseEvent.BUTTON1));
                panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,x,y,1,false,MouseEvent.BUTTON1));
                require(panel.getBoxReasoningFootprint().size()==1 && panel.getBoxReasoningFootprint().contains(30),"left click did not box r4c4");
                return panel;
            });
            String before=TechniqueStepCatalog.createSignature(b);
            edt(()->{enter(p);return null;});await(p);
            edt(()->{
                SolutionStep s=p.getStep();
                require(s!=null,"single-cell Box produced no conclusion");
                require(s.getType()==SolutionType.HIDDEN_SINGLE && s.getIndices().equals(Arrays.asList(30)) && s.getValues().equals(Arrays.asList(6)),"wrong preview: "+s);
                require(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"preview mutated board");
                enter(p);return null;
            });await(p);
            edt(()->{
                require(p.getSudoku().getValue(30)==6,"confirmed preview did not apply 6");
                p.undo();require(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"undo did not restore exact pencilmarks");
                return null;
            });
            System.out.println("PASS: exact pencilmarks; Box first Enter previews r4c4=6 without mutation; second Enter revalidates and applies");
        } catch(Throwable t){t.printStackTrace();System.exit(1);}
        finally {if(frame!=null){final MainFrame f=frame;edt(()->{f.dispose();return null;});}}
        System.exit(0);
    }
}
