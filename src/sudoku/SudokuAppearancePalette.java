/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;

/** Display-time colors for the on-screen Sudoku and related main-window views. */
public final class SudokuAppearancePalette {

	private static final Color LIGHT_WINDOW_BACKGROUND = new Color(242, 244, 247);
	private static final Color LIGHT_SURFACE_BACKGROUND = Color.WHITE;
	private static final Color LIGHT_CONTROL_BACKGROUND = new Color(244, 246, 249);
	private static final Color LIGHT_CONTROL_SELECTION_BACKGROUND = new Color(91, 184, 255);
	private static final Color LIGHT_CONTROL_SELECTION_BORDER = new Color(16, 132, 224);
	private static final Color LIGHT_CONTROL_SELECTION_FOREGROUND = new Color(17, 38, 60);
	private static final Color LIGHT_CONTROL_HOVER_BACKGROUND = new Color(218, 240, 255);
	private static final Color LIGHT_CONTROL_HOVER_BORDER = new Color(113, 190, 247);
	private static final Color LIGHT_ACTIVE_CELL = new Color(245, 158, 11);
	private static final Color LIGHT_ACTIVE_CELL_BORDER = new Color(151, 78, 0);
	private static final Color LIGHT_CANDIDATE = new Color(67, 80, 100);
	private static final Color WINDOW_BACKGROUND = new Color(23, 26, 31);
	private static final Color SURFACE_BACKGROUND = new Color(29, 33, 39);
	private static final Color CONTROL_BACKGROUND = new Color(39, 45, 53);
	private static final Color CONTROL_SELECTION_BACKGROUND = new Color(48, 98, 148);
	private static final Color CONTROL_SELECTION_BORDER = new Color(112, 187, 255);
	private static final Color CONTROL_SELECTION_FOREGROUND = new Color(245, 248, 252);
	private static final Color CONTROL_HOVER_BACKGROUND = new Color(48, 57, 68);
	private static final Color CONTROL_HOVER_BORDER = new Color(86, 106, 128);
	private static final Color DEFAULT_CELL = new Color(34, 39, 46);
	private static final Color ALTERNATE_CELL = new Color(39, 45, 53);
	private static final Color FIXED_VALUE = new Color(242, 240, 234);
	private static final Color USER_VALUE = new Color(119, 183, 255);
	private static final Color CANDIDATE = new Color(182, 189, 200);
	private static final Color INNER_GRID = new Color(82, 91, 104);
	private static final Color GRID = new Color(154, 165, 180);
	private static final Color ACTIVE_CELL = new Color(255, 224, 102);
	private static final Color POSSIBLE_CELL = new Color(31, 74, 63);
	private static final Color FILTER_MARKER_DARK_GREEN = new Color(8, 122, 98);
	private static final Color FILTER_MARKER_LIGHT_GREEN = new Color(118, 224, 192);
	private static final Color BIVALUE_LIGHT = new Color(253, 228, 198);
	private static final Color BIVALUE_DARK = new Color(85, 53, 22);
	private static final Color TRIVALUE_LIGHT = new Color(232, 226, 255);
	private static final Color TRIVALUE_DARK = new Color(54, 45, 92);
	private static final Color POSSIBLE_FIXED_CELL = new Color(43, 91, 76);
	private static final Color INVALID_CELL = new Color(90, 37, 48);
	private static final Color WRONG_VALUE = new Color(255, 156, 173);
	private static final Color DEVIATION = new Color(255, 176, 188);
	private static final Color HINT_BACKGROUND = new Color(35, 82, 58);
	private static final Color DELETE_BACKGROUND = new Color(91, 41, 51);
	private static final Color CANNIBALISTIC_BACKGROUND = new Color(107, 45, 57);
	private static final Color FIN_BACKGROUND = new Color(37, 63, 90);
	private static final Color ENDO_FIN_BACKGROUND = new Color(63, 49, 88);
	private static final Color HINT_FOREGROUND = new Color(242, 240, 233);
	private static final Color DARK_INK = new Color(23, 32, 42);
	private static final Color HINT_INK = new Color(145, 226, 175);
	private static final Color DELETE_INK = new Color(255, 156, 172);
	private static final Color CANNIBALISTIC_INK = new Color(255, 177, 189);
	private static final Color FIN_INK = new Color(139, 199, 255);
	private static final Color ENDO_FIN_INK = new Color(199, 175, 255);
	private static final Color ARROW = new Color(255, 113, 131);
	private static final Color LIGHT_HINT_BACKGROUND = new Color(63, 218, 101);
	private static final Color LIGHT_DELETE_BACKGROUND = new Color(255, 118, 132);
	private static final Color LIGHT_CANNIBALISTIC_BACKGROUND = new Color(235, 0, 0);
	private static final Color LIGHT_FIN_BACKGROUND = new Color(127, 187, 255);
	private static final Color LIGHT_ENDO_FIN_BACKGROUND = new Color(216, 178, 255);
	private static final Color LIGHT_ARROW = Color.RED;
	private static final Color HOVER_MISSING = new Color(182, 189, 200, 160);
	private static final Color HOVER_BACKGROUND = new Color(121, 175, 255, 60);
	private static final Color HOVER_BORDER = new Color(121, 175, 255, 170);
	private static final Color REFERENCE_LIGHT = new Color(0, 124, 145);
	private static final Color REFERENCE_DARK = new Color(98, 211, 232);
	private static final Color USER_CHAIN_STRONG_LIGHT = new Color(10, 125, 79);
	private static final Color USER_CHAIN_STRONG_DARK = new Color(93, 222, 160);
	private static final Color USER_CHAIN_WEAK_LIGHT = new Color(40, 100, 200);
	private static final Color USER_CHAIN_WEAK_DARK = new Color(121, 175, 255);
	private static final Color USER_CHAIN_INVALID_LIGHT = new Color(207, 48, 74);
	private static final Color USER_CHAIN_INVALID_DARK = new Color(255, 113, 131);
	private static final Color[] ALS_BACKGROUNDS = {
		new Color(49, 69, 43), new Color(74, 48, 55),
		new Color(41, 67, 71), new Color(74, 60, 36)
	};
	private static final Color[] LIGHT_ALS_BACKGROUNDS = {
		new Color(197, 232, 140), new Color(255, 203, 203),
		new Color(178, 223, 223), new Color(252, 220, 165)
	};
	private static final Color[] ALS_FOREGROUNDS = {
		new Color(184, 228, 169), new Color(255, 177, 189),
		new Color(159, 226, 224), new Color(255, 196, 138)
	};
	private static final Color[] DIFFICULTY_BACKGROUNDS = {
		new Color(43, 46, 51), new Color(42, 46, 50), new Color(41, 67, 53),
		new Color(74, 70, 40), new Color(90, 59, 40), new Color(91, 48, 53)
	};

