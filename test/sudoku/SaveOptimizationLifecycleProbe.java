/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * One probe bytecode runs against the fixed old app JAR and the new app JAR.
 * There are no link-time references to new persistence or coordinate classes.
 * Fixture construction is outside CLOSE_START; the actual close remains native Swing.
 */
public final class SaveOptimizationLifecycleProbe {
    private static final String PUZZLE = "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    private static final String SOLUTION = "534678912672195348198342567859761423426853791713924856961537284287419635345286179";
    private static final int STROKES = 80, POINTS = 120, FRAMES = 1200;
    private static final CountDownLatch PAINTED = new CountDownLatch(1);
    private static final Map<Class<?>, List<PropertyDescriptor>> PROPERTIES = new HashMap<Class<?>, List<PropertyDescriptor>>();
    private static final MethodHandle POINT_COUNT = optional("pointCount", int.class);
    private static final MethodHandle POINT_X = optional("pointX", double.class, int.class);
    private static final MethodHandle POINT_Y = optional("pointY", double.class, int.class);
    private static final class PaintedFrame extends MainFrame {
        PaintedFrame() { super(null); }
        @Override public void paint(Graphics graphics) { super.paint(graphics); PAINTED.countDown(); }
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("MODE REPORT_DIRECTORY [HISTORY_LAYERS=100] required");
        String mode = args[0]; Path report = Paths.get(args[1]); Files.createDirectories(report);
        require(System.getProperty("hodoku.data.dir") != null, "isolated hodoku.data.dir is required");
        if (mode.equals("verify")) { verify(report); return; }
        final int historyLayers = args.length == 3 ? Integer.parseInt(args[2]) : 100;
        require(historyLayers >= 10 && historyLayers <= 100, "history layers must be 10..100");
        require(java.util.Arrays.asList("light", "light-idle", "cold", "idle", "edit-after-idle", "settings-only", "history", "history-idle", "completed").contains(mode), "unknown mode");
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        MainFrame[] ready = {null};
        SwingUtilities.invokeAndWait(() -> { ready[0] = new PaintedFrame(); ready[0].setVisible(true); });
        require(PAINTED.await(15, TimeUnit.SECONDS), "native main window did not finish painting");
        waitForObserver();
        SwingUtilities.invokeAndWait(() -> {
            try { seed(ready[0], mode, historyLayers); } catch (Exception error) { throw new RuntimeException(error); }
        });
        PingMonitor ping = new PingMonitor(report); ping.start();
        boolean idle = mode.equals("light-idle") || mode.equals("idle") || mode.equals("edit-after-idle") || mode.equals("settings-only") || mode.equals("history-idle");
        if (idle) Thread.sleep(7000); // Off EDT: the real quiet-period scheduler gets to run.
        if (mode.equals("edit-after-idle")) SwingUtilities.invokeAndWait(() -> {
            try { editStroke(ready[0], 0, 17); ready[0].check(); } catch (Exception error) { throw new RuntimeException(error); }
        });
        if (mode.equals("settings-only")) {
            SwingUtilities.invokeAndWait(() -> {
                try { ((AbstractButton) field(MainFrame.class, "showCandidatesMenuItem").get(ready[0])).doClick(0); }
                catch (Exception error) { throw new RuntimeException(error); }
            });
            Thread.sleep(7000); // Confirmed settings change has its own quiet period.
        }
        // Expectations are captured immediately before close; neither source reflection
        // nor hashing is included in the measured close interval.
        ping.pause();
        SwingUtilities.invokeAndWait(() -> {
            try {
                MainFrame frame = ready[0];
                SessionSnapshot expected = expected(frame);
                Properties manifest = manifest(expected);
                manifest.setProperty("mode", mode);
                manifest.setProperty("fixture.history.layers", String.valueOf(mode.startsWith("history") ? historyLayers : 0));
                manifest.setProperty("options.showCandidates", String.valueOf(Options.getInstance().isShowCandidates()));
                manifest.setProperty("expected.replay.frames", String.valueOf(frame.getReplayController().session().frameCount()));
                manifest.setProperty("expected.replay.elapsed.minimum", String.valueOf(frame.getReplayController().session().elapsedMillis));
                try (OutputStream out = Files.newOutputStream(report.resolve("expected.properties"))) { manifest.store(out, "Pre-close expected persisted fields"); }
                System.out.println("EXPECTED mode=" + mode + " gui_sha256=" + manifest.getProperty("gui.sha256")
                        + " savepoints_sha256=" + manifest.getProperty("savepoints.sha256") + " replay_frames=" + manifest.getProperty("expected.replay.frames"));
                JRootPane pane = frame.getRootPane(); pane.paintImmediately(0, 0, pane.getWidth(), pane.getHeight()); Toolkit.getDefaultToolkit().sync();
                long start = System.nanoTime();
                javax.swing.Timer feedback = new javax.swing.Timer(15, event -> {
                    for (Window window : Window.getWindows()) if (window instanceof JDialog && window.isVisible()
                            && ((JDialog) window).getTitle().equals(SaveFailure.text("closing"))) {
                        try { Files.write(report.resolve("first-feedback-ms"), String.valueOf((System.nanoTime() - start) / 1e6).getBytes(StandardCharsets.UTF_8)); }
                        catch (Exception error) { throw new RuntimeException(error); }
                        ((javax.swing.Timer) event.getSource()).stop();
                    }
                });
                feedback.start();
                ping.resume();
                System.out.println("CLOSE_START wall_epoch_ms=" + System.currentTimeMillis()); System.out.flush();
                frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
                throw new AssertionError("close did not terminate");
            } catch (Exception error) { throw new RuntimeException(error); }
        });
    }

