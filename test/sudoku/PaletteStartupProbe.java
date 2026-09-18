/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.SwingUtilities;

/** Verifies that persisted tool-local colors are projected into the shared palette at startup. */
public final class PaletteStartupProbe {

	private static final String PUZZLE =
			"530070000600195000098000060800060003400803001700020006060000280000419005000080079";

	private PaletteStartupProbe() {
	}

	public static void main(String[] args) throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					File dataDirectory = Files.createTempDirectory("hodoku-palette-startup").toFile();
					System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, dataDirectory.getPath());

					Color coldStart = new Color(23, 61, 149, 211);
					Options.instance = optionsWithCandidatePrimary(coldStart);
					frame = new MainFrame(null);
					require(coldStart.equals(visiblePrimary(frame)),
							"no-session startup left the shared palette on its constructor default");
					frame.dispose();
					frame = null;

					Color explicitPuzzle = new Color(177, 43, 91, 199);
					Options.instance.setAnnotationPrimaryColor(
							AnnotationPaletteOwner.CANDIDATE_COLORING, explicitPuzzle);
					File puzzleFile = new File(dataDirectory, "explicit.txt");
					Files.write(puzzleFile.toPath(), PUZZLE.getBytes(StandardCharsets.UTF_8));
					frame = new MainFrame(puzzleFile.getPath());
					require(explicitPuzzle.equals(visiblePrimary(frame)),
							"explicit puzzle startup did not project the persisted Candidate palette");
					frame.dispose();
					frame = null;

					Color explicitConfig = new Color(37, 143, 79, 187);
					File configFile = new File(dataDirectory, "explicit.hcfg");
					optionsWithCandidatePrimary(explicitConfig).writeOptions(configFile.getPath());
					frame = new MainFrame(configFile.getPath());
					require(explicitConfig.equals(visiblePrimary(frame)),
							"explicit configuration startup did not project its Candidate palette");

					Options legacy = new Options();
					require(!legacy.isAnnotationPalettePreferencesInitialized(),
							"legacy configuration fixture unexpectedly had initialized palettes");
					File legacyConfig = new File(dataDirectory, "legacy.hcfg");
					legacy.writeOptions(legacyConfig.getPath());
					invokeLoadConfiguration(frame, legacyConfig);
					require(Options.getInstance().isAnnotationPalettePreferencesInitialized(),
							"runtime configuration load did not initialize legacy palette preferences");
					require(Options.DEFAULT_PRIMARY_COLOR.equals(visiblePrimary(frame)),
							"runtime configuration load did not re-project the active owner into the shared palette");
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
			throw new AssertionError("annotation palette startup check failed", failure[0]);
		}
		System.out.println("Annotation palette startup checks passed");
		System.exit(0);
	}

	private static Options optionsWithCandidatePrimary(Color primary) {
		Options options = new Options();
		options.initializeAnnotationPalettePreferences(null, null);
		options.setAnnotationPrimaryColor(AnnotationPaletteOwner.CANDIDATE_COLORING, primary);
		return options;
	}

	private static Color visiblePrimary(MainFrame frame) throws Exception {
		CellZoomPanel zoom = frame.getSudokuPanel().getCellZoomPanel();
		Field field = CellZoomPanel.class.getDeclaredField("colorPalette");
		field.setAccessible(true);
		return ((UIColorPalette) field.get(zoom)).getPrimaryColor();
	}

	private static void invokeLoadConfiguration(MainFrame frame, File file) throws Exception {
		Method method = MainFrame.class.getDeclaredMethod("loadFromFile", String.class, int.class);
		method.setAccessible(true);
		method.invoke(frame, file.getPath(), Integer.valueOf(0));
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
