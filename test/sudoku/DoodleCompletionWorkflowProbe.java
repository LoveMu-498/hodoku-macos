/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.nio.file.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleContradictionWorkflowProbe.*;

/** Full legal completion, real display/session/Enter transaction and native input. */
public final class DoodleCompletionWorkflowProbe {
    static final String BASE="000054900004038051003970060039780026025063879678090100040329600300846000006517300";
    static final String SOLUTION="761254983294638751853971264139785426425163879678492135547329618312846597986517342";
    static void fixture(boolean falseStart)throws Exception {
        p.clearDoodlesWithUndo();p.getSudoku().setSudoku(BASE);p.setShowCandidates(true);
        p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(0);p.setSize(810,810);paint(null);
        if(falseStart)mark(0,2,2,0);else mark(7,8,1,0);
        for(int cell=0;cell<81;cell++)if(BASE.charAt(cell)=='0'&&(falseStart||cell!=7))mark(cell,SOLUTION.charAt(cell)-'0',1,0);
        paint(null);
    }
    static void tests()throws Exception {
        fixture(false);int count=(int)BASE.chars().filter(c->c=='0').count();
        check(p.hasDoodleCompletion(),"screenshot completion not recognized");
        java.awt.image.BufferedImage im=paint("completion-blue");
        check(pixels(im,ChainOriginProbe.candidate(7,8),new Color(0x1769FF))>8,"true start lacks blue");
        check(ink().stream().noneMatch(DoodleStroke::isConclusionOutlined),"automatic blue polluted raw/session ink");
        call("beginDoodleThoughtPreview");im=paint("completion-peek");
        check(pixels(im,ChainOriginProbe.candidate(7,8),new Color(0x1769FF))>8,"held preview hides blue");call("clearDoodleThoughtPreview");
        GuiState state=new GuiState();state.setIncludeAnnotations(true);p.getState(state,true);
        SessionSnapshot snapshot=new SessionSnapshot();snapshot.setGuiState(state);
        SessionStore store=new SessionStore(output.resolve("completion-session.xml").toFile());store.save(snapshot);
        p.setState(store.load().getGuiState());p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(0);
        check(p.hasDoodleCompletion()&&ink().stream().noneMatch(DoodleStroke::isConclusionOutlined),"session did not recompute clean automatic selection");
        p.getCellZoomPanel().selectPaletteGroup(1);check(!p.hasDoodleCompletion(),"other color inherited completion");p.getCellZoomPanel().selectPaletteGroup(0);
        DoodleStroke last=find(80,2,1,0);ink().remove(last);
        String before=TechniqueStepCatalog.createSignature(p.getSudoku());enter();
        check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku()))&&!p.hasDoodleCompletion(),"stale completion applied partially");
        ink().add(last);check(p.hasDoodleCompletion(),"restored member failed to revalidate");
        mark(2,2,1,0);check(!p.hasDoodleCompletion(),"two values in one cell accepted");mark(2,2,1,0);
        mark(7,8,2,0);check(!p.hasDoodleCompletion(),"chosen value also crossed accepted");mark(7,8,2,0);
        DoodleStroke start=find(7,8,1,0);start.setHypothesisStart(false);check(!p.hasDoodleCompletion(),"no origin accepted");start.setHypothesisStart(true);
        last.setHypothesisStart(true);check(!p.hasDoodleCompletion(),"multiple OR starts accepted");last.setHypothesisStart(false);
        start.setProofOnly(true);check(!p.hasDoodleCompletion(),"hidden origin counted");start.setProofOnly(false);
        int undo=((Stack<?>)read("undoStack")).size();paint(null);enter();
        check(p.getSudoku().isSolved()&&p.getSudoku().checkSudoku(),"Enter did not fill legal whole grid");
        ReplayController controller=f.getReplayController();
        controller.flushPendingSaves();ReplaySession reopened=ReplayStore.read(controller.directory().resolve(controller.session().id+".hrep"));
        check(reopened.frames().size()==controller.session().frames().size(),"complete solve replay was not persisted");

        for(int cell=0;cell<81;cell++)check(p.getSudoku().getValue(cell)==SOLUTION.charAt(cell)-'0',"wrong chosen value");
        check(((Stack<?>)read("undoStack")).size()==undo+1,"completion is not one board undo");
        check(ink().stream().noneMatch(s->s.getThoughtGroup()==0),"applied branch not cleaned");
        p.undo();check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"one undo did not restore board");
        fixture(true);check(p.hasDoodleCompletion(),"false single start completion refused");
        enter();check(p.getSudoku().isSolved(),"false-start completion did not fill all circles");
        System.out.println("PASS: "+count+" screenshot fills, blue+peek+clean session, stale/mixed/OR/hidden guards, true/false origins, one Enter and one board undo");
    }
    static void nativeTest()throws Exception {
        edt(()->{fixture(false);f.setTitle("HoDoKu isolated complete-branch test");f.setVisible(true);f.toFront();p.requestFocusInWindow();return null;});
        long pid=Long.parseLong(java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]);
        Process activate=new ProcessBuilder("osascript","-e","tell application \"System Events\" to set frontmost of first application process whose unix id is "+pid+" to true").start();check(activate.waitFor()==0,"activate test window failed");
        Robot robot=new Robot();robot.setAutoDelay(30);robot.delay(450);
        edt(()->{f.toFront();p.requestFocusInWindow();return null;});
        long focusEnd=System.currentTimeMillis()+2000;
        while(System.currentTimeMillis()<focusEnd&&!edt(()->p.isFocusOwner()))robot.delay(40);
        check(edt(()->f.isActive()&&p.isFocusOwner()),"test panel lost focus");
        robot.keyPress(KeyEvent.VK_ENTER);robot.keyRelease(KeyEvent.VK_ENTER);
        long end=System.currentTimeMillis()+3000;
        while(System.currentTimeMillis()<end&&!edt(()->p.getSudoku().isSolved()))robot.delay(40);
        check(edt(()->p.getSudoku().isSolved()&&p.getSudoku().checkSudoku()),"native Enter did not finish branch");
        System.out.println("PASS: actual OS Enter via MainFrame dispatcher fills full branch");
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("apple.awt.UIElement","true");output=Paths.get(System.getProperty("hodoku.probe.output","build/doodle-completion-20261005/images"));Files.createDirectories(output);
        boolean passed=false;
        try {
            edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();tests();return null;});
            if(args.length>0&&args[0].equals("--native"))nativeTest();
            passed=true;
        }catch(Throwable ex){ex.printStackTrace();}
        finally{if(f!=null)edt(()->{f.dispose();return null;});}
        System.exit(passed?0:1);
    }
}