    private static void waitForObserver() throws Exception {
        String file = System.getenv("HODOKU_OBSERVER_READY"); if (file == null) return;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!Files.exists(Paths.get(file)) && System.nanoTime() < deadline) Thread.sleep(10);
        require(Files.exists(Paths.get(file)), "external observer not ready");
    }

    private static void seed(MainFrame frame, String mode, int historyLayers) throws Exception {
        frame.setPuzzle(PUZZLE); SudokuPanel panel = frame.getSudokuPanel(); panel.setCell(0, 2, 4);
        frame.getReplayController().startNewAttempt();
        GuiState initial = state(frame, true); List<DoodleStroke> ink = new ArrayList<DoodleStroke>();
        int strokes = mode.startsWith("light") ? 0 : STROKES;
        int frames = mode.startsWith("light") ? 0 : FRAMES;
        for (int s = 0; s < strokes; s++) {
            DoodleStroke stroke = new DoodleStroke(new Color((s * 339) | 0xff000000, true), 1.0f);
            for (int p = 0; p < POINTS; p++) stroke.getPoints().add(new DoodlePoint((s % 9 + p / 140.0) / 9, (s / 9 + Math.sin(p / 10.0) * 0.1) / 9));
            ink.add(stroke);
        }
        initial.setDoodleStrokes(ink); initial.set();
        if (mode.startsWith("history")) {
            panel.setAnnotationTool(AnnotationTool.DOODLE);
            for (int h = 0; h < historyLayers; h++) {
                editStroke(frame, h % STROKES, h % POINTS);
                if (h == historyLayers / 4 - 1 || h == historyLayers / 2 - 1 || h == historyLayers * 3 / 4 - 1 || h == historyLayers - 1) {
                    // Includes annotations intentionally, to cover the heaviest legitimate native state.
                    GuiState saved = state(frame, true); saved.setName("保存验证 " + h); saved.setTimestamp(new Date(1000L + h));
                    frame.getSavePoints().add(saved);
                }
            }
            for (int undo = 0; undo < 10; undo++) panel.undoCurrentAnnotation();
            for (int redo = 0; redo < 5; redo++) panel.redoCurrentAnnotation();
            GuiState check = state(frame, true);
            require(check.getDoodleUndoHistory().size() == historyLayers - 5 && check.getDoodleRedoHistory().size() == 5, "native undo/redo fixture was not built");
            require(frame.getSavePoints().size() == 4, "savepoint fixture was not built");
        }
        ReplayBoard entered = new ReplayBoard(panel.getSudoku()); Sudoku2 blank = new Sudoku2(); blank.setSudoku(PUZZLE);
        ReplayBoard cleared = new ReplayBoard(blank); long now = System.currentTimeMillis();
        for (int i = 1; i <= frames; i++) frame.getReplayController().appendOperation(Collections.singletonList(
                new ReplayFrame(i, now, 0, "manual", "synthetic undo/reentry", i % 2 == 1 ? cleared : entered, null)));
        if (mode.equals("completed")) {
            for (int at = 0; at < 81; at++) if (panel.getSudoku().getValue(at) == 0) panel.setCell(at / 9, at % 9, SOLUTION.charAt(at) - '0');
            frame.getReplayController().afterAction();
        }
        frame.check();
    }

    @SuppressWarnings("unchecked")
    private static void editStroke(MainFrame frame, int strokeIndex, int pointIndex) throws Exception {
        SudokuPanel panel = frame.getSudokuPanel(); Method push = SudokuPanel.class.getDeclaredMethod("pushDoodleUndo"); push.setAccessible(true); push.invoke(panel);
        List<DoodleStroke> strokes = (List<DoodleStroke>) field(SudokuPanel.class, "doodleStrokes").get(panel);
        DoodleStroke stroke = strokes.get(strokeIndex); DoodlePoint point = stroke.getPoints().get(pointIndex);
        point.setX(Math.nextUp(point.getX())); stroke.setThoughtGroup(strokeIndex % 6);
        panel.repaint();
    }

    private static GuiState state(MainFrame frame, boolean annotations) {
        GuiState state = new GuiState(frame.getSudokuPanel(), frame.getSudokuPanel().getSolver(), frame.getSolutionPanel());
        state.setIncludeAnnotations(annotations); state.get(true); return state;
    }
    private static SessionSnapshot expected(MainFrame frame) throws Exception {
        SessionSnapshot snapshot = new SessionSnapshot(); snapshot.setGuiState(state(frame, true));
        snapshot.setSavePoints(new ArrayList<GuiState>(frame.getSavePoints()));
        snapshot.setActiveRow(frame.getSudokuPanel().getActiveRow()); snapshot.setActiveCol(frame.getSudokuPanel().getActiveCol());
        snapshot.setCompleted(frame.getSudokuPanel().getSudoku().isSolved() && frame.getSudokuPanel().getSudoku().checkSudoku());
        snapshot.setCompletedLevel(Options.getInstance().getActLevel()); snapshot.setCompletedGameMode(Options.getInstance().getGameMode().name());
        CompletionTransition completion = (CompletionTransition) field(MainFrame.class, "completionTransition").get(frame);
        snapshot.setHistoryEligible(completion.isEnabled()); snapshot.setCompletionRecorded(completion.isCompletedRecorded()); snapshot.setPreviouslySolved(completion.wasPreviouslySolved());
        snapshot.setReplayId(frame.getReplayController().session().id); snapshot.setReplayFrameCount(frame.getReplayController().session().frameCount());
        snapshot.setReplayElapsedMillis(frame.getReplayController().session().elapsedMillis); return snapshot;
    }

    private static Properties manifest(SessionSnapshot snapshot) throws Exception {
        Properties result = new Properties();
        result.setProperty("hash.schema", "native-set-membership-v1");
        for (PropertyDescriptor property : properties(GuiState.class)) result.setProperty("gui." + property.getName() + ".sha256", hash(property.getReadMethod().invoke(snapshot.getGuiState())));
        result.setProperty("gui.sha256", hash(new TreeMap<Object, Object>(result)));
        result.setProperty("savepoints.sha256", hash(snapshot.getSavePoints()));
        for (PropertyDescriptor property : properties(SessionSnapshot.class)) {
            String name = property.getName();
            if (name.equals("formatVersion") || name.equals("guiState") || name.equals("savePoints") || name.equals("replayElapsedMillis")) continue;
            result.setProperty("session." + name + ".sha256", hash(property.getReadMethod().invoke(snapshot)));
        }
        result.setProperty("current.strokes", String.valueOf(snapshot.getGuiState().getDoodleStrokes().size()));
        result.setProperty("undo.layers", String.valueOf(snapshot.getGuiState().getDoodleUndoHistory().size()));
        result.setProperty("redo.layers", String.valueOf(snapshot.getGuiState().getDoodleRedoHistory().size()));
        result.setProperty("savepoints.count", String.valueOf(snapshot.getSavePoints().size()));
        return result;
    }

    private static void verify(Path report) throws Exception {
        Properties expected = new Properties(); try (InputStream in = Files.newInputStream(report.resolve("expected.properties"))) { expected.load(in); }
        SessionSnapshot saved = new SessionStore(ApplicationPaths.getSessionFile()).load(); Properties actual = manifest(saved);
        List<String> changed = new ArrayList<String>();
        for (String key : actual.stringPropertyNames()) if (!actual.getProperty(key).equals(expected.getProperty(key))) changed.add(key);
        require(changed.isEmpty(), "persisted fields changed: " + changed);
        require(Boolean.parseBoolean(expected.getProperty("options.showCandidates")) == Options.getInstance().isShowCandidates(), "confirmed setting was not persisted");
        ReplaySession replay = ReplayStore.read(ApplicationPaths.getDataDirectory().toPath().resolve("replays").resolve(saved.getReplayId() + ".hrep"));
        require(replay.frameCount() == Integer.parseInt(expected.getProperty("expected.replay.frames")), "replay frame count changed");
        require(replay.elapsedMillis >= Long.parseLong(expected.getProperty("expected.replay.elapsed.minimum")), "final elapsed time was lost");
        ReplayFiles.validate(replay);
        Files.write(report.resolve("verified.txt"), ("PASS complete fields, coordinate bits, undo/redo, savepoints, settings, valid replay frames=" + replay.frameCount() + "\n").getBytes(StandardCharsets.UTF_8));
        System.out.println("VERIFIED complete fields/coordinate bits/undo-redo/savepoints/settings and " + replay.frameCount() + " valid frames");
    }

    private static Field field(Class<?> type, String name) throws Exception { Field result = type.getDeclaredField(name); result.setAccessible(true); return result; }
    private static List<PropertyDescriptor> properties(Class<?> type) throws Exception {
        List<PropertyDescriptor> cached = PROPERTIES.get(type); if (cached != null) return cached;
        List<PropertyDescriptor> result = new ArrayList<PropertyDescriptor>();
        for (PropertyDescriptor property : Introspector.getBeanInfo(type).getPropertyDescriptors())
            if (property.getReadMethod() != null && property.getWriteMethod() != null) result.add(property);
        result.sort(Comparator.comparing(PropertyDescriptor::getName)); PROPERTIES.put(type, result); return result;
    }
    private static String hash(Object value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        DataOutputStream out = new DataOutputStream(new DigestOutputStream(new OutputStream() { @Override public void write(int b) {} @Override public void write(byte[] b, int off, int len) {} }, digest));
        canonical(value, out, new IdentityHashMap<Object, Boolean>()); out.close(); StringBuilder result = new StringBuilder();
        for (byte b : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", b & 255)); return result.toString();
    }
    private static void canonical(Object value, DataOutputStream out, IdentityHashMap<Object, Boolean> path) throws Exception {
        if (value == null) { out.writeByte(0); return; }
        if (value instanceof String || value instanceof Enum<?> || value instanceof Number || value instanceof Boolean || value instanceof Character) {
            out.writeByte(1); out.writeUTF(value.getClass().getName());
            if (value instanceof Double) out.writeLong(Double.doubleToRawLongBits((Double) value));
            else if (value instanceof Float) out.writeInt(Float.floatToRawIntBits((Float) value));
            else { byte[] text = value.toString().getBytes(StandardCharsets.UTF_8); out.writeInt(text.length); out.write(text); } return;
        }
        if (value instanceof Color) { out.writeByte(2); out.writeInt(((Color) value).getRGB()); return; }
        if (value instanceof Date) { out.writeByte(3); out.writeLong(((Date) value).getTime()); return; }
        // Native equality/contains are defined by these masks. Iteration-cache
        // getters mutate values/anz/initialized, so hashing those getters was both
        // order-dependent and modified the live savepoint while building expected data.
        if (value.getClass() == SudokuSet.class || value.getClass() == SudokuSetBase.class) {
            out.writeByte(9); out.writeUTF(value.getClass().getName());
            out.writeLong(((SudokuSetBase)value).getMask1()); out.writeLong(((SudokuSetBase)value).getMask2()); return;
        }
        if (value.getClass() == SudokuSetShort.class) {
            out.writeByte(10); out.writeShort(((SudokuSetShort)value).getMask()); return;
        }
        require(path.put(value, Boolean.TRUE) == null, "cyclic fixture bean graph");
        try {
            if (value instanceof DoodleStroke) {
                out.writeByte(4); DoodleStroke stroke = (DoodleStroke) value;
                for (PropertyDescriptor property : properties(DoodleStroke.class)) if (!property.getName().equals("points")) {
                    out.writeUTF(property.getName()); canonical(property.getReadMethod().invoke(stroke), out, path);
                }
                int count = count(stroke); out.writeInt(count);
                for (int i = 0; i < count; i++) { out.writeLong(Double.doubleToRawLongBits(x(stroke, i))); out.writeLong(Double.doubleToRawLongBits(y(stroke, i))); }
            } else if (value.getClass().isArray()) {
                out.writeByte(5); out.writeUTF(value.getClass().getName()); out.writeInt(Array.getLength(value));
                for (int i = 0; i < Array.getLength(value); i++) canonical(Array.get(value, i), out, path);
            } else if (value instanceof List<?>) {
                out.writeByte(6); out.writeInt(((List<?>) value).size()); for (Object item : (List<?>) value) canonical(item, out, path);
            } else if (value instanceof Map<?, ?>) {
                out.writeByte(7); out.writeInt(((Map<?, ?>) value).size());
                TreeMap<Object, Object> sorted = new TreeMap<Object, Object>(); sorted.putAll((Map<?, ?>) value);
                for (Map.Entry<Object, Object> entry : sorted.entrySet()) { canonical(entry.getKey(), out, path); canonical(entry.getValue(), out, path); }
            } else {
                out.writeByte(8); out.writeUTF(value.getClass().getName());
                for (PropertyDescriptor property : properties(value.getClass())) { out.writeUTF(property.getName()); canonical(property.getReadMethod().invoke(value), out, path); }
            }
        } finally { path.remove(value); }
    }
    private static MethodHandle optional(String name, Class<?> result, Class<?>... parameters) {
        try { return MethodHandles.publicLookup().findVirtual(DoodleStroke.class, name, MethodType.methodType(result, parameters)); }
        catch (ReflectiveOperationException missing) { return null; }
    }
    private static int count(DoodleStroke stroke) { try { return POINT_COUNT == null ? stroke.getPoints().size() : (int) POINT_COUNT.invokeExact(stroke); } catch (Throwable failure) { throw new AssertionError(failure); } }
    private static double x(DoodleStroke stroke, int index) { try { return POINT_X == null ? stroke.getPoints().get(index).getX() : (double) POINT_X.invokeExact(stroke, index); } catch (Throwable failure) { throw new AssertionError(failure); } }
    private static double y(DoodleStroke stroke, int index) { try { return POINT_Y == null ? stroke.getPoints().get(index).getY() : (double) POINT_Y.invokeExact(stroke, index); } catch (Throwable failure) { throw new AssertionError(failure); } }

    /** One outstanding ping prevents an overloaded EDT from accumulating a queue. */
    private static final class PingMonitor {
        final Path report; final AtomicBoolean pending = new AtomicBoolean(); final AtomicLong count = new AtomicLong();
        final AtomicBoolean paused = new AtomicBoolean();
        final AtomicLong generation = new AtomicLong();
        final AtomicLong maximum = new AtomicLong(), sum = new AtomicLong(), over100ms = new AtomicLong();
        PingMonitor(Path report) { this.report = report; }
        void pause() { paused.set(true); generation.incrementAndGet(); }
        void resume() { generation.incrementAndGet(); paused.set(false); }
        void start() {
            Thread sampler = new Thread(() -> {
                while (true) {
                    if (!paused.get() && pending.compareAndSet(false, true)) {
                        long sampleGeneration = generation.get(), before = System.nanoTime(); SwingUtilities.invokeLater(() -> {
                            // An already queued sample must not include deliberate fixture hashing/painting.
                            try { if (!paused.get() && generation.get() == sampleGeneration) {
                                long elapsed = System.nanoTime() - before; count.incrementAndGet(); sum.addAndGet(elapsed); maximum.accumulateAndGet(elapsed, Math::max);
                                if (elapsed >= 100000000L) over100ms.incrementAndGet();
                            }} finally { pending.set(false); }
                        });
                    }
                    try { Thread.sleep(20); } catch (InterruptedException stop) { return; }
                }
            }, "save-probe-edt-ping"); sampler.setDaemon(true); sampler.setPriority(Thread.MIN_PRIORITY); sampler.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { Files.write(report.resolve("edt-ping.properties"), ("count=" + count.get() + "\nmax_ms=" + maximum.get() / 1e6 + "\nmean_ms=" + sum.get() / 1e6 / Math.max(1, count.get()) + "\nover_100ms=" + over100ms.get() + "\n").getBytes(StandardCharsets.UTF_8)); }
                catch (Exception failure) { failure.printStackTrace(); }
            }, "save-probe-ping-report"));
        }
    }
}
