/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;

/** Reads the file actually re-saved by the unmodified Windows codec, then renders its anchors. */
public final class DoodleReplayCompatibilityProbe {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        java.util.Locale.setDefault(java.util.Locale.SIMPLIFIED_CHINESE);
        System.setProperty("apple.awt.UIElement","true");
        try{
            ReplaySession mac=ReplayFiles.importFile(Paths.get(args[0])),windows=ReplayFiles.importFile(Paths.get(args[1]));
            require(windows.foreignTimeline&&windows.frames().size()==mac.frames().size(),"Windows common frame count");
            for(int i=0;i<mac.frames().size();i++){
                ReplayFrame a=mac.frames().get(i),b=windows.frames().get(i);require(a.board.equals(b.board)&&a.methodStep()==b.methodStep()&&a.label.equals(b.label)&&a.annotations.overlay.equals(b.annotations.overlay),"Windows dropped common data "+i);
            }
            require(windows.last().annotations.doodleStep==null,"pretending Windows preserved the optional native step");
            java.util.List<DoodleStroke> marks=windows.last().annotations.ink();require(marks.size()>=5,"structured circle/cross anchors lost after Windows resave");
            require(marks.stream().anyMatch(s->s.getAnchorCell()==31&&s.getAnchorDigit()==7&&s.getCandidateMarkKind()==2&&s.isConclusionOutlined()),"last applied target metadata lost");
            require(marks.stream().filter(DoodleStroke::isContradictionWitness).count()==3,"red endpoint roles lost");
            require(windows.last().annotations.notice().isEmpty(),"known anchored marks reported as unknown pixels");
            ReplayAnnotations mixed=ReplayAnnotations.fromWindows(windows.last().annotations.overlay+"P -65536 1:2 3:4\n");require(!mixed.notice().isEmpty()&&mixed.ink().size()==marks.size(),"unknown freehand path hid or altered structured marks");
            try{ReplayAnnotations.fromWindows("DM1 999 1 2 0 -1 0.03 0.1 1 0 0 0 2\n");throw new AssertionError("invalid anchor accepted");}catch(java.io.IOException expected){}
            try{ReplayAnnotations.fromWindows("DM1 1 1 2 0 -1 NaN 0.1 1 0 0 0 2\n");throw new AssertionError("invalid geometry accepted");}catch(java.io.IOException expected){}
            edt(()->{
                f=new MainFrame(null);p=f.getSudokuPanel();ReplayBoard live=new ReplayBoard(p.getSudoku());ReplayController c=f.getReplayController();c.openViewer(windows);c.viewer().showFrame(windows.frames().size()-1);
                SudokuPanel panel=c.viewer().boardPanel();panel.setSize(810,810);BufferedImage im=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=im.createGraphics();panel.paint(graphics);graphics.dispose();
                require(ChainOriginProbe.bluePixels(im,DoodleReplayGuiProbe.point(panel,31,7))>3,"Windows-returned target not painted at candidate coordinates");
                String text=c.viewer().hintPanel().getText();require(text.contains("已应用到盘面：删除候选 R4C5(7)"),"Windows-returned explanation lost application");require(text.indexOf("来源：")==text.lastIndexOf("来源："),"common label/CD explanation duplicated");require(new ReplayBoard(p.getSudoku()).equals(live),"foreign replay changed current solve");c.closeViewer();return null;
            });
            System.out.println("PASS exact Windows-reader/writer roundtrip, method flags/type labels, structured circle/cross positions/red/blue, optional step loss, foreign rendering, single explanation, unknown-ink distinction and bad-anchor rejection");
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
