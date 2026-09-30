/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Exact, value-based matching between authored reasoning and native steps. */
final class NativeReasoningMatcher {
	private NativeReasoningMatcher() {
	}

	private interface Proof {
		String stableValue();
	}

	static final class BoxProof implements Proof {
		private final List<Integer> cells;

		private BoxProof(List<Integer> cells) {
			this.cells = Collections.unmodifiableList(new ArrayList<Integer>(cells));
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof BoxProof && cells.equals(((BoxProof) other).cells);
		}

		@Override
		public int hashCode() {
			return cells.hashCode();
		}

		@Override
		public String stableValue() {
			return "B" + cells.toString();
		}
	}

	private static final class CandidateNode implements Comparable<CandidateNode> {
		private final int cell;
		private final int candidate;

		private CandidateNode(int cell, int candidate) {
			this.cell = cell;
			this.candidate = candidate;
		}

		private int id() {
			return cell * 10 + candidate;
		}

		@Override
		public int compareTo(CandidateNode other) {
			return id() - other.id();
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof CandidateNode && cell == ((CandidateNode) other).cell
					&& candidate == ((CandidateNode) other).candidate;
		}

		@Override
		public int hashCode() {
			return id();
		}
	}

	private static final class ProofEdge implements Comparable<ProofEdge> {
		private final CandidateNode from;
		private final CandidateNode to;
		private final boolean strong;

		private ProofEdge(CandidateNode from, CandidateNode to, boolean strong) {
			this.from = from;
			this.to = to;
			this.strong = strong;
		}

		private ProofEdge reversed() {
			return new ProofEdge(to, from, strong);
		}

