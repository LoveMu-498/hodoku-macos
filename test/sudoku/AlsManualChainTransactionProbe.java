package sudoku;
import java.lang.reflect.Field;
import java.io.File;
import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;

/** Runs actual Enter preview/apply and Shift input handlers on isolated Swing state. */
public final class AlsManualChainTransactionProbe {
    public static void main(String[] args)throws Exception {try {
        edt(()->{
            ApplicationAppearance.initialize(AppearanceMode.LIGHT);
            f=new MainFrame(null);p=f.getSudokuPanel();p.setSize(720,720);p.setSudoku((String)null);
            p.getSudoku().setSudoku(new String(new char[81]).replace('\0','0'));
            AlsManualChainProbe.restrict(p.getSudoku(),29,"35");AlsManualChainProbe.restrict(p.getSudoku(),38,"135");AlsManualChainProbe.restrict(p.getSudoku(),42,"13");
            p.setShowCandidates(true);GroupedChainInteractionProbe.paint(p);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
            GroupedChainInteractionProbe.click(p,29,3,false);GroupedChainInteractionProbe.click(p,29,5,false);
            GroupedChainInteractionProbe.click(p,38,5,false);GroupedChainInteractionProbe.click(p,38,3,false);GroupedChainInteractionProbe.click(p,42,3,true);
            UserChain c=(UserChain)read("activeUserChain");check(c.getNodes().get(3).cells().length==2,"Shift cross-box grouping failed");
            File out=new File("/tmp/hodoku-als-proof");out.mkdirs();ImageIO.write(GroupedChainInteractionProbe.paint(p),"png",new File(out,"authored.png"));
            return null;
        });
        String before=edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()));
        edt(()->{call("handleReasoningEnter");return null;});await(true);
        check(before.equals(edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()))),"preview changed board");
        edt(()->{ImageIO.write(GroupedChainInteractionProbe.paint(p),"png",new File("/tmp/hodoku-als-proof/preview.png"));return null;});
        edt(()->{call("handleReasoningEnter");return null;});await(false);
        edt(()->{check(!p.getSudoku().isCandidate(36,3)&&!p.getSudoku().isCandidate(37,3),"native apply missed ALS deletion");p.undo();return null;});
        check(before.equals(edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()))),"undo lost candidates");
        edt(()->{
            p.setSudoku((String)null);p.getSudoku().setSudoku(new String(new char[81]).replace('\0','0'));p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
            Field a=SudokuPanel.class.getDeclaredField("activeUserChain");a.setAccessible(true);a.set(p,null);
            GroupedChainInteractionProbe.paint(p);GroupedChainInteractionProbe.click(p,0,3,false);GroupedChainInteractionProbe.click(p,10,3,true);GroupedChainInteractionProbe.click(p,2,3,true);
            check(((UserChain)read("activeUserChain")).getNodes().get(0).cells().length==3,"non-collinear box group rejected");
            ImageIO.write(GroupedChainInteractionProbe.paint(p),"png",new File("/tmp/hodoku-als-proof/box-three.png"));return null;
        });
        System.out.println("ALS Shift grouping, first Enter preview, second Enter native apply, undo and non-collinear group rendering passed");
    }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
