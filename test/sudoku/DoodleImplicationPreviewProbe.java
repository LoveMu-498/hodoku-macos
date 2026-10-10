/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuSelectionManager;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.active;
import static sudoku.ChainEvidencePreviewProbe.*;

/** Actual Swing event and popup paths on isolated, non-focusable test windows.
 * No native input injection, foreground activation or saved implication references.
 */
public final class DoodleImplicationPreviewProbe {
    private static final int FAR = 809;

    private static void circleImplication(int... complete) throws Exception {
        reset(); origin(complete); circleSource(0, complete);
        mark(0, FAR / 10, FAR % 10, CROSS, false);
    }
    private static void requireWeakTarget(int atom, String label) throws Exception {
        UserChainNode target = atoms(atom);
        check(!UserChainValidator.weak(tail(), target), label + ": fixture has ordinary all-pair weak relation");
        int relation = p.chainPreviewRelation(target);
        check(relation == 1, label + ": expected weak-only qualification 1, got " + relation);
        check(targetCount(target) == 1, label + ": weak target missing or duplicated");
    }
    private static void singleAndGroupEvents() throws Exception {
        for (int[] complete : new int[][] {{1}, {1, 402}}) {
            circleImplication(complete); p.setNextUserChainStrong(true); String before = actualBoard();
            BufferedImage off = ChainOriginProbe.render(null); preview(true);
            requireWeakTarget(FAR, "single/group remote false conclusion");
            check(!UserChainValidator.ordinaryStrong(p.getSudoku(), tail(), atoms(FAR)),
                    "remote fixture has an independent native strong coverage");
            BufferedImage blocked = ChainOriginProbe.render(null);
            check(changedNear(off, blocked, at(FAR / 10, FAR % 10), 22) > 20,
                    "weak implication did not draw blocked preview cue");
            int history = undoSize(); tap(0, FAR / 10, FAR % 10);
            assertNoAppend(1, history, "weak implication accepted as next strong edge");
            p.setNextUserChainStrong(false); BufferedImage allowed = ChainOriginProbe.render(null);
            check(changedNear(blocked, allowed, at(FAR / 10, FAR % 10), 22) > 10,
                    "weak target cue did not respond to next-edge change");
            tap(0, FAR / 10, FAR % 10);
            check(active().getNodes().size() == 2 && tail().key().equals(atoms(FAR).key())
                    && !active().getStrongRelations().get(0), "actual weak preview click lost source group or edge type");
            p.undoCurrentAnnotation(); check(tail().key().equals(atoms(complete).key()), "weak edge undo lost complete origin");
            p.redoCurrentAnnotation(); check(tail().key().equals(atoms(FAR).key()), "weak edge redo lost conclusion");
            check(before.equals(actualBoard()), "doodle implication preview/events changed actual board");
        }
    }
    private static void exactPremisesAndPolarity() throws Exception {
        circleImplication(1, 402); preview(true);
        DoodleStroke second = null;
        for (DoodleStroke stroke : ink()) if (stroke.isHypothesisStart() && stroke.getAnchorCell() == 40) second = stroke;
        check(second != null && second.getColor() != null, "colored start fixture missing");
        ink().remove(second); check(p.chainPreviewRelation(atoms(FAR)) == 0, "fewer starts matched extra origin member");
        ink().add(second); second.setAnchorDigit(3);
        check(p.chainPreviewRelation(atoms(FAR)) == 0, "same-count wrong atom matched origin");
        second.setAnchorDigit(2); second.setCandidateMarkKind(CROSS);
        check(p.chainPreviewRelation(atoms(FAR)) == 0, "mixed old start accepted by filtering circles");
        second.setCandidateMarkKind(CIRCLE); second.setThoughtGroup(1);
        check(p.chainPreviewRelation(atoms(FAR)) == 0, "different color starts were merged");
        second.setThoughtGroup(0); second.setHypothesisStart(false);
        check(p.chainPreviewRelation(atoms(FAR)) == 0, "ordinary non-start circle inflated complete premise");
        second.setHypothesisStart(true); DoodleStroke extra = mark(0, 60, 7, CIRCLE, true);
        check(p.chainPreviewRelation(atoms(FAR)) == 0, "extra circle start was omitted to match smaller origin");
        ink().remove(extra); mark(0, 70, 8, CROSS, true);
        check(p.chainPreviewRelation(atoms(FAR)) == 0, "start cross treated as a non-start false conclusion");

        reset(); origin(1); mark(0, 0, 1, CIRCLE, false); mark(0, FAR / 10, FAR % 10, CROSS, false);
        preview(true); check(p.chainPreviewRelation(atoms(FAR)) == 0, "ordinary circle guessed as a true start");
        reset(); origin(1); circleSource(0, 1); mark(1, FAR / 10, FAR % 10, CROSS, false);
        preview(true); check(p.chainPreviewRelation(atoms(FAR)) == 0, "different-color cross borrowed premise");
        reset(); origin(1); mark(0, 0, 1, CROSS, true); mark(0, FAR / 10, FAR % 10, CROSS, false);
        preview(true); check(p.chainPreviewRelation(atoms(FAR)) == 0, "false start plus false mark invented implication");
    }
    private static void noSubsetBorrowingOrStaleClick() throws Exception {
        reset(); origin(1); circleSource(0, 1, 402); mark(0, FAR / 10, FAR % 10, CROSS, false);
        preview(true); p.setNextUserChainStrong(false); int history = undoSize();
        check(p.chainPreviewRelation(atoms(FAR)) == 0, "single member borrowed OR-group true premise");
        tap(0, FAR / 10, FAR % 10); assertNoAppend(1, history, "ordinary click borrowed a start subset");
        tap(InputEvent.META_DOWN_MASK, FAR / 10, FAR % 10);
        assertNoAppend(1, history, "Command click borrowed a start subset");
        reset(); origin(1, 402, 607); circleSource(0, 1, 402); mark(0, FAR / 10, FAR % 10, CROSS, false);
        preview(true); check(p.chainPreviewRelation(atoms(FAR)) == 0, "larger origin borrowed smaller complete premise");

        circleImplication(1, 402); preview(true); p.setNextUserChainStrong(false); requireWeakTarget(FAR, "before source removal");
        history = undoSize(); p.clearDoodlesWithUndo();
        check(p.chainPreviewRelation(atoms(FAR)) == 0 && targetCount(atoms(FAR)) == 0, "removed source retained weak discovery");
        tap(0, FAR / 10, FAR % 10); assertNoAppend(1, history, "stale ordinary click appended withdrawn weak relation");
        tap(InputEvent.META_DOWN_MASK, FAR / 10, FAR % 10);
        assertNoAppend(1, history, "stale Command click appended withdrawn weak relation");
    }
    private static void qualifiedCommandAndCandidateFreshness() throws Exception {
        circleImplication(1, 402); circleSource(1, 1, 402); mark(1, FAR / 10, FAR % 10, CROSS, false);
        preview(true); p.setNextUserChainStrong(false); requireWeakTarget(FAR, "same complete premise in two colors");
        String before = actualBoard(); int history = undoSize();
        tap(InputEvent.META_DOWN_MASK, FAR / 10, FAR % 10);
        check(active().getNodes().size() == 2 && active().getNodes().get(0).key().equals(atoms(1, 402).key())
                && tail().key().equals(atoms(FAR).key()) && !active().getStrongRelations().get(0),
                "qualified Command did not append independent weak singleton from complete OR origin");
        check(undoSize() == history + 1 && before.equals(actualBoard()), "qualified Command history/board boundary");

        for (int absent : new int[]{1, 402, FAR}) {
            circleImplication(1, 402); preview(true); requireWeakTarget(FAR, "before candidate removal");
            p.getSudoku().delCandidate(absent / 10, absent % 10);
            check(p.chainPreviewRelation(atoms(FAR)) == 0 && targetCount(atoms(FAR)) == 0,
                    "missing actual premise/conclusion candidate retained weak target " + absent);
            check(tail().key().equals(atoms(1, 402).key()), "query silently shrank accepted origin after candidate removal");
        }
        for (int filled : new int[]{1, 402}) {
            circleImplication(1, 402); preview(true); requireWeakTarget(FAR, "before start placement");
            p.getSudoku().setCell(filled / 10, filled % 10);
            check(p.chainPreviewRelation(atoms(FAR)) == 0 && targetCount(atoms(FAR)) == 0,
                    "filled actual premise retained weak target " + filled);
        }
        // Keep the surviving member as the actual chain origin. Ignoring the
        // missing recorded start would incorrectly turn this into an exact match.
        reset(); origin(1); circleSource(0, 1, 402); mark(0, FAR / 10, FAR % 10, CROSS, false);
        p.getSudoku().delCandidate(40, 2); preview(true);
        check(p.chainPreviewRelation(atoms(FAR)) == 0 && targetCount(atoms(FAR)) == 0,
                "absent recorded start was dropped to fabricate a smaller premise");
    }
    private static void weakCannotPromotePartialCoverage() throws Exception {
        reset(); AlsManualChainProbe.restrict(p.getSudoku(), 0, "12"); origin(1, 402);
        circleSource(0, 1, 402); mark(0, 0, 2, CROSS, false); preview(true);
        check(!UserChainValidator.weak(tail(), atoms(2)), "partial-cell fixture became all-pair native weak");
        check(UserChainValidator.ordinaryStrong(p.getSudoku(), tail(), atoms(2)), "partial-cell coverage witness missing");
        requireWeakTarget(2, "basis weak cannot supply ordinary strong prerequisite");
        p.setNextUserChainStrong(true); int history = undoSize(); tap(0, 0, 2);
        assertNoAppend(1, history, "basis weak promoted partial-cell coverage to both/strong");
        p.setNextUserChainStrong(false); tap(0, 0, 2);
        check(tail().key().equals(atoms(2).key()) && !active().getStrongRelations().get(0),
                "partial-cell basis weak did not append chosen weak edge");
    }
    private static void independentStrongStillWorks() throws Exception {
        reset(); origin(1, 402); mark(0, 0, 1, CROSS, true); mark(0, 40, 2, CROSS, true);
        mark(0, FAR / 10, FAR % 10, CIRCLE, false); mark(0, 70, 8, CROSS, false);
        preview(true); check(p.chainPreviewRelation(atoms(FAR)) == 3, "existing all-false complete group lost strong-only implication");
        check(p.chainPreviewRelation(atoms(708)) == 0, "ordinary false conclusion created wrong implication polarity");
        p.setNextUserChainStrong(true); tap(0, FAR / 10, FAR % 10);
        check(active().getStrongRelations().get(0), "existing strong implication event failed");
        circleImplication(1, 402); mark(1, 0, 1, CROSS, true); mark(1, 40, 2, CROSS, true);
        mark(1, FAR / 10, FAR % 10, CIRCLE, false); preview(true);
        check(p.chainPreviewRelation(atoms(FAR)) == 2 && targetCount(atoms(FAR)) == 1,
                "independent strong and weak evidence failed to merge or duplicated target");
    }
    private static void weakAlongsideOldPopup() throws Exception {
        overlap(); circleSource(2, 1, 11); mark(2, 40, 3, CROSS, false);
        check(p.chainPreviewRelation(atoms(403)) == 1, "popup fixture lacks independent weak singleton");
    }
    private static void stalePopupMustRecheckFullEndpoint() throws Exception {
        weakAlongsideOldPopup(); JPopupMenu menu = popup(); JMenuItem item = choice(menu, atoms(403, 805));
        int history = undoSize(); ink().removeIf(stroke -> stroke.getThoughtGroup() == 0);
        check(p.chainPreviewRelation(atoms(403)) == 1, "remaining current weak evidence vanished");
        item.doClick(); menu.setVisible(false); MenuSelectionManager.defaultManager().clearSelectedPath();
        assertNoAppend(1, history, "stale full-group popup borrowed current singleton weak evidence");
        weakAlongsideOldPopup(); menu = popup(); item = choice(menu, atoms(403, 805)); history = undoSize();
        p.setNextUserChainStrong(false); item.doClick(); menu.setVisible(false);
        MenuSelectionManager.defaultManager().clearSelectedPath();
        assertNoAppend(1, history, "old strong-group popup fell back to independently weak singleton");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true"); int status = 0;
        try {
            edt(() -> {
                ApplicationAppearance.initialize(AppearanceMode.LIGHT); f = new MainFrame(null); p = f.getSudokuPanel();
                f.setAutoRequestFocus(false); f.setFocusableWindowState(false); f.setLocation(-4000, -4000); f.setVisible(true);
                singleAndGroupEvents(); System.out.println("PASS: complete single/group circle-to-cross weak targets, actual event gates and rendered cues");
                exactPremisesAndPolarity(); System.out.println("PASS: exact current starts, metadata, color and polarity boundaries");
                noSubsetBorrowingOrStaleClick(); System.out.println("PASS: ordinary/Command source-subset rejection and current-source removal gates");
                qualifiedCommandAndCandidateFreshness(); System.out.println("PASS: qualified Command weak append, cross-color target dedup and immediate actual-candidate/placement invalidation");
                weakCannotPromotePartialCoverage(); System.out.println("PASS: independent basis weak cannot promote partial-cell native coverage to strong/both");
                independentStrongStillWorks(); System.out.println("PASS: all-false complete-group strong implication and independent strong/weak merge");
                stalePopupMustRecheckFullEndpoint(); System.out.println("PASS: old popup rechecks complete endpoint and current next-edge qualification");
                preview(false); return null;
            });
            System.out.println("SCOPE: dispatched Swing listeners, actual popup actions and rendered pixels on non-focusable test window; no Robot/native input or Enter-lifetime expansion");
        } catch (Throwable failure) { failure.printStackTrace(); status = 1; }
        finally {
            if (f != null) edt(() -> { MenuSelectionManager.defaultManager().clearSelectedPath(); f.dispose(); return null; });
        }
        System.exit(status);
    }
}
