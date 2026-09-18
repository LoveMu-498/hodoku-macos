/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

/** macOS application appearance selected for the next launch. */
public enum AppearanceMode {
	SYSTEM("system"),
	LIGHT("NSAppearanceNameAqua"),
	DARK("NSAppearanceNameDarkAqua");

	private final String macOSPropertyValue;

	AppearanceMode(String macOSPropertyValue) {
		this.macOSPropertyValue = macOSPropertyValue;
	}

	public static AppearanceMode fromName(String name) {
		if (name != null) {
			for (AppearanceMode mode : values()) {
				if (mode.name().equalsIgnoreCase(name)) {
					return mode;
				}
			}
		}
		return SYSTEM;
	}

	String getMacOSPropertyValue() {
		return macOSPropertyValue;
	}
}
