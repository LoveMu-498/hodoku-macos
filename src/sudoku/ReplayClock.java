package sudoku;

import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/** Effective time uses a monotonic source; wall time is only an event label. EDT-owned. */
public final class ReplayClock {
    private final LongSupplier nanos, wallMillis;
    private final Set<String> pauses=new HashSet<String>();
    private long accumulatedNanos, lastNanos;
    public ReplayClock(){this(System::nanoTime,System::currentTimeMillis);}
    public ReplayClock(LongSupplier nanos,LongSupplier wallMillis){this.nanos=nanos;this.wallMillis=wallMillis;reset(0);}
    public void reset(long elapsedMillis){if(elapsedMillis<0)throw new IllegalArgumentException("Negative elapsed time");accumulatedNanos=elapsedMillis*1000000L;lastNanos=nanos.getAsLong();pauses.clear();}
    private void advance(){long now=nanos.getAsLong();if(pauses.isEmpty())accumulatedNanos+=Math.max(0,now-lastNanos);lastNanos=now;}
    public long elapsedMillis(){advance();return accumulatedNanos/1000000L;}
    public long wallTimeMillis(){return wallMillis.getAsLong();}
    public void pause(String reason){advance();pauses.add(reason);}
    public void resume(String reason){advance();pauses.remove(reason);}
    public boolean isPaused(){return !pauses.isEmpty();}
}
