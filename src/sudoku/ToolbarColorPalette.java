package sudoku;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Compact projection of the existing tool-local palette; owns no saved state. */
final class ToolbarColorPalette extends JPanel {
    private final CellZoomPanel owner;
    private final JButton primary, secondary, swap;
    private final JButton clearCurrent, clearAll;
    private final JPanel colorGroup;
    private final JButton modeState;
    private final JToggleButton[] groups = new JToggleButton[6];

    ToolbarColorPalette(CellZoomPanel owner, UIColorPalette actions) {
        super(new FlowLayout(FlowLayout.LEADING, 2, 0));
        this.owner = owner;
        setOpaque(false);
        JPanel current = new JPanel(null);
        current.setOpaque(false);
        current.setPreferredSize(new Dimension(32, 32));
        primary = button(new SwatchIcon(false), 22, "主色：选择配色，自动匹配最近色组；Command 点击清除该色");
        secondary = button(new SwatchIcon(true), 22, "副色：Opt 临时使用；选择配色时主副色整组更新");
        primary.setBounds(0, 1, 22, 22);
        secondary.setBounds(10, 10, 22, 22);
        current.add(primary); current.add(secondary);
        primary.addActionListener(e -> actions.chooseToolbarColor(false, (e.getModifiers() & ActionEvent.META_MASK) != 0));
        secondary.addActionListener(e -> actions.chooseToolbarColor(true, (e.getModifiers() & ActionEvent.META_MASK) != 0));
        add(current);
        swap = button(new SwapIcon(), 28, "Opt：临时使用副色；X：交换主副色");
        swap.addActionListener(e -> owner.swapColors());
        add(swap);
        colorGroup = new JPanel(new GridLayout(1, 6, 0, 0)) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(ApplicationAppearance.isDark() ? new Color(255,255,255,16) : new Color(0,0,0,12));
                    g.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 6, 6);
                } finally { g.dispose(); }
            }
        };
        colorGroup.setOpaque(false);
        colorGroup.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        for (int i = 0; i < groups.length; i++) {
            final int group = i;
            JToggleButton button = new JToggleButton(new PairIcon(i));
            button.setPreferredSize(new Dimension(18, 30));
            button.setMargin(new Insets(0, 0, 0, 0));
            button.setFocusable(true);
            button.setRequestFocusEnabled(false);
            button.setDisabledIcon(button.getIcon());
            FlatToolButtonUI.install(button);
            button.setToolTipText("颜色组 " + (char)('A' + i) + (i < 4 ? "（" + (char)('A' + i) + "）" : "") + "；滚轮成对切换");
            button.getAccessibleContext().setAccessibleName(button.getToolTipText());
            button.addActionListener(e -> owner.selectPaletteGroup(group));
            groups[i] = button;
            colorGroup.add(button);
        }
        add(colorGroup);
        modeState = button(new ModeStateIcon(), 30, "涂鸦粗细");
        modeState.addActionListener(e -> {
            if (owner.isDoodle() && owner.annotationBoard() != null) owner.annotationBoard().cycleDoodleWidthByClick();
        });
        modeState.setPreferredSize(new Dimension(30, 32));
        add(modeState);
        JPanel erasers = new JPanel(new GridLayout(2, 1, 0, 0));
        erasers.setOpaque(false);
        clearCurrent = button(new EraserIcon(false), 56, "清除当前工具标记（Shift+R）");
        clearAll = button(new EraserIcon(true), 56, "清空全部标注（R）：染色、涂鸦、链、框选；不改题目");
        clearCurrent.setPreferredSize(new Dimension(56, 18));
        clearAll.setPreferredSize(new Dimension(56, 18));
        clearCurrent.addActionListener(e -> {
            if (owner.annotationBoard() != null) owner.annotationBoard().clearCurrentAnnotationWithUndo();
        });
        clearAll.addActionListener(e -> {
            if (owner.annotationBoard() != null) owner.annotationBoard().clearAllAnnotationsWithUndo();
        });
        erasers.add(clearCurrent); erasers.add(clearAll);
        add(erasers);
        MouseWheelListener wheel = e -> {
            int forbidden = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
                    | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;
            if (owner.isDoodle() && (e.getModifiersEx() & (forbidden | InputEvent.ALT_DOWN_MASK)) == InputEvent.META_DOWN_MASK) {
                owner.annotationBoard().cycleDoodleWidth(e.getPreciseWheelRotation(), e.getWhen());
                e.consume();
            } else if (!owner.isDefaultMouse() && (e.getModifiersEx() & forbidden) == 0) {
                owner.cyclePaletteColor(e.getPreciseWheelRotation());
                e.consume();
            }
        };
        installWheel(this, wheel);
        refresh();
    }

    private JButton button(Icon icon, int width, String hint) {
        JButton button = new JButton(icon);
        button.setDisabledIcon(icon);
        button.setPreferredSize(new Dimension(width, 32));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(true);
        button.setRequestFocusEnabled(false);
        FlatToolButtonUI.install(button);
        button.setToolTipText(hint);
        button.getAccessibleContext().setAccessibleName(hint);
        return button;
    }

    private void installWheel(Component c, MouseWheelListener listener) {
        c.addMouseWheelListener(listener);
        if (c instanceof Container) for (Component child : ((Container)c).getComponents()) installWheel(child, listener);
    }

    void refresh() {
        int group = owner.getPaletteGroup();
        boolean active = !owner.isDefaultMouse();
        for (int i = 0; i < groups.length; i++) if (groups[i] != null) groups[i].setSelected(active && i == group);
        primary.setEnabled(active);
        for (JToggleButton button : groups) button.setEnabled(active);
        secondary.setEnabled(active && owner.supportsSecondaryColor());
        swap.setEnabled(active && owner.supportsSecondaryColor());
        clearCurrent.setEnabled(active);
        SudokuPanel board = owner.annotationBoard();
        String state = owner.isFreeChain() && board != null
                ? (board.isNextUserChainStrong() ? "下一条：实线强链（=）" : "下一条：虚线弱链（-）")
                : owner.isDoodle() && board != null ? "涂鸦粗细：" + (board.getDoodleWidthIndex() + 1) + " / 4（点击循环；Command+滚轮或 < / > 调整）"
                : "当前模式无附加笔触状态";
        modeState.setEnabled(owner.isDoodle());
        modeState.setToolTipText(state);
        modeState.getAccessibleContext().setAccessibleName(state);
        repaint();
    }

    private final class SwatchIcon implements Icon {
        private final boolean secondary;
        SwatchIcon(boolean secondary) { this.secondary = secondary; }
        public int getIconWidth() { return 18; }
        public int getIconHeight() { return 18; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            if (!c.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(0.28f));
            try {
            g.setColor(secondary ? owner.getSecondaryColor() : owner.getPrimaryColor());
            g.fillRect(x, y, 18, 18);
            g.setColor(ApplicationAppearance.isDark() ? new Color(255,255,255,100) : new Color(0,0,0,80));
            g.drawRect(x, y, 17, 17);
            } finally { g.dispose(); }
        }
    }

    private static final class PairIcon implements Icon {
        private final int group;
        PairIcon(int group) { this.group = group; }
        public int getIconWidth() { return 14; }
        public int getIconHeight() { return 24; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            if (!c.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(0.28f));
            try {
            Color[] colors = Options.getInstance().getColoringColors();
            for (int shade = 0; shade < 2; shade++) {
                g.setColor(colors[group * 2 + shade]);
                g.fillRect(x, y + shade * 12, 14, 11);
                g.setColor(ApplicationAppearance.isDark() ? new Color(255,255,255,60) : new Color(0,0,0,45));
                g.drawRect(x, y + shade * 12, 13, 10);
            }
            } finally { g.dispose(); }
        }
    }

    private final class ModeStateIcon implements Icon {
        public int getIconWidth() { return 30; }
        public int getIconHeight() { return 32; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            SudokuPanel board = owner.annotationBoard();
            if (board == null || (!owner.isFreeChain() && !owner.isDoodle())) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(SudokuAppearancePalette.forRendering(false).getPrimaryForeground());
                if (owner.isFreeChain()) {
                    g.setFont(c.getFont().deriveFont(Font.BOLD, 19f));
                    String symbol = board.isNextUserChainStrong() ? "=" : "-";
                    g.drawString(symbol, (30-g.getFontMetrics().stringWidth(symbol))/2, 23);
                } else {
                    int width = board.getDoodleWidthIndex()+1;
                    g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(6, 11, 24, 11);
                    g.setFont(c.getFont().deriveFont(Font.PLAIN, 9f));
                    g.drawString(Integer.toString(width), 12, 27);
                }
            } finally { g.dispose(); }
        }
    }

    private static final class EraserIcon implements Icon {
        private final boolean all;
        EraserIcon(boolean all) { this.all = all; }
        public int getIconWidth() { return 54; }
        public int getIconHeight() { return 18; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if (!c.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(0.28f));
                boolean dark = ApplicationAppearance.isDark();
                Polygon body = new Polygon(new int[]{3,24,30,9}, new int[]{5,2,12,15},4);
                g.setColor(dark ? new Color(109,156,163) : new Color(77,118,126));
                g.fillPolygon(body);
                g.setColor(dark ? new Color(211,220,223) : new Color(231,235,237));
                g.fillPolygon(new int[]{3,10,16,9},new int[]{5,4,14,15},4);
                g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(dark ? new Color(255,255,255,105) : new Color(38,52,59,125));
                g.drawPolygon(body);g.drawLine(10,4,16,14);
                g.setColor(new Color(255,255,255,100));g.drawLine(5,5,23,3);
                if (all) {
                    g.setColor(dark ? new Color(19,39,44) : Color.WHITE);
                    g.setFont(c.getFont().deriveFont(Font.BOLD, 7.5f));
                    g.drawString("all", 15, 10);
                }
                g.setColor(SudokuAppearancePalette.forRendering(false).getPrimaryForeground());
                g.setFont(c.getFont().deriveFont(Font.PLAIN, 9f));
                g.drawString(all ? "R" : "⇧R", 35, 12);
            } finally { g.dispose(); }
        }
    }

    private static final class SwapIcon implements Icon {
        public int getIconWidth() { return 26; }
        public int getIconHeight() { return 32; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D)graphics.create();
            try {
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(c.isEnabled() ? SudokuAppearancePalette.forRendering(false).getPrimaryForeground() : Color.GRAY);
                g.setFont(c.getFont().deriveFont(Font.PLAIN, 8f));
                g.drawString("Opt", (26 - g.getFontMetrics().stringWidth("Opt")) / 2, 8);
                g.drawString("X", (26 - g.getFontMetrics().stringWidth("X")) / 2, 30);
                g.setStroke(new BasicStroke(1.5f));
                g.drawLine(6, 13, 20, 13); g.drawLine(17, 10, 20, 13); g.drawLine(17, 16, 20, 13);
                g.drawLine(6, 19, 20, 19); g.drawLine(6, 19, 9, 16); g.drawLine(6, 19, 9, 22);
            } finally { g.dispose(); }
        }
    }
}
