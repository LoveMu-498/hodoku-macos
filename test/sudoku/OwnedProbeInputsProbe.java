/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Headless fault injection plus an actual SIGTERM to this probe's own child JVM. */
public final class OwnedProbeInputsProbe {
    private static final int CONTROL = KeyEvent.VK_CONTROL, FOREIGN = KeyEvent.VK_ALT;
    private static final int BUTTON = InputEvent.BUTTON1_DOWN_MASK;
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Backend implements OwnedProbeInputs.Backend {
        final Set<Integer> keys = new HashSet<Integer>(), buttons = new HashSet<Integer>();
        final List<String> events = new ArrayList<String>();
        boolean failAfterPress, failOneRelease;
        public void keyPress(int key) {
            keys.add(key); events.add("key+" + key);
            if (failAfterPress) { failAfterPress = false; throw new IllegalStateException("injected after native press"); }
        }
        public void keyRelease(int key) { events.add("key-" + key); keys.remove(key); }
        public void mousePress(int button) { events.add("button+" + button); buttons.add(button); }
        public void mouseRelease(int button) {
            events.add("button-" + button);
            if (failOneRelease) { failOneRelease = false; throw new IllegalStateException("injected release failure"); }
            buttons.remove(button);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "--term-child".equals(args[0])) { termChild(Paths.get(args[1])); return; }
        require(args.length == 0, "Usage: OwnedProbeInputsProbe");
        Backend backend = new Backend(); backend.keys.add(FOREIGN);
        OwnedProbeInputs inputs = new OwnedProbeInputs(backend);
        AssertionError injected = new AssertionError("injected while Control and button held");
        Throwable caught = null;
        try (OwnedProbeInputs scope = inputs) {
            scope.keyPress(CONTROL); scope.mousePress(BUTTON);
            scope.keyRelease(FOREIGN); scope.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
            throw injected;
        } catch (Throwable failure) { caught = failure; }
        require(caught == injected, "cleanup replaced the injected test failure");
        require(!inputs.hasHeldInputs() && backend.buttons.isEmpty(), "mouse button remained held after failure");
        require(backend.keys.size() == 1 && backend.keys.contains(FOREIGN), "cleanup released an unowned key");
        require(!backend.events.contains("key-" + FOREIGN) && !backend.events.contains("button-" + InputEvent.BUTTON2_DOWN_MASK),
                "unowned release was sent to backend");
        require(backend.events.indexOf("button-" + BUTTON) < backend.events.indexOf("key-" + CONTROL),
                "cleanup changed modifiers before mouse release");
        int before = backend.events.size(); inputs.close();
        require(backend.events.size() == before, "repeated close injected duplicate releases");
        Field hook = OwnedProbeInputs.class.getDeclaredField("shutdownHook"); hook.setAccessible(true);
        require(!Runtime.getRuntime().removeShutdownHook((Thread) hook.get(inputs)), "closed scope retained shutdown hook");

        Backend afterPress = new Backend(); afterPress.failAfterPress = true;
        try (OwnedProbeInputs scope = new OwnedProbeInputs(afterPress)) {
            scope.keyPress(CONTROL); throw new AssertionError("expected injected press failure");
        } catch (IllegalStateException expected) { require(expected.getMessage().contains("after native press"), "wrong press error"); }
        require(afterPress.keys.isEmpty(), "exception after press escaped ownership tracking");

        Backend releaseFailure = new Backend(); releaseFailure.failOneRelease = true;
        OwnedProbeInputs retry = new OwnedProbeInputs(releaseFailure);
        retry.keyPress(CONTROL); retry.mousePress(BUTTON);
        try { retry.close(); throw new AssertionError("expected release failure"); }
        catch (IllegalStateException expected) { require(expected.getCause().getMessage().contains("release failure"), "cleanup cause lost"); }
        require(releaseFailure.keys.isEmpty() && retry.hasHeldInputs(), "one failed release prevented other cleanup or lost retry ownership");
        retry.close(); require(!retry.hasHeldInputs(), "failed release could not be retried");
        require(!Runtime.getRuntime().removeShutdownHook((Thread) hook.get(retry)), "retried close retained shutdown hook");
        boolean refused = false;
        try { retry.keyPress(CONTROL); } catch (IllegalStateException expected) { refused = true; }
        require(refused, "closed input scope allowed new input");

        Path childLog = Files.createTempDirectory("hodoku-owned-input-term-").resolve("events.txt");
        String java = new File(new File(System.getProperty("java.home"), "bin"), "java").getAbsolutePath();
        Process child = new ProcessBuilder(java, "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                OwnedProbeInputsProbe.class.getName(), "--term-child", childLog.toString())
                .redirectErrorStream(true).redirectOutput(childLog.resolveSibling("child.log").toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while ((!Files.exists(childLog) || !read(childLog).contains("ready")) && child.isAlive() && System.nanoTime() < deadline)
                Thread.sleep(20);
            require(child.isAlive() && Files.exists(childLog) && read(childLog).contains("ready"), "owned TERM fixture did not become ready");
            child.destroy();
            require(child.waitFor(5, TimeUnit.SECONDS), "owned TERM child did not exit within cleanup deadline");
            String log = read(childLog);
            require(log.contains("button-" + BUTTON) && log.contains("key-" + CONTROL), "SIGTERM did not run owned input shutdown cleanup: " + log);
            require(!log.contains("key-" + FOREIGN), "SIGTERM released an unowned key");
        } finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); } }
        System.out.println("PASS OwnedProbeInputsProbe: failure cleanup, unowned inputs preserved, release retry, closed hook removal, real child TERM cleanup; " + childLog);
    }

    private static String read(Path path) throws IOException { return new String(Files.readAllBytes(path), StandardCharsets.UTF_8); }
    private static void termChild(final Path log) throws Exception {
        OwnedProbeInputs inputs = new OwnedProbeInputs(new OwnedProbeInputs.Backend() {
            private void event(String line) {
                try { Files.write(log, (line + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
                catch (IOException failure) { throw new IllegalStateException(failure); }
            }
            public void keyPress(int key) { event("key+" + key); }
            public void keyRelease(int key) { event("key-" + key); }
            public void mousePress(int button) { event("button+" + button); }
            public void mouseRelease(int button) { event("button-" + button); }
        });
        inputs.keyPress(CONTROL); inputs.mousePress(BUTTON);
        Files.write(log, "ready\n".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        new CountDownLatch(1).await(); // The parent terminates only this owned child.
    }
}
