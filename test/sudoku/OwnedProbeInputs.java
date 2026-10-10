/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Robot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Test-only input ownership. Never releases a key/button this instance did not press. */
final class OwnedProbeInputs implements AutoCloseable {
    interface Backend {
        void keyPress(int key);
        void keyRelease(int key);
        void mousePress(int button);
        void mouseRelease(int button);
    }

    private final Backend backend;
    private final Set<Integer> keys = new LinkedHashSet<Integer>();
    private final Set<Integer> buttons = new LinkedHashSet<Integer>();
    private final Thread shutdownHook;
    private boolean closed;

    static OwnedProbeInputs forRobot(final Robot robot) {
        return new OwnedProbeInputs(new Backend() {
            public void keyPress(int key) { robot.keyPress(key); }
            public void keyRelease(int key) { robot.keyRelease(key); }
            public void mousePress(int button) { robot.mousePress(button); }
            public void mouseRelease(int button) { robot.mouseRelease(button); }
        });
    }

    OwnedProbeInputs(Backend backend) {
        if (backend == null) throw new NullPointerException("backend");
        this.backend = backend;
        shutdownHook = new Thread(() -> {
            try { releaseAll(); }
            catch (Throwable failure) { System.err.println("Owned probe input shutdown cleanup failed:"); failure.printStackTrace(); }
        }, "hodoku-owned-probe-input-cleanup");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    synchronized void keyPress(int key) {
        requireOpen();
        // Register before the OS call: an exception can occur after input has been sent.
        keys.add(key); backend.keyPress(key);
    }

    synchronized void mousePress(int button) {
        requireOpen(); buttons.add(button); backend.mousePress(button);
    }

    synchronized void keyRelease(int key) {
        if (keys.contains(key)) { backend.keyRelease(key); keys.remove(key); }
    }

    synchronized void mouseRelease(int button) {
        if (buttons.contains(button)) { backend.mouseRelease(button); buttons.remove(button); }
    }

    synchronized boolean hasHeldInputs() { return !keys.isEmpty() || !buttons.isEmpty(); }

    synchronized void releaseAll() {
        Throwable failure = null;
        List<Integer> ownButtons = new ArrayList<Integer>(buttons), ownKeys = new ArrayList<Integer>(keys);
        // Mouse-up occurs while the original modifiers are still held.
        for (int index = ownButtons.size() - 1; index >= 0; index--) {
            try { mouseRelease(ownButtons.get(index)); }
            catch (Throwable error) { failure = append(failure, error); }
        }
        for (int index = ownKeys.size() - 1; index >= 0; index--) {
            try { keyRelease(ownKeys.get(index)); }
            catch (Throwable error) { failure = append(failure, error); }
        }
        if (failure != null) throw new IllegalStateException("Failed to release some owned probe inputs", failure);
    }

    @Override public synchronized void close() {
        closed = true;
        try { releaseAll(); }
        finally {
            // A failed release remains owned so the shutdown hook can retry it.
            if (!hasHeldInputs()) {
                try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
                catch (IllegalStateException shuttingDown) { /* The registered hook is already running. */ }
            }
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Owned probe input scope is closed");
    }

    static Throwable append(Throwable primary, Throwable next) {
        if (primary == null) return next;
        if (primary != next) primary.addSuppressed(next);
        return primary;
    }
}
