/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Diagnostic only: run this same compiled probe against baseline and candidate class paths in fresh JVMs. */
public final class DoodleStorageBenchmark {
    private static final java.lang.management.ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private static Method allocation;
    private static Method append;
    private static Object retained;
    private static long cpu() { return threads.isCurrentThreadCpuTimeSupported() ? threads.getCurrentThreadCpuTime() : -1; }
    private static long allocated() {
        try { return allocation == null ? -1 : ((Number)allocation.invoke(threads, Thread.currentThread().getId())).longValue(); }
        catch (Exception unsupported) { return -1; }
    }
    private static void report(String phase, long start, long cpu, long bytes, int size) {
        long elapsed = System.nanoTime() - start, consumed = cpu < 0 ? -1 : cpu() - cpu;
        long allocated = bytes < 0 ? -1 : allocated() - bytes;
        System.out.println("{\"phase\":\"" + phase + "\",\"elapsed_ns\":" + elapsed
            + ",\"cpu_ns\":" + consumed + ",\"allocated_bytes\":" + allocated + ",\"result_count\":" + size + "}");
    }
    private static DoodleStroke line(int ink, int points) throws Exception {
        DoodleStroke s = new DoodleStroke(new Color(27, 140, 63), .01f);
        for (int p = 0; p < points; p++) {
            double x = p / (double)(points - 1), y = .5 + .28 * Math.sin(p * .11 + ink * .071);
            if (append == null) s.getPoints().add(new DoodlePoint(x, y)); else append.invoke(s, x, y);
        }
        return s;
    }
    public static void main(String[] args) throws Exception {
        try { append = DoodleStroke.class.getMethod("addPoint", double.class, double.class); } catch (NoSuchMethodException old) {}
        try { allocation = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", long.class); }
        catch (ReflectiveOperationException unsupported) {}
        List<DoodleStroke> source = new ArrayList<DoodleStroke>();
        for (int i = 0; i < 100; i++) source.add(line(i, 120));
        // Setup is outside measurement. Three bounded warm-up passes exercise the actual copy implementation.
        for (int pass = 0; pass < 3; pass++) for (DoodleStroke s : source) retained = s.copy();
        List<List<DoodleStroke>> history = new ArrayList<List<DoodleStroke>>();
        long bytes = allocated(), cpu = cpu(), start = System.nanoTime();
        for (int edit = 0; edit < source.size(); edit++) {
            List<DoodleStroke> snapshot = new ArrayList<DoodleStroke>();
            for (int s = 0; s <= edit; s++) snapshot.add(source.get(s).copy());
            history.add(snapshot);
        }
        retained = history;
        report("retain_100_history_layers", start, cpu, bytes, history.size());
        for (boolean whole : new boolean[]{false, true}) {
            bytes = allocated(); cpu = cpu(); start = System.nanoTime();
            int resultCount = 0;
            for (int pass = 0; pass < 12; pass++) {
                DoodleGeometry.ErasurePreview cut = DoodleGeometry.previewSubtract(source,
                        new Ellipse2D.Double(330 + pass * 2, 360, 80, 100), 810, 810,
                        s -> AffineTransform.getScaleInstance(810, 810), s -> 8.1, whole);
                retained = cut; resultCount += cut.retained.size() + cut.removed.size();
            }
            report(whole ? "whole_eraser_12_previews" : "range_eraser_12_previews", start, cpu, bytes, resultCount);
        }
    }
}
