/* Copyright (C) 2026 HoDoKu contributors. GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;

/**
 * Fixed-fixture comparison harness. Synthetic MouseEvents use the real listeners.
 * Offscreen paint is measured separately from visible Swing repaint completion.
 * No measurement here observes the compositor or physical mouse-to-screen time.
 */
public final class InteractionSpeedBenchmark {
    private static final int SIZE = 900;
    private static final String[] FIXTURES = {"light", "standard-six-colors", "freehand-80x300", "standard-with-hint"};
    private static final Map<String, List<Double>> TIMES = new LinkedHashMap<String, List<Double>>();
    private static final Map<String, Field> FIELDS = new LinkedHashMap<String, Field>();
    private static MainFrame frame;
    private static SudokuPanel panel;
    private static JFrame window;
    private static BufferedImage image;
    private static List<DoodleStroke> template;
    private static String fixture;
    private static Method createMark, copyInk, snapshot, candidatePoint, drawDoodles, reconcile, drawComposite, timeline, timeKey;
    private static Object blackhole;
    private static long eventTime;
    private static boolean recording;
    private static boolean diagnostics;
    private static StatePaintRequest diagnosticRequest;
    private static final List<String> DETAILS = new ArrayList<String>();
    private static final int SAMPLES = Integer.getInteger("hodoku.benchmark.samples", 80);
    private static final int WARMUP = Integer.getInteger("hodoku.benchmark.warmup", 20);
    private static final boolean RAW = Boolean.parseBoolean(System.getProperty("hodoku.benchmark.raw", "true"));

    private interface Op { void run() throws Exception; }