	private final boolean dark;
	private final boolean screen;
	private final Options options;

	private SudokuAppearancePalette(boolean dark, boolean screen) {
		this.dark = dark;
		this.screen = screen;
		this.options = Options.getInstance();
	}

	public static SudokuAppearancePalette forRendering(boolean printOrExport) {
		boolean screen = !printOrExport;
		return new SudokuAppearancePalette(screen && ApplicationAppearance.isDark(), screen);
	}

	public boolean isDark() {
		return dark;
	}

	public Color getWindowBackground() {
		return dark ? WINDOW_BACKGROUND : LIGHT_WINDOW_BACKGROUND;
	}

	public Color getSurfaceBackground() {
		return dark ? SURFACE_BACKGROUND : LIGHT_SURFACE_BACKGROUND;
	}

	public Color getPrimaryForeground() {
		return dark ? FIXED_VALUE : options.getCellFixedValueColor();
	}

	public Color getControlBackground() {
		return dark ? CONTROL_BACKGROUND : LIGHT_CONTROL_BACKGROUND;
	}

	public Color getControlSelectionBackground() {
		return dark ? CONTROL_SELECTION_BACKGROUND : LIGHT_CONTROL_SELECTION_BACKGROUND;
	}

	public Color getControlSelectionBorder() {
		return dark ? CONTROL_SELECTION_BORDER : LIGHT_CONTROL_SELECTION_BORDER;
	}

