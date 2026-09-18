/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.lang.reflect.Field;
import java.util.SortedMap;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

/** Focused regression checks for the three coloring mouse modes. */
public final class ColoringInteractionProbe {

	private ColoringInteractionProbe() {
	}

	public static void main(String[] args) throws Exception {
		try {
			verifyModeSelection();
			verifyOneShotCandidateColoring();
			verifyModeButtonsAndKeyboardCycle();
			verifyKeyboardModeCursor();
			verifyMacCandidateFilterShortcuts();
			verifyFunctionCandidateFilterWithoutSelection();
			verifyPlainClickDoesNotUseStaleCommandState();
			verifyRepeatedPlainClickDeselects();
			verifyToolbarFilterIndependence();
			verifyPaletteResetClearsOnly();
			verifyEmptySelectionKeyboardGuard();
			verifyColorClearUndo();
			System.out.println("Coloring interaction checks passed");
			System.exit(0);
		} catch (Throwable ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	private static void verifyToolbarFilterIndependence() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					panel.resetShowHintCellValues();
					panel.toggleBivalueFilter();
					panel.toggleTrivalueFilter();
					JToggleButton[] buttons = readField(frame, "toggleButtons", JToggleButton[].class);

					buttons[1].doClick();
					require(panel.getShowHintCellValues()[2]
							&& panel.isBivalueFilterActive() && panel.isTrivalueFilterActive(),
							"toolbar digit selection cleared XY/XYZ filters");
					buttons[1].doClick();
					require(!panel.getShowHintCellValues()[2]
							&& panel.isBivalueFilterActive() && panel.isTrivalueFilterActive(),
							"toolbar digit cancellation cleared XY/XYZ filters");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("toolbar filter independence check failed", failure[0]);
		}
	}

