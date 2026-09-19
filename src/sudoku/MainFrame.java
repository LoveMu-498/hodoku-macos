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

import generator.BackgroundGeneratorThread;
import generator.SudokuGenerator;
import generator.SudokuGeneratorFactory;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;
import solver.SudokuSolver;
import solver.SudokuSolverFactory;
import sudoku.FileDrop;

/**
 * @author hobiwan
 */
public class MainFrame extends javax.swing.JFrame implements FlavorListener {

	private static final long serialVersionUID = 1L;
	public static final String VERSION = "HoDoKu - v" + ApplicationVersion.get();

	// public static final String BUILD = "Build 16";
	public static final String BUILD;
	public static final String REV = "$LastChangedRevision: 116 $";

	/** The size of the toggle button icons */
	private static final int TOGGLE_BUTTON_ICON_SIZE = 32;
	/** Annotation glyphs stay optically subordinate to the digit filters. */
	private static final int ANNOTATION_TOOLBAR_ICON_SIZE = 27;
	/** Shared toolbar control size. Keeping one visual center makes mixed icon groups easier to scan. */
	private static final int TOOLBAR_BUTTON_SIZE = 38;
	private static final int TOOLBAR_CONTROL_HEIGHT = 32;
	private SudokuPanel sudokuPanel;
	// private DifficultyLevel level =
	// Options.getInstance().getDifficultyLevels()[DifficultyType.EASY.ordinal()];
	private JToggleButton[] toggleButtons = new JToggleButton[10];
	/** Images for the filter toggle button icons (ColorKu version) */
	private ColorKuImage[] toggleButtonImagesColorKu = new ColorKuImage[10];
	/** Icons for the filter toggle buttons in the toolbar (ColorKu version) */
	private Icon[] toggleButtonIconsColorKu = new Icon[10];
	/** Icons for the filter toggle buttons in the toolbar (currently displayed) */
	private Icon[] toggleButtonIcons = new Icon[10];
	/** Icons for the filter toggle buttons in the toolbar (no candidates left) */
	private Icon[] emptyToggleButtonIcons = new Icon[10];
	/** Shadow-free standard digit filter icons. */
	private Icon[] flatToggleButtonIcons = new Icon[10];
	/** Shadow-free standard digit filter icons for unavailable candidates. */
	private Icon[] flatEmptyToggleButtonIcons = new Icon[10];
	/** One empty icon for disabled filter buttons - digits */
	@SuppressWarnings("unused")
	private Icon emptyToggleButtonIconOrg = new ImageIcon(getClass().getResource("/img/f_0c.png"));
	/** One empty icon for disabled filter buttons */
	private Icon emptyToggleButtonIconOrgColorKu = new ImageIcon(
			new ColorKuImage(TOGGLE_BUTTON_ICON_SIZE, Color.WHITE));
	/** One empty icon for disabled filter buttons */
	private JRadioButtonMenuItem[] levelMenuItems = new JRadioButtonMenuItem[5];
	private JRadioButtonMenuItem[] modeMenuItems;
	private boolean oldShowDeviations = true;
	/** only set, when the givens are changed */
	private boolean oldShowDeviationsValid = false;
	/** true if new givens are being entered */
	private boolean gameMode = false;
	private SplitPanel splitPanel = new SplitPanel();
	private SummaryPanel summaryPanel = new SummaryPanel(this);
	private SolutionPanel solutionPanel = new SolutionPanel(this);
	private final TechniqueStepCatalog techniqueStepCatalog = new TechniqueStepCatalog();
	private AllStepsPanel allStepsPanel = new AllStepsPanel(this, null);
	private CellZoomPanel cellZoomPanel = new CellZoomPanel(this);
	private JTabbedPane tabPane = new JTabbedPane();
	private Rectangle normalWindowBounds;
	private PageFormat pageFormat = null;
	private PrinterJob job = null;
	private double saveImageDefaultSize = 800;
	private int bildAufloesung = 96;
	private int bildEinheit = 2;
	
	private MyFileFilter[] puzzleFileSaveFilters = new MyFileFilter[] { 
		new MyFileFilter(1), new MyFileFilter(2), new MyFileFilter(3), 
		new MyFileFilter(4), new MyFileFilter(5), new MyFileFilter(6), 
		new MyFileFilter(7), new MyFileFilter(9)
	};
	
	private MyFileFilter[] puzzleFileLoadFilters = new MyFileFilter[] { 
		new MyFileFilter(1), new MyFileFilter(8), new MyFileFilter(9)
	};
	
	private MyFileFilter[] configFileFilters = new MyFileFilter[] { new MyFileFilter(0) };
	private boolean outerSplitPaneInitialized = false; // used to adjust divider bar at startup!
	private int resetHDivLocLoc = -1; // when resetting windows, the divider location gets changed by some layout function
	private boolean resetHDivLoc = false; // adjust DividerLocation after change
	private long resetHDivLocTicks = 0; // only adjust within a second or so
	private String configFileExt = java.util.ResourceBundle.getBundle("intl/MainFrame")
			.getString("MainFrame.config_file_ext");
	private String solutionFileExt = java.util.ResourceBundle.getBundle("intl/MainFrame")
			.getString("MainFrame.solution_file_ext");
	private String textFileExt = java.util.ResourceBundle.getBundle("intl/MainFrame")
			.getString("MainFrame.text_file_ext");
	private String ssFileExt = java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.ss_file_ext");
	private MessageFormat formatter = new MessageFormat("");
	private ReplayController replayController;
	public ReplayController getReplayController() { return replayController; }
    java.awt.Component replayArea() { return outerSplitPane; }
    void restoreReplayEditingMode() { setPlay(false); }
    private String pendingChainPaste;
    private long pendingChainPasteAt;
    private String pendingChainBoard;
    private javax.swing.Timer pendingChainPasteTimer;

    private void clearPendingChainPaste() {
        pendingChainPaste=null;pendingChainBoard=null;
        if(pendingChainPasteTimer!=null){pendingChainPasteTimer.stop();pendingChainPasteTimer=null;}
    }

    private void armChainPaste(String text) {
        clearPendingChainPaste();pendingChainPaste=text;pendingChainPasteAt=System.nanoTime();
        pendingChainBoard=TechniqueStepCatalog.createSignature(sudokuPanel.getSudoku());
        pendingChainPasteTimer=new javax.swing.Timer(100,event->{
            try {
                String current=(String)Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                if(!text.equals(current)||System.nanoTime()-pendingChainPasteAt>3000000000L
                        ||!pendingChainBoard.equals(TechniqueStepCatalog.createSignature(sudokuPanel.getSudoku())))clearPendingChainPaste();
            }catch(Exception unavailable){clearPendingChainPaste();}
        });pendingChainPasteTimer.start();
    }

    boolean importChainText(String text) {
        ChainTextCodec.Document document=ChainTextCodec.parse(text);
        if(document==null){clearPendingChainPaste();return false;}
        if(!document.matches(sudokuPanel.getSudoku())){
            boolean repeated=text.equals(pendingChainPaste)&&System.nanoTime()-pendingChainPasteAt<=3000000000L
                    &&TechniqueStepCatalog.createSignature(sudokuPanel.getSudoku()).equals(pendingChainBoard);
            if(!repeated){armChainPaste(text);announceStatus("MainFrame.chainText.mismatch");return true;}
            clearPendingChainPaste();
            GuiState saved=new GuiState(sudokuPanel,sudokuPanel.getSolver(),solutionPanel);
            saved.setIncludeAnnotations(true);saved.get(true);saved.setName("Before chain paste");saved.setTimestamp(new Date());
            savePoints.add(saved);
            StringBuilder values=new StringBuilder(81);for(int v:document.board().getValues())values.append(v);
            if(!setPuzzle(values.toString(),true,document.board())){setState(saved);return true;}
        }else clearPendingChainPaste();
        sudokuPanel.showImportedChainText(document);
        setHintText(document.text());
        announceStatus(document.hasConclusion()?"MainFrame.chainText.preview":"MainFrame.chainText.temporary");
        OperationSoundPlayer.play(OperationSoundPlayer.Sound.PASTE);fixFocus();return true;
    }

	private List<GuiState> savePoints = new ArrayList<GuiState>(); // container for savepoints
	// private GameMode mode = GameMode.PLAYING;
	private boolean changingFullScreenMode = false;
	/** Array with images for progressLabel */
	private ImageIcon[] progressImages = new ImageIcon[Options.DEFAULT_DIFFICULTY_LEVELS.length];
	/**
	 * For progress label: current difficulty level; access must be synchronized!
	 */
	private DifficultyLevel currentLevel = null;
	/** For progress label: current score; access must be synchronized! */
	private int currentScore = 0;
	/** Vage hint button in toolbar */
	private JButton vageHintToggleButton = null;
	/** Concrete hint button in toolbar */
	private JButton concreteHintToggleButton = null;
	/** Show next step button in toolbar */
	private JButton showNextStepToggleButton = null;
	/** Dropdown for selecting a currently available solving technique. */
	private JButton selectTechniqueToggleButton = null;
	/** Explicit technique selected for the next hint; null retains original solver order. */
	private SolutionType selectedHintTechnique = null;
	private SolutionStep selectedHintStep = null;
	private int selectedHintInstance = 0;
	private int selectedHintInstanceCount = 0;
	/** Coordinates the latest-board native Find All Steps scan. */
	private final Object techniqueScanLock = new Object();
	private String cachedTechniqueSignature = null;
	private List<SolutionType> cachedTechniqueTypes = new ArrayList<SolutionType>();
	private Map<SolutionType, List<SolutionStep>> cachedTechniqueSteps =
			new LinkedHashMap<SolutionType, List<SolutionStep>>();
	private Map<SolutionStep, StepFootprint> cachedTechniqueFootprints =
			new IdentityHashMap<SolutionStep, StepFootprint>();
	private boolean cachedTechniqueScanComplete = false;
	private boolean cachedTechniqueScanFailed = false;
	private Thread techniqueScanWorker;
	private String techniqueScanWorkerSignature;
	private javax.swing.JPopupMenu techniqueSelectorPopup = null;
	private boolean relaunchAfterQuit;
	private Timer techniqueScanTimer;
	private int techniqueChangesSinceScan;
	private boolean annotationKeyDispatcherInstalled;
	private boolean optionKeyDown;
	private CurrentReasoningMenu currentReasoningMenu;
	private boolean reasoningEnterDown;
	private final KeyEventDispatcher annotationKeyDispatcher = new KeyEventDispatcher() {
		@Override
		public boolean dispatchKeyEvent(KeyEvent event) {
            java.awt.Component source = event.getComponent();
            java.awt.Window enteredWindow = source instanceof java.awt.Window ? (java.awt.Window) source
                    : source == null ? null : SwingUtilities.getWindowAncestor(source);
            if (replayController != null && replayController.isViewing() && enteredWindow == MainFrame.this)
                return replayController.viewer().handleKeyEvent(event);
			if ((event.getID() != KeyEvent.KEY_PRESSED && event.getID() != KeyEvent.KEY_RELEASED
					&& event.getID() != KeyEvent.KEY_TYPED)
					|| event.isConsumed() || sudokuPanel == null) {
				return false;
			}

            if ((source == MainFrame.this || enteredWindow == MainFrame.this || currentReasoningMenu != null && currentReasoningMenu.isVisible())
                    && event.getKeyCode() == KeyEvent.VK_ENTER && event.getModifiersEx() == 0) {
                if (event.getID() == KeyEvent.KEY_RELEASED) reasoningEnterDown = false;
                else if (event.getID() == KeyEvent.KEY_PRESSED) {
                    if (reasoningEnterDown) return true;
                    reasoningEnterDown = currentReasoningMenu != null && currentReasoningMenu.isVisible() || sudokuPanel.hasReasoningEnterContext() || sudokuPanel.getStep()!=null;
                }
            }
            // Owned modal dialogs are descendants too; their Escape/Enter belong
            // to the dialog, not the board's annotation dispatcher.
            if (enteredWindow instanceof javax.swing.JDialog) return false;
			java.awt.Window sourceWindow = source == null ? null : SwingUtilities.getWindowAncestor(source);
			java.awt.Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
			boolean belongsToThisFrame = source != null && SwingUtilities.isDescendingFrom(source, MainFrame.this);
			if (!belongsToThisFrame && sourceWindow != MainFrame.this && activeWindow != MainFrame.this
					&& !isPopupOwnedByThisFrame(sourceWindow)
					&& !isPopupOwnedByThisFrame(activeWindow)) {
				return false;
			}
            // Handle board Tab before Aqua/Swing focus traversal consumes it.
            // Fields and dialogs keep their native Tab navigation.
            if((source==sudokuPanel || source==MainFrame.this) && event.getKeyCode()==KeyEvent.VK_TAB && event.getModifiersEx()==0
                    && (techniqueSelectorPopup==null || !techniqueSelectorPopup.isVisible())) {
                if(event.getID()==KeyEvent.KEY_PRESSED)SwingUtilities.invokeLater(()->showCurrentReasoning(true));
                event.consume();return true;
            }
			sudokuPanel.updateDeletionModifier(event);
			if (event.getKeyCode() == KeyEvent.VK_ALT) {
				if (event.getID() == KeyEvent.KEY_PRESSED) optionKeyDown = true;
				else if (event.getID() == KeyEvent.KEY_RELEASED) optionKeyDown = false;
                updateTechniqueMenuOpacity(techniqueSelectorPopup, optionKeyDown);
			}
			if (techniqueSelectorPopup != null && techniqueSelectorPopup.isVisible()) {
				// Popup controls retain their ordinary text editing, but none of their
				// keys should fall through into the board's annotation dispatcher.
				return dispatchTechniqueSelectorKeyEvent(event);
			}
			if (event.getID() == KeyEvent.KEY_TYPED) {
				return false;
			}
            if(event.getID()==KeyEvent.KEY_PRESSED && event.getKeyCode()==KeyEvent.VK_ENTER
                    && event.getModifiersEx()==0 && (source==sudokuPanel || source==MainFrame.this)
                    && !sudokuPanel.hasReasoningEnterContext() && sudokuPanel.getStep()!=null) {
                executeDisplayedStepFromKeyboard();event.consume();return true;
            }
			if (event.getID() == KeyEvent.KEY_PRESSED
					&& event.getKeyCode() == KeyEvent.VK_ESCAPE) {
				if (shouldDeferEscapeToNativePopup(event)) return false;
				return sudokuPanel.handleEscapeVisualReset();
			}
			if (source instanceof javax.swing.AbstractButton
					&& event.getKeyCode() == KeyEvent.VK_SPACE && event.getModifiersEx() == 0) {
				// Preserve the standard keyboard activation contract for focused buttons.
				// Annotation shortcuts still apply everywhere else in the window.
				return false;
			}
			if (event.getID() == KeyEvent.KEY_RELEASED) {
				if (sudokuPanel.handleMacUnitHighlightKeyReleased(event)) {
					return true;
				}
				return sudokuPanel.handleAnnotationToolKeyReleased(event);
			}
			if (isDirectPointerToolShortcut(event) && !canStartPointerToolGesture(event)) {
				return false;
			}
			if (sudokuPanel.handleMacUnitHighlightKeyPressed(event)) {
				return true;
			}
			return sudokuPanel.handleAnnotationKeyPressed(event);
		}
	};

