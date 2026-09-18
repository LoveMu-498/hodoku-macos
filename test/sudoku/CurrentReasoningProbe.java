package sudoku;

import java.util.*;

/** Real-board native participation and independent constraint checks for authored proofs. */
public final class CurrentReasoningProbe {
    static final String PUZZLE = "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    private static int proofs;
    public static void main(String[] args) {
        Sudoku2 board = new Sudoku2(); board.setSudoku(PUZZLE);
        String before = TechniqueStepCatalog.createSignature(board);
        List<SolutionStep> catalog = new TechniqueStepCatalog().findAllRawSteps(board, null);
        check(!catalog.isEmpty(), "real board produced no native steps");
        int nativeChains = 0, matched = 0;
        for (SolutionStep step : catalog) {
            ReasoningStepIndex index = ReasoningStepIndex.from(step, board);
            if (index.supported) {
                Set<Integer> selected = new TreeSet<Integer>(index.premiseNodes); selected.addAll(index.conclusionNodes);
                if (!selected.isEmpty()) {
                    check(index.role(selected, false) >= 0, "all real participants failed containment"); matched++;
                    selected.add(999); check(index.role(selected, false) < 0, "unrelated candidate matched");
                }
            }
            for (Chain nativeChain : step.getChains()) {
                UserChain authored = fromNative(nativeChain);
                if (authored == null || nativeChains >= 60) continue;
                UserChainValidator.Result result = UserChainValidator.validate(board, authored);
                if (result.status != UserChainValidator.Status.PROVEN) continue;
                checkConclusions(board, result.steps); nativeChains++;
                UserChain reversed = reverse(authored);
                UserChainValidator.Result backward = UserChainValidator.validate(board, reversed);
                check(backward.status == UserChainValidator.Status.PROVEN, "reversing valid proof lost conclusions");
                check(conclusions(result.steps).equals(conclusions(backward.steps)), "reversing proof changed conclusions");
            }
        }
        check(matched > 0 && nativeChains > 0, "missing real native matching or chain fixtures");
        check(before.equals(TechniqueStepCatalog.createSignature(board)), "analysis mutated board/options");
        verifyRelations(); verifyRoles(board); verifyDigitScope(board);
        System.out.println("Current reasoning checks passed: " + matched + " native instances, "
                + nativeChains + " authored/reversed chains, " + proofs + " independently refuted conclusions");
        System.exit(0);
    }
    static UserChain fromNative(Chain chain) {
        UserChain out = new UserChain();
        Set<Integer> seen = new HashSet<Integer>();
        int startId = -1;
        for (int i = chain.getStart(); i <= chain.getEnd(); i++) {
            int e = chain.getChain()[i];
            if (e <= 0 || Chain.getSNodeType(e) != Chain.NORMAL_NODE) return null;
            int cell = Chain.getSCellIndex(e), digit = Chain.getSCandidate(e), id = cell * 10 + digit;
            if (i == chain.getStart()) startId = id;
            if (i > chain.getStart()) out.getStrongRelations().add(chain.isStrong(i));
            if (id == startId && i == chain.getEnd()) { out.setClosed(true); break; }
            if (!seen.add(id)) return null;
            out.getNodes().add(new UserChainNode(cell, digit));
        }
        return out.getNodes().size() < 2 ? null : out;
    }
    private static UserChain reverse(UserChain source) {
        UserChain out = new UserChain(); out.setClosed(source.isClosed());
        if (source.isClosed()) {
            out.getNodes().add(source.getNodes().get(0));
            for (int i = source.getNodes().size() - 1; i > 0; i--) out.getNodes().add(source.getNodes().get(i));
        } else for (int i = source.getNodes().size() - 1; i >= 0; i--) out.getNodes().add(source.getNodes().get(i));
        for (int i = source.getStrongRelations().size() - 1; i >= 0; i--) out.getStrongRelations().add(source.getStrongRelations().get(i));
        return out;
    }
    private static Set<String> conclusions(List<SolutionStep> steps) {
        Set<String> result = new TreeSet<String>();
        for (SolutionStep s : steps) {
            for (Candidate c : s.getCandidatesToDelete()) result.add("D" + c.getIndex() + ":" + c.getValue());
            if (s.getAnzSet() > 0) for (int i = 0; i < s.getIndices().size(); i++) result.add("S" + s.getIndices().get(i) + ":" + s.getValues().get(i));
        }
        return result;
    }
    static void checkConclusions(Sudoku2 board, List<SolutionStep> steps) {
        check(satisfiable(board, -1, -1, false), "base fixture has no solution");
        for (SolutionStep step : steps) {
            for (Candidate c : step.getCandidatesToDelete()) {
                check(!satisfiable(board, c.getIndex(), c.getValue(), true), "unsound deletion " + c.getIndex() + ":" + c.getValue()); proofs++;
            }
            if (step.getAnzSet() > 0) for (int i = 0; i < step.getIndices().size(); i++) {
                check(!satisfiable(board, step.getIndices().get(i), step.getValues().get(i), false), "unsound placement"); proofs++;
            }
        }
    }
    // Independent MRV Sudoku search. Uses original logical candidates, no HoDoKu solver/proof code.
    private static boolean satisfiable(Sudoku2 board, int target, int digit, boolean force) {
        int[] values = board.getValues().clone(), masks = new int[81];
        for (int i = 0; i < 81; i++) {
            for (int d = 1; d <= 9; d++) if (board.isCandidate(i, d)) masks[i] |= 1 << d;
        }
        if (target >= 0) { if (force) masks[target] &= 1 << digit; else masks[target] &= ~(1 << digit); }
        return search(values, masks);
    }
    private static boolean search(int[] values, int[] masks) {
        int best = -1, available = 0, size = 10;
        for (int i = 0; i < 81; i++) if (values[i] == 0) {
            int m = masks[i];
            for (int j = 0; j < 81; j++) if (values[j] != 0 && (i / 9 == j / 9 || i % 9 == j % 9
                    || i / 27 == j / 27 && i % 9 / 3 == j % 9 / 3)) m &= ~(1 << values[j]);
            int count = Integer.bitCount(m); if (count == 0) return false;
            if (count < size) { best = i; available = m; size = count; }
        }
        if (best < 0) return true;
        for (int d = 1; d <= 9; d++) if ((available & 1 << d) != 0) {
            values[best] = d; if (search(values, masks)) { values[best] = 0; return true; }
        }
        values[best] = 0; return false;
    }
    private static UserChain chain(boolean closed, int[] ids, boolean... relations) {
        UserChain c = new UserChain(); c.setClosed(closed);
        for (int id : ids) c.getNodes().add(new UserChainNode(id / 10, id % 10));
        for (boolean relation : relations) c.getStrongRelations().add(relation);
        return c;
    }
    private static void verifyRelations() {
        Sudoku2 board = new Sudoku2(); board.setSudoku(new String(new char[81]).replace('\0', '0'));
        // r1c1 and r1c2: row has 9 occurrences, but shared box has only two.
        for (int c : new int[] {2,9,10,11,18,19,20}) board.delCandidate(c, 1);
        check(UserChainValidator.strong(board, 1, 11), "shared box strong link missed after row failure");
        check(UserChainValidator.weak(1, 11), "strong link cannot be used weakly");
        check(!UserChainValidator.weak(1, 401), "unrelated cells called weak");
        check(UserChainValidator.validate(board, chain(false, new int[]{1, 401}, false)).status == UserChainValidator.Status.INVALID, "invalid weak link accepted");
        check(UserChainValidator.validate(board, chain(false, new int[]{1, 41}, true)).status == UserChainValidator.Status.INVALID, "false strong accepted");
        check(UserChainValidator.validate(board, chain(false, new int[]{1, 11}, false)).status == UserChainValidator.Status.NO_CONCLUSION, "one weak link proves a deletion");
        check(UserChainValidator.validate(board, chain(false, new int[]{1})).status == UserChainValidator.Status.INCOMPLETE, "one node accepted");
        check(UserChainValidator.validate(board, chain(false, new int[]{1, 1}, false)).status == UserChainValidator.Status.INVALID, "duplicate nodes accepted");
    }
    private static void verifyRoles(Sudoku2 board) {
        SolutionStep step = new SolutionStep(SolutionType.ALS_XZ);
        SudokuSet cells = new SudokuSet(); cells.add(2); cells.add(3);
        step.addAls(cells, (short)(Sudoku2.MASKS[1] | Sudoku2.MASKS[2] | Sudoku2.MASKS[6]));
        step.addCandidateToDelete(5, 2);
        ReasoningStepIndex index = ReasoningStepIndex.from(step, board);
        check(index.role(Collections.singleton(2), true) == 0, "ALS-only premise absent");
        check(index.role(Collections.singleton(52), false) == 1, "deletion mistaken for premise");
        check(index.role(new HashSet<Integer>(Arrays.asList(2, 5)), true) == 2, "mixed roles absent");
        SolutionStep grouped = new SolutionStep(SolutionType.GROUPED_AIC);
        grouped.addChain(0, 1, new int[]{Chain.makeSEntry(2, 3, -1, 2, true, Chain.GROUP_NODE), Chain.makeSEntry(5, 2, false)});
        grouped.addCandidateToDelete(8, 2);
        ReasoningStepIndex gi = ReasoningStepIndex.from(grouped, board);
        check(gi.premiseCells.contains(2) && gi.premiseCells.contains(3) && !gi.premiseCells.contains(0), "group members decoded incorrectly");
    }
    private static void verifyDigitScope(Sudoku2 board) {
        TechniqueStepCatalog catalog = new TechniqueStepCatalog();
        List<SolutionStep> steps = catalog.findSteps(board, null, true);
        check(!steps.isEmpty(), "digit query empty");
        for (SolutionStep s : steps) {
            check(ReasoningStepIndex.SINGLE_DIGIT.contains(s.getType()), "unrelated technique entered digit query");
            for (int d = 1; d <= 9; d++) if (ReasoningStepIndex.singleDigit(s, d)) {
                if (s.getType() == SolutionType.NAKED_SINGLE) check(board.getAnzCandidates(s.getIndices().get(0)) == 1, "filtered digit became naked single");
            }
        }
        List<Boolean> enabled = new ArrayList<Boolean>();
        try {
            for (StepConfig c : Options.getInstance().solverSteps) { enabled.add(c.isAllStepsEnabled()); c.setAllStepsEnabled(false); }
            check(catalog.findSteps(board, null, true).isEmpty(), "disabled query still ran unrelated solvers");
        } finally {
            for (int i = 0; i < enabled.size(); i++) Options.getInstance().solverSteps[i].setAllStepsEnabled(enabled.get(i));
        }
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
