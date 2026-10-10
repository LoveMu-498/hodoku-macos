/*
 * Copyright (C) 2026 HoDoKu contributors
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.nio.file.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleContradictionWorkflowProbe.*;

/** Exact user grid, complete explicit P marks, real input/rendering/session/replay and native Enter. */
public final class DoodleUrContradictionProbe {
    static final String PUZZLE="029001006001900200000072139254000000890024060000000428038210000000005603900400810";
    static final String[] CANDIDATES={
        "357 2 9 358 3458 1 57 4578 6", "3567 678 1 9 34568 368 2 4578 457",
        "456 468 56 568 7 2 1 3 9", "2 5 4 13678 368 3678 379 79 17",
        "8 9 37 137 2 4 357 6 15", "167 167 367 357 359 379 4 2 8",
        "4567 3 8 2 1 679 579 4579 457", "147 147 27 78 89 5 6 479 3",
        "9 67 25 4 36 367 8 1 25"};
    static final int[] CORNERS={30,35,39,44}, DIGITS={1,7,7,1};
    static int checks;
    static void verify(boolean condition,String message){checks++;check(condition,message);}
    static Sudoku2 fixture(){
        Sudoku2 board=new Sudoku2();board.setSudoku(PUZZLE);
        int solutions=generator.SudokuGeneratorFactory.getDefaultGeneratorInstance().getNumberOfSolutions(board,1);
        verify(solutions==1,"user fixture is not unique");board.setStatus(solutions);
        for(int row=0;row<9;row++){
            String[] cells=CANDIDATES[row].split(" ");
            for(int col=0;col<9;col++)if(board.getValue(row*9+col)==0){
                int[] keep=new int[cells[col].length()];for(int i=0;i<keep.length;i++)keep[i]=cells[col].charAt(i)-'0';
                DoodleContradictionBasisProbe.candidates(board,row*9+col,keep);
            }
        }
        verify(!board.isCandidate(44,7),"fixture lost the missing-7 case");return board;
    }
    static List<DoodleStroke> branch(){
        List<DoodleStroke> marks=new ArrayList<DoodleStroke>();
        for(int i=0;i<4;i++)marks.add(DoodleContradictionBasisProbe.mark(CORNERS[i],DIGITS[i],1,i==3,0));
        return marks;
    }
    static DoodleContradictionBasis.Analysis analyze(Sudoku2 board,List<DoodleStroke> marks){return DoodleContradictionBasis.analyze(board,marks,0);}
    static void constraints(){
        Sudoku2 board=fixture();List<DoodleStroke> marks=branch();DoodleContradictionBasis.Analysis a=analyze(board,marks);
        verify(a.hasUniquenessConflict()&&a.hasRefutation()&&a.circleWitnesses.equals(new HashSet<Integer>(Arrays.asList(301,357,397))),"unboxed user UR or its three witnesses missed");
        marks.remove(0);verify(!analyze(board,marks).hasUniquenessConflict(),"incomplete true values inferred");marks=branch();
        marks.get(0).setCandidateMarkKind(DoodleStroke.MARK_FALSE_CROSS);verify(!analyze(board,marks).hasUniquenessConflict(),"cross completed a missing true corner");marks=branch();
        marks.get(0).setThoughtGroup(1);verify(!analyze(board,marks).hasUniquenessConflict(),"another color supplied a corner");marks=branch();
        for(DoodleStroke mark:marks){mark.setHypothesisStart(true);mark.setHypothesisGroupKind(1);}
        verify(!analyze(board,marks).hasUniquenessConflict(),"OR became four simultaneous truths");
        marks=branch();marks.get(0).setProofOnly(true);verify(!analyze(board,marks).hasUniquenessConflict(),"readonly source supplied a corner");
        marks=branch();marks.get(0).setRefutationResult(true);verify(!analyze(board,marks).hasUniquenessConflict(),"automatic display supplied a corner");
        marks=branch();for(DoodleStroke mark:marks)mark.setHypothesisStart(false);
        a=analyze(board,marks);verify(a.hasUniquenessConflict()&&!a.hasRefutation(),"no-origin UR fabricated inverse");
        board.setStatus(SudokuStatus.MULTIPLE_SOLUTIONS);verify(!analyze(board,branch()).hasUniquenessConflict(),"multiple-solution status allowed UR");
        board.setStatus(SudokuStatus.INVALID);verify(!analyze(board,branch()).hasUniquenessConflict(),"invalid status allowed UR");
        Sudoku2 blank=DoodleContradictionBasisProbe.board();blank.setStatus(SudokuStatus.VALID);
        // Synthetic topology fixtures isolate geometry; not uniqueness evidence.
        for(int[] corners:new int[][]{{0,1,9,10},{0,3,27,30}}){
            marks=new ArrayList<DoodleStroke>();for(int i=0;i<4;i++)marks.add(DoodleContradictionBasisProbe.mark(corners[i],i==0||i==3?1:7,1,i==3,0));
            verify(!analyze(blank,marks).hasUniquenessConflict(),"one/four-box rectangle counted as UR");
        }
        marks=new ArrayList<DoodleStroke>();for(int i=0;i<4;i++)marks.add(DoodleContradictionBasisProbe.mark(new int[]{0,1,27,28}[i],i==0||i==3?1:7,1,i==3,0));
        verify(analyze(blank,marks).hasUniquenessConflict(),"transposed two-box UR missed");
        marks.get(1).setAnchorDigit(1);verify(!analyze(blank,marks).hasUniquenessConflict(),"non-alternating values counted as UR");
        System.out.println("PASS: exact user candidates, real uniqueness, missing alternate candidate, incomplete/cross/other-color/OR/readonly/virtual/no-origin/status and geometry cases");
    }
    static void setup(boolean falseOrigins)throws Exception{
        p.setSudoku((String)null);p.getSudoku().set(fixture());p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.DOODLE);
        p.getCellZoomPanel().selectPaletteGroup(1);p.setSize(810,810);paint(null);
        if(falseOrigins){mark(44,5,2,1);p.toggleDoodleHypothesisEntry();mark(70,9,2,1);p.finishDoodleHypothesisEntry();mark(44,1,1,1);}
        else mark(44,1,1,1);
        mark(30,1,1,1);mark(35,7,1,1);paint(null);
    }
    static void fourth()throws Exception{ChainEditingProbe.click(1,0,ChainOriginProbe.candidate(39,7));p.flushMappedClick();paint(null);}
    static void boxMarks(boolean add)throws Exception{
        p.setAnnotationTool(AnnotationTool.BOX_SELECTION);p.getCellZoomPanel().selectPaletteGroup(1);
        for(int cell:CORNERS)ChainEditingProbe.click(add?1:3,0,ChainOriginProbe.candidate(cell,1));paint(null);
    }
    static void controller()throws Exception{
        setup(false);verify(!basis().hasRefutation(),"three circles triggered UR");
        boxMarks(true);verify(!basis().hasRefutation()&&!p.hasDoodleConclusionEnterContext(),"S boxes completed a missing true circle or acquired P Enter");
        boxMarks(false);p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(1);
        mark(65,2,1,1);find(65,2,1,1).setConclusionOutlined(true);
        fourth();verify(basis().hasUniquenessConflict()&&automatic().size()==1,"fourth explicit P circle did not create inverse without boxes");
        verify(!find(65,2,1,1).isConclusionOutlined(),"UR retained unrelated blue selection");
        java.awt.image.BufferedImage image=paint("ur-doodle-complete");
        for(int i=0;i<3;i++)verify(pixels(image,ChainOriginProbe.candidate(CORNERS[i],DIGITS[i]),Color.RED)>5,"UR witness lost original red circle");
        verify(ChainOriginProbe.bluePixels(image,ChainOriginProbe.candidate(44,1))>3,"P inverse blue missing");
        verify(ink().stream().noneMatch(s->s.isContradictionWitness()||s.isRefutationResult()),"UR display became raw ink");
        p.undoCurrentAnnotation();paint(null);verify(!basis().hasRefutation()&&find(65,2,1,1).isConclusionOutlined(),"P undo did not restore incomplete branch and previous selection");
        p.redoCurrentAnnotation();paint(null);verify(basis().hasRefutation(),"P redo failed to refresh UR");
        boxMarks(true);verify(basis().hasRefutation()&&!p.hasDoodleConclusionEnterContext(),"S altered P UR or intercepted Enter");
        boxMarks(false);verify(basis().hasRefutation(),"removing S boxes removed UR");
        p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(1);
        p.getSudoku().setStatus(SudokuStatus.MULTIPLE_SOLUTIONS);paint(null);verify(!basis().hasRefutation(),"status cache retained UR");
        p.getSudoku().setStatus(SudokuStatus.VALID);paint(null);verify(basis().hasRefutation(),"status cache failed to restore validated UR");
        call("beginDoodleThoughtPreview");image=paint("ur-held-preview");
        for(int i=0;i<3;i++)verify(pixels(image,ChainOriginProbe.candidate(CORNERS[i],DIGITS[i]),Color.RED)>5,"preview hid UR witness");
        call("clearDoodleThoughtPreview");
        GuiState state=new GuiState();state.setIncludeAnnotations(true);p.getState(state,true);
        SessionSnapshot snapshot=new SessionSnapshot();snapshot.setGuiState(state);
        SessionStore store=new SessionStore(output.resolve("ur-session.xml").toFile());store.save(snapshot);p.setState(store.load().getGuiState());
        p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(1);paint("ur-session-reopened");
        verify(basis().hasRefutation()&&automatic().size()==1,"session did not recompute unboxed UR");
        f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));enter();paint("ur-after-enter");
        verify(!p.getSudoku().isCandidate(44,1)&&p.getSudoku().getValue(44)==0,"P Enter did not delete only start candidate");
        verify(ink().stream().noneMatch(s->s.getThoughtGroup()==1),"UR old branch survived Enter");
        ReplaySession replay=savedReplay();verify(replay.frames().stream().anyMatch(frame->frame.annotations.ink().stream().filter(DoodleStroke::isContradictionWitness).count()==3),"UR replay lost three red witnesses");
        setup(true);fourth();verify(basis().isOrConclusion(),"UR did not refute all-false origins");
        String before=TechniqueStepCatalog.createSignature(p.getSudoku());f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));
        enter();verify(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"UR OR adoption changed board");
        verify(ink().stream().filter(s->!s.isProofOnly()).count()==2,"UR OR adoption retained activity");savedReplay();
        System.out.println("PASS: fourth actual P click, no S dependency, red original circles/blue inverse, selection/P undo/redo, held preview, session reopen, P Enter, cleanup and direct/OR replay import");
    }
    static void nativeEnter()throws Exception{
        edt(()->{setup(false);fourth();f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));f.setSize(1100,950);f.setLocation(70,70);f.setVisible(true);f.toFront();p.requestFocusInWindow();return null;});
        Process activation=new ProcessBuilder("osascript","-e","tell application \"System Events\" to set frontmost of first process whose unix id is "+java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]+" to true").start();verify(activation.waitFor()==0,"native activation failed");
        Robot robot=new Robot();robot.setAutoDelay(35);robot.delay(500);edt(()->{p.requestFocusInWindow();return null;});
        robot.keyPress(KeyEvent.VK_ENTER);robot.waitForIdle();String result=edt(()->{verify(!p.getSudoku().isCandidate(44,1),"native P Enter did not apply UR");return TechniqueStepCatalog.createSignature(p.getSudoku());});
        robot.keyPress(KeyEvent.VK_ENTER);robot.waitForIdle();edt(()->{verify(result.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"held P Enter repeated");return null;});robot.keyRelease(KeyEvent.VK_ENTER);
        System.out.println("PASS: native P-mode Robot Enter and held-key repeat boundary");
    }
    public static void main(String[]args)throws Exception{
        System.setProperty("apple.awt.UIElement","true");DoodleContradictionWorkflowProbe.output=Files.createDirectories(Paths.get(System.getProperty("hodoku.probe.output",System.getProperty("java.io.tmpdir"))));
        try{constraints();edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);controller();return null;});
            if(args.length==0||!"--listeners-only".equals(args[0]))nativeEnter();
            System.out.println("PASS DoodleUrContradictionProbe: "+checks+" checks");
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
