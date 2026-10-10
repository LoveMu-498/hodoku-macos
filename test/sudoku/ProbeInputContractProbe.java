/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.nio.file.Paths;

/** Runs before AWT: rejected modes and missing/default directories cannot open user data. */
public final class ProbeInputContractProbe {
    private static void rejected(String... args) {
        try { DoodleHypothesisSessionProbe.validatedMode(args); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Unsafe probe arguments accepted");
    }
    public static void main(String[] args) {
        String old = System.getProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY);
        try {
            System.clearProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY);
            rejected(); rejected("seed"); rejected("verify"); rejected("other"); rejected("seed", "verify");
            System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, "relative/test-data"); rejected("seed");
            System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, "/"); rejected("verify");
            System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY,
                    Paths.get(System.getProperty("user.home"), "Library", "Application Support", "HoDoKu").toAbsolutePath().toString());
            rejected("seed");
            System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY,
                    Paths.get(System.getProperty("java.io.tmpdir"), "hodoku-input-contract", "data").toAbsolutePath().toString());
            if (!"seed".equals(DoodleHypothesisSessionProbe.validatedMode(new String[]{"seed"}))
                    || !"verify".equals(DoodleHypothesisSessionProbe.validatedMode(new String[]{"verify"})))
                throw new AssertionError("Valid isolated modes changed");
            rejected("seed", "extra"); rejected("anything");
        } finally {
            if (old == null) System.clearProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY);
            else System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, old);
        }
        System.out.println("PASS ProbeInputContractProbe: exact seed/verify modes and explicit isolated data checked before AWT");
    }
}
