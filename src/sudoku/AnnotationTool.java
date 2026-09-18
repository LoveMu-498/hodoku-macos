/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

/** The mutually exclusive board-pointer tools used for puzzle-local annotations. */
public enum AnnotationTool {
	DEFAULT_MOUSE,
	CANDIDATE_COLORING,
	CELL_COLORING,
	DOODLE,
	FREE_CHAIN,
	BOX_SELECTION
}
