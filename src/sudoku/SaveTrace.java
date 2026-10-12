/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;

/** Local, bounded, asynchronous diagnostics. Never gate a real save on diagnostic I/O. */
final class SaveTrace {
    static final String ENABLED_PROPERTY="hodoku.save.trace";
    private static final boolean ENABLED=!"false".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY));
    private static final Logger LOG=Logger.getLogger(SaveTrace.class.getName());
    private static final String RUN=UUID.randomUUID().toString();
    private static final String PID=ManagementFactory.getRuntimeMXBean().getName().split("@",2)[0];
    private static final ThreadMXBean CPU=ManagementFactory.getThreadMXBean();
    private static final ThreadLocal<Context> CURRENT=new ThreadLocal<>();
    private static final AtomicLong IDS=new AtomicLong();
    private static final AtomicReference<Trace> EXIT=new AtomicReference<>();
    private static final AtomicBoolean WARNED=new AtomicBoolean();
    private static final ThreadPoolExecutor WRITER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(256),r->{Thread t=new Thread(r,"HoDoKu save diagnostics");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY-2);return t;});
    private static FileHandler handler;
    private static boolean unavailable;
    static {
        WRITER.allowCoreThreadTimeOut(true);
        if(ENABLED)try{Runtime.getRuntime().addShutdownHook(new Thread(SaveTrace::shutdown,"HoDoKu save trace shutdown"));}
        catch(RuntimeException e){warn(e);}
    }
    private SaveTrace(){}
    static Path directory(){return ApplicationPaths.getDataDirectory().toPath().resolve("diagnostics");}
    static Trace begin(String operation){
        return begin(operation,System.nanoTime());
    }
    static Trace begin(String operation,long requestedNanos){
        if(!ENABLED)return null;
        abandonExit("next_operation");
        Trace trace=new Trace(operation,requestedNanos);trace.record("operation_start","",0,0,null,null,null);return trace;
    }
    static Operation operation(String name){
        Context prior=CURRENT.get();Trace trace=prior==null?begin(name):prior.trace;
        return new Operation(trace,prior,prior==null);
    }
    static Operation operation(Trace trace){return new Operation(trace,CURRENT.get(),true);}
    static Context capture(){return CURRENT.get();}
    static Scope bind(Context context){return new Scope(context);}
    static Context context(Trace trace,int attempt){return trace==null?null:new Context(trace,attempt);}
    static Scope attempt(){Context c=CURRENT.get();return bind(c==null?null:new Context(c.trace,c.trace.attempts.incrementAndGet()));}
    static Span phase(String name){return phase(name,null);}
    static Span phase(String name,Path path){Context c=CURRENT.get();return c==null?Span.NONE:new Span(c,name,path);}
    static void event(String name,String detail){Context c=CURRENT.get();if(c!=null)c.trace.record("event",name,0,c.attempt,null,detail,null);}
    static void exiting(String route){
        Context c=CURRENT.get();if(c==null)return;
        c.trace.exiting=true;EXIT.set(c.trace);event("quit.exit_requested",route);
    }
    static void abandonExit(String reason){Trace prior=EXIT.getAndSet(null);if(prior!=null)prior.end("quit_response_returned",reason);}
    interface Checked {void run()throws Exception;}
    interface IOAction {void run()throws IOException;}
    interface IOValue<T> {T run()throws IOException;}
    static void checked(String name,Path path,Checked action)throws Exception{
        try(Span span=phase(name,path)){try{action.run();}catch(Exception|Error e){span.fail(e);throw e;}}
    }
    static void io(String name,Path path,IOAction action)throws IOException{
        try(Span span=phase(name,path)){try{action.run();}catch(IOException|RuntimeException|Error e){span.fail(e);throw e;}}
    }
    static <T>T ioValue(String name,Path path,IOValue<T> action)throws IOException{
        try(Span span=phase(name,path)){try{return action.run();}catch(IOException|RuntimeException|Error e){span.fail(e);throw e;}}
    }
    static final class Context {
        final Trace trace;final int attempt;
        Context(Trace trace,int attempt){this.trace=trace;this.attempt=attempt;}
    }
    static final class Scope implements AutoCloseable {
        private final Context prior;
        Scope(Context context){prior=CURRENT.get();if(context==null)CURRENT.remove();else CURRENT.set(context);}
        @Override public void close(){if(prior==null)CURRENT.remove();else CURRENT.set(prior);}
    }
    static final class Operation implements AutoCloseable {
        final Trace trace;private final Context prior;private final boolean owner;private String outcome="ok";
        Operation(Trace trace,Context prior,boolean owner){this.trace=trace;this.prior=prior;this.owner=owner;if(trace!=null)CURRENT.set(prior!=null&&prior.trace==trace?prior:new Context(trace,0));}
        void outcome(String value){outcome=value;}
        @Override public void close(){try{if(owner&&trace!=null){if(trace.exiting)trace.record("event","quit.response_returned",0,0,null,null,null);else trace.end(outcome,"operation_return");}}finally{if(prior==null)CURRENT.remove();else CURRENT.set(prior);}}
    }
    static final class Trace {
        final long id=IDS.incrementAndGet(),started;final String operation;
        final AtomicInteger attempts=new AtomicInteger();final AtomicLong spans=new AtomicLong(),dropped=new AtomicLong();
        volatile boolean exiting;private final AtomicBoolean ended=new AtomicBoolean();
        Trace(String operation,long requestedNanos){this.operation=operation;this.started=requestedNanos;}
        void event(String name,String detail){record("event",name,0,attempts.get(),null,detail,null);}
        void end(String outcome,String boundary){if(ended.compareAndSet(false,true))record("operation_end",boundary,0,attempts.get(),outcome,null,null);}
        void record(String event,String phase,long span,int attempt,String outcome,String detail,String extra){
            long now=System.nanoTime();StringBuilder row=new StringBuilder(640);
            row.append("{\"schema\":1,\"run_id\":").append(json(RUN)).append(",\"pid\":").append(json(PID))
                    .append(",\"trace_id\":").append(id).append(",\"operation\":").append(json(operation))
                    .append(",\"attempt\":").append(attempt).append(",\"event\":").append(json(event))
                    .append(",\"phase\":").append(json(phase)).append(",\"span_id\":").append(span)
                    .append(",\"wall_epoch_ms\":").append(System.currentTimeMillis()).append(",\"elapsed_ms\":").append(ms(now-started))
                    .append(",\"thread\":").append(json(Thread.currentThread().getName())).append(",\"outcome\":").append(json(outcome))
                    .append(",\"detail\":").append(json(detail)).append(",\"dropped_events\":").append(dropped.get());
            if(extra!=null)row.append(extra);row.append('}');publish(this,row.toString());
        }
    }
    static final class Span implements AutoCloseable {
        static final Span NONE=new Span();
        private final Context context;private final String name;private final Path path;
        private final long id,start,threadId,cpuStart;private final String startThread;
        private final AtomicBoolean closed=new AtomicBoolean();private String outcome="ok",errorTypes;
        private Span(){context=null;name=null;path=null;id=start=threadId=cpuStart=0;startThread=null;}
        Span(Context c,String name,Path path){this.context=c;this.name=name;this.path=path;id=c.trace.spans.incrementAndGet();start=System.nanoTime();threadId=Thread.currentThread().getId();startThread=Thread.currentThread().getName();cpuStart=cpu();c.trace.record("phase_start",name,id,c.attempt,null,null,extra(false));}
        void fail(Throwable error){if(context!=null){outcome="failed";StringBuilder types=new StringBuilder();Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());Deque<Throwable> pending=new ArrayDeque<>();if(error!=null)pending.add(error);while(!pending.isEmpty()&&seen.size()<16){Throwable e=pending.removeFirst();if(!seen.add(e))continue;if(types.length()>0)types.append(" -> ");types.append(e.getClass().getSimpleName());if(e.getCause()!=null)pending.addLast(e.getCause());for(Throwable suppressed:e.getSuppressed())if(pending.size()<16)pending.addLast(suppressed);}errorTypes=types.toString();}}
        void outcome(String value){if(context!=null)outcome=value;}
        private String extra(boolean finished){
            StringBuilder out=new StringBuilder();out.append(",\"start_ms\":").append(ms(start-context.trace.started)).append(",\"start_thread\":").append(json(startThread));
            if(path!=null)out.append(",\"target\":").append(json(path.toAbsolutePath().normalize().toString()));
            if(finished){long end=System.nanoTime(),endCpu=cpu();out.append(",\"duration_ms\":").append(ms(end-start)).append(",\"cpu_ms\":");
                if(threadId==Thread.currentThread().getId()&&cpuStart>=0&&endCpu>=cpuStart)out.append(ms(endCpu-cpuStart));else out.append("null");
                out.append(",\"error_types\":").append(json(errorTypes));}
            return out.toString();
        }
        @Override public void close(){if(context!=null&&closed.compareAndSet(false,true))context.trace.record("phase_end",name,id,context.attempt,outcome,null,extra(true));}
    }
    private static long cpu(){try{return CPU.isThreadCpuTimeSupported()&&CPU.isThreadCpuTimeEnabled()?CPU.getCurrentThreadCpuTime():-1;}catch(RuntimeException e){return -1;}}
    private static String ms(long nanos){long micros=Math.max(0,nanos)/1000,tail=micros%1000;return (micros/1000)+"."+(tail<100?"0":"")+(tail<10?"0":"")+tail;}
    private static String json(String value){
        if(value==null)return "null";StringBuilder out=new StringBuilder(value.length()+2).append('"');
        for(int i=0;i<value.length();i++){char c=value.charAt(i);switch(c){case '"':out.append("\\\"");break;case '\\':out.append("\\\\");break;case '\n':out.append("\\n");break;case '\r':out.append("\\r");break;case '\t':out.append("\\t");break;default:if(c<32)out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}}
        return out.append('"').toString();
    }
    private static void publish(Trace trace,String line){
        try{WRITER.execute(()->{if(unavailable)return;try{
            if(handler==null){Files.createDirectories(directory());handler=new FileHandler(directory().resolve("save-trace-%u-%g.jsonl").toString(),1024*1024,3,true);handler.setEncoding("UTF-8");handler.setFormatter(new java.util.logging.Formatter(){@Override public String format(LogRecord record){return record.getMessage()+"\n";}});handler.setErrorManager(new ErrorManager(){@Override public void error(String message,Exception error,int code){unavailable=true;warn(error==null?new IOException(message):error);}});}
            handler.publish(new LogRecord(Level.INFO,line));handler.flush();
        }catch(Exception e){unavailable=true;warn(e);}});}catch(RejectedExecutionException e){trace.dropped.incrementAndGet();}
    }
    private static void warn(Exception e){if(WARNED.compareAndSet(false,true))LOG.log(Level.WARNING,"Save timings unavailable; real saves continue normally",e);}
    static boolean flush(long timeoutMillis){
        if(!ENABLED)return true;
        try{WRITER.submit(()->{if(handler!=null)handler.flush();}).get(Math.max(1,timeoutMillis),TimeUnit.MILLISECONDS);return !unavailable;}
        catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
        catch(ExecutionException|TimeoutException|RejectedExecutionException e){return false;}
    }
    private static void shutdown(){
        Trace trace=EXIT.get();long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(100);
        if(trace!=null)try(Scope scope=bind(context(trace,trace.attempts.get()));Span hook=phase("jvm.shutdown_hook")){
            try(Span flush=phase("telemetry.flush")){if(!flush(remaining(deadline)))flush.outcome("timeout_or_unavailable");}
        }
        if(trace!=null)trace.end("quit_requested","shutdown_hook");
        flush(remaining(deadline));
    }
    private static long remaining(long deadline){return Math.max(1,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));}
}