	private boolean dispatchTechniqueSelectorKeyEvent(KeyEvent event) {
		javax.swing.JPopupMenu popup;
		synchronized (techniqueScanLock) {
			popup = techniqueSelectorPopup;
		}
		if (popup == null || !popup.isVisible()) return false;
		if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
			if (event.getID() == KeyEvent.KEY_PRESSED) popup.setVisible(false);
			event.consume();
			return true;
		}
		TechniqueSelectorPanel selector = popup instanceof CurrentReasoningMenu
                ? (TechniqueSelectorPanel) ((CurrentReasoningMenu) popup).selector()
                : popup.getComponentCount() == 1 && popup.getComponent(0) instanceof TechniqueSelectorPanel
                ? (TechniqueSelectorPanel) popup.getComponent(0) : null;
        if (selector != null && selector.handleSelectorKeyEvent(event)) {
			return true;
		}
		// Aqua may leave the previous component focused while opening/replacing
		// the popup. Do not let that brief focus gap edit the underlying Sudoku.
		if (event.getComponent() != null
				&& SwingUtilities.isDescendingFrom(event.getComponent(), popup)) return false;
		event.consume();
		return true;
	}

	/** Lets Swing close an open menu/combo popup before Escape unwinds board state. */
	private boolean shouldDeferEscapeToNativePopup(KeyEvent event) {
		if (javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length != 0) {
			return true;
		}
		java.awt.Component source = event.getComponent();
		for (java.awt.Component current = source; current != null; current = current.getParent()) {
			if (current instanceof javax.swing.JPopupMenu) return true;
			if (current instanceof javax.swing.JComboBox
					&& ((javax.swing.JComboBox<?>) current).isPopupVisible()) return true;
		}
		java.awt.Window sourceWindow = source == null ? null : SwingUtilities.getWindowAncestor(source);
		java.awt.Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
		return isPopupOwnedByThisFrame(sourceWindow) || isPopupOwnedByThisFrame(activeWindow);
	}

	private boolean isDirectPointerToolShortcut(KeyEvent event) {
		if (event.getModifiersEx() != 0) return false;
		int keyCode = event.getKeyCode();
		return keyCode == KeyEvent.VK_M || keyCode == KeyEvent.VK_T
				|| keyCode == KeyEvent.VK_P || keyCode == KeyEvent.VK_L
				|| keyCode == KeyEvent.VK_S;
	}

	private boolean canStartPointerToolGesture(KeyEvent event) {
		java.awt.Component source = event.getComponent();
		if (source instanceof javax.swing.text.JTextComponent
				&& ((javax.swing.text.JTextComponent) source).isEditable()) {
			return false;
		}
		if (javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length != 0) {
			return false;
		}
		java.awt.Window sourceWindow = source == null ? null : SwingUtilities.getWindowAncestor(source);
		return !(sourceWindow instanceof java.awt.Dialog && ((java.awt.Dialog) sourceWindow).isModal());
	}

	private boolean isOwnedByThisFrame(java.awt.Window window) {
		for (java.awt.Window current = window; current != null; current = current.getOwner()) {
			if (current == this) return true;
		}
		return false;
	}

	private boolean isPopupOwnedByThisFrame(java.awt.Window window) {
		return window != null && window.getType() == java.awt.Window.Type.POPUP
				&& isOwnedByThisFrame(window);
	}
	/** Execute next step button in toolbar */
	private JButton executeStepToggleButton = null;
	/** Abort step button in toolbar */
	private JButton abortStepToggleButton = null;
	/** Seperator for hint buttons in toolbar */
	private JSeparator hintSeperator = null;
	/**
	 * A Timer for accessing the clipboard. If the Clipboard is not available when
	 * querying for DataFlavors, it is started an we try again when it expires.
	 */
	private Timer clipTimer = new Timer(100, null);
	/** The file name of the last loaded sudoku file */
	private String sudokuFileName = null;
	/**
	 * The file type of the last loaded sudoku file: 1 .. hsol, 8 .. txt or 9 .. ss
	 */
	private int sudokuFileType = -1;
	private final SessionStore sessionStore = new SessionStore(ApplicationPaths.getSessionFile());
	private final CompletionTransition completionTransition = new CompletionTransition();
	private boolean sessionRestoreInProgress;
	private javax.swing.JDialog quitFailureDialog;

	private UIExportLine exportWindow;
	private UIImportLine importWindow;

	private javax.swing.JMenuItem aboutMenuItem;
	private javax.swing.JRadioButtonMenuItem allStepsMenuItem;
	private javax.swing.JMenuItem setAllSinglesMenuItem;
	private javax.swing.JMenu viewMenu;
	private javax.swing.JMenuItem askQuestionMenuItem;
	private javax.swing.JMenuItem backdoorSearchMenuItem;
	private javax.swing.JMenu editMenu;
	private javax.swing.JMenuItem exitMenuItem;
	private javax.swing.JRadioButtonMenuItem cellZoomMenuItem;
	private javax.swing.ButtonGroup colorButtonGroup;
	private javax.swing.JRadioButtonMenuItem colorCandidatesMenuItem;
	private javax.swing.JRadioButtonMenuItem colorCellsMenuItem;
	private javax.swing.JMenuItem configMenuItem;
	private javax.swing.JMenuItem copyCluesMenuItem;
	private javax.swing.JMenuItem copyFilledMenuItem;
	private javax.swing.JMenuItem copyLibraryMenuItem;
	private javax.swing.JMenuItem copyPmGridMenuItem;
	private javax.swing.JMenuItem copyPmGridWithStepMenuItem;
	private javax.swing.JMenuItem copySSMenuItem;
	private javax.swing.JMenuItem createSavePointMenuItem;
    private javax.swing.JMenuItem viewReplayMenuItem;
    private javax.swing.JMenuItem replayLibraryMenuItem;
	private javax.swing.JMenu fileMenu;
	private javax.swing.JMenuItem printMenuItem;
	private javax.swing.JMenuItem extendedPrintMenuItem;
	private javax.swing.JToggleButton f1ToggleButton;
	private javax.swing.JToggleButton f2ToggleButton;
	private javax.swing.JToggleButton f3ToggleButton;
	private javax.swing.JToggleButton f4ToggleButton;
	private javax.swing.JToggleButton f5ToggleButton;
	private javax.swing.JToggleButton f6ToggleButton;
	private javax.swing.JToggleButton f7ToggleButton;
	private javax.swing.JToggleButton f8ToggleButton;
	private javax.swing.JToggleButton f9ToggleButton;
	private javax.swing.JCheckBoxMenuItem fullScreenMenuItem;
	private javax.swing.JToggleButton fxyToggleButton;
	private javax.swing.JToggleButton fxyzToggleButton;
	private final javax.swing.JToggleButton[] annotationToolButtons =
			new javax.swing.JToggleButton[AnnotationTool.values().length];
	private javax.swing.JMenu helpMenu;
	private javax.swing.JPanel hintPanel;
	private javax.swing.JButton hinweisAbbrechenButton;
	private javax.swing.JButton hinweisAusfuehrenButton;
	private HintTextArea hinweisTextArea;
	private javax.swing.JMenuItem historyMenuItem;
	private javax.swing.JMenuBar jMenuBar1;
	private javax.swing.JPanel jPanel1;
	private javax.swing.JScrollPane jScrollPane1;
	private javax.swing.JSeparator jSeparator1;
	private javax.swing.JSeparator jSeparator11;
	private javax.swing.JSeparator jSeparator12;
	private javax.swing.JSeparator jSeparator13;
	private javax.swing.JSeparator jSeparator24;
	private javax.swing.JSeparator jSeparator25;
	private javax.swing.JSeparator jSeparator8;
	private javax.swing.JSeparator jSeparator9;
	private javax.swing.JToolBar jToolBar1;
	private javax.swing.JMenuItem keyMenuItem;
	private javax.swing.JRadioButtonMenuItem learningMenuItem;
	private javax.swing.ButtonGroup levelButtonGroup;
	private javax.swing.JComboBox<String> levelComboBox;
	private javax.swing.JMenu levelMenu;
	private javax.swing.JRadioButtonMenuItem levelEasyMenuItem;
	private javax.swing.JRadioButtonMenuItem levelMediumMenuItem;
	private javax.swing.JRadioButtonMenuItem levelHardMenuItem;
	private javax.swing.JRadioButtonMenuItem levelDiabolicalMenuItem;
	private javax.swing.JRadioButtonMenuItem levelExtremeMenuItem;
	private javax.swing.JMenuItem loadConfigMenuItem;
	private javax.swing.JMenuItem loadPuzzleMenuItem;
	private javax.swing.JMenuItem solutionStepMenuItem;
	private javax.swing.JMenuItem selectTechniqueMenuItem;
	private javax.swing.JMenuItem mediumHintMenuItem;
	private javax.swing.JMenuItem solvePuzzleMenuItem;
	private javax.swing.JMenuItem solutionCountMenuItem;
	private javax.swing.ButtonGroup modeButtonGroup;
	private javax.swing.JMenu modeMenu;
	private javax.swing.JMenuItem newMenuItem;
	private javax.swing.JMenuItem newEmptyMenuItem;
	private javax.swing.JButton newNoteButton;
	private javax.swing.JButton newGameToolButton;
	private javax.swing.JMenu optionMenu;
	private javax.swing.JSplitPane outerSplitPane;
	private javax.swing.JMenuItem pasteMenuItem;
	private javax.swing.JRadioButtonMenuItem playingMenuItem;
	private javax.swing.JRadioButtonMenuItem practisingMenuItem;
	private javax.swing.JLabel progressLabel;
	private javax.swing.JMenuItem projectHomePageMenuItem;
	private javax.swing.JMenu puzzleMenu;
	private javax.swing.JToggleButton redGreenToggleButton;
	private javax.swing.JMenuItem redoMenuItem;
	private javax.swing.JButton redoToolButton;
	private javax.swing.JMenuItem reportErrorMenuItem;
	private javax.swing.JMenuItem resetSpielMenuItem;
	private javax.swing.JMenuItem resetViewMenuItem;
	private javax.swing.JMenuItem restartSpielMenuItem;
	private javax.swing.JMenuItem restoreSavePointMenuItem;
	private javax.swing.JMenuItem saveConfigAsMenuItem;
	private javax.swing.JMenuItem savePuzzleAsMenuItem;
	private javax.swing.JMenuItem importPuzzleMenuItem;
	private javax.swing.JMenuItem exportPuzzleMenuItem;
	private javax.swing.JMenuItem savePuzzleMenuItem;
	private javax.swing.JMenuItem printSetupMenuItem;
	private javax.swing.JMenuItem resetCandidatesMenuItem;
	private javax.swing.JMenuItem setGivensMenuItem;
	private javax.swing.JCheckBoxMenuItem showCandidatesMenuItem;
	private javax.swing.JCheckBoxMenuItem showCandidateHighlightMenuItem;
	private javax.swing.JCheckBoxMenuItem operationSoundsMenuItem;
	private javax.swing.JCheckBoxMenuItem markInvalidLinksMenuItem;
	private javax.swing.JCheckBoxMenuItem showColorKuMenuItem;
	private javax.swing.JCheckBoxMenuItem showDeviationsMenuItem;
	private javax.swing.JCheckBoxMenuItem showHintButtonsCheckBoxMenuItem;
	private javax.swing.JCheckBoxMenuItem showHintPanelMenuItem;
	private javax.swing.JCheckBoxMenuItem showToolBarMenuItem;
	private javax.swing.JCheckBoxMenuItem showWrongValuesMenuItem;
	private javax.swing.JRadioButtonMenuItem solutionMenuItem;
	private javax.swing.JButton solveUpToButton;
	private javax.swing.JMenuItem solvingGuideMenuItem;
	private javax.swing.JMenuItem saveAsPictureMenuItem;
	private javax.swing.JMenuItem editGivensMenuItem;
	private javax.swing.JMenuItem playGameMenuItem;
	private javax.swing.JLabel statusLabelCellCandidate;
	private javax.swing.JLabel statusLabelLevel;
	private javax.swing.JLabel statusLabelModus;
	private javax.swing.JLabel statusLabelCellSelection;
	private javax.swing.JPanel statusLinePanel;
	private javax.swing.JPanel statusPanelColor1;
	private javax.swing.JPanel statusPanelColor2;
	private javax.swing.JPanel statusPanelColor3;
	private javax.swing.JPanel statusPanelColor4;
	private javax.swing.JPanel statusPanelColor5;
	private javax.swing.JPanel statusPanelColorClear;
	private javax.swing.JPanel statusPanelColorReset;
	private javax.swing.JPanel statusPanelColorResult;
	private javax.swing.JRadioButtonMenuItem sudokuOnlyMenuItem;
	private javax.swing.JRadioButtonMenuItem summaryMenuItem;
	private javax.swing.JMenuItem undoMenuItem;
	private javax.swing.JButton undoToolButton;
	private javax.swing.JMenuItem userManualMenuItem;
	private javax.swing.JMenuItem vagueHintMenuItem;
	private javax.swing.ButtonGroup viewButtonGroup;

	/**
	 * Incorporates the last subversion revision of this file into the version
	 * string.<br>
	 * <br>
	 * 
	 * CAUTION: MainFrame.java must be changed and committed to change the build
	 * number.
	 */
	static {
		String[] dummy = REV.split(" ");
		BUILD = "Build " + dummy[1];
	}

	/**
	 * Creates new form MainFrame
	 * 
	 * @param launchFile
	 */
	public MainFrame(String launchFile) {
		String normalizedLaunchFile = launchFile == null ? null
				: launchFile.toLowerCase(java.util.Locale.ROOT);
		// if a configuration file is given at the command line, load it before anything
		// else is done (helps restoring the screen layout)
		if (normalizedLaunchFile != null && normalizedLaunchFile.endsWith("." + configFileExt)) {
			Options.readOptions(launchFile);
			BackgroundGeneratorThread.getInstance().resetAll();
		}
		Options.getInstance().checkAllFonts();

		initComponents();
		setTitleWithFile();
		outerSplitPane.getActionMap().getParent().remove("startResize");
		outerSplitPane.getActionMap().getParent().remove("toggleFocus");

		// change hintTextArea font to a proportional font
		String fontName = "Arial";
		if (!Options.getInstance().checkFont(fontName)) {
			fontName = Font.SANS_SERIF;
		}
		
		Font font = hinweisTextArea.getFont();

		font = new Font(fontName, font.getStyle(), editMenu.getFont().getSize());
		hinweisTextArea.setFont(font);

		// status line fonts are a bit larger than default in Windows LAF
		// allow adjustments
		font = statusLinePanel.getFont();
		fontName = "Tahoma";
		if (!Options.getInstance().checkFont(fontName)) {
			fontName = font.getName();
		}
		
		int fontSize = 12;
		if (font.getSize() > fontSize) {
			fontSize = font.getSize();
		}
		
		font = new Font(fontName, getFont().getStyle(), fontSize);
		statusLabelCellCandidate.setFont(font);
		statusLabelLevel.setFont(font);
		statusLabelModus.setFont(font);
		statusLabelCellSelection.setFont(font);
		progressLabel.setFont(font);

		// get the current difficulty level (is overriden when levels are added
		// to the combo box)
		int actLevel = Options.getInstance().getActLevel();

		Color lafMenuBackColor = UIManager.getColor("textHighlight");
		Color lafMenuColor = UIManager.getColor("textHighlightText");

		if (lafMenuBackColor == null) {
			lafMenuBackColor = Color.BLUE;
		}
		
		if (lafMenuColor == null) {
			lafMenuColor = Color.BLACK;
		}

		statusLinePanel.setBackground(lafMenuBackColor);
		statusLabelLevel.setForeground(lafMenuColor);
		summaryPanel.setTitleLabelColors(lafMenuColor, lafMenuBackColor);
		solutionPanel.setTitleLabelColors(lafMenuColor, lafMenuBackColor);
		cellZoomPanel.setTitleLabelColors(lafMenuColor, lafMenuBackColor);
		statusLabelModus.setText(ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.playingMenuItem.text"));

		clipTimer.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				clipTimer.stop();
				adjustPasteMenuItem();
			}
		});
		
		try {
			Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
			clip.addFlavorListener(this);
			adjustPasteMenuItem();
		} catch (IllegalStateException ex) {
			clipTimer.start();
		}

		sudokuPanel = new SudokuPanel(this);
		sudokuPanel.setCellZoomPanel(cellZoomPanel);
		cellZoomPanel.setSudokuPanel(sudokuPanel);
		installAnnotationKeyDispatcher();
		outerSplitPane.setLeftComponent(splitPanel);
		splitPanel.setSplitPane(sudokuPanel, null);

		tabPane.addTab(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.summary"), summaryPanel);
		tabPane.addTab(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.solution_path"), solutionPanel);
		tabPane.addTab(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.all_steps"), allStepsPanel);
		tabPane.addTab(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.cell_zoom"), cellZoomPanel);
		tabPane.addChangeListener(new javax.swing.event.ChangeListener() {
			@Override
			public void stateChanged(javax.swing.event.ChangeEvent evt) {
				syncDisplayModeMenuSelection();
			}
		});

		if (Options.getInstance().isSaveWindowLayout()) {
			setWindowLayout(false);
		} else {
			setWindowLayout(true);
		}
		
		outerSplitPaneInitialized = false;
		restoreWindowPlacement();
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentMoved(ComponentEvent evt) {
				rememberNormalWindowBounds();
			}

			@Override
			public void componentResized(ComponentEvent evt) {
				rememberNormalWindowBounds();
			}
		});
		
		showHintPanelMenuItem.setSelected(Options.getInstance().isShowHintPanel());
		showToolBarMenuItem.setSelected(Options.getInstance().isShowToolBar());

		levelMenuItems[0] = levelEasyMenuItem;
		levelMenuItems[1] = levelMediumMenuItem;
		levelMenuItems[2] = levelHardMenuItem;
		levelMenuItems[3] = levelDiabolicalMenuItem;
		levelMenuItems[4] = levelExtremeMenuItem;
		
		FontMetrics metrics = levelComboBox.getFontMetrics(levelComboBox.getFont());
		int miWidth = 0;
		int miHeight = metrics.getHeight();
		Set<Character> mnemonics = new HashSet<Character>();
		
		for (int i = 1; i < DifficultyType.values().length; i++) {
			
			levelMenuItems[i - 1].setText(Options.getInstance().getDifficultyLevels()[i].getName());
			char mnemonic = 0;
			boolean mnemonicFound = false;
			
			for (int j = 0; j < Options.getInstance().getDifficultyLevels()[i].getName().length(); j++) {
				mnemonic = Options.getInstance().getDifficultyLevels()[i].getName().charAt(j);
				if (!mnemonics.contains(mnemonic)) {
					mnemonicFound = true;
					break;
				}
			}
			
			if (mnemonicFound) {
				mnemonics.add(mnemonic);
				levelMenuItems[i - 1].setMnemonic(mnemonic);
			}
			
			levelComboBox.addItem(Options.getInstance().getDifficultyLevels()[i].getName());
			int aktWidth = metrics.stringWidth(Options.getInstance().getDifficultyLevels()[i].getName());

			if (aktWidth > miWidth) {
				miWidth = aktWidth;
			}
		}
		
		mnemonics = null;

		// mode menu items
		modeMenuItems = new JRadioButtonMenuItem[] { playingMenuItem, learningMenuItem, practisingMenuItem };

		// in Windows miWidth = 35, miHeight = 14; size = 60/20
		if (miWidth > 35) {
			Dimension newLevelSize = new Dimension(60 + (miWidth - 35) + 8, 20 + (miHeight - 14) + 3);
			levelComboBox.setMaximumSize(newLevelSize);
			levelComboBox.setMinimumSize(newLevelSize);
			levelComboBox.setPreferredSize(newLevelSize);
			levelComboBox.setSize(newLevelSize);
		}

		// set back the saved difficulty level
		Options.getInstance().setActLevel(actLevel);

		check();

		toggleButtons[0] = f1ToggleButton;
		toggleButtons[1] = f2ToggleButton;
		toggleButtons[2] = f3ToggleButton;
		toggleButtons[3] = f4ToggleButton;
		toggleButtons[4] = f5ToggleButton;
		toggleButtons[5] = f6ToggleButton;
		toggleButtons[6] = f7ToggleButton;
		toggleButtons[7] = f8ToggleButton;
		toggleButtons[8] = f9ToggleButton;
		toggleButtons[9] = fxyToggleButton;
		
		for (int i = 0, lim = toggleButtons.length; i < lim; i++) {
			toggleButtonIcons[i] = toggleButtons[i].getIcon();
			
			if (i >= Sudoku2.UNITS) {
				emptyToggleButtonIcons[i] = toggleButtons[i].getIcon();
			}
		}
		
		setToggleButton(null, false);
		prepareToggleButtonIcons(Options.getInstance().isShowColorKu());

		// initialize colorKuMeniItem
		showColorKuMenuItem.setSelected(Options.getInstance().isShowColorKu());
		Options.getInstance().setShowColorKuAct(Options.getInstance().isShowColorKu());

		hinweisTextArea.setSudokuPanel(sudokuPanel);
		hinweisTextArea.setExternalPasteAction(new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent event) {
				pasteFromClipboard(true);
			}
		});

		// Images for progressLabel
		createProgressLabelImages();
		progressLabel.setIcon(progressImages[0]);

		// set the mode
		setMode(Options.getInstance().getGameMode(), false);

		// show hint buttons in toolbar
		setShowHintButtonsInToolbar();
		styleToolbarControls();
		applyMainWindowAppearance();
		installMacOSApplicationHandlers();
		// Let completed-session startup reuse the normal pre-generated-puzzle path.
		BackgroundGeneratorThread.getInstance().startCreation();

		// Explicit files always win over the automatically managed last session.
		if (launchFile != null) {
			Options.getInstance().initializeAnnotationPalettePreferences(null, null);
		}
		if (normalizedLaunchFile != null && normalizedLaunchFile.endsWith("." + solutionFileExt)) {
			loadFromFile(launchFile, 1);
		} else if (normalizedLaunchFile != null && normalizedLaunchFile.endsWith("." + textFileExt)) {
			loadFromFile(launchFile, 8);
		} else if (launchFile == null) {
			restoreLastSession();
		}
		projectAnnotationPaletteForCurrentTool();
		replayController = new ReplayController(this, sudokuPanel, statusLinePanel, launchFile == null);

		fixFocus();

		new FileDrop(this, new FileDrop.Listener() {
			public void filesDropped(File[] files) {
				onDragDropFile(files);
			}
		});

		updateCellSelectionStatus();
		exportWindow = new UIExportLine(sudokuPanel);
		importWindow = new UIImportLine(this);
	}

	private void applyMainWindowAppearance() {
		if (!ApplicationAppearance.isDark()) {
			return;
		}
		SudokuAppearancePalette palette = SudokuAppearancePalette.forRendering(false);
		Color window = palette.getWindowBackground();
		Color surface = palette.getSurfaceBackground();
		Color control = palette.getControlBackground();
		Color foreground = palette.getPrimaryForeground();

		getContentPane().setBackground(window);
		splitPanel.applyAppearance();
		outerSplitPane.setBackground(window);
		outerSplitPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		hintPanel.setBackground(surface);
		jPanel1.setBackground(surface);
		jToolBar1.setBackground(surface);
		tabPane.setBackground(surface);
		tabPane.setForeground(foreground);
		tabPane.setBorder(javax.swing.BorderFactory.createEmptyBorder());
		hinweisTextArea.setBackground(surface);
		hinweisTextArea.setForeground(foreground);
		jScrollPane1.setBackground(surface);
		jScrollPane1.getViewport().setBackground(surface);
		statusLinePanel.setBackground(surface);
		statusLabelCellCandidate.setForeground(foreground);
		statusLabelLevel.setForeground(foreground);
		progressLabel.setForeground(foreground);
		statusLabelModus.setForeground(foreground);
		statusLabelCellSelection.setForeground(foreground);
		jSeparator1.setForeground(control);
		jSeparator8.setForeground(control);
		jSeparator24.setForeground(control);
		jSeparator25.setForeground(control);
		statusPanelColorResult.setBackground(surface);
		if (hintPanel.getBorder() instanceof javax.swing.border.TitledBorder) {
			((javax.swing.border.TitledBorder) hintPanel.getBorder()).setTitleColor(foreground);
		}
		applySurfaceColors(jToolBar1, palette);
		applySurfaceColors(hintPanel, palette);
		applyToolbarIcons();
		applyHintToolbarIcons();

		summaryPanel.applyAppearance();
		solutionPanel.applyAppearance();
		allStepsPanel.applyAppearance();
		cellZoomPanel.applyAppearance();
	}

	private void applyToolbarIcons() {
		newGameToolButton.setIcon(ToolbarIcons.newGame());
		fxyToggleButton.setIcon(ToolbarIcons.xyFilter());
		fxyzToggleButton.setIcon(ToolbarIcons.xyzFilter());
	}

	private void applyHintToolbarIcons() {
		if (vageHintToggleButton == null) return;
		vageHintToggleButton.setIcon(ToolbarIcons.vagueHint());
		concreteHintToggleButton.setIcon(ToolbarIcons.concreteHint());
		showNextStepToggleButton.setIcon(ToolbarIcons.nextStep());
		executeStepToggleButton.setIcon(ToolbarIcons.execute());
		abortStepToggleButton.setIcon(ToolbarIcons.abort());
	}

	/**
	 * Applies the small, shared visual contract used by every toolbar group. The
	 * existing actions and icons stay untouched; only their common hit area,
	 * alignment and focus treatment are normalized here.
	 */
	private void styleToolbarControls() {
		if (jToolBar1 == null) {
			return;
		}
		jToolBar1.setFloatable(false);
        if (!(jToolBar1.getLayout() instanceof ToolbarWrapLayout)) jToolBar1.setLayout(new ToolbarWrapLayout());
		jToolBar1.setRollover(true);
		jToolBar1.setMargin(new Insets(2, 4, 2, 4));
		jToolBar1.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 4, 3, 4));

		configureToolbarIconButton(undoToolButton);
		configureToolbarIconButton(redoToolButton);
		configureToolbarIconButton(newGameToolButton);
		configureToolbarToggleButton(redGreenToggleButton);
		configureToolbarToggleButton(f1ToggleButton);
		configureToolbarToggleButton(f2ToggleButton);
		configureToolbarToggleButton(f3ToggleButton);
		configureToolbarToggleButton(f4ToggleButton);
		configureToolbarToggleButton(f5ToggleButton);
		configureToolbarToggleButton(f6ToggleButton);
		configureToolbarToggleButton(f7ToggleButton);
		configureToolbarToggleButton(f8ToggleButton);
		configureToolbarToggleButton(f9ToggleButton);
		configureToolbarToggleButton(fxyToggleButton);
		configureToolbarToggleButton(fxyzToggleButton);
		for (JToggleButton button : annotationToolButtons) {
			configureToolbarToggleButton(button);
		}

		if (levelComboBox != null) {
			Dimension preferred = levelComboBox.getPreferredSize();
			int width = Math.max(80, preferred == null ? 80 : preferred.width);
			Dimension comboSize = new Dimension(width, TOOLBAR_CONTROL_HEIGHT);
			levelComboBox.setMinimumSize(comboSize);
			levelComboBox.setPreferredSize(comboSize);
			levelComboBox.setMaximumSize(new Dimension(Math.max(width, 180), TOOLBAR_CONTROL_HEIGHT));
			levelComboBox.setFocusable(false);
			levelComboBox.getAccessibleContext().setAccessibleName(levelComboBox.getToolTipText());
		}

		configureToolbarIconButton(vageHintToggleButton);
		configureToolbarIconButton(concreteHintToggleButton);
		configureToolbarIconButton(showNextStepToggleButton);
		configureToolbarTextButton(selectTechniqueToggleButton);
		configureToolbarIconButton(executeStepToggleButton);
		configureToolbarIconButton(abortStepToggleButton);
		styleToolbarSeparator(jSeparator9);
		styleToolbarSeparator(jSeparator11);
		styleToolbarSeparator(hintSeperator);
	}

	private void configureToolbarIconButton(javax.swing.AbstractButton button) {
		if (button == null) {
			return;
		}
		Dimension size = new Dimension(TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE);
		button.setMinimumSize(size);
		button.setPreferredSize(size);
		button.setMaximumSize(size);
		button.setMargin(new Insets(2, 2, 2, 2));
		button.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		button.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
		button.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
		button.setVerticalTextPosition(javax.swing.SwingConstants.CENTER);
		button.setFocusPainted(false);
		button.setRequestFocusEnabled(false);
		button.setFocusable(false);
		button.setIconTextGap(0);
		button.setBorderPainted(true);
		if (button.getAccessibleContext().getAccessibleName() == null) {
			button.getAccessibleContext().setAccessibleName(button.getToolTipText());
		}
	}

	private void configureToolbarToggleButton(JToggleButton button) {
		configureToolbarIconButton(button);
		FlatToolButtonUI.install(button);
	}

	private void configureToolbarTextButton(javax.swing.AbstractButton button) {
		if (button == null) {
			return;
		}
		Dimension preferred = button.getPreferredSize();
		int width = preferred == null ? 180 : Math.max(140, preferred.width);
		Dimension size = new Dimension(width, TOOLBAR_BUTTON_SIZE);
		button.setMinimumSize(new Dimension(140, TOOLBAR_BUTTON_SIZE));
		button.setPreferredSize(size);
		button.setMaximumSize(new Dimension(Math.max(width, 420), TOOLBAR_BUTTON_SIZE));
		button.setMargin(new Insets(2, 8, 2, 8));
		button.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		button.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
		button.setFocusPainted(false);
		button.setRequestFocusEnabled(false);
		button.setFocusable(false);
		button.setBorderPainted(true);
		if (button.getAccessibleContext().getAccessibleName() == null) {
			button.getAccessibleContext().setAccessibleName(button.getToolTipText());
		}
	}

	private void styleToolbarSeparator(javax.swing.JComponent separator) {
		if (separator != null) {
			separator.setPreferredSize(new Dimension(8, 28));
			separator.setMinimumSize(new Dimension(8, 28));
			separator.setMaximumSize(new Dimension(8, 28));
		}
	}

	void applySurfaceColors(java.awt.Component component, SudokuAppearancePalette palette) {
		Color surface = palette.getSurfaceBackground();
		Color control = palette.getControlBackground();
		Color foreground = palette.getPrimaryForeground();
		if (component instanceof javax.swing.AbstractButton
				|| component instanceof javax.swing.JComboBox) {
			component.setBackground(control);
			// Aqua paints native button/combo backgrounds independently of our
			// surface palette, so retain its matching native text contrast.
			component.setForeground(component instanceof javax.swing.JButton
					? javax.swing.UIManager.getColor("Button.foreground")
					: component instanceof javax.swing.JComboBox
					? javax.swing.UIManager.getColor("ComboBox.foreground") : foreground);
		} else if (component instanceof javax.swing.JPanel
				|| component instanceof javax.swing.JToolBar
				|| component instanceof javax.swing.JScrollPane
				|| component instanceof javax.swing.JViewport
				|| component instanceof javax.swing.JTextArea) {
			component.setBackground(surface);
			component.setForeground(foreground);
		}
		if (component instanceof java.awt.Container) {
			for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
				applySurfaceColors(child, palette);
			}
		}
	}

	void onDragDropFile(File[] files) {
        if (replayController != null && replayController.isViewing()) return;

		if (files.length == 1 && files[0].exists() && files[0].canRead() && files[0].isFile()) {

			try {
				String importLine = new String(Files.readAllBytes(Paths.get(files[0].toURI())), StandardCharsets.UTF_8);
				if (ValidateImportLine(importLine)) {
					loadFromFile(files[0].getPath(), 8);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void updateCellSelectionStatus() {
		
		if (sudokuPanel == null) {
			return;
		}
		
		int size = sudokuPanel.getCellSelectionSize();
		int r = sudokuPanel.getActiveRow() + 1;
		int c = sudokuPanel.getActiveCol() + 1;
		
        if (sudokuPanel.getInspectedBoxGroup() >= 0) {
            int group = sudokuPanel.getInspectedBoxGroup();
            int[] counts = sudokuPanel.getBoxReasoningCounts(group);
            statusLabelCellSelection.setText(java.text.MessageFormat.format(
                    ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.box.alsCounts"),
                    Character.toString((char) ('A' + group)), counts[0], counts[1]));
        } else if (size == 1) {
			statusLabelCellSelection.setText("R"+r+"C"+c);
		} else {
			statusLabelCellSelection.setText("");
		}
		
		statusLabelCellSelection.repaint();
	}
	
	private void initComponents() {

		levelButtonGroup = new javax.swing.ButtonGroup();
		viewButtonGroup = new javax.swing.ButtonGroup();
		colorButtonGroup = new javax.swing.ButtonGroup();
		modeButtonGroup = new javax.swing.ButtonGroup();
		statusLinePanel = new javax.swing.JPanel();
		statusPanelColorResult = new javax.swing.JPanel();
		jPanel1 = new javax.swing.JPanel();
		statusPanelColor1 = new StatusColorPanel(0);
		statusPanelColor2 = new StatusColorPanel(2);
		statusPanelColor3 = new StatusColorPanel(4);
		statusPanelColor4 = new StatusColorPanel(6);
		statusPanelColor5 = new StatusColorPanel(8);
		statusPanelColorClear = new StatusColorPanel(-1);
		statusPanelColorReset = new StatusColorPanel(-2);
		statusLabelCellCandidate = new javax.swing.JLabel();
		jSeparator1 = new javax.swing.JSeparator();
		statusLabelLevel = new javax.swing.JLabel();
		jSeparator8 = new javax.swing.JSeparator();
		progressLabel = new javax.swing.JLabel();
		jSeparator24 = new javax.swing.JSeparator();
		jSeparator25 = new javax.swing.JSeparator();
		statusLabelModus = new javax.swing.JLabel();
		statusLabelCellSelection = new javax.swing.JLabel();
		jToolBar1 = new javax.swing.JToolBar();
		undoToolButton = new javax.swing.JButton();
		redoToolButton = new javax.swing.JButton();
		jSeparator9 = new javax.swing.JSeparator();
		newGameToolButton = new javax.swing.JButton();
		jSeparator12 = new javax.swing.JSeparator();
		levelComboBox = new javax.swing.JComboBox<String>();
		jSeparator13 = new javax.swing.JSeparator();
		jSeparator11 = new javax.swing.JSeparator();
		redGreenToggleButton = new javax.swing.JToggleButton();
		f1ToggleButton = new javax.swing.JToggleButton();
		f2ToggleButton = new javax.swing.JToggleButton();
		f3ToggleButton = new javax.swing.JToggleButton();
		f4ToggleButton = new javax.swing.JToggleButton();
		f5ToggleButton = new javax.swing.JToggleButton();
		f6ToggleButton = new javax.swing.JToggleButton();
		f7ToggleButton = new javax.swing.JToggleButton();
		f8ToggleButton = new javax.swing.JToggleButton();
		f9ToggleButton = new javax.swing.JToggleButton();
		fxyToggleButton = new javax.swing.JToggleButton();
		fxyzToggleButton = new javax.swing.JToggleButton();
		outerSplitPane = new javax.swing.JSplitPane();
		hintPanel = new javax.swing.JPanel();
		newNoteButton = new javax.swing.JButton();
		hinweisAusfuehrenButton = new javax.swing.JButton();
		solveUpToButton = new javax.swing.JButton();
		hinweisAbbrechenButton = new javax.swing.JButton();
		jScrollPane1 = new javax.swing.JScrollPane();
		hinweisTextArea = new HintTextArea();
		jMenuBar1 = new javax.swing.JMenuBar();
		fileMenu = new javax.swing.JMenu();
		newMenuItem = new javax.swing.JMenuItem();
		newEmptyMenuItem = new javax.swing.JMenuItem();
		loadPuzzleMenuItem = new javax.swing.JMenuItem();
		savePuzzleMenuItem = new javax.swing.JMenuItem();
		savePuzzleAsMenuItem = new javax.swing.JMenuItem();
		importPuzzleMenuItem = new javax.swing.JMenuItem();
		exportPuzzleMenuItem = new javax.swing.JMenuItem();
		loadConfigMenuItem = new javax.swing.JMenuItem();
		saveConfigAsMenuItem = new javax.swing.JMenuItem();
		printSetupMenuItem = new javax.swing.JMenuItem();
		printMenuItem = new javax.swing.JMenuItem();
		extendedPrintMenuItem = new javax.swing.JMenuItem();
		saveAsPictureMenuItem = new javax.swing.JMenuItem();
		editGivensMenuItem = new javax.swing.JMenuItem();
		playGameMenuItem = new javax.swing.JMenuItem();
		exitMenuItem = new javax.swing.JMenuItem();
		editMenu = new javax.swing.JMenu();
		undoMenuItem = new javax.swing.JMenuItem();
		redoMenuItem = new javax.swing.JMenuItem();
		copyCluesMenuItem = new javax.swing.JMenuItem();
		copyFilledMenuItem = new javax.swing.JMenuItem();
		copyPmGridMenuItem = new javax.swing.JMenuItem();
		copyPmGridWithStepMenuItem = new javax.swing.JMenuItem();
		copyLibraryMenuItem = new javax.swing.JMenuItem();
		copySSMenuItem = new javax.swing.JMenuItem();
		pasteMenuItem = new javax.swing.JMenuItem();
		restartSpielMenuItem = new javax.swing.JMenuItem();
		resetSpielMenuItem = new javax.swing.JMenuItem();
		configMenuItem = new javax.swing.JMenuItem();
		modeMenu = new javax.swing.JMenu();
		playingMenuItem = new javax.swing.JRadioButtonMenuItem();
		learningMenuItem = new javax.swing.JRadioButtonMenuItem();
		practisingMenuItem = new javax.swing.JRadioButtonMenuItem();
		optionMenu = new javax.swing.JMenu();
		showCandidatesMenuItem = new javax.swing.JCheckBoxMenuItem();
		showCandidateHighlightMenuItem = new javax.swing.JCheckBoxMenuItem();
		operationSoundsMenuItem = new javax.swing.JCheckBoxMenuItem();
		markInvalidLinksMenuItem = new javax.swing.JCheckBoxMenuItem();
		showWrongValuesMenuItem = new javax.swing.JCheckBoxMenuItem();
		showDeviationsMenuItem = new javax.swing.JCheckBoxMenuItem();
		showColorKuMenuItem = new javax.swing.JCheckBoxMenuItem();
		colorCellsMenuItem = new javax.swing.JRadioButtonMenuItem();
		colorCandidatesMenuItem = new javax.swing.JRadioButtonMenuItem();
		levelMenu = new javax.swing.JMenu();
		levelEasyMenuItem = new javax.swing.JRadioButtonMenuItem();
		levelMediumMenuItem = new javax.swing.JRadioButtonMenuItem();
		levelHardMenuItem = new javax.swing.JRadioButtonMenuItem();
		levelDiabolicalMenuItem = new javax.swing.JRadioButtonMenuItem();
		levelExtremeMenuItem = new javax.swing.JRadioButtonMenuItem();
		puzzleMenu = new javax.swing.JMenu();
		vagueHintMenuItem = new javax.swing.JMenuItem();
		mediumHintMenuItem = new javax.swing.JMenuItem();
		solvePuzzleMenuItem = new javax.swing.JMenuItem();
		solutionCountMenuItem = new javax.swing.JMenuItem();
		solutionStepMenuItem = new javax.swing.JMenuItem();
		selectTechniqueMenuItem = new javax.swing.JMenuItem();
		backdoorSearchMenuItem = new javax.swing.JMenuItem();
		historyMenuItem = new javax.swing.JMenuItem();
		createSavePointMenuItem = new javax.swing.JMenuItem();
		restoreSavePointMenuItem = new javax.swing.JMenuItem();
		resetCandidatesMenuItem = new javax.swing.JMenuItem();
		setGivensMenuItem = new javax.swing.JMenuItem();
		setAllSinglesMenuItem = new javax.swing.JMenuItem();
		viewMenu = new javax.swing.JMenu();
		sudokuOnlyMenuItem = new javax.swing.JRadioButtonMenuItem();
		summaryMenuItem = new javax.swing.JRadioButtonMenuItem();
		solutionMenuItem = new javax.swing.JRadioButtonMenuItem();
		allStepsMenuItem = new javax.swing.JRadioButtonMenuItem();
		cellZoomMenuItem = new javax.swing.JRadioButtonMenuItem();
		showHintPanelMenuItem = new javax.swing.JCheckBoxMenuItem();
		showToolBarMenuItem = new javax.swing.JCheckBoxMenuItem();
		showHintButtonsCheckBoxMenuItem = new javax.swing.JCheckBoxMenuItem();
		fullScreenMenuItem = new javax.swing.JCheckBoxMenuItem();
		resetViewMenuItem = new javax.swing.JMenuItem();
		helpMenu = new javax.swing.JMenu();
		keyMenuItem = new javax.swing.JMenuItem();
		userManualMenuItem = new javax.swing.JMenuItem();
		solvingGuideMenuItem = new javax.swing.JMenuItem();
		projectHomePageMenuItem = new javax.swing.JMenuItem();
		reportErrorMenuItem = new javax.swing.JMenuItem();
		askQuestionMenuItem = new javax.swing.JMenuItem();
		aboutMenuItem = new javax.swing.JMenuItem();
		
		// Swing uses F10 as a default menu selector to navigate with keyboard.
		// When setting filters, I accidently hit F10 all the time which stalls the key events.
		// For this reason, I am disabling this feature and making F10 highlight bivalue cells instead.
		String key = "F10";
		KeyStroke f10 = KeyStroke.getKeyStroke(key);
		this.getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(f10, key);
		this.getRootPane().getActionMap().put(key, new AbstractAction() {
			private static final long serialVersionUID = 628736937855343558L;
			public void actionPerformed(ActionEvent e) {/*do nothing*/}
		});
		
		setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
		java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("intl/MainFrame");
		setTitle(bundle.getString("MainFrame.title"));
		setIconImage(getIcon());
		addWindowListener(new java.awt.event.WindowAdapter() {
			
			@Override
			public void windowClosed(java.awt.event.WindowEvent evt) {
				formWindowClosed(evt);
			}

			@Override
			public void windowClosing(java.awt.event.WindowEvent evt) {
				formWindowClosing(evt);
			}
		});
		addWindowFocusListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowLostFocus(java.awt.event.WindowEvent event) {
				if (!isOwnedByThisFrame(event.getOppositeWindow())) optionKeyDown = false;
				if (sudokuPanel != null) sudokuPanel.cancelAnnotationToolInteractionOnDeactivation();
			}
		});

		statusLinePanel.setBackground(new java.awt.Color(0, 153, 255));
		statusLinePanel.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

		statusPanelColorResult.setToolTipText(bundle.getString("MainFrame.statusPanelColorResult.toolTipText"));

		javax.swing.GroupLayout statusPanelColorResultLayout = new javax.swing.GroupLayout(statusPanelColorResult);
		statusPanelColorResult.setLayout(statusPanelColorResultLayout);
		statusPanelColorResultLayout.setHorizontalGroup(statusPanelColorResultLayout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 30, Short.MAX_VALUE));
		statusPanelColorResultLayout.setVerticalGroup(statusPanelColorResultLayout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));

		statusLinePanel.add(statusPanelColorResult);

		jPanel1.setOpaque(false);
		jPanel1.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 1, 0));

		statusPanelColor1.setToolTipText(bundle.getString("MainFrame.statusPanelColor1.toolTipText"));
		statusPanelColor1.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				statusPanelColor1MouseClicked(evt);
			}
		});

		javax.swing.GroupLayout statusPanelColor1Layout = new javax.swing.GroupLayout(statusPanelColor1);
		statusPanelColor1.setLayout(statusPanelColor1Layout);
		statusPanelColor1Layout.setHorizontalGroup(statusPanelColor1Layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));
		statusPanelColor1Layout.setVerticalGroup(statusPanelColor1Layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));

		jPanel1.add(statusPanelColor1);

		statusPanelColor2.setToolTipText(bundle.getString("MainFrame.statusPanelColor2.toolTipText"));
		statusPanelColor2.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				statusPanelColor2MouseClicked(evt);
			}
		});

		javax.swing.GroupLayout statusPanelColor2Layout = new javax.swing.GroupLayout(statusPanelColor2);
		statusPanelColor2.setLayout(statusPanelColor2Layout);
		statusPanelColor2Layout.setHorizontalGroup(statusPanelColor2Layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));
		statusPanelColor2Layout.setVerticalGroup(statusPanelColor2Layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));

		jPanel1.add(statusPanelColor2);

		statusPanelColor3.setToolTipText(bundle.getString("MainFrame.statusPanelColor3.toolTipText"));
		statusPanelColor3.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				statusPanelColor3MouseClicked(evt);
			}
		});

		javax.swing.GroupLayout statusPanelColor3Layout = new javax.swing.GroupLayout(statusPanelColor3);
		statusPanelColor3.setLayout(statusPanelColor3Layout);
		statusPanelColor3Layout.setHorizontalGroup(statusPanelColor3Layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));
		statusPanelColor3Layout.setVerticalGroup(statusPanelColor3Layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));

		jPanel1.add(statusPanelColor3);

		statusPanelColor4.setToolTipText(bundle.getString("MainFrame.statusPanelColor4.toolTipText"));
		statusPanelColor4.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				statusPanelColor4MouseClicked(evt);
			}
		});

		javax.swing.GroupLayout statusPanelColor4Layout = new javax.swing.GroupLayout(statusPanelColor4);
		statusPanelColor4.setLayout(statusPanelColor4Layout);
		statusPanelColor4Layout.setHorizontalGroup(statusPanelColor4Layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));
		statusPanelColor4Layout.setVerticalGroup(statusPanelColor4Layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));

		jPanel1.add(statusPanelColor4);

		statusPanelColor5.setToolTipText(bundle.getString("MainFrame.statusPanelColor5.toolTipText"));
		statusPanelColor5.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				statusPanelColor5MouseClicked(evt);
			}
		});

		javax.swing.GroupLayout statusPanelColor5Layout = new javax.swing.GroupLayout(statusPanelColor5);
		statusPanelColor5.setLayout(statusPanelColor5Layout);
		statusPanelColor5Layout.setHorizontalGroup(statusPanelColor5Layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));
		statusPanelColor5Layout.setVerticalGroup(statusPanelColor5Layout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));

		jPanel1.add(statusPanelColor5);

		statusPanelColorClear.setToolTipText(bundle.getString("MainFrame.statusPanelColorClear.toolTipText"));
		statusPanelColorClear.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				statusPanelColorClearMouseClicked(evt);
			}
		});

		javax.swing.GroupLayout statusPanelColorClearLayout = new javax.swing.GroupLayout(statusPanelColorClear);
		statusPanelColorClear.setLayout(statusPanelColorClearLayout);
		statusPanelColorClearLayout.setHorizontalGroup(statusPanelColorClearLayout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));
		statusPanelColorClearLayout.setVerticalGroup(statusPanelColorClearLayout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));

		jPanel1.add(statusPanelColorClear);

		statusPanelColorReset.setToolTipText(bundle.getString("MainFrame.statusPanelColorReset.toolTipText"));
		statusPanelColorReset.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				statusPanelColorResetMouseClicked(evt);
			}
		});

		javax.swing.GroupLayout statusPanelColorResetLayout = new javax.swing.GroupLayout(statusPanelColorReset);
		statusPanelColorReset.setLayout(statusPanelColorResetLayout);
		statusPanelColorResetLayout.setHorizontalGroup(statusPanelColorResetLayout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));
		statusPanelColorResetLayout.setVerticalGroup(statusPanelColorResetLayout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 15, Short.MAX_VALUE));

		jPanel1.add(statusPanelColorReset);

		statusLinePanel.add(jPanel1);

		statusLabelCellCandidate.setText(bundle.getString("MainFrame.statusLabelCellCandidate.text.cell"));
		statusLabelCellCandidate.setToolTipText(bundle.getString("MainFrame.statusLabelCellCandidate.toolTipText"));
		statusLabelCellCandidate.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent evt) {
				statusLabelCellCandidateMouseClicked(evt);
			}
		});
		statusLinePanel.add(statusLabelCellCandidate);

		jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);
		jSeparator1.setPreferredSize(new java.awt.Dimension(2, 17));
		statusLinePanel.add(jSeparator1);

		statusLabelLevel.setText(bundle.getString("MainFrame.statusLabelLevel.text"));
		statusLabelLevel.setToolTipText(bundle.getString("MainFrame.statusLabelLevel.toolTipText"));
		statusLinePanel.add(statusLabelLevel);

		jSeparator8.setOrientation(javax.swing.SwingConstants.VERTICAL);
		jSeparator8.setPreferredSize(new java.awt.Dimension(2, 17));
		statusLinePanel.add(jSeparator8);

		progressLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/invalid20.png")));
		progressLabel.setText("null");
		progressLabel.setToolTipText(bundle.getString("MainFrame.progressLabel.toolTipText"));
		statusLinePanel.add(progressLabel);

		jSeparator24.setOrientation(javax.swing.SwingConstants.VERTICAL);
		jSeparator24.setPreferredSize(new java.awt.Dimension(2, 17));
		statusLinePanel.add(jSeparator24);

		statusLabelModus.setText(bundle.getString("MainFrame.statusLabelModus.textPlay"));
		statusLabelModus.setToolTipText(bundle.getString("MainFrame.statusLabelModus.toolTipText"));
		statusLinePanel.add(statusLabelModus);

		jSeparator25.setOrientation(javax.swing.SwingConstants.VERTICAL);
		jSeparator25.setPreferredSize(new java.awt.Dimension(2, 17));
		statusLinePanel.add(jSeparator25);
		
		statusLabelCellSelection.setText("");
		statusLinePanel.add(statusLabelCellSelection);

		getContentPane().add(statusLinePanel, java.awt.BorderLayout.SOUTH);

		undoToolButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/undo.png")));
		undoToolButton.setToolTipText(
				SudokuUtil.getPlatformShortcutText(bundle.getString("MainFrame.undoToolButton.toolTipText")));
		undoToolButton.setEnabled(false);
		undoToolButton.setRequestFocusEnabled(false);
		undoToolButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				undoToolButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(undoToolButton);

		redoToolButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/redo.png")));
		redoToolButton.setToolTipText(
				SudokuUtil.getPlatformShortcutText(bundle.getString("MainFrame.redoToolButton.toolTipText")));
		redoToolButton.setEnabled(false);
		redoToolButton.setRequestFocusEnabled(false);
		redoToolButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				redoToolButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(redoToolButton);

		jSeparator9.setOrientation(javax.swing.SwingConstants.VERTICAL);
		jSeparator9.setMaximumSize(new java.awt.Dimension(5, 32767));
		jToolBar1.add(jSeparator9);

		newGameToolButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/hodoku02-32.png")));
		newGameToolButton.setToolTipText(bundle.getString("MainFrame.neuesSpielToolButton.toolTipText"));
		newGameToolButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				newGameToolButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(newGameToolButton);

		jSeparator12.setEnabled(false);
		jSeparator12.setMaximumSize(new java.awt.Dimension(3, 0));
		jToolBar1.add(jSeparator12);

		levelComboBox.setToolTipText(bundle.getString("MainFrame.levelComboBox.toolTipText"));
		levelComboBox.setMaximumSize(new java.awt.Dimension(80, 20));
		levelComboBox.setMinimumSize(new java.awt.Dimension(15, 8));
		levelComboBox.setPreferredSize(new java.awt.Dimension(20, 10));
		levelComboBox.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				levelComboBoxActionPerformed(evt);
			}
		});
		jToolBar1.add(levelComboBox);

		jSeparator13.setMaximumSize(new java.awt.Dimension(3, 0));
		jToolBar1.add(jSeparator13);

		jSeparator11.setOrientation(javax.swing.SwingConstants.VERTICAL);
		jSeparator11.setMaximumSize(new java.awt.Dimension(5, 32767));
		jToolBar1.add(jSeparator11);

		redGreenToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/rgDeselected1.png")));
		redGreenToggleButton.setSelected(true);
		redGreenToggleButton.setToolTipText(bundle.getString("MainFrame.redGreenToggleButton.toolTipText"));
		redGreenToggleButton.setSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/img/rgSelected1.png")));
		redGreenToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				redGreenToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(redGreenToggleButton);

		f1ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_1c.png")));
		f1ToggleButton.setToolTipText(bundle.getString("MainFrame.f1ToggleButton.toolTipText"));
		f1ToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				f1ToggleButtonActionPerformed1(evt);
			}
		});
		jToolBar1.add(f1ToggleButton);

		f2ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_2c.png")));
		f2ToggleButton.setToolTipText(bundle.getString("MainFrame.f2ToggleButton.toolTipText"));
		f2ToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				f1ToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(f2ToggleButton);

		f3ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_3c.png")));
		f3ToggleButton.setToolTipText(bundle.getString("MainFrame.f3ToggleButton.toolTipText"));
		f3ToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				f1ToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(f3ToggleButton);

		f4ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_4c.png")));
		f4ToggleButton.setToolTipText(bundle.getString("MainFrame.f4ToggleButton.toolTipText"));
		f4ToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				f1ToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(f4ToggleButton);

		f5ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_5c.png")));
		f5ToggleButton.setToolTipText(bundle.getString("MainFrame.f5ToggleButton.toolTipText"));
		f5ToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				f1ToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(f5ToggleButton);

		f6ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_6c.png")));
		f6ToggleButton.setToolTipText(bundle.getString("MainFrame.f6ToggleButton.toolTipText"));
		f6ToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				f1ToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(f6ToggleButton);

		f7ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_7c.png")));
		f7ToggleButton.setToolTipText(bundle.getString("MainFrame.f7ToggleButton.toolTipText"));
		f7ToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				f1ToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(f7ToggleButton);

		f8ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_8c.png")));
		f8ToggleButton.setToolTipText(bundle.getString("MainFrame.f8ToggleButton.toolTipText"));
		f8ToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				f1ToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(f8ToggleButton);

		f9ToggleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/f_9c.png")));
		f9ToggleButton.setToolTipText(bundle.getString("MainFrame.f9ToggleButton.toolTipText"));
		f9ToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				f1ToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(f9ToggleButton);

		fxyToggleButton.setIcon(ToolbarIcons.xyFilter());
		fxyToggleButton.setToolTipText(bundle.getString("MainFrame.fxyToggleButton.toolTipText"));
		fxyToggleButton.setFocusable(false);
		fxyToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
		fxyToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
		fxyToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				fxyToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(fxyToggleButton);

		fxyzToggleButton.setIcon(ToolbarIcons.xyzFilter());
		fxyzToggleButton.setToolTipText(bundle.getString("MainFrame.fxyzToggleButton.toolTipText"));
		fxyzToggleButton.setFocusable(false);
		fxyzToggleButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
		fxyzToggleButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
		fxyzToggleButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				fxyzToggleButtonActionPerformed(evt);
			}
		});
		jToolBar1.add(fxyzToggleButton);
		addAnnotationToolButtonsToToolbar();

		getContentPane().add(jToolBar1, java.awt.BorderLayout.NORTH);

		outerSplitPane.setDividerLocation(525);
		outerSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
		outerSplitPane.setResizeWeight(1.0);
		outerSplitPane.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
			public void propertyChange(java.beans.PropertyChangeEvent evt) {
				outerSplitPanePropertyChange(evt);
			}
		});

		hintPanel.setBorder(
				javax.swing.BorderFactory.createTitledBorder(bundle.getString("MainFrame.hintPanel.border.title")));
		hintPanel.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
			public void propertyChange(java.beans.PropertyChangeEvent evt) {
				hintPanelPropertyChange(evt);
			}
		});

		newNoteButton.setMnemonic('n');
		newNoteButton.setText(bundle.getString("MainFrame.neuerHinweisButton.text"));
		newNoteButton.setToolTipText(bundle.getString("MainFrame.neuerHinweisButton.toolTipText"));
		newNoteButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				neuerHinweisButtonActionPerformed(evt);
			}
		});

		hinweisAusfuehrenButton.setMnemonic('f');
		hinweisAusfuehrenButton.setText(bundle.getString("MainFrame.hinweisAusfuehrenButton.text"));
		hinweisAusfuehrenButton.setToolTipText(bundle.getString("MainFrame.hinweisAusfuehrenButton.toolTipText"));
		hinweisAusfuehrenButton.setEnabled(false);
		hinweisAusfuehrenButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				hinweisAusfuehrenButtonActionPerformed(evt);
			}
		});

		solveUpToButton.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.solveUpToButton.mnemonic").charAt(0));
		solveUpToButton.setText(bundle.getString("MainFrame.solveUpToButton.text"));
		solveUpToButton.setToolTipText(bundle.getString("MainFrame.solveUpToButton.toolTipText"));
		solveUpToButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				solveUpToButtonActionPerformed(evt);
			}
		});

		hinweisAbbrechenButton.setMnemonic('a');
		hinweisAbbrechenButton.setText(bundle.getString("MainFrame.hinweisAbbrechenButton.text"));
		hinweisAbbrechenButton.setToolTipText(bundle.getString("MainFrame.hinweisAbbrechenButton.toolTipText"));
		hinweisAbbrechenButton.setEnabled(false);
		hinweisAbbrechenButton.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				hinweisAbbrechenButtonActionPerformed(evt);
			}
		});

		hinweisTextArea.setColumns(20);
		hinweisTextArea.setEditable(false);
		hinweisTextArea.setLineWrap(true);
		hinweisTextArea.setRows(5);
		hinweisTextArea.setWrapStyleWord(true);
		hinweisTextArea.setToolTipText(bundle.getString("MainFrame.hinweisTextArea.toolTipText"));
		jScrollPane1.setViewportView(hinweisTextArea);

		javax.swing.GroupLayout hintPanelLayout = new javax.swing.GroupLayout(hintPanel);
		hintPanel.setLayout(hintPanelLayout);
		hintPanelLayout.setHorizontalGroup(hintPanelLayout
				.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(javax.swing.GroupLayout.Alignment.TRAILING, hintPanelLayout.createSequentialGroup()
						.addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 476, Short.MAX_VALUE)
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
						.addGroup(hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
								.addComponent(newNoteButton).addComponent(solveUpToButton))
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
						.addGroup(hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
								.addComponent(hinweisAusfuehrenButton).addComponent(hinweisAbbrechenButton))));

		hintPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {
				hinweisAbbrechenButton, hinweisAusfuehrenButton, newNoteButton, solveUpToButton });

		hintPanelLayout.setVerticalGroup(hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
				.addGroup(hintPanelLayout.createSequentialGroup()
						.addGroup(hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
								.addComponent(hinweisAusfuehrenButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23,
										javax.swing.GroupLayout.PREFERRED_SIZE)
								.addComponent(newNoteButton))
						.addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
						.addGroup(hintPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
								.addComponent(hinweisAbbrechenButton).addComponent(solveUpToButton)))
				.addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 77, Short.MAX_VALUE));

		hintPanelLayout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] { hinweisAbbrechenButton,
				hinweisAusfuehrenButton, newNoteButton, solveUpToButton });

		outerSplitPane.setRightComponent(hintPanel);

		getContentPane().add(outerSplitPane, java.awt.BorderLayout.CENTER);

		fileMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.dateiMenuMnemonic").charAt(0));
		fileMenu.setText(bundle.getString("MainFrame.dateiMenu.text"));

		newMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, SudokuUtil.getMenuShortcutMask()));
		newMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.neuMenuItemMnemonic").charAt(0));
		newMenuItem.setText(bundle.getString("MainFrame.neuMenuItem.text"));
		newMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				neuMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(newMenuItem);
		
		if (SudokuUtil.isMacOS()) {
			newEmptyMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
					java.awt.event.KeyEvent.VK_N,
					java.awt.event.InputEvent.ALT_MASK | SudokuUtil.getMenuShortcutMask()));
		}
		newEmptyMenuItem.setText(bundle.getString("MainFrame.newEmptyMenuItem.text"));
		newEmptyMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				newEmptyGameMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(newEmptyMenuItem);
		fileMenu.add(new javax.swing.JPopupMenu.Separator());

		loadPuzzleMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, SudokuUtil.getMenuShortcutMask()));
		loadPuzzleMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.loadMenuItemMnemonic").charAt(0));
		loadPuzzleMenuItem.setText(bundle.getString("MainFrame.loadPuzzleMenuItem.text"));
		loadPuzzleMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				loadPuzzleMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(loadPuzzleMenuItem);

		savePuzzleMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, SudokuUtil.getMenuShortcutMask()));
		savePuzzleMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.savePuzzleMenuItem.mnemonic").charAt(0));
		savePuzzleMenuItem.setText(bundle.getString("MainFrame.savePuzzleMenuItem.text"));
		savePuzzleMenuItem.setEnabled(false);
		savePuzzleMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				savePuzzleMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(savePuzzleMenuItem);

		savePuzzleAsMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
						java.awt.event.InputEvent.SHIFT_MASK | SudokuUtil.getMenuShortcutMask()));
		savePuzzleAsMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.saveAsMenuItemMnemonic").charAt(0));
		savePuzzleAsMenuItem.setText(bundle.getString("MainFrame.savePuzzleAsMenuItem.text"));
		savePuzzleAsMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				savePuzzleAsMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(savePuzzleAsMenuItem);

		importPuzzleMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_I, SudokuUtil.getMenuShortcutMask()));
		importPuzzleMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.saveAsMenuItemMnemonic").charAt(0));
		importPuzzleMenuItem.setText(bundle.getString("MainFrame.importPuzzleMenuItem.text"));
		importPuzzleMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				importPuzzleMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(importPuzzleMenuItem);

		exportPuzzleMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, SudokuUtil.getMenuShortcutMask()));
		exportPuzzleMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.saveAsMenuItemMnemonic").charAt(0));
		exportPuzzleMenuItem.setText(bundle.getString("MainFrame.exportPuzzleMenuItem.text"));
		exportPuzzleMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				exportPuzzleMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(exportPuzzleMenuItem);

		loadConfigMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.loadConfigMenuItem.mnemonic").charAt(0));
		loadConfigMenuItem.setText(bundle.getString("MainFrame.loadConfigMenuItem.text"));
		loadConfigMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				loadConfigMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(loadConfigMenuItem);

		saveConfigAsMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.saveConfigAsMenuItem.mnemonic").charAt(0));
		saveConfigAsMenuItem.setText(bundle.getString("MainFrame.saveConfigAsMenuItem.text"));
		saveConfigAsMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				saveConfigAsMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(saveConfigAsMenuItem);
		fileMenu.add(new javax.swing.JPopupMenu.Separator());

		printSetupMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.seiteEinrichtenMenuItemMnemonic").charAt(0));
		printSetupMenuItem.setText(bundle.getString("MainFrame.seiteEinrichtenMenuItem.text"));
		printSetupMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				printSetupMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(printSetupMenuItem);

		printMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_P, SudokuUtil.getMenuShortcutMask()));
		printMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.druckenMenuItemMnemonic").charAt(0));
		printMenuItem.setText(bundle.getString("MainFrame.druckenMenuItem.text"));
		printMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				druckenMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(printMenuItem);

		extendedPrintMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.extendedPrintMenuItem.mnemonic").charAt(0));
		extendedPrintMenuItem.setText(bundle.getString("MainFrame.extendedPrintMenuItem.text"));
		extendedPrintMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				extendedPrintMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(extendedPrintMenuItem);

		saveAsPictureMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.speichernAlsBildMenuItemMnemonic").charAt(0));
		saveAsPictureMenuItem.setText(bundle.getString("MainFrame.speichernAlsBildMenuItem.text"));
		saveAsPictureMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				speichernAlsBildMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(saveAsPictureMenuItem);
		fileMenu.add(new javax.swing.JPopupMenu.Separator());

		if (!SudokuUtil.isMacOS()) {
			exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
					java.awt.event.KeyEvent.VK_X, java.awt.event.InputEvent.ALT_MASK));
		}
		exitMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.beendenMenuItemMnemonic").charAt(0));
		exitMenuItem.setText(bundle.getString("MainFrame.beendenMenuItem.text"));
		exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				exitMenuItemActionPerformed(evt);
			}
		});
		fileMenu.add(exitMenuItem);

		jMenuBar1.add(fileMenu);

		editMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.bearbeitenMenuMnemonic").charAt(0));
		editMenu.setText(bundle.getString("MainFrame.bearbeitenMenu.text"));

		undoMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, SudokuUtil.getMenuShortcutMask()));
		undoMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.undoMenuItemMnemonic").charAt(0));
		undoMenuItem.setText(bundle.getString("MainFrame.undoMenuItem.text"));
		undoMenuItem.setEnabled(false);
		undoMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				undoMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(undoMenuItem);

		redoMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, SudokuUtil.getMenuShortcutMask()));
		redoMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.redoMenuItemMnemonic").charAt(0));
		redoMenuItem.setText(bundle.getString("MainFrame.redoMenuItem.text"));
		redoMenuItem.setEnabled(false);
		redoMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				redoMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(redoMenuItem);
		if (SudokuUtil.isMacOS()) {
			KeyStroke redoAlternate = KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z,
					java.awt.event.InputEvent.SHIFT_MASK | SudokuUtil.getMenuShortcutMask());
			String redoAlternateAction = "redoAlternate";
			getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
					.put(redoAlternate, redoAlternateAction);
			getRootPane().getActionMap().put(redoAlternateAction, new AbstractAction() {
				private static final long serialVersionUID = 1L;

				@Override
				public void actionPerformed(ActionEvent evt) {
					if (redoMenuItem.isEnabled()) {
						redoMenuItem.doClick();
					}
				}
			});
		}
		editMenu.add(new javax.swing.JPopupMenu.Separator());

		copyCluesMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_G, SudokuUtil.getMenuShortcutMask()));
		copyCluesMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.copyCluesMenuItemMnemonic").charAt(0));
		copyCluesMenuItem.setText(bundle.getString("MainFrame.copyCluesMenuItem.text"));
		copyCluesMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				copyCluesMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(copyCluesMenuItem);

		copyFilledMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.copyFilledMenuItemMnemonic").charAt(0));
		copyFilledMenuItem.setText(bundle.getString("MainFrame.copyFilledMenuItem.text"));
		copyFilledMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				copyFilledMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(copyFilledMenuItem);

		copyPmGridMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, SudokuUtil.getMenuShortcutMask()));
		copyPmGridMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.copyPmGridMenuItemMnemonic").charAt(0));
		copyPmGridMenuItem.setText(bundle.getString("MainFrame.copyPmGridMenuItem.text"));
		copyPmGridMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				copyPmGridMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(copyPmGridMenuItem);

		copyPmGridWithStepMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.copyPmGridWithStepMenuItemMnemonic").charAt(0));
		if (SudokuUtil.isMacOS()) {
			copyPmGridWithStepMenuItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C,
					java.awt.event.InputEvent.ALT_MASK | SudokuUtil.getMenuShortcutMask()));
		}
		copyPmGridWithStepMenuItem.setText(bundle.getString("MainFrame.copyPmGridWithStepMenuItem.text"));
		copyPmGridWithStepMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				copyPmGridWithStepMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(copyPmGridWithStepMenuItem);

		copyLibraryMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.copyLibraryMenuItemMnemonic").charAt(0));
		copyLibraryMenuItem.setText(bundle.getString("MainFrame.copyLibraryMenuItem.text"));
		copyLibraryMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				copyLibraryMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(copyLibraryMenuItem);

		copySSMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.copySSMenuItem.mnemonic").charAt(0));
		copySSMenuItem.setText(bundle.getString("MainFrame.copySSMenuItem.text"));
		copySSMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				copySSMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(copySSMenuItem);

		pasteMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, SudokuUtil.getMenuShortcutMask()));
		pasteMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.pasteMenuItemMnemonic").charAt(0));
		pasteMenuItem.setText(bundle.getString("MainFrame.pasteMenuItem.text"));
		pasteMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				pasteMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(pasteMenuItem);
		editMenu.add(new javax.swing.JPopupMenu.Separator());
		
		editGivensMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("editGivensMenuItemMnemonic").charAt(0));
		editGivensMenuItem.setText(bundle.getString("MainFrame.editGivensMenuItem.text"));
		editGivensMenuItem.setEnabled(false);
		editGivensMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				spielEditierenMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(editGivensMenuItem);

		playGameMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
				java.awt.event.KeyEvent.VK_ENTER, SudokuUtil.getMenuShortcutMask()));
		playGameMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.spielenMenuItemMnemonic").charAt(0));
		playGameMenuItem.setText(bundle.getString("MainFrame.playGameMenuItem.text"));
		playGameMenuItem.setEnabled(true);
		playGameMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				spielSpielenMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(playGameMenuItem);
		editMenu.add(new javax.swing.JPopupMenu.Separator());

		restartSpielMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, SudokuUtil.getMenuShortcutMask()));
		restartSpielMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.restartSpielMenuItemMnemonic").charAt(0));
		restartSpielMenuItem.setText(bundle.getString("MainFrame.restartSpielMenuItem.text"));
		restartSpielMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				restartSpielMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(restartSpielMenuItem);

		resetSpielMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.resetSpielMenuItemMnemonic").charAt(0));
		resetSpielMenuItem.setText(bundle.getString("MainFrame.resetSpielMenuItem.text"));
		resetSpielMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				resetSpielMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(resetSpielMenuItem);
		editMenu.add(new javax.swing.JPopupMenu.Separator());

		configMenuItem.setAccelerator(SudokuUtil.getPlatformKeyStroke(
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.configMenuItemAccelerator")));
		configMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.configMenuItemMnemonic").charAt(0));
		configMenuItem.setText(bundle.getString("MainFrame.configMenuItem.text"));
		configMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				configMenuItemActionPerformed(evt);
			}
		});
		editMenu.add(configMenuItem);

		jMenuBar1.add(editMenu);

		modeMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.modeMenu.mnemonic").charAt(0));
		modeMenu.setText(bundle.getString("MainFrame.modeMenu.text"));

		modeButtonGroup.add(playingMenuItem);
		playingMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.playingMenuItem.mnemonic").charAt(0));
		playingMenuItem.setSelected(true);
		playingMenuItem.setText(bundle.getString("MainFrame.playingMenuItem.text"));
		playingMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				playingMenuItemActionPerformed(evt);
			}
		});
		modeMenu.add(playingMenuItem);

		modeButtonGroup.add(learningMenuItem);
		learningMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.learningMenuItem.mnemonic").charAt(0));
		learningMenuItem.setText(bundle.getString("MainFrame.learningMenuItem.text"));
		learningMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				learningMenuItemActionPerformed(evt);
			}
		});
		modeMenu.add(learningMenuItem);

		modeButtonGroup.add(practisingMenuItem);
		practisingMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.practisingMenuItem.mnemonic").charAt(0));
		practisingMenuItem.setText(bundle.getString("MainFrame.practisingMenuItem.text"));
		practisingMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				practisingMenuItemActionPerformed(evt);
			}
		});
		modeMenu.add(practisingMenuItem);

		jMenuBar1.add(modeMenu);

		optionMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.optionenMenuMnemonic").charAt(0));
		optionMenu.setText(bundle.getString("MainFrame.optionenMenu.text"));

		showCandidatesMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.showCandidatesMenuItemMnemonic").charAt(0));
		showCandidatesMenuItem.setText(bundle.getString("MainFrame.showCandidatesMenuItem.text"));
		showCandidatesMenuItem.setSelected(Options.getInstance().isShowCandidates());
		showCandidatesMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				showCandidatesMenuItemActionPerformed(evt);
			}
		});
		optionMenu.add(showCandidatesMenuItem);

		showCandidateHighlightMenuItem.setText(bundle.getString("MainFrame.showCandidateHighlightMenuItem.text"));
		showCandidateHighlightMenuItem.setSelected(Options.getInstance().isShowCandidateHighlight());
		showCandidateHighlightMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				showCandidateHighlightMenuItemActionPerformed(evt);
			}
		});
		optionMenu.add(showCandidateHighlightMenuItem);

		operationSoundsMenuItem.setText(bundle.getString("MainFrame.operationSoundsMenuItem.text"));
		operationSoundsMenuItem.setSelected(Options.getInstance().isOperationSoundsEnabled());
		operationSoundsMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				operationSoundsMenuItemActionPerformed(evt);
			}
		});
		optionMenu.add(operationSoundsMenuItem);

		markInvalidLinksMenuItem.setText(bundle.getString("MainFrame.markInvalidLinksMenuItem.text"));
		markInvalidLinksMenuItem.setSelected(Options.getInstance().isMarkInvalidLinks());
		markInvalidLinksMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				markInvalidLinksMenuItemActionPerformed(evt);
			}
		});
		optionMenu.add(markInvalidLinksMenuItem);

		showWrongValuesMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.showWrongValuesMenuItemMnemonic").charAt(0));
		showWrongValuesMenuItem.setText(bundle.getString("MainFrame.showWrongValuesMenuItem.text"));
		showWrongValuesMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				showWrongValuesMenuItemActionPerformed(evt);
			}
		});
		optionMenu.add(showWrongValuesMenuItem);

		showDeviationsMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.showDeviationsMenuItemMnemonic").charAt(0));
		showDeviationsMenuItem.setSelected(true);
		showDeviationsMenuItem.setText(bundle.getString("MainFrame.showDeviationsMenuItem.text"));
		showDeviationsMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				showDeviationsMenuItemActionPerformed(evt);
			}
		});
		optionMenu.add(showDeviationsMenuItem);

		showColorKuMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.showColorKuMenuItem.mnemonic").charAt(0));
		showColorKuMenuItem.setSelected(true);
		showColorKuMenuItem.setText(bundle.getString("MainFrame.showColorKuMenuItem.text"));
		showColorKuMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				showColorKuMenuItemActionPerformed(evt);
			}
		});
		optionMenu.add(showColorKuMenuItem);
		optionMenu.add(new javax.swing.JPopupMenu.Separator());

		colorButtonGroup.add(colorCellsMenuItem);
		colorCellsMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.colorCellsMenuItem.mnemonic").charAt(0));
		colorCellsMenuItem.setSelected(true);
		colorCellsMenuItem.setText(bundle.getString("MainFrame.colorCellsMenuItem.text"));
		colorCellsMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				colorCellsMenuItemActionPerformed(evt);
			}
		});
		colorCellsMenuItem.setVisible(false); // legacy session/action compatibility

		colorButtonGroup.add(colorCandidatesMenuItem);
		colorCandidatesMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.colorCandidatesMenuItem.mnemonic").charAt(0));
		colorCandidatesMenuItem.setText(bundle.getString("MainFrame.colorCandidatesMenuItem.text"));
		colorCandidatesMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				colorCandidatesMenuItemActionPerformed(evt);
			}
		});
		optionMenu.add(colorCandidatesMenuItem);

		optionMenu.add(new javax.swing.JPopupMenu.Separator());

		levelMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.levelMenuMnemonic").charAt(0));
		levelMenu.setText(bundle.getString("MainFrame.levelMenu.text"));

		levelButtonGroup.add(levelEasyMenuItem);
		levelEasyMenuItem.setSelected(true);
		levelEasyMenuItem.setText("Leicht");
		levelEasyMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				levelEasyMenuItemActionPerformed(evt);
			}
		});
		levelMenu.add(levelEasyMenuItem);

		levelButtonGroup.add(levelMediumMenuItem);
		levelMediumMenuItem.setText("Mittel");
		levelMediumMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				levelMediumMenuItemActionPerformed(evt);
			}
		});
		levelMenu.add(levelMediumMenuItem);

		levelButtonGroup.add(levelHardMenuItem);
		levelHardMenuItem.setText("Schwer\n");
		levelHardMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				levelHardMenuItemActionPerformed(evt);
			}
		});
		levelMenu.add(levelHardMenuItem);

		levelButtonGroup.add(levelDiabolicalMenuItem);
		levelDiabolicalMenuItem.setText("Unfair");
		levelDiabolicalMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				levelDiabolicalMenuItemActionPerformed(evt);
			}
		});
		levelMenu.add(levelDiabolicalMenuItem);

		levelButtonGroup.add(levelExtremeMenuItem);
		levelExtremeMenuItem.setText("Extrem");
		levelExtremeMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				levelExtremeMenuItemActionPerformed(evt);
			}
		});
		levelMenu.add(levelExtremeMenuItem);

		optionMenu.add(levelMenu);

		jMenuBar1.add(optionMenu);

		puzzleMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.raetselMenuMnemonic").charAt(0));
		puzzleMenu.setText(bundle.getString("MainFrame.raetselMenu.text"));

		vagueHintMenuItem.setAccelerator(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F12, java.awt.event.InputEvent.ALT_MASK));
		vagueHintMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.vageHintMenuItemMnemonic").charAt(0));
		vagueHintMenuItem.setText(bundle.getString("MainFrame.vageHintMenuItem"));
		vagueHintMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				vageHintMenuItemActionPerformed(evt);
			}
		});
		puzzleMenu.add(vagueHintMenuItem);

		mediumHintMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F12,
				SudokuUtil.getMenuShortcutMask()));
		mediumHintMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.mediumHintMenuItemMnemonic").charAt(0));
		mediumHintMenuItem.setText(bundle.getString("MainFrame.mediumHintMenuItem.text"));
		mediumHintMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				mediumHintMenuItemActionPerformed(evt);
			}
		});
		puzzleMenu.add(mediumHintMenuItem);

		solutionStepMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F12, 0));
		solutionStepMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.loesungsSchrittMenuItemMnemonic").charAt(0));
		solutionStepMenuItem.setText(bundle.getString("MainFrame.loesungsSchrittMenuItem.text"));
		solutionStepMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				loesungsSchrittMenuItemActionPerformed(evt);
			}
		});
		puzzleMenu.add(solutionStepMenuItem);

		selectTechniqueMenuItem.setText(bundle.getString("MainFrame.selectTechniqueMenuItem.text"));
		selectTechniqueMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F12,
				SudokuUtil.getMenuShortcutMask() | java.awt.event.InputEvent.ALT_MASK));
		selectTechniqueMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				if ((evt.getModifiers() & java.awt.event.ActionEvent.ALT_MASK) != 0) {
					optionKeyDown = true;
				}
				// A menu item action runs before the native/Aqua menu has completely
				// disappeared. Showing another popup synchronously lets that teardown
				// cancel the new selector as well.
				SwingUtilities.invokeLater(() -> showTechniqueSelector());
			}
		});
		puzzleMenu.add(selectTechniqueMenuItem);
        javax.swing.JMenuItem currentReasoningMenu = new javax.swing.JMenuItem(
                bundle.getString("MainFrame.currentReasoning.title"));
        currentReasoningMenu.setAccelerator(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F12, KeyEvent.SHIFT_DOWN_MASK));
        currentReasoningMenu.addActionListener(e -> SwingUtilities.invokeLater(() -> showCurrentReasoning()));
        puzzleMenu.add(currentReasoningMenu);
		puzzleMenu.add(new javax.swing.JPopupMenu.Separator());

		solvePuzzleMenuItem.setText(bundle.getString("MainFrame.solvePuzzleMenuItem.text"));
		solvePuzzleMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				showPuzzleSolution();
			}
		});
		puzzleMenu.add(solvePuzzleMenuItem);
		
		solutionCountMenuItem.setAccelerator(
			javax.swing.KeyStroke.getKeyStroke(
				java.awt.event.KeyEvent.VK_M, 
				SudokuUtil.getMenuShortcutMask()
			)
		);
		solutionCountMenuItem.setText(bundle.getString("MainFrame.solutionCountMenuItem.text"));
		solutionCountMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				showSolutionCount();
			}
		});
		puzzleMenu.add(solutionCountMenuItem);		
		puzzleMenu.add(new JSeparator());

		backdoorSearchMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.backdoorSearchMenuItem.mnemonic").charAt(0));
		backdoorSearchMenuItem.setText(bundle.getString("MainFrame.backdoorSearchMenuItem.text"));
		backdoorSearchMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				backdoorSearchMenuItemActionPerformed(evt);
			}
		});
		puzzleMenu.add(backdoorSearchMenuItem);

		historyMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.historyMenuItem.mnemonic").charAt(0));
		historyMenuItem.setText(bundle.getString("MainFrame.historyMenuItem.text"));
		historyMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				historyMenuItemActionPerformed(evt);
			}
		});
		puzzleMenu.add(historyMenuItem);

		createSavePointMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.setSavePointMenuItem.mnemonic").charAt(0));
		if (SudokuUtil.isMacOS()) {
			createSavePointMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
					java.awt.event.KeyEvent.VK_S,
					java.awt.event.InputEvent.ALT_MASK | SudokuUtil.getMenuShortcutMask()));
		}
		createSavePointMenuItem.setText(bundle.getString("MainFrame.createSavePointMenuItem.text"));
		createSavePointMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				createSavePointMenuItemActionPerformed(evt);
			}
		});
		puzzleMenu.add(createSavePointMenuItem);

		restoreSavePointMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.restoreSavePointMenuItem.mnemonic").charAt(0));
		if (SudokuUtil.isMacOS()) {
			restoreSavePointMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
					java.awt.event.KeyEvent.VK_O,
					java.awt.event.InputEvent.ALT_MASK | SudokuUtil.getMenuShortcutMask()));
		}
		restoreSavePointMenuItem.setText(bundle.getString("MainFrame.restoreSavePointMenuItem.text"));
		restoreSavePointMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				restoreSavePointMenuItemActionPerformed(evt);
			}
		});
		puzzleMenu.add(restoreSavePointMenuItem);
        viewReplayMenuItem = new javax.swing.JMenuItem(ReplayText.text("viewCurrent"));
        viewReplayMenuItem.addActionListener(e -> {
            if (replayController != null) replayController.openViewer(replayController.session());
        });
        puzzleMenu.add(viewReplayMenuItem);
        replayLibraryMenuItem = new javax.swing.JMenuItem(ReplayText.text("library") + "…");
        replayLibraryMenuItem.addActionListener(e -> {
            if (replayController != null) replayController.openLibrary();
        });
        puzzleMenu.add(replayLibraryMenuItem);
		puzzleMenu.add(new javax.swing.JPopupMenu.Separator());
		
		resetCandidatesMenuItem.setText(bundle.getString("MainFrame.resetCandidatesMenuItem.text"));
		resetCandidatesMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				resetCandidatesMenuItemActionPerformed(evt);
			}
		});
		puzzleMenu.add(resetCandidatesMenuItem);		

		setGivensMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.setGivensMenuItem.mnemonic").charAt(0));
		setGivensMenuItem.setText(bundle.getString("MainFrame.setGivensMenuItem.text"));
		setGivensMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				setGivensMenuItemActionPerformed(evt);
			}
		});
		puzzleMenu.add(setGivensMenuItem);
		puzzleMenu.add(new javax.swing.JPopupMenu.Separator());

		setAllSinglesMenuItem
				.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F11, 0));
		setAllSinglesMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.alleHiddenSinglesSetzenMenuItemMnemonic").charAt(0));
		setAllSinglesMenuItem.setText(bundle.getString("MainFrame.alleHiddenSinglesSetzenMenuItem.text"));
		setAllSinglesMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				alleHiddenSinglesSetzenMenuItemActionPerformed(evt);
			}
		});
		puzzleMenu.add(setAllSinglesMenuItem);

		jMenuBar1.add(puzzleMenu);

		viewMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.ansichtMenuMnemonic").charAt(0));
		viewMenu.setText(bundle.getString("MainFrame.ansichtMenu.text"));

		if (SudokuUtil.isMacOS()) {
			sudokuOnlyMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(
					java.awt.event.KeyEvent.VK_0,
					java.awt.event.InputEvent.SHIFT_MASK | SudokuUtil.getMenuShortcutMask()));
		} else {
			sudokuOnlyMenuItem.setAccelerator(
					SudokuUtil.getPlatformKeyStroke("shift control " + java.util.ResourceBundle.getBundle("intl/MainFrame")
							.getString("MainFrame.sudokuOnlyMenuItemMnemonic").toUpperCase().charAt(0)));
		}
		viewButtonGroup.add(sudokuOnlyMenuItem);
		sudokuOnlyMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.sudokuOnlyMenuItemMnemonic").charAt(0));
		sudokuOnlyMenuItem.setSelected(true);
		sudokuOnlyMenuItem.setText(bundle.getString("MainFrame.sudokuOnlyMenuItem.text"));
		sudokuOnlyMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				sudokuOnlyMenuItemActionPerformed(evt);
			}
		});
		viewMenu.add(sudokuOnlyMenuItem);
		viewMenu.add(new javax.swing.JPopupMenu.Separator());

		summaryMenuItem.setAccelerator(SudokuUtil.getPlatformKeyStroke("shift control " + java.util.ResourceBundle
				.getBundle("intl/MainFrame").getString("MainFrame.summaryMenuItemMnemonic").toUpperCase().charAt(0)));
		viewButtonGroup.add(summaryMenuItem);
		summaryMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.summaryMenuItemMnemonic").charAt(0));
		summaryMenuItem.setText(bundle.getString("MainFrame.summaryMenuItem.text"));
		summaryMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				summaryMenuItemActionPerformed(evt);
			}
		});
		viewMenu.add(summaryMenuItem);

		solutionMenuItem.setAccelerator(SudokuUtil.getPlatformKeyStroke("shift control " + java.util.ResourceBundle
				.getBundle("intl/MainFrame").getString("MainFrame.solutionMenuItemMnemonic").toUpperCase().charAt(0)));
		viewButtonGroup.add(solutionMenuItem);
		solutionMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.solutionMenuItemMnemonic").charAt(0));
		solutionMenuItem.setText(bundle.getString("MainFrame.solutionMenuItem.text"));
		solutionMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				solutionMenuItemActionPerformed(evt);
			}
		});
		viewMenu.add(solutionMenuItem);

		allStepsMenuItem.setAccelerator(SudokuUtil.getPlatformKeyStroke("shift control " + java.util.ResourceBundle
				.getBundle("intl/MainFrame").getString("MainFrame.allStepsMenuItemMnemonic").toUpperCase().charAt(0)));
		viewButtonGroup.add(allStepsMenuItem);
		allStepsMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.allStepsMenuItemMnemonic").charAt(0));
		allStepsMenuItem.setText(bundle.getString("MainFrame.allStepsMenuItem.text"));
		allStepsMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				allStepsMenuItemActionPerformed(evt);
			}
		});
		viewMenu.add(allStepsMenuItem);

		cellZoomMenuItem.setAccelerator(SudokuUtil.getPlatformKeyStroke("shift control " + java.util.ResourceBundle
				.getBundle("intl/MainFrame").getString("MainFrame.cellZoomMenuItemMnemonic").toUpperCase().charAt(0)));
		viewButtonGroup.add(cellZoomMenuItem);
		cellZoomMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.cellZoomMenuItemMnemonic").charAt(0));
		cellZoomMenuItem.setText(bundle.getString("MainFrame.cellZoomMenuItem.text"));
		cellZoomMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				cellZoomMenuItemActionPerformed(evt);
			}
		});
		viewMenu.add(cellZoomMenuItem);
		viewMenu.add(new javax.swing.JPopupMenu.Separator());

		showHintPanelMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.showHintPanelMenuItem.mnemonic").charAt(0));
		showHintPanelMenuItem.setSelected(true);
		showHintPanelMenuItem.setText(bundle.getString("MainFrame.showHintPanelMenuItem.text"));
		showHintPanelMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				showHintPanelMenuItemActionPerformed(evt);
			}
		});
		viewMenu.add(showHintPanelMenuItem);

		showToolBarMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.showToolBarMenuItem.mnemonic").charAt(0));
		showToolBarMenuItem.setSelected(true);
		showToolBarMenuItem.setText(bundle.getString("MainFrame.showToolBarMenuItem.text"));
		showToolBarMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				showToolBarMenuItemActionPerformed(evt);
			}
		});
		viewMenu.add(showToolBarMenuItem);

		showHintButtonsCheckBoxMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.showHintButtonsCheckBoxMenuItem.mnemonic").charAt(0));
		showHintButtonsCheckBoxMenuItem.setText(bundle.getString("MainFrame.showHintButtonsCheckBoxMenuItem.text"));
		showHintButtonsCheckBoxMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				showHintButtonsCheckBoxMenuItemActionPerformed(evt);
			}
		});
		viewMenu.add(showHintButtonsCheckBoxMenuItem);

		if (SudokuUtil.isMacOS()) {
			javax.swing.JMenu appearanceMenu = new javax.swing.JMenu(bundle.getString("MainFrame.appearanceMenu.text"));
			javax.swing.ButtonGroup appearanceGroup = new javax.swing.ButtonGroup();
			AppearanceMode selectedAppearance = AppearanceMode.fromName(Options.getInstance().getAppearanceMode());
			for (final AppearanceMode mode : AppearanceMode.values()) {
				String appearanceKey = "MainFrame.appearance." + mode.name().toLowerCase(java.util.Locale.ROOT);
				javax.swing.JRadioButtonMenuItem item = new javax.swing.JRadioButtonMenuItem(
						bundle.getString(appearanceKey));
				item.setSelected(mode == selectedAppearance);
				item.addActionListener(new java.awt.event.ActionListener() {
					@Override
					public void actionPerformed(java.awt.event.ActionEvent evt) {
						if (!appearanceModeSelected(mode)) {
							AppearanceMode restored = AppearanceMode.fromName(
									Options.getInstance().getAppearanceMode());
							java.util.Enumeration<javax.swing.AbstractButton> buttons =
									appearanceGroup.getElements();
							while (buttons.hasMoreElements()) {
								javax.swing.AbstractButton button = buttons.nextElement();
								button.setSelected(restored.name().equals(
										button.getClientProperty("appearanceMode")));
							}
						}
					}
				});
				item.putClientProperty("appearanceMode", mode.name());
				appearanceGroup.add(item);
				appearanceMenu.add(item);
			}
			viewMenu.add(appearanceMenu);
		}

		fullScreenMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F,
				java.awt.event.InputEvent.SHIFT_MASK | SudokuUtil.getMenuShortcutMask()));
		fullScreenMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.fullScreenMenuItem.mnemonic").charAt(0));
		fullScreenMenuItem.setText(bundle.getString("MainFrame.fullScreenMenuItem.text"));
		fullScreenMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				fullScreenMenuItemActionPerformed(evt);
			}
		});
		viewMenu.add(fullScreenMenuItem);
		viewMenu.add(new javax.swing.JPopupMenu.Separator());

		resetViewMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.resetViewMenuItemMnemonic").charAt(0));
		resetViewMenuItem.setText(bundle.getString("MainFrame.resetViewMenuItem.text"));
		resetViewMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				resetViewMenuItemActionPerformed(evt);
			}
		});
		viewMenu.add(resetViewMenuItem);

		jMenuBar1.add(viewMenu);

		helpMenu.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.helpMenu.mnemonic").charAt(0));
		helpMenu.setText(bundle.getString("MainFrame.helpMenu.text"));

		keyMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.keyMenuItem.mnemonic").charAt(0));
		keyMenuItem.setText(bundle.getString("MainFrame.keyMenuItem.text"));
		keyMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				keyMenuItemActionPerformed(evt);
			}
		});
		helpMenu.add(keyMenuItem);
		helpMenu.add(new javax.swing.JPopupMenu.Separator());

		userManualMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.userManualMenuItem.mnemonic").charAt(0));
		userManualMenuItem.setText(bundle.getString("MainFrame.userManualMenuItem.text"));
		userManualMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				userManualMenuItemActionPerformed(evt);
			}
		});
		helpMenu.add(userManualMenuItem);

		solvingGuideMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.solvingGuideMenuItem.mnemonic").charAt(0));
		solvingGuideMenuItem.setText(bundle.getString("MainFrame.solvingGuideMenuItem.text"));
		solvingGuideMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				solvingGuideMenuItemActionPerformed(evt);
			}
		});
		helpMenu.add(solvingGuideMenuItem);

		projectHomePageMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.projectHomePageMenuItem.mnemonic").charAt(0));
		projectHomePageMenuItem.setText(bundle.getString("MainFrame.projectHomePageMenuItem.text"));
		projectHomePageMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				projectHomePageMenuItemActionPerformed(evt);
			}
		});
		helpMenu.add(projectHomePageMenuItem);
		helpMenu.add(new javax.swing.JPopupMenu.Separator());

		reportErrorMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.reportErrorMenuItem.mnemonic").charAt(0));
		reportErrorMenuItem.setText(bundle.getString("MainFrame.reportErrorMenuItem.text"));
		reportErrorMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				reportErrorMenuItemActionPerformed(evt);
			}
		});
		helpMenu.add(reportErrorMenuItem);

		askQuestionMenuItem.setMnemonic(java.util.ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.askQuestionMenuItem.mnemonic").charAt(0));
		askQuestionMenuItem.setText(bundle.getString("MainFrame.askQuestionMenuItem.text"));
		askQuestionMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				askQuestionMenuItemActionPerformed(evt);
			}
		});
		helpMenu.add(askQuestionMenuItem);
		helpMenu.add(new javax.swing.JPopupMenu.Separator());

		aboutMenuItem.setMnemonic(
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.aboutMenuItem.").charAt(0));
		aboutMenuItem.setText(bundle.getString("MainFrame.aboutMenuItem.text"));
		aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(java.awt.event.ActionEvent evt) {
				aboutMenuItemActionPerformed(evt);
			}
		});
		helpMenu.add(aboutMenuItem);

		jMenuBar1.add(helpMenu);

		setJMenuBar(jMenuBar1);

		pack();
	}

	private void savePuzzleAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		saveToFile(true);
	}

	private void importPuzzleMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		importWindow.setVisible(true);
		importWindow.focusCursor();
	}

	private void exportPuzzleMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		exportWindow.setVisible(true);
	}

	private void loadPuzzleMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		loadFromFile(true);
	}

	private void configMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		
		new ConfigDialog(this, true, -1).setVisible(true);
		sudokuPanel.resetActiveColor();
		
		if (cellZoomPanel.isColoring()) {
			statusPanelColorResult.setBackground(sudokuPanel.getActiveColor());
		}
		
		sudokuPanel.setColorIconsInPopupMenu();
		check();
		fixFocus();
		sudokuPanel.repaint();
		repaint();
	}

	private void statusLabelCellCandidateMouseClicked(java.awt.event.MouseEvent evt) {
		sudokuPanel.updateCellZoomPanel();
		check();
		fixFocus();
	}

	private void allStepsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		allStepsPanel.setSudoku(sudokuPanel.getSudoku());
		setSplitPane(allStepsPanel);
		// initializeResultPanels();
		repaint();
	}

	private void solutionMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setSplitPane(solutionPanel);
		// initializeResultPanels();
		repaint();
	}

	private void sudokuOnlyMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		splitPanel.setRight(null);
		if (getExtendedState() != JFrame.MAXIMIZED_BOTH) {
			if (splitPanel.getBounds().getWidth() < splitPanel.getBounds().getHeight()) {
				setSize(getWidth() + 1, getHeight());
			}
		}
	}

	private void summaryMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setSplitPane(summaryPanel);
		// initializeResultPanels();
		repaint();
	}

	private void speichernAlsBildMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		WriteAsPNGDialog dlg = new WriteAsPNGDialog(this, true, saveImageDefaultSize, bildAufloesung, bildEinheit);
		dlg.setVisible(true);
		if (dlg.isOk()) {
			File bildFile = dlg.getBildFile();
			bildAufloesung = dlg.getAufloesung();
			saveImageDefaultSize = dlg.getBildSize();
			bildEinheit = dlg.getEinheit();
			int size = 0;
			switch (bildEinheit) {
			case 0:
				size = (int) (saveImageDefaultSize / 25.4 * bildAufloesung);
				break;
			case 1:
				size = (int) (saveImageDefaultSize * bildAufloesung);
				break;
			case 2:
				size = (int) saveImageDefaultSize;
				break;
			}
			if (bildFile.exists()) {
				// Override warning!
				MessageFormat msgf = new MessageFormat("");
				Object[] args = new Object[] { bildFile.getName() };
				msgf.applyPattern(
						java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.file_exists"));
				String warning = msgf.format(args);
				String title = java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.hint");
				if (JOptionPane.showConfirmDialog(null, warning, title,
						JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
					return;
				}
			}
			sudokuPanel.saveSudokuAsPNG(bildFile, size, bildAufloesung);
		}
	}

	private void druckenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		if (job == null) {
			job = PrinterJob.getPrinterJob();
		}
		if (pageFormat == null) {
			pageFormat = job.defaultPage();
		}
		try {
			job.setPrintable(sudokuPanel, pageFormat);
			if (job.printDialog()) {
				job.print();
			}
		} catch (PrinterException ex) {
			JOptionPane.showMessageDialog(
				this,
				ex.toString(),
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"),
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	private void printSetupMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		if (job == null) {
			job = PrinterJob.getPrinterJob();
		}
		if (pageFormat == null) {
			pageFormat = job.defaultPage();
		}
		pageFormat = job.pageDialog(pageFormat);
	}

	private void restartSpielMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		if (JOptionPane.showConfirmDialog(this,
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.start_new_game"),
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.start_new"),
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			sudokuPanel.setSudoku(sudokuPanel.getSudokuString(ClipboardMode.CLUES_ONLY));
			sudokuPanel.checkProgress();
			allStepsPanel.setSudoku(sudokuPanel.getSudoku());
			initializeResultPanels();
			repaint();
			setPlay(true);
			check();
			fixFocus();
			if (replayController != null) replayController.startNewAttempt();
		}
	}

	private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		requestQuit(null);
	}

	private void copyLibraryMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		copyToClipboard(ClipboardMode.LIBRARY, false);
	}

	private void copyPmGridWithStepMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        String chainText=sudokuPanel.copyChainText();
        if(chainText!=null){
            try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(chainText),null);
                OperationSoundPlayer.play(OperationSoundPlayer.Sound.COPY);
            }catch(IllegalStateException busy){Logger.getLogger(getClass().getName()).log(Level.WARNING,"Clipboard busy",busy);}
            return;
        }
		SolutionStep activeStep = sudokuPanel.getStep();
		if (activeStep == null) {
			JOptionPane.showMessageDialog(
				this,
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.no_step_selected"),
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"),
				JOptionPane.ERROR_MESSAGE
			);
			return;
		}
		copyToClipboard(ClipboardMode.PM_GRID_WITH_STEP, false);
	}

	private void copyPmGridMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		copyToClipboard(ClipboardMode.PM_GRID, false);
	}

	private void copyFilledMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		copyToClipboard(ClipboardMode.VALUES_ONLY, false);
	}

	private void showDeviationsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		sudokuPanel.setShowDeviations(showDeviationsMenuItem.isSelected());
		check();
		fixFocus();
	}

	private void newGameToolButtonActionPerformed(java.awt.event.ActionEvent evt) {

		int actLevel = Options.getInstance().getActLevel();
		DifficultyLevel actDiffLevel = Options.getInstance().getDifficultyLevel(actLevel);
		if (Options.getInstance().getGameMode() == GameMode.LEARNING) {
			// in LEARNING ANY puzzle is accepted, that has at least one Training Step in it
			actDiffLevel = Options.getInstance().getDifficultyLevel(DifficultyType.EXTREME.ordinal());
		}
		
		String preGenSudoku = BackgroundGeneratorThread.getInstance().getSudoku(actDiffLevel, Options.getInstance().getGameMode());
		Sudoku2 tmpSudoku = null;
		
		if (preGenSudoku == null) {
			// no pre-genrated puzzle available -> do it in GUI
			GenerateSudokuProgressDialog dlg = new GenerateSudokuProgressDialog(this, true, actDiffLevel, Options.getInstance().getGameMode());
			dlg.setVisible(true);
			tmpSudoku = dlg.getSudoku();
		} else {
			tmpSudoku = new Sudoku2();
			tmpSudoku.setSudoku(preGenSudoku, true);
			Sudoku2 solvedSudoku = tmpSudoku.clone();
			SudokuSolver solver = SudokuSolverFactory.getDefaultSolverInstance();
			solver.solve(
				actDiffLevel, 
				solvedSudoku, 
				true, 
				null, 
				false, 
				Options.getInstance().solverSteps, 
				Options.getInstance().getGameMode()
			);
			tmpSudoku.setLevel(solvedSudoku.getLevel());
			tmpSudoku.setScore(solvedSudoku.getScore());
		}
		
		if (tmpSudoku != null) {
			archiveCurrentPuzzleInHistory();
			sudokuPanel.setSudoku(tmpSudoku, true);
			allStepsPanel.setSudoku(sudokuPanel.getSudoku());
			initializeResultPanels();
			addSudokuToHistory(tmpSudoku);
			completionTransition.begin(true, false);
			sudokuPanel.clearColoring();
			sudokuPanel.setShowHintCellValue(0);
			sudokuPanel.setShowInvalidOrPossibleCells(false);
			
			if (Options.getInstance().getGameMode() == GameMode.LEARNING) {
				// solve the sudoku up until the first trainingStep
				Sudoku2 trainingSudoku = sudokuPanel.getSudoku();
				List<SolutionStep> steps = sudokuPanel.getSolver().getSteps();
				for (SolutionStep step : steps) {
					if (step.getType().getStepConfig().isEnabledTraining()) {
						break;
					} else {
						sudokuPanel.getSolver().doStep(trainingSudoku, step);
					}
				}
			}
			
			clearSavePoints();
			sudokuFileName = null;
			setTitleWithFile();
			if (replayController != null) replayController.startNewAttempt();
			check();
		}
		
		cellZoomPanel.setDefaultMouse(true);
		sudokuPanel.clearColoring();
		sudokuPanel.setActiveColor(null);
		setPlay(true);
		fixFocus();
	}

	private void levelComboBoxActionPerformed(java.awt.event.ActionEvent evt) {
		Options.getInstance().setActLevel(Options.getInstance().getDifficultyLevels()[levelComboBox.getSelectedIndex() + 1].getOrdinal());
		BackgroundGeneratorThread.getInstance().setNewLevel(Options.getInstance().getActLevel());
		check();
		fixFocus();
	}

	private void levelExtremeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setLevelFromMenu();
	}

	private void levelDiabolicalMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setLevelFromMenu();
	}

	private void levelHardMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setLevelFromMenu();
	}

	private void levelMediumMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setLevelFromMenu();
	}

	private void levelEasyMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setLevelFromMenu();
	}

	private void f1ToggleButtonActionPerformed1(java.awt.event.ActionEvent evt) {
		f1ToggleButtonActionPerformed(evt);
	}

	private void f1ToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {
		setToggleButton((JToggleButton) evt.getSource(), SudokuUtil.isMenuShortcutDown(evt.getModifiers()));
	}

	private void redGreenToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {
		sudokuPanel.setInvalidCells(!sudokuPanel.isInvalidCells());
		sudokuPanel.repaint();
		check();
		fixFocus();
	}

	private void mediumHintMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		getHint(1);
	}

	private void vageHintMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		getHint(0);
	}

	private void alleHiddenSinglesSetzenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		
		hinweisAbbrechenButtonActionPerformed(null);
		sudokuPanel.setAllSingles();
		fixFocus();
	}

	private void hinweisAbbrechenButtonActionPerformed(java.awt.event.ActionEvent evt) {
		if (sudokuPanel.cancelReasoningFromUi()) {
			fixFocus();
			return;
		}
		abortStep();
	}

	private void hinweisAusfuehrenButtonActionPerformed(java.awt.event.ActionEvent evt) {
		if (sudokuPanel.confirmReasoningProposal()) {
			fixFocus();
			return;
		}

		sudokuPanel.doStep();
		resetSelectedHintTechnique();
		sudokuPanel.checkProgress();
		setHintText("");
		hinweisAbbrechenButton.setEnabled(false);
		hinweisAusfuehrenButton.setEnabled(false);
		
		if (executeStepToggleButton != null) {
			executeStepToggleButton.setEnabled(false);
		}
		
		if (abortStepToggleButton != null) {
			abortStepToggleButton.setEnabled(false);
		}
		
		fixFocus();
	}

	private void loesungsSchrittMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		getHint(2);
	}

	private void neuerHinweisButtonActionPerformed(java.awt.event.ActionEvent evt) {
		loesungsSchrittMenuItemActionPerformed(evt);
	}

	private void neuMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		newGameToolButtonActionPerformed(null);
	}

	private void copyCluesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		copyToClipboard(ClipboardMode.CLUES_ONLY, false);
	}

	private void showWrongValuesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		sudokuPanel.setShowWrongValues(showWrongValuesMenuItem.isSelected());
		check();
		fixFocus();
	}

	private void showCandidatesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {

		Options.getInstance().toggleShowCandidates();

		if (!showCandidatesMenuItem.isSelected()) {
			// just set the flag and be done!
			sudokuPanel.setShowCandidates(showCandidatesMenuItem.isSelected());
		} else {
			// if no user candidates have been set, the internal flag is just toggled.
			// if user candidates have been set, further checks have to be made
			if (sudokuPanel.getSudoku().userCandidatesEmpty()) {
				// just set the flag and be done!
				sudokuPanel.setShowCandidates(showCandidatesMenuItem.isSelected());
			} else {
				// display a dialog, that lets the user choose, what to do
				boolean doYes = true;
				if (!sudokuPanel.getSudoku().checkUserCands()) {
					// necessary candidates are missing!
					int ret = JOptionPane.showConfirmDialog(null,
							java.util.ResourceBundle.getBundle("intl/MainFrame")
									.getString("MainFrame.candidatesMissing"),
							java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"),
							JOptionPane.YES_NO_CANCEL_OPTION);
					if (ret == JOptionPane.CANCEL_OPTION) {
						// change the menu item!
						showCandidatesMenuItem.setSelected(false);
						fixFocus();
						return;
					} else if (ret == JOptionPane.YES_OPTION) {
						doYes = true;
					} else {
						doYes = false;
					}
				}
				
				if (doYes) {
					// retain all changes to the user candidates
					sudokuPanel.getSudoku().switchToAllCandidates();
					sudokuPanel.getSolver().setSudoku(sudokuPanel.getSudoku());
					sudokuPanel.checkProgress();
					sudokuPanel.setShowCandidates(showCandidatesMenuItem.isSelected());
				} else {
					// revert all changes
					sudokuPanel.getSudoku().rebuildAllCandidates();
					sudokuPanel.getSolver().setSudoku(sudokuPanel.getSudoku());
					sudokuPanel.checkProgress();
					sudokuPanel.setShowCandidates(showCandidatesMenuItem.isSelected());
				}
			}
		}

		check();
		fixFocus();
	}

	private void showCandidateHighlightMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		Options.getInstance().setShowCandidateHighlight(this.showCandidateHighlightMenuItem.isSelected());
		repaint();
	}

	private void operationSoundsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		Options.getInstance().setOperationSoundsEnabled(operationSoundsMenuItem.isSelected());
	}

	private void markInvalidLinksMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		Options.getInstance().setMarkInvalidLinks(markInvalidLinksMenuItem.isSelected());
		sudokuPanel.repaint();
	}

	private void redoToolButtonActionPerformed(java.awt.event.ActionEvent evt) {
		redoForCurrentTool();
		allStepsPanel.setSudoku(sudokuPanel.getSudoku());
		allStepsPanel.resetPanel();
		check();
		fixFocus();
	}

	private void redoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		redoForCurrentTool();
		allStepsPanel.setSudoku(sudokuPanel.getSudoku());
		allStepsPanel.resetPanel();
		check();
		fixFocus();
	}

	private void undoToolButtonActionPerformed(java.awt.event.ActionEvent evt) {
		undoForCurrentTool();
		allStepsPanel.setSudoku(sudokuPanel.getSudoku());
		allStepsPanel.resetPanel();
		check();
		fixFocus();
	}

	private void undoMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		undoForCurrentTool();
		allStepsPanel.setSudoku(sudokuPanel.getSudoku());
		allStepsPanel.resetPanel();
		check();
		fixFocus();
	}

	private void undoForCurrentTool() {
		if (sudokuPanel.isAnnotationUndoContext()) {
			sudokuPanel.undoCurrentAnnotation();
		} else {
			sudokuPanel.undo();
		}
	}

	private void redoForCurrentTool() {
		if (sudokuPanel.isAnnotationUndoContext()) {
			sudokuPanel.redoCurrentAnnotation();
		} else {
			sudokuPanel.redo();
		}
	}

	private void pasteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		pasteFromClipboard(hinweisTextArea.isFocusOwner());
	}

	private void pasteFromClipboard(boolean hintTarget) {
		try {
			
			Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
			Transferable clipboardContent = clip.getContents(this);
			
			if ((clipboardContent != null) && (clipboardContent.isDataFlavorSupported(DataFlavor.stringFlavor))) {
				String content = (String) clipboardContent.getTransferData(DataFlavor.stringFlavor);
				pasteText(content, hintTarget);
			}
			
		} catch (Exception ex) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error pasting from clipboard", ex);
		}
		
		if (!hintTarget) {
			check();
			fixFocus();
		}
	}

	void pasteText(String content, boolean hintTarget) {
		if (content == null) {
			return;
		}
		if (hintTarget) {
            if(importChainText(content))return;
            // Malformed chain payloads are atomic no-ops; ordinary explanatory text keeps its established behavior.
            if(content.contains("Chain:") || content.contains("=>") || content.matches("(?s).*\\([1-9]\\)r[1-9]c[1-9].*"))return;
			importExternalHintText(content);
			OperationSoundPlayer.play(OperationSoundPlayer.Sound.PASTE);
		} else {
			if (setPuzzle(content, true)) {
				clearSavePoints();
				OperationSoundPlayer.play(OperationSoundPlayer.Sound.PASTE);
			}
		}
	}

	void importExternalHintText(String text) {
		setHintText(text);
		hinweisTextArea.requestFocusInWindow();
	}

	private void outerSplitPanePropertyChange(java.beans.PropertyChangeEvent evt) {
		
		// if the hintPanel is to small, the horizontal divider is moved up
		if (!outerSplitPaneInitialized && 
			outerSplitPane.getSize().getHeight() != 0 &&
			hintPanel.getSize().getHeight() != 0) {
			// adjust to minimum size of hintPanel to allow for LAF differences
			outerSplitPaneInitialized = true; // beware of recursion!
			int diff = (int) (hintPanel.getMinimumSize().getHeight() - hintPanel.getSize().getHeight());
			if (diff > 0) {
				resetHDivLocLoc = outerSplitPane.getDividerLocation() - diff - 5;
				outerSplitPane.setDividerLocation(resetHDivLocLoc);;
			}
			outerSplitPaneInitialized = false;
		}

		// if the window layout is reset, the horizontal divider is moved back to its
		// default location; since we dont know, how large toolbar and statu line are
		// in each and every laf, this value is too small and has to be
		// adjusted again!
		if (resetHDivLoc && outerSplitPane.getDividerLocation() != resetHDivLocLoc) {
			resetHDivLoc = false;
			if (System.currentTimeMillis() - resetHDivLocTicks < 1000) {
				outerSplitPane.setDividerLocation(resetHDivLocLoc);
				setSize(getWidth() + 1, getHeight());
			} else {
			}
		}
	}

	private void formWindowClosed(java.awt.event.WindowEvent evt) {
		changingFullScreenMode = false;
	}

	@Override
	public void dispose() {
        clearPendingChainPaste();
		if (currentReasoningMenu != null) currentReasoningMenu.dispose();
		if (sudokuPanel != null) sudokuPanel.shutdownReasoning();
		shutdownTechniqueScanning();
		if (annotationKeyDispatcherInstalled) {
			KeyboardFocusManager.getCurrentKeyboardFocusManager()
					.removeKeyEventDispatcher(annotationKeyDispatcher);
			annotationKeyDispatcherInstalled = false;
		}
		super.dispose();
	}

	private void shutdownTechniqueScanning() {
		javax.swing.JPopupMenu popup;
		synchronized (techniqueScanLock) {
			if (techniqueScanTimer != null) techniqueScanTimer.stop();
			if (techniqueScanWorker != null) techniqueScanWorker.interrupt();
			techniqueScanWorker = null;
			techniqueScanWorkerSignature = null;
			popup = techniqueSelectorPopup;
			techniqueSelectorPopup = null;
		}
		if (popup != null) popup.setVisible(false);
		if (sudokuPanel != null) sudokuPanel.clearTechniquePreviewCells();
	}

	private void installAnnotationKeyDispatcher() {
		if (!annotationKeyDispatcherInstalled) {
			KeyboardFocusManager.getCurrentKeyboardFocusManager()
					.addKeyEventDispatcher(annotationKeyDispatcher);
			annotationKeyDispatcherInstalled = true;
		}
	}

	private void formWindowClosing(java.awt.event.WindowEvent evt) {
		if (!changingFullScreenMode) {
			requestQuit(null);
		}
	}

	private void installMacOSApplicationHandlers() {
		if (!SudokuUtil.isMacOS()) {
			return;
		}
		boolean handlersInstalled = MacOSApplication.installHandlers(new Runnable() {
			@Override
			public void run() {
				aboutMenuItemActionPerformed(null);
			}
		}, new Runnable() {
			@Override
			public void run() {
				configMenuItemActionPerformed(null);
			}
		}, new MacOSApplication.QuitCallback() {
			@Override
			public void quitRequested(final MacOSApplication.QuitRequest request) {
				if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
					javax.swing.SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							requestQuit(request);
						}
					});
				} else {
					requestQuit(request);
				}
			}
		});

		if (handlersInstalled) {
			// About, Preferences and Quit belong to the native HoDoKu application menu.
			exitMenuItem.setVisible(false);
			editMenu.remove(configMenuItem);
			int lastItem = editMenu.getMenuComponentCount() - 1;
			if (lastItem >= 0 && editMenu.getMenuComponent(lastItem) instanceof JSeparator) {
				editMenu.remove(lastItem);
			}
			aboutMenuItem.setVisible(false);
		}
	}

	private void requestQuit(MacOSApplication.QuitRequest nativeRequest) {
		if (nativeRequest != null && (nativeRequest.isRepeated() || quitFailureDialog != null)) {
			if (quitFailureDialog != null) {
				quitFailureDialog.dispose();
				quitFailureDialog = null;
			}
			nativeRequest.perform();
			return;
		}

		while (true) {
			try {
				saveApplicationState();
				if (relaunchAfterQuit && !MacOSApplication.scheduleRelaunch()) {
					relaunchAfterQuit = false;
					ResourceBundle bundle = ResourceBundle.getBundle("intl/MainFrame");
					JOptionPane.showMessageDialog(this,
							bundle.getString("MainFrame.appearance.restartUnavailable"),
							bundle.getString("MainFrame.appearance.title"),
							JOptionPane.WARNING_MESSAGE);
                    if (replayController != null) replayController.cancelQuit();
					return;
				}
				if (nativeRequest != null) {
					nativeRequest.perform();
				} else {
					System.exit(0);
				}
				return;
			} catch (IOException ex) {
				Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE,
						"Unable to save application state before quitting", ex);
				int choice = showQuitFailureDialog(ex);
				if (choice == 0) {
					continue;
				}
				if (choice == 2) {
					relaunchAfterQuit = false;
					if (nativeRequest != null) {
						nativeRequest.perform();
					} else {
						System.exit(0);
					}
				} else {
					relaunchAfterQuit = false;
					if (replayController != null) replayController.cancelQuit();
                    if (nativeRequest != null) nativeRequest.cancel();
				}
				return;
			}
		}
	}

	private int showQuitFailureDialog(IOException failure) {
		ResourceBundle bundle = ResourceBundle.getBundle("intl/MainFrame");
		Object[] options = { bundle.getString("MainFrame.quitSaveFailure.retry"),
				bundle.getString("MainFrame.quitSaveFailure.cancel"),
				bundle.getString("MainFrame.quitSaveFailure.quitAnyway") };
		JOptionPane pane = new JOptionPane(bundle.getString("MainFrame.quitSaveFailure.message")
				+ "\n" + failure.getMessage(), JOptionPane.ERROR_MESSAGE,
				JOptionPane.DEFAULT_OPTION, null, options, options[0]);
		quitFailureDialog = pane.createDialog(this, bundle.getString("MainFrame.quitSaveFailure.title"));
		quitFailureDialog.setVisible(true);
		quitFailureDialog = null;
		Object selected = pane.getValue();
		if (selected == options[0]) {
			return 0;
		}
		if (selected == options[2]) {
			return 2;
		}
		return 1;
	}

	private void saveApplicationState() throws IOException {
        if (replayController != null) replayController.prepareQuit();
		saveWindowStateInOptions();
		SessionSnapshot snapshot = new SessionSnapshot();
		GuiState state = new GuiState(sudokuPanel, sudokuPanel.getSolver(), solutionPanel);
		state.setIncludeAnnotations(true);
		state.get(true);
		snapshot.setGuiState(state);
		snapshot.setSavePoints(new ArrayList<GuiState>(savePoints));
		snapshot.setActiveRow(sudokuPanel.getActiveRow());
		snapshot.setActiveCol(sudokuPanel.getActiveCol());
		snapshot.setCompleted(isCurrentPuzzleCorrectlySolved());
		snapshot.setCompletedLevel(Options.getInstance().getActLevel());
		snapshot.setCompletedGameMode(Options.getInstance().getGameMode().name());
		snapshot.setHistoryEligible(completionTransition.isEnabled());
		snapshot.setCompletionRecorded(completionTransition.isCompletedRecorded());
		snapshot.setPreviouslySolved(completionTransition.wasPreviouslySolved());
		sessionStore.save(snapshot);
		Options.getInstance().writeOptionsSafely();
        if (replayController != null) replayController.completeQuit();
	}

	private void restoreLastSession() {
		if (!sessionStore.exists()) {
			Options.getInstance().initializeAnnotationPalettePreferences(null, null);
			completionTransition.begin(false, false);
			return;
		}
		try {
			SessionSnapshot snapshot = sessionStore.load();
			Options.getInstance().initializeAnnotationPalettePreferences(
					snapshot.getPrimaryColor(), snapshot.getSecondaryColor());
			int savedLevel = snapshot.getCompletedLevel();
			if (savedLevel <= 0 || savedLevel >= Options.getInstance().getDifficultyLevels().length) {
				savedLevel = Options.ACT_LEVEL;
			}
			Options.getInstance().setActLevel(savedLevel);
			try {
				Options.getInstance().setGameMode(GameMode.valueOf(snapshot.getCompletedGameMode()));
			} catch (RuntimeException ex) {
				Options.getInstance().setGameMode(GameMode.PLAYING);
			}
			if (snapshot.isCompleted()) {
				setMode(Options.getInstance().getGameMode(), false);
				newGameToolButtonActionPerformed(null);
				return;
			}

			sessionRestoreInProgress = true;
			GuiState state = snapshot.getGuiState();
			state.initialize(sudokuPanel, sudokuPanel.getSolver(), solutionPanel);
			setState(state);
			savePoints = snapshot.getSavePoints() == null
					? new ArrayList<GuiState>() : new ArrayList<GuiState>(snapshot.getSavePoints());
			for (GuiState savePoint : savePoints) {
				if (savePoint != null) {
					savePoint.initialize(sudokuPanel, SudokuSolverFactory.getDefaultSolverInstance(), solutionPanel);
				}
			}
			sudokuPanel.clearSelection(snapshot.getActiveRow(), snapshot.getActiveCol());
			// Completed annotations restore with the session, but pointer tools always
			// begin in the safe default mode after launch.
			setColoring(null, false);
			setMode(Options.getInstance().getGameMode(), false);
			completionTransition.restore(snapshot.isHistoryEligible(), snapshot.isCompletionRecorded(),
					snapshot.isPreviouslySolved());
		} catch (IOException ex) {
			Options.getInstance().initializeAnnotationPalettePreferences(null, null);
			Logger.getLogger(MainFrame.class.getName()).log(Level.WARNING,
					"Unable to restore last work session", ex);
			completionTransition.begin(false, false);
		} finally {
			sessionRestoreInProgress = false;
		}
	}

    /** Reconcile an abnormal replay recovery as one complete attempt, without puzzle import. */
    /** Preserve live native state if a branch installation fails before commit. */
    void installReplayBranch(ReplaySession branch) {
        GuiState previous=new GuiState(sudokuPanel,sudokuPanel.getSolver(),solutionPanel);previous.setIncludeAnnotations(true);previous.get(true);
        List<GuiState> previousPoints=new ArrayList<GuiState>(savePoints);CompletionTransition previousCompletion=completionTransition.copy();
        String previousFile=sudokuFileName;int previousType=sudokuFileType;boolean previousInput=isInputMode();
        try{restoreReplayAttempt(branch);}catch(RuntimeException failure){
            sessionRestoreInProgress=true;
            try{setState(previous);savePoints=previousPoints;completionTransition.restore(previousCompletion);sudokuFileName=previousFile;sudokuFileType=previousType;setTitleWithFile();setPlay(!previousInput);}
            catch(RuntimeException rollback){failure.addSuppressed(rollback);}finally{sessionRestoreInProgress=false;}
            throw failure;
        }
    }

    void restoreReplayAttempt(ReplaySession recovered) {
        sessionRestoreInProgress=true;
        try{
            ReplayBoard board=recovered.last().board;
            int[] values=board.values();boolean[] fixed=board.fixed();StringBuilder clues=new StringBuilder(81);
            for(int cell=0;cell<81;cell++)clues.append(fixed[cell]?values[cell]:0);
            Sudoku2 definition=new Sudoku2();definition.setSudoku(clues.toString());
            definition.setLevel(Options.getInstance().getDifficultyLevels()[DifficultyType.EASY.ordinal()]);definition.setScore(0);
            int solutions=generator.SudokuGeneratorFactory.getDefaultGeneratorInstance().getNumberOfSolutions(definition,1);
            definition.setStatus(solutions);definition.setStatusGivens(solutions);
            sudokuPanel.getSolver().setSudoku(definition.clone());
            if(solutions==1)sudokuPanel.getSolver().solve();
            Sudoku2 solvedDefinition=sudokuPanel.getSolver().getSudoku();
            definition.setLevel(solvedDefinition.getLevel());definition.setScore(solvedDefinition.getScore());
            GuiState state=recoveredReplayState(board,definition);
            setState(state);
            clearSavePoints();
            // Rebuild only durable markers owned by this exact replay. Never reuse a
            // previous clean attempt's similarly named or same-givens savepoint list.
            List<ReplayFrame> recoveredFrames=recovered.frames();
            for(ReplayBookmark marker:recovered.bookmarks()){
                if(marker.frameIndex<0||marker.frameIndex>=recoveredFrames.size())continue;
                ReplayBoard marked=recoveredFrames.get(marker.frameIndex).board;
                if(!board.samePuzzle(marked))continue;
                GuiState point=recoveredReplayState(marked,definition);
                point.setName(marker.name);point.setTimestamp(new java.util.Date(marker.wallTimeMillis));savePoints.add(point);
            }
            sudokuFileName=null;setTitle(VERSION);
            completionTransition.begin(solutions==1,false);
            resetSelectedHintTechnique();
            if(recovered.initialAnnotations().length>0)try{sudokuPanel.restoreReplayAnnotations(recovered.initialAnnotations());}catch(IOException e){throw new IllegalArgumentException("Invalid replay annotations",e);}
        }finally{sessionRestoreInProgress=false;}
    }
    private GuiState recoveredReplayState(ReplayBoard board,Sudoku2 definition){
        Sudoku2 restored=board.toSudoku();restored.setStatus(definition.getStatus());restored.setStatusGivens(definition.getStatusGivens());
        restored.setInitialState(definition.getInitialState());restored.setLevel(definition.getLevel());restored.setScore(definition.getScore());
        if(definition.isSolutionSet())restored.setSolution(definition.getSolution().clone());
        GuiState state=new GuiState(sudokuPanel,sudokuPanel.getSolver(),solutionPanel);
        sudokuPanel.getSolver().getState(state,true);state.setSudoku(restored);state.setIncludeAnnotations(true);
        state.setTitels(java.util.Collections.singletonList("恢复"));
        state.setTabSteps(java.util.Collections.singletonList(state.getSteps()));
        return state;
    }

	/** Projects the active tool's persisted owner state into the one shared palette UI. */
	private void projectAnnotationPaletteForCurrentTool() {
		if (cellZoomPanel == null) {
			return;
		}
		AnnotationTool tool = sudokuPanel == null
				? AnnotationTool.DEFAULT_MOUSE : sudokuPanel.getAnnotationTool();
		cellZoomPanel.selectAnnotationTool(tool);
	}

	void sudokuStateChanged() {
        clearPendingChainPaste();
		sudokuPanel.reasoningBoardChanged();
		sudokuPanel.clearTechniquePreviewCells();
		resetResolvedSelectedHintStep();
		if (!sessionRestoreInProgress && completionTransition.update(isCurrentPuzzleCorrectlySolved())) {
			Options.getInstance().recordPuzzleCompleted(sudokuPanel.getSudoku());
		}
		if (!sessionRestoreInProgress) {
			scheduleTechniquePreScan();
		}
	}

	private void scheduleTechniquePreScan() {
		boolean selectorOpen;
		javax.swing.JPopupMenu visiblePopup;
		synchronized (techniqueScanLock) {
			// The current board generation changed; stale results are never published.
			if (techniqueScanWorker != null) techniqueScanWorker.interrupt();
			cachedTechniqueSignature = null;
			cachedTechniqueScanComplete = false;
			cachedTechniqueScanFailed = false;
			selectorOpen = techniqueSelectorPopup != null && techniqueSelectorPopup.isVisible();
			visiblePopup = selectorOpen ? techniqueSelectorPopup : null;
		}
		// An open selector must stop exposing steps from the previous board
		// immediately.  The replacement row is disabled until the new fast scan
		// publishes its first results.
		if (visiblePopup != null && !(visiblePopup instanceof CurrentReasoningMenu)) {
			Runnable refreshPopup = () -> populateTechniqueSelector(visiblePopup,
					new ArrayList<SolutionType>(),
					ResourceBundle.getBundle("intl/MainFrame"));
			if (SwingUtilities.isEventDispatchThread()) refreshPopup.run();
			else SwingUtilities.invokeLater(refreshPopup);
		}
		techniqueChangesSinceScan++;
		if (techniqueScanTimer == null) {
			techniqueScanTimer = new Timer(5000, new ActionListener() {
				@Override public void actionPerformed(ActionEvent event) { requestTechniqueScan(); }
			});
			techniqueScanTimer.setRepeats(false);
		}
		techniqueScanTimer.restart();
		if (techniqueChangesSinceScan >= 10 || selectorOpen) requestTechniqueScan();
	}

	private void requestTechniqueScan() {
		final Sudoku2 snapshot = sudokuPanel.getSudoku().clone();
		final String signature = getTechniqueScanSignature(snapshot);
		Thread worker;
		synchronized (techniqueScanLock) {
			if (signature.equals(cachedTechniqueSignature) && cachedTechniqueScanComplete) return;
			if (techniqueScanWorker != null && techniqueScanWorker.isAlive()
					&& signature.equals(techniqueScanWorkerSignature)) return;
			if (techniqueScanWorker != null) techniqueScanWorker.interrupt();
			worker = new Thread(() -> runTechniqueScan(snapshot, signature),
					"hodoku-technique-all-steps");
			worker.setDaemon(true);
			techniqueScanWorker = worker;
			techniqueScanWorkerSignature = signature;
		}
		worker.start();
	}

	void resetSelectedHintTechnique() {
		selectedHintTechnique = null;
		selectedHintStep = null;
		selectedHintInstance = 0;
		selectedHintInstanceCount = 0;
		updateTechniqueSelectorButton();
	}

	private void resetResolvedSelectedHintStep() {
		if (selectedHintStep == null
				|| SudokuPanel.isNativeConclusionExecutable(
						selectedHintStep, sudokuPanel.getSudoku())) {
			return;
		}
		boolean selectedStepIsDisplayed = sameTechniqueStep(
				sudokuPanel.getStep(), selectedHintStep);
		resetSelectedHintTechnique();
		if (selectedStepIsDisplayed) abortStep();
	}

	private boolean isCurrentPuzzleCorrectlySolved() {
		return sudokuPanel.getSudoku().isSolved() && sudokuPanel.getSudoku().checkSudoku();
	}

	private void beginTrackedPuzzle(boolean addToHistory) {
		boolean valid = sudokuPanel.getSudoku().getStatus() == SudokuStatus.VALID;
		boolean eligible = valid && sudokuPanel.getSudoku().getLevel() != null;
		if (addToHistory && valid) {
			Options.getInstance().recordPuzzleStarted(sudokuPanel.getSudoku());
		}
		completionTransition.begin(eligible, isCurrentPuzzleCorrectlySolved());
	}

	private void solveUpToButtonActionPerformed(java.awt.event.ActionEvent evt) {
		
		if (sudokuPanel != null) {
			sudokuPanel.solveUpTo();
		}
		
		check();
		fixFocus();
	}

	private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		new AboutDialog(this, true).setVisible(true);
		check();
		fixFocus();
	}

	private void keyMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		new KeyboardLayoutFrame().setVisible(true);
		check();
		fixFocus();
	}

	private void resetViewMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setWindowLayout(true);
		check();
		fixFocus();
		repaint();
	}

	private boolean appearanceModeSelected(AppearanceMode mode) {
		AppearanceMode previous = AppearanceMode.fromName(Options.getInstance().getAppearanceMode());
		if (mode == previous) return true;
		Options.getInstance().setAppearanceMode(mode.name());
		ResourceBundle bundle = ResourceBundle.getBundle("intl/MainFrame");
		Object[] choices = {
			bundle.getString("MainFrame.appearance.cancel"),
			bundle.getString("MainFrame.appearance.later"),
			bundle.getString("MainFrame.appearance.restartNow")
		};
		int choice = JOptionPane.showOptionDialog(this,
				bundle.getString("MainFrame.appearance.restart"),
				bundle.getString("MainFrame.appearance.title"), JOptionPane.DEFAULT_OPTION,
				JOptionPane.INFORMATION_MESSAGE, null, choices, choices[2]);
		if (choice == 2) {
			relaunchAfterQuit = true;
			requestQuit(null);
			return true;
		}
		if (choice == 1) return true;
		Options.getInstance().setAppearanceMode(previous.name());
		return false;
	}

	private void hintPanelPropertyChange(java.beans.PropertyChangeEvent evt) {
		outerSplitPanePropertyChange(null);
	}

	private void newEmptyGameMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		
		if (sudokuPanel.getSolvedCellsAnz() != 0) {
			
			int antwort = JOptionPane.showConfirmDialog(
				this,
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.delete_sudoku"),
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.new_input"),
				JOptionPane.YES_NO_OPTION
			);
			
			// do nothing
			if (antwort != JOptionPane.YES_OPTION) {
				return;
			}
		}
		if (replayController != null) replayController.beginEditing(true);
		archiveCurrentPuzzleInHistory();
		sudokuPanel.setSudoku((String) null);
		sudokuPanel.checkProgress();
		allStepsPanel.setSudoku(sudokuPanel.getSudoku());
		resetResultPanels();
		sudokuPanel.setNoClues();
		cellZoomPanel.setDefaultMouse(true);
		sudokuPanel.clearColoring();
		sudokuPanel.setActiveColor(null);
		hinweisAbbrechenButtonActionPerformed(null);
		setPlay(false);
		completionTransition.begin(false, false);
	}

	private void spielEditierenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		if (replayController != null) replayController.beginEditing(false);
		resetResultPanels();
		sudokuPanel.setNoClues();
		sudokuPanel.checkProgress();
		sudokuPanel.resetShowHintCellValues();
		hinweisAbbrechenButtonActionPerformed(null);
		setPlay(false);
		completionTransition.begin(false, false);
	}

	private void spielSpielenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		if (sudokuPanel.getSolvedCellsAnz() > 0) {
			sudokuPanel.setSudoku(sudokuPanel.getSudokuString(ClipboardMode.VALUES_ONLY));
			if (sudokuPanel.getSudoku().getStatus() != SudokuStatus.VALID) {
				setPlay(false);
				return;
			}
			sudokuPanel.checkProgress();
			allStepsPanel.setSudoku(sudokuPanel.getSudoku());
			initializeResultPanels();
			beginTrackedPuzzle(true);
		}
		
		setPlay(true);
		if (replayController != null) replayController.finishEditing();
	}

	private void resetSpielMenuItemActionPerformed(java.awt.event.ActionEvent evt) {

		if (JOptionPane.showConfirmDialog(
				this,
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.reset_game"),
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.reset"),
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			
			sudokuPanel.setSudoku(sudokuPanel.getSudokuString(ClipboardMode.CLUES_ONLY));
			sudokuPanel.checkProgress();
			allStepsPanel.setSudoku(sudokuPanel.getSudoku());
			allStepsPanel.resetPanel();
			
			repaint();
			setPlay(true);
			check();
			fixFocus();
			if (replayController != null) replayController.startNewAttempt();
		}
	}

	private void statusPanelColor1MouseClicked(java.awt.event.MouseEvent evt) {
		coloringPanelClicked(Options.getInstance().getColoringColors()[0]);
	}

	private void statusPanelColor2MouseClicked(java.awt.event.MouseEvent evt) {
		coloringPanelClicked(Options.getInstance().getColoringColors()[2]);
	}

	private void statusPanelColor3MouseClicked(java.awt.event.MouseEvent evt) {
		coloringPanelClicked(Options.getInstance().getColoringColors()[4]);
	}

	private void statusPanelColor4MouseClicked(java.awt.event.MouseEvent evt) {
		coloringPanelClicked(Options.getInstance().getColoringColors()[6]);
	}

	private void statusPanelColor5MouseClicked(java.awt.event.MouseEvent evt) {
		coloringPanelClicked(Options.getInstance().getColoringColors()[8]);
	}

	private void statusPanelColorClearMouseClicked(java.awt.event.MouseEvent evt) {
		coloringPanelClicked(null);
	}

	private void statusPanelColorResetMouseClicked(java.awt.event.MouseEvent evt) {
		sudokuPanel.clearColoring();
		coloringPanelClicked(null);
		sudokuPanel.repaint();
	}

	private void colorCellsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		//sudokuPanel.setColorCells(true);
		cellZoomPanel.setColorCells(true);
		sudokuPanel.updateCellZoomPanel();
		check();
		fixFocus();
	}

	private void colorCandidatesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		//sudokuPanel.setColorCells(false);
		cellZoomPanel.setColorCandidates(true);
		sudokuPanel.updateCellZoomPanel();
		check();
		fixFocus();
	}

	public void toggleSingleClickMode() {
		Options.getInstance().setSingleClickMode(!Options.getInstance().isSingleClickMode());
		if (Options.getInstance().isSingleClickMode()) {
			sudokuPanel.clearLastCandidateMouseOn();
		}
	}

	public void setSingleClickMode(boolean enabled) {

		if (enabled && !Options.getInstance().isSingleClickMode()) {
			sudokuPanel.clearLastCandidateMouseOn();
		}

		Options.getInstance().setSingleClickMode(enabled);
	}

	private void cellZoomMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setSplitPane(cellZoomPanel);
		repaint();
	}

	private void userManualMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		MyBrowserLauncher.getInstance().launchUserManual();
	}

	private void solvingGuideMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		MyBrowserLauncher.getInstance().launchSolvingGuide();
	}

	private void projectHomePageMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		MyBrowserLauncher.getInstance().launchHomePage();
	}

	private void loadConfigMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		loadFromFile(false);
	}

	private void saveConfigAsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		saveToFile(false);
	}

	private void historyMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		
		GuiState state = new GuiState(sudokuPanel, sudokuPanel.getSolver(), solutionPanel);
		state.setIncludeAnnotations(true);
		state.get(true);
		CompletionTransition savedCompletionTransition = completionTransition.copy();
		if (replayController != null) { replayController.capture("manual", ReplayText.text("manual")); replayController.suspend(); }
        boolean confirmed=false;
        try {
		HistoryDialog dlg = new HistoryDialog(this, true);
		dlg.setVisible(true);
		String puzzle = dlg.getSelectedPuzzle();
		PuzzleHistoryEntry selectedEntry = dlg.getSelectedEntry();
		
		if (puzzle != null) {
            confirmed=true;
			if (dlg.isDoubleClicked()) {
				// everything is already initialized, so don't do anything
			} else {
				// act like paste
				setPuzzleFromHistory(puzzle);
			}
			completionTransition.begin(true, selectedEntry != null && selectedEntry.isCompleted());
			clearSavePoints();
		} else {
			// restore everything
			setState(state);
			completionTransition.restore(savedCompletionTransition);
		}
		
        } finally { if (replayController != null) replayController.resume(); }
        if (confirmed && replayController != null) replayController.startNewAttempt();
		state = null;
	}

	private void createSavePointMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		
		String defaultName = ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.createsp.default") + 
				" "	+ (savePoints.size() + 1);
		
		String name = (String) JOptionPane.showInputDialog(this,
				ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.createsp.message"),
				ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.createsp.title"),
				JOptionPane.QUESTION_MESSAGE, null, null, defaultName);
		
		if (name != null) {
			GuiState state = new GuiState(sudokuPanel, sudokuPanel.getSolver(), solutionPanel);
			state.get(true);
			state.setName(name);
			state.setTimestamp(new Date());
			savePoints.add(state);
			if (replayController != null) {
				replayController.createSavePointMarker(name);
				replayController.showRetentionFailure(ReplayText.text("savePointSaved"));
			}
		}
	}

	private void restoreSavePointMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        GuiState state = new GuiState(sudokuPanel, sudokuPanel.getSolver(), solutionPanel);
        state.setIncludeAnnotations(true);
        state.get(true);
        if (replayController != null) { replayController.capture("manual", ReplayText.text("manual")); replayController.suspend(); }
        boolean confirmed=false;
        try {
            RestoreSavePointDialog dlg = new RestoreSavePointDialog(this, true);
            dlg.setVisible(true);
            confirmed=dlg.isOkPressed();
            if (!confirmed) setState(state);
        } finally {
            if (replayController != null) replayController.resume();
        }
        if (confirmed && replayController != null) replayController.savePointRestored();
    }

	private void playingMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setMode(GameMode.PLAYING, true);
		check();
	}

	private void learningMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setMode(GameMode.LEARNING, true);
		check();
	}

	private void practisingMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		setMode(GameMode.PRACTISING, true);
		check();
	}

	private void backdoorSearchMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		new BackdoorSearchDialog(this, true, sudokuPanel).setVisible(true);
	}
	
	private void resetCandidatesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {

		java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("intl/MainFrame");
		String msg = bundle.getString("MainFrame.resetCandidatesMenuItem.dialog");
		
		int input = JOptionPane.showConfirmDialog(null, msg);
		if (input != 0) {
			return;
		}
		
		sudokuPanel.getSudoku().resetCandidates();
		repaint();
	}

	private void setGivensMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		SetGivensDialog dlg = new SetGivensDialog(this, true);
		dlg.setVisible(true);
		if (dlg.isOkPressed()) {
			String givens = dlg.getGivens();
			sudokuPanel.setGivens(givens);
		}
	}

	private void fullScreenMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		
		if (fullScreenMenuItem.isSelected()) {
			changingFullScreenMode = true;
			saveWindowStateInOptions();
			dispose();
			installAnnotationKeyDispatcher();
			setUndecorated(true);
			setExtendedState(MAXIMIZED_BOTH);
			hintPanel.setVisible(false);
			jToolBar1.setVisible(true);
			showToolBarMenuItem.setEnabled(false);
			showHintPanelMenuItem.setEnabled(false);
			setVisible(true);
		} else {
			changingFullScreenMode = true;
			dispose();
			installAnnotationKeyDispatcher();
			setUndecorated(false);
			setExtendedState(NORMAL);
			showToolBarMenuItem.setEnabled(true);
			showHintPanelMenuItem.setEnabled(true);
			setWindowLayout(false);
			setVisible(true);
		}
		
		check();
		fixFocus();
	}

	private void showHintPanelMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		
		Options.getInstance().setShowHintPanel(showHintPanelMenuItem.isSelected());
		hintPanel.setVisible(showHintPanelMenuItem.isSelected());
		
		if (Options.getInstance().isShowHintPanel()) {
			
			int horzDivLoc = Options.getInstance().getInitialHorzDividerLoc();
			if (horzDivLoc > getHeight() - 204) {
				horzDivLoc = getHeight() - 204;
				Options.getInstance().setInitialHorzDividerLoc(horzDivLoc);
			}
			
			outerSplitPane.setDividerLocation(horzDivLoc);
		}
	}

	private void showToolBarMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		Options.getInstance().setShowToolBar(showToolBarMenuItem.isSelected());
		jToolBar1.setVisible(showToolBarMenuItem.isSelected());
	}

	private void fxyToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {
		sudokuPanel.toggleBivalueFilter();
		check();
		sudokuPanel.repaint();
		fixFocus();
	}

	private void fxyzToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {
		sudokuPanel.toggleTrivalueFilter();
		check();
		sudokuPanel.repaint();
		fixFocus();
	}

	private void addAnnotationToolButtonsToToolbar() {
		javax.swing.JSeparator separator = new javax.swing.JSeparator();
		separator.setOrientation(javax.swing.SwingConstants.VERTICAL);
		separator.setMaximumSize(new java.awt.Dimension(5, 32767));
		jToolBar1.add(separator);
		javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
		for (final AnnotationTool tool : AnnotationTool.values()) {
			javax.swing.JToggleButton button = new javax.swing.JToggleButton(
					new AnnotationToolIcon(tool, ANNOTATION_TOOLBAR_ICON_SIZE,
                            () -> sudokuPanel == null || sudokuPanel.isNextUserChainStrong()));
			java.awt.Dimension toolButtonSize = new java.awt.Dimension(38, 38);
			button.setPreferredSize(toolButtonSize);
			button.setMinimumSize(toolButtonSize);
			button.setMaximumSize(toolButtonSize);
			button.setFocusable(false);
			button.setMargin(new java.awt.Insets(2, 2, 2, 2));
			button.setToolTipText(annotationToolName(tool));
			button.addActionListener(new java.awt.event.ActionListener() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    if (sudokuPanel == null) return;
                    if (tool == AnnotationTool.FREE_CHAIN && sudokuPanel.getAnnotationTool() == tool)
                        sudokuPanel.setNextUserChainStrong(!sudokuPanel.isNextUserChainStrong());
                    else sudokuPanel.setAnnotationTool(tool);
				}
			});
			annotationToolButtons[tool.ordinal()] = button;
            if(tool==AnnotationTool.CELL_COLORING){button.setVisible(false);continue;}
			group.add(button);
			jToolBar1.add(button);
		}
		jToolBar1.add(cellZoomPanel.getToolbarPalette());
        jToolBar1.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentShown(java.awt.event.ComponentEvent e) { cellZoomPanel.setToolbarPaletteVisible(true); }
            @Override public void componentHidden(java.awt.event.ComponentEvent e) { cellZoomPanel.setToolbarPaletteVisible(false); }
        });
        cellZoomPanel.setToolbarPaletteVisible(true);
		annotationToolButtons[AnnotationTool.DEFAULT_MOUSE.ordinal()].setSelected(true);
        chainRelationUiChanged();
	}

	private String annotationToolName(AnnotationTool tool) {
		ResourceBundle annotationBundle = ResourceBundle.getBundle("intl/MainFrame");
		switch (tool) {
		case CANDIDATE_COLORING: return annotationBundle.getString("MainFrame.annotationTool.candidate");
		case CELL_COLORING: return annotationBundle.getString("MainFrame.annotationTool.cell");
		case DOODLE: return annotationBundle.getString("MainFrame.annotationTool.doodle");
		case FREE_CHAIN: return annotationBundle.getString("MainFrame.annotationTool.chain");
		case BOX_SELECTION: return annotationBundle.getString("MainFrame.annotationTool.box");
		default: return annotationBundle.getString("MainFrame.annotationTool.mouse");
		}
	}

	void annotationToolChanged(AnnotationTool tool) {
		if (tool != null && annotationToolButtons[tool.ordinal()] != null
				&& !annotationToolButtons[tool.ordinal()].isSelected()) {
			annotationToolButtons[tool.ordinal()].setSelected(true);
		}
	}

    void chainRelationUiChanged() {
        javax.swing.JToggleButton button = annotationToolButtons[AnnotationTool.FREE_CHAIN.ordinal()];
        if (button == null || sudokuPanel == null) return;
        String text = ResourceBundle.getBundle("intl/MainFrame").getString(sudokuPanel.isNextUserChainStrong()
                ? "MainFrame.chainToolbar.strong" : "MainFrame.chainToolbar.weak");
        button.setToolTipText(text); button.getAccessibleContext().setAccessibleName(text); button.repaint();
    }

	/** Refreshes only controls whose meaning changes with an annotation tool. */
	void annotationToolUiChanged(AnnotationTool tool) {
        updateCellSelectionStatus();
		annotationToolChanged(tool);
		refreshAnnotationUndoControls();
		refreshAnnotationColorModeControls();
		fixFocus();
	}

	private void refreshAnnotationColorModeControls() {
		if (cellZoomPanel.isColoringCells()) {
			colorCellsMenuItem.setSelected(true);
			statusLabelCellCandidate.setText(ResourceBundle.getBundle("intl/MainFrame")
					.getString("MainFrame.statusLabelCellCandidate.text.cell"));
		} else if (cellZoomPanel.isColoringCandidates()) {
			colorCandidatesMenuItem.setSelected(true);
			statusLabelCellCandidate.setText(ResourceBundle.getBundle("intl/MainFrame")
					.getString("MainFrame.statusLabelCellCandidate.text.candidate"));
		} else {
			colorButtonGroup.clearSelection();
			statusLabelCellCandidate.setText(ResourceBundle.getBundle("intl/MainFrame")
					.getString("MainFrame.statusLabelCellCandidate.text.default"));
		}
	}

	/** Updates annotation undo affordances without recalculating unrelated game UI. */
	void refreshAnnotationUndoControls() {
		if (sudokuPanel == null) return;
		boolean annotationUndo = sudokuPanel.isAnnotationUndoContext();
		boolean canUndo = annotationUndo ? sudokuPanel.currentAnnotationUndoPossible()
				: sudokuPanel.undoPossible();
		boolean canRedo = annotationUndo ? sudokuPanel.currentAnnotationRedoPossible()
				: sudokuPanel.redoPossible();
		undoMenuItem.setEnabled(canUndo);
		undoToolButton.setEnabled(canUndo);
		redoMenuItem.setEnabled(canRedo);
		redoToolButton.setEnabled(canRedo);
	}

	private void showHintButtonsCheckBoxMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		Options.getInstance().setShowHintButtonsInToolbar(showHintButtonsCheckBoxMenuItem.isSelected());
		setShowHintButtonsInToolbar();
	}

	private void extendedPrintMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		new ExtendedPrintDialog(this, true).setVisible(true);
	}

	private void copySSMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		copyToClipboard(null, true);
	}

	private void savePuzzleMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		
		if (sudokuFileName != null) {
			try {
				saveToFile(true, sudokuFileName, sudokuFileType);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(
					this, 
					ex.toString(),
					java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"),
					JOptionPane.ERROR_MESSAGE
				);
				sudokuFileName = null;
			}
		}
	}

	private void showColorKuMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		Options.getInstance().setShowColorKuAct(showColorKuMenuItem.isSelected());
		sudokuPanel.setShowColorKu();
		check();
		fixFocus();
	}

	private void askQuestionMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		MyBrowserLauncher.getInstance().launchForum();
	}

	private void reportErrorMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
		MyBrowserLauncher.getInstance().launchTracker();
	}

	/**
	 * Adjusts icons for hint toggle buttons according to the mode (normal/ColorKu)
	 * and according to the colors (necessary for color changes). Icons are created
	 * on the fly as necessary.
	 * 
	 * @param on
	 */
	private void prepareToggleButtonIcons(boolean on) {
		
		if (on) {
			
			for (int i = 0, lim = toggleButtons.length - 1; i < lim; i++) {
				
				if (toggleButtonImagesColorKu[i] == null || 
					!toggleButtonImagesColorKu[i].getColor().
					equals(Options.getInstance().getColorKuColor(i + 1))) {

					toggleButtonImagesColorKu[i] = new ColorKuImage(
						TOGGLE_BUTTON_ICON_SIZE, 
						Options.getInstance().getColorKuColor(i + 1)
					);
					
					toggleButtonIconsColorKu[i] = new ImageIcon(toggleButtonImagesColorKu[i]);
				}
				
				toggleButtonIcons[i] = toggleButtonIconsColorKu[i];
				emptyToggleButtonIcons[i] = emptyToggleButtonIconOrgColorKu;
			}

		} else {
			for (int i = 0, lim = toggleButtons.length - 1; i < lim; i++) {
				if (flatToggleButtonIcons[i] == null) {
					flatToggleButtonIcons[i] = new CandidateFilterIcon(
							i + 1, true, TOGGLE_BUTTON_ICON_SIZE);
					flatEmptyToggleButtonIcons[i] = new CandidateFilterIcon(
							i + 1, false, TOGGLE_BUTTON_ICON_SIZE);
				}
				toggleButtonIcons[i] = flatToggleButtonIcons[i];
				emptyToggleButtonIcons[i] = flatEmptyToggleButtonIcons[i];
			}
		}
	}

	/**
	 * Gets a new hint for the sudoku if possible. Checks are made to ensure, that
	 * hints are only displayed for valid puzzles.
	 * 
	 * @param mode <code>0</code> for "vage hint", <code>1</code> for "concrete
	 *             hint" and <code>2</code> for "show next step".
	 */
	private void getHint(int mode) {
		
		if (sudokuPanel.getSudoku().isSolved()) {
			
			JOptionPane.showMessageDialog(
				this,
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.already_solved")
			);
			
			return;
		}
		
		if (sudokuPanel.getSudoku().getStatus() == SudokuStatus.EMPTY || 
			sudokuPanel.getSudoku().getStatus() == SudokuStatus.INVALID) {
			
			JOptionPane.showMessageDialog(
				this,
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.invalid_puzzle")
			);
			
			return;
		}
		
		if (!sudokuPanel.isShowCandidates()) {
			
			JOptionPane.showMessageDialog(
				this,
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.not_available"),
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.hint"),
				JOptionPane.INFORMATION_MESSAGE
			);
			
			return;
		}
		
		if (sudokuPanel.getSudoku().checkSudoku() == false) {
			
			JOptionPane.showMessageDialog(
				this,
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.invalid_values_or_candidates"),
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.hint"),
				JOptionPane.INFORMATION_MESSAGE
			);
			
			return;
		}
		
		if (sudokuPanel.showSelectedReasoningHint(mode)) return;
		SolutionStep step = selectedHintTechnique == null
				? sudokuPanel.getNextStep(false)
				: (selectedHintStep != null ? sudokuPanel.getNextStep(selectedHintStep)
						: sudokuPanel.getNextStep(selectedHintTechnique));
		if (mode == 0 || mode == 1) {
			
			sudokuPanel.abortStep();
			fixFocus();
			
			if (step != null) {
				
				int strMode = 0;
				String msg = java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.vage_hint");
				
				if (mode == 1) {
					strMode = 1;
					msg = java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.medium_hint");
				}
				
				JOptionPane.showMessageDialog(
					this,
					java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.possible_step") + step.toString(strMode),
					msg,
					JOptionPane.INFORMATION_MESSAGE
				);
				
			} else {
				
				JOptionPane.showMessageDialog(
					this,
					java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.dont_know"),
					java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"),
					JOptionPane.ERROR_MESSAGE
				);
			}
		} else {
			
			if (step != null) {
				setSolutionStep(step, false);
			} else {
				setHintText(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.dont_know"));
			}
		}
		
		fixFocus();
		check();
	}

    void showCurrentReasoning() {showCurrentReasoning(false);}
    void showCurrentReasoning(boolean quick) {
        if(currentReasoningMenu!=null&&currentReasoningMenu.isVisible()) {
            if(currentReasoningMenu.isQuick()==quick)return;
            currentReasoningMenu.dispose();
        }
        Sudoku2 board = sudokuPanel.getSudoku();
        if (board.isSolved() || board.getStatus() == SudokuStatus.EMPTY || board.getStatus() == SudokuStatus.INVALID
                || !sudokuPanel.isShowCandidates() || !board.checkSudoku()) {
            announceReasoningStatus("MainFrame.currentReasoning.invalidBoard"); return;
        }
        if (techniqueSelectorPopup != null) techniqueSelectorPopup.setVisible(false);
        currentReasoningMenu = new CurrentReasoningMenu(this,quick);
        techniqueSelectorPopup = currentReasoningMenu;
        java.awt.Component anchor = selectTechniqueToggleButton != null && selectTechniqueToggleButton.isShowing()
                ? selectTechniqueToggleButton : sudokuPanel;
        currentReasoningMenu.show(anchor, 0, anchor == sudokuPanel ? 0 : anchor.getHeight());
    }

    boolean isTechniqueOptionHeld() { return optionKeyDown; }

    void updateTechniqueMenuOpacity(javax.swing.JPopupMenu popup, boolean held) {
        if (popup == null) return;
        java.awt.Window window = SwingUtilities.getWindowAncestor(popup);
        if (window == null || window == this) return;
        java.awt.GraphicsDevice device = window.getGraphicsConfiguration().getDevice();
        if (device.isWindowTranslucencySupported(java.awt.GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
            window.setOpacity(held ? 0.25f : 1.0f);
        }
    }

    javax.swing.JComponent createTechniqueModeSwitch(final javax.swing.JPopupMenu popup, boolean current) {
        javax.swing.JPanel header = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 6, 2));
        javax.swing.JCheckBox toggle = new javax.swing.JCheckBox(ResourceBundle.getBundle("intl/MainFrame")
                .getString("MainFrame.currentReasoning.toolbar"), current);
        toggle.setName("techniqueModeSwitch");
        toggle.setToolTipText(ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.currentReasoning.modeToggle"));
        toggle.addActionListener(e -> {
            popup.putClientProperty("switchingTechniqueMode", Boolean.TRUE);
            updateTechniqueMenuOpacity(popup, false); popup.setVisible(false);
            SwingUtilities.invokeLater(() -> { if (toggle.isSelected()) showCurrentReasoning(); else showTechniqueSelector(); });
        });
        header.add(toggle); return header;
    }

    javax.swing.JComponent createCurrentReasoningSelector(CurrentReasoningMenu menu,
            Map<SolutionType, List<SolutionStep>> steps) {
        return new TechniqueSelectorPanel(new ArrayList<SolutionType>(steps.keySet()),
                ResourceBundle.getBundle("intl/MainFrame"), menu, menu, steps);
    }

    void currentReasoningMenuClosed(CurrentReasoningMenu menu) {
        sudokuPanel.clearTechniquePreviewCells();
        if (techniqueSelectorPopup == menu) techniqueSelectorPopup = null;
        fixFocus();
    }

    void selectCurrentReasoning(SolutionStep selected,SudokuSet boxes,UserChain chain,boolean verified,int instance,int count) {
        applyTechniqueInstance(selected.getType(),selected,instance,count);
        sudokuPanel.selectReasoningHint(selected,boxes,chain,verified);
        announceReasoningStatus("MainFrame.currentReasoning.selectedHint");
        fixFocus();
    }

    void previewCurrentReasoning(SolutionStep selected, SudokuSet boxes, UserChain chain,
            boolean verified, int instance, int count) {
        if (!verified) applyTechniqueInstance(selected.getType(), selected, instance, count);
        sudokuPanel.previewCurrentReasoning(selected, boxes, chain, verified);
        if (verified) announceReasoningStatus("MainFrame.currentReasoning.verified");
        fixFocus();
    }

    void announceReasoningChoices(int count) {
        setHintText(MessageFormat.format(ResourceBundle.getBundle("intl/MainFrame")
                .getString("MainFrame.currentReasoning.choices"), count)
                + "\n" + (sudokuPanel.getStep() == null ? "" : sudokuPanel.getStep().toString()));
    }

    String chainValidationMessage(UserChainValidator.Result result) {
        String key = result.status == UserChainValidator.Status.ASSUMED ? "assumed" : result.status == UserChainValidator.Status.INCOMPLETE ? "incomplete"
                : result.status == UserChainValidator.Status.INVALID ? "problem." + result.problem.name()
                : result.status == UserChainValidator.Status.NO_CONCLUSION ? "noConclusion" : "verified";
        return MessageFormat.format(ResourceBundle.getBundle("intl/MainFrame")
                .getString("MainFrame.currentReasoning." + key), result.invalidEdge + 1);
    }
    void announceChainValidation(UserChainValidator.Result result) {
        sudokuPanel.showUserChainValidation(result);
        String message = chainValidationMessage(result);
        setHintText(message);
        statusLabelCellCandidate.setText(message);
    }

    void announceDisconnectedChains(List<UserChainNode> anchors) {
        List<String> positions=new ArrayList<>();
        for(UserChainNode node:anchors) {
            int cell=node.cells()[0];positions.add("r"+(cell/9+1)+"c"+(cell%9+1)+"("+node.getCandidate()+")");
        }
        String message=MessageFormat.format(ResourceBundle.getBundle("intl/MainFrame")
                .getString("MainFrame.chainOrigin.disconnected"),anchors.size(),String.join(", ",positions));
        setHintText(message);statusLabelCellCandidate.setText(message);
    }

	private void showTechniqueSelector() {
		final ResourceBundle selectorBundle = java.util.ResourceBundle.getBundle("intl/MainFrame");
		final javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
        popup.setLightWeightPopupEnabled(false);
		installTechniqueSelectorPopupLifecycle(popup);
		final Sudoku2 snapshot = sudokuPanel.getSudoku().clone();
		final String signature = getTechniqueScanSignature(snapshot);
		List<SolutionType> cached = null;
		synchronized (techniqueScanLock) {
			techniqueSelectorPopup = popup;
			if (signature.equals(cachedTechniqueSignature) && cachedTechniqueScanComplete) {
				cached = new ArrayList<SolutionType>(cachedTechniqueTypes);
			}
		}
		if (cached != null) {
			// Build cached contents before the popup becomes visible. Repacking an
			// already-visible Aqua popup can cancel it or leave only the loading row.
			populateTechniqueSelector(popup, cached, selectorBundle);
		} else {
			popup.add(withTechniqueModeSwitch(createTechniquePendingContent(selectorBundle), popup));
		}
		java.awt.Component anchor = selectTechniqueToggleButton != null && selectTechniqueToggleButton.isShowing()
				? selectTechniqueToggleButton : this;
		popup.show(anchor, 0, anchor.getHeight());
		if (cached == null) {
			// Close the small race where a worker finishes after the first cache
			// check but before popup.show(): finishTechniqueScan deliberately does
			// not update a popup which is not visible yet.
			List<SolutionType> justFinished = null;
			synchronized (techniqueScanLock) {
				if (signature.equals(cachedTechniqueSignature) && cachedTechniqueScanComplete) {
					justFinished = new ArrayList<SolutionType>(cachedTechniqueTypes);
				}
			}
			if (justFinished != null) populateTechniqueSelector(popup, justFinished, selectorBundle);
			else requestTechniqueScan();
		}
	}

	private void installTechniqueSelectorPopupLifecycle(final javax.swing.JPopupMenu popup) {
		popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
			private boolean released;

			private void releasePopup() {
				if (released) return;
				released = true;
                updateTechniqueMenuOpacity(popup, false);
                if (!Boolean.TRUE.equals(popup.getClientProperty("switchingTechniqueMode"))) commitTechniqueSelectorSelection(popup);
				sudokuPanel.clearTechniquePreviewCells();
				synchronized (techniqueScanLock) {
					if (techniqueSelectorPopup == popup) techniqueSelectorPopup = null;
				}
			}
			@Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent event) {
                SwingUtilities.invokeLater(() -> updateTechniqueMenuOpacity(popup, optionKeyDown));
            }
			@Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent event) {
				releasePopup();
			}
			@Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent event) {
				releasePopup();
			}
		});
	}

	private void commitTechniqueSelectorSelection(javax.swing.JPopupMenu popup) {
		if (popup.getComponentCount() == 1
				&& popup.getComponent(0) instanceof TechniqueSelectorPanel) {
			((TechniqueSelectorPanel) popup.getComponent(0)).commitSelectionOnClose();
		}
	}

	private void runTechniqueScan(Sudoku2 snapshot, String signature) {
		final Thread worker = Thread.currentThread();
		Map<SolutionType, List<SolutionStep>> result =
				new LinkedHashMap<SolutionType, List<SolutionStep>>();
		Throwable failure = null;
		try {
			result = findAvailableTechniqueSteps(snapshot);
		} catch (Throwable ex) {
			failure = ex;
			if (!worker.isInterrupted()) {
				Logger.getLogger(MainFrame.class.getName()).log(Level.WARNING,
						"Technique selector Find All Steps scan failed", ex);
			}
		}
		final Map<SolutionType, List<SolutionStep>> scanned = result;
		final boolean cancelledBeforeFootprints = worker.isInterrupted();
		final Map<SolutionStep, StepFootprint> footprints = cancelledBeforeFootprints
				? new IdentityHashMap<SolutionStep, StepFootprint>()
				: buildTechniqueFootprints(scanned, snapshot);
		// Re-read after footprint construction: a board change or app shutdown may
		// have interrupted this worker while that secondary work was in progress.
		final boolean cancelled = cancelledBeforeFootprints || worker.isInterrupted();
		final Throwable scanFailure = failure;
		SwingUtilities.invokeLater(() -> finishTechniqueScan(signature, worker, scanned,
				footprints, scanFailure, cancelled));
	}

	private void finishTechniqueScan(String signature, Thread worker,
			Map<SolutionType, List<SolutionStep>> scanned,
			Map<SolutionStep, StepFootprint> footprints, Throwable failure, boolean cancelled) {
		javax.swing.JPopupMenu popup = null;
		List<SolutionType> popupTypes = null;
		synchronized (techniqueScanLock) {
			if (techniqueScanWorker == worker) {
				techniqueScanWorker = null;
				techniqueScanWorkerSignature = null;
			}
			String currentSignature = getTechniqueScanSignature(sudokuPanel.getSudoku());
			if (!cancelled && failure == null && signature.equals(currentSignature)) {
				cachedTechniqueSignature = signature;
				cachedTechniqueSteps = orderTechniqueSteps(scanned);
				cachedTechniqueFootprints = new IdentityHashMap<SolutionStep, StepFootprint>(footprints);
				cachedTechniqueTypes = new ArrayList<SolutionType>(cachedTechniqueSteps.keySet());
				cachedTechniqueScanComplete = true;
				cachedTechniqueScanFailed = false;
				techniqueChangesSinceScan = 0;
				popup = techniqueSelectorPopup;
				popupTypes = new ArrayList<SolutionType>(cachedTechniqueTypes);
			} else if (failure != null && signature.equals(currentSignature)) {
				cachedTechniqueScanFailed = true;
				cachedTechniqueScanComplete = true;
				popup = techniqueSelectorPopup;
				popupTypes = new ArrayList<SolutionType>(cachedTechniqueTypes);
			}
		}
		if (popup != null && !(popup instanceof CurrentReasoningMenu) && popup.isVisible() && popupTypes != null) {
			populateTechniqueSelector(popup, popupTypes,
					java.util.ResourceBundle.getBundle("intl/MainFrame"));
		}
	}

	private Map<SolutionStep, StepFootprint> buildTechniqueFootprints(
			Map<SolutionType, List<SolutionStep>> steps, Sudoku2 snapshot) {
		Map<SolutionStep, StepFootprint> footprints =
				new IdentityHashMap<SolutionStep, StepFootprint>();
		for (List<SolutionStep> instances : steps.values()) {
			for (SolutionStep step : instances) {
				if (Thread.currentThread().isInterrupted()) return footprints;
				footprints.put(step, StepFootprint.from(step, snapshot));
			}
		}
		return footprints;
	}

	private Map<SolutionType, List<SolutionStep>> orderTechniqueSteps(
			Map<SolutionType, List<SolutionStep>> steps) {
		Map<SolutionType, List<SolutionStep>> ordered =
				new LinkedHashMap<SolutionType, List<SolutionStep>>();
		for (StepConfig config : Options.getInstance().solverSteps) {
			if (steps.containsKey(config.getType())) ordered.put(config.getType(), steps.get(config.getType()));
		}
		for (Map.Entry<SolutionType, List<SolutionStep>> entry : steps.entrySet()) {
			if (!ordered.containsKey(entry.getKey())) ordered.put(entry.getKey(), entry.getValue());
		}
		return ordered;
	}

	private static final class StepFootprint {
		private final Set<Integer> cells = new HashSet<Integer>();
		private final Set<Integer> candidates = new HashSet<Integer>();
		private final Set<String> links = new HashSet<String>();

		private static StepFootprint from(SolutionStep step, Sudoku2 snapshot) {
			StepFootprint footprint = new StepFootprint();
			footprint.cells.addAll(step.getIndices());
			footprint.addCandidates(step.getCandidatesToDelete());
			footprint.addCandidates(step.getCannibalistic());
			footprint.addCandidates(step.getFins());
			footprint.addCandidates(step.getEndoFins());
			for (Integer index : step.getColorCandidates().keySet()) {
				footprint.cells.add(index);
				for (Integer value : step.getValues()) {
					if (snapshot == null || snapshot.isCandidate(index, value)) {
						footprint.addCandidate(index, value);
					}
				}
			}
			// The existing hint renderer treats indices and values as independent
			// sets and highlights their Cartesian product, but only for candidates
			// that are still visible in the scanned board.
			for (Integer index : step.getIndices()) {
				for (Integer value : step.getValues()) {
					if (snapshot == null || snapshot.isCandidate(index, value)) {
						footprint.addCandidate(index, value);
					}
				}
			}
			for (AlsInSolutionStep als : step.getAlses()) {
				footprint.cells.addAll(als.getIndices());
				if (snapshot != null) {
					for (Integer index : als.getIndices()) {
						for (Integer value : als.getCandidates()) {
							if (snapshot.isCandidate(index, value)) footprint.addCandidate(index, value);
						}
					}
				}
			}
			for (Chain chain : step.getChains()) {
				Integer previous = null;
				for (int i = chain.getStart(); i <= chain.getEnd(); i++) {
					boolean branchStart = chain.getChain()[i] < 0;
					int candidate = chain.getCandidate(i);
					int first = chain.getCellIndex(i);
					footprint.addCandidate(first, candidate);
					// Only grouped nodes store more cell indices here. Normal nodes
					// leave zeroes, while ALS nodes store an ALS reference, not cells.
					if (chain.getNodeType(i) == Chain.GROUP_NODE) {
						footprint.addCandidate(Chain.getSCellIndex2(chain.getChain()[i]), candidate);
						footprint.addCandidate(Chain.getSCellIndex3(chain.getChain()[i]), candidate);
					}
					int node = first * 10 + candidate;
					if (previous != null && !branchStart) {
						footprint.links.add(linkKey(previous, node, chain.isStrong(i)));
					}
					previous = Integer.valueOf(node);
				}
			}
			return footprint;
		}

		private void addCandidates(List<Candidate> list) {
			for (Candidate candidate : list) addCandidate(candidate.getIndex(), candidate.getValue());
		}

		private void addCandidate(int cell, int candidate) {
			if (cell < 0 || cell >= 81 || candidate < 1 || candidate > 9) return;
			cells.add(Integer.valueOf(cell));
			candidates.add(Integer.valueOf(cell * 10 + candidate));
		}

		private static String linkKey(int first, int second, boolean strong) {
			int low = Math.min(first, second);
			int high = Math.max(first, second);
			return low + ":" + high + ":" + (strong ? "S" : "W");
		}
	}

	private static final class TechniqueMatchContext {
		private final Set<Integer> selectedCells;
		private final Set<Integer> coloredCells;
		private final Set<Integer> coloredCandidates;
		private final Set<Integer> chainCandidates = new HashSet<Integer>();
		private final Set<String> chainLinks = new HashSet<String>();

		private TechniqueMatchContext(SudokuPanel panel) {
			selectedCells = panel.getSelectedCellsForTechniqueMatching();
			coloredCells = panel.getColoredCellsForTechniqueMatching();
			coloredCandidates = panel.getColoredCandidatesForTechniqueMatching();
			for (UserChain chain : panel.getUserChainsForTechniqueMatching()) {
				List<UserChainNode> nodes = chain.getNodes();
				for (UserChainNode node : nodes) {
					chainCandidates.add(Integer.valueOf(node.getCellIndex() * 10 + node.getCandidate()));
				}
				for (int i = 1; i < nodes.size(); i++) {
					boolean strong = i - 1 < chain.getStrongRelations().size()
							&& chain.getStrongRelations().get(i - 1).booleanValue();
					int first = nodes.get(i - 1).getCellIndex() * 10 + nodes.get(i - 1).getCandidate();
					int second = nodes.get(i).getCellIndex() * 10 + nodes.get(i).getCandidate();
					chainLinks.add(StepFootprint.linkKey(first, second, strong));
				}
			}
		}
	}

	private static final class TechniqueInstance {
		private final SolutionStep step;
		private final int originalIndex;
		private final int originalCount;
		private final StepFootprint footprint;
		private double score;
		private int matchLevel;

		private TechniqueInstance(SolutionStep step, int originalIndex, int originalCount,
				StepFootprint footprint, TechniqueMatchContext context) {
			this.step = step;
			this.originalIndex = originalIndex;
			this.originalCount = originalCount;
			this.footprint = footprint == null ? StepFootprint.from(step, null) : footprint;
			score(context);
		}

		private void score(TechniqueMatchContext context) {
			int exactLinks = intersectionSize(footprint.links, context.chainLinks);
			int chainNodes = intersectionSize(footprint.candidates, context.chainCandidates);
			int coloredCandidates = intersectionSize(footprint.candidates, context.coloredCandidates);
			int coloredCells = intersectionSize(footprint.cells, context.coloredCells);
			int selectedCells = intersectionSize(footprint.cells, context.selectedCells);
			int raw = exactLinks * 18 + chainNodes * 12 + coloredCandidates * 8
					+ coloredCells * 3 + selectedCells;
			score = raw == 0 ? 0.0 : raw / Math.sqrt(Math.max(1, footprint.cells.size()));
			matchLevel = getTechniqueMatchLevel(score);
		}

		private static <T> int intersectionSize(Set<T> first, Set<T> second) {
			int count = 0;
			for (T value : first) if (second.contains(value)) count++;
			return count;
		}
	}

	private void populateTechniqueSelector(javax.swing.JPopupMenu popup, List<SolutionType> types,
			ResourceBundle selectorBundle) {
		sudokuPanel.clearTechniquePreviewCells();
		javax.swing.JComponent content;
		if (!types.isEmpty()) {
			content = new TechniqueSelectorPanel(types, selectorBundle, popup);
		} else if (cachedTechniqueScanFailed) {
			javax.swing.JMenuItem failed = new javax.swing.JMenuItem("扫描未完成，请稍后重试");
			failed.setEnabled(false);
			content = failed;
		} else if (cachedTechniqueScanComplete) {
			javax.swing.JMenuItem empty = new javax.swing.JMenuItem(
					selectorBundle.getString("MainFrame.techniqueSelector.empty"));
			empty.setEnabled(false);
			content = empty;
		} else {
			content = createTechniquePendingContent(selectorBundle);
		}
		// Construct the replacement before clearing the visible popup. If list
		// rendering ever fails, the user keeps the previous loading/result row
		// instead of seeing an empty popup.
        if (!(content instanceof TechniqueSelectorPanel)) content = withTechniqueModeSwitch(content, popup);
		popup.removeAll();
		popup.add(content);
		refreshTechniquePopupLayout(popup);
	}

    private javax.swing.JComponent withTechniqueModeSwitch(javax.swing.JComponent content, javax.swing.JPopupMenu popup) {
        javax.swing.JPanel wrapper = new javax.swing.JPanel(new java.awt.BorderLayout());
        wrapper.add(createTechniqueModeSwitch(popup, false), java.awt.BorderLayout.NORTH);
        wrapper.add(content, java.awt.BorderLayout.CENTER); return wrapper;
    }

	void refreshTechniquePopupLayout(javax.swing.JPopupMenu popup) {
		popup.revalidate();
		if (popup.isVisible()) {
			// JPopupMenu.pack() may dispose/recreate Aqua's popup peer while it is
			// visible. JPopupMenu can also keep the old loading-row preferred size
			// during this event cycle, so measure the replacement content directly.
			java.awt.Component content = popup.getComponentCount() == 0
					? null : popup.getComponent(0);
			java.awt.Dimension contentSize = content == null
					? popup.getPreferredSize() : content.getPreferredSize();
			java.awt.Insets insets = popup.getInsets();
			java.awt.Dimension popupSize = new java.awt.Dimension(
					contentSize.width + insets.left + insets.right,
					contentSize.height + insets.top + insets.bottom);
			popup.setPreferredSize(popupSize);
			popup.setPopupSize(popupSize);
			// Aqua may retain the old loading-row peer size after setPopupSize().
			// Updating the live component itself avoids requiring pack(), which can
			// cancel and recreate a visible native popup.
			popup.setSize(popupSize);
            popup.doLayout();
            // Resize Aqua's heavyweight host as well as the Swing popup content.
            java.awt.Window host=SwingUtilities.getWindowAncestor(popup);
            if(host instanceof javax.swing.JWindow) {
                host.setSize(popupSize);
                host.validate();
            }
		}
		popup.repaint();
	}

	private javax.swing.JComponent createTechniquePendingContent(ResourceBundle bundle) {
		javax.swing.JPanel pending = new javax.swing.JPanel(new java.awt.GridBagLayout());
		javax.swing.JLabel label = new javax.swing.JLabel(
				bundle.getString("MainFrame.techniqueSelector.searching"));
		java.awt.Color disabled = javax.swing.UIManager.getColor("Label.disabledForeground");
		if (disabled != null) label.setForeground(disabled);
		pending.add(label);
		pending.setPreferredSize(new java.awt.Dimension(610, getTechniqueSelectorHeightCap()));
		pending.getAccessibleContext().setAccessibleName(label.getText());
		return pending;
	}

	int getTechniqueSelectorHeightCap() {
		java.awt.GraphicsConfiguration graphics = getGraphicsConfiguration();
		java.awt.Rectangle screen = graphics != null ? graphics.getBounds()
				: java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
						.getMaximumWindowBounds();
		return Math.min(17 * 27, Math.max(180, screen.height - 280));
	}

	private final class TechniqueSelectorPanel extends javax.swing.JPanel {
		private final javax.swing.JList<TechniqueChoice> techniqueList;
		private final javax.swing.JPanel instanceNavigator;
		private final javax.swing.JLabel relevanceLabel;
		private final javax.swing.JButton previousInstanceButton;
		private final javax.swing.JButton nextInstanceButton;
		private final javax.swing.JTextField instanceNumberField;
		private final javax.swing.JLabel instanceCountLabel;
		private final javax.swing.JPopupMenu owner;
		private final ResourceBundle bundle;
        private final CurrentReasoningMenu reasoning;
		private TechniqueChoice navigatorChoice;
		private int displayedInstanceIndex = -1;
		private double highestMatchScore;
		private boolean optionPreviewHeld = optionKeyDown;
		private final Set<Integer> previewColorKeysDown = new HashSet<Integer>();

		TechniqueSelectorPanel(List<SolutionType> types, ResourceBundle bundle,
				javax.swing.JPopupMenu owner) {
            this(types, bundle, owner, null, cachedTechniqueSteps);
        }

        TechniqueSelectorPanel(List<SolutionType> types, ResourceBundle bundle,
                javax.swing.JPopupMenu owner, CurrentReasoningMenu reasoning,
                Map<SolutionType, List<SolutionStep>> stepSource) {
            super(new java.awt.BorderLayout(6, 0));
            this.reasoning = reasoning;
			this.owner = owner;
			this.bundle = bundle;
			javax.swing.DefaultListModel<TechniqueChoice> techniqueModel =
					new javax.swing.DefaultListModel<TechniqueChoice>();
            if (reasoning == null) techniqueModel.addElement(new TechniqueChoice(null, null,
                    bundle.getString("MainFrame.techniqueSelector.automatic")));
			TechniqueMatchContext matchContext = new TechniqueMatchContext(sudokuPanel);
			for (SolutionType type : types) {
				List<SolutionStep> instances = stepSource.get(type);
				if (instances != null && !instances.isEmpty()) {
					List<TechniqueInstance> ranked = new ArrayList<TechniqueInstance>();
					for (int i = 0; i < instances.size(); i++) {
						TechniqueInstance instance = new TechniqueInstance(instances.get(i), i,
								instances.size(), reasoning == null ? cachedTechniqueFootprints.get(instances.get(i))
                                        : StepFootprint.from(instances.get(i), sudokuPanel.getSudoku()),
								matchContext);
						ranked.add(instance);
						highestMatchScore = Math.max(highestMatchScore, instance.score);
					}
					Collections.sort(ranked, new Comparator<TechniqueInstance>() {
						@Override public int compare(TechniqueInstance first, TechniqueInstance second) {
                            int score = reasoning == null ? Double.compare(second.score, first.score)
                                    : Integer.compare(reasoning.priority(first.step), reasoning.priority(second.step));
							return score != 0 ? score : first.originalIndex - second.originalIndex;
						}
					});
					techniqueModel.addElement(new TechniqueChoice(type, ranked, type.getStepName()));
				}
			}
			techniqueList = new javax.swing.JList<TechniqueChoice>(techniqueModel);
			techniqueList.setName(reasoning == null ? "techniqueList" : "reasoningTechniques");
            techniqueList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
			techniqueList.setCellRenderer(new TechniqueChoiceRenderer(bundle));
			javax.swing.JScrollPane techniqueScroll = new javax.swing.JScrollPane(techniqueList);
			techniqueScroll.setWheelScrollingEnabled(true);

			instanceNavigator = new javax.swing.JPanel(
					new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 6, 4));
			java.awt.Color separator = javax.swing.UIManager.getColor("Separator.foreground");
			instanceNavigator.setBorder(javax.swing.BorderFactory.createMatteBorder(
					1, 0, 0, 0, separator == null ? java.awt.Color.GRAY : separator));
			relevanceLabel = new javax.swing.JLabel();
			// Reserve the label width so moving between match levels does not make
			// the navigation controls jump horizontally.
			relevanceLabel.setText("\u25c6\u25c6\u25c6");
			relevanceLabel.setPreferredSize(relevanceLabel.getPreferredSize());
			relevanceLabel.setText("");
			previousInstanceButton = new javax.swing.JButton("\u2039");
			nextInstanceButton = new javax.swing.JButton("\u203a");
			previousInstanceButton.setToolTipText(
					bundle.getString("MainFrame.techniqueSelector.previousInstance"));
			nextInstanceButton.setToolTipText(
					bundle.getString("MainFrame.techniqueSelector.nextInstance"));
			previousInstanceButton.getAccessibleContext().setAccessibleName(
					bundle.getString("MainFrame.techniqueSelector.previousInstance"));
			nextInstanceButton.getAccessibleContext().setAccessibleName(
					bundle.getString("MainFrame.techniqueSelector.nextInstance"));
			java.awt.Dimension navigationButtonSize = new java.awt.Dimension(28, 24);
			previousInstanceButton.setPreferredSize(navigationButtonSize);
			nextInstanceButton.setPreferredSize(navigationButtonSize);
			instanceNumberField = new javax.swing.JTextField(3);
            instanceNumberField.setName("reasoningInstance");
			instanceNumberField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
			instanceNumberField.setEditable(true);
			instanceNumberField.setFocusable(true);
			instanceNumberField.setToolTipText(
					bundle.getString("MainFrame.techniqueSelector.originalIndexHelp"));
			instanceNumberField.getAccessibleContext().setAccessibleName(
					bundle.getString("MainFrame.techniqueSelector.originalIndex"));
			instanceCountLabel = new javax.swing.JLabel();
			instanceNavigator.add(relevanceLabel);
			instanceNavigator.add(previousInstanceButton);
			instanceNavigator.add(new javax.swing.JLabel(
					bundle.getString("MainFrame.techniqueSelector.originalIndex")));
			instanceNavigator.add(instanceNumberField);
			instanceNavigator.add(instanceCountLabel);
			instanceNavigator.add(nextInstanceButton);
			javax.swing.JLabel previewLabel = new javax.swing.JLabel(
					bundle.getString("MainFrame.techniqueSelector.previewHint"));
			previewLabel.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));
			instanceNavigator.add(previewLabel);
			instanceNavigator.setVisible(false);
			// This panel is built before it is attached to the visible popup, so its
			// own GraphicsConfiguration is still null. Use the already-visible frame
			// (with a defensive desktop fallback) to size the scroll panes.
			int heightCap = getTechniqueSelectorHeightCap();
			int height = Math.min(Math.max(180, techniqueModel.size() * 27), heightCap);
			techniqueScroll.setPreferredSize(new java.awt.Dimension(610, height));
            add(reasoning == null ? createTechniqueModeSwitch(owner, false) : reasoning.modeHeader(), java.awt.BorderLayout.NORTH);
			add(techniqueScroll, java.awt.BorderLayout.CENTER);
			add(instanceNavigator, java.awt.BorderLayout.SOUTH);

			techniqueList.addListSelectionListener(event -> {
				if (!event.getValueIsAdjusting()) {
					showInstanceNavigator(techniqueList.getSelectedValue());
					if (optionPreviewHeld) previewSelectedTechnique();
				}
			});
			techniqueList.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override public void mouseClicked(java.awt.event.MouseEvent event) {
					activateTechniqueChoice(techniqueList.getSelectedValue());
				}
			});
			java.awt.event.MouseMotionAdapter previewMotion = new java.awt.event.MouseMotionAdapter() {
				@Override public void mouseMoved(java.awt.event.MouseEvent event) {
					if (!event.isAltDown()) {
						sudokuPanel.clearTechniquePreviewCells();
						return;
					}
					int row = techniqueList.locationToIndex(event.getPoint());
					TechniqueChoice choice = row < 0 ? null : techniqueList.getModel().getElementAt(row);
					if (choice != null && choice.instances != null && choice.instances.size() == 1) {
						sudokuPanel.setTechniquePreviewCells(choice.instances.get(0).footprint.cells);
					} else if (choice == navigatorChoice && displayedInstanceIndex >= 0) {
						showInstancePreview(displayedInstanceIndex);
					} else sudokuPanel.clearTechniquePreviewCells();
				}
			};
			techniqueList.addMouseMotionListener(previewMotion);
			previousInstanceButton.addActionListener(event -> moveDisplayedInstance(-1));
			nextInstanceButton.addActionListener(event -> moveDisplayedInstance(1));
			instanceNumberField.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override public void mousePressed(java.awt.event.MouseEvent event) {
					instanceNumberField.requestFocusInWindow();
				}
			});
			java.awt.event.MouseWheelListener instanceWheel = event -> {
				if (event.getWheelRotation() != 0) {
					moveDisplayedInstance(event.getWheelRotation() > 0 ? 1 : -1);
					event.consume();
				}
			};
			instanceNumberField.addMouseWheelListener(instanceWheel);
			techniqueList.addMouseWheelListener(event -> {
				int row = techniqueList.locationToIndex(event.getPoint());
				java.awt.Rectangle bounds = row < 0 ? null : techniqueList.getCellBounds(row, row);
				if (navigatorChoice != null && row == techniqueList.getSelectedIndex()
						&& bounds != null && bounds.contains(event.getPoint())) {
					instanceWheel.mouseWheelMoved(event);
				} else {
					// Installing a listener on JList stops AWT's automatic wheel
					// forwarding, so preserve normal list scrolling elsewhere.
					java.awt.Point point = SwingUtilities.convertPoint(
							techniqueList, event.getPoint(), techniqueScroll);
					techniqueScroll.dispatchEvent(new java.awt.event.MouseWheelEvent(
							techniqueScroll, event.getID(), event.getWhen(), event.getModifiersEx(),
							point.x, point.y, event.getXOnScreen(), event.getYOnScreen(),
							event.getClickCount(), event.isPopupTrigger(), event.getScrollType(),
							event.getScrollAmount(), event.getWheelRotation(), event.getPreciseWheelRotation()));
					event.consume();
				}
			});
			bindListActions();
			int selectedRow = 0;
			for (int i = 1; i < techniqueModel.size(); i++) {
				if (techniqueModel.get(i).type == selectedHintTechnique) selectedRow = i;
			}
			techniqueList.setSelectedIndex(selectedRow);
			techniqueList.ensureIndexIsVisible(selectedRow);
			showInstanceNavigator(techniqueList.getSelectedValue());
			javax.swing.SwingUtilities.invokeLater(() -> techniqueList.requestFocusInWindow());
		}

		private boolean handleSelectorKeyEvent(KeyEvent event) {
			int id = event.getID();
			int keyCode = event.getKeyCode();
            if (event.getComponent() != null && "techniqueModeSwitch".equals(event.getComponent().getName())) return false;
			if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN) {
				if (id == KeyEvent.KEY_PRESSED) {
					int direction = keyCode == KeyEvent.VK_UP ? -1 : 1;
					if (event.getComponent() != null
							&& SwingUtilities.isDescendingFrom(event.getComponent(), instanceNavigator)) {
						moveDisplayedInstance(direction);
					} else {
						int row = Math.max(0, Math.min(techniqueList.getSelectedIndex() + direction,
								techniqueList.getModel().getSize() - 1));
						techniqueList.setSelectedIndex(row);
						techniqueList.ensureIndexIsVisible(row);
					}
				}
				event.consume();
				return true;
			}
			if (keyCode == KeyEvent.VK_ENTER) {
				if (id == KeyEvent.KEY_PRESSED) {
					if (event.getComponent() == instanceNumberField) {
						if (commitOriginalInstanceNumber()) activateDisplayedInstance();
					} else activateTechniqueChoice(techniqueList.getSelectedValue());
				}
				event.consume();
				return true;
			}
			if (id == KeyEvent.KEY_TYPED) {
				if (previewColorKeysDown.isEmpty()) return false;
				event.consume();
				return true;
			}
			if (keyCode == KeyEvent.VK_ALT) {
				if (id == KeyEvent.KEY_PRESSED) {
					optionPreviewHeld = true;
                    updateTechniqueMenuOpacity(owner, true);
					previewSelectedTechnique();
				} else if (id == KeyEvent.KEY_RELEASED) {
					optionPreviewHeld = false;
                    updateTechniqueMenuOpacity(owner, false);
					previewColorKeysDown.clear();
					sudokuPanel.clearTechniquePreviewCells();
				}
				event.consume();
				return true;
			}
			if (keyCode < KeyEvent.VK_A || keyCode > KeyEvent.VK_D) return false;

			int modifiers = event.getModifiersEx();
			boolean optionDown = optionPreviewHeld
					|| (modifiers & KeyEvent.ALT_DOWN_MASK) != 0;
			if (!optionDown && !previewColorKeysDown.contains(Integer.valueOf(keyCode))) {
				return false;
			}
			if (id == KeyEvent.KEY_PRESSED) {
				boolean firstPress = previewColorKeysDown.add(Integer.valueOf(keyCode));
				int keyboardModifiers = KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK
						| KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK
						| KeyEvent.ALT_GRAPH_DOWN_MASK;
				int relevantModifiers = modifiers & keyboardModifiers;
				boolean optionOnly = relevantModifiers == KeyEvent.ALT_DOWN_MASK
						|| (optionPreviewHeld && relevantModifiers == 0);
				if (firstPress && optionOnly) {
					optionPreviewHeld = true;
					if (!sudokuPanel.hasTechniquePreviewCells()) previewSelectedTechnique();
					Color color = Options.getInstance().getColoringColors()[
							(keyCode - KeyEvent.VK_A) * 2];
					sudokuPanel.colorTechniquePreviewCells(color);
				}
			} else if (id == KeyEvent.KEY_RELEASED) {
				previewColorKeysDown.remove(Integer.valueOf(keyCode));
			}
			event.consume();
			return true;
		}

		private void bindListActions() {
			javax.swing.Action activateTechnique = new javax.swing.AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) {
					activateTechniqueChoice(techniqueList.getSelectedValue());
				}
			};
			techniqueList.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "activate");
			techniqueList.getActionMap().put("activate", activateTechnique);
			techniqueList.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_RIGHT, 0), "instances");
			techniqueList.getActionMap().put("instances", new javax.swing.AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) {
					if (instanceNavigator.isVisible()) instanceNumberField.requestFocusInWindow();
				}
			});
			javax.swing.InputMap navigatorInput = instanceNavigator.getInputMap(
					javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
			javax.swing.ActionMap navigatorActions = instanceNavigator.getActionMap();
			navigatorInput.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_LEFT, 0), "techniques");
			navigatorActions.put("techniques", new javax.swing.AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) {
					techniqueList.requestFocusInWindow();
				}
			});
			navigatorInput.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "previousInstance");
			navigatorInput.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "nextInstance");
			navigatorActions.put("previousInstance", new javax.swing.AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) { moveDisplayedInstance(-1); }
			});
			navigatorActions.put("nextInstance", new javax.swing.AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) { moveDisplayedInstance(1); }
			});
			javax.swing.InputMap numberInput = instanceNumberField.getInputMap(
					javax.swing.JComponent.WHEN_FOCUSED);
			javax.swing.ActionMap numberActions = instanceNumberField.getActionMap();
			numberInput.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0),
					"previousRankedInstance");
			numberInput.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0),
					"nextRankedInstance");
			numberActions.put("previousRankedInstance", new javax.swing.AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) {
					moveDisplayedInstance(-1);
				}
			});
			numberActions.put("nextRankedInstance", new javax.swing.AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) {
					moveDisplayedInstance(1);
				}
			});
			numberInput.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0),
					"activateRankedInstance");
			numberActions.put("activateRankedInstance", new javax.swing.AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) {
					if (commitOriginalInstanceNumber()) activateDisplayedInstance();
				}
			});
		}

		private void showInstanceNavigator(TechniqueChoice choice) {
			boolean wasVisible = instanceNavigator.isVisible();
			if (choice != null && choice.instances != null && choice.instances.size() > 1) {
				navigatorChoice = choice;
				int selected = -1;
				if (choice.type == selectedHintTechnique) {
					for (int i = 0; i < choice.instances.size(); i++) {
						if (sameTechniqueStep(choice.instances.get(i).step, selectedHintStep)) selected = i;
					}
				}
				setDisplayedInstanceIndex(selected >= 0 ? selected : 0);
				instanceNavigator.setVisible(true);
			} else {
				navigatorChoice = null;
				displayedInstanceIndex = -1;
				instanceNavigator.setVisible(false);
                if (reasoning != null && choice != null && choice.instances != null && !choice.instances.isEmpty())
                    reasoning.describe(choice.instances.get(0).step);
			}
			revalidate();
			if (wasVisible != instanceNavigator.isVisible()) refreshTechniquePopupLayout(owner);
		}

		private void setDisplayedInstanceIndex(int index) {
			if (navigatorChoice == null || navigatorChoice.instances == null
					|| navigatorChoice.instances.isEmpty()) return;
			displayedInstanceIndex = Math.max(0, Math.min(index, navigatorChoice.instances.size() - 1));
			TechniqueInstance instance = navigatorChoice.instances.get(displayedInstanceIndex);
			instanceNumberField.setText(Integer.toString(instance.originalIndex + 1));
			instanceCountLabel.setText("/ " + instance.originalCount);
			boolean best = instance.score > 0.0
					&& Double.compare(instance.score, highestMatchScore) == 0;
			relevanceLabel.setText(createTechniqueMatchMarker(instance.matchLevel, best));
			relevanceLabel.setToolTipText(createTechniqueMatchDescription(
					instance.matchLevel, best));
            if (reasoning != null) {
                relevanceLabel.setText("");
                relevanceLabel.setIcon(reasoning.badgeIcon(reasoning.relatedMask(instance.step)));
                relevanceLabel.setPreferredSize(new java.awt.Dimension(64, 20));
                relevanceLabel.setToolTipText(reasoning.relatedDescription(reasoning.relatedMask(instance.step)));
            }
			previousInstanceButton.setEnabled(displayedInstanceIndex > 0);
			nextInstanceButton.setEnabled(displayedInstanceIndex + 1 < navigatorChoice.instances.size());
			if (reasoning != null) reasoning.describe(instance.step);
            if (optionPreviewHeld) showInstancePreview(displayedInstanceIndex);
		}

		private void moveDisplayedInstance(int delta) {
			if (navigatorChoice == null || displayedInstanceIndex < 0) return;
			setDisplayedInstanceIndex(displayedInstanceIndex + delta);
		}

		private boolean commitOriginalInstanceNumber() {
			if (navigatorChoice == null) return false;
			try {
				int requested = Integer.parseInt(instanceNumberField.getText().trim()) - 1;
				for (int i = 0; i < navigatorChoice.instances.size(); i++) {
					if (navigatorChoice.instances.get(i).originalIndex == requested) {
						setDisplayedInstanceIndex(i);
						return true;
					}
				}
			} catch (NumberFormatException ex) {
				// Restore the current stable original index below.
			}
			setDisplayedInstanceIndex(displayedInstanceIndex);
			instanceNumberField.selectAll();
			java.awt.Toolkit.getDefaultToolkit().beep();
			return false;
		}

		private void activateTechniqueChoice(TechniqueChoice choice) {
			if (choice == null) return;
			if (choice.type == null) {
				selectTechniqueInstance(null, null, 0, 0);
			} else if (choice.instances.size() == 1) {
				TechniqueInstance instance = choice.instances.get(0);
				selectInstance(instance);
			} else {
				showInstanceNavigator(choice);
				instanceNumberField.requestFocusInWindow();
				instanceNumberField.selectAll();
			}
		}

		private void activateDisplayedInstance() {
			if (navigatorChoice != null && navigatorChoice.type != null && displayedInstanceIndex >= 0) {
				TechniqueInstance instance = navigatorChoice.instances.get(displayedInstanceIndex);
				selectInstance(instance);
			}
		}

        private void selectInstance(TechniqueInstance instance) {
            if (reasoning != null) reasoning.confirm(instance.step, instance.originalIndex, instance.originalCount);
            else selectTechniqueInstance(instance.step.getType(), instance.step, instance.originalIndex, instance.originalCount);
        }

		private void commitSelectionOnClose() {
            if (reasoning != null) return;
			TechniqueChoice choice = techniqueList.getSelectedValue();
			if (choice == null) return;
			if (choice.type == null) {
				applyTechniqueInstance(null, null, 0, 0);
				return;
			}
			if (choice.instances.size() == 1) {
				TechniqueInstance instance = choice.instances.get(0);
				applyTechniqueInstance(choice.type, instance.step,
						instance.originalIndex, instance.originalCount);
				return;
			}
			if (navigatorChoice != choice) showInstanceNavigator(choice);
			commitOriginalInstanceNumber();
			if (displayedInstanceIndex >= 0) {
				TechniqueInstance instance = choice.instances.get(displayedInstanceIndex);
				applyTechniqueInstance(choice.type, instance.step,
						instance.originalIndex, instance.originalCount);
			}
		}

		private void showInstancePreview(int displayedIndex) {
			TechniqueChoice choice = techniqueList.getSelectedValue();
			if (choice == null || choice.instances == null || displayedIndex < 0
					|| displayedIndex >= choice.instances.size()) {
				sudokuPanel.clearTechniquePreviewCells();
				return;
			}
			sudokuPanel.setTechniquePreviewCells(choice.instances.get(displayedIndex).footprint.cells);
		}

		private void previewSelectedTechnique() {
			if (instanceNavigator.isVisible() && navigatorChoice == techniqueList.getSelectedValue()) {
				showInstancePreview(displayedInstanceIndex);
				return;
			}
			TechniqueChoice choice = techniqueList.getSelectedValue();
			if (choice != null && choice.instances != null && choice.instances.size() == 1) {
				sudokuPanel.setTechniquePreviewCells(choice.instances.get(0).footprint.cells);
			} else {
				sudokuPanel.clearTechniquePreviewCells();
			}
		}

		private final class TechniqueChoiceRenderer extends javax.swing.JPanel
				implements javax.swing.ListCellRenderer<TechniqueChoice> {
			private final javax.swing.JLabel checkLabel = new javax.swing.JLabel();
			private final javax.swing.JLabel nameLabel = new javax.swing.JLabel();
			private final javax.swing.JLabel statusLabel = new javax.swing.JLabel();

			TechniqueChoiceRenderer(ResourceBundle bundle) {
				super(new java.awt.BorderLayout(8, 0));
				checkLabel.setPreferredSize(new java.awt.Dimension(16, 24));
				statusLabel.setHorizontalAlignment(javax.swing.SwingConstants.TRAILING);
				add(checkLabel, java.awt.BorderLayout.WEST);
				add(nameLabel, java.awt.BorderLayout.CENTER);
				add(statusLabel, java.awt.BorderLayout.EAST);
				setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 6, 1, 8));
				setOpaque(true);
			}

			@Override public java.awt.Component getListCellRendererComponent(
					javax.swing.JList<? extends TechniqueChoice> list, TechniqueChoice choice,
					int index, boolean selected, boolean focus) {
				boolean current = choice.type == selectedHintTechnique;
				checkLabel.setText(current ? "✓" : "");
				nameLabel.setText(choice.label);
				String status = "";
				String accessibleStatus = "";
				if (choice.instances != null) {
					TechniqueInstance strongest = choice.instances.get(0);
					boolean best = strongest.score > 0.0
							&& Double.compare(strongest.score, highestMatchScore) == 0;
					String marker = createTechniqueMatchMarker(strongest.matchLevel, best);
					String count = Integer.toString(choice.instances.size());
					if (current && selectedHintInstanceCount > 1) {
						count = (selectedHintInstance + 1) + "/" + selectedHintInstanceCount;
					}
					status = (marker.isEmpty() ? "" : marker + "  ") + count
							+ (choice.instances.size() > 1 ? "  ›" : "");
					String matchDescription = createTechniqueMatchDescription(
							strongest.matchLevel, best);
					setToolTipText(matchDescription);
					accessibleStatus = (matchDescription == null ? "" : matchDescription + " ")
							+ count;
				} else {
					setToolTipText(null);
				}
				                if (reasoning != null && choice.instances != null) {
                    int mask = 0;
                    for (TechniqueInstance candidate : choice.instances) mask |= reasoning.relatedMask(candidate.step);
                    statusLabel.setIcon(reasoning.badgeIcon(mask));
                    status = choice.instances.size() + (choice.instances.size() > 1 ? "  ›" : "");
                    accessibleStatus = reasoning.relatedDescription(mask) + " " + choice.instances.size();
                    setToolTipText(reasoning.relatedDescription(mask));
                }
                statusLabel.setText(status);
				java.awt.Color background = selected
						? list.getSelectionBackground() : list.getBackground();
				java.awt.Color foreground = selected
						? list.getSelectionForeground() : list.getForeground();
				setBackground(background);
				checkLabel.setForeground(foreground);
				nameLabel.setForeground(foreground);
				statusLabel.setForeground(foreground);
				java.awt.Font font = list.getFont();
				checkLabel.setFont(font);
				nameLabel.setFont(font);
				statusLabel.setFont(font);
				getAccessibleContext().setAccessibleName(
						choice.label + (accessibleStatus.isEmpty() ? "" : " " + accessibleStatus));
				return this;
			}
		}

		private String createTechniqueMatchDescription(int level, boolean best) {
			if (level == 0) return null;
			String description = MessageFormat.format(
					bundle.getString("MainFrame.techniqueSelector.matchLevel"),
					Integer.valueOf(level));
			return best ? description + ", "
					+ bundle.getString("MainFrame.techniqueSelector.bestMatch") : description;
		}
	}

	private static String createTechniqueMatchMarker(int level, boolean best) {
		if (level <= 0) return "";
		String symbol = best ? "\u25c6" : "\u2022";
		StringBuilder marker = new StringBuilder(level);
		for (int i = 0; i < level; i++) marker.append(symbol);
		return marker.toString();
	}

	private static int getTechniqueMatchLevel(double score) {
		return score >= 8.0 ? 3 : score >= 3.0 ? 2 : score > 0.0 ? 1 : 0;
	}

	private static final class TechniqueChoice {
		private final SolutionType type;
		private final List<TechniqueInstance> instances;
		private final String label;
		TechniqueChoice(SolutionType type, List<TechniqueInstance> instances, String label) {
			this.type = type;
			this.instances = instances;
			this.label = label;
		}
	}

	private static boolean sameTechniqueStep(SolutionStep first, SolutionStep second) {
		return first != null && second != null && first.getType() == second.getType()
				&& first.compareTo(second) == 0 && second.compareTo(first) == 0;
	}

	private Map<SolutionType, List<SolutionStep>> findAvailableTechniqueSteps(Sudoku2 snapshot) {
		Map<SolutionType, List<SolutionStep>> grouped =
				new LinkedHashMap<SolutionType, List<SolutionStep>>();
		for (SolutionStep step : techniqueStepCatalog.findAllRawSteps(snapshot, null)) {
			if (step == null) continue;
			List<SolutionStep> instances = grouped.get(step.getType());
			if (instances == null) {
				instances = new ArrayList<SolutionStep>();
				grouped.put(step.getType(), instances);
			}
			instances.add(step);
		}
		return orderTechniqueSteps(grouped);
	}

	TechniqueStepCatalog getTechniqueStepCatalog() {
		return techniqueStepCatalog;
	}

	private String getTechniqueScanSignature(Sudoku2 snapshot) {
		return TechniqueStepCatalog.createSignature(snapshot);
	}

	private void selectTechniqueInstance(SolutionType type, SolutionStep selectedStep,
			int instanceIndex, int instanceCount) {
		applyTechniqueInstance(type, selectedStep, instanceIndex, instanceCount);
		if (techniqueSelectorPopup != null) {
			techniqueSelectorPopup.setVisible(false);
		}
		fixFocus();
	}

	private void applyTechniqueInstance(SolutionType type, SolutionStep selectedStep,
			int instanceIndex, int instanceCount) {
		sudokuPanel.clearSelectedReasoningHint();
		selectedHintTechnique = type;
		selectedHintStep = selectedStep == null ? null : (SolutionStep) selectedStep.clone();
		selectedHintInstance = instanceIndex;
		selectedHintInstanceCount = instanceCount;
		sudokuPanel.clearTechniquePreviewCells();
		updateTechniqueSelectorButton();
	}

	private void updateTechniqueSelectorButton() {
		if (selectTechniqueToggleButton == null) {
			return;
		}
		ResourceBundle bundle = java.util.ResourceBundle.getBundle("intl/MainFrame");
		String label = selectedHintTechnique == null
				? bundle.getString("MainFrame.techniqueSelector.automatic")
				: selectedHintTechnique.getStepName();
		if (selectedHintTechnique != null && selectedHintInstanceCount > 0) {
			label += "  " + (selectedHintInstance + 1) + "/" + selectedHintInstanceCount;
		}
		selectTechniqueToggleButton.setText(label + "  ▾");
		selectTechniqueToggleButton.setToolTipText(label + " — "
				+ bundle.getString("MainFrame.selectTechniqueToolButton.toolTipText"));
	}

	/**
	 * Sets a new mode ({@link GameMode#LEARNING}, {@link GameMode#PLAYING} or
	 * {@link GameMode#PRACTISING}). If the new mode is "playing", no further action
	 * is necessary. If the new mode is "learning" or "practising", steps have to be
	 * selected.<br>
	 * If a user tries to set "learning" or "practising", but doesnt select any
	 * steps, "playing" is set.<br>
	 * If the configuration dialog is cancelled, the mode is not changed.
	 * 
	 * @param newMode
	 * @param showDialog
	 */
	private void setMode(GameMode newMode, boolean showDialog) {
		
		if (newMode == GameMode.PLAYING) {
			Options.getInstance().setGameMode(newMode);
		} else {
			
			if (showDialog) {
				// show config dialog
				ConfigTrainingDialog dlg = new ConfigTrainingDialog(this, true);
				dlg.setVisible(true);
				if (!dlg.isOkPressed()) {
					return;
				}
			}
			
			String techniques = Options.getInstance().getTrainingStepsString(true);
			if (techniques.isEmpty()) {
				
				JOptionPane.showMessageDialog(
					this,
					ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.notechniques"),
					ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"),
					JOptionPane.ERROR_MESSAGE
				);
				
				Options.getInstance().setGameMode(GameMode.PLAYING);
				
			} else {
				Options.getInstance().setGameMode(newMode);
			}
		}
	}

	/**
	 * Sets a puzzle and initializes all views. Used by
	 * {@link #pasteMenuItemActionPerformed} and
	 * {@link #historyMenuItemActionPerformed(java.awt.event.ActionEvent)}. This
	 * method should only be used if the puzzle is only available as String. If more
	 * state information is saved, use {@link #setState(sudoku.GuiState)} instead.
	 * 
	 * @param puzzle
	 */
	public void setPuzzle(String puzzle) {
		setPuzzle(puzzle, true);
	}

	/** Opens an existing history row without creating another start record. */
	public void setPuzzleFromHistory(String puzzle) {
		setPuzzle(puzzle, false);
	}

    private boolean setPuzzle(String puzzle, boolean addToHistory) {return setPuzzle(puzzle,addToHistory,null);}
	private boolean setPuzzle(String puzzle, boolean addToHistory, Sudoku2 exact) {

		setHintText("");
		try {
			if(exact==null)sudokuPanel.setSudoku(puzzle);
            else sudokuPanel.setSudoku(puzzle,false,false);
		} catch (Exception ex) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error setting sudoku in SudokuPanel", ex);
			return false;
		}

        if(exact!=null){
            Sudoku2 prepared=sudokuPanel.getSudoku();
            exact.setLevel(prepared.getLevel());exact.setScore(prepared.getScore());
            exact.setStatus(prepared.getStatus());exact.setStatusGivens(prepared.getStatusGivens());
            if(prepared.isSolutionSet())exact.setSolution(prepared.getSolution());
            for(int c=0;c<81;c++)exact.setIsFixed(c,prepared.isFixed(c));
            prepared.set(exact);sudokuPanel.getSolver().setSudoku(prepared);
        }
		allStepsPanel.setSudoku(sudokuPanel.getSudoku());
		initializeResultPanels();
		sudokuPanel.clearColoring();
		sudokuPanel.setShowHintCellValue(0);
		sudokuPanel.setShowInvalidOrPossibleCells(false);
		setPlay(true);
		beginTrackedPuzzle(addToHistory);
		check();
		repaint();
		if (replayController != null) replayController.startNewAttempt();
		return true;
	}

	/**
	 * Restores a complete GUI state including puzzle (optionally with coloring and
	 * selected step), solutions and summary. Used by {@link #loadFromFile(boolean)}
	 * and {@link RestoreSavePointDialog}.<br>
	 * 
	 * @param state
	 */
	public void setState(GuiState state) {
		state.set();
		summaryPanel.initialize(SudokuSolverFactory.getDefaultSolverInstance());
		allStepsPanel.setSudoku(sudokuPanel.getSudoku());
		allStepsPanel.resetPanel();
		setSolutionStep(state.getStep(), true);
		setPlay(true);
		check();
		repaint();
	}

	/**
	 * Adds a new sudoku to the creation history. The size of the history buffer is
	 * adjusted accordingly. New sudokus are always inserted at the start of the
	 * list and deleted from the end of the list, effectively turning the list in a
	 * queue (the performance overhead can be ignored here).
	 * 
	 * @param sudoku
	 */
	private void addSudokuToHistory(Sudoku2 sudoku) {
		Options.getInstance().addSudokuToHistory(sudoku);
	}

	private void archiveCurrentPuzzleInHistory() {
		if (sudokuPanel.getSolvedCellsAnz() != 0
				&& sudokuPanel.getSudoku().getStatus() == SudokuStatus.VALID) {
			addSudokuToHistory(sudokuPanel.getSudoku());
		}
	}

	/**
	 * Old GuiStates remain in memory as long as they are not overwritten. Since
	 * that can consume quite a lot of memory, they should be nulled out before
	 * clearing the list.
	 */
	private void clearSavePoints() {
		
		for (int i = 0; i < savePoints.size(); i++) {
			savePoints.set(i, null);
		}
		
		savePoints.clear();
	}

	/**
	 * Should be called only from {@link CellZoomPanel}.
	 * 
	 * @param colorNumber
	 * @param isCell
	 */
	public void setColoring(Color color, boolean isCell) {
		AnnotationTool target = color == null ? AnnotationTool.DEFAULT_MOUSE
				: (isCell ? AnnotationTool.CELL_COLORING : AnnotationTool.CANDIDATE_COLORING);
		if (sudokuPanel.getAnnotationTool() != target && !sudokuPanel.setAnnotationTool(target)) {
			cellZoomPanel.selectAnnotationTool(sudokuPanel.getAnnotationTool());
			return;
		}
		
		if (color == null) {
			cellZoomPanel.setDefaultMouse(true);
		} else if (isCell) {
			cellZoomPanel.setColorCells(true);
		} else {
			cellZoomPanel.setColorCandidates(true);
		}
		
		coloringPanelClicked(color);
		sudokuPanel.synchronizeAnnotationToolFromColoring(isCell, color);
		check();
		fixFocus();
	}

	/** Cycles the three mouse modes without changing the selected coloring color. */
    void cycleColoringMode(boolean reverse) {
        setColoring(cellZoomPanel.isDefaultMouse()?cellZoomPanel.getPrimaryColor():null,false);
    }

	public void coloringPanelClicked(Color color) {
		
		if (color == null) {
			
			Color statusBackground = ApplicationAppearance.isDark()
					? SudokuAppearancePalette.forRendering(false).getSurfaceBackground()
					: Options.getInstance().getDefaultCellColor();
			statusPanelColorResult.setBackground(statusBackground);
			sudokuPanel.setActiveColor(null);
			/*
			if (colorNumber == -2) {
				sudokuPanel.clearColoring();
				repaint();
			}*/
			
		} else {
			
			statusPanelColorResult.setBackground(color);
			sudokuPanel.setActiveColor(color);
		}
	}

	private void syncDisplayModeMenuSelection() {
		if (!splitPanel.hasRight()) {
			return;
		}

		Object selected = tabPane.getSelectedComponent();
		if (selected == summaryPanel) {
			summaryMenuItem.setSelected(true);
		} else if (selected == solutionPanel) {
			solutionMenuItem.setSelected(true);
		} else if (selected == allStepsPanel) {
			allStepsMenuItem.setSelected(true);
		} else if (selected == cellZoomPanel) {
			cellZoomMenuItem.setSelected(true);
		}
	}

	private void saveWindowStateInOptions() {
		
		// save the complete window state
		Options o = Options.getInstance();
		if (!isFullScreenWindow()) {
			boolean maximized = (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
			Rectangle bounds = maximized && normalWindowBounds != null ? normalWindowBounds : getBounds();
			o.setInitialXPos(bounds.x);
			o.setInitialYPos(bounds.y);
			o.setInitialHeight(bounds.height);
			o.setInitialWidth(bounds.width);
			o.setInitialExtendedState(maximized ? MAXIMIZED_BOTH : NORMAL);

			GraphicsDevice device = findBestScreenDevice(bounds, null);
			if (device != null) {
				o.setInitialScreenDeviceId(device.getIDstring());
			}
		}
		
		// the horizontal divider position must not be saved if the panel is not visible!
		if (o.isShowHintPanel()) {
			o.setInitialHorzDividerLoc(outerSplitPane.getDividerLocation());
		}
		
		// Save the visible component, not a possibly stale menu selection.
		o.setInitialDisplayMode(getCurrentDisplayMode());
		
		o.setInitialVertDividerLoc(-1);
		if (o.getInitialDisplayMode() != 0) {
			o.setInitialVertDividerLoc(splitPanel.getDividerLocation());
		}
	}

	private int getCurrentDisplayMode() {
		if (!splitPanel.hasRight()) {
			return 0;
		}

		Object selected = tabPane.getSelectedComponent();
		if (selected == summaryPanel) {
			return 1;
		}
		if (selected == solutionPanel) {
			return 2;
		}
		if (selected == allStepsPanel) {
			return 3;
		}
		if (selected == cellZoomPanel) {
			return 4;
		}
		return 0;
	}

	private void writeOptionsWithWindowState(String fileName) throws FileNotFoundException {
		
		// save window state
		saveWindowStateInOptions();
		if (fileName == null) {
			Options.getInstance().writeOptions();
		} else {
			Options.getInstance().writeOptions(fileName);
		}
	}

	private void setWindowLayout(boolean reset) {
		
		Options o = Options.getInstance();

		if (reset) {
			o.setInitialDisplayMode(Options.INITIAL_DISP_MODE);
			o.setInitialHeight(Options.INITIAL_HEIGHT);
			o.setInitialHorzDividerLoc(Options.INITIAL_HORZ_DIVIDER_LOC);
			o.setInitialVertDividerLoc(Options.INITIAL_VERT_DIVIDER_LOC);
			o.setInitialWidth(Options.INITIAL_WIDTH);
			o.setShowHintPanel(Options.INITIAL_SHOW_HINT_PANEL);
			o.setShowToolBar(Options.INITIAL_SHOW_TOOLBAR);
		}

		jToolBar1.setVisible(o.isShowToolBar());
		hintPanel.setVisible(o.isShowHintPanel());

		Rectangle screenBounds = getPreferredScreenBounds(o);

		int width = o.getInitialWidth();
		int height = o.getInitialHeight();
		int horzDivLoc = o.getInitialHorzDividerLoc();

		if (screenBounds.height < height) {
			height = screenBounds.height;
		}
		
		if (horzDivLoc > height - 204) {
			horzDivLoc = height - 204;
			// can be used during program run, so has to be saved here
			Options.getInstance().setInitialHorzDividerLoc(horzDivLoc);
		}
		
		if (screenBounds.width < width) {
			width = screenBounds.width;
		}
		
		setSize(width, height);
		switch (o.getInitialDisplayMode()) {
		case 0:
			splitPanel.setRight(null);
			sudokuOnlyMenuItem.setSelected(true);
			break;
		case 1:
			setSplitPane(summaryPanel);
			summaryMenuItem.setSelected(true);
			break;
		case 2:
			setSplitPane(solutionPanel);
			solutionMenuItem.setSelected(true);
			break;
		case 3:
			allStepsPanel.setSudoku(sudokuPanel.getSudoku());
			setSplitPane(allStepsPanel);
			allStepsMenuItem.setSelected(true);
			break;
		case 4:
			setSplitPane(cellZoomPanel);
			cellZoomMenuItem.setSelected(true);
		}
		if (o.getInitialVertDividerLoc() != -1) {
			splitPanel.setDividerLocation(o.getInitialVertDividerLoc());
		}
		syncDisplayModeMenuSelection();

		outerSplitPane.setDividerLocation(horzDivLoc);

		// doesn't work at reset sometimes -> adjust in PropertyChangeListener
		// doesn't work when going back from fullscreen mode either

		outerSplitPaneInitialized = false;
		resetHDivLocLoc = horzDivLoc;
		resetHDivLocTicks = System.currentTimeMillis();
		resetHDivLoc = true;
	}

	private void resetResultPanels() {
		summaryPanel.initialize(null);
		solutionPanel.initialize(null);
		allStepsPanel.resetPanel();
	}

	private void initializeResultPanels() {
		summaryPanel.initialize(sudokuPanel.getSolver());
		solutionPanel.initialize(sudokuPanel.getSolver().getSteps());
		allStepsPanel.resetPanel();
	}

	private void setSplitPane(JPanel panel) {
		
		if (!splitPanel.hasRight()) {
			splitPanel.setRight(tabPane);
		}
		
		tabPane.setSelectedComponent(panel);
		syncDisplayModeMenuSelection();
	}

	private void restoreWindowPlacement() {
		Options o = Options.getInstance();
		if (o.getInitialXPos() != -1 && o.getInitialYPos() != -1) {
			Rectangle desired = new Rectangle(o.getInitialXPos(), o.getInitialYPos(), getWidth(), getHeight());
			Rectangle screenBounds = getPreferredScreenBounds(o);
			setBounds(clampWindowBounds(desired, screenBounds));
		}

		normalWindowBounds = getBounds();
		if ((o.getInitialExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH) {
			setExtendedState(MAXIMIZED_BOTH);
		}
	}

	private void rememberNormalWindowBounds() {
		int state = getExtendedState();
		if ((state & (MAXIMIZED_BOTH | ICONIFIED)) == 0 && !isFullScreenWindow()) {
			normalWindowBounds = getBounds();
		}
	}

	private boolean isFullScreenWindow() {
		if (fullScreenMenuItem != null && fullScreenMenuItem.isSelected() && isUndecorated()) {
			return true;
		}
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			if (device.getFullScreenWindow() == this) {
				return true;
			}
		}
		return false;
	}

	private Rectangle getPreferredScreenBounds(Options o) {
		Rectangle desired = null;
		if (o.getInitialXPos() != -1 && o.getInitialYPos() != -1) {
			desired = new Rectangle(o.getInitialXPos(), o.getInitialYPos(),
					Math.max(1, o.getInitialWidth()), Math.max(1, o.getInitialHeight()));
		}
		GraphicsDevice device = findBestScreenDevice(desired, o.getInitialScreenDeviceId());
		GraphicsConfiguration configuration = device == null ? getGraphicsConfiguration()
				: device.getDefaultConfiguration();
		if (configuration == null) {
			configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
					.getDefaultConfiguration();
		}

		Rectangle bounds = new Rectangle(configuration.getBounds());
		Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
		bounds.x += insets.left;
		bounds.y += insets.top;
		bounds.width -= insets.left + insets.right;
		bounds.height -= insets.top + insets.bottom;
		return bounds;
	}

	private GraphicsDevice findBestScreenDevice(Rectangle desired, String preferredId) {
		GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		if (preferredId != null && !preferredId.isEmpty()) {
			for (GraphicsDevice device : devices) {
				if (preferredId.equals(device.getIDstring())) {
					return device;
				}
			}
		}

		GraphicsDevice best = null;
		long bestArea = 0;
		if (desired != null) {
			for (GraphicsDevice device : devices) {
				Rectangle intersection = desired.intersection(device.getDefaultConfiguration().getBounds());
				long area = intersection.isEmpty() ? 0 : (long) intersection.width * intersection.height;
				if (area > bestArea) {
					best = device;
					bestArea = area;
				}
			}
		}
		return best != null ? best : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
	}

	static Rectangle clampWindowBounds(Rectangle desired, Rectangle screenBounds) {
		int width = Math.min(Math.max(1, desired.width), screenBounds.width);
		int height = Math.min(Math.max(1, desired.height), screenBounds.height);
		int maxX = screenBounds.x + screenBounds.width - width;
		int maxY = screenBounds.y + screenBounds.height - height;
		int x = Math.max(screenBounds.x, Math.min(desired.x, maxX));
		int y = Math.max(screenBounds.y, Math.min(desired.y, maxY));
		return new Rectangle(x, y, width, height);
	}

	private void setPlay(boolean isPlaying) {
		
		gameMode = !isPlaying;
		
		if (isPlaying) {
			if (oldShowDeviationsValid) {
				showDeviationsMenuItem.setSelected(oldShowDeviations);
				oldShowDeviationsValid = false;
			}
		} else {
			oldShowDeviations = showDeviationsMenuItem.isSelected();
			oldShowDeviationsValid = true;
			showDeviationsMenuItem.setSelected(false);
		}
		
		showDeviationsMenuItemActionPerformed(null);

		vagueHintMenuItem.setEnabled(isPlaying);
		mediumHintMenuItem.setEnabled(isPlaying);
		solutionStepMenuItem.setEnabled(isPlaying);
		setAllSinglesMenuItem.setEnabled(isPlaying);
		showDeviationsMenuItem.setEnabled(isPlaying);
		showColorKuMenuItem.setEnabled(isPlaying);

		playGameMenuItem.setEnabled(!isPlaying);
		editGivensMenuItem.setEnabled(isPlaying);
	}

	public void setSolutionStep(SolutionStep step, boolean setInSudokuPanel) {
		
		if (setInSudokuPanel) {
			if (step == null) {
				sudokuPanel.abortStep();
			} else {
				sudokuPanel.setStep(step);
			}
		}
		
		if (step == null) {
			
			setHintText("");
			hinweisAbbrechenButton.setEnabled(false);
			hinweisAusfuehrenButton.setEnabled(false);
			
			if (executeStepToggleButton != null) {
				executeStepToggleButton.setEnabled(false);
			}
			
			if (abortStepToggleButton != null) {
				abortStepToggleButton.setEnabled(false);
			}
			
		} else {
			
			setHintText(step.toString());
			hinweisAbbrechenButton.setEnabled(true);
			hinweisAusfuehrenButton.setEnabled(true);
			getRootPane().setDefaultButton(hinweisAusfuehrenButton);
			
			if (executeStepToggleButton != null) {
				executeStepToggleButton.setEnabled(true);
			}
			
			if (abortStepToggleButton != null) {
				abortStepToggleButton.setEnabled(true);
			}
		}
		
		fixFocus();
	}

	/** Uses the same executor and UI cleanup as the established detailed-hint button. */
	void executeDisplayedStepFromKeyboard() {
		hinweisAusfuehrenButtonActionPerformed(null);
	}

	/** Keeps both hint surfaces synchronized while native reasoning is analyzed or applied. */
	void setReasoningControls(boolean canExecute, boolean canCancel) {
		hinweisAusfuehrenButton.setEnabled(canExecute);
		hinweisAbbrechenButton.setEnabled(canCancel);
		if (executeStepToggleButton != null) {
			executeStepToggleButton.setEnabled(canExecute);
		}
		if (abortStepToggleButton != null) {
			abortStepToggleButton.setEnabled(canCancel);
		}
		if (canExecute) {
			getRootPane().setDefaultButton(hinweisAusfuehrenButton);
		} else if (getRootPane().getDefaultButton() == hinweisAusfuehrenButton) {
			getRootPane().setDefaultButton(null);
		}
	}

	boolean isDisplayedStepExecutionEnabled() {
		return hinweisAusfuehrenButton.isEnabled()
				|| executeStepToggleButton != null && executeStepToggleButton.isEnabled();
	}

	/** Announces one meaningful reasoning transition without opening a modal dialog. */
	void announceReasoningStatus(String resourceKey) {
		announceStatus(resourceKey);
	}

	/** Announces a transient application status without opening a modal dialog. */
	void announceStatus(String resourceKey) {
		if (resourceKey == null || statusLabelCellCandidate == null) return;
		ResourceBundle bundle = ResourceBundle.getBundle("intl/MainFrame");
		String text;
		try {
			text = bundle.getString(resourceKey);
		} catch (java.util.MissingResourceException ex) {
			text = resourceKey;
		}
		statusLabelCellCandidate.setText(text);
		statusLabelCellCandidate.setToolTipText(text);
		statusLabelCellCandidate.getAccessibleContext().setAccessibleName(text);
		statusLabelCellCandidate.getAccessibleContext().setAccessibleDescription(text);
	}

	private void setHintText(String text) {
		hinweisTextArea.setReferenceText(text);
	}

	/**
	 * Copy the current sudoku to the clipboard. If <code>simpleSudoku</code> is set
	 * to <code>true</code>, the givensm the currently set cells and a PM grid are
	 * copied.
	 * 
	 * @param mode
	 * @param simpleSudoku
	 */
	private void copyToClipboard(ClipboardMode mode, boolean simpleSudoku) {
		
		String clipStr = "";
		
		if (simpleSudoku) {
			String dummy = sudokuPanel.getSudokuString(ClipboardMode.CLUES_ONLY);
			clipStr = SudokuUtil.getSSFormatted(dummy);
			clipStr += SudokuUtil.NEW_LINE;
			clipStr += SudokuUtil.NEW_LINE;
			dummy = sudokuPanel.getSudokuString(ClipboardMode.VALUES_ONLY);
			clipStr += SudokuUtil.getSSFormatted(dummy);
			clipStr += SudokuUtil.NEW_LINE;
			clipStr += SudokuUtil.NEW_LINE;
			dummy = sudokuPanel.getSudokuString(ClipboardMode.PM_GRID);
			clipStr += SudokuUtil.getSSPMGrid(dummy);
		} else {
			clipStr = sudokuPanel.getSudokuString(mode);
		}
		
		try {
			Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
			StringSelection content = new StringSelection(clipStr);
			clip.setContents(content, null);
			OperationSoundPlayer.play(OperationSoundPlayer.Sound.COPY);
		} catch (Exception ex) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error writing to clipboard", ex);
		}
		
		fixFocus();
	}

	private void setToggleButton(JToggleButton button, boolean ctrlPressed) {
		
		if (button == null) {
			sudokuPanel.resetShowHintCellValues();
		} else {
			
			int index = 0;
			for (index = 0; index < toggleButtons.length; index++) {
				if (toggleButtons[index] == button) {
					break;
				}
			}
			
			if (index == Sudoku2.UNITS) {
				sudokuPanel.toggleBivalueFilter();
			} else {
				sudokuPanel.toggleCandidateValueFilter(index + 1, ctrlPressed);
			}
			sudokuPanel.checkIsShowInvalidOrPossibleCells();
		}
		
		check();
		sudokuPanel.repaint();
		fixFocus();
	}

	/**
	 * Save puzzles/configurations.
	 * 
	 * @param puzzle
	 */
	private void saveToFile(boolean puzzle) {
		
		JFileChooser chooser = new JFileChooser(Options.getInstance().getDefaultFileDir());
		chooser.setAcceptAllFileFilterUsed(false);
		MyFileFilter[] filters = puzzleFileSaveFilters;
		if (!puzzle) {
			filters = configFileFilters;
		}
		
		for (int i = 0; i < filters.length; i++) {
			chooser.addChoosableFileFilter(filters[i]);
		}
		
		int returnVal = chooser.showSaveDialog(this);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			try {
				String path = chooser.getSelectedFile().getPath();
				path = path.substring(0, path.lastIndexOf(File.separatorChar));
				Options.getInstance().setDefaultFileDir(path);
				MyFileFilter actFilter = (MyFileFilter) chooser.getFileFilter();
				int filterType = actFilter.getType();
				path = chooser.getSelectedFile().getAbsolutePath();
				if (!puzzle) {
					// Options
					if (!path.endsWith("." + configFileExt)) {
						path += "." + configFileExt;
					}
				} else {
					if (filterType == 1) {
						if (!path.endsWith("." + solutionFileExt)) {
							path += "." + solutionFileExt;
						}
					} else if (filterType == 9) {
						if (!path.endsWith("." + ssFileExt)) {
							path += "." + ssFileExt;
						}
					} else {
						if (!path.endsWith("." + textFileExt)) {
							path += "." + textFileExt;
						}
					}
				}
				
				File checkFile = new File(path);
				if (checkFile.exists()) {
					// Override warning!
					MessageFormat msgf = new MessageFormat("");
					Object[] args = new Object[] { checkFile.getName() };
					msgf.applyPattern(
							java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.file_exists"));
					String warning = msgf.format(args);
					String title = java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.hint");
					if (JOptionPane.showConfirmDialog(null, warning, title,
							JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
						return;
					}
				}

				saveToFile(puzzle, path, filterType);
				
			} catch (Exception ex2) {
				
				JOptionPane.showMessageDialog(
					this, 
					ex2.toString(),
					java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"),
					JOptionPane.ERROR_MESSAGE
				);
				
				sudokuFileName = null;
			}
			
			setTitleWithFile();
		}
	}

	/**
	 * Actually writes configurations and sudoku files. If <code>puzzle</code> is
	 * <code>false</code>, a configuration is written. <code>filterType</code> is
	 * one of the file types defined by {@link MyFileFilter}. If the method is
	 * called via the "Save puzzle" menu item, <code>filterType</code> can be 8
	 * (generic text file). In this case a PM grid is written.
	 * 
	 * @param puzzle
	 * @param path
	 * @param filterType
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private void saveToFile(boolean puzzle, String path, int filterType) throws FileNotFoundException, IOException {
		
		sudokuFileName = path;
		
		if (!puzzle) {
			// Options
			writeOptionsWithWindowState(path);
		} else {

			String newLine = System.getProperty("line.separator");
			if (filterType == 1) {
				
				sudokuFileType = 1;
				ZipOutputStream zOut = new ZipOutputStream(new FileOutputStream(path));
				zOut.putNextEntry(new ZipEntry("SudokuData"));
				XMLEncoder out = new XMLEncoder(zOut);
                final Exception[] encodingFailure = new Exception[1];
                out.setExceptionListener(error -> encodingFailure[0] = error);
				out.writeObject(sudokuPanel.getSudoku());
				out.writeObject(SudokuSolverFactory.getDefaultSolverInstance().getAnzSteps());
				out.writeObject(SudokuSolverFactory.getDefaultSolverInstance().getSteps());
				out.writeObject(solutionPanel.getTitels());
				out.writeObject(solutionPanel.getTabSteps());
				out.writeObject(savePoints);
				out.close();
				zOut.flush();
				zOut.close();
                if (encodingFailure[0] != null) throw new IOException("Unable to save Sudoku state", encodingFailure[0]);
				
			} else if (filterType == 9) {
				
				sudokuFileType = 9;
				// SimpleSudoku format (see comment in loadFromFile())
				PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(path)));
				String clues = sudokuPanel.getSudokuString(ClipboardMode.CLUES_ONLY);
				out.println(SudokuUtil.getSSFormatted(clues));
				out.println();
				// additionally set cells
				Sudoku2 tmpSudoku = sudokuPanel.getSudoku();
				for (int i = 0; i < Sudoku2.LENGTH; i++) {
					if (tmpSudoku.getValue(i) != 0 && !tmpSudoku.isFixed(i)) {
						out.printf("I%02d%d%n", i, tmpSudoku.getValue(i));
					}
				}
				
				// eliminated candidates
				for (int i = 0; i < Sudoku2.LENGTH; i++) {
					if (tmpSudoku.getValue(i) == 0) {
						for (int j = 1; j <= Sudoku2.UNITS; j++) {
							if (tmpSudoku.isValidValue(i, j) && !tmpSudoku.isCandidate(i, j)) {
								out.printf("E%02d%03d%n", i, j);
							}
						}
					}
				}
				
				out.close();
                if (out.checkError()) throw new IOException("Unable to save Sudoku text file");
				
			} else {
				
				sudokuFileType = 8;
				if (filterType == 8) {
					// generic text file: can occur when save is executed
					filterType = 6;
				}
				
				BufferedWriter out = new BufferedWriter(new FileWriter(path));
				String line = "";
				switch (filterType) {
				case 2:
					line = sudokuPanel.getSudokuString(ClipboardMode.CLUES_ONLY);
					break;
				case 3:
					line = sudokuPanel.getSudokuString(ClipboardMode.CLUES_ONLY_FORMATTED);
					break;
				case 4:
					line = sudokuPanel.getSudokuString(ClipboardMode.PM_GRID);
					break;
				case 5:
					line = sudokuPanel.getSudokuString(ClipboardMode.PM_GRID_WITH_STEP);
					break;
				case 6:
					line = sudokuPanel.getSudokuString(ClipboardMode.CLUES_ONLY_FORMATTED);
					line += newLine;
					line += newLine;
					line += sudokuPanel.getSudokuString(ClipboardMode.VALUES_ONLY_FORMATTED);
					line += newLine;
					line += newLine;
					line += sudokuPanel.getSudokuString(ClipboardMode.PM_GRID);
					break;
				case 7:
					line = sudokuPanel.getSudokuString(ClipboardMode.LIBRARY);
					break;
				}
				
				out.write(line);
				out.close();
			}
            if (replayController != null) {
                replayController.retainCurrent();
                replayController.showRetentionFailure(ReplayText.text("puzzleSaved"));
            }
		}
	}

	/**
	 * Loads puzzles and/or configurations from files. loading either type resets
	 * the mode to "playing".
	 * 
	 * @param puzzle
	 */
	private void loadFromFile(boolean puzzle) {
		
		JFileChooser chooser = new JFileChooser(Options.getInstance().getDefaultFileDir());
		chooser.setAcceptAllFileFilterUsed(false);
		MyFileFilter[] filters = puzzleFileLoadFilters;
		
		if (!puzzle) {
			filters = configFileFilters;
		}
		
		for (int i = 0; i < filters.length; i++) {
			chooser.addChoosableFileFilter(filters[i]);
		}
		
		int returnVal = chooser.showOpenDialog(this);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			String path = chooser.getSelectedFile().getPath();
			path = path.substring(0, path.lastIndexOf(File.separatorChar));
			Options.getInstance().setDefaultFileDir(path);
			path = chooser.getSelectedFile().getAbsolutePath();
			MyFileFilter filter = (MyFileFilter) chooser.getFileFilter();
			loadFromFile(path, filter.getType());
		}
	}

	public static void ShowWarningMSG(String title, String msg) {
		JOptionPane.showMessageDialog(null, msg, title, JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * Verify if the import line contains valid characters.
	 * 
	 * @param importString
	 * @return
	 */
	public boolean ValidateImportLine(String line) {

		ResourceBundle bundle = ResourceBundle.getBundle("intl/MainFrame");
		String title = bundle.getString("MainFrame.ValidateImportLine.title");
		String error1 = bundle.getString("MainFrame.ValidateImportLine.error1");
		String error2a = bundle.getString("MainFrame.ValidateImportLine.error2a");
		String error2b = bundle.getString("MainFrame.ValidateImportLine.error2b");
		
		if (line.length() != Sudoku2.LENGTH) {
			ShowWarningMSG(title, error1);
			return false;
		}

		for (int i = 0; i < line.length(); i++) {
			
			char c = line.charAt(i);
			boolean isDigit = c >= '0' && c <= '9';
			boolean isEmpty = c == '.';
			
			if (!isDigit && !isEmpty) {
				ShowWarningMSG(title, error2a + c + error2b);
				return false;
			}
		}

		return true;
	}

	public boolean loadFromImportLine(String importString) {

		String line = importString.trim();
		if (ValidateImportLine(line)) {
			setPuzzle(line);
			clearSavePoints();
			return true;
		}

		return false;
	}

	private void showPuzzleSolution() {

		boolean isStatusValid = sudokuPanel.getSudoku().getStatus() == SudokuStatus.VALID;
		boolean hasMultipleSolutions = sudokuPanel.getSudoku().getStatus() == SudokuStatus.MULTIPLE_SOLUTIONS;
		
		if (hasMultipleSolutions) {			
			String msg = java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.solve_error");
			JOptionPane.showMessageDialog(this, msg);			
			return;
		}
		
		if (!isStatusValid || sudokuPanel.getSudoku().isSolved()) {
			return;
		}

		sudokuPanel.saveState();
		completionTransition.begin(false, true);
		Sudoku2 sudoku = sudokuPanel.getSudoku();

		for (int c = 0; c < Sudoku2.UNITS; c++) {
			for (int r = 0; r < Sudoku2.UNITS; r++) {
				int value = sudoku.getSolution(r, c);
				sudoku.setCell(r, c, value);
			}
		}

		repaint();
	}
	
	public void showSolutionCount() {
		
		ResourceBundle bundle = java.util.ResourceBundle.getBundle("intl/MainFrame");
		String msg1 = bundle.getString("MainFrame.solutionCountMenuItem.msg1");
		String msg2 = bundle.getString("MainFrame.solutionCountMenuItem.msg2");
		String msg3 = bundle.getString("MainFrame.solutionCountMenuItem.msg3");
		String msg4 = bundle.getString("MainFrame.solutionCountMenuItem.msg4");
		String msg5 = bundle.getString("MainFrame.solutionCountMenuItem.msg5");
		
		Sudoku2 sudoku = sudokuPanel.getSudoku().clone();
		SudokuGenerator generator = SudokuGeneratorFactory.getDefaultGeneratorInstance();
		int solutionCount = generator.getNumberOfSolutions(sudoku, 999);
		
		if (solutionCount == 0) {
			JOptionPane.showMessageDialog(
				this,
				msg3
			);
		} else if (solutionCount == 1) {
			JOptionPane.showMessageDialog(
				this,
				msg1 + " " + solutionCount + " " + msg4
			);
		} else if (solutionCount <= 999) {
			JOptionPane.showMessageDialog(
				this,
				msg1 + " " + solutionCount + " " + msg5
			);
		} else {
			JOptionPane.showMessageDialog(
				this,
				msg2 + " " + solutionCount + " " + msg5
			);
		}
	}

	/**
	 * Loads a file
	 * 
	 * @param path
	 * @param fileType 0 .. options, 1 .. sudoku from hsol, 8 .. sudoku from text
	 *                 file
	 */
	@SuppressWarnings({ "unchecked" })
	private void loadFromFile(String path, int fileType) {

		try {
			sudokuFileName = path;
			sudokuFileType = fileType;
			if (fileType == 0) {
				// Options
				Options.readOptions(path);
				Options.getInstance().initializeAnnotationPalettePreferences(null, null);
				projectAnnotationPaletteForCurrentTool();
				BackgroundGeneratorThread.getInstance().resetAll();
				sudokuFileName = null;
			} else if (fileType == 1) {
				// Puzzle
				ZipInputStream zIn = new ZipInputStream(new FileInputStream(path));
				zIn.getNextEntry();
				XMLDecoder in = new XMLDecoder(zIn);
				GuiState state = new GuiState(sudokuPanel, sudokuPanel.getSolver(), solutionPanel);
				// could be old file -> contains instance of Sudoku and not Sudoku2!
				Object sudokuTemp = in.readObject();
				if (sudokuTemp instanceof Sudoku2) {
					// ok: new version!
					state.setSudoku((Sudoku2) sudokuTemp);
				} else {
					// old version: convert it!
					Sudoku dummy = (Sudoku) sudokuTemp;
					String sudokuTempLib = dummy.getSudoku(ClipboardMode.LIBRARY, null);

					state.setSudoku(new Sudoku2());
					state.getSudoku().setSudoku(sudokuTempLib, false);
					state.getSudoku().setInitialState(dummy.getInitialState());
					// contains another instance of Sudoku (solvedSudoku)
					// that is not needed anymore
					sudokuTemp = in.readObject();
				}
				
				state.setAnzSteps((int[]) in.readObject());
				state.setSteps((List<SolutionStep>) in.readObject());
				state.setTitels((List<String>) in.readObject());
				state.setTabSteps((List<List<SolutionStep>>) in.readObject());
				state.resetAnzSteps();
				
				try {
					savePoints = (List<GuiState>) in.readObject();
					for (int i = 0; i < savePoints.size(); i++) {
						// internal fields must be set!
						savePoints.get(i).initialize(sudokuPanel, SudokuSolverFactory.getDefaultSolverInstance(),
								solutionPanel);
					}
				} catch (Exception ex) {
					// when an older puzzle file is loaded, savepoints are not in the file or the
					// format is incompatible
					clearSavePoints();
				}
				
				in.close();
				// .hsol files deliberately do not own screen-local doodles or free chains.
				// Opening a file therefore cannot leave annotations from the previous puzzle.
				sudokuPanel.discardAnnotations();
				setState(state);
				setMode(GameMode.PLAYING, true);
				completionTransition.begin(false, isCurrentPuzzleCorrectlySolved());
				if (replayController != null) replayController.startNewAttempt();
				
			} else if (fileType == 8) {
				
				// load from text file
				BufferedReader in = new BufferedReader(new FileReader(path));
				StringBuilder tmp = new StringBuilder();
				String line = null;
				
				while ((line = in.readLine()) != null) {
					tmp.append(line);
					tmp.append("\r\n");
				}
				
				in.close();
				setPuzzle(tmp.toString());
				clearSavePoints();
				
			} else if (fileType == 9) {
				
				// SimpleSudoku format: The givens followed by set cells and eliminated
				// candidates
				// example:
//                     *-----------*
//                     |38.|...|5.6|
//                     |...|9..|...|
//                     |...|.4.|89.|
//                     |---+---+---|
//                     |.4.|6..|.3.|
//                     |9..|7.1|..4|
//                     |.7.|..8|.1.|
//                     |---+---+---|
//                     |.13|.6.|...|
//                     |...|..4|...|
//                     |2.5|...|.83|
//                     *-----------*
//
//                    I388
//                    I074
//                    I029
//                    I153
//                    I358
//                    I751
//                    E78007
//                    E78009
//                    E73009
				// Innc: nn - index of the cell, c - candidate
				// Ennccc: nn - index of the cell, ccc - candidate
				BufferedReader in = new BufferedReader(new FileReader(path));
				StringBuilder tmp = new StringBuilder();
				String line = null;
				
				while ((line = in.readLine()) != null) {
					
					if (line.trim().isEmpty()) {
						// all the givens are read -> abort reading
						break;
					}
					
					tmp.append(line);
					tmp.append("\r\n");
				}
				
				// first set the givens
				Sudoku2 tmpSudoku = new Sudoku2();
				tmpSudoku.setSudoku(tmp.toString());
				
				// now read set cells and eliminations
				while ((line = in.readLine()) != null) {
					
					if (line.trim().isEmpty()) {
						continue;
					}
					
					char recordType = line.charAt(0);
					if (recordType == 'I') {
						int index = Integer.parseInt(line.substring(1, 3));
						int candidate = Character.digit(line.charAt(3), 10);
						tmpSudoku.setCell(index, candidate);
					} else if (recordType == 'E') {
						int index = Integer.parseInt(line.substring(1, 3));
						int candidate = Integer.parseInt(line.substring(3, 6));
						tmpSudoku.delCandidate(index, candidate);
					}
				}
				
				in.close();
				// everything read and decoded -> set the sudoku
				setPuzzle(tmpSudoku.getSudoku(ClipboardMode.LIBRARY));
				clearSavePoints();
				
			} else {
				
				formatter.applyPattern(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.invalid_filename"));
				String msg = formatter.format(new Object[] { path });
				JOptionPane.showMessageDialog(
					this,
					msg,
					java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"),
					JOptionPane.ERROR_MESSAGE
				);
				
				sudokuFileName = null;
			}
		} catch (Exception ex2) {
			JOptionPane.showMessageDialog(
				this,
				ex2.toString(),
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"),
				JOptionPane.ERROR_MESSAGE
			);
			ex2.printStackTrace();
			sudokuFileName = null;
		}
		
		setTitleWithFile();
	}

	/**
	 * @param args the command line arguments
	 */
	/*
	public static void main(String args[]) {
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ex) {
			Logger.getLogger(MainFrame.class.getName()).log(Level.SEVERE, "Error setting LaF", ex);
		}
		
		java.awt.EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				new MainFrame(null).setVisible(true);
			}
		});
	}*/

	private void setTitleWithFile() {
		
		savePuzzleMenuItem.setEnabled(sudokuFileName != null);
		if (sudokuFileName == null) {
			setTitle(VERSION);
		} else {
			int index = sudokuFileName.lastIndexOf('\\') + 1;
			int index2 = sudokuFileName.lastIndexOf('/') + 1;
			if (index2 > index) {
				index = index2;
			}
			String fileName = sudokuFileName.substring(index);
			setTitle(VERSION + "  (" + fileName + ")");
		}
	}

	private void setLevelFromMenu() {
		
		int selected = 0;
		for (int i = 0; i < levelMenuItems.length; i++) {
			if (levelMenuItems[i].isSelected()) {
				selected = i + 1;
				break;
			}
		}

		Options.getInstance().setActLevel(Options.getInstance().getDifficultyLevels()[selected].getOrdinal());
		BackgroundGeneratorThread.getInstance().setNewLevel(Options.getInstance().getActLevel());
		check();
		fixFocus();
	}

	public void stepAusfuehren() {
		hinweisAusfuehrenButtonActionPerformed(null);
	}

	public final void fixFocus() {
		sudokuPanel.requestFocusInWindow();
	}

	public SudokuPanel getSudokuPanel() {
		return sudokuPanel;
	}

	HintTextArea getHintTextArea() {
		return hinweisTextArea;
	}

	public SolutionPanel getSolutionPanel() {
		return solutionPanel;
	}

	public final void check() {
        updateCellSelectionStatus();
		
		if (sudokuPanel != null) {
			refreshAnnotationUndoControls();
			showCandidatesMenuItem.setSelected(sudokuPanel.isShowCandidates());
			showWrongValuesMenuItem.setSelected(sudokuPanel.isShowWrongValues());
			showDeviationsMenuItem.setSelected(sudokuPanel.isShowDeviations());
			showColorKuMenuItem.setSelected(Options.getInstance().isShowColorKuAct());
			prepareToggleButtonIcons(Options.getInstance().isShowColorKuAct());
			
			// either all ToggleButtons are set or none is
			if (toggleButtons[0] != null) {
				
				boolean[] remainingCandidates = sudokuPanel.getRemainingCandidates();
				for (int i = 0; i < remainingCandidates.length; i++) {
					
					JToggleButton button = toggleButtons[i];
					
					// change the standard icons
					if (remainingCandidates[i]) {

						if (button.getIcon() != toggleButtonIcons[i]) {
							button.setIcon(toggleButtonIcons[i]);
						}

					} else {

						if (button.getIcon() != emptyToggleButtonIcons[i]) {
							button.setIcon(emptyToggleButtonIcons[i]);
						}
					}
				}
				
				for (int i = 0; i < Sudoku2.UNITS; i++) {
					if (toggleButtons[i].isEnabled()) {
						if (sudokuPanel.getShowHintCellValues()[i + 1]) {
							toggleButtons[i].setSelected(true);
						} else {
							toggleButtons[i].setSelected(false);
						}
					}
				}
				fxyToggleButton.setSelected(sudokuPanel.isBivalueFilterActive());
				fxyzToggleButton.setSelected(sudokuPanel.isTrivalueFilterActive());
			}
			
			redGreenToggleButton.setSelected(sudokuPanel.isInvalidCells());
			Sudoku2 sudoku = sudokuPanel.getSudoku();
			if (sudoku != null) {
				
				DifficultyLevel tmpLevel = sudoku.getLevel();
				if (tmpLevel != null) {
					statusLabelLevel.setText(StepConfig.getLevelName(tmpLevel) + " (" + sudoku.getScore() + ")");
				} else {
					statusLabelLevel.setText("-");
				}
				
				setProgressLabel();
				
			} else {
				// no puzzle loaded
				statusLabelLevel.setText(ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.statusLabelLevel.text"));
			}
			
			refreshAnnotationColorModeControls();
			
			fixFocus();
		}
		
		// adjust mode menus and labels
		// Options.actLevel is always valid
		if (Options.getInstance().getActLevel() != -1) {
			if (Options.getInstance().getGameMode() != GameMode.PLAYING) {
				// we cant have a level that is easier than the easiest
				// selected training/practising puzzle -> we could never
				// find a new sudoku for that
				int tmpLevel = Options.getInstance().getActLevel();
				for (StepConfig act : Options.getInstance().getOrgSolverSteps()) {
					if (act.isEnabledTraining() && act.getLevel() > tmpLevel) {
						tmpLevel = act.getLevel();
					}
				}
				
				if (tmpLevel != Options.getInstance().getActLevel()) {
//                    level = Options.getInstance().getDifficultyLevel(tmpLevel);
					Options.getInstance().setActLevel(tmpLevel);
				}
			}
			
			int ord = Options.getInstance().getActLevel() - 1;
			if (levelMenuItems[ord] != null && levelComboBox.getItemCount() > ord) {
				levelMenuItems[ord].setSelected(true);
				levelComboBox.setSelectedIndex(ord);
			}
			
			int mOrdinal = Options.getInstance().getGameMode().ordinal();
			if (modeMenuItems != null && modeMenuItems[mOrdinal] != null) {
				
				modeMenuItems[mOrdinal].setSelected(true);
				
				String labelStr = modeMenuItems[mOrdinal].getText();
				if (labelStr.endsWith("...")) {
					labelStr = labelStr.substring(0, labelStr.length() - 3);
				}
				
				if (Options.getInstance().getGameMode() != GameMode.PLAYING) {
					labelStr += " (" + Options.getInstance().getTrainingStepsString(true) + ")";
				}
				
				statusLabelModus.setText(labelStr);
			}
			
			showHintButtonsCheckBoxMenuItem.setSelected(Options.getInstance().isShowHintButtonsInToolbar());
		}
		
		statusLinePanel.invalidate();
	}

	private boolean isStringFlavorInClipboard() {
		
		Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();

		if (clip.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
			return true;
		}
		
		return false;
	}

	private void adjustPasteMenuItem() {
		
		try {
			
			if (Main.OS_NAME.contains("mac")) {
				pasteMenuItem.setEnabled(true);
			} else {
				if (isStringFlavorInClipboard()) {
					pasteMenuItem.setEnabled(true);
				} else {
					pasteMenuItem.setEnabled(false);
				}
			}
			
		} catch (IllegalStateException ex) {
			clipTimer.start();
		}
	}

	@Override
	public void flavorsChanged(FlavorEvent e) {
		adjustPasteMenuItem();
	}

	private Image getIcon() {
		URL url = getClass().getResource("/img/hodoku02-32.png");
		return getToolkit().getImage(url);
	}

	public List<GuiState> getSavePoints() {
		return savePoints;
	}

	public void abortStep() {
		
		sudokuPanel.abortStep();
		setHintText("");
		hinweisAbbrechenButton.setEnabled(false);
		hinweisAusfuehrenButton.setEnabled(false);
		
		if (executeStepToggleButton != null) {
			executeStepToggleButton.setEnabled(false);
		}
		
		if (abortStepToggleButton != null) {
			abortStepToggleButton.setEnabled(false);
		}
		
		fixFocus();
	}

	private void createProgressLabelImages() {
		
		try {
			
			progressImages[0] = new ImageIcon(getClass().getResource("/img/invalid20.png"));
			BufferedImage overlayImage = ImageIO.read(getClass().getResource("/img/ce1-20.png"));
			
			for (int i = 1; i < progressImages.length; i++) {
				BufferedImage act = new BufferedImage(20, 20, BufferedImage.TYPE_4BYTE_ABGR);
				Graphics2D gAct = (Graphics2D) act.getGraphics();
				gAct.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				gAct.setColor(Options.getInstance().getDifficultyLevels()[i].getBackgroundColor());
				gAct.fillOval(2, 2, 16, 16);
				gAct.drawImage(overlayImage, 0, 0, null);
				progressImages[i] = new ImageIcon(act);
			}
			
		} catch (Exception ex) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Error creating progressLabel images", ex);
		}
	}

	public void setProgressLabel() {
		
		if (sudokuPanel == null) {
			// do nothing
			return;
		}
		
		Sudoku2 sudoku = sudokuPanel.getSudoku();
		if (sudoku.getStatus() != SudokuStatus.VALID) {
			progressLabel.setIcon(progressImages[0]);
			progressLabel.setText("-");
			return;
		}
		
		// ok valid sudoku, currentLevel and currentScore must have been set
		// not necessarily!
		if (getCurrentLevel() != null) {
			
			progressLabel.setIcon(progressImages[getCurrentLevel().getOrdinal()]);
			double proc = getCurrentScore();
			int intProc = (int) (proc / sudoku.getScore() * 100);
			
			if (intProc > 100) {
				intProc = 100;
			}
			
			if (intProc < 1) {
				intProc = 1;
			}
			
			if (getCurrentScore() == 0) {
				intProc = 0;
			}
			
			progressLabel.setText(intProc + "%");
		} else {
			progressLabel.setIcon(progressImages[0]);
			progressLabel.setText("-");
		}
	}

	/**
	 * @return the currentLevel
	 */
	public synchronized DifficultyLevel getCurrentLevel() {
		return currentLevel;
	}

	/**
	 * @param currentLevel the currentLevel to set
	 */
	public synchronized void setCurrentLevel(DifficultyLevel currentLevel) {
		this.currentLevel = currentLevel;
	}

	/**
	 * @return the currentScore
	 */
	public synchronized int getCurrentScore() {
		return currentScore;
	}

	/**
	 * @param currentScore the currentScore to set
	 */
	public synchronized void setCurrentScore(int currentScore) {
		this.currentScore = currentScore;
	}

	/** Publishes a background progress result only while its source board is current. */
	void publishProgressCheck(Sudoku2 checkedSudoku, String boardSignature,
			boolean updateStatus, boolean updateProgress) {
		Sudoku2 currentSudoku = sudokuPanel.getSudoku();
		if (!boardSignature.equals(TechniqueStepCatalog.createSignature(currentSudoku))) return;
		if (updateStatus) {
			currentSudoku.setStatus(checkedSudoku.getStatus());
			if (checkedSudoku.getStatus() == SudokuStatus.VALID
					&& checkedSudoku.isSolutionSet()) {
				currentSudoku.setSolution(checkedSudoku.getSolution().clone());
			}
		}
		if (updateProgress) {
			setCurrentLevel(checkedSudoku.getLevel());
			setCurrentScore(checkedSudoku.getScore());
		} else {
			setCurrentLevel(null);
			setCurrentScore(0);
		}
		setProgressLabel();
		if (updateStatus) sudokuPanel.repaint();
	}

	/**
	 * Handles the display of the hint buttons in the toolbar. If they are made
	 * visible the first time, they have to be created.
	 * 
	 */
	private void setShowHintButtonsInToolbar() {
		
		if (vageHintToggleButton == null) {
			
			// create the buttons
			hintSeperator = new javax.swing.JSeparator();
			hintSeperator.setOrientation(javax.swing.SwingConstants.VERTICAL);
			hintSeperator.setMaximumSize(new java.awt.Dimension(5, 32767));
			hintSeperator.setVisible(false);
			jToolBar1.add(hintSeperator);

			vageHintToggleButton = new javax.swing.JButton();
			vageHintToggleButton.setIcon(ToolbarIcons.vagueHint());
			vageHintToggleButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent evt) {
					hintToggleButtonActionPerformed(true);
				}
			});
			vageHintToggleButton.setToolTipText(java.util.ResourceBundle.getBundle("intl/MainFrame")
					.getString("MainFrame.vageHintToolButton.toolTipText"));
			vageHintToggleButton.setVisible(false);
			jToolBar1.add(vageHintToggleButton);

			concreteHintToggleButton = new javax.swing.JButton();
			concreteHintToggleButton.setIcon(ToolbarIcons.concreteHint());
			concreteHintToggleButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent evt) {
					hintToggleButtonActionPerformed(false);
				}
			});
			concreteHintToggleButton.setVisible(false);
			concreteHintToggleButton.setToolTipText(java.util.ResourceBundle.getBundle("intl/MainFrame")
					.getString("MainFrame.concreteHintToolButton.toolTipText"));
			jToolBar1.add(concreteHintToggleButton);

			showNextStepToggleButton = new javax.swing.JButton();
			showNextStepToggleButton.setIcon(ToolbarIcons.nextStep());
			showNextStepToggleButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent evt) {
					loesungsSchrittMenuItemActionPerformed(null);
				}
			});
			showNextStepToggleButton.setToolTipText(java.util.ResourceBundle.getBundle("intl/MainFrame")
					.getString("MainFrame.neuerHinweisButton.toolTipText"));
			showNextStepToggleButton.setVisible(false);
			jToolBar1.add(showNextStepToggleButton);

			selectTechniqueToggleButton = new javax.swing.JButton();
			selectTechniqueToggleButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent evt) {
					if ((evt.getModifiers() & java.awt.event.ActionEvent.ALT_MASK) != 0) {
						optionKeyDown = true;
					}
					showTechniqueSelector();
				}
			});
			updateTechniqueSelectorButton();
            selectTechniqueToggleButton.setName("availableTechniquesToolbarButton");
            jToolBar1.add(selectTechniqueToggleButton);

			executeStepToggleButton = new javax.swing.JButton();
			executeStepToggleButton.setIcon(ToolbarIcons.execute());
			executeStepToggleButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent evt) {
					hinweisAusfuehrenButtonActionPerformed(null);
				}
			});
			executeStepToggleButton.setToolTipText(java.util.ResourceBundle.getBundle("intl/MainFrame")
					.getString("MainFrame.hinweisAusfuehrenButton.toolTipText"));
			executeStepToggleButton.setVisible(false);
			jToolBar1.add(executeStepToggleButton);

			abortStepToggleButton = new javax.swing.JButton();
			abortStepToggleButton.setIcon(ToolbarIcons.abort());
			abortStepToggleButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent evt) {
					hinweisAbbrechenButtonActionPerformed(null);
				}
			});
			abortStepToggleButton.setToolTipText(java.util.ResourceBundle.getBundle("intl/MainFrame")
					.getString("MainFrame.hinweisAbbrechenButton.toolTipText"));
			abortStepToggleButton.setVisible(false);
			jToolBar1.add(abortStepToggleButton);
		}
		if (Options.getInstance().isShowHintButtonsInToolbar()) {
			
			hintSeperator.setVisible(true);
			vageHintToggleButton.setVisible(true);
			concreteHintToggleButton.setVisible(true);
			showNextStepToggleButton.setVisible(true);
			selectTechniqueToggleButton.setVisible(true);
			executeStepToggleButton.setVisible(true);
			abortStepToggleButton.setVisible(true);
			executeStepToggleButton.setEnabled(hinweisAusfuehrenButton.isEnabled());
			abortStepToggleButton.setEnabled(hinweisAbbrechenButton.isEnabled());
			
		} else {
			
			hintSeperator.setVisible(false);
			vageHintToggleButton.setVisible(false);
			concreteHintToggleButton.setVisible(false);
			showNextStepToggleButton.setVisible(false);
			executeStepToggleButton.setVisible(false);
			abortStepToggleButton.setVisible(false);
		}
        selectTechniqueToggleButton.setVisible(true);
		styleToolbarControls();
        jToolBar1.revalidate();
		
		check();
		fixFocus();
	}

	/**
	 * Action event for hint buttons
	 * 
	 * @param isVage
	 */
	private void hintToggleButtonActionPerformed(boolean isVage) {
		if (isVage) {
			vageHintMenuItemActionPerformed(null);
		} else {
			mediumHintMenuItemActionPerformed(null);
		}
	}

	public boolean isInputMode() {
		return gameMode;
	}

	class MyFileFilter extends FileFilter {

		private int type;

		MyFileFilter(int type) {
			this.type = type;
		}

		@Override
		public boolean accept(File f) {
			
			if (f.isDirectory()) {
				return true;
			}
			
			String[] parts = f.getName().split("\\.");
			if (parts.length > 1) {
				String ext = parts[parts.length - 1];
				switch (type) {
				case 0:
					// Configuration Files
					if (ext.equalsIgnoreCase(java.util.ResourceBundle.getBundle("intl/MainFrame")
							.getString("MainFrame.config_file_ext"))) {
						return true;
					}
					break;
				case 1:
					// Puzzles with Solutions
					if (ext.equalsIgnoreCase(java.util.ResourceBundle.getBundle("intl/MainFrame")
							.getString("MainFrame.solution_file_ext"))) {
						return true;
					}
					break;
				case 2:
				case 3:
				case 4:
				case 5:
				case 6:
				case 7:
				case 8:
					// Any kind of text file
					if (ext.equalsIgnoreCase(java.util.ResourceBundle.getBundle("intl/MainFrame")
							.getString("MainFrame.text_file_ext"))) {
						return true;
					}
					break;
				case 9:
					// SimpleSudoku files
					if (ext.equalsIgnoreCase(
							java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.ss_file_ext"))) {
						return true;
					}
					break;
				default:
					return false;
				}
			}
			return false;
		}

		@Override
		public String getDescription() {
			switch (type) {
			case 0:
				return java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.config_file_descr");
			case 1:
				return java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.solution_file_descr");
			case 2:
				return java.util.ResourceBundle.getBundle("intl/MainFrame")
						.getString("MainFrame.solution_file_descr_gal");
			case 3:
				return java.util.ResourceBundle.getBundle("intl/MainFrame")
						.getString("MainFrame.solution_file_descr_gf");
			case 4:
				return java.util.ResourceBundle.getBundle("intl/MainFrame")
						.getString("MainFrame.solution_file_descr_pm");
			case 5:
				return java.util.ResourceBundle.getBundle("intl/MainFrame")
						.getString("MainFrame.solution_file_descr_pms");
			case 6:
				return java.util.ResourceBundle.getBundle("intl/MainFrame")
						.getString("MainFrame.solution_file_descr_pmg");
			case 7:
				return java.util.ResourceBundle.getBundle("intl/MainFrame")
						.getString("MainFrame.solution_file_descr_l");
			case 8:
				return java.util.ResourceBundle.getBundle("intl/MainFrame")
						.getString("MainFrame.solution_file_descr_text");
			case 9:
				return java.util.ResourceBundle.getBundle("intl/MainFrame")
						.getString("MainFrame.solution_file_descr_ss");
			default:
				return java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.unknown_file_type");
			}
		}

		public int getType() {
			return type;
		}
	}

}
