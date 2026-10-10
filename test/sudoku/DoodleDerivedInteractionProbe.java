/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Color;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import javax.imageio.ImageIO;
import javax.swing.MenuSelectionManager;
import javax.swing.JButton;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
import static sudoku.ChainEvidencePreviewProbe.atoms;

/** Real panel mutations, dispatched Swing mouse listeners, rendering and session
 * storage. This does not claim native Robot delivery or packaged-app acceptance.
 */
public final class DoodleDerivedInteractionProbe {
    private static final int CIRCLE = DoodleStroke.MARK_TRUE_CIRCLE;
    private static final int CROSS = DoodleStroke.MARK_FALSE_CROSS;
    private static final int[] SOURCE = {1, 103, 205, 607, 809};
    private static final int X = 312, Z = 416, Y = 614, W = 716;
    private static Path output;

    @SuppressWarnings("unchecked")
    private static List<DoodleStroke> ink() throws Exception {
        return (List<DoodleStroke>) read("doodleStrokes");
    }
    @SuppressWarnings("unchecked")
    private static List<DoodleStroke> activeInk() throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod("activeDoodleMarks");
        method.setAccessible(true); return (List<DoodleStroke>) method.invoke(p);
    }
    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.get(target);
    }
    private static int history() throws Exception {
        return ((Stack<?>) read("doodleUndoStack")).size();
    }
    private static String board() {
        StringBuilder state = new StringBuilder();
        for (int cell = 0; cell < 81; cell++) state.append(p.getSudoku().getValue(cell)).append(':')
                .append(Arrays.toString(p.getSudoku().getAllCandidates(cell))).append(';');
        return state.toString();
    }
    private static String markState() throws Exception {
        StringBuilder state = new StringBuilder();
        for (DoodleStroke mark : ink()) state.append(mark.getAnchorCell()).append(':')
                .append(mark.getAnchorDigit()).append(':').append(mark.getCandidateMarkKind()).append(':')
                .append(mark.getThoughtGroup()).append(':').append(mark.isHypothesisStart()).append(':')
                .append(mark.getHypothesisGroupKind()).append(':').append(mark.getHypothesisConditionId()).append(':')
                .append(mark.isConclusionOutlined()).append(':').append(mark.getConclusionSourceMask()).append(':')
                .append(mark.isProofOnly()).append(':').append(mark.getDerivedSourceId()).append(';');
        return state.toString();
    }
    private static DoodleStroke mark(int group, int atom, int kind, boolean start) throws Exception {
        DoodleStroke mark = ChainEvidencePreviewProbe.mark(group, atom / 10, atom % 10, kind, start);
        double radius = .14;
        if (kind == CROSS) {
            mark.getPoints().add(new DoodlePoint(-radius, -radius));
            mark.getPoints().add(new DoodlePoint(radius, radius));
            mark.getPoints().add(new DoodlePoint(radius, -radius));
            mark.getPoints().add(new DoodlePoint(-radius, radius));
        } else for (int step = 0; step <= 48; step++) {
            double angle = step * Math.PI * 2 / 48;
            mark.getPoints().add(new DoodlePoint(radius * Math.cos(angle), radius * Math.sin(angle)));
        }
        return mark;
    }
    private static DoodleStroke find(int group, int atom, int kind, boolean proof) throws Exception {
        for (DoodleStroke mark : ink()) if (mark.getThoughtGroup() == group
                && mark.getAnchorCell() * 10 + mark.getAnchorDigit() == atom
                && mark.getCandidateMarkKind() == kind && mark.isProofOnly() == proof) return mark;
        return null;
    }
    private static void reset() throws Exception {
        ChainEvidencePreviewProbe.reset();
        p.setAnnotationTool(AnnotationTool.DOODLE); p.getCellZoomPanel().selectPaletteGroup(1);
        p.setSize(810, 810); ChainOriginProbe.render(null);
    }
    private static void source() throws Exception {
        for (int atom : SOURCE) mark(0, atom, CIRCLE, true);
    }
    private static void normalizeStarts() throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod("normalizeDoodleHypothesisStarts", int.class);
        method.setAccessible(true);
        for (int group = 0; group < 6; group++) method.invoke(p, group);
    }
    private static void negativeFixture() throws Exception {
        reset(); source(); mark(1, X, CROSS, true);
        for (int atom : new int[]{SOURCE[0], SOURCE[1], SOURCE[2]}) mark(1, atom, CROSS, false);
        normalizeStarts();
    }
    private static DoodleDerivedBasis.CircleReduction reduction(int... result) {
        String key = atoms(result).key();
        for (DoodleDerivedBasis.CircleReduction reduction : p.doodleCircleReductions())
            if (key.equals(reduction.getResult().key())) return reduction;
        throw new AssertionError("current palette omitted complete circle reduction " + key);
    }
    private static boolean adopt(DoodleDerivedBasis.CircleReduction reduction) {
        DoodleDerivedBasis.Evidence evidence = reduction.evidenceForGroup(p.getCellZoomPanel().getPaletteGroup());
        check(evidence != null, "fixture reduction lacks current palette evidence");
        return p.adoptDoodleCircleReduction(reduction.getResult().key(), evidence.getSourceGroup(),
                evidence.getBranchGroupMask());
    }
    private static void assertActiveCircles(int group, int... members) throws Exception {
        List<Integer> expected = new ArrayList<Integer>();
        for (int atom : members) expected.add(atom);
        List<Integer> actual = new ArrayList<Integer>(); int condition = 0;
        for (DoodleStroke mark : activeInk()) if (mark.getThoughtGroup() == group) {
            check(mark.isHypothesisStart() && mark.getCandidateMarkKind() == CIRCLE,
                    "replaced palette retained active old premise or intermediate conclusion");
            check(mark.getHypothesisGroupKind() == (members.length > 1 ? 1 : 0),
                    "adopted start has wrong OR/individual semantics");
            check(members.length > 1 ? mark.getHypothesisConditionId() > 0 : mark.getHypothesisConditionId() == 0,
                    "adopted start lost group/individual condition identity");
            if (condition == 0) condition = mark.getHypothesisConditionId();
            check(condition == mark.getHypothesisConditionId(), "new circle starts split into conditions");
            actual.add(mark.getAnchorCell() * 10 + mark.getAnchorDigit());
        }
        java.util.Collections.sort(actual); java.util.Collections.sort(expected);
        check(expected.equals(actual), "active circle starts differ: " + actual + " expected " + expected);
    }
    private static void adoptionUndoAndSession() throws Exception {
        negativeFixture(); String before = board(), prior = markState(); int history = history();
        check(adopt(reduction(X, SOURCE[3], SOURCE[4])), "same-palette 5-to-3 adoption refused");
        check(p.getCellZoomPanel().getPaletteGroup() == 1 && history() == history + 1,
                "adoption changed palette or was not one undo operation");
        assertActiveCircles(1, X, SOURCE[3], SOURCE[4]);
        check(find(1, X, CROSS, true) != null && find(1, SOURCE[0], CROSS, true) != null,
                "captured negative premise or intermediate evidence lost");
        for (int atom : SOURCE) check(find(0, atom, CIRCLE, false) != null,
                "adoption destroyed the original source palette");
        String adopted = markState(); check(before.equals(board()), "adoption changed actual Sudoku");
        p.undoCurrentAnnotation(); check(prior.equals(markState()), "one undo did not restore exact original branch");
        p.redoCurrentAnnotation(); check(adopted.equals(markState()), "one redo did not restore adopted group/source");

        GuiState state = new GuiState(); state.setIncludeAnnotations(true); p.getState(state, true);
        SessionSnapshot snapshot = new SessionSnapshot(); snapshot.setGuiState(state);
        SessionStore store = new SessionStore(output.resolve("derived-interaction-session.xml").toFile());
        store.save(snapshot); SessionSnapshot loaded = store.load();
        check(loaded != null, "actual session store did not load");
        p.clearDoodlesWithUndo(); p.setState(loaded.getGuiState());
        p.setAnnotationTool(AnnotationTool.DOODLE); p.getCellZoomPanel().selectPaletteGroup(1);
        check(adopted.equals(markState()), "session reopening changed active/source metadata");
        assertActiveCircles(1, X, SOURCE[3], SOURCE[4]);
        p.undoCurrentAnnotation(); check(prior.equals(markState()), "restored session undo cannot recover original branch");
        p.redoCurrentAnnotation(); check(adopted.equals(markState()), "restored session redo lost captured source");
        check(before.equals(board()), "adoption/undo/session changed board");
        System.out.println("PASS: same palette 5-to-3, one undo/redo, original source preserved, actual session reopen with usable history");
    }
    private static void staleAdoption() throws Exception {
        negativeFixture(); DoodleDerivedBasis.CircleReduction offer = reduction(X, SOURCE[3], SOURCE[4]);
        DoodleStroke omitted = find(1, SOURCE[2], CROSS, false); ink().remove(omitted);
        String inkBefore = markState(), boardBefore = board(); int history = history();
        check(!adopt(offer) && history() == history && inkBefore.equals(markState()) && boardBefore.equals(board()),
                "stale missing coverage adopted or changed history/ink/board");
        ink().add(omitted); p.getCellZoomPanel().selectPaletteGroup(2);
        check(!p.adoptDoodleCircleReduction(offer.getResult().key(), 0, 1 << 1)
                && history() == history, "inactive branch adopted into unrelated current palette");
        p.getCellZoomPanel().selectPaletteGroup(1); p.getSudoku().delCandidate(SOURCE[3] / 10, SOURCE[3] % 10);
        inkBefore = markState(); boardBefore = board();
        check(!adopt(offer) && history() == history && inkBefore.equals(markState()) && boardBefore.equals(board()),
                "removed result candidate adopted or mutation occurred before final recheck");
        negativeFixture(); offer = reduction(X, SOURCE[3], SOURCE[4]); history = history();
        check(!p.adoptDoodleCircleReduction(offer.getResult().key(), 0, 1 << 2) && history() == history,
                "wrong source evidence mask borrowed valid result key");
        System.out.println("PASS: stale coverage/result, unrelated current palette and wrong evidence reject without mutation or undo");
    }
    private static void twoBranches() throws Exception {
        negativeFixture(); mark(2, Y, CROSS, true);
        mark(2, SOURCE[3], CROSS, false); mark(2, SOURCE[4], CROSS, false);
        normalizeStarts();
        p.getCellZoomPanel().selectPaletteGroup(2); DoodleDerivedBasis.CircleReduction offer = reduction(X, Y);
        check(adopt(offer), "complete two-branch 5-to-2 adoption refused"); assertActiveCircles(2, X, Y);
        check(find(1, X, CROSS, false) != null && find(1, SOURCE[2], CROSS, false) != null,
                "adoption into C destroyed B's independently current branch");
        check(find(2, Y, CROSS, true) != null && find(2, X, CROSS, true) != null,
                "two-color captured evidence incomplete");
        p.setAnnotationTool(AnnotationTool.FREE_CHAIN); ChainEvidencePreviewProbe.origin(X);
        ChainEvidencePreviewProbe.preview(true);
        check(p.chainPreviewRelation(atoms(Y)) == 3, "adopted OR did not reuse existing L complement strong preview");
        p.setNextUserChainStrong(true); ChainEvidencePreviewProbe.tap(0, Y / 10, Y % 10);
        check(ChainEvidencePreviewProbe.tail().key().equals(atoms(Y).key())
                && active().getStrongRelations().get(0), "existing L click did not connect adopted representatives");
        ChainEvidencePreviewProbe.preview(false);
        System.out.println("PASS: two palette branches yield X-or-Y in current C and existing L complement click");
    }
    private static void adoptedPair() throws Exception {
        negativeFixture(); mark(2, Y, CROSS, true);
        mark(2, SOURCE[3], CROSS, false); mark(2, SOURCE[4], CROSS, false);
        normalizeStarts(); p.getCellZoomPanel().selectPaletteGroup(2);
        check(adopt(reduction(X, Y)), "pair lifecycle fixture not adopted");
    }
    private static void assertNoCurrentStarts(int group) throws Exception {
        for (DoodleStroke mark : activeInk()) check(mark.getThoughtGroup() != group || !mark.isHypothesisStart(),
                "satisfied derived OR upgraded its remaining member to truth");
    }
    private static void derivedMemberBoardChanges() throws Exception {
        adoptedPair(); call("pushDoodleUndo"); p.getSudoku().setCell(X / 10, X % 10);
        call("pruneStaleCandidateAnnotations"); assertNoCurrentStarts(2);
        p.undoCurrentAnnotation(); assertNoCurrentStarts(2);
        p.redoCurrentAnnotation(); assertNoCurrentStarts(2);
        check(p.getSudoku().getValue(X / 10) == X % 10 && p.getSudoku().getValue(Y / 10) == 0,
                "annotation prune/undo/redo changed board or filled the other OR member");
        adoptedPair(); call("pushDoodleUndo"); p.getSudoku().setCandidate(X / 10, X % 10, false, true);
        call("pruneStaleCandidateAnnotations"); assertActiveCircles(2, Y);
        p.undoCurrentAnnotation(); assertActiveCircles(2, Y);
        p.redoCurrentAnnotation(); assertActiveCircles(2, Y);
        check(!p.getSudoku().isCandidate(X / 10, X % 10) && p.getSudoku().getValue(Y / 10) == 0,
                "false-member pruning restored deleted candidate or wrote an automatic placement");
        System.out.println("PASS: true OR member satisfies/removes whole derived start group; false member alone shrinks it, including annotation undo/redo");
    }
    @SuppressWarnings("unchecked")
    private static void assertEmptyProjection() throws Exception {
        Method build = SudokuPanel.class.getDeclaredMethod("buildDoodleThoughtPreviewProjection", int.class);
        build.setAccessible(true); Object projection = build.invoke(p, 1);
        check(((Map<Integer, Integer>) field(projection, "filledCells")).isEmpty()
                && ((Set<Integer>) field(projection, "removedCandidates")).isEmpty(),
                "hidden evidence or OR members projected individual fills/deletions");
    }
    private static void sourceIsolation() throws Exception {
        negativeFixture(); check(adopt(reduction(X, SOURCE[3], SOURCE[4])), "isolation fixture not adopted");
        Method fade = SudokuPanel.class.getDeclaredMethod("fadeCurrentDoodleCross", int.class, int.class);
        fade.setAccessible(true); String before = board();
        for (int atom : new int[]{X, SOURCE[0], SOURCE[1], SOURCE[2]}) {
            check(p.getDoodleMarkSourceMask(atom / 10, atom % 10, CROSS) == 0,
                    "hidden source entered current cross-source mask");
            check(!(Boolean) fade.invoke(p, atom / 10, atom % 10), "hidden source dimmed actual candidate");
            Point point = ChainOriginProbe.candidate(atom / 10, atom % 10);
            int priorHistory = history(); click(MouseEvent.BUTTON1, InputEvent.META_DOWN_MASK, point); p.flushMappedClick();
            for (DoodleStroke mark : ink()) if (mark.isProofOnly()) check(!mark.isConclusionOutlined(),
                    "Command selected hidden source evidence");
            // X itself also has a visible adopted circle, which may be selected;
            // only the old false conclusions have no active current-palette mark.
            if (atom != X) check(history() == priorHistory, "Command on hidden intermediate conclusion created undo");
        }
        assertEmptyProjection();
        // Deliberately stale bean metadata must never bypass the application gate.
        for (DoodleStroke mark : ink()) if (mark.isProofOnly()) {
            mark.setConclusionOutlined(true); mark.setConclusionSourceMask(1 << 1);
        }
        call("applyOutlinedDoodleConclusions");
        check(before.equals(board()), "hidden source stale selection reached application path");
        call("handleReasoningEnter");
        check(before.equals(board()) && read("reasoningProposal") == null,
                "P Enter interpreted hidden evidence or OR members as actual fills/deletions");
        for (DoodleStroke mark : ink()) if (mark.isProofOnly()) {
            mark.setConclusionOutlined(false); mark.setConclusionSourceMask(0);
        }
        System.out.println("PASS: hidden source absent from masks, candidate dimming, projection, Command, direct application and P Enter");
    }
    private static void sharedActiveSourceIsolation() throws Exception {
        negativeFixture(); mark(2, Y, CROSS, true);
        mark(2, SOURCE[3], CROSS, false); mark(2, SOURCE[4], CROSS, false);
        DoodleStroke sharedA = mark(0, SOURCE[0], CROSS, false), sharedB = find(1, SOURCE[0], CROSS, false);
        Method create = SudokuPanel.class.getDeclaredMethod("createCandidateHypothesisMark",
                int.class, int.class, int.class, int.class, Color.class);
        create.setAccessible(true);
        DoodleStroke nativeGeometry = (DoodleStroke) create.invoke(p, SOURCE[0] / 10, SOURCE[0] % 10,
                CROSS, 1, sharedB.getColor());
        sharedB.setPoints(nativeGeometry.getPoints()); sharedB.setWidthFactor(nativeGeometry.getWidthFactor());
        sharedA.setConclusionOutlined(true); sharedA.setConclusionSourceMask(3);
        normalizeStarts(); p.getCellZoomPanel().selectPaletteGroup(2);
        String before = board(); check(adopt(reduction(X, Y)), "shared source fixture not adopted");
        int history = history(); Point point = ChainOriginProbe.candidate(SOURCE[0] / 10, SOURCE[0] % 10);
        click(MouseEvent.BUTTON1, InputEvent.META_DOWN_MASK, point); p.flushMappedClick();
        check(history == history(), "hidden actual shared A/B cross was hit through Command fallback");
        call("applyOutlinedDoodleConclusions"); call("handleReasoningEnter");
        check(before.equals(board()) && read("reasoningProposal") == null,
                "preselected old shared source reached current C Enter/application");
        BufferedImage hidden = ChainOriginProbe.render(null);
        // Recreate exactly the old B geometry/color using the actual mutation.
        // A new current-C mark is active even though its pixels match a source.
        Method toggle = SudokuPanel.class.getDeclaredMethod("toggleCandidateHypothesisMark",
                int.class, int.class, int.class, int.class, Color.class);
        toggle.setAccessible(true); toggle.invoke(p, SOURCE[0] / 10, SOURCE[0] % 10, CROSS, 2, sharedB.getColor());
        DoodleStroke current = find(2, SOURCE[0], CROSS, false);
        check(current != null && current.getPoints().size() == sharedB.getPoints().size()
                && current.getWidthFactor() == sharedB.getWidthFactor(), "same-geometry current mark fixture differs");
        for (int i = 0; i < current.getPoints().size(); i++) check(
                current.getPoints().get(i).getX() == sharedB.getPoints().get(i).getX()
                && current.getPoints().get(i).getY() == sharedB.getPoints().get(i).getY(),
                "new current mark did not recreate old source geometry");
        BufferedImage redrawn = ChainOriginProbe.render(null);
        check(ChainEvidencePreviewProbe.changedNear(hidden, redrawn, point, 22) > 12,
                "new current-C mark with source geometry was hidden as old evidence");
        check(before.equals(board()), "shared-source/new-mark checks changed board");
        System.out.println("PASS: hidden actual shared A/B source cannot Cmd/apply/Enter; newly drawn same-geometry current-C cross stays visible");
    }
    @SuppressWarnings("unchecked")
    private static List<DoodleStroke> interactiveInk() throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod("interactiveDoodleMarks");
        method.setAccessible(true); return (List<DoodleStroke>) method.invoke(p);
    }
    private static void nestedDerivedSourceVisibility() throws Exception {
        negativeFixture(); check(adopt(reduction(X, SOURCE[3], SOURCE[4])), "nested B fixture not adopted");
        DoodleStroke oldB = find(1, SOURCE[3], CIRCLE, false);
        mark(2, Y, CROSS, true); mark(2, SOURCE[3], CROSS, false); mark(2, SOURCE[4], CROSS, false);
        normalizeStarts(); p.getCellZoomPanel().selectPaletteGroup(2);
        check(adopt(reduction(X, Y)), "nested C reduction of B not adopted"); assertActiveCircles(2, X, Y);
        check(!interactiveInk().contains(oldB), "old derived B start remains visible/editable in adopted C");
        for (DoodleStroke mark : activeInk()) if (mark.getThoughtGroup() == 2)
            check(interactiveInk().contains(mark), "new current C starts hidden with nested old B evidence");
        p.getCellZoomPanel().selectPaletteGroup(1);
        check(interactiveInk().contains(oldB), "switching back to B did not restore its ordinary editing/display");
        p.getCellZoomPanel().selectPaletteGroup(2);
        check(!interactiveInk().contains(oldB), "returning to C exposed old derived B source again");
        System.out.println("PASS: nested A-to-B-to-C hides old derived B only in C; switching back restores B editing and C active starts stay visible");
    }
    private static String originalActiveSources() throws Exception {
        StringBuilder state = new StringBuilder();
        for (DoodleStroke mark : activeInk()) if (mark.getThoughtGroup() == 0 || mark.getThoughtGroup() == 1) {
            state.append(mark.getThoughtGroup()).append(':').append(mark.getAnchorCell()).append(':')
                    .append(mark.getAnchorDigit()).append(':').append(mark.getCandidateMarkKind()).append(':')
                    .append(mark.isHypothesisStart()).append(':').append(mark.getHypothesisConditionId()).append(':')
                    .append(mark.getHypothesisGroupKind()).append(':').append(mark.getDerivedSourceId()).append(':')
                    .append(mark.getColor()).append(':').append(mark.getWidthFactor());
            for (DoodlePoint point : mark.getPoints()) state.append(':').append(point.getX()).append(',').append(point.getY());
            state.append(';');
        }
        return state.toString();
    }
    private static void hiddenSourceEraserIsolation() throws Exception {
        for (boolean rectangle : new boolean[]{false, true}) {
            nestedDerivedSourceVisibility(); String sources = originalActiveSources(), before = board();
            int history = history(); Point x = ChainOriginProbe.candidate(X / 10, X % 10);
            if (rectangle) {
                Point start = new Point(3, 3), end = new Point(805, 805);
                click(MouseEvent.BUTTON3, 0, start);
                event(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON3, 0, start);
                event(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, InputEvent.BUTTON3_DOWN_MASK,
                        new Point(start.x + 4, start.y));
                check("RECT_ERASER".equals(read("doodleGesture").toString()), "actual second-right-drag did not use rectangle eraser");
                event(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON, InputEvent.BUTTON3_DOWN_MASK, end);
                event(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON3, 0, end);
                assertNoCurrentStarts(2);
            } else {
                drag(MouseEvent.BUTTON3, 0, new Point(x.x - 35, x.y), new Point(x.x + 35, x.y));
                check(find(2, X, CIRCLE, false) == null, "actual trajectory eraser did not remove current C visible mark");
                check(find(2, Y, CIRCLE, false) != null, "narrow trajectory erased unrelated current C endpoint");
            }
            p.flushMappedClick();
            check(sources.equals(originalActiveSources()), "eraser changed hidden actual A/B sources, including old derived B");
            check(history() == history + 1 && before.equals(board()), "visible eraser did not stay one undo operation/annotation-only");
            p.undoCurrentAnnotation(); assertActiveCircles(2, X, Y);
            check(sources.equals(originalActiveSources()), "erase undo changed original hidden A/B sources");
            p.redoCurrentAnnotation();
            check(sources.equals(originalActiveSources()), "erase redo changed original hidden A/B sources");
            if (rectangle) assertNoCurrentStarts(2);
            else check(find(2, X, CIRCLE, false) == null, "trajectory redo restored erased current C mark");
            check(before.equals(board()), "erase undo/redo changed actual Sudoku");
        }
        System.out.println("PASS: actual trajectory and rectangle erasers protect hidden actual A/B and old derived B; visible C deletion and one undo/redo stay normal");
    }
    private static void independentLPaletteVisibility() throws Exception {
        nestedDerivedSourceVisibility(); DoodleStroke oldB = find(1, SOURCE[3], CIRCLE, false);
        Options.getInstance().setAnnotationPaletteGroup(AnnotationPaletteOwner.FREE_CHAIN, 0);
        Color expected = Options.getInstance().getAnnotationPrimaryColor(AnnotationPaletteOwner.FREE_CHAIN);
        String before = board(), state = markState(); int history = history();
        p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
        check(p.getCellZoomPanel().getPaletteGroup() == 0 && p.getCellZoomPanel().getPrimaryColor().equals(expected),
                "showing adopted P group changed independent L palette");
        check(!interactiveInk().contains(oldB), "P-C to L-A made old B source visible again");
        for (DoodleStroke mark : activeInk()) if (mark.getThoughtGroup() == 2)
            check(interactiveInk().contains(mark), "P-C adopted circles hidden when L uses another palette");
        Point intermediate = ChainOriginProbe.candidate(SOURCE[1] / 10, SOURCE[1] % 10);
        BufferedImage hidden = ChainOriginProbe.render(null); ChainEvidencePreviewProbe.preview(true);
        BufferedImage peek = ChainOriginProbe.render(null);
        check(ChainEvidencePreviewProbe.changedNear(hidden, peek, intermediate, 22) > 12,
                "independent L palette omitted adopted P source peek");
        ChainEvidencePreviewProbe.preview(false); BufferedImage released = ChainOriginProbe.render(null);
        check(ChainEvidencePreviewProbe.changedNear(hidden, released, intermediate, 22) == 0
                && history == history() && state.equals(markState()) && before.equals(board()),
                "L source preview changed annotation/board or left source visible");
        p.setAnnotationTool(AnnotationTool.DOODLE); p.getCellZoomPanel().selectPaletteGroup(1);
        check(interactiveInk().contains(oldB), "returning to P-B did not restore original B editing");
        System.out.println("PASS: P-C adoption to independent L-A preserves L color, hides old A/B, shows C, source peek/release and P-B return");
    }
    private static void peekRendering() throws Exception {
        negativeFixture(); check(adopt(reduction(X, SOURCE[3], SOURCE[4])), "peek fixture not adopted");
        String before = board(), state = markState(); int history = history();
        BufferedImage hidden = ChainOriginProbe.render("derived-hidden");
        // The captured source also hides its still-current original ink while
        // this adopted result is selected, so compare to a mark-free rendering
        // at an intermediate candidate outside the new circle-start group.
        set("doodlesVisible", false); BufferedImage absent = ChainOriginProbe.render(null);
        set("doodlesVisible", true);
        Point intermediate = ChainOriginProbe.candidate(SOURCE[1] / 10, SOURCE[1] % 10);
        check(ChainEvidencePreviewProbe.changedNear(hidden, absent, intermediate, 22) == 0,
                "captured original intermediate ink remains visible by default");
        ChainEvidencePreviewProbe.preview(true);
        check(Boolean.TRUE.equals(read("doodleThoughtPreviewHeld")), "middle hold failed to enter existing P preview");
        BufferedImage peek = ChainOriginProbe.render("derived-middle-peek");
        check(ChainEvidencePreviewProbe.changedNear(hidden, peek, intermediate, 22) > 12,
                "middle peek omitted captured original source");
        assertEmptyProjection(); ChainEvidencePreviewProbe.preview(false);
        BufferedImage released = ChainOriginProbe.render("derived-peek-released");
        check(ChainEvidencePreviewProbe.changedNear(hidden, released, intermediate, 22) == 0,
                "release left captured source visible");
        set("optionPreviewHeld", true); call("refreshHeldPreview");
        BufferedImage option = ChainOriginProbe.render("derived-option-peek");
        check(ChainEvidencePreviewProbe.changedNear(hidden, option, intermediate, 22) > 12,
                "existing Option preview did not expose source");
        set("optionPreviewHeld", false); call("refreshHeldPreview");
        check(history() == history && state.equals(markState()) && before.equals(board()),
                "read-only source peek changed selections, source metadata, undo or board");
        ImageIO.write(hidden, "png", output.resolve("derived-hidden.png").toFile());
        ImageIO.write(peek, "png", output.resolve("derived-middle-peek.png").toFile());
        ImageIO.write(released, "png", output.resolve("derived-peek-released.png").toFile());
        System.out.println("PASS: source hidden by default, middle/Option peek and release render without projection or mutation");
    }
    private static void weakFixture(boolean complete) throws Exception {
        reset(); source();
        mark(1, X, CIRCLE, true); mark(1, Z, CIRCLE, true);
        for (int atom : new int[]{SOURCE[0], SOURCE[1], SOURCE[2]}) mark(1, atom, CROSS, false);
        mark(2, Y, CIRCLE, true); mark(2, W, CIRCLE, true);
        mark(2, SOURCE[3], CROSS, false); if (complete) mark(2, SOURCE[4], CROSS, false);
        normalizeStarts();
        p.setAnnotationTool(AnnotationTool.FREE_CHAIN); ChainEvidencePreviewProbe.origin(X, Z);
        ChainEvidencePreviewProbe.preview(true);
    }
    private static void weakRelations() throws Exception {
        weakFixture(true); UserChainNode target = atoms(Y, W); String before = board();
        check(!UserChainValidator.weak(atoms(X, Z), target), "weak fixture unexpectedly has ordinary Sudoku weak qualification");
        check(p.chainPreviewRelation(target) == 1, "complete excluded-OR evidence is not weak-only");
        check(p.chainPreviewRelation(atoms(Y)) == 0, "partial target borrowed complete weak endpoint");
        check(ChainEvidencePreviewProbe.targetCount(target) == 1, "complete derived weak target missing or duplicated");
        int chainHistory = ChainEvidencePreviewProbe.undoSize(); p.setNextUserChainStrong(true);
        ChainEvidencePreviewProbe.tap(0, Y / 10, Y % 10);
        ChainEvidencePreviewProbe.assertNoAppend(1, chainHistory, "weak-only target appended as strong");
        p.setNextUserChainStrong(false); ChainEvidencePreviewProbe.tap(InputEvent.META_DOWN_MASK, Y / 10, Y % 10);
        ChainEvidencePreviewProbe.assertNoAppend(1, chainHistory, "Command borrowed derived complete-group weak qualification");
        ChainEvidencePreviewProbe.tap(0, W / 10, W % 10);
        check(active().getNodes().size() == 2 && ChainEvidencePreviewProbe.tail().key().equals(target.key())
                && !active().getStrongRelations().get(0), "ordinary member click split weak endpoint or changed relation type");
        check(chainHistory + 1 == ChainEvidencePreviewProbe.undoSize(), "complete weak endpoint did not append atomically");
        p.undoCurrentAnnotation(); check(ChainEvidencePreviewProbe.tail().key().equals(atoms(X, Z).key()),
                "weak group undo lost complete origin");
        p.redoCurrentAnnotation(); check(ChainEvidencePreviewProbe.tail().key().equals(target.key()),
                "weak group redo split endpoint");
        check(before.equals(board()), "derived weak preview/click/undo changed Sudoku");
        ChainEvidencePreviewProbe.preview(false);
        Point intermediate = ChainOriginProbe.candidate(SOURCE[1] / 10, SOURCE[1] % 10);
        BufferedImage hidden = ChainOriginProbe.render(null);
        set("doodlesVisible", false); BufferedImage absent = ChainOriginProbe.render(null);
        set("doodlesVisible", true);
        check(ChainEvidencePreviewProbe.changedNear(hidden, absent, intermediate, 22) == 0,
                "accepted weak relation left source branch ink visible by default");
        int peekHistory = ChainEvidencePreviewProbe.undoSize(); String peekState = markState();
        ChainEvidencePreviewProbe.preview(true); BufferedImage peek = ChainOriginProbe.render(null);
        check(ChainEvidencePreviewProbe.changedNear(hidden, peek, intermediate, 22) > 12,
                "accepted weak relation omitted source branches during middle preview");
        ChainEvidencePreviewProbe.preview(false); BufferedImage released = ChainOriginProbe.render(null);
        check(ChainEvidencePreviewProbe.changedNear(hidden, released, intermediate, 22) == 0
                && peekHistory == ChainEvidencePreviewProbe.undoSize() && peekState.equals(markState())
                && before.equals(board()), "weak source peek release leaked ink or changed state");
        ImageIO.write(hidden, "png", output.resolve("derived-weak-hidden.png").toFile());
        ImageIO.write(peek, "png", output.resolve("derived-weak-middle-peek.png").toFile());
        // The adopted weak edge remains inside a longer authored path. Its
        // evidence must not reappear merely because a later edge becomes tail.
        int extension = 524;
        UserChain chain = active(); chain.getNodes().add(atoms(extension));
        chain.getStrongRelations().add(true); chain.setNextStrong(false);
        call("noteUserChainReasoningChanged");
        BufferedImage continued = ChainOriginProbe.render(null);
        set("doodlesVisible", false); BufferedImage continuedAbsent = ChainOriginProbe.render(null);
        set("doodlesVisible", true);
        check(ChainEvidencePreviewProbe.changedNear(continued, continuedAbsent, intermediate, 22) == 0,
                "continuing after derived weak edge made old source ink visible again");
        ChainEvidencePreviewProbe.preview(true); BufferedImage continuedPeek = ChainOriginProbe.render(null);
        check(ChainEvidencePreviewProbe.changedNear(continued, continuedPeek, intermediate, 22) > 12,
                "continuing chain lost prior derived weak source peek");
        ChainEvidencePreviewProbe.preview(false);
        weakFixture(false); target = atoms(Y, W); chainHistory = ChainEvidencePreviewProbe.undoSize();
        check(p.chainPreviewRelation(target) == 0 && ChainEvidencePreviewProbe.targetCount(target) == 0,
                "missing one source member still supplied derived weak relation");
        ChainEvidencePreviewProbe.preview(false);
        weakFixture(true); target = atoms(Y, W); chainHistory = ChainEvidencePreviewProbe.undoSize();
        ink().remove(find(2, SOURCE[4], CROSS, false)); p.setNextUserChainStrong(false);
        ChainEvidencePreviewProbe.tap(0, W / 10, W % 10);
        ChainEvidencePreviewProbe.assertNoAppend(1, chainHistory, "final click reused stale derived weak coverage");
        check(p.chainPreviewRelation(target) == 0, "live weak coverage withdrawal remained discoverable");
        ChainEvidencePreviewProbe.preview(false);
        System.out.println("PASS: complete remote weak group, no strong/partial/Cmd leakage, member click/undo/redo and live missing-coverage recheck");
    }
    private static JButton adoptionButton() throws Exception {
        return (JButton) field(f, "doodleCircleReductionButton");
    }
    private static void dynamicButtonFixture() throws Exception {
        negativeFixture(); ink().remove(find(1, SOURCE[1], CROSS, false));
        ink().remove(find(1, SOURCE[2], CROSS, false));
        set("doodleHypothesisConsumedMask", 3); p.finishDoodleHypothesisEntry();
        f.doodleHypothesisStateChanged();
        check(!adoptionButton().isVisible(), "one covered member unexpectedly reduced source count");
    }
    private static void dynamicButtonRefresh() throws Exception {
        dynamicButtonFixture();
        DoodleHypothesisCompositionProbe.mark(SOURCE[1] / 10, SOURCE[1] % 10, CROSS, 1);
        check(adoptionButton().isVisible(), "last ordinary cross did not refresh button without tool/palette change");
        DoodleHypothesisCompositionProbe.mark(SOURCE[1] / 10, SOURCE[1] % 10, CROSS, 1);
        check(!adoptionButton().isVisible(), "ordinary cross withdrawal did not immediately hide button");
        p.undoCurrentAnnotation(); check(adoptionButton().isVisible(), "undo did not refresh restored reduction button");
        p.redoCurrentAnnotation(); check(!adoptionButton().isVisible(), "redo did not hide withdrawn reduction button");
        System.out.println("PASS: ordinary cross insertion/removal and undo/redo refresh contextual button without tool/palette switching");
    }
    private static BufferedImage nativeCapture(String name) throws Exception {
        ChainEvidenceNativeProbe.verifyFocus();
        Rectangle region = edt(() -> {
            Rectangle panel = new Rectangle(p.getLocationOnScreen(), p.getSize());
            return panel.intersection(f.getBounds()).intersection(f.getGraphicsConfiguration().getBounds());
        });
        BufferedImage capture = ChainEvidenceNativeProbe.robot.createScreenCapture(region);
        ChainEvidenceNativeProbe.verifyFocus(); ImageIO.write(capture, "png", output.resolve(name + ".png").toFile());
        return capture;
    }
    private static void nativeWindowCapture(String name) throws Exception {
        ChainEvidenceNativeProbe.verifyFocus();
        Rectangle region = edt(() -> f.getBounds().intersection(f.getGraphicsConfiguration().getBounds()));
        ImageIO.write(ChainEvidenceNativeProbe.robot.createScreenCapture(region), "png",
                output.resolve(name + ".png").toFile()); ChainEvidenceNativeProbe.verifyFocus();
    }
    private static Point nativeRelativeCandidate(int atom) throws Exception {
        Point point = ChainEvidenceNativeProbe.candidateOnScreen(atom / 10, atom % 10);
        return edt(() -> {
            Rectangle region = new Rectangle(p.getLocationOnScreen(), p.getSize()).intersection(f.getBounds())
                    .intersection(f.getGraphicsConfiguration().getBounds());
            return new Point(point.x - region.x, point.y - region.y);
        });
    }
    private static void nativeDoubleCross(int atom) throws Exception {
        ChainEvidenceNativeProbe.move(ChainEvidenceNativeProbe.candidateOnScreen(atom / 10, atom % 10));
        ChainEvidenceNativeProbe.robot.setAutoDelay(20);
        try {
            for (int tap = 0; tap < 2; tap++) {
                ChainEvidenceNativeProbe.verifyFocus();
                ChainEvidenceNativeProbe.inputs.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                ChainEvidenceNativeProbe.inputs.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                if (tap == 0) ChainEvidenceNativeProbe.robot.delay(25);
            }
            ChainEvidenceNativeProbe.robot.waitForIdle(); ChainEvidenceNativeProbe.robot.delay(230);
            ChainEvidenceNativeProbe.verifyFocus();
        } finally { ChainEvidenceNativeProbe.robot.setAutoDelay(60); }
    }
    private static void nativeUndo(boolean redo) throws Exception {
        ChainEvidenceNativeProbe.pressKey(KeyEvent.VK_META);
        ChainEvidenceNativeProbe.pressKey(redo ? KeyEvent.VK_Y : KeyEvent.VK_Z);
        ChainEvidenceNativeProbe.releaseKey(redo ? KeyEvent.VK_Y : KeyEvent.VK_Z);
        ChainEvidenceNativeProbe.releaseKey(KeyEvent.VK_META);
    }
    private static void awaitNativeButton(boolean visible, String message) throws Exception {
        long deadline = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < deadline) {
            if (edt(() -> adoptionButton().isVisible() == visible)) return;
            ChainEvidenceNativeProbe.robot.delay(30); ChainEvidenceNativeProbe.idle();
        }
        throw new AssertionError(message + " [undo=" + edt(() -> history()) + ", redo="
                + edt(() -> ((Stack<?>) read("doodleRedoStack")).size()) + ", ink=" + edt(() -> markState()) + "]");
    }
    private static void nativeTool(AnnotationTool tool) throws Exception {
        Point point = edt(() -> {
            javax.swing.JToggleButton[] buttons = (javax.swing.JToggleButton[]) field(f, "annotationToolButtons");
            javax.swing.JToggleButton button = buttons[tool.ordinal()]; Point q = button.getLocationOnScreen();
            q.translate(button.getWidth() / 2, button.getHeight() / 2);
            check(f.getBounds().contains(q), "native tool button is outside isolated frame"); return q;
        });
        ChainEvidenceNativeProbe.nativeTap(point);
        edt(() -> { check(p.getAnnotationTool() == tool, "native tool click did not switch expected tool");
            f.validate(); ChainEvidenceNativeProbe.paintActualPanel(); return null; });
    }
    private static void nativeChecks() throws Exception {
        ChainEvidenceNativeProbe.initialize();
        edt(() -> {
            f.setTitle("HoDoKu isolated derived premise native test"); dynamicButtonFixture();
            f.validate(); ChainEvidenceNativeProbe.paintActualPanel(); p.requestFocusInWindow(); return null;
        });
        ChainEvidenceNativeProbe.idle(); nativeDoubleCross(SOURCE[1]);
        edt(() -> { check(adoptionButton().isShowing() && find(1, SOURCE[1], CROSS, false) != null,
                "native last ordinary cross did not immediately expose contextual button"); return null; });
        ChainEvidenceNativeProbe.robot.delay(230);
        ChainEvidenceNativeProbe.move(ChainEvidenceNativeProbe.candidateOnScreen(SOURCE[1] / 10, SOURCE[1] % 10));
        ChainEvidenceNativeProbe.pressButton(InputEvent.BUTTON3_DOWN_MASK);
        ChainEvidenceNativeProbe.releaseButton(InputEvent.BUTTON3_DOWN_MASK);
        edt(() -> { check(!adoptionButton().isVisible(), "native ordinary cross withdrawal left button visible"); return null; });
        nativeUndo(false); awaitNativeButton(true, "native undo did not restore contextual button");
        nativeUndo(true); awaitNativeButton(false, "native redo did not remove contextual button");
        nativeDoubleCross(SOURCE[1]); nativeDoubleCross(SOURCE[2]);
        edt(() -> {
            f.doodleHypothesisStateChanged(); f.validate(); ChainEvidenceNativeProbe.paintActualPanel();
            check(p.doodleCircleReductions().size() == 1 && adoptionButton().isShowing(),
                    "unique reduction did not expose contextual native toolbar button");
            p.getCellZoomPanel().selectPaletteGroup(2); f.doodleHypothesisStateChanged();
            check(!adoptionButton().isVisible(), "switching to unrelated palette retained contextual adoption button");
            p.getCellZoomPanel().selectPaletteGroup(1); f.doodleHypothesisStateChanged(); f.validate();
            check(adoptionButton().isShowing(), "returning to evidence palette did not restore contextual button");
            ChainEvidenceNativeProbe.paintActualPanel(); p.requestFocusInWindow(); return null;
        });
        ChainEvidenceNativeProbe.idle(); String before = edt(() -> board()); int history = edt(() -> history());
        Point buttonPoint = edt(() -> {
            JButton button = adoptionButton(); Point point = button.getLocationOnScreen();
            point.translate(button.getWidth() / 2, button.getHeight() / 2);
            check(f.getBounds().contains(point) && f.getGraphicsConfiguration().getBounds().contains(point),
                    "contextual adoption button lies outside test window/screen; native input refused");
            return point;
        });
        nativeWindowCapture("native-derived-toolbar-before-adopt"); nativeCapture("native-derived-before-adopt");
        ChainEvidenceNativeProbe.nativeTap(buttonPoint);
        edt(() -> {
            assertActiveCircles(1, X, SOURCE[3], SOURCE[4]);
            check(p.getCellZoomPanel().getPaletteGroup() == 1 && history() == history + 1,
                    "native toolbar click changed palette or did not adopt in one operation");
            check(MenuSelectionManager.defaultManager().getSelectedPath().length == 0,
                    "unique reduction still required a popup/menu choice");
            check(before.equals(board()), "native toolbar adoption changed actual Sudoku"); return null;
        });
        nativeWindowCapture("native-derived-toolbar-after-adopt");
        BufferedImage hidden = nativeCapture("native-derived-hidden");
        Point intermediate = nativeRelativeCandidate(SOURCE[1]); String state = edt(() -> markState());
        int afterAdoptionHistory = edt(() -> history());
        for (boolean option : new boolean[]{true, false}) {
            if (option) ChainEvidenceNativeProbe.pressKey(KeyEvent.VK_ALT);
            else {
                // Native screen capture includes the mouse cursor on this host.
                // Hold inside our panel padding, away from the compared source.
                Point point = edt(() -> { Point q = p.getLocationOnScreen(); q.translate(70, 70); return q; });
                ChainEvidenceNativeProbe.move(point); ChainEvidenceNativeProbe.pressButton(InputEvent.BUTTON2_DOWN_MASK);
            }
            check(edt(() -> Boolean.TRUE.equals(read("doodleThoughtPreviewHeld"))),
                    "native Option/middle did not enter existing P held preview");
            BufferedImage peek = nativeCapture(option ? "native-derived-option-peek" : "native-derived-middle-peek");
            check(ChainEvidencePreviewProbe.changedNear(hidden, peek, intermediate, 22) > 12,
                    "native held preview omitted captured intermediate source");
            edt(() -> { assertEmptyProjection(); return null; });
            if (option) ChainEvidenceNativeProbe.releaseKey(KeyEvent.VK_ALT);
            else ChainEvidenceNativeProbe.releaseButton(InputEvent.BUTTON2_DOWN_MASK);
            BufferedImage released = nativeCapture(option ? "native-derived-option-released" : "native-derived-middle-released");
            check(ChainEvidencePreviewProbe.changedNear(hidden, released, intermediate, 22) == 0,
                    "native preview release left source pixels visible");
            edt(() -> {
                check(!Boolean.TRUE.equals(read("doodleThoughtPreviewHeld")) && afterAdoptionHistory == history()
                        && state.equals(markState()) && before.equals(board()),
                        "native source peek changed undo/source/board or left preview held"); return null;
            });
        }
        edt(() -> { Options.getInstance().setAnnotationPaletteGroup(AnnotationPaletteOwner.FREE_CHAIN, 0); return null; });
        nativeTool(AnnotationTool.FREE_CHAIN);
        edt(() -> {
            check(p.getCellZoomPanel().getPaletteGroup() == 0, "native P-to-L switch changed independent L color");
            for (DoodleStroke mark : activeInk()) if (mark.getThoughtGroup() == 1)
                check(interactiveInk().contains(mark), "native P-to-L switch hid adopted B circles");
            check(!interactiveInk().contains(find(0, SOURCE[1], CIRCLE, false)),
                    "native P-to-L switch made original A source visible"); return null;
        });
        BufferedImage lHidden = nativeCapture("native-derived-L-independent-hidden");
        Point lIntermediate = nativeRelativeCandidate(SOURCE[1]);
        ChainEvidenceNativeProbe.holdPreview(false); BufferedImage lPeek = nativeCapture("native-derived-L-independent-peek");
        check(ChainEvidencePreviewProbe.changedNear(lHidden, lPeek, lIntermediate, 22) > 12,
                "native independent L palette did not show P source during middle hold");
        ChainEvidenceNativeProbe.releasePreview(false);
        BufferedImage lReleased = nativeCapture("native-derived-L-independent-released");
        check(ChainEvidencePreviewProbe.changedNear(lHidden, lReleased, lIntermediate, 22) == 0,
                "native independent L release left P source visible");
        edt(() -> { check(state.equals(markState()) && before.equals(board()),
                "native P-to-L display/peek changed source annotations or board"); return null; });
        nativeTool(AnnotationTool.DOODLE);
        System.out.println("PASS NATIVE: real P-to-L toolbar switch keeps independent L color and adopted P circles; old source stays hidden with read-only middle peek/release");
        System.out.println("PASS NATIVE: last ordinary cross/withdrawal/undo/redo immediately refresh contextual toolbar; palette return; one real mouse click adopts; Option/middle read-only source peek/release");
    }
    public static void main(String[] args) throws Exception {
        boolean nativeMode = args.length == 1 && "--native".equals(args[0]);
        if (args.length != 0 && !nativeMode) throw new IllegalArgumentException("Expected no args or --native");
        if (!nativeMode) System.setProperty("apple.awt.UIElement", "true");
        output = Paths.get(System.getProperty("hodoku.probe.output",
                Files.createTempDirectory("hodoku-derived-interaction-").toString()));
        Files.createDirectories(output);
        boolean passed = false;
        Throwable failure = null;
        try {
            if (nativeMode) nativeChecks();
            else edt(() -> {
                ApplicationAppearance.initialize(AppearanceMode.LIGHT); f = new MainFrame(null); p = f.getSudokuPanel();
                adoptionUndoAndSession(); staleAdoption(); twoBranches(); derivedMemberBoardChanges();
                sourceIsolation(); sharedActiveSourceIsolation(); nestedDerivedSourceVisibility(); hiddenSourceEraserIsolation();
                independentLPaletteVisibility();
                peekRendering(); weakRelations(); dynamicButtonRefresh();
                return null;
            });
            System.out.println("PASS DoodleDerivedInteractionProbe: " + output);
            System.out.println(nativeMode
                    ? "SCOPE: native Robot in isolated source/JDK window; packaged launcher remains separate"
                    : "SCOPE: actual Swing listeners/API, rendering and SessionStore; not native OS input or packaged launcher");
            passed = true;
        } catch (Throwable error) {
            failure = error;
        } finally {
            if (nativeMode) failure = ChainEvidenceNativeProbe.finish(failure);
            else {
                try { if (f != null) edt(() -> { MenuSelectionManager.defaultManager().clearSelectedPath(); f.dispose(); return null; }); }
                catch (Throwable cleanup) { failure = OwnedProbeInputs.append(failure, cleanup); }
            }
        }
        if (failure != null) failure.printStackTrace();
        System.exit(passed && failure == null ? 0 : 1);
    }
}
