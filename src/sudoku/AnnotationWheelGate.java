package sudoku;

/** One step per wheel gesture; fractional trackpad deltas must accumulate first. */
final class AnnotationWheelGate {
    private long last;
    private double accumulated;
    private boolean latched;
    int step(double delta, long now) {
        if (delta == 0) return 0;
        if (last == 0 || now - last >= 350) { accumulated = 0; latched = false; }
        last = now;
        if (latched) return 0;
        if (accumulated * delta < 0) accumulated = 0;
        accumulated += delta;
        if (Math.abs(accumulated) < 1.0) return 0;
        latched = true;
        return accumulated > 0 ? 1 : -1;
    }
    void reset() { last = 0; accumulated = 0; latched = false; }
}
