/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.*;
import javax.swing.*;

/** Vector start-state icons, sharing the existing annotation toolbar size and stroke. */
final class DoodleHypothesisIcon implements Icon {
    private final boolean cancel;
    private final int size;
    DoodleHypothesisIcon(boolean cancel, int size) { this.cancel = cancel; this.size = size; }
    public int getIconWidth() { return size; }
    public int getIconHeight() { return size; }
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = component.getForeground();
            if (component instanceof AbstractButton && ((AbstractButton) component).isSelected())
                color = SudokuAppearancePalette.forRendering(false).getControlSelectionForeground();
            g.setColor(color);
            g.setStroke(new BasicStroke(Math.max(1.8f, size / 11f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int left = 3, right = size - 4, top = 3, bottom = size - 4, arm = size / 5;
            g.drawLine(left, top + arm, left, top); g.drawLine(left, top, left + arm, top);
            g.drawLine(right - arm, top, right, top); g.drawLine(right, top, right, top + arm);
            g.drawLine(left, bottom - arm, left, bottom); g.drawLine(left, bottom, left + arm, bottom);
            g.drawLine(right - arm, bottom, right, bottom); g.drawLine(right, bottom, right, bottom - arm);
            if (cancel) g.drawLine(7, size - 8, size - 8, 7);
            else {
                int center = (size - 1) / 2;
                g.drawLine(center - arm, center, center + arm, center);
                g.drawLine(center, center - arm, center, center + arm);
            }
        } finally { g.dispose(); }
    }
}
