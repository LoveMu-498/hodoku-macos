/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import generator.BackgroundGeneratorThread;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Confirmed settings are coalesced and written by one bounded, low-priority worker. */
public final class SettingsPersistence {
    private static final Object WRITER = new Object();
    private static final Object QUEUE = new Object();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final AtomicBoolean REQUEST_POSTED = new AtomicBoolean();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(1), task -> {
                Thread thread = new Thread(task, "HoDoKu settings save");
                thread.setDaemon(true); thread.setPriority(Thread.NORM_PRIORITY - 1); return thread;
            });
    static { EXECUTOR.allowCoreThreadTimeOut(true); }
    private static volatile boolean enabled;
    private static Timer debounce;
    private static Snapshot pending;
    private static boolean draining;
    private static Snapshot committed;
    private static byte[] committedDigest;
    private static long successfulWrites;
    private static volatile long lastWriteNanos;
    private static volatile IOException backgroundFailure;
    private static long backgroundFailureSequence = -1;
    private static File backgroundFailureDestination;
    private static volatile Consumer<IOException> backgroundFailureListener;

    private SettingsPersistence() { }

    /** Captured only at a stable UI boundary; subsequent UI edits cannot alter this object. */
    public static final class Snapshot {
        private final SettingsSnapshot content;
        private final File destination;
        private final long sequence;
        private Snapshot(Options options) {
            content = new SettingsSnapshot(options);
            destination = ApplicationPaths.getConfigurationFile().getAbsoluteFile();
            sequence = SEQUENCE.incrementAndGet();
        }
        public void saveDefault() throws IOException { commit(this); }
        public void saveTo(File file) throws IOException {
            if (file.getAbsoluteFile().equals(destination)) saveDefault();
            else content.options.writeSnapshotSafely(file);
        }
    }

    public static Snapshot capture(Options options) {
        if (enabled && options == Options.instance && !SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Capture live settings on the event dispatch thread");
        try (SaveTrace.Span span = SaveTrace.phase("settings.capture")) {
            // All generator cache mutations hold this monitor, including array shifts and resets.
            synchronized (BackgroundGeneratorThread.getInstance()) { return new Snapshot(options); }
        }
    }

    /** Enable only after window initialization. CLI and detached tests do not start autosaving. */
    public static void start() { enabled = true; }
    /** Reports on the EDT; null clears an earlier unsaved-settings status after success. */
    public static void setBackgroundFailureListener(Consumer<IOException> listener) {
        backgroundFailureListener = listener;
    }
    public static IOException getBackgroundFailure() { return backgroundFailure; }
    public static void suspend() {
        enabled = false;
        if (SwingUtilities.isEventDispatchThread() && debounce != null) debounce.stop();
    }

    /** Called at accepted preference/menu/layout/cache changes, never at each paint or each point. */
    public static void requestSave() {
        if (!enabled || !REQUEST_POSTED.compareAndSet(false, true)) return;
        SwingUtilities.invokeLater(() -> {
            REQUEST_POSTED.set(false);
            if (!enabled) return;
            if (debounce == null) {
                debounce = new Timer(1000, event -> {
                    if (!enabled) return;
                    if (!PersistenceUiBoundary.canCaptureInBackground()) { requestSave(); return; }
                    try { enqueue(capture(Options.getInstance())); }
                    catch (RuntimeException failure) { reportBackgroundFailure(failure, null); }
                });
                debounce.setRepeats(false);
            }
            long remaining = TimeUnit.SECONDS.toNanos(5) - (System.nanoTime() - lastWriteNanos);
            debounce.setInitialDelay((int) Math.max(1000L, TimeUnit.NANOSECONDS.toMillis(remaining)));
            debounce.restart();
        });
    }

    /** Explicit confirmations retain the existing loading/error feedback and await success. */
    static void saveConfirmed(Window owner) throws IOException {
        SaveProgress.run(owner, false, () -> {
            final Snapshot snapshot = capture(Options.getInstance());
            return progress -> {
                progress.update(SaveFailure.text("options"), 20);
                snapshot.saveDefault();
                progress.update(SaveFailure.text("done"), 100);
            };
        });
    }

    private static void enqueue(Snapshot snapshot) {
        synchronized (QUEUE) {
            pending = snapshot;
            if (draining) return;
            draining = true;
            EXECUTOR.execute(() -> {
                try {
                while (true) {
                    Snapshot next;
                    synchronized (QUEUE) {
                        next = pending; pending = null;
                        if (next == null) { draining = false; return; }
                    }
                    try (SaveTrace.Operation operation = SaveTrace.operation("settings_idle")) {
                        try {
                            long delay;
                            synchronized (WRITER) {
                                delay = TimeUnit.SECONDS.toNanos(5) - (System.nanoTime() - lastWriteNanos);
                                if (delay <= 0) commit(next);
                            }
                            if (delay > 0) {
                                synchronized (QUEUE) {
                                    if (pending == null || pending.sequence < next.sequence) pending = next;
                                }
                                // Only this daemon sleeps; explicit confirmation/quit can acquire WRITER immediately.
                                try { TimeUnit.NANOSECONDS.sleep(delay); }
                                catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    synchronized (QUEUE) { draining = false; }
                                    return;
                                }
                            }
                        }
                        catch (IOException | RuntimeException failure) {
                            operation.outcome("failed"); reportBackgroundFailure(failure, next);
                        }
                    }
                }
                } catch (Error failure) {
                    // Do not strand the coalescer if a fatal worker failure escapes.
                    synchronized (QUEUE) { draining = false; }
                    throw failure;
                }
            });
        }
    }

    private static void reportBackgroundFailure(Throwable failure, Snapshot attempted) {
        // A failed background submission never advances the committed snapshot. Explicit save/quit retries it.
        File destination = attempted == null ? ApplicationPaths.getConfigurationFile().getAbsoluteFile() : attempted.destination;
        long failedSequence = attempted == null ? SEQUENCE.incrementAndGet() : attempted.sequence;
        IOException detail = new SaveFailure(SaveFailure.text("options"),
                destination.toPath(), failure);
        synchronized (WRITER) {
            if (committed != null && destination.equals(committed.destination) && failedSequence <= committed.sequence) return;
            if (backgroundFailure != null && failedSequence < backgroundFailureSequence) return;
            backgroundFailure = detail;
            backgroundFailureSequence = failedSequence;
            backgroundFailureDestination = destination;
            publish(detail);
        }
        Logger.getLogger(SettingsPersistence.class.getName()).log(Level.WARNING,
                "Settings remain unsaved; the next confirmation or quit will retry", detail);
    }

    /** Called under WRITER: an older success cannot hide an unsaved newer attempt. */
    private static void clearFailure(Snapshot saved) {
        if (backgroundFailure == null || (saved.sequence >= backgroundFailureSequence
                && saved.destination.equals(backgroundFailureDestination))) {
            backgroundFailure = null; backgroundFailureSequence = -1; backgroundFailureDestination = null;
            publish(null);
        }
    }

    private static void publish(IOException value) {
        Consumer<IOException> listener = backgroundFailureListener;
        if (listener != null) SwingUtilities.invokeLater(() -> listener.accept(value));
    }

    private static void commit(Snapshot snapshot) throws IOException {
        try (SaveTrace.Span wait = SaveTrace.phase("settings.writer_wait")) {
            synchronized (WRITER) {
                wait.close();
                boolean sameFile = committed != null && snapshot.destination.equals(committed.destination);
                if (sameFile && snapshot.sequence < committed.sequence) {
                    SaveTrace.event("settings.skip_stale_snapshot", "newer snapshot already committed"); return;
                }
                if (sameFile && snapshot.content.sameContent(committed.content)
                        && committedDigest != null && Files.isRegularFile(snapshot.destination.toPath(), LinkOption.NOFOLLOW_LINKS)
                        && MessageDigest.isEqual(committedDigest, digest(snapshot.destination))) {
                    committed = snapshot;
                    clearFailure(snapshot);
                    SaveTrace.event("settings.skip_unchanged", "exact settings and disk digest match"); return;
                }
                snapshot.content.options.writeSnapshotSafely(snapshot.destination);
                byte[] written = AtomicFileIO.validatedDigest(snapshot.destination, Options.class);
                committed = snapshot; committedDigest = written; successfulWrites++;
                lastWriteNanos = System.nanoTime();
                clearFailure(snapshot);
            }
        }
    }

    /** Decode and associate its proof under the writer lock, before live settings migrations. */
    static Options read(File file) throws IOException {
        synchronized (WRITER) {
            Options options = Options.decodeOptions(file);
            if (file.getAbsoluteFile().equals(ApplicationPaths.getConfigurationFile().getAbsoluteFile())) {
                // This object is newly decoded and detached, so no UI/generator capture lock is required.
                committed = new Snapshot(options);
                committedDigest = AtomicFileIO.validatedDigest(file, Options.class);
            }
            return options;
        }
    }

    private static byte[] digest(File file) throws IOException {
        try (SaveTrace.Span span = SaveTrace.phase("settings.disk_digest", file.toPath())) {
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException failure) { throw new IOException(failure); }
            try (InputStream input = Files.newInputStream(file.toPath())) {
                byte[] buffer = new byte[8192];
                for (int length; (length = input.read(buffer)) != -1;) digest.update(buffer, 0, length);
            }
            return digest.digest();
        }
    }

    static long successfulWritesForTest() { synchronized (WRITER) { return successfulWrites; } }
}
