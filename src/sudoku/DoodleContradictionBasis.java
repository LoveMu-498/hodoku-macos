/*
 * Copyright (C) 2026 HoDoKu contributors
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Necessary constraints and validated uniqueness on one trusted doodle branch. No solver search. */
final class DoodleContradictionBasis {
    enum ConflictKind {
        CELL_NO_CANDIDATE, HOUSE_NO_DIGIT, ATOM_BOTH_TRUE_FALSE,
        CELL_MULTIPLE_TRUE, HOUSE_DUPLICATE_TRUE, EXISTING_VALUE_CONFLICT,
        OR_PREMISE_ALL_FALSE, DEADLY_RECTANGLE
    }
    /** Exact explicitly authored endpoint; this is not an invented chain edge. */
    static final class Witness {
        final ConflictKind kind;
        final int location, digit;
        private final Set<Integer> atoms;
        Witness(ConflictKind kind, int location, int digit, Set<Integer> atoms) {
            this.kind=kind;this.location=location;this.digit=digit;
            this.atoms=Collections.unmodifiableSet(new TreeSet<Integer>(atoms));
        }
        Set<Integer> atoms(){return atoms;}
    }
    static final class Analysis {
        final int group;
        final Set<Integer> circleWitnesses = new HashSet<Integer>();
        final Set<Integer> crossWitnesses = new HashSet<Integer>();
        final Set<Integer> starts = new TreeSet<Integer>();
        private int startKind;
        private final Map<String,Witness> endpoints=new TreeMap<String,Witness>();
        int startKind(){return startKind;}
        List<Witness> witnesses(){return Collections.unmodifiableList(new ArrayList<Witness>(endpoints.values()));}
        void record(ConflictKind kind,int location,int digit,Set<Integer> atoms){
            String key=kind.ordinal()+":"+location+":"+digit+(kind==ConflictKind.DEADLY_RECTANGLE?":"+new TreeSet<Integer>(atoms):"");Witness old=endpoints.get(key);
            Set<Integer> joined=new TreeSet<Integer>(atoms);if(old!=null)joined.addAll(old.atoms());
            endpoints.put(key,new Witness(kind,location,digit,joined));
        }
        void record(ConflictKind kind,int location,int digit,int... atoms){
            Set<Integer> values=new TreeSet<Integer>();for(int atom:atoms)values.add(atom);record(kind,location,digit,values);
        }
        private boolean validStart = true;
        private boolean uniquenessConflict;

        Analysis(int group) { this.group = group; }
        boolean hasConflict() { return !circleWitnesses.isEmpty() || !crossWitnesses.isEmpty(); }
        boolean hasRefutation() { return hasConflict() && validStart && !starts.isEmpty(); }
        boolean hasUniquenessConflict() { return uniquenessConflict; }
        boolean isOrConclusion() { return hasRefutation() && startKind == DoodleStroke.MARK_FALSE_CROSS && starts.size() > 1; }
        int conclusionKind() { return startKind == DoodleStroke.MARK_TRUE_CIRCLE ? DoodleStroke.MARK_FALSE_CROSS : DoodleStroke.MARK_TRUE_CIRCLE; }
        boolean witnesses(DoodleStroke mark) {
            if (mark.getThoughtGroup() != group || !mark.isStandardCandidateMark()) return false;
            return (mark.getCandidateMarkKind() == DoodleStroke.MARK_TRUE_CIRCLE ? circleWitnesses : crossWitnesses).contains(atom(mark));
        }
        boolean protects(int cell, int digit) {
            int atom = cell * 10 + digit;
            return circleWitnesses.contains(atom) || crossWitnesses.contains(atom)
                    || hasRefutation() && starts.contains(atom);
        }
        Set<Integer> conflictAtoms() {
            Set<Integer> result = new HashSet<Integer>(circleWitnesses);
            result.addAll(crossWitnesses); return result;
        }
    }

    static Analysis analyze(Sudoku2 board, Iterable<DoodleStroke> marks, int group) {
        return analyze(board, marks, group, board != null && board.getStatus() == SudokuStatus.VALID);
    }

