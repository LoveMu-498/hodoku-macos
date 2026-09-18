/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.util.ArrayList;
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
		int bestPriority = Integer.MAX_VALUE;
		for (SolutionStep step : catalog) {
			BoxProof nativeProof = normalizeNativeBoxProof(step);
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
			if (left == null || right == null || left.identity() != right.identity()) return false;
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
        if(step != null && step.isAuthoredPlacement())return new NativeStepKey("SET|"+ReasoningStepIndex.identity(step));
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
