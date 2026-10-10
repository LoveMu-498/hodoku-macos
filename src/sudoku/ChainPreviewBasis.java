/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Current annotation premises that supply independent relation targets for a
 * complete origin. Ordinary relations, filters and path eligibility belong to the caller.
 */
final class ChainPreviewBasis {
    private ChainPreviewBasis() { }

    static List<UserChainNode> strongTargets(Sudoku2 board, UserChainNode origin,
            Iterable<DoodleStroke> doodles, Collection<SudokuSet> boxGroups) {
        Map<String, UserChainNode> targets = new LinkedHashMap<String, UserChainNode>();
        if (board == null || origin == null || !present(board, origin)) {
            return new ArrayList<UserChainNode>();
        }
        if (doodles != null) addDoodleTargets(board, origin, doodles, targets);
        if (boxGroups != null && origin.sameDigit() != 0) {
            for (SudokuSet cells : boxGroups) addAlsTargets(board, origin, cells, targets);
        }
        return new ArrayList<UserChainNode>(targets.values());
    }

    private static final class DoodleGroup {
        final Set<Integer> starts = new TreeSet<Integer>();
        final Set<Integer> trueConclusions = new TreeSet<Integer>();
        final Set<Integer> falseConclusions = new TreeSet<Integer>();
        int startKind;
        boolean valid = true;
    }

    private static void addDoodleTargets(Sudoku2 board, UserChainNode origin,
            Iterable<DoodleStroke> doodles, Map<String, UserChainNode> targets) {
        DoodleGroup[] groups = doodleGroups(board, doodles);
        int[] source = origin.atoms();
        for (DoodleGroup group : groups) {
            if (group == null || !group.valid || group.starts.isEmpty()) continue;
            if (group.startKind == DoodleStroke.MARK_FALSE_CROSS) {
                if (!origin.key().equals(node(group.starts).key())) continue;
                for (int atom : group.trueConclusions) add(targets, new UserChainNode(atom / 10, atom % 10));
            } else if (group.startKind == DoodleStroke.MARK_TRUE_CIRCLE && source.length < group.starts.size()) {
                Set<Integer> remainder = new TreeSet<Integer>(group.starts);
                boolean subset = true;
                for (int atom : source) if (!remainder.remove(atom)) { subset = false; break; }
                if (subset) add(targets, node(remainder));
            }
        }
    }

    /** Trusted whole-start OR truth implies a false candidate: A AND B is impossible. */
    static List<UserChainNode> weakTargets(Sudoku2 board, UserChainNode origin, Iterable<DoodleStroke> doodles) {
        Map<String, UserChainNode> targets = new LinkedHashMap<String, UserChainNode>();
        if (board == null || origin == null || !present(board, origin) || doodles == null)
            return new ArrayList<UserChainNode>();
        for (DoodleGroup group : doodleGroups(board, doodles)) {
            if (group == null || !group.valid || group.startKind != DoodleStroke.MARK_TRUE_CIRCLE
                    || group.starts.isEmpty() || !origin.key().equals(node(group.starts).key())) continue;
            for (int atom : group.falseConclusions) add(targets, new UserChainNode(atom / 10, atom % 10));
        }
        return new ArrayList<UserChainNode>(targets.values());
    }

    private static DoodleGroup[] doodleGroups(Sudoku2 board, Iterable<DoodleStroke> doodles) {
        DoodleGroup[] groups = new DoodleGroup[6];
        for (DoodleStroke mark : doodles) {
            if (mark == null || mark.isProofOnly() || mark.isRefutationResult() || mark.getThoughtGroup() < 0 || mark.getThoughtGroup() >= groups.length) continue;
            int group = mark.getThoughtGroup();
            if (groups[group] == null) groups[group] = new DoodleGroup();
            DoodleGroup entry = groups[group];
            boolean current = mark.isStandardCandidateMark()
                    && board.getValue(mark.getAnchorCell()) == 0
                    && board.isCandidate(mark.getAnchorCell(), mark.getAnchorDigit());
            if (mark.isHypothesisStart()) {
                if (!current) { entry.valid = false; continue; }
                int kind = mark.getCandidateMarkKind();
                if (entry.startKind != 0 && entry.startKind != kind) entry.valid = false;
                entry.startKind = kind;
                entry.starts.add(mark.getAnchorCell() * 10 + mark.getAnchorDigit());
            } else if (current && mark.getCandidateMarkKind() == DoodleStroke.MARK_TRUE_CIRCLE) {
                entry.trueConclusions.add(mark.getAnchorCell() * 10 + mark.getAnchorDigit());
            } else if (current && mark.getCandidateMarkKind() == DoodleStroke.MARK_FALSE_CROSS) {
                entry.falseConclusions.add(mark.getAnchorCell() * 10 + mark.getAnchorDigit());
            }
        }
        return groups;
    }

    private static void addAlsTargets(Sudoku2 board, UserChainNode origin, SudokuSet boxed,
            Map<String, UserChainNode> targets) {
        if (boxed == null || boxed.isEmpty()) return;
        List<Integer> cells = new ArrayList<Integer>();
        for (int i = 0; i < boxed.size(); i++) {
            int cell = boxed.get(i);
            if (cell < 0 || cell >= Sudoku2.LENGTH) return;
            if (board.getValue(cell) == 0) cells.add(cell);
        }
        if (cells.isEmpty()) return;
        int first = cells.get(0);
        boolean row = true, col = true, block = true;
        for (int cell : cells) {
            row &= Sudoku2.getRow(cell) == Sudoku2.getRow(first);
            col &= Sudoku2.getCol(cell) == Sudoku2.getCol(first);
            block &= Sudoku2.getBlock(cell) == Sudoku2.getBlock(first);
        }
        if (!row && !col && !block) return;
        Map<Integer, Set<Integer>> digits = new LinkedHashMap<Integer, Set<Integer>>();
        for (int cell : cells) {
            int[] candidates = board.getAllCandidates(cell);
            if (candidates.length == 0) return;
            for (int digit : candidates) {
                Set<Integer> positions = digits.get(digit);
                if (positions == null) {
                    positions = new TreeSet<Integer>();
                    digits.put(digit, positions);
                }
                positions.add(cell * 10 + digit);
            }
        }
        if (digits.size() != cells.size() + 1) return;
        Set<Integer> source = digits.get(origin.sameDigit());
        if (source == null || !origin.key().equals(node(source).key())) return;
        for (Map.Entry<Integer, Set<Integer>> digit : digits.entrySet()) {
            if (digit.getKey() != origin.sameDigit()) add(targets, node(digit.getValue()));
        }
    }

    private static boolean present(Sudoku2 board, UserChainNode node) {
        if (!node.validShape()) return false;
        for (int atom : node.atoms()) {
            if (board.getValue(atom / 10) != 0 || !board.isCandidate(atom / 10, atom % 10)) return false;
        }
        return true;
    }

    private static UserChainNode node(Set<Integer> atoms) {
        int[] members = new int[atoms.size()];
        int i = 0;
        for (int atom : atoms) members[i++] = atom;
        return UserChainNode.fromAtoms(members, null);
    }

    private static void add(Map<String, UserChainNode> targets, UserChainNode target) {
        targets.put(target.key(), target);
    }
}
