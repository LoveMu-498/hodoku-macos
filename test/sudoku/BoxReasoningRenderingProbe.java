/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.List;
import javax.swing.SwingUtilities;

/** Verifies readable Box group badges without obscuring candidate glyphs. */
public final class BoxReasoningRenderingProbe {
	private static final String VALID_PUZZLE =
			"530070000600195000098000060800060003400803001700020006060000280000419005000080079";

	private BoxReasoningRenderingProbe() {
	}

	public static void main(String[] args) throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					ApplicationAppearance.initialize(AppearanceMode.LIGHT);
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					panel.setSudoku(VALID_PUZZLE);
					panel.setSize(900, 900);
					panel.setShowCandidates(true);

					List<SudokuSet> groups = boxGroups(panel);
					int[] anchors = { 2, 3, 5, 6, 7, 8 };
					for (int group = 0; group < groups.size(); group++) {
						groups.get(group).clear();
						groups.get(group).add(anchors[group]);
					}
					panel.setActiveBoxReasoningGroup(0);
					BufferedImage ordinary = paint(panel, 900);
					int cellSize = panel.getX(0, 1) - panel.getX(0, 0);
					Color[] palette = Options.getInstance().getColoringColors();
					for (int group = 0; group < groups.size(); group++) {
						int index = anchors[group];
						int x = panel.getX(Sudoku2.getRow(index), Sudoku2.getCol(index));
						int y = panel.getY(Sudoku2.getRow(index), Sudoku2.getCol(index));
						int size = Math.max(10, Math.min(18, cellSize / 6));
						int badgeX = x + cellSize / 3 - size / 2;
						int badgeY = y + Math.max(2, cellSize / 40);
						Color groupColor = palette[group * 2];
						require(countExactColor(ordinary, badgeX, badgeY, size, size, groupColor) >= size,
								"Box group " + (char) ('A' + group) + " has no readable badge fill");
						require(hasHighContrastInk(ordinary, badgeX + 2, badgeY + 2,
								Math.max(1, size - 4), Math.max(1, size - 4), groupColor),
								"Box group " + (char) ('A' + group) + " has no high-contrast label");
					}
					panel.setStep(new SolutionStep(SolutionType.NAKED_PAIR));
					BufferedImage dimmed = paint(panel, 900);
					panel.abortStep();
					int activeBadgeSize = Math.max(10, Math.min(18, cellSize / 6));
					int activeBadgeX = panel.getX(0, 2) + cellSize / 3 - activeBadgeSize / 2;
					int activeBadgeY = panel.getY(0, 2) + Math.max(2, cellSize / 40);
					require(countExactColor(dimmed, activeBadgeX, activeBadgeY,
							activeBadgeSize, activeBadgeSize, palette[0])
							< countExactColor(ordinary, activeBadgeX, activeBadgeY,
									activeBadgeSize, activeBadgeSize, palette[0]),
							"native-step preview did not visually subordinate Box reasoning marks");

					for (SudokuSet group : groups) group.clear();
					int overlap = 2;
					for (SudokuSet group : groups) group.add(overlap);
					BufferedImage overlapImage = paint(panel, 900);
					int overlapX = panel.getX(Sudoku2.getRow(overlap), Sudoku2.getCol(overlap));
					int overlapY = panel.getY(Sudoku2.getRow(overlap), Sudoku2.getCol(overlap));
					int segmentWidth = Math.max(7, Math.min(12, cellSize / 8));
					int segmentHeight = Math.max(7, Math.min(11, cellSize / 9));
					int badgeWidth = segmentWidth * 3;
					int badgeHeight = segmentHeight * 2;
					int stackedX = overlapX + (cellSize - badgeWidth) / 2;
					int stackedY = overlapY + Math.max(2, cellSize / 3 - badgeHeight / 2);
					for (int group = 0; group < groups.size(); group++) {
						int segmentX = stackedX + (group % 3) * segmentWidth;
						int segmentY = stackedY + (group / 3) * segmentHeight;
						Color groupColor = palette[group * 2];
						require(countExactColor(overlapImage, segmentX, segmentY,
								segmentWidth, segmentHeight, groupColor) > 2,
								"six-group fallback lost the " + (char) ('A' + group) + " badge segment");
						require(hasHighContrastInk(overlapImage, segmentX + 1, segmentY + 1,
								Math.max(1, segmentWidth - 2), Math.max(1, segmentHeight - 2), groupColor),
								"six-group fallback lost the " + (char) ('A' + group) + " label");
						int slot = group < 3 ? group : group - 3;
						int tickLength = Math.max(5, cellSize / 8);
						int tickCenterX = overlapX + (2 * slot + 1) * cellSize / 6;
						int tickY = group < 3 ? overlapY + Math.max(2, cellSize / 40)
								: overlapY + cellSize - Math.max(2, cellSize / 40);
						require(countExactColor(overlapImage, tickCenterX - tickLength / 2,
								tickY - 1, tickLength + 1, 3, groupColor) >= Math.max(2, tickLength / 3),
								"six-group fallback lost the " + (char) ('A' + group) + " perimeter tick");
					}