		@Override
		public int compareTo(ProofEdge other) {
			int result = from.compareTo(other.from);
			if (result == 0) result = to.compareTo(other.to);
			if (result == 0) result = (strong ? 1 : 0) - (other.strong ? 1 : 0);
			return result;
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof ProofEdge)) return false;
			ProofEdge edge = (ProofEdge) other;
			return from.equals(edge.from) && to.equals(edge.to) && strong == edge.strong;
		}

		@Override
		public int hashCode() {
			int value = 31 * from.hashCode() + to.hashCode();
			return 31 * value + (strong ? 1 : 0);
		}

		@Override
		public String toString() {
			return from.id() + ">" + to.id() + (strong ? "S" : "W");
		}
	}

	static final class ChainProof implements Proof {
		private final boolean closed;
		private final List<ProofEdge> edges;

		private ChainProof(boolean closed, List<ProofEdge> edges) {
			this.closed = closed;
			this.edges = Collections.unmodifiableList(new ArrayList<ProofEdge>(edges));
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof ChainProof && closed == ((ChainProof) other).closed
					&& edges.equals(((ChainProof) other).edges);
		}

		@Override
		public int hashCode() {
			return 31 * (closed ? 1 : 0) + edges.hashCode();
		}

		@Override
		public String stableValue() {
			return (closed ? "C" : "O") + edges.toString();
		}
	}

	static final class NativeStepKey {
		private final String value;

		private NativeStepKey(String value) {
			this.value = value;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof NativeStepKey && value.equals(((NativeStepKey) other).value);
		}

		@Override
		public int hashCode() {
			return value.hashCode();
		}

		@Override
		public String toString() {
			return value;
		}
	}

	static final class Match {
		private final SolutionStep step;
		private final NativeStepKey key;

		Match(SolutionStep step, NativeStepKey key) {
			this.step = step;
			this.key = key;
		}

		SolutionStep getStep() {
			return step;
		}

		NativeStepKey getKey() {
			return key;
		}
	}

	static BoxProof normalizeBoxSelection(SudokuSet selected, Sudoku2 board) {
		if (selected == null || board == null || selected.isEmpty()) return null;
		List<Integer> cells = new ArrayList<Integer>(selected.size());
		for (int i = 0; i < selected.size(); i++) {
			int index = selected.get(i);
			if (index < 0 || index >= 81 || board.getValue(index) != 0) return null;
			cells.add(Integer.valueOf(index));
		}
		Collections.sort(cells);
		return new BoxProof(cells);
	}

	static Match findBoxMatch(List<SolutionStep> catalog, BoxProof selected) {
		if (catalog == null || selected == null) return null;
		SolutionStep best = null;
		for (SolutionStep step : catalog) {
			BoxProof nativeProof = normalizeNativeBoxProof(step);
			if (selected.equals(nativeProof) && hasExecutableDeletion(step)) {
				if (preferredBoxStep(step, best)) best = step;
			}
		}
		return best == null ? null : new Match(best, keyForStep(best));
	}

    // Windows reference 20260925: detectSinglesInSelection, then BOX_SELECTION_FISH_TYPES.
    // This affects only selection of a Box result; ordinary hint configuration is unchanged.
    private static final SolutionType[] BOX_PRIORITY = {
            SolutionType.NAKED_SINGLE,
            SolutionType.HIDDEN_SINGLE,
            SolutionType.NAKED_PAIR,
            SolutionType.NAKED_TRIPLE,
            SolutionType.NAKED_QUADRUPLE,
            SolutionType.HIDDEN_PAIR,
            SolutionType.HIDDEN_TRIPLE,
            SolutionType.HIDDEN_QUADRUPLE,
            SolutionType.LOCKED_PAIR,
            SolutionType.LOCKED_TRIPLE,
            SolutionType.LOCKED_CANDIDATES_1,
            SolutionType.LOCKED_CANDIDATES_2,
            SolutionType.X_WING,
            SolutionType.FINNED_X_WING,
            SolutionType.SASHIMI_X_WING,
            SolutionType.SWORDFISH,
            SolutionType.FINNED_SWORDFISH,
            SolutionType.SASHIMI_SWORDFISH,
            SolutionType.JELLYFISH,
            SolutionType.FINNED_JELLYFISH,
            SolutionType.SASHIMI_JELLYFISH,
            SolutionType.SKYSCRAPER,
            SolutionType.TWO_STRING_KITE,
            SolutionType.EMPTY_RECTANGLE,
            SolutionType.XY_WING,
            SolutionType.XYZ_WING,
            SolutionType.W_WING,
            SolutionType.XY_CHAIN,
            SolutionType.REMOTE_PAIR,
            SolutionType.ALS_XZ,
            SolutionType.ALS_XY_WING,
            SolutionType.ALS_XY_CHAIN,
            SolutionType.SUE_DE_COQ,
            SolutionType.GROUPED_AIC,
            SolutionType.GROUPED_NICE_LOOP
    };

    private static int boxPriority(SolutionType type) {
        if(type==SolutionType.GROUPED_CONTINUOUS_NICE_LOOP
                || type==SolutionType.GROUPED_DISCONTINUOUS_NICE_LOOP)type=SolutionType.GROUPED_NICE_LOOP;
        for(int i=0;i<BOX_PRIORITY.length;i++)if(BOX_PRIORITY[i]==type)return i;
        return BOX_PRIORITY.length;
    }

    static boolean preferredBoxStep(SolutionStep candidate, SolutionStep current) {
        if(current==null)return true;
        int candidatePriority=boxPriority(candidate.getType()),currentPriority=boxPriority(current.getType());
        if(candidatePriority!=currentPriority)return candidatePriority<currentPriority;
        // Keep the first native result within a Windows technique. Retain macOS-only
        // techniques after that list, using their existing configured order.
        return candidatePriority==BOX_PRIORITY.length && configuredPriority(candidate)<configuredPriority(current);
    }

    /** Singles identify their target as a conclusion, not as a premise. */
    static boolean matchesBoxSelection(SolutionStep step, Sudoku2 board, Set<Integer> selected) {
        if (step == null || selected == null || selected.isEmpty()) return false;
        ReasoningStepIndex index = ReasoningStepIndex.from(step, board);
        if (!index.supported) return false;
        switch (step.getType()) {
        case FULL_HOUSE:
        case NAKED_SINGLE:
        case HIDDEN_SINGLE:
            return selected.size() == 1 && index.conclusionCells.equals(selected)
                    && SudokuPanel.isNativeConclusionExecutable(step, board);
        default:
            return !index.premiseCells.isEmpty() && index.premiseCells.equals(selected)
                    && SudokuPanel.isNativeConclusionExecutable(step, board);
        }
    }

	static Match matchBox(List<SolutionStep> catalog, Sudoku2 board, SudokuSet selected) {
		return findBoxMatch(catalog, normalizeBoxSelection(selected, board));
	}

	static Match matchChain(List<SolutionStep> catalog, Sudoku2 board, UserChain authored) {
		ChainProof selected = normalizeUserChain(authored, board);
		if (catalog == null || selected == null) return null;
		SolutionStep best = null;
		int bestPriority = Integer.MAX_VALUE;
		for (SolutionStep step : catalog) {
			ChainProof nativeProof = normalizeNativeChainProof(step);
			if (selected.equals(nativeProof) && hasExecutableDeletion(step)) {
				int priority = configuredPriority(step);
				if (best == null || priority < bestPriority) {
					best = step;
					bestPriority = priority;
				}
			}
		}
		return best == null ? null : new Match(best, keyForStep(best));
	}

    /** Visual path identity for Tab: complete propositions and every drawn link. */
    static String authoredCompletePath(UserChain chain, Sudoku2 board) {
        if (chain == null || board == null || chain.isActive()) return null;
        List<UserChainNode> source = chain.getNodes();
        int size = source == null ? 0 : source.size();
        if (size < (chain.isClosed() ? 3 : 2)
                || chain.getStrongRelations().size() != size - (chain.isClosed() ? 0 : 1)) return null;
        List<String> nodes = new ArrayList<String>(size);
        for (UserChainNode node : source) {
            if (node == null || !node.nativeEncodable()) return null;
            for (int atom : node.atoms())
                if (!board.isCandidate(atom / 10, atom % 10)) return null;
            if (nodes.contains(node.key())) return null;
            nodes.add(node.key());
        }
        for (int i = 0; i < chain.getStrongRelations().size(); i++) {
            if (chain.getStrongRelations().get(i) == null) return null;
            if (chain.getStrongRelations().get(i)
                    && !UserChainValidator.ordinaryStrong(board, source.get(i), source.get((i + 1) % size)))
                return null; // An ALS-only strong relation has no equal GROUP_NODE proof source.
        }
        return canonicalPath(nodes, chain.getStrongRelations(), chain.isClosed());
    }

    static boolean matchesCompletePath(String authoredPath, SolutionStep step) {
        if (authoredPath == null || step == null || step.getChains().size() != 1) return false;
        Chain chain = step.getChains().get(0);
        if (chain == null || chain.getChain() == null || chain.getStart() < 0
                || chain.getEnd() <= chain.getStart() || chain.getEnd() >= chain.getChain().length) return false;
        List<String> nodes = new ArrayList<String>();
        for (int i = chain.getStart(); i <= chain.getEnd(); i++) {
            int entry = chain.getChain()[i];
            if (entry <= 0) return false; // negative entries encode branches
            int digit = Chain.getSCandidate(entry), first = Chain.getSCellIndex(entry);
            int kind = Chain.getSNodeType(entry);
            if (!validCandidate(first, digit)) return false;
            int[] atoms;
            if (kind == Chain.NORMAL_NODE) atoms = new int[]{first * 10 + digit};
            else if (kind == Chain.GROUP_NODE) {
                int second = Chain.getSCellIndex2(entry), third = Chain.getSCellIndex3(entry);
                if (!validCandidate(second, digit)) return false;
                atoms = third < 0 ? new int[]{first * 10 + digit, second * 10 + digit}
                        : new int[]{first * 10 + digit, second * 10 + digit, third * 10 + digit};
                if (third >= 0 && !validCandidate(third, digit)) return false;
                Arrays.sort(atoms);
                if (atoms[0] == atoms[1] || atoms.length == 3 && atoms[1] == atoms[2]) return false;
            } else return false; // ALS nodes require a separate proof representation.
            nodes.add(Arrays.toString(atoms));
        }
        boolean repeatedStart = nodes.get(0).equals(nodes.get(nodes.size() - 1));
        SolutionType type = step.getType();
        boolean loopType = type == SolutionType.CONTINUOUS_NICE_LOOP
                || type == SolutionType.DISCONTINUOUS_NICE_LOOP
                || type == SolutionType.GROUPED_CONTINUOUS_NICE_LOOP
                || type == SolutionType.GROUPED_DISCONTINUOUS_NICE_LOOP;
        boolean closed = repeatedStart || loopType;
        if (repeatedStart) nodes.remove(nodes.size() - 1);
        if (nodes.size() < (closed ? 3 : 2) || new java.util.HashSet<String>(nodes).size() != nodes.size()) return false;
        List<Boolean> relations = new ArrayList<Boolean>();
        for (int i = chain.getStart() + 1; i <= chain.getEnd(); i++) relations.add(chain.isStrong(i));
        if (closed && !repeatedStart) relations.add(chain.isStrong(chain.getStart()));
        return authoredPath.equals(canonicalPath(nodes, relations, closed));
    }

    private static String canonicalPath(List<String> nodes, List<Boolean> links, boolean closed) {
        int size = nodes.size(), expected = closed ? size : size - 1;
        if (links.size() != expected) return null;
        String best = null;
        for (int direction = 0; direction < 2; direction++) {
            for (int offset = 0; offset < (closed ? size : 1); offset++) {
                StringBuilder key = new StringBuilder(closed ? "C|" : "O|");
                for (int edge = 0; edge < expected; edge++) {
                    int from = closed ? Math.floorMod(offset + (direction == 0 ? edge : -edge), size)
                            : (direction == 0 ? edge : size - 1 - edge);
                    int to = closed ? Math.floorMod(offset + (direction == 0 ? edge + 1 : -edge - 1), size)
                            : (direction == 0 ? edge + 1 : size - 2 - edge);
                    int link = direction == 0 ? (closed ? from : edge)
                            : (closed ? to : size - 2 - edge);
                    key.append(nodes.get(from)).append(links.get(link) ? '=' : '-')
                            .append(nodes.get(to)).append(';');
                }
                String value = key.toString();
                if (best == null || value.compareTo(best) < 0) best = value;
            }
        }
        return best;
    }

	static boolean sameAuthoredChain(UserChain first, UserChain second) {
		if (first == second) return true;
		if (first == null || second == null || first.isClosed() != second.isClosed()
				|| first.isActive() != second.isActive()) return false;
		List<UserChainNode> firstNodes = first.getNodes();
		List<UserChainNode> secondNodes = second.getNodes();
		List<Boolean> firstRelations = first.getStrongRelations();
		List<Boolean> secondRelations = second.getStrongRelations();
		if (firstNodes == null || secondNodes == null || firstRelations == null || secondRelations == null
				|| firstNodes.size() != secondNodes.size() || !firstRelations.equals(secondRelations)) return false;
		for (int i = 0; i < firstNodes.size(); i++) {
			UserChainNode left = firstNodes.get(i);
			UserChainNode right = secondNodes.get(i);
			if (left == null || right == null || !left.key().equals(right.key())) return false;
		}
		return true;
	}

	private static BoxProof normalizeNativeBoxProof(SolutionStep step) {
		if (step == null) return null;
		int expected;
		switch (step.getType()) {
		case NAKED_PAIR:
		case HIDDEN_PAIR:
		case LOCKED_PAIR:
			expected = 2;
			break;
		case NAKED_TRIPLE:
		case HIDDEN_TRIPLE:
		case LOCKED_TRIPLE:
			expected = 3;
			break;
		case NAKED_QUADRUPLE:
		case HIDDEN_QUADRUPLE:
			expected = 4;
			break;
		case LOCKED_CANDIDATES_1:
		case LOCKED_CANDIDATES_2:
			expected = step.getIndices().size();
			if (expected < 2 || expected > 3 || step.getValues().size() != 1) return null;
			break;
		default:
			return null;
		}
		if (step.getIndices().size() != expected) return null;
		if (step.getType() != SolutionType.LOCKED_CANDIDATES_1
				&& step.getType() != SolutionType.LOCKED_CANDIDATES_2
				&& step.getValues().size() != expected) return null;
		List<Integer> cells = new ArrayList<Integer>(step.getIndices());
		Collections.sort(cells);
		for (int i = 0; i < cells.size(); i++) {
			int index = cells.get(i).intValue();
			if (index < 0 || index >= 81 || (i > 0 && cells.get(i - 1).intValue() == index)) return null;
		}
		return new BoxProof(cells);
	}

	private static boolean hasExecutableDeletion(SolutionStep step) {
		return normalizedDeletions(step) != null;
	}

	private static int configuredPriority(SolutionStep step) {
		if (step == null || step.getType() == null) return Integer.MAX_VALUE;
		StepConfig config = step.getType().getStepConfig();
		return config == null ? Integer.MAX_VALUE : config.getIndex();
	}

	static NativeStepKey keyForStep(SolutionStep step) {
        if(step != null && (step.isAuthoredPlacement() || !step.getGeneralizedProofs().isEmpty()))return new NativeStepKey("SET|"+ReasoningStepIndex.identity(step));
		Proof proof = normalizeNativeBoxProof(step);
		if (proof == null) proof = normalizeNativeChainProof(step);
		if (proof == null) return new NativeStepKey(ReasoningStepIndex.identity(step));
		List<Integer> deletions = normalizedDeletions(step);
		if (deletions == null) return null;
		StringBuilder value = new StringBuilder();
		value.append(step.getType().name()).append('|');
		value.append(step.getSubType() == null ? "-" : step.getSubType().name()).append('|');
		value.append(proof.stableValue()).append('|').append(deletions).append('|');
		value.append(step.getEntity()).append(':').append(step.getEntityNumber()).append(':')
				.append(step.getEntity2()).append(':').append(step.getEntity2Number()).append(':')
				.append(step.isIsSiamese()).append('|');
		value.append(sortedIntegers(step.getValues())).append('|')
				.append(sortedIntegers(step.getIndices()));
		return new NativeStepKey(value.toString());
	}

	private static ChainProof normalizeUserChain(UserChain chain, Sudoku2 board) {
		if (chain == null || board == null || chain.isActive()) return null;
		List<UserChainNode> sourceNodes = chain.getNodes();
		int requiredRelations = sourceNodes == null ? -1
				: sourceNodes.size() - (chain.isClosed() ? 0 : 1);
		if (sourceNodes == null || sourceNodes.size() < (chain.isClosed() ? 3 : 2)
				|| chain.getStrongRelations() == null
				|| chain.getStrongRelations().size() != requiredRelations) return null;
		List<CandidateNode> nodes = new ArrayList<CandidateNode>(sourceNodes.size());
		Set<Integer> seen = new TreeSet<Integer>();
		for (UserChainNode source : sourceNodes) {
			if (source == null || source.grouped() || !validCandidate(source.getCellIndex(), source.getCandidate())
					|| !board.isCandidate(source.getCellIndex(), source.getCandidate())) return null;
			CandidateNode node = new CandidateNode(source.getCellIndex(), source.getCandidate());
			if (!seen.add(Integer.valueOf(node.id()))) return null;
			nodes.add(node);
		}
		List<ProofEdge> edges = new ArrayList<ProofEdge>(nodes.size() - 1);
		for (int i = 1; i < nodes.size(); i++) {
			Boolean relation = chain.getStrongRelations().get(i - 1);
			if (relation == null) return null;
			edges.add(new ProofEdge(nodes.get(i - 1), nodes.get(i), relation.booleanValue()));
		}
		if (chain.isClosed()) {
			Boolean closing = chain.getStrongRelations().get(nodes.size() - 1);
			if (closing == null) return null;
			edges.add(new ProofEdge(nodes.get(nodes.size() - 1), nodes.get(0), closing.booleanValue()));
			return canonicalClosedProof(edges);
		}
		return canonicalOpenProof(edges);
	}

	private static ChainProof normalizeNativeChainProof(SolutionStep step) {
		if (step == null || step.getChains().size() != 1) return null;
		boolean nativeLoop = step.getType() == SolutionType.CONTINUOUS_NICE_LOOP
				|| step.getType() == SolutionType.DISCONTINUOUS_NICE_LOOP;
		boolean xChain = step.getType() == SolutionType.X_CHAIN;
		if (!nativeLoop && !xChain && step.getType() != SolutionType.AIC) return null;
		Chain chain = step.getChains().get(0);
		if (chain == null || chain.getChain() == null || chain.getStart() < 0
				|| chain.getEnd() <= chain.getStart() || chain.getEnd() >= chain.getChain().length) return null;
		List<CandidateNode> nodes = new ArrayList<CandidateNode>();
		for (int i = chain.getStart(); i <= chain.getEnd(); i++) {
			int entry = chain.getChain()[i];
			if (entry <= 0 || Chain.getSNodeType(entry) != Chain.NORMAL_NODE) return null;
			CandidateNode node = new CandidateNode(Chain.getSCellIndex(entry), Chain.getSCandidate(entry));
			if (!validCandidate(node.cell, node.candidate)) return null;
			nodes.add(node);
		}
		CandidateNode first = nodes.get(0);
		CandidateNode last = nodes.get(nodes.size() - 1);
		if (xChain) {
			for (CandidateNode node : nodes) {
				if (node.candidate != first.candidate) return null;
			}
		}
		boolean repeatedStart = first.equals(last);
		boolean closed = nativeLoop || (xChain && repeatedStart);
		if (closed) {
			if (nativeLoop && first.cell != last.cell) return null;
			List<CandidateNode> uniqueNodes = repeatedStart
					? new ArrayList<CandidateNode>(nodes.subList(0, nodes.size() - 1)) : nodes;
			if (uniqueNodes.size() < 3 || hasDuplicateNode(uniqueNodes)) return null;
			List<ProofEdge> edges = new ArrayList<ProofEdge>(uniqueNodes.size());
			for (int i = 1; i < nodes.size(); i++) {
				edges.add(new ProofEdge(nodes.get(i - 1), nodes.get(i),
						chain.isStrong(chain.getStart() + i)));
			}
			if (!repeatedStart) {
				edges.add(new ProofEdge(last, first, chain.isStrong(chain.getStart())));
			}
			return canonicalClosedProof(edges);
		}
		if (first.cell == last.cell || hasDuplicateNode(nodes)) return null;
		List<ProofEdge> edges = new ArrayList<ProofEdge>(nodes.size() - 1);
		for (int i = 1; i < nodes.size(); i++) {
			edges.add(new ProofEdge(nodes.get(i - 1), nodes.get(i), chain.isStrong(chain.getStart() + i)));
		}
		return canonicalOpenProof(edges);
	}

	private static ChainProof canonicalOpenProof(List<ProofEdge> edges) {
		List<ProofEdge> reversed = new ArrayList<ProofEdge>(edges.size());
		for (int i = edges.size() - 1; i >= 0; i--) reversed.add(edges.get(i).reversed());
		return new ChainProof(false, compareEdgeLists(edges, reversed) <= 0 ? edges : reversed);
	}

	private static ChainProof canonicalClosedProof(List<ProofEdge> edges) {
		List<ProofEdge> reversed = new ArrayList<ProofEdge>(edges.size());
		for (int i = edges.size() - 1; i >= 0; i--) reversed.add(edges.get(i).reversed());
		List<ProofEdge> best = null;
		for (int direction = 0; direction < 2; direction++) {
			List<ProofEdge> source = direction == 0 ? edges : reversed;
			for (int offset = 0; offset < source.size(); offset++) {
				List<ProofEdge> rotated = new ArrayList<ProofEdge>(source.size());
				for (int i = 0; i < source.size(); i++) {
					rotated.add(source.get((offset + i) % source.size()));
				}
				if (best == null || compareEdgeLists(rotated, best) < 0) best = rotated;
			}
		}
		return new ChainProof(true, best);
	}

	private static boolean hasDuplicateNode(List<CandidateNode> nodes) {
		Set<Integer> seen = new TreeSet<Integer>();
		for (CandidateNode node : nodes) {
			if (!seen.add(Integer.valueOf(node.id()))) return true;
		}
		return false;
	}

	private static int compareEdgeLists(List<ProofEdge> first, List<ProofEdge> second) {
		int size = Math.min(first.size(), second.size());
		for (int i = 0; i < size; i++) {
			int result = first.get(i).compareTo(second.get(i));
			if (result != 0) return result;
		}
		return first.size() - second.size();
	}

	private static boolean validCandidate(int cell, int candidate) {
		return cell >= 0 && cell < 81 && candidate >= 1 && candidate <= 9;
	}

	private static List<Integer> normalizedDeletions(SolutionStep step) {
		if (step == null || step.getCandidatesToDelete().isEmpty()) return null;
		Set<Integer> values = new TreeSet<Integer>();
		for (Candidate candidate : step.getCandidatesToDelete()) {
			if (candidate == null || candidate.getIndex() < 0 || candidate.getIndex() >= 81
					|| candidate.getValue() < 1 || candidate.getValue() > 9) return null;
			values.add(Integer.valueOf(candidate.getIndex() * 10 + candidate.getValue()));
		}
		return new ArrayList<Integer>(values);
	}

	private static List<Integer> sortedIntegers(List<Integer> source) {
		List<Integer> copy = new ArrayList<Integer>(source);
		Collections.sort(copy);
		return copy;
	}
}
