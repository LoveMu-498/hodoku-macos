/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.Icon;

/** Shadow-free candidate digit used by the compact toolbar filters. */
final class CandidateFilterIcon implements Icon {
	private final int digit;
	private final boolean available;
	private final int size;

	CandidateFilterIcon(int digit, boolean available, int size) {
		this.digit = digit;
		this.available = available;
		this.size = size;
	}

	@Override
	public int getIconWidth() {
		return size;
	}

	@Override
	public int getIconHeight() {
		return size;
	}

	@Override
	public void paintIcon(Component component, Graphics graphics, int x, int y) {
		Graphics2D g = (Graphics2D) graphics.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			SudokuAppearancePalette palette = SudokuAppearancePalette.forRendering(false);
			boolean selected = component instanceof AbstractButton
					&& ((AbstractButton) component).isSelected();
			Color color = selected ? palette.getControlSelectionForeground()
					: available ? palette.getPrimaryForeground() : palette.getCandidateColor();
			Font base = component == null ? new Font(Font.SANS_SERIF, Font.BOLD, 12)
					: component.getFont();
			Font font = base.deriveFont(Font.BOLD, Math.max(18.0f, size * 0.72f));
			g.setFont(font);
			g.setColor(color);
			String text = Integer.toString(digit);
			FontMetrics metrics = g.getFontMetrics();
			int textX = x + (size - metrics.stringWidth(text)) / 2;
			int textY = y + (size - metrics.getHeight()) / 2 + metrics.getAscent();
			g.drawString(text, textX, textY);
		} finally {
			g.dispose();
		}
	}
}
