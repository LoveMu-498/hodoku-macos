/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Frozen atomic chain conclusions and a read-only plan for their P-mark roles. */
final class DoodleChainTranscription {
    private DoodleChainTranscription() { }

    static final class Results {
        final SortedSet<Integer> trueAtoms, falseAtoms;
        Results(Set<Integer> truths, Set<Integer> falses) {
            trueAtoms = immutable(truths); falseAtoms = immutable(falses);
        }
        boolean isEmpty() { return trueAtoms.isEmpty() && falseAtoms.isEmpty(); }
    }

    static final class Plan {
        final SortedSet<Integer> startAtoms, trueAtoms, falseAtoms;
        final int startKind, circleCount, crossCount;
        Plan(Set<Integer> starts, int kind, Set<Integer> truths, Set<Integer> falses) {
            startAtoms = immutable(starts); startKind = kind;
            trueAtoms = immutable(truths); falseAtoms = immutable(falses);
            circleCount = trueAtoms.size(); crossCount = falseAtoms.size();
        }
    }

    private static SortedSet<Integer> immutable(Set<Integer> atoms) {
        return Collections.unmodifiableSortedSet(new TreeSet<Integer>(atoms));
    }

    /** Capture conclusions only; proof-path truth colors never become P marks. */
    static Results capture(UserChainValidator.Result result) {
        Set<Integer> truths = new TreeSet<Integer>(), falses = new TreeSet<Integer>();
        if (result != null) for (SolutionStep step : result.steps) {
            List<Integer> indices = step.getIndices(), values = step.getValues();
            if (!indices.isEmpty() && !values.isEmpty()) {
                if (values.size() != 1 && values.size() != indices.size())
                    throw new IllegalArgumentException("Incomplete chain placement conclusions");
                for (int i = 0; i < indices.size(); i++)
                    truths.add(atom(indices.get(i), values.get(values.size() == 1 ? 0 : i)));
            }
            for (Candidate candidate : step.getCandidatesToDelete())
                falses.add(atom(candidate.getIndex(), candidate.getValue()));
        }
        return new Results(truths, falses);
    }

    private static int atom(int cell, int digit) {
        if (cell < 0 || cell >= Sudoku2.LENGTH || digit < 1 || digit > Sudoku2.UNITS)
            throw new IllegalArgumentException("Invalid chain conclusion candidate");
        return cell * 10 + digit;
    }

    /** Plan ordinary marks without editing ink or claiming the starts justify the chain. */
    static Plan plan(Sudoku2 board, List<DoodleStroke> marks, int group, Results results) {
        if (board == null || marks == null || results == null || results.isEmpty() || group < 0 || group > 5)
            return null;
        Set<Integer> starts = new TreeSet<Integer>();
        int kind = DoodleStroke.MARK_NONE;
        for (DoodleStroke mark : marks) {
            if (mark == null || mark.isProofOnly() || mark.isRefutationResult()
                    || mark.getThoughtGroup() != group || !mark.isHypothesisStart()) continue;
            int nextKind = mark.getCandidateMarkKind();
            if (!mark.isStandardCandidateMark()
                    || !DoodleContradictionBasis.currentCandidate(board, mark.getAnchorCell(), mark.getAnchorDigit())
                    || kind != DoodleStroke.MARK_NONE && kind != nextKind) return null;
            kind = nextKind;
            starts.add(atom(mark.getAnchorCell(), mark.getAnchorDigit()));
        }
        if (starts.isEmpty()) return null;
        for (int value : results.trueAtoms)
            if (!DoodleContradictionBasis.currentCandidate(board, value / 10, value % 10)) return null;
        for (int value : results.falseAtoms)
            if (!DoodleContradictionBasis.currentCandidate(board, value / 10, value % 10)) return null;
        Set<Integer> truths = new TreeSet<Integer>(results.trueAtoms), falses = new TreeSet<Integer>(results.falseAtoms);
        // A singleton truth or all-false condition already asserts its matching
        // atoms. An OR condition never absorbs a separately derived member truth.
        if (kind == DoodleStroke.MARK_FALSE_CROSS) falses.removeAll(starts);
        else if (starts.size() == 1) truths.removeAll(starts);
        return new Plan(starts, kind, truths, falses);
    }
}
