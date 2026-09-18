/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.util.List;

/** Focused checks for interactive Sudoku references in hint text. */
public final class SudokuReferenceParserProbe {

	private SudokuReferenceParserProbe() {
	}

	public static void main(String[] args) {
		String text = "r1c2 r34c6 r5c89 R7C3 r37 c34 r236c24b3 b3 b39 第三宫 4宫 ordinary";
		List<SudokuTextReference> references = SudokuReferenceParser.parse(text);

		requireReference(references, "r1c2", SudokuTextReference.Kind.CELLS, 1);
		requireReference(references, "r34c6", SudokuTextReference.Kind.CELLS, 2);
		requireReference(references, "r5c89", SudokuTextReference.Kind.CELLS, 2);
		requireReference(references, "R7C3", SudokuTextReference.Kind.CELLS, 1);
		requireReference(references, "r37", SudokuTextReference.Kind.ROWS, 18);
		requireReference(references, "c34", SudokuTextReference.Kind.COLUMNS, 18);
		requireReference(references, "r236", SudokuTextReference.Kind.ROWS, 27);
		requireReference(references, "c24", SudokuTextReference.Kind.COLUMNS, 18);
		requireReference(references, "b3", SudokuTextReference.Kind.BLOCKS, 9);
		requireReference(references, "b39", SudokuTextReference.Kind.BLOCKS, 18);
		requireReference(references, "第三宫", SudokuTextReference.Kind.BLOCKS, 9);
		requireReference(references, "4宫", SudokuTextReference.Kind.BLOCKS, 9);
		require(find(references, "ordinary") == null, "ordinary text was parsed as a reference");

		SudokuTextReference compact = find(references, "r34c6");
		require(compact.containsCell(2 * 9 + 5), "r34c6 omitted r3c6");
		require(compact.containsCell(3 * 9 + 5), "r34c6 omitted r4c6");
		require(!compact.containsCell(2 * 9 + 4), "r34c6 highlighted an unrelated cell");

		List<SudokuTextReference> fins = SudokuReferenceParser.parse("fr1c2 efr89c3");
		requireReference(fins, "fr1c2", SudokuTextReference.Kind.CELLS, 1);
		requireReference(fins, "efr89c3", SudokuTextReference.Kind.CELLS, 2);

		List<SudokuTextReference> rejected = SudokuReferenceParser.parse("r0c1 r10c2 b0 第十宫 car1");
		require(rejected.isEmpty(), "out-of-range or embedded references were accepted");

		System.out.println("Sudoku reference parser checks passed");
	}

	private static void requireReference(List<SudokuTextReference> references, String token,
			SudokuTextReference.Kind kind, int cellCount) {
		SudokuTextReference reference = find(references, token);
		require(reference != null, "missing reference: " + token);
		require(reference.getKind() == kind, "wrong reference kind for " + token);
		require(reference.getCellIndices().length == cellCount, "wrong target count for " + token);
	}

	private static SudokuTextReference find(List<SudokuTextReference> references, String token) {
		for (SudokuTextReference reference : references) {
			if (token.equals(reference.getText())) {
				return reference;
			}
		}
		return null;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
