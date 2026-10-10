/* Copyright (C) 2026 HoDoKu contributors. GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

/** Real file I/O, failed writes, cross-thread correlation and unavailable diagnostics. */
public final class SaveTraceProbe {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        boolean unavailable=args.length>0&&args[0].equals("unavailable");
        if(unavailable){Files.createDirectories(ApplicationPaths.getDataDirectory().toPath());Files.write(SaveTrace.directory(),new byte[]{1});}
        Path target=ApplicationPaths.getDataDirectory().toPath().resolve("probe.dat");
        try(SaveTrace.Operation operation=SaveTrace.operation("probe");SaveTrace.Scope attempt=SaveTrace.attempt()) {
            AtomicFileIO.write(target.toFile(),file->Files.write(file.toPath(),new byte[]{1,2,3}),file->require(Files.size(file.toPath())==3,"invalid new bytes"));
            SaveTrace.Context captured=SaveTrace.capture();
            SaveTrace.Span queued=SaveTrace.phase("probe.queue_wait");
            ExecutorService executor=Executors.newSingleThreadExecutor();
            try{executor.submit(()->{try(SaveTrace.Scope scope=SaveTrace.bind(captured)){queued.close();SaveTrace.event("probe.worker",null);}}).get();}finally{executor.shutdown();}
            SaveTrace.Trace nested=SaveTrace.begin("nested_probe");
            try(SaveTrace.Operation another=SaveTrace.operation(nested)){
                if(nested!=null)require(SaveTrace.capture().trace==nested,"explicit request inherited outer trace");
            }
            if(captured!=null)require(SaveTrace.capture()==captured,"nested request failed to restore context");
            IOException expected=new IOException("PRIVATE_RAW_DOCUMENT_TEXT");expected.addSuppressed(new IllegalArgumentException("PRIVATE_SUPPRESSED_TEXT"));
            try{SaveTrace.io("probe.failed",target,()->{throw expected;});throw new AssertionError("failure swallowed");}
            catch(IOException actual){require(actual==expected,"changed original exception");}
            operation.outcome("expected_failure");
        }
        require(Files.size(target)==3,"diagnostics changed real save");
        if(unavailable){require(!SaveTrace.flush(2000),"diagnostic failure not visible");System.out.println("Unavailable diagnostic destination did not block actual save");return;}
        require(SaveTrace.flush(2000),"diagnostics did not flush");
        if("false".equalsIgnoreCase(System.getProperty(SaveTrace.ENABLED_PROPERTY))){require(!Files.exists(SaveTrace.directory()),"disabled diagnostics wrote files");System.out.println("Opt-out did not change actual save");return;}
        StringBuilder rows=new StringBuilder();try(java.util.stream.Stream<Path> files=Files.list(SaveTrace.directory())){files.filter(p->p.toString().endsWith(".jsonl")).forEach(p->{try{rows.append(new String(Files.readAllBytes(p),StandardCharsets.UTF_8));}catch(IOException e){throw new UncheckedIOException(e);}});}
        require(rows.indexOf("PRIVATE_")<0,"raw error text leaked into telemetry");
        require(rows.indexOf("IllegalArgumentException")>=0&&rows.indexOf("IOException")>=0,"suppressed error classes omitted");
        require(rows.indexOf("probe.queue_wait")>=0&&rows.indexOf("operation_end")>=0,"cross-thread/root events missing");
        System.out.println("Trace correlation, suppression classes, exception identity, opt-out and real bytes verified");
    }
}
