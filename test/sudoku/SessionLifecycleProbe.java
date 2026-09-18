/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.SortedMap;
import javax.swing.SwingUtilities;

/** Exercises a real MainFrame save/restore and explicit-file precedence. */
public final class SessionLifecycleProbe {

	private static final String PUZZLE_A =
			"530070000600195000098000060800060003400803001700020006060000280000419005000080079";
	private static final String PUZZLE_B =
			"000260701680070090190004500820100040004602900050003028009300074040050036703018000";

	private SessionLifecycleProbe() {
	}

	public static void main(String[] args) throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame original = null;
				MainFrame explicit = null;
				MainFrame restored = null;
				try {
					original = new MainFrame(null);
					original.setPuzzle(PUZZLE_A);
					SudokuPanel panel = original.getSudokuPanel();
					panel.pushUndo();
					panel.setCell(0, 2, 4);
					panel.clearSelection(2, 6);
					Color primary = new Color(31, 101, 171);
					Color secondary = new Color(214, 87, 66);
					original.setColoring(primary, false);
					panel.getCellZoomPanel().setSecondaryColor(secondary);
					panel.handleColoring(0, 3, 6, primary);

					GuiState savePoint = new GuiState(panel, panel.getSolver(), original.getSolutionPanel());
					savePoint.get(true);
					original.getSavePoints().add(savePoint);
					invokeSave(original);

					File explicitFile = new File(ApplicationPaths.getDataDirectory(), "explicit.txt");
					Files.write(explicitFile.toPath(), PUZZLE_B.getBytes(StandardCharsets.UTF_8));
					explicit = new MainFrame(explicitFile.getPath());
					require(PUZZLE_B.equals(PuzzleHistoryEntry.normalizeClues(explicit.getSudokuPanel()
							.getSudokuString(ClipboardMode.CLUES_ONLY))),
							"explicit launch file did not override the last session");
					explicit.dispose();
					explicit = null;

					restored = new MainFrame(null);
					SudokuPanel restoredPanel = restored.getSudokuPanel();
					require(PUZZLE_A.equals(PuzzleHistoryEntry.normalizeClues(
							restoredPanel.getSudokuString(ClipboardMode.CLUES_ONLY))),
							"last session puzzle was not restored");
					require(restoredPanel.getSudoku().getValue(2) == 4, "entered value was not restored");
					require(restoredPanel.undoPossible(), "undo stack was not restored");
					require(restoredPanel.getActiveRow() == 2 && restoredPanel.getActiveCol() == 6,
							"active cell was not restored as one selection");
					require(restored.getSavePoints().size() == 1, "save points were not restored");
					GuiState restoredState = new GuiState();
					restoredPanel.getState(restoredState, true);
					SortedMap<Integer, Color> candidateColors = restoredState.getColoringCandidateMap();
					require(primary.equals(candidateColors.get(Integer.valueOf(36))),
							"candidate coloring was not restored");
					require(restoredPanel.getCellZoomPanel().isDefaultMouse(),
							"a restored session did not return to the safe default pointer");
					require(primary.equals(restoredPanel.getCellZoomPanel().getPrimaryColor())
							&& secondary.equals(restoredPanel.getCellZoomPanel().getSecondaryColor()),
							"primary and secondary colors were not restored");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (original != null) {
						original.dispose();
					}
					if (explicit != null) {
						explicit.dispose();
					}
					if (restored != null) {
						restored.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("real session lifecycle check failed", failure[0]);
		}
		System.out.println("Real GUI session lifecycle checks passed");
		System.exit(0);
	}

	private static void invokeSave(MainFrame frame) throws Exception {
		Method method = MainFrame.class.getDeclaredMethod("saveApplicationState");
		method.setAccessible(true);
		method.invoke(frame);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
