/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;

/** JavaBean representation of one dual-swatch annotation palette selection. */
public class AnnotationPaletteSelection {
	private int primarySlot = 6;
	private Color primaryCustom;
	private int secondarySlot = 7;
    private Integer paletteGroup;
    private boolean swapped;
	private Color secondaryCustom;

	public AnnotationPaletteSelection() {
	}

    public Integer getPaletteGroup() { return paletteGroup; }
    public void setPaletteGroup(Integer value) { paletteGroup = value; }
    public boolean isSwapped() { return swapped; }
    public void setSwapped(boolean value) { swapped = value; }

    // Legacy properties remain readable for existing JavaBean configuration files.
	public int getPrimarySlot() { return primarySlot; }
	public void setPrimarySlot(int value) { primarySlot = value; }
	public Color getPrimaryCustom() { return primaryCustom; }
	public void setPrimaryCustom(Color value) { primaryCustom = value; }
	public int getSecondarySlot() { return secondarySlot; }
	public void setSecondarySlot(int value) { secondarySlot = value; }
	public Color getSecondaryCustom() { return secondaryCustom; }
	public void setSecondaryCustom(Color value) { secondaryCustom = value; }
}
