package sudoku;
import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
/** Real clipboard/timer boundary, with isolated application state. */
public final class ChainTextPasteWindowProbe {
    public static void main(String[] args)throws Exception{
        Clipboard clipboard=Toolkit.getDefaultToolkit().getSystemClipboard();Transferable previous=clipboard.getContents(null);
        try{
            String text=edt(()->{
                f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().setSudoku(new String(new char[81]).replace('\0','0'));
                UserChain c=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0),GroupedChainProbe.node(1,9)},true);
                String result=ChainTextCodec.format(p.getSudoku(),Arrays.asList(c),null);p.getSudoku().delCandidate(55,9);return result;
            });
            clipboard.setContents(new StringSelection(text),null);edt(()->{f.pasteText(text,true);return null;});
            Thread.sleep(3200);
            edt(()->{f.pasteText(text,true);check(!p.getSudoku().isCandidate(55,9),"expired repeat replaced board");return null;});
            clipboard.setContents(new StringSelection("different clipboard"),null);Thread.sleep(250);
            clipboard.setContents(new StringSelection(text),null);Thread.sleep(150);
            edt(()->{f.pasteText(text,true);check(!p.getSudoku().isCandidate(55,9),"changed clipboard did not cancel replacement");
                f.sudokuStateChanged();f.pasteText(text,true);check(!p.getSudoku().isCandidate(55,9),"board event did not cancel replacement");
                f.pasteText(text,true);check(p.getSudoku().isCandidate(55,9),"fresh deliberate repeat failed");return null;});
            System.out.println("Repeat paste expiry, clipboard-change cancellation and board-event cancellation passed");
        }finally{if(f!=null)edt(()->{f.dispose();return null;});if(previous!=null)clipboard.setContents(previous,null);}
        System.exit(0);
    }
}
