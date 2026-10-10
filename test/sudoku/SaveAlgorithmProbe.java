/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real immutable-prefix, bounded-backlog and metadata-preserving corruption checks. */
public final class SaveAlgorithmProbe {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        System.setProperty("java.awt.headless","true");
        if(System.getProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY)==null)System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY,Files.createTempDirectory("hodoku-algorithm-data-").toString());
        Sudoku2 board=new Sudoku2();board.clearSudoku();ReplayBoard immutable=new ReplayBoard(board);
        ReplaySession live=new ReplaySession(immutable,1);live.setInitialAnnotations(new byte[]{1});
        ReplaySession first=live.persistenceSnapshot(),nested=first.persistenceSnapshot();
        first.loadFrame(new ReplayFrame(1,2,0,"manual","fork",immutable,null));
        check(nested.frameCount()==1&&live.frameCount()==1,"snapshot mutation leaked into another aggregate");
        live.setInitialFrameAnnotations(ReplayAnnotations.fromWindows("K 1 0\n"));
        check(nested.last().annotations.overlay.isEmpty(),"initial-frame replacement mutated snapshot");
        live.setInitialAnnotations(new byte[]{2});check(nested.initialAnnotations()[0]==1,"initial geometry mutated snapshot");
        for(int i=1;i<=5000;i++)live.append(Collections.singletonList(new ReplayFrame(i,i,0,"manual","row",immutable,null)));
        ReplaySession prefix=live.persistenceSnapshot();
        ExecutorService reader=Executors.newSingleThreadExecutor();Future<?> read=reader.submit(()->{for(int loop=0;loop<10;loop++){int count=0;for(ReplayFrame f:prefix.frames()){check(f.operationId==count,"frame order changed");count++;}check(count==5001,"prefix grew while another thread appended");}});
        for(int i=5001;i<=10000;i++)live.append(Collections.singletonList(new ReplayFrame(i,i,0,"manual","row",immutable,null)));
        read.get();reader.shutdown();check(prefix.frameCount()==5001&&live.frameCount()==10001,"lost append boundary");

        live.setInitialAnnotations(new byte[0]);
        Path dir=Files.createTempDirectory("hodoku-bounded-save-");ReplayPersistence queue=new ReplayPersistence(dir,new ReplayRecovery(dir),()->{});
        Field field=ReplayPersistence.class.getDeclaredField("writer");field.setAccessible(true);ScheduledThreadPoolExecutor writer=(ScheduledThreadPoolExecutor)field.get(queue);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);writer.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});entered.await();
        queue.enqueue(live);
        for(int i=0;i<1000;i++){live.elapsedMillis=i;queue.checkpoint(live,false,false);}
        check(writer.getQueue().size()<=2,"heartbeats built an unbounded task backlog");
        Throwable[] error={null};Thread clean=new Thread(()->{try{queue.checkpoint(live,true,false);}catch(Throwable e){error[0]=e;}});clean.start();
        Field barrier=ReplayPersistence.class.getDeclaredField("cleanBarrier");barrier.setAccessible(true);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(System.nanoTime()<deadline){boolean ready;synchronized(queue){ready=barrier.getBoolean(queue);}if(ready)break;Thread.yield();}
        release.countDown();clean.join();check(error[0]==null,"clean checkpoint failed");writer.submit(()->{}).get();Thread.sleep(180);writer.submit(()->{}).get();
        try(DataInputStream in=new DataInputStream(Files.newInputStream(dir.resolve("active.checkpoint")))){in.readInt();in.readUTF();in.readInt();in.readLong();check(in.readBoolean(),"late heartbeat overwrote clean checkpoint");}
        queue.close();

        Path repeated=Files.createTempDirectory("hodoku-unchanged-save-");ReplayPersistence stable=new ReplayPersistence(repeated,new ReplayRecovery(repeated),()->{});
        Sudoku2 solved=new Sudoku2();solved.setSudoku("534678912672195348198342567859761423426853791713924856961537284287419635345286179");
        ReplaySession completed=new ReplaySession(new ReplayBoard(solved),1);completed.completed=true;completed.retained=true;completed.endedAt=2;
        stable.enqueue(completed);stable.flush();Path replay=repeated.resolve(completed.id+".hrep");
        byte[] committed=Files.readAllBytes(replay);java.nio.file.attribute.FileTime old=java.nio.file.attribute.FileTime.fromMillis(1000);
        Files.setLastModifiedTime(replay,old);stable.enqueue(completed);stable.flush();
        check(Files.getLastModifiedTime(replay).equals(old),"identical committed replay was rewritten");
        byte[] corrupt=committed.clone();corrupt[0]='?';Files.write(replay,corrupt);Files.setLastModifiedTime(replay,old);
        stable.enqueue(completed);stable.flush();check(Arrays.equals(committed,Files.readAllBytes(replay)),"unchanged shortcut ignored same-size/mtime corruption");
        Path external=repeated.resolve("external-preserved.bin");Files.write(external,committed);Files.delete(replay);Files.createSymbolicLink(replay,external);
        stable.enqueue(completed);stable.flush();check(!Files.isSymbolicLink(replay)&&Arrays.equals(committed,Files.readAllBytes(external)),"unchanged shortcut retained a symlink or changed external bytes");
        completed.interruption="updated note";stable.enqueue(completed);stable.flush();
        check(ReplayStore.read(replay).interruption.equals("updated note"),"metadata change was skipped");stable.close();

        File file=Files.createTempDirectory("hodoku-backup-proof-").resolve("session.xml").toFile();SessionStore store=new SessionStore(file);
        SessionSnapshot one=snapshot(4),two=snapshot(8),three=snapshot(6);store.save(one);store.save(two);
        byte[] goodBackup=Files.readAllBytes(AtomicFileIO.backupFor(file).toPath());java.nio.file.attribute.FileTime time=Files.getLastModifiedTime(file.toPath());
        byte[] bad=Files.readAllBytes(file.toPath());bad[0]='?';Files.write(file.toPath(),bad);Files.setLastModifiedTime(file.toPath(),time);
        store.save(three);check(Arrays.equals(goodBackup,Files.readAllBytes(AtomicFileIO.backupFor(file).toPath())),"same-size/mtime corruption replaced good backup");
        check(store.load().getGuiState().getSudoku().getValue(2)==6,"new state did not survive corruption recovery");
        System.out.println("PASS prefix isolation/concurrent append, bounded 1000-heartbeat queue, clean-checkpoint fence, unchanged replay and metadata/corruption guards, SHA-256 backup proof despite preserved size/mtime");
    }
    private static SessionSnapshot snapshot(int value){Sudoku2 sudoku=new Sudoku2();sudoku.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");sudoku.setCell(2,value);GuiState state=new GuiState();state.setSudoku(sudoku);SessionSnapshot result=new SessionSnapshot();result.setGuiState(state);return result;}
}
