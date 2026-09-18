/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

/** Behavioral checks for exact native reasoning proof matching. */
public final class NativeReasoningMatcherProbe {
	private NativeReasoningMatcherProbe() {
	}

	public static void main(String[] args) {
		verifyExactBoxPremiseMatching();
		verifyBoxWhitelistAndStableOrder();
		verifyStableNativeStepKey();
		verifyOpenChainDirectionAndReversal();
		verifyClosedChainRotationsAndClosingRelations();
		verifyXChainAndRejectLossyNativeChains();
		verifyAuthoredChainSemanticIdentity();
		System.out.println("Native reasoning matcher checks passed");
	}

	private static void verifyAuthoredChainSemanticIdentity() {
		UserChain first = userChain(false,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 } },
				new boolean[] { true, false });
		first.getNodes().get(0).setColor(Color.RED);
		UserChain recolored = userChain(false,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 } },
				new boolean[] { true, false });
		recolored.getNodes().get(0).setColor(Color.BLUE);
		require(NativeReasoningMatcher.sameAuthoredChain(first, recolored),
				"draw-time chain color leaked into proof-source identity");
		UserChain changed = userChain(false,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 } },
				new boolean[] { false, false });
		require(!NativeReasoningMatcher.sameAuthoredChain(first, changed),
				"changed relation retained authored proof-source identity");
	}

	private static void verifyXChainAndRejectLossyNativeChains() {
		SolutionStep openX = chainStep(SolutionType.X_CHAIN,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 1 }, { 27, 1 } },
				new boolean[] { false, true, false, true }, 36, 1);
		UserChain openAuthored = userChain(false,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 1 }, { 27, 1 } },
				new boolean[] { true, false, true });
		require(NativeReasoningMatcher.matchChain(
				Arrays.asList(openX), emptyBoard(), openAuthored) != null,
				"ordinary native X-Chain was not supported");

		SolutionStep closedX = chainStep(SolutionType.X_CHAIN,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 1 }, { 0, 1 } },
				new boolean[] { false, true, false, true }, 27, 1);
		UserChain closedAuthored = userChain(true,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 1 } },
				new boolean[] { true, false, true });
		require(NativeReasoningMatcher.matchChain(
				Arrays.asList(closedX), emptyBoard(), closedAuthored) != null,
				"native repeated-start X-Chain loop was not represented losslessly");

		int a = Chain.makeSEntry(0, 1, false);
		int b = Chain.makeSEntry(9, 1, true);
		int c = Chain.makeSEntry(18, 2, false);
		SolutionStep branched = rawChainStep(SolutionType.AIC,
				new int[] { a, -b, c }, 27, 2);
		SolutionStep grouped = rawChainStep(SolutionType.AIC,
				new int[] { a, Chain.makeSEntry(9, 1, true, Chain.GROUP_NODE), c }, 27, 2);
		SolutionStep als = rawChainStep(SolutionType.AIC,
				new int[] { a, Chain.makeSEntry(9, 0, 1, true, Chain.ALS_NODE), c }, 27, 2);
		SolutionStep marker = rawChainStep(SolutionType.AIC,
				new int[] { a, Integer.MIN_VALUE, c }, 27, 2);
		SolutionStep multiple = chainStep(SolutionType.AIC,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 } },
				new boolean[] { false, true, false }, 27, 2);
		multiple.addChain(0, 1, new int[] { a, b });
		SolutionStep unsupported = chainStep(SolutionType.XY_CHAIN,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 } },
				new boolean[] { false, true, false }, 27, 2);
		List<SolutionStep> rejected = Arrays.asList(branched, grouped, als, marker, multiple, unsupported);
		for (SolutionStep step : rejected) {
			require(NativeReasoningMatcher.matchChain(
					Arrays.asList(step), emptyBoard(), userChain(false,
							new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 } },
							new boolean[] { true, false })) == null,
					"lossy or unsupported native chain was accepted: " + step.getType().name());
		}

		UserChain extraNode = userChain(false,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 }, { 27, 2 } },
				new boolean[] { true, false, true });
		require(NativeReasoningMatcher.matchChain(
				Arrays.asList(chainStep(SolutionType.AIC,
						new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 } },
						new boolean[] { false, true, false }, 27, 2)),
				emptyBoard(), extraNode) == null,
				"extra authored node/edge produced an approximate match");
	}

	private static void verifyClosedChainRotationsAndClosingRelations() {
		SolutionStep repeatedStart = chainStep(SolutionType.DISCONTINUOUS_NICE_LOOP,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 1 }, { 0, 1 } },
				new boolean[] { true, false, true, false }, 27, 1);
		UserChain authored = userChain(true,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 1 } },
				new boolean[] { false, true, false });
		require(NativeReasoningMatcher.matchChain(
				Arrays.asList(repeatedStart), emptyBoard(), authored) != null,
				"native repeated-start loop did not match its explicit closing relation");

		UserChain rotated = userChain(true,
				new int[][] { { 9, 1 }, { 18, 1 }, { 0, 1 } },
				new boolean[] { true, false, false });
		require(NativeReasoningMatcher.matchChain(
				Arrays.asList(repeatedStart), emptyBoard(), rotated) != null,
				"closed loop rotation did not normalize to the same proof");

		SolutionStep implicitClosing = chainStep(SolutionType.CONTINUOUS_NICE_LOOP,
				new int[][] { { 0, 1 }, { 9, 1 }, { 9, 2 }, { 0, 2 } },
				new boolean[] { false, true, false, true }, 18, 2);
		UserChain implicitAuthored = userChain(true,
				new int[][] { { 0, 1 }, { 9, 1 }, { 9, 2 }, { 0, 2 } },
				new boolean[] { true, false, true, false });
		require(NativeReasoningMatcher.matchChain(
				Arrays.asList(implicitClosing), emptyBoard(), implicitAuthored) != null,
				"same-cell/different-candidate Nice Loop lost its first-entry closing relation");

		UserChain wrongClosing = userChain(true,
				new int[][] { { 0, 1 }, { 9, 1 }, { 9, 2 }, { 0, 2 } },
				new boolean[] { true, false, true, true });
		require(NativeReasoningMatcher.matchChain(
				Arrays.asList(implicitClosing), emptyBoard(), wrongClosing) == null,
				"wrong closing relation produced a loop match");
	}

	private static void verifyOpenChainDirectionAndReversal() {
		SolutionStep nativeStep = chainStep(SolutionType.AIC,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 } },
				new boolean[] { false, true, false }, 27, 2);
		UserChain forward = userChain(false,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 } },
				new boolean[] { true, false });
		NativeReasoningMatcher.Match forwardMatch = NativeReasoningMatcher.matchChain(
				Arrays.asList(nativeStep), emptyBoard(), forward);
		require(forwardMatch != null && forwardMatch.getStep() == nativeStep,
				"ordered open candidate chain did not match its native AIC");

		UserChain reversed = userChain(false,
				new int[][] { { 18, 2 }, { 9, 1 }, { 0, 1 } },
				new boolean[] { false, true });
		require(NativeReasoningMatcher.matchChain(Arrays.asList(nativeStep), emptyBoard(), reversed) != null,
				"full open-chain reversal did not preserve relation ownership");

		UserChain wrongRelation = userChain(false,
				new int[][] { { 0, 1 }, { 9, 1 }, { 18, 2 } },
				new boolean[] { false, false });
		require(NativeReasoningMatcher.matchChain(
				Arrays.asList(nativeStep), emptyBoard(), wrongRelation) == null,
				"wrong Strong/Weak relation produced a chain match");
	}

	private static void verifyStableNativeStepKey() {
		SolutionStep step = subsetStep(SolutionType.NAKED_PAIR,
				new int[] { 0, 1 }, new int[] { 1, 2 }, 8, 1);
		NativeReasoningMatcher.NativeStepKey key = NativeReasoningMatcher.keyForStep(step);
		require(key != null && key.equals(NativeReasoningMatcher.keyForStep((SolutionStep) step.clone())),
				"native-step key did not survive SolutionStep cloning");

		SolutionStep differentConclusion = subsetStep(SolutionType.NAKED_PAIR,
				new int[] { 0, 1 }, new int[] { 1, 2 }, 8, 2);
		require(!key.equals(NativeReasoningMatcher.keyForStep(differentConclusion)),
				"same-type steps with different conclusions shared a key");

		SolutionStep differentEntity = subsetStep(SolutionType.NAKED_PAIR,
				new int[] { 0, 1 }, new int[] { 1, 2 }, 8, 1);
		differentEntity.setEntity(Sudoku2.ROW);
		differentEntity.setEntityNumber(4);
		require(!key.equals(NativeReasoningMatcher.keyForStep(differentEntity)),
				"execution-relevant entity metadata was absent from the key");

		NativeReasoningMatcher.Match match = NativeReasoningMatcher.matchBox(
				Arrays.asList(step), emptyBoard(), cells(0, 1));
		require(match != null && key.equals(match.getKey()),
				"exact match did not carry its stable native-step key");
	}

	private static void verifyExactBoxPremiseMatching() {
		Sudoku2 board = emptyBoard();
		SudokuSet selected = cells(0, 1);
		NativeReasoningMatcher.BoxProof proof =
				NativeReasoningMatcher.normalizeBoxSelection(selected, board);
		require(proof != null, "valid Box selection did not normalize");

		SolutionStep missing = subsetStep(SolutionType.NAKED_PAIR,
				new int[] { 0, 2 }, new int[] { 1, 2 }, 3, 1);
		SolutionStep exact = subsetStep(SolutionType.NAKED_PAIR,
				new int[] { 0, 1 }, new int[] { 1, 2 }, 3, 1);
		SolutionStep extra = subsetStep(SolutionType.NAKED_TRIPLE,
				new int[] { 0, 1, 2 }, new int[] { 1, 2, 3 }, 3, 1);
		List<SolutionStep> catalog = Arrays.asList(missing, exact, extra);
		NativeReasoningMatcher.Match match =
				NativeReasoningMatcher.findBoxMatch(catalog, proof);
		require(match != null && match.getStep() == exact,
				"Box selection did not require the exact complete premise-cell set");
	}

	private static void verifyBoxWhitelistAndStableOrder() {
		Sudoku2 board = emptyBoard();
		SolutionType[] types = {
			SolutionType.NAKED_PAIR, SolutionType.NAKED_TRIPLE, SolutionType.NAKED_QUADRUPLE,
			SolutionType.HIDDEN_PAIR, SolutionType.HIDDEN_TRIPLE, SolutionType.HIDDEN_QUADRUPLE,
			SolutionType.LOCKED_PAIR, SolutionType.LOCKED_TRIPLE,
			SolutionType.LOCKED_CANDIDATES_1, SolutionType.LOCKED_CANDIDATES_2
		};
		int[] sizes = { 2, 3, 4, 2, 3, 4, 2, 3, 2, 3 };
		for (int i = 0; i < types.length; i++) {
			int[] indices = sequence(sizes[i]);
			int[] values = types[i] == SolutionType.LOCKED_CANDIDATES_1
					|| types[i] == SolutionType.LOCKED_CANDIDATES_2
					? new int[] { 1 } : sequenceFromOne(sizes[i]);
			SolutionStep step = subsetStep(types[i], indices, values, 8, 1);
			NativeReasoningMatcher.Match match = NativeReasoningMatcher.matchBox(
					Arrays.asList(step), board, cells(indices));
			require(match != null && match.getStep() == step,
					"supported Box family was rejected: " + types[i].name());
		}

		SolutionStep unsupported = subsetStep(SolutionType.X_WING,
				new int[] { 0, 1 }, new int[] { 1, 2 }, 8, 1);
		require(NativeReasoningMatcher.matchBox(Arrays.asList(unsupported), board, cells(0, 1)) == null,
				"unsupported candidate-role proof matched a Box footprint");
		Sudoku2 filled = emptyBoard();
		filled.setCell(0, 9);
		require(NativeReasoningMatcher.matchBox(Arrays.asList(
				subsetStep(SolutionType.NAKED_PAIR, new int[] { 0, 1 }, new int[] { 1, 2 }, 8, 1)),
				filled, cells(0, 1)) == null, "filled Box cell was accepted as a premise");

		SolutionStep hidden = subsetStep(SolutionType.HIDDEN_PAIR,
				new int[] { 0, 1 }, new int[] { 1, 2 }, 8, 1);
		SolutionStep naked = subsetStep(SolutionType.NAKED_PAIR,
				new int[] { 0, 1 }, new int[] { 1, 2 }, 8, 1);
		StepConfig hiddenConfig = SolutionType.HIDDEN_PAIR.getStepConfig();
		StepConfig nakedConfig = SolutionType.NAKED_PAIR.getStepConfig();
		int oldHiddenIndex = hiddenConfig.getIndex();
		int oldNakedIndex = nakedConfig.getIndex();
		try {
			hiddenConfig.setIndex(200);
			nakedConfig.setIndex(100);
			NativeReasoningMatcher.Match priority = NativeReasoningMatcher.matchBox(
					Arrays.asList(hidden, naked), board, cells(0, 1));
			require(priority != null && priority.getStep() == naked,
					"configured solver priority did not precede raw catalog order");
			hiddenConfig.setIndex(100);
			NativeReasoningMatcher.Match tied = NativeReasoningMatcher.matchBox(
					Arrays.asList(hidden, naked), board, cells(0, 1));
			require(tied != null && tied.getStep() == hidden,
					"stable raw catalog order did not break an exact priority tie");
		} finally {
			hiddenConfig.setIndex(oldHiddenIndex);
			nakedConfig.setIndex(oldNakedIndex);
		}
	}

	private static SolutionStep subsetStep(SolutionType type, int[] indices,
			int[] values, int deleteIndex, int deleteCandidate) {
		SolutionStep step = new SolutionStep(type);
		for (int index : indices) step.addIndex(index);
		for (int value : values) step.addValue(value);
		step.addCandidateToDelete(deleteIndex, deleteCandidate);
		return step;
	}

	private static SolutionStep chainStep(SolutionType type, int[][] nodes,
			boolean[] entryStrong, int deleteIndex, int deleteCandidate) {
		if (nodes.length != entryStrong.length) throw new IllegalArgumentException();
		int[] entries = new int[nodes.length];
		for (int i = 0; i < nodes.length; i++) {
			entries[i] = Chain.makeSEntry(nodes[i][0], nodes[i][1], entryStrong[i]);
		}
		SolutionStep step = new SolutionStep(type);
		step.addChain(0, entries.length - 1, entries);
		step.addCandidateToDelete(deleteIndex, deleteCandidate);
		return step;
	}

	private static SolutionStep rawChainStep(SolutionType type, int[] entries,
			int deleteIndex, int deleteCandidate) {
		SolutionStep step = new SolutionStep(type);
		step.addChain(0, entries.length - 1, entries);
		step.addCandidateToDelete(deleteIndex, deleteCandidate);
		return step;
	}

	private static UserChain userChain(boolean closed, int[][] nodes, boolean[] relations) {
		UserChain chain = new UserChain();
		chain.setClosed(closed);
		for (int[] node : nodes) chain.getNodes().add(new UserChainNode(node[0], node[1]));
		for (boolean relation : relations) chain.getStrongRelations().add(Boolean.valueOf(relation));
		return chain;
	}

	private static SudokuSet cells(int... indices) {
		SudokuSet set = new SudokuSet();
		for (int index : indices) set.add(index);
		return set;
	}

	private static int[] sequence(int size) {
		int[] values = new int[size];
		for (int i = 0; i < size; i++) values[i] = i;
		return values;
	}

	private static int[] sequenceFromOne(int size) {
		int[] values = new int[size];
		for (int i = 0; i < size; i++) values[i] = i + 1;
		return values;
	}

	private static Sudoku2 emptyBoard() {
		Sudoku2 board = new Sudoku2();
		board.setSudoku("000000000000000000000000000000000000000000000000000000000000000000000000000000000");
		return board;
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
