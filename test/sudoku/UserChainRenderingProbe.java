/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

/** Verifies user-chain route obstruction and node-over-edge rendering. */
public final class UserChainRenderingProbe {
	private static final String PUZZLE =
			"530070000600195000098000060800060003400803001700020006060000280000419005000080079";

	private UserChainRenderingProbe() {
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
					panel.setSize(900, 900);
					panel.setSudoku(PUZZLE);
					panel.setShowCandidates(true);
					paint(panel);

					int cellSize = panel.getX(0, 1) - panel.getX(0, 0);
					Font candidateFont = new Font(
							Options.getInstance().getDefaultCandidateFont().getName(),
							Options.getInstance().getDefaultCandidateFont().getStyle(),
							(int) (cellSize * Options.getInstance().getCandidateFontFactor()));
					FontMetrics candidateMetrics = panel.getFontMetrics(candidateFont);
					int generatedNodeDiameter = (int) Math.round(
							(candidateMetrics.getAscent() - candidateMetrics.getDescent())
									* Options.getInstance().getHintBackFactor());
					require(SudokuPanel.userChainNodeDiameter(generatedNodeDiameter, cellSize)
							== generatedNodeDiameter,
							"free-chain and generated-step candidate circles use different diameters");
					int[][] line = findCollinearCandidates(panel);
					int[] sideNode = findCandidateOutsideRow(panel, line[1][0]);
					panel.setAnnotationTool(AnnotationTool.FREE_CHAIN);
					panel.getCellZoomPanel().selectPaletteGroup(1);
					clickCandidate(panel, line[1][0], line[1][1], line[1][2], cellSize);
					clickCandidate(panel, sideNode[0], sideNode[1], sideNode[2], cellSize);
					press(panel, KeyEvent.VK_ENTER);

					panel.getCellZoomPanel().selectPaletteGroup(0);
					clickCandidate(panel, line[0][0], line[0][1], line[0][2], cellSize);
					clickCandidate(panel, line[2][0], line[2][1], line[2][2], cellSize);
					BufferedImage rendered = paint(panel);

					Point2D.Double middle = candidateCenter(panel,
							line[1][0], line[1][1], line[1][2], cellSize);
					SudokuAppearancePalette palette = SudokuAppearancePalette.forRendering(false);
					int middleIndex = Sudoku2.getIndex(line[1][0], line[1][1]);
					Color middleBackground = Sudoku2.getBlock(middleIndex) % 2 == 0
							? palette.getDefaultCellColor() : palette.getAlternateCellColor();
					Color expectedNode = palette.getUserChainNodeColor(
							Options.getInstance().getColoringColors()[2],
							middleBackground);
					int radius = SudokuPanel.userChainNodeDiameter(generatedNodeDiameter, cellSize) / 2;
					int fillPixels = 0;
					for (int x = (int) Math.round(middle.x) - radius + 2;
							x <= (int) Math.round(middle.x) + radius - 2; x++) {
						if (new Color(rendered.getRGB(x, (int) Math.round(middle.y))).equals(expectedNode)) {
							fillPixels++;
						}
					}
					require(fillPixels >= radius,
							"a crossing user relation covered an opaque candidate node");
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
			throw new AssertionError("user-chain rendering check failed", failure[0]);
		}
		System.out.println("User-chain rendering checks passed");
		System.exit(0);
	}

	private static BufferedImage paint(SudokuPanel panel) {
		BufferedImage image = new BufferedImage(900, 900, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		panel.paint(graphics);
		graphics.dispose();
		return image;
	}

	private static int[][] findCollinearCandidates(SudokuPanel panel) {
		for (int row = 0; row < Sudoku2.UNITS; row++) {
			for (int candidate = 1; candidate <= Sudoku2.UNITS; candidate++) {
				int[][] result = new int[3][3];
				int found = 0;
				for (int col = 0; col < Sudoku2.UNITS && found < result.length; col++) {
					int index = Sudoku2.getIndex(row, col);
					if (panel.getSudoku().isCandidate(index, candidate)) {
						result[found++] = new int[] { row, col, candidate };
					}
				}
				if (found == result.length) {
					return result;
				}
			}
		}
		throw new AssertionError("probe puzzle has no three collinear candidates");
	}

	private static int[] findCandidateOutsideRow(SudokuPanel panel, int excludedRow) {
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			if (Sudoku2.getRow(index) == excludedRow) {
				continue;
			}
			int[] candidates = panel.getSudoku().getAllCandidates(index);
			if (candidates.length > 0) {
				return new int[] { Sudoku2.getRow(index), Sudoku2.getCol(index), candidates[0] };
			}
		}
		throw new AssertionError("probe puzzle has no side candidate");
	}

	private static void clickCandidate(SudokuPanel panel, int row, int col, int candidate,
			int cellSize) {
		Point2D.Double center = candidateCenter(panel, row, col, candidate, cellSize);
		int x = (int) Math.round(center.x);
		int y = (int) Math.round(center.y);
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x, y));
	}

	private static Point2D.Double candidateCenter(SudokuPanel panel, int row, int col,
			int candidate, int cellSize) {
		double third = cellSize / 3.0;
		return new Point2D.Double(
				panel.getX(row, col) + ((candidate - 1) % 3) * third + third / 2.0,
				panel.getY(row, col) + ((candidate - 1) / 3) * third + third / 2.0);
	}

	private static MouseEvent mouse(SudokuPanel panel, int id, int x, int y) {
		return new MouseEvent(panel, id, System.currentTimeMillis(), 0, x, y, 1, false,
				MouseEvent.BUTTON1);
	}

	private static void press(SudokuPanel panel, int keyCode) {
		KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener listener : panel.getKeyListeners()) {
			listener.keyPressed(event);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
