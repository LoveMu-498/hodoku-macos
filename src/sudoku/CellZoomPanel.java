/*
 * Copyright (C) 2019-20  PseudoFish
 * Copyright (C) 2008-12  Bernhard Hobiger
 *
 * This file is part of HoDoKu.
 *
 * HoDoKu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HoDoKu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HoDoKu. If not, see <http://www.gnu.org/licenses/>.
 */
package sudoku;

import java.awt.Color;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ResourceBundle;
import java.util.SortedMap;

import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JToggleButton;

/**
 *
 * @author hobiwan
 */
@SuppressWarnings("serial")
public class CellZoomPanel extends JPanel implements ActionListener {

	private static final int X_OFFSET = 10;
	private static final int Y_OFFSET = 33;
	private static final int SMALL_GAP = 6;
	private static final int LARGE_GAP = 14;
	private static final int COLOR_PANEL_MAX_HEIGHT = 50;
	private static final int GRID_MIN_SIZE = 96;
	private static final int GRID_MAX_SIZE = 132;
	private static final int TOOL_SELECTOR_HEIGHT = 34;
	private static final int TOOL_SELECTOR_BUTTON_SIZE = 34;
	private static final int TOOL_SELECTOR_ICON_SIZE = 26;
	private static final int COLOR_PALETTE_SIZE = 63;
	private static final int DIFF_SIZE = 1;
	private static final String[] NUMBERS = new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9" };
	private static final int COLOR_BUTTON_COUNT = 12;
	
	private MainFrame mainFrame;
	private Font buttonFont = null;
	private Font iconFont = null;
	private int buttonFontSize = -1;
	private int defaultButtonFontSize = -1;
	private int defaultButtonHeight = -1;
	private JButton[] setValueButtons = null;
	private JButton[] toggleCandidatesButtons = null;
	private JToggleButton[] cellPanels = null;
	private ButtonGroup colorButtonGroup;
	private Color normButtonForeground = null;
	private Color normButtonBackground = null;
	private SudokuPanel sudokuPanel;
	private long lastPaletteWheelAt;
    private final AnnotationWheelGate paletteWheelGate = new AnnotationWheelGate();
	private AnnotationPaletteOwner effectivePaletteOwner = AnnotationPaletteOwner.CANDIDATE_COLORING;
	private int colorImageHeight = -1;
	private Icon[] colorKuIcons = new Icon[9];
	
	private javax.swing.JPanel chooseColorPanel;
	private javax.swing.JButton jFontButton;
	private javax.swing.JPanel jPanel1;
	private javax.swing.JButton setValueButton1;
	private javax.swing.JButton setValueButton2;
	private javax.swing.JButton setValueButton3;
	private javax.swing.JButton setValueButton4;
	private javax.swing.JButton setValueButton5;
	private javax.swing.JButton setValueButton6;
	private javax.swing.JButton setValueButton7;
	private javax.swing.JButton setValueButton8;
	private javax.swing.JButton setValueButton9;
	private javax.swing.JLabel setValueLabel;
	private javax.swing.JPanel setValuePanel;
	private javax.swing.JLabel titleLabel;
	private javax.swing.JButton toggleCandidatesButton1;
	private javax.swing.JButton toggleCandidatesButton2;
	private javax.swing.JButton toggleCandidatesButton3;
	private javax.swing.JButton toggleCandidatesButton4;
	private javax.swing.JButton toggleCandidatesButton5;
	private javax.swing.JButton toggleCandidatesButton6;
	private javax.swing.JButton toggleCandidatesButton7;
	private javax.swing.JButton toggleCandidatesButton8;
	private javax.swing.JButton toggleCandidatesButton9;
	private javax.swing.JLabel toggleCandidatesLabel;
	private javax.swing.JPanel toggleCandidatesPanel;
	private JPanel radioButtonPanel;
	private ButtonGroup radioButtonGroup;
	private JRadioButton radioButtonDefault;
	private JRadioButton radioButtonColorCells;
	private JRadioButton radioButtonColorCandidates;
	private JRadioButton radioButtonDoodle;
	private JRadioButton radioButtonFreeChain;
	private JRadioButton radioButtonBoxSelection;
	private UIColorPalette colorPalette;
	private ToolbarColorPalette toolbarPalette;
	private boolean toolbarPaletteVisible;
	private UIColorTools colorTools;
	private JPanel annotationCardPanel;
	private CardLayout annotationCardLayout;
	private JComboBox<String> doodleWidthCombo;
	private JToggleButton chainStrongButton;
	private JToggleButton chainWeakButton;
	private JPanel chainCard;
	private String chainAnalysisText = "尚未完成链";

	/**
	 * Creates new form CellZoomPanel
	 * 
	 * @param mainFrame
	 */
	public CellZoomPanel(MainFrame mainFrame) {
		
		this.mainFrame = mainFrame;
		
		cellPanels = new JToggleButton[COLOR_BUTTON_COUNT];
		
		initComponents();

		setValueButtons = new JButton[] {
			setValueButton1, setValueButton2, setValueButton3, 
			setValueButton4, setValueButton5, setValueButton6, 
			setValueButton7, setValueButton8, setValueButton9 
		};
		
		toggleCandidatesButtons = new JButton[] { 
			toggleCandidatesButton1, toggleCandidatesButton2, toggleCandidatesButton3, 
			toggleCandidatesButton4, toggleCandidatesButton5, toggleCandidatesButton6,
			toggleCandidatesButton7, toggleCandidatesButton8, toggleCandidatesButton9 
		};
		
		normButtonForeground = setValueButton1.getForeground();
		normButtonBackground = setValueButton1.getBackground();

		jFontButton.setVisible(false);
		buttonFont = jFontButton.getFont();
		buttonFontSize = 11;
		defaultButtonFontSize = buttonFontSize;
		defaultButtonHeight = 23;
		iconFont = new Font(buttonFont.getName(), buttonFont.getStyle(), defaultButtonFontSize - DIFF_SIZE);

		int fontSize = 12;
		if (getFont().getSize() > 12) {
			fontSize = getFont().getSize();
		}
		
		Font font = titleLabel.getFont();
		titleLabel.setFont(new Font(font.getName(), Font.BOLD, fontSize));

		calculateLayout();
		applyAppearance();
	}
	
