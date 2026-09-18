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
import java.lang.reflect.Field;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

/** Verifies the screen-only boundary of the macOS Sudoku appearance. */
public final class AppearanceRenderingProbe {
	private static final String FILTER_PUZZLE =
			"530070000600195000098000060800060003400803001700020006060000280000419005000080079";

	private AppearanceRenderingProbe() {
	}

	public static void main(String[] args) throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			@SuppressWarnings({ "rawtypes", "unchecked" })
			public void run() {
				MainFrame frame = null;
				MainFrame lightFrame = null;
				try {
					ApplicationAppearance.initialize(AppearanceMode.LIGHT);
					SudokuAppearancePalette light = SudokuAppearancePalette.forRendering(false);
					requireUserPaletteSeparatedFromSystem(light);
					requireUserChainColors(light);
					requireOriginalHintSemantics(light);
					requirePrintHintPaletteUnchanged();
					require(contrastRatio(light.getCandidateColor(), light.getDefaultCellColor()) >= 7.0,
							"light candidates remain too faint on the default cell background");
					require(contrastRatio(light.getCandidateColor(), light.getAlternateCellColor()) >= 7.0,
							"light candidates remain too faint on the alternate cell background");
					require(contrastRatio(light.getCandidateColor(), light.getPossibleCellColor()) >= 7.0,
							"light candidates remain too faint on the candidate-filter background");
					require(contrastRatio(light.getActiveCellBorderColor(),
							light.getDefaultCellColor()) >= 3.0,
							"light selected-cell outer edge does not remain visible on the board");
					require(relativeLuminance(light.getActiveCellColor()) > 0.30,
							"light selected-cell accent retained the previous heavy brown appearance");
					require(colorRange(light.getActiveCellColor()) > 190,
							"light selected-cell accent is not sufficiently saturated");
					Color lightControlSelection = light.getControlSelectionBackground();
					require(relativeLuminance(lightControlSelection) > 0.40,
							"light tool selection is still too dark");
					require(colorRange(lightControlSelection) > 140,
							"light tool selection is not saturated enough");
					require(contrastRatio(lightControlSelection,
							light.getControlSelectionForeground()) >= 4.5,
							"light tool selection foreground lacks readable contrast");
					lightFrame = new MainFrame(null);
					JToggleButton lightFirstFilter = readFirstFilterButton(lightFrame);
					require(lightFirstFilter.getIcon() instanceof CandidateFilterIcon,
							"light candidate filter retained the glossy bitmap icon");
					require(lightFirstFilter.getUI() instanceof FlatToolButtonUI,
							"light candidate filter retained the native glossy selected state");
					lightFirstFilter.setSize(38, 38);
					lightFirstFilter.setSelected(true);
					BufferedImage selectedFilter = new BufferedImage(38, 38,
							BufferedImage.TYPE_INT_ARGB);
					Graphics2D selectedGraphics = selectedFilter.createGraphics();
					lightFirstFilter.paint(selectedGraphics);
					selectedGraphics.dispose();
					require(containsColor(selectedFilter, 0, 0, 38, 38, lightControlSelection),
							"light candidate filter did not paint the brighter semantic selection");

					CellZoomPanel lightCellZoom = lightFrame.getSudokuPanel().getCellZoomPanel();
					lightCellZoom.setSize(900, 760);
					lightCellZoom.calculateLayout();
					JPanel valueGrid = readField(lightCellZoom, "setValuePanel", JPanel.class);
					JPanel toolSelector = readField(lightCellZoom, "radioButtonPanel", JPanel.class);
					JRadioButton defaultTool = readField(lightCellZoom,
							"radioButtonDefault", JRadioButton.class);
					require(valueGrid.getWidth() <= 132,
							"right-side number grid still expands beyond the compact size");
					require(toolSelector.getHeight() == 34,
							"right-side tool selector retained its oversized height");
					require(defaultTool.getIcon().getIconWidth() == 26,
							"right-side annotation glyph retained its oversized icon");
					require(defaultTool.getUI() instanceof FlatToolButtonUI,
							"right-side annotation tool retained the native glossy selected state");
					JToggleButton lightXyFilter = readField(lightFrame, "fxyToggleButton", JToggleButton.class);
					JToggleButton lightXyzFilter = readField(lightFrame, "fxyzToggleButton", JToggleButton.class);
					require(iconOpaqueLuminance(lightXyFilter.getIcon()) < 0.30,
							"light x/y candidate filter icon is not dark enough");
					require(iconOpaqueLuminance(lightXyzFilter.getIcon()) < 0.30,
							"light x/y/z candidate filter icon is not dark enough");
					SudokuPanel lightPanel = lightFrame.getSudokuPanel();
					lightPanel.setSize(900, 900);
					lightPanel.setSudoku(FILTER_PUZZLE);
					lightPanel.clearSelection(0);
					BufferedImage lightBoard = new BufferedImage(900, 900,
							BufferedImage.TYPE_INT_RGB);
					Graphics2D lightBoardGraphics = lightBoard.createGraphics();
					lightPanel.paint(lightBoardGraphics);
					lightBoardGraphics.dispose();
					int lightCellWidth = lightPanel.getX(0, 1) - lightPanel.getX(0, 0);
					require(containsColor(lightBoard, lightPanel.getX(0, 0), lightPanel.getY(0, 0),
							lightCellWidth, lightCellWidth, light.getActiveCellColor()),
							"light board did not paint the brighter selection accent");
					require(containsColor(lightBoard, lightPanel.getX(0, 0), lightPanel.getY(0, 0),
							lightCellWidth, lightCellWidth, light.getActiveCellBorderColor()),
							"light board selection lost its one-pixel contrast edge");
					lightFrame.dispose();
					lightFrame = null;

					ApplicationAppearance.initialize(AppearanceMode.DARK);
					SudokuAppearancePalette dark = SudokuAppearancePalette.forRendering(false);
					requireUserChainColors(dark);
					requireDarkHintRolesRemainDistinct(dark);
					require(dark.isDark(), "explicit Dark appearance did not enable the screen palette");
					require(dark.getDefaultCellColor().equals(new Color(34, 39, 46)),
							"dark board background differs from the accepted graphite palette");

					Color originalDefault = Options.getInstance().getDefaultCellColor();
					Color customDefault = new Color(12, 34, 56);
					Options.getInstance().setDefaultCellColor(customDefault);
					require(SudokuAppearancePalette.forRendering(false).getDefaultCellColor().equals(customDefault),
							"dark appearance replaced a customized cell color");
					Options.getInstance().setDefaultCellColor(originalDefault);
					Color coloredCandidateForeground = dark.getReadableForeground(
							new Color(170, 160, 230), dark.getCandidateColor());
					require(contrastRatio(coloredCandidateForeground, new Color(170, 160, 230)) >= 4.5,
							"candidate text is not readable on a colored cell background");

					frame = new MainFrame(null);
					require(frame.getContentPane().getBackground().equals(dark.getWindowBackground()),
							"main work window retained a light background");
					JTextArea hintArea = findComponent(frame.getContentPane(), JTextArea.class);
					require(hintArea.getBackground().equals(dark.getSurfaceBackground()),
							"hint area retained a light background");
					Container statusLine = readField(frame, "statusLinePanel", Container.class);
					require(statusLine.getBackground().equals(dark.getSurfaceBackground()),
							"status line retained its bright selection background");
					JLabel statusLevel = readField(frame, "statusLabelLevel", JLabel.class);
					require(statusLevel.getForeground().equals(dark.getPrimaryForeground()),
							"status line text did not use the dark foreground");
					Container hintPanel = readField(frame, "hintPanel", Container.class);
					require(hintPanel.getParent() != null && hintPanel instanceof javax.swing.JPanel,
							"hint panel was not initialized");
					TitledBorder hintBorder = (TitledBorder) ((javax.swing.JPanel) hintPanel).getBorder();
					require(hintBorder.getTitleColor().equals(dark.getPrimaryForeground()),
							"hint border title remained dark on a dark surface");
					JToggleButton firstFilter = readFirstFilterButton(frame);
					require(iconOpaqueLuminance(firstFilter.getIcon()) > 0.55,
							"candidate filter toolbar icon remained too dark");
					JToggleButton xyFilter = readField(frame, "fxyToggleButton", JToggleButton.class);
					require(iconOpaqueLuminance(xyFilter.getIcon()) > 0.55,
							"x/y candidate filter toolbar icon remained too dark");
					JToggleButton xyzFilter = readField(frame, "fxyzToggleButton", JToggleButton.class);
					require(iconOpaqueLuminance(xyzFilter.getIcon()) > 0.55,
							"x/y/z candidate filter toolbar icon remained too dark");
					JButton newGame = readField(frame, "newGameToolButton", JButton.class);
					require(iconOpaquePixelCount(newGame.getIcon()) < 700,
							"new-game toolbar icon retained its opaque light bitmap");
					CellZoomPanel cellZoomPanel = frame.getSudokuPanel().getCellZoomPanel();
					UIColorPalette colorPalette = readField(cellZoomPanel, "colorPalette", UIColorPalette.class);
					requirePaletteActionSurfaces(colorPalette, dark.getControlBackground());
					JButton vagueHint = readField(frame, "vageHintToggleButton", JButton.class);
					require(iconOpaquePixelCount(vagueHint.getIcon()) < 500,
							"dark hint toolbar retained the old glossy bitmap icon");
					require(relativeLuminance(dark.getGridColor()) < 0.4,
							"dark board separators are brighter than the accepted restrained palette");

					SummaryPanel summary = new SummaryPanel(frame);
					JTable table = findComponent(summary, JTable.class);
					Component summaryCell = table.getCellRenderer(0, 0)
							.getTableCellRendererComponent(table, "", false, false, 0, 0);
					require(summaryCell.getBackground().equals(dark.getSurfaceBackground()),
							"summary renderer retained a light background");

					SolutionPanel solution = new SolutionPanel(frame);
					JList list = findComponent(solution, JList.class);
					Component solutionCell = list.getCellRenderer()
							.getListCellRendererComponent(list, "Step", 0, false, false);
					require(solutionCell.getBackground().equals(dark.getSurfaceBackground()),
							"solution renderer retained a light background");

					SudokuPanel panel = frame.getSudokuPanel();
					panel.setSize(900, 900);
					BufferedImage screen = new BufferedImage(900, 900, BufferedImage.TYPE_INT_RGB);
					Graphics2D screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					int sampleX = panel.getX(0, 0) + 3;
					int sampleY = panel.getY(0, 0) + 3;
					require(new Color(screen.getRGB(sampleX, sampleY)).equals(dark.getDefaultCellColor()),
							"on-screen Sudoku cell did not use the dark palette");
					Color coloredCell = new Color(170, 160, 230);
					Color readableCandidate = dark.getReadableForeground(coloredCell, dark.getCandidateColor());
					panel.handleColoring(0, 0, -1, coloredCell);
					screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					int cellWidth = panel.getX(0, 1) - panel.getX(0, 0);
					require(containsColor(screen, panel.getX(0, 0), panel.getY(0, 0), cellWidth, cellWidth,
							readableCandidate),
							"colored cell candidates retained an unreadable dark-gray foreground");
					panel.clearColoring();

					int[] filteredCell = findCandidateCell(panel);
					int filteredRow = Sudoku2.getRow(filteredCell[0]);
					int filteredCol = Sudoku2.getCol(filteredCell[0]);
					Color userCellColor = new Color(170, 160, 230);
					Color markerColor = dark.getFilterMarkerColor(userCellColor);
					panel.setShowCandidates(true);
					panel.setShowHintCellValue(filteredCell[1]);
					panel.checkIsShowInvalidOrPossibleCells();
					panel.handleColoring(filteredRow, filteredCol, -1, userCellColor);
					panel.clearSelection(filteredCell[0] == 0 ? 1 : 0);
					screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					int filteredX = panel.getX(filteredRow, filteredCol);
					int filteredY = panel.getY(filteredRow, filteredCol);
					require(new Color(screen.getRGB(filteredX + 2, filteredY + 2)).equals(markerColor),
							"candidate filter marker did not remain visible over a user-colored cell");
					require(!new Color(screen.getRGB(filteredX + cellWidth / 6, filteredY + cellWidth / 6))
							.equals(markerColor),
							"candidate filter marker intruded into a candidate position");

					panel.clearSelection(filteredRow, filteredCol);
					screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					int fullFrameSize = Math.max(1,
							(int) (cellWidth * Options.getInstance().getCursorFrameSize()));
					require(containsColor(screen, filteredX, filteredY, cellWidth, cellWidth,
							dark.getActiveCellColor()),
							"selected filtered cell lost its yellow outer frame");
					require(containsColor(screen, filteredX, filteredY, cellWidth,
							fullFrameSize + 2, markerColor),
							"selected filtered cell did not place its green marker inside the existing frame band");
					require(!containsColor(screen, filteredX + fullFrameSize + 2,
							filteredY + fullFrameSize + 2, cellWidth - 2 * fullFrameSize - 4,
							cellWidth - 2 * fullFrameSize - 4, markerColor),
							"selected filtered cell marker intruded beyond the original cursor frame band");
					panel.resetShowHintCellValues();
					panel.clearColoring();

					boolean oldSmallFilters = Options.getInstance().isOnlySmallFilters();
					Options.getInstance().setOnlySmallFilters(false);
					panel.setSudoku(FILTER_PUZZLE);
					panel.setShowCandidates(true);
					int manualOverlapCell = findCandidateCountMismatchCell(panel, 5);
					int manualOtherCandidate = findCandidateOtherThan(panel, manualOverlapCell, 5);
					panel.getSudoku().setUserCells(new short[Sudoku2.LENGTH]);
					panel.getSudoku().setCandidate(manualOverlapCell, 5, true, true);
					panel.getSudoku().setCandidate(manualOverlapCell, manualOtherCandidate, true, true);
					panel.setShowCandidates(false);
					panel.toggleBivalueFilter();
					panel.setShowHintCellValue(5);
					screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					int manualEmptySlot = findCandidateAbsentFromUserCell(panel, manualOverlapCell);
					Color manualBaseColor = dark.getBivalueFilterColor();
					require(candidateSlotColor(screen, panel, manualOverlapCell, manualEmptySlot)
							.equals(manualBaseColor),
							"manual-candidate XY overlap lost its XY cell background");
					require(containsCandidateColor(screen, panel, manualOverlapCell, 5,
							dark.getPossibleCellColor()),
							"manual-candidate XY overlap lost the selected candidate marker");
					panel.resetShowHintCellValues();
					panel.setShowCandidates(true);
					panel.setSudoku(FILTER_PUZZLE);
					int overlapCell = findCandidateCountCell(panel, 2, 5);
					int valueOnlyCell = findCandidateOutsideCountCell(panel, 2, 5);
					panel.toggleBivalueFilter();
					panel.toggleCandidateValueFilter(5, false);
					panel.toggleCandidateValueFilter(5, false);
					require(panel.isBivalueFilterActive() && !panel.hasCandidateValueFilter(),
							"clearing a toolbar candidate filter also cleared the independent XY filter");
					panel.toggleCandidateValueFilter(5, false);
					screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					int overlapRow = Sudoku2.getRow(overlapCell);
					int overlapCol = Sudoku2.getCol(overlapCell);
					int overlapX = panel.getX(overlapRow, overlapCol);
					int overlapY = panel.getY(overlapRow, overlapCol);
					int fifthX = overlapX + cellWidth / 2;
					int fifthY = overlapY + cellWidth / 2;
					int emptyOverlapSlot = findCandidateAbsentFromCell(panel, overlapCell);
					Color overlapBaseColor = dark.getBivalueFilterColor();
					require(candidateSlotColor(screen, panel, overlapCell, emptyOverlapSlot)
							.equals(overlapBaseColor),
							"XY and candidate-value overlap lost its XY cell background");
					require(containsColor(screen, fifthX - cellWidth / 10, fifthY - cellWidth / 10,
							cellWidth / 5, cellWidth / 5, dark.getPossibleCellColor()),
							"candidate-value marker disappeared in an XY overlap");
					int valueOnlyRow = Sudoku2.getRow(valueOnlyCell);
					int valueOnlyCol = Sudoku2.getCol(valueOnlyCell);
					int valueOnlyX = panel.getX(valueOnlyRow, valueOnlyCol);
					int valueOnlyY = panel.getY(valueOnlyRow, valueOnlyCol);
					require(new Color(screen.getRGB(valueOnlyX + cellWidth / 2, valueOnlyY + 2))
							.equals(dark.getPossibleCellColor()),
							"candidate value outside XY was not highlighted after XY became active");
					panel.setInvalidCells(true);
					screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					require(!new Color(screen.getRGB(valueOnlyX + cellWidth / 2, valueOnlyY + 2))
							.equals(dark.getInvalidCellColor()),
							"inverse filtering treated a candidate-value match outside XY as invalid");
					panel.setInvalidCells(false);
					Color overlapCellColor = new Color(170, 160, 230);
					Color oldFrameColor = dark.getFilterMarkerColor(overlapCellColor);
					panel.handleColoring(overlapRow, overlapCol, -1, overlapCellColor);
					panel.clearAllCellSelection();
					screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					require(!containsColor(screen, overlapX, overlapY, cellWidth, 5, oldFrameColor),
							"cross-group overlap retained the obsolete green cell frame over user coloring");
					require(containsColor(screen, fifthX - cellWidth / 10, fifthY - cellWidth / 10,
							cellWidth / 5, cellWidth / 5, dark.getPossibleCellColor()),
							"cross-group overlap marker disappeared over user cell coloring");
					panel.clearColoring();
					boolean oldUseOrFilters = Options.getInstance().isUseOrInsteadOfAndForFilter();
					int missingFromOverlap = findCandidateAbsentFromCell(panel, overlapCell);
					panel.resetShowHintCellValues();
					panel.toggleBivalueFilter();
					panel.setShowHintCellValue(5);
					panel.toggleCandidateValueFilter(missingFromOverlap, true);
					Options.getInstance().setUseOrInsteadOfAndForFilter(false);
					screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					require(candidateSlotColor(screen, panel, overlapCell, emptyOverlapSlot)
							.equals(dark.getBivalueFilterColor()),
							"AND candidate filters did not leave XY as the only matching group");
					require(!containsColor(screen, fifthX - cellWidth / 10, fifthY - cellWidth / 10,
							cellWidth / 5, cellWidth / 5, dark.getPossibleCellColor()),
							"AND candidate filters marked a value that did not satisfy the full candidate set");
					Options.getInstance().setUseOrInsteadOfAndForFilter(true);
					screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					require(candidateSlotColor(screen, panel, overlapCell, emptyOverlapSlot)
							.equals(overlapBaseColor),
							"OR candidate filters did not restore the XY overlap background");
					require(containsColor(screen, fifthX - cellWidth / 10, fifthY - cellWidth / 10,
							cellWidth / 5, cellWidth / 5, dark.getPossibleCellColor()),
							"OR candidate filters did not mark the selected candidate in the XY overlap");
					Options.getInstance().setUseOrInsteadOfAndForFilter(oldUseOrFilters);
					panel.resetShowHintCellValues();
					panel.toggleBivalueFilter();
					panel.setShowHintCellValue(5);
					Options.getInstance().setOnlySmallFilters(true);
					screenGraphics = screen.createGraphics();
					panel.paint(screenGraphics);
					screenGraphics.dispose();
					Color smallMarkerBaseColor = Sudoku2.getBlock(overlapCell) % 2 == 0
							? dark.getDefaultCellColor() : dark.getAlternateCellColor();
					require(candidateSlotColor(screen, panel, overlapCell, emptyOverlapSlot)
							.equals(smallMarkerBaseColor),
							"candidate-marker display still painted a whole overlap cell");
					require(containsCandidateColor(screen, panel, overlapCell, 5,
							dark.getPossibleCellColor()),
							"candidate-marker display lost the selected overlap candidate marker");
					int otherOverlapCandidate = findCandidateOtherThan(panel, overlapCell, 5);
					require(containsCandidateColor(screen, panel, overlapCell, otherOverlapCandidate,
							dark.getBivalueFilterColor()),
							"candidate-marker display lost the ordinary XY marker on another candidate");
					Options.getInstance().setOnlySmallFilters(oldSmallFilters);
					panel.resetShowHintCellValues();

					BufferedImage darkExport = panel.getSudokuImage(900);
					BufferedImage darkPrint = printImage(panel);
					ApplicationAppearance.initialize(AppearanceMode.LIGHT);
					BufferedImage lightExport = panel.getSudokuImage(900);
					BufferedImage lightPrint = printImage(panel);
					require(imagesEqual(darkExport, lightExport),
							"Dark appearance changed PNG/image export output");
					require(imagesEqual(darkPrint, lightPrint),
							"Dark appearance changed print rendering output");
					require(new Color(darkExport.getRGB(sampleX, sampleY)).equals(originalDefault),
							"Dark appearance exported a non-light Sudoku background");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					ApplicationAppearance.initialize(AppearanceMode.LIGHT);
					if (lightFrame != null) {
						lightFrame.dispose();
					}
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			new AssertionError("appearance rendering check failed", failure[0]).printStackTrace();
			System.exit(1);
		}
		System.out.println("Appearance rendering checks passed");
		System.exit(0);
	}

