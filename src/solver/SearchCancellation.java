package solver;

/** Cooperative cancellation is enabled only for disposable background searches. */
public final class SearchCancellation {
    private static final ThreadLocal<Boolean> ENABLED=new ThreadLocal<Boolean>();
    private SearchCancellation() {}
    public static Scope enable(){return new Scope();}
    public static void check(){
        if(Thread.currentThread().isInterrupted() && Boolean.TRUE.equals(ENABLED.get()))
            throw new java.util.concurrent.CancellationException("Search canceled");
    }
    public static final class Scope implements AutoCloseable {
        private final Boolean previous;
        private Scope(){previous=ENABLED.get();ENABLED.set(Boolean.TRUE);}
        public void close(){if(previous==null)ENABLED.remove();else ENABLED.set(previous);}
    }
}
