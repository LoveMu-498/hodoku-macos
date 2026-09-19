package sudoku;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.awt.Toolkit;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
public final class ChainTextInteractionProbe {
    public static void main(String[] args)throws Exception{try{
        edt(()->{
            f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().setSudoku(new String(new char[81]).replace('\0','0'));
            UserChain chain=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0),GroupedChainProbe.node(1,9)},true);
            String text=ChainTextCodec.format(p.getSudoku(),Arrays.asList(chain),null);
            f.pasteText(text,true);check(p.getStep()!=null,"temporary preview absent");
            check(p.currentReasoningChains().isEmpty(),"paste changed authored chains");
            p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_ENTER,'\n'));
            check(p.getStep()==null&&p.currentReasoningChains().size()==1,"Enter did not adopt temporary chain");
            check(p.copyChainText().equals(text),"copy all authored chains differs");
            SolutionStep claims=new SolutionStep(SolutionType.AIC);claims.addCandidateToDelete(20,4);claims.addIndex(30);claims.addValue(5);
            String claimed=ChainTextCodec.format(p.getSudoku(),Arrays.asList(chain),claims);
            String before=TechniqueStepCatalog.createSignature(p.getSudoku());
            f.pasteText(claimed,true);check(p.getStep()!=null&&p.getStep().getValues().contains(5),"external conclusion not previewed");
            check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"paste applied conclusion early");
            p.setSize(720,720);javax.imageio.ImageIO.write(GroupedChainInteractionProbe.paint(p),"png",new java.io.File("/tmp/hodoku-chain-text-preview.png"));
            p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_ENTER,'\n'));
            check(p.getSudoku().getValue(30)==5&&!p.getSudoku().isCandidate(20,4),"mixed conclusions not applied exactly");
            p.undo();check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"undo did not restore imported application");
            f.pasteText(claimed,true);String copy=p.copyChainText();
            f.pasteText(claimed+"\nnot a chain",true);check(copy.equals(p.copyChainText()),"malformed paste replaced existing preview");
            p.getSudoku().delCandidate(55,9);f.sudokuStateChanged();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text),null);
            f.pasteText(text,true);check(!p.getSudoku().isCandidate(55,9),"first mismatch changed puzzle");
            f.pasteText(text,true);check(p.getSudoku().isCandidate(55,9)&&p.getStep()!=null,"repeat paste failed to import snapshot");
            check(!f.getSavePoints().isEmpty(),"replacement lost recovery savepoint");
            f.setState(f.getSavePoints().get(f.getSavePoints().size()-1));
            check(!p.getSudoku().isCandidate(55,9),"recovery savepoint failed to restore previous candidates");
            System.out.println("Chain text paste/adopt, preview/apply/undo, malformed no-op and repeated-paste checks passed");
            return null;
        });
    }finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
