/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

/** Exact immutable coordinates. Unchanged strokes can share this value across undo snapshots. */
public final class DoodleCoordinates {
    private final double[] values;
    private final int size;

    DoodleCoordinates(double[] values, int size) {
        this.values = values;
        this.size = size;
    }

    /** Copies caller-owned coordinates; no mutable array is retained or exposed publicly. */
    public static DoodleCoordinates copyOf(double[] xy) {
        if (xy == null || (xy.length & 1) != 0) throw new IllegalArgumentException("Expected x/y pairs");
        return new DoodleCoordinates(xy.clone(), xy.length / 2);
    }

    public int size() { return size; }
    public double x(int index) { check(index); return values[index * 2]; }
    public double y(int index) { check(index); return values[index * 2 + 1]; }
    public double[] toArray() { return java.util.Arrays.copyOf(values, size * 2); }

    // Only the copy-on-write owner uses this array, and it must detach before every mutation.
    double[] sharedValues() { return values; }

    private void check(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Point " + index + ", size " + size);
    }
}
