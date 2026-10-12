/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.lang.ref.WeakReference;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.RandomAccess;

/**
 * Dense double coordinates with a lazy, writable List compatibility view.
 * The owning UI thread mutates a list; immutable snapshots may be read on workers.
 */
public final class DoodlePoints extends AbstractList<DoodlePoint> implements RandomAccess {
    private double[] values = new double[0];
    private int size;
    private DoodleCoordinates snapshot;
    private Binding[] handles;

    public DoodlePoints() {}

    DoodlePoints(DoodleCoordinates coordinates) {
        values = coordinates.sharedValues();
        size = coordinates.size();
        snapshot = coordinates;
    }

    DoodleCoordinates snapshot() {
        if (snapshot == null) snapshot = new DoodleCoordinates(values, size);
        return snapshot;
    }

    DoodlePoints copy() { return new DoodlePoints(snapshot()); }
    @Override public int size() { return size; }
    double x(int index) { check(index); return values[index * 2]; }
    double y(int index) { check(index); return values[index * 2 + 1]; }

    void append(double x, double y) {
        ensureWritable(size + 1);
        values[size * 2] = x;
        values[size * 2 + 1] = y;
        size++;
        modCount++;
    }

    void setCoordinates(int index, double x, double y) {
        check(index);
        if (Double.doubleToRawLongBits(values[index * 2]) == Double.doubleToRawLongBits(x)
                && Double.doubleToRawLongBits(values[index * 2 + 1]) == Double.doubleToRawLongBits(y)) return;
        Binding handle = handles == null ? null : handles[index];
        if (handle != null) {
            // Preserve references handed out by get(), including aliases in other lists.
            handle.point.setX(x);
            handle.point.setY(y);
        } else {
            ensureWritable(size);
            values[index * 2] = x;
            values[index * 2 + 1] = y;
        }
    }

    @Override public DoodlePoint get(int index) {
        check(index);
        ensureHandles();
        Binding handle = handles[index];
        if (handle == null) {
            handle = new Binding(this, index, new DoodlePoint(x(index), y(index)));
            handles[index] = handle;
        }
        return handle.point;
    }

    @Override public void add(int index, DoodlePoint point) {
        if (index < 0 || index > size) throw new IndexOutOfBoundsException("Point " + index + ", size " + size);
        if (point == null) throw new NullPointerException("point");
        ensureWritable(size + 1);
        System.arraycopy(values, index * 2, values, (index + 1) * 2, (size - index) * 2);
        ensureHandles();
        System.arraycopy(handles, index, handles, index + 1, size - index);
        values[index * 2] = point.getX();
        values[index * 2 + 1] = point.getY();
        handles[index] = new Binding(this, index, point);
        size++;
        reindex(index + 1);
        modCount++;
    }

    @Override public DoodlePoint set(int index, DoodlePoint point) {
        check(index);
        if (point == null) throw new NullPointerException("point");
        DoodlePoint previous = get(index);
        if (previous == point) return previous;
        ensureWritable(size);
        handles[index].detach();
        handles[index] = new Binding(this, index, point);
        values[index * 2] = point.getX();
        values[index * 2 + 1] = point.getY();
        return previous;
    }

    @Override public DoodlePoint remove(int index) {
        check(index);
        DoodlePoint previous = get(index);
        removeRange(index, index + 1);
        return previous;
    }

    @Override protected void removeRange(int from, int to) {
        if (from < 0 || to > size || from > to) throw new IndexOutOfBoundsException();
        if (from == to) return;
        ensureWritable(size);
        if (handles != null) {
            for (int i = from; i < to; i++) if (handles[i] != null) handles[i].detach();
            System.arraycopy(handles, to, handles, from, size - to);
            Arrays.fill(handles, size - (to - from), size, null);
        }
        System.arraycopy(values, to * 2, values, from * 2, (size - to) * 2);
        size -= to - from;
        reindex(from);
        modCount++;
    }

    @Override public void clear() {
        if (size == 0) return;
        if (handles != null) {
            for (int i = 0; i < size; i++) if (handles[i] != null) handles[i].detach();
            Arrays.fill(handles, null);
        }
        // Do not clone a shared array just to discard it (eraser fragments use copy then clear).
        if (snapshot != null) { values = new double[0]; handles = null; snapshot = null; }
        size = 0;
        modCount++;
    }

    private void ensureWritable(int required) {
        if (required > Integer.MAX_VALUE / 2) throw new OutOfMemoryError("Too many doodle points");
        int capacity = values.length / 2;
        if (snapshot != null || required > capacity) {
            int next = capacity;
            if (required > capacity) next = (int)Math.min(Integer.MAX_VALUE / 2,
                    Math.max((long)required, Math.max(8L, capacity + (long)capacity / 2)));
            values = Arrays.copyOf(values, next * 2);
            snapshot = null;
            if (handles != null && handles.length < next) handles = Arrays.copyOf(handles, next);
        }
    }

    private void ensureHandles() {
        if (handles == null) handles = new Binding[values.length / 2];
    }

    private void reindex(int from) {
        if (handles != null) for (int i = from; i < size; i++) if (handles[i] != null) handles[i].index = i;
    }

    private void check(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException("Point " + index + ", size " + size);
    }

    private static final class Binding implements DoodlePoint.CoordinateListener {
        final WeakReference<DoodlePoints> owner;
        final DoodlePoint point;
        int index;
        Binding(DoodlePoints owner, int index, DoodlePoint point) {
            this.owner = new WeakReference<DoodlePoints>(owner);
            this.index = index;
            this.point = point;
            point.addCoordinateListener(this);
        }
        @Override public boolean coordinatesChanged(double x, double y) {
            DoodlePoints list = owner.get();
            if (list == null) return false;
            list.ensureWritable(list.size);
            list.values[index * 2] = x;
            list.values[index * 2 + 1] = y;
            return true;
        }
        void detach() { point.removeCoordinateListener(this); }
    }
}
