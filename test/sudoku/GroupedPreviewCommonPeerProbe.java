/*
 * Copyright (C) 2026 HoDoKu contributors
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
import static sudoku.ChainEvidencePreviewProbe.*;

/** Regression for partial same-cell coverage leaking into whole-group preview.
 * Uses actual Swing listeners/paint; does not claim native Robot input. */
public final class GroupedPreviewCommonPeerProbe {
    static void screenshotFixture() throws Exception {
        reset();
        AlsManualChainProbe.restrict(p.getSudoku(), 0, "89");
        AlsManualChainProbe.restrict(p.getSudoku(), 2, "89");
        origin(9, 29);
    }
    static void rejectsPartialCell() throws Exception {
        screenshotFixture();
        UserChainNode group = atoms(9, 29), eight = atoms(8);
        // This is a broad OR-coverage implication, not an ordinary grouped link:
        // 8 in the left cell can coexist with 9 in the right cell.
        check(UserChainValidator.ordinaryStrong(p.getSudoku(), group, eight), "fixture lost old broad coverage");
        check(!UserChainValidator.weak(group, eight), "partial same-cell relation became whole-group mutual exclusion");
        check(p.chainPreviewRelation(eight) == 0, "screenshot: partial-cell 8 leaked into complete 9-group preview");
        check(p.chainPreviewRelation(atoms(28)) == 0, "symmetric partial-cell 8 leaked into preview");
        check(p.chainPreviewRelation(atoms(8, 28)) == 0, "partial cross-pairs leaked into grouped target");
        check(targetCount(eight) == 0 && targetCount(atoms(28)) == 0, "hidden partial-cell target remains discoverable");
        String before = actualBoard();
        BufferedImage off = ChainOriginProbe.render("group-peer-before");
        preview(true);
        BufferedImage on = ChainOriginProbe.render("group-peer-fixed");
        check(changedNear(off, on, at(0, 8), 8) == 0, "invalid 8 still paints a preview cue");
        for (boolean strong : new boolean[]{true, false}) for (int modifiers : new int[]{0, InputEvent.META_DOWN_MASK}) {
            p.setNextUserChainStrong(strong);
            int history = undoSize(); tap(modifiers, 0, 8);
            assertNoAppend(1, history, "invalid 8 click appended node/history, modifiers=" + modifiers + ", strong=" + strong);
            check(tail().key().equals(group.key()), "invalid click split group");
        }
        check(before.equals(actualBoard()), "preview/click changed actual board");
        System.out.println("PASS: screenshot-equivalent grouped 9 -> local 8 absent, unpainted, unclickable for ordinary/Command and both edge types");
    }
    static void commonPeerAndCoverage() throws Exception {
        screenshotFixture();
        check(p.chainPreviewRelation(atoms(19)) == 1, "all-member peer should be weak before complete coverage");
        for (int cell : new int[]{3,4,5,6,7,8}) p.getSudoku().delCandidate(cell, 9);
        check(p.chainPreviewRelation(atoms(19)) == 2, "complete row grouped conjugate lost strong qualification");
        preview(true); p.setNextUserChainStrong(true); tap(0, 1, 9);
        check(active().getNodes().size() == 2 && tail().key().equals(atoms(19).key()), "qualified grouped conjugate failed actual click");
        reset(); origin(9, 99);
        check(p.chainPreviewRelation(atoms(199)) == 1, "common column/box weak target rejected");
        check(p.chainPreviewRelation(atoms(39)) == 0, "candidate seeing only one group member leaked");
        System.out.println("PASS: full-group common row/column/box peers retained; strong needs complete cell/house coverage");
    }
    static void independentEvidence() throws Exception {
        als();
        check(!UserChainValidator.weak(tail(), atoms(1,11)) && p.chainPreviewRelation(atoms(1,11)) == 3,
                "ordinary peer gate suppressed independently supported ALS strong-only group");
        screenshotFixture();
        mark(0,0,9,CROSS,true); mark(0,2,9,CROSS,true); mark(0,0,8,CIRCLE,false);
        check(p.chainPreviewRelation(atoms(8)) == 3, "explicit false-start premise lost its independent strong-only target");
        screenshotFixture(); circleSource(0,9,29,8);
        check(p.chainPreviewRelation(atoms(8)) == 3, "declared true complete partition lost strong-only qualification");
        reset(); AlsManualChainProbe.restrict(p.getSudoku(),0,"89"); origin(9);
        check(p.chainPreviewRelation(atoms(8)) == 2, "single-candidate bivalue-cell relation regressed");
        System.out.println("PASS: ALS/P strong-only evidence and ordinary singleton bivalue relations preserved");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement","true");
        int status = 0;
        try {
            edt(() -> {
                ApplicationAppearance.initialize(AppearanceMode.LIGHT);
                f=new MainFrame(null); p=f.getSudokuPanel();
                rejectsPartialCell(); commonPeerAndCoverage(); independentEvidence(); return null;
            });
        } catch (Throwable failure) { failure.printStackTrace(); status = 1; }
        finally { if(f!=null) edt(() -> {f.dispose(); return null;}); }
        System.exit(status);
    }
}
