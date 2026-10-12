/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

/** Retained entry point so an old command cannot inject obsolete desktop gestures. */
@Deprecated
public final class AnnotationDeletionMouseProbe {
    public static void main(String[] args) {
        System.err.println("RETIRED AnnotationDeletionMouseProbe: obsolete Control/right-button eraser gestures. "
                + "Use DoodleStrokeErasureNativeProbe and ContinuousEraserWheelProbe through the isolated probe runner. "
                + "No window was created and no desktop input was sent.");
        System.exit(2);
    }
}
