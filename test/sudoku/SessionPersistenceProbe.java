/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Functional idle/final persistence checks; all files and mutable graphs are synthetic. */
public final class SessionPersistenceProbe {
    private static final String PUZZLE = "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    public static void main(String[] args) throws Exception {
        System.setProperty("hodoku.save.trace", "false");
        Path root = Files.createTempDirectory("hodoku-session-coordinator-");
        System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, root.resolve("data").toString());
        Options.instance = new Options();
        explicitPaths(root);
        idlePaths(root, args.length > 0 && "--full-interval".equals(args[0]));
        startupRecovery(root);
        System.out.println("Session coordinator checks passed: capture, elapsed, fences, corruption, retry, idle cadence, startup recovery");
        // Functional coordinator tests have closed every owned writer/timer. Native
        // application quit is measured separately; do not await AppKit auto-shutdown.
        System.exit(0);
    }

    private static void explicitPaths(Path root) throws Exception {
        File file = root.resolve("session.xml").toFile();
        SessionStore store = new SessionStore(file);
        AtomicReference<SessionSnapshot> live = new AtomicReference<SessionSnapshot>(fixture(100));
        SessionPersistence persistence = new SessionPersistence(store, file,
                () -> SessionSnapshots.detached(live.get()), error -> require(SwingUtilities.isEventDispatchThread(), "status is not on EDT"));
        SessionPersistence.Pending first = capture(persistence);
        live.get().getGuiState().getSudoku().setCell(0, 2, 4);
        live.get().getGuiState().getDoodleStrokes().get(0).setPoint(0, 90, 91);
        persistence.commit(first, true);
        SessionSnapshot read = store.load();
        require(read.getGuiState().getSudoku().getValue(2) == 0, "live board polluted pending snapshot");
        require(read.getGuiState().getDoodleStrokes().get(0).pointX(0) == 1, "live ink polluted pending snapshot");
        live.set(SessionSnapshots.detached(first.snapshot));
        live.get().setReplayElapsedMillis(500);
        persistence.commit(capture(persistence), false);
        require(persistence.writesForTest() == 1 && persistence.skipsForTest() == 1, "elapsed-only idle change rewrote session");
        require(store.load().getReplayElapsedMillis() == 100, "skip silently claimed new elapsed on disk");
        persistence.commit(capture(persistence), true);
        require(persistence.writesForTest() == 2 && store.load().getReplayElapsedMillis() == 500,
                "failed replay flush strict elapsed did not get saved");

        SessionPersistence.Pending older = capture(persistence);
        live.get().setActiveCol(7);
        SessionPersistence.Pending newer = capture(persistence);
        Field writerField = SessionPersistence.class.getDeclaredField("writer"); writerField.setAccessible(true);
        AtomicReference<Throwable> delayedFailure = new AtomicReference<Throwable>();
        CountDownLatch delayedStarted = new CountDownLatch(1);
        Thread delayed = new Thread(() -> {
            delayedStarted.countDown();
            try { persistence.commit(older, true); } catch (Throwable failure) { delayedFailure.set(failure); }
        }, "probe delayed old writer");
        synchronized (writerField.get(persistence)) {
            delayed.start(); require(delayedStarted.await(2, TimeUnit.SECONDS), "delayed writer did not start");
            persistence.commit(newer, true);
        }
        delayed.join(5000);
        require(!delayed.isAlive() && delayedFailure.get() == null, "delayed writer failed");
        require(store.load().getActiveCol() == 7 && persistence.writesForTest() == 3, "old writer replaced new snapshot");

        byte[] bytes = Files.readAllBytes(file.toPath());
        FileTime modified = Files.getLastModifiedTime(file.toPath());
        bytes[0] = '!'; Files.write(file.toPath(), bytes); Files.setLastModifiedTime(file.toPath(), modified);
        persistence.commit(capture(persistence), true);
        require(persistence.writesForTest() == 4 && store.load().getActiveCol() == 7,
                "same-size/mtime corruption was skipped");
        Method report = SessionPersistence.class.getDeclaredMethod("report", Throwable.class, long.class);
        report.setAccessible(true);
        SessionPersistence.Pending saved = capture(persistence); persistence.commit(saved, true);
        report.invoke(persistence, new IOException("late old failure"), older.sequence);
        require(persistence.failure() == null, "late old failure hid a newer successful commit");
        live.get().setActiveRow(7);
        SessionPersistence.Pending failedNewer = capture(persistence);
        report.invoke(persistence, new IOException("newer failure"), failedNewer.sequence);
        persistence.commit(saved, true);
        require(persistence.failure() != null, "old unchanged success cleared newer unsaved failure");
        persistence.commit(failedNewer, true);
        require(persistence.failure() == null, "newer retry did not clear own failure");
        SwingUtilities.invokeAndWait(persistence::close);

        Path blocked = root.resolve("blocked-explicit"); Files.write(blocked, new byte[] {1});
        File retryFile = blocked.resolve("session.xml").toFile();
        SessionPersistence retry = new SessionPersistence(new SessionStore(retryFile), retryFile,
                () -> SessionSnapshots.detached(live.get()), null);
        SessionPersistence.Pending retrySnapshot = capture(retry);
        boolean failed = false;
        try { retry.commit(retrySnapshot, true); } catch (IOException expected) { failed = true; }
        require(failed && retry.writesForTest() == 0, "failed write became committed");
        Files.delete(blocked); Files.createDirectory(blocked);
        retry.commit(retrySnapshot, true);
        require(retry.writesForTest() == 1, "same failed snapshot could not retry");
        SwingUtilities.invokeAndWait(retry::close);
    }

    private static void idlePaths(Path root, boolean fullInterval) throws Exception {
        File file = root.resolve("idle.xml").toFile();
        AtomicReference<SessionSnapshot> live = new AtomicReference<SessionSnapshot>(fixture(100));
        AtomicReference<IOException> status = new AtomicReference<IOException>();
        SessionPersistence persistence = fullInterval
                ? new SessionPersistence(new SessionStore(file), file, () -> SessionSnapshots.detached(live.get()), status::set)
                : new SessionPersistence(new SessionStore(file), file, () -> SessionSnapshots.detached(live.get()), status::set, 40, 300);
        int quiet = fullInterval ? 5000 : 40, interval = fullInterval ? 30000 : 300;
        SwingUtilities.invokeAndWait(() -> {
            persistence.start();
            for (int i = 0; i < 100; i++) { live.get().setActiveCol(i % 9); persistence.changed(); }
        });
        await(() -> persistence.writesForTest() == 1, quiet + 5000, "first idle burst did not save");
        require(new SessionStore(file).load().getActiveCol() == 0, "idle save did not use latest burst state");
        SwingUtilities.invokeAndWait(() -> { live.get().setActiveCol(8); persistence.changed(); });
        Thread.sleep(fullInterval ? 2000 : 80);
        require(persistence.writesForTest() == 1, "idle interval did not throttle next save");
        await(() -> persistence.writesForTest() == 2, interval + 5000, "throttled dirty state was lost");
        require(new SessionStore(file).load().getActiveCol() == 8, "delayed idle snapshot was stale");
        SwingUtilities.invokeAndWait(() -> { live.get().setReplayElapsedMillis(900); persistence.changed(); });
        if (!fullInterval) {
            await(() -> persistence.skipsForTest() == 1, 5000, "clock-only idle snapshot was not skipped");
            require(persistence.writesForTest() == 2, "clock-only idle caused a write");
        }
        SwingUtilities.invokeAndWait(persistence::close);
        require(status.get() == null, "successful idle save reported an error");

        Path blocked = root.resolve("blocked-idle"); Files.write(blocked, new byte[] {1});
        File failedFile = blocked.resolve("session.xml").toFile();
        CountDownLatch reported = new CountDownLatch(1);
        SessionPersistence retry = new SessionPersistence(new SessionStore(failedFile), failedFile,
                () -> SessionSnapshots.detached(live.get()), error -> { if (error != null) reported.countDown(); }, 30, 100);
        SwingUtilities.invokeAndWait(retry::start);
        require(reported.await(5, TimeUnit.SECONDS) && retry.failure() != null, "background error was hidden");
        SwingUtilities.invokeAndWait(retry::suspend);
        Files.delete(blocked); Files.createDirectory(blocked);
        retry.commit(capture(retry), true);
        require(retry.failure() == null && retry.writesForTest() == 1, "final retry did not clear idle failure");
        SwingUtilities.invokeAndWait(retry::close);
    }

    private static void startupRecovery(Path root) throws Exception {
        File file = root.resolve("loaded.xml").toFile();
        SessionStore store = new SessionStore(file);
        SessionSnapshot first = fixture(100); first.setActiveCol(1); store.save(first);
        SessionSnapshot second = fixture(100); second.setActiveCol(2); store.save(second);
        byte[] secondBytes = Files.readAllBytes(file.toPath());
        Files.write(file.toPath(), new byte[] {'b', 'a', 'd'});
        SessionSnapshot backup = store.load();
        require(backup.getActiveCol() == 1, "test did not enter backup recovery");
        AtomicReference<SessionSnapshot> live = new AtomicReference<SessionSnapshot>(backup);
        SessionPersistence persistence = new SessionPersistence(store, file, () -> SessionSnapshots.detached(live.get()), null);
        // Another instance restores the former primary after our backup recovery.
        Files.write(file.toPath(), secondBytes);
        persistence.commit(capture(persistence), true);
        require(store.load().getActiveCol() == 1, "backup snapshot was incorrectly matched to stale primary proof");
        SwingUtilities.invokeAndWait(persistence::close);
    }

    private static SessionPersistence.Pending capture(SessionPersistence persistence) throws Exception {
        AtomicReference<SessionPersistence.Pending> result = new AtomicReference<SessionPersistence.Pending>();
        SwingUtilities.invokeAndWait(() -> result.set(persistence.captureNow())); return result.get();
    }
    private static SessionSnapshot fixture(long elapsed) {
        Sudoku2 board = new Sudoku2(); board.setSudoku(PUZZLE);
        GuiState state = new GuiState(); state.setSudoku(board); state.setUndoStack(new Stack<Sudoku2>());
        state.setRedoStack(new Stack<Sudoku2>());
        DoodleStroke stroke = new DoodleStroke(); stroke.addPoint(1, 2); stroke.addPoint(3, 4);
        ArrayList<DoodleStroke> strokes = new ArrayList<DoodleStroke>(); strokes.add(stroke); state.setDoodleStrokes(strokes);
        SessionSnapshot snapshot = new SessionSnapshot(); snapshot.setGuiState(state);
        snapshot.setReplayId("synthetic"); snapshot.setReplayFrameCount(2); snapshot.setReplayElapsedMillis(elapsed); return snapshot;
    }
    private interface Check { boolean done(); }
    private static void await(Check check, int timeoutMillis, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!check.done() && System.nanoTime() < deadline) Thread.sleep(10);
        require(check.done(), message);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
