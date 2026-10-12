/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Stack;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Actual Swing mouse handlers, popup actions, undo and rendered pixels on isolated candidate boards.
 * These checks do not claim native modifier delivery or packaged-app acceptance. */
public final class ChainEvidencePreviewProbe {
    static final int CIRCLE = DoodleStroke.MARK_TRUE_CIRCLE;
    static final int CROSS = DoodleStroke.MARK_FALSE_CROSS;

    static UserChainNode atoms(int... values) { return UserChainNode.fromAtoms(values, Color.ORANGE); }
    static Point at(int cell, int digit) throws Exception { return ChainOriginProbe.candidate(cell, digit); }
    static void tap(int mods, int cell, int digit) throws Exception {
        click(MouseEvent.BUTTON1, mods, at(cell, digit)); p.flushMappedClick();
    }
    static void preview(boolean down) {
        event(down ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED,
                MouseEvent.BUTTON2, 0, new Point(100, 100));
    }
    static int undoSize() throws Exception { return ((Stack<?>) read("userChainUndoStack")).size(); }
    static UserChainNode tail() throws Exception {
        return active().getNodes().get(active().getNodes().size() - 1);
    }
    static String board() { return TechniqueStepCatalog.createSignature(p.getSudoku()); }
    static String actualBoard() {
        StringBuilder result = new StringBuilder();
        for (int cell = 0; cell < 81; cell++) result.append(p.getSudoku().getValue(cell)).append(':')
                .append(java.util.Arrays.toString(p.getSudoku().getAllCandidates(cell))).append(';');
        return result.toString();
    }
    @SuppressWarnings("unchecked")
    static List<DoodleStroke> ink() throws Exception { return (List<DoodleStroke>) read("doodleStrokes"); }
    @SuppressWarnings("unchecked")
    static List<SudokuSet> boxes() throws Exception { return (List<SudokuSet>) read("boxReasoningGroups"); }
    @SuppressWarnings("unchecked")
    static List<UserChainNode> targets() throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod("chainPreviewTargets");
        method.setAccessible(true); return (List<UserChainNode>) method.invoke(p);
    }
    static int targetCount(UserChainNode node) throws Exception {
        int count = 0; for (UserChainNode target : targets()) if (target.key().equals(node.key())) count++;
        return count;
    }
    static DoodleStroke mark(int group, int cell, int digit, int kind, boolean start) throws Exception {
        DoodleStroke stroke = new DoodleStroke(Options.getInstance().getColoringColors()[group * 2], .04f);
        stroke.setAnchorCell(cell); stroke.setAnchorDigit(digit); stroke.setCandidateMarkKind(kind);
        stroke.setThoughtGroup(group); stroke.setHypothesisStart(start);
        stroke.setHypothesisConditionId(group + 1); stroke.setHypothesisGroupKind(start ? kind == CIRCLE ? 1 : 2 : 0);
        ink().add(stroke); return stroke;
    }
    static void circleSource(int group, int... values) throws Exception {
        for (int value : values) mark(group, value / 10, value % 10, CIRCLE, true);
    }
    static void reset() throws Exception {
        MenuSelectionManager.defaultManager().clearSelectedPath();
        p.cancelAnnotationToolInteractionOnDeactivation();
        p.setSudoku((String) null); p.getSudoku().set(GroupedChainProbe.blank());
        p.setShowCandidates(true); p.setShowHintCellValues(new boolean[10]);
        p.setAnnotationTool(AnnotationTool.FREE_CHAIN); p.setSize(810, 810);
        ChainOriginProbe.render(null);
    }
    static void origin(int... values) throws Exception {
        tap(0, values[0] / 10, values[0] % 10);
        for (int i = 1; i < values.length; i++) tap(InputEvent.SHIFT_DOWN_MASK, values[i] / 10, values[i] % 10);
        check(tail().key().equals(atoms(values).key()), "origin fixture lost its complete candidate set");
    }
    static void als() throws Exception {
        reset(); AlsManualChainProbe.restrict(p.getSudoku(), 0, "12");
        AlsManualChainProbe.restrict(p.getSudoku(), 1, "14");
        boxes().get(0).add(0); boxes().get(0).add(1); origin(14);
    }
    static void alsWithoutOrdinaryCoverage() throws Exception {
        reset(); AlsManualChainProbe.restrict(p.getSudoku(), 0, "123");
        AlsManualChainProbe.restrict(p.getSudoku(), 1, "12");
        boxes().get(0).add(0); boxes().get(0).add(1); origin(3);
    }
    static int changedNear(BufferedImage a, BufferedImage b, Point center, int radius) {
        int count = 0;
        for (int y = Math.max(0, center.y - radius); y <= Math.min(a.getHeight() - 1, center.y + radius); y++)
            for (int x = Math.max(0, center.x - radius); x <= Math.min(a.getWidth() - 1, center.x + radius); x++)
                if (a.getRGB(x, y) != b.getRGB(x, y)) count++;
        return count;
    }
    static int coloredNear(BufferedImage image, Point center, boolean green) {
        int count = 0;
        for (int y = center.y - 18; y <= center.y + 18; y++) for (int x = center.x - 18; x <= center.x + 18; x++) {
            if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) continue;
            Color tone = new Color(image.getRGB(x, y));
            if (green ? tone.getGreen() > tone.getRed() + 25 && tone.getGreen() > tone.getBlue() + 10
                    : tone.getRed() > tone.getGreen() + 20 && tone.getRed() > tone.getBlue() + 15) count++;
        }
        return count;
    }
    static void assertNoAppend(int count, int history, String message) throws Exception {
        check(active().getNodes().size() == count && undoSize() == history, message);
    }

    static void alsGroupClicks() throws Exception {
        for (int clickedCell : new int[]{0, 1}) {
            als(); p.setNextUserChainStrong(true); String before = board(); int history = undoSize();
            preview(true); tap(0, clickedCell, 1);
            check(active().getNodes().size() == 2 && tail().key().equals(atoms(1, 11).key()),
                    "ordinary ALS preview click must append complete {r1c1=1,r1c2=1}, member " + clickedCell);
            check(active().getStrongRelations().get(0), "ALS group appended with wrong edge type");
            check(undoSize() == history + 1, "complete group append must be one undo entry");
            p.undoCurrentAnnotation();
            check(active().getNodes().size() == 1 && tail().key().equals(atoms(14).key()), "group append undo lost origin");
            p.redoCurrentAnnotation(); check(tail().key().equals(atoms(1, 11).key()), "group append redo split endpoint");
            boxes().get(0).clear(); check(tail().key().equals(atoms(1, 11).key()), "source deletion removed already authored edge");
            check(before.equals(board()), "preview/group click/undo/redo changed actual board");
        }
        als(); BufferedImage off = ChainOriginProbe.render(null); preview(true);
        check(p.chainPreviewRelation(atoms(1, 11)) == 3, "ALS inclusive target must be strong only");
        check(targetCount(atoms(1, 11)) == 1, "complete ALS group missing or duplicated");
        BufferedImage on = ChainOriginProbe.render("chain-evidence-als-strong");
        check(changedNear(off, on, at(0, 1), 20) > 20 && changedNear(off, on, at(1, 1), 20) > 20,
                "both group members must render in held preview");
        Point a = at(0, 1), b = at(1, 1), middle = new Point((a.x + b.x) / 2, (a.y + b.y) / 2);
        check(changedNear(off, on, middle, 7) > 10, "temporary complete group band not rendered");
        check(coloredNear(on, at(0, 1), true) > 10, "strong-only target lacks green accepted cue");
        p.setNextUserChainStrong(false); BufferedImage blocked = ChainOriginProbe.render("chain-evidence-als-blocked");
        check(coloredNear(blocked, at(0, 1), false) > 5, "strong-only group lacks blocked weak-edge cue");
        check(targetCount(atoms(1, 11)) == 1, "blocked target vanished and would allow singleton fallback");
    }

    static void commandAndGates() throws Exception {
        als(); preview(true); p.setNextUserChainStrong(false);
        int count = active().getNodes().size(), history = undoSize();
        tap(0, 0, 1); assertNoAppend(count, history, "strong-only group accepted as weak");
        tap(0, 1, 1); assertNoAppend(count, history, "ordinary grouped click fell back to independent weak singleton");
        tap(InputEvent.META_DOWN_MASK, 1, 1);
        check(active().getNodes().size() == count + 1 && tail().key().equals(atoms(11).key())
                && !active().getStrongRelations().get(0), "Command must use independent same-cell weak singleton");
        als(); preview(true); p.setNextUserChainStrong(true); history = undoSize();
        tap(InputEvent.META_DOWN_MASK, 0, 1); assertNoAppend(1, history, "Command borrowed incomplete ALS endpoint qualification");
        tap(InputEvent.META_DOWN_MASK, 1, 1);
        check(tail().key().equals(atoms(11).key()) && active().getStrongRelations().get(0),
                "Command missed independently conjugate same-cell singleton");
        reset(); origin(1); preview(true); p.setNextUserChainStrong(true); history = undoSize();
        check(p.chainPreviewRelation(atoms(11)) == 1, "ordinary row target must remain weak only");
        tap(0, 1, 1); assertNoAppend(1, history, "weak-only singleton accepted as strong");
        p.setNextUserChainStrong(false); tap(0, 1, 1);
        check(!active().getStrongRelations().get(0), "ordinary weak append changed type");
        for (boolean strong : new boolean[]{false, true}) {
            reset(); origin(1);
            for (int cell : new int[]{2, 9, 10, 11, 18, 19, 20}) p.getSudoku().delCandidate(cell, 1);
            preview(true); p.setNextUserChainStrong(strong);
            check(p.chainPreviewRelation(atoms(11)) == 2, "conjugate pair lost independent both qualifications");
            tap(0, 1, 1); check(active().getStrongRelations().get(0) == strong, "both target did not honor chosen edge type");
        }
    }

    static void falseStarts() throws Exception {
        reset(); origin(1, 402);
        DoodleStroke first = mark(0, 0, 1, CROSS, true), second = mark(0, 40, 2, CROSS, true);
        DoodleStroke conclusion = mark(0, 80, 9, CIRCLE, false); preview(true);
        check(p.chainPreviewRelation(atoms(809)) == 3, "complete false start did not supply remote strong-only conclusion");
        check(targetCount(atoms(809)) == 1, "remote P conclusion target missing");
        ink().remove(second); check(p.chainPreviewRelation(atoms(809)) == 0, "missing start still matched complete origin");
        ink().add(second); second.setAnchorDigit(3);
        check(p.chainPreviewRelation(atoms(809)) == 0, "same-count different candidate matched start");
        second.setAnchorDigit(2); second.setCandidateMarkKind(CIRCLE);
        check(p.chainPreviewRelation(atoms(809)) == 0, "mixed polarity start supplied false-premise evidence");
        second.setCandidateMarkKind(CROSS); second.setThoughtGroup(1);
        check(p.chainPreviewRelation(atoms(809)) == 0, "different colors were combined into start");
        second.setThoughtGroup(0); conclusion.setThoughtGroup(1);
        check(p.chainPreviewRelation(atoms(809)) == 0, "different-color conclusion borrowed source");
        conclusion.setThoughtGroup(0); first.setHypothesisStart(false); second.setHypothesisStart(false);
        check(p.chainPreviewRelation(atoms(809)) == 0, "ordinary crosses guessed as starts");
        first.setHypothesisStart(true); second.setHypothesisStart(true);
        String before = board(); p.setNextUserChainStrong(true); tap(0, 80, 9);
        check(tail().key().equals(atoms(809).key()) && active().getStrongRelations().get(0), "P conclusion event did not append strong edge");
        ink().clear(); check(active().getNodes().size() == 2 && tail().key().equals(atoms(809).key()),
                "P edit retroactively removed ordinary hand-authored edge");
        p.undoCurrentAnnotation(); check(tail().key().equals(atoms(1, 402).key()), "P edge undo lost mixed full origin");
        check(before.equals(board()), "P-derived chain event changed actual board");
        // Use the actual annotation undo/redo path, while the source itself is a structural fixture.
        reset(); origin(1, 402); mark(0, 0, 1, CROSS, true); mark(0, 40, 2, CROSS, true);
        mark(0, 80, 9, CIRCLE, false); call("pushDoodleUndo"); ink().remove(ink().size() - 1);
        preview(true); check(p.chainPreviewRelation(atoms(809)) == 0, "removed conclusion remained discoverable");
        p.setAnnotationTool(AnnotationTool.DOODLE); p.undoCurrentAnnotation();
        p.setAnnotationTool(AnnotationTool.FREE_CHAIN); preview(true);
        check(p.chainPreviewRelation(atoms(809)) == 3, "P undo did not restore current evidence");
        p.setAnnotationTool(AnnotationTool.DOODLE); p.redoCurrentAnnotation();
        p.setAnnotationTool(AnnotationTool.FREE_CHAIN); preview(true);
        check(p.chainPreviewRelation(atoms(809)) == 0, "P redo retained old conclusion evidence");
    }

    static void circlePartitionAndDedup() throws Exception {
        reset(); origin(1, 11); circleSource(0, 1, 11, 403, 805);
        mark(0, 60, 7, CIRCLE, false); preview(true); UserChainNode remainder = atoms(403, 805);
        check(p.chainPreviewRelation(remainder) == 3 && targetCount(remainder) == 1, "trusted whole-circle split missing mixed remainder");
        check(p.chainPreviewRelation(atoms(403)) == 0, "incomplete singleton remainder received group qualification");
        int history = undoSize(); tap(InputEvent.META_DOWN_MASK, 40, 3);
        assertNoAppend(1, history, "Command split circle remainder without independent relation");
        tap(0, 80, 5); check(tail().key().equals(remainder.key()), "circle remainder click lost member or included ordinary circle");
        p.undoCurrentAnnotation(); DoodleStroke mixed = mark(0, 70, 8, CROSS, true);
        check(p.chainPreviewRelation(remainder) == 0, "old mixed start was filtered to circles and accepted");
        ink().remove(mixed); check(p.chainPreviewRelation(remainder) == 3, "removing mixed start did not restore full-circle evidence");
        als(); circleSource(1, 14, 1, 11); preview(true);
        check(targetCount(atoms(1, 11)) == 1 && p.chainPreviewRelation(atoms(1, 11)) == 3,
                "same full target from ALS and trusted circle source duplicated or acquired weak qualification");
    }

    static JPopupMenu popup() {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        check(path.length > 0 && path[0] instanceof JPopupMenu, "overlapping complete-group chooser missing");
        return (JPopupMenu) path[0];
    }
    static JMenuItem choice(JPopupMenu popup, UserChainNode wanted) throws Exception {
        check(popup.getComponentCount() == 2, "chooser must contain exactly two complete groups and no singleton");
        JMenuItem found = null;
        for (int i = 0; i < popup.getComponentCount(); i++) {
            JMenuItem item = (JMenuItem) popup.getComponent(i); item.setArmed(true);
            UserChainNode shown = (UserChainNode) read("chainEndpointPreview");
            check(shown != null && shown.grouped(), "chooser hover previewed singleton");
            if (shown.key().equals(wanted.key())) found = item;
            item.setArmed(false);
        }
        check(found != null, "chooser omitted wanted complete proposition"); return found;
    }
    static void overlap() throws Exception {
        reset(); origin(1, 11); circleSource(0, 1, 11, 403, 805); circleSource(1, 1, 11, 403, 506);
        preview(true); p.setNextUserChainStrong(true);
        check(targetCount(atoms(403, 805)) == 1 && targetCount(atoms(403, 506)) == 1,
                "overlapping complete targets merged");
        tap(0, 40, 3);
    }
    static void chooserFreshChecks() throws Exception {
        overlap(); JPopupMenu menu = popup(); JMenuItem item = choice(menu, atoms(403, 805));
        int history = undoSize(); menu.setVisible(false); MenuSelectionManager.defaultManager().clearSelectedPath();
        assertNoAppend(1, history, "chooser cancel changed chain");
        overlap(); menu = popup(); item = choice(menu, atoms(403, 805));
        item.setArmed(true); ChainOriginProbe.render("chain-evidence-choice-hover"); item.doClick(); menu.setVisible(false);
        check(tail().key().equals(atoms(403, 805).key()), "chooser selection changed complete proposition");
        p.undoCurrentAnnotation(); check(active().getNodes().size() == 1, "chooser append was not one undo action");
        overlap(); menu = popup(); item = choice(menu, atoms(403, 805)); history = undoSize();
        ink().removeIf(s -> s.getThoughtGroup() == 0); item.doClick(); menu.setVisible(false);
        assertNoAppend(1, history, "stale chooser reused removed P evidence");
        overlap(); menu = popup(); item = choice(menu, atoms(403, 805)); history = undoSize();
        p.setNextUserChainStrong(false); item.doClick(); menu.setVisible(false);
        assertNoAppend(1, history, "stale chooser bypassed changed next-edge qualification");
        overlap(); menu = popup(); item = choice(menu, atoms(403, 805)); history = undoSize();
        p.getSudoku().delCandidate(40, 3); item.doClick(); menu.setVisible(false);
        assertNoAppend(1, history, "stale chooser appended a missing candidate");
        overlap(); menu = popup(); item = choice(menu, atoms(403, 805));
        p.undoCurrentAnnotation(); history = undoSize(); String origin = tail().key();
        item.doClick(); menu.setVisible(false);
        assertNoAppend(1, history, "stale chooser used prior complete source");
        check(tail().key().equals(origin), "stale chooser replaced changed current origin");
        reset(); origin(1); circleSource(0, 1, 11, 21); circleSource(1, 1, 11, 403);
        preview(true); p.setNextUserChainStrong(false); tap(0, 1, 1);
        check(tail().key().equals(atoms(11, 21).key()) && !active().getStrongRelations().get(0),
                "overlap must offer only eligible complete groups, without blocked group or singleton fallback");
    }

    static void alsFreshAndVisibility() throws Exception {
        alsWithoutOrdinaryCoverage(); preview(true); UserChainNode ones = atoms(1, 11); String before = board();
        call("pushBoxReasoningUndo"); boxes().get(0).clear();
        check(p.chainPreviewRelation(ones) == 0 && targetCount(ones) == 0, "removed S group retained ALS evidence");
        p.setAnnotationTool(AnnotationTool.BOX_SELECTION); p.undoCurrentAnnotation();
        p.setAnnotationTool(AnnotationTool.FREE_CHAIN); preview(true);
        check(p.chainPreviewRelation(ones) == 3, "S undo did not restore current complete ALS");
        p.setAnnotationTool(AnnotationTool.BOX_SELECTION); p.redoCurrentAnnotation();
        p.setAnnotationTool(AnnotationTool.FREE_CHAIN); preview(true);
        check(p.chainPreviewRelation(ones) == 0, "S redo retained historical ALS evidence");
        check(before.equals(board()), "S evidence undo/redo changed actual board");
        alsWithoutOrdinaryCoverage(); preview(true); p.getSudoku().setCandidate(1, 4, true);
        check(p.chainPreviewRelation(ones) == 0, "n/n+1 counted displayed subset instead of actual candidates");
        reset(); AlsManualChainProbe.restrict(p.getSudoku(), 0, "123");
        AlsManualChainProbe.restrict(p.getSudoku(), 40, "12"); boxes().get(0).add(0); boxes().get(0).add(40);
        origin(3); preview(true);
        check(p.chainPreviewRelation(atoms(1, 401)) == 0, "non-house n/n+1 cell set accepted as ALS");
        alsWithoutOrdinaryCoverage(); preview(true); String actualBefore = actualBoard();
        for (int cell = 0; cell < 81; cell++) for (int digit = 1; digit <= 9; digit++)
            p.getSudoku().setCandidate(cell, digit, p.getSudoku().isCandidate(cell, digit), true);
        // The user-edit API also updates the actual plane. Restore only that plane
        // to create an intentional display-only omission in this isolated fixture.
        p.getSudoku().setCandidate(0, 1, false, true);
        p.getSudoku().setCandidate(0, 1, true); p.setShowCandidates(false);
        check(p.chainPreviewRelation(ones) == 0 && targetCount(ones) == 0,
                "hiding one member kept or shrank the complete ALS target");
        check(p.getSudoku().isCandidate(0, 1) && actualBefore.equals(actualBoard()), "display visibility changed actual ALS candidate set");
        p.getSudoku().setCandidate(0, 1, true, true);
        check(p.chainPreviewRelation(ones) == 3 && targetCount(ones) == 1, "visibility restoration did not restore complete group");
        alsWithoutOrdinaryCoverage(); preview(true); int history = undoSize(); p.getSudoku().delCandidate(0, 3);
        check(targets().isEmpty() && p.chainPreviewRelation(ones) == 0,
                "missing actual origin candidate retained relation/discovery");
        tap(0, 0, 1); assertNoAppend(1, history, "stale origin event appended a target");
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        try {
            edt(() -> {
                ApplicationAppearance.initialize(AppearanceMode.LIGHT); f = new MainFrame(null); p = f.getSudokuPanel();
                f.setVisible(true);
                alsGroupClicks(); System.out.println("PASS: ALS full-group event append, one undo/redo, rendering and board isolation");
                commandAndGates(); System.out.println("PASS: independent Command singleton, no ordinary fallback, only-strong/only-weak/both gates");
                falseStarts(); System.out.println("PASS: P complete all-false origin, current source/undo/redo, no post-append binding");
                circlePartitionAndDedup(); System.out.println("PASS: trusted circle full remainder, mixed-start rejection and same-target dedup");
                chooserFreshChecks(); System.out.println("PASS: complete-group chooser, cancel, full append and fresh source/type/candidate/tail checks");
                alsFreshAndVisibility(); System.out.println("PASS: S current/undo/redo, real candidate and house validation, whole-group visibility");
                preview(false); check(!(Boolean) read("chainRelationPreviewHeld"), "preview release stuck");
                return null;
            });
            System.out.println("SCOPE: actual Swing listeners, popup actions and pixels; native Option/Command delivery and packaged app are separate checks");
        } catch (Throwable failure) {
            failure.printStackTrace(); System.exit(1);
        } finally {
            if (f != null) edt(() -> { MenuSelectionManager.defaultManager().clearSelectedPath(); f.dispose(); return null; });
        }
        System.exit(0);
    }
}