    static Analysis analyze(Sudoku2 board, Iterable<DoodleStroke> marks, int group, boolean unique) {
        Analysis result = new Analysis(group);
        if (board == null || marks == null || group < 0 || group > 5) return result;
        Set<Integer> circles = new TreeSet<Integer>(), crosses = new TreeSet<Integer>();
        List<DoodleStroke> currentMarks = new ArrayList<DoodleStroke>();
        for (DoodleStroke mark : marks) {
            if (mark == null || mark.isProofOnly() || mark.isRefutationResult()
                    || mark.getThoughtGroup() != group || !mark.isStandardCandidateMark()) continue;
            boolean current = currentCandidate(board, mark.getAnchorCell(), mark.getAnchorDigit());
            if (mark.isHypothesisStart()) {
                int kind = mark.getCandidateMarkKind();
                if (!current || result.startKind != 0 && result.startKind != kind) result.validStart = false;
                result.startKind = kind;
                result.starts.add(atom(mark));
            }
            if (!current) continue;
            currentMarks.add(mark);
        }
        boolean orStart = result.validStart && result.startKind == DoodleStroke.MARK_TRUE_CIRCLE
                && result.starts.size() > 1;
        for (DoodleStroke mark : currentMarks) {
            if (mark.getCandidateMarkKind() == DoodleStroke.MARK_FALSE_CROSS) crosses.add(atom(mark));
            else if (mark.getHypothesisGroupKind() != 1 && !(orStart && mark.isHypothesisStart()))
                circles.add(atom(mark));
        }
        // Exclude the OR premise records, not their atoms: an independent ordinary
        // circle on the same candidate still asserts that candidate individually.
        if (circles.isEmpty() && crosses.isEmpty()) return result;
        for (int atom : circles) if (crosses.contains(atom)) {
            result.circleWitnesses.add(atom); result.crossWitnesses.add(atom);
            result.record(ConflictKind.ATOM_BOTH_TRUE_FALSE,atom/10,atom%10,atom);
        }
        for (int first : circles) {
            for (int second : circles) if (second > first
                    && (first / 10 == second / 10 || first % 10 == second % 10 && peers(first / 10, second / 10))) {
                result.circleWitnesses.add(first); result.circleWitnesses.add(second);
                if(first/10==second/10)result.record(ConflictKind.CELL_MULTIPLE_TRUE,first/10,0,first,second);
                else recordSharedUnits(result,ConflictKind.HOUSE_DUPLICATE_TRUE,first,second);
            }
            for (int cell = 0; cell < 81; cell++) if (board.getValue(cell) == first % 10 && peers(cell, first / 10)) {
                result.circleWitnesses.add(first);
                result.record(ConflictKind.EXISTING_VALUE_CONFLICT,cell,first%10,first);
            }
        }
        for (int cell = 0; cell < 81; cell++) if (board.getValue(cell) == 0) {
            Set<Integer> candidates = new HashSet<Integer>();
            for (int digit : board.getAllCandidates(cell)) candidates.add(cell * 10 + digit);
            addAllFalse(candidates, crosses, result, ConflictKind.CELL_NO_CANDIDATE,cell,0);
        }
        for (int unit = 0; unit < 27; unit++) for (int digit = 1; digit <= 9; digit++) {
            boolean satisfied = false;
            Set<Integer> positions = new HashSet<Integer>();
            for (int offset = 0; offset < 9; offset++) {
                int cell = unitCell(unit, offset);
                if (board.getValue(cell) == digit) { satisfied = true; break; }
                if (currentCandidate(board, cell, digit)) positions.add(cell * 10 + digit);
            }
            if (!satisfied) addAllFalse(positions, crosses, result, ConflictKind.HOUSE_NO_DIGIT,unit,digit);
        }
        if (result.validStart && result.startKind == DoodleStroke.MARK_TRUE_CIRCLE
                && !result.starts.isEmpty() && crosses.containsAll(result.starts)) {
            result.circleWitnesses.addAll(result.starts); result.crossWitnesses.addAll(result.starts);
            result.record(ConflictKind.OR_PREMISE_ALL_FALSE,-1,0,result.starts);
        }
        if (unique && circles.size() >= 4) addDeadlyRectangles(board, circles, result);
        return result;
    }

