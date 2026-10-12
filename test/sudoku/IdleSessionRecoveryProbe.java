/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/** Separate JVMs prove committed native state survives a dirty replay checkpoint. */
public final class IdleSessionRecoveryProbe {
    private static final String PUZZLE = "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static void child(Path data, String scenario, String phase) throws Exception {
        Process process = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-Xmx512m",
                "-Dhodoku.data.dir=" + data, "-cp", System.getProperty("java.class.path"),
                IdleSessionRecoveryProbe.class.getName(), data.toString(), scenario, phase).inheritIO().start();
        if (!process.waitFor(25, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Isolated recovery child timed out: " + scenario + "/" + phase);
        }
        check(process.exitValue() == (phase.equals("write") ? 7 : 0), "Recovery child failed: " + scenario + "/" + phase);
    }
    public static void main(String[] args) throws Exception {
        Path data = Paths.get(args[0]);
        if (args.length == 1) {
            for (String scenario : new String[]{"equal", "time-only", "replay-ahead", "board-conflict", "foreign-id", "native-ahead", "native-roundtrip", "new-bookmarks"}) {
                Path target = data.resolve(scenario); Files.createDirectories(target);
                child(target, scenario, "write"); child(target, scenario, "read");
            }
            System.out.println("PASS equal-frame idle annotations/history/savepoints/time recovery, replay precedence, board and attempt identity guards");
            return;
        }
        String scenario = args[1]; boolean write = args[2].equals("write");
        Throwable[] failure = {null}; MainFrame[] frame = {null};
        SwingUtilities.invokeAndWait(() -> {
            try {
                frame[0] = new MainFrame(null);
                MainFrame owner = frame[0]; SudokuPanel panel = owner.getSudokuPanel();
                ReplayController replay = owner.getReplayController();
                if (write) {
                    owner.setPuzzle(PUZZLE); replay.flushPendingSaves(); replay.checkpoint(false);
                    if (!scenario.equals("time-only")) {
                        panel.pushUndo();
                        panel.handleColoring(0, 3, 6, Color.BLUE);
                        GuiState point = new GuiState(panel, panel.getSolver(), owner.getSolutionPanel());
                        point.setIncludeAnnotations(true); point.get(true); point.setName("idle native point");
                        point.setTimestamp(new Date(1000)); owner.getSavePoints().add(point);
                        if (scenario.equals("new-bookmarks")) {
                            owner.getSavePoints().add(point); // Repeated name/board: preserve multiplicity.
                            replay.createSavePointMarker(point.getName()); replay.createSavePointMarker(point.getName());
                            replay.flushPendingSaves();
                        }
                    }
                    Field field = MainFrame.class.getDeclaredField("sessionPersistence"); field.setAccessible(true);
                    SessionPersistence persistence = (SessionPersistence) field.get(owner); persistence.suspend();
                    persistence.commit(persistence.captureNow(), false);
                    SessionStore store = new SessionStore(ApplicationPaths.getSessionFile());
                    SessionSnapshot saved = store.load();
                    check(saved.getReplayFrameCount() == replay.session().frameCount(), "Fixture must start with equal frame counts");
                    if (scenario.equals("time-only")) {
                        replay.session().elapsedMillis = 9000;
                        persistence.commit(persistence.captureNow(), true); // Strict native fallback if replay time cannot be committed.
                    } else if (scenario.equals("replay-ahead")) {
                        panel.handleColoring(0, 3, 6, Color.RED);
                        panel.setCell(0, 2, 4); replay.afterAction(); replay.flushPendingSaves(); replay.checkpoint(false);
                        replay.flushPendingSaves();
                    } else if (scenario.equals("board-conflict")) {
                        saved.getGuiState().getSudoku().setCell(2, 4); store.save(saved);
                    } else if (scenario.equals("foreign-id")) {
                        saved.setReplayId("different-attempt"); store.save(saved);
                    } else if (scenario.equals("native-ahead") || scenario.equals("native-roundtrip")) {
                        panel.setCell(0, 2, 4); replay.afterAction(); replay.flushPendingSaves();
                        if (scenario.equals("native-roundtrip")) {
                            panel.setCell(0, 2, 0); replay.afterAction(); replay.flushPendingSaves();
                        }
                        persistence.commit(persistence.captureNow(), false);
                        ReplaySession disk = ReplayStore.read(replay.directory().resolve(replay.session().id + ".hrep"));
                        ReplaySession old = new ReplaySession(disk.id, disk.startedAt);
                        old.loadFrame(disk.frames().get(0)); old.setInitialAnnotations(disk.initialAnnotations());
                        ReplayStore.write(replay.directory().resolve(old.id + ".hrep"), old);
                        new ReplayRecovery(replay.directory()).checkpoint(old, false);
                    } else if (scenario.equals("new-bookmarks")) {
                        replay.createSavePointMarker("idle native point"); replay.flushPendingSaves();
                        check(ReplayStore.read(replay.directory().resolve(replay.session().id + ".hrep")).bookmarks().size() == 3, "New bookmark must be durable before crash");
                    }
                } else {
                    boolean retain = scenario.equals("equal") || scenario.equals("native-ahead") || scenario.equals("native-roundtrip") || scenario.equals("new-bookmarks");
                    GuiState actual = new GuiState(); actual.setIncludeAnnotations(true); panel.getState(actual, true);
                    Color expected = retain ? Color.BLUE : scenario.equals("replay-ahead") ? Color.RED : null;
                    check(java.util.Objects.equals(expected, actual.getColoringCandidateMap().get(36)), "Incorrect native annotation precedence: " + scenario);
                    if (retain) {
                        check(actual.getUndoStack().size() > 0, "Native undo history was dropped");
                        check(actual.getColoringUndoCandidates().size() > 0, "Native annotation undo history was dropped");
                        check(owner.getSavePoints().size() == (scenario.equals("new-bookmarks") ? 3 : 1), "Native/new durable savepoint multiplicity was lost");
                        GuiState point = owner.getSavePoints().get(0);
                        check(point.getName().equals("idle native point") && Color.BLUE.equals(point.getColoringCandidateMap().get(36)), "Savepoint annotations were lost");
                        if (scenario.equals("native-ahead")) check(replay.session().last().kind.equals("evidence-unavailable"), "Missing durable operations must be disclosed in replay");
                        else check(replay.session().frameCount() == 1, "Annotation-only recovery must not fabricate operations");
                        if (scenario.equals("native-roundtrip")) check(!replay.session().interruption.isEmpty(), "Missing roundtrip history must be disclosed");
                        java.lang.reflect.Method undo = SudokuPanel.class.getDeclaredMethod("undoColoring"); undo.setAccessible(true); undo.invoke(panel);
                        GuiState undone = new GuiState(); panel.getState(undone, true);
                        check(!Color.BLUE.equals(undone.getColoringCandidateMap().get(36)), "Recovered annotation undo is unusable");
                        java.lang.reflect.Method redo = SudokuPanel.class.getDeclaredMethod("redoColoring"); redo.setAccessible(true); redo.invoke(panel);
                        GuiState redone = new GuiState(); panel.getState(redone, true);
                        check(Color.BLUE.equals(redone.getColoringCandidateMap().get(36)), "Recovered annotation redo is unusable");
                    } else check(owner.getSavePoints().isEmpty(), "Unrelated native savepoint survived");
                    check(panel.getSudoku().getValue(2) == (scenario.equals("replay-ahead") || scenario.equals("native-ahead") ? 4 : 0), "Incorrect durable board precedence");
                    if (scenario.equals("time-only")) {
                        check(replay.session().elapsedMillis >= 9000, "Strict native elapsed fallback was lost");
                        check(replay.session().frameCount() == 1, "Time-only recovery must not fabricate operations");
                    }
                    ReplayFiles.validate(replay.session());
                }
            } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) { failure[0].printStackTrace(); System.exit(1); }
        if (write) Runtime.getRuntime().halt(7); // Only this isolated fixture process, after validated commits.
        SwingUtilities.invokeAndWait(() -> frame[0].dispose()); System.exit(0);
    }
}
