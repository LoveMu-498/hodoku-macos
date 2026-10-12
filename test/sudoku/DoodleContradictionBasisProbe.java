/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Necessary-constraint checks against real Sudoku2 candidates, including incomplete pencil notes. */
public final class DoodleContradictionBasisProbe {
    private static int checks;
    static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    static Sudoku2 board() { Sudoku2 result = new Sudoku2(); result.setSudoku(new String(new char[81]).replace('\0', '0')); return result; }
    static DoodleStroke mark(int cell, int digit, int kind, boolean start, int group) {
        DoodleStroke result = new DoodleStroke(Color.MAGENTA, .08f);
        result.setAnchorCell(cell); result.setAnchorDigit(digit); result.setCandidateMarkKind(kind);
        result.setThoughtGroup(group); result.setHypothesisStart(start); return result;
    }
    static List<DoodleStroke> origin(int kind) {
        return new ArrayList<DoodleStroke>(Arrays.asList(mark(80, 5, kind, true, 0)));
    }
    static void candidates(Sudoku2 board, int cell, int... keep) {
        for (int digit = 1; digit <= 9; digit++) {
            boolean retained = false; for (int value : keep) retained |= value == digit;
            if (!retained) board.delCandidate(cell, digit);
        }
    }
    static void falseAt(List<DoodleStroke> marks, int cell, int... digits) {
        for (int digit : digits) marks.add(mark(cell, digit, 2, false, 0));
    }
    static DoodleContradictionBasis.Analysis analyze(Sudoku2 board, List<DoodleStroke> marks) {
        return DoodleContradictionBasis.analyze(board, marks, 0);
    }
    public static void main(String[] args) {
        Sudoku2 board = board(); candidates(board, 32, 1, 7, 9);
        List<DoodleStroke> marks = origin(1); falseAt(marks, 32, 1, 7);
        DoodleContradictionBasis.Analysis result = analyze(board, marks);
        check(!result.hasConflict(), "missing handwritten 9 became an implicit false");
        falseAt(marks, 32, 9); result = analyze(board, marks);
        check(result.hasRefutation() && result.conclusionKind() == 2 && !result.isOrConclusion(), "empty cell did not refute a true start");
        check(result.crossWitnesses.containsAll(Arrays.asList(321, 327, 329)) && result.crossWitnesses.size() == 3,
                "empty-cell witness did not consist of all three explicit crosses");
        check(result.circleWitnesses.isEmpty(), "empty-cell refutation painted its unrelated origin red");

        marks = origin(2); marks.add(mark(80, 5, 1, false, 0)); result = analyze(board(), marks);
        check(result.hasRefutation() && result.conclusionKind() == 1 && result.circleWitnesses.contains(805)
                && result.crossWitnesses.contains(805), "same atom true/false did not refute the false start");
        marks = origin(2); marks.add(mark(20, 1, 1, false, 0)); marks.add(mark(20, 2, 1, false, 0));
        check(analyze(board(), marks).circleWitnesses.containsAll(Arrays.asList(201, 202)), "same cell double truth missed");
        for (int[] cells : new int[][]{{0, 8}, {0, 72}, {0, 10}}) {
            marks = origin(2); marks.add(mark(cells[0], 1, 1, false, 0)); marks.add(mark(cells[1], 1, 1, false, 0));
            check(analyze(board(), marks).circleWitnesses.size() == 2, "row/column/box double truth missed");
        }
        for (int[] cells : new int[][]{{0,1,2,3,4,5,6,7,8}, {0,9,18,27,36,45,54,63,72}, {30,31,32,39,40,41,48,49,50}}) {
            marks = origin(1); for (int cell : cells) falseAt(marks, cell, 3);
            result = analyze(board(), marks);
            check(result.hasRefutation() && result.crossWitnesses.size() == 9, "row/column/box digit no-position missed");
        }
        board = board(); board.setCell(0, 3); marks = origin(1);
        for (int cell = 1; cell < 9; cell++) falseAt(marks, cell, 3);
        check(!analyze(board, marks).hasConflict(), "satisfied row digit was treated as having no position");
        board = board(); candidates(board, 32); marks = origin(1);
        check(!analyze(board, marks).hasConflict(), "pre-existing empty cell refuted unrelated start");
        board = board(); for (int cell = 0; cell < 9; cell++) board.delCandidate(cell, 3);
        check(!analyze(board, marks).hasConflict(), "pre-existing no-position row refuted unrelated start");

        board = board(); marks = origin(1); marks.add(mark(79, 5, 1, true, 0));
        check(!analyze(board, marks).hasConflict(), "OR starts were expanded as simultaneous true peers");
        falseAt(marks, 80, 5); check(!analyze(board, marks).hasConflict(), "partial OR negation refuted the complete OR");
        falseAt(marks, 79, 5); result = analyze(board, marks);
        check(result.hasRefutation() && result.starts.size() == 2 && result.conclusionKind() == 2
                && result.circleWitnesses.size() == 2 && result.crossWitnesses.size() == 2, "whole OR negation missed");
        board = board(); candidates(board, 32, 1, 7, 9);
        marks = origin(2); marks.add(mark(79, 4, 2, true, 0)); falseAt(marks, 32, 1, 7, 9);
        result = analyze(board, marks);
        check(result.isOrConclusion() && result.starts.size() == 2, "refuted all-false group was not a complete OR conclusion");

        marks = new ArrayList<DoodleStroke>(); falseAt(marks, 32, 1, 7, 9);
        result = analyze(board, marks); check(result.hasConflict() && !result.hasRefutation(), "no-start record got an invented origin");
        marks = origin(1); marks.add(mark(80, 5, 2, false, 1));
        check(!analyze(board(), marks).hasConflict(), "another color contaminated the branch");
        DoodleStroke hidden = mark(80, 5, 2, false, 0); hidden.setProofOnly(true); marks.add(hidden);
        DoodleStroke automatic = mark(80, 5, 2, false, 0); automatic.setRefutationResult(true); marks.add(automatic);
        check(!analyze(board(), marks).hasConflict(), "readonly or automatic inverse self-triggered a contradiction");
        marks = origin(1); marks.add(mark(20, 1, 1, false, 0)); marks.add(mark(20, 1, 1, false, 0));
        check(!analyze(board(), marks).hasConflict(), "duplicate same-digit circles became double truth");
        marks = origin(1); marks.add(mark(20, 2, 2, true, 0)); falseAt(marks, 32, 1, 7, 9);
        check(!analyze(board, marks).hasRefutation(), "mixed legacy starts generated a guessed inverse");
        System.out.println("PASS DoodleContradictionBasisProbe: " + checks + " real-board checks");
    }
}
