/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.MenuSelectionManager;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEvidencePreviewProbe.*;

/** Source discovery is temporary; accepted edges use the existing Enter contract.
 * --model-only creates no window and verifies model semantics, not event delivery.
 */
public final class ChainEvidenceLifecycleProbe {
    private static final int A = 1, B = 11, C = 91;

    private static DoodleStroke premise(int group, int atom, int kind, boolean start) {
        DoodleStroke mark = new DoodleStroke();
        mark.setColor(Options.getInstance().getColoringColors()[group * 2]);
        mark.setAnchorCell(atom / 10); mark.setAnchorDigit(atom % 10);
        mark.setCandidateMarkKind(kind); mark.setThoughtGroup(group); mark.setHypothesisStart(start);
        return mark;
    }
    private static List<DoodleStroke> premises() {
        return new ArrayList<DoodleStroke>(Arrays.asList(
                premise(0, A, CROSS, true), premise(0, B, CIRCLE, false),
                premise(1, C, CROSS, true), premise(1, A, CIRCLE, false)));
    }
    private static UserChainNode discovered(Sudoku2 board, UserChainNode source, int target,
            List<DoodleStroke> ink) {
        for (UserChainNode node : ChainPreviewBasis.strongTargets(board, source, ink, null))
            if (node.key().equals(atoms(target).key())) return node;
        throw new AssertionError("fixture premise did not discover complete target " + target);
    }
    private static UserChain acceptedRing(Sudoku2 board, List<DoodleStroke> ink) {
        UserChain ring = new UserChain();
        UserChainNode a = atoms(A), b = discovered(board, a, B, ink);
        ring.getNodes().add(a); ring.getNodes().add(b); ring.getStrongRelations().add(true);
        UserChainNode c = atoms(C);
        check(UserChainValidator.weak(b, c), "fixture weak B-C relation missing");
        ring.getNodes().add(c); ring.getStrongRelations().add(false);
        check(discovered(board, c, A, ink).key().equals(a.key()), "closure fixture target differs from origin");
        ring.getStrongRelations().add(true); ring.setClosed(true);
        return ring;
    }
    private static SolutionStep assumedPlacement(Sudoku2 board, UserChain ring) {
        check(UserChainValidator.validate(board, ring).status == UserChainValidator.Status.INVALID,
                "blank fixture acquired an independent native proof");
        UserChainValidator.Result result = UserChainValidator.preview(board, ring);
        check(result.status == UserChainValidator.Status.ASSUMED, "accepted P edges must remain conditional");
        check(result.invalidRelations.size() == 2, "conditional fixture lost native edge diagnostics");
        for (SolutionStep step : result.steps) if (step.isAuthoredPlacement()) {
            check(step.getIndices().size() == 1 && step.getIndices().get(0) == A / 10
                    && step.getValues().size() == 1 && step.getValues().get(0) == A % 10,
                    "double-strong closure no longer derives its junction truth");
            return step;
        }
        throw new AssertionError("conditional ring produced no actual placement step");
    }
    private static String snapshot(Sudoku2 board) {
        StringBuilder result = new StringBuilder();
        for (int cell = 0; cell < 81; cell++) result.append(board.getValue(cell)).append(':')
                .append(Arrays.toString(board.getAllCandidates(cell))).append(';');
        return result.toString();
    }
    private static void modelChecks() {
        Sudoku2 board = GroupedChainProbe.blank(); List<DoodleStroke> ink = premises();
        String before = snapshot(board); UserChain ring = acceptedRing(board, ink);
        Object conclusion = NativeReasoningMatcher.keyForStep(assumedPlacement(board, ring));
        ink.clear();
        List<SudokuSet> boxes = new ArrayList<SudokuSet>();
        SudokuSet region = new SudokuSet(); region.add(0); region.add(1); boxes.add(region);
        region.clear(); boxes.clear();
        check(ChainPreviewBasis.strongTargets(board, atoms(C), ink, boxes).isEmpty(),
                "withdrawn evidence remained discoverable");
        check(conclusion.equals(NativeReasoningMatcher.keyForStep(assumedPlacement(board, ring))),
                "ordinary accepted edge conclusions changed when discovery source was removed");
        check(before.equals(snapshot(board)), "model preview changed board");
        System.out.println("PASS MODEL: P discovery builds strong/weak/strong ring; source withdrawal preserves ordinary ASSUMED conclusion and board");
    }
    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static UserChain currentRing() {
        UserChainAssembly.Result result = UserChainAssembly.assemble(p.currentReasoningChains());
        check(result.chain != null, "accepted ring disappeared or disconnected"); return result.chain;
    }
    private static void withdrawSources() {
        p.clearDoodlesWithUndo(); p.clearBoxReasoningWithUndo();
    }
    private static void drawThroughPreview() throws Exception {
        reset(); ink().addAll(premises());
        boxes().get(0).add(0); boxes().get(0).add(1);
        origin(A); p.setNextUserChainStrong(true); preview(true);
        tap(0, B / 10, B % 10);
        check(!p.isNextUserChainStrong(), "strong append did not alternate next edge");
        tap(0, C / 10, C % 10);
        check(p.isNextUserChainStrong(), "weak append did not alternate next edge");
        tap(0, A / 10, A % 10); preview(false);
        check(UserChainAssembly.sameStructure(acceptedRing(p.getSudoku(), premises()), currentRing()),
                "actual held-preview clicks did not form the intended complete ring");
        assumedPlacement(p.getSudoku(), currentRing());
    }
    private static void swingChecks(boolean removeBeforeEnter) throws Exception {
        final String[] before = new String[1];
        edt(() -> {
            drawThroughPreview(); before[0] = snapshot(p.getSudoku());
            UserChain accepted = currentRing();
            if (removeBeforeEnter) withdrawSources();
            check(UserChainAssembly.sameStructure(accepted, currentRing()), "source edit changed accepted chain");
            call("handleReasoningEnter"); return null;
        });
        await(true);
        final int[] placement = new int[2];
        edt(() -> {
            check(before[0].equals(snapshot(p.getSudoku())), "first Enter changed actual board");
            Object proposal = read("reasoningProposal");
            check(Boolean.TRUE.equals(field(field(proposal, "request"), "assumedRelations")),
                    "actual Enter request did not retain ASSUMED status");
            SolutionStep expected = assumedPlacement(p.getSudoku(), currentRing());
            check(NativeReasoningMatcher.keyForStep(expected).equals(NativeReasoningMatcher.keyForStep(p.getStep())),
                    "actual first Enter preview differs from the authored conditional proof");
            placement[0] = p.getStep().getIndices().get(0); placement[1] = p.getStep().getValues().get(0);
            UserChain accepted = currentRing();
            if (!removeBeforeEnter) withdrawSources();
            check(read("reasoningProposal") == proposal && p.getStep() != null,
                    "P/S source edit added a new chain-proposal invalidation rule");
            check(UserChainAssembly.sameStructure(accepted, currentRing()), "P/S edit deleted an accepted ring edge");
            check(before[0].equals(snapshot(p.getSudoku())), "P/S source removal changed actual board");
            call("handleReasoningEnter"); return null;
        });
        await(false);
        edt(() -> {
            check(p.getSudoku().getValue(placement[0]) == placement[1], "second Enter did not execute its actual conditional placement");
            check(!before[0].equals(snapshot(p.getSudoku())), "conditional apply did not change actual board");
            p.undo(); check(before[0].equals(snapshot(p.getSudoku())), "native undo did not restore full board candidates/values");
            return null;
        });
        System.out.println("PASS SWING: evidence removed " + (removeBeforeEnter ? "before first Enter" : "during proposal")
                + "; held-preview clicks, ordinary ASSUMED Enter, second confirmation and native undo");
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "--model-only".equals(args[0])) {
            modelChecks();
            System.out.println("SCOPE: pure model only; no Swing controller, actual mouse events, native Enter application or undo executed");
            return;
        }
        if (args.length != 0) throw new IllegalArgumentException("Expected no arguments or --model-only");
        System.setProperty("apple.awt.UIElement", "true");
        try {
            modelChecks();
            edt(() -> { ApplicationAppearance.initialize(AppearanceMode.LIGHT); f = new MainFrame(null);
                p = f.getSudokuPanel(); f.setVisible(true); return null; });
            swingChecks(true); swingChecks(false);
            System.out.println("SCOPE: actual Swing dispatched mouse listeners and Enter controller; not native Robot modifier delivery or packaged-app acceptance");
        } finally {
            if (f != null) edt(() -> { MenuSelectionManager.defaultManager().clearSelectedPath(); f.dispose(); return null; });
        }
        System.exit(0);
    }
}