	public Color getControlSelectionForeground() {
		return dark ? CONTROL_SELECTION_FOREGROUND : LIGHT_CONTROL_SELECTION_FOREGROUND;
	}

	public Color getControlHoverBackground() {
		return dark ? CONTROL_HOVER_BACKGROUND : LIGHT_CONTROL_HOVER_BACKGROUND;
	}

	public Color getControlHoverBorder() {
		return dark ? CONTROL_HOVER_BORDER : LIGHT_CONTROL_HOVER_BORDER;
	}

	public Color getDefaultCellColor() {
		return map(options.getDefaultCellColor(), Options.DEFAULT_CELL_COLOR, DEFAULT_CELL);
	}

	public Color getAlternateCellColor() {
		return map(options.getAlternateCellColor(), Options.ALTERNATE_CELL_COLOR, ALTERNATE_CELL);
	}

	public Color getFixedValueColor() {
		return map(options.getCellFixedValueColor(), Options.CELL_FIXED_VALUE_COLOR, FIXED_VALUE);
	}

	public Color getUserValueColor() {
		return map(options.getCellValueColor(), Options.CELL_VALUE_COLOR, USER_VALUE);
	}

	public Color getCandidateColor() {
		return mapScreen(options.getCandidateColor(), Options.CANDIDATE_COLOR,
				LIGHT_CANDIDATE, CANDIDATE);
	}

	public Color getInnerGridColor() {
		return map(options.getInnerGridColor(), Options.INNER_GRID_COLOR, INNER_GRID);
	}

	public Color getGridColor() {
		return map(options.getGridColor(), Options.GRID_COLOR, GRID);
	}

	public Color getActiveCellColor() {
		return mapScreen(options.getAktCellColor(), Options.AKT_CELL_COLOR,
				LIGHT_ACTIVE_CELL, ACTIVE_CELL);
	}

	/** A contrast-bearing outer edge for the brighter default Light selection frame. */
	public Color getActiveCellBorderColor() {
		Color actual = options.getAktCellColor();
		if (screen && !dark && actual != null && actual.equals(Options.AKT_CELL_COLOR)) {
			return LIGHT_ACTIVE_CELL_BORDER;
		}
		return getActiveCellColor();
	}

	public Color getPossibleCellColor() {
		return map(options.getPossibleCellColor(), Options.FILTER_COLOR, POSSIBLE_CELL);
	}

	/**
	 * Returns a visible candidate-filter marker for a user-colored cell without
	 * changing the configured filter or user coloring colors.
	 */
	public Color getFilterMarkerColor(Color background) {
		Color preferred = getPossibleCellColor();
		if (background == null || contrastRatio(background, preferred) >= 3.0) {
			return preferred;
		}
		return contrastRatio(background, FILTER_MARKER_DARK_GREEN)
				>= contrastRatio(background, FILTER_MARKER_LIGHT_GREEN)
						? FILTER_MARKER_DARK_GREEN : FILTER_MARKER_LIGHT_GREEN;
	}

	public Color getBivalueFilterColor() {
		return dark ? BIVALUE_DARK : BIVALUE_LIGHT;
	}

	public Color getTrivalueFilterColor() {
		return dark ? TRIVALUE_DARK : TRIVALUE_LIGHT;
	}

	public Color getPossibleFixedCellColor() {
		return map(options.getPossibleFixedCellColor(), Options.FILTER_GIVEN_CELL_COLOR,
				POSSIBLE_FIXED_CELL);
	}

	public Color getInvalidCellColor() {
		return map(options.getInvalidCellColor(), Options.INVERSE_FILTER_COLOR, INVALID_CELL);
	}

	public Color getWrongValueColor() {
		return map(options.getWrongValueColor(), Options.WRONG_VALUE_COLOR, WRONG_VALUE);
	}

	public Color getDeviationColor() {
		return map(options.getDeviationColor(), Options.DEVIATION_COLOR, DEVIATION);
	}

	public Color getHintBackgroundColor() {
		return mapScreen(options.getHintCandidateBackColor(), Options.HINT_CANDIDATE_BACK_COLOR,
				LIGHT_HINT_BACKGROUND, HINT_BACKGROUND);
	}

