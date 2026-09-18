package sudoku;

import java.util.*;

/** Semantic participation index. Conclusions never silently become premises. */
final class ReasoningStepIndex {
    final Set<Integer> premiseCells = new TreeSet<Integer>(), premiseNodes = new TreeSet<Integer>();
    final Set<Integer> conclusionCells = new TreeSet<Integer>(), conclusionNodes = new TreeSet<Integer>();
    boolean supported = true;
    static final Set<SolutionType> SINGLE_DIGIT = Collections.unmodifiableSet(EnumSet.of(
            SolutionType.FULL_HOUSE, SolutionType.HIDDEN_SINGLE, SolutionType.NAKED_SINGLE,
            SolutionType.LOCKED_CANDIDATES_1, SolutionType.LOCKED_CANDIDATES_2,
            SolutionType.SKYSCRAPER, SolutionType.TWO_STRING_KITE, SolutionType.TURBOT_FISH,
            SolutionType.EMPTY_RECTANGLE));
    static ReasoningStepIndex from(SolutionStep step, Sudoku2 board) {
        ReasoningStepIndex out = new ReasoningStepIndex();
        for (Candidate c : step.getCandidatesToDelete()) out.conclusion(c.getIndex(), c.getValue());
        if (step.getAnzSet() > 0) {
            for (int i = 0; i < step.getIndices().size(); i++) {
                int v = step.getType() == SolutionType.TEMPLATE_SET ? step.getValues().get(0)
                        : step.getValues().get(Math.min(i, step.getValues().size() - 1));
                out.conclusion(step.getIndices().get(i), v);
            }
        }
        switch (step.getType()) {
        case FULL_HOUSE: case HIDDEN_SINGLE: case NAKED_SINGLE:
            break;
        case NAKED_PAIR: case NAKED_TRIPLE: case NAKED_QUADRUPLE:
        case HIDDEN_PAIR: case HIDDEN_TRIPLE: case HIDDEN_QUADRUPLE:
        case LOCKED_PAIR: case LOCKED_TRIPLE:
        case LOCKED_CANDIDATES_1: case LOCKED_CANDIDATES_2:
        case SKYSCRAPER: case TWO_STRING_KITE: case EMPTY_RECTANGLE:
        case DUAL_TWO_STRING_KITE: case DUAL_EMPTY_RECTANGLE:
        case XY_WING: case XYZ_WING: case W_WING:
        case SUE_DE_COQ:
        case UNIQUENESS_1: case UNIQUENESS_2: case UNIQUENESS_3:
        case UNIQUENESS_4: case UNIQUENESS_5: case UNIQUENESS_6:
        case AVOIDABLE_RECTANGLE_1: case AVOIDABLE_RECTANGLE_2:
        case HIDDEN_RECTANGLE: // UniquenessSolver stores all four corners and both digits.
            for (int c : step.getIndices()) for (int v : step.getValues()) out.premise(c, v, board);
            break;
        default:
            // FishSolver stores base candidates minus fins in indices and one
            // actual fish digit in values; fins/endo-fins are added below.
            if (step.getType().isFish()) {
                for (int c : step.getIndices()) for (int v : step.getValues()) out.premise(c, v, board);
                break;
            }
            // Chains, ALS and colored candidate structures are decoded below.
            if (step.getChains().isEmpty() && step.getAlses().isEmpty()
                    && step.getColorCandidates().isEmpty()) out.supported = false;
        }
        for (Candidate c : step.getFins()) out.premise(c.getIndex(), c.getValue(), board);
        for (Candidate c : step.getEndoFins()) out.premise(c.getIndex(), c.getValue(), board);
        for (Map.Entry<Integer, Integer> c : step.getColorCandidates().entrySet())
            for (int v : step.getValues()) out.premise(c.getKey(), v, board);
        for (AlsInSolutionStep als : step.getAlses())
            for (int c : als.getIndices()) for (int v : als.getCandidates()) out.premise(c, v, board);
        for (Chain chain : step.getChains()) {
            for (int i = chain.getStart(); i <= chain.getEnd(); i++) {
                int entry = chain.getChain()[i];
                if (entry == Integer.MIN_VALUE) continue; // native end-of-branch marker, not a candidate
                entry = Math.abs(entry);
                int kind = Chain.getSNodeType(entry), digit = Chain.getSCandidate(entry);
                if (kind == Chain.ALS_NODE) continue; // full ALS memberships already indexed
                if (kind != Chain.NORMAL_NODE && kind != Chain.GROUP_NODE) { out.supported = false; continue; }
                out.premise(Chain.getSCellIndex(entry), digit, board);
                if (kind == Chain.GROUP_NODE) {
                    out.premise(Chain.getSCellIndex2(entry), digit, board);
                    out.premise(Chain.getSCellIndex3(entry), digit, board);
                }
            }
        }
        return out;
    }
    private void premise(int c, int v, Sudoku2 board) {
        if (c >= 0 && c < 81 && v > 0 && v <= 9 && board.isCandidate(c, v)) {
            premiseCells.add(c); premiseNodes.add(c * 10 + v);
        }
    }
    private void conclusion(int c, int v) {
        conclusionCells.add(c); conclusionNodes.add(c * 10 + v);
    }
    int role(Set<Integer> selected, boolean cells) {
        Set<Integer> premise = cells ? premiseCells : premiseNodes;
        Set<Integer> conclusion = cells ? conclusionCells : conclusionNodes;
        Set<Integer> all = new HashSet<Integer>(premise); all.addAll(conclusion);
        if (!supported || selected.isEmpty() || !all.containsAll(selected)) return -1;
        boolean p = !Collections.disjoint(premise, selected), c = !Collections.disjoint(conclusion, selected);
        return p && c ? 2 : p ? 0 : 1;
    }
    static boolean singleDigit(SolutionStep step, int digit) {
        if (!SINGLE_DIGIT.contains(step.getType()) && step.getType() != SolutionType.DUAL_TWO_STRING_KITE
                && step.getType() != SolutionType.DUAL_EMPTY_RECTANGLE) return false;
        if (step.getAnzSet() > 0) return step.getValues().contains(digit);
        if (step.getCandidatesToDelete().isEmpty()) return false;
        for (Candidate c : step.getCandidatesToDelete()) if (c.getValue() != digit) return false;
        return true;
    }
    static String identity(SolutionStep s) {
        StringBuilder key = new StringBuilder(s.getType().name()).append('|').append(s.getSubType());
        key.append('|').append(s.getIndices()).append('|').append(s.getValues());
        appendCandidates(key, s.getCandidatesToDelete());
        appendCandidates(key, s.getFins()); appendCandidates(key, s.getEndoFins());
        appendCandidates(key, s.getCannibalistic());
        key.append('|').append(new TreeMap<Integer, Integer>(s.getColorCandidates()));
        for (Entity entity : s.getBaseEntities()) key.append("|B:").append(entity.getEntityName()).append(':').append(entity.getEntityNumber());
        for (Entity entity : s.getCoverEntities()) key.append("|C:").append(entity.getEntityName()).append(':').append(entity.getEntityNumber());
        for (solver.RestrictedCommon rc : s.getRestrictedCommons()) key.append("|R:").append(rc.getAls1()).append(':')
                .append(rc.getAls2()).append(':').append(rc.getCand1()).append(':').append(rc.getCand2()).append(':').append(rc.getActualRC());
        for (AlsInSolutionStep als : s.getAlses()) key.append('|').append(als.getIndices()).append(':').append(als.getCandidates());
        for (Chain chain : s.getChains()) key.append('|').append(Arrays.toString(
                Arrays.copyOfRange(chain.getChain(), chain.getStart(), chain.getEnd() + 1)));
        return key.append('|').append(s.getEntity()).append(':').append(s.getEntityNumber())
                .append(':').append(s.getEntity2()).append(':').append(s.getEntity2Number())
                .append(':').append(s.isIsSiamese()).toString();
    }
    private static void appendCandidates(StringBuilder key, List<Candidate> candidates) {
        Set<Integer> ids = new TreeSet<Integer>();
        for (Candidate c : candidates) ids.add(c.getIndex() * 10 + c.getValue());
        key.append('|').append(ids);
    }
}
