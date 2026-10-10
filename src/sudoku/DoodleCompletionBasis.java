/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;

/** A full chosen completion, checked independently of a solver's stored solution. */
final class DoodleCompletionBasis {
    static final class Analysis {
        final int group;
        final Set<Integer> fills = new TreeSet<Integer>();
        Analysis(int group) { this.group = group; }
        boolean complete() { return !fills.isEmpty(); }
        boolean selects(DoodleStroke mark) {
            return mark != null && !mark.isProofOnly() && !mark.isRefutationResult()
                    && mark.getThoughtGroup() == group && mark.isStandardCandidateMark()
                    && mark.getCandidateMarkKind() == DoodleStroke.MARK_TRUE_CIRCLE
                    && fills.contains(mark.getAnchorCell()*10+mark.getAnchorDigit());
        }
    }
    static Analysis analyze(Sudoku2 board, Iterable<DoodleStroke> marks, int group) {
        Analysis result = new Analysis(group);
        if (board == null || marks == null || group < 0 || group > 5) return result;
        Map<Integer,Integer> chosen = new LinkedHashMap<Integer,Integer>();
        Set<Integer> crosses = new TreeSet<Integer>();
        int starts = 0;
        for (DoodleStroke mark : marks) {
            if (mark == null || mark.isProofOnly() || mark.isRefutationResult() || mark.getThoughtGroup() != group) continue;
            if (mark.isHypothesisStart()) {
                starts++;
                if (!mark.isStandardCandidateMark() || mark.getHypothesisGroupKind() != 0
                        || mark.getHypothesisConditionId() != 0 || board.getValue(mark.getAnchorCell()) != 0
                        || !board.isCandidate(mark.getAnchorCell(),mark.getAnchorDigit())) return result;
            }
            if (!mark.isStandardCandidateMark()) continue;
            int cell = mark.getAnchorCell(), digit = mark.getAnchorDigit();
            if (mark.getCandidateMarkKind() == DoodleStroke.MARK_FALSE_CROSS) { crosses.add(cell*10+digit); continue; }
            if (mark.getHypothesisGroupKind() == 1) return result;
            if (board.getValue(cell) != 0) {
                if (board.getValue(cell) != digit) return result;
                continue;
            }
            if (!board.isCandidate(cell,digit)) return result;
            Integer prior = chosen.put(cell,digit);
            if (prior != null && prior != digit) return result;
        }
        if (starts != 1 || chosen.isEmpty()) return result;
        int[] values = new int[81];
        for (int cell=0;cell<81;cell++) {
            Integer choice = chosen.get(cell);
            values[cell] = board.getValue(cell) != 0 ? board.getValue(cell) : choice == null ? 0 : choice;
            if (values[cell] == 0 || crosses.contains(cell*10+values[cell])) return result;
        }
        for (int unit=0;unit<27;unit++) {
            int seen=0;
            for (int i=0;i<9;i++) {
                int cell = unit<9 ? unit*9+i : unit<18 ? i*9+unit-9
                        : (unit-18)/3*27+(unit-18)%3*3+i/3*9+i%3;
                int bit=1<<values[cell];
                if ((seen&bit)!=0) return result;
                seen|=bit;
            }
            if (seen!=0x3fe) return result;
        }
        for (Map.Entry<Integer,Integer> entry:chosen.entrySet()) result.fills.add(entry.getKey()*10+entry.getValue());
        return result;
    }
}
