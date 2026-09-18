/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

/** Focused checks for GUI file-open detection at the real Main entry boundary. */
public final class MainLaunchArgumentsProbe {

	private MainLaunchArgumentsProbe() {
	}

	public static void main(String[] args) {
		require("puzzle.txt".equals(Main.findGuiLaunchFile(new String[] { "puzzle.txt" }, false)),
				"standalone text file was not routed to the GUI");
		require("PUZZLE.HSOL".equals(Main.findGuiLaunchFile(new String[] { "PUZZLE.HSOL" }, false)),
				"standalone solution file matching was not case-insensitive");
		require("settings.hcfg".equals(Main.findGuiLaunchFile(
				new String[] { "/launch4j", "settings.hcfg" }, false)),
				"launcher file-open request was not routed to the GUI");
		require("puzzle.txt".equals(Main.findGuiLaunchFile(
				new String[] { "/gui", "puzzle.txt" }, true)),
				"explicit GUI text file was not detected");
		require(Main.findGuiLaunchFile(new String[] { "/bs", "library.txt" }, false) == null,
				"CLI input file was incorrectly consumed as a GUI launch file");
		require(Main.findGuiLaunchFile(new String[] { "/gui" }, true) == null,
				"GUI launch without a file invented a launch path");
		require(Main.findGuiLaunchFile(new String[] {
				"530070000600195000098000060800060003400803001700020006060000280000419005000080079"
		}, false) == null, "console puzzle was incorrectly treated as a GUI file");
		System.out.println("Main launch argument checks passed");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
