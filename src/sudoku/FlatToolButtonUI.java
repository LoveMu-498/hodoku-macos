/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicToggleButtonUI;

/** Flat visual state for compact toolbar and annotation-mode toggle buttons. */
final class FlatToolButtonUI extends BasicToggleButtonUI {

	static void install(AbstractButton button) {
		button.setUI(new FlatToolButtonUI());
		button.setOpaque(false);
		button.setContentAreaFilled(false);
		button.setBorderPainted(false);
		button.setRolloverEnabled(true);
	}

	@Override
	public void paint(Graphics graphics, JComponent component) {
		AbstractButton button = (AbstractButton) component;
		ButtonModel model = button.getModel();
		SudokuAppearancePalette palette = SudokuAppearancePalette.forRendering(false);
		boolean selected = model.isSelected();
		boolean hover = model.isRollover() || (model.isPressed() && model.isArmed());
		boolean focused = button.isFocusOwner();

		if (selected || hover || focused) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
						RenderingHints.VALUE_ANTIALIAS_ON);
				int width = Math.max(0, component.getWidth() - 3);
				int height = Math.max(0, component.getHeight() - 3);
				if (selected || hover) {
					g.setColor(selected ? palette.getControlSelectionBackground()
							: palette.getControlHoverBackground());
					g.fillRoundRect(1, 1, width, height, 9, 9);
				}
				g.setColor(selected ? palette.getControlSelectionBorder()
						: palette.getControlHoverBorder());
				g.setStroke(new BasicStroke(focused ? 2.0f : 1.0f));
				g.drawRoundRect(1, 1, width, height, 9, 9);
			} finally {
				g.dispose();
			}
		}
		super.paint(graphics, component);
	}

	@Override
	protected void paintFocus(Graphics graphics, AbstractButton button,
			java.awt.Rectangle viewRect, java.awt.Rectangle textRect,
			java.awt.Rectangle iconRect) {
		// The rounded focus border is painted with the state background above.
	}
}
