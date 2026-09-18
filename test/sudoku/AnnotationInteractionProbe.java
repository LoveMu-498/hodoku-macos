/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;

/** Focused checks for puzzle-local annotation pointer behavior. */
public final class AnnotationInteractionProbe {

	private AnnotationInteractionProbe() {
	}

	public static void main(String[] args) throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					panel.setSize(600, 600);
					makeUndoableBoardEdit(panel);
					String boardAfterPuzzleEdit = panel.getSudokuString(ClipboardMode.PM_GRID);
					verifyBoxShortcut(panel, boardAfterPuzzleEdit);
					verifyUnifiedToolEdgeCases(panel);
					verifyBoxSelection(frame, panel, boardAfterPuzzleEdit);
					verifyToolLocalPalettes(panel);
					verifyPaletteAccessibility(frame, panel);

					press(panel, KeyEvent.VK_P, 0);
					require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
							"P did not enter Doodle mode");
					verifyToolbarToolButtons(frame, panel);
					CellZoomPanel zoom = panel.getCellZoomPanel();
					showCellZoomPanel(frame, zoom);
					zoom.setPrimaryColor(Color.BLACK);
					panel.setDoodlesVisible(false);
					panel.setDoodlesVisible(true);
					javax.swing.JComboBox<?> widthCombo = (javax.swing.JComboBox<?>) readField(zoom,
							"doodleWidthCombo");
					require(javax.swing.SwingUtilities.isDescendingFrom(widthCombo, frame),
							"Doodle width control was not attached to the main window");
					dispatchFocusedKey(frame, widthCombo, KeyEvent.VK_M, 0);
					require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
							"M did not leave Doodle mode when a Doodle control owned focus");
					dispatchFocusedKey(frame, widthCombo, KeyEvent.VK_P, 0);
					require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
							"P did not restore Doodle mode when a Doodle control owned focus");
					javax.swing.JPopupMenu nativePopup = new javax.swing.JPopupMenu();
					javax.swing.JMenuItem nativePopupItem = new javax.swing.JMenuItem("Width");
					nativePopup.add(nativePopupItem);
					javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(
							new javax.swing.MenuElement[] { nativePopup, nativePopupItem });
					try {
						require(!dispatchFocusedPress(frame, widthCombo, KeyEvent.VK_ESCAPE, 0)
									&& panel.getAnnotationTool() == AnnotationTool.DOODLE,
								"global Escape did not defer to an open native popup");
					} finally {
						javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
					}
					panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, 20, 30));
					panel.dispatchEvent(drag(panel, 45, 55));
					require(readField(panel, "activeDoodleStroke") != null,
							"focused-Escape setup did not create a transient Doodle stroke");
					dispatchFocusedKey(frame, widthCombo, KeyEvent.VK_ESCAPE,
							InputEvent.SHIFT_DOWN_MASK);
					require(readField(panel, "activeDoodleStroke") == null
							&& panel.getAnnotationTool() == AnnotationTool.DOODLE,
							"focused Escape did not cancel only the transient Doodle layer");
					panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, 55, 65));
					dispatchFocusedKey(frame, widthCombo, KeyEvent.VK_ESCAPE,
							InputEvent.ALT_DOWN_MASK);
					require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
							"second focused Escape did not exit Doodle mode");
					panel.toggleBivalueFilter();
					panel.setActiveCell(Sudoku2.getIndex(0, 0));
					panel.setStep(new SolutionStep(SolutionType.FULL_HOUSE));
					require(panel.hasAnyViewFilter() && panel.getCellSelectionSize() > 0,
							"focused-Escape reset setup did not create filter and selection state");
					dispatchFocusedKey(frame, widthCombo, KeyEvent.VK_ESCAPE,
							InputEvent.META_DOWN_MASK);
					require(panel.getStep() == null && panel.hasAnyViewFilter()
							&& panel.getCellSelectionSize() > 0,
							"focused Escape did not dismiss only the displayed solving step layer");
					dispatchFocusedKey(frame, widthCombo, KeyEvent.VK_ESCAPE,
							InputEvent.CTRL_DOWN_MASK);
					require(!panel.hasAnyViewFilter() && panel.getCellSelectionSize() == 0,
							"focused Escape did not clear Default-mouse filter and selection state");
					dispatchFocusedKey(frame, widthCombo, KeyEvent.VK_P, 0);
					require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
							"P did not restore Doodle mode after the focused modified-Escape check");
					javax.swing.JTextField editable = new javax.swing.JTextField();
					frame.getLayeredPane().add(editable);
					dispatchEditableKey(frame, editable, KeyEvent.VK_M);
					require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
							"an editable text component lost an unmodified tool letter");
					frame.getLayeredPane().remove(editable);
					int widthBefore = readInt(panel, "doodleWidthIndex");
					for (int i = 0; i < 4; i++) {
						press(panel, KeyEvent.VK_A + i, 0);
						require(Options.getInstance().getColoringColors()[i * 2].equals(zoom.getPrimaryColor()),
								"A-D did not select the matching shared palette color in Doodle mode");
					}
					require(!panel.hasColoring(),
							"Doodle A shortcut leaked into cell/candidate coloring after hide/show");
					press(panel, KeyEvent.VK_E, 0);
					require(!panel.hasColoring(),
							"legacy E coloring shortcut leaked into Doodle mode");
					pressChar(panel, KeyEvent.VK_PERIOD, InputEvent.SHIFT_DOWN_MASK, '>');
					require(readInt(panel, "doodleWidthIndex") == Math.min(3, widthBefore + 1),
							"> did not increase Doodle width");
					String boardBeforeAnnotations = panel.getSudokuString(ClipboardMode.PM_GRID);
					press(panel, KeyEvent.VK_1, 0);
					press(panel, KeyEvent.VK_2, InputEvent.META_DOWN_MASK);
					press(panel, KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK);
					press(panel, KeyEvent.VK_SPACE, 0);
					press(panel, KeyEvent.VK_DELETE, 0);
					require(boardBeforeAnnotations.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
							"Doodle mode leaked a board-edit shortcut into the Sudoku board");

					panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, 20, 30));
					panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, 20, 30));
					require(panel.getDoodleStrokeCount() == 0,
							"a click without movement created a doodle stroke");

					panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, 20, 30));
					panel.dispatchEvent(drag(panel, 80, 90));
					panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, 120, 120));
					require(panel.getDoodleStrokeCount() == 1,
							"a drag in the left Sudoku region did not create a doodle stroke");
					require(doodleIsVisible(panel, zoom.getPrimaryColor()),
							"the completed doodle was not painted with the shared palette color");

					press(panel, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
					require(panel.getDoodleStrokeCount() == 0,
							"Command+Z in Doodle mode did not undo the last doodle stroke");
					invokeFrameAction(frame, "undoMenuItemActionPerformed");
					require(boardAfterPuzzleEdit.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
							"Command+Z in Doodle mode also undid Sudoku content");
					press(panel, KeyEvent.VK_Y, InputEvent.META_DOWN_MASK);
					require(panel.getDoodleStrokeCount() == 1,
							"Command+Y in Doodle mode did not redo the last doodle stroke");
					invokeFrameAction(frame, "redoMenuItemActionPerformed");
					require(boardAfterPuzzleEdit.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
							"Command+Y in Doodle mode also redid Sudoku content");
					dispatchFocusedKey(frame, widthCombo, KeyEvent.VK_R, 0);
					require(panel.getDoodleStrokeCount() == 0,
							"R did not clear annotations when a Doodle control owned focus");

					verifyFreeChainV2(frame, panel);
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
			failure[0].printStackTrace();
			System.exit(1);
		}
		System.out.println("Annotation interaction checks passed");
		System.exit(0);
	}

	private static void verifyBoxShortcut(SudokuPanel panel, String boardBefore) {
		keyDown(panel, KeyEvent.VK_S, 0);
		keyUp(panel, KeyEvent.VK_S, 0);
		require(panel.getAnnotationTool() == AnnotationTool.BOX_SELECTION,
				"S tap did not enter Box selection mode");
		require(boardBefore.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
				"Box tool shortcut changed the Sudoku board");
		verifySettledToolToggles(panel);
		keyDown(panel, KeyEvent.VK_M, 0);
		keyUp(panel, KeyEvent.VK_M, 0);
		press(panel, KeyEvent.VK_P, 0);
		long holdStartedAt = System.currentTimeMillis();
		keyDownAt(panel, KeyEvent.VK_S, 0, holdStartedAt);
		require(panel.getAnnotationTool() == AnnotationTool.BOX_SELECTION,
				"held S did not immediately expose Box selection");
		keyUpAt(panel, KeyEvent.VK_S, 0, holdStartedAt + 300L);
		require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
				"releasing held S did not restore the preceding tool");
		long doubleStartedAt = holdStartedAt + 1000L;
		keyDownAt(panel, KeyEvent.VK_M, 0, doubleStartedAt);
		keyUpAt(panel, KeyEvent.VK_M, 0, doubleStartedAt + 20L);
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
				"first M tap did not make Default mouse sticky");
		keyDownAt(panel, KeyEvent.VK_M, 0, doubleStartedAt + 120L);
		keyUpAt(panel, KeyEvent.VK_M, 0, doubleStartedAt + 140L);
		require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
				"double-tapping M did not restore the pre-sequence tool");
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
		long actionStartedAt = doubleStartedAt + 1000L;
		keyDownAt(panel, KeyEvent.VK_P, 0, actionStartedAt);
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, 30, 30));
		keyUpAt(panel, KeyEvent.VK_P, 0, actionStartedAt + 100L);
		require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
				"temporary tool restored before its captured pointer gesture ended");
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, 30, 30));
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE
				&& panel.getDoodleStrokeCount() == 0,
				"board use before 250 ms did not make P temporary through pointer release");

		long clearStartedAt = actionStartedAt + 1000L;
		keyDownAt(panel, KeyEvent.VK_P, 0, clearStartedAt);
		keyUpAt(panel, KeyEvent.VK_P, 0, clearStartedAt + 20L);
		keyDownAt(panel, KeyEvent.VK_P, 0, clearStartedAt + 120L);
		keyUpAt(panel, KeyEvent.VK_P, 0, clearStartedAt + 140L);
		require(panel.getDoodleStrokeCount() == 0
				&& panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
				"double-tapping P did not clear Doodles and restore the pre-sequence tool");

		keyDownAt(panel, KeyEvent.VK_P, InputEvent.SHIFT_DOWN_MASK, clearStartedAt + 1000L);
		keyUpAt(panel, KeyEvent.VK_P, InputEvent.SHIFT_DOWN_MASK, clearStartedAt + 1020L);
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
				"modified P entered the pointer-tool gesture state machine");
	}

	private static void verifySettledToolToggles(SudokuPanel panel) {
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
		long startedAt = System.currentTimeMillis() + 1000L;

		tapAt(panel, KeyEvent.VK_P, startedAt);
		require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
				"first settled P tap did not enter Doodle mode");
		tapAt(panel, KeyEvent.VK_L, startedAt + 400L);
		require(panel.getAnnotationTool() == AnnotationTool.FREE_CHAIN,
				"first settled L tap did not enter Free Chain mode");
		tapAt(panel, KeyEvent.VK_L, startedAt + 800L);
		require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
				"settled second L tap did not restore the preceding Doodle mode");
		tapAt(panel, KeyEvent.VK_P, startedAt + 1200L);
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
				"settled second P tap did not restore the preceding Default mouse mode");

		tapAt(panel, KeyEvent.VK_S, startedAt + 2000L);
		require(panel.getAnnotationTool() == AnnotationTool.BOX_SELECTION,
				"first settled S tap did not enter Box selection mode");
		tapAt(panel, KeyEvent.VK_S, startedAt + 2400L);
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
				"settled second S tap did not restore the preceding Default mouse mode");
	}

	private static void verifyUnifiedToolEdgeCases(SudokuPanel panel) throws Exception {
		BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
		panel.paint(image.getGraphics());
		int cellSize = readInt(panel, "cellSize");
		int[][] candidates = firstCandidateLocations(panel, 1);
		int x = candidateX(panel, candidates[0][0], candidates[0][1], candidates[0][2], cellSize);
		int y = candidateY(panel, candidates[0][0], candidates[0][1], candidates[0][2], cellSize);
		verifyModifiedEscapeLayers(panel, x, y);
		verifySettledToggleAfterBoardUse(panel, x, y);

		panel.setAnnotationTool(AnnotationTool.DOODLE);
		long temporaryMouseAt = System.currentTimeMillis();
		keyDownAt(panel, KeyEvent.VK_M, 0, temporaryMouseAt);
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
		keyUpAt(panel, KeyEvent.VK_M, 0, temporaryMouseAt + 100L);
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
				"temporary M restored before its Default pointer gesture ended");
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x, y));
		require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
				"temporary M did not restore the preceding tool after pointer release");

		long pinAt = temporaryMouseAt + 1000L;
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
		keyDownAt(panel, KeyEvent.VK_P, 0, pinAt);
		panel.setAnnotationTool(AnnotationTool.DOODLE);
		keyUpAt(panel, KeyEvent.VK_P, 0, pinAt + 400L);
		require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
				"clicking the current toolbar tool was overturned by a late shortcut release");

		KeyEvent shiftedT = new KeyEvent(panel, KeyEvent.KEY_PRESSED, pinAt + 500L,
				InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_T, 'T');
		require(!panel.handleAnnotationKeyPressed(shiftedT),
				"Shift+T was consumed by the plain pointer-tool shortcut state machine");

		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
		String boardBeforeTemporaryChain = panel.getSudokuString(ClipboardMode.PM_GRID);
		long temporaryChainAt = pinAt + 700L;
		keyDownAt(panel, KeyEvent.VK_L, 0, temporaryChainAt);
		panel.dispatchEvent(mouseButton(panel, MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON3));
		keyUpAt(panel, KeyEvent.VK_L, 0, temporaryChainAt + 100L);
		require(panel.getAnnotationTool() == AnnotationTool.FREE_CHAIN,
				"temporary L restored before its right-button gesture ended");
		panel.dispatchEvent(mouseButton(panel, MouseEvent.MOUSE_RELEASED, x, y, MouseEvent.BUTTON3));
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE
				&& boardBeforeTemporaryChain.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
				"temporary Free-chain right click leaked into Default Sudoku editing");

		boolean singleClickBefore = Options.getInstance().isSingleClickMode();
		try {
			Options.getInstance().setSingleClickMode(true);
			panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
			String boardBeforeLateRelease = panel.getSudokuString(ClipboardMode.PM_GRID);
			long temporaryDoodleAt = pinAt + 1000L;
			keyDownAt(panel, KeyEvent.VK_P, 0, temporaryDoodleAt);
			panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
			keyUpAt(panel, KeyEvent.VK_P, 0, temporaryDoodleAt + 100L);
			panel.cancelAnnotationToolInteractionOnDeactivation();
			require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
					"deactivation did not restore the preceding temporary tool");
			panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x, y));
			require(boardBeforeLateRelease.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
					"a canceled annotation gesture leaked its late release into Sudoku editing");

			panel.setAnnotationTool(AnnotationTool.DOODLE);
			String boardBeforePuzzleResetRelease = panel.getSudokuString(ClipboardMode.PM_GRID);
			panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
			panel.discardAnnotations();
			panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x, y));
			require(boardBeforePuzzleResetRelease.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
					"a pre-reset annotation release leaked into the replacement puzzle");

			GuiState restoredState = new GuiState(panel, null, null);
			restoredState.setIncludeAnnotations(true);
			restoredState.get(true);
			panel.setAnnotationTool(AnnotationTool.DOODLE);
			String boardBeforeStateRelease = panel.getSudokuString(ClipboardMode.PM_GRID);
			panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
			restoredState.set();
			panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x, y));
			require(boardBeforeStateRelease.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
					"a pre-restore annotation release leaked into the restored session");
		} finally {
			Options.getInstance().setSingleClickMode(singleClickBefore);
		}

		panel.setNextUserChainStrong(false);
		panel.discardAnnotations();
		require((Boolean) readField(panel, "nextUserChainStrong"),
				"loading a new puzzle did not reset the first Free-chain relation to Strong");
	}

	private static void verifyModifiedEscapeLayers(SudokuPanel panel, int x, int y) throws Exception {
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
		String boardBeforeTemporaryChain = panel.getSudokuString(ClipboardMode.PM_GRID);
		long temporaryChainAt = System.currentTimeMillis();
		keyDownAt(panel, KeyEvent.VK_L, 0, temporaryChainAt);
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
		keyUpAt(panel, KeyEvent.VK_L, 0, temporaryChainAt + 100L);
		require(panel.getAnnotationTool() == AnnotationTool.FREE_CHAIN
				&& readField(panel, "activeUserChain") != null,
				"temporary Free-chain setup did not defer its tool restore");
		press(panel, KeyEvent.VK_ESCAPE, InputEvent.SHIFT_DOWN_MASK);
		require(readField(panel, "activeUserChain") == null
				&& panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
				"Escape did not finish the pending restore after canceling a temporary chain draft");
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x, y));
		require(boardBeforeTemporaryChain.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
				"the late release from an Escape-canceled temporary chain edited the Sudoku");

		panel.setAnnotationTool(AnnotationTool.FREE_CHAIN);
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x, y));
		require(readField(panel, "activeUserChain") != null,
				"modified-Escape setup did not create an active Free-chain draft");
		press(panel, KeyEvent.VK_ESCAPE, InputEvent.SHIFT_DOWN_MASK);
		require(readField(panel, "activeUserChain") == null
				&& panel.getAnnotationTool() == AnnotationTool.FREE_CHAIN,
				"Shift+Escape did not cancel only the active chain draft");
		press(panel, KeyEvent.VK_ESCAPE, InputEvent.META_DOWN_MASK);
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
				"Command+Escape did not exit the settled Free-chain tool");

		panel.setAnnotationTool(AnnotationTool.DOODLE);
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
		panel.dispatchEvent(drag(panel, x + 12, y + 12));
		require(readField(panel, "activeDoodleStroke") != null,
				"modified-Escape setup did not create an active Doodle stroke");
		press(panel, KeyEvent.VK_ESCAPE, InputEvent.ALT_DOWN_MASK);
		require(readField(panel, "activeDoodleStroke") == null
				&& panel.getAnnotationTool() == AnnotationTool.DOODLE
				&& panel.getDoodleStrokeCount() == 0,
				"Option+Escape did not cancel only the active Doodle stroke");
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x + 24, y + 24));
		press(panel, KeyEvent.VK_ESCAPE, InputEvent.CTRL_DOWN_MASK);
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
				"Control+Escape did not exit Doodle after canceling its active stroke");

		int[] modifiers = {
			InputEvent.SHIFT_DOWN_MASK,
			InputEvent.CTRL_DOWN_MASK,
			InputEvent.ALT_DOWN_MASK,
			InputEvent.META_DOWN_MASK,
			InputEvent.ALT_GRAPH_DOWN_MASK,
			InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK
		};
		for (int modifier : modifiers) {
			panel.setAnnotationTool(AnnotationTool.DOODLE);
			press(panel, KeyEvent.VK_ESCAPE, modifier);
			require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
					"modified Escape did not exit Doodle mode for mask " + modifier);
		}
	}

	private static void verifySettledToggleAfterBoardUse(SudokuPanel panel, int x, int y) {
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
		long startedAt = System.currentTimeMillis() + 1000L;
		tapAt(panel, KeyEvent.VK_P, startedAt);
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
		panel.dispatchEvent(drag(panel, x + 12, y + 12));
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x + 24, y + 24));
		require(panel.getDoodleStrokeCount() == 1,
				"settled-toggle setup did not commit a Doodle stroke");
		tapAt(panel, KeyEvent.VK_P, startedAt + 400L);
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE
				&& panel.getDoodleStrokeCount() == 1,
				"P lost its return mode after board use or cleared completed Doodles");
		int userValueIndex = firstUserValueIndex(panel);
		panel.setActiveCell(userValueIndex);
		String boardBeforeEdit = panel.getSudokuString(ClipboardMode.PM_GRID);
		keyDownAt(panel, KeyEvent.VK_DELETE, 0, startedAt + 450L);
		keyUpAt(panel, KeyEvent.VK_DELETE, 0, startedAt + 470L);
		require(!boardBeforeEdit.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
				"Delete did not mutate the Sudoku board during double-tap cancellation setup");
		tapAt(panel, KeyEvent.VK_P, startedAt + 500L);
		require(panel.getAnnotationTool() == AnnotationTool.DOODLE
				&& panel.getDoodleStrokeCount() == 1,
				"Delete did not cancel the stale P double-tap candidate");
		panel.undo();
		require(boardBeforeEdit.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
				"double-tap cancellation setup did not restore the Sudoku board");
		panel.clearDoodlesWithUndo();
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
	}

	private static int firstUserValueIndex(SudokuPanel panel) {
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			if (panel.getSudoku().getValue(index) != 0 && !panel.getSudoku().isFixed(index)) {
				return index;
			}
		}
		throw new AssertionError("probe puzzle exposed no deletable user value");
	}

	private static void verifyBoxSelection(MainFrame frame, SudokuPanel panel, String boardBefore) {
		press(panel, KeyEvent.VK_S, 0);
		BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
		panel.paint(image.getGraphics());
		int cellSize;
		try {
			cellSize = readInt(panel, "cellSize");
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
		int x00 = panel.getX(0, 0) + cellSize / 2;
		int y00 = panel.getY(0, 0) + cellSize / 2;
		java.util.ArrayList<Integer> selectionBefore =
				new java.util.ArrayList<Integer>(panel.getSelectedCells());
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x00, y00));
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x00, y00));
		SudokuSet footprint = panel.getBoxReasoningFootprint();
		require(footprint.size() == 1 && footprint.contains(0),
				"Box point gesture did not add the hit cell");
		require(selectionBefore.equals(panel.getSelectedCells()),
				"Box point gesture changed ordinary Sudoku selection");
		require(boardBefore.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
				"Box point gesture changed the Sudoku board");

		int x11 = panel.getX(1, 1) + cellSize / 2;
		int y11 = panel.getY(1, 1) + cellSize / 2;
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x00, y00));
		panel.dispatchEvent(drag(panel, x11, y11));
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x11, y11));
		footprint = panel.getBoxReasoningFootprint();
		require(footprint.size() == 3 && !footprint.contains(0) && footprint.contains(1)
				&& footprint.contains(9) && footprint.contains(10),
				"Box drag did not use semantic cell-center hit testing: " + footprint);
		press(panel, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
		require(panel.getBoxReasoningFootprint().size() == 1,
				"Box undo did not restore the prior region atomically");

		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x00, y00,
				SudokuUtil.getDeletionModifierMask()));
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x00, y00));
		require(panel.getBoxReasoningFootprint().isEmpty(),
				"Control Box gesture did not remove the selected cell");
		press(panel, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
		require(panel.getBoxReasoningFootprint().size() == 1,
				"Box undo did not restore a deletion gesture");
		require(panel.getStep() == null, "Box release started analysis without Enter");
		GuiState state = new GuiState(panel, null, null);
		state.setIncludeAnnotations(true);
		state.get(true);
		MainFrame restoredFrame = new MainFrame(null);
		try {
			state.initialize(restoredFrame.getSudokuPanel(), null, null);
			state.set();
			SudokuPanel restored = restoredFrame.getSudokuPanel();
			require(restored.getBoxReasoningFootprint().size() == 1,
					"last-session state did not restore Box reasoning regions");
			require(restored.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
					"restoring Box regions did not reset the pointer tool");
			press(restored, KeyEvent.VK_S, 0);
			press(restored, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
			require(restored.getBoxReasoningFootprint().isEmpty(),
					"last-session state did not restore Box undo history");
		} finally {
			restoredFrame.dispose();
		}
		panel.clearBoxReasoningWithUndo();
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
	}

	private static void verifyToolLocalPalettes(SudokuPanel panel) {
		Options.getInstance().initializeAnnotationPalettePreferences(null, null);
		CellZoomPanel zoom = panel.getCellZoomPanel();
		// Both legacy coloring entry points select one tool and share its paired palette.
		panel.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);
		zoom.selectPaletteGroup(1);
		requirePalettePair(zoom, 1, false, "initial coloring pair");
		panel.setAnnotationTool(AnnotationTool.CELL_COLORING);
		require(panel.getAnnotationTool() == AnnotationTool.CANDIDATE_COLORING,
				"legacy Cell Coloring entry did not select unified coloring");
		requirePalettePair(zoom, 1, false, "legacy coloring entry lost the shared pair");
		zoom.selectPaletteGroup(2);
		press(panel, KeyEvent.VK_X, 0);
		requirePalettePair(zoom, 2, true, "X did not swap the shared coloring pair");
		panel.setAnnotationTool(AnnotationTool.DOODLE);
		zoom.selectPaletteGroup(4);
		panel.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);
		requirePalettePair(zoom, 2, true, "coloring did not retain its shared group and orientation");
		panel.setAnnotationTool(AnnotationTool.CELL_COLORING);
		requirePalettePair(zoom, 2, true, "legacy coloring entry restored a separate palette");
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
		press(panel, KeyEvent.VK_T, 0);
		require(panel.getAnnotationTool() == AnnotationTool.CANDIDATE_COLORING,
				"T did not restore unified coloring");
		requirePalettePair(zoom, 2, true, "T lost the shared coloring pair");
		panel.setAnnotationTool(AnnotationTool.DOODLE);
		requirePalettePair(zoom, 4, false, "Doodle did not retain its independent pair");
		panel.setAnnotationTool(AnnotationTool.FREE_CHAIN);
		press(panel, KeyEvent.VK_A, 0);
		panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
		press(panel, KeyEvent.VK_B, 0);
		panel.setAnnotationTool(AnnotationTool.FREE_CHAIN);
		require(zoom.getPaletteGroup() == 0,
				"Free Chain did not retain its independent Primary group");
		panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
		require(zoom.getPaletteGroup() == 1,
				"Box group selection leaked into Free Chain");
		Color secondary = zoom.getSecondaryColor();
		zoom.setSecondaryColor(Color.YELLOW);
		require(secondary.equals(zoom.getSecondaryColor()),
				"Box accepted a Secondary palette state");
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
	}

	private static void requirePalettePair(CellZoomPanel zoom, int group,
			boolean swapped, String message) {
		Color[] colors = Options.getInstance().getColoringColors();
		require(zoom.getPaletteGroup() == group
				&& colors[group * 2 + (swapped ? 1 : 0)].equals(zoom.getPrimaryColor())
				&& colors[group * 2 + (swapped ? 0 : 1)].equals(zoom.getSecondaryColor()), message);
	}

	private static void verifyPaletteAccessibility(MainFrame frame, SudokuPanel panel) throws Exception {
		CellZoomPanel zoom = panel.getCellZoomPanel();
		Object slots = readField(zoom, "cellPanels");
		require(slots instanceof javax.swing.JToggleButton[],
				"shared palette slots are not keyboard-operable toggle controls");
		javax.swing.JToggleButton[] buttons = (javax.swing.JToggleButton[]) slots;
		require(buttons.length == 12, "shared palette did not expose all A-F color slots");
		for (javax.swing.JToggleButton button : buttons) {
			require(button.isFocusable() && button.isEnabled(),
					"shared palette exposed a mouse-only color slot");
			require(button.getAccessibleContext().getAccessibleRole()
					== javax.accessibility.AccessibleRole.TOGGLE_BUTTON,
					"shared palette slot did not expose its toggle-button role");
		}

		panel.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);
		requireFocusedButtonKeyboardPassThrough(frame, buttons[9]);
		buttons[9].doClick(); // E light
		require(Options.getInstance().getColoringColors()[9].equals(zoom.getPrimaryColor())
				&& buttons[9].isSelected()
				&& buttons[9].getAccessibleContext().getAccessibleStateSet().contains(
						javax.accessibility.AccessibleState.CHECKED),
				"keyboard activation did not select and expose the E-light palette state");

		panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
		requireFocusedButtonKeyboardPassThrough(frame, buttons[11]);
		buttons[11].doClick(); // F pair, activated through its light swatch
		require(zoom.getPaletteGroup() == 5 && buttons[10].isSelected()
				&& buttons[10].getAccessibleContext().getAccessibleStateSet().contains(
						javax.accessibility.AccessibleState.CHECKED),
				"keyboard activation did not select and expose Box palette group F");
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
	}

	private static void requireFocusedButtonKeyboardPassThrough(MainFrame frame,
			javax.swing.JToggleButton button) throws Exception {
		KeyEvent event = new KeyEvent(button, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
				KeyEvent.VK_SPACE, ' ');
		java.awt.KeyEventDispatcher dispatcher =
				(java.awt.KeyEventDispatcher) readField(frame, "annotationKeyDispatcher");
		require(!dispatcher.dispatchKeyEvent(event),
				"global annotation shortcuts swallowed Space from a focused palette button");
		require(button.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(
				javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false)) != null,
				"focused palette button has no Space-key activation binding");
	}

	private static void press(SudokuPanel panel, int keyCode, int modifiers) {
		keyDown(panel, keyCode, modifiers);
		keyUp(panel, keyCode, modifiers);
	}

	private static void keyDown(SudokuPanel panel, int keyCode, int modifiers) {
		keyDownAt(panel, keyCode, modifiers, System.currentTimeMillis());
	}

	private static void keyDownAt(SudokuPanel panel, int keyCode, int modifiers, long when) {
		KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED, when, modifiers,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener listener : panel.getKeyListeners()) {
			listener.keyPressed(event);
		}
	}

	private static void keyUp(SudokuPanel panel, int keyCode, int modifiers) {
		keyUpAt(panel, keyCode, modifiers, System.currentTimeMillis());
	}

	private static void keyUpAt(SudokuPanel panel, int keyCode, int modifiers, long when) {
		KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_RELEASED, when, modifiers,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener listener : panel.getKeyListeners()) {
			listener.keyReleased(event);
		}
	}

	private static void tapAt(SudokuPanel panel, int keyCode, long when) {
		keyDownAt(panel, keyCode, 0, when);
		keyUpAt(panel, keyCode, 0, when + 20L);
	}

	private static void pressChar(SudokuPanel panel, int keyCode, int modifiers, char keyChar) {
		KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers,
				keyCode, keyChar);
		for (java.awt.event.KeyListener listener : panel.getKeyListeners()) {
			listener.keyPressed(event);
		}
	}

	private static MouseEvent mouse(SudokuPanel panel, int id, int x, int y) {
		return mouse(panel, id, x, y, 0);
	}

	private static MouseEvent mouse(SudokuPanel panel, int id, int x, int y, int modifiers) {
		return new MouseEvent(panel, id, System.currentTimeMillis(), modifiers, x, y, 1, false,
				MouseEvent.BUTTON1);
	}

	private static MouseEvent mouseButton(SudokuPanel panel, int id, int x, int y, int button) {
		return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false, button);
	}

	private static void dispatchWheel(SudokuPanel panel, int rotation, int modifiers) {
		panel.dispatchEvent(new java.awt.event.MouseWheelEvent(panel, MouseEvent.MOUSE_WHEEL,
				System.currentTimeMillis(), modifiers, 100, 100, 0, false,
				java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, rotation));
	}

	private static MouseEvent drag(SudokuPanel panel, int x, int y) {
		return new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
				MouseEvent.BUTTON1_DOWN_MASK, x, y, 0, false, MouseEvent.NOBUTTON);
	}

	private static boolean doodleIsVisible(SudokuPanel panel, Color expected) {
		BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
		panel.paint(image.getGraphics());
		for (int y = 75; y <= 100; y++) {
			for (int x = 70; x <= 95; x++) {
				Color actual = new Color(image.getRGB(x, y), true);
				int distance = Math.abs(actual.getRed() - expected.getRed())
						+ Math.abs(actual.getGreen() - expected.getGreen())
						+ Math.abs(actual.getBlue() - expected.getBlue());
				if (actual.getAlpha() > 180 && distance < 75) {
					return true;
				}
			}
		}
		return false;
	}

	private static void verifyFreeChainV2(MainFrame frame, SudokuPanel panel) throws Exception {
		java.awt.BasicStroke shortWeak = SudokuPanel.createUserChainStroke(false, 3.0f, 9.0);
		require(shortWeak.getDashArray() != null && shortWeak.getEndCap() == java.awt.BasicStroke.CAP_BUTT,
				"Weak-link rendering lost its dashed relation shape");
		require(SudokuPanel.createUserChainStroke(true, 3.0f, 9.0).getDashArray() == null,
				"Strong link unexpectedly uses a dashed stroke");
		press(panel, KeyEvent.VK_L, 0);
		require(panel.getAnnotationTool() == AnnotationTool.FREE_CHAIN,
				"L did not enter Free Chain mode");
		CellZoomPanel zoom = panel.getCellZoomPanel();
		press(panel, KeyEvent.VK_A, 0);
		Color deep = Options.getInstance().getColoringColors()[0];
		Color light = Options.getInstance().getColoringColors()[1];
		require(deep.equals(zoom.getPrimaryColor()), "A did not select Free Chain group A");
		int groupBeforeWheel = zoom.getPaletteGroup();
		writeLong(zoom, "lastPaletteWheelAt", 0L);
		dispatchWheel(panel, 1, InputEvent.ALT_DOWN_MASK);
		require(zoom.getPaletteGroup() == (groupBeforeWheel + 1) % 6
				&& zoom.getSecondaryColor().equals(Options.getInstance().getColoringColors()[
						zoom.getPaletteGroup() * 2 + 1]),
				"Free Chain did not treat Option-wheel as a single Primary-group change");
		press(panel, KeyEvent.VK_A, 0);

		String boardBeforeChain = panel.getSudokuString(ClipboardMode.PM_GRID);
		press(panel, KeyEvent.VK_2, 0);
		press(panel, KeyEvent.VK_4, InputEvent.META_DOWN_MASK);
		require(boardBeforeChain.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
				"Free Chain mode leaked a digit into the Sudoku board");

		BufferedImage image = new BufferedImage(600, 600, BufferedImage.TYPE_INT_ARGB);
		panel.paint(image.getGraphics());
		int cellSize = readInt(panel, "cellSize");
		int[][] candidates = firstCandidateLocations(panel, 4);
		short[] savedUserCandidates = panel.getSudoku().getUserCells().clone();
		panel.getSudoku().setUserCells(new short[Sudoku2.LENGTH]);
		panel.setShowCandidates(false);
		clickCandidate(panel, candidates[0], cellSize);
		require(readField(panel, "activeUserChain") == null,
				"Free Chain created a node for a candidate hidden in user-candidate mode");
		panel.getSudoku().setUserCells(savedUserCandidates);
		panel.setShowCandidates(true);
		clickCandidate(panel, candidates[0], cellSize);
		UserChain draft = (UserChain) readField(panel, "activeUserChain");
		require(draft != null && draft.getNodes().size() == 1,
				"first candidate click did not immediately create a Free-chain node");
		require(deep.equals(draft.getNodes().get(0).getColor()),
				"first Free-chain node did not capture the pair's Strong color");
		clickCandidate(panel, candidates[0], cellSize);
		require(((UserChain) readField(panel, "activeUserChain")).getNodes().size() == 1
				&& panel.getUserChainCount() == 0,
				"clicking the current end unexpectedly completed the chain");

		press(panel, KeyEvent.VK_P, 0);
		press(panel, KeyEvent.VK_L, 0);
		require(readField(panel, "activeUserChain") != null,
				"temporary tool switching discarded an unfinished chain");
		clickCandidate(panel, candidates[1], cellSize);
		draft = (UserChain) readField(panel, "activeUserChain");
		require(draft.getStrongRelations().size() == 1
				&& draft.getStrongRelations().get(0).booleanValue() && !draft.isNextStrong(),
				"first edge was not Strong or did not alternate the pending relation");
		press(panel, KeyEvent.VK_SPACE, 0);
		press(panel, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
		draft = (UserChain) readField(panel, "activeUserChain");
		require(draft != null && draft.getNodes().size() == 1,
				"pending Strong/Weak toggle created its own undo transaction");
		press(panel, KeyEvent.VK_Y, InputEvent.META_DOWN_MASK);
		draft = (UserChain) readField(panel, "activeUserChain");
		require(draft.getNodes().size() == 2 && draft.isNextStrong(),
				"content redo did not preserve the pending relation captured with the edge");
		press(panel, KeyEvent.VK_SPACE, 0);
		require(!((UserChain) readField(panel, "activeUserChain")).isNextStrong(),
				"Space did not switch the pending relation back to Weak");
		clickCandidate(panel, candidates[2], cellSize);
		draft = (UserChain) readField(panel, "activeUserChain");
		require(draft.getStrongRelations().size() == 2
				&& !draft.getStrongRelations().get(1).booleanValue() && draft.isNextStrong(),
				"second edge was not Weak or did not alternate back to Strong");
		clickCandidate(panel, candidates[0], cellSize);
		draft = (UserChain) readField(panel, "activeUserChain");
		require(draft.isClosed() && draft.getStrongRelations().size() == 3
				&& panel.getUserChainCount() == 0,
				"clicking the start did not close the draft without analyzing it");
		clickCandidate(panel, candidates[3], cellSize);
		require(((UserChain) readField(panel, "activeUserChain")).getNodes().size() == 3,
				"a closed draft accepted another node");
		press(panel, KeyEvent.VK_BACK_SPACE, 0);
		draft = (UserChain) readField(panel, "activeUserChain");
		require(!draft.isClosed() && draft.getStrongRelations().size() == 2 && draft.isNextStrong(),
				"Backspace did not reopen the closed draft and restore its closing relation");
		press(panel, KeyEvent.VK_ENTER, 0);
		require(panel.getUserChainCount() == 1 && readField(panel, "activeUserChain") == null,
				"Enter did not become the sole Free-chain confirmation boundary");
		@SuppressWarnings("unchecked")
		java.util.List<UserChain> completed = (java.util.List<UserChain>) readField(panel, "userChains");
		require(deep.equals(completed.get(0).getNodes().get(1).getColor())
				&& light.equals(completed.get(0).getNodes().get(2).getColor()),
				"Free-chain nodes did not capture Strong/Weak pair members");
		press(panel, KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
		require(panel.getUserChainCount() == 0,
				"Free-chain confirmation was not independently undoable");
		press(panel, KeyEvent.VK_Y, InputEvent.META_DOWN_MASK);
		require(panel.getUserChainCount() == 1,
				"Free-chain confirmation was not independently redoable");
		require(boardBeforeChain.equals(panel.getSudokuString(ClipboardMode.PM_GRID)),
				"Free-chain history traversed Sudoku history");
		clickCandidate(panel, candidates[3], cellSize);
		require(readField(panel, "activeUserChain") != null,
				"session sanitation fixture did not create an unfinished draft");
		require(!panel.hasSelectedUserChainForReasoning(),
				"starting a new draft retained the previously confirmed chain source");
		completed.get(0).setSourceId(0L);

		GuiState state = new GuiState(panel, null, null);
		state.setIncludeAnnotations(true);
		state.get(true);
		MainFrame restoredFrame = new MainFrame(null);
		try {
			state.initialize(restoredFrame.getSudokuPanel(), null, null);
			state.set();
			require(restoredFrame.getSudokuPanel().getUserChainCount() == 1,
					"last-session state did not restore completed Free chains");
			@SuppressWarnings("unchecked")
			java.util.List<UserChain> restoredChains = (java.util.List<UserChain>) readField(
					restoredFrame.getSudokuPanel(), "userChains");
			require(restoredChains.get(0).getSourceId() > 0L,
					"legacy completed Free chains were not assigned an analyzable source ID");
			require(!restoredFrame.getSudokuPanel().hasSelectedUserChainForReasoning(),
					"session restore implicitly selected an old completed chain for analysis");
			require(readField(restoredFrame.getSudokuPanel(), "activeUserChain") == null,
					"last-session state restored an unfinished Free-chain draft");
			require(restoredFrame.getSudokuPanel().getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
					"session restore did not reset the active annotation tool");
			press(restoredFrame.getSudokuPanel(), KeyEvent.VK_L, 0);
			press(restoredFrame.getSudokuPanel(), KeyEvent.VK_Z, InputEvent.META_DOWN_MASK);
			require(restoredFrame.getSudokuPanel().getUserChainCount() == 0
					&& readField(restoredFrame.getSudokuPanel(), "activeUserChain") == null,
					"sanitized session history revived a draft or kept an empty transition");
		} finally {
			restoredFrame.dispose();
		}
		panel.clearUserChainsWithUndo();
		panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
	}

	private static int[][] firstCandidateLocations(SudokuPanel panel, int count) {
		int[][] result = new int[count][3];
		int found = 0;
		Sudoku2 sudoku = panel.getSudoku();
		for (int index = 0; index < Sudoku2.LENGTH && found < count; index++) {
			if (sudoku.getValue(index) != 0) continue;
			for (int candidate = 1; candidate <= Sudoku2.UNITS && found < count; candidate++) {
				if (sudoku.isCandidate(index, candidate)) {
					result[found][0] = Sudoku2.getRow(index);
					result[found][1] = Sudoku2.getCol(index);
					result[found][2] = candidate;
					found++;
				}
			}
		}
		require(found == count, "probe puzzle did not expose enough candidates");
		return result;
	}

	private static void clickCandidate(SudokuPanel panel, int[] candidate, int cellSize) {
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED,
				candidateX(panel, candidate[0], candidate[1], candidate[2], cellSize),
				candidateY(panel, candidate[0], candidate[1], candidate[2], cellSize)));
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED,
				candidateX(panel, candidate[0], candidate[1], candidate[2], cellSize),
				candidateY(panel, candidate[0], candidate[1], candidate[2], cellSize)));
	}

	private static void verifyToolbarToolButtons(MainFrame frame, SudokuPanel panel) throws Exception {
		Field field = MainFrame.class.getDeclaredField("annotationToolButtons");
		field.setAccessible(true);
		javax.swing.JToggleButton[] buttons = (javax.swing.JToggleButton[]) field.get(frame);
		require(buttons.length == AnnotationTool.values().length,
				"toolbar did not expose all annotation tools");
		buttons[AnnotationTool.DEFAULT_MOUSE.ordinal()].doClick();
		require(panel.getAnnotationTool() == AnnotationTool.DEFAULT_MOUSE,
				"toolbar Mouse button did not switch annotation tools");
		buttons[AnnotationTool.DOODLE.ordinal()].doClick();
		require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
				"toolbar Doodle button did not switch annotation tools");
		javax.swing.JRadioButton chainRadio = (javax.swing.JRadioButton) readField(
				panel.getCellZoomPanel(), "radioButtonFreeChain");
		javax.swing.JRadioButton doodleRadio = (javax.swing.JRadioButton) readField(
				panel.getCellZoomPanel(), "radioButtonDoodle");
		chainRadio.doClick();
		require(panel.getAnnotationTool() == AnnotationTool.FREE_CHAIN,
				"sidebar Free Chain radio did not switch annotation tools");
		doodleRadio.doClick();
		require(panel.getAnnotationTool() == AnnotationTool.DOODLE,
				"sidebar Doodle radio did not switch annotation tools");
	}

	private static void makeUndoableBoardEdit(SudokuPanel panel) {
		Sudoku2 sudoku = panel.getSudoku();
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			if (sudoku.getValue(index) != 0) continue;
			for (int candidate = 1; candidate <= Sudoku2.UNITS; candidate++) {
				if (sudoku.isCandidate(index, candidate)) {
					panel.saveState();
					panel.setCell(Sudoku2.getRow(index), Sudoku2.getCol(index), candidate);
					return;
				}
			}
		}
		throw new AssertionError("probe puzzle had no editable cell");
	}

	private static void dispatchFocusedKey(MainFrame frame, java.awt.Component source,
			int keyCode, int modifiers) throws Exception {
		long when = System.currentTimeMillis();
		require(dispatchFocusedPress(frame, source, keyCode, modifiers, when),
				"global annotation dispatcher did not consume focused shortcut " + KeyEvent.getKeyText(keyCode));
		KeyEvent release = new KeyEvent(source, KeyEvent.KEY_RELEASED, when, modifiers,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		java.awt.KeyEventDispatcher dispatcher =
				(java.awt.KeyEventDispatcher) readField(frame, "annotationKeyDispatcher");
		dispatcher.dispatchKeyEvent(release);
	}

	private static boolean dispatchFocusedPress(MainFrame frame, java.awt.Component source,
			int keyCode, int modifiers) throws Exception {
		return dispatchFocusedPress(frame, source, keyCode, modifiers, System.currentTimeMillis());
	}

	private static boolean dispatchFocusedPress(MainFrame frame, java.awt.Component source,
			int keyCode, int modifiers, long when) throws Exception {
		KeyEvent event = new KeyEvent(source, KeyEvent.KEY_PRESSED, when, modifiers,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		java.awt.KeyEventDispatcher dispatcher =
				(java.awt.KeyEventDispatcher) readField(frame, "annotationKeyDispatcher");
		return dispatcher.dispatchKeyEvent(event);
	}

	private static void dispatchEditableKey(MainFrame frame, java.awt.Component source,
			int keyCode) throws Exception {
		KeyEvent event = new KeyEvent(source, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
				keyCode, Character.toLowerCase((char) keyCode));
		java.awt.KeyEventDispatcher dispatcher =
				(java.awt.KeyEventDispatcher) readField(frame, "annotationKeyDispatcher");
		require(!dispatcher.dispatchKeyEvent(event),
				"global annotation dispatcher consumed a letter from editable text");
	}

	private static void invokeFrameAction(MainFrame frame, String methodName) throws Exception {
		java.lang.reflect.Method method = MainFrame.class.getDeclaredMethod(methodName,
				java.awt.event.ActionEvent.class);
		method.setAccessible(true);
		method.invoke(frame, new Object[] { null });
	}

	private static void showCellZoomPanel(MainFrame frame, CellZoomPanel zoom) throws Exception {
		java.lang.reflect.Method method = MainFrame.class.getDeclaredMethod("setSplitPane",
				javax.swing.JPanel.class);
		method.setAccessible(true);
		method.invoke(frame, zoom);
	}

	private static Object readField(Object target, String fieldName) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(target);
	}

	private static int candidateX(SudokuPanel panel, int row, int col, int candidate, int cellSize) {
		return (int) Math.round(panel.getX(row, col) + ((candidate - 1) % 3) * cellSize / 3.0 + cellSize / 6.0);
	}

	private static int candidateY(SudokuPanel panel, int row, int col, int candidate, int cellSize) {
		return (int) Math.round(panel.getY(row, col) + ((candidate - 1) / 3) * cellSize / 3.0 + cellSize / 6.0);
	}

	private static int readInt(Object target, String fieldName) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.getInt(target);
	}

	private static void writeLong(Object target, String fieldName, long value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.setLong(target, value);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
