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
import java.util.Set;

/** Complete-source fixtures and independent truth-table checks; no UI or solver certificate. */
public final class DoodleDerivedBasisProbe {
    private static final int CIRCLE = DoodleStroke.MARK_TRUE_CIRCLE;
    private static final int CROSS = DoodleStroke.MARK_FALSE_CROSS;
    private static final int[] SOURCE = {1, 23, 45, 67, 89};

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Sudoku2 blank() {
        Sudoku2 board = new Sudoku2();
        board.setSudoku(new String(new char[81]).replace('\0', '0')); return board;
    }
    private static UserChainNode node(int... atoms) { return UserChainNode.fromAtoms(atoms, null); }
    private static DoodleStroke mark(int atom, int kind, int group, boolean start) {
        DoodleStroke mark = new DoodleStroke();
        mark.setAnchorCell(atom / 10); mark.setAnchorDigit(atom % 10);
        mark.setCandidateMarkKind(kind); mark.setThoughtGroup(group); mark.setHypothesisStart(start);
        return mark;
    }
    private static void add(List<DoodleStroke> ink, int group, int kind, boolean start, int... atoms) {
        for (int atom : atoms) ink.add(mark(atom, kind, group, start));
    }
    private static List<DoodleStroke> source() {
        List<DoodleStroke> ink = new ArrayList<DoodleStroke>(); add(ink, 0, CIRCLE, true, SOURCE); return ink;
    }
    private static Set<String> reductionKeys(DoodleDerivedBasis.Analysis analysis) {
        Set<String> keys = new HashSet<String>();
        for (DoodleDerivedBasis.CircleReduction reduction : analysis.getCircleReductions()) {
            check(reduction.getResult().validShape(), "malformed result");
            check(keys.add(reduction.getResult().key()), "duplicate result");
            check(!reduction.getEvidence().isEmpty(), "missing complete evidence");
            for (DoodleDerivedBasis.Evidence evidence : reduction.getEvidence()) {
                check(evidence.getSource().validShape(), "malformed source");
                check(reduction.getResult().atoms().length < evidence.getSource().atoms().length,
                        "not a strict reduction");
                check((evidence.getBranchGroupMask() & 1 << evidence.getSourceGroup()) == 0,
                        "source used as its own branch");
                check(evidence.getProofGroupMask() == (evidence.getBranchGroupMask() | 1 << evidence.getSourceGroup()),
                        "incomplete proof group mask");
            }
        }
        return keys;
    }
    private static void expectReductions(Sudoku2 board, List<DoodleStroke> ink,
            String label, UserChainNode... expected) {
        Set<String> wanted = new HashSet<String>(); for (UserChainNode n : expected) wanted.add(n.key());
        Set<String> actual = reductionKeys(DoodleDerivedBasis.analyze(board, ink));
        check(actual.equals(wanted), label + ": expected " + wanted + ", got " + actual);
    }
    private static void reductions() {
        Sudoku2 board = blank(); List<DoodleStroke> ink = source();
        add(ink, 1, CROSS, true, 102); add(ink, 1, CROSS, false, 1, 23);
        expectReductions(board, ink, "5 to 4", node(45, 67, 89, 102));
        add(ink, 1, CROSS, false, 45);
        expectReductions(board, ink, "5 to 3", node(67, 89, 102));
        add(ink, 2, CROSS, true, 201); add(ink, 2, CROSS, false, 67, 89);
        expectReductions(board, ink, "two negative branches",
                node(67, 89, 102), node(1, 23, 45, 201), node(102, 201));
        DoodleDerivedBasis.Analysis analysis = DoodleDerivedBasis.analyze(board, ink);
        check(analysis.getWeakRelations().isEmpty(), "negative branches became a weak relation");
        check(analysis.getCircleReductions().get(0).getResult().key().equals(node(102, 201).key()),
                "smallest result not first");
        DoodleDerivedBasis.CircleReduction pair = analysis.getCircleReductions().get(0);
        check(pair.evidenceForGroup(1) != null && pair.evidenceForGroup(2) != null
                && pair.evidenceForGroup(0) == null && pair.evidenceForGroup(5) == null,
                "current branch evidence selection");
        ink = source(); add(ink, 1, CROSS, true, 102, 113); add(ink, 1, CROSS, false, 1, 23, 45, 67);
        expectReductions(board, ink, "complete all-false premise", node(89, 102, 113));
        ink = source(); add(ink, 1, CROSS, true, 67); add(ink, 1, CROSS, false, 1, 23, 45);
        expectReductions(board, ink, "representative already in remainder", node(67, 89));
        ink = source(); add(ink, 1, CROSS, true, 102); add(ink, 1, CROSS, false, 1);
        expectReductions(board, ink, "no candidate count reduction");
        ink = source(); add(ink, 1, CROSS, true, 102); add(ink, 1, CROSS, false, 11, 23, 45);
        expectReductions(board, ink, "same digit at another cell cannot replace a source member", node(1, 67, 89, 102));
    }
    private static void positiveConvergence() {
        Sudoku2 board = blank(); List<DoodleStroke> ink = source();
        add(ink, 1, CIRCLE, true, 1, 23, 45); add(ink, 1, CIRCLE, false, 102);
        add(ink, 2, CIRCLE, true, 67, 89); add(ink, 2, CIRCLE, false, 201);
        expectReductions(board, ink, "positive OR premises use contrapositive",
                node(67, 89, 102), node(1, 23, 45, 201), node(102, 201));
        check(DoodleDerivedBasis.analyze(board, ink).getWeakRelations().isEmpty(),
                "ordinary true result invented a weak relation");
        add(ink, 1, CIRCLE, false, 113);
        expectReductions(board, ink, "ordinary true representatives remain alternatives",
                node(67, 89, 102), node(67, 89, 113), node(1, 23, 45, 201),
                node(102, 201), node(113, 201));
        ink = source(); add(ink, 1, CIRCLE, true, 1, 23, 45, 98); add(ink, 1, CIRCLE, false, 102);
        expectReductions(board, ink, "entire positive premise must belong to source");
        ink = source(); add(ink, 0, CIRCLE, false, 102);
        expectReductions(board, ink, "source cannot certify its own case result");
    }
    private static void equivalentRepresentativeAssignments() {
        Sudoku2 board = blank(); List<DoodleStroke> ink = source();
        add(ink, 1, CIRCLE, true, 1, 23, 45); add(ink, 1, CIRCLE, false, 102, 201);
        add(ink, 2, CIRCLE, true, 67, 89); add(ink, 2, CIRCLE, false, 102, 201);
        expectReductions(board, ink, "equivalent assignments retain every complete result",
                node(102), node(201), node(102, 201), node(67, 89, 102), node(67, 89, 201),
                node(1, 23, 45, 102), node(1, 23, 45, 201));
        for (DoodleDerivedBasis.CircleReduction result : DoodleDerivedBasis.analyze(board, ink).getCircleReductions()) {
            if (!result.getResult().key().equals(node(102, 201).key())) continue;
            check(result.getEvidence().size() == 1 && result.evidenceForGroup(1) != null
                    && result.evidenceForGroup(2) != null && result.getEvidence().get(0).getBranchGroupMask() == 6,
                    "equivalent assignment deduplication lost complete branch evidence");
        }
    }
    private static List<DoodleStroke> weakInk() {
        List<DoodleStroke> ink = source();
        add(ink, 1, CIRCLE, true, 102, 113); add(ink, 1, CROSS, false, 1, 23, 45);
        add(ink, 2, CIRCLE, true, 201, 215); add(ink, 2, CROSS, false, 67, 89); return ink;
    }
    private static void weakRelations() {
        Sudoku2 board = blank(); List<DoodleStroke> ink = weakInk();
        DoodleDerivedBasis.Analysis analysis = DoodleDerivedBasis.analyze(board, ink);
        check(analysis.getCircleReductions().isEmpty(), "true exclusion branches invented an OR");
        check(analysis.getWeakRelations().size() == 1, "complete cover did not yield exactly one weak relation");
        check(analysis.weakTargets(node(102, 113)).get(0).key().equals(node(201, 215).key()), "wrong full weak target");
        check(analysis.weakTargets(node(201, 215)).get(0).key().equals(node(102, 113).key()), "weak symmetry lost");
        check(analysis.weakTargets(node(102)).isEmpty(), "partial group borrowed full weak qualification");
        check(analysis.weakTargets(node(102, 114)).isEmpty(), "wrong candidate borrowed weak qualification");
        check(analysis.weakTargets(null).isEmpty(), "null origin");
        ink.remove(ink.size() - 1);
        check(DoodleDerivedBasis.analyze(board, ink).getWeakRelations().isEmpty(), "missing E allowed weak relation");
        add(ink, 2, CROSS, false, 98);
        check(DoodleDerivedBasis.analyze(board, ink).getWeakRelations().isEmpty(), "same digit or wrong cell completed coverage");
    }
    private static void sourceGuards() {
        Sudoku2 board = blank(); List<DoodleStroke> ink = source();
        add(ink, 1, CROSS, true, 102); add(ink, 1, CROSS, false, 1, 23, 45);
        add(ink, 0, CROSS, true, 201);
        expectReductions(board, ink, "mixed source rejected without silently dropping cross");
        ink = source(); add(ink, 1, CROSS, true, 102); add(ink, 1, CROSS, false, 1, 23, 45);
        add(ink, 1, CIRCLE, true, 201);
        expectReductions(board, ink, "mixed branch rejected");
        ink = source(); add(ink, 1, CROSS, true, 102); add(ink, 1, CROSS, false, 1, 23, 45);
        board.delCandidate(0, 1); expectReductions(board, ink, "stale source must not shrink");
        board = blank(); board.delCandidate(10, 2); expectReductions(board, ink, "stale premise must not shrink");
        board = blank(); board.delCandidate(4, 5);
        // The stale source is invalid too, rather than silently keeping its four members.
        expectReductions(board, ink, "stale result candidate cannot complete a source");
        board = blank(); ink = new ArrayList<DoodleStroke>();
        add(ink, 0, CIRCLE, false, SOURCE); add(ink, 1, CROSS, true, 102);
        add(ink, 1, CROSS, false, 1, 23, 45);
        expectReductions(board, ink, "ordinary circles do not supply source premises");
        ink = source(); add(ink, 1, CROSS, false, 102, 1, 23, 45);
        expectReductions(board, ink, "ordinary crosses do not invent branch premise");
        DoodleStroke free = new DoodleStroke(); free.setThoughtGroup(1); ink.add(free); ink.add(null);
        expectReductions(board, ink, "free ink does not supply a premise");
        free.setHypothesisStart(true); expectReductions(board, ink, "free ink start invalidates branch");
    }
    private static void archivedEvidenceDoesNotAct() {
        Sudoku2 board = blank(); List<DoodleStroke> ink = source();
        add(ink, 1, CROSS, true, 102); add(ink, 1, CROSS, false, 1, 23, 45);
        ink.get(ink.size() - 1).setProofOnly(true);
        expectReductions(board, ink, "archived false conclusion is not coverage", node(45, 67, 89, 102));
        ink.get(SOURCE.length).setProofOnly(true);
        expectReductions(board, ink, "archived branch start is not a premise");
        ink = source(); add(ink, 1, CROSS, true, 102); add(ink, 1, CROSS, false, 1, 23, 45);
        for (int i = 0; i < SOURCE.length; i++) ink.get(i).setProofOnly(true);
        expectReductions(board, ink, "archived source cannot produce another result");
        ink = source(); add(ink, 1, CIRCLE, true, 1, 23, 45); add(ink, 1, CIRCLE, false, 102);
        ink.get(ink.size() - 1).setProofOnly(true);
        expectReductions(board, ink, "archived positive representative is not a conclusion");
        ink = weakInk(); ink.get(ink.size() - 1).setProofOnly(true);
        check(DoodleDerivedBasis.analyze(board, ink).getWeakRelations().isEmpty(),
                "archived proof completed a current weak relation");
    }
    private static void deduplicationAndNonMutation() {
        Sudoku2 board = blank(); List<DoodleStroke> ink = source(); add(ink, 3, CIRCLE, true, SOURCE);
        add(ink, 1, CROSS, true, 102); add(ink, 1, CROSS, false, 1, 23, 45);
        add(ink, 2, CROSS, true, 102); add(ink, 2, CROSS, false, 1, 23, 45);
        DoodleDerivedBasis.Analysis analysis = DoodleDerivedBasis.analyze(board, ink);
        check(analysis.getCircleReductions().size() == 1, "result not deduplicated across complete evidence");
        DoodleDerivedBasis.CircleReduction result = analysis.getCircleReductions().get(0);
        check(result.getEvidence().size() == 6, "complete alternative evidence lost");
        check(result.evidenceForGroup(1) != null && result.evidenceForGroup(2) != null,
                "deduplication lost current group");
        List<DoodleStroke> weak = weakInk(); add(weak, 3, CIRCLE, true, SOURCE);
        analysis = DoodleDerivedBasis.analyze(board, weak);
        check(analysis.getWeakRelations().size() == 1 && analysis.getWeakRelations().get(0).getEvidence().size() == 2,
                "weak deduplication lost complete source evidence");
        short[] before = new short[81]; for (int cell = 0; cell < 81; cell++) before[cell] = board.getCell(cell);
        List<String> marks = markKeys(ink); DoodleDerivedBasis.analyze(board, ink);
        for (int cell = 0; cell < 81; cell++) check(before[cell] == board.getCell(cell), "board mutated");
        check(marks.equals(markKeys(ink)), "source ink mutated");
        UserChainNode mutable = result.getResult(); mutable.setMemberCandidates(new int[]{1});
        check(result.getResult().key().equals(node(67, 89, 102).key()), "mutable result leaked");
        mutable = result.getEvidence().get(0).getSource(); mutable.setMemberCandidates(new int[]{1});
        check(result.getEvidence().get(0).getSource().atoms().length == SOURCE.length, "mutable source leaked");
        check(DoodleDerivedBasis.analyze(null, ink).getCircleReductions().isEmpty(), "null board");
        check(DoodleDerivedBasis.analyze(board, null).getWeakRelations().isEmpty(), "null ink");
        check(DoodleDerivedBasis.analyze(board, Collections.<DoodleStroke>emptyList()).getCircleReductions().isEmpty(), "empty ink");
    }
    private static List<String> markKeys(List<DoodleStroke> ink) {
        List<String> result = new ArrayList<String>();
        for (DoodleStroke mark : ink) result.add(mark.getAnchorCell() + ":" + mark.getAnchorDigit()
                + ":" + mark.getCandidateMarkKind() + ":" + mark.getThoughtGroup() + ":" + mark.isHypothesisStart());
        return result;
    }
    private static void fullCandidateCapacity() {
        Sudoku2 board = blank(); List<DoodleStroke> ink = new ArrayList<DoodleStroke>();
        for (int cell = 0; cell < 81; cell++) for (int digit = 1; digit <= 9; digit++) {
            int atom = cell * 10 + digit; add(ink, 0, CIRCLE, true, atom);
            if (atom != 1) add(ink, 1, CROSS, false, atom);
        }
        add(ink, 1, CROSS, true, 1); expectReductions(board, ink, "729 source atoms are not truncated", node(1));
        check(DoodleDerivedBasis.analyze(board, ink).getCircleReductions().get(0).getEvidence().get(0)
                .getSource().atoms().length == 729, "complete source capacity lost");
    }
    private static boolean any(int assignment, int[] atoms, int[] variables) {
        for (int atom : atoms) for (int i = 0; i < variables.length; i++)
            if (atom == variables[i] && (assignment & 1 << i) != 0) return true;
        return false;
    }
    /** Independent evaluation of the recorded implications, not helper internals. */
    private static void truthTables() {
        List<DoodleStroke> strongInk = source();
        add(strongInk, 1, CROSS, true, 102); add(strongInk, 1, CROSS, false, 1, 23, 45);
        add(strongInk, 2, CROSS, true, 201); add(strongInk, 2, CROSS, false, 67, 89);
        DoodleDerivedBasis.Analysis strong = DoodleDerivedBasis.analyze(blank(), strongInk);
        List<DoodleStroke> weakInk = source();
        add(weakInk, 1, CIRCLE, true, 102); add(weakInk, 1, CROSS, false, 1, 23, 45);
        add(weakInk, 2, CIRCLE, true, 201); add(weakInk, 2, CROSS, false, 67, 89);
        DoodleDerivedBasis.Analysis weak = DoodleDerivedBasis.analyze(blank(), weakInk);
        int[] variables = {1, 23, 45, 67, 89, 102, 201}; int strongModels = 0, weakModels = 0, bothFalseModels = 0;
        for (int assignment = 0; assignment < 1 << variables.length; assignment++) {
            boolean source = any(assignment, SOURCE, variables);
            boolean x = any(assignment, new int[]{102}, variables), y = any(assignment, new int[]{201}, variables);
            boolean abc = any(assignment, new int[]{1, 23, 45}, variables), de = any(assignment, new int[]{67, 89}, variables);
            // !X => all ABC false, !Y => all DE false.
            if (source && (x || !abc) && (y || !de)) {
                strongModels++; check(x || y, "strong derived OR counterexample");
                for (DoodleDerivedBasis.CircleReduction result : strong.getCircleReductions())
                    check(any(assignment, result.getResult().atoms(), variables), "helper OR has counterexample");
            }
            // X => all ABC false, Y => all DE false.
            if (source && (!x || !abc) && (!y || !de)) {
                weakModels++; check(!(x && y), "weak derived relation counterexample");
                for (DoodleDerivedBasis.WeakRelation result : weak.getWeakRelations())
                    check(!(any(assignment, result.getLeft().atoms(), variables)
                            && any(assignment, result.getRight().atoms(), variables)), "helper weak result has counterexample");
                if (!x && !y) bothFalseModels++;
            }
            // (OR ABC) => X and (OR DE) => Y are identical to the false-premise implications.
            check(((x || !abc) && (y || !de)) == ((!abc || x) && (!de || y)), "contrapositive differs");
        }
        check(strongModels > 0 && weakModels > 0 && bothFalseModels > 0,
                "vacuous truth table or weak relation accidentally excludes both false");
    }
    public static void main(String[] args) {
        reductions(); positiveConvergence(); equivalentRepresentativeAssignments(); weakRelations(); sourceGuards(); archivedEvidenceDoesNotAct();
        deduplicationAndNonMutation();
        fullCandidateCapacity(); truthTables();
        System.out.println("PASS: complete source OR reductions, positive convergence, weak-only coverage, full endpoints, stale/mixed/ordinary/archived guards, all evidence, 729 atoms and truth tables");
    }
}
