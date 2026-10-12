/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
/** Actual Windows re-save retains shared fin roles without reconstructing a native proof. */
public final class ReplayFinnedWindowsProbe {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);System.setProperty("apple.awt.UIElement","true");
        try{
            ReplaySession nativeRecord=ReplayFiles.importFile(Paths.get(args[0])),foreign=ReplayFiles.importFile(Paths.get(args[1]));
            check(foreign.foreignTimeline&&foreign.frames().size()==nativeRecord.frames().size(),"Windows record schema");
            for(int i=0;i<foreign.frames().size();i++){ReplayFrame a=nativeRecord.frames().get(i),b=foreign.frames().get(i);check(a.board.equals(b.board)&&a.annotations.overlay.equals(b.annotations.overlay)&&a.label.equals(b.label)&&a.methodStep()==b.methodStep(),"Windows common data changed");check(b.evidence().length==0,"inventing a native proof after tail loss");}
            try{ReplayAnnotations.fromWindows("PF1 191 FOO\n");throw new AssertionError("invalid fin role accepted");}catch(java.io.IOException expected){}
            try{ReplayAnnotations.fromWindows("PF1 999 FIN\n");throw new AssertionError("invalid fin atom accepted");}catch(java.io.IOException expected){}
            ReplayAnnotations endo=ReplayAnnotations.fromWindows("PF1 191 ENDO_FIN\nC 19 1\n");check(endo.proofFinAtoms(true).contains(191)&&endo.proofFinAtoms(false).isEmpty(),"endo fin role lost");
            edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();ReplayBoard live=new ReplayBoard(p.getSudoku());ReplayController c=f.getReplayController();c.openViewer(foreign);ReplayBoxCandidatesProbe.output=Files.createDirectories(Paths.get(args[2]));
                for(int frame:new int[]{1,2,3}){c.viewer().showFrame(frame);SudokuPanel panel=c.viewer().boardPanel();panel.setShowCandidates(false);BufferedImage image=ReplayBoxCandidatesProbe.render(panel,"windows-fish-"+frame);ReplayFinnedSwordfishProbe.colors(panel,image,"Windows "+frame);check(panel.getStep()==null,"native proof reconstructed from colored atoms");check(panel.getSudoku().isCandidate(0,1)==(frame!=3),"public roles changed board state");check(c.viewer().hintPanel().getText().contains("Finned Swordfish"),"Windows step description missing");}
                c.viewer().showFrame(0);check(c.viewer().boardPanel().getStep()==null,"initial native proof invented");check(new ReplayBoard(p.getSudoku()).equals(live),"foreign view changed live board");c.closeViewer();return null;});
            System.out.println("PASS exact Windows reader/writer data; all three BOX frames show eight green bases/blue fin/red target with candidates off; fin/endo roles validated; native proof tail loss and live isolation preserved");
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
