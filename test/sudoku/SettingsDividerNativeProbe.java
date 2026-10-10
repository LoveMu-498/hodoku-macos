/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Two isolated native processes: automatic divider persistence, then actual startup restoration. */
public final class SettingsDividerNativeProbe {
    private static final CountDownLatch PAINTED = new CountDownLatch(1);
    private static final class PaintedFrame extends MainFrame {
        PaintedFrame() { super(null); }
        @Override public void paint(Graphics graphics) { super.paint(graphics); PAINTED.countDown(); }
    }
    private interface UiRead<T> { T get() throws Exception; }
    private interface Check { boolean done() throws Exception; }

    /** write REPORT_DIRECTORY, then verify REPORT_DIRECTORY in a fresh JVM using the same isolated data. */
    public static void main(String[] args) throws Exception {
        require(args.length == 2 && ("write".equals(args[0]) || "verify".equals(args[0])),
                "Usage: SettingsDividerNativeProbe write|verify REPORT_DIRECTORY");
        String isolated = System.getProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY);
        require(isolated != null && !isolated.trim().isEmpty(), "isolated hodoku.data.dir required");
        Path report = Paths.get(args[1]); Files.createDirectories(report);
        Properties result = new Properties(); MainFrame[] frame = {null}; Throwable failure = null;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            if ("write".equals(args[0])) {
                Options.getInstance().setSaveWindowLayout(true);
                Options.getInstance().setShowHintPanel(true);
                Options.getInstance().setInitialDisplayMode(1); // Summary makes the inner divider meaningful.
            }
            Properties expected = new Properties();
            if ("verify".equals(args[0])) {
                try (InputStream input = Files.newInputStream(report.resolve("divider.expected.properties"))) {
                    expected.load(input);
                }
                Options loaded = Options.getInstance();
                require(loaded.getInitialHorzDividerLoc() == integer(expected, "outer"), "startup options lost outer divider");
                require(loaded.getInitialVertDividerLoc() == integer(expected, "inner"), "startup options lost inner divider");
                require(loaded.isSaveWindowLayout() && loaded.isShowHintPanel() && loaded.getInitialDisplayMode() == 1,
                        "startup options lost the visible layout mode");
            }
            SwingUtilities.invokeAndWait(() -> { frame[0] = new PaintedFrame(); frame[0].setVisible(true); });
            require(PAINTED.await(15, TimeUnit.SECONDS), "native frame did not paint");
            settleLayout(frame[0]);
            JSplitPane outer = readUi(() -> (JSplitPane) field(MainFrame.class, "outerSplitPane").get(frame[0]));
            SplitPanel inner = readUi(() -> (SplitPanel) field(MainFrame.class, "splitPanel").get(frame[0]));
            require(readUi(inner::hasRight), "inner divider has no visible right panel");

