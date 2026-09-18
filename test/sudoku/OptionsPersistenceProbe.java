/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Checks durable, atomic option persistence and backup recovery. */
public final class OptionsPersistenceProbe {

	private OptionsPersistenceProbe() {
	}

	public static void main(String[] args) throws Exception {
		File directory = Files.createTempDirectory("hodoku-options-probe").toFile();
		String oldDirectory = System.getProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY);
		try {
			System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, directory.getPath());

			Options first = new Options();
			require(!first.isMarkInvalidLinks(), "invalid-link marking is not disabled by default");
			first.setColoringColors(previousDefaultColoringColors());
			first.setLanguage("de");
			first.setAppearanceMode(AppearanceMode.LIGHT.name());
			first.setInitialExtendedState(6);
			first.setInitialScreenDeviceId("display-one");
			first.setOperationSoundsEnabled(true);
			first.setMarkInvalidLinks(true);
			Color customGrid = new Color(18, 52, 86);
			first.setGridColor(customGrid);
			Options.instance = first;
			first.writeOptionsSafely();

			Options second = new Options();
			second.setColoringColors(previousDefaultColoringColors());
			second.setLanguage("zh");
			second.setAppearanceMode(AppearanceMode.DARK.name());
			second.setOperationSoundsEnabled(false);
			second.setMarkInvalidLinks(false);
			second.setGridColor(Color.BLACK);
			second.setDefaultCellColor(Color.WHITE);
			second.setCandidateColor(new Color(100, 100, 100));
			second.setAktCellColor(new Color(255, 255, 150));
			second.initializeAnnotationPalettePreferences(null, null);
			Color candidatePrimary = new Color(11, 37, 83, 177);
			Color cellPrimary = new Color(91, 13, 141, 219);
			Color doodleSecondary = new Color(7, 111, 43, 133);
			second.setAnnotationPrimaryColor(AnnotationPaletteOwner.CANDIDATE_COLORING,
					candidatePrimary);
			second.setAnnotationPrimaryColor(AnnotationPaletteOwner.CELL_COLORING, cellPrimary);
			second.setAnnotationSecondaryColor(AnnotationPaletteOwner.DOODLE, doodleSecondary);
			second.setAnnotationPaletteGroup(AnnotationPaletteOwner.FREE_CHAIN, 5);
			second.setAnnotationPaletteGroup(AnnotationPaletteOwner.BOX_SELECTION, 1);
            int candidateSlot = second.getAnnotationPalettePreferences().getCandidateColoring().getPrimarySlot();
            int cellSlot = second.getAnnotationPalettePreferences().getCellColoring().getPrimarySlot();
            int doodleSlot = second.getAnnotationPalettePreferences().getDoodle().getSecondarySlot();
			Options.instance = second;
			second.writeOptionsSafely();

			Options.instance = null;
			Options.readOptions();
			require("zh".equals(Options.getInstance().getLanguage()), "current configuration was not loaded");
			require(AppearanceMode.DARK.name().equals(Options.getInstance().getAppearanceMode()),
					"appearance was not persisted");
			require(!Options.getInstance().isOperationSoundsEnabled(), "disabled operation sounds were not persisted");
			require(!Options.getInstance().isMarkInvalidLinks(), "disabled invalid-link marking was not persisted");
			require(colorsEqual(Options.getInstance().getColoringColors(), Options.COLORING_COLORS),
					"previous default user palette was not migrated to the distinct palette");
			require(Options.GRID_COLOR.equals(Options.getInstance().getGridColor()),
					"previous default grid color was not migrated");
			require(Options.DEFAULT_CELL_COLOR.equals(Options.getInstance().getDefaultCellColor()),
					"previous default cell color was not migrated");
			require(Options.CANDIDATE_COLOR.equals(Options.getInstance().getCandidateColor()),
					"previous default candidate color was not migrated");
			require(Options.AKT_CELL_COLOR.equals(Options.getInstance().getAktCellColor()),
					"previous default selection color was not migrated");
			require(Options.getInstance().isAnnotationPalettePreferencesInitialized(),
					"tool-local annotation palettes were not persisted as Options");
			require(Options.getInstance().getColoringColors()[candidateSlot].equals(Options.getInstance().getAnnotationPrimaryColor(
					AnnotationPaletteOwner.CANDIDATE_COLORING)),
					"Candidate paired palette state did not round-trip");
			require(Options.getInstance().getColoringColors()[cellSlot].equals(Options.getInstance().getAnnotationPrimaryColor(
					AnnotationPaletteOwner.CELL_COLORING)),
					"Cell palette state leaked into Candidate coloring");
			require(Options.getInstance().getColoringColors()[doodleSlot].equals(Options.getInstance().getAnnotationSecondaryColor(
					AnnotationPaletteOwner.DOODLE)),
					"Doodle paired Secondary state did not round-trip");
			require(Options.getInstance().getAnnotationPaletteGroup(AnnotationPaletteOwner.FREE_CHAIN) == 5
					&& Options.getInstance().getAnnotationPaletteGroup(AnnotationPaletteOwner.BOX_SELECTION) == 1,
					"Chain/Box palette groups did not persist independently");

