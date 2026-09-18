/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;

/** Verifies that Help > Keyboard displays its localized HTML body. */
public final class KeyboardHelpDialogProbe {
	private KeyboardHelpDialogProbe() {
	}

	public static void main(String[] args) throws Exception {
		String originalOsName = System.getProperty("os.name");
		Locale originalLocale = Locale.getDefault();
		final Throwable[] failure = new Throwable[1];
		try {
			System.setProperty("os.name", "Mac OS X");
			Locale.setDefault(Locale.CHINESE);
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					KeyboardLayoutFrame frame = null;
					try {
						frame = new KeyboardLayoutFrame();
						JEditorPane editor = findComponent(frame.getContentPane(), JEditorPane.class);
						require(editor != null, "keyboard help has no editor pane");
						require(editor.getDocument().getLength() > 500,
								"keyboard help HTML document is empty");
						require(editor.getDocument().getText(0, editor.getDocument().getLength())
								.contains("基本操作"),
								"keyboard help did not load the Chinese body");

						editor.setSize(editor.getPreferredSize());
						BufferedImage image = new BufferedImage(Math.max(1, editor.getWidth()),
								Math.max(1, Math.min(500, editor.getHeight())), BufferedImage.TYPE_INT_RGB);
						Graphics2D graphics = image.createGraphics();
						editor.paint(graphics);
						graphics.dispose();
						require(hasVisibleInk(image, editor.getBackground()),
								"keyboard help body paints as a blank surface");
					} catch (Throwable ex) {
						failure[0] = ex;
					} finally {
						if (frame != null) frame.dispose();
					}
				}
			});
		} finally {
			Locale.setDefault(originalLocale);
			if (originalOsName == null) {
				System.clearProperty("os.name");
			} else {
				System.setProperty("os.name", originalOsName);
			}
		}
		if (failure[0] != null) throw new AssertionError("keyboard help dialog check failed", failure[0]);
		System.out.println("Keyboard help dialog checks passed");
		System.exit(0);
	}

	private static boolean hasVisibleInk(BufferedImage image, Color background) {
		int backgroundRgb = background.getRGB() & 0x00ffffff;
		int changed = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) & 0x00ffffff) != backgroundRgb && ++changed >= 20) return true;
			}
		}
		return false;
	}

	private static <T extends Component> T findComponent(Container root, Class<T> type) {
		for (Component component : root.getComponents()) {
			if (type.isInstance(component)) return type.cast(component);
			if (component instanceof Container) {
				T nested = findComponent((Container) component, type);
				if (nested != null) return nested;
			}
		}
		return null;
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
