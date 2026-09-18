/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import javax.swing.SwingUtilities;

/** Verifies that text references and board handles use a temporary screen overlay. */
public final class TransientReferenceHighlightProbe {

	private TransientReferenceHighlightProbe() {
	}

	public static void main(String[] args) throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					ApplicationAppearance.initialize(AppearanceMode.DARK);
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					panel.setSize(720, 720);
					BufferedImage baseline = render(panel);
					int selectedCount = panel.getCellSelectionSize();

					SudokuTextReference cell = SudokuReferenceParser.parse("r1c1").get(0);
					panel.setTransientReferenceHighlight(cell);
					require(panel.hasTransientReferenceHighlight(), "reference press did not activate an overlay");
					BufferedImage highlighted = render(panel);
					int x = panel.getX(0, 0) + 8;
					int y = panel.getY(0, 0) + 8;
					require(baseline.getRGB(x, y) != highlighted.getRGB(x, y),
							"referenced cell background did not change");
					int otherX = panel.getX(8, 8) + 8;
					int otherY = panel.getY(8, 8) + 8;
					require(baseline.getRGB(otherX, otherY) == highlighted.getRGB(otherX, otherY),
							"reference overlay changed an unrelated cell");
					panel.clearTransientReferenceHighlight();
					require(!panel.hasTransientReferenceHighlight(), "reference release did not clear the overlay");
					require(baseline.getRGB(x, y) == render(panel).getRGB(x, y),
							"clearing the overlay did not restore the board");

