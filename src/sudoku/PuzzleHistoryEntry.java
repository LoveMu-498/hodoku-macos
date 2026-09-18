/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

/**
 * Typed view of one entry in HoDoKu's string-based puzzle history property.
 *
 * <p>The underlying {@code List<String>} remains in {@link Options} so existing
 * XML configuration files stay compatible. This class owns parsing, validation
 * and the extended on-disk representation.</p>
 */
public final class PuzzleHistoryEntry {

	private static final String IN_PROGRESS_CODE = "I";
	private static final String COMPLETED_CODE = "C";

	/** State of the most recent attempt stored in this history row. */
	public enum Status {
		IN_PROGRESS,
		COMPLETED
	}

	private final String clues;
	private final int level;
	private final int score;
	private final long lastStartedAt;
	private final Status status;
	private final String finalGrid;
	private final long completedAt;

	private PuzzleHistoryEntry(String clues, int level, int score, long lastStartedAt,
			Status status, String finalGrid, long completedAt) {
		this.clues = normalizeGrid(clues, false);
		if (level < 0) {
			throw new IllegalArgumentException("Difficulty level must not be negative");
		}
		if (lastStartedAt < 0 || completedAt < 0) {
			throw new IllegalArgumentException("History timestamps must not be negative");
		}
		if (status == null) {
			throw new IllegalArgumentException("History status is missing");
		}

		String normalizedFinal = null;
		if (status == Status.COMPLETED) {
			normalizedFinal = normalizeGrid(finalGrid, true);
			validateFinalGrid(this.clues, normalizedFinal);
		} else if (finalGrid != null && finalGrid.length() != 0) {
			throw new IllegalArgumentException("An in-progress row cannot contain a final grid");
		}

		this.level = level;
		this.score = score;
		this.lastStartedAt = lastStartedAt;
		this.status = status;
		this.finalGrid = normalizedFinal;
		this.completedAt = status == Status.COMPLETED ? completedAt : 0L;
	}

	/** Creates an in-progress history row. */
	public static PuzzleHistoryEntry inProgress(String clues, int level, int score, long lastStartedAt) {
		return new PuzzleHistoryEntry(clues, level, score, lastStartedAt,
				Status.IN_PROGRESS, null, 0L);
	}

	/** Creates a completed history row. */
	public static PuzzleHistoryEntry completed(String clues, int level, int score, long lastStartedAt,
			String finalGrid, long completedAt) {
		return new PuzzleHistoryEntry(clues, level, score, lastStartedAt,
				Status.COMPLETED, finalGrid, completedAt);
	}

	/**
	 * Parses either the original four-field representation or the extended
	 * seven-field representation.
	 */
	public static PuzzleHistoryEntry parse(String encoded) {
		if (encoded == null) {
			throw new IllegalArgumentException("History row is null");
		}
		String[] parts = encoded.split("#", -1);
		try {
			if (parts.length == 4) {
				return inProgress(parts[0], Integer.parseInt(parts[1]),
						Integer.parseInt(parts[2]), Long.parseLong(parts[3]));
			}
			if (parts.length == 7) {
				int level = Integer.parseInt(parts[1]);
				int score = Integer.parseInt(parts[2]);
				long startedAt = Long.parseLong(parts[3]);
				if (IN_PROGRESS_CODE.equals(parts[4])) {
					if (parts[5].length() != 0) {
						throw new IllegalArgumentException("In-progress history row has a final grid");
					}
					return inProgress(parts[0], level, score, startedAt);
				}
				if (COMPLETED_CODE.equals(parts[4])) {
					return completed(parts[0], level, score, startedAt,
							parts[5], Long.parseLong(parts[6]));
				}
				throw new IllegalArgumentException("Unknown history status: " + parts[4]);
			}
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid numeric field in history row", ex);
		}
		throw new IllegalArgumentException("Unsupported history row field count: " + parts.length);
	}

	/** Encodes this row without changing the bean-compatible List&lt;String&gt; property. */
	public String encode() {
		return clues + "#" + level + "#" + score + "#" + lastStartedAt + "#"
				+ (isCompleted() ? COMPLETED_CODE : IN_PROGRESS_CODE) + "#"
				+ (finalGrid == null ? "" : finalGrid) + "#" + completedAt;
	}

	/** Returns a copy marked as a newly started attempt. */
	public PuzzleHistoryEntry startAgain(int newLevel, int newScore, long startedAt) {
		if (isCompleted()) {
			return completed(clues, newLevel, newScore, startedAt, finalGrid, completedAt);
		}
		return inProgress(clues, newLevel, newScore, startedAt);
	}

	/** Returns a copy containing the most recent completion snapshot. */
	public PuzzleHistoryEntry complete(int newLevel, int newScore, String newFinalGrid, long completionTime) {
		return completed(clues, newLevel, newScore, lastStartedAt, newFinalGrid, completionTime);
	}

	/**
	 * Returns text accepted by {@link Sudoku2#setSudoku(String)}. Completed rows
	 * use library format so original givens remain fixed while entered values stay
	 * distinguishable.
	 */
	public String getPuzzleForOpen() {
		if (!isCompleted()) {
			return clues;
		}
		StringBuilder out = new StringBuilder(170);
		out.append(":0000:x:");
		for (int i = 0; i < clues.length(); i++) {
			if (clues.charAt(i) == '0') {
				out.append('+');
			}
			out.append(finalGrid.charAt(i));
		}
		out.append(":::");
		return out.toString();
	}

	/** Normalizes dots and zeroes to one canonical 81-character key. */
	public static String normalizeClues(String clues) {
		return normalizeGrid(clues, false);
	}

	private static String normalizeGrid(String grid, boolean requireComplete) {
		if (grid == null) {
			throw new IllegalArgumentException("Puzzle grid is null");
		}
		StringBuilder normalized = new StringBuilder(81);
		for (int i = 0; i < grid.length(); i++) {
			char ch = grid.charAt(i);
			if (ch >= '1' && ch <= '9') {
				normalized.append(ch);
			} else if (ch == '0' || ch == '.') {
				if (requireComplete) {
					throw new IllegalArgumentException("Completed grid contains an empty cell");
				}
				normalized.append('0');
			} else if (!Character.isWhitespace(ch)) {
				throw new IllegalArgumentException("Invalid character in puzzle grid: " + ch);
			}
		}
		if (normalized.length() != 81) {
			throw new IllegalArgumentException("Puzzle grid must contain exactly 81 cells");
		}
		return normalized.toString();
	}

	private static void validateFinalGrid(String clues, String finalGrid) {
		for (int i = 0; i < clues.length(); i++) {
			char clue = clues.charAt(i);
			if (clue != '0' && clue != finalGrid.charAt(i)) {
				throw new IllegalArgumentException("Final grid changes a given at index " + i);
			}
		}
	}

	public String getClues() {
		return clues;
	}

	public int getLevel() {
		return level;
	}

	public int getScore() {
		return score;
	}

	public long getLastStartedAt() {
		return lastStartedAt;
	}

	public Status getStatus() {
		return status;
	}

	public boolean isCompleted() {
		return status == Status.COMPLETED;
	}

	public String getFinalGrid() {
		return finalGrid;
	}

	public long getCompletedAt() {
		return completedAt;
	}
}
