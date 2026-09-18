/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Focused checks for the read-only, reference-aware hint surface. */
public final class HintInteractionProbe {

	private static final String PUZZLE =
			"530070000600195000098000060800060003400803001700020006060000280000419005000080079";

	private HintInteractionProbe() {
	}

	public static void main(String[] args) throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					verifyFocusedPasteShortcut();
					frame = new MainFrame(null);
					HintTextArea hint = frame.getHintTextArea();
					SudokuPanel panel = frame.getSudokuPanel();
					hint.setSize(600, 100);
					frame.importExternalHintText("查看 r1c2 和第三宫");
					require(!hint.isEditable(), "hint text became editable");
					require(!hint.getCaret().isVisible(), "read-only hint exposed a caret");
					require(hint.getReferences().size() == 2, "external hint references were not parsed");

					SudokuTextReference reference = hint.getReferences().get(0);
					Rectangle bounds = hint.modelToView(reference.getStart());
					require(bounds != null, "reference did not have view bounds");
					int x = bounds.x + 1;
					int y = bounds.y + Math.max(1, bounds.height / 2);
					hint.dispatchEvent(mouse(hint, MouseEvent.MOUSE_MOVED, x, y));
					require(hint.getCursor().getType() == java.awt.Cursor.HAND_CURSOR,
							"hovering a hint reference did not use the link cursor");
					hint.dispatchEvent(mouse(hint, MouseEvent.MOUSE_PRESSED, x, y));
					require(panel.hasTransientReferenceHighlight(), "holding a hint reference did not highlight the grid");
					hint.dispatchEvent(mouse(hint, MouseEvent.MOUSE_DRAGGED,
							hint.getWidth() - 2, hint.getHeight() - 2));
					require(!panel.hasTransientReferenceHighlight(),
							"dragging away from a hint reference left the grid highlighted");
					require(hint.getCursor().getType() == java.awt.Cursor.DEFAULT_CURSOR,
							"dragging away from a hint reference left the link cursor active");
					hint.dispatchEvent(mouse(hint, MouseEvent.MOUSE_PRESSED, x, y));
					hint.dispatchEvent(mouse(hint, MouseEvent.MOUSE_RELEASED, x, y));
					require(!panel.hasTransientReferenceHighlight(), "releasing a hint reference left the grid highlighted");

					int caret = hint.getCaretPosition();
					hint.dispatchEvent(mouse(hint, MouseEvent.MOUSE_CLICKED, x, y));
					require(hint.getCaretPosition() == caret && hint.getSelectionStart() == hint.getSelectionEnd(),
							"clicking read-only hint text moved or selected the caret");

					String before = panel.getSudoku().getSudoku(ClipboardMode.VALUES_ONLY);
					panel.setStep(new SolutionStep(SolutionType.FULL_HOUSE));
					frame.pasteText("再看 r2c3", true);
					require("再看 r2c3".equals(hint.getText()), "focused paste did not replace the hint text");
					require(panel.getStep() != null, "focused paste removed the displayed solution step");
					require(before.equals(panel.getSudoku().getSudoku(ClipboardMode.VALUES_ONLY)),
							"focused hint paste changed the Sudoku");

					String longHint = buildLongHint();
					frame.pasteText(longHint, true);
					require(longHint.equals(hint.getText()), "long Unicode hint paste was altered or truncated");
					require(hint.getReferences().size() >= 120,
							"long Unicode hint references were not parsed after paste");

					frame.pasteText(PUZZLE, false);
					require(hint.getText().isEmpty(), "puzzle paste did not clear temporary hint text");
					require(PUZZLE.equals(PuzzleHistoryEntry.normalizeClues(
							panel.getSudoku().getSudoku(ClipboardMode.CLUES_ONLY))),
							"ordinary paste no longer loaded the Sudoku");
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
		System.out.println("Hint interaction checks passed");
		System.exit(0);
	}

	private static void verifyFocusedPasteShortcut() {
		HintTextArea hint = new HintTextArea();
		final boolean[] invoked = new boolean[1];
		hint.setExternalPasteAction(new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent event) {
				invoked[0] = true;
			}
		});
		KeyStroke shortcut = KeyStroke.getKeyStroke(KeyEvent.VK_V, SudokuUtil.getMenuShortcutMask());
		Object actionKey = hint.getInputMap(JComponent.WHEN_FOCUSED).get(shortcut);
		Action action = actionKey == null ? null : hint.getActionMap().get(actionKey);
		require(action != null, "focused hint paste shortcut was not installed");
		action.actionPerformed(new ActionEvent(hint, ActionEvent.ACTION_PERFORMED, "paste"));
		require(invoked[0], "focused hint paste shortcut was swallowed by the read-only text area");
	}

	private static String buildLongHint() {
		StringBuilder text = new StringBuilder();
		text.append("空矩形（Empty Rectangle）：6 位于 b3 (r34c6) => r4c7<>6\n");
		text.append("中文、箭头 →、强链 ║ 与 HTML 实体 &#x20; 都应原样保留。\n");
		for (int i = 0; i < 120; i++) {
			int row = i % 9 + 1;
			int column = (i * 2) % 9 + 1;
			text.append("步骤 ").append(i + 1).append("：检查 r").append(row).append('c').append(column)
					.append("，再看 c").append(column).append(" 与第").append(row).append("行。\n");
		}
		return text.toString();
	}

	private static MouseEvent mouse(HintTextArea hint, int id, int x, int y) {
		boolean motion = id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_MOVED;
		int modifiers = id == MouseEvent.MOUSE_DRAGGED ? java.awt.event.InputEvent.BUTTON1_DOWN_MASK : 0;
		int button = motion ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
		return new MouseEvent(hint, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
