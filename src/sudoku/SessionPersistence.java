/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import javax.swing.*;

/** Bounded idle saving. A snapshot is immutable once it crosses the EDT boundary. */
final class SessionPersistence {
    private final SessionStore store;
    private final File file;
    private final Supplier<SessionSnapshot> capture;
    private final Consumer<IOException> status;
    private final Object writer = new Object(), queue = new Object();
    private final ThreadPoolExecutor executor;
    private final Timer debounce;
    private final long intervalNanos;
    private final int quietMillis;
    private boolean enabled, draining;
    private long sequence, lastAttemptNanos;
    private Pending pending;
    private Pending committed;
    private byte[] digest;
    private volatile IOException failure;
    private long failureSequence;
    private long writes, skips;

    static final class Pending {
        final SessionSnapshot snapshot;
        final long sequence;
        Pending(SessionSnapshot snapshot, long sequence) { this.snapshot=snapshot; this.sequence=sequence; }
    }

    SessionPersistence(SessionStore store, File file, Supplier<SessionSnapshot> capture, Consumer<IOException> status) {
        this(store,file,capture,status,5000,30000);
    }
    SessionPersistence(SessionStore store, File file, Supplier<SessionSnapshot> capture, Consumer<IOException> status,
                       int quietMillis, int intervalMillis) {
        this.store=store; this.file=file; this.capture=capture; this.status=status;
        this.quietMillis=quietMillis; intervalNanos=TimeUnit.MILLISECONDS.toNanos(intervalMillis);
        executor=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1), task->{
            Thread thread=new Thread(task,"HoDoKu idle session save"); thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY-1); return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        debounce=new Timer(quietMillis,event->captureIdle()); debounce.setRepeats(false);
    }
    void start() { requireEdt(); enabled=true; changed(); }
    void suspend() { requireEdt(); enabled=false; debounce.stop(); synchronized(queue){pending=null;} }
    void close() { suspend(); executor.shutdown(); }
    IOException failure() { return failure; }

    /** Constant-time hint, not a claim that every mutable legacy model has a revision counter. */
    void changed() {
        requireEdt(); if(!enabled)return;
        long remaining=intervalNanos-(System.nanoTime()-lastAttemptNanos);
        debounce.setInitialDelay((int)Math.min(Integer.MAX_VALUE,Math.max(quietMillis,
                TimeUnit.NANOSECONDS.toMillis(Math.max(0,remaining))+1)));
        debounce.restart();
    }
    Pending captureNow() {
        requireEdt();
        long capturedSequence = ++sequence;
        try(SaveTrace.Span span=SaveTrace.phase("session.capture_detached")) {
            return new Pending(capture.get(),capturedSequence);
        }
    }
    private void captureIdle() {
        if(!enabled)return;
        if (!PersistenceUiBoundary.canCaptureInBackground()) { changed(); return; }
        lastAttemptNanos=System.nanoTime();
        try(SaveTrace.Operation operation=SaveTrace.operation("session_idle_capture")) {
            try { enqueue(captureNow()); }
            catch(RuntimeException error) { operation.outcome("failed"); report(error, sequence); }
        }
    }
    private void enqueue(Pending next) {
        synchronized(queue) {
            pending=next;
            if(draining)return;
            draining=true;
            executor.execute(()->{
                try {
                    while(true) {
                        Pending work;
                        synchronized(queue){work=pending;pending=null;if(work==null){draining=false;return;}}
                        try(SaveTrace.Operation operation=SaveTrace.operation("session_idle")) {
                            try { commit(work,false); }
                            catch(IOException|RuntimeException error){operation.outcome("failed");report(error,work.sequence);}
                        }
                    }
                } catch(Error error) { synchronized(queue){draining=false;} throw error; }
            });
        }
    }
    /** A final save joins the writer, and a sequence fence rejects a delayed older idle snapshot. */
    void commit(Pending next, boolean requireElapsed) throws IOException {
        try(SaveTrace.Span wait=SaveTrace.phase("session.writer_wait")) {
            synchronized(writer) {
                wait.close();
                if(committed!=null&&next.sequence<committed.sequence){SaveTrace.event("session.skip_stale_snapshot",null);return;}
                boolean same;
                try(SaveTrace.Span compare=SaveTrace.phase("session.compare")) {
                    same=committed!=null&&(requireElapsed?SessionSnapshots.sameContent(next.snapshot,committed.snapshot)
                            :SessionSnapshots.sameWorkingContent(next.snapshot,committed.snapshot));
                }
                if(same&&digest!=null&&Files.isRegularFile(file.toPath(),LinkOption.NOFOLLOW_LINKS)
                        &&MessageDigest.isEqual(digest,diskDigest())) {
                    // Retain the on-disk elapsed value: if a later replay flush fails, strict comparison
                    // must still notice time that only existed in a previous skipped snapshot.
                    committed=new Pending(committed.snapshot,next.sequence); skips++;
                    clearFailure(next.sequence); SaveTrace.event("session.skip_unchanged","content and validated disk digest match");return;
                }
                store.save(next.snapshot); // Includes complete decode validation before replacement.
                digest=AtomicFileIO.validatedDigest(file,SessionSnapshot.class);
                committed=next;writes++;clearFailure(next.sequence);
            }
        }
    }
    private byte[] diskDigest() throws IOException {
        try(SaveTrace.Span span=SaveTrace.phase("session.disk_digest",file.toPath())) {
            MessageDigest hash;
            try{hash=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException error){throw new IOException(error);}
            try(InputStream input=Files.newInputStream(file.toPath())) {
                byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))!=-1;)hash.update(buffer,0,n);
            }
            return hash.digest();
        }
    }
    private void clearFailure(long savedSequence) {
        if (savedSequence >= failureSequence) { failure=null; publish(null); }
    }
    private void report(Throwable error, long failedSequence) {
        synchronized(writer) {
            if (committed != null && committed.sequence >= failedSequence) return;
            if (failedSequence < failureSequence) return;
            failureSequence=failedSequence;
            failure=new SaveFailure(SaveFailure.text("session"),file.toPath(),error);
            Logger.getLogger(SessionPersistence.class.getName()).log(Level.WARNING,"Work session remains unsaved; close will retry",failure);
            publish(failure);
        }
    }
    private void publish(IOException value){if(status!=null)SwingUtilities.invokeLater(()->status.accept(value));}
    private static void requireEdt(){if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Capture/schedule session on EDT");}
    long writesForTest(){synchronized(writer){return writes;}}
    long skipsForTest(){synchronized(writer){return skips;}}
}
