/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;
import java.awt.*;
import javax.swing.*;

/** Keep the digit/ColorKu identity while separating completion from filter selection. */
final class DimmedCandidateIcon implements Icon {
    private final Icon base;
    DimmedCandidateIcon(Icon base) { this.base = base; }
    public int getIconWidth() { return base.getIconWidth(); }
    public int getIconHeight() { return base.getIconHeight(); }
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D)graphics.create();
        try {
            g.setComposite(AlphaComposite.SrcOver.derive(0.28f));
            base.paintIcon(c, g, x, y);
            if (c instanceof JComponent && Boolean.TRUE.equals(((JComponent)c).getClientProperty("candidateComplete"))) {
                g.setComposite(AlphaComposite.SrcOver);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                SudokuAppearancePalette palette = SudokuAppearancePalette.forRendering(false);
                g.setColor(c instanceof AbstractButton && ((AbstractButton)c).isSelected()
                        ? palette.getControlSelectionForeground() : palette.getPrimaryForeground());
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int right=x+getIconWidth()-2, bottom=y+getIconHeight()-3;
                g.drawLine(right-7,bottom-3,right-4,bottom);
                g.drawLine(right-4,bottom,right,bottom-6);
            }
        } finally { g.dispose(); }
    }
}
