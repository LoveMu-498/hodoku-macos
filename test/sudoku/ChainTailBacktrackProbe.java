package sudoku;

import java.awt.*;
import java.awt.image.BufferedImage;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Panel event regression: a completed chain remains drawable after tail backtracking. */
public final class ChainTailBacktrackProbe {
    static void right() throws Exception { mappedTap(3, 0, point(40)); }
    static void left(int cell) throws Exception { mappedTap(1, 0, point(cell)); }
    static UserChain fixture(boolean closed) {
        UserChain c=GroupedChainProbe.chain(false,new UserChainNode[]{
            GroupedChainProbe.node(1,0),GroupedChainProbe.node(1,9),GroupedChainProbe.node(1,18)},true,false);
        if(closed){c.setClosed(true);c.getStrongRelations().add(true);c.getRelationColors().add(Color.RED);}
        return c;
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("apple.awt.UIElement","true");
        try {
            edt(()->{
                f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);
                p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);
                p.setAnnotationTool(AnnotationTool.FREE_CHAIN);p.setSize(810,810);
                p.paint(new BufferedImage(810,810,1).getGraphics());
                String board=TechniqueStepCatalog.createSignature(p.getSudoku());
                install(fixture(false));p.finishCurrentUserChain();long id=done().get(0).getSourceId();
                right();check(active()!=null&&active().getNodes().size()==2&&done().isEmpty(),"completed chain not reopened");
                check(active().getSourceId()==id&&!p.isNextUserChainStrong(),"source or deleted-edge strength lost");
                p.undoCurrentAnnotation();check(active()==null&&done().size()==1&&done().get(0).getNodes().size()==3,"undo completion state");
                p.redoCurrentAnnotation();check(active()!=null&&active().getNodes().size()==2,"redo cursor");
                left(27);check(active().getNodes().size()==3&&active().getNodes().get(2).getCellIndex()==27&&done().isEmpty(),"next click started new chain");
                right();right();check(active()!=null&&active().getNodes().size()==1,"last edge discarded starting cursor");
                left(36);check(active().getNodes().size()==2,"singleton could not extend");
                right();right();check(active()==null&&done().isEmpty(),"last node not removed");right();
                install(fixture(false));p.finishCurrentUserChain();
                click(3,0,point(0));click(3,0,point(0));
                check(active()==null&&done().size()==1&&done().get(0).getNodes().size()==3,"double-click rollback lost completed state");
                check((Integer)read("preciseChainCandidate")==1,"double right failed to enter precise deletion");
                p.handleEscapeVisualReset();
                install(fixture(true));p.finishCurrentUserChain();right();
                check(active()!=null&&!active().isClosed()&&active().getNodes().size()==3&&active().getStrongRelations().size()==2&&p.isNextUserChainStrong(),"closed chain did not just open");
                right();check(active().getNodes().size()==2&&!p.isNextUserChainStrong(),"second backtrack did not remove tail");
                install(fixture(false));p.finishCurrentUserChain();UserChain older=done().get(0);
                left(40);p.finishCurrentUserChain();right();check(active()==null&&done().size()==1&&done().get(0)==older,"finished singleton skipped into older chain");
                check(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"backtracking changed board");
                return null;
            });
            ChainPreviewSourceChangeProbe.setup();
            edt(()->{p.setSize(810,810);p.paint(new BufferedImage(810,810,1).getGraphics());right();
                ChainPreviewSourceChangeProbe.gone();check(active()!=null&&!active().isClosed(),"preview cancellation lost reopened cursor");return null;});
            System.out.println("PASS: completed/open/closed/singleton backtrack, continuation, strength, source identity, undo/redo, board preservation and preview cancellation");
        }catch(Throwable t){t.printStackTrace();System.exit(1);}
        finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
