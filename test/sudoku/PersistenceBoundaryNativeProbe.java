/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import solver.SudokuSolver;

/** Real modal nested loops, a blocked solver worker, and deferred native quit without busy waiting. */
public final class PersistenceBoundaryNativeProbe {
    private static final String PUZZLE = "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    private static final CountDownLatch PAINTED = new CountDownLatch(1);
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class PaintedFrame extends MainFrame {
        PaintedFrame() { super(null); }
        @Override public void paint(Graphics graphics) { super.paint(graphics); PAINTED.countDown(); }
    }

    /** The real native QuitCoordinator invokes these public response methods reflectively. */
    public static final class Response {
        final AtomicInteger performed = new AtomicInteger(), cancelled = new AtomicInteger();
        final CountDownLatch answered = new CountDownLatch(1);
        final AtomicBoolean workerFinished, finalApplied;
        final AtomicReference<Throwable> failure;
        Response(AtomicBoolean workerFinished, AtomicBoolean finalApplied, AtomicReference<Throwable> failure) {
            this.workerFinished = workerFinished; this.finalApplied = finalApplied; this.failure = failure;
        }
        public void performQuit() {
            if (!workerFinished.get() || !finalApplied.get()) failure.compareAndSet(null, new AssertionError("native quit performed before complete worker/UI state"));
            performed.incrementAndGet(); answered.countDown();
        }
        public void cancelQuit() { cancelled.incrementAndGet(); answered.countDown(); }
    }

