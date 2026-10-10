/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.File;
import java.nio.file.Files;
import java.util.AbstractList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import solver.SudokuSolver;

/** Progress scoring must not persist its temporary template override, including failure paths. */
public final class TemplateCheckScopeProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("hodoku.save.trace", "false");
        File directory = Files.createTempDirectory("hodoku-template-scope-").toFile();
        System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, directory.getPath());
        Options.instance = new Options();
        Options.getInstance().setCheckTemplates(true);
        CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        RuntimeException injected = new RuntimeException("injected scoring failure");
        Thread worker = new Thread(() -> {
            try {
                try {
                    new SudokuSolver().getProgressScore(new Sudoku2(), new AbstractList<SolutionStep>() {
                        @Override public int size() { return 1; }
                        @Override public SolutionStep get(int index) {
                            require(!Options.getInstance().isCheckTemplates(), "scoring override was not active");
                            entered.countDown();
                            try { require(resume.await(5, TimeUnit.SECONDS), "probe release missing"); }
                            catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                            throw injected;
                        }
                    }, null);
                    throw new AssertionError("injected scoring failure disappeared");
                } catch (RuntimeException expected) {
                    require(expected == injected, "scoring did not preserve original exception");
                }
                require(Options.getInstance().isCheckTemplates(), "worker override leaked after exception");
            } catch (Throwable unexpected) { failure.set(unexpected); }
        }, "probe scoring worker");
        worker.start();
        require(entered.await(5, TimeUnit.SECONDS), "scoring did not reach controlled boundary");
        require(Options.getInstance().isCheckTemplates(), "worker changed the user's preference");
        Options.getInstance().writeOptionsSafely();
        require(Options.decodeOptions(ApplicationPaths.getConfigurationFile()).isCheckTemplates(),
                "save persisted a transient scoring value");
        resume.countDown(); worker.join(5000);
        require(!worker.isAlive() && failure.get() == null, "scoring scope recovery failed: " + failure.get());
        try (Options.TemplateCheckScope outer = Options.withTemplateChecks(false)) {
            require(!Options.getInstance().isCheckTemplates(), "outer override missing");
            Options.getInstance().setInitialWidth(1234);
            Options.getInstance().writeOptionsSafely();
            require(Options.decodeOptions(ApplicationPaths.getConfigurationFile()).isCheckTemplates(),
                    "same-thread save encoded the runtime override instead of the stored preference");
            try (Options.TemplateCheckScope inner = Options.withTemplateChecks(true)) {
                require(Options.getInstance().isCheckTemplates(), "inner override missing");
            }
            require(!Options.getInstance().isCheckTemplates(), "nested close did not restore outer override");
        }
        require(Options.getInstance().isCheckTemplates(), "scope close did not restore persisted preference");
        System.out.println("Template scoring scope checks passed: concurrent preference, persisted snapshot, exception, nesting");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