	public Color getHintDeleteBackgroundColor() {
		return mapScreen(options.getHintCandidateDeleteBackColor(),
				Options.HINT_CANDIDATE_DELETE_BACK_COLOR, LIGHT_DELETE_BACKGROUND, DELETE_BACKGROUND);
	}

	public Color getHintCannibalisticBackgroundColor() {
		return mapScreen(options.getHintCandidateCannibalisticBackColor(),
				Options.HINT_CANDIDATE_CANNIBALISTIC_BACK_COLOR,
				LIGHT_CANNIBALISTIC_BACKGROUND, CANNIBALISTIC_BACKGROUND);
	}

	public Color getHintFinBackgroundColor() {
		return mapScreen(options.getHintCandidateFinBackColor(), Options.HINT_CANDIDATE_FIN_BACK_COLOR,
				LIGHT_FIN_BACKGROUND, FIN_BACKGROUND);
	}

	public Color getHintEndoFinBackgroundColor() {
		return mapScreen(options.getHintCandidateEndoFinBackColor(),
				Options.HINT_CANDIDATE_ENDO_FIN_BACK_COLOR,
				LIGHT_ENDO_FIN_BACKGROUND, ENDO_FIN_BACKGROUND);
	}

	public Color getHintForegroundColor(Color actual, Color factoryDefault) {
		if (!screen || actual == null || !actual.equals(factoryDefault)) {
			return actual;
		}
		if (!dark) return Color.BLACK;
		if (factoryDefault.equals(Options.HINT_CANDIDATE_COLOR)) {
			return HINT_INK;
		}
		if (factoryDefault.equals(Options.HINT_CANDIDATE_DELETE_COLOR)) {
			return DELETE_INK;
		}
		if (factoryDefault.equals(Options.HINT_CANDIDATE_CANNIBALISTIC_COLOR)) {
			return CANNIBALISTIC_INK;
		}
		if (factoryDefault.equals(Options.HINT_CANDIDATE_FIN_COLOR)) {
			return FIN_INK;
		}
		if (factoryDefault.equals(Options.HINT_CANDIDATE_ENDO_FIN_COLOR)) {
			return ENDO_FIN_INK;
		}
		for (int i = 0; i < Options.HINT_CANDIDATE_ALS_COLORS.length; i++) {
			if (factoryDefault.equals(Options.HINT_CANDIDATE_ALS_COLORS[i])) {
				return ALS_FOREGROUNDS[i];
			}
		}
		return HINT_FOREGROUND;
	}

	public Color getAlsBackgroundColor(int index, Color actual) {
		Color factoryDefault = Options.HINT_CANDIDATE_ALS_BACK_COLORS[
				index % Options.HINT_CANDIDATE_ALS_BACK_COLORS.length];
		return mapScreen(actual, factoryDefault,
				LIGHT_ALS_BACKGROUNDS[index % LIGHT_ALS_BACKGROUNDS.length],
				ALS_BACKGROUNDS[index % ALS_BACKGROUNDS.length]);
	}

	public Color getArrowColor() {
		return mapScreen(options.getArrowColor(), Options.ARROW_COLOR, LIGHT_ARROW, ARROW);
	}

	public Color getHoverMissingCandidateColor() {
		return dark ? HOVER_MISSING : new Color(95, 107, 122, 77);
	}

	public Color getHoverBackgroundColor() {
		return dark ? HOVER_BACKGROUND : new Color(23, 92, 211, 26);
	}

	public Color getHoverBorderColor() {
		return dark ? HOVER_BORDER : new Color(23, 92, 211, 64);
	}

	public Color getReferenceHighlightColor() {
		return dark ? REFERENCE_DARK : REFERENCE_LIGHT;
	}

	/** Semantic relation colors stay distinct from user-selected node colors. */
	public Color getUserChainLinkColor(boolean strong) {
		Color preferred = strong
				? (dark ? USER_CHAIN_STRONG_DARK : USER_CHAIN_STRONG_LIGHT)
				: (dark ? USER_CHAIN_WEAK_DARK : USER_CHAIN_WEAK_LIGHT);
		return ensureContrast(preferred, getDefaultCellColor(), 3.0);
	}

