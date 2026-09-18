/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

/** One interactive Sudoku structure reference found in hint text. */
public final class SudokuTextReference {

	public enum Kind {
		CELLS,
		ROWS,
		COLUMNS,
		BLOCKS
	}

	private final int start;
	private final int end;
	private final String text;
	private final Kind kind;
	private final int[] cellIndices;

	SudokuTextReference(int start, int end, String text, Kind kind, int[] cellIndices) {
		this.start = start;
		this.end = end;
		this.text = text;
		this.kind = kind;
		this.cellIndices = cellIndices.clone();
	}

	public int getStart() {
		return start;
	}

	public int getEnd() {
		return end;
	}

	public String getText() {
		return text;
	}

	public Kind getKind() {
		return kind;
	}

	public int[] getCellIndices() {
		return cellIndices.clone();
	}

	public boolean containsCell(int index) {
		for (int cellIndex : cellIndices) {
			if (cellIndex == index) {
				return true;
			}
		}
		return false;
	}
}
