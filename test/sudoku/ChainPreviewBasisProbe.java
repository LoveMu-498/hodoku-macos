/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure current-source fixtures; no panel, solver enumeration or user session. */
public final class ChainPreviewBasisProbe {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Sudoku2 blank() {
        Sudoku2 board = new Sudoku2();
        board.setSudoku(new String(new char[81]).replace('\0', '0'));
        return board;
    }
    private static UserChainNode node(int... atoms) {
        return UserChainNode.fromAtoms(atoms, null);
    }
    private static DoodleStroke mark(int atom, int kind, int group, boolean start) {
        DoodleStroke mark = new DoodleStroke();
        mark.setAnchorCell(atom / 10); mark.setAnchorDigit(atom % 10);
        mark.setCandidateMarkKind(kind); mark.setThoughtGroup(group); mark.setHypothesisStart(start);
        return mark;
    }
    private static SudokuSet cells(int... indices) {
        SudokuSet set = new SudokuSet();
        for (int index : indices) set.add(index);
        return set;
    }
    private static void restrict(Sudoku2 board, int cell, String digits) {
        for (int digit = 1; digit <= 9; digit++) board.setCandidate(cell, digit,
                digits.indexOf((char) ('0' + digit)) >= 0);
    }
    private static Set<String> keys(List<UserChainNode> targets) {
        Set<String> result = new HashSet<String>();
        for (UserChainNode target : targets) {
            check(target.validShape(), "invalid target shape");
            check(result.add(target.key()), "duplicate complete target");
        }
        return result;
    }
    private static void expect(Sudoku2 board, UserChainNode origin, List<DoodleStroke> ink,
            List<SudokuSet> boxes, String label, UserChainNode... expected) {
        Set<String> wanted = new HashSet<String>();
        for (UserChainNode target : expected) wanted.add(target.key());
        Set<String> actual = keys(ChainPreviewBasis.strongTargets(board, origin, ink, boxes));
        check(actual.equals(wanted), label + ": expected " + wanted + ", got " + actual);
    }
    private static void falseStarts() {
        Sudoku2 board = blank();
        List<DoodleStroke> ink = new ArrayList<DoodleStroke>(Arrays.asList(
                mark(1, 2, 0, true), mark(11, 2, 0, true), mark(403, 1, 0, false),
                mark(505, 1, 0, false), mark(606, 2, 0, false), mark(707, 1, 1, false)));
        expect(board, node(11, 1), ink, null, "exact all-false origin", node(403), node(505));
        expect(board, node(1), ink, null, "missing origin member");
        expect(board, node(1, 11, 21), ink, null, "extra origin member");
        expect(board, node(1, 21), ink, null, "same digit wrong cell");
        expect(board, node(2, 12), ink, null, "wrong digit");
        ink.add(mark(21, 1, 0, true));
        expect(board, node(1, 11), ink, null, "mixed old starts cannot be filtered");
        ink.remove(ink.size() - 1);
        board.delCandidate(1, 1);
        expect(board, node(1), ink, null, "missing start candidate cannot shrink premise");
        List<DoodleStroke> independent = Arrays.asList(mark(1, 2, 2, true), mark(708, 1, 2, false));
        ink.addAll(independent);
        expect(board, node(1), ink, null, "invalid group does not disable another", node(708));
        board = blank();
        expect(board, node(1), Arrays.asList(mark(1, 2, 0, false), mark(403, 1, 0, false)),
                null, "ordinary marks do not invent starts");
        DoodleStroke free = new DoodleStroke(); free.setThoughtGroup(0);
        expect(board, node(1), Arrays.asList(mark(1, 2, 0, true), free), null,
                "freehand is not a candidate conclusion");
        expect(board, node(1), Arrays.asList(mark(1, 2, 0, true), mark(11, 1, 0, false)),
                null, "single start default metadata is valid", node(11));
        board.delCandidate(1, 1);
        expect(board, node(1), Arrays.asList(mark(1, 2, 0, true), mark(11, 1, 0, false)),
                null, "stale conclusion excluded");
    }
    private static void circleStarts() {
        Sudoku2 board = blank();
        List<DoodleStroke> ink = new ArrayList<DoodleStroke>(Arrays.asList(
                mark(1, 1, 0, true), mark(11, 1, 0, true), mark(403, 1, 0, true),
                mark(505, 1, 0, true), mark(606, 1, 0, false)));
        expect(board, node(1, 11), ink, null, "OR pair split", node(403, 505));
        expect(board, node(1), ink, null, "OR singleton split", node(11, 403, 505));
        expect(board, node(1, 403), ink, null, "mixed origin split", node(11, 505));
        expect(board, node(1, 11, 403, 505), ink, null, "empty complement excluded");
        expect(board, node(1, 606), ink, null, "ordinary circle is not in start set");
        expect(board, node(21), ink, null, "non-subset origin");
        ink.add(mark(707, 2, 0, true));
        expect(board, node(1), ink, null, "mixed start set not silently narrowed");
        ink.remove(ink.size() - 1);
        board.delCandidate(50, 5);
        expect(board, node(1), ink, null, "missing circle start invalidates entire set");
    }
    private static void alsTargets() {
        Sudoku2 board = blank(); restrict(board, 0, "12"); restrict(board, 1, "14");
        List<SudokuSet> boxes = Collections.singletonList(cells(0, 1));
        expect(board, node(14), null, boxes, "ALS digit groups", node(1, 11), node(2));
        expect(board, node(1, 11), null, boxes, "complete repeated digit origin", node(2), node(14));
        expect(board, node(1), null, boxes, "partial repeated digit origin excluded");
        expect(board, node(1, 11, 21), null, boxes, "extra digit position excluded");
        expect(board, node(1, 21), null, boxes, "wrong position excluded");
        expect(board, node(1, 14), null, boxes, "mixed digit origin is not ALS digit group");
        expect(board, node(14), null, Arrays.asList(cells(0), cells(1)),
                "different colors are not merged", node(11));
        board.setCandidate(0, 3, true);
        expect(board, node(14), null, boxes, "real extra digit invalidates ALS");
        for (int[] pair : new int[][] {{0, 9}, {0, 10}}) {
            board = blank(); restrict(board, pair[0], "12"); restrict(board, pair[1], "14");
            expect(board, node(pair[1] * 10 + 4), null, Collections.singletonList(cells(pair)),
                    "column or box ALS", node(1, pair[1] * 10 + 1), node(2));
        }
        board = blank(); restrict(board, 0, "12"); restrict(board, 40, "14");
        expect(board, node(404), null, Collections.singletonList(cells(0, 40)), "not a shared unit");
        board = blank(); restrict(board, 0, "12"); restrict(board, 1, "14"); board.setCell(40, 9);
        expect(board, node(14), null, Collections.singletonList(cells(0, 1, 40)),
                "filled cells excluded before unit/count checks", node(1, 11), node(2));
        board = blank(); restrict(board, 0, "123"); restrict(board, 1, "124"); restrict(board, 2, "134");
        expect(board, node(2, 12), null, Collections.singletonList(cells(0, 1, 2)),
                "different same-digit subgroups gain no ALS edge", node(1, 11, 21), node(3, 23), node(14, 24));
    }
    private static void integrityAndDeduplication() {
        Sudoku2 board = blank(); restrict(board, 0, "12"); restrict(board, 1, "14");
        List<DoodleStroke> ink = Arrays.asList(mark(14, 2, 0, true), mark(2, 1, 0, false),
                mark(14, 1, 1, true), mark(2, 1, 1, true), mark(14, 2, 2, true), mark(2, 1, 2, false));
        expect(board, node(14), ink, Arrays.asList(cells(0, 1), cells(0, 1), null),
                "same target across all bases deduplicated", node(2), node(1, 11));
        List<DoodleStroke> overlap = Arrays.asList(mark(14, 1, 0, true), mark(1, 1, 0, true),
                mark(11, 1, 0, true), mark(14, 1, 1, true), mark(1, 1, 1, true), mark(2, 1, 1, true));
        expect(board, node(14), overlap, null, "overlapping full targets remain distinct", node(1, 11), node(1, 2));
        short[] before = new short[81];
        for (int cell = 0; cell < 81; cell++) before[cell] = board.getCell(cell);
        String starts = overlap.get(0).getThoughtGroup() + ":" + overlap.get(0).isHypothesisStart();
        SudokuSet box = cells(0, 1), boxCopy = box.clone();
        ChainPreviewBasis.strongTargets(board, node(14), overlap, Collections.singletonList(box));
        for (int cell = 0; cell < 81; cell++) check(before[cell] == board.getCell(cell), "board mutated");
        check(box.equals(boxCopy), "source box mutated");
        check(starts.equals(overlap.get(0).getThoughtGroup() + ":" + overlap.get(0).isHypothesisStart()), "source ink mutated");
        check(ChainPreviewBasis.strongTargets(null, node(1), ink, null).isEmpty(), "null board");
        check(ChainPreviewBasis.strongTargets(board, null, ink, null).isEmpty(), "null origin");
        check(ChainPreviewBasis.strongTargets(board, new UserChainNode(), ink, null).isEmpty(), "malformed origin");
        expect(board, node(14), Arrays.asList((DoodleStroke) null), Collections.<SudokuSet>emptyList(), "empty sources");
        expect(board, node(14), null, null, "null sources");
    }
    public static void main(String[] args) {
        falseStarts(); circleStarts(); alsTargets(); integrityAndDeduplication();
        System.out.println("PASS: current doodle premises, exact groups, OR complements, same-color ALS, complete digit endpoints, stale/mixed rejection, deduplication and non-mutation");
    }
}
