/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Pure checks of frozen atomic conclusions, role planning and OR contradiction semantics. */
public final class DoodleChainTranscriptionProbe {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static Set<Integer> atoms(Integer... values) { return new TreeSet<Integer>(Arrays.asList(values)); }
    private static Sudoku2 board() {
        Sudoku2 board = new Sudoku2(); board.setSudoku(new String(new char[81]).replace('\0', '0')); return board;
    }
    private static DoodleStroke mark(int atom, int kind, boolean start) {
        DoodleStroke mark = new DoodleStroke(Color.ORANGE, .04f);
        mark.setAnchorCell(atom / 10); mark.setAnchorDigit(atom % 10);
        mark.setCandidateMarkKind(kind); mark.setThoughtGroup(0); mark.setHypothesisStart(start);
        return mark;
    }
    private static DoodleChainTranscription.Results results(Set<Integer> truths, Set<Integer> falses) {
        return new DoodleChainTranscription.Results(truths, falses);
    }
    private static List<DoodleStroke> planned(List<DoodleStroke> starts, DoodleChainTranscription.Plan plan) {
        List<DoodleStroke> marks = new ArrayList<DoodleStroke>(starts);
        for (int atom : plan.trueAtoms) marks.add(mark(atom, 1, false));
        for (int atom : plan.falseAtoms) marks.add(mark(atom, 2, false));
        return marks;
    }

