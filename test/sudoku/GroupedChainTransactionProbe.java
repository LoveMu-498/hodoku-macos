package sudoku;
import java.util.concurrent.*;
import java.lang.reflect.*;
import javax.swing.*;

/** The real async Analyze/Apply controller on an isolated candidate fixture. */
public final class GroupedChainTransactionProbe {
    static MainFrame f;static SudokuPanel p;
    static <T>T edt(Callable<T> c)throws Exception{FutureTask<T> t=new FutureTask<T>(c);SwingUtilities.invokeAndWait(t);return t.get();}
    static void call(String method)throws Exception{Method m=SudokuPanel.class.getDeclaredMethod(method);m.setAccessible(true);m.invoke(p);}
    static Object read(String name)throws Exception{Field x=SudokuPanel.class.getDeclaredField(name);x.setAccessible(true);return x.get(p);}
    static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    static void await(boolean proposal)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(edt(()->proposal?read("reasoningProposal")!=null:read("reasoningProposal")==null&&read("reasoningRequest")==null))return;Thread.sleep(30);}throw new AssertionError("transaction timeout");}
    public static void main(String[] args)throws Exception{try {
        edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().setSudoku(new String(new char[81]).replace('\0','0'));
            for(int c:new int[]{9,10,11,18,19,20})p.getSudoku().delCandidate(c,1);
            p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
            UserChain c=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0,1),GroupedChainProbe.node(1,2)},true);c.setActive(true);
            Field a=SudokuPanel.class.getDeclaredField("activeUserChain");a.setAccessible(true);a.set(p,c);call("noteUserChainReasoningChanged");return null;});
        String before=edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()));
        edt(()->{call("handleReasoningEnter");return null;});await(true);
        check(before.equals(edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()))),"preview mutated board");
        edt(()->{p.setSize(720,720);java.awt.image.BufferedImage im=new java.awt.image.BufferedImage(720,720,1);p.paint(im.getGraphics());javax.imageio.ImageIO.write(im,"png",new java.io.File("/tmp/hodoku-group-build/group-proof.png"));p.cancelReasoningFromUi();return null;});
        check(before.equals(edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()))),"cancel mutated board");
        edt(()->{call("handleReasoningEnter");return null;});await(true);
        edt(()->{call("handleReasoningEnter");return null;});await(false);
        check(!before.equals(edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()))),"apply did not execute");
        edt(()->{for(int c=3;c<9;c++)check(!p.getSudoku().isCandidate(c,1),"missing group deletion");p.undo();return null;});
        check(before.equals(edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()))),"undo did not restore board");
        edt(()->{UserChain c=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0,1),GroupedChainProbe.node(1,2)},true);c.setActive(true);Field a=SudokuPanel.class.getDeclaredField("activeUserChain");a.setAccessible(true);a.set(p,c);call("noteUserChainReasoningChanged");call("handleReasoningEnter");return null;});await(true);
        edt(()->{p.getSudoku().delCandidate(3,1);p.reasoningBoardChanged();check(read("reasoningProposal")==null,"stale group proposal retained");return null;});
        System.out.println("Grouped asynchronous preview, cancel, revalidation, native apply and undo passed");
    }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
