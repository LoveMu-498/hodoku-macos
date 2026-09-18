package solver;

import java.util.function.Consumer;
import sudoku.SolutionStep;

/** Request-local observation of complete native proofs before recommendation deduplication.
 * The observer must copy retained steps; it never changes solver search or shared options.
 */
public final class NativeProofCollector implements AutoCloseable {
    private static final ThreadLocal<Consumer<SolutionStep>> ACTIVE = new ThreadLocal<>();
    private final Consumer<SolutionStep> previous;
    public NativeProofCollector(Consumer<SolutionStep> observer) {
        previous = ACTIVE.get(); ACTIVE.set(observer);
    }
    public static boolean active() { return ACTIVE.get() != null; }
    public static void offer(SolutionStep step) {
        Consumer<SolutionStep> observer = ACTIVE.get();
        if (observer != null) { SearchCancellation.check(); observer.accept(step); }
    }
    public static SolutionStep copy(SolutionStep step) {
        SolutionStep copy = (SolutionStep) step.clone();
        copy.getChains().clear();
        for (sudoku.Chain chain : step.getChains()) copy.addChain((sudoku.Chain) chain.clone());
        return copy;
    }
    @Override public void close() {
        if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
    }
}