	public Color getUserChainInvalidColor() {
		return ensureContrast(dark ? USER_CHAIN_INVALID_DARK : USER_CHAIN_INVALID_LIGHT,
				getDefaultCellColor(), 3.0);
	}

	/** Preserve the chosen hue while keeping a node ring visible on its actual cell. */
	public Color getUserChainNodeColor(Color requested, Color background) {
		return ensureContrast(requested, background, 3.0);
	}

	/** Preserve a Box group hue while keeping its boundary visible on the current cell. */
	public Color getReasoningBoundaryColor(Color requested, Color background) {
		return ensureContrast(requested, background, 3.0);
	}

	public Color getReferenceHighlightBackground(Color background) {
		Color accent = getReferenceHighlightColor();
		double alpha = dark ? 0.22 : 0.15;
		return new Color(
				blend(background.getRed(), accent.getRed(), alpha),
				blend(background.getGreen(), accent.getGreen(), alpha),
				blend(background.getBlue(), accent.getBlue(), alpha));
	}

	public Color getUnitHandleColor(boolean hover) {
		if (hover) {
			return getReferenceHighlightColor();
		}
		Color foreground = getPrimaryForeground();
		return new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), dark ? 150 : 125);
	}

	public Color getDifficultyBackground(int ordinal, Color actual) {
		if (!dark || ordinal < 0 || ordinal >= Options.DEFAULT_DIFFICULTY_LEVELS.length
				|| !actual.equals(Options.DEFAULT_DIFFICULTY_LEVELS[ordinal].getBackgroundColor())) {
			return actual;
		}
		return DIFFICULTY_BACKGROUNDS[ordinal];
	}

	public Color getDifficultyForeground(int ordinal, Color actual) {
		if (!dark || ordinal < 0 || ordinal >= Options.DEFAULT_DIFFICULTY_LEVELS.length
				|| !actual.equals(Options.DEFAULT_DIFFICULTY_LEVELS[ordinal].getForegroundColor())) {
			return actual;
		}
		return HINT_FOREGROUND;
	}

	/**
	 * Keeps candidate labels legible when a user color or a hint background is
	 * drawn behind them in either appearance.
	 */
	public Color getReadableForeground(Color background, Color preferred) {
		if (background == null || preferred == null || contrastRatio(background, preferred) >= 4.5) {
			return preferred;
		}
		return contrastRatio(background, DARK_INK) >= contrastRatio(background, HINT_FOREGROUND)
				? DARK_INK : HINT_FOREGROUND;
	}

	private Color map(Color actual, Color factoryDefault, Color darkColor) {
		return dark && actual != null && actual.equals(factoryDefault) ? darkColor : actual;
	}

	private Color mapScreen(Color actual, Color factoryDefault, Color lightColor, Color darkColor) {
		if (!screen || actual == null || !actual.equals(factoryDefault)) return actual;
		return dark ? darkColor : lightColor;
	}

	private Color ensureContrast(Color foreground, Color background, double minimum) {
		if (foreground == null || background == null || contrastRatio(foreground, background) >= minimum) {
			return foreground;
		}
		Color target = contrastRatio(background, DARK_INK) >= contrastRatio(background, HINT_FOREGROUND)
				? DARK_INK : HINT_FOREGROUND;
		for (int step = 1; step <= 10; step++) {
			double amount = step / 10.0;
			Color adjusted = new Color(
					blend(foreground.getRed(), target.getRed(), amount),
					blend(foreground.getGreen(), target.getGreen(), amount),
					blend(foreground.getBlue(), target.getBlue(), amount));
			if (contrastRatio(adjusted, background) >= minimum) {
				return adjusted;
			}
		}
		return target;
	}

	private static double contrastRatio(Color first, Color second) {
		double lighter = Math.max(relativeLuminance(first), relativeLuminance(second));
		double darker = Math.min(relativeLuminance(first), relativeLuminance(second));
		return (lighter + 0.05) / (darker + 0.05);
	}

	private static double relativeLuminance(Color color) {
		return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen()) + 0.0722 * linear(color.getBlue());
	}

	private static double linear(int component) {
		double value = component / 255.0;
		return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	private static int blend(int background, int foreground, double alpha) {
		return (int) Math.round(background * (1.0 - alpha) + foreground * alpha);
	}
}