			Options legacy = new Options();
			require(!legacy.isAnnotationPalettePreferencesInitialized(),
					"new palette preferences cannot distinguish an old configuration");
			Color legacyPrimary = new Color(19, 61, 103, 201);
			Color legacySecondary = new Color(151, 43, 89, 181);
			legacy.initializeAnnotationPalettePreferences(legacyPrimary, legacySecondary);
            Color migratedPrimary = legacy.getAnnotationPrimaryColor(AnnotationPaletteOwner.CANDIDATE_COLORING);
            int migratedSlot = legacy.getAnnotationPalettePreferences().getCandidateColoring().getPrimarySlot();
            require(legacy.getColoringColors()[migratedSlot ^ 1].equals(
                    legacy.getAnnotationSecondaryColor(AnnotationPaletteOwner.DOODLE)),
                    "legacy secondary was not normalized to primary partner");
            legacy.initializeAnnotationPalettePreferences(Color.YELLOW, Color.CYAN);
            require(migratedPrimary.equals(legacy.getAnnotationPrimaryColor(AnnotationPaletteOwner.CANDIDATE_COLORING)),
                    "a later legacy session overwrote initialized palette");

			try (FileOutputStream output = new FileOutputStream(ApplicationPaths.getConfigurationFile())) {
				output.write("broken".getBytes("UTF-8"));
			}
			Logger optionsLogger = Logger.getLogger(Options.class.getName());
			Level previousLevel = optionsLogger.getLevel();
			try {
				optionsLogger.setLevel(Level.OFF);
				Options.instance = null;
				Options.readOptions();
			} finally {
				optionsLogger.setLevel(previousLevel);
			}
			require("de".equals(Options.getInstance().getLanguage()), "valid backup was not recovered");
			require(Options.getInstance().getInitialExtendedState() == 6, "window state backup was not recovered");
			require("display-one".equals(Options.getInstance().getInitialScreenDeviceId()),
					"display assignment backup was not recovered");
			require(Options.getInstance().isOperationSoundsEnabled(),
					"enabled operation sounds were not recovered from backup");
			require(Options.getInstance().isMarkInvalidLinks(),
					"enabled invalid-link marking was not recovered from backup");
			require(customGrid.equals(Options.getInstance().getGridColor()),
					"customized grid color was overwritten during migration");
		} finally {
			Options.instance = null;
			if (oldDirectory == null) {
				System.clearProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY);
			} else {
				System.setProperty(ApplicationPaths.DATA_DIRECTORY_PROPERTY, oldDirectory);
			}
		}

		System.out.println("Option persistence checks passed");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static Color[] previousDefaultColoringColors() {
		return new Color[] {
			new Color(255, 174, 91), new Color(255, 209, 173),
			new Color(177, 165, 243), new Color(220, 212, 252),
			new Color(247, 165, 167), new Color(255, 210, 210),
			new Color(134, 232, 208), new Color(206, 251, 237),
			new Color(134, 242, 128), new Color(215, 255, 215),
			new Color(51, 204, 255), new Color(184, 227, 255)
		};
	}

	private static boolean colorsEqual(Color[] first, Color[] second) {
		if (first == null || second == null || first.length != second.length) {
			return false;
		}
		for (int i = 0; i < first.length; i++) {
			if (!first[i].equals(second[i])) {
				return false;
			}
		}
		return true;
	}
}
