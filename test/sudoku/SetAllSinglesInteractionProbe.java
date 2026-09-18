/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.lang.reflect.Field;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import solver.SudokuSolver;

/** Guards F11 against retrying the same non-executable Single forever on the EDT. */
public final class SetAllSinglesInteractionProbe {
	private static final String SINGLES_PUZZLE =
			"530070000600195000098000060800060003400803001700020006060000280000419005000080079";
	private static final String SINGLES_SOLUTION =
			"534678912672195348198342567859761423426853791713924856961537284287419635345286179";
	private static final String REPLACEMENT_PUZZLE =
			"534678921671295348298341567859762413416853792723914856962537184187429635345186279";

	private SetAllSinglesInteractionProbe() {
	}

	public static void main(String[] args) throws Exception {
		final Throwable[] failure = new Throwable[1];
		final MainFrame[] frameHolder = new MainFrame[1];
		final SudokuPanel[] panelHolder = new SudokuPanel[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					frameHolder[0] = frame;
					panelHolder[0] = panel;
					SudokuSolver realSolver = panel.getSolver();
					panel.setSudoku(SINGLES_PUZZLE);
					Sudoku2 boardIdentity = panel.getSudoku();
					NoProgressSolver noProgressSolver = new NoProgressSolver();
					writeField(panel, "solver", noProgressSolver);
					String before = panel.getSudokuString(ClipboardMode.PM_GRID);
					long started = System.nanoTime();
					pressF11(frame);
					long elapsed = Math.round((System.nanoTime() - started) / 1_000_000.0);
					System.out.println("F11 non-executable Set All Singles returned after " + elapsed + " ms");
					if (noProgressSolver.getHintCalls() != 1) {
						throw new AssertionError("F11 retried a non-executable Single "
								+ noProgressSolver.getHintCalls() + " times");
					}
					if (!before.equals(panel.getSudokuString(ClipboardMode.PM_GRID))) {
						throw new AssertionError("a non-executable Single changed the Sudoku");
					}
					if (panel.getSudoku() != boardIdentity) {
						throw new AssertionError("a failed Single replaced the shared Sudoku object");
					}
					javax.swing.JLabel statusLabel = (javax.swing.JLabel) readField(
							frame, MainFrame.class, "statusLabelCellCandidate");
					String expectedStatus = java.util.ResourceBundle.getBundle("intl/MainFrame")
							.getString("MainFrame.singles.noProgress");
					if (!expectedStatus.equals(statusLabel.getText())) {
						throw new AssertionError("F11 did not announce its safe no-progress stop");
					}

					panel.setSudoku(SINGLES_PUZZLE);
					boardIdentity = panel.getSudoku();
					OneProgressThenStopSolver partialSolver = new OneProgressThenStopSolver();
					writeField(panel, "solver", partialSolver);
					int partialBefore = panel.getSolvedCellsAnz();
					pressF11(frame);
					if (partialSolver.getHintCalls() != 2
							|| panel.getSolvedCellsAnz() != partialBefore + 1) {
						throw new AssertionError("F11 did not stop after one successful then one failed Single");
					}
					if (panel.getSudoku() != boardIdentity) {
						throw new AssertionError("partial Set All Singles detached the shared Sudoku object");
					}

					writeField(panel, "solver", realSolver);
					Options.getInstance().setShowSudokuSolved(false);
					panel.setSudoku(SINGLES_PUZZLE);
					panel.getSudoku().setStatus(SudokuStatus.INVALID);
					int solvedBefore = panel.getSolvedCellsAnz();
					PanelRepaintManager repaints = new PanelRepaintManager(panel);
					RepaintManager previousRepaints = RepaintManager.currentManager(panel);
					RepaintManager.setCurrentManager(repaints);
					started = System.nanoTime();
					try {
						pressF11(frame);
					} finally {
						RepaintManager.setCurrentManager(previousRepaints);
					}
					elapsed = Math.round((System.nanoTime() - started) / 1_000_000.0);
					int filled = panel.getSolvedCellsAnz() - solvedBefore;
					System.out.println("F11 Set All Singles filled " + filled + " cells in " + elapsed
							+ " ms with " + repaints.dirtyRegions + " board repaints");
					SolutionStep remainingSingle = realSolver.getHint(panel.getSudoku(), true);
					if (remainingSingle != null) {
						throw new AssertionError("F11 left an executable Single on the board: "
								+ remainingSingle);
					}
					if (!SINGLES_SOLUTION.equals(panel.getSudokuString(ClipboardMode.VALUES_ONLY))) {
						throw new AssertionError("F11 did not execute the complete Set All Singles cascade");
					}
					if (repaints.dirtyRegions > 6) {
						throw new AssertionError("Set All Singles repainted the board per step: "
								+ repaints.dirtyRegions);
					}
				} catch (Throwable ex) {
					failure[0] = ex;
				}
			}
		});
		if (failure[0] == null) {
			try {
				awaitBackgroundStatus(panelHolder[0]);
				verifyStaleResultIsDiscarded(frameHolder[0], panelHolder[0]);
			} catch (Throwable ex) {
				failure[0] = ex;
			}
		}
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				if (frameHolder[0] != null) frameHolder[0].dispose();
			}
		});
		if (failure[0] != null) {
			failure[0].printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}

	private static void awaitBackgroundStatus(final SudokuPanel panel) throws Exception {
		long deadline = System.nanoTime() + 30_000_000_000L;
		while (System.nanoTime() < deadline) {
			final boolean[] published = new boolean[1];
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					published[0] = panel.getSudoku().getStatus() == SudokuStatus.VALID
							&& panel.getSudoku().isSolutionSet();
				}
			});
			if (published[0]) return;
			Thread.sleep(10L);
		}
		throw new AssertionError("background Set All Singles status was not published");
	}

	private static void verifyStaleResultIsDiscarded(final MainFrame frame,
			final SudokuPanel panel) throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				try {
					Sudoku2 oldResult = panel.getSudoku().clone();
					String oldSignature = TechniqueStepCatalog.createSignature(oldResult);
					frame.setCurrentLevel(Options.getInstance().getDifficultyLevel(1));
					frame.setCurrentScore(42);
					frame.publishProgressCheck(oldResult, oldSignature, false, false);
					if (frame.getCurrentLevel() != null || frame.getCurrentScore() != 0) {
						throw new AssertionError("an unavailable progress result left stale progress data");
					}
					panel.setSudoku(REPLACEMENT_PUZZLE);
					SudokuStatus newStatus = panel.getSudoku().getStatus();
					oldResult.setStatus(SudokuStatus.INVALID);
					frame.publishProgressCheck(oldResult, oldSignature, true, true);
					if (panel.getSudoku().getStatus() != newStatus) {
						throw new AssertionError("a stale background result overwrote a replacement puzzle");
					}
				} catch (Throwable ex) {
					failure[0] = ex;
				}
			}
		});
		if (failure[0] != null) throw new AssertionError(failure[0]);
	}

	private static void writeField(Object target, String name, Object value) throws Exception {
		Field field = SudokuPanel.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Object readField(Object target, Class<?> owner, String name) throws Exception {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void pressF11(MainFrame frame) {
		frame.setVisible(true);
		SudokuPanel panel = frame.getSudokuPanel();
		panel.requestFocusInWindow();
		KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED,
				System.currentTimeMillis(), 0, KeyEvent.VK_F11, KeyEvent.CHAR_UNDEFINED);
		KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(panel, event);
		if (!event.isConsumed()) {
			throw new AssertionError("F11 did not activate Set All Singles");
		}
	}

	private static final class NoProgressSolver extends SudokuSolver {
		private final SolutionStep single = new SolutionStep(SolutionType.HIDDEN_SINGLE);
		private int hintCalls;

		@Override
		public SolutionStep getHint(Sudoku2 board, boolean singlesOnly) {
			if (++hintCalls > 2) {
				throw new AssertionError("F11 kept requesting the same non-executable Single");
			}
			return single;
		}

		@Override
		public void doStep(Sudoku2 board, SolutionStep step) {
		}

		int getHintCalls() {
			return hintCalls;
		}
	}

	private static final class OneProgressThenStopSolver extends SudokuSolver {
		private final SolutionStep single = new SolutionStep(SolutionType.HIDDEN_SINGLE);
		private int hintCalls;
		private int stepCalls;

		@Override
		public SolutionStep getHint(Sudoku2 board, boolean singlesOnly) {
			if (++hintCalls > 2) {
				throw new AssertionError("F11 continued after a partial batch stopped making progress");
			}
			return single;
		}

		@Override
		public void doStep(Sudoku2 board, SolutionStep step) {
			if (stepCalls++ == 0) board.setCell(2, 4);
		}

		int getHintCalls() {
			return hintCalls;
		}
	}

	private static final class PanelRepaintManager extends RepaintManager {
		private final JComponent panel;
		private int dirtyRegions;

		private PanelRepaintManager(JComponent panel) {
			this.panel = panel;
		}

		@Override
		public synchronized void addDirtyRegion(JComponent component,
				int x, int y, int width, int height) {
			if (component == panel) dirtyRegions++;
		}
	}
}
