/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.AbstractButton;
import javax.swing.Icon;

/** One flat, appearance-aware glyph family for the main toolbar. */
final class ToolbarIcons {

	private static final int SIZE = 32;
	private static final BasicStroke STROKE = new BasicStroke(
			2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	private ToolbarIcons() {
	}

	static Icon vagueHint() { return new CircledGlyphIcon("?", SemanticColor.HINT); }
	static Icon concreteHint() { return new CircledGlyphIcon("?", SemanticColor.HINT); }
	static Icon nextStep() { return new CircledGlyphIcon("!", SemanticColor.HINT); }
	static Icon execute() { return new CircledActionIcon(true); }
	static Icon abort() { return new CircledActionIcon(false); }
	static Icon xyFilter() { return new CandidateCountIcon("y"); }
	static Icon xyzFilter() { return new CandidateCountIcon("yz"); }
	static Icon newGame() { return new NewGameIcon(); }

	static Image swapColor() {
		BufferedImage image = smallCanvas();
		Graphics2D graphics = graphics(image);
		graphics.setColor(ApplicationAppearance.isDark()
				? new Color(245, 247, 250) : new Color(43, 49, 56));
		graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawLine(2, 4, 12, 4);
		graphics.drawLine(9, 1, 12, 4);
		graphics.drawLine(12, 4, 9, 7);
		graphics.drawLine(13, 11, 3, 11);
		graphics.drawLine(6, 8, 3, 11);
		graphics.drawLine(3, 11, 6, 14);
		graphics.dispose();
		return image;
	}

	static Image clearColoring() {
		return clearColoring(ApplicationAppearance.isDark()
				? new Color(235, 238, 242) : new Color(51, 105, 77),
				ApplicationAppearance.isDark()
						? new Color(95, 104, 113) : new Color(250, 250, 250));
	}

	private static Image clearColoring(Color eraserColor, Color eraserShade) {
		BufferedImage image = smallCanvas();
		Graphics2D graphics = graphics(image);
		graphics.setColor(eraserColor);
		graphics.rotate(-Math.PI / 4.0, 8.0, 8.0);
		graphics.fillRoundRect(3, 4, 10, 8, 2, 2);
		graphics.setColor(eraserShade);
		graphics.fillRect(3, 9, 10, 3);
		graphics.dispose();
		return image;
	}

	private static Color color(SemanticColor semantic) {
		boolean dark = ApplicationAppearance.isDark();
		switch (semantic) {
		case HINT:
			return dark ? new Color(173, 220, 110) : new Color(75, 139, 27);
		case EXECUTE:
			return dark ? new Color(113, 213, 146) : new Color(34, 134, 75);
		case ABORT:
			return dark ? new Color(255, 120, 120) : new Color(197, 55, 72);
		default:
			return SudokuAppearancePalette.forRendering(false).getPrimaryForeground();
		}
	}

	private enum SemanticColor { PRIMARY, HINT, EXECUTE, ABORT }

	private static final class CandidateCountIcon implements Icon {
		private final String suffix;

		CandidateCountIcon(String suffix) { this.suffix = suffix; }
		@Override public int getIconWidth() { return SIZE; }
		@Override public int getIconHeight() { return SIZE; }

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				prepare(g);
				boolean selected = component instanceof AbstractButton
						&& ((AbstractButton) component).isSelected();
				g.setColor(selected
						? SudokuAppearancePalette.forRendering(false).getControlSelectionForeground()
						: color(SemanticColor.PRIMARY));
				g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 21));
				g.drawString("X", x + 2, y + 22);
				g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
				g.drawString(suffix, x + (suffix.length() == 1 ? 18 : 16), y + 26);
			} finally {
				g.dispose();
			}
		}
	}

	private static final class CircledGlyphIcon implements Icon {
		private final String glyph;
		private final SemanticColor semantic;

		CircledGlyphIcon(String glyph, SemanticColor semantic) {
			this.glyph = glyph;
			this.semantic = semantic;
		}

		@Override public int getIconWidth() { return SIZE; }
		@Override public int getIconHeight() { return SIZE; }

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				prepare(g);
				g.setColor(color(semantic));
				g.setStroke(STROKE);
				g.drawOval(x + 4, y + 4, 24, 24);
				g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
				FontMetrics metrics = g.getFontMetrics();
				int textX = x + (SIZE - metrics.stringWidth(glyph)) / 2;
				int textY = y + (SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
				g.drawString(glyph, textX, textY);
			} finally {
				g.dispose();
			}
		}
	}

	private static final class CircledActionIcon implements Icon {
		private final boolean execute;

		CircledActionIcon(boolean execute) { this.execute = execute; }
		@Override public int getIconWidth() { return SIZE; }
		@Override public int getIconHeight() { return SIZE; }

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				prepare(g);
				g.setColor(color(execute ? SemanticColor.EXECUTE : SemanticColor.ABORT));
				g.setStroke(STROKE);
				g.drawOval(x + 4, y + 4, 24, 24);
				if (execute) {
					g.drawLine(x + 9, y + 17, x + 14, y + 22);
					g.drawLine(x + 14, y + 22, x + 24, y + 10);
				} else {
					g.drawLine(x + 10, y + 10, x + 22, y + 22);
					g.drawLine(x + 22, y + 10, x + 10, y + 22);
				}
			} finally {
				g.dispose();
			}
		}
	}

	private static final class NewGameIcon implements Icon {
		@Override public int getIconWidth() { return SIZE; }
		@Override public int getIconHeight() { return SIZE; }

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				prepare(g);
				SudokuAppearancePalette palette = SudokuAppearancePalette.forRendering(false);
				g.setColor(palette.getControlBackground());
				g.fillRoundRect(x + 3, y + 3, 22, 22, 4, 4);
				g.setColor(palette.getGridColor());
				g.setStroke(new BasicStroke(1.2f));
				g.drawRoundRect(x + 3, y + 3, 22, 22, 4, 4);
				g.drawLine(x + 10, y + 4, x + 10, y + 24);
				g.drawLine(x + 17, y + 4, x + 17, y + 24);
				g.drawLine(x + 4, y + 10, x + 24, y + 10);
				g.drawLine(x + 4, y + 17, x + 24, y + 17);
				g.setColor(palette.getPrimaryForeground());
				g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 8));
				g.drawString("5", x + 11, y + 16);
				g.setColor(ApplicationAppearance.isDark()
						? new Color(242, 202, 92) : new Color(198, 118, 0));
				g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.drawLine(x + 27, y + 4, x + 27, y + 10);
				g.drawLine(x + 24, y + 7, x + 30, y + 7);
			} finally {
				g.dispose();
			}
		}
	}

	private static BufferedImage smallCanvas() {
		return new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB);
	}

	private static Graphics2D graphics(BufferedImage image) {
		Graphics2D graphics = image.createGraphics();
		prepare(graphics);
		return graphics;
	}

	private static void prepare(Graphics2D graphics) {
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
				RenderingHints.VALUE_STROKE_PURE);
	}
}