    private static <T> T edt(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<T>(callable);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static Method method(String name, Class<?>... arguments) throws Exception {
        Method value = SudokuPanel.class.getDeclaredMethod(name, arguments);
        value.setAccessible(true);
        return value;
    }

    private static Object field(String name) throws Exception {
        Field value = FIELDS.get(name);
        if (value == null) {
            value = SudokuPanel.class.getDeclaredField(name);
            value.setAccessible(true);
            FIELDS.put(name, value);
        }
        return value.get(panel);
    }

    private static void set(String name, Object value) throws Exception {
        field(name);
        FIELDS.get(name).set(panel, value);
    }

    @SuppressWarnings("unchecked")
    private static List<DoodleStroke> ink() throws Exception { return (List<DoodleStroke>) field("doodleStrokes"); }

    private static void record(String metric, long nanos) {
        if (!recording) return;
        String name = fixture + "/" + metric;
        List<Double> values = TIMES.get(name);
        if (values == null) { values = new ArrayList<Double>(); TIMES.put(name, values); }
        double millis = nanos / 1e6;
        values.add(millis);
    }

    private static void measure(String metric, Op operation) throws Exception {
        long start = System.nanoTime();
        operation.run();
        record(metric, System.nanoTime() - start);
    }

    private static void paint() {
        Graphics2D graphics = image.createGraphics();
        try { panel.paint(graphics); } finally { graphics.dispose(); }
    }

    private static Point candidate(int cell, int digit) throws Exception {
        Point2D point = (Point2D) candidatePoint.invoke(panel, cell, digit, (Integer) field("cellSize"));
        return new Point((int) point.getX(), (int) point.getY());
    }

    private static void event(int id, int button, int modifiers, Point point, long when) {
        panel.dispatchEvent(new MouseEvent(panel, id, when, modifiers, point.x, point.y, 1, false, button));
    }

    private static void click(int button, Point point, long when) {
        event(MouseEvent.MOUSE_PRESSED, button, 0, point, when);
        event(MouseEvent.MOUSE_RELEASED, button, 0, point, when + 8);
    }

    private static void initialize() throws Exception {
        ApplicationAppearance.initialize(AppearanceMode.LIGHT);
        frame = new MainFrame(null);
        panel = frame.getSudokuPanel();
        if (diagnostics) {
            SudokuPanel original = panel;
            panel = new ObservedPanel(frame);
            panel.setCellZoomPanel(original.getCellZoomPanel());
            panel.getCellZoomPanel().setSudokuPanel(panel);
            Field framePanel = MainFrame.class.getDeclaredField("sudokuPanel");
            framePanel.setAccessible(true); framePanel.set(frame, panel);
        }
        panel.setSudoku((String) null);
        panel.getSudoku().setSudoku(new String(new char[81]).replace('\0', '0'));
        panel.setShowCandidates(true);
        panel.setAnnotationTool(AnnotationTool.DOODLE);
        panel.getCellZoomPanel().selectPaletteGroup(0);
        panel.setSize(SIZE, SIZE);
        image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        createMark = method("createCandidateHypothesisMark", int.class, int.class, int.class, int.class, Color.class);
        copyInk = method("copyDoodles", List.class);
        snapshot = method("snapshotMappedSingle");
        candidatePoint = method("getCandKoord", int.class, int.class, int.class);
        drawDoodles = method("drawDoodles", Graphics2D.class, int.class, int.class, boolean.class);
        reconcile = method("reconcileDoodleComposition");
        drawComposite = method("drawDoodleComposite", Graphics2D.class, DoodleStroke.class, List.class, int.class, int.class);
        timeline = method("refreshAnnotationTimeline");
        timeKey = method("doodleTimeKey", DoodleStroke.class);
        paint();
        System.out.printf(Locale.ROOT, "ENV java=%s os=%s arch=%s processors=%d panel=%dx%d board=blank-all-candidates appearance=LIGHT warmup=%d samples=%d doublePressDelta=80ms dragPoints=12%n",
                System.getProperty("java.version"), System.getProperty("os.version"), System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(), panel.getWidth(), panel.getHeight(), WARMUP, SAMPLES);
        System.out.println("SCOPE handler=synthetic-events-through-real-Swing-listeners; offscreen=BufferedImage; visible=posted-EDT-task-to-first-state-Swing-paintDirtyRegions; compositor=unmeasured");
    }

    private static void buildFixture(String name) throws Exception {
        fixture = name;
        panel.flushMappedClick();
        panel.cancelAnnotationToolInteractionOnDeactivation();
        ink().clear();
        panel.setStep(null);
        set("doodleHypothesisConsumedMask", 63);
        template = new ArrayList<DoodleStroke>();
        if (name.startsWith("standard")) {
            Color[] colors = {Color.RED, Color.BLUE, Color.MAGENTA, Color.ORANGE, Color.CYAN, new Color(90, 50, 190)};
            // 120 targets x 6 sources. Even targets circle, odd targets cross.
            // Cell 80/digit 9 is deliberately left free for measured mutations.
            for (int target = 0; target < 120; target++) {
                int cell = target / 3, digit = target % 3 + 1, kind = target % 2 + 1;
                for (int group = 0; group < 6; group++) {
                    DoodleStroke mark = (DoodleStroke) createMark.invoke(panel, cell, digit, kind, group, colors[group]);
                    if (target == 0 || target == 2) {
                        mark.setHypothesisStart(true);
                        mark.setHypothesisConditionId(group + 1);
                        mark.setHypothesisGroupKind(1);
                    }
                    if (target % 12 == 0 && group == 0) {
                        mark.setConclusionOutlined(true);
                        mark.setConclusionSourceMask(63);
                    }
                    template.add(mark);
                }
            }
        } else if (name.equals("freehand-80x300")) {
            // Same 80 x 300 normalized-points fixture as AnnotationPipelineLatencyProbe.
            for (int stroke = 0; stroke < 80; stroke++) {
                DoodleStroke mark = new DoodleStroke(Color.BLUE, .002f);
                for (int point = 0; point < 300; point++)
                    mark.getPoints().add(new DoodlePoint((point % 100) / 120.0,
                            (stroke % 40) / 50.0 + (point % 7) / 1000.0));
                template.add(mark);
            }
        }
        if (name.equals("standard-with-hint")) {
            SolutionStep hint = new SolutionStep(SolutionType.LOCKED_CANDIDATES_2);
            hint.addIndex(0); hint.addIndex(1); hint.addValue(1); hint.addCandidateToDelete(9, 1);
            panel.setStep(hint);
        }
        reset();
        recording = true;
        measure("first-fixture-offscreen-paint", () -> paint());
        recording = false;
        int points = 0, starts = 0, outlines = 0;
        for (DoodleStroke mark : ink()) {
            points += mark.getPoints().size();
            if (mark.isHypothesisStart()) starts++;
            if (mark.isConclusionOutlined()) outlines++;
        }
        long content = 1;
        for (DoodleStroke mark : ink()) {
            content = content * 31 + mark.getColor().getRGB();
            content = content * 31 + Float.floatToIntBits(mark.getWidthFactor());
            content = content * 31 + mark.getAnchorCell() * 1000 + mark.getAnchorDigit() * 100
                    + mark.getCandidateMarkKind() * 10 + mark.getThoughtGroup();
            for (DoodlePoint point : mark.getPoints()) {
                content = content * 31 + Double.doubleToLongBits(point.getX());
                content = content * 31 + Double.doubleToLongBits(point.getY());
            }
        }
        System.out.printf(Locale.ROOT, "FIXTURE %s strokes=%d points=%d starts=%d outlines=%d hint=%s panel=%dx%d content=%s%n",
                name, ink().size(), points, starts, outlines, name.equals("standard-with-hint"), panel.getWidth(), panel.getHeight(), Long.toHexString(content));
        if (name.startsWith("standard") && (ink().size() != 720 || starts != 12 || outlines != 10
                || panel.getDoodleMarkSourceMask(0, 1, 1) != 63)) throw new AssertionError("dense fixture drift");
    }

    @SuppressWarnings("unchecked")
    private static void reset() throws Exception {
        panel.flushMappedClick();
        panel.cancelAnnotationToolInteractionOnDeactivation();
        ink().clear();
        ink().addAll((List<DoodleStroke>) copyInk.invoke(panel, template));
        for (String name : new String[]{"doodleUndoStack", "doodleRedoStack", "doodleHypothesisUndoMasks", "doodleHypothesisRedoMasks"})
            ((Stack<?>) field(name)).clear();
        set("doodleHypothesisConsumedMask", 63);
        // Monotonic synthetic press times avoid accidental pairing across trials.
        eventTime = System.currentTimeMillis() + 1000;
    }

    private static void gesture(String name) throws Exception {
        if (field("annotationTool") != AnnotationTool.DOODLE) throw new AssertionError("benchmark left P tool");
        Point target = candidate(80, 9);
        long start = System.nanoTime();
        if (name.equals("single")) {
            click(1, target, eventTime);
        } else if (name.equals("double-rollback")) {
            click(1, target, eventTime);
            click(1, target, eventTime + 80);
        } else {
            Point first = new Point(150, 150);
            if (name.equals("rectangle-erase")) click(3, first, eventTime);
            long when = eventTime + (name.equals("rectangle-erase") ? 80 : 0);
            event(MouseEvent.MOUSE_PRESSED, 3, 0, first, when);
            for (int i = 1; i <= 12; i++) {
                final Point point = new Point(150 + i * 25, 150 + i * 15);
                final long dragWhen = when + 8 + i;
                measure(name + "-drag-handler", () -> event(MouseEvent.MOUSE_DRAGGED, MouseEvent.NOBUTTON,
                        InputEvent.BUTTON3_DOWN_MASK, point, dragWhen));
                if (i == 1) {
                    String expected = name.equals("rectangle-erase") ? "RECT_ERASER" : "CIRCLE_ERASER";
                    if (!field("doodleGesture").toString().equals(expected))
                        throw new AssertionError("wrong measured drag path: " + field("doodleGesture"));
                }
            }
            event(MouseEvent.MOUSE_RELEASED, 3, 0, new Point(450, 330), when + 28);
        }
        record(name + "-handler", System.nanoTime() - start);
        if (name.equals("single") || name.equals("double-rollback")) {
            int expected = name.equals("single") ? DoodleStroke.MARK_TRUE_CIRCLE : DoodleStroke.MARK_FALSE_CROSS;
            int found = 0;
            for (DoodleStroke mark : ink()) if (mark.getAnchorCell() == 80 && mark.getAnchorDigit() == 9) {
                if (mark.getCandidateMarkKind() != expected) throw new AssertionError("click measured wrong mutation");
                found++;
            }
            if (found != 1 || ink().size() != template.size() + 1)
                throw new AssertionError("click left first-click state or did not edit ink");
        }
    }

    private static void offscreenFixture() throws Exception {
        String[] gestures = {"single", "double-rollback", "rectangle-erase", "sweep-erase"};
        for (String operation : gestures) {
            for (int trial = -WARMUP; trial < SAMPLES; trial++) {
                reset();
                recording = trial >= 0;
                gesture(operation);
                panel.flushMappedClick();
                measure(operation + "-offscreen-paint", () -> paint());
            }
        }
        reset();
        for (int trial = -WARMUP; trial < SAMPLES; trial++) {
            recording = trial >= 0;
            measure("unmodified-offscreen-paint", () -> paint());
        }
        // Independent cost assays run outside a complete paint frame. They expose
        // baseline cost, but do not model optimized per-frame indices or add up to total paint.
        for (int trial = -WARMUP; trial < SAMPLES; trial++) {
            recording = trial >= 0;
            measure("cost-copy-ink", () -> blackhole = copyInk.invoke(panel, ink()));
            measure("cost-mapped-snapshot", () -> blackhole = snapshot.invoke(panel));
            measure("cost-mainframe-check", () -> frame.check());
            measure("cost-reconcile", () -> reconcile.invoke(panel));
            measure("cost-729-source-lookups", () -> {
                int value = 0;
                for (int cell = 0; cell < 81; cell++) for (int digit = 1; digit <= 9; digit++)
                    value ^= panel.getDoodleMarkSourceMask(cell, digit, 2);
                blackhole = value;
            });
            measure("cost-draw-doodles-direct", () -> {
                Graphics2D graphics = image.createGraphics();
                try { drawDoodles.invoke(panel, graphics, SIZE, SIZE, false); }
                finally { graphics.dispose(); }
            });
            if (fixture.startsWith("standard")) measure("cost-120-composites-direct", () -> {
                Graphics2D graphics = image.createGraphics();
                try { for (int target = 0; target < 120; target++)
                    drawComposite.invoke(panel, graphics, ink().get(target * 6), ink(), SIZE, SIZE); }
                finally { graphics.dispose(); }
            });
        }
        recording = false;
    }

    private static long stateSignature() throws Exception {
        long signature = ink().size();
        for (DoodleStroke mark : ink()) signature = signature * 31 + mark.getAnchorCell() * 1000
                + mark.getAnchorDigit() * 100 + mark.getCandidateMarkKind() * 10 + mark.getThoughtGroup()
                + mark.getPoints().size() * 37;
        return signature;
    }

    private static final class StatePaintRequest {
        final CountDownLatch painted = new CountDownLatch(1);
        final String operation;
        final long posted;
        long handlerEnd, signature;
        boolean armed;
        int dirtyAdds, managerPasses, panelPaints;
        long panelNanos;
        String graphics;
        Throwable failure;
        StatePaintRequest(String operation, long posted) { this.operation = operation; this.posted = posted; }
    }

    /** Test-only subclass: forwards every paint to the product implementation. */
    private static final class ObservedPanel extends SudokuPanel {
        ObservedPanel(MainFrame owner) { super(owner); }
        @Override protected void paintComponent(Graphics graphics) {
            StatePaintRequest request = diagnosticRequest;
            boolean measured = request != null && request.armed;
            if (measured) {
                request.panelPaints++;
                if (request.graphics == null) request.graphics = graphicsDescription((Graphics2D) graphics);
            }
            long start = System.nanoTime();
            super.paintComponent(graphics);
            if (measured) {
                long elapsed = System.nanoTime() - start;
                request.panelNanos += elapsed;
                record(request.operation + "-diagnostic-panel-paint", elapsed);
            }
        }
    }

    private static String graphicsDescription(Graphics2D graphics) {
        String surface = "unavailable";
        try {
            Field data = Class.forName("sun.java2d.SunGraphics2D").getDeclaredField("surfaceData");
            data.setAccessible(true);
            Object value = data.get(graphics);
            Method destination = Class.forName("sun.java2d.SurfaceData").getMethod("getDestination");
            destination.setAccessible(true);
            Object target = destination.invoke(value);
            surface = value.getClass().getName() + " destination=" + (target == null ? "null" : target.getClass().getName());
        } catch (ReflectiveOperationException failure) { surface = failure.getClass().getSimpleName(); }
        return "class=" + graphics.getClass().getName() + " surface=" + surface
                + " config=" + graphics.getDeviceConfiguration().getClass().getName()
                + " transform=" + graphics.getTransform() + " clip=" + graphics.getClipBounds()
                + " composite=" + graphics.getComposite() + " hints=" + graphics.getRenderingHints();
    }

    private static final class StatePaintManager extends RepaintManager {
        private boolean panelDirty;
        private StatePaintRequest request;
        @Override public synchronized void addDirtyRegion(JComponent component, int x, int y, int width, int height) {
            if (component == panel && width > 0 && height > 0) {
                panelDirty = true;
                if (request != null) request.dirtyAdds++;
            }
            super.addDirtyRegion(component, x, y, width, height);
        }
        @Override public void paintDirtyRegions() {
            StatePaintRequest captured = request;
            if (captured != null && captured.armed) captured.managerPasses++;
            boolean contains = panelDirty && captured != null && captured.armed;
            panelDirty = false;
            long start = System.nanoTime();
            try {
                if (contains && stateSignature() != captured.signature)
                    throw new AssertionError("state changed before first Swing repaint");
                super.paintDirtyRegions();
                if (contains) {
                    record(captured.operation + "-visible-state-to-paint-start", start - captured.handlerEnd);
                    record(captured.operation + "-visible-post-to-paint-start", start - captured.posted);
                    record(captured.operation + "-visible-paint-dirty-regions", System.nanoTime() - start);
                    record(captured.operation + "-visible-post-to-paint-end", System.nanoTime() - captured.posted);
                    if (diagnostics && recording) DETAILS.add("DETAIL " + fixture + "/" + captured.operation
                            + " dirtyAdds=" + captured.dirtyAdds + " managerPasses=" + captured.managerPasses
                            + " panelPaints=" + captured.panelPaints + " panelMs=" + captured.panelNanos / 1e6
                            + " " + captured.graphics);
                }
            } catch (Throwable failure) {
                if (captured != null) captured.failure = failure;
                else throw new RuntimeException(failure);
            } finally {
                if (contains) { request = null; diagnosticRequest = null; captured.painted.countDown(); }
            }
        }
    }

    private static void visible() throws Exception {
        final StatePaintManager manager = new StatePaintManager();
        edt(() -> {
            initialize();
            RepaintManager.setCurrentManager(manager);
            window = new JFrame("HoDoKu interaction benchmark — isolated synthetic input");
            window.setAutoRequestFocus(false);
            panel.setPreferredSize(new Dimension(SIZE, SIZE));
            window.setContentPane(panel);
            window.pack();
            window.setLocation(25, 25);
            window.setVisible(true);
            if (panel.getWidth() != SIZE || panel.getHeight() != SIZE) throw new AssertionError("window panel size drift");
            System.out.println("VISIBLE window=" + window.getSize() + " logicalPanel=" + panel.getSize()
                    + " deviceTransform=" + window.getGraphicsConfiguration().getDefaultTransform()
                    + " deviceBounds=" + window.getGraphicsConfiguration().getBounds()
                    + " windowBounds=" + window.getBounds() + " opaque=" + window.isOpaque()
                    + " doubleBuffered=" + manager.isDoubleBufferingEnabled()
                    + " maximumBuffer=" + manager.getDoubleBufferMaximumSize());
            return null;
        });
        String[] names = diagnostics ? new String[]{"light", "freehand-80x300"} : FIXTURES;
        for (String name : names) {
            edt(() -> { buildFixture(name); return null; });
            for (String operation : new String[]{"single", "double-rollback"}) {
                for (int trial = -WARMUP; trial < SAMPLES; trial++) {
                    edt(() -> { reset(); panel.repaint(); manager.paintDirtyRegions(); return null; });
                    recording = trial >= 0;
                    final StatePaintRequest request = new StatePaintRequest(operation, System.nanoTime());
                    SwingUtilities.invokeLater(() -> {
                        try {
                            record(operation + "-visible-queue", System.nanoTime() - request.posted);
                            manager.request = request;
                            diagnosticRequest = request;
                            gesture(operation);
                            request.handlerEnd = System.nanoTime();
                            panel.flushMappedClick();
                            request.signature = stateSignature();
                            request.armed = true;
                            panel.repaint();
                        } catch (Throwable failure) {
                            request.failure = failure;
                            request.painted.countDown();
                        }
                    });
                    if (!request.painted.await(15, TimeUnit.SECONDS)) throw new AssertionError("first-state Swing repaint timeout");
                    if (request.failure != null) throw new AssertionError(request.failure);
                }
            }
        }
    }

    private static void summarize() {
        for (String detail : DETAILS) System.out.println(detail);
        for (Map.Entry<String, List<Double>> entry : TIMES.entrySet()) {
            if (RAW) for (int i = 0; i < entry.getValue().size(); i++)
                System.out.printf(Locale.ROOT, "SAMPLE %s n=%d ms=%.6f%n", entry.getKey(), i + 1, entry.getValue().get(i));
            List<Double> samples = new ArrayList<Double>(entry.getValue());
            Collections.sort(samples);
            int count = samples.size();
            System.out.printf(Locale.ROOT, "SUMMARY %s n=%d p50=%.6fms p95=%.6fms max=%.6fms%n", entry.getKey(), count,
                    samples.get((int) Math.ceil(count * .50) - 1), samples.get((int) Math.ceil(count * .95) - 1), samples.get(count - 1));
        }
    }

    private static void saveImage() throws Exception {
        Path output = Paths.get(System.getProperty("hodoku.benchmark.output",
                "build/interaction-speed-20260930/benchmark/images"));
        Files.createDirectories(output);
        paint();
        Path png = output.resolve(fixture + ".png");
        ImageIO.write(image, "png", png.toFile());
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int pixel = image.getRGB(x, y);
            hash.update((byte) (pixel >>> 24)); hash.update((byte) (pixel >>> 16));
            hash.update((byte) (pixel >>> 8)); hash.update((byte) pixel);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : hash.digest()) hex.append(String.format(Locale.ROOT, "%02x", value & 255));
        System.out.println("IMAGE " + fixture + " path=" + png.toAbsolutePath() + " pixelSHA256=" + hex);
    }

    private static void timelineCosts() throws Exception {
        for (int trial = -WARMUP; trial < SAMPLES; trial++) {
            recording = trial >= 0;
            measure("cost-refresh-timeline", () -> timeline.invoke(panel));
            measure("cost-all-ink-timekeys", () -> {
                for (DoodleStroke mark : ink()) blackhole = timeKey.invoke(panel, mark);
            });
        }
        recording = false;
    }

    public static void main(String[] arguments) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        try {
            diagnostics = arguments.length > 0 && arguments[0].equals("visible-diagnostics");
            if (arguments.length > 0 && (arguments[0].equals("visible") || diagnostics)) visible();
            else edt(() -> {
                initialize();
                for (String name : FIXTURES) {
                    buildFixture(name);
                    if (arguments.length > 0 && arguments[0].equals("images")) saveImage();
                    else if (arguments.length > 0 && arguments[0].equals("timeline-cost")) timelineCosts();
                    else offscreenFixture();
                }
                return null;
            });
            summarize();
        } catch (Throwable failure) {
            failure.printStackTrace(); System.exit(1);
        } finally {
            edt(() -> { if (window != null) window.dispose(); if (frame != null) frame.dispose(); return null; });
        }
        System.exit(0);
    }
}