    public static void main(String[] args) {
        SolutionStep first = new SolutionStep(SolutionType.AIC); first.addIndex(80); first.addValue(5);
        SolutionStep second = new SolutionStep(SolutionType.AIC); second.addIndex(79); second.addValue(4);
        SolutionStep deletion = new SolutionStep(SolutionType.AIC);
        deletion.addCandidateToDelete(80, 5); deletion.addCandidateToDelete(32, 7);
        UserChainProof path = new UserChainProof(); path.getNodes().add(new UserChainNode(10, 7));
        path.getTruths().add(true); path.addTo(first);
        List<SolutionStep> steps = new ArrayList<SolutionStep>(Arrays.asList(first, second, deletion, first));
        DoodleChainTranscription.Results captured = DoodleChainTranscription.capture(
                new UserChainValidator.Result(UserChainValidator.Status.ASSUMED, 0, steps));
        check(captured.trueAtoms.equals(atoms(794, 805)) && captured.falseAtoms.equals(atoms(327, 805)),
                "capture lost a later placement, deletion or conflicting direction");
        check(!captured.trueAtoms.contains(107), "proof-path truth became a conclusion");
        first.addIndex(70); steps.clear();
        check(captured.trueAtoms.equals(atoms(794, 805)), "captured result retained mutable step references");
        boolean immutable = false;
        try { captured.trueAtoms.add(107); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable && DoodleChainTranscription.capture(null).isEmpty(), "results are mutable or empty capture invented atoms");

        Sudoku2 board = board();
        DoodleChainTranscription.Results both = results(atoms(805), atoms(805));
        List<DoodleStroke> starts = Collections.singletonList(mark(805, 1, true));
        DoodleChainTranscription.Plan single = DoodleChainTranscription.plan(board, starts, 0, both);
        check(single != null && single.trueAtoms.isEmpty() && single.falseAtoms.equals(atoms(805))
                && single.circleCount == 0 && single.crossCount == 1, "single-circle same-direction result was not absorbed");
        check(DoodleContradictionBasis.analyze(board, planned(starts, single), 0).hasRefutation(),
                "opposite conclusion no longer refutes a single-circle start");
        starts = Collections.singletonList(mark(805, 2, true));
        single = DoodleChainTranscription.plan(board, starts, 0, both);
        check(single != null && single.trueAtoms.equals(atoms(805)) && single.falseAtoms.isEmpty(),
                "single-cross result absorption changed polarity");

        for (boolean normalized : new boolean[]{false, true}) {
            starts = Arrays.asList(mark(805, 1, true), mark(795, 1, true));
            if (normalized) for (DoodleStroke start : starts) {
                start.setHypothesisGroupKind(1); start.setHypothesisConditionId(1);
            }
            List<DoodleStroke> partial = new ArrayList<DoodleStroke>(starts); partial.add(mark(805, 2, false));
            check(!DoodleContradictionBasis.analyze(board, partial, 0).hasConflict(),
                    "partially negated OR start became a contradiction");
            DoodleChainTranscription.Plan orPlan = DoodleChainTranscription.plan(board, starts, 0, both);
            check(orPlan != null && orPlan.trueAtoms.equals(atoms(805)) && orPlan.falseAtoms.equals(atoms(805)),
                    "OR member's independently derived truth was absorbed into its premise");
            DoodleContradictionBasis.Analysis contradiction = DoodleContradictionBasis.analyze(board, planned(starts, orPlan), 0);
            check(contradiction.hasRefutation() && contradiction.conflictAtoms().equals(atoms(805)),
                    "independent member truth/false conflict was hidden by the OR start");
        }
        starts = Arrays.asList(mark(805, 2, true), mark(795, 2, true));
        DoodleChainTranscription.Plan allFalse = DoodleChainTranscription.plan(board, starts, 0,
                results(atoms(805), atoms(805, 795)));
        check(allFalse != null && allFalse.falseAtoms.isEmpty() && allFalse.trueAtoms.equals(atoms(805)),
                "all-false starts did not absorb their matching false atoms");
        check(DoodleContradictionBasis.analyze(board, planned(starts, allFalse), 0).isOrConclusion(),
                "refuted all-false premise became individually true members");

        check(DoodleChainTranscription.plan(board, Collections.<DoodleStroke>emptyList(), 0, both) == null,
                "missing starts accepted");
        check(DoodleChainTranscription.plan(board, Arrays.asList(mark(805, 1, true), mark(795, 2, true)), 0, both) == null,
                "mixed starts accepted");
        board.delCandidate(80, 5);
        check(DoodleChainTranscription.plan(board, starts, 0, both) == null, "stale start accepted");
        board = board(); board.delCandidate(32, 7);
        check(DoodleChainTranscription.plan(board, starts, 0, captured) == null, "partial stale result was silently planned");
        check(starts.get(0).isHypothesisStart() && starts.get(0).getCandidateMarkKind() == 2,
                "planning mutated the original marks");

        DoodleStroke root = mark(805, 1, true); root.setHypothesisGroupKind(1);
        DoodleStroke ordinary = mark(805, 1, false); ordinary.setConclusionOutlined(true);
        DoodleStroke other = mark(805, 1, false); other.setThoughtGroup(1);
        List<DoodleStroke> ink = Arrays.asList(root, ordinary, other);
        DoodleFrameIndex index = new DoodleFrameIndex(ink, ink);
        check(index.visibleSources(root).equals(Arrays.asList(ordinary, other)) && index.sourceMask(root) == 3,
                "role preservation changed color-source deduplication");
        check(index.visibleRoleMarks(root).containsAll(ink) && index.visibleRoleMarks(root).size() == 3,
                "frame lost the OR start or the independent selected truth role");
        DoodleStroke later = mark(805, 1, false);
        ink = Arrays.asList(root, ordinary, later, other); index = new DoodleFrameIndex(ink, ink);
        check(index.visibleRoleMarks(root).contains(root) && index.visibleRoleMarks(root).contains(later)
                && !index.visibleRoleMarks(root).contains(ordinary), "same-role last-mark parity changed");
        DoodleFrameIndex preview = new DoodleFrameIndex(ink, Collections.singletonList(later));
        check(preview.visibleRoleMarks(root).equals(Collections.singletonList(later)) && preview.sourceMask(root) == 3,
                "erase-preview roles leaked raw starts or changed raw source masks");
        System.out.println("PASS DoodleChainTranscriptionProbe: " + checks + " frozen conclusions, OR/atomic roles and refutations");
    }
}