					for (SudokuSet group : groups) group.clear();
					int anchor = 2;
					int continuation = 11;
					groups.get(0).add(anchor);
					groups.get(0).add(continuation);
					panel.getSudoku().setUserCells(new short[Sudoku2.LENGTH]);
					panel.getSudoku().setCandidate(anchor, 4, true, true);
					panel.getSudoku().setCandidate(continuation, 4, true, true);
					panel.setShowCandidates(false);
					panel.clearAllCellSelection();
					BufferedImage candidateImage = paint(panel, 900);
					int third = cellSize / 3;
					int glyphWidth = Math.max(4, third / 4);
					int glyphHeight = Math.max(6, third / 2);
					int anchorX = panel.getX(Sudoku2.getRow(anchor), Sudoku2.getCol(anchor))
							+ third / 2 - glyphWidth / 2;
					int anchorY = panel.getY(Sudoku2.getRow(anchor), Sudoku2.getCol(anchor))
							+ third + third / 2 - glyphHeight / 2;
					int continuationX = panel.getX(Sudoku2.getRow(continuation), Sudoku2.getCol(continuation))
							+ third / 2 - glyphWidth / 2;
					int continuationY = panel.getY(Sudoku2.getRow(continuation), Sudoku2.getCol(continuation))
							+ third + third / 2 - glyphHeight / 2;
					require(regionsEqual(candidateImage, anchorX, anchorY,
							continuationX, continuationY, glyphWidth, glyphHeight),
							"component badge changed or obscured the candidate glyph safety region");

					for (SudokuSet group : groups) group.clear();
					groups.get(0).add(anchor);
					panel.setSize(360, 360);
					BufferedImage smallImage = paint(panel, 360);
					int smallCellSize = panel.getX(0, 1) - panel.getX(0, 0);
					require(smallCellSize < 48, "small-cell fallback fixture is not small enough");
					int smallX = panel.getX(Sudoku2.getRow(anchor), Sudoku2.getCol(anchor));
					int smallY = panel.getY(Sudoku2.getRow(anchor), Sudoku2.getCol(anchor));
					int smallSegmentWidth = Math.max(7, Math.min(12, smallCellSize / 8));
					int smallSegmentHeight = Math.max(7, Math.min(11, smallCellSize / 9));
					int smallBadgeX = smallX + (smallCellSize - smallSegmentWidth) / 2;
					int smallBadgeY = smallY + Math.max(2, smallCellSize / 3 - smallSegmentHeight / 2);
					require(countExactColor(smallImage, smallBadgeX, smallBadgeY,
							smallSegmentWidth, smallSegmentHeight, palette[0]) > 2,
							"small-cell fallback did not use the compact stacked badge");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) frame.dispose();
					ApplicationAppearance.initialize(AppearanceMode.LIGHT);
				}
			}
		});
		if (failure[0] != null) {
			failure[0].printStackTrace();
			System.exit(1);
		}
		System.out.println("Box reasoning rendering checks passed");
		System.exit(0);
	}

	@SuppressWarnings("unchecked")
	private static List<SudokuSet> boxGroups(SudokuPanel panel) throws Exception {
		Field field = SudokuPanel.class.getDeclaredField("boxReasoningGroups");
		field.setAccessible(true);
		return (List<SudokuSet>) field.get(panel);
	}

	private static BufferedImage paint(SudokuPanel panel, int size) {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		panel.paint(graphics);
		graphics.dispose();
		return image;
	}

	private static int countExactColor(BufferedImage image, int x, int y, int width, int height, Color color) {
		int count = 0;
		for (int row = Math.max(0, y); row < Math.min(image.getHeight(), y + height); row++) {
			for (int col = Math.max(0, x); col < Math.min(image.getWidth(), x + width); col++) {
				if (new Color(image.getRGB(col, row)).equals(color)) count++;
			}
		}
		return count;
	}

	private static boolean hasHighContrastInk(BufferedImage image, int x, int y,
			int width, int height, Color background) {
		for (int row = Math.max(0, y); row < Math.min(image.getHeight(), y + height); row++) {
			for (int col = Math.max(0, x); col < Math.min(image.getWidth(), x + width); col++) {
				Color pixel = new Color(image.getRGB(col, row));
				if (!pixel.equals(background) && contrastRatio(pixel, background) >= 4.5) return true;
			}
		}
		return false;
	}

	private static boolean regionsEqual(BufferedImage image, int firstX, int firstY,
			int secondX, int secondY, int width, int height) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (image.getRGB(firstX + x, firstY + y) != image.getRGB(secondX + x, secondY + y)) {
					return false;
				}
			}
		}
		return true;
	}

	private static double contrastRatio(Color first, Color second) {
		double brighter = Math.max(relativeLuminance(first), relativeLuminance(second));
		double darker = Math.min(relativeLuminance(first), relativeLuminance(second));
		return (brighter + 0.05) / (darker + 0.05);
	}

	private static double relativeLuminance(Color color) {
		return 0.2126 * linear(color.getRed() / 255.0)
				+ 0.7152 * linear(color.getGreen() / 255.0)
				+ 0.0722 * linear(color.getBlue() / 255.0);
	}

	private static double linear(double value) {
		return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