	private static void verifyKeyboardModeCursor() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					frame.setVisible(true);
					SudokuPanel panel = frame.getSudokuPanel();
					CellZoomPanel zoom = panel.getCellZoomPanel();
					JRadioButton candidateButton = readField(zoom,
							"radioButtonColorCandidates", JRadioButton.class);
					JRadioButton defaultButton = readField(zoom,
							"radioButtonDefault", JRadioButton.class);
					candidateButton.doClick();
					String buttonCursorName = panel.getCursor().getName();
					require(panel.getCursor().getType() == Cursor.CUSTOM_CURSOR,
							"right-side coloring control did not install a custom color cursor");
					defaultButton.doClick();
					pressKey(panel, KeyEvent.VK_T, 0);
					require(zoom.isColoringCandidates(),
							"T did not enter candidate coloring for cursor comparison");
					require(buttonCursorName.equals(panel.getCursor().getName()),
							"T coloring mode did not install the same color cursor as the right-side control");
					require(panel.getCursor().getType() == Cursor.CUSTOM_CURSOR,
							"T coloring mode left the board on the default pointer");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("keyboard coloring cursor check failed", failure[0]);
		}
	}

	private static void verifyModeButtonsAndKeyboardCycle() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					CellZoomPanel zoom = panel.getCellZoomPanel();
					JRadioButton defaultButton = selectedRadioButton(zoom);

					frame.setColoring(zoom.getPrimaryColor(), true);
					defaultButton.doClick();
					require(zoom.isDefaultMouse() && !zoom.isColoring(),
							"clicking Default Mouse returned to candidate coloring");

					long cycleAt = System.currentTimeMillis();
					pressKeyAt(panel, KeyEvent.VK_T, 0, KeyEvent.CHAR_UNDEFINED, cycleAt);
					require(zoom.isColoringCells(),
							"T did not restore the last used Cell Coloring mode");
					pressKeyAt(panel, KeyEvent.VK_T, 0, KeyEvent.CHAR_UNDEFINED, cycleAt + 400L);
					require(zoom.isColoringCandidates(),
							"T did not toggle Cell Coloring to Candidate Coloring");
					pressKeyAt(panel, KeyEvent.VK_T, 0, KeyEvent.CHAR_UNDEFINED, cycleAt + 800L);
					require(zoom.isColoringCells(),
							"T did not toggle Candidate Coloring to Cell Coloring");

					pressKey(panel, KeyEvent.VK_T, InputEvent.SHIFT_DOWN_MASK);
					require(zoom.isColoringCandidates(),
							"Shift+T no longer preserved the existing reverse coloring cycle");

					pressKey(panel, KeyEvent.VK_R, 0);
					require(zoom.isColoringCandidates(),
							"R changed the mouse mode when there was no coloring to clear");

					zoom.setColorCandidates(true);
					panel.handleColoring(0, 0, -1, zoom.getPrimaryColor());
					pressKey(panel, KeyEvent.VK_R, 0);
					require(cellColors(panel).isEmpty() && zoom.isColoringCandidates(),
							"R did not clear coloring or changed the active mode");
					panel.handleColoring(0, 0, -1, zoom.getPrimaryColor());
					pressKey(panel, KeyEvent.VK_R, InputEvent.SHIFT_DOWN_MASK);
					require(cellColors(panel).isEmpty() && zoom.isColoringCandidates(),
							"Shift+R did not clear coloring or changed the active mode");

					panel.handleColoring(0, 0, -1, zoom.getPrimaryColor());
					pressKey(panel, KeyEvent.VK_ESCAPE, 0);
					require(zoom.isDefaultMouse() && !cellColors(panel).isEmpty(),
							"Escape did not return to Default Mouse while preserving coloring");
					panel.clearColoring();

					pressKey(panel, KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK);
					require(zoom.isDefaultMouse(), "Option+R unexpectedly cycled the mouse mode");
					pressKey(panel, KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK);
					require(zoom.isDefaultMouse(), "Control+R unexpectedly cycled the mouse mode");
					pressKey(panel, KeyEvent.VK_R, InputEvent.META_DOWN_MASK);
					require(zoom.isDefaultMouse(), "Command+R unexpectedly changed the mouse mode");
					pressKey(panel, KeyEvent.VK_T, InputEvent.ALT_DOWN_MASK);
					require(zoom.isDefaultMouse(), "Option+T unexpectedly changed the mouse mode");
					pressKey(panel, KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK);
					require(zoom.isDefaultMouse(), "Control+T unexpectedly changed the mouse mode");
					pressKey(panel, KeyEvent.VK_T, InputEvent.META_DOWN_MASK);
					require(zoom.isDefaultMouse(), "Command+T unexpectedly changed the mouse mode");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("mouse mode button and cycle check failed", failure[0]);
		}
	}

	private static void verifyMacCandidateFilterShortcuts() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					int oldValue = panel.getSudoku().getValue(panel.getActiveRow(), panel.getActiveCol());

					panel.resetShowHintCellValues();
					pressKey(panel, KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK, '\u2122');
					require(panel.getShowHintCellValues()[2],
							"Option+2 did not select candidate filter 2");
					require(panel.getSudoku().getValue(panel.getActiveRow(), panel.getActiveCol()) == oldValue,
							"Option+2 changed the active cell value");
					pressKey(panel, KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK, '\u2122');
					require(!panel.getShowHintCellValues()[2],
							"repeating Option+2 did not cancel candidate filter 2");

					pressKey(panel, KeyEvent.VK_NUMPAD3, InputEvent.ALT_DOWN_MASK, '3');
					require(panel.getShowHintCellValues()[3],
							"Option+numeric-keypad 3 did not select candidate filter 3");

					pressKey(panel, KeyEvent.VK_1, InputEvent.ALT_DOWN_MASK, '\u00a1');
					pressKey(panel, KeyEvent.VK_NUMPAD2,
							InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK,
							KeyEvent.CHAR_UNDEFINED);
					require(panel.getShowHintCellValues()[1] && panel.getShowHintCellValues()[2],
							"Command+Option+numeric-keypad 2 did not add to the filter set");
					pressKey(panel, KeyEvent.VK_2,
							InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK,
							KeyEvent.CHAR_UNDEFINED);
					require(panel.getShowHintCellValues()[1] && !panel.getShowHintCellValues()[2],
							"repeating Command+Option+2 did not remove it from the filter set");

					boolean[] beforeExtraModifier = panel.getShowHintCellValues().clone();
					pressKey(panel, KeyEvent.VK_4,
							InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, '4');
					require(java.util.Arrays.equals(beforeExtraModifier, panel.getShowHintCellValues()),
							"Option+Shift+4 unexpectedly changed candidate filters");

					panel.setShowHintCellValue(1);
					boolean[] beforePreferences = panel.getShowHintCellValues().clone();
					pressKey(panel, KeyEvent.VK_COMMA, InputEvent.META_DOWN_MASK, ',');
					require(java.util.Arrays.equals(beforePreferences, panel.getShowHintCellValues()),
							"Command+Comma also changed the candidate filter");

					panel.resetShowHintCellValues();
					String beforeCountShortcut = panel.getSudokuString(ClipboardMode.VALUES_ONLY);
					pressKey(panel, KeyEvent.VK_0, InputEvent.ALT_DOWN_MASK, '\u00ba');
					require(panel.isBivalueFilterActive() && !panel.isTrivalueFilterActive(),
							"Option+0 did not toggle the XY filter");
					pressKey(panel, KeyEvent.VK_0, InputEvent.ALT_DOWN_MASK, '\u00ba');
					require(!panel.isBivalueFilterActive(),
							"repeating Option+0 did not cancel the XY filter");
					pressKey(panel, KeyEvent.VK_NUMPAD0,
							InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, '0');
					require(panel.isTrivalueFilterActive() && !panel.isBivalueFilterActive(),
							"Option+Shift+numeric-keypad 0 did not toggle the XYZ filter");
					require(beforeCountShortcut.equals(panel.getSudokuString(ClipboardMode.VALUES_ONLY)),
							"Option+0 candidate-count shortcut changed the puzzle");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("macOS candidate-filter shortcut check failed", failure[0]);
		}
	}

	private static void verifyFunctionCandidateFilterWithoutSelection() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					int candidate = firstAvailableCandidate(panel);
					panel.clearAllCellSelection();
					require(panel.getActiveCell() == null, "test setup did not clear the active cell");
					pressKey(panel, KeyEvent.VK_F1 + candidate - 1, 0);
					require(panel.getShowHintCellValues()[candidate],
							"F-key did not select an available candidate without an active cell");
					require(panel.getActiveCell() == null,
							"F-key recreated a hidden active cell");
					pressKey(panel, KeyEvent.VK_F1 + candidate - 1, 0);
					require(!panel.hasCandidateValueFilter(),
							"repeating the F-key did not clear the candidate filter");

					panel.getSudoku().setUserCells(new short[Sudoku2.LENGTH]);
					panel.setShowCandidates(false);
					panel.setShowHintCellValue(1);
					pressKey(panel, KeyEvent.VK_F2, 0);
					require(panel.getShowHintCellValues()[1] && !panel.getShowHintCellValues()[2],
							"unavailable F-key candidate replaced the existing filter");
					panel.setShowCandidates(true);
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("function candidate filter without selection check failed", failure[0]);
		}
	}

	private static int firstAvailableCandidate(SudokuPanel panel) {
		for (int candidate = 1; candidate <= Sudoku2.UNITS; candidate++) {
			for (int index = 0; index < Sudoku2.LENGTH; index++) {
				if (panel.getSudoku().getValue(index) == 0
						&& panel.getSudoku().isCandidate(index, candidate, !panel.isShowCandidates())) {
					return candidate;
				}
			}
		}
		throw new AssertionError("test puzzle has no available candidate");
	}

	private static void verifyPlainClickDoesNotUseStaleCommandState() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					panel.setSize(900, 900);
					panel.getSudokuImage(900);
					Point first = candidatePoint(panel, 0, 0, 1);
					Point second = candidatePoint(panel, 0, 1, 1);
					Point third = candidatePoint(panel, 1, 1, 1);

					click(panel, first, MouseEvent.BUTTON1, 0);
					click(panel, second, MouseEvent.BUTTON1, InputEvent.META_DOWN_MASK);
					require(panel.getCellSelectionSize() == 2,
							"Command click did not establish a multi-cell selection");

					pressKey(panel, KeyEvent.VK_META, InputEvent.META_DOWN_MASK);
					click(panel, third, MouseEvent.BUTTON1, 0);
					require(panel.getCellSelectionSize() == 1,
							"plain click reused a stale Command state and kept multiple cells selected");
					require(panel.getActiveCell().intValue() == Sudoku2.getIndex(1, 1),
							"plain click with a stale Command state selected the wrong active cell");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("plain-click selection check failed", failure[0]);
		}
	}

	private static void verifyModeSelection() {
		CellZoomPanel zoom = new CellZoomPanel(null);

		require(zoom.isDefaultMouse(), "default mouse mode must be selected initially");
		zoom.setColorCells(true);
		require(zoom.isColoringCells(), "setColorCells(true) did not select cell coloring");
		zoom.setColorCandidates(true);
		require(zoom.isColoringCandidates(),
				"setColorCandidates(true) did not select candidate coloring");
		zoom.setColorCandidates(false);
		require(zoom.isDefaultMouse(),
				"setColorCandidates(false) did not return to default mouse mode");
		zoom.setColorCells(true);
		zoom.setDefaultMouse(true);
		require(zoom.isDefaultMouse(), "setDefaultMouse(true) did not leave cell coloring");

	}

	private static void verifyRepeatedPlainClickDeselects() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				boolean originalSingleClickMode = Options.getInstance().isSingleClickMode();
				try {
					Options.getInstance().setSingleClickMode(false);
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					panel.setSize(900, 900);
					panel.getSudokuImage(900);
					int target = Sudoku2.getIndex(0, 0);
					int other = Sudoku2.getIndex(0, 1);
					Point targetPoint = candidatePoint(panel, 0, 0, 1);
					Point otherPoint = candidatePoint(panel, 0, 1, 1);

					panel.clearSelection(target);
					click(panel, targetPoint, MouseEvent.BUTTON1, 0);
					require(panel.getCellSelectionSize() == 0 && panel.getActiveCell() == null,
							"repeated plain click did not clear the active cell selection");

					click(panel, otherPoint, MouseEvent.BUTTON1, 0);
					click(panel, targetPoint, MouseEvent.BUTTON1, 0);
					require(panel.getCellSelectionSize() == 1
								&& panel.getActiveCell().intValue() == target,
							"plain click after deselection did not select a new active cell");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					Options.getInstance().setSingleClickMode(originalSingleClickMode);
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("repeated plain-click deselection check failed", failure[0]);
		}
	}

	private static void verifyOneShotCandidateColoring() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				String originalOsName = System.getProperty("os.name");
				try {
					System.setProperty("os.name", "Mac OS X");
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					CellZoomPanel zoom = panel.getCellZoomPanel();
					Color chosenStatusColor = new Color(19, 83, 147);
					panel.setActiveColor(chosenStatusColor);
					require(chosenStatusColor.equals(zoom.getPrimaryColor()),
							"status coloring selection did not become the primary color");
					panel.setActiveColor(null);
					panel.setShowCandidates(true);
					panel.getSudokuImage(900);

					int row = 0;
					int col = 0;
					int candidate = firstDisplayedCandidate(panel, row, col);
					Point point = candidatePoint(panel, row, col, candidate);
					int key = Sudoku2.getIndex(row, col) * 10 + candidate;

					zoom.setDefaultMouse(true);
					click(panel, point, MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK);
					SortedMap<Integer, Color> colors = candidateColors(panel);
					require(zoom.isDefaultMouse(),
							"one-shot coloring left the default mouse mode");
					require(zoom.getPrimaryColor().equals(colors.get(Integer.valueOf(key))),
							"Option+left click did not apply the primary color to a displayed candidate");

					click(panel, point, MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK);
					require(!candidateColors(panel).containsKey(Integer.valueOf(key)),
							"repeating one-shot coloring with the same color did not remove it");
					click(panel, point, MouseEvent.BUTTON1,
							InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK);
					require(!candidateColors(panel).containsKey(Integer.valueOf(key)),
							"Option+Command unexpectedly triggered one-shot coloring");
					click(panel, point, MouseEvent.BUTTON1,
							InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
					require(!candidateColors(panel).containsKey(Integer.valueOf(key)),
							"Option+Shift unexpectedly triggered one-shot coloring");
					click(panel, point, MouseEvent.BUTTON1,
							InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK);
					require(!candidateColors(panel).containsKey(Integer.valueOf(key)),
							"Option+Control unexpectedly triggered one-shot coloring");
					click(panel, point, MouseEvent.BUTTON2, InputEvent.ALT_DOWN_MASK);
					require(!candidateColors(panel).containsKey(Integer.valueOf(key)),
							"Option+middle click unexpectedly triggered one-shot coloring");

					String savedOsName = System.getProperty("os.name");
					System.setProperty("os.name", "Linux");
					click(panel, point, MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK);
					require(!candidateColors(panel).containsKey(Integer.valueOf(key)),
							"Option+left click unexpectedly colored a candidate outside macOS");
					System.setProperty("os.name", savedOsName);

					zoom.setColorCells(true);
					click(panel, point, MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK);
					Color cellColor = cellColors(panel).get(Integer.valueOf(Sudoku2.getIndex(row, col)));
					require(zoom.getSecondaryColor().equals(cellColor),
							"Option+left click in sticky cell coloring did not use the secondary color");

					Point nextCell = candidatePoint(panel, row, col + 1, candidate);
					click(panel, nextCell, MouseEvent.BUTTON1, 0);
					require(zoom.getPrimaryColor().equals(
							cellColors(panel).get(Integer.valueOf(Sudoku2.getIndex(row, col + 1)))),
							"unmodified left click in sticky cell coloring did not use the primary color");

					zoom.setColorCandidates(true);
					click(panel, point, MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK);
					require(zoom.getSecondaryColor().equals(
							candidateColors(panel).get(Integer.valueOf(key))),
							"Option+left click in sticky candidate coloring did not use the secondary color");
					click(panel, point, MouseEvent.BUTTON2, 0);
					require(zoom.getSecondaryColor().equals(
							candidateColors(panel).get(Integer.valueOf(key))),
							"middle click unexpectedly changed sticky candidate coloring");

					pressKey(panel, KeyEvent.VK_ESCAPE, 0);
					require(zoom.isDefaultMouse(), "Escape did not return to the default mouse mode");

					panel.handleColoring(row, col, candidate, zoom.getPrimaryColor());
					panel.handleColoring(row, col, -1, zoom.getSecondaryColor());
					pressKey(panel, KeyEvent.VK_R, 0);
					require(candidateColors(panel).isEmpty() && cellColors(panel).isEmpty(),
							"R did not clear cell and candidate coloring");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
					if (originalOsName == null) {
						System.clearProperty("os.name");
					} else {
						System.setProperty("os.name", originalOsName);
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("one-shot candidate coloring check failed", failure[0]);
		}
	}

	private static void verifyPaletteResetClearsOnly() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					CellZoomPanel zoom = panel.getCellZoomPanel();
					UIColorPalette palette = findPalette(zoom);

					clickPaletteReset(palette);
					require(zoom.isDefaultMouse(),
							"palette clear control changed the mouse mode without coloring");

					frame.setColoring(zoom.getPrimaryColor(), false);
					panel.handleColoring(0, 0, -1, zoom.getPrimaryColor());
					panel.handleColoring(0, 0, 1, zoom.getSecondaryColor());
					require(!candidateColors(panel).isEmpty() && !cellColors(panel).isEmpty(),
							"palette reset test setup did not create coloring");
					clickPaletteReset(palette);
					require(candidateColors(panel).isEmpty() && cellColors(panel).isEmpty()
								&& zoom.isColoringCandidates(),
							"palette clear control did not clear coloring or changed the mouse mode");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("palette reset interaction check failed", failure[0]);
		}
	}

	private static void verifyEmptySelectionKeyboardGuard() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					panel.clearSelection(0, 0);
					panel.clearAllCellSelection();
					require(panel.getCellSelectionSize() == 0 && panel.getActiveCell() == null,
							"test setup did not create a true empty selection");
					String before = panel.getSudokuString(ClipboardMode.VALUES_ONLY);
					pressKey(panel, KeyEvent.VK_1, 0, '1');
					pressKey(panel, KeyEvent.VK_1, InputEvent.META_DOWN_MASK, '1');
					pressKey(panel, KeyEvent.VK_DELETE, 0);
					pressKey(panel, KeyEvent.VK_ENTER, 0);
					pressKey(panel, KeyEvent.VK_SPACE, 0);
					require(before.equals(panel.getSudokuString(ClipboardMode.VALUES_ONLY)),
							"keyboard editing changed the puzzle without an active cell");
					require(panel.getCellSelectionSize() == 0 && panel.getActiveCell() == null,
							"keyboard editing recreated a hidden selection");

					pressKey(panel, KeyEvent.VK_RIGHT, 0);
					require(panel.getActiveCell() != null
							&& panel.getActiveCell().intValue() == Sudoku2.getIndex(0, 0),
							"first navigation key did not restore the safe anchor");
					pressKey(panel, KeyEvent.VK_RIGHT, 0);
					require(panel.getActiveCell() != null
							&& panel.getActiveCell().intValue() == Sudoku2.getIndex(0, 1),
							"second navigation key did not move from the restored anchor");

					panel.resetShowHintCellValues();
					panel.toggleBivalueFilter();
					panel.toggleTrivalueFilter();
					panel.toggleCandidateValueFilter(1, false);
					require(panel.isBivalueFilterActive() && panel.isTrivalueFilterActive()
							&& panel.getShowHintCellValues()[1]
							&& panel.hasAnyViewFilter(),
							"candidate-value and candidate-count filters were not independent");

					panel.clearAllCellSelection();
					pressKey(panel, KeyEvent.VK_ESCAPE, InputEvent.SHIFT_DOWN_MASK);
					require(!panel.hasAnyViewFilter() && panel.getCellSelectionSize() == 0,
							"modified Escape did not reach the filter-and-selection reset layer");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("empty-selection keyboard guard check failed", failure[0]);
		}
	}

	private static void verifyColorClearUndo() throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					Color cellColor = new Color(22, 88, 53);
					Color candidateColor = new Color(224, 142, 58);
					panel.handleColoring(4, 4, -1, cellColor);
					panel.handleColoring(4, 4, 1, candidateColor);
					SortedMap<Integer, Color> beforeCells = cellColors(panel);
					SortedMap<Integer, Color> beforeCandidates = candidateColors(panel);
					require(!beforeCells.isEmpty() && !beforeCandidates.isEmpty(),
							"color-clear undo setup did not create both coloring maps");

					frame.setColoring(panel.getCellZoomPanel().getPrimaryColor(), true);
					String boardBefore = panel.getSudokuString(ClipboardMode.VALUES_ONLY);
					pressKey(panel, KeyEvent.VK_1, 0, '1');
					require(boardBefore.equals(panel.getSudokuString(ClipboardMode.VALUES_ONLY)),
							"Coloring mode leaked a digit into the Sudoku board");
					pressKey(panel, KeyEvent.VK_R, 0);
					require(cellColors(panel).isEmpty() && candidateColors(panel).isEmpty(),
							"R did not clear all coloring");
					pressKey(panel, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
					require(beforeCells.equals(cellColors(panel))
							&& beforeCandidates.equals(candidateColors(panel)),
							"Command+Z in Coloring mode did not restore the cleared coloring");
					pressKey(panel, KeyEvent.VK_Y, InputEvent.META_DOWN_MASK);
					require(cellColors(panel).isEmpty() && candidateColors(panel).isEmpty(),
							"Command+Y in Coloring mode did not reapply the coloring clear");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("color-clear undo check failed", failure[0]);
		}
	}

	private static void clickPaletteReset(UIColorPalette palette) {
		for (java.awt.Component component : palette.getComponents()) {
			if (component instanceof UIBorderedImagePanel
					&& component.getX() == 0 && component.getY() > 0) {
				MouseEvent event = new MouseEvent(component, MouseEvent.MOUSE_PRESSED,
						System.currentTimeMillis(), 0, 1, 1, 1, false, MouseEvent.BUTTON1);
				for (MouseListener listener : component.getMouseListeners()) {
					listener.mousePressed(event);
				}
				return;
			}
		}
		throw new AssertionError("palette reset control is missing");
	}

	private static UIColorPalette findPalette(CellZoomPanel zoom) {
		for (java.awt.Component component : zoom.getComponents()) {
			if (component instanceof UIColorPalette) {
				return (UIColorPalette) component;
			}
		}
		throw new AssertionError("color palette is missing");
	}

	private static int firstDisplayedCandidate(SudokuPanel panel, int row, int col) {
		int index = Sudoku2.getIndex(row, col);
		for (int candidate = 1; candidate <= Sudoku2.UNITS; candidate++) {
			if (panel.getSudoku().isCandidate(index, candidate, !panel.isShowCandidates())) {
				return candidate;
			}
		}
		throw new AssertionError("test cell has no displayed candidate");
	}

	private static Point candidatePoint(SudokuPanel panel, int row, int col, int candidate) {
		int cellSize = panel.getX(row, col + 1) - panel.getX(row, col);
		int gap = panel.getX(row, 3) - panel.getX(row, 2) - cellSize;
		int candidateCol = (candidate - 1) % 3;
		int candidateRow = (candidate - 1) / 3;
		int x = panel.getX(row, col) - gap + (candidateCol * 2 + 1) * cellSize / 6;
		int y = panel.getY(row, col) - gap + (candidateRow * 2 + 1) * cellSize / 6;
		Point point = new Point(x, y);
		require(panel.getCandidate(point, row, col) == candidate,
				"test point does not hit candidate " + candidate);
		return point;
	}

	private static void click(SudokuPanel panel, Point point, int button, int modifiers) {
		long now = System.currentTimeMillis();
		panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, now, modifiers,
				point.x, point.y, 1, false, button));
		panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, now + 1, modifiers,
				point.x, point.y, 1, false, button));
	}

	private static void pressKey(SudokuPanel panel, int keyCode, int modifiers) {
		pressKey(panel, keyCode, modifiers, KeyEvent.CHAR_UNDEFINED);
	}

	private static void pressKey(SudokuPanel panel, int keyCode, int modifiers, char keyChar) {
		pressKeyAt(panel, keyCode, modifiers, keyChar, System.currentTimeMillis());
	}

	private static void pressKeyAt(SudokuPanel panel, int keyCode, int modifiers,
			char keyChar, long now) {
		KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED, now,
				modifiers, keyCode, keyChar);
		for (KeyListener listener : panel.getKeyListeners()) {
			listener.keyPressed(event);
		}
		KeyEvent released = new KeyEvent(panel, KeyEvent.KEY_RELEASED, now + 1L,
				modifiers, keyCode, keyChar);
		for (KeyListener listener : panel.getKeyListeners()) {
			listener.keyReleased(released);
		}
	}

	private static JRadioButton selectedRadioButton(java.awt.Container parent) {
		for (java.awt.Component component : parent.getComponents()) {
			if (component instanceof JRadioButton && ((JRadioButton) component).isSelected()) {
				return (JRadioButton) component;
			}
			if (component instanceof java.awt.Container) {
				try {
					return selectedRadioButton((java.awt.Container) component);
				} catch (AssertionError ex) {
					// Continue looking in sibling containers.
				}
			}
		}
		throw new AssertionError("selected mouse-mode radio button is missing");
	}

	private static <T> T readField(Object target, String name, Class<T> type) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return type.cast(field.get(target));
	}

	private static SortedMap<Integer, Color> candidateColors(SudokuPanel panel) {
		GuiState state = new GuiState();
		panel.getState(state, true);
		return state.getColoringCandidateMap();
	}

	private static SortedMap<Integer, Color> cellColors(SudokuPanel panel) {
		GuiState state = new GuiState();
		panel.getState(state, true);
		return state.getColoringMap();
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