    /** Trusted true circles are projected values, not merely the presence of two candidates. */
    private static void addDeadlyRectangles(Sudoku2 board, Set<Integer> circles, Analysis result) {
        int[] values = new int[81];
        for (int atom : circles) {
            int cell = atom / 10, digit = atom % 10;
            if (board.getValue(cell) != 0 || board.isFixed(cell)) continue;
            values[cell] = values[cell] == 0 || values[cell] == digit ? digit : -1;
        }
        for (int row = 0; row < 8; row++) for (int nextRow = row + 1; nextRow < 9; nextRow++)
            for (int col = 0; col < 8; col++) for (int nextCol = col + 1; nextCol < 9; nextCol++) {
                // Exactly two boxes: one axis stays within a band/stack and the other crosses it.
                if ((row / 3 == nextRow / 3) == (col / 3 == nextCol / 3)) continue;
                int a = row * 9 + col, b = row * 9 + nextCol;
                int c = nextRow * 9 + col, d = nextRow * 9 + nextCol;
                if (values[a] <= 0 || values[b] <= 0 || values[a] == values[b]
                        || values[a] != values[d] || values[b] != values[c]) continue;
                result.uniquenessConflict = true;
                result.record(ConflictKind.DEADLY_RECTANGLE,-1,0,a*10+values[a],b*10+values[b],c*10+values[c],d*10+values[d]);
                // Swapping these values preserves every row, column, box and given.
                // Removed pencil candidates do not disprove the uniqueness argument.
                for (int cell : new int[]{a, b, c, d}) {
                    int atom = cell * 10 + values[cell];
                    if (!result.starts.contains(atom)) result.circleWitnesses.add(atom);
                }
            }
    }

    private static void recordSharedUnits(Analysis result, ConflictKind kind,int first,int second){
        int a=first/10,b=second/10,digit=first%10;
        if(Sudoku2.getRow(a)==Sudoku2.getRow(b))result.record(kind,Sudoku2.getRow(a),digit,first,second);
        if(Sudoku2.getCol(a)==Sudoku2.getCol(b))result.record(kind,9+Sudoku2.getCol(a),digit,first,second);
        if(Sudoku2.getBlock(a)==Sudoku2.getBlock(b))result.record(kind,18+Sudoku2.getBlock(a),digit,first,second);
    }
    private static void addAllFalse(Set<Integer> candidates, Set<Integer> crosses, Analysis result,
            ConflictKind kind,int location,int digit) {
        // A pre-existing empty set is not a contradiction caused by this branch.
        if (!candidates.isEmpty() && crosses.containsAll(candidates)) {
            result.crossWitnesses.addAll(candidates);result.record(kind,location,digit,candidates);
        }
    }
    static boolean currentCandidate(Sudoku2 board, int cell, int digit) {
        return cell >= 0 && cell < 81 && digit >= 1 && digit <= 9
                && board.getValue(cell) == 0 && board.isCandidate(cell, digit);
    }
    private static int atom(DoodleStroke mark) { return mark.getAnchorCell() * 10 + mark.getAnchorDigit(); }
    private static boolean peers(int a, int b) {
        return a != b && (Sudoku2.getRow(a) == Sudoku2.getRow(b)
                || Sudoku2.getCol(a) == Sudoku2.getCol(b) || Sudoku2.getBlock(a) == Sudoku2.getBlock(b));
    }
    private static int unitCell(int unit, int offset) {
        if (unit < 9) return unit * 9 + offset;
        if (unit < 18) return offset * 9 + unit - 9;
        int box = unit - 18;
        return (box / 3 * 3 + offset / 3) * 9 + box % 3 * 3 + offset % 3;
    }
}
