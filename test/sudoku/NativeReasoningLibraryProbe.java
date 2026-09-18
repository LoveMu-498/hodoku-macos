package sudoku;

import java.util.*;
import solver.SudokuSolver;

/** Exercises actual library candidate states, not fabricated SolutionStep structures. */
public final class NativeReasoningLibraryProbe {
    public static void main(String[] args) {
        int cases = 0, chainProofs = 0;
        for (String[] fixture : ReasoningFixtures.CASES) {
            SolutionType type = SolutionType.valueOf(fixture[0]);
            Sudoku2 board = new Sudoku2(); board.setSudoku(fixture[1]);
            SudokuSolver solver = new SudokuSolver(); solver.setSudoku(board);
            List<SolutionStep> found = new ArrayList<SolutionStep>();
            FindAllSteps finder = new FindAllSteps(found, board, null, solver.getStepFinder());
            finder.setTestType(Collections.singletonList(type.getStepConfig().getType()));
            finder.setCalculateProgressScores(false); finder.run();
            SolutionStep step = null;
            for (SolutionStep candidate : found) {
                if (candidate.getType() != type && type != SolutionType.FORCING_CHAIN && type != SolutionType.FORCING_NET) continue;
                if (ReasoningStepIndex.SINGLE_DIGIT.contains(type)
                        && !ReasoningStepIndex.singleDigit(candidate, Integer.parseInt(fixture[1].split(":")[2]))) continue;
                step = candidate; break;
            }
            CurrentReasoningProbe.check(step != null, "missing library technique " + type);
            ReasoningStepIndex index = ReasoningStepIndex.from(step, board);
            CurrentReasoningProbe.check(index.supported, "unindexed library technique " + type);
            Set<Integer> all = new TreeSet<Integer>(index.premiseNodes); all.addAll(index.conclusionNodes);
            CurrentReasoningProbe.check(!all.isEmpty() && index.role(all, false) >= 0, "library participation lost " + type);
            for (int id : all) CurrentReasoningProbe.check(index.role(Collections.singleton(id), false) >= 0, "partial input lost " + type);
            all.add(999); CurrentReasoningProbe.check(index.role(all, false) < 0, "extra input matched " + type);
            if (ReasoningStepIndex.SINGLE_DIGIT.contains(type)) {
                int digit = Integer.parseInt(fixture[1].split(":")[2]);
                CurrentReasoningProbe.check(ReasoningStepIndex.singleDigit(step, digit), "digit positive missing " + type);
                CurrentReasoningProbe.check(!ReasoningStepIndex.singleDigit(step, digit % 9 + 1), "foreign digit matched " + type);
            }
            for (Chain chain : step.getChains()) {
                UserChain authored = CurrentReasoningProbe.fromNative(chain);
                if (authored == null) continue;
                UserChainValidator.Result result = UserChainValidator.validate(board, authored);
                if (result.status == UserChainValidator.Status.PROVEN) {
                    CurrentReasoningProbe.checkConclusions(board, result.steps); chainProofs++;
                }
            }
            cases++; System.out.println("Verified library case: " + type.name());
        }
        CurrentReasoningProbe.check(cases >= 23, "incomplete library coverage");
        System.out.println("Native library checks passed: " + cases + " technique families, " + chainProofs + " independently checked authored proofs");
        System.exit(0);
    }
}