					int handleX = panel.getX(0, 2) + (panel.getX(0, 3) - panel.getX(0, 2)) / 2;
					int handleY = panel.getY(0, 0) - 7;
					panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED,
							System.currentTimeMillis(), 0, handleX, handleY, 1, false, MouseEvent.BUTTON1));
					require(panel.hasTransientReferenceHighlight(), "column handle press did not activate an overlay");
					require(panel.getTransientReferenceKind() == SudokuTextReference.Kind.COLUMNS,
							"top handle did not target a column");
					require(panel.getCellSelectionSize() == selectedCount,
							"column handle changed the current-cell selection");
					panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED,
							System.currentTimeMillis(), 0, handleX, handleY, 1, false, MouseEvent.BUTTON1));
					require(!panel.hasTransientReferenceHighlight(), "column handle release did not clear the overlay");

					verifyKeyboardUnitHighlight(panel);
					verifyApplicationWideKeyboardUnitHighlight(frame, panel);
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
		System.out.println("Transient reference highlight checks passed");
		System.exit(0);
	}

	private static void verifyKeyboardUnitHighlight(SudokuPanel panel) {
		int modifiers = InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
		pressKey(panel, KeyEvent.VK_R, modifiers);
		pressKey(panel, KeyEvent.VK_3, modifiers);
		require(panel.getTransientReferenceKind() == SudokuTextReference.Kind.ROWS,
				"Shift+Option+R+3 did not highlight row 3");
		releaseKey(panel, KeyEvent.VK_3, modifiers);
		require(!panel.hasTransientReferenceHighlight(),
				"releasing the row number left the row highlighted");
		releaseKey(panel, KeyEvent.VK_R, modifiers);

		pressKey(panel, KeyEvent.VK_C, modifiers);
		pressKey(panel, KeyEvent.VK_NUMPAD4, modifiers);
		require(panel.getTransientReferenceKind() == SudokuTextReference.Kind.COLUMNS,
				"Shift+Option+C+numpad 4 did not highlight column 4");
		releaseKey(panel, KeyEvent.VK_NUMPAD4, modifiers);
		require(!panel.hasTransientReferenceHighlight(),
				"releasing the column number left the column highlighted");
		releaseKey(panel, KeyEvent.VK_C, modifiers);

		pressKey(panel, KeyEvent.VK_B, modifiers);
		pressKey(panel, KeyEvent.VK_9, modifiers);
		require(panel.getTransientReferenceKind() == SudokuTextReference.Kind.BLOCKS,
				"Shift+Option+B+9 did not highlight block 9");
		releaseKey(panel, KeyEvent.VK_B, modifiers);
		require(!panel.hasTransientReferenceHighlight(),
				"releasing B left the block highlighted");
		releaseKey(panel, KeyEvent.VK_9, modifiers);

		pressKey(panel, KeyEvent.VK_R, modifiers);
		pressKey(panel, KeyEvent.VK_5, modifiers);
		releaseKey(panel, KeyEvent.VK_ALT, 0);
		require(!panel.hasTransientReferenceHighlight(),
				"releasing Option left the row highlighted");
		releaseKey(panel, KeyEvent.VK_5, 0);
		releaseKey(panel, KeyEvent.VK_R, 0);

		pressKey(panel, KeyEvent.VK_R, modifiers);
		pressKey(panel, KeyEvent.VK_5, modifiers);
		releaseKey(panel, KeyEvent.VK_SHIFT, InputEvent.ALT_DOWN_MASK);
		require(!panel.hasTransientReferenceHighlight(),
				"releasing Shift left the row highlighted");
		releaseKey(panel, KeyEvent.VK_5, InputEvent.ALT_DOWN_MASK);
		releaseKey(panel, KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK);

		pressKey(panel, KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK);
		pressKey(panel, KeyEvent.VK_3, InputEvent.ALT_DOWN_MASK);
		require(!panel.hasTransientReferenceHighlight(),
				"Option+R+3 must not trigger a unit highlight");
	}

	private static void verifyApplicationWideKeyboardUnitHighlight(MainFrame frame,
			SudokuPanel panel) throws Exception {
		Field field = MainFrame.class.getDeclaredField("annotationKeyDispatcher");
		field.setAccessible(true);
		KeyEventDispatcher dispatcher = (KeyEventDispatcher) field.get(frame);
		java.awt.Component source = frame.getRootPane();
		int modifiers = InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
		require(dispatch(dispatcher, source, KeyEvent.KEY_PRESSED, KeyEvent.VK_R,
				modifiers), "application dispatcher did not consume Shift+Option+R");
		require(dispatch(dispatcher, source, KeyEvent.KEY_PRESSED, KeyEvent.VK_3,
				modifiers), "application dispatcher did not consume Shift+Option+R+3");
		require(panel.getTransientReferenceKind() == SudokuTextReference.Kind.ROWS,
				"application-wide Shift+Option+R+3 did not highlight row 3");
		require(dispatch(dispatcher, source, KeyEvent.KEY_RELEASED, KeyEvent.VK_3,
				modifiers), "application dispatcher did not consume row-number release");
		require(!panel.hasTransientReferenceHighlight(),
				"application-wide row highlight remained after number release");
		require(dispatch(dispatcher, source, KeyEvent.KEY_RELEASED, KeyEvent.VK_R,
				modifiers), "application dispatcher did not consume R release");
	}

	private static boolean dispatch(KeyEventDispatcher dispatcher, java.awt.Component source,
			int id, int keyCode, int modifiers) {
		return dispatcher.dispatchKeyEvent(new KeyEvent(source, id, System.currentTimeMillis(), modifiers,
				keyCode, KeyEvent.CHAR_UNDEFINED));
	}

	private static void pressKey(SudokuPanel panel, int keyCode, int modifiers) {
		key(panel, KeyEvent.KEY_PRESSED, keyCode, modifiers);
	}

	private static void releaseKey(SudokuPanel panel, int keyCode, int modifiers) {
		key(panel, KeyEvent.KEY_RELEASED, keyCode, modifiers);
	}

	private static void key(SudokuPanel panel, int id, int keyCode, int modifiers) {
		KeyEvent event = new KeyEvent(panel, id, System.currentTimeMillis(), modifiers,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		for (KeyListener listener : panel.getKeyListeners()) {
			if (id == KeyEvent.KEY_PRESSED) {
				listener.keyPressed(event);
			} else {
				listener.keyReleased(event);
			}
		}
	}

	private static BufferedImage render(SudokuPanel panel) {
		BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		panel.paint(graphics);
		graphics.dispose();
		return image;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
