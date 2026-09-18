/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Freehand points use panel fractions; candidate-bound points use cell-size offsets from the native candidate center. */
public final class DoodleStroke {
	private Color color;
    private int anchorCell = -1;
    private int anchorDigit;
	private float widthFactor;
	private List<DoodlePoint> points = new ArrayList<DoodlePoint>();

	public DoodleStroke() {
	}

	public DoodleStroke(Color color, float widthFactor) {
		this.color = color;
		this.widthFactor = widthFactor;
	}

    public int getAnchorCell() { return anchorCell; }
    public void setAnchorCell(int value) { anchorCell = value; }
    public int getAnchorDigit() { return anchorDigit; }
    public void setAnchorDigit(int value) { anchorDigit = value; }
    public boolean isCandidateAnchored() { return anchorCell >= 0 && anchorCell < 81 && anchorDigit >= 1 && anchorDigit <= 9; }

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public float getWidthFactor() {
		return widthFactor;
	}

	public void setWidthFactor(float widthFactor) {
		this.widthFactor = widthFactor;
	}

	public List<DoodlePoint> getPoints() {
		return points;
	}

	public void setPoints(List<DoodlePoint> points) {
		this.points = points == null ? new ArrayList<DoodlePoint>() : points;
	}

	public DoodleStroke copy() {
		DoodleStroke copy = new DoodleStroke(color, widthFactor);
        copy.anchorCell = anchorCell; copy.anchorDigit = anchorDigit;
		for (DoodlePoint point : points) {
			copy.getPoints().add(new DoodlePoint(point.getX(), point.getY()));
		}
		return copy;
	}
}
