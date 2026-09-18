/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

/** Persistent owner of one state projected into the shared annotation palette. */
public enum AnnotationPaletteOwner {
	CANDIDATE_COLORING(true),
	CELL_COLORING(true),
	DOODLE(true),
	FREE_CHAIN(false),
	BOX_SELECTION(false);

	private final boolean secondarySupported;

	AnnotationPaletteOwner(boolean secondarySupported) {
		this.secondarySupported = secondarySupported;
	}

	public boolean isSecondarySupported() {
		return secondarySupported;
	}

	public static AnnotationPaletteOwner fromTool(AnnotationTool tool) {
		if (tool == null || tool == AnnotationTool.DEFAULT_MOUSE) {
			return CANDIDATE_COLORING;
		}
		switch (tool) {
		case CELL_COLORING:
			return CELL_COLORING;
		case DOODLE:
			return DOODLE;
		case FREE_CHAIN:
			return FREE_CHAIN;
		case BOX_SELECTION:
			return BOX_SELECTION;
		case CANDIDATE_COLORING:
		case DEFAULT_MOUSE:
		default:
			return CANDIDATE_COLORING;
		}
	}
}
