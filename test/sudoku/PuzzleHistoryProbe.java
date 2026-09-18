/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Focused regression checks for backwards-compatible puzzle history storage. */
public final class PuzzleHistoryProbe {

	private static final String CLUES_A =
			"530070000600195000098000060800060003400803001700020006060000280000419005000080079";
	private static final String CLUES_B =
			"000260701680070090190004500820100040004602900050003028009300074040050036703018000";
	private static final String SOLUTION_A =
			"534678912672195348198342567859761423426853791713924856961537284287419635345286179";

	private PuzzleHistoryProbe() {
	}

	public static void main(String[] args) {
		parseLegacyEntry();
		encodeAndOpenCompletedEntry();
		upsertMovesAndUpdatesEntry();
		unratedPuzzleUsesIncompleteLevel();
		completionUsesOneRow();
		startingAgainPreservesLatestCompletion();
		historyLimitIsEnforced();
		malformedLegacyRowsDoNotBreakTheView();
		System.out.println("Puzzle history checks passed");
	}

	private static void parseLegacyEntry() {
		String dotted = CLUES_A.replace('0', '.');
		PuzzleHistoryEntry entry = PuzzleHistoryEntry.parse(dotted + "#2#234#123456789");
		require(CLUES_A.equals(entry.getClues()), "legacy clues were not normalized");
		require(entry.getLevel() == 2, "legacy difficulty was not read");
		require(entry.getScore() == 234, "legacy score was not read");
		require(entry.getLastStartedAt() == 123456789L, "legacy timestamp was not read");
		require(!entry.isCompleted(), "legacy row must default to in-progress");
		require(entry.getFinalGrid() == null, "legacy row unexpectedly has a final grid");
	}

	private static void encodeAndOpenCompletedEntry() {
		PuzzleHistoryEntry entry = PuzzleHistoryEntry.completed(
				CLUES_A, 2, 234, 1000L, SOLUTION_A, 2000L);
		PuzzleHistoryEntry decoded = PuzzleHistoryEntry.parse(entry.encode());
		require(decoded.isCompleted(), "completed status was not encoded");
		require(SOLUTION_A.equals(decoded.getFinalGrid()), "final grid was not encoded");
		require(decoded.getCompletedAt() == 2000L, "completion timestamp was not encoded");

		String openPuzzle = decoded.getPuzzleForOpen();
		require(openPuzzle.startsWith(":0000:x:"), "completed row must open in library format");
		require(openPuzzle.endsWith(":::"), "library row suffix is malformed");

		Options.instance = new Options();
		Sudoku2 opened = new Sudoku2();
		opened.setSudoku(openPuzzle);
		require(CLUES_A.equals(PuzzleHistoryEntry.normalizeClues(
				opened.getSudoku(ClipboardMode.CLUES_ONLY))),
				"completed row lost original givens when reopened");
		require(SOLUTION_A.equals(PuzzleHistoryEntry.normalizeClues(
				opened.getSudoku(ClipboardMode.VALUES_ONLY))),
				"completed row lost final values when reopened");
	}

	private static void upsertMovesAndUpdatesEntry() {
		Options options = new Options();
		options.setHistoryOfCreatedPuzzles(new ArrayList<String>(Arrays.asList(
				CLUES_A + "#1#100#10",
				CLUES_B + "#2#200#20")));

		options.recordPuzzleStarted(CLUES_A.replace('0', '.'), 3, 333, 30L);
		List<PuzzleHistoryEntry> entries = options.getPuzzleHistoryEntries();
		require(entries.size() == 2, "same clues created a duplicate history row");
		require(CLUES_A.equals(entries.get(0).getClues()), "updated row was not moved to the top");
		require(entries.get(0).getLevel() == 3, "difficulty was not updated");
		require(entries.get(0).getScore() == 333, "score was not updated");
		require(entries.get(0).getLastStartedAt() == 30L, "start time was not updated");
	}

	private static void unratedPuzzleUsesIncompleteLevel() {
		Options options = new Options();
		Sudoku2 puzzle = new Sudoku2();
		puzzle.setSudoku(CLUES_A);
		options.addSudokuToHistory(puzzle);
		PuzzleHistoryEntry entry = options.getPuzzleHistoryEntries().get(0);
		require(entry.getLevel() == DifficultyType.INCOMPLETE.ordinal(),
				"unrated puzzle was not retained as incomplete history");
	}

	private static void completionUsesOneRow() {
		Options options = new Options();
		options.recordPuzzleStarted(CLUES_A, 2, 234, 1000L);
		options.recordPuzzleCompleted(CLUES_A, 2, 234, SOLUTION_A, 2000L);
		options.recordPuzzleCompleted(CLUES_A, 2, 235, SOLUTION_A, 3000L);

		List<PuzzleHistoryEntry> entries = options.getPuzzleHistoryEntries();
		require(entries.size() == 1, "completing one puzzle created extra rows");
		require(entries.get(0).isCompleted(), "completion did not update row status");
		require(entries.get(0).getLastStartedAt() == 1000L, "completion replaced the start time");
		require(entries.get(0).getCompletedAt() == 3000L, "latest completion was not retained");
		require(entries.get(0).getScore() == 235, "latest completion metadata was not retained");
	}

	private static void startingAgainPreservesLatestCompletion() {
		Options options = new Options();
		options.recordPuzzleStarted(CLUES_A, 2, 234, 1000L);
		options.recordPuzzleCompleted(CLUES_A, 2, 234, SOLUTION_A, 2000L);
		options.recordPuzzleStarted(CLUES_A, 3, 300, 3000L);

		PuzzleHistoryEntry entry = options.getPuzzleHistoryEntries().get(0);
		require(entry.isCompleted(), "starting the same puzzle erased its completed status");
		require(SOLUTION_A.equals(entry.getFinalGrid()), "starting again erased the latest completion snapshot");
		require(entry.getCompletedAt() == 2000L, "starting again changed the completion time");
		require(entry.getLastStartedAt() == 3000L, "starting again did not refresh the start time");
		require(entry.getLevel() == 3 && entry.getScore() == 300,
				"starting again did not refresh difficulty metadata");
	}

	private static void historyLimitIsEnforced() {
		Options options = new Options();
		options.setHistorySize(1);
		options.recordPuzzleStarted(CLUES_A, 1, 100, 10L);
		options.recordPuzzleStarted(CLUES_B, 2, 200, 20L);
		require(options.getPuzzleHistoryEntries().size() == 1, "history limit was not enforced");
		require(CLUES_B.equals(options.getPuzzleHistoryEntries().get(0).getClues()),
				"history did not retain the newest row");
	}

	private static void malformedLegacyRowsDoNotBreakTheView() {
		Options options = new Options();
		options.setHistoryOfCreatedPuzzles(new ArrayList<String>(Arrays.asList(
				"broken",
				CLUES_A + "#2#234#1000")));
		require(options.getPuzzleHistoryEntries().size() == 1,
				"malformed XML history row was not safely ignored");
		require(options.getHistoryOfCreatedPuzzles().size() == 2,
				"reading the typed view must not destructively rewrite the XML property");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
