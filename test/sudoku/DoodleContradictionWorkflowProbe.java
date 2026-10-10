/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;

/** Real panel input/controller, rendered pixels, session reopening and replay import on isolated fixtures. */
public final class DoodleContradictionWorkflowProbe {
    static final String PUZZLE = "007806032208035600360002180020300006706080203503264070631400020000623700972008364";
    static Path output;
    @SuppressWarnings("unchecked") static List<DoodleStroke> ink() throws Exception { return (List<DoodleStroke>) read("doodleStrokes"); }
    static DoodleStroke find(int cell, int digit, int kind, int group) throws Exception {
        for (DoodleStroke mark : ink()) if (!mark.isProofOnly() && mark.getAnchorCell() == cell && mark.getAnchorDigit() == digit
                && mark.getCandidateMarkKind() == kind && mark.getThoughtGroup() == group) return mark;
        return null;
    }
    static void mark(int cell, int digit, int kind, int group) throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod("toggleCandidateHypothesisMark", int.class, int.class, int.class, int.class, Color.class);
        method.setAccessible(true); method.invoke(p, cell, digit, kind, group, Options.getInstance().getColoringColors()[group * 2]);
    }
    static DoodleContradictionBasis.Analysis basis() throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod("currentDoodleContradiction"); method.setAccessible(true);
        return (DoodleContradictionBasis.Analysis) method.invoke(p);
    }
    @SuppressWarnings("unchecked") static List<DoodleStroke> automatic() throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod("doodleRefutationMarks", DoodleContradictionBasis.Analysis.class);
        method.setAccessible(true); return (List<DoodleStroke>) method.invoke(p, basis());
    }
    static BufferedImage paint(String name) throws Exception {
        BufferedImage image = new BufferedImage(810, 810, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics(); p.paint(g); g.dispose();
        if (name != null) ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
        return image;
    }
    static void initialize(int group) throws Exception {
        p.flushMappedClick(); call("clearDoodleThoughtPreview");
        p.clearDoodlesWithUndo(); p.getSudoku().setSudoku(PUZZLE);
        DoodleContradictionBasisProbe.candidates(p.getSudoku(), 31, 5, 7);
        p.setShowCandidates(true); p.setAnnotationTool(AnnotationTool.DOODLE);
        p.getCellZoomPanel().selectPaletteGroup(group); p.setSize(810, 810); paint(null);
    }
    static void emptyCell(int group) throws Exception { for (int digit : new int[]{1,7,9}) mark(32, digit, 2, group); }
    static void command(Point point) { ChainEditingProbe.click(1, InputEvent.META_DOWN_MASK, point); p.flushMappedClick(); }
    static void enter() {
        KeyEvent event = new KeyEvent(p, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER, '\n');
        check(p.handleAnnotationKeyPressed(event) && event.isConsumed(), "Enter was not handled as a doodle action");
    }
    static int pixels(BufferedImage image, Point point, Color target) {
        int count = 0;
        for (int y = point.y - 16; y <= point.y + 16; y++) for (int x = point.x - 16; x <= point.x + 16; x++) {
            Color color = new Color(image.getRGB(x, y));
            // At 1x, a thin cross/rim may have no fully opaque pixel. Recognize
            // its actual antialias blend with the light board, not neutral digits.
            double dr=target.getRed()-255,dg=target.getGreen()-255,db=target.getBlue()-255;
            double alpha=((color.getRed()-255)*dr+(color.getGreen()-255)*dg+(color.getBlue()-255)*db)
                    /(dr*dr+dg*dg+db*db);
            if(alpha>=.3&&alpha<=1.1&&Math.abs(color.getRed()-(255+alpha*dr))<15
                    &&Math.abs(color.getGreen()-(255+alpha*dg))<15&&Math.abs(color.getBlue()-(255+alpha*db))<15)count++;
        }
        return count;
    }
    static ReplaySession savedReplay() throws Exception {
        ReplayController replay = f.getReplayController(); replay.persist();
        Path file = replay.directory().resolve(replay.session().id + ".hrep");
        ReplaySession imported = ReplayFiles.importFile(file);
        Files.copy(file, output.resolve("last-conclusion.hrep"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return imported;
    }

    static void sharedEraser() throws Exception {
        initialize(0); mark(32,1,2,0); p.cancelDoodleHypothesisStarts();
        p.getCellZoomPanel().selectPaletteGroup(1);
        mark(31,7,1,1); emptyCell(1); paint(null);
        check(basis().hasRefutation(), "eraser fixture lacks refutation");
        Point point=ChainOriginProbe.candidate(32,1);
        java.lang.reflect.Field radius=SudokuPanel.class.getDeclaredField("doodleEraserRadius");
        radius.setAccessible(true);radius.setFloat(p,.012f);
        ChainEditingProbe.event(java.awt.event.MouseEvent.MOUSE_PRESSED,3,0,point);
        Point dragEnd=new Point(point.x+5,point.y);
        ChainEditingProbe.event(java.awt.event.MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,dragEnd);
        paint("eraser-shadow");
        Method display=SudokuPanel.class.getDeclaredMethod("displayedDoodleContradiction");
        display.setAccessible(true);
        check(!((DoodleContradictionBasis.Analysis)display.invoke(p)).hasRefutation(), "eraser shadow retained stale automatic result");
        check(basis().hasRefutation()&&find(32,1,2,0)!=null&&find(32,1,2,1)!=null, "eraser preview modified raw sources");
        ChainEditingProbe.event(java.awt.event.MouseEvent.MOUSE_RELEASED,3,0,dragEnd); paint(null);
        check(find(32,1,2,0)!=null&&find(32,1,2,1)==null&&!basis().hasRefutation(), "erase removed another source or retained current source");
        p.undoCurrentAnnotation();paint(null);check(basis().hasRefutation()&&find(32,1,2,0)!=null, "eraser undo failed to recompute refutation");
        System.out.println("PASS: actual sweep eraser preview, source-specific removal, no premature raw edits, automatic result withdrawal and undo");
    }

    static void purpleSourceAndPreview() throws Exception {
        initialize(0); mark(32, 1, 2, 0); mark(70, 1, 1, 0);
        command(ChainOriginProbe.candidate(70, 1)); check(find(70,1,1,0).isConclusionOutlined(), "other branch selection setup failed");
        p.getCellZoomPanel().selectPaletteGroup(1); mark(31,7,1,1); mark(27,1,1,1); mark(29,9,1,1); mark(59,7,1,1);
        command(ChainOriginProbe.candidate(59,7)); check(find(59,7,1,1).isConclusionOutlined(), "current manual selection setup failed");
        emptyCell(1); check(basis().hasRefutation(), "R4C6 all-false did not refute R4C5=7");
        check(!find(59,7,1,1).isConclusionOutlined() && find(70,1,1,0).isConclusionOutlined(), "refutation cancellation crossed source ownership");
        check(automatic().size()==1 && automatic().get(0).getCandidateMarkKind()==2 && automatic().get(0).getAnchorCell()==31,
                "wrong automatic inverse of R4C5=7");
        check(ink().stream().noneMatch(DoodleStroke::isRefutationResult), "automatic display became an authored premise");
        BufferedImage normal = paint("purple-normal");
        Point shared = ChainOriginProbe.candidate(32,1), origin = ChainOriginProbe.candidate(31,7);
        check(pixels(normal, shared, Color.RED)>3 && pixels(normal, shared, Options.getInstance().getColoringColors()[0])>3,
                "shared cross lost either the red purple segment or the other original color");
        check(ChainOriginProbe.bluePixels(normal, origin)>8 && pixels(normal, origin, new Color(0,215,45))>5,
                "origin did not retain green identity with inverse blue selection");
        p.undoCurrentAnnotation(); paint(null);
        check(!basis().hasRefutation() && find(59,7,1,1).isConclusionOutlined(), "one undo did not restore the manual selection preceding the contradictory edit");
        p.redoCurrentAnnotation(); paint(null);
        check(basis().hasRefutation() && !find(59,7,1,1).isConclusionOutlined(), "redo did not restore exclusive automatic selection");
        int undoDepth = ((java.util.Stack<?>)read("doodleUndoStack")).size();
        command(ChainOriginProbe.candidate(59,7)); command(origin);
        check(((java.util.Stack<?>)read("doodleUndoStack")).size()==undoDepth && automatic().size()==1,
                "Command changed or created an undo entry for the automatic selection");
        call("beginDoodleThoughtPreview"); BufferedImage preview = paint("purple-preview");
        Object projection = read("doodleThoughtPreviewProjection");
        java.lang.reflect.Field field = projection.getClass().getDeclaredField("filledCells"); field.setAccessible(true);
        check(!((Map<?,?>)field.get(projection)).containsKey(31), "preview hid the refuted origin under a large assumed value");
        check(pixels(preview, shared, Color.RED)>3 && ChainOriginProbe.bluePixels(preview, origin)>8, "held preview hid red witnesses or inverse selection");
        ChainEditingProbe.mappedDouble(1,0,ChainOriginProbe.candidate(32,9)); p.flushMappedClick(); paint(null);
        check(!basis().hasRefutation() && automatic().isEmpty() && !find(59,7,1,1).isConclusionOutlined(),
                "ordinary edit was locked by auto-blue or revived old manual selection");
        check(find(32,9,1,1)==null && find(32,9,2,1)==null, "double-click rollback retained its temporary true circle");
        p.undoCurrentAnnotation(); paint(null); check(basis().hasRefutation(), "editing undo did not re-create the derived result");
        f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));
        int count=f.getReplayController().session().frames().size(); enter(); paint("purple-applied");
        check(!p.getSudoku().isCandidate(31,7) && p.getSudoku().getValue(31)==0 && p.getSudoku().getValue(59)==0 && p.getSudoku().getValue(70)==0,
                "inverse Enter filled a conditional or unrelated blue conclusion");
        check(ink().stream().noneMatch(s->s.getThoughtGroup()==1) && find(32,1,2,0)!=null && find(70,1,1,0).isConclusionOutlined(),
                "applied purple branch or other-source state was cleared incorrectly");
        check(p.getDoodleMarkSourceMask(32,1,2)==1, "purple remained in shared mark composition after apply");
        ReplaySession restored=savedReplay(); check(restored.frames().size()==count+2, "direct inverse did not record one input/result pair");
        ReplayAnnotations input=restored.frames().get(count).annotations;
        check(input.ink().stream().anyMatch(s->s.isHypothesisStart()&&s.getAnchorCell()==31&&s.getThoughtGroup()==1), "reopened replay lost original hypothesis identity");
        check(input.ink().stream().anyMatch(s->s.isRefutationResult()&&s.isConclusionOutlined()&&s.getAnchorCell()==31), "reopened replay lost inverse presentation metadata");
        check(input.ink().stream().anyMatch(s->s.isContradictionWitness()&&s.getThoughtGroup()==1&&s.getColor().equals(Options.getInstance().getColoringColors()[2])),
                "reopened replay lost red witness role or original source color");
        System.out.println("PASS: purple-source red segments, normal/held preview, editing and double rollback, atomic selection undo, exclusive Command, whole-branch cleanup, native replay reopen");
    }

    static void otherInverseCases() throws Exception {
        initialize(0); mark(31,5,2,0); emptyCell(0); paint(null); f.getReplayController().beginSession(new ReplayBoard(p.getSudoku())); enter();
        check(p.getSudoku().getValue(31)==5 && ink().stream().noneMatch(s->s.getThoughtGroup()==0), "single false start inverse did not fill only its origin and clear the branch");
        initialize(0); mark(31,7,1,0); p.toggleDoodleHypothesisEntry(); mark(70,9,1,0); p.finishDoodleHypothesisEntry(); emptyCell(0);
        check(automatic().size()==2 && automatic().stream().allMatch(s->s.getCandidateMarkKind()==2), "true OR inverse did not select all false members");
        paint(null); f.getReplayController().beginSession(new ReplayBoard(p.getSudoku())); enter();
        check(!p.getSudoku().isCandidate(31,7) && !p.getSudoku().isCandidate(70,9) && p.getSudoku().getValue(31)==0 && p.getSudoku().getValue(70)==0,
                "true OR inverse was split or filled as independent truths");
        System.out.println("PASS: single-false fill and complete true-OR deletion through Enter");
    }

    static void orAdoptionAndSession() throws Exception {
        initialize(0); mark(31,5,2,0); p.toggleDoodleHypothesisEntry(); mark(59,7,2,0); p.finishDoodleHypothesisEntry(); emptyCell(0);
        check(basis().isOrConclusion() && automatic().size()==2, "all-false inverse did not form one OR");
        String before=TechniqueStepCatalog.createSignature(p.getSudoku()); paint("or-before-enter");
        f.getReplayController().beginSession(new ReplayBoard(p.getSudoku())); int count=f.getReplayController().session().frames().size();
        int undoDepth=((java.util.Stack<?>)read("doodleUndoStack")).size(); enter(); paint("or-after-enter");
        check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())), "OR adoption changed the board");
        check(((java.util.Stack<?>)read("doodleUndoStack")).size()==undoDepth+1, "OR adoption did not have one annotation undo");
        check(ink().stream().filter(s->!s.isProofOnly()).count()==2 && ink().stream().filter(DoodleStroke::isProofOnly).count()==5,
                "OR adoption retained old activity or lost its readonly source");
        check(ink().stream().filter(s->!s.isProofOnly()).allMatch(s->s.isHypothesisStart()&&s.getHypothesisGroupKind()==1
                &&s.getCandidateMarkKind()==1&&!s.isConclusionOutlined()&&!s.isRefutationResult()), "adopted OR lost green starts or retained blue selection");
        check(!basis().hasConflict(), "captured old all-false source refuted the new OR");
        ReplaySession reopened=savedReplay(); check(reopened.frames().size()==count+2
                &&reopened.frames().get(count).kind.equals("doodle-or-input")&&reopened.last().kind.equals("doodle-or-result"), "OR adoption replay pair was rejected or missing");
        check(reopened.last().annotations.ink().stream().filter(s->s.getThoughtGroup()==0).allMatch(s->s.isHypothesisStart()&&s.getHypothesisGroupKind()==1),
                "reopened OR replay lost its grouped hypothesis identity");
        GuiState state=new GuiState();state.setIncludeAnnotations(true);p.getState(state,true);
        check(state.getDoodleStrokes().stream().noneMatch(s->s.isRefutationResult()||s.isContradictionWitness()), "session persisted transient display flags");
        SessionSnapshot snapshot=new SessionSnapshot(); snapshot.setGuiState(state);
        SessionStore store=new SessionStore(output.resolve("or-session.xml").toFile());store.save(snapshot);
        p.setState(store.load().getGuiState()); paint("or-session-reopened");
        check(!basis().hasConflict() && ink().stream().filter(s->!s.isProofOnly()).count()==2, "SessionStore reopening changed the adopted OR");
        p.setAnnotationTool(AnnotationTool.DOODLE);
        p.undoCurrentAnnotation();paint(null);check(basis().isOrConclusion()&&automatic().size()==2, "reopened annotation undo did not restore old branch and recompute OR");
        p.redoCurrentAnnotation();paint(null);check(!basis().hasRefutation()&&ink().stream().filter(s->!s.isProofOnly()).count()==2, "reopened redo did not restore adopted OR");
        System.out.println("PASS: OR-only Enter, readonly source isolation, one undo, annotation-only replay import, actual SessionStore save/reopen and undo/redo");
    }

    static void nativeEnter() throws Exception {
        edt(()->{initialize(1);mark(31,7,1,1);emptyCell(1);
            f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));
            f.setSize(1100,950);f.setLocation(70,70);f.setVisible(true);f.toFront();p.requestFocusInWindow();return null;});
        Process activation=new ProcessBuilder("osascript","-e",
                "tell application \"System Events\" to set frontmost of first process whose unix id is "
                +java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]+" to true").start();
        check(activation.waitFor()==0,"native activation failed");
        java.awt.Robot robot=new java.awt.Robot();robot.setAutoDelay(35);robot.delay(500);
        edt(()->{p.requestFocusInWindow();return null;});
        robot.waitForIdle();
        java.util.concurrent.atomic.AtomicBoolean lostFocus=new java.util.concurrent.atomic.AtomicBoolean();
        java.awt.event.WindowFocusListener windowWatcher=new java.awt.event.WindowAdapter(){
            @Override public void windowLostFocus(java.awt.event.WindowEvent event){lostFocus.set(true);}
        };
        java.awt.event.FocusListener panelWatcher=new java.awt.event.FocusAdapter(){
            @Override public void focusLost(java.awt.event.FocusEvent event){lostFocus.set(true);}
        };
        java.util.concurrent.Callable<Void> requireFocus=()->{
            check(!lostFocus.get()&&f.isActive()&&f.isFocused()&&p.isFocusOwner(),
                    "native input aborted: isolated frame/panel lost focus [active="+f.isActive()
                    +", focused="+f.isFocused()+", panelFocus="+p.isFocusOwner()
                    +", permanentFocusLoss="+lostFocus.get()+"]");
            return null;
        };
        edt(()->{requireFocus.call();f.addWindowFocusListener(windowWatcher);p.addFocusListener(panelWatcher);return null;});
        boolean enterDown=false;
        try{
            int frames=edt(()->f.getReplayController().session().frames().size());
            edt(requireFocus);enterDown=true;robot.keyPress(KeyEvent.VK_ENTER);robot.waitForIdle();edt(requireFocus);
            String after=edt(()->{check(!p.getSudoku().isCandidate(31,7)&&p.getSudoku().getValue(31)==0,
                    "native Enter did not apply inverse deletion");return TechniqueStepCatalog.createSignature(p.getSudoku());});
            edt(requireFocus);robot.keyPress(KeyEvent.VK_ENTER);robot.waitForIdle();edt(requireFocus);
            edt(requireFocus);robot.keyPress(KeyEvent.VK_ENTER);robot.waitForIdle();edt(requireFocus);
            edt(()->{check(after.equals(TechniqueStepCatalog.createSignature(p.getSudoku()))
                    &&f.getReplayController().session().frames().size()==frames+2,"held native Enter repeated or fell into another operation");return null;});
            edt(requireFocus);robot.keyRelease(KeyEvent.VK_ENTER);enterDown=false;robot.waitForIdle();edt(requireFocus);
        }finally{
            if(enterDown)robot.keyRelease(KeyEvent.VK_ENTER);
            edt(()->{f.removeWindowFocusListener(windowWatcher);p.removeFocusListener(panelWatcher);return null;});
        }
        System.out.println("PASS: native Robot Enter through MainFrame dispatcher; complete inverse deletion and held-key repeat suppression");
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement","true");
        output=Files.createDirectories(Paths.get(System.getProperty("hodoku.probe.output", System.getProperty("java.io.tmpdir"))));
        try { edt(()->{
            ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);
            purpleSourceAndPreview();otherInverseCases();orAdoptionAndSession();sharedEraser();
            System.out.println("PASS DoodleContradictionWorkflowProbe: "+output);return null;
        });
            if(args.length==0||!"--listeners-only".equals(args[0]))nativeEnter();
        } catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        finally {if(f!=null)edt(()->{f.dispose();return null;});}
        System.exit(0);
    }
}
