/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.event.InputEvent;
import java.io.InputStream;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Scanner;
import javax.swing.KeyStroke;

/** Focused regression checks for locale reload and platform shortcuts. */
public final class LocaleShortcutProbe {

	private LocaleShortcutProbe() {
	}

	@SuppressWarnings("deprecation")
	public static void main(String[] args) throws Exception {
		String originalOsName = System.getProperty("os.name");
		Locale originalLocale = Locale.getDefault();
		try {
			System.setProperty("os.name", "Mac OS X");
			require(SudokuUtil.getMenuShortcutMask() == InputEvent.META_MASK,
					"macOS menu shortcut must use Command");
			require(SudokuUtil.isMenuShortcutDown(InputEvent.META_DOWN_MASK),
					"macOS extended Command modifier was not detected");
			require(!SudokuUtil.isMenuShortcutDown(InputEvent.CTRL_DOWN_MASK),
					"macOS Control must not alias Command");
            require(SudokuUtil.getDeletionModifierMask()==InputEvent.CTRL_DOWN_MASK,"Mac deletion modifier");
            require((SudokuUtil.getPlatformKeyStroke("meta X").getModifiers() & InputEvent.CTRL_DOWN_MASK)!=0,"Win to Mac Control mapping");
            require("[Control]+X".equals(SudokuUtil.getPlatformShortcutText("[Win]+X")),"Win label mapping");
			KeyStroke macStroke = SudokuUtil.getPlatformKeyStroke("shift control P");
			require((macStroke.getModifiers() & (InputEvent.META_MASK | InputEvent.META_DOWN_MASK)) != 0,
					"resource accelerator was not converted to Command");
			require("Undo (Command+Z) [Option]".equals(
					SudokuUtil.getPlatformShortcutText("Undo (Ctrl+Z) [Alt]")),
					"visible shortcut labels were not converted for macOS");
			String macHelp = SudokuUtil.getPlatformShortcutText(readResource("/help/keyboard_zh.html"));
			require(macHelp.contains("<dt>[Command][N]</dt>"),
					"Chinese keyboard help did not show Command on macOS");
			require(macHelp.contains("<dt>[Option][F12]</dt>"),
					"Chinese keyboard help did not show Option on macOS");
			require(macHelp.contains("<dt>[Option][1] ... [Option][9]（macOS）</dt>"),
					"Chinese keyboard help omitted the macOS number-row filter shortcut");
			require(macHelp.contains("[Option][Command][8]"),
					"Chinese keyboard help omitted the macOS Zoom conflict note");
			require(macHelp.contains("<dt>[Option][0] / [Shift][Option][0]（macOS）</dt>"),
					"Chinese keyboard help omitted the macOS XY/XYZ number shortcut");

			System.setProperty("os.name", "Windows 11");
            require(SudokuUtil.getDeletionModifierMask()==InputEvent.META_DOWN_MASK,"Windows deletion modifier");
            require(!SudokuUtil.isMenuShortcutDown(InputEvent.META_DOWN_MASK),"Windows Meta aliases Ctrl");
			require(SudokuUtil.getMenuShortcutMask() == InputEvent.CTRL_MASK,
					"non-macOS menu shortcut must remain Ctrl");
			require(SudokuUtil.isMenuShortcutDown(InputEvent.CTRL_DOWN_MASK),
					"non-macOS Ctrl modifier was not detected");
			require("Undo (Ctrl+Z) [Alt]".equals(
					SudokuUtil.getPlatformShortcutText("Undo (Ctrl+Z) [Alt]")),
					"non-macOS shortcut labels changed unexpectedly");

			Locale.setDefault(Locale.ENGLISH);
			String englishStep = SolutionType.HIDDEN_SINGLE.getStepName();
			String englishCategory = SolutionCategory.SINGLES.getCategoryName();
			Locale.setDefault(Locale.CHINESE);
			SolutionType.resetStepNames();
			SolutionCategory.resetCategoryNames();
			require(!englishStep.equals(SolutionType.HIDDEN_SINGLE.getStepName()),
					"solution technique did not reload after locale change");
			require(!englishCategory.equals(SolutionCategory.SINGLES.getCategoryName()),
					"solution category did not reload after locale change");
			require("文件".equals(ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.dateiMenu.text")),
					"Chinese MainFrame bundle was not selected");

			for (String bundleName : args) {
				ResourceBundle bundle = ResourceBundle.getBundle("intl/" + bundleName, Locale.CHINESE);
				require(!bundle.keySet().isEmpty(), "empty Chinese bundle: " + bundleName);
			}
		} finally {
			Locale.setDefault(originalLocale);
			if (originalOsName == null) {
				System.clearProperty("os.name");
			} else {
				System.setProperty("os.name", originalOsName);
			}
		}
		System.out.println("Locale and shortcut checks passed");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static String readResource(String name) throws Exception {
		try (InputStream input = LocaleShortcutProbe.class.getResourceAsStream(name);
				Scanner scanner = new Scanner(input, "UTF-8")) {
			scanner.useDelimiter("\\A");
			return scanner.hasNext() ? scanner.next() : "";
		}
	}
}
