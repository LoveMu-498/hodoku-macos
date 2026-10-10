/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;

/** User's exact shown candidates; a real FishSolver proof and the actual BOX preview/apply path. */
public final class ReplayFinnedSwordfishProbe {
    static final String[] ROWS={
        "12678 168 1278 126 =5 =4 =9 38 2378",
        "24567 569 247 26 =3 =8 2457 45 =1",
        "12458 158 =3 =9 =7 12 245 =6 2458",
        "146 =3 =9 147 =8 157 45 =2 456",
        "14 =2 =5 134 =6 13 =8 =7 =9",
        "468 =7 48 234 =9 235 =1 345 3456",
        "1578 =4 178 37 =2 =9 =6 1358 3578",
        "=3 159 127 =8 =4 67 257 159 257",
        "2789 89 =6 =5 =1 37 347 3489 23478"};
    static final int[] BASE={18,23,27,30,32,36,39,41};
    static int checks;
    static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Sudoku2 fixture(){
        String[] tokens=String.join(" ",ROWS).split(" ");StringBuilder values=new StringBuilder();for(String t:tokens)values.append(t.startsWith("=")?t.charAt(1):'0');
        Sudoku2 board=new Sudoku2();board.setSudoku(values.toString());
        for(int cell=0;cell<81;cell++)if(!tokens[cell].startsWith("="))for(int digit=1;digit<=9;digit++)board.setCandidate(cell,digit,tokens[cell].indexOf((char)('0'+digit))>=0,false);
        for(int cell:new int[]{4,28,31,44,49,76})board.setIsFixed(cell,false);
        return board;
    }
    static SolutionStep nativeFish(Sudoku2 board){
        solver.SudokuSolver solver=new solver.SudokuSolver();solver.setSudoku(board);
        for(SolutionStep step:solver.getStepFinder().getAllFishes(board,3,3,1,0,null,1,0,false))
            if(step.getType()==SolutionType.FINNED_SWORDFISH&&step.getFins().size()==1&&step.getFins().get(0).getIndex()==19
                    &&step.getCandidatesToDelete().size()==1&&step.getCandidatesToDelete().get(0).getIndex()==0)return step;
        throw new AssertionError("actual FishSolver did not find screenshot's fin R3C2(1) -> R1C1<>1");
    }
    static int blue(BufferedImage image,Point point){int count=0;for(int y=point.y-9;y<=point.y+9;y++)for(int x=point.x-9;x<=point.x+9;x++){Color c=new Color(image.getRGB(x,y));if(c.getBlue()>c.getRed()+30&&c.getBlue()>c.getGreen()+15)count++;}return count;}
    static void colors(SudokuPanel panel,BufferedImage image,String stage)throws Exception{
        require(ReplayBoxCandidatesProbe.red(image,DoodleReplayGuiProbe.point(panel,0,1))>10,stage+": red deletion R1C1(1) missing");
        require(blue(image,DoodleReplayGuiProbe.point(panel,19,1))>10,stage+": blue fin R3C2(1) missing");
        for(int cell:BASE)require(ReplayBoxCandidatesProbe.green(image,DoodleReplayGuiProbe.point(panel,cell,1))>10,stage+": green fish basis missing at "+cell);
    }
    public static void main(String[] args)throws Exception{
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);System.setProperty("apple.awt.UIElement","true");ReplayBoxCandidatesProbe.output=Files.createDirectories(Paths.get(args[0]));
        try{
            Sudoku2 board=fixture();SolutionStep fish=nativeFish(board);require(fish.getValues().equals(Collections.singletonList(1)),"wrong fish digit");
            edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(board);p.setShowCandidates(true);ReplayBoxCandidatesProbe.render(p,"source-board");p.setAnnotationTool(AnnotationTool.BOX_SELECTION);p.getCellZoomPanel().selectPaletteGroup(1);
                for(int cell:BASE)ChainEditingProbe.click(MouseEvent.BUTTON1,0,ChainEditingProbe.point(cell));ChainEditingProbe.click(MouseEvent.BUTTON1,0,ChainEditingProbe.point(19));
                f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));p.previewCurrentReasoning(fish,p.getBoxReasoningFootprint(),null,false);
                require(p.getStep()!=null&&f.getReplayController().session().frames().size()==1,"real selected fish preview failed or prematurely recorded");colors(p,ReplayBoxCandidatesProbe.render(p,"native-preview"),"native");call("handleReasoningEnter");return null;});await(false);
            edt(()->{ReplayController c=f.getReplayController();ReplaySession record=c.session();require(record.frames().size()==4,"actual BOX apply did not record triple");require(!p.getSudoku().isCandidate(0,1),"actual fish deletion missing");
                Path file=ReplayBoxCandidatesProbe.output.resolve("finned-swordfish.hrep");ReplayFiles.exportFile(file,record,c.directory());ReplaySession reopened=ReplayFiles.importFile(file);ReplayBoard live=new ReplayBoard(p.getSudoku());c.openViewer(reopened);
                for(int frame:new int[]{1,2,3}){
                    c.viewer().showFrame(frame);SudokuPanel panel=c.viewer().boardPanel();BufferedImage image=ReplayBoxCandidatesProbe.render(panel,"replay-"+frame);colors(panel,image,"frame "+frame);
                    require(panel.getStep()!=null&&panel.getStep().getType()==SolutionType.FINNED_SWORDFISH,"native technique layer missing");require(panel.getSudoku().isCandidate(0,1)==(frame!=3),"historical highlight changed real candidate state");
                    panel.setShowCandidates(false);colors(panel,ReplayBoxCandidatesProbe.render(panel,"replay-"+frame+"-candidates-off"),"off "+frame);
                }
                c.viewer().showFrame(0);require(c.viewer().boardPanel().getStep()==null,"technique leaked into initial board");require(new ReplayBoard(p.getSudoku()).equals(live),"viewer altered live attempt");c.closeViewer();
                Path legacy=ReplayBoxCandidatesProbe.output.resolve("finned-extension4.hrep");ReplayBoxCandidatesProbe.legacyFile(legacy,reopened);ReplaySession old=ReplayFiles.importFile(legacy);c.openViewer(old);c.viewer().showFrame(1);colors(c.viewer().boardPanel(),ReplayBoxCandidatesProbe.render(c.viewer().boardPanel(),"legacy-source"),"legacy source");c.closeViewer();
                c.branchFromFrame(old,1);require(p.getStep()==null&&p.getSudoku().isCandidate(0,1),"shown source proof applied to editable branch");require(c.session().frames().get(0).annotations.proofCandidateAtoms().isEmpty(),"source branch inherited public proof colors");
                System.out.println("PASS ReplayFinnedSwordfishProbe: "+checks+" exact screenshot candidates, actual FishSolver/BOX apply, all eight green bases/blue fin/red target, three frames and candidates-off checks; "+ReplayBoxCandidatesProbe.output);return null;});
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