	private static BufferedImage printImage(SudokuPanel panel) {
		BufferedImage image = new BufferedImage(900, 900, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		panel.printSudoku(graphics, 0, 0, 900, false, 1.0);
		graphics.dispose();
		return image;
	}

	private static boolean imagesEqual(BufferedImage left, BufferedImage right) {
		if (left.getWidth() != right.getWidth() || left.getHeight() != right.getHeight()) {
			return false;
		}
		for (int y = 0; y < left.getHeight(); y++) {
			for (int x = 0; x < left.getWidth(); x++) {
				if (left.getRGB(x, y) != right.getRGB(x, y)) {
					return false;
				}
			}
		}
		return true;
	}

	private static int[] findCandidateCell(SudokuPanel panel) {
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			if (panel.getSudoku().getValue(index) == 0) {
				int[] candidates = panel.getSudoku().getAllCandidates(index);
				if (candidates.length > 0) {
					return new int[] { index, candidates[0] };
				}
			}
		}
		throw new AssertionError("test puzzle has no candidate cell");
	}

	private static int findCandidateCountCell(SudokuPanel panel, int count, int candidate) {
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			if (panel.getSudoku().getValue(index) == 0
					&& panel.getSudoku().getAllCandidates(index).length == count
					&& panel.getSudoku().isCandidate(index, candidate)) {
				return index;
			}
		}
		throw new AssertionError("test puzzle has no matching candidate-count cell");
	}

	private static int findCandidateOutsideCountCell(SudokuPanel panel, int count, int candidate) {
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			if (panel.getSudoku().getValue(index) == 0
					&& panel.getSudoku().getAllCandidates(index).length != count
					&& panel.getSudoku().isCandidate(index, candidate)) {
				return index;
			}
		}
		throw new AssertionError("test puzzle has no candidate outside the selected count");
	}

	private static int findCandidateAbsentFromCell(SudokuPanel panel, int index) {
		for (int candidate = 1; candidate <= Sudoku2.UNITS; candidate++) {
			if (!panel.getSudoku().isCandidate(index, candidate)) {
				return candidate;
			}
		}
		throw new AssertionError("test cell contains every candidate");
	}

	private static int findCandidateOtherThan(SudokuPanel panel, int index, int excludedCandidate) {
		for (int candidate : panel.getSudoku().getAllCandidates(index)) {
			if (candidate != excludedCandidate) {
				return candidate;
			}
		}
		throw new AssertionError("test cell has no second candidate");
	}

	private static int findCandidateCountMismatchCell(SudokuPanel panel, int candidate) {
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			int[] candidates = panel.getSudoku().getAllCandidates(index);
			if (panel.getSudoku().getValue(index) == 0 && candidates.length > 2
					&& panel.getSudoku().isCandidate(index, candidate)) {
				return index;
			}
		}
		throw new AssertionError("test puzzle has no candidate-count mismatch cell");
	}

	private static int findCandidateAbsentFromUserCell(SudokuPanel panel, int index) {
		for (int candidate = 1; candidate <= Sudoku2.UNITS; candidate++) {
			if (!panel.getSudoku().isCandidate(index, candidate, true)) {
				return candidate;
			}
		}
		throw new AssertionError("test cell contains every user candidate");
	}

	private static boolean containsCandidateColor(BufferedImage image, SudokuPanel panel, int index,
			int candidate, Color color) {
		int row = Sudoku2.getRow(index);
		int col = Sudoku2.getCol(index);
		int cellX = panel.getX(row, col);
		int cellY = panel.getY(row, col);
		int cellSize = panel.getX(0, 1) - panel.getX(0, 0);
		int third = Math.max(1, cellSize / 3);
		int candidateX = cellX + ((candidate - 1) % 3) * third;
		int candidateY = cellY + ((candidate - 1) / 3) * third;
		return containsColor(image, candidateX, candidateY, third, third, color);
	}

	private static Color candidateSlotColor(BufferedImage image, SudokuPanel panel, int index, int candidate) {
		int row = Sudoku2.getRow(index);
		int col = Sudoku2.getCol(index);
		int cellX = panel.getX(row, col);
		int cellY = panel.getY(row, col);
		int cellSize = panel.getX(0, 1) - panel.getX(0, 0);
		int third = Math.max(1, cellSize / 3);
		int x = cellX + ((candidate - 1) % 3) * third + third / 2;
		int y = cellY + ((candidate - 1) / 3) * third + third / 2;
		return new Color(image.getRGB(x, y));
	}

	private static void requirePaletteActionSurfaces(UIColorPalette palette, Color expectedBackground) {
		boolean swapFound = false;
		boolean resetFound = false;
		for (Component component : palette.getComponents()) {
			if (component instanceof UIBorderedImagePanel && component.getY() == 0 && component.getX() > 0) {
				swapFound = true;
				require(component.getBackground().equals(expectedBackground),
						"dark palette swap icon retained a bright default panel");
			} else if (component instanceof UIBorderedImagePanel && component.getX() == 0
					&& component.getY() > 0) {
				resetFound = true;
				require(component.getBackground().equals(expectedBackground),
						"dark palette reset icon retained a bright default panel");
			}
		}
		require(swapFound && resetFound, "palette action icons are missing");
	}

	private static JToggleButton readFirstFilterButton(MainFrame frame) throws Exception {
		JToggleButton[] buttons = readField(frame, "toggleButtons", JToggleButton[].class);
		return buttons[0];
	}

	private static double iconOpaqueLuminance(Icon icon) {
		BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		icon.paintIcon(null, graphics, 0, 0);
		graphics.dispose();
		double total = 0;
		int count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				Color color = new Color(image.getRGB(x, y), true);
				if (color.getAlpha() > 96) {
					total += relativeLuminance(color);
					count++;
				}
			}
		}
		return count == 0 ? 0 : total / count;
	}

	private static int colorRange(Color color) {
		int maximum = Math.max(color.getRed(), Math.max(color.getGreen(), color.getBlue()));
		int minimum = Math.min(color.getRed(), Math.min(color.getGreen(), color.getBlue()));
		return maximum - minimum;
	}

	private static int iconOpaquePixelCount(Icon icon) {
		BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		icon.paintIcon(null, graphics, 0, 0);
		graphics.dispose();
		int count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (new Color(image.getRGB(x, y), true).getAlpha() > 96) {
					count++;
				}
			}
		}
		return count;
	}

	private static void requireUserPaletteSeparatedFromSystem(SudokuAppearancePalette palette) {
		Color[] systemColors = {
			palette.getPossibleCellColor(), palette.getBivalueFilterColor(),
			palette.getTrivalueFilterColor(), palette.getActiveCellColor(),
			palette.getInvalidCellColor()
		};
		for (Color userColor : Options.COLORING_COLORS) {
			for (Color systemColor : systemColors) {
				require(rgbDistance(userColor, systemColor) >= 40.0,
						"default user coloring is too close to a system highlight");
			}
		}
	}

	private static void requireUserChainColors(SudokuAppearancePalette palette) {
		Color background = palette.getDefaultCellColor();
		Color strong = palette.getUserChainLinkColor(true);
		Color weak = palette.getUserChainLinkColor(false);
		require(contrastRatio(strong, background) >= 3.0,
				"Strong-link color lacks contrast against the board");
		require(contrastRatio(weak, background) >= 3.0,
				"Weak-link color lacks contrast against the board");
		require(rgbDistance(strong, weak) >= 80.0,
				"Strong and Weak link colors are not visually distinct");
		Color adjustedNode = palette.getUserChainNodeColor(new Color(62, 96, 210), background);
		require(contrastRatio(adjustedNode, background) >= 3.0,
				"user-chain node color remains unreadable on the board");
	}

	private static void requireOriginalHintSemantics(SudokuAppearancePalette palette) {
		require(palette.getHintBackgroundColor().equals(new Color(63, 218, 101)),
				"generated-step premise no longer uses the original HoDoKu green");
		require(palette.getHintDeleteBackgroundColor().equals(new Color(255, 118, 132)),
				"generated-step elimination no longer uses the original HoDoKu red");
		require(palette.getHintFinBackgroundColor().equals(new Color(127, 187, 255)),
				"generated-step fin no longer uses the original HoDoKu blue");
		Color[] als = Options.getInstance().getHintCandidateAlsBackColors();
		Color[] expected = { new Color(197, 232, 140), new Color(255, 203, 203),
				new Color(178, 223, 223), new Color(252, 220, 165) };
		for (int i = 0; i < expected.length; i++) {
			require(palette.getAlsBackgroundColor(i, als[i]).equals(expected[i]),
					"ALS group " + i + " no longer uses the original HoDoKu color");
		}
		require(!palette.getHintDeleteBackgroundColor().equals(
				palette.getAlsBackgroundColor(1, als[1])),
				"ALS grouping and elimination collapsed into the same red");
	}

	private static void requirePrintHintPaletteUnchanged() {
		SudokuAppearancePalette print = SudokuAppearancePalette.forRendering(true);
		require(print.getHintBackgroundColor().equals(Options.HINT_CANDIDATE_BACK_COLOR)
				&& print.getHintDeleteBackgroundColor().equals(Options.HINT_CANDIDATE_DELETE_BACK_COLOR)
				&& print.getArrowColor().equals(Options.ARROW_COLOR),
				"screen-only HoDoKu hint colors leaked into print/export rendering");
	}

	private static void requireDarkHintRolesRemainDistinct(SudokuAppearancePalette palette) {
		Color premise = palette.getHintForegroundColor(
				Options.HINT_CANDIDATE_COLOR, Options.HINT_CANDIDATE_COLOR);
		Color deletion = palette.getHintForegroundColor(
				Options.HINT_CANDIDATE_DELETE_COLOR, Options.HINT_CANDIDATE_DELETE_COLOR);
		Color fin = palette.getHintForegroundColor(
				Options.HINT_CANDIDATE_FIN_COLOR, Options.HINT_CANDIDATE_FIN_COLOR);
		Color als = palette.getHintForegroundColor(
				Options.HINT_CANDIDATE_ALS_COLORS[1], Options.HINT_CANDIDATE_ALS_COLORS[1]);
		require(!premise.equals(deletion) && !deletion.equals(fin) && !deletion.equals(als),
				"dark generated-step roles collapsed to one foreground color");
	}

	private static double rgbDistance(Color first, Color second) {
		int red = first.getRed() - second.getRed();
		int green = first.getGreen() - second.getGreen();
		int blue = first.getBlue() - second.getBlue();
		return Math.sqrt(red * red + green * green + blue * blue);
	}

	private static boolean containsColor(BufferedImage image, int x, int y, int width, int height, Color expected) {
		for (int row = y; row < y + height; row++) {
			for (int col = x; col < x + width; col++) {
				if (new Color(image.getRGB(col, row)).equals(expected)) {
					return true;
				}
			}
		}
		return false;
	}

	private static double relativeLuminance(Color color) {
		double red = linear(color.getRed() / 255.0);
		double green = linear(color.getGreen() / 255.0);
		double blue = linear(color.getBlue() / 255.0);
		return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
	}

	private static double contrastRatio(Color first, Color second) {
		double brighter = Math.max(relativeLuminance(first), relativeLuminance(second));
		double darker = Math.min(relativeLuminance(first), relativeLuminance(second));
		return (brighter + 0.05) / (darker + 0.05);
	}

	private static double linear(double value) {
		return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	private static <T> T readField(Object target, String name, Class<T> type) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return type.cast(field.get(target));
	}

	private static <T extends Component> T findComponent(Container parent, Class<T> type) {
		for (Component component : parent.getComponents()) {
			if (type.isInstance(component)) {
				return type.cast(component);
			}
			if (component instanceof Container) {
				T nested = findComponentOrNull((Container) component, type);
				if (nested != null) {
					return nested;
				}
			}
		}
		throw new AssertionError("missing component " + type.getSimpleName());
	}

	private static <T extends Component> T findComponentOrNull(Container parent, Class<T> type) {
		for (Component component : parent.getComponents()) {
			if (type.isInstance(component)) {
				return type.cast(component);
			}
			if (component instanceof Container) {
				T nested = findComponentOrNull((Container) component, type);
				if (nested != null) {
					return nested;
				}
			}
		}
		return null;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
