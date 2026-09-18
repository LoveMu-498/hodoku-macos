package sudoku;

import java.awt.Rectangle;

/** Regression checks for multi-display window-bound restoration. */
public final class WindowLayoutProbe {

	private WindowLayoutProbe() {
	}

	public static void main(String[] args) {
		Rectangle leftDisplay = new Rectangle(-1920, 0, 1920, 1080);
		assertBounds(new Rectangle(-1800, 100, 1200, 800),
				MainFrame.clampWindowBounds(new Rectangle(-1800, 100, 1200, 800), leftDisplay),
				"valid negative coordinates must be preserved");

		assertBounds(new Rectangle(-1920, 0, 1920, 1080),
				MainFrame.clampWindowBounds(new Rectangle(-2500, -200, 3000, 1600), leftDisplay),
				"oversized/off-screen bounds must fit the selected display");

		assertBounds(new Rectangle(1120, 280, 800, 800),
				MainFrame.clampWindowBounds(new Rectangle(1800, 900, 800, 800),
						new Rectangle(0, 0, 1920, 1080)),
				"right and bottom edges must stay on screen");

		System.out.println("Window layout checks passed");
	}

	private static void assertBounds(Rectangle expected, Rectangle actual, String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}
}