            if ("write".equals(args[0])) {
                int[] outerMove = readUi(() -> {
                    int before = outer.getDividerLocation();
                    int target = different(before, 47, 100, outer.getHeight() - 160);
                    outer.setDividerLocation(target); frame[0].validate();
                    return new int[]{before, target};
                });
                settleLayout(frame[0]);
                require(readUi(outer::getDividerLocation) == outerMove[1], "layout replaced the requested outer divider");
                await(() -> {
                    Options disk = readDisk(); return disk != null && disk.getInitialHorzDividerLoc() == outerMove[1];
                }, 30000, "outer divider was not committed by default background settings save");
                result.setProperty("outer.before", String.valueOf(outerMove[0]));
                result.setProperty("outer.saved", String.valueOf(outerMove[1]));

                long beforeWrites = SettingsPersistence.successfulWritesForTest();
                int[] innerMove = readUi(() -> {
                    int before = inner.getDividerLocation();
                    int target = different(before, 53, 100, inner.getWidth() - 180);
                    // A continuous drag emits a burst of property changes; the real listener receives each one.
                    for (int index = 0; index < 30; index++)
                        inner.setDividerLocation(before + (target - before) * (index + 1) / 30);
                    inner.setDividerLocation(target); frame[0].validate();
                    return new int[]{before, target};
                });
                settleLayout(frame[0]);
                require(readUi(inner::getDividerLocation) == innerMove[1], "layout replaced the requested inner divider");
                await(() -> {
                    Options disk = readDisk(); return disk != null && disk.getInitialHorzDividerLoc() == outerMove[1]
                            && disk.getInitialVertDividerLoc() == innerMove[1];
                }, 30000, "inner divider was not committed by default background settings save");
                long burstWrites = SettingsPersistence.successfulWritesForTest() - beforeWrites;
                require(burstWrites == 1, "one divider burst triggered " + burstWrites + " configuration writes");
                result.setProperty("inner.before", String.valueOf(innerMove[0]));
                result.setProperty("inner.saved", String.valueOf(innerMove[1]));
                result.setProperty("inner.burst_writes", String.valueOf(burstWrites));
                expected.setProperty("outer", String.valueOf(outerMove[1]));
                expected.setProperty("inner", String.valueOf(innerMove[1]));
                Rectangle bounds = readUi(() -> frame[0].getBounds());
                expected.setProperty("width", String.valueOf(bounds.width));
                expected.setProperty("height", String.valueOf(bounds.height));
                try (OutputStream output = Files.newOutputStream(report.resolve("divider.expected.properties"))) {
                    expected.store(output, "Positions committed before frame disposal; no explicit settings save or quit save was invoked");
                }
            } else {
                int actualOuter = readUi(outer::getDividerLocation), actualInner = readUi(inner::getDividerLocation);
                result.setProperty("outer.actual", String.valueOf(actualOuter));
                result.setProperty("inner.actual", String.valueOf(actualInner));
                result.setProperty("outer.expected", expected.getProperty("outer"));
                result.setProperty("inner.expected", expected.getProperty("inner"));
                require(actualOuter == integer(expected, "outer"), "visible startup outer divider did not restore saved position");
                require(actualInner == integer(expected, "inner"), "visible startup inner divider did not restore saved position");
            }
            result.setProperty("status", "passed");
        } catch (Throwable error) {
            failure = error; result.setProperty("status", "failed"); result.setProperty("failure", String.valueOf(error));
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                SettingsPersistence.suspend();
                if (frame[0] != null) frame[0].dispose();
            });
            SwingUtilities.invokeAndWait(() -> {}); // Let WINDOW_CLOSED detach the replay and persistence listeners.
            try (OutputStream output = Files.newOutputStream(report.resolve("divider." + args[0] + ".properties"))) {
                result.store(output, "Real visible MainFrame divider persistence check");
            }
        }
        if (failure != null) { failure.printStackTrace(); System.exit(1); }
        System.out.println("PASS divider " + args[0] + ": " + result);
        System.exit(0); // Ends only this isolated test process, including its generator workers.
    }

    private static int different(int current, int delta, int minimum, int maximum) {
        require(maximum > minimum + delta, "test window is too small for a meaningful divider movement");
        int target = Math.max(minimum, Math.min(maximum, current - delta));
        if (target == current) target = Math.min(maximum, current + delta);
        require(target != current, "divider target did not move"); return target;
    }
    private static Options readDisk() {
        // A read-only decoder avoids changing AtomicFileIO's proof cache while the real writer is active.
        try (java.beans.XMLDecoder decoder = new java.beans.XMLDecoder(
                new java.io.BufferedInputStream(Files.newInputStream(ApplicationPaths.getConfigurationFile().toPath())))) {
            final Exception[] failure = {null}; decoder.setExceptionListener(error -> failure[0] = error);
            Object decoded = decoder.readObject();
            return failure[0] == null && decoded instanceof Options ? (Options) decoded : null;
        }
        catch (Exception unavailable) { return null; }
    }
    private static void settleLayout(MainFrame frame) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5), stableSince = 0;
        String previous = null;
        while (System.nanoTime() < deadline) {
            String now = readUi(() -> {
                JSplitPane outer = (JSplitPane) field(MainFrame.class, "outerSplitPane").get(frame);
                SplitPanel inner = (SplitPanel) field(MainFrame.class, "splitPanel").get(frame);
                return frame.getBounds() + "/" + outer.getSize() + "/" + outer.getDividerLocation()
                        + "/" + inner.getSize() + "/" + inner.getDividerLocation();
            });
            if (!now.equals(previous)) { previous = now; stableSince = System.nanoTime(); }
            else if (System.nanoTime() - stableSince >= TimeUnit.MILLISECONDS.toNanos(300)) return;
            Thread.sleep(25);
        }
        throw new AssertionError("native layout did not settle within five seconds");
    }
    private static <T> T readUi(UiRead<T> read) throws Exception {
        AtomicReference<T> result = new AtomicReference<T>(); AtomicReference<Exception> failure = new AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> { try { result.set(read.get()); } catch (Exception error) { failure.set(error); } });
        if (failure.get() != null) throw failure.get(); return result.get();
    }
    private static Field field(Class<?> owner, String name) throws Exception { Field result = owner.getDeclaredField(name); result.setAccessible(true); return result; }
    private static int integer(Properties source, String name) { return Integer.parseInt(source.getProperty(name)); }
    private static void await(Check check, int timeoutMillis, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!check.done() && System.nanoTime() < deadline) Thread.sleep(40);
        require(check.done(), message);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
