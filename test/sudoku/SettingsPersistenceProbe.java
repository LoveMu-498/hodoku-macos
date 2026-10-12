/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import generator.GeneratorPattern;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/** Exact snapshots, unchanged skip, failure retry, stale writer and bounded confirmation checks. */
public final class SettingsPersistenceProbe {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("hodoku-settings-snapshot-");
        System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, root.resolve("data").toString());
        System.setProperty("hodoku.save.trace", "false");
        Options options = new Options();
        Options.instance = options;
        options.setLanguage("de");
        options.getNormalPuzzles()[0][0] = "cached-puzzle";
        options.getHistoryOfCreatedPuzzles().add("history-before");
        ArrayList<GeneratorPattern> patterns = new ArrayList<GeneratorPattern>();
        patterns.add(new GeneratorPattern("pattern-before"));
        options.setGeneratorPatterns(patterns);
        options.getAnnotationPalettePreferences().getDoodle().setPrimaryCustom(new Color(1, 2, 3, 4));
        int originalScore = options.getOrgSolverSteps()[0].getBaseScore();
        SettingsPersistence.Snapshot first = SettingsPersistence.capture(options);

        // Mutations through nested containers must not change the submitted graph.
        options.setLanguage("zh");
        options.getNormalPuzzles()[0][0] = "cache-after";
        options.getHistoryOfCreatedPuzzles().set(0, "history-after");
        patterns.get(0).getPattern()[17] = true;
        options.getAnnotationPalettePreferences().getDoodle().setPrimaryCustom(Color.RED);
        options.getOrgSolverSteps()[0].setBaseScore(originalScore + 7);
        first.saveDefault();
        Options.readOptions();
        Options read = Options.getInstance();
        require("de".equals(read.getLanguage()), "scalar snapshot changed after capture");
        require("cached-puzzle".equals(read.getNormalPuzzles()[0][0]), "generator cache was not detached");
        require("history-before".equals(read.getHistoryOfCreatedPuzzles().get(0)), "history was not detached");
        require(!read.getGeneratorPatterns().get(0).getPattern()[17], "pattern points were not detached");
        require(new Color(1, 2, 3, 4).equals(read.getAnnotationPalettePreferences().getDoodle().getPrimaryCustom()),
                "palette nested state was not detached");
        require(read.getOrgSolverSteps()[0].getBaseScore() == originalScore, "solver setting was not detached");

        Options.instance = options;
        SettingsPersistence.Snapshot second = SettingsPersistence.capture(options);
        second.saveDefault();
        long writes = SettingsPersistence.successfulWritesForTest();
        Path configuration = ApplicationPaths.getConfigurationFile().toPath();
        FileTime modified = Files.getLastModifiedTime(configuration);
        SettingsPersistence.capture(options).saveDefault();
        require(SettingsPersistence.successfulWritesForTest() == writes, "unchanged settings were rewritten");
        require(modified.equals(Files.getLastModifiedTime(configuration)), "unchanged file was replaced");
        first.saveDefault();
        require(SettingsPersistence.successfulWritesForTest() == writes, "stale snapshot rewrote newer settings");

        // Same length + mtime corruption must invalidate the disk proof.
        byte[] bytes = Files.readAllBytes(configuration);
        bytes[0] = '!';
        Files.write(configuration, bytes); Files.setLastModifiedTime(configuration, modified);
        SettingsPersistence.capture(options).saveDefault();
        require(SettingsPersistence.successfulWritesForTest() == writes + 1, "corrupt unchanged file was skipped");
        Options.readOptions();
        require("zh".equals(Options.getInstance().getLanguage()), "replacement after corruption failed");

        Path invalidParent = root.resolve("blocked-parent");
        Files.write(invalidParent, new byte[] {1});
        System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, invalidParent.toString());
        Options.instance = options;
        SettingsPersistence.Snapshot retry = SettingsPersistence.capture(options);
        boolean failed = false;
        try { retry.saveDefault(); } catch (IOException expected) { failed = true; }
        require(failed, "invalid destination did not report failure");
        Files.delete(invalidParent); Files.createDirectory(invalidParent);
        retry.saveDefault();
        require(Files.isRegularFile(ApplicationPaths.getConfigurationFile().toPath()), "failed snapshot was not retryable");

        Method report = SettingsPersistence.class.getDeclaredMethod("reportBackgroundFailure",
                Throwable.class, SettingsPersistence.Snapshot.class);
        report.setAccessible(true);
        SettingsPersistence.Snapshot saved = SettingsPersistence.capture(options); saved.saveDefault();
        report.invoke(null, new IOException("late older failure"), retry);
        require(SettingsPersistence.getBackgroundFailure() == null, "late older failure hid newer successful settings");
        options.setInitialHeight(999);
        SettingsPersistence.Snapshot failedNewer = SettingsPersistence.capture(options);
        report.invoke(null, new IOException("newer failed settings"), failedNewer);
        saved.saveDefault();
        require(SettingsPersistence.getBackgroundFailure() != null, "older success cleared newer unsaved settings");
        failedNewer.saveDefault();
        require(SettingsPersistence.getBackgroundFailure() == null, "new settings retry did not clear own failure");

        long beforeIdle = SettingsPersistence.successfulWritesForTest();
        CountDownLatch captured = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            SettingsPersistence.start();
            for (int i = 0; i < 30; i++) {
                options.setInitialWidth(900 + i);
                SettingsPersistence.requestSave();
            }
            captured.countDown();
        });
        require(captured.await(1, TimeUnit.SECONDS), "EDT scheduling failed");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (SettingsPersistence.successfulWritesForTest() == beforeIdle && System.nanoTime() < deadline)
            Thread.sleep(20);
        SwingUtilities.invokeAndWait(SettingsPersistence::suspend);
        require(SettingsPersistence.successfulWritesForTest() == beforeIdle + 1, "burst was not coalesced to one write");
        Options.readOptions();
        require(Options.getInstance().getInitialWidth() == 929, "latest accepted layout was not saved");

        Path blockedIdle = root.resolve("blocked-idle");
        Files.write(blockedIdle, new byte[] {1});
        System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, blockedIdle.toString());
        Options.instance = options;
        CountDownLatch failureReported = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            SettingsPersistence.setBackgroundFailureListener(failure -> {
                require(SwingUtilities.isEventDispatchThread(), "background failure feedback escaped EDT");
                failureReported.countDown();
            });
            SettingsPersistence.start();
            SettingsPersistence.requestSave();
        });
        require(failureReported.await(10, TimeUnit.SECONDS), "background failure was hidden");
        SwingUtilities.invokeAndWait(SettingsPersistence::suspend);
        require(SettingsPersistence.getBackgroundFailure() != null, "failed background save lost its dirty status");
        Files.delete(blockedIdle); Files.createDirectory(blockedIdle);
        SettingsPersistence.capture(options).saveDefault();
        require(SettingsPersistence.getBackgroundFailure() == null, "successful retry did not clear failure status");
        SwingUtilities.invokeAndWait(() -> {}); // Drain final status delivery after stopping the idle timer.
        System.out.println("Settings persistence checks passed: detached graph, skip, corruption, retry, ordering, coalescing, failure feedback");
        // This probe tests the persistence coordinator, not AppKit auto-shutdown.
        // Real application termination is verified separately by the external observer.
        System.exit(0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
