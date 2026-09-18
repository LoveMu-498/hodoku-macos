package sudoku;

import java.awt.*;

/** Keeps existing toolbar controls reachable when the window needs another row. */
final class ToolbarWrapLayout extends FlowLayout {
    ToolbarWrapLayout() { super(FlowLayout.LEADING, 2, 2); }
    @Override public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int width = target.getWidth();
            if (width <= 0 && target.getParent() != null) width = target.getParent().getWidth();
            int available = width > 0 ? Math.max(1, width - insets.left - insets.right - getHgap() * 2) : Integer.MAX_VALUE;
            int rowWidth = 0, rowHeight = 0, maxWidth = 0, totalHeight = 0;
            for (Component component : target.getComponents()) {
                if (!component.isVisible()) continue;
                Dimension size = component.getPreferredSize();
                int next = rowWidth == 0 ? size.width : rowWidth + getHgap() + size.width;
                if (rowWidth > 0 && next > available) {
                    maxWidth = Math.max(maxWidth, rowWidth); totalHeight += rowHeight + getVgap();
                    rowWidth = 0; rowHeight = 0;
                }
                rowWidth += (rowWidth == 0 ? 0 : getHgap()) + size.width;
                rowHeight = Math.max(rowHeight, size.height);
            }
            return new Dimension(Math.max(maxWidth, rowWidth) + insets.left + insets.right + 2 * getHgap(),
                    totalHeight + rowHeight + insets.top + insets.bottom + 2 * getVgap());
        }
    }
    @Override public Dimension minimumLayoutSize(Container target) {
        Dimension size = preferredLayoutSize(target); size.width = 0; return size;
    }
}
