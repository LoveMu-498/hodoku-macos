/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.nio.file.*;
import java.util.*;

/** Real disk writes: detached snapshots, batching, failure recovery and ordered flush. */
public final class ReplayPersistenceProbe {
    public static void main(String[] args)throws Exception{
        System.setProperty("java.awt.headless","true");
        Path directory=Files.createTempDirectory("hodoku-save-queue-");
        Sudoku2 sudoku=new Sudoku2();sudoku.clearSudoku();ReplayBoard board=new ReplayBoard(sudoku);
        ReplaySession session=new ReplaySession(board,1);ReplayPersistence queue=new ReplayPersistence(directory,new ReplayRecovery(directory),()->{});
        long start=System.nanoTime();
        for(int i=1;i<=200;i++){session.elapsedMillis=i;session.append(Collections.singletonList(new ReplayFrame(i,i+1,i,"manual","record "+i,board,null)));queue.enqueue(session);}
        long enqueueNanos=System.nanoTime()-start;
        queue.flush();ReplaySession loaded=ReplayStore.read(directory.resolve(session.id+".hrep"));
        require(loaded.frames().size()==201&&loaded.last().operationId==200,"batch lost a frame");
        require(loaded.elapsedMillis==200,"batch lost metadata");
        Path bad=Files.createTempFile("hodoku-blocked-replay-", ".file");ReplayPersistence blocked=new ReplayPersistence(bad,new ReplayRecovery(bad),()->{});
        blocked.enqueue(session);
        try{blocked.flush();throw new AssertionError("failed write reported success");}
        catch(java.io.IOException expected){String detail=SaveFailure.details(expected);require(detail.contains(bad.toString())&&detail.contains("FileAlreadyExistsException"),"missing precise failure detail");}
        Files.delete(bad);Files.createDirectory(bad);blocked.flush();require(blocked.failure()==null&&blocked.failed().isEmpty(),"retry did not recover");
        require(ReplayStore.read(bad.resolve(session.id+".hrep")).frames().size()==201,"retry lost content");
        queue.close();blocked.close();
        Path baseline=Files.createTempDirectory("hodoku-save-each-");ReplaySession every=new ReplaySession(board,1);long syncStart=System.nanoTime();
        for(int i=1;i<=200;i++){every.elapsedMillis=i;every.append(Collections.singletonList(new ReplayFrame(i,i+1,i,"manual","record "+i,board,null)));ReplayStore.write(baseline.resolve(every.id+".hrep"),every);}
        long syncNanos=System.nanoTime()-syncStart;
        System.out.printf(Locale.ROOT,"200 operations: EDT enqueue %.2f ms; write every operation %.2f ms; every frame restored, retry recovered%n",enqueueNanos/1e6,syncNanos/1e6);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
