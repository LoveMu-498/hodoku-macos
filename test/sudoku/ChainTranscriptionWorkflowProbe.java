/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;

/** Real panel keys, authored analysis, P editing, independent undo and session restore. */
public final class ChainTranscriptionWorkflowProbe {
    private static MainFrame frame;
    private static SudokuPanel panel;
    private static Path output;
    private static int checks;
    private static final int TRUE = DoodleStroke.MARK_TRUE_CIRCLE;
    private static final int FALSE = DoodleStroke.MARK_FALSE_CROSS;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static <T> T edt(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<T>(callable);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
    private static Object read(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static Object read(String name) throws Exception { return read(panel, name); }
    private static void write(String name, Object value) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(panel, value);
    }
    private static void call(String name) throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(panel);
    }
    @SuppressWarnings("unchecked")
    private static List<DoodleStroke> ink() throws Exception { return (List<DoodleStroke>) read("doodleStrokes"); }
    @SuppressWarnings("unchecked")
    private static List<UserChain> chains() throws Exception { return (List<UserChain>) read("userChains"); }
    private static int depth(String name) throws Exception { return ((Stack<?>) read(name)).size(); }
    private static String board() { return TechniqueStepCatalog.createSignature(panel.getSudoku()); }
    private static String otherAnnotations() throws Exception {
        return read("coloringMap").toString() + read("coloringCandidateMap").toString() + read("boxReasoningGroups").toString();
    }
    private static void paint(String name) throws Exception {
        BufferedImage image = new BufferedImage(810, 810, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        if (name != null) javax.imageio.ImageIO.write(image, "png", output.resolve(name + ".png").toFile());
    }
    private static Point point(int cell, int digit) throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod("getCandKoord", int.class, int.class, int.class);
        method.setAccessible(true);
        Point2D at = (Point2D) method.invoke(panel, cell, digit, (Integer) read("cellSize"));
        return new Point((int) Math.round(at.getX()), (int) Math.round(at.getY()));
    }
    private static void click(Point point, int modifiers) {
        for (int id : new int[] { MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED })
            panel.dispatchEvent(new MouseEvent(panel, id, System.currentTimeMillis(), modifiers,
                    point.x, point.y, 1, false, MouseEvent.BUTTON1));
        panel.flushMappedClick();
    }
    private static void mark(int cell, int digit, int kind) throws Exception {
        click(point(cell, digit), kind == FALSE ? InputEvent.SHIFT_DOWN_MASK : 0);
    }
    private static void key(int code, int modifiers) {
        KeyEvent pressed = new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                modifiers, code, code == KeyEvent.VK_ENTER ? '\n' : KeyEvent.CHAR_UNDEFINED);
        check(panel.handleAnnotationKeyPressed(pressed), "annotation key was not handled: " + code);
        panel.handleAnnotationToolKeyReleased(new KeyEvent(panel, KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), modifiers, code, KeyEvent.CHAR_UNDEFINED));
    }
    private static DoodleStroke find(int cell, int digit, int kind, int group, boolean start) throws Exception {
        for (DoodleStroke stroke : ink()) if (!stroke.isProofOnly() && stroke.isStandardCandidateMark()
                && stroke.getAnchorCell() == cell && stroke.getAnchorDigit() == digit
                && stroke.getCandidateMarkKind() == kind && stroke.getThoughtGroup() == group
                && stroke.isHypothesisStart() == start) return stroke;
        return null;
    }
    private static Set<Integer> atoms(int group, int kind, boolean start) throws Exception {
        Set<Integer> values = new TreeSet<Integer>();
        for (DoodleStroke stroke : ink()) if (!stroke.isProofOnly() && stroke.isStandardCandidateMark()
                && stroke.getThoughtGroup() == group && stroke.getCandidateMarkKind() == kind
                && stroke.isHypothesisStart() == start)
            values.add(stroke.getAnchorCell() * 10 + stroke.getAnchorDigit());
        return values;
    }
    private static List<String> sourceFrames(int group) throws Exception {
        List<String> frames = new ArrayList<String>();
        for (DoodleStroke stroke : ink()) if (stroke.isProofOnly() && stroke.getThoughtGroup() == group) {
            StringBuilder frame = new StringBuilder();
            frame.append(stroke.getColor().getRGB()).append('|').append(stroke.getWidthFactor())
                    .append('|').append(stroke.getAnchorCell()).append('|').append(stroke.getAnchorDigit())
                    .append('|').append(stroke.getCandidateMarkKind()).append('|').append(stroke.getThoughtGroup())
                    .append('|').append(stroke.isHypothesisStart()).append('|').append(stroke.getHypothesisConditionId())
                    .append('|').append(stroke.getHypothesisGroupKind()).append('|').append(stroke.getDerivedSourceId())
                    .append('|').append(stroke.isProofOnly()).append('|').append(stroke.isConclusionOutlined())
                    .append('|').append(stroke.getConclusionSourceMask()).append('|').append(stroke.isContradictionWitness())
                    .append('|').append(stroke.isRefutationResult());
            for (DoodlePoint point : stroke.getPoints()) frame.append('|').append(Double.toHexString(point.getX()))
                    .append(',').append(Double.toHexString(point.getY()));
            frames.add(frame.toString());
        }
        Collections.sort(frames);
        return frames;
    }
    private static UserChain ring() {
        return GroupedChainProbe.chain(true, new UserChainNode[] {
                GroupedChainProbe.node(1, 0), GroupedChainProbe.node(1, 1), GroupedChainProbe.node(1, 9)
        }, true, false, true);
    }
    private static void reset() throws Exception {
        panel.setReplayReadOnly(false);
        panel.setSudoku((String) null);
        panel.getSudoku().set(GroupedChainProbe.blank());
        panel.setShowCandidates(true);
        panel.setSize(810, 810);
        panel.setAnnotationTool(AnnotationTool.DOODLE);
        panel.getCellZoomPanel().selectPaletteGroup(2);
        panel.setAnnotationTool(AnnotationTool.FREE_CHAIN);
        panel.getCellZoomPanel().selectPaletteGroup(0);
        UserChain chain = ring();
        chain.setActive(true);
        write("activeUserChain", chain);
        call("noteUserChainReasoningChanged");
        paint(null);
    }
    private static void awaitPreview() throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (edt(() -> read("reasoningProposal") != null)) return;
            Thread.sleep(25);
        }
        throw new AssertionError("authored chain preview timed out");
    }
    private static void preview() throws Exception {
        edt(() -> { key(KeyEvent.VK_ENTER, 0); return null; });
        awaitPreview();
    }
    private static void prepare() throws Exception {
        preview();
        edt(() -> {
            String before = board();
            check(panel.hasChainTranscriptionShortcutContext(), "valid L preview did not enable transcription");
            key(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
            check(panel.hasPendingChainTranscription(), "Shift+Enter did not prepare transcription");
            check(panel.getAnnotationTool() == AnnotationTool.DOODLE, "entry did not select P");
            check(panel.getCellZoomPanel().getPaletteGroup() == 2, "entry copied L palette into P");
            check(before.equals(board()) && !chains().isEmpty(), "entry mutated board or consumed source");
            return null;
        });
    }
    private static UserChainValidator.Result validation() throws Exception {
        return (UserChainValidator.Result) read(read(read("reasoningProposal"), "request"), "replayValidation");
    }
    private static Set<Integer> conclusionAtoms(UserChainValidator.Result result, boolean truth) {
        Set<Integer> values = new TreeSet<Integer>();
        for (SolutionStep step : result.steps) {
            if (truth) for (int i = 0; i < step.getIndices().size() && i < step.getValues().size(); i++)
                if (step.isAuthoredPlacement()) values.add(step.getIndices().get(i) * 10 + step.getValues().get(i));
            if (!truth) for (Candidate candidate : step.getCandidatesToDelete())
                values.add(candidate.getIndex() * 10 + candidate.getValue());
        }
        return values;
    }
    private static void batchAndUndo() throws Exception {
        edt(() -> {
            reset(); panel.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);
            panel.getCellZoomPanel().selectPaletteGroup(2); click(point(40, 5), 0);
            panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
            panel.getCellZoomPanel().selectPaletteGroup(2); click(point(41, 5), 0);
            panel.setAnnotationTool(AnnotationTool.DOODLE);
            mark(55, 9, FALSE); mark(56, 8, TRUE);
            DoodleStroke free = new DoodleStroke(panel.getCellZoomPanel().getPrimaryColor(), .008f);
            free.setThoughtGroup(2); free.getPoints().add(new DoodlePoint(.2, .7));
            free.getPoints().add(new DoodlePoint(.3, .75)); ink().add(free);
            panel.getCellZoomPanel().selectPaletteGroup(1); mark(55, 9, FALSE);
            panel.getCellZoomPanel().selectPaletteGroup(2);
            panel.setAnnotationTool(AnnotationTool.FREE_CHAIN);
            return null;
        });
        prepare();
        edt(() -> {
            UserChainValidator.Result expected = validation();
            check(expected.status == UserChainValidator.Status.ASSUMED, "fixture stopped exercising assumed authored relations");
            check(expected.steps.size() > 1, "fixture did not produce multiple native result steps");
            Set<Integer> trueAtoms = conclusionAtoms(expected, true), falseAtoms = conclusionAtoms(expected, false);
            check(!trueAtoms.isEmpty() && falseAtoms.size() > 1, "fixture lacks a full mixed conclusion batch");
            int undoBefore = depth("doodleUndoStack"), chainUndoBefore = depth("userChainUndoStack");
            String before = board();
            String otherBefore = otherAnnotations();
            key(KeyEvent.VK_ENTER, 0);
            check(!panel.hasPendingChainTranscription() && panel.getAnnotationTool() == AnnotationTool.DOODLE,
                    "successful Enter retained pending state or left P");
            check(before.equals(board()), "transcription applied to the true Sudoku");
            check(atoms(2, TRUE, false).equals(trueAtoms) && atoms(2, FALSE, false).equals(falseAtoms),
                    "did not transcribe the complete native result batch");
            check(find(55, 9, FALSE, 2, true) != null && find(56, 8, TRUE, 2, false) == null,
                    "batch removed start or retained old non-start");
            check(ink().stream().noneMatch(s -> s.getThoughtGroup() == 2 && !s.isCandidateAnchored()),
                    "batch retained current-group freehand");
            check(find(55, 9, FALSE, 1, true) != null, "batch modified another P group");
            check(otherBefore.equals(otherAnnotations()), "batch changed same-color T or S annotations");
            check(panel.currentReasoningChains().isEmpty(), "successful batch did not consume source chain");
            check(depth("doodleUndoStack") == undoBefore + 1 && depth("userChainUndoStack") == chainUndoBefore + 1,
                    "commit did not create one separate P and L undo");
            check(read("reasoningProposal") == null && panel.getStep() == null, "commit retained executable preview");
            paint("batch-result");
            panel.undoCurrentAnnotation();
            check(find(56, 8, TRUE, 2, false) != null && ink().stream().anyMatch(s -> s.getThoughtGroup() == 2 && !s.isCandidateAnchored()),
                    "one P undo did not restore whole old batch");
            check(panel.currentReasoningChains().isEmpty() && !panel.hasPendingChainTranscription(),
                    "P undo restored L or pending authorization");
            panel.redoCurrentAnnotation();
            check(atoms(2, TRUE, false).equals(trueAtoms) && atoms(2, FALSE, false).equals(falseAtoms)
                    && !panel.hasPendingChainTranscription(), "P redo lost batch or revived authorization");
            panel.setAnnotationTool(AnnotationTool.FREE_CHAIN); panel.undoCurrentAnnotation();
            check(panel.currentReasoningChains().size() == 1 && read("reasoningProposal") == null
                    && !panel.hasPendingChainTranscription(), "L undo did not restore only original chain");
            panel.redoCurrentAnnotation();
            check(panel.currentReasoningChains().isEmpty() && !panel.hasPendingChainTranscription(), "L redo revived old task");
            check(before.equals(board()), "independent undo/redo changed the true board");
            return null;
        });
    }
    private static void invalidStartAndEditing() throws Exception {
        edt(() -> { reset(); return null; }); prepare();
        edt(() -> {
            String before = board(); int pUndo = depth("doodleUndoStack"), lUndo = depth("userChainUndoStack");
            key(KeyEvent.VK_ENTER, 0);
            check(panel.hasPendingChainTranscription() && before.equals(board()) && !chains().isEmpty(),
                    "empty start Enter committed or applied old preview");
            check(depth("doodleUndoStack") == pUndo && depth("userChainUndoStack") == lUndo,
                    "empty start Enter changed undo history");
            mark(55, 9, FALSE); panel.toggleDoodleHypothesisEntry(); mark(56, 8, FALSE);
            check(atoms(2, FALSE, true).size() == 2 && panel.hasPendingChainTranscription(),
                    "multiple starts applied immediately or were not accepted");
            panel.undoCurrentAnnotation();
            check(atoms(2, FALSE, true).size() == 1 && panel.hasPendingChainTranscription(), "preparing P undo canceled task");
            panel.redoCurrentAnnotation();
            check(atoms(2, FALSE, true).size() == 2 && panel.hasPendingChainTranscription(), "preparing P redo canceled task");
            key(KeyEvent.VK_ENTER, 0);
            check(!panel.isDoodleHypothesisEntryActive() && !panel.hasPendingChainTranscription()
                    && atoms(2, FALSE, true).size() == 2 && before.equals(board()),
                    "final Enter did not finish G and preserve the complete multi-start condition");
            return null;
        });
    }
    private static void contradictionAndOrRole() throws Exception {
        edt(() -> { reset(); return null; }); prepare();
        edt(() -> {
            mark(0, 1, FALSE); String before = board(); key(KeyEvent.VK_ENTER, 0); paint("refuted-start");
            check(find(0, 1, FALSE, 2, true) != null && find(0, 1, TRUE, 2, false) != null,
                    "opposite chain result replaced start or was filtered");
            Method method = SudokuPanel.class.getDeclaredMethod("currentDoodleContradiction"); method.setAccessible(true);
            DoodleContradictionBasis.Analysis analysis = (DoodleContradictionBasis.Analysis) method.invoke(panel);
            check(analysis.hasRefutation() && analysis.starts.contains(1), "recorded opposite result did not reach P refutation");
            check(before.equals(board()) && panel.getSudoku().getValue(0) == 0,
                    "same confirmation also applied the derived refutation");
            return null;
        });
        edt(() -> { reset(); return null; }); prepare();
        edt(() -> {
            mark(0, 1, TRUE); panel.toggleDoodleHypothesisEntry(); mark(40, 2, TRUE);
            key(KeyEvent.VK_ENTER, 0); paint("or-member-result");
            check(find(0, 1, TRUE, 2, true) != null && find(0, 1, TRUE, 2, false) != null
                    && find(40, 2, TRUE, 2, true) != null,
                    "individual OR-member truth was absorbed into an OR start");
            GuiState state = new GuiState(); state.setIncludeAnnotations(true); panel.getState(state, true);
            SessionSnapshot snapshot = new SessionSnapshot(); snapshot.setGuiState(state);
            SessionStore store = new SessionStore(output.resolve("transcribed-session.xml").toFile());
            store.save(snapshot); panel.setState(store.load().getGuiState());
            panel.setAnnotationTool(AnnotationTool.DOODLE); panel.getCellZoomPanel().selectPaletteGroup(2); paint(null);
            check(find(0, 1, TRUE, 2, true) != null && find(0, 1, TRUE, 2, false) != null
                    && !panel.hasPendingChainTranscription() && panel.currentReasoningChains().isEmpty(),
                    "session restore lost independent roles or resurrected task/source");
            return null;
        });
    }
    private static void cancellation() throws Exception {
        for (String event : Arrays.asList("escape", "tool", "external", "source", "board", "quiet-board", "quiet-source", "step", "readonly", "restore", "new-puzzle")) {
            edt(() -> { reset(); return null; }); prepare();
            edt(() -> {
                mark(55, 9, FALSE); mark(56, 8, TRUE); String before = board();
                if (event.equals("escape")) {
                    panel.toggleDoodleHypothesisEntry(); panel.handleEscapeVisualReset();
                    check(panel.hasPendingChainTranscription() && !panel.isDoodleHypothesisEntryActive(), "first Esc canceled pending before G");
                    panel.handleEscapeVisualReset();
                } else if (event.equals("tool")) panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
                else if (event.equals("external")) panel.cancelChainTranscriptionOnExternalFocusLoss();
                else if (event.equals("source")) panel.clearUserChainsWithUndo();
                else if (event.equals("board")) { panel.getSudoku().delCandidate(80, 9); panel.reasoningBoardChanged(); }
                else if (event.equals("quiet-board")) { panel.getSudoku().delCandidate(80, 9); key(KeyEvent.VK_ENTER, 0); }
                else if (event.equals("quiet-source")) { chains().get(0).getNodes().set(0, GroupedChainProbe.node(1, 2)); key(KeyEvent.VK_ENTER, 0); }
                else if (event.equals("step")) {
                    SolutionStep hint = new SolutionStep(SolutionType.NAKED_SINGLE); hint.addIndex(80); hint.addValue(9);
                    frame.setSolutionStep(hint, true);
                } else if (event.equals("readonly")) panel.setReplayReadOnly(true);
                else if (event.equals("restore")) {
                    GuiState state = new GuiState(); state.setIncludeAnnotations(true); panel.getState(state, true); panel.setState(state);
                } else panel.setSudoku((String) null);
                check(!panel.hasPendingChainTranscription(), event + " left stale pending authorization");
                if (!event.equals("new-puzzle")) check(find(55, 9, FALSE, 2, true) != null, event + " erased ordinary authored start");
                if (!event.equals("new-puzzle")) check(find(56, 8, TRUE, 2, false) != null, event + " cleared P content before successful commit");
                if (!event.equals("new-puzzle") && !event.equals("source"))
                    check(!panel.currentReasoningChains().isEmpty(), event + " consumed an unconfirmed source");
                if (!event.equals("board") && !event.equals("quiet-board") && !event.equals("new-puzzle")) check(before.equals(board()), event + " changed Sudoku");
                if (event.equals("escape") || event.equals("external"))
                    check(panel.getAnnotationTool() == AnnotationTool.DOODLE && panel.getStep() == null,
                            event + " did not remain in ordinary P after canceling preview");
                return null;
            });
        }
    }
    private static void exactSelectedSource() throws Exception {
        edt(() -> {
            reset();
            UserChain source = ring(); source.setSourceId(901001); source.setActive(false);
            UserChain unrelated = GroupedChainProbe.chain(false, new UserChainNode[] {
                    GroupedChainProbe.node(4, 60), GroupedChainProbe.node(4, 61)
            }, true); unrelated.setSourceId(901002);
            write("activeUserChain", null); chains().clear(); chains().add(source); chains().add(unrelated);
            call("noteUserChainReasoningChanged");
            UserChainValidator.Result result = UserChainValidator.preview(panel.getSudoku(), source);
            panel.previewCurrentReasoning(result.steps.get(0), null, source, true);
            check(panel.hasChainTranscriptionShortcutContext(), "selected single-source preview cannot transcribe");
            key(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK); mark(55, 9, FALSE); key(KeyEvent.VK_ENTER, 0);
            check(chains().size() == 1 && chains().get(0).getSourceId() == unrelated.getSourceId(),
                    "transcription consumed unrelated chain alongside selected source");
            return null;
        });
    }
    private static void assembledSources() throws Exception {
        edt(() -> {
            reset(); write("activeUserChain", null); chains().clear();
            UserChain first = GroupedChainProbe.chain(false, new UserChainNode[] {
                    GroupedChainProbe.node(1, 0), GroupedChainProbe.node(1, 1), GroupedChainProbe.node(1, 9)
            }, true, false); first.setSourceId(902001);
            UserChain second = GroupedChainProbe.chain(false, new UserChainNode[] {
                    GroupedChainProbe.node(1, 9), GroupedChainProbe.node(1, 0)
            }, true); second.setSourceId(902002);
            chains().add(first); chains().add(second); call("noteUserChainReasoningChanged"); return null;
        });
        prepare();
        edt(() -> {
            check(chains().size() == 2, "analysis prematurely consumed multi-segment sources");
            mark(55, 9, FALSE); key(KeyEvent.VK_ENTER, 0);
            check(panel.currentReasoningChains().isEmpty(), "assembled source segments were not all consumed");
            panel.setAnnotationTool(AnnotationTool.FREE_CHAIN); panel.undoCurrentAnnotation();
            Set<Long> restored = new HashSet<Long>(); for (UserChain chain : chains()) restored.add(chain.getSourceId());
            check(restored.equals(new HashSet<Long>(Arrays.asList(902001L, 902002L))), "one L undo lost exact submitted segment identities");
            return null;
        });
    }
    private static void retainedDerivedEvidence() throws Exception {
        edt(() -> {
            reset(); panel.setAnnotationTool(AnnotationTool.DOODLE);
            String before = board();
            mark(55, 9, FALSE); panel.toggleDoodleHypothesisEntry(); mark(56, 8, FALSE);
            panel.finishDoodleHypothesisEntry();
            for (int digit = 1; digit <= 9; digit++) mark(0, digit, FALSE);
            // Exercise the actual P refutation-to-OR adoption, which captures old
            // starts with their historical start identity in read-only support.
            key(KeyEvent.VK_ENTER, 0);
            check(before.equals(board()) && atoms(2, TRUE, true).equals(new TreeSet<Integer>(Arrays.asList(559, 568))),
                    "real P adoption failed to produce a derived OR without changing Sudoku");
            check(ink().stream().anyMatch(s -> s.isProofOnly() && s.isHypothesisStart() && s.getThoughtGroup() == 2),
                    "derived fixture lacks the readonly historical start that exercised duplicate retention");
            check(sourceFrames(2).size() == 11 && ink().stream().filter(s -> !s.isProofOnly() && s.getThoughtGroup() == 2)
                    .allMatch(s -> s.isHypothesisStart() && s.getDerivedSourceId() > 0),
                    "actual OR adoption lost its complete source association");
            mark(70, 4, TRUE); panel.setAnnotationTool(AnnotationTool.FREE_CHAIN); return null;
        });
        prepare();
        edt(() -> {
            List<String> beforeSupport = sourceFrames(2);
            check(new HashSet<String>(beforeSupport).size() == beforeSupport.size(), "derived fixture has duplicate source records");
            Set<Integer> truths = conclusionAtoms(validation(), true), falses = conclusionAtoms(validation(), false);
            String before = board(); int undoBefore = depth("doodleUndoStack");
            key(KeyEvent.VK_ENTER, 0); paint("derived-source-transcribed");
            check(sourceFrames(2).equals(beforeSupport), "transcription duplicated or changed owned readonly source geometry/metadata");
            check(atoms(2, TRUE, true).equals(new TreeSet<Integer>(Arrays.asList(559, 568)))
                    && atoms(2, TRUE, false).equals(truths) && atoms(2, FALSE, false).equals(falses),
                    "readonly historical starts or results became live transcribed premises/conclusions");
            check(panel.getDoodleMarkSourceMask(55, 9, FALSE) == 0 && panel.getDoodleMarkSourceMask(56, 8, FALSE) == 0,
                    "retained readonly false starts reentered live P projections");
            Method method = SudokuPanel.class.getDeclaredMethod("currentDoodleContradiction"); method.setAccessible(true);
            check(!((DoodleContradictionBasis.Analysis) method.invoke(panel)).hasConflict(),
                    "historical readonly crosses refuted the current OR and chain truth");
            check(before.equals(board()) && depth("doodleUndoStack") == undoBefore + 1,
                    "derived-source transcription applied Sudoku or created several P undos");
            panel.undoCurrentAnnotation();
            check(sourceFrames(2).equals(beforeSupport) && find(70, 4, TRUE, 2, false) != null
                    && !panel.hasPendingChainTranscription() && panel.currentReasoningChains().isEmpty(),
                    "P undo did not restore original derived evidence and ordinary ink independently of consumed L");
            panel.redoCurrentAnnotation();
            check(sourceFrames(2).equals(beforeSupport) && find(70, 4, TRUE, 2, false) == null && before.equals(board()),
                    "P redo duplicated owned sources, retained old ink or applied evidence");
            return null;
        });
    }
    private static void actualReplayViewing() throws Exception {
        edt(() -> { reset(); return null; }); prepare();
        edt(() -> {
            mark(55, 9, FALSE); mark(56, 8, TRUE);
            String before = board(); int inkCount = ink().size();
            long sourceId = chains().get(0).getSourceId();
            ReplayController controller = frame.getReplayController();
            ReplaySession selected = new ReplaySession(new ReplayBoard(panel.getSudoku()), System.currentTimeMillis());
            controller.openViewer(selected);
            try {
                check(controller.isViewing() && frame.getGlassPane().isVisible(), "actual ReplayController did not open its in-window viewer");
                check(!panel.hasPendingChainTranscription() && !((Boolean) read("replayReadOnly")),
                        "glass-pane viewing left pending task or relied on changing the live panel to read-only");
                check(panel.getStep() == null && panel.getAnnotationTool() == AnnotationTool.DOODLE
                        && chains().size() == 1 && chains().get(0).getSourceId() == sourceId,
                        "viewer entry retained old apply preview, left P or consumed source");
                ReplayViewer viewer = controller.viewer(); SudokuPanel viewerBoard = viewer.boardPanel();
                check((Boolean) read(viewerBoard, "replayReadOnly"), "actual viewer board is not read-only");
                String viewerBefore = TechniqueStepCatalog.createSignature(viewerBoard.getSudoku());
                KeyEventDispatcher dispatcher = (KeyEventDispatcher) read(frame, "annotationKeyDispatcher");
                for (int modifiers : new int[] { InputEvent.SHIFT_DOWN_MASK, 0 }) {
                    KeyEvent pressed = new KeyEvent(viewerBoard, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                            modifiers, KeyEvent.VK_ENTER, '\n');
                    check(dispatcher.dispatchKeyEvent(pressed) && pressed.isConsumed(), "viewer Enter was not owned by actual replay key dispatch");
                    dispatcher.dispatchKeyEvent(new KeyEvent(viewerBoard, KeyEvent.KEY_RELEASED, System.currentTimeMillis(),
                            modifiers, KeyEvent.VK_ENTER, '\n'));
                }
                check(!panel.hasPendingChainTranscription() && !viewerBoard.hasPendingChainTranscription()
                        && before.equals(board()) && viewerBefore.equals(TechniqueStepCatalog.createSignature(viewerBoard.getSudoku()))
                        && ink().size() == inkCount && chains().get(0).getSourceId() == sourceId,
                        "readonly viewer keys transcribed, consumed sources or applied a board");
            } finally { controller.closeViewer(); }
            check(!controller.isViewing() && !panel.hasPendingChainTranscription() && read("reasoningProposal") == null
                    && panel.getStep() == null, "closing actual viewer resurrected old transcription authorization");
            check(before.equals(board()) && ink().size() == inkCount && find(55, 9, FALSE, 2, true) != null
                    && find(56, 8, TRUE, 2, false) != null && chains().size() == 1 && chains().get(0).getSourceId() == sourceId,
                    "actual viewer open/close lost formal P or unconfirmed L content");
            return null;
        });
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        output = Files.createDirectories(Paths.get(System.getProperty("hodoku.probe.output", System.getProperty("java.io.tmpdir")),
                "chain-transcription"));
        System.setProperty("hodoku.replay.dir", output.resolve("replays").toString());
        try {
            edt(() -> { ApplicationAppearance.initialize(AppearanceMode.LIGHT); frame = new MainFrame(null); panel = frame.getSudokuPanel(); return null; });
            batchAndUndo(); invalidStartAndEditing(); contradictionAndOrRole(); cancellation(); exactSelectedSource(); assembledSources(); retainedDerivedEvidence(); actualReplayViewing();
            System.out.println("PASS ChainTranscriptionWorkflowProbe: " + checks + " real controller/key/edit/undo/session checks; " + output);
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        finally { if (frame != null) edt(() -> { frame.dispose(); return null; }); }
        System.exit(0);
    }
}
