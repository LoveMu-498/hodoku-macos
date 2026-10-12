/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Long-session fixture with actual successive revisions, retained undo/redo and savepoints. */
public final class SessionHistoryPerformanceProbe {
    public static final String PUZZLE = "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    /** Usable by the real GUI close probe to install the same stable fixture. */
    static SessionSnapshot fixture(int strokeCount, int pointsPerStroke, int historyCount) {
        GuiState state = new GuiState(); Sudoku2 sudoku = new Sudoku2(); sudoku.setSudoku(PUZZLE);
        state.setSudoku(sudoku); state.setIncludeAnnotations(true);
        List<DoodleStroke> current = new ArrayList<DoodleStroke>();
        for (int s = 0; s < strokeCount; s++) {
            DoodleStroke stroke = new DoodleStroke(new Color((s * 339) | 0xff000000, true), 1.25f);
            for (int p = 0; p < pointsPerStroke; p++) stroke.addPoint((s % 9 + p / 140.0) / 9, (s / 9 + Math.sin(p / 10.0) * 0.1) / 9);
            current.add(stroke);
        }
        state.setDoodleUndoHistory(new ArrayList<List<DoodleStroke>>());
        state.setDoodleRedoHistory(new ArrayList<List<DoodleStroke>>());
        List<GuiState> savePoints = new ArrayList<GuiState>();
        for (int h = 0; h < historyCount; h++) {
            List<DoodleStroke> prior = copies(current);
            if (h < historyCount * 4 / 5) state.getDoodleUndoHistory().add(prior);
            else state.getDoodleRedoHistory().add(prior);
            int edited = h % strokeCount;
            DoodleStroke changed = current.get(edited).copy();
            // Model one partial erasure/moved point: only this stroke detaches its coordinates.
            int at = h % pointsPerStroke;
            changed.setPoint(at, Math.nextUp(changed.pointX(at)), changed.pointY(at));
            changed.setThoughtGroup(h % 6);
            current.set(edited, changed);
            if (h % 25 == 0) {
                GuiState saved = new GuiState(); saved.setName("存档 " + h); saved.setSudoku(sudoku.clone());
                saved.setIncludeAnnotations(true); saved.setDoodleStrokes(copies(current));
                saved.setDoodleUndoHistory(historyCopies(state.getDoodleUndoHistory()));
                saved.setDoodleRedoHistory(historyCopies(state.getDoodleRedoHistory()));
                savePoints.add(saved);
            }
        }
        state.setDoodleStrokes(copies(current));
        SessionSnapshot snapshot = new SessionSnapshot(); snapshot.setGuiState(state); snapshot.setSavePoints(savePoints);
        snapshot.setReplayId("long-history-fixture"); snapshot.setReplayFrameCount(1201); snapshot.setReplayElapsedMillis(50000);
        return snapshot;
    }

    public static void main(String[] args) throws Exception {
        SessionSnapshot source = fixture(80, 120, 100);
        File output = args.length == 0 ? Files.createTempDirectory("hodoku-session-history-").resolve("last-session.xml").toFile() : new File(args[0]);
        SessionStore store = new SessionStore(output);
        long start = System.nanoTime(); SessionSnapshot frozen = SessionSnapshots.detached(source); long captured = System.nanoTime();
        store.save(frozen); long saved = System.nanoTime(); SessionSnapshot read = store.load(); long loaded = System.nanoTime();
        assertAllInk(frozen.getGuiState(), read.getGuiState());
        check(read.getSavePoints().size() == frozen.getSavePoints().size(), "savepoint count");
        for (int i = 0; i < read.getSavePoints().size(); i++) assertAllInk(frozen.getSavePoints().get(i), read.getSavePoints().get(i));
        long checked = System.nanoTime();
        IdentityHashMap<DoodleCoordinates, Boolean> unique = new IdentityHashMap<DoodleCoordinates, Boolean>();
        long[] stats = new long[2]; statistics(frozen.getGuiState(), unique, stats);
        for (GuiState state : frozen.getSavePoints()) statistics(state, unique, stats);
        long uniquePoints = 0; for (DoodleCoordinates c : unique.keySet()) uniquePoints += c.size();
        check(uniquePoints < stats[1] / 10, "fixture lacks substantial repeated history");
        check(SessionSnapshots.sameContent(source, frozen), "capture changed source state");
        System.out.println("PASS history: every coordinate/metadata in current + 100 undo/redo + 4 full-history savepoints restored");
        System.out.println("current_points=9600 history_layers=100 savepoints=" + frozen.getSavePoints().size()
                + " stroke_references=" + stats[0] + " total_referenced_points=" + stats[1]
                + " unique_geometries=" + unique.size() + " unique_points=" + uniquePoints + " bytes=" + output.length());
        System.out.printf(java.util.Locale.ROOT, "capture_ms=%.3f save_and_validate_ms=%.3f additional_load_ms=%.3f all_ink_assert_ms=%.3f%n",
                (captured - start) / 1e6, (saved - captured) / 1e6, (loaded - saved) / 1e6, (checked - loaded) / 1e6);
    }

