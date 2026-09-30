package sudoku;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/** Tool attributes above the board and a compact status palette share the tool-local state. */
final class ToolbarColorPalette extends JPanel {
    private final CellZoomPanel owner;
    private final JPanel statusPalette = new JPanel(new FlowLayout(FlowLayout.LEADING, 1, 0));
    private final JButton swap;
    private final JButton clearAll;
    private final JButton modeState;
    private final JToggleButton freeEraser;
    private final JButton boxStatistics;
    private final JToggleButton[] groups = new JToggleButton[6];

    ToolbarColorPalette(CellZoomPanel owner) {
        super(new FlowLayout(FlowLayout.LEADING, 0, 0));
        this.owner = owner;
        setOpaque(false);
        modeState = new JButton(new ModeStateIcon());
        modeState.setDisabledIcon(modeState.getIcon());
        modeState.setPreferredSize(new Dimension(38, 38));
        modeState.setMargin(new Insets(2, 2, 2, 2));
        modeState.setFocusable(false);
        FlatToolButtonUI.install(modeState);
        modeState.addActionListener(e -> {
            SudokuPanel board = owner.annotationBoard();
            if (owner.isDoodle() && board != null && !board.isDoodleErasing()) board.cycleDoodleWidthByClick();
        });
        modeState.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (owner.annotationBoard() != null) owner.annotationBoard().setChainJunctionHover(true);
            }
            @Override public void mouseExited(MouseEvent e) {
                if (owner.annotationBoard() != null) owner.annotationBoard().setChainJunctionHover(false);
            }
        });
        modeState.addMouseWheelListener(e -> {
            SudokuPanel board = owner.annotationBoard();
            if (!owner.isDoodle() || board == null) return;
            int modifiers = e.getModifiersEx();
            if (modifiers == InputEvent.CTRL_DOWN_MASK) {
                board.cycleDoodleEraser(e.getPreciseWheelRotation(), e.getWhen());
                e.consume();
            } else if (modifiers == InputEvent.META_DOWN_MASK) {
                board.cycleDoodleWidth(e.getPreciseWheelRotation(), e.getWhen());
                e.consume();
            }
        });
        add(modeState);
        freeEraser=new JToggleButton(java.util.ResourceBundle.getBundle("intl/CellZoomPanel").getString("CellZoomPanel.doodleEraser.text"));
        freeEraser.setFocusable(false);
        freeEraser.setToolTipText("左键连续擦除；右拖框擦。再次点击返回画笔");
        freeEraser.addActionListener(e -> {if(owner.annotationBoard()!=null)owner.annotationBoard().setDoodleFreeEraser(freeEraser.isSelected());});
        add(freeEraser);
        boxStatistics=new JButton(java.util.ResourceBundle.getBundle("intl/CellZoomPanel").getString("CellZoomPanel.inspectBox.text"));
        boxStatistics.setFocusable(false);
        boxStatistics.addActionListener(e -> {if(owner.annotationBoard()!=null)owner.annotationBoard().inspectCurrentBoxGroup();});
        add(boxStatistics);

        statusPalette.setOpaque(false);
        swap = new JButton(new StatusSwatch(-1, 30));
        configureStatusButton(swap, 30, "交换主副色（X）；Option／中键按住预览");
        swap.addActionListener(e -> owner.swapColors());
        statusPalette.add(swap);
        for (int i = 0; i < groups.length; i++) {
            final int group = i;
            JToggleButton button = new JToggleButton(new StatusSwatch(i, 15));
            configureStatusButton(button, 15, "颜色组 " + (char)('A' + i) + "；滚轮切换色组");
            button.addActionListener(e -> owner.selectPaletteGroup(group));
            groups[i] = button;
            statusPalette.add(button);
        }
        clearAll = new JButton("R");
        configureStatusButton(clearAll, 15, "清空全部标注（R）：染色、涂鸦、链、框选；不改题目");
        clearAll.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        clearAll.addActionListener(e -> {
            if (owner.annotationBoard() != null) owner.annotationBoard().clearAllAnnotationsWithUndo();
        });
        statusPalette.add(clearAll);
        MouseWheelListener wheel = e -> {
            if(e.isAltDown()||owner.annotationBoard()!=null && owner.annotationBoard().isAnnotationPreviewHeld())return;
            int forbidden = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
                    | InputEvent.META_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;
            if (!owner.isDefaultMouse() && (e.getModifiersEx() & forbidden) == 0) {
                owner.setPaletteOptionDown(false);
                owner.cyclePaletteColor(e.getPreciseWheelRotation());
                e.consume();
            }
        };
        swap.addMouseWheelListener(wheel);
        for (JToggleButton button : groups) button.addMouseWheelListener(wheel);
        statusPalette.addMouseWheelListener(wheel);
        refresh();
    }

    JPanel getStatusPalette() { return statusPalette; }

    private void configureStatusButton(AbstractButton button, int width, String hint) {
        button.setPreferredSize(new Dimension(width, 15));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setContentAreaFilled(false);
        button.setFocusable(true);
        button.setRequestFocusEnabled(false);
        button.setFocusPainted(false);
        button.setDisabledIcon(button.getIcon());
        button.setToolTipText(hint);
        button.getAccessibleContext().setAccessibleName(hint);
        if (button.getIcon() == null) {
            button.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        }
    }

    void refresh() {
        boolean active = !owner.isDefaultMouse();
        for (int i = 0; i < groups.length; i++) {
            groups[i].setSelected(active && i == owner.getPaletteGroup());
            groups[i].setEnabled(active);
        }
        swap.setEnabled(active && owner.supportsSecondaryColor());
        String swapHint = !active ? "鼠标模式下不可选择颜色"
                : !owner.supportsSecondaryColor() ? "当前工具使用固定色对，不交换主副色"
                : "交换主副色（X）；Option／中键按住预览";
        swap.setToolTipText(swapHint);
        swap.getAccessibleContext().setAccessibleName(swapHint);
        SudokuPanel board = owner.annotationBoard();
        if (board != null && !owner.isFreeChain()) board.setChainJunctionHover(false);
        freeEraser.setVisible(owner.isDoodle()&&(board==null||!board.usesSweepEraseVariant()));
        freeEraser.setSelected(board!=null&&board.isDoodleFreeEraser());
        boxStatistics.setVisible(false);
        boolean erasing = owner.isDoodle() && board != null && board.isDoodleErasing();
        String state = owner.isFreeChain() && board != null
                ? (board.chainEndpointState().problem().isEmpty()?board.chainJunctionSummary().description():board.chainEndpointState().problem())
                : erasing ? "涂鸦橡皮擦：直径 " + eraserDiameter(board) + "% 棋盘短边（右键轨迹擦除时滚轮连续调整；右键双击并按住第二下拖框删除）"
                : owner.isDoodle() && board != null ? "涂鸦粗细：" + (board.getDoodleWidthIndex() + 1) + " / 4（点击循环；Command+滚轮或 < / > 调整）"
                : "当前模式无附加笔触状态";
        modeState.setEnabled(owner.isDoodle() && !erasing);
        modeState.setToolTipText(state);
        modeState.getAccessibleContext().setAccessibleName(state);
        clearAll.setForeground(SudokuAppearancePalette.forRendering(false).getPrimaryForeground());
        repaint();
        statusPalette.repaint();
    }

    private static String eraserDiameter(SudokuPanel board) {
        return String.format(java.util.Locale.ROOT, "%.1f", board.getDoodleEraserRadius() * 200);
    }

    private final class StatusSwatch implements Icon {
        private final int group, width;
        StatusSwatch(int group, int width) { this.group = group; this.width = width; }
        public int getIconWidth() { return width; }
        public int getIconHeight() { return 15; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                Color background = SudokuAppearancePalette.forRendering(false).getSurfaceBackground();
                g.setColor(background); g.fillRect(0, 0, width, 15);
                if (owner.isDefaultMouse()) g.setComposite(AlphaComposite.SrcOver.derive(0.25f));
                g.setColor(group < 0 ? owner.getPrimaryColor() : owner.getDisplayedPaletteColor(group));
                g.fillRect(0, 0, width, 15);
                if (group < 0) {
                    g.setColor(owner.getSecondaryColor()); g.fillRect(width - 9, 0, 9, 15);
                }
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(ApplicationAppearance.isDark() ? new Color(115, 120, 128) : Color.GRAY);
                g.drawRect(0, 0, width - 1, 14);
                if (((AbstractButton)c).isSelected() || c.isFocusOwner()) {
                    g.setColor(SudokuAppearancePalette.forRendering(false).getPrimaryForeground());
                    g.drawRect(1, 1, width - 3, 12);
                    g.setColor(background); g.drawRect(2, 2, width - 5, 10);
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
                    String problem=board.chainEndpointState().problem();
                    if(!problem.isEmpty()){
                        g.setFont(c.getFont().deriveFont(Font.PLAIN,10f));
                        String[] lines=problem.equals("多段未连接")?new String[]{"多段","未连接"}:problem.equals("存在分叉")?new String[]{"存在","分叉"}:new String[]{"链端点","待整理"};
                        for(int i=0;i<2;i++)g.drawString(lines[i],(30-g.getFontMetrics().stringWidth(lines[i]))/2,12+i*15);
                        return;
                    }
                    ChainJunctionSummary summary = board.chainJunctionSummary();
                    for (int row = 0; row < 2; row++) {
                        int count = row == 0 ? summary.strong.size() : summary.weak.size();
                        String label = (row == 0 ? "强·" : "弱·") + (summary.available() ? Integer.toString(count) : "—");
                        g.setFont(c.getFont().deriveFont(count > 0 ? Font.BOLD : Font.PLAIN, 11f));
                        int textWidth = g.getFontMetrics().stringWidth(label);
                        if (textWidth > 30) g.setFont(g.getFont().deriveFont(11f * 30 / textWidth));
                        g.setColor(count > 0 ? (ApplicationAppearance.isDark() ? new Color(255,190,80) : new Color(170,100,0))
                                : SudokuAppearancePalette.forRendering(false).getPrimaryForeground());
                        g.setComposite(AlphaComposite.SrcOver.derive(count > 0 ? 1f : 0.5f));
                        g.drawString(label, (30-g.getFontMetrics().stringWidth(label))/2, 12 + row*15);
                    }
                } else if (board.isDoodleErasing()) {
                    g.drawOval(10, 2, 10, 10);
                    g.setFont(c.getFont().deriveFont(Font.PLAIN, 9f));
                    String value = eraserDiameter(board) + "%";
                    g.drawString(value, (30-g.getFontMetrics().stringWidth(value))/2, 27);
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
}
