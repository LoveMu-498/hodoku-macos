/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

/** Read-only evidence against the original strict pre-close hash, before changing canonicalization. */
public final class SavepointHashDiagnostic {
    public static void main(String[] args) throws Exception {
        Properties expected = new Properties(); try (InputStream input = Files.newInputStream(Paths.get(args[1]))) { expected.load(input); }
        SessionSnapshot loaded = new SessionStore(Paths.get(args[0]).toFile()).load();
        Method hash = SaveOptimizationLifecycleProbe.class.getDeclaredMethod("hash", Object.class); hash.setAccessible(true);
        System.out.println("expected=" + expected.getProperty("savepoints.sha256"));
        System.out.println("loaded=" + hash.invoke(null, loaded.getSavePoints()));
        SessionSnapshot cold = SessionSnapshots.detached(loaded); int count = 0;
        for (GuiState point : cold.getSavePoints()) {
            List<SudokuSet> groups = point.getBoxReasoningGroups();
            if (groups == null) continue;
            for (SudokuSet group : groups) {
                if (group.getMask1() != 0 || group.getMask2() != 0) throw new AssertionError("Diagnostic expects this fixture's empty box groups");
                System.out.println("savepoint=" + point.getName() + " group=" + count + " loaded_initialized=" + group.isInitialized() + " membership=0/0");
                group.setValues(null); group.setAnz(0); group.setInitialized(false); count++;
            }
        }
        String reconstructed = (String) hash.invoke(null, cold.getSavePoints());
        System.out.println("same_data_with_cold_iteration_cache=" + reconstructed);
        if (!expected.getProperty("savepoints.sha256").equals(reconstructed)) throw new AssertionError("More than the proven set-cache discrepancy changed");
        System.out.println("PASS original strict hash exactly reproduced by changing only " + count + " empty set iteration caches in memory; all remaining data unchanged");
    }
}