	private JToggleButton createColorButtonPanel(final int id) {
		JToggleButton button = new PaletteSlotButton(id);
		button.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent event) {
				handleColorChange(id, SudokuUtil.isMenuShortcutDown(event.getModifiers()));
			}
		});
		return button;
	}

	private void initComponents() {

		jPanel1 = new javax.swing.JPanel();
		titleLabel = new javax.swing.JLabel();
		setValueLabel = new javax.swing.JLabel();
		setValuePanel = new javax.swing.JPanel();
		setValueButton1 = new javax.swing.JButton();
		setValueButton2 = new javax.swing.JButton();
		setValueButton3 = new javax.swing.JButton();
		setValueButton4 = new javax.swing.JButton();
		setValueButton5 = new javax.swing.JButton();
		setValueButton6 = new javax.swing.JButton();
		setValueButton7 = new javax.swing.JButton();
		setValueButton8 = new javax.swing.JButton();
		setValueButton9 = new javax.swing.JButton();
		toggleCandidatesLabel = new javax.swing.JLabel();
		toggleCandidatesPanel = new javax.swing.JPanel();
		toggleCandidatesButton1 = new javax.swing.JButton();
		toggleCandidatesButton2 = new javax.swing.JButton();
		toggleCandidatesButton3 = new javax.swing.JButton();
		toggleCandidatesButton4 = new javax.swing.JButton();
		toggleCandidatesButton5 = new javax.swing.JButton();
		toggleCandidatesButton6 = new javax.swing.JButton();
		toggleCandidatesButton7 = new javax.swing.JButton();
		toggleCandidatesButton8 = new javax.swing.JButton();
		toggleCandidatesButton9 = new javax.swing.JButton();
		chooseColorPanel = new javax.swing.JPanel();
		jFontButton = new javax.swing.JButton();
		
		colorPalette = new UIColorPalette(this);
		add(colorPalette);
		
		colorTools = new UIColorTools();
		add(colorTools);
		
		ResourceBundle bundle = java.util.ResourceBundle.getBundle("intl/CellZoomPanel");
		String defaultText = bundle.getString("CellZoomPanel.radioButtonDefault.text");
		String colorCandidatesText = bundle.getString("CellZoomPanel.radioButtonColorCandidates.text");
		String colorCellsText = bundle.getString("CellZoomPanel.radioButtonColorCells.text");
		String doodleText = bundle.getString("CellZoomPanel.radioButtonDoodle.text");
		String freeChainText = bundle.getString("CellZoomPanel.radioButtonFreeChain.text");
		String boxSelectionText = bundle.getString("CellZoomPanel.radioButtonBoxSelection.text");
		
		radioButtonPanel = new JPanel(new GridLayout(1, 5, 2, 0));
		radioButtonPanel.setSize(6 * TOOL_SELECTOR_BUTTON_SIZE, TOOL_SELECTOR_HEIGHT);
		radioButtonGroup = new ButtonGroup();
		radioButtonDefault = new JRadioButton();
		radioButtonDefault.setIcon(new AnnotationToolIcon(
				AnnotationTool.DEFAULT_MOUSE, TOOL_SELECTOR_ICON_SIZE));
		radioButtonDefault.setToolTipText(defaultText + " (M)");
		radioButtonDefault.getAccessibleContext().setAccessibleName(defaultText + " (M)");
		radioButtonDefault.setSelected(true);
		radioButtonColorCandidates = new JRadioButton();
		radioButtonColorCandidates.setIcon(new AnnotationToolIcon(
				AnnotationTool.CANDIDATE_COLORING, TOOL_SELECTOR_ICON_SIZE));
		radioButtonColorCandidates.setToolTipText(colorCandidatesText + " (T)");
		radioButtonColorCandidates.getAccessibleContext().setAccessibleName(colorCandidatesText + " (T)");
		radioButtonColorCells = new JRadioButton();
		radioButtonColorCells.setIcon(new AnnotationToolIcon(
				AnnotationTool.CELL_COLORING, TOOL_SELECTOR_ICON_SIZE));
		radioButtonColorCells.setToolTipText(colorCellsText + " (T)");
		radioButtonColorCells.getAccessibleContext().setAccessibleName(colorCellsText + " (T)");
		radioButtonDoodle = new JRadioButton();
		radioButtonDoodle.setIcon(new AnnotationToolIcon(
				AnnotationTool.DOODLE, TOOL_SELECTOR_ICON_SIZE));
		radioButtonDoodle.setToolTipText(doodleText + " (P)");
		radioButtonDoodle.getAccessibleContext().setAccessibleName(doodleText + " (P)");
		radioButtonFreeChain = new JRadioButton();
		radioButtonFreeChain.setIcon(new AnnotationToolIcon(
				AnnotationTool.FREE_CHAIN, TOOL_SELECTOR_ICON_SIZE));
		radioButtonFreeChain.setToolTipText(freeChainText + " (L)");
		radioButtonFreeChain.getAccessibleContext().setAccessibleName(freeChainText + " (L)");
		radioButtonBoxSelection = new JRadioButton();
		radioButtonBoxSelection.setIcon(new AnnotationToolIcon(
				AnnotationTool.BOX_SELECTION, TOOL_SELECTOR_ICON_SIZE));
		radioButtonBoxSelection.setToolTipText(boxSelectionText + " (S)");
		radioButtonBoxSelection.getAccessibleContext().setAccessibleName(boxSelectionText + " (S)");
		radioButtonDefault.addActionListener(this);
		radioButtonColorCells.addActionListener(this);
		radioButtonColorCandidates.addActionListener(this);
		radioButtonDoodle.addActionListener(this);
		radioButtonFreeChain.addActionListener(this);
		radioButtonBoxSelection.addActionListener(this);
		radioButtonGroup.add(radioButtonDefault);
		radioButtonGroup.add(radioButtonColorCandidates);
		radioButtonGroup.add(radioButtonColorCells);
		radioButtonGroup.add(radioButtonDoodle);
		radioButtonGroup.add(radioButtonFreeChain);
		radioButtonGroup.add(radioButtonBoxSelection);
		configureToolSelectorButton(radioButtonDefault);
		configureToolSelectorButton(radioButtonColorCandidates);
		configureToolSelectorButton(radioButtonColorCells);
		configureToolSelectorButton(radioButtonDoodle);
		configureToolSelectorButton(radioButtonFreeChain);
		configureToolSelectorButton(radioButtonBoxSelection);
		radioButtonPanel.add(radioButtonDefault);
		radioButtonPanel.add(radioButtonColorCandidates);
		// Legacy cell-coloring enum remains readable; one visible coloring entry.
		radioButtonPanel.add(radioButtonDoodle);
		radioButtonPanel.add(radioButtonFreeChain);
		radioButtonPanel.add(radioButtonBoxSelection);
		add(radioButtonPanel);

		annotationCardLayout = new CardLayout();
		annotationCardPanel = new JPanel(annotationCardLayout);
		annotationCardPanel.add(createToolCard("", null, null),
				AnnotationTool.DEFAULT_MOUSE.name());
		annotationCardPanel.add(createToolCard("", null, null),
				AnnotationTool.CANDIDATE_COLORING.name());

		JPanel doodleCard = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 1));
		doodleWidthCombo = new JComboBox<String>(new String[] { "·", "••", "●", "●●" });
		doodleWidthCombo.setSelectedIndex(1);
		doodleWidthCombo.setToolTipText(bundle.getString("CellZoomPanel.doodleWidth.tooltip"));
		doodleWidthCombo.getAccessibleContext().setAccessibleName(
				bundle.getString("CellZoomPanel.doodleWidth.tooltip"));
		doodleWidthCombo.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent event) {
				if (sudokuPanel != null) sudokuPanel.setDoodleWidthIndex(doodleWidthCombo.getSelectedIndex());
			}
		});
		doodleCard.add(doodleWidthCombo);
		annotationCardPanel.add(doodleCard, AnnotationTool.DOODLE.name());

		chainCard = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 1));
		chainStrongButton = new JToggleButton(bundle.getString("CellZoomPanel.chainStrong.short"));
		chainStrongButton.setToolTipText(bundle.getString("CellZoomPanel.chainStrong.tooltip"));
		chainStrongButton.getAccessibleContext().setAccessibleName(
				bundle.getString("CellZoomPanel.chainStrong.tooltip"));
		chainWeakButton = new JToggleButton(bundle.getString("CellZoomPanel.chainWeak.short"));
		chainWeakButton.setToolTipText(bundle.getString("CellZoomPanel.chainWeak.tooltip"));
		chainWeakButton.getAccessibleContext().setAccessibleName(
				bundle.getString("CellZoomPanel.chainWeak.tooltip"));
		ButtonGroup chainRelationGroup = new ButtonGroup();
		chainRelationGroup.add(chainStrongButton);
		chainRelationGroup.add(chainWeakButton);
		chainStrongButton.setSelected(true);
		configureCompactToggleButton(chainStrongButton);
		configureCompactToggleButton(chainWeakButton);
		chainStrongButton.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent event) { sudokuPanel.setNextUserChainStrong(true); }
		});
		chainWeakButton.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent event) { sudokuPanel.setNextUserChainStrong(false); }
		});
		chainCard.add(chainStrongButton);
		chainCard.add(chainWeakButton);
		JButton clearChains = new JButton(bundle.getString("CellZoomPanel.clear.text"));
		clearChains.getAccessibleContext().setAccessibleName(
				bundle.getString("CellZoomPanel.clearChains.accessible"));
		clearChains.addActionListener(new ActionListener() {
			@Override public void actionPerformed(ActionEvent event) { sudokuPanel.clearUserChainsWithUndo(); }
		});
		chainCard.add(clearChains);
		chainCard.setToolTipText(chainAnalysisText);
		annotationCardPanel.add(chainCard, AnnotationTool.FREE_CHAIN.name());
		annotationCardPanel.add(createToolCard(bundle.getString("CellZoomPanel.boxGroups.text"),
				bundle.getString("CellZoomPanel.clear.text"), new ActionListener() {
			@Override public void actionPerformed(ActionEvent event) {
				sudokuPanel.clearBoxReasoningWithUndo();
			}
		}), AnnotationTool.BOX_SELECTION.name());
		add(annotationCardPanel);

		javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
		jPanel1.setLayout(jPanel1Layout);
		jPanel1Layout.setHorizontalGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 100, Short.MAX_VALUE));
		jPanel1Layout.setVerticalGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 100, Short.MAX_VALUE));

		addComponentListener(new java.awt.event.ComponentAdapter() {
			public void componentResized(java.awt.event.ComponentEvent evt) {
				formComponentResized(evt);
			}
		});
		setLayout(null);

		titleLabel.setBackground(new java.awt.Color(0, 51, 255));
		titleLabel.setFont(new java.awt.Font("Tahoma", 1, 12));
		titleLabel.setForeground(new java.awt.Color(255, 255, 255));
		titleLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		titleLabel.setText(bundle.getString("CellZoomPanel.titleLabel.text"));
		titleLabel.setOpaque(true);
		add(titleLabel);
		titleLabel.setBounds(0, 0, 63, 15);

		setValueLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		setValueLabel.setText(bundle.getString("CellZoomPanel.setValueLabel.text"));
		add(setValueLabel);
		setValueLabel.setBounds(0, 0, 49, 14);

		setValuePanel.setLayout(new java.awt.GridLayout(3, 3));

		setValueButton1.setText("1");
		setValueButton1.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				setValueButton1ActionPerformed(evt);
			}
		});
		setValuePanel.add(setValueButton1);

		setValueButton2.setText("2");
		setValueButton2.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				setValueButton1ActionPerformed(evt);
			}
		});
		setValuePanel.add(setValueButton2);

		setValueButton3.setText("3");
		setValueButton3.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				setValueButton1ActionPerformed(evt);
			}
		});
		setValuePanel.add(setValueButton3);

		setValueButton4.setText("4");
		setValueButton4.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				setValueButton1ActionPerformed(evt);
			}
		});
		setValuePanel.add(setValueButton4);

		setValueButton5.setText("5");
		setValueButton5.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				setValueButton1ActionPerformed(evt);
			}
		});
		setValuePanel.add(setValueButton5);

		setValueButton6.setText("6");
		setValueButton6.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				setValueButton1ActionPerformed(evt);
			}
		});
		setValuePanel.add(setValueButton6);

		setValueButton7.setText("7");
		setValueButton7.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				setValueButton1ActionPerformed(evt);
			}
		});
		setValuePanel.add(setValueButton7);

		setValueButton8.setText("8");
		setValueButton8.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				setValueButton1ActionPerformed(evt);
			}
		});
		setValuePanel.add(setValueButton8);

		setValueButton9.setText("9");
		setValueButton9.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				setValueButton1ActionPerformed(evt);
			}
		});
		setValuePanel.add(setValueButton9);

		add(setValuePanel);
		setValuePanel.setBounds(0, 0, 117, 69);

		toggleCandidatesLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		toggleCandidatesLabel.setText(bundle.getString("CellZoomPanel.toggleCandidatesLabel.text"));
		add(toggleCandidatesLabel);
		toggleCandidatesLabel.setBounds(0, 0, 93, 14);

		toggleCandidatesPanel.setLayout(new java.awt.GridLayout(3, 3));

		toggleCandidatesButton1.setText("1");
		toggleCandidatesButton1.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				toggleCandidatesButton1ActionPerformed(evt);
			}
		});
		toggleCandidatesPanel.add(toggleCandidatesButton1);

		toggleCandidatesButton2.setText("2");
		toggleCandidatesButton2.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				toggleCandidatesButton1ActionPerformed(evt);
			}
		});
		toggleCandidatesPanel.add(toggleCandidatesButton2);

		toggleCandidatesButton3.setText("3");
		toggleCandidatesButton3.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				toggleCandidatesButton1ActionPerformed(evt);
			}
		});
		toggleCandidatesPanel.add(toggleCandidatesButton3);

		toggleCandidatesButton4.setText("4");
		toggleCandidatesButton4.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				toggleCandidatesButton1ActionPerformed(evt);
			}
		});
		toggleCandidatesPanel.add(toggleCandidatesButton4);

		toggleCandidatesButton5.setText("5");
		toggleCandidatesButton5.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				toggleCandidatesButton1ActionPerformed(evt);
			}
		});
		toggleCandidatesPanel.add(toggleCandidatesButton5);

		toggleCandidatesButton6.setText("6");
		toggleCandidatesButton6.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				toggleCandidatesButton1ActionPerformed(evt);
			}
		});
		toggleCandidatesPanel.add(toggleCandidatesButton6);

		toggleCandidatesButton7.setText("7");
		toggleCandidatesButton7.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				toggleCandidatesButton1ActionPerformed(evt);
			}
		});
		toggleCandidatesPanel.add(toggleCandidatesButton7);

		toggleCandidatesButton8.setText("8");
		toggleCandidatesButton8.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				toggleCandidatesButton1ActionPerformed(evt);
			}
		});
		toggleCandidatesPanel.add(toggleCandidatesButton8);

		toggleCandidatesButton9.setText("9");
		toggleCandidatesButton9.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				toggleCandidatesButton1ActionPerformed(evt);
			}
		});
		toggleCandidatesPanel.add(toggleCandidatesButton9);

		add(toggleCandidatesPanel);
		toggleCandidatesPanel.setBounds(0, 0, 117, 69);

		chooseColorPanel.setLayout(new java.awt.GridLayout(2, 6, 1, 1));
		colorButtonGroup = new ButtonGroup();
		for (int i = 0; i < COLOR_BUTTON_COUNT; i++) {
			cellPanels[i] = createColorButtonPanel(i);
			colorButtonGroup.add(cellPanels[i]);
			String group = Character.toString((char) ('A' + i / 2));
			String member = bundle.getString(i % 2 == 0
					? "CellZoomPanel.palette.deep" : "CellZoomPanel.palette.light");
			cellPanels[i].getAccessibleContext().setAccessibleName(java.text.MessageFormat.format(
					bundle.getString("CellZoomPanel.paletteSlot.accessible"), group, member));
		}		
		for (int i = 0; i < COLOR_BUTTON_COUNT; i += 2) {
			chooseColorPanel.add(cellPanels[i]);
		}		
		for (int i = 1; i < COLOR_BUTTON_COUNT; i += 2) {
			chooseColorPanel.add(cellPanels[i]);
		}

		add(chooseColorPanel);
		chooseColorPanel.setBounds(0, 0, 113, 3);

		jFontButton.setText("FontButton");
		jFontButton.setEnabled(false);
		add(jFontButton);
		jFontButton.setBounds(29, 130, 110, 23);
	}

	private void formComponentResized(java.awt.event.ComponentEvent evt) {
		calculateLayout();
		printSize();
	}

	private void setValueButton1ActionPerformed(java.awt.event.ActionEvent evt) {
		setValue((JButton) evt.getSource());
	}

	private void toggleCandidatesButton1ActionPerformed(java.awt.event.ActionEvent evt) {
		handleCandidateChange((JButton) evt.getSource());
	}

	private void handleCandidateChange(JButton button) {
		
		int candidate = -1;
		
		for (int i = 0; i < toggleCandidatesButtons.length; i++) {
			if (button == toggleCandidatesButtons[i]) {
				candidate = i + 1;
				break;
			}
		}
		
		if (sudokuPanel != null && candidate != -1) {
			if (isDefaultMouse()) {
				sudokuPanel.toggleOrRemoveCandidateFromCellZoomPanel(candidate);
			} else {
				sudokuPanel.handleColoring(candidate);
			}
		}
	}

	private void setValue(JButton button) {
		
		int number = -1;
		
		for (int i = 0; i < setValueButtons.length; i++) {
			if (button == setValueButtons[i]) {
				number = i + 1;
				break;
			}
		}
		
		if (sudokuPanel != null && number != -1) {
			sudokuPanel.setCellFromCellZoomPanel(number);
		}
	}

	private void handleColorChange(int colorNumber, boolean isCtrlDown) {
		if (colorNumber >= 0 && !isCtrlDown) {
			Color color = Options.getInstance().getColoringColors()[colorNumber];
			setPrimaryColor(color);
            selectPaletteGroup(colorNumber / 2);
		}
		
		if (colorNumber >= 0 && colorNumber < cellPanels.length && mainFrame != null) {
			if (isCtrlDown && colorNumber > 0) {
				if (isColoringCells()) {
					sudokuPanel.clearCellColor(Options.getInstance().getColoringColors()[colorNumber]);
				} else if (isColoringCandidates()) {
					sudokuPanel.clearCandidateColor(Options.getInstance().getColoringColors()[colorNumber]);
				}
			} else {
				if (isColoring()) {
					if (isColoringCells()) {
						mainFrame.setColoring(getPrimaryColor(), true);
					} else if (isColoringCandidates()) {
						mainFrame.setColoring(getPrimaryColor(), false);
					}
				}
			}
		}
		syncPaletteSlotSelection();
		sudokuPanel.repaint();
	}

	public final void calculateLayout() {
		
		if (defaultButtonHeight == -1) {
			// not yet initialized!
			return;
		}
		
		int width = getWidth();
		int height = getHeight();
		int y = Y_OFFSET;

		FontMetrics metrics = getFontMetrics(getFont());
		int textHeight = metrics.getHeight();
		int contentWidth = Math.max(1, width - 2 * X_OFFSET);
		int colorPanelHeight = Math.min(COLOR_PANEL_MAX_HEIGHT,
				Math.max(36, Math.min(50, contentWidth / 8)));
		int colorRowHeight = Math.max(COLOR_PALETTE_SIZE, colorPanelHeight);
		int verticalBudget = Math.max(GRID_MIN_SIZE,
				height - Y_OFFSET - 2 * textHeight - TOOL_SELECTOR_HEIGHT - colorRowHeight
						- 3 * SMALL_GAP - 2 * LARGE_GAP);
		int gridWidthBudget = Math.max(GRID_MIN_SIZE, (contentWidth - LARGE_GAP) / 2);
		int buttonPanelHeight = Math.min(GRID_MAX_SIZE,
				Math.min(gridWidthBudget, verticalBudget));
		boolean sideBySide = contentWidth >= 2 * GRID_MIN_SIZE + LARGE_GAP;
		int newColorImageHeight = colorPanelHeight * 2 / 3;

		titleLabel.setBounds(0, 0, width, textHeight);
		int firstGridX = (width - (sideBySide ? 2 * buttonPanelHeight + LARGE_GAP
				: buttonPanelHeight)) / 2;
		if (sideBySide) {
			setValueLabel.setBounds(firstGridX, y, buttonPanelHeight, textHeight);
			toggleCandidatesLabel.setBounds(firstGridX + buttonPanelHeight + LARGE_GAP, y,
					buttonPanelHeight, textHeight);
			y += textHeight + SMALL_GAP;
			setValuePanel.setBounds(firstGridX, y, buttonPanelHeight, buttonPanelHeight);
			toggleCandidatesPanel.setBounds(firstGridX + buttonPanelHeight + LARGE_GAP, y,
					buttonPanelHeight, buttonPanelHeight);
			setValuePanel.doLayout();
			toggleCandidatesPanel.doLayout();
			y += buttonPanelHeight + LARGE_GAP;
		} else {
			setValueLabel.setBounds(X_OFFSET, y, contentWidth, textHeight);
			y += textHeight + SMALL_GAP;
			setValuePanel.setBounds((width - buttonPanelHeight) / 2, y,
					buttonPanelHeight, buttonPanelHeight);
			setValuePanel.doLayout();
			y += buttonPanelHeight + LARGE_GAP;
			toggleCandidatesLabel.setBounds(X_OFFSET, y, contentWidth, textHeight);
			y += textHeight + SMALL_GAP;
			toggleCandidatesPanel.setBounds((width - buttonPanelHeight) / 2, y,
					buttonPanelHeight, buttonPanelHeight);
			toggleCandidatesPanel.doLayout();
			y += buttonPanelHeight + LARGE_GAP;
		}

		int toolCount = AnnotationTool.values().length-1;
		int radioWidth = Math.min(contentWidth,
				toolCount * TOOL_SELECTOR_BUTTON_SIZE + (toolCount - 1) * 2);
		radioButtonPanel.setBounds((width - radioWidth) / 2, y, radioWidth,
				TOOL_SELECTOR_HEIGHT);
		radioButtonPanel.doLayout();
		y += TOOL_SELECTOR_HEIGHT + LARGE_GAP;

		int colorStartX = Math.max(X_OFFSET, (width - (COLOR_PALETTE_SIZE + SMALL_GAP
				+ colorPanelHeight * 4 + SMALL_GAP + colorPanelHeight / 2)) / 2);
		colorPalette.setLocation(colorStartX, y);
		chooseColorPanel.setSize(colorPanelHeight * 4, colorPanelHeight);
		chooseColorPanel.setLocation(colorStartX + COLOR_PALETTE_SIZE + SMALL_GAP, y);
		chooseColorPanel.doLayout();
		colorTools.setSize(colorPanelHeight / 2, colorPanelHeight / 2);
		colorTools.setLocation(chooseColorPanel.getX() + chooseColorPanel.getWidth() + SMALL_GAP, y);
		colorTools.doLayout();
		if (toolbarPaletteVisible) {
            colorTools.setLocation(X_OFFSET, y);
        }
        int annotationX = colorTools.getX() + colorTools.getWidth() + SMALL_GAP;
		annotationCardPanel.setBounds(annotationX, y,
				Math.max(1, width - annotationX - X_OFFSET), colorRowHeight);
		annotationCardPanel.doLayout();
		y += colorRowHeight + LARGE_GAP;

		// set correct font size for buttons
		int newFontSize = defaultButtonFontSize * buttonPanelHeight / (defaultButtonHeight * 4);
		if (newFontSize > 0 && newFontSize != buttonFontSize) {

			buttonFontSize = newFontSize;
			buttonFont = new Font(buttonFont.getName(), buttonFont.getStyle(), buttonFontSize);
			iconFont = new Font(buttonFont.getName(), buttonFont.getStyle(), buttonFontSize - DIFF_SIZE);

			for (int i = 0; i < setValueButtons.length; i++) {
				setValueButtons[i].setFont(buttonFont);
				toggleCandidatesButtons[i].setFont(buttonFont);
			}
		}
		
		// ColorKu icons should be the same size as the candidate numbers
		// icons are only created, if colorKu mode is active
		if (newColorImageHeight > 0 && 
			Options.getInstance().isShowColorKuAct() && 
			newColorImageHeight != colorImageHeight) {
			
			colorImageHeight = newColorImageHeight;
			for (int i = 0; i < colorKuIcons.length; i++) {
				colorKuIcons[i] = new ImageIcon(new ColorKuImage(colorImageHeight, Options.getInstance().getColorKuColor(i + 1)));
			}
		}
		
		repaint();
	}

	/**
	 * Two modi: normal operation or coloring.<br>
	 * Normal operation:
	 * <ul>
	 * <li>activeColor has to be -1</li>
	 * <li>values and candidates contain valid choices (set of cells or single
	 * cell)</li>
	 * </ul>
	 * Coloring:
	 * <ul>
	 * <li>activeColor holds the color, colorCellOrCandidate is true for coloring
	 * cells</li>
	 * <li>buttons are only available, if a single cell is selected
	 * <li>
	 * <li>if a set of cells is selected, coloredCells and coloredCandidates are
	 * null</li>
	 * </ul>
	 * 
	 * @param values
	 * @param candidates
	 * @param aktColor
	 * @param index
	 * @param colorCellOrCandidate
	 * @param singleCell
	 * @param coloredCells
	 * @param coloredCandidates
	 */
	public void update(
			SudokuSet values, 
			SudokuSet candidates, 
			int index, 
			boolean singleCell, 
			SortedMap<Integer, Color> coloredCells,
			SortedMap<Integer, Color> coloredCandidates) {
		
		// reset all buttons
		for (int i = 0; i < setValueButtons.length; i++) {
			setValueButtons[i].setText("");
			setValueButtons[i].setEnabled(false);
			setValueButtons[i].setForeground(normButtonForeground);
			setValueButtons[i].setBackground(normButtonBackground);
			setValueButtons[i].setIcon(null);
			toggleCandidatesButtons[i].setText("");
			toggleCandidatesButtons[i].setEnabled(false);
			toggleCandidatesButtons[i].setForeground(normButtonForeground);
			toggleCandidatesButtons[i].setBackground(normButtonBackground);
			toggleCandidatesButtons[i].setIcon(null);
		}

		// now set accordingly
		if (isDefaultMouse()) {
			// no coloring -> buttons are available
			for (int i = 0; i < values.size(); i++) {
				int cand = values.get(i) - 1;
				if (cand >= 0 && cand <= 8) {
					if (Options.getInstance().isShowColorKuAct()) {
						setValueButtons[cand].setText(null);
						setValueButtons[cand].setIcon(colorKuIcons[cand]);
					} else {
						setValueButtons[cand].setText(NUMBERS[cand]);
						setValueButtons[cand].setIcon(null);
					}
					setValueButtons[cand].setEnabled(true);
				}
			}
			
			for (int i = 0; i < candidates.size(); i++) {
				int cand = candidates.get(i) - 1;
				if (cand >= 0 && cand <= 8) {
					if (Options.getInstance().isShowColorKuAct()) {
						toggleCandidatesButtons[cand].setText(null);
						toggleCandidatesButtons[cand].setIcon(colorKuIcons[cand]);
					} else {
						toggleCandidatesButtons[cand].setText(NUMBERS[cand]);
						toggleCandidatesButtons[cand].setIcon(null);
					}
					toggleCandidatesButtons[cand].setEnabled(true);
				}
			}
			
			if (singleCell) {
				toggleCandidatesLabel.setText(ResourceBundle.getBundle("intl/CellZoomPanel")
						.getString("CellZoomPanel.toggleCandidatesLabel.text"));
				for (int i = 0; i < toggleCandidatesButtons.length; i++) {
					toggleCandidatesButtons[i].setEnabled(true);
				}
			} else {
				toggleCandidatesLabel.setText(ResourceBundle.getBundle("intl/CellZoomPanel")
						.getString("CellZoomPanel.toggleCandidatesLabel.text2"));
			}
			
		} else {
			
			// coloring			
			if (coloredCells != null) {

				if (isColoringCells()) {
					for (int i = 0; i < candidates.size(); i++) {
						int cand = candidates.get(i);
						if (coloredCandidates.containsKey(index * 10 + cand)) {
							toggleCandidatesButtons[cand - 1].setForeground(getPrimaryColor());
							toggleCandidatesButtons[cand - 1].setBackground(getPrimaryColor());
							toggleCandidatesButtons[cand - 1].setIcon(createImage(colorImageHeight, getPrimaryColor(), cand));
							toggleCandidatesButtons[cand - 1].setEnabled(true);
						} else {
							toggleCandidatesButtons[cand - 1].setText(NUMBERS[cand - 1]);
							toggleCandidatesButtons[cand - 1].setEnabled(true);
						}
					}
				}
			}
		}
	}

	private ImageIcon createImage(int size, Color color, int cand) {
		
		if (size > 0) {
			
			Image img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = (Graphics2D) img.getGraphics();
			
			if (color == null) {
				color = Options.getInstance().getDefaultCellColor();
			}
			
			g.setColor(color);
			g.fillRect(0, 0, size, size);
			if (cand > 0) {
				if (Options.getInstance().isShowColorKuAct()) {
					BufferedImage cImg = new ColorKuImage(size, Options.getInstance().getColorKuColor(cand));
					g.drawImage(cImg, 0, 0, null);
				} else {
					g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
					g.setFont(iconFont);
					FontMetrics fm = g.getFontMetrics();
					String str = String.valueOf(cand);
					int strWidth = fm.stringWidth(str);
					int strHeight = fm.getAscent();
					g.setColor(normButtonForeground);
					g.drawString(String.valueOf(cand), (size - strWidth) / 2, (size + strHeight - 2) / 2);
				}
			}
			
			return new ImageIcon(img);
		} else {
			return null;
		}
	}

	private void printSize() {}

	public void setTitleLabelColors(Color fore, Color back) {
		titleLabel.setBackground(back);
		titleLabel.setForeground(fore);
	}

	public void applyAppearance() {
		if (!ApplicationAppearance.isDark()) {
			return;
		}
		SudokuAppearancePalette palette = SudokuAppearancePalette.forRendering(false);
		Color background = palette.getSurfaceBackground();
		Color control = palette.getControlBackground();
		Color foreground = palette.getPrimaryForeground();
		setBackground(background);
		chooseColorPanel.setBackground(background);
		jPanel1.setBackground(background);
		setValuePanel.setBackground(background);
		toggleCandidatesPanel.setBackground(background);
		radioButtonPanel.setBackground(background);
		annotationCardPanel.setBackground(background);
		applyToolCardAppearance(annotationCardPanel, background, control, foreground);
		colorPalette.setBackground(background);
		colorPalette.applyAppearance();
		colorTools.setBackground(background);
		setValueLabel.setForeground(foreground);
		toggleCandidatesLabel.setForeground(foreground);
		radioButtonDefault.setBackground(background);
		radioButtonDefault.setForeground(foreground);
		radioButtonColorCandidates.setBackground(background);
		radioButtonColorCandidates.setForeground(foreground);
		radioButtonColorCells.setBackground(background);
		radioButtonColorCells.setForeground(foreground);
		radioButtonDoodle.setBackground(background);
		radioButtonDoodle.setForeground(foreground);
		radioButtonFreeChain.setBackground(background);
		radioButtonFreeChain.setForeground(foreground);
		radioButtonBoxSelection.setBackground(background);
		radioButtonBoxSelection.setForeground(foreground);
		for (JButton button : setValueButtons) {
			button.setBackground(control);
			button.setForeground(foreground);
		}
		for (JButton button : toggleCandidatesButtons) {
			button.setBackground(control);
			button.setForeground(foreground);
		}
		normButtonBackground = control;
		normButtonForeground = foreground;
	}

	private void applyToolCardAppearance(Container parent, Color background, Color control,
			Color foreground) {
		for (Component component : parent.getComponents()) {
			component.setForeground(foreground);
			if (component instanceof javax.swing.AbstractButton || component instanceof JComboBox) {
				component.setBackground(control);
			} else {
				component.setBackground(background);
			}
			if (component instanceof JPanel) {
				applyToolCardAppearance((Container) component, background, control, foreground);
			}
		}
	}

	/**
	 * @param sudokuPanel the sudokuPanel to set
	 */
	public void setSudokuPanel(SudokuPanel sudokuPanel) {
		this.sudokuPanel = sudokuPanel;
		this.colorPalette.setSudokuPanel(sudokuPanel);
		this.colorTools.setSudokuPanel(sudokuPanel);
	}
	
	public void swapColors() {
		if (!effectivePaletteOwner.isSecondarySupported()) {
			return;
		}
		Options options = Options.getInstance();
        options.swapAnnotationPaletteColors(effectivePaletteOwner);
		projectPaletteForTool(sudokuPanel == null ? AnnotationTool.DEFAULT_MOUSE
				: sudokuPanel.getAnnotationTool());
        if (sudokuPanel != null) {
            if (isColoring()) sudokuPanel.resetActiveColor();
            sudokuPanel.updateColorCursor();
        }
		repaint();
	}

	/** Moves one step through the paired dark/light coloring palette. */
	void cyclePaletteColor(int wheelRotation, boolean secondary) {
        cyclePaletteColor((double) wheelRotation);
    }

    void cyclePaletteColor(double rotation) {
        if (lastPaletteWheelAt == 0) paletteWheelGate.reset();
        lastPaletteWheelAt = System.currentTimeMillis();
        int step = paletteWheelGate.step(rotation, lastPaletteWheelAt);
        if (step != 0) selectPaletteGroup((getPaletteGroup() + step + 6) % 6);
    }

    void selectPaletteGroup(int group) {
        int sanitized = Math.max(0, Math.min(5, group));
        Options options = Options.getInstance();
        options.setAnnotationPaletteGroup(effectivePaletteOwner, sanitized);
        projectPaletteForTool(sudokuPanel == null ? AnnotationTool.DEFAULT_MOUSE : sudokuPanel.getAnnotationTool());
        if (sudokuPanel != null) {
            if (isColoring()) sudokuPanel.resetActiveColor();
            sudokuPanel.updateColorCursor();
            sudokuPanel.repaint();
        }
    }

    int getPaletteGroup() {
        return Options.getInstance().getAnnotationPaletteGroup(effectivePaletteOwner);
    }

    SudokuPanel annotationBoard() { return sudokuPanel; }

    boolean supportsSecondaryColor() { return effectivePaletteOwner.isSecondarySupported(); }

    ToolbarColorPalette getToolbarPalette() {
        if (toolbarPalette == null) toolbarPalette = new ToolbarColorPalette(this, colorPalette);
        return toolbarPalette;
    }

    void setToolbarPaletteVisible(boolean visible) {
        toolbarPaletteVisible = visible;
        colorPalette.setVisible(!visible);
        chooseColorPanel.setVisible(!visible);
        calculateLayout();
        repaint();
    }

	public boolean isDefaultMouse() {
		return radioButtonDefault.isSelected();
	}

	public boolean isDoodle() {
		return radioButtonDoodle.isSelected();
	}

	public boolean isFreeChain() {
		return radioButtonFreeChain.isSelected();
	}

	public boolean isBoxSelection() {
		return radioButtonBoxSelection.isSelected();
	}
	
	public boolean isColoringCells() {
		return radioButtonColorCells.isSelected();
	}
	
	public boolean isColoringCandidates() {
		return radioButtonColorCandidates.isSelected();
	}
	
	public boolean isColoring() {
		return isColoringCells() || isColoringCandidates();
	}
	
	public void setDefaultMouse(boolean enable) {
		if (enable) {
			radioButtonDefault.setSelected(true);
		}
	}
	
	public void setColorCells(boolean enable) {
		if (enable) {
			radioButtonColorCells.setSelected(true);
		} else if (radioButtonColorCells.isSelected()) {
			radioButtonDefault.setSelected(true);
		}
	}
	
	public void setColorCandidates(boolean enable) {
		if (enable) {
			radioButtonColorCandidates.setSelected(true);
		} else if (radioButtonColorCandidates.isSelected()) {
			radioButtonDefault.setSelected(true);
		}
	}

	void selectAnnotationTool(AnnotationTool tool) {
		switch (tool) {
		case CANDIDATE_COLORING:
			radioButtonColorCandidates.setSelected(true);
			break;
		case CELL_COLORING:
			radioButtonColorCells.setSelected(true);
			break;
		case DOODLE:
			radioButtonDoodle.setSelected(true);
			break;
		case FREE_CHAIN:
			radioButtonFreeChain.setSelected(true);
			break;
		case BOX_SELECTION:
			radioButtonBoxSelection.setSelected(true);
			break;
		case DEFAULT_MOUSE:
		default:
			radioButtonDefault.setSelected(true);
			break;
		}
		projectPaletteForTool(tool);
		if (annotationCardLayout != null) {
			String card = tool == AnnotationTool.CELL_COLORING
					? AnnotationTool.CANDIDATE_COLORING.name() : tool.name();
			annotationCardLayout.show(annotationCardPanel, card);
		}
		if (colorTools != null) {
			colorTools.syncVisibility();
		}
	}

	private void projectPaletteForTool(AnnotationTool tool) {
		effectivePaletteOwner = AnnotationPaletteOwner.fromTool(tool);
		Options options = Options.getInstance();
		colorPalette.showSelection(options.getAnnotationPrimaryColor(effectivePaletteOwner),
				options.getAnnotationSecondaryColor(effectivePaletteOwner));
		colorPalette.setPairOnly(!effectivePaletteOwner.isSecondarySupported(),
				options.getAnnotationPaletteGroup(effectivePaletteOwner));
		syncPaletteSlotSelection();
        if (toolbarPalette != null) toolbarPalette.refresh();
		if (effectivePaletteOwner == AnnotationPaletteOwner.BOX_SELECTION && sudokuPanel != null) {
			sudokuPanel.setActiveBoxReasoningGroup(
					options.getAnnotationPaletteGroup(AnnotationPaletteOwner.BOX_SELECTION));
		}
	}

	private JPanel createToolCard(String text, String buttonText, ActionListener listener) {
		JPanel card = new JPanel(new GridLayout(1, buttonText == null ? 1 : 2, 4, 0));
		card.add(new JLabel(text));
		if (buttonText != null) {
			JButton button = new JButton(buttonText);
			button.addActionListener(listener);
			card.add(button);
		}
		return card;
	}

	private void configureToolSelectorButton(JRadioButton button) {
		button.setMargin(new java.awt.Insets(1, 1, 1, 1));
		button.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		button.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
		button.setIconTextGap(0);
		button.setFocusPainted(false);
		FlatToolButtonUI.install(button);
	}

	private void configureCompactToggleButton(JToggleButton button) {
		button.setMargin(new java.awt.Insets(2, 7, 2, 7));
		button.setFocusPainted(false);
		FlatToolButtonUI.install(button);
	}

	void updateChainAnalysis(String result) {
		if ("PENDING_NATIVE_MATCH".equals(result)) {
			chainAnalysisText = "已确认：正在匹配原生步骤（Native Match）";
		} else if ("VALID_ALTERNATING_LOOP".equals(result)) {
			chainAnalysisText = "闭环：有效交替链（Alternating）";
		} else if ("VALID_STRONG_DISCONTINUITY_LOOP".equals(result)) {
			chainAnalysisText = "闭环：强不连续（Strong Discontinuity）";
		} else if ("VALID_WEAK_DISCONTINUITY_LOOP".equals(result)) {
			chainAnalysisText = "闭环：弱不连续（Weak Discontinuity）";
		} else if ("VALID_OPEN_CHAIN".equals(result)) {
			chainAnalysisText = "开放链（Open）：已保存";
		} else if ("VALID_RELATIONS_NOT_ALTERNATING".equals(result)) {
			chainAnalysisText = "闭环：关系有效但不交替";
		} else {
			chainAnalysisText = "链中存在无效关系";
		}
		if (chainCard != null) chainCard.setToolTipText(chainAnalysisText);
	}

	void updateDoodleWidthSelection(int index) {
        if (toolbarPalette != null) toolbarPalette.refresh();
		if (doodleWidthCombo != null && doodleWidthCombo.getSelectedIndex() != index) {
			doodleWidthCombo.setSelectedIndex(index);
		}
	}

	void updateChainRelationSelection(boolean strong) {
        if (toolbarPalette != null) toolbarPalette.refresh();
		if (chainStrongButton != null) chainStrongButton.setSelected(strong);
		if (chainWeakButton != null) chainWeakButton.setSelected(!strong);
	}

	void repaintSharedColorControls() {
		syncPaletteSlotSelection();
		colorPalette.repaint();
		chooseColorPanel.repaint();
        if (toolbarPalette != null) toolbarPalette.refresh();
	}

	private void syncPaletteSlotSelection() {
		if (colorButtonGroup == null || cellPanels == null) return;
		int selected = -1;
		if (!effectivePaletteOwner.isSecondarySupported()) {
			selected = getPaletteGroup() * 2;
		} else {
			Color primary = getPrimaryColor();
			Color[] colors = Options.getInstance().getColoringColors();
			for (int i = 0; i < colors.length; i++) {
				if (colors[i].equals(primary)) {
					selected = i;
					break;
				}
			}
		}
		if (selected >= 0 && selected < cellPanels.length) {
			if (!cellPanels[selected].isSelected()) cellPanels[selected].setSelected(true);
		} else {
			colorButtonGroup.clearSelection();
		}
	}

	private static final class PaletteSlotButton extends JToggleButton {
		private static final long serialVersionUID = 1L;
		private final int colorIndex;

		PaletteSlotButton(int colorIndex) {
			this.colorIndex = colorIndex;
			setBorderPainted(false);
			setContentAreaFilled(false);
			setFocusPainted(false);
			setFocusable(true);
			setRequestFocusEnabled(true);
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				int width = Math.max(0, getWidth() - 1);
				int height = Math.max(0, getHeight() - 1);
				Color color = Options.getInstance().getColoringColors()[colorIndex];
				g.setColor(color);
				g.fillRect(0, 0, width, height);
				if (getModel().isPressed() && getModel().isArmed()) {
					g.setColor(new Color(0, 0, 0, 45));
					g.fillRect(0, 0, width, height);
				}

				g.setColor(ApplicationAppearance.isDark()
						? new Color(105, 110, 118) : Color.LIGHT_GRAY);
				g.drawLine(width, 0, width, height);
				g.drawLine(0, height, width, height);
				g.setColor(ApplicationAppearance.isDark()
						? new Color(65, 69, 75) : Color.DARK_GRAY);
				g.drawLine(0, 0, width, 0);
				g.drawLine(0, 0, 0, height);

				Color contrast = color.getRed() * 299 + color.getGreen() * 587
						+ color.getBlue() * 114 >= 128000 ? Color.BLACK : Color.WHITE;
				if (isSelected()) {
					g.setColor(contrast);
					g.drawRect(2, 2, Math.max(0, width - 4), Math.max(0, height - 4));
					g.drawRect(3, 3, Math.max(0, width - 6), Math.max(0, height - 6));
				}
				if (isFocusOwner()) {
					g.setColor(contrast);
					g.drawRect(5, 5, Math.max(0, width - 10), Math.max(0, height - 10));
				}
			} finally {
				g.dispose();
			}
		}
	}
	
	public Color getPrimaryColor() {
		return Options.getInstance().getAnnotationPrimaryColor(effectivePaletteOwner);
	}

	public void setPrimaryColor(Color color) {
		Options.getInstance().setAnnotationPrimaryColor(effectivePaletteOwner, color);
		projectPaletteForTool(sudokuPanel == null ? AnnotationTool.DEFAULT_MOUSE
				: sudokuPanel.getAnnotationTool());
		if (effectivePaletteOwner == AnnotationPaletteOwner.BOX_SELECTION && sudokuPanel != null) {
			sudokuPanel.setActiveBoxReasoningGroup(getPaletteGroup());
		}
	}
	
	public Color getSecondaryColor() {
		return Options.getInstance().getAnnotationSecondaryColor(effectivePaletteOwner);
	}

	public void setSecondaryColor(Color color) {
		if (!effectivePaletteOwner.isSecondarySupported()) return;
		Options.getInstance().setAnnotationSecondaryColor(effectivePaletteOwner, color);
		projectPaletteForTool(sudokuPanel == null ? AnnotationTool.DEFAULT_MOUSE
				: sudokuPanel.getAnnotationTool());
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		AnnotationTool target = null;
		if (e.getSource() == radioButtonDefault) {
			target = AnnotationTool.DEFAULT_MOUSE;
		} else if (e.getSource() == radioButtonColorCells) {
			target = AnnotationTool.CELL_COLORING;
		} else if (e.getSource() == radioButtonColorCandidates) {
			target = AnnotationTool.CANDIDATE_COLORING;
		} else if (e.getSource() == radioButtonDoodle) {
			target = AnnotationTool.DOODLE;
		} else if (e.getSource() == radioButtonFreeChain) {
			target = AnnotationTool.FREE_CHAIN;
		} else if (e.getSource() == radioButtonBoxSelection) {
			target = AnnotationTool.BOX_SELECTION;
		}
		if (target != null) sudokuPanel.setAnnotationTool(target);
	}
}
