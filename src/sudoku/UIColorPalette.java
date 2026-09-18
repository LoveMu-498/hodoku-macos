/**
 * Copyright (C) 2019-20 PseudoFish
 */

package sudoku;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class UIColorPalette extends JPanel implements MouseListener, ActionListener {

	private static final long serialVersionUID = -7096778906107607575L;
	
	private static final int DEFAULT_BUTTON_SIZE = 42;
	private static final int PANEL_SIZE = DEFAULT_BUTTON_SIZE + DEFAULT_BUTTON_SIZE/2;	

	private UIBorderedImagePanel primaryColor;
	private UIBorderedImagePanel secondaryColor;
	private UIBorderedImagePanel switchColor;
	private UIBorderedImagePanel resetButton;
	private JDialog dialog;
	private JButton dialogButtonOK;
	private JButton dialogButtonCancel;
	private JButton dialogButtonReset;
	private Color initialColor;
	private Color selectedColor;
	private JColorChooser colorChooser;
	private SudokuPanel sudokuPanel;
	private CellZoomPanel cellZoomPanel;
	private ResourceBundle bundle;
	private boolean pairOnly;
	private int pairOnlyGroup = -1;
	
	UIColorPalette(CellZoomPanel cellZoomPanel) {
		
		super(true);
		
		this.setSize(PANEL_SIZE, PANEL_SIZE);
		this.setLayout(null);
		
		this.cellZoomPanel = cellZoomPanel;
		
		bundle = ResourceBundle.getBundle("intl/UIColorPalette");
		
		dialog = null;
		dialogButtonOK = new JButton(bundle.getString("dialogButtonOK.text"));
		dialogButtonCancel = new JButton(bundle.getString("dialogButtonCancel.text"));
		dialogButtonReset = new JButton(bundle.getString("dialogButtonReset.text"));
		initialColor = Color.white;
		selectedColor = Color.white;
		colorChooser = new JColorChooser(initialColor);
		
		dialogButtonOK.addActionListener(this);
		dialogButtonCancel.addActionListener(this);
		dialogButtonReset.addActionListener(this);
		
		primaryColor = new UIBorderedImagePanel();
		primaryColor.setBackground(Options.DEFAULT_PRIMARY_COLOR);
		primaryColor.setSize(DEFAULT_BUTTON_SIZE, DEFAULT_BUTTON_SIZE);
		primaryColor.setLocation(0, 0);
		primaryColor.setToolTipText(bundle.getString("primaryColor.tooltip"));
		primaryColor.getAccessibleContext().setAccessibleName(bundle.getString("primaryColor.tooltip"));
		primaryColor.addMouseListener(this);
		add(primaryColor);
		
		secondaryColor = new UIBorderedImagePanel();
		secondaryColor.setBackground(Options.DEFAULT_SECONDARY_COLOR);
		secondaryColor.setSize(DEFAULT_BUTTON_SIZE, DEFAULT_BUTTON_SIZE);
		secondaryColor.setLocation(DEFAULT_BUTTON_SIZE/2, DEFAULT_BUTTON_SIZE/2);
		secondaryColor.setToolTipText(bundle.getString("secondaryColor.tooltip"));
		secondaryColor.getAccessibleContext().setAccessibleName(bundle.getString("secondaryColor.tooltip"));
		secondaryColor.addMouseListener(this);
		add(secondaryColor);
		
		Image switchImage = ToolbarIcons.swapColor();
		Image resetImage = ToolbarIcons.clearColoring();
		int offset = PANEL_SIZE - switchImage.getWidth(null);
		
		switchColor = new UIBorderedImagePanel(switchImage);
		switchColor.setLocation(offset, 0);
		switchColor.setToolTipText(bundle.getString("switchColor.tooltip"));
		switchColor.getAccessibleContext().setAccessibleName(bundle.getString("switchColor.tooltip"));
		switchColor.addMouseListener(this);
		add(switchColor);
		
		resetButton = new UIBorderedImagePanel(resetImage);
		resetButton.setLocation(0, offset);
		resetButton.setBorderVisible(true);
		resetButton.setToolTipText(bundle.getString("resetButton.tooltip"));
		resetButton.getAccessibleContext().setAccessibleName(bundle.getString("resetButton.tooltip"));
		resetButton.addMouseListener(this);
		add(resetButton);

		MouseWheelListener wheelListener = new MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent evt) {
				int modifiers = evt.getModifiersEx();
				int relevant = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
						| InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK
						| InputEvent.ALT_GRAPH_DOWN_MASK;
				if ((cellZoomPanel.isColoring() || cellZoomPanel.isDoodle()
						|| cellZoomPanel.isFreeChain() || cellZoomPanel.isBoxSelection())
						&& (modifiers & (relevant & ~InputEvent.ALT_DOWN_MASK)) == 0) {
					cellZoomPanel.cyclePaletteColor(evt.getWheelRotation(),
							(modifiers & InputEvent.ALT_DOWN_MASK) != 0);
					evt.consume();
				}
			}
		};
		addMouseWheelListener(wheelListener);
		primaryColor.addMouseWheelListener(wheelListener);
		secondaryColor.addMouseWheelListener(wheelListener);
		switchColor.addMouseWheelListener(wheelListener);
		resetButton.addMouseWheelListener(wheelListener);
	}
	
	public void setPrimaryColor(Color color) {
		primaryColor.setBackground(color);
	}
	
	public Color getPrimaryColor() {
		return primaryColor.getBackground();
	}
	
	public Color getSecondaryColor() {
		return secondaryColor.getBackground();
	}

	public void setSecondaryColor(Color color) {
		secondaryColor.setBackground(color);
	}

	void showSelection(Color primary, Color secondary) {
		if (!Objects.equals(primary, primaryColor.getBackground())) primaryColor.setBackground(primary);
		if (!Objects.equals(secondary, secondaryColor.getBackground())) secondaryColor.setBackground(secondary);
	}

	void setPairOnly(boolean value, int group) {
		int normalizedGroup = Math.max(0, Math.min(5, group));
		if (pairOnly == value && (!value || pairOnlyGroup == normalizedGroup)) return;
		pairOnly = value;
		pairOnlyGroup = normalizedGroup;
		secondaryColor.setEnabled(!value);
		switchColor.setEnabled(!value);
		String primaryTooltip = value
				? MessageFormat.format(bundle.getString("primaryColor.groupOnly.tooltip"),
						Character.toString((char) ('A' + normalizedGroup)))
				: bundle.getString("primaryColor.tooltip");
		String secondaryTooltip = value ? bundle.getString("secondaryColor.groupOnly.tooltip")
				: bundle.getString("secondaryColor.tooltip");
		String switchTooltip = value ? bundle.getString("switchColor.groupOnly.tooltip")
				: bundle.getString("switchColor.tooltip");
		primaryColor.setToolTipText(primaryTooltip);
		secondaryColor.setToolTipText(secondaryTooltip);
		switchColor.setToolTipText(switchTooltip);
		primaryColor.getAccessibleContext().setAccessibleName(primaryTooltip);
		primaryColor.getAccessibleContext().setAccessibleDescription(primaryTooltip);
		secondaryColor.getAccessibleContext().setAccessibleName(secondaryTooltip);
		secondaryColor.getAccessibleContext().setAccessibleDescription(secondaryTooltip);
		switchColor.getAccessibleContext().setAccessibleName(switchTooltip);
		switchColor.getAccessibleContext().setAccessibleDescription(switchTooltip);
	}
	
	public void swap() {
		cellZoomPanel.swapColors();
	}
	
	private void clearColor(Color c) {
		if (cellZoomPanel.isColoringCells()) {
			sudokuPanel.clearCellColor(c);
		} else if (cellZoomPanel.isColoringCandidates()) {
			sudokuPanel.clearCandidateColor(c);
		}
	}
	
	void clearColoring() {
		if (sudokuPanel.getAnnotationTool() == AnnotationTool.DOODLE) {
			sudokuPanel.clearDoodlesWithUndo();
		} else if (sudokuPanel.getAnnotationTool() == AnnotationTool.FREE_CHAIN) {
			sudokuPanel.clearUserChainsWithUndo();
		} else if (sudokuPanel.getAnnotationTool() == AnnotationTool.BOX_SELECTION) {
			sudokuPanel.clearBoxReasoningWithUndo();
		} else if (sudokuPanel.hasColoring()) {
			sudokuPanel.clearColoringWithUndo();
		}
		sudokuPanel.repaint();
	}
	
    void chooseToolbarColor(boolean secondary, boolean clear) {
        if (secondary && pairOnly) return;
        Color current = secondary ? getSecondaryColor() : getPrimaryColor();
        if (clear) clearColor(current);
        else if (secondary) cellZoomPanel.setSecondaryColor(showColorChooserDialog(current));
        else cellZoomPanel.setPrimaryColor(showColorChooserDialog(current));
        if (sudokuPanel != null) sudokuPanel.updateColorCursor();
    }

	private Color showColorChooserDialog(Color currentColor) {
		
		initialColor = currentColor;
		selectedColor = currentColor;
		colorChooser.setColor(currentColor);
		
		JButton[] buttons = {
			dialogButtonOK, 
			dialogButtonCancel, 
			dialogButtonReset
		};
		
		JOptionPane optionPane = new JOptionPane();
		optionPane.setMessage(colorChooser);
		optionPane.setMessageType(JOptionPane.PLAIN_MESSAGE);
		optionPane.setOptions(buttons);
		optionPane.setOptionType(JOptionPane.NO_OPTION);
		optionPane.setInitialValue(buttons[0]);
		optionPane.setInitialSelectionValue(dialogButtonOK);
		
		dialog = optionPane.createDialog(bundle.getString("optionPane.title"));
		dialog.setVisible(true);
		
		return selectedColor;
	}
	
	public void setSudokuPanel(SudokuPanel sudokuPanel) {
		this.sudokuPanel = sudokuPanel;
	}

	/** Gives the small action glyphs an opaque, high-contrast dark-mode surface. */
	public void applyAppearance() {
		if (!ApplicationAppearance.isDark()) {
			return;
		}
		Color control = SudokuAppearancePalette.forRendering(false).getControlBackground();
		switchColor.setBackground(control);
		switchColor.setBorderVisible(true);
		resetButton.setBackground(control);
		resetButton.setBorderVisible(true);
	}
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		
		if (e.getSource() == dialogButtonOK) {
			selectedColor = colorChooser.getColor();
			dialog.setVisible(false);
		} else if (e.getSource() == dialogButtonCancel) {
			dialog.setVisible(false);
		} else if (e.getSource() == dialogButtonReset) {
			selectedColor = initialColor;
			colorChooser.setColor(initialColor);
		} else {
			dialog.setVisible(false);
		}
		
		repaint();
	}

	@Override
	public void mousePressed(MouseEvent e) {
		
		if (e.getSource() == primaryColor) {
			
			if (SudokuUtil.isMenuShortcutDown(e)) {
				clearColor(primaryColor.getBackground());
			} else {
				cellZoomPanel.setPrimaryColor(showColorChooserDialog(primaryColor.getBackground()));
				if (!cellZoomPanel.isDefaultMouse()) {
					sudokuPanel.updateColorCursor();
				}
			}
			
			repaint();
			
		} else if (e.getSource() == secondaryColor) {
			if (pairOnly) return;
			
			if (SudokuUtil.isMenuShortcutDown(e)) {
				clearColor(secondaryColor.getBackground());
			} else {
				cellZoomPanel.setSecondaryColor(showColorChooserDialog(secondaryColor.getBackground()));
				if (!cellZoomPanel.isDefaultMouse()) {
					sudokuPanel.updateColorCursor();
				}
			}
			
			repaint();
			
		} else if (e.getSource() == switchColor) {
			if (pairOnly) return;
			swap();
		} else if (e.getSource() == resetButton) {
			clearColoring();
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {}
	@Override
	public void mouseEntered(MouseEvent e) {}
	@Override
	public void mouseExited(MouseEvent e) {}
	@Override
	public void mouseReleased(MouseEvent e) {}
}