    private static final class ControlledSolver extends SudokuSolver {
        final SudokuSolver liveSolver;
        final CountDownLatch visible, started = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicBoolean finished = new AtomicBoolean();
        final AtomicReference<Throwable> failure;
        ControlledSolver(SudokuSolver liveSolver, CountDownLatch visible, AtomicReference<Throwable> failure) {
            this.liveSolver = liveSolver; this.visible = visible; this.failure = failure;
        }
        @Override public boolean solve(DifficultyLevel level, Sudoku2 temporary, boolean rejectLow, SolverProgressDialog dialog) {
            try {
                require(visible.await(10, TimeUnit.SECONDS), "solver modal never became visible");
                // This is the same shared solver result list that GuiState.get() reads.
                liveSolver.getSteps().clear(); liveSolver.getSteps().add(step(9));
                started.countDown();
                require(release.await(25, TimeUnit.SECONDS), "controlled worker was not released");
                liveSolver.getSteps().clear(); liveSolver.getSteps().add(step(4));
                finished.set(true); return true;
            } catch (Throwable error) {
                failure.compareAndSet(null, error); started.countDown(); return false;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        require(System.getProperty("hodoku.data.dir") != null, "isolated hodoku.data.dir required");
        Path report = args.length == 0 ? ApplicationPaths.getDataDirectory().toPath().resolve("boundary-probe") : Paths.get(args[0]);
        Files.createDirectories(report);
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        MainFrame[] frame = {null}; JDialog[] ordinary = {null}; SolverProgressDialog[] computing = {null};
        ControlledSolver[] controlled = {null}; Timer[] ticker = {null}; AtomicInteger ticks = new AtomicInteger();
        Properties result = new Properties(); boolean passed = false;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.invokeAndWait(() -> {
                frame[0] = new PaintedFrame(); frame[0].setVisible(true); frame[0].setPuzzle(PUZZLE);
                frame[0].getSudokuPanel().getSolver().getSteps().clear();
                ticker[0] = new Timer(20, event -> ticks.incrementAndGet()); ticker[0].start();
            });
            require(PAINTED.await(15, TimeUnit.SECONDS), "native frame did not paint");
            SaveProgress.Work[] baseline = {null};
            SwingUtilities.invokeAndWait(() -> {
                try { baseline[0] = (SaveProgress.Work) invoke(frame[0], "prepareApplicationSave"); }
                catch (Exception error) { throw new RuntimeException(error); }
            });
            baseline[0].run((stage, percent) -> {}); // Outside EDT; fences older queued snapshots before the assertions.
            SwingUtilities.invokeAndWait(() -> {
                try {
                    frame[0].getReplayController().cancelQuit();
                    ((SessionPersistence) field(MainFrame.class, "sessionPersistence").get(frame[0])).start();
                    SettingsPersistence.start();
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            byte[] sessionBefore = digest(ApplicationPaths.getSessionFile().toPath());
            byte[] settingsBefore = digest(ApplicationPaths.getConfigurationFile().toPath());
            final boolean initialCandidates = Options.getInstance().isShowCandidates();

            // Even ordinary configuration modals must not autosave unconfirmed preview settings.
            CountDownLatch ordinaryVisible = new CountDownLatch(1), ordinaryReturned = new CountDownLatch(1);
            long[] ordinarySequences = new long[2];
            SwingUtilities.invokeLater(() -> {
                try {
                    JDialog dialog = new JDialog(frame[0], "配置边界验证", true); ordinary[0] = dialog;
                    dialog.add(new JLabel("受控配置预览；验证结束后关闭")); dialog.pack(); dialog.setLocationRelativeTo(frame[0]);
                    dialog.addWindowListener(new WindowAdapter() { @Override public void windowOpened(WindowEvent event) {
                        try {
                            require(!PersistenceUiBoundary.canCaptureInBackground(), "ordinary modal allowed background capture");
                            require(PersistenceUiBoundary.computationDialog() == null, "ordinary config modal misclassified as computation");
                            ordinarySequences[0] = sessionSequence(frame[0]); ordinarySequences[1] = settingsSequence();
                            Options.getInstance().setShowCandidates(!initialCandidates); frame[0].check(); ordinaryVisible.countDown();
                        } catch (Throwable error) { failure.compareAndSet(null, error); ordinaryVisible.countDown(); }
                    }});
                    dialog.setVisible(true); ordinaryReturned.countDown();
                } catch (Throwable error) { failure.compareAndSet(null, error); ordinaryVisible.countDown(); ordinaryReturned.countDown(); }
            });
            require(ordinaryVisible.await(10, TimeUnit.SECONDS), "configuration modal never opened");
            int beforeOrdinaryTicks = ticks.get(); Thread.sleep(5500);
            assertNotCaptured(frame[0], ordinarySequences, sessionBefore, settingsBefore, "ordinary configuration modal");
            require(ticks.get() - beforeOrdinaryTicks >= 20, "configuration modal stopped EDT timers");
            result.setProperty("configuration_modal_ticks", String.valueOf(ticks.get() - beforeOrdinaryTicks));
            SwingUtilities.invokeAndWait(() -> { Options.getInstance().setShowCandidates(initialCandidates); ordinary[0].dispose(); });
            require(ordinaryReturned.await(5, TimeUnit.SECONDS), "configuration modal did not return");

            CountDownLatch solverVisible = new CountDownLatch(1), solverReturned = new CountDownLatch(1);
            AtomicBoolean applied = new AtomicBoolean(); long[] solverSequences = new long[2];
            SwingUtilities.invokeLater(() -> {
                try {
                    controlled[0] = new ControlledSolver(frame[0].getSudokuPanel().getSolver(), solverVisible, failure);
                    SolverProgressDialog dialog = new SolverProgressDialog(frame[0], true, controlled[0]); computing[0] = dialog;
                    dialog.setLocationRelativeTo(frame[0]);
                    dialog.addWindowListener(new WindowAdapter() { @Override public void windowOpened(WindowEvent event) {
                        try {
                            require(PersistenceUiBoundary.computationDialog() == dialog, "real solver dialog not recognized");
                            require(!PersistenceUiBoundary.canCaptureInBackground(), "solver modal allowed background capture");
                            solverSequences[0] = sessionSequence(frame[0]); solverSequences[1] = settingsSequence();
                            Options.getInstance().setShowCandidates(!initialCandidates); frame[0].check(); solverVisible.countDown();
                        } catch (Throwable error) { failure.compareAndSet(null, error); solverVisible.countDown(); }
                    }});
                    dialog.setVisible(true);
                    require(controlled[0].finished.get(), "dialog returned before worker completed");
                    frame[0].getSudokuPanel().setCell(0, 2, 4);
                    frame[0].getSudokuPanel().getSolver().getSteps().clear(); frame[0].getSudokuPanel().getSolver().getSteps().add(step(4));
                    Options.getInstance().setShowCandidates(initialCandidates);
                    frame[0].getReplayController().afterAction(); frame[0].check(); applied.set(true); solverReturned.countDown();
                } catch (Throwable error) { failure.compareAndSet(null, error); solverVisible.countDown(); solverReturned.countDown(); }
            });
            require(solverVisible.await(10, TimeUnit.SECONDS), "solver modal did not open");
            require(controlled[0].started.await(5, TimeUnit.SECONDS), "controlled worker did not expose temporary data");
            Response response = new Response(controlled[0].finished, applied, failure);
            MacOSApplication.QuitCoordinator coordinator = new MacOSApplication.QuitCoordinator(request -> {
                try { Method method = MainFrame.class.getDeclaredMethod("requestQuit", MacOSApplication.QuitRequest.class); method.setAccessible(true); method.invoke(frame[0], request); }
                catch (Throwable error) { failure.compareAndSet(null, error); }
            });
            SwingUtilities.invokeAndWait(() -> {
                coordinator.requestQuit(response);
                require(SaveFailure.text("waitComputation").equals(computing[0].getTitle()), "waiting status was not presented on the current dialog");
                require(response.performed.get() == 0 && response.cancelled.get() == 0, "native response completed while worker owned model");
            });
            int beforeSolverTicks = ticks.get(); Thread.sleep(5500);
            assertNotCaptured(frame[0], solverSequences, sessionBefore, settingsBefore, "solver temporary model");
            require(response.performed.get() == 0 && response.cancelled.get() == 0, "quit bypassed the blocked worker");
            require(ticks.get() - beforeSolverTicks >= 20, "waiting for computation blocked EDT timers");
            result.setProperty("solver_wait_ticks", String.valueOf(ticks.get() - beforeSolverTicks));
            controlled[0].release.countDown();
            require(solverReturned.await(10, TimeUnit.SECONDS), "completed solver UI did not return");
            require(response.answered.await(15, TimeUnit.SECONDS), "deferred quit did not complete after solver");
            if (failure.get() != null) throw new AssertionError(failure.get());
            require(response.performed.get() == 1 && response.cancelled.get() == 0, "native quit handshake not exactly once");
            SessionSnapshot saved = new SessionStore(ApplicationPaths.getSessionFile()).load();
            require(saved.getGuiState().getSudoku().getValue(2) == 4, "final complete board was not saved");
            List<SolutionStep> steps = saved.getGuiState().getSteps();
            require(steps.size() == 1 && steps.get(0).getValues().equals(java.util.Collections.singletonList(4)), "temporary solver result was saved");
            require(Options.decodeOptions(ApplicationPaths.getConfigurationFile()).isShowCandidates() == initialCandidates, "unconfirmed temporary setting was saved");
            ReplaySession replay = ReplayStore.read(ApplicationPaths.getDataDirectory().toPath().resolve("replays").resolve(saved.getReplayId() + ".hrep"));
            ReplayFiles.validate(replay); require(replay.last().board.equals(new ReplayBoard(saved.getGuiState().getSudoku())), "replay missed final complete model");
            require(SaveTrace.flush(2000), "trace writer did not flush");
            double waitMillis = waitDuration(); require(waitMillis >= 5000, "quit.wait_for_computation end span missing or too short");
            result.setProperty("quit_wait_for_computation_ms", String.valueOf(waitMillis)); result.setProperty("native_performed", "1");
            result.setProperty("configuration_and_session_captures_during_modals", "0"); result.setProperty("restored_final_board_and_step", "4");
            try (OutputStream out = Files.newOutputStream(report.resolve("boundary-result.properties"))) { result.store(out, "Real Swing modal/solver boundary checks"); }
            System.out.println("PASS modal boundaries: configuration previews and solver intermediate model were not captured; EDT remained responsive; native quit waited for complete worker/UI state; final session/settings/replay read back");
            System.out.println("quit.wait_for_computation=" + waitMillis + " ms, properties=" + report.resolve("boundary-result.properties")); passed = true;
        } catch (Throwable error) {
            error.printStackTrace();
        } finally {
            if (controlled[0] != null) controlled[0].release.countDown();
            SwingUtilities.invokeAndWait(() -> {
                if (ticker[0] != null) ticker[0].stop(); SettingsPersistence.suspend();
                if (frame[0] != null) {
                    try { Timer deferred = (Timer) field(MainFrame.class, "deferredQuitTimer").get(frame[0]); if (deferred != null) deferred.stop(); }
                    catch (Exception ignored) { }
                    if (computing[0] != null) computing[0].dispose(); if (ordinary[0] != null) ordinary[0].dispose(); frame[0].dispose();
                }
            });
            if (!passed) System.err.println("Boundary probe failed; isolated diagnostic files retained in " + report);
        }
        System.exit(passed ? 0 : 1);
    }

    private static SolutionStep step(int value) { SolutionStep result = new SolutionStep(SolutionType.NAKED_SINGLE); result.getIndices().add(2); result.getValues().add(value); return result; }
    private static Object invoke(Object owner, String name) throws Exception { Method method = MainFrame.class.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(owner); }
    private static Field field(Class<?> type, String name) throws Exception { Field result = type.getDeclaredField(name); result.setAccessible(true); return result; }
    private static long sessionSequence(MainFrame frame) throws Exception { Object persistence = field(MainFrame.class, "sessionPersistence").get(frame); return field(SessionPersistence.class, "sequence").getLong(persistence); }
    private static long settingsSequence() throws Exception { return ((AtomicLong) field(SettingsPersistence.class, "SEQUENCE").get(null)).get(); }
    private static byte[] digest(Path file) throws Exception { return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)); }
    private static void assertNotCaptured(MainFrame frame, long[] expected, byte[] session, byte[] settings, String reason) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try { require(sessionSequence(frame) == expected[0], reason + " advanced session capture sequence"); require(settingsSequence() == expected[1], reason + " advanced settings capture sequence"); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        require(Arrays.equals(session, digest(ApplicationPaths.getSessionFile().toPath())), reason + " replaced the session file");
        require(Arrays.equals(settings, digest(ApplicationPaths.getConfigurationFile().toPath())), reason + " replaced confirmed settings");
    }
    private static double waitDuration() throws Exception {
        Pattern duration = Pattern.compile("\\\"duration_ms\\\":([0-9.]+)"); double found = -1;
        try (java.util.stream.Stream<Path> files = Files.list(SaveTrace.directory())) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".jsonl"))::iterator)
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
                    if (line.contains("\"event\":\"phase_end\"") && line.contains("\"phase\":\"quit.wait_for_computation\"")) {
                        Matcher match = duration.matcher(line); if (match.find()) found = Math.max(found, Double.parseDouble(match.group(1)));
                    }
        }
        return found;
    }
}
