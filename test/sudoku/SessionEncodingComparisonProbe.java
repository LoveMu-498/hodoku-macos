/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.File;
import java.nio.file.Files;

/** Re-encodes the same actual lightweight session in an isolated JVM; this is not a close timing. */
public final class SessionEncodingComparisonProbe {
    public static void main(String[] args) throws Exception {
        File input = new File(args[0]), output = new File(args[1]); Files.createDirectories(output.toPath().getParent());
        SessionSnapshot source = new SessionStore(input).load();
        if (!source.getGuiState().getDoodleStrokes().isEmpty() || source.getReplayFrameCount() != 1)
            throw new AssertionError("Expected actual light fixture: zero ink and one replay frame");
        long captureStart = System.nanoTime(); SessionSnapshot detached = SessionSnapshots.detached(source); long captureEnd = System.nanoTime();
        long start;
        try (SaveTrace.Operation operation = SaveTrace.operation("array_codec_comparison")) {
            start = System.nanoTime(); new SessionStore(output).save(detached);
        }
        long saved = System.nanoTime(); SessionSnapshot read = new SessionStore(output).load(); long loaded = System.nanoTime();
        if (!source.getGuiState().getSudoku().getSudoku(ClipboardMode.LIBRARY).equals(read.getGuiState().getSudoku().getSudoku(ClipboardMode.LIBRARY)))
            throw new AssertionError("Board/candidates changed");
        if (read.getReplayFrameCount() != 1 || !read.getReplayId().equals(source.getReplayId())) throw new AssertionError("Replay relation changed");
        if (!java.util.Arrays.equals(source.getGuiState().getAnzSteps(), read.getGuiState().getAnzSteps())) throw new AssertionError("Step counts changed");
        SaveTrace.flush(1000);
        System.out.printf(java.util.Locale.ROOT, "capture_ms=%.3f save_and_validate_ms=%.3f extra_load_ms=%.3f bytes=%d%n",
                (captureEnd-captureStart)/1e6, (saved-start)/1e6, (loaded-saved)/1e6, output.length());
    }
}
