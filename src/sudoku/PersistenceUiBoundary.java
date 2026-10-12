/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Dialog;
import java.awt.Window;

/** EDT-only checks: modal loops can run timers while a worker mutates solver data. */
final class PersistenceUiBoundary {
    private PersistenceUiBoundary() {}
    static boolean canCaptureInBackground() {
        for (Window window : Window.getWindows())
            if (window instanceof Dialog && ((Dialog)window).isModal() && window.isVisible()) return false;
        return true;
    }
    static Dialog computationDialog() {
        for (Window window : Window.getWindows()) {
            if (window.isVisible() && (window instanceof SolverProgressDialog
                    || window instanceof FindAllStepsProgressDialog
                    || window instanceof GenerateSudokuProgressDialog
                    || window instanceof ExtendedPrintProgressDialog)) return (Dialog)window;
        }
        return null;
    }
}
