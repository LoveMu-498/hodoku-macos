/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

/** A point stored relative to the left Sudoku drawing region. */
public final class DoodlePoint {
	private double x;
	private double y;
	private java.util.ArrayList<CoordinateListener> coordinateListeners;

	public DoodlePoint() {
	}

	public DoodlePoint(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public double getX() {
		return x;
	}

	public void setX(double x) {
		if (Double.doubleToRawLongBits(this.x) == Double.doubleToRawLongBits(x)) return;
		this.x = x;
		coordinatesChanged();
	}

	public double getY() {
		return y;
	}

	public void setY(double y) {
		if (Double.doubleToRawLongBits(this.y) == Double.doubleToRawLongBits(y)) return;
		this.y = y;
		coordinatesChanged();
	}

	interface CoordinateListener { boolean coordinatesChanged(double x, double y); }
	void addCoordinateListener(CoordinateListener listener) {
		if (coordinateListeners == null) coordinateListeners = new java.util.ArrayList<CoordinateListener>(1);
		coordinateListeners.add(listener);
	}
	void removeCoordinateListener(CoordinateListener listener) {
		if (coordinateListeners != null) coordinateListeners.remove(listener);
	}
	private void coordinatesChanged() {
		if (coordinateListeners == null) return;
		for (int i = coordinateListeners.size() - 1; i >= 0; i--)
			if (!coordinateListeners.get(i).coordinatesChanged(x, y)) coordinateListeners.remove(i);
	}
}