    private static List<DoodleStroke> copies(List<DoodleStroke> strokes) {
        List<DoodleStroke> result = new ArrayList<DoodleStroke>(strokes.size());
        for (DoodleStroke stroke : strokes) result.add(stroke.copy()); return result;
    }
    private static List<List<DoodleStroke>> historyCopies(List<List<DoodleStroke>> history) {
        List<List<DoodleStroke>> result = new ArrayList<List<DoodleStroke>>(history.size());
        for (List<DoodleStroke> layer : history) result.add(copies(layer));
        return result;
    }
    private static void assertAllInk(GuiState first, GuiState second) {
        compare(first.getDoodleStrokes(), second.getDoodleStrokes());
        compareHistory(first.getDoodleUndoHistory(), second.getDoodleUndoHistory());
        compareHistory(first.getDoodleRedoHistory(), second.getDoodleRedoHistory());
        check(first.getSudoku().getSudoku(ClipboardMode.LIBRARY).equals(second.getSudoku().getSudoku(ClipboardMode.LIBRARY)), "savepoint board changed");
        check(java.util.Objects.equals(first.getName(), second.getName()), "savepoint name changed");
    }
    private static void compareHistory(List<List<DoodleStroke>> first, List<List<DoodleStroke>> second) {
        check(first != null && second != null && first.size() == second.size(), "history length changed");
        for (int i = 0; i < first.size(); i++) compare(first.get(i), second.get(i));
    }
    private static void compare(List<DoodleStroke> first, List<DoodleStroke> second) {
        check(first.size() == second.size(), "stroke count changed");
        for (int s = 0; s < first.size(); s++) {
            DoodleStroke a = first.get(s), b = second.get(s);
            check(SessionInkCodec.metadata(a).equals(SessionInkCodec.metadata(b)), "stroke metadata changed");
            check(a.pointCount() == b.pointCount(), "point count changed");
            for (int p = 0; p < a.pointCount(); p++)
                check(Double.doubleToRawLongBits(a.pointX(p)) == Double.doubleToRawLongBits(b.pointX(p))
                        && Double.doubleToRawLongBits(a.pointY(p)) == Double.doubleToRawLongBits(b.pointY(p)), "point bits changed");
        }
    }
    private static void statistics(GuiState state, IdentityHashMap<DoodleCoordinates, Boolean> unique, long[] stats) {
        count(state.getDoodleStrokes(), unique, stats);
        for (List<DoodleStroke> list : state.getDoodleUndoHistory()) count(list, unique, stats);
        for (List<DoodleStroke> list : state.getDoodleRedoHistory()) count(list, unique, stats);
    }
    private static void count(List<DoodleStroke> list, IdentityHashMap<DoodleCoordinates, Boolean> unique, long[] stats) {
        for (DoodleStroke stroke : list) { stats[0]++; stats[1] += stroke.pointCount(); unique.put(stroke.coordinateSnapshot(), Boolean.TRUE); }
    }
}
