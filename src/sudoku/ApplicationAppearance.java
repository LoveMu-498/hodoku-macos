/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import javax.swing.UIManager;

/** Effective startup appearance used by HoDoKu's custom-painted screen areas. */
public final class ApplicationAppearance {

	private static boolean effectiveDark;

	private ApplicationAppearance() {
	}

	/** Resolves the configured appearance after the Look & Feel has been installed. */
	public static void initialize(AppearanceMode requestedMode) {
		if (!SudokuUtil.isMacOS()) {
			effectiveDark = false;
			return;
		}
		if (requestedMode == AppearanceMode.DARK) {
			effectiveDark = true;
		} else if (requestedMode == AppearanceMode.LIGHT) {
			effectiveDark = false;
		} else {
			effectiveDark = detectSystemDarkAppearance();
		}
	}

	public static boolean isDark() {
		return effectiveDark;
	}

	private static boolean detectSystemDarkAppearance() {
		Color background = UIManager.getColor("text");
		Color foreground = UIManager.getColor("textText");
		return background != null && foreground != null
				&& relativeLuminance(background) < relativeLuminance(foreground);
	}

	private static double relativeLuminance(Color color) {
		return 0.2126 * linear(color.getRed() / 255.0)
				+ 0.7152 * linear(color.getGreen() / 255.0)
				+ 0.0722 * linear(color.getBlue() / 255.0);
	}

	private static double linear(double component) {
		return component <= 0.04045 ? component / 12.92
				: Math.pow((component + 0.055) / 1.055, 2.4);
	}
}
