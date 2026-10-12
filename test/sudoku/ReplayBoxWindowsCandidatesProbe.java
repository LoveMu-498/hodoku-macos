/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;

/** Reopens a file actually re-saved by the unmodified Windows codec, with no Mac tail. */
public final class ReplayBoxWindowsCandidatesProbe {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);System.setProperty("apple.awt.UIElement","true");
        try{
            ReplaySession mac=ReplayFiles.importFile(Paths.get(args[0])),windows=ReplayFiles.importFile(Paths.get(args[1]));
            require(windows.foreignTimeline&&mac.frames().size()==windows.frames().size(),"Windows frame schema changed");
            for(int i=0;i<mac.frames().size();i++){
                ReplayFrame a=mac.frames().get(i),b=windows.frames().get(i);
                require(a.board.equals(b.board)&&a.label.equals(b.label)&&a.methodStep()==b.methodStep()&&a.elapsedMillis==b.elapsedMillis&&a.annotations.overlay.equals(b.annotations.overlay),"Windows common data changed at "+i);
                require(b.evidence().length==0,"pretending Windows kept native proof tail");
            }
            final SolutionStep step=ReplayEvidence.proof(mac.frames().get(2).evidence());final Candidate target=step.getCandidatesToDelete().get(0);
            edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();ReplayBoard live=new ReplayBoard(p.getSudoku());ReplayController c=f.getReplayController();c.openViewer(windows);ReplayBoxCandidatesProbe.output=Files.createDirectories(Paths.get(args[2]));
                for(int index:new int[]{2,3}){
                    c.viewer().showFrame(index);SudokuPanel panel=c.viewer().boardPanel();panel.setShowCandidates(false);BufferedImage image=ReplayBoxCandidatesProbe.render(panel,"windows-"+index);
                    require(ReplayBoxCandidatesProbe.red(image,DoodleReplayGuiProbe.point(panel,target.getIndex(),target.getValue()))>10,"Windows-returned red target hidden at "+index);
                    require(ReplayBoxCandidatesProbe.green(image,DoodleReplayGuiProbe.point(panel,75,6))>10,"Windows-returned premise hidden at "+index);
                    require(panel.getStep()==null,"invented a native proof from common colors");
                    require(c.viewer().hintPanel().getText().contains(step.getStepName()),"Windows-returned technique explanation missing");
                    if(index==3)require(!panel.getSudoku().isCandidate(target.getIndex(),target.getValue()),"Windows-returned board restored deleted candidate");
                }
                c.viewer().showFrame(1);BufferedImage input=ReplayBoxCandidatesProbe.render(c.viewer().boardPanel(),"windows-input");require(ReplayBoxCandidatesProbe.red(input,DoodleReplayGuiProbe.point(c.viewer().boardPanel(),target.getIndex(),target.getValue()))>10,"Windows BOX source frame omitted proof candidates");
                require(new ReplayBoard(p.getSudoku()).equals(live),"foreign view mutated live attempt");c.closeViewer();return null;});
            System.out.println("PASS exact Windows reader/writer common roundtrip; proof tail loss accepted; premise/red removed candidate and technique explanation visible in both frames with candidates off; live state unchanged");
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
