/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Diagnoses the actual in-memory comparison; never substitutes XML hashes for equivalence. */
public final class SessionIdleDifferenceProbe {
    private static final CountDownLatch PAINTED = new CountDownLatch(1);
    private static final class PaintedFrame extends MainFrame {
        PaintedFrame() { super(null); }
        @Override public void paint(Graphics graphics) { super.paint(graphics); PAINTED.countDown(); }
    }
    public static void main(String[] args) throws Exception {
        if (System.getProperty("hodoku.data.dir") == null) throw new IllegalArgumentException("isolated data required");
        Path report = Paths.get(args[0]); Files.createDirectories(report); StringBuilder text = new StringBuilder(); MainFrame[] frame = {null};
        boolean success = false;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.invokeAndWait(() -> { frame[0] = new PaintedFrame(); frame[0].setVisible(true); });
            if (!PAINTED.await(15, TimeUnit.SECONDS)) throw new AssertionError("window never painted");
            SwingUtilities.invokeAndWait(() -> {
                try { Method seed = SaveOptimizationLifecycleProbe.class.getDeclaredMethod("seed", MainFrame.class, String.class, int.class); seed.setAccessible(true); seed.invoke(null, frame[0], "idle", 100); }
                catch (Exception error) { throw new RuntimeException(error); }
            });
            Thread.sleep(7000);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    Object persistence = field(MainFrame.class, "sessionPersistence").get(frame[0]);
                    Object committed = field(SessionPersistence.class, "committed").get(persistence);
                    if (committed == null) throw new AssertionError("No completed idle save");
                    SessionSnapshot saved = (SessionSnapshot) field(committed.getClass(), "snapshot").get(committed);
                    compare("before expectation capture", saved, capture(frame[0]), text);
                    Method expected = SaveOptimizationLifecycleProbe.class.getDeclaredMethod("expected", MainFrame.class); expected.setAccessible(true);
                    Method manifest = SaveOptimizationLifecycleProbe.class.getDeclaredMethod("manifest", SessionSnapshot.class); manifest.setAccessible(true);
                    manifest.invoke(null, expected.invoke(null, frame[0]));
                    JRootPane pane = frame[0].getRootPane(); pane.paintImmediately(0, 0, pane.getWidth(), pane.getHeight()); Toolkit.getDefaultToolkit().sync();
                    compare("after expectation/paint", saved, capture(frame[0]), text);
                    frame[0].getReplayController().prepareQuitSave();
                    compare("after replay quit prepare", saved, capture(frame[0]), text);
                    frame[0].getReplayController().cancelQuit();
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            success = true;
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (frame[0] != null) frame[0].dispose(); });
            Files.write(report.resolve("first-differences.txt"), text.toString().getBytes(StandardCharsets.UTF_8)); System.out.print(text);
        }
        System.exit(success ? 0 : 1);
    }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static SessionSnapshot capture(MainFrame frame) throws Exception { Method method = MainFrame.class.getDeclaredMethod("captureWorkSession"); method.setAccessible(true); return (SessionSnapshot) method.invoke(frame); }
    private static void compare(String stage, SessionSnapshot saved, SessionSnapshot current, StringBuilder text) throws Exception {
        text.append(stage).append(": sameWorkingContent=").append(SessionSnapshots.sameWorkingContent(saved, current)).append('\n');
        SessionSnapshot normalized = SessionSnapshots.withWireVersion(current, saved.getFormatVersion()); normalized.setReplayElapsedMillis(saved.getReplayElapsedMillis());
        List<String> diffs = new ArrayList<String>(); diff(saved, normalized, "$", new IdentityHashMap<Object, IdentityHashMap<Object, Boolean>>(), diffs);
        for (String line : diffs) text.append("  ").append(line).append('\n');
    }
    @SuppressWarnings("unchecked")
    private static List<Field> fields(Class<?> type) throws Exception { Method method = SessionSnapshots.class.getDeclaredMethod("fields", Class.class); method.setAccessible(true); return (List<Field>) method.invoke(null, type); }
    private static void diff(Object a, Object b, String path, IdentityHashMap<Object, IdentityHashMap<Object, Boolean>> seen, List<String> result) throws Exception {
        if (a == b || result.size() >= 20) return;
        if (a == null || b == null) { result.add(path + ": " + describe(a) + " -> " + describe(b)); return; }
        if (a instanceof Number || a instanceof String || a instanceof Boolean || a instanceof Character || a instanceof Enum<?> || a instanceof Date || a instanceof Color) {
            boolean same = a instanceof Double && b instanceof Double ? Double.doubleToRawLongBits((Double)a) == Double.doubleToRawLongBits((Double)b)
                    : a instanceof Float && b instanceof Float ? Float.floatToRawIntBits((Float)a) == Float.floatToRawIntBits((Float)b) : a.equals(b);
            if (!same) result.add(path + ": " + describe(a) + " -> " + describe(b)); return;
        }
        if ((a.getClass() == SudokuSet.class || a.getClass() == SudokuSetBase.class || a.getClass() == SudokuSetShort.class) && a.getClass() == b.getClass()) {
            if (!a.equals(b)) result.add(path + ": native membership mask differs"); return;
        }
        IdentityHashMap<Object, Boolean> matches = seen.get(a); if (matches != null && matches.containsKey(b)) return;
        if (matches == null) { matches = new IdentityHashMap<Object, Boolean>(); seen.put(a, matches); } matches.put(b, Boolean.TRUE);
        if (a instanceof DoodleStroke && b instanceof DoodleStroke) {
            if (!SessionInkCodec.metadata((DoodleStroke)a).equals(SessionInkCodec.metadata((DoodleStroke)b))) result.add(path + ": stroke metadata differs");
            diff(((DoodleStroke)a).coordinateSnapshot(), ((DoodleStroke)b).coordinateSnapshot(), path + ".coordinates", seen, result); return;
        }
        if (a instanceof DoodleCoordinates && b instanceof DoodleCoordinates) {
            diff(((DoodleCoordinates)a).toArray(), ((DoodleCoordinates)b).toArray(), path + ".values", seen, result); return;
        }
        if (a.getClass().isArray() && b.getClass().isArray()) {
            if (Array.getLength(a) != Array.getLength(b)) { result.add(path + ": array length differs"); return; }
            for (int i = 0; i < Array.getLength(a); i++) diff(Array.get(a,i), Array.get(b,i), path + "[" + i + "]", seen, result); return;
        }
        if (a instanceof List<?> && b instanceof List<?>) {
            List<?> left=(List<?>)a,right=(List<?>)b; if (left.size()!=right.size()) { result.add(path + ": list size " + left.size() + " -> " + right.size()); return; }
            for (int i=0;i<left.size();i++) diff(left.get(i),right.get(i),path+"["+i+"]",seen,result);return;
        }
        if (a instanceof Map<?,?> && b instanceof Map<?,?>) {
            Map<?,?> left=(Map<?,?>)a,right=(Map<?,?>)b; if (!left.keySet().equals(right.keySet())) { result.add(path+": map keys differ"); return; }
            for (Object key:left.keySet()) diff(left.get(key),right.get(key),path+"["+key+"]",seen,result);return;
        }
        if (a.getClass()!=b.getClass()) { result.add(path + ": class " + a.getClass().getName() + " -> " + b.getClass().getName());return; }
        for (Field field:fields(a.getClass())) {
            if (field.getDeclaringClass()==SessionSnapshot.class&&field.getName().equals("formatVersion"))continue;
            diff(field.get(a),field.get(b),path+"."+field.getName(),seen,result);
        }
    }
    private static String describe(Object value) { return value == null ? "null" : value.getClass().getSimpleName() + "(" + value + ")"; }
}
