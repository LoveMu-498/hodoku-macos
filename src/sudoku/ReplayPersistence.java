/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Serial, coalesced disk writes of detached EDT snapshots. Flush is a durability barrier. */
final class ReplayPersistence {
    private static final java.util.logging.Logger LOG=java.util.logging.Logger.getLogger(ReplayPersistence.class.getName());
    private final Path directory;
    private final ReplayRecovery recovery;
    private final ScheduledThreadPoolExecutor writer=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"HoDoKu replay save");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private final Map<String,ReplaySession> pending=new LinkedHashMap<>(),failed=new LinkedHashMap<>();
    private IOException failure;
    private ScheduledFuture<?> scheduled;
    private boolean writing;
    private ReplaySession pendingCheckpoint;
    private boolean checkpointQueued,cleanBarrier;
    private long checkpointGeneration;
    private String activeId;
    private ReplaySession committed;
    private byte[] committedDigest;
    private long archiveRevision,cleanedRevision=-1;
    private String cleanedId;
    private final Runnable changed;
    private String loggedFailure;
    ReplayPersistence(Path directory,ReplayRecovery recovery,Runnable changed){this.directory=directory;this.recovery=recovery;this.changed=changed;writer.setRemoveOnCancelPolicy(true);}
    synchronized void enqueue(ReplaySession source){
        if(!source.id.equals(activeId)){committed=null;committedDigest=null;}
        activeId=source.id;
        pending.put(source.id,source.persistenceSnapshot());
        if(scheduled==null||scheduled.isDone())scheduled=writer.schedule(()->{drain(false);signal();},120,TimeUnit.MILLISECONDS);
    }
    private void signal(){javax.swing.SwingUtilities.invokeLater(changed);}
    private void drain(boolean retry){
        Map<String,ReplaySession> batch=new LinkedHashMap<>();
        synchronized(this){writing=true;if(retry)batch.putAll(failed);batch.putAll(pending);pending.clear();scheduled=null;}
        IOException errors=null;
        for(ReplaySession snapshot:batch.values()){
            Path path=directory.resolve(snapshot.id+".hrep");
            try{
                try{
                    ReplaySession known;byte[] knownDigest;
                    synchronized(this){known=committed;knownDigest=committedDigest;}
                    boolean unchanged=known!=null&&snapshot.sameDiskState(known)&&java.nio.file.Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)
                            &&java.security.MessageDigest.isEqual(knownDigest,ReplayStore.fingerprint(path));
                    if(!unchanged){
                        byte[] written=ReplayStore.writeChecked(path,snapshot);
                        synchronized(this){if(snapshot.id.equals(activeId)){committed=snapshot;committedDigest=written;}if(snapshot.retained&&snapshot.endedAt!=0)archiveRevision++;}
                    }
                    else SaveTrace.event("replay.skip_unchanged",path.toString());
                }catch(Exception e){throw new SaveFailure(SaveFailure.text("replay"),path,e);}
                try{recovery.checkpoint(snapshot,false);}catch(Exception e){throw new SaveFailure(SaveFailure.text("checkpoint"),directory.resolve("active.checkpoint"),e);}
                if(snapshot.completed&&(!snapshot.id.equals(cleanedId)||cleanedRevision!=archiveRevision)){
                    Set<String> protectedIds;
                    synchronized(this){protectedIds=new HashSet<>(failed.keySet());protectedIds.addAll(pending.keySet());}
                    try{SaveTrace.io("replay.library_cleanup",directory,()->new ReplayLibrary(directory).rotate(snapshot.id,protectedIds));}catch(Exception e){throw new SaveFailure(SaveFailure.text("replayCleanup"),directory,e);}
                    cleanedId=snapshot.id;cleanedRevision=archiveRevision;
                }
                synchronized(this){failed.remove(snapshot.id);}
            }catch(IOException e){report(e);synchronized(this){failed.put(snapshot.id,snapshot);}if(errors==null)errors=e;else errors.addSuppressed(e);}
        }
        synchronized(this){writing=false;if(errors!=null)failure=errors;else if(failed.isEmpty()&&!batch.isEmpty()){failure=null;loggedFailure=null;}}
    }
    void flush()throws IOException{
        try(SaveTrace.Span wait=SaveTrace.phase("replay.flush_wait")){
        try{await(submitTraced("replay.queue_wait",()->{
            try(SaveTrace.Span write=SaveTrace.phase("replay.drain",directory)){
                drain(true);signal();synchronized(this){if(failure!=null)write.fail(failure);}
            }
            return null;
        }));
        synchronized(this){if(failure!=null)throw failure;}
        }catch(IOException|RuntimeException|Error e){wait.fail(e);throw e;}
        }
    }
    private <T>Future<T> submitTraced(String phase,Callable<T> work){
        SaveTrace.Context context=SaveTrace.capture();SaveTrace.Span queue=SaveTrace.phase(phase);
        SaveTrace.event("replay.queue_depth",String.valueOf(writer.getQueue().size()));
        try{return writer.submit(()->{try(SaveTrace.Scope scope=SaveTrace.bind(context)){queue.close();return work.call();}});}
        catch(RuntimeException e){queue.fail(e);queue.close();throw e;}
    }
    void checkpoint(ReplaySession source,boolean clean,boolean editing)throws IOException{
        synchronized(this){if(failure!=null)enqueue(source);}
        ReplaySession snapshot=source.persistenceSnapshot();
        Callable<Void> work=()->{
            drain(true);
            synchronized(this){if(failure!=null)throw failure;}
            try{recovery.checkpoint(snapshot,clean,editing);}catch(Exception e){throw new SaveFailure(SaveFailure.text("checkpoint"),directory.resolve("active.checkpoint"),e);}
            return null;
        };
        if(clean){
            synchronized(this){cleanBarrier=true;checkpointGeneration++;pendingCheckpoint=null;}
            await(submitTraced("checkpoint.queue_wait",work));
        } else synchronized(this){
            if(cleanBarrier){cleanBarrier=false;checkpointGeneration++;}
            pendingCheckpoint=snapshot;
            scheduleCheckpoint();
        }
    }
    private void scheduleCheckpoint(){
        if(checkpointQueued||pendingCheckpoint==null)return;
        checkpointQueued=true;
        writer.submit(()->{
            ReplaySession snapshot;long generation;
            synchronized(this){snapshot=pendingCheckpoint;pendingCheckpoint=null;generation=checkpointGeneration;}
            try{
                if(snapshot!=null){
                    drain(true);
                    synchronized(this){if(failure!=null)throw failure;if(cleanBarrier||generation!=checkpointGeneration||!snapshot.id.equals(activeId))return;}
                    try{recovery.checkpoint(snapshot,false);}catch(Exception e){throw new SaveFailure(SaveFailure.text("checkpoint"),directory.resolve("active.checkpoint"),e);}
                }
            }catch(Exception e){report(e);synchronized(this){failure=e instanceof IOException?(IOException)e:new IOException(e);}}
            finally{synchronized(this){checkpointQueued=false;scheduleCheckpoint();}signal();}
        });
    }
    private void report(Exception error){String detail=SaveFailure.details(error);synchronized(this){if(detail.equals(loggedFailure))return;loggedFailure=detail;}LOG.log(java.util.logging.Level.WARNING,"Replay persistence failed",error);}
    private static void await(Future<?> task)throws IOException{
        try{task.get();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Save interrupted",e);}
        catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof IOException)throw(IOException)cause;throw new IOException("Save worker failed",cause);}
    }
    synchronized IOException failure(){return failure;}
    synchronized Map<String,ReplaySession> failed(){return new LinkedHashMap<>(failed);}
    synchronized boolean busy(){return writing||!pending.isEmpty()||scheduled!=null&&!scheduled.isDone();}
    void close(){writer.shutdown();}
}
