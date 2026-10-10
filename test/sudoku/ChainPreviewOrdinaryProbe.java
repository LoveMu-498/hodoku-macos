/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Compares singleton batching with the unchanged per-pair coverage rule. */
public final class ChainPreviewOrdinaryProbe {
    private static int cases;
    private static int compared;
    private static volatile int timingSink;

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
    private static UserChainNode unitNode(int[] unit, int digit, int members) {
        int[] atoms = new int[members];
        for (int i = 0; i < members; i++) atoms[i] = unit[i] * 10 + digit;
        return node(atoms);
    }
    private static void restrict(Sudoku2 board, int cell, String digits) {
        for (int digit = 1; digit <= 9; digit++) board.setCandidate(cell, digit,
                digits.indexOf((char) ('0' + digit)) >= 0);
    }
    private static List<Integer> candidates(Sudoku2 board) {
        List<Integer> atoms = new ArrayList<Integer>();
        for (int cell = 0; cell < 81; cell++) if (board.getValue(cell) == 0)
            for (int digit : board.getAllCandidates(cell)) atoms.add(cell * 10 + digit);
        return atoms;
    }
    private static Set<Integer> reference(Sudoku2 board, UserChainNode origin) {
        Set<Integer> result = new HashSet<Integer>();
        // Preview eligibility admits actual candidates only, including all members
        // of the origin here: used-node restrictions belong to the panel.
        for (int atom : candidates(board)) if (UserChainValidator.ordinaryStrong(board, origin,
                new UserChainNode(atom / 10, atom % 10))) result.add(atom);
        return result;
    }
    private static Set<Integer> compare(Sudoku2 board, UserChainNode origin, String label) {
        check(origin.validShape(), label + ": malformed origin");
        for (int atom : origin.atoms()) check(board.getValue(atom / 10) == 0
                && board.isCandidate(atom / 10, atom % 10), label + ": absent origin member");
        short[] masks = new short[81];
        int[] values = new int[81];
        for (int cell = 0; cell < 81; cell++) { masks[cell] = board.getCell(cell); values[cell] = board.getValue(cell); }
        String key = origin.key();
        Set<Integer> actual = UserChainValidator.ordinaryStrongSingleTargets(board, origin);
        Set<Integer> expected = reference(board, origin);
        Set<Integer> missing = new HashSet<Integer>(expected); missing.removeAll(actual);
        Set<Integer> extra = new HashSet<Integer>(actual); extra.removeAll(expected);
        check(missing.isEmpty() && extra.isEmpty(), label + ": missing " + missing + ", extra " + extra);
        check(new HashSet<Integer>(candidates(board)).containsAll(actual), label + ": non-candidate target");
        for (int cell = 0; cell < 81; cell++) check(masks[cell] == board.getCell(cell)
                && values[cell] == board.getValue(cell), label + ": board changed");
        check(origin.key().equals(key), label + ": origin changed");
        cases++; compared += candidates(board).size();
        return actual;
    }
    private static void coverageFixtures() {
        Sudoku2 board = blank();
        check(compare(board, node(1, 112, 405), "blank mixed group").isEmpty(), "blank mixed witness");

        restrict(board, 0, "123");
        check(compare(board, node(1, 2), "cell missing one").equals(Collections.singleton(3)), "cell missing-one witness");
        check(compare(board, node(1, 2, 3), "complete cell").size() == candidates(board).size(), "complete cell witness");
        check(compare(board, node(1), "cell missing two").isEmpty(), "cell missing-two witness");

        board = blank();
        check(compare(board, unitNode(Sudoku2.ROWS[0], 1, 8), "row missing one").equals(Collections.singleton(81)), "row missing-one witness");
        check(compare(board, unitNode(Sudoku2.ROWS[0], 1, 9), "complete row digit").size() == 729, "complete row witness");
        check(compare(board, unitNode(Sudoku2.ROWS[0], 1, 7), "row missing two").isEmpty(), "row missing-two witness");
        check(compare(board, unitNode(Sudoku2.COLS[0], 2, 8), "column missing one").equals(Collections.singleton(722)), "column missing-one witness");
        check(compare(board, unitNode(Sudoku2.BLOCKS[0], 3, 8), "box missing one").equals(Collections.singleton(203)), "box missing-one witness");

        board = blank(); board.setCell(0, 1);
        // Retain inconsistent user candidates deliberately, to test the existing
        // rule that a placed digit forbids using that house as a coverage proof.
        board.setCandidate(1, 1, true); board.setCandidate(2, 1, true); board.setCandidate(3, 1, true);
        check(!compare(board, node(11, 31), "placed digit in covered house").contains(21), "placed digit supplied false house proof");

        board = blank(); restrict(board, 0, "123"); board.setCandidate(0, 4, true);
        check(compare(board, node(1, 2), "candidate added").isEmpty(), "added candidate ignored");
        board.delCandidate(0, 4);
        check(compare(board, node(1, 2), "candidate removed").equals(Collections.singleton(3)), "removed candidate remained cached");
    }
    private static void seededFixtures() {
        Random random = new Random(0x436861696eL);
        int[] sizes = {2, 3, 5, 8, 12, 20, 45, 120};
        for (int sample = 0; sample < sizes.length; sample++) {
            Sudoku2 board = blank();
            for (int cell = 0; cell < 81; cell++) {
                int keep = (1 << (1 + random.nextInt(9))) | (1 << (1 + random.nextInt(9)));
                for (int digit = 1; digit <= 9; digit++) if (random.nextBoolean()) keep |= 1 << digit;
                for (int digit = 1; digit <= 9; digit++) board.setCandidate(cell, digit, (keep & (1 << digit)) != 0);
            }
            List<Integer> present = candidates(board); Collections.shuffle(present, random);
            int[] atoms = new int[sizes[sample]];
            for (int i = 0; i < atoms.length; i++) atoms[i] = present.get(i);
            compare(board, node(atoms), "fixed-seed candidate group " + sample + " size " + atoms.length);
        }
    }
    private static void warmTiming() {
        Sudoku2 board = blank(); UserChainNode origin = node(1, 11);
        for (int i = 0; i < 300; i++) timingSink += UserChainValidator.ordinaryStrongSingleTargets(board, origin).size();
        for (int i = 0; i < 3; i++) timingSink += reference(board, origin).size();
        int batchRuns = 1000, pairRuns = 6;
        long start = System.nanoTime();
        for (int i = 0; i < batchRuns; i++) timingSink += UserChainValidator.ordinaryStrongSingleTargets(board, origin).size();
        long batch = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < pairRuns; i++) timingSink += reference(board, origin).size();
        long pairs = System.nanoTime() - start;
        System.out.printf(java.util.Locale.ROOT,
                "MEASURED: warm batch %.3f ms/query (%d runs); original per-target coverage %.3f ms/query (%d runs); sink=%d%n",
                batch / 1e6 / batchRuns, batchRuns, pairs / 1e6 / pairRuns, pairRuns, timingSink);
    }
    public static void main(String[] args) {
        coverageFixtures(); seededFixtures();
        System.out.println("PASS: " + cases + " fixtures / " + compared
                + " current candidate targets match original ordinaryStrong; overlap, cell/house coverage, placed digits and fresh candidate state preserved");
        warmTiming();
    }
}
