/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

/** Options-owned selections for the one shared six-pair annotation palette. */
public class AnnotationPalettePreferences {
	private AnnotationPaletteSelection candidateColoring = new AnnotationPaletteSelection();
	private AnnotationPaletteSelection cellColoring = new AnnotationPaletteSelection();
	private AnnotationPaletteSelection doodle = new AnnotationPaletteSelection();
	private int freeChainGroup = 3;
	private int boxSelectionGroup = 3;

	public AnnotationPalettePreferences() {
	}

	public AnnotationPaletteSelection getCandidateColoring() { return candidateColoring; }
	public void setCandidateColoring(AnnotationPaletteSelection value) { candidateColoring = value; }
	public AnnotationPaletteSelection getCellColoring() { return cellColoring; }
	public void setCellColoring(AnnotationPaletteSelection value) { cellColoring = value; }
	public AnnotationPaletteSelection getDoodle() { return doodle; }
	public void setDoodle(AnnotationPaletteSelection value) { doodle = value; }
	public int getFreeChainGroup() { return freeChainGroup; }
	public void setFreeChainGroup(int value) { freeChainGroup = value; }
	public int getBoxSelectionGroup() { return boxSelectionGroup; }
	public void setBoxSelectionGroup(int value) { boxSelectionGroup = value; }
}
