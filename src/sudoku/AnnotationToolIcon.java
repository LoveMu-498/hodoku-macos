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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import javax.swing.AbstractButton;
import javax.swing.Icon;

/** Small scalable, theme-aware tool glyph with the documented key in its corner. */
public final class AnnotationToolIcon implements Icon {
	private final AnnotationTool tool;
	private final int size;
    private final java.util.function.BooleanSupplier chainStrong;

	public AnnotationToolIcon(AnnotationTool tool, int size) {
        this(tool, size, () -> true);
    }

    public AnnotationToolIcon(AnnotationTool tool, int size, java.util.function.BooleanSupplier chainStrong) {
        this.tool = tool;
        this.size = size;
        this.chainStrong = chainStrong;
	}

	@Override
	public int getIconWidth() { return size; }

	@Override
	public int getIconHeight() { return size; }

	@Override
	public void paintIcon(Component component, Graphics graphics, int x, int y) {
		Graphics2D g = (Graphics2D) graphics.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Color foreground = component == null ? Color.DARK_GRAY : component.getForeground();
			Color background = component == null ? new Color(0, 0, 0, 0) : component.getBackground();
			if (component instanceof AbstractButton && ((AbstractButton) component).isSelected()) {
				SudokuAppearancePalette palette = SudokuAppearancePalette.forRendering(false);
				foreground = palette.getControlSelectionForeground();
				background = palette.getControlSelectionBackground();
			}
			g.setColor(foreground);
			g.setStroke(new BasicStroke(Math.max(1.8f, size / 11.0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			int main = Math.max(12, size - 4);
			switch (tool) {
			case DEFAULT_MOUSE:
				g.fillPolygon(new int[] { x + 2, x + 2, x + main - 1 },
						new int[] { y + 1, y + main + 1, y + main / 2 }, 3);
				break;
			case CANDIDATE_COLORING:
				for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++)
					g.fillOval(x + 2 + col * (main / 2), y + 2 + row * (main / 2), main / 3, main / 3);
				break;
			case CELL_COLORING:
				g.drawRoundRect(x + 2, y + 2, main - 1, main - 1, 4, 4);
				break;
			case DOODLE:
				g.drawArc(x + 1, y + 1, main * 2 / 3, main * 2 / 3, 220, 210);
				g.drawArc(x + main / 3, y + main / 3, main * 2 / 3, main * 2 / 3, 40, 210);
				break;
			case FREE_CHAIN:
                int nodeSize = Math.max(5, size / 5);
                int centerY = y + main / 2;
                int left = x + 3, right = x + main - 1;
                g.fillOval(left - nodeSize / 2, centerY - nodeSize / 2, nodeSize, nodeSize);
                g.fillOval(right - nodeSize / 2, centerY - nodeSize / 2, nodeSize, nodeSize);
                int start = left + nodeSize / 2 + 2, end = right - nodeSize / 2 - 2;
                if (chainStrong.getAsBoolean()) {
                    g.drawLine(start, centerY - 3, end, centerY - 3);
                    g.drawLine(start, centerY + 2, end, centerY + 2);
                } else g.drawLine(start, centerY, end, centerY);
				break;
			case BOX_SELECTION:
				g.drawRect(x + 2, y + 3, main - 2, main - 4);
				g.drawRect(x + main / 3, y + main / 3, main - main / 3, main - main / 3);
				break;
			default:
				break;
			}
			String key = tool == AnnotationTool.DEFAULT_MOUSE ? "M"
					: tool == AnnotationTool.DOODLE ? "P" : tool == AnnotationTool.FREE_CHAIN ? "L"
					: tool == AnnotationTool.BOX_SELECTION ? "S" : "T";
			Font baseFont = component == null ? new Font(Font.SANS_SERIF, Font.BOLD, 12) : component.getFont();
			Font keyFont = baseFont.deriveFont(Font.BOLD, (float) Math.max(10, Math.round(size * 0.48f)));
			GlyphVector glyph = keyFont.createGlyphVector(g.getFontRenderContext(), key);
			Rectangle2D bounds = glyph.getVisualBounds();
			float keyX = (float) (x + size - 1 - bounds.getX() - bounds.getWidth());
			float keyY = (float) (y + size - 1 - bounds.getY() - bounds.getHeight());
			Shape keyShape = glyph.getOutline(keyX, keyY);
			// Separate the shortcut letter from the enlarged glyph with a soft,
			// theme-colored halo instead of an opaque boxed keycap.
			g.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(), 220));
			g.setStroke(new BasicStroke(Math.max(3.0f, size / 7.0f),
					BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(keyShape);
			g.setColor(foreground);
			g.fill(keyShape);
		} finally {
			g.dispose();
		}
	}
}
