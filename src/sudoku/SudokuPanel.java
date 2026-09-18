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

import generator.SudokuGenerator;
import generator.SudokuGeneratorFactory;
import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Point2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.w3c.dom.Node;
import solver.SudokuSolver;
import solver.SudokuSolverFactory;
import solver.SudokuStepFinder;

/**
 * A specialized JPanel for displaying and manipulating Sudokus.<br>
 *
 * Mouse click detection:<br>
 * <br>
 *
 * AWT seems to have problems with mouse click detection: if the mouse moves a
 * tiny little bit between PRESSED and RELEASED, no CLICKED event is produced.
 * This means, that when playing HoDoKu with the mouse fast, the program often
 * seems to ignore the mouse.<br>
 * <br>
 *
 * The solution is simple: Catch the PRESSED and RELEASED events and decide for
 * yourself, if a CLICKED has happened. For HoDoKu a CLICKED event is generated,
 * if PRESSED and RELEASED occured on the same candidate.
 *
 * @author hobiwan
 */
public class SudokuPanel extends javax.swing.JPanel implements Printable {

	private static final long serialVersionUID = 1L;

	private static BufferedImage[] colorKuImagesSmall = new BufferedImage[Sudoku2.UNITS + 2];
	private static BufferedImage[] colorKuImagesLarge = new BufferedImage[Sudoku2.UNITS];
	private static final int DEFAULT_DOUBLE_CLICK_SPEED = 500;
	private static final int DELTA = 5;
	private static final int DELTA_RAND = 5;
	private static final int UNIT_HANDLE_SIZE = 20;
	private static final int[] KEY_CODES = new int[] {
		KeyEvent.VK_0, KeyEvent.VK_1, KeyEvent.VK_2,
		KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5,
		KeyEvent.VK_6, KeyEvent.VK_7, KeyEvent.VK_8,
		KeyEvent.VK_9
	};

	private ReasoningRequest selectedReasoningHintRequest;
    private SolutionStep selectedReasoningHintStep;
    private boolean selectedReasoningHintVerified;

	private enum ReasoningSourceKind {
		BOX,
		NONE,
		FREE_CHAIN
	}

	/** Immutable identity captured when Enter starts native reasoning analysis. */
	private static final class ReasoningRequest {
		private final long requestId;
		private final ReasoningSourceKind sourceKind;
		private final long sourceId;
		private final long boardRevision;
		private final long sourceRevision;
		private final long generatedOwnerRevision;
		private final String boardSignature;
		private final SudokuSet boxFootprint;
		private final UserChain chainFootprint;
        private String searchIdentity;
        private int matchCount;
        private boolean assumedRelations;

		private ReasoningRequest(long requestId, ReasoningSourceKind sourceKind,
				long sourceId, long boardRevision, long sourceRevision,
				long generatedOwnerRevision, String boardSignature,
				SudokuSet boxFootprint, UserChain chainFootprint) {
			this.requestId = requestId;
			this.sourceKind = sourceKind;
			this.sourceId = sourceId;
			this.boardRevision = boardRevision;
			this.sourceRevision = sourceRevision;
			this.generatedOwnerRevision = generatedOwnerRevision;
			this.boardSignature = boardSignature;
			this.boxFootprint = boxFootprint;
			this.chainFootprint = chainFootprint;
		}
	}

	/** Authorization token for the native step currently rendered as a preview. */
	private static final class ReasoningProposal {
		private final ReasoningRequest request;
		private final NativeReasoningMatcher.NativeStepKey stepKey;
		private final long displayedOwnerRevision;
		private boolean authoredChainProof;

		private ReasoningProposal(ReasoningRequest request,
				NativeReasoningMatcher.NativeStepKey stepKey,
				long displayedOwnerRevision) {
			this.request = request;
			this.stepKey = stepKey;
			this.displayedOwnerRevision = displayedOwnerRevision;
		}
	}
	
	private boolean showCandidates = Options.getInstance().isShowCandidates();
	private boolean showWrongValues = Options.getInstance().isShowWrongValues();
	private boolean showDeviations = Options.getInstance().isShowDeviations();
	private boolean invalidCells = Options.getInstance().isInvalidCells();
	private boolean showInvalidOrPossibleCells = false;
	private boolean[] showHintCellValues = new boolean[Sudoku2.UNITS + 1];
	private boolean showBivalueCells;
	private boolean showTrivalueCells;
	private boolean showAllCandidatesAkt = false;
	private boolean showAllCandidates = false;
	private int delta = DELTA;
	private int deltaRand = DELTA_RAND;
	private Font valueFont;
	private Font candidateFont;
	private int candidateHeight;
	private Font bigFont;
	private Font smallFont;
	private Sudoku2 sudoku;
	private SudokuSolver solver;
	private SudokuGenerator generator;
	private MainFrame mainFrame;
	private CellZoomPanel cellZoomPanel;
	private SolutionStep step;
	private int chainIndex = -1;
	private List<Integer> alsToShow = new ArrayList<Integer>();
	private Rectangle gridRegion = new Rectangle();
	private float strokeWidth = 1;
	private float boxStrokeWidth = 1;
	private int strokeWidthInt = 1;
	private int oldWidth;
	private int cellSize;
	private Graphics2D g2;
	private Polygon arrow = new Polygon();
	private Stroke arrowStroke = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private Stroke strongLinkStroke = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private Stroke weakLinkStroke = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, new float[] { 5.0f }, 0.0f);
	private List<Point2D.Double> points = new ArrayList<Point2D.Double>(200);
	private double arrowLengthFactor = 1.0 / 6.0;
	private double arrowHeightFactor = 1.0 / 3.0;
	private int shiftRow = -1;
	private int shiftCol = -1;
	private Stack<Sudoku2> undoStack = new Stack<Sudoku2>();
	private Stack<Sudoku2> redoStack = new Stack<Sudoku2>();
	private SortedMap<Integer, Color> coloringMap = new TreeMap<Integer, Color>();
	private SortedMap<Integer, Color> coloringCandidateMap = new TreeMap<Integer, Color>();
	private Map<Sudoku2, ColoringSnapshot> undoColoringStates = new IdentityHashMap<Sudoku2, ColoringSnapshot>();
	private Map<Sudoku2, ColoringSnapshot> redoColoringStates = new IdentityHashMap<Sudoku2, ColoringSnapshot>();
	private final Stack<ColoringSnapshot> coloringUndoStack = new Stack<ColoringSnapshot>();
	private final Stack<ColoringSnapshot> coloringRedoStack = new Stack<ColoringSnapshot>();
	private ArrayList<Integer> cellSelection = new ArrayList<Integer>();
	private boolean[] dragCellSelection = new boolean[82];
	private ProgressChecker progressChecker = null;
	private Timer deleteCursorTimer = new Timer(Options.getInstance().getDeleteCursorDisplayLength(), null);
	private long lastCursorChanged = -1;
	private RightClickMenu rightClickMenu = null;
	private boolean[] remainingCandidates = new boolean[Sudoku2.UNITS];
	
	// event meta data
	public int lastPressedRow = -1;
	public int lastPressedCol = -1;
	public int lastPressedCandidate = -1;
	private int lastClickedRow = -1;
	private int lastClickedCol = -1;
	private int lastClickedCandidate = -1;
	private long lastClickedTime = 0;
	private long doubleClickSpeed = -1;
	private Candidate lastCandidateMouseOn = new Candidate();
	private boolean isCtrlDown;
	private boolean deselectSelectionOnRelease;
	private int emptySelectionAnchor = Sudoku2.getIndex(4, 4);
	private SudokuTextReference transientReferenceHighlight;
	private final Set<Integer> techniquePreviewCells = new HashSet<Integer>();
	private SudokuTextReference.Kind hoveredUnitHandleKind;
	private int hoveredUnitHandle = -1;
	private boolean unitHandlePressed;
	private SudokuTextReference.Kind keyboardUnitHighlightKind;
	private int keyboardUnitHighlightDigitKey = KeyEvent.VK_UNDEFINED;

	private static final class ColoringSnapshot {
		private final SortedMap<Integer, Color> cells;
		private final SortedMap<Integer, Color> candidates;

		private ColoringSnapshot(SortedMap<Integer, Color> cells,
				SortedMap<Integer, Color> candidates) {
			this.cells = new TreeMap<Integer, Color>(cells);
			this.candidates = new TreeMap<Integer, Color>(candidates);
		}
	}
	private Point lastMousePosition = new Point();
	private int lastHighlightedDigit = 0;
	private boolean isColoringVisible = true;
	private AnnotationTool annotationTool = AnnotationTool.DEFAULT_MOUSE;
	private AnnotationTool stickyAnnotationTool = AnnotationTool.DEFAULT_MOUSE;
	private AnnotationTool lastColoringTool = AnnotationTool.CANDIDATE_COLORING;
	private int activeAnnotationToolKey = KeyEvent.VK_UNDEFINED;
	private long activeAnnotationToolPressedAt;
	private AnnotationTool activeAnnotationToolTarget;
	private AnnotationTool annotationToolBeforeKeyGesture;
	private boolean annotationToolGestureUsedBoard;
	private boolean activeAnnotationToolDoubleTap;
	private AnnotationTool activeAnnotationToolDoubleTapOrigin;
	private int pendingAnnotationToolTapKey = KeyEvent.VK_UNDEFINED;
	private long pendingAnnotationToolTapReleasedAt;
	private AnnotationTool pendingAnnotationToolTapOrigin;
	private AnnotationTool pendingAnnotationToolTapTarget;
	private String pendingAnnotationToolTapBoardSignature;
	private final Map<Integer, AnnotationTool> settledAnnotationToolOrigins =
			new TreeMap<Integer, AnnotationTool>();
	private boolean activeAnnotationToolToggleBack;
	private AnnotationTool activeAnnotationToolToggleOrigin;
	private boolean applyingAnnotationTool;
	private boolean annotationToolPointerCaptured;
	private AnnotationTool pendingAnnotationToolRestore;
	private boolean suppressNextAnnotationPointerRelease;
	private static final long ANNOTATION_TOOL_HOLD_MILLIS = 250L;
	private static final long ANNOTATION_TOOL_DOUBLE_TAP_MILLIS = 300L;
	private final List<DoodleStroke> doodleStrokes = new ArrayList<DoodleStroke>();
	private final Stack<List<DoodleStroke>> doodleUndoStack = new Stack<List<DoodleStroke>>();
	private final Stack<List<DoodleStroke>> doodleRedoStack = new Stack<List<DoodleStroke>>();
	private DoodleStroke activeDoodleStroke;
    private enum DoodleGesture { NONE, PEN, ELLIPSE, CIRCLE_ERASER, RECT_ERASER }
    private DoodleGesture doodleGesture = DoodleGesture.NONE;
    private Point doodleGestureStart, doodleGestureCurrent;
    private List<DoodleStroke> doodleErasePreview;
    private float doodleEraserRadius = 0.025f;
    private boolean deletionModifierDown;
    private Cursor annotationCursorBeforeDelete;
    private Point chainDeleteStart, chainDeleteCurrent;
    private Point chainFlipStart, chainFlipCurrent;
    private int preciseChainCandidate = -1;
    private boolean preciseChainDelete;
    private boolean chainRightDelete, chainRightDragged;
    private final AnnotationTimeline annotationTimeline = new AnnotationTimeline();
    private int annotationPaintLayer;


    private Point coloringPress, coloringCurrent;
    private boolean coloringErase, coloringRight;
    private Color coloringGestureColor;
    private boolean boxRightToggle, boxRightDragged;
    private boolean doodleDragged;
    private double lastUserChainRouteDiameter;
	private float doodleWidthFactor = 0.006f;
	private final List<UserChain> userChains = new ArrayList<UserChain>();
	private final Stack<List<UserChain>> userChainUndoStack = new Stack<List<UserChain>>();
	private final Stack<List<UserChain>> userChainRedoStack = new Stack<List<UserChain>>();
	private UserChain activeUserChain;
	private boolean nextUserChainStrong = true;
    private static final float BACKGROUND_ANNOTATION_ALPHA = 0.12f;
	private final List<SudokuSet> boxReasoningGroups = createEmptyBoxReasoningGroups();
	private final Stack<List<SudokuSet>> boxReasoningUndoStack = new Stack<List<SudokuSet>>();
	private final Stack<List<SudokuSet>> boxReasoningRedoStack = new Stack<List<SudokuSet>>();
	private int activeBoxReasoningGroup;
	private Point boxDragStart;
	private Point boxDragCurrent;
	private boolean boxDragRemoves;
	private int boxDragGroup;
	private SudokuSet boxDragPreview;
	private boolean boxReasoningVisible = true;
	private long boxReasoningRevision;
	private long userChainReasoningRevision;
	private long nextUserChainSourceId = 1L;
	private long confirmedUserChainSourceId;
	private long reasoningBoardRevision;
    private final java.util.Set<String> invalidUserChainRelations = new java.util.HashSet<String>();
	private long generatedStepOwnerRevision;
	private long nextReasoningRequestId = 1L;
	private ReasoningRequest reasoningRequest;
	private ReasoningProposal reasoningProposal;
	private Thread reasoningWorker;
	private boolean applyingReasoningProposal;
	private boolean internalReasoningStepChange;
	private boolean executingReasoningStep;
	private String lastNoMatchIdentity;
	private boolean doodlesVisible = true;
	private boolean userChainsVisible = true;
	private static final float[] DOODLE_WIDTH_FACTORS = { 0.0035f, 0.0050f, 0.0070f, 0.0095f };
	private int doodleWidthIndex = 1;
	private long lastDoodleWheelChange;
	private static final int ANNOTATION_UNDO_LIMIT = 100;

	/**
	 * Creates new form SudokuPanel
	 * @param mf
	 */
	public SudokuPanel(MainFrame mf) {

		mainFrame = mf;
		sudoku = new Sudoku2();
		sudoku.clearSudoku();
		setShowCandidates(Options.getInstance().isShowCandidates());
		generator = SudokuGeneratorFactory.getDefaultGeneratorInstance();
		solver = SudokuSolverFactory.getDefaultSolverInstance();
		solver.setSudoku(sudoku.clone());
		solver.solve();
		progressChecker = new ProgressChecker(mainFrame);
		isCtrlDown = false;
		setActiveCell(4, 4);
		//cellSelection.add(new Integer(Sudoku2.getIndex(4, 4)));
		
		clearDragSelection();
		deselectSelectionOnRelease = false;
		initComponents();
		
		rightClickMenu = new RightClickMenu(mf, this);
		rightClickMenu.setColorIconsInPopupMenu();
		updateCellZoomPanel();
		calculateGridRegion(getBounds(), false, false);
		
		deleteCursorTimer.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteCursorTimer.stop();
				lastCursorChanged = System.currentTimeMillis() - Options.getInstance().getDeleteCursorDisplayLength() - 100;
				repaint();
			}
		});

		Object cs = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
		if (cs instanceof Integer) {
			doubleClickSpeed = ((Integer) cs).intValue();
		}

		if (doubleClickSpeed == -1) {
			doubleClickSpeed = DEFAULT_DOUBLE_CLICK_SPEED;
		}
	}

	private void clearDragSelection() {
		Arrays.fill(dragCellSelection, false);
	}
	
	public int getFirstRow() {
		
		if (cellSelection.isEmpty()) {
			return -1;
		}
		
		int index = cellSelection.get(0);
		return Sudoku2.getRow(index);
	}
	
	public int getFirstCol() {
		
		if (cellSelection.isEmpty()) {
			return -1;
		}
		
		int index = cellSelection.get(0);
		return Sudoku2.getCol(index);
	}
	
	public int getCellSelectionSize() {
		return cellSelection.size();
	}
	
	public int getActiveRow() {
		
		if (cellSelection.isEmpty()) {
			return -1;
		}
		
		int index = cellSelection.get(cellSelection.size()-1);
		return Sudoku2.getRow(index);
	}
	
	public int getActiveCol() {
		
		if (cellSelection.isEmpty()) {
			return -1;
		}
		
		int index = cellSelection.get(cellSelection.size()-1);
		return Sudoku2.getCol(index);
	}
	
	public Integer getActiveCell() {
		
		if (cellSelection.isEmpty()) {
			return null;
		}
		
		return Integer.valueOf(Sudoku2.getIndex(getActiveRow(), getActiveCol()));
	}
	
	public void setActiveCell(int index) {
		if (!Sudoku2.isValidIndex(Sudoku2.getRow(index), Sudoku2.getCol(index))) {
			return;
		}
		emptySelectionAnchor = index;
		
		Integer intObj = Integer.valueOf(index);

        if (Options.getInstance().isDeleteCursorDisplay()) {
        	if (cellZoomPanel != null && !cellZoomPanel.isColoring()) {
                deleteCursorTimer.stop();
                lastCursorChanged = System.currentTimeMillis();
                deleteCursorTimer.setDelay(Options.getInstance().getDeleteCursorDisplayLength());
                deleteCursorTimer.setInitialDelay(Options.getInstance().getDeleteCursorDisplayLength());
                deleteCursorTimer.start();	
        	}
        }

		if (cellSelection.contains(intObj)) {
			cellSelection.remove(intObj);
		}
		
		cellSelection.add(intObj);
		mainFrame.updateCellSelectionStatus();
	}
	
	public void setActiveCell(int row, int col) {		
		if (!Sudoku2.isValidIndex(row, col)) {
			return;
		}
		setActiveCell(Sudoku2.getIndex(row, col));
	}
	
	public void clearSelection(int lastCell) {
		cellSelection.clear();
		//cellSelection.add(new Integer(lastCell));
		setActiveCell(lastCell);
	}
	
	public void clearSelection(int row, int col) {
		clearSelection(Sudoku2.getIndex(row, col));
	}
	
	public void clearSelection() {
		if (cellSelection.isEmpty()) {
			return;
		}
		
		Integer lastCell = cellSelection.get(cellSelection.size()-1);
		cellSelection.clear();
		//cellSelection.add(lastCell);
		setActiveCell(lastCell);
	}

	/** Clears the cursor completely; used only for a repeated plain cell click. */
	private void clearActiveSelection() {
		if (!cellSelection.isEmpty()) {
			emptySelectionAnchor = cellSelection.get(cellSelection.size() - 1).intValue();
		}
		cellSelection.clear();
		mainFrame.updateCellSelectionStatus();
	}

	/** Clears the visible selection while retaining a safe navigation anchor. */
	public void clearAllCellSelection() {
		clearActiveSelection();
		shiftRow = -1;
		shiftCol = -1;
		updateCellZoomPanel();
	}

	private boolean hasActiveCell() {
		return !cellSelection.isEmpty();
	}

	private boolean isNavigationKey(int keyCode) {
		return keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_DOWN
				|| keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_RIGHT
				|| keyCode == KeyEvent.VK_HOME || keyCode == KeyEvent.VK_END;
	}

	private boolean restoreEmptySelectionForNavigation() {
		int anchor = emptySelectionAnchor;
		if (!Sudoku2.isValidIndex(Sudoku2.getRow(anchor), Sudoku2.getCol(anchor))) {
			anchor = Sudoku2.getIndex(4, 4);
		}
		setActiveCell(anchor);
		updateCellZoomPanel();
		mainFrame.check();
		repaint();
		return true;
	}
	
	public void resetKeyState() {
		isCtrlDown = false;
		resetKeyboardUnitHighlight();
		cancelAnnotationToolKeyGesture();
	}

	void cancelAnnotationToolInteractionOnDeactivation() {
		if (annotationToolPointerCaptured || boxDragStart != null || activeDoodleStroke != null) {
			suppressNextAnnotationPointerRelease = true;
		}
		clearBoxReasoningDragState();
        chainDeleteStart = chainDeleteCurrent = null;
        chainFlipStart=chainFlipCurrent=null;clearPreciseChainGesture();
        coloringPress=coloringCurrent=null;
        deletionModifierDown=false;updateDeletionCursor();
		cancelDoodleGesture();
		annotationToolPointerCaptured = false;
		if (pendingAnnotationToolRestore != null) {
			AnnotationTool restore = pendingAnnotationToolRestore;
			pendingAnnotationToolRestore = null;
			stickyAnnotationTool = restore;
			applyAnnotationTool(restore);
		} else {
			cancelAnnotationToolKeyGesture();
		}
		clearPendingAnnotationToolTap();
		repaint();
	}

	private void initComponents() {

		rightClickMenu = new RightClickMenu(this.mainFrame, this);

		setFocusTraversalKeys(java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, java.util.Collections.emptySet());
        setBackground(new java.awt.Color(255, 255, 255));
		setMinimumSize(new java.awt.Dimension(300, 300));
		setPreferredSize(new java.awt.Dimension(600, 600));
		addMouseListener(new java.awt.event.MouseListener() {

			@Override
			public void mouseClicked(java.awt.event.MouseEvent evt) {}

			@Override
			public void mousePressed(java.awt.event.MouseEvent evt) {
				// If a canceled gesture released outside the window, its one-shot guard
				// must not consume the next complete pointer sequence.
				suppressNextAnnotationPointerRelease = false;
                updateDeletionPointer(evt);
                if (beginColoringGesture(evt)) return;
                if (beginPreciseChainGesture(evt)) return;
                if (beginChainDeleteDrag(evt)) return;
				if (beginBoxReasoningDrag(evt)) {
					return;
				}
				if (handleFreeChainMousePressed(evt)) {
					return;
				}
				if (beginDoodle(evt)) {
					return;
				}
				if (annotationTool == AnnotationTool.DEFAULT_MOUSE && isOnGrid(evt.getPoint())) {
					noteAnnotationToolBoardAction(true);
				} else if ((annotationTool == AnnotationTool.CANDIDATE_COLORING
						|| annotationTool == AnnotationTool.CELL_COLORING)
						&& SwingUtilities.isLeftMouseButton(evt) && isOnGrid(evt.getPoint())) {
					noteAnnotationToolBoardAction(true);
				}
				if (beginUnitHandleHighlight(evt)) {
					return;
				}
				onMouseDown(evt);
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent evt) {
				if (suppressNextAnnotationPointerRelease) {
					suppressNextAnnotationPointerRelease = false;
					finishAnnotationToolPointerGesture();
					return;
				}
				if (finishColoringGesture(evt)) { finishAnnotationToolPointerGesture(); return; }
                if (finishChainFlipDrag(evt)) { finishAnnotationToolPointerGesture(); return; }
                if (finishChainDeleteDrag(evt)) { finishAnnotationToolPointerGesture(); return; }
                if (finishBoxReasoningDrag(evt)) {
					finishAnnotationToolPointerGesture();
					return;
				}
				if (finishDoodle(evt)) {
					finishAnnotationToolPointerGesture();
					return;
				}
				if (annotationTool == AnnotationTool.DOODLE) {
					return;
				}
				if (annotationTool == AnnotationTool.FREE_CHAIN) {
					// The chain was handled on press.  Do not let release enter the
					// legacy single/double-click board editor.
					finishAnnotationToolPointerGesture();
					return;
				}
				if (unitHandlePressed) {
					unitHandlePressed = false;
					clearTransientReferenceHighlight();
					return;
				}
				onMouseUp(evt);
				finishAnnotationToolPointerGesture();
			}

			@Override
			public void mouseEntered(MouseEvent evt) {
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent evt) {
                lastMousePosition = null;
				lastCandidateMouseOn = null;
				hoveredUnitHandleKind = null;
				hoveredUnitHandle = -1;
				if (unitHandlePressed) {
					unitHandlePressed = false;
					clearTransientReferenceHighlight();
				}
				repaint();
			}
		});

		addMouseMotionListener(new java.awt.event.MouseMotionListener() {

			@Override
			public void mouseDragged(MouseEvent e) {
                updateDeletionPointer(e);
                if(coloringPress!=null){coloringCurrent=e.getPoint();repaint();return;}
                if (chainFlipStart != null) {if(chainFlipStart.distance(e.getPoint())>=4)chainRightDragged=true;chainFlipCurrent=e.getPoint();repaint();return;}
                if (chainDeleteStart != null) {chainDeleteCurrent=e.getPoint();repaint();return;}
				if (extendBoxReasoningDrag(e)) {
					return;
				}
				if (extendDoodle(e)) {
					return;
				}
				if (annotationTool == AnnotationTool.FREE_CHAIN) {
					// Free Chain owns the complete left-button gesture.  Even a tiny
					// pointer wobble must not fall through to legacy multi-cell selection.
					return;
				}
				if (unitHandlePressed) {
					return;
				}

				lastMousePosition = e.getPoint();

				int row = getRow(e.getPoint());
				int col = getCol(e.getPoint());
				int index = Sudoku2.getIndex(row, col);

				updateCandidateMouseHighlight(e.getPoint());

				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				deselectSelectionOnRelease = false;
				
				if (!Sudoku2.isValidIndex(row, col)) {
					return;
				}

				if (cellZoomPanel.isColoring()) {
					return;
				}

				if (!dragCellSelection[index]) {

					dragCellSelection[index] = true;
					if (cellSelection.contains(Integer.valueOf(index))) {						
						cellSelection.remove(Integer.valueOf(index));					
					} else {						
						cellSelection.add(Integer.valueOf(index));
					}
				}
				
				setActiveCell(row, col);
				repaint();
			}

			@Override
			public void mouseMoved(MouseEvent e) {
                updateDeletionPointer(e);
				lastMousePosition = e.getPoint();
				updateHoveredUnitHandle(e.getPoint());
				updateCandidateMouseHighlight(e.getPoint());
			}
		});

		addMouseWheelListener(new java.awt.event.MouseWheelListener() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent evt) {
				int modifiers = evt.getModifiersEx();
				int relevant = KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK
						| KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK
						| KeyEvent.ALT_GRAPH_DOWN_MASK;
				if (annotationTool == AnnotationTool.DOODLE && SudokuUtil.isDeletionModifierDown(evt)
                        && (modifiers & (relevant & ~SudokuUtil.getDeletionModifierMask())) == 0) {
                    doodleEraserRadius = Math.max(0.006f, Math.min(0.20f,
                            doodleEraserRadius + Integer.signum(evt.getWheelRotation()) * 0.005f));
                    updateDeletionPointer(evt); evt.consume(); repaint();
                } else if (annotationTool == AnnotationTool.DOODLE
						&& (modifiers & relevant) == 0) {
					cycleDoodleWidth(evt.getWheelRotation());
					evt.consume();
				} else if ((cellZoomPanel.isColoring() || annotationTool == AnnotationTool.FREE_CHAIN
						|| annotationTool == AnnotationTool.BOX_SELECTION)
						&& (modifiers & (relevant & ~KeyEvent.ALT_DOWN_MASK)) == 0) {
					cellZoomPanel.cyclePaletteColor(evt.getWheelRotation(),
							(modifiers & KeyEvent.ALT_DOWN_MASK) != 0);
					evt.consume();
				}
			}
		});

		addKeyListener(new java.awt.event.KeyAdapter() {

			@Override
			public void keyPressed(java.awt.event.KeyEvent evt) {
				onKeyPressed(evt);
			}

			@Override
			public void keyReleased(java.awt.event.KeyEvent evt) {
				onKeyReleased(evt);
			}
		});

		javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
		this.setLayout(layout);

		layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 600, Short.MAX_VALUE));

		layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 600, Short.MAX_VALUE));
	}
	
	private Rectangle calculateGridRegion(Rectangle bounds, boolean isPrint, boolean withBorder) {
		
		// determine the actual size of the quad
		int handleInset = isPrint ? 0 : UNIT_HANDLE_SIZE;
		int availableWidth = Math.max(1, bounds.width - handleInset);
		int availableHeight = Math.max(1, bounds.height - handleInset);
		int width = Math.min(availableHeight, availableWidth);
		int height = width;
		
		// make the size of the lines larger, especially for high res printing
		this.strokeWidth = 2.0f / 1000.0f * width;
		if (width > 1000) {
			this.strokeWidth *= 1.5f;
		}

		this.boxStrokeWidth = (float) (this.strokeWidth * Options.getInstance().getBoxLineFactor());
		this.strokeWidthInt = Math.round(this.boxStrokeWidth / 2);
		
		this.delta = bounds.width / 100;
		this.deltaRand = bounds.width / 100;
		
		if (this.deltaRand < this.strokeWidthInt) {
			this.deltaRand = this.strokeWidthInt;
		}

		if (Options.getInstance().getDrawMode() == 1) {
			this.delta = 0;
		}
		
		// calculate the size of the cells and adjust for rounding errors
		this.cellSize = (width - 4 * this.delta - 2 * this.deltaRand) / Sudoku2.UNITS;

		width = height = this.cellSize * Sudoku2.UNITS + 4 * this.delta;
		
		int sx = handleInset + (availableWidth - width) / 2;
		int sy = handleInset + (availableHeight - height) / 2;
		
		if (isPrint && withBorder) {
			sy = 0;
		}
		
		return new Rectangle(sx, sy, width, height);
	}

	private boolean beginUnitHandleHighlight(MouseEvent event) {
		if (!SwingUtilities.isLeftMouseButton(event)) {
			return false;
		}
		Point point = event.getPoint();
		int column = columnHandleAt(point);
		if (column != -1) {
			unitHandlePressed = true;
			setTransientReferenceHighlight(SudokuReferenceParser.parse("c" + (column + 1)).get(0));
			return true;
		}
		int row = rowHandleAt(point);
		if (row != -1) {
			unitHandlePressed = true;
			setTransientReferenceHighlight(SudokuReferenceParser.parse("r" + (row + 1)).get(0));
			return true;
		}
		return false;
	}

	private void updateHoveredUnitHandle(Point point) {
		SudokuTextReference.Kind oldKind = hoveredUnitHandleKind;
		int oldHandle = hoveredUnitHandle;
		int column = columnHandleAt(point);
		if (column != -1) {
			hoveredUnitHandleKind = SudokuTextReference.Kind.COLUMNS;
			hoveredUnitHandle = column;
		} else {
			int row = rowHandleAt(point);
			if (row != -1) {
				hoveredUnitHandleKind = SudokuTextReference.Kind.ROWS;
				hoveredUnitHandle = row;
			} else {
				hoveredUnitHandleKind = null;
				hoveredUnitHandle = -1;
			}
		}
		if (oldKind != hoveredUnitHandleKind || oldHandle != hoveredUnitHandle) {
			repaint();
		}
	}

	private int columnHandleAt(Point point) {
		if (point.y < gridRegion.y - UNIT_HANDLE_SIZE || point.y >= gridRegion.y
				|| point.x < gridRegion.x || point.x >= gridRegion.x + gridRegion.width) {
			return -1;
		}
		return getCol(point);
	}

	private int rowHandleAt(Point point) {
		if (point.x < gridRegion.x - UNIT_HANDLE_SIZE || point.x >= gridRegion.x
				|| point.y < gridRegion.y || point.y >= gridRegion.y + gridRegion.height) {
			return -1;
		}
		return getRow(point);
	}
	
	public boolean isOnGrid(Point point) {
		
		return 
			point.x >= this.gridRegion.x &&
			point.x < (this.gridRegion.x + this.gridRegion.width) &&
			point.y >= this.gridRegion.y &&
			point.y < (this.gridRegion.y + this.gridRegion.height);
			
	}

	private void updateCandidateMouseHighlight(Point mouse) {

		if (showCandidateHighlight()) {

			int row = getRow(mouse);
			int col = getCol(mouse);
			int candidate = getCandidate(mouse, row, col);
			int index = Sudoku2.getIndex(row, col);

			if (Sudoku2.isValidIndex(row, col)) {

				Candidate mouseOn = new Candidate(index, candidate);
				if (lastCandidateMouseOn != mouseOn) {
					lastCandidateMouseOn = mouseOn;
					repaint();
				}

			} else {
				if (lastCandidateMouseOn != null) {
					lastCandidateMouseOn = null;
					repaint();
				}
			}
		}
	}
	
	private void updateAutoHighlight(int row, int col) {
		
		if (Options.getInstance().isAutoHighlighting()) {
			int value = sudoku.getValue(row, col);
			
			if (value != 0 && value != lastHighlightedDigit) {
				setShowHintCellValue(value);
				setShowInvalidOrPossibleCells(true);
				lastHighlightedDigit = value;
			} else {/*
				resetShowHintCellValues();
				setShowInvalidOrPossibleCells(false);
				lastHighlightedDigit = 0;*/
			}
		}
	}

	private void onKeyReleased(java.awt.event.KeyEvent evt) {
        updateDeletionModifier(evt);
		if (handleAnnotationToolKeyReleased(evt)) {
			return;
		}
		handleKeysReleased(evt);
		updateCellZoomPanel();
		mainFrame.fixFocus();
	}

	private void onKeyPressed(java.awt.event.KeyEvent evt) {
        if(evt.getKeyCode()==KeyEvent.VK_TAB && evt.getModifiersEx()==0){mainFrame.showCurrentReasoning(true);evt.consume();return;}
		if (evt.getKeyCode() == KeyEvent.VK_ESCAPE) {
			handleEscapeVisualReset();
			updateCellZoomPanel();
			mainFrame.fixFocus();
			return;
		}
		if (handleAnnotationKeyPressed(evt)) {
			return;
		}

		int keyCode = evt.getKeyCode();
		switch (keyCode) {
		default:
			handleKeys(evt);
		}

		updateCellZoomPanel();
		mainFrame.fixFocus();
	}

	boolean handleAnnotationKeyPressed(KeyEvent event) {
        updateDeletionModifier(event);
		int modifiers = event.getModifiersEx();
		int allModifiers = KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK | KeyEvent.META_DOWN_MASK
				| KeyEvent.ALT_DOWN_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK;
		if (!isUnmodifiedAnnotationToolShortcut(event)) {
			clearPendingAnnotationToolTap();
		}
		if (event.getKeyCode() == KeyEvent.VK_ENTER && modifiers == 0
				&& (annotationTool != AnnotationTool.DEFAULT_MOUSE || reasoningProposal != null || reasoningRequest != null || applyingReasoningProposal)) {
			handleReasoningEnter();
			return true;
		}
		if (event.getKeyCode() == KeyEvent.VK_R && modifiers == 0) {
			clearAllAnnotationsWithUndo();
			return true;
		}
		if (event.getKeyCode() == KeyEvent.VK_R && modifiers == KeyEvent.SHIFT_DOWN_MASK) {
			clearCurrentAnnotationWithUndo();
			return true;
		}
		if (annotationTool != AnnotationTool.DEFAULT_MOUSE && SudokuUtil.isMenuShortcutDown(event)
				&& event.getKeyCode() == KeyEvent.VK_Z
				&& (modifiers & KeyEvent.SHIFT_DOWN_MASK) == 0) {
			undoCurrentAnnotation();
			return true;
		}
		if (annotationTool != AnnotationTool.DEFAULT_MOUSE && SudokuUtil.isMenuShortcutDown(event)
				&& ((event.getKeyCode() == KeyEvent.VK_Y
						&& (modifiers & KeyEvent.SHIFT_DOWN_MASK) == 0)
						|| (event.getKeyCode() == KeyEvent.VK_Z
						&& (modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0))) {
			redoCurrentAnnotation();
			return true;
		}
        if ((annotationTool == AnnotationTool.CANDIDATE_COLORING || annotationTool == AnnotationTool.CELL_COLORING)
                && event.getKeyCode() >= KeyEvent.VK_A && event.getKeyCode() <= KeyEvent.VK_D
                && modifiers == 0) {
            cellZoomPanel.selectPaletteGroup(event.getKeyCode() - KeyEvent.VK_A);
            event.consume();
            return true;
        }
		if (annotationTool == AnnotationTool.DOODLE) {
			int keyCode = event.getKeyCode();
			int colorModifiers = modifiers & ~(KeyEvent.SHIFT_DOWN_MASK);
			if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_D && colorModifiers == 0) {
				int colorIndex = (keyCode - KeyEvent.VK_A) * 2
						+ ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 ? 1 : 0);
				if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0)
                    cellZoomPanel.setPrimaryColor(Options.getInstance().getColoringColors()[colorIndex]);
                else cellZoomPanel.selectPaletteGroup(keyCode - KeyEvent.VK_A);
				cellZoomPanel.repaintSharedColorControls();
				return true;
			}
			char keyChar = event.getKeyChar();
			if ((keyChar == '>' || (keyCode == KeyEvent.VK_PERIOD
					&& modifiers == KeyEvent.SHIFT_DOWN_MASK))) {
				adjustDoodleWidth(1);
				return true;
			}
			if ((keyChar == '<' || (keyCode == KeyEvent.VK_COMMA
					&& modifiers == KeyEvent.SHIFT_DOWN_MASK))) {
				adjustDoodleWidth(-1);
				return true;
			}
		}
		if ((annotationTool == AnnotationTool.FREE_CHAIN
				|| annotationTool == AnnotationTool.BOX_SELECTION)
				&& event.getKeyCode() >= KeyEvent.VK_A && event.getKeyCode() <= KeyEvent.VK_D
				&& modifiers == 0) {
			int group = event.getKeyCode() - KeyEvent.VK_A;
			cellZoomPanel.selectPaletteGroup(group);
			return true;
		}
		if (annotationTool != AnnotationTool.DEFAULT_MOUSE
				&& annotationTool != AnnotationTool.CANDIDATE_COLORING
				&& annotationTool != AnnotationTool.CELL_COLORING
				&& event.getKeyCode() >= KeyEvent.VK_A && event.getKeyCode() <= KeyEvent.VK_E
				&& (modifiers & ~(KeyEvent.SHIFT_DOWN_MASK)) == 0) {
			// A-E are legacy coloring shortcuts. Doodle handles A-D above; the
			// remaining combinations must not color selected Sudoku cells while a
			// different annotation pointer tool owns the board.
			return true;
		}
		if (annotationTool == AnnotationTool.FREE_CHAIN) {
			if (event.getKeyCode() == KeyEvent.VK_BACK_SPACE && modifiers == 0) {
				removeLastUserChainNode();
				return true;
			}
			if (event.getKeyCode() == KeyEvent.VK_SPACE && modifiers == 0) {
				toggleNextUserChainStrong();
				return true;
			}
		}
		if (annotationTool != AnnotationTool.DEFAULT_MOUSE && isBoardEditingKey(event)) {
			// Candidate-filter shortcuts remain global tools in every pointer mode.  All
			// other digit/space/delete/enter variants belong to board editing and must
			// not leak through to the legacy key handler while annotating.
			if (isMacCandidateFilterShortcut(event)) {
				return false;
			}
			return true;
		}
		if ((modifiers & allModifiers) != 0) {
			return false;
		}
		if (handleAnnotationToolKeyPressed(event)) {
			return true;
		}
		return annotationTool != AnnotationTool.DEFAULT_MOUSE && isBoardEditingKey(event);
	}

	/**
	 * Applies the documented non-destructive Escape layers regardless of which
	 * control in the main window currently owns keyboard focus.
	 */
	boolean handleEscapeVisualReset() {
		clearPendingAnnotationToolTap();
		if (handleAnnotationEscape()) return true;
		if (cellZoomPanel.isColoring()) {
			mainFrame.setColoring(null, false);
			return true;
		}
		if (step != null) {
			mainFrame.abortStep();
			return true;
		}
		resetShowHintCellValues();
		clearAllCellSelection();
		lastCandidateMouseOn = null;
		lastHighlightedDigit = 0;
		mainFrame.check();
		repaint();
		return true;
	}

	private boolean handleAnnotationEscape() {
        if(coloringPress!=null){coloringPress=coloringCurrent=null;suppressNextAnnotationPointerRelease=true;finishAnnotationToolPointerGesture();repaint();return true;}
        if(chainFlipStart!=null){chainFlipStart=chainFlipCurrent=null;finishAnnotationToolPointerGesture();repaint();return true;}
        if(chainDeleteStart!=null){chainDeleteStart=chainDeleteCurrent=null;finishAnnotationToolPointerGesture();repaint();return true;}
		if (reasoningRequest != null || reasoningProposal != null || applyingReasoningProposal) {
			cancelReasoningState(true, "MainFrame.reasoning.canceled");
			return true;
		}
		if (boxDragStart != null) {
			suppressNextAnnotationPointerRelease |= annotationToolPointerCaptured;
			cancelBoxReasoningDrag();
			mainFrame.announceReasoningStatus("MainFrame.reasoning.boxCanceled");
			return true;
		}
		if (doodleGesture != DoodleGesture.NONE) {
			suppressNextAnnotationPointerRelease |= annotationToolPointerCaptured;
			cancelDoodleGesture();
			finishAnnotationToolPointerGesture();
			repaint();
			return true;
		}
		if (activeUserChain != null) {
			suppressNextAnnotationPointerRelease |= annotationToolPointerCaptured;
			pushUserChainUndo();
			activeUserChain = null;
			setNextUserChainStrong(true);
			noteUserChainReasoningChanged();
			finishAnnotationToolPointerGesture();
			mainFrame.check();
			repaint();
			return true;
		}
		if (annotationTool != AnnotationTool.DEFAULT_MOUSE) {
			setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
			return true;
		}
		return false;
	}

	/** Resolves the two-stage Analyze/Apply command for non-Default pointer tools. */
	private void handleReasoningEnter() {
		if (confirmReasoningProposal()) return;
		if (step != null) {
			// An ordinary F12/selector detail keeps its established Enter-to-apply
			// command. It can never manufacture a reasoning authorization token.
			mainFrame.executeDisplayedStepFromKeyboard();
			return;
		}
        // An unrendered menu choice never owns Enter in the two drawing tools.
        if(selectedReasoningHintRequest!=null && annotationTool!=AnnotationTool.FREE_CHAIN
                && annotationTool!=AnnotationTool.BOX_SELECTION) {
            if(selectedReasoningHintRequest.searchIdentity!=null
                    && selectedReasoningHintRequest.searchIdentity.equals(currentReasoningInputIdentity())) {
                mainFrame.announceReasoningStatus("MainFrame.currentReasoning.selectedHint");return;
            }
            clearSelectedReasoningHint();
        }
        if (annotationTool == AnnotationTool.FREE_CHAIN) {
            UserChainAssembly.Result assembled=UserChainAssembly.assemble(currentReasoningChains());
            if(assembled.chain==null) {
                mainFrame.announceChainValidation(new UserChainValidator.Result(
                        assembled.problem==UserChainValidator.Problem.NONE?UserChainValidator.Status.INCOMPLETE:UserChainValidator.Status.INVALID,
                        -1,new ArrayList<SolutionStep>(),assembled.problem));return;
            }
            if(activeUserChain!=null) finishActiveUserChain(activeUserChain.isClosed(),true,true);
            startReasoningAnalysis(ReasoningSourceKind.FREE_CHAIN,-1L,null,assembled.chain);return;
        }

		if (annotationTool == AnnotationTool.BOX_SELECTION) {
			SudokuSet footprint = getBoxReasoningFootprint();
			if (!footprint.isEmpty()) {
				startReasoningAnalysis(ReasoningSourceKind.BOX, 0L, footprint, null);
				return;
			}
		}
		mainFrame.announceReasoningStatus("MainFrame.reasoning.noInput");
	}

	private UserChain findConfirmedUserChain() {
		if (confirmedUserChainSourceId == 0L) return null;
		for (int i = userChains.size() - 1; i >= 0; i--) {
			UserChain chain = userChains.get(i);
			if (chain.getSourceId() == confirmedUserChainSourceId) return chain;
		}
		return null;
	}

    void clearSelectedReasoningHint() {
        selectedReasoningHintRequest = null; selectedReasoningHintStep = null;selectedReasoningHintVerified=false;
    }
    private void rememberReasoningHint(ReasoningRequest request, SolutionStep selected) {
        selectedReasoningHintRequest = request; selectedReasoningHintStep = (SolutionStep) selected.clone();selectedReasoningHintVerified=false;
    }
    void selectReasoningHint(SolutionStep selected,SudokuSet boxes,UserChain chain,boolean verified) {
        cancelReasoningState(true,null);
        if(step!=null)mainFrame.setSolutionStep(null,true);
        ReasoningSourceKind kind=chain!=null?ReasoningSourceKind.FREE_CHAIN:boxes!=null?ReasoningSourceKind.BOX:ReasoningSourceKind.NONE;
        ReasoningRequest request=new ReasoningRequest(nextReasoningRequestId++,kind,chain==null?0:chain.getSourceId(),
                reasoningBoardRevision,reasoningSourceRevision(kind),generatedStepOwnerRevision,
                TechniqueStepCatalog.createSignature(sudoku),boxes==null?null:boxes.clone(),chain==null?null:copyUserChain(chain));
        request.searchIdentity=currentReasoningInputIdentity();
        rememberReasoningHint(request,selected);selectedReasoningHintVerified=verified;
    }
    boolean showSelectedReasoningHint(int mode) {
        ReasoningRequest request = selectedReasoningHintRequest;
        if (request == null || selectedReasoningHintStep == null) return false;
        if(mode==2 && step!=null && request.boardRevision==reasoningBoardRevision
                && request.boardSignature.equals(TechniqueStepCatalog.createSignature(sudoku))
                && ReasoningStepIndex.identity(step).equals(ReasoningStepIndex.identity(selectedReasoningHintStep))) {
            mainFrame.fixFocus();return true;
        }
        if (request.boardRevision != reasoningBoardRevision
                || !request.boardSignature.equals(TechniqueStepCatalog.createSignature(sudoku))
                || request.sourceRevision != reasoningSourceRevision(request.sourceKind)
                || !reasoningSourceStillExists(request)
                || request.searchIdentity != null && !request.searchIdentity.equals(currentReasoningInputIdentity())) {
            clearSelectedReasoningHint();
            mainFrame.announceReasoningStatus("MainFrame.reasoning.invalidated");
            return true;
        }
        SolutionStep selected = (SolutionStep) selectedReasoningHintStep.clone();
        if (mode == 2) previewCurrentReasoning(selected, request.boxFootprint, request.chainFootprint, selectedReasoningHintVerified);
        else {
            cancelReasoningState(true, null);
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("intl/MainFrame");
            javax.swing.JOptionPane message = new javax.swing.JOptionPane(bundle.getString("MainFrame.possible_step")
                    + selected.toString(mode), javax.swing.JOptionPane.INFORMATION_MESSAGE);
            final javax.swing.JDialog dialog = message.createDialog(mainFrame,
                    bundle.getString(mode == 0 ? "MainFrame.vage_hint" : "MainFrame.medium_hint"));
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                    javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
            try { dialog.setVisible(true); } finally { dialog.dispose(); }
        }
        mainFrame.fixFocus();
        return true;
    }
    private void invalidateSearchPreview() {
        if (step==null && reasoningProposal != null && reasoningProposal.request.searchIdentity != null
                && !reasoningProposal.request.searchIdentity.equals(currentReasoningInputIdentity()))
            cancelReasoningState(true, "MainFrame.reasoning.invalidated");
    }

    boolean hasReasoningEnterContext() {
        return annotationTool != AnnotationTool.DEFAULT_MOUSE || reasoningProposal != null
                || reasoningRequest != null || applyingReasoningProposal;
    }

    java.util.Set<Integer> currentReasoningColoredCells() {return new java.util.TreeSet<Integer>(coloringMap.keySet());}
    java.util.Set<Integer> currentReasoningColoredCandidates() {return new java.util.TreeSet<Integer>(coloringCandidateMap.keySet());}

    int currentReasoningDigit() { return isInvalidCells() ? 0 : getShowHintCellValue(); }

    List<UserChain> currentReasoningChains() {
        List<UserChain> result = new ArrayList<UserChain>();
        if (activeUserChain != null) result.add(copyUserChain(activeUserChain));
        for (int i = userChains.size() - 1; i >= 0; i--) result.add(copyUserChain(userChains.get(i)));
        return result;
    }

    String currentReasoningInputIdentity() {
        StringBuilder id = new StringBuilder(TechniqueStepCatalog.createSignature(sudoku));
        id.append('|').append(coloringMap).append('|').append(coloringCandidateMap);
        id.append('|').append(getBoxReasoningFootprint()).append('|').append(currentReasoningDigit())
                .append('|').append(new java.util.TreeSet<Integer>(getSelectedCellsForTechniqueMatching()));
        for (UserChain chain : currentReasoningChains()) {
            id.append('|').append(chain.getSourceId()).append(':').append(chain.isClosed());
            for (UserChainNode node : chain.getNodes()) id.append(':').append(node.identity());
            id.append(chain.getStrongRelations());
        }
        return id.toString();
    }

    void previewCurrentReasoning(SolutionStep selected, SudokuSet boxes, UserChain chain, boolean verified) {
        if(step!=null && ReasoningStepIndex.identity(step).equals(ReasoningStepIndex.identity(selected))
                && (reasoningProposal==null || isReasoningProposalCurrent(reasoningProposal)))return;
        cancelReasoningState(true, null);
        ReasoningSourceKind kind = boxes != null ? ReasoningSourceKind.BOX
                : chain != null ? ReasoningSourceKind.FREE_CHAIN : ReasoningSourceKind.NONE;
        if (chain != null && chain.isActive()) {
            if (!sameUserChainContent(chain, activeUserChain)) return;
            chain = finishActiveUserChain(chain.isClosed(), true, true);
        }
        ReasoningRequest request = new ReasoningRequest(nextReasoningRequestId++, kind,
                chain == null ? 0 : chain.getSourceId(), reasoningBoardRevision, reasoningSourceRevision(kind),
                generatedStepOwnerRevision, TechniqueStepCatalog.createSignature(sudoku),
                boxes == null ? null : boxes.clone(), chain == null ? null : copyUserChain(chain));
        request.searchIdentity = currentReasoningInputIdentity();
        reasoningRequest = request;
        publishReasoningAnalysis(request, new NativeReasoningMatcher.Match(selected, NativeReasoningMatcher.keyForStep(selected)), null);
        if (reasoningProposal != null) {
            reasoningProposal.authoredChainProof = verified;
            if (!verified) rememberReasoningHint(request, selected);
        }
    }

	boolean hasSelectedUserChainForReasoning() {
		return findConfirmedUserChain() != null;
	}

    private static java.util.Set<Integer> boxCells(SudokuSet box) {
        java.util.Set<Integer> cells=new java.util.TreeSet<Integer>();
        if(box!=null)for(int i=0;i<box.size();i++)cells.add(box.get(i));return cells;
    }
	private void startReasoningAnalysis(ReasoningSourceKind sourceKind, long sourceId,
			SudokuSet boxFootprint, UserChain chainFootprint) {
		final Sudoku2 boardSnapshot = sudoku.clone();
		final String boardSignature = TechniqueStepCatalog.createSignature(boardSnapshot);
		final long sourceRevision = reasoningSourceRevision(sourceKind);
		final ReasoningRequest request = new ReasoningRequest(nextReasoningRequestId++, sourceKind,
				sourceId, reasoningBoardRevision, sourceRevision, generatedStepOwnerRevision,
				boardSignature, boxFootprint == null ? null : boxFootprint.clone(),
				chainFootprint == null ? null : copyUserChain(chainFootprint));
		String identity = reasoningRequestIdentity(request);
		reasoningRequest = request;
		mainFrame.setReasoningControls(false, true);
		mainFrame.announceReasoningStatus("MainFrame.reasoning.analyzing");
		Thread worker = new Thread(new Runnable() {
			@Override public void run() {
				NativeReasoningMatcher.Match match = null;
				Throwable failure = null;
				try {
                    if (request.sourceKind == ReasoningSourceKind.FREE_CHAIN) {
                        UserChainValidator.Result validated = UserChainValidator.preview(boardSnapshot, request.chainFootprint);
                        request.matchCount = validated.steps.size();
                        request.assumedRelations=validated.status==UserChainValidator.Status.ASSUMED;
                        if (!validated.steps.isEmpty()) {
                            SolutionStep first = validated.steps.get(0);
                            match = new NativeReasoningMatcher.Match(first, NativeReasoningMatcher.keyForStep(first));
                        }
                        final UserChainValidator.Result status = validated;
                        SwingUtilities.invokeLater(() -> {
                            if (isReasoningRequestCurrent(request)) mainFrame.announceChainValidation(status);
                        });
                    } else {
                        List<SolutionStep> catalog = mainFrame.getTechniqueStepCatalog().findBoxSteps(boardSnapshot,boxCells(request.boxFootprint));
                        for (SolutionStep candidate : catalog) {
                            ReasoningStepIndex index = ReasoningStepIndex.from(candidate, boardSnapshot);
                            java.util.Set<Integer> selected = new java.util.TreeSet<Integer>();
                            for (int i = 0; i < request.boxFootprint.size(); i++) selected.add(request.boxFootprint.get(i));
                            if (index.supported && !index.premiseCells.isEmpty() && index.premiseCells.equals(selected)
                                    && isNativeConclusionExecutable(candidate, boardSnapshot)) {
                                request.matchCount++;
                                if (match == null || candidate.getType().getStepConfig().getIndex()
                                        < match.getStep().getType().getStepConfig().getIndex())
                                    match = new NativeReasoningMatcher.Match(candidate, NativeReasoningMatcher.keyForStep(candidate));
                            }
                        }
                    }
				} catch (Throwable ex) {
					failure = ex;
				}
				final NativeReasoningMatcher.Match result = match;
				final Throwable error = failure;
				SwingUtilities.invokeLater(new Runnable() {
					@Override public void run() {
						publishReasoningAnalysis(request, result, error);
					}
				});
			}
		}, "hodoku-native-reasoning-analyze");
		worker.setDaemon(true);
		reasoningWorker = worker;
		worker.start();
	}

	private void publishReasoningAnalysis(ReasoningRequest request,
			NativeReasoningMatcher.Match match, Throwable error) {
		if (!isReasoningRequestCurrent(request)) return;
		reasoningRequest = null;
		reasoningWorker = null;
		if (error != null) {
			mainFrame.setReasoningControls(false, false);
			Logger.getLogger(SudokuPanel.class.getName()).log(Level.WARNING,
					"Native reasoning analysis failed", error);
			mainFrame.announceReasoningStatus("MainFrame.reasoning.error");
			return;
		}
		if (match == null || match.getKey() == null || match.getStep() == null) {
			mainFrame.setReasoningControls(false, false);
			lastNoMatchIdentity = reasoningRequestIdentity(request);
			if (request.sourceKind == ReasoningSourceKind.BOX) {
                mainFrame.announceReasoningStatus("MainFrame.reasoning.noMatch");
                mainFrame.showCurrentReasoning();
            }
            // Chain status has already been published; keep its precise diagnosis.
            return;
        }
        lastNoMatchIdentity = null;
		internalReasoningStepChange = true;
		try {
			mainFrame.setSolutionStep((SolutionStep) match.getStep().clone(), true);
		} finally {
			internalReasoningStepChange = false;
		}
		reasoningProposal = new ReasoningProposal(request, match.getKey(),
				generatedStepOwnerRevision);
		mainFrame.setReasoningControls(true, true);
		reasoningProposal.authoredChainProof = request.sourceKind == ReasoningSourceKind.FREE_CHAIN;
        if (!reasoningProposal.authoredChainProof) rememberReasoningHint(request, match.getStep());
		mainFrame.announceReasoningStatus(reasoningProposal.authoredChainProof
                ? "MainFrame.currentReasoning.verified" : "MainFrame.reasoning.preview");
        if (request.matchCount > 1) mainFrame.announceReasoningChoices(request.matchCount);
        if(request.assumedRelations)mainFrame.announceReasoningStatus("MainFrame.currentReasoning.assumed");
	}

	private void startReasoningApply(final ReasoningProposal proposal) {
		if (!isReasoningProposalCurrent(proposal)) {
			cancelReasoningState(true, "MainFrame.reasoning.invalidated");
			return;
		}
		applyingReasoningProposal = true;
		mainFrame.setReasoningControls(false, true);
		mainFrame.announceReasoningStatus("MainFrame.reasoning.revalidating");
		final Sudoku2 boardSnapshot = sudoku.clone();
		Thread worker = new Thread(new Runnable() {
			@Override public void run() {
				SolutionStep relocated = null;
				Throwable failure = null;
				try {
					List<SolutionStep> catalog = proposal.authoredChainProof
                            ? UserChainValidator.preview(boardSnapshot, proposal.request.chainFootprint).steps
                            : mainFrame.getTechniqueStepCatalog().findMatchingSteps(boardSnapshot,
                                candidate -> proposal.stepKey.equals(NativeReasoningMatcher.keyForStep(candidate)));
					for (SolutionStep candidate : catalog) {
						if (proposal.stepKey.equals(NativeReasoningMatcher.keyForStep(candidate))
								&& isNativeConclusionExecutable(candidate, boardSnapshot)) {
							relocated = candidate;
							break;
						}
					}
				} catch (Throwable ex) {
					failure = ex;
				}
				final SolutionStep result = relocated;
				final Throwable error = failure;
				SwingUtilities.invokeLater(new Runnable() {
					@Override public void run() {
						finishReasoningApply(proposal, result, error);
					}
				});
			}
		}, "hodoku-native-reasoning-apply");
		worker.setDaemon(true);
		reasoningWorker = worker;
		worker.start();
	}

	private void finishReasoningApply(ReasoningProposal proposal,
			SolutionStep relocated, Throwable error) {
		// A canceled revalidation may finish after the user has already started a
		// new analysis. Its callback must not clear or cancel that newer request.
		if (reasoningProposal != proposal || !applyingReasoningProposal) return;
		reasoningWorker = null;
		if (!isReasoningProposalCurrent(proposal) || error != null || relocated == null) {
			applyingReasoningProposal = false;
			if (error != null) {
				Logger.getLogger(SudokuPanel.class.getName()).log(Level.WARNING,
						"Native reasoning revalidation failed", error);
			}
			cancelReasoningState(true, error == null
					? "MainFrame.reasoning.invalidated" : "MainFrame.reasoning.error");
			return;
		}
		reasoningProposal = null;
		lastNoMatchIdentity = null;
		try {
			step = (SolutionStep) relocated.clone();
			generatedStepOwnerRevision++;
			internalReasoningStepChange = true;
			executingReasoningStep = true;
			try {
				if (!doStep()) {
					step = null;
					setChainInStep(-1);
					mainFrame.setSolutionStep(null, false);
					mainFrame.announceReasoningStatus("MainFrame.reasoning.invalidated");
					return;
				}
			} catch (RuntimeException | Error ex) {
				Logger.getLogger(SudokuPanel.class.getName()).log(Level.WARNING,
						"Native reasoning execution failed", ex);
				step = null;
				setChainInStep(-1);
				mainFrame.setSolutionStep(null, false);
				mainFrame.announceReasoningStatus("MainFrame.reasoning.error");
				return;
			} finally {
				executingReasoningStep = false;
				internalReasoningStepChange = false;
			}
			consumeAppliedReasoningSource(proposal.request);
			mainFrame.setSolutionStep(null, false);
			mainFrame.announceReasoningStatus("MainFrame.reasoning.applied");
		} finally {
			applyingReasoningProposal = false;
		}
	}

	/** Removes only the annotation source authorized by the successfully applied proposal. */
	private void consumeAppliedReasoningSource(ReasoningRequest request) {
        // A later research edit must never be consumed with the frozen proof.
        if(request.sourceRevision!=reasoningSourceRevision(request.sourceKind))return;
		if (request.sourceKind == ReasoningSourceKind.NONE || request.searchIdentity!=null && request.sourceKind==ReasoningSourceKind.BOX) return;
		if (request.sourceKind == ReasoningSourceKind.BOX) {
			if (request.boxFootprint == null || request.boxFootprint.isEmpty()) return;
			pushBoxReasoningUndo();
			for (SudokuSet group : boxReasoningGroups) {
				group.andNot(request.boxFootprint);
			}
			boxReasoningRevision++;
			noteBoxReasoningChanged();
			mainFrame.check();
			repaint();
			return;
		}
        if(request.sourceId==-1L){clearUserChainsWithUndo();return;}
		for (int i = userChains.size() - 1; i >= 0; i--) {
			if (userChains.get(i).getSourceId() == request.sourceId) {
				pushUserChainUndo();
				userChains.remove(i);
				if (confirmedUserChainSourceId == request.sourceId) {
					confirmedUserChainSourceId = 0L;
				}
				noteUserChainReasoningChanged();
				mainFrame.check();
				repaint();
				return;
			}
		}
	}

	private boolean isReasoningRequestCurrent(ReasoningRequest request) {
		return reasoningRequest == request
				&& request.boardRevision == reasoningBoardRevision
				&& request.sourceRevision == reasoningSourceRevision(request.sourceKind)
				&& request.generatedOwnerRevision == generatedStepOwnerRevision
				&& request.boardSignature.equals(TechniqueStepCatalog.createSignature(sudoku))
				&& (request.searchIdentity == null || request.searchIdentity.equals(currentReasoningInputIdentity()))
                && reasoningSourceStillExists(request);
	}

	private boolean isReasoningProposalCurrent(ReasoningProposal proposal) {
		return reasoningProposal == proposal && proposal.displayedOwnerRevision == generatedStepOwnerRevision
				&& proposal.request.boardRevision == reasoningBoardRevision
				&& proposal.request.boardSignature.equals(TechniqueStepCatalog.createSignature(sudoku))
                && step != null
				&& proposal.stepKey.equals(NativeReasoningMatcher.keyForStep(step));
	}

	private boolean reasoningSourceStillExists(ReasoningRequest request) {
		if (request.sourceKind == ReasoningSourceKind.NONE) return true;
		if (request.sourceKind == ReasoningSourceKind.BOX) {
			return request.boxFootprint != null
					&& request.boxFootprint.equals(getBoxReasoningFootprint());
		}
		UserChain chain = request.sourceId == -1L
                ? UserChainAssembly.assemble(currentReasoningChains()).chain : findUserChain(request.sourceId);
		return chain != null && sameUserChainContent(request.chainFootprint, chain);
	}

	private UserChain findUserChain(long sourceId) {
		if (sourceId == 0L) return null;
		for (UserChain chain : userChains) {
			if (chain.getSourceId() == sourceId) return chain;
		}
		return null;
	}

	private static boolean sameUserChainContent(UserChain first, UserChain second) {
		if (first == null || second == null || first.isClosed() != second.isClosed()
				|| first.getNodes().size() != second.getNodes().size()
				|| !first.getStrongRelations().equals(second.getStrongRelations())) {
			return false;
		}
		for (int i = 0; i < first.getNodes().size(); i++) {
			UserChainNode left = first.getNodes().get(i);
			UserChainNode right = second.getNodes().get(i);
			if (left.identity() != right.identity()) return false;
		}
		return true;
	}

	private long reasoningSourceRevision(ReasoningSourceKind sourceKind) {
		if (sourceKind == ReasoningSourceKind.NONE) return 0L;
		return sourceKind == ReasoningSourceKind.BOX
				? boxReasoningRevision : userChainReasoningRevision;
	}

	private String reasoningRequestIdentity(ReasoningRequest request) {
		return request.sourceKind.name() + ':' + request.sourceId + ':'
				+ request.boardSignature + ':' + request.sourceRevision;
	}

	/** Returns whether at least one delete or set conclusion remains unapplied. */
	static boolean isNativeConclusionExecutable(SolutionStep candidate, Sudoku2 board) {
		if (candidate == null || board == null) return false;
		for (Candidate deletion : candidate.getCandidatesToDelete()) {
			if (deletion != null && deletion.getIndex() >= 0 && deletion.getIndex() < Sudoku2.LENGTH
					&& deletion.getValue() >= 1 && deletion.getValue() <= Sudoku2.UNITS
					&& board.getValue(deletion.getIndex()) == 0
					&& board.isCandidate(deletion.getIndex(), deletion.getValue())) {
				return true;
			}
		}

		if (candidate.getAnzSet() == 0 && candidate.getType() != SolutionType.BRUTE_FORCE) {
			return false;
		}
		List<Integer> indices = candidate.getIndices();
		List<Integer> values = candidate.getValues();
		if (indices.isEmpty() || values.isEmpty()) return false;
		if (candidate.getType() == SolutionType.TEMPLATE_SET
				|| candidate.getType() == SolutionType.BRUTE_FORCE) {
			int value = values.get(0);
			for (Integer index : indices) {
				if (index != null && index >= 0 && index < Sudoku2.LENGTH
						&& value >= 1 && value <= Sudoku2.UNITS
						&& board.getValue(index) != value) return true;
			}
			return false;
		}
		for (int i = 0; i < indices.size() && i < values.size(); i++) {
			Integer index = indices.get(i);
			Integer value = values.get(i);
			if (index != null && value != null && index >= 0 && index < Sudoku2.LENGTH
					&& value >= 1 && value <= Sudoku2.UNITS
					&& board.getValue(index) != value) return true;
		}
		return false;
	}

	boolean isReasoningAnalysisInProgress() {
		return reasoningRequest != null || applyingReasoningProposal;
	}

	/** Routes toolbar, hint-panel, and Enter confirmation through one revalidation gate. */
	boolean confirmReasoningProposal() {
		if (reasoningRequest != null || applyingReasoningProposal) {
			mainFrame.announceReasoningStatus(applyingReasoningProposal
					? "MainFrame.reasoning.revalidating" : "MainFrame.reasoning.analyzing");
			return true;
		}
		if (reasoningProposal != null) {
			startReasoningApply(reasoningProposal);
			return true;
		}
		return false;
	}

	/** Routes both visible cancel buttons through the same reasoning cancellation. */
	boolean cancelReasoningFromUi() {
		return cancelReasoningState(true, "MainFrame.reasoning.canceled");
	}

	private void noteUserChainReasoningChanged() {
        clearPreciseChainGesture();
        invalidUserChainRelations.clear();
		userChainReasoningRevision++;
		lastNoMatchIdentity = null;
		invalidateReasoningSource(ReasoningSourceKind.FREE_CHAIN);
	}

	private void noteBoxReasoningChanged() {
        mainFrame.updateCellSelectionStatus();
		lastNoMatchIdentity = null;
		invalidateReasoningSource(ReasoningSourceKind.BOX);
	}

	private void invalidateReasoningSource(ReasoningSourceKind sourceKind) {
        refreshAnnotationTimeline();
        if(reasoningProposal!=null && step!=null)return;
		ReasoningSourceKind activeKind = reasoningProposal != null
				? reasoningProposal.request.sourceKind
				: (reasoningRequest == null ? null : reasoningRequest.sourceKind);
		if (activeKind == sourceKind) {
			cancelReasoningState(true, "MainFrame.reasoning.invalidated");
		}
	}

	private boolean cancelReasoningState(boolean removeOverlay, String statusKey) {
		boolean changed = reasoningRequest != null || reasoningProposal != null
				|| applyingReasoningProposal;
		if (reasoningWorker != null) reasoningWorker.interrupt();
		reasoningWorker = null;
		reasoningRequest = null;
		ReasoningProposal oldProposal = reasoningProposal;
		reasoningProposal = null;
		applyingReasoningProposal = false;
		if (removeOverlay && oldProposal != null && step != null) {
			internalReasoningStepChange = true;
			try {
				mainFrame.setSolutionStep(null, true);
			} finally {
				internalReasoningStepChange = false;
			}
		}
		if (changed) mainFrame.setReasoningControls(false, false);
		if (changed && statusKey != null) mainFrame.announceReasoningStatus(statusKey);
		return changed;
	}

	private void generatedStepOwnerWillChange() {
		generatedStepOwnerRevision++;
		if (!internalReasoningStepChange) {
			cancelReasoningState(false, "MainFrame.reasoning.invalidated");
		}
	}

	/** Called by MainFrame after every real Sudoku mutation. */
	void reasoningBoardChanged() {
        invalidUserChainRelations.clear();
		clearPendingAnnotationToolTap();
		activeAnnotationToolDoubleTap = false;
		activeAnnotationToolDoubleTapOrigin = null;
		reasoningBoardRevision++;
		lastNoMatchIdentity = null;
		if (!executingReasoningStep) {
			cancelReasoningState(true, "MainFrame.reasoning.invalidated");
            if(step!=null)mainFrame.setSolutionStep(null,true);
		}
	}

	void shutdownReasoning() {
		cancelReasoningState(true, null);
	}

	private boolean handleAnnotationToolKeyPressed(KeyEvent event) {
		AnnotationTool resolvedTarget = resolveAnnotationToolKey(event.getKeyCode());
		if (resolvedTarget == null) {
			return false;
		}
		if (activeAnnotationToolKey != KeyEvent.VK_UNDEFINED) {
			// Consume operating-system repeat and competing direct-tool keys until the
			// matching physical release closes this gesture.
			return true;
		}
		long sinceLastTap = event.getWhen() - pendingAnnotationToolTapReleasedAt;
		activeAnnotationToolDoubleTap = pendingAnnotationToolTapKey == event.getKeyCode()
				&& sinceLastTap >= 0L && sinceLastTap <= ANNOTATION_TOOL_DOUBLE_TAP_MILLIS
				&& pendingAnnotationToolTapBoardSignature != null
				&& pendingAnnotationToolTapBoardSignature.equals(
						currentAnnotationToolTapBoardSignature());
		AnnotationTool settledOrigin = settledAnnotationToolOrigins.get(
				Integer.valueOf(event.getKeyCode()));
		activeAnnotationToolToggleBack = !activeAnnotationToolDoubleTap
				&& settledOrigin != null
				&& shortcutOwnsAnnotationTool(event.getKeyCode(), stickyAnnotationTool);
		activeAnnotationToolToggleOrigin = activeAnnotationToolToggleBack ? settledOrigin : null;
		AnnotationTool target = activeAnnotationToolDoubleTap
				? pendingAnnotationToolTapTarget
				: (activeAnnotationToolToggleBack ? stickyAnnotationTool : resolvedTarget);
		activeAnnotationToolDoubleTapOrigin = activeAnnotationToolDoubleTap
				? pendingAnnotationToolTapOrigin : null;
		clearPendingAnnotationToolTap();
		activeAnnotationToolKey = event.getKeyCode();
		activeAnnotationToolPressedAt = event.getWhen();
		activeAnnotationToolTarget = target;
		annotationToolBeforeKeyGesture = stickyAnnotationTool;
		annotationToolGestureUsedBoard = false;
		applyAnnotationTool(target);
		return true;
	}

	boolean handleAnnotationToolKeyReleased(KeyEvent event) {
        updateDeletionModifier(event);
		if (event.getKeyCode() != activeAnnotationToolKey) {
			return false;
		}
		long heldFor = Math.max(0L, event.getWhen() - activeAnnotationToolPressedAt);
		boolean temporary = heldFor >= ANNOTATION_TOOL_HOLD_MILLIS || annotationToolGestureUsedBoard;
		AnnotationTool target = activeAnnotationToolTarget;
		AnnotationTool preceding = annotationToolBeforeKeyGesture;
		boolean doubleTap = activeAnnotationToolDoubleTap && !temporary;
		AnnotationTool doubleTapOrigin = activeAnnotationToolDoubleTapOrigin;
		boolean toggleBack = activeAnnotationToolToggleBack && !temporary;
		AnnotationTool toggleOrigin = activeAnnotationToolToggleOrigin;
		int shortcutKey = activeAnnotationToolKey;
		clearActiveAnnotationToolKeyGesture();
		if (doubleTap) {
			clearAnnotationToolWithUndo(target);
			stickyAnnotationTool = doubleTapOrigin;
			applyAnnotationTool(doubleTapOrigin);
		} else if (temporary && annotationToolPointerCaptured) {
			pendingAnnotationToolRestore = preceding;
		} else if (temporary) {
			stickyAnnotationTool = preceding;
			applyAnnotationTool(preceding);
		} else if (toggleBack) {
			stickyAnnotationTool = toggleOrigin;
			applyAnnotationTool(toggleOrigin);
			recordPendingAnnotationToolTap(shortcutKey, event.getWhen(), preceding, target);
		} else {
			rememberSettledAnnotationToolOrigin(shortcutKey, target, preceding);
			stickyAnnotationTool = target;
			if (annotationTool != target) applyAnnotationTool(target);
			recordPendingAnnotationToolTap(event.getKeyCode(), event.getWhen(), preceding, target);
		}
		return true;
	}

	private void recordPendingAnnotationToolTap(int keyCode, long releasedAt,
			AnnotationTool origin, AnnotationTool target) {
		pendingAnnotationToolTapKey = keyCode;
		pendingAnnotationToolTapReleasedAt = releasedAt;
		pendingAnnotationToolTapOrigin = origin;
		pendingAnnotationToolTapTarget = target;
		pendingAnnotationToolTapBoardSignature = currentAnnotationToolTapBoardSignature();
	}

	private boolean isUnmodifiedAnnotationToolShortcut(KeyEvent event) {
		if (event.getModifiersEx() != 0) return false;
		int keyCode = event.getKeyCode();
		return keyCode == KeyEvent.VK_M || keyCode == KeyEvent.VK_T
				|| keyCode == KeyEvent.VK_P || keyCode == KeyEvent.VK_L
				|| keyCode == KeyEvent.VK_S;
	}

	private String currentAnnotationToolTapBoardSignature() {
		return sudoku.getSudoku(ClipboardMode.PM_GRID);
	}

	private boolean shortcutOwnsAnnotationTool(int keyCode, AnnotationTool tool) {
		return (keyCode == KeyEvent.VK_T && isColoringTool(tool))
                || (keyCode == KeyEvent.VK_P && tool == AnnotationTool.DOODLE)
				|| (keyCode == KeyEvent.VK_L && tool == AnnotationTool.FREE_CHAIN)
				|| (keyCode == KeyEvent.VK_S && tool == AnnotationTool.BOX_SELECTION);
	}

	private void rememberSettledAnnotationToolOrigin(int keyCode,
			AnnotationTool target, AnnotationTool origin) {
		if (origin != null && target != origin && shortcutOwnsAnnotationTool(keyCode, target)) {
			settledAnnotationToolOrigins.put(Integer.valueOf(keyCode), origin);
		}
	}

	private void rememberSettledAnnotationToolOrigin(AnnotationTool target, AnnotationTool origin) {
		if(isColoringTool(target)){rememberSettledAnnotationToolOrigin(KeyEvent.VK_T,target,origin);}
        else if (target == AnnotationTool.DOODLE) {
			rememberSettledAnnotationToolOrigin(KeyEvent.VK_P, target, origin);
		} else if (target == AnnotationTool.FREE_CHAIN) {
			rememberSettledAnnotationToolOrigin(KeyEvent.VK_L, target, origin);
		} else if (target == AnnotationTool.BOX_SELECTION) {
			rememberSettledAnnotationToolOrigin(KeyEvent.VK_S, target, origin);
		}
	}

	private AnnotationTool resolveAnnotationToolKey(int keyCode) {
		switch (keyCode) {
		case KeyEvent.VK_M:
			return AnnotationTool.DEFAULT_MOUSE;
		case KeyEvent.VK_T:
            return AnnotationTool.CANDIDATE_COLORING;
		case KeyEvent.VK_P:
			return AnnotationTool.DOODLE;
		case KeyEvent.VK_L:
			return AnnotationTool.FREE_CHAIN;
		case KeyEvent.VK_S:
			return AnnotationTool.BOX_SELECTION;
		default:
			return null;
		}
	}

	private void cancelAnnotationToolKeyGesture() {
		if (activeAnnotationToolKey == KeyEvent.VK_UNDEFINED) {
			return;
		}
		AnnotationTool preceding = annotationToolBeforeKeyGesture;
		clearActiveAnnotationToolKeyGesture();
		stickyAnnotationTool = preceding;
		applyAnnotationTool(preceding);
	}

	private void clearActiveAnnotationToolKeyGesture() {
		activeAnnotationToolKey = KeyEvent.VK_UNDEFINED;
		activeAnnotationToolPressedAt = 0L;
		activeAnnotationToolTarget = null;
		annotationToolBeforeKeyGesture = null;
		annotationToolGestureUsedBoard = false;
		activeAnnotationToolDoubleTap = false;
		activeAnnotationToolDoubleTapOrigin = null;
		activeAnnotationToolToggleBack = false;
		activeAnnotationToolToggleOrigin = null;
	}

	private void clearPendingAnnotationToolTap() {
		pendingAnnotationToolTapKey = KeyEvent.VK_UNDEFINED;
		pendingAnnotationToolTapReleasedAt = 0L;
		pendingAnnotationToolTapOrigin = null;
		pendingAnnotationToolTapTarget = null;
		pendingAnnotationToolTapBoardSignature = null;
	}

	private void noteAnnotationToolBoardAction(boolean capturesPointer) {
		annotationToolGestureUsedBoard = true;
		clearPendingAnnotationToolTap();
		annotationToolPointerCaptured |= capturesPointer;
	}

	private void finishAnnotationToolPointerGesture() {
		annotationToolPointerCaptured = false;
		if (pendingAnnotationToolRestore != null) {
			AnnotationTool restore = pendingAnnotationToolRestore;
			pendingAnnotationToolRestore = null;
			stickyAnnotationTool = restore;
			applyAnnotationTool(restore);
		}
	}

	private boolean isBoardEditingKey(KeyEvent event) {
		int keyCode = event.getKeyCode();
		if ((keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
				|| (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9)) {
			return true;
		}
		return keyCode == KeyEvent.VK_DELETE || keyCode == KeyEvent.VK_BACK_SPACE
				|| keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_SPACE
				|| keyCode == KeyEvent.VK_F11;
	}

	private boolean isMacCandidateFilterShortcut(KeyEvent event) {
		int keyCode = event.getKeyCode();
		boolean digit = (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9)
				|| (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9);
		int modifiers = event.getModifiersEx();
		int forbidden = KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK;
		return digit && (modifiers & KeyEvent.ALT_DOWN_MASK) != 0 && (modifiers & forbidden) == 0;
	}

	void clearCurrentAnnotationWithUndo() {
		clearAnnotationToolWithUndo(annotationTool);
	}

	private void clearAnnotationToolWithUndo(AnnotationTool tool) {
		switch (tool) {
		case CANDIDATE_COLORING:
		case CELL_COLORING:
			clearColoringWithUndo();
			break;
		case DOODLE:
			clearDoodlesWithUndo();
			break;
		case FREE_CHAIN:
			clearUserChainsWithUndo();
			break;
		case BOX_SELECTION:
			clearBoxReasoningWithUndo();
			break;
		default:
			break;
		}
	}

	public void clearAllAnnotationsWithUndo() {
		boolean coloringChanged = clearColoringWithUndo(false);
		boolean doodlesChanged = clearDoodlesWithUndo(false);
		boolean chainsChanged = clearUserChainsWithUndo(false);
		boolean boxChanged = clearBoxReasoningWithUndo(false);
		if (!coloringChanged && !doodlesChanged && !chainsChanged && !boxChanged) {
			return;
		}

		if (coloringChanged) updateCellZoomPanel();
		mainFrame.refreshAnnotationUndoControls();
		repaint();
	}

	public AnnotationTool getAnnotationTool() {
		return annotationTool;
	}

	boolean isAnnotationUndoContext() {
		return annotationTool != AnnotationTool.DEFAULT_MOUSE;
	}

	boolean currentAnnotationUndoPossible() {
		switch (annotationTool) {
		case CANDIDATE_COLORING:
		case CELL_COLORING:
			return !coloringUndoStack.isEmpty();
		case DOODLE:
			return !doodleUndoStack.isEmpty();
		case FREE_CHAIN:
			return !userChainUndoStack.isEmpty();
		case BOX_SELECTION:
			return !boxReasoningUndoStack.isEmpty();
		default:
			return false;
		}
	}

	boolean currentAnnotationRedoPossible() {
		switch (annotationTool) {
		case CANDIDATE_COLORING:
		case CELL_COLORING:
			return !coloringRedoStack.isEmpty();
		case DOODLE:
			return !doodleRedoStack.isEmpty();
		case FREE_CHAIN:
			return !userChainRedoStack.isEmpty();
		case BOX_SELECTION:
			return !boxReasoningRedoStack.isEmpty();
		default:
			return false;
		}
	}

	void undoCurrentAnnotation() {
		switch (annotationTool) {
		case CANDIDATE_COLORING:
		case CELL_COLORING:
			undoColoring();
			break;
		case DOODLE:
			undoDoodle();
			break;
		case FREE_CHAIN:
			undoUserChains();
			break;
		case BOX_SELECTION:
			undoBoxReasoning();
			break;
		default:
			break;
		}
		mainFrame.check();
	}

	void redoCurrentAnnotation() {
		switch (annotationTool) {
		case CANDIDATE_COLORING:
		case CELL_COLORING:
			redoColoring();
			break;
		case DOODLE:
			redoDoodle();
			break;
		case FREE_CHAIN:
			redoUserChains();
			break;
		case BOX_SELECTION:
			redoBoxReasoning();
			break;
		default:
			break;
		}
		mainFrame.check();
	}

	public boolean setAnnotationTool(AnnotationTool tool) {
        if(tool==AnnotationTool.CELL_COLORING)tool=AnnotationTool.CANDIDATE_COLORING;
		if (tool == null) {
			return false;
		}
		AnnotationTool precedingStickyTool = stickyAnnotationTool;
		rememberSettledAnnotationToolOrigin(tool, precedingStickyTool);
		if (tool == annotationTool) {
			stickyAnnotationTool = tool;
			clearActiveAnnotationToolKeyGesture();
			clearPendingAnnotationToolTap();
			pendingAnnotationToolRestore = null;
			return true;
		}
		if (annotationToolPointerCaptured || boxDragStart != null || activeDoodleStroke != null) {
			suppressNextAnnotationPointerRelease = true;
		}
		if (tool != AnnotationTool.BOX_SELECTION) {
			cancelBoxReasoningDrag();
		}
		if (tool != AnnotationTool.DOODLE) {
			cancelDoodleGesture();
		}
		stickyAnnotationTool = tool;
		clearActiveAnnotationToolKeyGesture();
		clearPendingAnnotationToolTap();
		pendingAnnotationToolRestore = null;
		annotationToolPointerCaptured = false;
		return applyAnnotationTool(tool);
	}

	private boolean applyAnnotationTool(AnnotationTool tool) {
        if(tool==AnnotationTool.CELL_COLORING)tool=AnnotationTool.CANDIDATE_COLORING;
		if (tool == null) {
			return false;
		}
		AnnotationTool previousTool = annotationTool;
        if(annotationCursorBeforeDelete!=null){setCursor(annotationCursorBeforeDelete);annotationCursorBeforeDelete=null;}
        chainDeleteStart=chainDeleteCurrent=null;
        chainFlipStart=chainFlipCurrent=null;clearPreciseChainGesture();
        coloringPress=coloringCurrent=null;
		annotationTool = tool;
		boolean wasApplyingAnnotationTool = applyingAnnotationTool;
		applyingAnnotationTool = true;
		try {
			if (tool == AnnotationTool.DOODLE) {
				doodlesVisible = true;
			} else if (tool == AnnotationTool.FREE_CHAIN) {
				userChainsVisible = true;
			}
			if (cellZoomPanel != null) cellZoomPanel.selectAnnotationTool(tool);
			if (tool == AnnotationTool.CANDIDATE_COLORING || tool == AnnotationTool.CELL_COLORING) {
				lastColoringTool = tool;
			}
			if (mainFrame != null) {
				Color activeColor = tool == AnnotationTool.CANDIDATE_COLORING
						|| tool == AnnotationTool.CELL_COLORING
								? cellZoomPanel.getPrimaryColor() : null;
				if (isColoringTool(previousTool) || isColoringTool(tool)) {
					mainFrame.coloringPanelClicked(activeColor);
				}
				mainFrame.annotationToolUiChanged(tool);
			}
		} finally {
			applyingAnnotationTool = wasApplyingAnnotationTool;
		}
        updateDeletionCursor();
		repaint();
		return true;
	}

	private static boolean isColoringTool(AnnotationTool tool) {
		return tool == AnnotationTool.CANDIDATE_COLORING || tool == AnnotationTool.CELL_COLORING;
	}

	/** Keeps the unified annotation state aligned with legacy color controls. */
	void synchronizeAnnotationToolFromColoring(boolean isCell, Color color) {
		annotationTool = color == null ? AnnotationTool.DEFAULT_MOUSE
				: AnnotationTool.CANDIDATE_COLORING;
		if (!applyingAnnotationTool) {
			stickyAnnotationTool = annotationTool;
		}
		if (annotationTool == AnnotationTool.CANDIDATE_COLORING || annotationTool == AnnotationTool.CELL_COLORING) {
			lastColoringTool = annotationTool;
		}
		if (cellZoomPanel != null) {
			cellZoomPanel.selectAnnotationTool(annotationTool);
		}
		if (mainFrame != null) {
			mainFrame.annotationToolChanged(annotationTool);
		}
	}

	public int getDoodleStrokeCount() {
		return doodleStrokes.size();
	}

	public int getUserChainCount() {
		return userChains.size();
	}

    void updateDeletionModifier(KeyEvent event) {
        if (event.getID()==KeyEvent.KEY_RELEASED && (event.getKeyCode()==KeyEvent.VK_ALT
                || preciseChainDelete && event.getKeyCode()==KeyEvent.VK_CONTROL)) clearPreciseChainGesture();
        if (!SudokuUtil.isDeletionModifierKey(event)) return;
        deletionModifierDown = event.getID() == KeyEvent.KEY_PRESSED;
        updateDeletionCursor();repaint();
    }

    private void updateDeletionPointer(MouseEvent event) {
        if (preciseChainCandidate>=0 && (!event.isAltDown() || preciseChainDelete && !event.isControlDown())) clearPreciseChainGesture();
        deletionModifierDown = SudokuUtil.isDeletionModifierDown(event);
        lastMousePosition = event.getPoint();updateDeletionCursor();repaint();
    }

    private void updateDeletionCursor() {
        boolean erase = deletionModifierDown && (annotationTool == AnnotationTool.DOODLE
                || annotationTool == AnnotationTool.BOX_SELECTION || annotationTool == AnnotationTool.FREE_CHAIN || isColoringTool(annotationTool));
        if (erase && annotationCursorBeforeDelete == null) {
            annotationCursorBeforeDelete = getCursor();
            java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = icon.createGraphics();
            if (annotationTool != AnnotationTool.DOODLE) {
                g.setColor(Color.WHITE);g.setStroke(new BasicStroke(3));g.drawRect(6,6,20,20);
                g.drawLine(6,6,26,26);g.drawLine(26,6,6,26);
                g.setColor(Color.BLACK);g.setStroke(new BasicStroke(1));g.drawRect(6,6,20,20);
                g.drawLine(6,6,26,26);g.drawLine(26,6,6,26);
            }
            g.dispose();setCursor(java.awt.Toolkit.getDefaultToolkit().createCustomCursor(icon,new Point(16,16),"Annotation erase"));
        } else if (!erase && annotationCursorBeforeDelete != null) {
            Cursor restore=annotationCursorBeforeDelete;annotationCursorBeforeDelete=null;setCursor(restore);
        }
    }

    private void drawDeletionGesture(Graphics2D graphics) {
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            java.awt.Shape shape=null;
            if (doodleGesture==DoodleGesture.RECT_ERASER) shape=dragRectangle(doodleGestureStart,doodleGestureCurrent);
            else if (annotationTool==AnnotationTool.DOODLE && deletionModifierDown && lastMousePosition!=null) {
                double r=doodleEraserRadius*Math.min(getWidth(),getHeight());
                shape=new java.awt.geom.Ellipse2D.Double(lastMousePosition.x-r,lastMousePosition.y-r,2*r,2*r);
            } else if(chainDeleteStart!=null) shape=dragRectangle(chainDeleteStart,chainDeleteCurrent);
            else if(chainFlipStart!=null && !chainRightDelete) shape=dragRectangle(chainFlipStart,chainFlipCurrent);
            else if(coloringPress!=null && coloringErase)shape=dragRectangle(coloringPress,coloringCurrent);
            if(shape!=null) {
                g.setColor(Color.WHITE);g.setStroke(new BasicStroke(3));g.draw(shape);
                g.setColor(Color.BLACK);g.setStroke(new BasicStroke(1));g.draw(shape);
                if(chainDeleteStart!=null || doodleGesture==DoodleGesture.RECT_ERASER) {java.awt.Rectangle r=shape.getBounds();g.drawLine(r.x,r.y,r.x+r.width,r.y+r.height);g.drawLine(r.x+r.width,r.y,r.x,r.y+r.height);}
            }
        } finally {g.dispose();}
    }

    private boolean beginColoringGesture(MouseEvent e) {
        if(!isColoringTool(annotationTool))return false;
        if(!isOnGrid(e.getPoint()))return true;
        if(!SwingUtilities.isLeftMouseButton(e)&&!SwingUtilities.isRightMouseButton(e))return true;
        int forbidden=java.awt.event.InputEvent.SHIFT_DOWN_MASK|java.awt.event.InputEvent.META_DOWN_MASK|java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK;
        if((e.getModifiersEx()&forbidden)!=0)return true;
        coloringPress=coloringCurrent=e.getPoint();coloringRight=SwingUtilities.isRightMouseButton(e);
        coloringErase=SudokuUtil.isDeletionModifierDown(e);
        coloringGestureColor=e.isAltDown()?cellZoomPanel.getSecondaryColor():cellZoomPanel.getPrimaryColor();
        noteAnnotationToolBoardAction(true);repaint();return true;
    }
    private boolean finishColoringGesture(MouseEvent e) {
        if(coloringPress==null)return isColoringTool(annotationTool);
        if(coloringErase) {
            java.awt.Rectangle r=dragRectangle(coloringPress,e.getPoint());
            java.util.List<Integer> remove=new ArrayList<Integer>();
            Map<Integer,Color> map=coloringRight?coloringCandidateMap:coloringMap;
            for(int key:map.keySet()) {
                int cell=coloringRight?key/10:key;
                Point2D center=coloringRight?getCandKoord(cell,key%10,cellSize)
                    :new Point2D.Double(getX(cell/9,cell%9)+cellSize/2.0,getY(cell/9,cell%9)+cellSize/2.0);
                if(r.contains(center))remove.add(key);
            }
            if(!remove.isEmpty()){pushColoringUndo();for(int key:remove)map.remove(key);updateCellZoomPanel();mainFrame.check();}
        } else if(isOnGrid(e.getPoint()) && getRow(coloringPress)==getRow(e.getPoint()) && getCol(coloringPress)==getCol(e.getPoint())) {
            int row=getRow(e.getPoint()),col=getCol(e.getPoint());int d=coloringRight?getCandidate(e.getPoint(),row,col):-1;
            if(!coloringRight || d>0&&isUserChainCandidateVisible(row*9+col,d))handleColoring(row,col,d,coloringGestureColor);
        }
        coloringPress=coloringCurrent=null;repaint();return true;
    }

    private String chainNodeTimeKey(UserChainNode node){return "node:"+node.identity();}
    private String chainEdgeTimeKey(UserChainNode from,UserChainNode to){return "edge:"+from.identity()+":"+to.identity();}
    private String doodleTimeKey(DoodleStroke stroke) {
        StringBuilder key=new StringBuilder("ink:").append(stroke.getColor()).append(':').append(stroke.getWidthFactor());
        if(stroke.isCandidateAnchored())key.append(":candidate:").append(stroke.getAnchorCell()).append(':').append(stroke.getAnchorDigit());
        for(DoodlePoint point:stroke.getPoints())key.append(':').append(point.getX()).append(',').append(point.getY());
        return key.toString();
    }
    private void refreshAnnotationTimeline() {
        Map<String,String> objects=new java.util.HashMap<>();
        for(DoodleStroke stroke:doodleStrokes)objects.put(doodleTimeKey(stroke),"ink");
        for(int group=0;group<boxReasoningGroups.size();group++)for(int i=0;i<boxReasoningGroups.get(group).size();i++)
            objects.put("box:"+group+":"+boxReasoningGroups.get(group).get(i),Options.getInstance().getColoringColors()[group*2].toString());
        for(Map.Entry<Integer,Color> e:coloringMap.entrySet())objects.put("cell:"+e.getKey(),e.getValue().toString());
        for(Map.Entry<Integer,Color> e:coloringCandidateMap.entrySet())objects.put("candidate:"+e.getKey(),e.getValue().toString());
        List<UserChain> chains=new ArrayList<>(userChains);if(activeUserChain!=null)chains.add(activeUserChain);
        for(UserChain chain:chains) {
            for(UserChainNode node:chain.getNodes())objects.put(chainNodeTimeKey(node),String.valueOf(node.getColor()));
            int n=chain.getNodes().size();
            for(int i=0;i<chain.getStrongRelations().size();i++) {
                String key=chainEdgeTimeKey(chain.getNodes().get(i),chain.getNodes().get((i+1)%n));
                String value=chain.getStrongRelations().get(i)+":"+(i<chain.getRelationColors().size()?chain.getRelationColors().get(i):null);
                objects.put(key,objects.containsKey(key)?objects.get(key)+"/"+value:value);
            }
        }
        annotationTimeline.observe(step==null?null:ReasoningStepIndex.identity(step),objects);
    }
    private boolean paintAnnotation(String key) {
        if(step==null)return annotationPaintLayer!=2;
        boolean later=annotationTimeline.isLater(key);
        if(key.startsWith("box:") && boxDragPreview!=null) {
            String[] parts=key.split(":");int group=Integer.parseInt(parts[1]),cell=Integer.parseInt(parts[2]);
            if(!boxReasoningGroups.get(group).contains(cell)&&isBoxReasoningCellPredicted(group,cell))later=true;
        }
        return annotationPaintLayer==2?later:!later;
    }
    private Color annotationColor(String key,Color color,Color background) {
        if(step==null||annotationTimeline.isLater(key))return color;
        float a=BACKGROUND_ANNOTATION_ALPHA;
        return new Color(Math.round(color.getRed()*a+background.getRed()*(1-a)),
            Math.round(color.getGreen()*a+background.getGreen()*(1-a)),
            Math.round(color.getBlue()*a+background.getBlue()*(1-a)));
    }
    private void drawLaterCandidateColors(Graphics2D graphics,SudokuAppearancePalette appearance,double diameter) {
        if(!isColoringVisible)return;
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setFont(candidateFont);
            for(Map.Entry<Integer,Color> entry:coloringCandidateMap.entrySet()) {
                if(!annotationTimeline.isLater("candidate:"+entry.getKey()))continue;
                int cell=entry.getKey()/10,digit=entry.getKey()%10;
                if(!sudoku.isCandidate(cell,digit))continue;
                double third=cellSize/3.0;
                double x=getX(cell/9,cell%9)+((digit-1)%3+.5)*third;
                double y=getY(cell/9,cell%9)+((digit-1)/3+.5)*third;
                g.setColor(entry.getValue());g.fill(new java.awt.geom.Rectangle2D.Double(x-diameter/2,y-diameter/2,diameter,diameter));
                g.setColor(appearance.getReadableForeground(entry.getValue(),appearance.getCandidateColor()));
                java.awt.FontMetrics fm=g.getFontMetrics();
                g.drawString(Integer.toString(digit),(float)(x-fm.stringWidth(Integer.toString(digit))/2.0),(float)(y+(fm.getAscent()-fm.getDescent())/2.0));
            }
        }finally{g.dispose();}
    }
    private float annotationOpacity(){return step!=null&&annotationPaintLayer!=2?BACKGROUND_ANNOTATION_ALPHA:1f;}

    private void clearPreciseChainGesture() { preciseChainCandidate=-1;repaint(); }

    private boolean beginPreciseChainGesture(MouseEvent e) {
        if(annotationTool!=AnnotationTool.FREE_CHAIN || !e.isAltDown())return false;
        boolean deleting=e.isControlDown() && SwingUtilities.isLeftMouseButton(e);
        if(!deleting && (!SwingUtilities.isRightMouseButton(e)||e.isControlDown()))return false;
        noteAnnotationToolBoardAction(true);
        int row=getRow(e.getPoint()),col=getCol(e.getPoint());
        if(!Sudoku2.isValidIndex(row,col)){clearPreciseChainGesture();return true;}
        int digit=getCandidate(e.getPoint(),row,col),cell=Sudoku2.getIndex(row,col);
        if(digit<1 || !isUserChainCandidateVisible(cell,digit)){clearPreciseChainGesture();return true;}
        int picked=cell*10+digit;
        if(preciseChainCandidate<0 || preciseChainDelete!=deleting) {
            preciseChainCandidate=picked;preciseChainDelete=deleting;
            mainFrame.announceReasoningStatus("MainFrame.chainEdit.secondEndpoint");repaint();return true;
        }
        int first=preciseChainCandidate;clearPreciseChainGesture();
        UserChainSegment target=null;int count=0;
        for(UserChainSegment segment:collectUserChainSegments()) {
            boolean forward=segment.from.contains(first/10,first%10)&&segment.to.contains(cell,digit);
            boolean reverse=segment.to.contains(first/10,first%10)&&segment.from.contains(cell,digit);
            if(first!=picked && (forward||reverse)){target=segment;count++;}
        }
        if(count!=1){mainFrame.announceReasoningStatus(count==0?"MainFrame.chainEdit.noEdge":"MainFrame.chainEdit.ambiguous");return true;}
        Map<UserChain,java.util.Set<Integer>> targets=new java.util.IdentityHashMap<>();
        for(UserChain chain:userChains)collectHitChainEdge(targets,chain,target);
        if(activeUserChain!=null)collectHitChainEdge(targets,activeUserChain,target);
        if(deleting)deleteExactChainEdges(targets);else flipChainEdges(targets);
        return true;
    }

    private UserChain latestChainWithEdge() {
        if(activeUserChain!=null&&!activeUserChain.getStrongRelations().isEmpty())return activeUserChain;
        for(int i=userChains.size()-1;i>=0;i--)if(!userChains.get(i).getStrongRelations().isEmpty())return userChains.get(i);
        return null;
    }
    private void synchronizeNextChainEdge() {
        UserChain last=latestChainWithEdge();
        updateNextUserChainStrong(last==null || !last.getStrongRelations().get(last.getStrongRelations().size()-1));
    }
    private boolean finishChainFlipDrag(MouseEvent e) {
        if(chainFlipStart==null)return false;
        if(chainRightDelete) {
            boolean click=!chainRightDragged && chainFlipStart.distance(e.getPoint())<4;
            chainFlipStart=chainFlipCurrent=null;chainRightDelete=false;
            if(click)removeLastUserChainNode();repaint();return true;
        }
        Map<UserChain,java.util.Set<Integer>> targets;
        if(chainFlipStart.distance(e.getPoint())<4) {
            targets=new java.util.IdentityHashMap<>();UserChain last=latestChainWithEdge();
            if(last!=null)targets.put(last,java.util.Collections.singleton(last.getStrongRelations().size()-1));
        } else targets=chainEdgesInRectangle(dragRectangle(chainFlipStart,e.getPoint()));
        chainFlipStart=chainFlipCurrent=null;flipChainEdges(targets);repaint();return true;
    }
    private void flipChainEdges(Map<UserChain,java.util.Set<Integer>> targets) {
        if(targets.isEmpty())return;
        UserChain last=latestChainWithEdge();
        boolean tail=last!=null&&targets.containsKey(last)&&targets.get(last).contains(last.getStrongRelations().size()-1);
        pushUserChainUndo();
        Color[] palette=Options.getInstance().getColoringColors();
        for(Map.Entry<UserChain,java.util.Set<Integer>> entry:targets.entrySet())for(int edge:entry.getValue()) {
            UserChain chain=entry.getKey();boolean strong=!chain.getStrongRelations().get(edge);
            chain.getStrongRelations().set(edge,strong);
            if(edge<chain.getRelationColors().size()) {
                Color old=chain.getRelationColors().get(edge);int group=cellZoomPanel.getPaletteGroup();
                for(int i=0;i<palette.length;i++)if(palette[i].equals(old)){group=i/2;break;}
                chain.getRelationColors().set(edge,palette[group*2+(strong?0:1)]);
            }
            chain.setNextStrong(!chain.getStrongRelations().get(chain.getStrongRelations().size()-1));
        }
        if(tail)synchronizeNextChainEdge();
        noteUserChainReasoningChanged();mainFrame.check();repaint();
    }
    private void deleteExactChainEdges(Map<UserChain,java.util.Set<Integer>> targets) {
        if(targets.isEmpty())return;
        pushUserChainUndo();
        for(Map.Entry<UserChain,java.util.Set<Integer>> entry:targets.entrySet()) {
            UserChain original=entry.getKey();int insert=userChains.indexOf(original);
            if(insert<0)insert=userChains.size();userChains.remove(original);
            if(activeUserChain==original)activeUserChain=null;
            List<UserChain> fragments=UserChainCuts.removeEdges(original,entry.getValue());
            for(UserChain fragment:fragments) {
                fragment.setSourceId(nextUserChainSourceId++);
                if(fragment.isActive())activeUserChain=fragment;else userChains.add(Math.min(insert++,userChains.size()),fragment);
            }
        }
        synchronizeNextChainEdge();noteUserChainReasoningChanged();mainFrame.check();repaint();
    }

    private boolean beginChainDeleteDrag(MouseEvent event) {
        if(annotationTool!=AnnotationTool.FREE_CHAIN || !SudokuUtil.isDeletionModifierDown(event))return false;
        if(SwingUtilities.isRightMouseButton(event))return false;
        if(SwingUtilities.isLeftMouseButton(event)) {
            chainDeleteStart=chainDeleteCurrent=event.getPoint();noteAnnotationToolBoardAction(true);repaint();
        }
        return true;
    }

    private Map<UserChain,java.util.Set<Integer>> chainEdgesInRectangle(java.awt.Rectangle rectangle) {
        Map<UserChain, java.util.Set<Integer>> targets=new java.util.IdentityHashMap<UserChain, java.util.Set<Integer>>();
        List<UserChainSegment> segments=collectUserChainSegments();
        List<Point2D.Double> obstacles=collectUserChainObstacles();
        Map<Long,Integer> counts=new TreeMap<Long,Integer>(),ordinals=new TreeMap<Long,Integer>();
        for(UserChainSegment segment:segments){long key=userChainSegmentKey(segment.from,segment.to);counts.put(key,counts.containsKey(key)?counts.get(key)+1:1);}
        double diameter=lastUserChainRouteDiameter>0?lastUserChainRouteDiameter:Math.max(1.0,candidateHeight);
        double spacing=Math.min(Math.max(2.0,cellSize/28.0),cellSize/10.0);
        for(UserChainSegment segment:segments) {
            long key=userChainSegmentKey(segment.from,segment.to);int ordinal=ordinals.containsKey(key)?ordinals.get(key):0;ordinals.put(key,ordinal+1);
            ChainRouteGeometry.Route route=userChainRoute(segment,diameter,obstacles,
                    ChainRouteGeometry.boundedLaneOffset(ordinal,counts.get(key),spacing,diameter));
            java.awt.Shape path=route.isCurved()?new java.awt.geom.CubicCurve2D.Double(route.getStart().x,route.getStart().y,
                    route.getControl1().x,route.getControl1().y,route.getControl2().x,route.getControl2().y,route.getEnd().x,route.getEnd().y)
                    :new java.awt.geom.Line2D.Double(route.getStart(),route.getEnd());
            if(!new BasicStroke(Math.max(1.8f,cellSize/30.0f)).createStrokedShape(path).intersects(rectangle))continue;
            for(UserChain chain:userChains) collectHitChainEdge(targets,chain,segment);
            if(activeUserChain!=null) collectHitChainEdge(targets,activeUserChain,segment);
        }
        return targets;
    }
    private boolean finishChainDeleteDrag(MouseEvent event) {
        if(chainDeleteStart==null)return false;
        java.awt.Rectangle rectangle=dragRectangle(chainDeleteStart,event.getPoint());
        if(rectangle.width<1)rectangle.width=1;if(rectangle.height<1)rectangle.height=1;
        Map<UserChain,java.util.Set<Integer>> targets=chainEdgesInRectangle(rectangle);
        List<UserChain> all=new ArrayList<UserChain>(userChains);
        if(activeUserChain!=null)all.add(activeUserChain);
        Map<Integer,java.util.Set<Integer>> members=new java.util.HashMap<Integer,java.util.Set<Integer>>();
        for(UserChain chain:all)for(UserChainNode n:chain.getNodes())if(n.grouped() || chain.getNodes().size()==1) {
            java.util.Set<Integer> hit=new java.util.HashSet<Integer>();
            for(int member:n.cells())if(rectangle.contains(getCandKoord(member,n.getCandidate(),cellSize)))hit.add(member);
            if(!hit.isEmpty())members.put(n.identity(),hit);
        }
        if(!targets.isEmpty() || !members.isEmpty()) {
            pushUserChainUndo();
            for(UserChain original:all) {
                java.util.Set<Integer> removed=targets.get(original);
                boolean affected=removed!=null;
                for(UserChainNode n:original.getNodes())affected |= members.containsKey(n.identity());
                if(!affected)continue;
                if(removed==null)removed=new java.util.HashSet<Integer>();
                int insert=userChains.indexOf(original);if(insert<0)insert=userChains.size();
                userChains.remove(original);if(activeUserChain==original)activeUserChain=null;
                if(original.getSourceId()==confirmedUserChainSourceId)confirmedUserChainSourceId=0;
                for(UserChain fragment:UserChainCuts.removeMembersAndEdges(original,removed,members)) {
                    fragment.setSourceId(nextUserChainSourceId++);
                    if(fragment.isActive())activeUserChain=fragment;else userChains.add(Math.min(insert++,userChains.size()),fragment);
                }
            }
            synchronizeNextChainEdge();
            noteUserChainReasoningChanged();mainFrame.check();
        }
        chainDeleteStart=chainDeleteCurrent=null;repaint();return true;
    }

    private void collectHitChainEdge(Map<UserChain,java.util.Set<Integer>> hits,UserChain chain,UserChainSegment segment) {
        int n=chain.getNodes().size();
        for(int edge=0;edge<chain.getStrongRelations().size();edge++) {
            if(chain.getNodes().get(edge)==segment.from && chain.getNodes().get((edge+1)%n)==segment.to) {
                java.util.Set<Integer> indexes=hits.get(chain);
                if(indexes==null){indexes=new java.util.HashSet<Integer>();hits.put(chain,indexes);}
                indexes.add(edge);
            }
        }
    }

    private Point2D.Double[] userChainEndpoints(UserChainNode a,UserChainNode b) {
        Point2D.Double start=null,end=null;double best=Double.MAX_VALUE;
        for(int x:a.cells())for(int y:b.cells()) {
            Point2D.Double p=getCandKoord(x,a.getCandidate(),cellSize), q=getCandKoord(y,b.getCandidate(),cellSize);
            double d=p.distanceSq(q);if(d<best){best=d;start=p;end=q;}
        }
        return new Point2D.Double[]{start,end};
    }
    void showUserChainValidation(UserChainValidator.Result result) {
        invalidUserChainRelations.clear();invalidUserChainRelations.addAll(result.invalidRelations);repaint();
    }
    private ChainRouteGeometry.Route userChainRoute(UserChainSegment segment,double diameter,
            List<Point2D.Double> obstacles,double offset) {
        Point2D.Double[] ends=userChainEndpoints(segment.from,segment.to);
        Point2D.Double start=ends[0], end=ends[1];
        boolean forward=userChainNodeKey(segment.from)<userChainNodeKey(segment.to);
        ChainRouteGeometry.Route route=forward?ChainRouteGeometry.createLaneZero(start,end,diameter,obstacles)
                :ChainRouteGeometry.createLaneZero(end,start,diameter,obstacles);
        route=route.withLaneOffset(offset);return forward?route:route.reversed();
    }

    private void cancelDoodleGesture() {
        activeDoodleStroke = null; doodleGesture = DoodleGesture.NONE;
        doodleGestureStart = doodleGestureCurrent = null; doodleErasePreview = null;
    }

    private boolean beginDoodle(MouseEvent event) {
        if (annotationTool != AnnotationTool.DOODLE) return false;
        if (!isInsideDoodleRegion(event.getPoint())) return true;
        boolean deleting = SudokuUtil.isDeletionModifierDown(event);
        boolean right = SwingUtilities.isRightMouseButton(event);
        if (!right && !SwingUtilities.isLeftMouseButton(event)) return true;
        int forbidden = MouseEvent.SHIFT_DOWN_MASK | MouseEvent.ALT_GRAPH_DOWN_MASK
                | (SudokuUtil.isMacOS() ? MouseEvent.META_DOWN_MASK : MouseEvent.CTRL_DOWN_MASK);
        if ((event.getModifiersEx() & forbidden) != 0) return true;
        cancelDoodleGesture(); noteAnnotationToolBoardAction(true);
        doodleGestureStart = doodleGestureCurrent = event.getPoint();doodleDragged=false;
        if (deleting) {
            doodleGesture = right ? DoodleGesture.RECT_ERASER : DoodleGesture.CIRCLE_ERASER;
            doodleErasePreview = copyDoodles(doodleStrokes);
            if (!right) eraseDoodleCircle(event.getPoint());
        } else {
            doodleGesture = right ? DoodleGesture.ELLIPSE : DoodleGesture.PEN;
            Color color = event.isAltDown() ? cellZoomPanel.getSecondaryColor() : cellZoomPanel.getPrimaryColor();
            activeDoodleStroke = new DoodleStroke(color, doodleWidthFactor);
            activeDoodleStroke.getPoints().add(toDoodlePoint(event.getPoint()));
        }
        repaint();return true;
    }

    private void eraseDoodleCircle(Point point) {
        float diameter = 2 * doodleEraserRadius * Math.min(getWidth(), getHeight());
        java.awt.Shape sweep = new BasicStroke(diameter, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                .createStrokedShape(new java.awt.geom.Line2D.Double(doodleGestureCurrent, point));
        java.awt.geom.Area area = new java.awt.geom.Area(sweep);
        area.add(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(
                point.x-diameter/2,point.y-diameter/2,diameter,diameter)));
        doodleErasePreview = DoodleGeometry.subtract(doodleErasePreview,area,getWidth(),getHeight(), stroke -> doodleProjection(stroke,getWidth(),getHeight()));
    }

    private boolean extendDoodle(MouseEvent event) {
        if (doodleGesture == DoodleGesture.NONE) return false;
        Point point = event.getPoint();
        if(doodleGestureStart.distance(point)>=4)doodleDragged=true;
        if (doodleGesture == DoodleGesture.CIRCLE_ERASER) eraseDoodleCircle(point);
        doodleGestureCurrent = point;
        if (doodleGesture == DoodleGesture.PEN) activeDoodleStroke.getPoints().add(toDoodlePoint(point));
        else if (doodleGesture == DoodleGesture.ELLIPSE) {
            activeDoodleStroke.getPoints().clear();
            double cx=(doodleGestureStart.x+point.x)/2.0,cy=(doodleGestureStart.y+point.y)/2.0;
            double rx=Math.abs(doodleGestureStart.x-point.x)/2.0,ry=Math.abs(doodleGestureStart.y-point.y)/2.0;
            int count=Math.max(32,(int)Math.ceil(Math.PI*(rx+ry)));
            for(int i=0;i<=count;i++) {
                double angle=2*Math.PI*i/count;
                activeDoodleStroke.getPoints().add(new DoodlePoint((cx+rx*Math.cos(angle))/getWidth(),
                        (cy+ry*Math.sin(angle))/getHeight()));
            }
        }
        repaint();return true;
    }

    private boolean finishDoodle(MouseEvent event) {
        if (doodleGesture == DoodleGesture.NONE) return false;
        extendDoodle(event);
        if(doodleGesture==DoodleGesture.ELLIPSE && !doodleDragged) {
            activeDoodleStroke.getPoints().clear();
            int row=getRow(doodleGestureStart),col=getCol(doodleGestureStart);
            int digit=getCandidate(doodleGestureStart,row,col);
            if(isOnGrid(doodleGestureStart)&&Sudoku2.isValidIndex(row,col)&&digit>0&&isUserChainCandidateVisible(row*9+col,digit)) {
                double radius=(lastUserChainRouteDiameter>0?lastUserChainRouteDiameter:Math.max(10.0,cellSize/5.0))/2;
                activeDoodleStroke.setAnchorCell(row*9+col);
                activeDoodleStroke.setAnchorDigit(digit);
                activeDoodleStroke.setWidthFactor(doodleWidthFactor * Math.min(getWidth(),getHeight()) / Math.max(1,cellSize));
                for(int i=0;i<=48;i++){double angle=2*Math.PI*i/48;activeDoodleStroke.getPoints().add(new DoodlePoint(radius*Math.cos(angle)/cellSize,radius*Math.sin(angle)/cellSize));}
            }
        }
        if (doodleGesture == DoodleGesture.RECT_ERASER)
            doodleErasePreview=DoodleGeometry.subtract(doodleStrokes,
                    dragRectangle(doodleGestureStart,doodleGestureCurrent),getWidth(),getHeight(), stroke -> doodleProjection(stroke,getWidth(),getHeight()));
        if (doodleErasePreview != null) {
            if (!sameDoodles(doodleStrokes,doodleErasePreview)) {
                pushDoodleUndo();doodleStrokes.clear();doodleStrokes.addAll(doodleErasePreview);
            }
        } else if (getDoodleStrokeDistance(activeDoodleStroke)>=2.0/Math.max(1,Math.min(getWidth(),getHeight()))) {
            pushDoodleUndo();doodleStrokes.add(activeDoodleStroke);
        }
        cancelDoodleGesture();mainFrame.check();repaint();return true;
    }

    private static java.awt.Rectangle dragRectangle(Point a,Point b) {
        return new java.awt.Rectangle(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.abs(a.x-b.x),Math.abs(a.y-b.y));
    }

    private static boolean sameDoodles(List<DoodleStroke> a,List<DoodleStroke> b) {
        if(a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++) {
            if(a.get(i).getAnchorCell()!=b.get(i).getAnchorCell() || a.get(i).getAnchorDigit()!=b.get(i).getAnchorDigit())return false;
            List<DoodlePoint> ap=a.get(i).getPoints(),bp=b.get(i).getPoints();
            if(ap.size()!=bp.size())return false;
            for(int j=0;j<ap.size();j++) if(Math.abs(ap.get(j).getX()-bp.get(j).getX())>1e-9
                    || Math.abs(ap.get(j).getY()-bp.get(j).getY())>1e-9)return false;
        }
        return true;
    }

	private double getDoodleStrokeDistance(DoodleStroke stroke) {
		double distance = 0.0;
		for (int i = 1; i < stroke.getPoints().size(); i++) {
			DoodlePoint previous = stroke.getPoints().get(i - 1);
			DoodlePoint current = stroke.getPoints().get(i);
			distance += Math.hypot(current.getX() - previous.getX(), current.getY() - previous.getY());
		}
		return distance;
	}

	private boolean isInsideDoodleRegion(Point point) {
		return point.x >= 0 && point.y >= 0 && point.x < getWidth() && point.y < getHeight();
	}

	private DoodlePoint toDoodlePoint(Point point) {
		double x = Math.max(0.0, Math.min(1.0, point.x / (double) Math.max(1, getWidth())));
		double y = Math.max(0.0, Math.min(1.0, point.y / (double) Math.max(1, getHeight())));
		return new DoodlePoint(x, y);
	}

	private void pushDoodleUndo() {
        refreshAnnotationTimeline();
		if (doodleUndoStack.size() == ANNOTATION_UNDO_LIMIT) {
			doodleUndoStack.remove(0);
		}
		doodleUndoStack.push(copyDoodles(doodleStrokes));
		doodleRedoStack.clear();
	}

	private List<DoodleStroke> copyDoodles(List<DoodleStroke> source) {
		List<DoodleStroke> copy = new ArrayList<DoodleStroke>(source.size());
		for (DoodleStroke stroke : source) {
			copy.add(stroke.copy());
		}
		return copy;
	}

	private void undoDoodle() {
        cancelDoodleGesture();
		if (doodleUndoStack.isEmpty()) {
			return;
		}
		if (doodleRedoStack.size() == ANNOTATION_UNDO_LIMIT) {
			doodleRedoStack.remove(0);
		}
		doodleRedoStack.push(copyDoodles(doodleStrokes));
		doodleStrokes.clear();
		doodleStrokes.addAll(doodleUndoStack.pop());
		repaint();
	}

	private void redoDoodle() {
        cancelDoodleGesture();
		if (doodleRedoStack.isEmpty()) {
			return;
		}
		if (doodleUndoStack.size() == ANNOTATION_UNDO_LIMIT) {
			doodleUndoStack.remove(0);
		}
		doodleUndoStack.push(copyDoodles(doodleStrokes));
		doodleStrokes.clear();
		doodleStrokes.addAll(doodleRedoStack.pop());
		repaint();
	}

	public void clearDoodlesWithUndo() {
		clearDoodlesWithUndo(true);
	}

	private boolean clearDoodlesWithUndo(boolean refreshUi) {
		if (doodleStrokes.isEmpty()) {
			return false;
		}
		pushDoodleUndo();
		doodleStrokes.clear();
		if (refreshUi) repaint();
		return true;
	}

	private void cycleDoodleWidth(int rotation) {
		if (rotation == 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastDoodleWheelChange < 90L) {
			return;
		}
		lastDoodleWheelChange = now;
		adjustDoodleWidth(rotation > 0 ? 1 : -1);
	}

	private void adjustDoodleWidth(int delta) {
		setDoodleWidthIndex(doodleWidthIndex + delta);
		if (cellZoomPanel != null) {
			cellZoomPanel.updateDoodleWidthSelection(doodleWidthIndex);
		}
		repaint();
	}

	int getDoodleWidthIndex() { return doodleWidthIndex; }

	public void setDoodleWidthIndex(int index) {
		doodleWidthIndex = Math.max(0, Math.min(DOODLE_WIDTH_FACTORS.length - 1, index));
		doodleWidthFactor = DOODLE_WIDTH_FACTORS[doodleWidthIndex];
        if (cellZoomPanel != null) cellZoomPanel.updateDoodleWidthSelection(doodleWidthIndex);
	}

	public void setDoodlesVisible(boolean visible) {
		doodlesVisible = visible;
		repaint();
	}

	public void setUserChainsVisible(boolean visible) {
		userChainsVisible = visible;
		repaint();
	}

    boolean isNextUserChainStrong() { return nextUserChainStrong; }

	public boolean toggleNextUserChainStrong() {
        updateNextUserChainStrong(!nextUserChainStrong);
		noteUserChainReasoningChanged();
		repaint();
		return nextUserChainStrong;
	}

	public void setNextUserChainStrong(boolean strong) {
		if (nextUserChainStrong == strong) return;
		updateNextUserChainStrong(strong);
		if (activeUserChain != null) noteUserChainReasoningChanged();
	}

	private void updateNextUserChainStrong(boolean strong) {
		updateNextUserChainStrong(strong, true);
	}

	private void updateNextUserChainStrong(boolean strong, boolean repaintBoard) {
		nextUserChainStrong = strong;
		if (activeUserChain != null) activeUserChain.setNextStrong(strong);
		if (cellZoomPanel != null) {
			cellZoomPanel.updateChainRelationSelection(strong);
		}
        if (mainFrame != null) mainFrame.chainRelationUiChanged();
		if (repaintBoard) repaint();
	}

	public void setCurrentAnnotationVisible(boolean visible) {
		switch (annotationTool) {
		case DOODLE:
			setDoodlesVisible(visible);
			break;
		case FREE_CHAIN:
			setUserChainsVisible(visible);
			break;
		case BOX_SELECTION:
			boxReasoningVisible = visible;
			repaint();
			break;
		default:
			setColorsVisible(visible);
			repaint();
			break;
		}
	}

	public boolean isCurrentAnnotationVisible() {
		if (annotationTool == AnnotationTool.DOODLE) return doodlesVisible;
		if (annotationTool == AnnotationTool.FREE_CHAIN) return userChainsVisible;
		if (annotationTool == AnnotationTool.BOX_SELECTION) return boxReasoningVisible;
		return isColoringVisible;
	}

	private void drawBoxReasoningCellUnderlay(Graphics2D graphics, int cellIndex, int x, int y,
			SudokuAppearancePalette appearance, Color cellBackground) {
		if (!boxReasoningVisible || (!hasBoxReasoningCells() && boxDragPreview == null)) {
			return;
		}
		float annotationAlpha = annotationOpacity();
		for (int group = 0; group < boxReasoningGroups.size(); group++) {
			if (!isBoxReasoningCellPredicted(group, cellIndex) || !paintAnnotation("box:"+group+":"+cellIndex)) {
				continue;
			}
			Color color = Options.getInstance().getColoringColors()[group * 2];
			Color boundaryColor = appearance.getReasoningBoundaryColor(color, cellBackground);
			boolean active = group == activeBoxReasoningGroup;
			Graphics2D copy = (Graphics2D) graphics.create();
			try {
				if (active) {
					copy.setComposite(AlphaComposite.SrcOver.derive(0.06f * annotationAlpha));
					copy.setColor(color);
					copy.fillRect(x, y, cellSize, cellSize);
				}
				copy.setComposite(AlphaComposite.SrcOver.derive(annotationAlpha));
				float width = Math.max(1.4f, cellSize / 34.0f);
				if (active) {
					copy.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				} else {
					float dash = Math.max(2.0f, cellSize / 14.0f);
					copy.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
							10.0f, new float[] { dash, dash * 0.7f }, 0.0f));
				}
				copy.setColor(boundaryColor);
				int inset = Math.min(cellSize / 5, 2 + group * Math.max(1, cellSize / 45));
				int left = x + inset;
				int right = x + cellSize - inset;
				int top = y + inset;
				int bottom = y + cellSize - inset;
				int row = Sudoku2.getRow(cellIndex);
				int col = Sudoku2.getCol(cellIndex);
				if (row == 0 || !isBoxReasoningCellPredicted(group, cellIndex - 9)) {
					copy.drawLine(left, top, right, top);
				}
				if (row == 8 || !isBoxReasoningCellPredicted(group, cellIndex + 9)) {
					copy.drawLine(left, bottom, right, bottom);
				}
				if (col == 0 || !isBoxReasoningCellPredicted(group, cellIndex - 1)) {
					copy.drawLine(left, top, left, bottom);
				}
				if (col == 8 || !isBoxReasoningCellPredicted(group, cellIndex + 1)) {
					copy.drawLine(right, top, right, bottom);
				}
			} finally {
				copy.dispose();
			}
		}
		drawBoxReasoningComponentBadge(graphics, cellIndex, x, y, appearance, cellBackground);
	}

	private boolean hasBoxReasoningCells() {
		for (SudokuSet group : boxReasoningGroups) {
			if (!group.isEmpty()) return true;
		}
		return false;
	}

	private void drawBoxReasoningComponentBadge(Graphics2D graphics, int cellIndex, int x, int y,
			SudokuAppearancePalette appearance, Color cellBackground) {
		List<Integer> anchoredGroups = new ArrayList<Integer>(6);
		for (int group = 0; group < boxReasoningGroups.size(); group++) {
			if (isBoxReasoningComponentAnchor(group, cellIndex) && paintAnnotation("box:"+group+":"+cellIndex)) {
				anchoredGroups.add(Integer.valueOf(group));
			}
		}
		if (anchoredGroups.isEmpty()) return;

		Graphics2D copy = (Graphics2D) graphics.create();
		try {
			if (step != null) {
				copy.setComposite(AlphaComposite.SrcOver.derive(annotationOpacity()));
			}
			copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (anchoredGroups.size() > 1 || cellSize < 48) {
				drawStackedBoxReasoningBadge(copy, anchoredGroups, x, y, appearance, cellBackground);
			} else {
				drawSingleBoxReasoningBadge(copy, anchoredGroups.get(0).intValue(), x, y);
			}
		} finally {
			copy.dispose();
		}
	}

	private boolean isBoxReasoningComponentAnchor(int group, int cellIndex) {
		if (!isBoxReasoningCellPredicted(group, cellIndex)) return false;
		boolean[] visited = new boolean[Sudoku2.LENGTH];
		int[] pending = new int[Sudoku2.LENGTH];
		int pendingSize = 1;
		pending[0] = cellIndex;
		visited[cellIndex] = true;
		int minimum = cellIndex;
		while (pendingSize > 0) {
			int current = pending[--pendingSize];
			minimum = Math.min(minimum, current);
			int row = Sudoku2.getRow(current);
			int col = Sudoku2.getCol(current);
			if (row > 0) pendingSize = addBoxReasoningNeighbor(
					group, current - Sudoku2.UNITS, visited, pending, pendingSize);
			if (row < Sudoku2.UNITS - 1) pendingSize = addBoxReasoningNeighbor(
					group, current + Sudoku2.UNITS, visited, pending, pendingSize);
			if (col > 0) pendingSize = addBoxReasoningNeighbor(
					group, current - 1, visited, pending, pendingSize);
			if (col < Sudoku2.UNITS - 1) pendingSize = addBoxReasoningNeighbor(
					group, current + 1, visited, pending, pendingSize);
		}
		return minimum == cellIndex;
	}

	private int addBoxReasoningNeighbor(int group, int neighbor, boolean[] visited,
			int[] pending, int pendingSize) {
		if (!visited[neighbor] && isBoxReasoningCellPredicted(group, neighbor)) {
			visited[neighbor] = true;
			pending[pendingSize++] = neighbor;
		}
		return pendingSize;
	}

	private void drawSingleBoxReasoningBadge(Graphics2D graphics, int group, int x, int y) {
		int size = Math.max(10, Math.min(18, cellSize / 6));
		int badgeX = x + cellSize / 3 - size / 2;
		int badgeY = y + Math.max(2, cellSize / 40);
		Color color = Options.getInstance().getColoringColors()[group * 2];
		graphics.setColor(color);
		graphics.fillRoundRect(badgeX, badgeY, size, size, Math.max(4, size / 2), Math.max(4, size / 2));
		drawBoxReasoningBadgeLetter(graphics, Character.toString((char) ('A' + group)),
				color, badgeX, badgeY, size, size);
	}

	private void drawStackedBoxReasoningBadge(Graphics2D graphics, List<Integer> groups, int x, int y,
			SudokuAppearancePalette appearance, Color cellBackground) {
		Color[] palette = Options.getInstance().getColoringColors();
		int tickLength = Math.max(5, cellSize / 8);
		float tickWidth = Math.max(1.8f, cellSize / 36.0f);
		graphics.setStroke(new BasicStroke(tickWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (int position = 0; position < groups.size(); position++) {
			int group = groups.get(position).intValue();
			graphics.setColor(appearance.getReasoningBoundaryColor(
					palette[group * 2], cellBackground));
			boolean top = position < 3;
			int slot = top ? position : position - 3;
			int centerX = x + (2 * slot + 1) * cellSize / 6;
			int tickY = top ? y + Math.max(2, cellSize / 40)
					: y + cellSize - Math.max(2, cellSize / 40);
			graphics.drawLine(centerX - tickLength / 2, tickY, centerX + tickLength / 2, tickY);
		}

		int columns = Math.min(3, groups.size());
		int rows = (groups.size() + columns - 1) / columns;
		int segmentWidth = Math.max(7, Math.min(12, cellSize / 8));
		int segmentHeight = Math.max(7, Math.min(11, cellSize / 9));
		int badgeWidth = segmentWidth * columns;
		int badgeHeight = segmentHeight * rows;
		int badgeX = x + (cellSize - badgeWidth) / 2;
		int badgeY = y + Math.max(2, cellSize / 3 - badgeHeight / 2);
		for (int position = 0; position < groups.size(); position++) {
			int group = groups.get(position).intValue();
			int segmentX = badgeX + (position % columns) * segmentWidth;
			int segmentY = badgeY + (position / columns) * segmentHeight;
			Color color = palette[group * 2];
			graphics.setColor(color);
			graphics.fillRect(segmentX, segmentY, segmentWidth, segmentHeight);
			drawBoxReasoningBadgeLetter(graphics, Character.toString((char) ('A' + group)),
					color, segmentX, segmentY, segmentWidth, segmentHeight);
		}
	}

	private void drawBoxReasoningBadgeLetter(Graphics2D graphics, String label, Color background,
			int x, int y, int width, int height) {
		float fontSize = Math.max(6.0f, Math.min(11.0f, height * 0.78f));
		Font base = candidateFont == null ? getFont() : candidateFont;
		graphics.setFont(base.deriveFont(Font.BOLD, fontSize));
		FontMetrics metrics = graphics.getFontMetrics();
		int textX = x + (width - metrics.stringWidth(label)) / 2;
		int textY = y + (height + metrics.getAscent() - metrics.getDescent()) / 2;
		graphics.setColor(boxReasoningBadgeForeground(background));
		graphics.drawString(label, textX, textY);
	}

	private Color boxReasoningBadgeForeground(Color background) {
		double luminance = 0.2126 * linearBoxReasoningColor(background.getRed() / 255.0)
				+ 0.7152 * linearBoxReasoningColor(background.getGreen() / 255.0)
				+ 0.0722 * linearBoxReasoningColor(background.getBlue() / 255.0);
		double blackContrast = (luminance + 0.05) / 0.05;
		double whiteContrast = 1.05 / (luminance + 0.05);
		return blackContrast >= whiteContrast ? Color.BLACK : Color.WHITE;
	}

	private double linearBoxReasoningColor(double value) {
		return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	private boolean isBoxReasoningCellPredicted(int group, int cellIndex) {
		if (cellIndex < 0 || cellIndex >= Sudoku2.LENGTH) {
			return false;
		}
		boolean selected = boxReasoningGroups.get(group).contains(cellIndex);
		if (boxDragPreview != null && !boxRightToggle && boxDragPreview.contains(cellIndex)) {
            if (boxDragRemoves) return false;
            boolean existing = false;
            for (SudokuSet cells : boxReasoningGroups) existing |= cells.contains(cellIndex);
            return !existing && group == boxDragGroup;
		}
		return selected;
	}

	private void drawBoxReasoningDragRubberBand(Graphics2D graphics) {
		if (boxDragStart == null || boxDragCurrent == null || boxDragStart.distance(boxDragCurrent) < 4.0) {
			return;
		}
		int x = Math.min(boxDragStart.x, boxDragCurrent.x);
		int y = Math.min(boxDragStart.y, boxDragCurrent.y);
		int width = Math.abs(boxDragStart.x - boxDragCurrent.x);
		int height = Math.abs(boxDragStart.y - boxDragCurrent.y);
		Graphics2D copy = (Graphics2D) graphics.create();
		try {
			Color color = Options.getInstance().getColoringColors()[boxDragGroup * 2];
			copy.setColor(color);
			copy.setStroke(new BasicStroke(Math.max(1.2f, cellSize / 42.0f), BasicStroke.CAP_BUTT,
					BasicStroke.JOIN_MITER, 10.0f, new float[] { 5.0f, 4.0f }, 0.0f));
			copy.drawRect(x, y, width, height);
            if (boxDragRemoves) {copy.drawLine(x,y,x+width,y+height);copy.drawLine(x+width,y,x,y+height);}
		} finally {
			copy.dispose();
		}
	}

	private void drawDoodles(Graphics2D graphics, int width, int height, boolean boardOnly) {
		if (!doodlesVisible || (doodleStrokes.isEmpty() && activeDoodleStroke == null)) {
			return;
		}
		Graphics2D copy = (Graphics2D) graphics.create();
		try {
			if (step != null) {
				copy.setComposite(AlphaComposite.SrcOver.derive(annotationOpacity()));
			}
			if (boardOnly) {
				copy.clip(gridRegion);
			}
			for (DoodleStroke stroke : doodleErasePreview != null ? doodleErasePreview : doodleStrokes) {
                if(!paintAnnotation(doodleTimeKey(stroke)))continue;
				drawDoodleStroke(copy, stroke, width, height);
			}
			if (activeDoodleStroke != null && (step==null || annotationPaintLayer==2)) {
                copy.setComposite(AlphaComposite.SrcOver);
				drawDoodleStroke(copy, activeDoodleStroke, width, height);
			}
		} finally {
			copy.dispose();
		}
	}

    private java.awt.geom.AffineTransform doodleProjection(DoodleStroke stroke, int width, int height) {
        if (!stroke.isCandidateAnchored())
            return java.awt.geom.AffineTransform.getScaleInstance(Math.max(1,width),Math.max(1,height));
        if (!isUserChainCandidateVisible(stroke.getAnchorCell(),stroke.getAnchorDigit())) return null;
        Point2D.Double center = getCandKoord(stroke.getAnchorCell(),stroke.getAnchorDigit(),cellSize);
        java.awt.geom.AffineTransform projection = java.awt.geom.AffineTransform.getTranslateInstance(center.x,center.y);
        projection.scale(Math.max(1,cellSize),Math.max(1,cellSize));
        return projection;
    }

    private void drawDoodleStroke(Graphics2D graphics, DoodleStroke stroke, int width, int height) {
        if (stroke == null || stroke.getPoints().size() < 2) return;
        java.awt.geom.AffineTransform projection = doodleProjection(stroke,width,height);
        if (projection == null) return;
        Path2D.Double path = new Path2D.Double();
        DoodlePoint first = stroke.getPoints().get(0);
        path.moveTo(first.getX(),first.getY());
        for (int i=1;i<stroke.getPoints().size();i++) {
            DoodlePoint point=stroke.getPoints().get(i);path.lineTo(point.getX(),point.getY());
        }
        float scale = stroke.isCandidateAnchored() ? cellSize : Math.min(width,height);
        graphics.setColor(stroke.getColor());
        graphics.setStroke(new BasicStroke(Math.max(2.0f,scale*stroke.getWidthFactor()),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        graphics.draw(projection.createTransformedShape(path));
    }

	private boolean handleFreeChainMousePressed(MouseEvent event) {
		if (annotationTool != AnnotationTool.FREE_CHAIN) {
			return false;
		}
		if (SwingUtilities.isRightMouseButton(event)) {
			noteAnnotationToolBoardAction(true);
            clearPreciseChainGesture();
            if(event.isShiftDown()) {
                if(activeUserChain!=null) finishActiveUserChain(activeUserChain.isClosed(),true,true);
            } else { chainRightDelete=event.isControlDown();chainRightDragged=false;chainFlipStart=chainFlipCurrent=event.getPoint();repaint(); }
			return true;
		}
		if (!SwingUtilities.isLeftMouseButton(event)) {
			noteAnnotationToolBoardAction(true);
			return true;
		}
		noteAnnotationToolBoardAction(true);
		int row = getRow(event.getPoint());
		int col = getCol(event.getPoint());
		int candidate = getCandidate(event.getPoint(), row, col);
		if (!Sudoku2.isValidIndex(row, col) || candidate < 1) {
			return true;
		}
		int index = Sudoku2.getIndex(row, col);
		if (!isUserChainCandidateVisible(index, candidate)) {
			return true;
		}
        boolean restarted=event.isAltDown() && activeUserChain!=null;
        if(restarted){pushUserChainUndo();finishActiveUserChain(activeUserChain.isClosed(),false,true);}
		int paletteGroup = cellZoomPanel.getPaletteGroup();
		Color[] palette = Options.getInstance().getColoringColors();
		UserChainNode node = new UserChainNode(index, candidate,
				palette[paletteGroup * 2]);
		if (activeUserChain == null) {
			if(!restarted)pushUserChainUndo();
			confirmedUserChainSourceId = 0L;
			activeUserChain = new UserChain();
			activeUserChain.setActive(true);
			activeUserChain.setNextStrong(nextUserChainStrong);
			activeUserChain.getNodes().add(node);
			noteUserChainReasoningChanged();
			mainFrame.check();
			repaint();
			return true;
		}
		int nodeCount = activeUserChain.getNodes().size();
		if (activeUserChain.isClosed()) {
			return true;
		}
        if (event.isShiftDown()) {
            for(UserChainNode existing:activeUserChain.getNodes())
                if(existing.contains(index,candidate))return true;
            UserChainNode last=activeUserChain.getNodes().get(nodeCount-1);
            String problem=null;
            if(candidate!=last.getCandidate())problem="digit";
            else if(last.cells().length>=3)problem="size";
            UserChainNode expanded=last.copy();
            int[] members=java.util.Arrays.copyOf(last.cells(),last.cells().length+1);
            members[members.length-1]=index;expanded.setGroupCells(members);
            if(problem==null && !expanded.validShape())problem="shape";
            if(problem!=null){mainFrame.announceReasoningStatus("MainFrame.currentReasoning.group."+problem);return true;}
            pushUserChainUndo();activeUserChain.getNodes().set(nodeCount-1,expanded);
            noteUserChainReasoningChanged();mainFrame.check();repaint();return true;
        }
		if (sameUserChainNode(activeUserChain.getNodes().get(nodeCount - 1), node)) {
			return true;
		}
		if (sameUserChainNode(activeUserChain.getNodes().get(0), node) && nodeCount >= 3) {
			pushUserChainUndo();
			activeUserChain.getStrongRelations().add(Boolean.valueOf(nextUserChainStrong));
			activeUserChain.getRelationColors().add(palette[paletteGroup * 2
					+ (nextUserChainStrong ? 0 : 1)]);
			activeUserChain.setClosed(true);
			updateNextUserChainStrong(!nextUserChainStrong);
			noteUserChainReasoningChanged();
			mainFrame.check();
			repaint();
			return true;
		}
		for (UserChainNode existing : activeUserChain.getNodes()) {
			if (sameUserChainNode(existing, node)) {
				return true;
			}
		}
		pushUserChainUndo();
		activeUserChain.getStrongRelations().add(Boolean.valueOf(nextUserChainStrong));
		Color relationColor = palette[paletteGroup * 2 + (nextUserChainStrong ? 0 : 1)];
		activeUserChain.getRelationColors().add(relationColor);
		node.setColor(relationColor);
		activeUserChain.getNodes().add(node);
		updateNextUserChainStrong(!nextUserChainStrong);
		noteUserChainReasoningChanged();
		mainFrame.check();
		repaint();
		return true;
	}

	private static List<SudokuSet> createEmptyBoxReasoningGroups() {
		List<SudokuSet> groups = new ArrayList<SudokuSet>(6);
		for (int i = 0; i < 6; i++) {
			groups.add(new SudokuSet());
		}
		return groups;
	}

	private boolean beginBoxReasoningDrag(MouseEvent event) {
		if (annotationTool != AnnotationTool.BOX_SELECTION) {
			return false;
		}
		boolean right=SwingUtilities.isRightMouseButton(event);
        if(!SwingUtilities.isLeftMouseButton(event)&&!right)return true;
        if(right && event.getModifiersEx() != java.awt.event.InputEvent.BUTTON3_DOWN_MASK && event.getModifiersEx()!=0)return true;
        cancelBoxReasoningDrag();boxRightToggle=right;boxRightDragged=false;
		boxDragStart = event.getPoint();
		boxDragCurrent = event.getPoint();
		boxDragRemoves = SudokuUtil.isDeletionModifierDown(event);
		boxDragGroup = activeBoxReasoningGroup;
		boxDragPreview = calculateBoxReasoningDragCells(boxDragCurrent);
		noteAnnotationToolBoardAction(true);
		repaint();
		return true;
	}

	private boolean extendBoxReasoningDrag(MouseEvent event) {
		if (boxDragStart == null) {
			return annotationTool == AnnotationTool.BOX_SELECTION;
		}
		boxDragCurrent = event.getPoint();
        if(boxRightToggle && boxDragStart.distance(boxDragCurrent)>=4)boxRightDragged=true;
		boxDragPreview = calculateBoxReasoningDragCells(boxDragCurrent);
		repaint();
		return true;
	}

	private boolean finishBoxReasoningDrag(MouseEvent event) {
		if (boxDragStart == null) {
			return annotationTool == AnnotationTool.BOX_SELECTION;
		}
		boxDragCurrent = event.getPoint();
		SudokuSet affected = calculateBoxReasoningDragCells(boxDragCurrent);
        boolean changed = false;
        List<SudokuSet> after = copyBoxReasoningGroups(boxReasoningGroups);
        if(boxRightToggle) {
            if(boxRightDragged || boxDragStart.distance(boxDragCurrent)>=4 || getRow(boxDragStart)!=getRow(boxDragCurrent) || getCol(boxDragStart)!=getCol(boxDragCurrent))affected.clear();
            boolean existing=false;for(SudokuSet group:after)for(int i=0;i<affected.size();i++)existing |= group.contains(affected.get(i));
            if(existing){for(SudokuSet group:after)group.andNot(affected);}else after.get(boxDragGroup).or(affected);
        } else if (boxDragRemoves) {
            for (SudokuSet group : after) group.andNot(affected);
        } else {
            for (int i = 0; i < affected.size(); i++) {
                int cell = affected.get(i);
                boolean existing = false;
                for (SudokuSet group : after) existing |= group.contains(cell);
                if (existing) {
                    for (SudokuSet group : after) group.remove(cell);
                } else after.get(boxDragGroup).add(cell);
            }
        }
        for(int i=0;i<after.size();i++) if(!after.get(i).equals(boxReasoningGroups.get(i))) changed=true;
        if(changed) {
            pushBoxReasoningUndo();boxReasoningGroups.clear();boxReasoningGroups.addAll(after);
            boxReasoningRevision++;noteBoxReasoningChanged();
            mainFrame.announceReasoningStatus("MainFrame.reasoning.boxCommitted");mainFrame.check();
        }

		clearBoxReasoningDragState();
		repaint();
		return true;
	}

	private SudokuSet calculateBoxReasoningDragCells(Point current) {
		SudokuSet result = new SudokuSet();
		if (boxDragStart == null || current == null) {
			return result;
		}
		if (boxDragStart.distance(current) < 4.0) {
			int row = getRow(current);
			int col = getCol(current);
			if (isOnGrid(current) && Sudoku2.isValidIndex(row, col)) {
				result.add(Sudoku2.getIndex(row, col));
			}
			return result;
		}
		double minX = Math.min(boxDragStart.x, current.x);
		double maxX = Math.max(boxDragStart.x, current.x);
		double minY = Math.min(boxDragStart.y, current.y);
		double maxY = Math.max(boxDragStart.y, current.y);
		for (int row = 0; row < Sudoku2.UNITS; row++) {
			for (int col = 0; col < Sudoku2.UNITS; col++) {
				double centerX = getX(row, col) + cellSize / 2;
				double centerY = getY(row, col) + cellSize / 2;
				if (centerX >= minX && centerX <= maxX && centerY >= minY && centerY <= maxY) {
					result.add(Sudoku2.getIndex(row, col));
				}
			}
		}
		return result;
	}

	private void cancelBoxReasoningDrag() {
		if (boxDragStart == null) {
			return;
		}
		clearBoxReasoningDragState();
		finishAnnotationToolPointerGesture();
		repaint();
	}

	private void clearBoxReasoningDragState() {
		boxDragStart = null;
		boxDragCurrent = null;
		boxDragPreview = null;
		boxDragRemoves = false;
		boxDragGroup = 0;
	}

	private void pushBoxReasoningUndo() {
        refreshAnnotationTimeline();
		if (boxReasoningUndoStack.size() == ANNOTATION_UNDO_LIMIT) {
			boxReasoningUndoStack.remove(0);
		}
		boxReasoningUndoStack.push(copyBoxReasoningGroups(boxReasoningGroups));
		boxReasoningRedoStack.clear();
	}

	private List<SudokuSet> copyBoxReasoningGroups(List<SudokuSet> source) {
		List<SudokuSet> copy = createEmptyBoxReasoningGroups();
		for (int i = 0; i < Math.min(copy.size(), source.size()); i++) {
			if (source.get(i) != null) {
				copy.set(i, source.get(i).clone());
			}
		}
		return copy;
	}

	private void restoreBoxReasoningGroups(List<SudokuSet> snapshot) {
		boxReasoningGroups.clear();
		boxReasoningGroups.addAll(copyBoxReasoningGroups(snapshot));
		boxReasoningRevision++;
		noteBoxReasoningChanged();
	}

	private void undoBoxReasoning() {
		if (boxReasoningUndoStack.isEmpty()) {
			return;
		}
		if (boxReasoningRedoStack.size() == ANNOTATION_UNDO_LIMIT) {
			boxReasoningRedoStack.remove(0);
		}
		boxReasoningRedoStack.push(copyBoxReasoningGroups(boxReasoningGroups));
		restoreBoxReasoningGroups(boxReasoningUndoStack.pop());
		repaint();
	}

	private void redoBoxReasoning() {
		if (boxReasoningRedoStack.isEmpty()) {
			return;
		}
		if (boxReasoningUndoStack.size() == ANNOTATION_UNDO_LIMIT) {
			boxReasoningUndoStack.remove(0);
		}
		boxReasoningUndoStack.push(copyBoxReasoningGroups(boxReasoningGroups));
		restoreBoxReasoningGroups(boxReasoningRedoStack.pop());
		repaint();
	}

	public void clearBoxReasoningWithUndo() {
		clearBoxReasoningWithUndo(true);
	}

	private boolean clearBoxReasoningWithUndo(boolean refreshUi) {
		if (getBoxReasoningFootprint().isEmpty()) {
			return false;
		}
		pushBoxReasoningUndo();
		for (SudokuSet group : boxReasoningGroups) {
			group.clear();
		}
		boxReasoningRevision++;
		noteBoxReasoningChanged();
		if (refreshUi) {
			mainFrame.check();
			repaint();
		}
		return true;
	}

	SudokuSet getBoxReasoningFootprint() {
		SudokuSet footprint = new SudokuSet();
		for (SudokuSet group : boxReasoningGroups) {
			footprint.or(group);
		}
		return footprint;
	}

	List<SudokuSet> getBoxReasoningGroupsSnapshot() {
		return copyBoxReasoningGroups(boxReasoningGroups);
	}

	void setActiveBoxReasoningGroup(int group) {
		activeBoxReasoningGroup = Math.max(0, Math.min(5, group));
        if (mainFrame != null) mainFrame.updateCellSelectionStatus();
		repaint();
	}

    /** ALS counts use the union of candidates in unsolved cells, never occurrences or values. */
    int[] getBoxReasoningCounts(int group) {
        int cells = 0, digits = 0;
        for (int i = 0; i < boxReasoningGroups.get(group).size(); i++) {
            int cell = boxReasoningGroups.get(group).get(i);
            if (sudoku.getValue(cell) != 0) continue;
            cells++;
            for (int digit : sudoku.getAllCandidates(cell)) digits |= 1 << digit;
        }
        return new int[] { cells, Integer.bitCount(digits) };
    }

	int getActiveBoxReasoningGroup() {
		return activeBoxReasoningGroup;
	}

	private boolean sameUserChainNode(UserChainNode first, UserChainNode second) {
		return first.contains(second.getCellIndex(), second.getCandidate());
	}

	private UserChain finishActiveUserChain(boolean closed, boolean pushHistory) {
        return finishActiveUserChain(closed, pushHistory, false);
    }

    private UserChain finishActiveUserChain(boolean closed, boolean pushHistory, boolean allowSingleNode) {
        if (activeUserChain == null || activeUserChain.getNodes().size() < (allowSingleNode ? 1 : 2)) {
			return null;
		}
		if (pushHistory) {
			pushUserChainUndo();
		}
		activeUserChain.setClosed(closed);
		activeUserChain.setActive(false);
		activeUserChain.setAnalysisResult("PENDING_NATIVE_MATCH");
		if (activeUserChain.getSourceId() == 0L) {
			activeUserChain.setSourceId(nextUserChainSourceId++);
		}
		if (cellZoomPanel != null) cellZoomPanel.updateChainAnalysis(activeUserChain.getAnalysisResult());
		UserChain completed = activeUserChain;
		userChains.add(completed);
		confirmedUserChainSourceId = completed.getSourceId();
		activeUserChain = null;
		updateNextUserChainStrong(true);
		noteUserChainReasoningChanged();
		mainFrame.check();
		repaint();
		return completed;
	}

	private void removeLastUserChainNode() {
        if(activeUserChain==null) {
            UserChain last=latestChainWithEdge();if(last==null)return;
            Map<UserChain,java.util.Set<Integer>> targets=new java.util.IdentityHashMap<>();
            targets.put(last,java.util.Collections.singleton(last.getStrongRelations().size()-1));
            deleteExactChainEdges(targets);return;
        }
		if (activeUserChain.isClosed()) {
			pushUserChainUndo();
			activeUserChain.setClosed(false);
			int relationIndex = activeUserChain.getStrongRelations().size() - 1;
			boolean restoredRelation = activeUserChain.getStrongRelations().remove(relationIndex).booleanValue();
			if (relationIndex < activeUserChain.getRelationColors().size()) {
				activeUserChain.getRelationColors().remove(relationIndex);
			}
			updateNextUserChainStrong(restoredRelation);
			noteUserChainReasoningChanged();
			mainFrame.check();
			repaint();
			return;
		}
		int last = activeUserChain.getNodes().size() - 1;
		if (last < 0) {
			return;
		}
		pushUserChainUndo();
		activeUserChain.getNodes().remove(last);
		if (!activeUserChain.getStrongRelations().isEmpty()) {
			int relationIndex = activeUserChain.getStrongRelations().size() - 1;
			boolean restoredRelation = activeUserChain.getStrongRelations().remove(relationIndex).booleanValue();
			if (relationIndex < activeUserChain.getRelationColors().size()) {
				activeUserChain.getRelationColors().remove(relationIndex);
			}
			updateNextUserChainStrong(restoredRelation);
		}
		if (activeUserChain.getNodes().isEmpty()) {
			activeUserChain = null;
			updateNextUserChainStrong(true);
		}
		noteUserChainReasoningChanged();
		mainFrame.check();
		repaint();
	}

	private void deleteUserChainAt(Point point) {
		for (int i = userChains.size() - 1; i >= 0; i--) {
			if (userChainContainsPoint(userChains.get(i), point)) {
				pushUserChainUndo();
				UserChain removed = userChains.remove(i);
				if (removed.getSourceId() == confirmedUserChainSourceId) {
					confirmedUserChainSourceId = 0L;
				}
				noteUserChainReasoningChanged();
				mainFrame.check();
				repaint();
				return;
			}
		}
	}

	private boolean userChainContainsPoint(UserChain chain, Point point) {
		if (isUserChainReplacedByReasoningProposal(chain)) {
			return false;
		}
		for (UserChainNode node : chain.getNodes()) {
			for(int member:node.cells()) {
                Point2D.Double center = getCandKoord(member, node.getCandidate(), cellSize);
                if(center.distance(point)<=Math.max(8,cellSize/6))return true;
            }
		}
		return false;
	}

	private void pushUserChainUndo() {
        refreshAnnotationTimeline();
		if (userChainUndoStack.size() == ANNOTATION_UNDO_LIMIT) {
			userChainUndoStack.remove(0);
		}
		userChainUndoStack.push(snapshotUserChains());
		userChainRedoStack.clear();
	}

	private List<UserChain> snapshotUserChains() {
		List<UserChain> snapshot = copyUserChains(userChains);
        if(activeUserChain==null && !snapshot.isEmpty())snapshot.get(snapshot.size()-1).setNextStrong(nextUserChainStrong);
		if (activeUserChain != null) {
			UserChain active = copyUserChain(activeUserChain);
			active.setActive(true);
			snapshot.add(active);
		}
		return snapshot;
	}

	private List<UserChain> copyUserChains(List<UserChain> source) {
		List<UserChain> copy = new ArrayList<UserChain>(source.size());
		for (UserChain chain : source) {
			copy.add(copyUserChain(chain));
		}
		return copy;
	}

	private UserChain copyUserChain(UserChain chain) {
		UserChain clone = new UserChain();
		clone.setClosed(chain.isClosed());
		clone.setActive(chain.isActive());
		clone.setAnalysisResult(chain.getAnalysisResult());
		clone.setNextStrong(chain.isNextStrong());
		clone.setSourceId(chain.getSourceId());
		for (UserChainNode node : chain.getNodes()) {
			clone.getNodes().add(node.copy());
		}
		clone.getStrongRelations().addAll(chain.getStrongRelations());
		clone.getRelationColors().addAll(chain.getRelationColors());
		return clone;
	}

	private void restoreUserChainSnapshot(List<UserChain> snapshot) {
		userChains.clear();
		activeUserChain = null;
		for (UserChain chain : copyUserChains(snapshot)) {
			if (chain.isActive()) {
				chain.setActive(true);
				activeUserChain = chain;
				nextUserChainStrong = chain.isNextStrong();
			} else {
				userChains.add(chain);
			}
		}
		if (activeUserChain == null) nextUserChainStrong = userChains.isEmpty() || userChains.get(userChains.size()-1).isNextStrong();
		highestCompletedUserChainSourceId();
		confirmedUserChainSourceId = 0L;
		if (cellZoomPanel != null) cellZoomPanel.updateChainRelationSelection(nextUserChainStrong);
        if (mainFrame != null) mainFrame.chainRelationUiChanged();
	}

	private long highestCompletedUserChainSourceId() {
		long highest = 0L;
		for (UserChain chain : userChains) {
			highest = Math.max(highest, chain.getSourceId());
		}
		nextUserChainSourceId = Math.max(nextUserChainSourceId, highest + 1L);
		for (UserChain chain : userChains) {
			if (chain.getSourceId() <= 0L) {
				chain.setSourceId(nextUserChainSourceId++);
			}
			highest = Math.max(highest, chain.getSourceId());
		}
		return highest;
	}

	private void undoUserChains() {
		if (userChainUndoStack.isEmpty()) {
			return;
		}
		if (userChainRedoStack.size() == ANNOTATION_UNDO_LIMIT) {
			userChainRedoStack.remove(0);
		}
		userChainRedoStack.push(snapshotUserChains());
		restoreUserChainSnapshot(userChainUndoStack.pop());
		noteUserChainReasoningChanged();
		repaint();
	}

	private void redoUserChains() {
		if (userChainRedoStack.isEmpty()) {
			return;
		}
		if (userChainUndoStack.size() == ANNOTATION_UNDO_LIMIT) {
			userChainUndoStack.remove(0);
		}
		userChainUndoStack.push(snapshotUserChains());
		restoreUserChainSnapshot(userChainRedoStack.pop());
		noteUserChainReasoningChanged();
		repaint();
	}

	public void clearUserChainsWithUndo() {
		clearUserChainsWithUndo(true);
	}

	private boolean clearUserChainsWithUndo(boolean refreshUi) {
		if (userChains.isEmpty() && activeUserChain == null) {
			return false;
		}
		pushUserChainUndo();
		userChains.clear();
		activeUserChain = null;
		updateNextUserChainStrong(true, refreshUi);
		confirmedUserChainSourceId = 0L;
		noteUserChainReasoningChanged();
		if (refreshUi) {
			mainFrame.check();
			repaint();
		}
		return true;
	}

	private void drawUserChains(Graphics2D graphics, SudokuAppearancePalette appearance,
			double candidateDiameter) {
		if (!userChainsVisible || (userChains.isEmpty() && activeUserChain == null)) {
			return;
		}
		Graphics2D copy = (Graphics2D) graphics.create();
		try {
			if (step != null) {
				copy.setComposite(AlphaComposite.SrcOver.derive(annotationOpacity()));
			}
			drawUserChainGroupBands(copy, appearance, candidateDiameter);
            List<UserChainSegment> segments = collectUserChainSegments();
			List<Point2D.Double> obstacles = collectUserChainObstacles();
			Map<Long, Integer> segmentCounts = new TreeMap<Long, Integer>();
			for (UserChainSegment segment : segments) {
				long key = userChainSegmentKey(segment.from, segment.to);
				Integer count = segmentCounts.get(key);
				segmentCounts.put(key, count == null ? 1 : count.intValue() + 1);
			}
			Map<Long, Integer> segmentOrdinals = new TreeMap<Long, Integer>();
			double laneSpacing = Math.min(Math.max(2.0, cellSize / 28.0), cellSize / 10.0);
			double routeDiameter = candidateDiameter > 0.0
					? candidateDiameter : Math.max(1.0, candidateHeight);
			for (UserChainSegment segment : segments) {
				long key = userChainSegmentKey(segment.from, segment.to);
				Integer ordinalValue = segmentOrdinals.get(key);
				int ordinal = ordinalValue == null ? 0 : ordinalValue.intValue();
				segmentOrdinals.put(key, ordinal + 1);
				int count = segmentCounts.get(key).intValue();
				double laneOffset = ChainRouteGeometry.boundedLaneOffset(
						ordinal, count, laneSpacing, routeDiameter);
				drawUserChainSegment(copy, segment, appearance, routeDiameter, obstacles, laneOffset);
			}
			drawUserChainNodes(copy, appearance, routeDiameter);
            if(preciseChainCandidate>=0) {
                copy.setStroke(new BasicStroke(Math.max(1.2f,cellSize/50f)));
                copy.setColor(appearance.getUserChainLinkColor(true));
                java.util.Set<Integer> shown=new java.util.HashSet<>();
                for(UserChainSegment segment:segments)for(UserChainNode node:new UserChainNode[]{segment.from,segment.to})
                    if(node.contains(preciseChainCandidate/10,preciseChainCandidate%10))for(int member:node.cells())if(shown.add(member*10+node.getCandidate())) {
                        Point2D.Double center=getCandKoord(member,node.getCandidate(),cellSize);double r=routeDiameter/2+3;
                        copy.draw(new java.awt.geom.Ellipse2D.Double(center.x-r,center.y-r,2*r,2*r));
                    }
            }
		} finally {
			copy.dispose();
		}
	}

    private void drawUserChainGroupBands(Graphics2D graphics, SudokuAppearancePalette appearance,double diameter) {
        List<UserChain> all=new ArrayList<UserChain>(userChains);if(activeUserChain!=null)all.add(activeUserChain);
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setComposite(AlphaComposite.SrcOver.derive(0.22f*annotationOpacity()));
            double d=diameter>0?diameter:Math.max(10.0,cellSize/5.0);
            g.setStroke(new BasicStroke(userChainNodeDiameter(d,cellSize),BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER));
            for(UserChain chain:all)if(!isUserChainReplacedByReasoningProposal(chain))for(UserChainNode node:chain.getNodes()) {
                if(!paintAnnotation(chainNodeTimeKey(node)) || !node.grouped() || !node.validShape() || !isUserChainNodeVisible(node))continue;
                int[] cells=node.cells();
                Point2D.Double a=getCandKoord(cells[0],node.getCandidate(),cellSize),b=getCandKoord(cells[cells.length-1],node.getCandidate(),cellSize);
                g.setColor(appearance.getUserChainNodeColor(userChainNodeColor(node),appearance.getDefaultCellColor()));
                g.draw(new java.awt.geom.Line2D.Double(a,b));
            }
        }finally{g.dispose();}
    }

	private List<UserChainSegment> collectUserChainSegments() {
		List<UserChainSegment> segments = new ArrayList<UserChainSegment>();
		for (UserChain chain : userChains) {
			if (!isUserChainReplacedByReasoningProposal(chain)) {
				collectUserChainSegments(chain, segments);
			}
		}
		if (activeUserChain != null && !isUserChainReplacedByReasoningProposal(activeUserChain)) {
			collectUserChainSegments(activeUserChain, segments);
		}
		return segments;
	}

	private void collectUserChainSegments(UserChain chain, List<UserChainSegment> segments) {
		List<UserChainNode> nodes = chain.getNodes();
		int segmentCount = Math.min(Math.max(0, nodes.size() - 1), chain.getStrongRelations().size());
		for (int i = 0; i < segmentCount; i++) {
			segments.add(new UserChainSegment(nodes.get(i), nodes.get(i + 1),
					chain.getStrongRelations().get(i).booleanValue(),
					i < chain.getRelationColors().size() ? chain.getRelationColors().get(i) : null));
		}
		if (chain.isClosed() && nodes.size() > 2 && chain.getStrongRelations().size() == nodes.size()) {
			segments.add(new UserChainSegment(nodes.get(nodes.size() - 1), nodes.get(0),
					chain.getStrongRelations().get(chain.getStrongRelations().size() - 1).booleanValue(),
					chain.getRelationColors().size() == chain.getStrongRelations().size()
							? chain.getRelationColors().get(chain.getRelationColors().size() - 1) : null));
		}
	}

	private List<Point2D.Double> collectUserChainObstacles() {
		List<Point2D.Double> obstacles = new ArrayList<Point2D.Double>();
		for (UserChain chain : userChains) {
			if (!isUserChainReplacedByReasoningProposal(chain)) {
				collectUserChainObstacles(chain, obstacles);
			}
		}
		if (activeUserChain != null && !isUserChainReplacedByReasoningProposal(activeUserChain)) {
			collectUserChainObstacles(activeUserChain, obstacles);
		}
		return obstacles;
	}

	private void collectUserChainObstacles(UserChain chain, List<Point2D.Double> obstacles) {
		for (UserChainNode node : chain.getNodes()) {
			if (isUserChainNodeVisible(node)) {
				for(int member:node.cells()) obstacles.add(getCandKoord(member, node.getCandidate(), cellSize));
			}
		}
	}

    private UserChain endpointMarkedChain() {
        UserChain chain=activeUserChain!=null?activeUserChain:userChains.isEmpty()?null:userChains.get(userChains.size()-1);
        return chain!=null&&chain.getNodes().size()>1&&!isUserChainReplacedByReasoningProposal(chain)?chain:null;
    }
    private void drawChainEndCap(Graphics2D g,Point2D.Double center,Point2D.Double tip,double radius,float width) {
        double angle=-Math.toDegrees(Math.atan2(tip.y-center.y,tip.x-center.x));
        g.setStroke(new BasicStroke(Math.max(1.2f,width*.8f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Arc2D.Double(center.x-radius,center.y-radius,2*radius,2*radius,angle-58,116,java.awt.geom.Arc2D.OPEN));
    }

	private void drawUserChainSegment(Graphics2D graphics, UserChainSegment segment,
			SudokuAppearancePalette appearance, double candidateDiameter,
			List<Point2D.Double> obstacles, double laneOffset) {
		UserChainNode from = segment.from;
		UserChainNode to = segment.to;
        if(!paintAnnotation(chainEdgeTimeKey(from,to)))return;
		Point2D.Double[] ends=userChainEndpoints(from,to);
        Point2D.Double start=ends[0],end=ends[1];
		if (start.distance(end) < 1.0) {
			return;
		}
		ChainRouteGeometry.Route route = userChainRoute(segment, candidateDiameter, obstacles, laneOffset);
        UserChain marked=endpointMarkedChain();
        boolean startCap=marked!=null&&from==marked.getNodes().get(0)&&to==marked.getNodes().get(1);
        boolean endCap=marked!=null&&(marked.isClosed()
                ? from==marked.getNodes().get(marked.getNodes().size()-1)&&to==marked.getNodes().get(0)
                : to==marked.getNodes().get(marked.getNodes().size()-1));
        double capRadius=candidateDiameter/2+Math.max(1.5,cellSize*.012);
        java.awt.Shape oldClip=graphics.getClip();
        if(startCap||endCap) {
            java.awt.geom.Area clip=new java.awt.geom.Area(oldClip==null?new java.awt.Rectangle(0,0,getWidth(),getHeight()):oldClip);
            if(startCap)clip.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(start.x-capRadius,start.y-capRadius,2*capRadius,2*capRadius)));
            if(endCap)clip.subtract(new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(end.x-capRadius,end.y-capRadius,2*capRadius,2*capRadius)));
            graphics.setClip(clip);
        }
		boolean valid = !invalidUserChainRelations.contains(UserChainValidator.relationKey(from,to,segment.strong))
                && (!Options.getInstance().isMarkInvalidLinks() || from.grouped() || to.grouped()
                    || isUserChainRelationValid(from, to, segment.strong));
		Color semanticColor = segment.capturedColor != null
				? segment.capturedColor : appearance.getUserChainLinkColor(segment.strong);
		java.awt.Composite previousComposite=graphics.getComposite();
        if(!valid && (step==null || reasoningProposal!=null && reasoningProposal.authoredChainProof)) graphics.setComposite(AlphaComposite.SrcOver);
        Color color = valid ? appearance.getUserChainNodeColor(semanticColor, appearance.getDefaultCellColor())
				: appearance.getUserChainInvalidColor();
		float width = Math.max(1.8f, cellSize / 30.0f);
		double visibleLength = route.getStart().distance(route.getEnd());
        if(!valid){
            width*=1.7f;graphics.setColor(appearance.getDefaultCellColor());
            graphics.setStroke(createUserChainStroke(segment.strong,width+3,visibleLength));
            drawChainRoute(graphics,route,cellSize,candidateDiameter,!endCap);
        }
        graphics.setColor(color);
		graphics.setStroke(createUserChainStroke(segment.strong, width, visibleLength));
		drawChainRoute(graphics, route, cellSize, candidateDiameter,!endCap);
        graphics.setClip(oldClip);
        if(startCap)drawChainEndCap(graphics,start,route.getStart(),capRadius,width);
        if(endCap)drawChainEndCap(graphics,end,route.getEnd(),capRadius,width);
        graphics.setComposite(previousComposite);
	}

	private void drawUserChainNodes(Graphics2D graphics, SudokuAppearancePalette appearance,
			double candidateDiameter) {
		for (UserChain chain : userChains) {
			if (!isUserChainReplacedByReasoningProposal(chain)) {
				drawUserChainNodes(graphics, chain, appearance, candidateDiameter);
			}
		}
		if (activeUserChain != null && !isUserChainReplacedByReasoningProposal(activeUserChain)) {
			drawUserChainNodes(graphics, activeUserChain, appearance, candidateDiameter);
		}
	}

	private void drawUserChainNodes(Graphics2D graphics, UserChain chain,
			SudokuAppearancePalette appearance, double candidateDiameter) {
		Font oldFont = graphics.getFont();
		Font nodeFont = candidateFont.deriveFont(Font.BOLD);
		graphics.setFont(nodeFont);
		FontMetrics metrics = graphics.getFontMetrics(nodeFont);
		List<UserChainNode> nodes = chain.getNodes();
		for (int i = 0; i < nodes.size(); i++) {
			UserChainNode node = nodes.get(i);
			if (!isUserChainNodeVisible(node) || !paintAnnotation(chainNodeTimeKey(node))) {
				continue;
			}
			for(int member:node.cells()) {
            Point2D.Double point = getCandKoord(member, node.getCandidate(), cellSize);
			double effectiveDiameter = candidateDiameter > 0.0
					? candidateDiameter : Math.max(10.0, cellSize / 5.0);
			int diameter = userChainNodeDiameter(effectiveDiameter, cellSize);
			Color background = Sudoku2.getBlock(member) % 2 == 0
					? appearance.getDefaultCellColor() : appearance.getAlternateCellColor();
			Color color = appearance.getUserChainNodeColor(userChainNodeColor(node), background);
			graphics.setColor(color);
			graphics.fillOval((int) Math.round(point.x - effectiveDiameter / 2.0),
					(int) Math.round(point.y - effectiveDiameter / 2.0), diameter, diameter);
			String glyph = Integer.toString(node.getCandidate());
			graphics.setColor(Color.WHITE);
			graphics.drawString(glyph,
					(int) Math.round(point.x - metrics.stringWidth(glyph) / 2.0),
					(int) Math.round(point.y + (metrics.getAscent() - metrics.getDescent()) / 2.0));
		}
        }
		graphics.setFont(oldFont);
	}

	static int userChainNodeDiameter(double candidateDiameter, int cellSize) {
		return candidateDiameter > 0.0
				? Math.max(1, (int) Math.round(candidateDiameter))
				: Math.max(10, (int) Math.round(cellSize / 5.0));
	}

    private boolean isUserChainReplacedByReasoningProposal(UserChain chain) {
        return false; // Time-layer rendering now separates old sources from subsequent edits.
    }

	private boolean isUserChainNodeVisible(UserChainNode node) {
		for(int member:node.cells())if(!isUserChainCandidateVisible(member,node.getCandidate()))return false;
        return true;
	}

	private boolean isUserChainCandidateVisible(int cellIndex, int candidate) {
		if (sudoku.getValue(cellIndex) != 0) {
			return false;
		}
		boolean userCandidates = !showCandidates;
		if (showAllCandidates || showAllCandidatesAkt
				&& Sudoku2.getRow(cellIndex) == getActiveRow()
				&& Sudoku2.getCol(cellIndex) == getActiveCol()) {
			userCandidates = false;
		}
		return sudoku.isCandidate(cellIndex, candidate, userCandidates)
				|| showCandidates && showDeviations && sudoku.isSolutionSet()
						&& candidate == sudoku.getSolution(cellIndex);
	}

	private static int userChainNodeKey(UserChainNode node) {
		return node.identity();
	}

	private static long userChainSegmentKey(UserChainNode first, UserChainNode second) {
		int firstKey = userChainNodeKey(first);
		int secondKey = userChainNodeKey(second);
		int low = Math.min(firstKey, secondKey);
		int high = Math.max(firstKey, secondKey);
		return ((long) low << 32) | (high & 0xffffffffL);
	}

	private static final class UserChainSegment {
		private final UserChainNode from;
		private final UserChainNode to;
		private final boolean strong;
		private final Color capturedColor;

		private UserChainSegment(UserChainNode from, UserChainNode to,
				boolean strong, Color capturedColor) {
			this.from = from;
			this.to = to;
			this.strong = strong;
			this.capturedColor = capturedColor;
		}
	}

	static BasicStroke createUserChainStroke(boolean strong, float width, double visibleLength) {
		if (strong) {
			return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
		}
		// A short relation still needs at least one visible gap. Butt caps keep the
		// neighboring dash ends from visually closing that gap at high stroke widths.
		float dash = Math.max(1.25f, Math.min(width * 2.2f, (float) visibleLength / 3.2f));
		float gap = Math.max(1.25f, Math.min(width * 1.8f, (float) visibleLength / 3.2f));
		return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
				10.0f, new float[] { dash, gap }, 0.0f);
	}

	private Color userChainNodeColor(UserChainNode node) {
		return node.getColor() != null ? node.getColor() : cellZoomPanel.getPrimaryColor();
	}

    private boolean isUserChainRelationValid(UserChainNode first, UserChainNode second, boolean strong) {
        if(!first.validShape() || !second.validShape())return false;
        for(int c:first.cells())if(!sudoku.isCandidate(c,first.getCandidate()))return false;
        for(int c:second.cells())if(!sudoku.isCandidate(c,second.getCandidate()))return false;
        return strong ? UserChainValidator.strong(sudoku,first,second) : UserChainValidator.weak(first,second);
    }

	private boolean sharesWeakUserChainRelation(UserChainNode first, UserChainNode second) {
		if (first.getCellIndex() == second.getCellIndex()) {
			return first.getCandidate() != second.getCandidate();
		}
		if (first.getCandidate() != second.getCandidate()) {
			return false;
		}
		int row1 = Sudoku2.getRow(first.getCellIndex());
		int col1 = Sudoku2.getCol(first.getCellIndex());
		int row2 = Sudoku2.getRow(second.getCellIndex());
		int col2 = Sudoku2.getCol(second.getCellIndex());
		return row1 == row2 || col1 == col2 || (row1 / 3 == row2 / 3 && col1 / 3 == col2 / 3);
	}

	private boolean isStrongUserChainRelation(UserChainNode first, UserChainNode second) {
		if (first.getCellIndex() == second.getCellIndex()) {
			return sudoku.getAllCandidates(first.getCellIndex(), false).length == 2;
		}
		if (first.getCandidate() != second.getCandidate()) {
			return false;
		}
		int row1 = Sudoku2.getRow(first.getCellIndex());
		int col1 = Sudoku2.getCol(first.getCellIndex());
		int row2 = Sudoku2.getRow(second.getCellIndex());
		int col2 = Sudoku2.getCol(second.getCellIndex());
		return (row1 == row2 && countCandidatesInSharedUnit(first.getCandidate(), row1, -1, -1) == 2)
				|| (col1 == col2 && countCandidatesInSharedUnit(first.getCandidate(), -1, col1, -1) == 2)
				|| (row1 / 3 == row2 / 3 && col1 / 3 == col2 / 3
						&& countCandidatesInSharedUnit(first.getCandidate(), -1, -1, Sudoku2.getBlock(first.getCellIndex())) == 2);
	}

	/** Counts one exact conjugate-pair unit; overlapping units must not be merged. */
	private int countCandidatesInSharedUnit(int candidate, int targetRow, int targetCol, int targetBlock) {
		int count = 0;
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			if (!sudoku.isCandidate(index, candidate)) {
				continue;
			}
			if ((targetRow >= 0 && Sudoku2.getRow(index) == targetRow)
					|| (targetCol >= 0 && Sudoku2.getCol(index) == targetCol)
					|| (targetBlock >= 0 && Sudoku2.getBlock(index) == targetBlock)) {
				count++;
			}
		}
		return count;
	}

	private void onMouseDown(java.awt.event.MouseEvent evt) {

		lastMousePosition = evt.getPoint();
		lastPressedRow = getRow(evt.getPoint());
		lastPressedCol = getCol(evt.getPoint());
		lastPressedCandidate = getCandidate(evt.getPoint(), lastPressedRow, lastPressedCol);

		clearDragSelection();

		Integer index = Sudoku2.getIndex(lastPressedRow, lastPressedCol);
		boolean isLeftClick = evt.getButton() == MouseEvent.BUTTON1;
		boolean isRightClick = evt.getButton() == MouseEvent.BUTTON3;
		boolean isMenuShortcutClick = SudokuUtil.isMenuShortcutDown(evt);
		boolean rightClickOutsideSelection = !cellSelection.contains(Integer.valueOf(index)) && isRightClick;
		boolean onGrid = isOnGrid(evt.getPoint());
		
		if (!onGrid) {

			clearSelection();
			clearDragSelection();
			updateCellZoomPanel();
			
		} else if (rightClickOutsideSelection || cellZoomPanel.isColoring()) {
			
			if (rightClickOutsideSelection) {
				setActiveCell(lastPressedRow, lastPressedCol);
			}
			
			clearSelection();
			clearDragSelection();
			
		} else if (isLeftClick) {

			if (!isMenuShortcutClick) {
				int keyboardModifiers = MouseEvent.SHIFT_DOWN_MASK | MouseEvent.CTRL_DOWN_MASK
						| MouseEvent.META_DOWN_MASK | MouseEvent.ALT_DOWN_MASK
						| MouseEvent.ALT_GRAPH_DOWN_MASK;
				boolean repeatPlainCellClick = (evt.getModifiersEx() & keyboardModifiers) == 0
						&& !Options.getInstance().isSingleClickMode()
						&& !cellZoomPanel.isColoring() && cellSelection.size() == 1
						&& cellSelection.contains(index);
				if (repeatPlainCellClick) {
					deselectSelectionOnRelease = true;
				} else {
					updateAutoHighlight(lastPressedRow, lastPressedCol);
					setActiveCell(lastPressedRow, lastPressedCol);
					clearSelection();
					clearDragSelection();
				}
				
			} else {

				if (cellSelection.contains(index) && cellSelection.size() > 1) {
					cellSelection.remove(index);
					int lastCellSelection = cellSelection.get(cellSelection.size()-1).intValue();
					setActiveCell(Sudoku2.getRow(lastCellSelection), Sudoku2.getCol(lastCellSelection));
				} else if (!cellSelection.contains(index)) {
					cellSelection.add(index);
					setActiveCell(index);
				}
			}
		}
		
		repaint();
	}

	private void onMouseUp(java.awt.event.MouseEvent evt) {

		lastMousePosition = evt.getPoint();

		int row = getRow(evt.getPoint());
		int col = getCol(evt.getPoint());
		int candidate = getCandidate(evt.getPoint(), row, col);
		boolean onGrid = isOnGrid(evt.getPoint());

		if (!onGrid) {

			lastPressedRow = -1;
			lastPressedCol = -1;
			lastPressedCandidate = -1;
			clearSelection();
			clearDragSelection();
			updateCellZoomPanel();
			
			lastPressedRow = -1;
			lastPressedCol = -1;
			lastPressedCandidate = -1;
			return;			
		}
			
		long ticks = System.currentTimeMillis();

		boolean isDoubleClick = 
			lastClickedTime != -1 && 
			(ticks - lastClickedTime) <= doubleClickSpeed && 
			row == lastClickedRow && 
			col == lastClickedCol && 
			candidate == lastClickedCandidate;

		if (isDoubleClick) {
			handleMouseClicked(evt, true);
			lastClickedTime = -1;
		} else {
			handleMouseClicked(evt, false);
			lastClickedTime = ticks;
		}

		lastClickedRow = row;
		lastClickedCol = col;
		lastClickedCandidate = candidate;
	}

	boolean onRightClick(MouseClickDTO dto) {
		
		boolean change = false;
		
		if (Options.getInstance().isSingleClickMode()) {
			
			// toggle candidate in cell(s) (three state mode)
			if (cellSelection.contains(Integer.valueOf(Sudoku2.getIndex(dto.row, dto.col)))) {
				
				// a region select exists and the cells lies within: toggle candidate
				if (dto.candidate != -1) {
					change = toggleCandidateInAktCells(dto.candidate);
				}
				
			} else {
				
				// no region or cell outside region -> change focus and toggle candidate
				setActiveCell(dto.row, dto.col);
				clearRegion();
				
				if (sudoku.getValue(dto.row, dto.col) != 0 && !sudoku.isFixed(dto.row, dto.col)) {
					
					rightClickMenu.deleteValuePopup(dto.row, dto.col, cellSize);
					
				} else {
					
					int showHintCellValue = getShowHintCellValue();
					if ((dto.candidate == -1 || 
						!sudoku.isCandidate(dto.row, dto.col, dto.candidate, !showCandidates)) && 
						showHintCellValue != 0) {
						
						// if the candidate is not present, but part of the solution and
						// show deviations is set, it is displayed, although technically
						// not present: it should be toggled, even if it is not the
						// hint value
						if (showDeviations && 
							sudoku.isSolutionSet() && 
							dto.candidate == sudoku.getSolution(getActiveRow(), getActiveCol())) {
							toggleCandidateInCell(getActiveRow(), getActiveCol(), dto.candidate);
						} else {
							toggleCandidateInCell(getActiveRow(), getActiveCol(), showHintCellValue);
						}
						
					} else if (dto.candidate != -1) {
						toggleCandidateInCell(getActiveRow(), getActiveCol(), dto.candidate);
					}
					
					change = true;
				}
			}
			
		} else {
			// bring up popup menu
			rightClickMenu.showPopupMenu(dto.row, dto.col, cellSize);
		}
		
		return change;
	}
	
	boolean onSingleLeftClick(MouseClickDTO dto, int index) {
		
		if (dto.ctrlPressed) {
			
			/*
			Integer cell = Integer.valueOf(index);

			// select additional cell
			if (cellSelection.size() == 0) {
				
				cellSelection.add(cell);
				setActiveCell(dto.row, dto.col);
							
				if (!dragCellSelection[index]) {
					if (cellSelection.contains(Integer.valueOf(index))) {
						cellSelection.remove(Integer.valueOf(index));
					} else {
						cellSelection.add(Integer.valueOf(index));
					}
				}
				
				setActiveCell(dto.row, dto.col);				
			}*/
			
			return false;			
		}
		
		if (dto.shiftPressed) {
			
			if (Options.getInstance().isUseShiftForRegionSelect()) {
				// select range of cells
				selectRegion(dto.row, dto.col);
			} else {
				if (dto.candidate != -1) {
					// toggle candidate
					if (sudoku.isCandidate(index, dto.candidate, !showCandidates)) {
						sudoku.setCandidate(index, dto.candidate, false, !showCandidates);
					} else {
						sudoku.setCandidate(index, dto.candidate, true, !showCandidates);
					}
					
					clearRegion();
					return true;
				}
			}
			
			return false;			
		}
		
		// select single cell, delete old markings if available
		// in the alternative mouse mode a single cell is only
		// selected, if the cell is outside a selected region
		if ((Options.getInstance().isSingleClickMode() == false && 
			cellSelection.contains(Integer.valueOf(Sudoku2.getIndex(dto.row, dto.col))) == false) ||
			Options.getInstance().isSingleClickMode() == true) {
			setActiveCell(dto.row, dto.col);
			clearRegion();
		}
		
		if (Options.getInstance().isSingleClickMode()) {
			
			// the selected cell(s) must be set to cand
			if (sudoku.getValue(index) == 0) {
				if (cellSelection.isEmpty()) {
					
					int showHintCellValue = getShowHintCellValue();
					if (sudoku.getAnzCandidates(index, !showCandidates) == 1) {
						// Naked single -> set it!
						int actCand = sudoku.getAllCandidates(index, !showCandidates)[0];
						setCell(dto.row, dto.col, actCand);
						return true;
					} else if (showHintCellValue != 0 && isHiddenSingle(showHintCellValue, dto.row, dto.col)) {
						// Hidden Single -> it
						setCell(dto.row, dto.col, showHintCellValue);
						return true;
					} else if (dto.candidate != -1) {
						// set candidate
						// (only if that candidate is still set in the cell)
						if (sudoku.isCandidate(index, dto.candidate, !showCandidates)) {
							setCell(dto.row, dto.col, dto.candidate);
						}
						
						return true;
					}
					
				} else {
					
					if (dto.candidate == -1 || !sudoku.isCandidate(index, dto.candidate, !showCandidates)) {
						// an empty space was clicked in the cell -> clear region
						setActiveCell(dto.row, dto.col);
						clearRegion();
					} else if (dto.candidate != -1) {
						// an actual candidate was clicked -> set value in all cells where it is
						// still possible (collect cells first to avoid side effects!)
						List<Integer> cells = new ArrayList<Integer>();
						for (int selIndex : cellSelection) {
							if (sudoku.getValue(selIndex) == 0 && 
								sudoku.isCandidate(selIndex, dto.candidate, !showCandidates)) {
								cells.add(selIndex);
							}
						}
						
						for (int cellIndex : cells) {
							setCell(Sudoku2.getRow(cellIndex), Sudoku2.getCol(cellIndex), dto.candidate);
						}
					}
				}
			} else {
				// clear selection
				setActiveCell(dto.row, dto.col);
				clearRegion();				
			}
			
			return true;
		}
		
		return false;
	}
	
	boolean onDoubleLeftClick(MouseClickDTO dto, int index) {
		
		if (dto.ctrlPressed) {
			if (dto.candidate != -1) {
				
				// toggle candidate
				if (sudoku.isCandidate(index, dto.candidate, !showCandidates)) {
					sudoku.setCandidate(index, dto.candidate, false, !showCandidates);
				} else {
					sudoku.setCandidate(index, dto.candidate, true, !showCandidates);
				}
				
				clearRegion();
				return true;
			}
			
		} else {
			
			if (sudoku.getValue(index) == 0) {
				
				int showHintCellValue = getShowHintCellValue();
				if (dto.candidate > 0 && showCandidates && showDeviations && sudoku.isSolutionSet()
						&& dto.candidate == sudoku.getSolution(index)
						&& !sudoku.isCandidate(index, dto.candidate)) {
					// A missing solution candidate is still drawn in red. Honor that
					// explicit target before the remaining-candidate single shortcuts.
					setCell(dto.row, dto.col, dto.candidate);
					setCandidateFilterByGiven(dto.row, dto.col);
					return true;
				} else if (sudoku.getAnzCandidates(index, !showCandidates) == 1) {
					// Naked single -> set it!
					int actCand = sudoku.getAllCandidates(index, !showCandidates)[0];
					setCell(dto.row, dto.col, actCand);
					setCandidateFilterByGiven(dto.row, dto.col);
					return true;
				} else if (showHintCellValue != 0 && isHiddenSingle(showHintCellValue, dto.row, dto.col)) {
					// Hidden Single -> it
					setCell(dto.row, dto.col, showHintCellValue);
					setCandidateFilterByGiven(dto.row, dto.col);
					return true;
				} else if (dto.candidate != -1) {
					// candidate double clicked -> set it
					// (only if that candidate is still set in the cell)
					if (sudoku.isCandidate(index, dto.candidate, !showCandidates)) {
						setCell(dto.row, dto.col, dto.candidate);
						setCandidateFilterByGiven(dto.row, dto.col);
					}
					
					return true;
				}
				
			} else if (!sudoku.isFixed(index)) {
				// double clicking a user input value removes it
				setCell(dto.row, dto.col, 0);
				setCandidateFilterByGiven(dto.row, dto.col);
				return true;
			}
		}
		
		return false;
	}
	
	void setCandidateFilterByGiven(int row, int col) {
		if (Options.getInstance().isAutoHighlighting()) {
			int value = sudoku.getValue(row, col);
			if (value != 0) {
				setShowHintCellValue(value);
				setShowInvalidOrPossibleCells(true);
				showHintCellValues[value] = true;
				lastHighlightedDigit = value;
			} else {
				resetShowHintCellValues();
				setShowInvalidOrPossibleCells(false);
				lastHighlightedDigit = 0;
				mainFrame.repaint();
			}
		}
	}
	
	boolean onLeftClick(MouseClickDTO dto) {
		
		boolean change = false;
				
		// in normal mode we only react to the left mouse button
		int index = Sudoku2.getIndex(dto.row, dto.col);
		if (dto.isDoubleClick) {
			change = onDoubleLeftClick(dto, index);			
		} else {			
			change = onSingleLeftClick(dto, index);
		}
		
		return change;
	}
	
	void onColoring(MouseClickDTO dto) {
		if (!dto.isLeftClick || (!dto.hasNoKeyboardModifier && !dto.hasOnlyOptionModifier)) {
			return;
		}

		Color color = dto.hasOnlyOptionModifier
				? cellZoomPanel.getSecondaryColor()
				: cellZoomPanel.getPrimaryColor();
		
		if (cellZoomPanel.isColoringCells()) {
			// coloring for cells
			if (dto.isCellClicked) {
				handleColoring(dto.row, dto.col, -1, color);
			}
		} else if (cellZoomPanel.isColoringCandidates()) {
			// coloring for candidates
			if (dto.candidate != -1) {
				if (dto.isCandidateClicked) {
					handleColoring(dto.row, dto.col, dto.candidate, color);
				}
			}
		}
	}

	private boolean isOneShotCandidateColoring(MouseClickDTO dto) {
		if (!SudokuUtil.isMacOS() || !cellZoomPanel.isDefaultMouse() || !dto.isLeftClick
				|| !dto.isCandidateClicked || !dto.hasOnlyOptionModifier) {
			return false;
		}

		int index = Sudoku2.getIndex(dto.row, dto.col);
		return sudoku.getValue(index) == 0
				&& sudoku.isCandidate(index, dto.candidate, !showCandidates);
	}

	private void onOneShotCandidateColoring(MouseClickDTO dto) {
		handleColoring(dto.row, dto.col, dto.candidate, cellZoomPanel.getPrimaryColor());
	}
	
	class MouseClickDTO {
		
		public int row;
		public int col;
		public int candidate;
		public boolean ctrlPressed;
		public boolean shiftPressed;
		public boolean hasNoKeyboardModifier;
		public boolean hasOnlyOptionModifier;
		public boolean isValidCellIndex;
		public boolean isLeftClick;
		public boolean isMiddleClick;
		public boolean isRightClick;
		public boolean isCellClicked;
		public boolean isCandidateClicked;
		public boolean isDoubleClick;
		
		MouseClickDTO(MouseEvent e, SudokuPanel panel, boolean isDoubleClick) {
			int modifiers = e.getModifiersEx();
			this.row = panel.getRow(e.getPoint());
			this.col = panel.getCol(e.getPoint());
			this.candidate = panel.getCandidate(e.getPoint(), row, col);
			this.ctrlPressed = SudokuUtil.isMenuShortcutDown(e);
			this.shiftPressed = (modifiers & MouseEvent.SHIFT_DOWN_MASK) != 0;
			int keyboardModifiers = MouseEvent.SHIFT_DOWN_MASK | MouseEvent.CTRL_DOWN_MASK
					| MouseEvent.META_DOWN_MASK | MouseEvent.ALT_DOWN_MASK
					| MouseEvent.ALT_GRAPH_DOWN_MASK;
			this.hasNoKeyboardModifier = (modifiers & keyboardModifiers) == 0;
			this.hasOnlyOptionModifier = (modifiers & keyboardModifiers) == MouseEvent.ALT_DOWN_MASK;
			this.isValidCellIndex = Sudoku2.isValidIndex(row, col);
			this.isLeftClick = e.getButton() == MouseEvent.BUTTON1;
			this.isMiddleClick = e.getButton() == MouseEvent.BUTTON2;
			this.isRightClick = e.getButton() == MouseEvent.BUTTON3;
			this.isCellClicked = 
				this.row == panel.lastPressedRow && 
				this.col == panel.lastPressedCol;
			this.isCandidateClicked = 
				this.isCellClicked &&
				this.candidate == panel.lastPressedCandidate;
			this.isDoubleClick = isDoubleClick;
		}
	}
	
	/**
	 * New mouse control for version 2.0:
	 * <ul>
	 * <li>clicking a cell sets the cursor to the cell (not in coloring mode)</li>
	 * <li>holding shift or ctrl down while clicking selects a region of cells</li>
	 * <li>double clicking a cell with only one candidate left sets that candidate
	 * in the cell</li>
	 * <li>double clicking a cell containing a Hidden Single sets that cell if
	 * filters are applied for the candidate</li>
	 * <li>double clicking a candidate with ctrl pressed toggles the candidate</li>
	 * <li>right click on a cell activates the context menu</li>
	 * </ul>
	 * In a sticky coloring mode the mouse behaviour changes completely (the active
	 * radio button decides whether a cell or candidate is colored):
	 * <ul>
	 * <li>left click on a cell/candidate toggles the color on the
	 * cell/candidate</li>
	 * <li>left click on a cell/candidate with Option pressed toggles the secondary
	 * color on the cell/candidate</li>
	 * </ul>
	 * Context menu:<br>
	 * The context menu for a single cell shows entries to set the cell to all
	 * remaining candidates, entries to remove all remaining candidates (including
	 * one entry to remove multiple candidates in one move) and entries for
	 * coloring.<br>
	 * <b>Alternative Mouse Mode:</b><br>
	 * Since v2.1a new alternative mouse mode is available: Left click on a
	 * candidate sets the candidate in the cell(s), right click toggles the
	 * candidate in the cell(s). Selection works as before.
	 *
	 * @param evt
	 */
	private void handleMouseClicked(MouseEvent evt, boolean isDoubleClick) {
		
		MouseClickDTO dto = new MouseClickDTO(evt, this, isDoubleClick);
		if (deselectSelectionOnRelease) {
			deselectSelectionOnRelease = false;
			if (!dto.isDoubleClick && dto.isLeftClick && dto.hasNoKeyboardModifier
					&& dto.isCellClicked && !cellZoomPanel.isColoring()) {
				clearActiveSelection();
				updateCellZoomPanel();
				mainFrame.check();
				repaint();
				return;
			}
		}
		boolean changed = false;
		
		undoStack.push(sudoku.clone());
		
		if (dto.isValidCellIndex) {
			if (dto.isRightClick) {				
				changed = onRightClick(dto);				
			} else {
				if (cellZoomPanel.isColoring()) {
					onColoring(dto);		
				} else if (isOneShotCandidateColoring(dto)) {
					onOneShotCandidateColoring(dto);
				} else if (dto.isLeftClick) {
					changed = onLeftClick(dto);	
				}				
			}
			
			if (changed) {
				redoStack.clear();
				redoColoringStates.clear();
				checkProgress();
				mainFrame.sudokuStateChanged();
			} else {
				undoStack.pop();
			}
			
			updateCellZoomPanel();
			mainFrame.check();
			repaint();
		}
	}

	public void saveState() {
		undoStack.push(sudoku.clone());
		mainFrame.check();
		repaint();
	}
	
	/**
	 * Moves the cursor. If the cell actually changed, a timer is started, that
	 * triggers a repaint after {@link Options#getDeleteCursorDisplayLength() } ms.
	 *
	 * @param row
	 * @param col
	 */
	/*
	private void setAktRowCol(int row, int col) {
		
		if (activeRow != row) {
			activeRow = row;
		}
		
		if (activeCol != col) {
			activeCol = col;
		}
		
		if (Options.getInstance().isDeleteCursorDisplay()) {
			deleteCursorTimer.stop();
			lastCursorChanged = System.currentTimeMillis();
			deleteCursorTimer.setDelay(Options.getInstance().getDeleteCursorDisplayLength());
			deleteCursorTimer.setInitialDelay(Options.getInstance().getDeleteCursorDisplayLength());
			deleteCursorTimer.start();
		}
	}*/
	
	public void pushUndo() {
		this.undoStack.push(this.sudoku.clone());
	}
	
	public void popUndo() {
		this.undoStack.pop();
	}
	
	public void clearRedoStack() {
		this.redoStack.clear();
	}
	
	public ArrayList<Integer> getSelectedCells() {
		return this.cellSelection;
	}

	/**
	 * Loads all relevant objects into <code>state</code>. If <code>copy</code> is
	 * true, all objects are copied.<br>
	 * Some objects have to be copied regardless of parameter <code>copy</code>.
	 *
	 * @param state
	 * @param copy
	 */
	@SuppressWarnings("unchecked")
	public void getState(GuiState state, boolean copy) {
		// items that don't have to be copied
		state.setChainIndex(chainIndex);
		// items that must be copied anyway
		state.setUndoStack((Stack<Sudoku2>) undoStack.clone());
		state.setRedoStack((Stack<Sudoku2>) redoStack.clone());
		state.setColoringMap((SortedMap<Integer, Color>) ((TreeMap<Integer, Color>) coloringMap).clone());
		state.setColoringCandidateMap((SortedMap<Integer, Color>) ((TreeMap<Integer, Color>) coloringCandidateMap).clone());
		if (state.isIncludeAnnotations()) {
			state.setDoodleStrokes(copyDoodles(doodleStrokes));
			state.setUserChains(copyUserChains(userChains));
			state.setDoodleUndoHistory(copyDoodleHistory(doodleUndoStack));
			state.setDoodleRedoHistory(copyDoodleHistory(doodleRedoStack));
			state.setUserChainUndoHistory(copySanitizedUserChainHistory(userChainUndoStack));
			state.setUserChainRedoHistory(copySanitizedUserChainHistory(userChainRedoStack));
			state.setBoxReasoningGroups(copyBoxReasoningGroups(boxReasoningGroups));
			state.setBoxReasoningUndoHistory(copyBoxReasoningHistory(boxReasoningUndoStack));
			state.setBoxReasoningRedoHistory(copyBoxReasoningHistory(boxReasoningRedoStack));
			state.setColoringUndoCells(copyColoringHistory(coloringUndoStack, true));
			state.setColoringUndoCandidates(copyColoringHistory(coloringUndoStack, false));
			state.setColoringRedoCells(copyColoringHistory(coloringRedoStack, true));
			state.setColoringRedoCandidates(copyColoringHistory(coloringRedoStack, false));
		}
		// items that might be null (and therefore wont be copied)
		state.setSudoku(sudoku);
		SolutionStep persistedStep = reasoningProposal == null ? step : null;
		state.setStep(persistedStep);
		if (copy) {
			state.setSudoku(sudoku.clone());
			if (persistedStep != null) {
				state.setStep((SolutionStep) persistedStep.clone());
			}
		}
	}

	/**
	 * Loads back a saved state. Whether the objects had been copied before is
	 * irrelevant here.<br>
	 * The optional objects {@link GuiState#undoStack} and
	 * {@link GuiState#redoStack} can be null. If this is the case they are cleared.
	 *
	 * @param state
	 */
	public void setState(GuiState state) {
		if (annotationToolPointerCaptured || boxDragStart != null || activeDoodleStroke != null) {
			suppressNextAnnotationPointerRelease = true;
		}
		generatedStepOwnerWillChange();
		undoColoringStates.clear();
		redoColoringStates.clear();
		
		chainIndex = state.getChainIndex();
		if (state.getUndoStack() != null) {
			undoStack = state.getUndoStack();
		} else {
			undoStack.clear();
		}
		
		if (state.getRedoStack() != null) {
			redoStack = state.getRedoStack();
		} else {
			redoStack.clear();
		}
		
		if (state.getColoringMap() != null) {
			coloringMap = state.getColoringMap();
		} else {
			coloringMap.clear();
		}
		
		if (state.getColoringCandidateMap() != null) {
			coloringCandidateMap = state.getColoringCandidateMap();
		} else {
			coloringCandidateMap.clear();
		}
		if (state.isIncludeAnnotations()) {
			doodleStrokes.clear();
			if (state.getDoodleStrokes() != null) {
				doodleStrokes.addAll(copyDoodles(state.getDoodleStrokes()));
			}
			userChains.clear();
			if (state.getUserChains() != null) {
				for (UserChain chain : copyUserChains(state.getUserChains())) {
					if (!chain.isActive()) userChains.add(chain);
				}
			}
			cancelDoodleGesture();
			activeUserChain = null;
			restoreDoodleHistory(doodleUndoStack, state.getDoodleUndoHistory());
			restoreDoodleHistory(doodleRedoStack, state.getDoodleRedoHistory());
			restoreUserChainHistory(userChainUndoStack, state.getUserChainUndoHistory());
			restoreUserChainHistory(userChainRedoStack, state.getUserChainRedoHistory());
			restoreBoxReasoningGroups(state.getBoxReasoningGroups() == null
					? createEmptyBoxReasoningGroups() : state.getBoxReasoningGroups());
			restoreBoxReasoningHistory(boxReasoningUndoStack, state.getBoxReasoningUndoHistory());
			restoreBoxReasoningHistory(boxReasoningRedoStack, state.getBoxReasoningRedoHistory());
			clearBoxReasoningDragState();
			restoreColoringHistory(coloringUndoStack, state.getColoringUndoCells(),
					state.getColoringUndoCandidates());
			restoreColoringHistory(coloringRedoStack, state.getColoringRedoCells(),
					state.getColoringRedoCandidates());
			annotationTool = AnnotationTool.DEFAULT_MOUSE;
			stickyAnnotationTool = AnnotationTool.DEFAULT_MOUSE;
			settledAnnotationToolOrigins.clear();
			clearActiveAnnotationToolKeyGesture();
			clearPendingAnnotationToolTap();
			pendingAnnotationToolRestore = null;
			annotationToolPointerCaptured = false;
			highestCompletedUserChainSourceId();
			confirmedUserChainSourceId = 0L;
			userChainReasoningRevision++;
			lastNoMatchIdentity = null;
			if (cellZoomPanel != null) {
				cellZoomPanel.selectAnnotationTool(annotationTool);
			}
		}
		
		sudoku = state.getSudoku();
		reasoningBoardRevision++;
		sudoku.checkSudoku();
		step = state.getStep();
		resetShowHintCellValues();
		showBivalueCells = false;
		showTrivalueCells = false;
		
		updateCellZoomPanel();
		checkProgress();
		mainFrame.check();
		repaint();
	}

	private List<List<DoodleStroke>> copyDoodleHistory(Stack<List<DoodleStroke>> source) {
		List<List<DoodleStroke>> result = new ArrayList<List<DoodleStroke>>(source.size());
		for (List<DoodleStroke> item : source) result.add(copyDoodles(item));
		return result;
	}

	private void restoreDoodleHistory(Stack<List<DoodleStroke>> target,
			List<List<DoodleStroke>> source) {
		target.clear();
		if (source != null) for (List<DoodleStroke> item : source) target.add(copyDoodles(item));
	}

	private List<List<UserChain>> copySanitizedUserChainHistory(Stack<List<UserChain>> source) {
		List<List<UserChain>> result = new ArrayList<List<UserChain>>(source.size());
		List<UserChain> previous = null;
		for (List<UserChain> item : source) {
			List<UserChain> completed = new ArrayList<UserChain>();
			for (UserChain chain : item) {
				if (chain != null && !chain.isActive()) completed.add(copyUserChain(chain));
			}
			if (previous == null || !sameUserChainFrame(previous, completed)) {
				result.add(completed);
				previous = completed;
			}
		}
		List<UserChain> currentCompleted = copyUserChains(userChains);
		while (!result.isEmpty()
				&& sameUserChainFrame(result.get(result.size() - 1), currentCompleted)) {
			result.remove(result.size() - 1);
		}
		return result;
	}

	private static boolean sameUserChainFrame(List<UserChain> first, List<UserChain> second) {
		if (first.size() != second.size()) return false;
		for (int i = 0; i < first.size(); i++) {
			UserChain left = first.get(i);
			UserChain right = second.get(i);
			if (!sameUserChainContent(left, right)
					|| left.isNextStrong() != right.isNextStrong()
					|| !left.getRelationColors().equals(right.getRelationColors())) return false;
			for (int node = 0; node < left.getNodes().size(); node++) {
				Color leftColor = left.getNodes().get(node).getColor();
				Color rightColor = right.getNodes().get(node).getColor();
				if (leftColor == null ? rightColor != null : !leftColor.equals(rightColor)) return false;
			}
		}
		return true;
	}

	private List<List<SudokuSet>> copyBoxReasoningHistory(Stack<List<SudokuSet>> source) {
		List<List<SudokuSet>> result = new ArrayList<List<SudokuSet>>(source.size());
		for (List<SudokuSet> item : source) {
			result.add(copyBoxReasoningGroups(item));
		}
		return result;
	}

	private void restoreBoxReasoningHistory(Stack<List<SudokuSet>> target,
			List<List<SudokuSet>> source) {
		target.clear();
		if (source != null) {
			for (List<SudokuSet> item : source) {
				target.add(copyBoxReasoningGroups(item));
			}
		}
	}

	private void restoreUserChainHistory(Stack<List<UserChain>> target,
			List<List<UserChain>> source) {
		target.clear();
		if (source == null) return;
		List<UserChain> previous = null;
		for (List<UserChain> item : source) {
			List<UserChain> completed = new ArrayList<UserChain>();
			for (UserChain chain : item) {
				if (chain != null && !chain.isActive()) completed.add(copyUserChain(chain));
			}
			if (previous == null || !sameUserChainFrame(previous, completed)) {
				target.add(completed);
				previous = completed;
			}
		}
		while (!target.isEmpty()
				&& sameUserChainFrame(target.peek(), userChains)) target.pop();
	}

	private List<SortedMap<Integer, Color>> copyColoringHistory(Stack<ColoringSnapshot> source,
			boolean cells) {
		List<SortedMap<Integer, Color>> result = new ArrayList<SortedMap<Integer, Color>>(source.size());
		for (ColoringSnapshot item : source) {
			result.add(new TreeMap<Integer, Color>(cells ? item.cells : item.candidates));
		}
		return result;
	}

	private void restoreColoringHistory(Stack<ColoringSnapshot> target,
			List<SortedMap<Integer, Color>> cells, List<SortedMap<Integer, Color>> candidates) {
		target.clear();
		if (cells == null || candidates == null) return;
		int count = Math.min(cells.size(), candidates.size());
		for (int i = 0; i < count; i++) target.add(new ColoringSnapshot(cells.get(i), candidates.get(i)));
	}

	/*
	public void loadFromFile(Sudoku sudoku, Sudoku solvedSudoku) {
		
		this.sudoku = sudoku;
		this.solvedSudoku = solvedSudoku;
		
		redoStack.clear();
		undoStack.clear();
		coloringMap.clear();
		coloringCandidateMap.clear();
		step = null;
		setChainInStep(-1);
		updateCellZoomPanel();
		mainFrame.check();
		repaint();
	}*/
	
	private void checkShowAllCandidates(int modifiers, int keyCode) {

		boolean oldShowAllCandidatesAkt = showAllCandidatesAkt;
		showAllCandidatesAkt = false;
		
		if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 && SudokuUtil.isMenuShortcutDown(modifiers)) {
			showAllCandidatesAkt = true;
		}

		boolean oldShowAllCandidates = showAllCandidates;
		showAllCandidates = false;
		
		if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 && (modifiers & KeyEvent.ALT_DOWN_MASK) != 0) {
			showAllCandidates = true;
		}
		
		if (oldShowAllCandidatesAkt != showAllCandidatesAkt || oldShowAllCandidates != showAllCandidates) {
			repaint();
		}
	}

	public void handleKeysReleased(KeyEvent evt) {
		handleMacUnitHighlightKeyReleased(evt);

		int modifiers = evt.getModifiersEx();
		int keyCode = 0;

		if (SudokuUtil.isMenuShortcutKey(evt)) {
			isCtrlDown = false;
			clearLastCandidateMouseOn();
			repaint();
		}

		checkShowAllCandidates(modifiers, keyCode);

	}

	public void handleKeys(KeyEvent evt) {
		if (handleMacUnitHighlightKeyPressed(evt)) {
			return;
		}

		boolean changed = false;
		boolean notifySudokuStateChanged = false;
		undoStack.push(sudoku.clone());

		int keyCode = evt.getKeyCode();
		int modifiers = evt.getModifiersEx();

		checkShowAllCandidates(modifiers, keyCode);

		if (!isCtrlDown && SudokuUtil.isMenuShortcutKey(evt)) {
			isCtrlDown = true;
			updateCandidateMouseHighlight(lastMousePosition);
		}

		// 20120111: makes problems on certain laptops where key combinations are
		// used to produce numbers. New try: If getKeyChar() gives a number, the
		// corresponding key code is set
		char keyChar = evt.getKeyChar();
		if (Character.isDigit(keyChar)) {
			keyCode = KEY_CODES[keyChar - '0'];
		}

		if (!hasActiveCell() && isNavigationKey(keyCode)) {
			restoreEmptySelectionForNavigation();
			undoStack.pop();
			return;
		}
		
		int number = 0;
		boolean clearSelectedRegion = true;
		switch (keyCode) {
		case KeyEvent.VK_DOWN:
			
			if (SudokuUtil.isMenuShortcutDown(modifiers) && 
				(modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0	&& 
				getShowHintCellValue() != 0) {
				// go to next filtered candidate
				int index = findNextHintCandidate(getFirstRow(), getFirstCol(), keyCode);
				setActiveCell(Sudoku2.getRow(index), Sudoku2.getCol(index));
			} else if (getActiveRow() < 8) {
				
				// go to the next row
				setActiveCell(getActiveRow() + 1, getActiveCol());
				
				if (SudokuUtil.isMenuShortcutDown(modifiers)) {
					// go to the next unset cell
					while (getActiveRow() < 8 && sudoku.getValue(getActiveRow(), getActiveCol()) != 0) {
						setActiveCell(getActiveRow() + 1, getActiveCol());
					}
				} else if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
					// expand the selected region
					
					setShift();
					//setActiveCell(getActiveRow() - 1, getActiveCol());
					//if (shiftRow < 8) {
					//	shiftRow++;
					//}
					
					selectRegion(shiftRow, shiftCol);
					clearSelectedRegion = false;
				}
				
			} else if (getActiveRow() == 8) {
				setActiveCell(0, getActiveCol());
			}

			if (clearSelectedRegion) {
				clearRegion();
			}

			break;
		case KeyEvent.VK_UP:
			if (SudokuUtil.isMenuShortcutDown(modifiers) && 
				(modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 && 
				getShowHintCellValue() != 0) {
				// go to next filtered candidate
				int index = findNextHintCandidate(getFirstRow(), getFirstCol(), keyCode);
				setActiveCell(Sudoku2.getRow(index), Sudoku2.getCol(index));
			} else if (getActiveRow() > 0) {
				// go to the next row
				setActiveCell(getActiveRow() - 1, getActiveCol());
				if (SudokuUtil.isMenuShortcutDown(modifiers)) {
					// go to the next unset cell
					while (getActiveRow() > 0 && sudoku.getValue(getActiveRow(), getActiveCol()) != 0) {
						setActiveCell(getActiveRow() - 1, getActiveCol());
					}
				} else if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
					// expand the selected region
					
					setShift();
					//setActiveCell(getActiveRow() + 1, getActiveCol());
					
					//if (shiftRow > 0) {
					//	shiftRow--;
					//}
					
					selectRegion(shiftRow, shiftCol);
					clearSelectedRegion = false;
				}
			} else if (getActiveRow() == 0) {
				setActiveCell(8, getActiveCol());
			}

			if (clearSelectedRegion) {
				clearRegion();
			}

			break;
		case KeyEvent.VK_RIGHT:
			if (SudokuUtil.isMenuShortcutDown(modifiers) && 
				(modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0 &&
				getShowHintCellValue() != 0) {
				// go to next filtered candidate
				int index = findNextHintCandidate(getFirstRow(), getFirstCol(), keyCode);
				setActiveCell(Sudoku2.getRow(index), Sudoku2.getCol(index));
			} else if (getActiveCol() < 8) {
				// go to the next row
				setActiveCell(getActiveRow(), getActiveCol() + 1);
				if (SudokuUtil.isMenuShortcutDown(modifiers)) {
					// go to the next unset cell
					while (getActiveCol() < 8 && sudoku.getValue(getActiveRow(), getActiveCol()) != 0) {
						setActiveCell(getActiveRow(), getActiveCol() + 1);
					}
				} else if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
					// expand the selected region
					
					setShift();
					//setActiveCell(getActiveRow(), getActiveCol() - 1);
					
					//if (shiftCol < 8) {
					//	shiftCol++;
					//}
					
					selectRegion(shiftRow, shiftCol);
					clearSelectedRegion = false;
				}
			} else if (getActiveCol() == 8) {
				setActiveCell(getActiveRow(), 0);
			}

			if (clearSelectedRegion) {
				clearRegion();
			}

			break;
		case KeyEvent.VK_LEFT:
			if (SudokuUtil.isMenuShortcutDown(modifiers) && 
				(modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0	&& 
				getShowHintCellValue() != 0) {
				// go to next filtered candidate
				int index = findNextHintCandidate(getFirstRow(), getFirstCol(), keyCode);
				setActiveCell(Sudoku2.getRow(index), Sudoku2.getCol(index));
			} else if (getActiveCol() > 0) {
				// go to the next col
				setActiveCell(getActiveRow(), getActiveCol() - 1);
				if (SudokuUtil.isMenuShortcutDown(modifiers)) {
					// go to the next unset cell
					while (getActiveCol() > 0 && sudoku.getValue(getActiveRow(), getActiveCol()) != 0) {
						setActiveCell(getActiveRow(), getActiveCol() - 1);
					}
				} else if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
					// expand the selected region
					
					setShift();
					//setActiveCell(getActiveRow(), getActiveCol() + 1);
					
					//if (shiftCol > 0) {
					//	shiftCol--;
					//}
					
					selectRegion(shiftRow, shiftCol);
					clearSelectedRegion = false;
				}
			} else if (getActiveCol() == 0) {
				setActiveCell(getActiveRow(), 8);
			}

			if (clearSelectedRegion) {
				clearRegion();
			}

			break;
		case KeyEvent.VK_HOME:
			if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
				
				setShift();
				if (SudokuUtil.isMenuShortcutDown(modifiers)) {
					shiftRow = 0;
				} else {
					shiftCol = 0;
				}
				
				selectRegion(shiftRow, shiftCol);
				clearSelectedRegion = false;
			} else {
				if (SudokuUtil.isMenuShortcutDown(modifiers)) {
					setActiveCell(0, getActiveCol());
				} else {
					setActiveCell(getActiveRow(), 0);
				}
			}
			
			if (clearSelectedRegion) {
				clearRegion();
			}
			
			break;
		case KeyEvent.VK_END:
			if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
				setShift();
				if (SudokuUtil.isMenuShortcutDown(modifiers)) {
					shiftRow = 8;
				} else {
					shiftCol = 8;
				}
				
				selectRegion(shiftRow, shiftCol);
				clearSelectedRegion = false;
				
			} else {
				
				if (SudokuUtil.isMenuShortcutDown(modifiers)) {
					setActiveCell(8, getActiveCol());
				} else {
					setActiveCell(getActiveRow(), 8);
				}
			}
			
			if (clearSelectedRegion) {
				clearRegion();
			}
			
			break;
		case KeyEvent.VK_ENTER: {
			if (!hasActiveCell()) {
				break;
			}
			int index = Sudoku2.getIndex(getActiveRow(), getActiveCol());
			if (sudoku.getValue(index) == 0) {
				int showHintCellValue = getShowHintCellValue();
				if (sudoku.getAnzCandidates(index, !showCandidates) == 1) {
					// Naked single -> set it!
					int actCand = sudoku.getAllCandidates(index, !showCandidates)[0];
					setCell(getActiveRow(), getActiveCol(), actCand);
					changed = true;
				} else if (showHintCellValue != 0 && isHiddenSingle(showHintCellValue, getActiveRow(), getActiveCol())) {
					// Hidden Single -> it
					setCell(getActiveRow(), getActiveCol(), showHintCellValue);
					changed = true;
				}
			}
		}
			break;
		case KeyEvent.VK_9:
		case KeyEvent.VK_NUMPAD9:
			number++;
		case KeyEvent.VK_8:
		case KeyEvent.VK_NUMPAD8:
			number++;
		case KeyEvent.VK_7:
		case KeyEvent.VK_NUMPAD7:
			number++;
		case KeyEvent.VK_6:
		case KeyEvent.VK_NUMPAD6:
			number++;
		case KeyEvent.VK_5:
		case KeyEvent.VK_NUMPAD5:
			number++;
		case KeyEvent.VK_4:
		case KeyEvent.VK_NUMPAD4:
			number++;
		case KeyEvent.VK_3:
		case KeyEvent.VK_NUMPAD3:
			number++;
		case KeyEvent.VK_2:
		case KeyEvent.VK_NUMPAD2:
			number++;
		case KeyEvent.VK_1:
		case KeyEvent.VK_NUMPAD1:
			number++;
			if (handleMacCandidateFilterShortcut(evt, modifiers, number)) {
				break;
			}
			if (!hasActiveCell()) {
				break;
			}
			if (!SudokuUtil.isMenuShortcutDown(modifiers)) {
				if (cellSelection.isEmpty()) {
					setCell(getActiveRow(), getActiveCol(), number);
					setCandidateFilterByGiven(getActiveRow(), getActiveCol());
					if (mainFrame.isInputMode() && Options.getInstance().isEditModeAutoAdvance()) {
						// automatically advance to the next cell
						if (getActiveCol() < 8) {
							setActiveCell(getActiveRow(), getActiveCol() + 1);
						} else if (getActiveRow() < 8) {
							setActiveCell(getActiveRow() + 1, 0);
						}
					}
				} else {
					
					// set value only in cells where the candidate is still present
					// problem: setting the first removes all other candidates in the
					// corresponding blocks so we have to collect the applicable cells first
					
					/*
					List<Integer> cells = new ArrayList<Integer>();
					for (int index : cellSelection) {
						if (sudoku.getValue(index) == 0 && sudoku.isCandidate(index, number, !showCandidates)) {
							cells.add(index);
						}
					}*/
					
					List<Integer> cells = new ArrayList<Integer>(cellSelection);
					Integer activeIndex = Integer.valueOf(Sudoku2.getIndex(getActiveRow(), getActiveCol()));
					if (!cells.contains(activeIndex)) {
						cells.add(activeIndex);
					}
					
					if (cells.size() == 1) {
						setCell(getActiveRow(), getActiveCol(), number);
						setCandidateFilterByGiven(getActiveRow(), getActiveCol());
					} else {
						for (int index : cells) {
							setCell(Sudoku2.getRow(index), Sudoku2.getCol(index), number);
						}	
					}
				}
				
				changed = true;
				
			} else {
				// only when shift is NOT pressed (if pressed its a menu accelerator)
				// 20120115: the accelerators have been removed!
				if (cellSelection.isEmpty()) {
					toggleCandidateInCell(getActiveRow(), getActiveCol(), number);
					changed = true;
					notifySudokuStateChanged = true;
				} else {
					changed = toggleCandidateInAktCells(number);
					notifySudokuStateChanged = changed;
				}
			}
			
			break;
		case KeyEvent.VK_BACK_SPACE:
		case KeyEvent.VK_DELETE:
		case KeyEvent.VK_0:
		case KeyEvent.VK_NUMPAD0:
			if (handleMacCandidateCountFilterShortcut(evt, modifiers, keyCode)) {
				break;
			}
			if (!hasActiveCell()) {
				break;
			}
			
			if (!SudokuUtil.isMenuShortcutDown(modifiers)) {
				
				if (sudoku.getValue(getActiveRow(), getActiveCol()) != 0 && !sudoku.isFixed(getActiveRow(), getActiveCol())) {
					sudoku.setCell(getActiveRow(), getActiveCol(), 0);
					setCandidateFilterByGiven(getActiveRow(), getActiveCol());
					changed = true;
					notifySudokuStateChanged = true;
				}
				
				if (mainFrame.isInputMode() && Options.getInstance().isEditModeAutoAdvance()) {
					// automatically advance to the next cell
					if (getActiveCol() < 8) {
						setActiveCell(getActiveRow(), getActiveCol() + 1);
					} else if (getActiveRow() < 8) {
						setActiveCell(getActiveRow() + 1, 0);
					}
				}
			}
			
			break;
		case KeyEvent.VK_F10:
			if ((modifiers & (KeyEvent.ALT_DOWN_MASK | KeyEvent.META_DOWN_MASK
					| KeyEvent.CTRL_DOWN_MASK)) == 0) {
				if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
					toggleTrivalueFilter();
				} else {
					toggleBivalueFilter();
				}
			}
			break;
		case KeyEvent.VK_F9:
			number++;
		case KeyEvent.VK_F8:
			number++;
		case KeyEvent.VK_F7:
			number++;
		case KeyEvent.VK_F6:
			number++;
		case KeyEvent.VK_F5:
			number++;
		case KeyEvent.VK_F4:
			number++;
		case KeyEvent.VK_F3:
			number++;
		case KeyEvent.VK_F2:
			number++;
		case KeyEvent.VK_F1:
			number++;
			if ((modifiers & KeyEvent.ALT_DOWN_MASK) == 0) {
				// pressing <Alt><F4> changes the selection ... not good
				// <fn> toggles the corresponding filter
				// <ctrl><fn> selects an additional candidate for filtering
				// <shift><fn> additionally toggles the filter mode
				if (SudokuUtil.isMenuShortcutDown(modifiers)) {
					toggleCandidateValueFilterFromShortcut(number, true);
				} else {
					if (toggleCandidateValueFilterFromShortcut(number, false)
							&& (modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
						invalidCells = !invalidCells;
					}
				}
			}
			break;
		case KeyEvent.VK_SPACE:
			if (!hasActiveCell()) {
				break;
			}
			int candidate = getShowHintCellValue();
			if (isShowInvalidOrPossibleCells() && candidate != 0) {
				changed = toggleCandidateInAktCells(candidate);
				notifySudokuStateChanged = changed;
			}
			break;
		case KeyEvent.VK_X:
			cellZoomPanel.swapColors();
			break;
		case KeyEvent.VK_E:
			number++;
		case KeyEvent.VK_D:
			number++;
		case KeyEvent.VK_C:
			number++;
		case KeyEvent.VK_B:
			number++;
		case KeyEvent.VK_A:
			if (!hasActiveCell()) {
				break;
			}
			
			// if ctrl or alt or meta is pressed, it's a shortcut
			if ((modifiers & KeyEvent.ALT_DOWN_MASK) != 0 || 
				(modifiers & KeyEvent.ALT_GRAPH_DOWN_MASK) != 0	||
				(modifiers & KeyEvent.META_DOWN_MASK) != 0 ||
				SudokuUtil.isMenuShortcutDown(modifiers)) {
				// do nothing!
				break;
			}
			
			// calculate index in coloringColors[]
			number *= 2;
			if ((modifiers & KeyEvent.SHIFT_DOWN_MASK) != 0) {
				number++;
			}
			
			handleColoring(-1, Options.getInstance().getColoringColors()[number]);
			
			break;
		case KeyEvent.VK_R:
			if (modifiers == 0 || modifiers == KeyEvent.SHIFT_DOWN_MASK) {
				if (hasColoring()) {
					changed = clearColoringUsingExistingUndoEntry();
				}
			}
			break;
		case KeyEvent.VK_T:
			if (modifiers == 0 || modifiers == KeyEvent.SHIFT_DOWN_MASK) {
				mainFrame.cycleColoringMode(modifiers == KeyEvent.SHIFT_DOWN_MASK);
			}
			break;
		case KeyEvent.VK_GREATER:
		case KeyEvent.VK_COMMA:
		case KeyEvent.VK_LESS:
		case KeyEvent.VK_PERIOD:
		default:
			// doesn't work on all keyboards :-(
			// more precisely: doesn't work, if the keyboard layout in the OS
			// doesn't match the physical layout of the keyboard
			short rem = sudoku.getRemainingCandidates();
			char ch = evt.getKeyChar();
			int shortcutModifiers = KeyEvent.CTRL_DOWN_MASK | KeyEvent.META_DOWN_MASK
					| KeyEvent.ALT_DOWN_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK;
			if ((modifiers & shortcutModifiers) == 0
					&& (ch == '<' || ch == '>' || ch == ',' || ch == '.')) {
				boolean isUp = ch == '>' || ch == '.';
				if (isShowInvalidOrPossibleCells()) {
					int cand = 0;
					for (int i = 1; i <= Sudoku2.UNITS; i++) {
						if (showHintCellValues[i]) {
							cand = i;
							if (!isUp) {
								// get the first candidate
								break;
							}
						}
					}
					
					int count = 0;
					do {
						if (isUp) {
							cand++;
							if (cand > Sudoku2.UNITS) {
								cand = 1;
							}
						} else {
							cand--;
							if (cand < 1) {
								cand = Sudoku2.UNITS;
							}
						}
						count++;
					} while (count < 8 && (Sudoku2.MASKS[cand] & rem) == 0);
					
					if (count < 8) {
						// if only one candidate is left for filtering,
						// it would be toggled without this check
						setShowHintCellValue(cand);
						checkIsShowInvalidOrPossibleCells();
					}
				}
			}
			
			break;
		}
		
		if (changed) {
			redoStack.clear();
			redoColoringStates.clear();
			checkProgress();
			if (notifySudokuStateChanged) mainFrame.sudokuStateChanged();
		} else {
			undoStack.pop();
		}
		
		updateCellZoomPanel();
		mainFrame.check();
		repaint();
	}

	private boolean handleMacCandidateFilterShortcut(KeyEvent event, int modifiers, int candidate) {
		if (!SudokuUtil.isMacOS() || (modifiers & KeyEvent.ALT_DOWN_MASK) == 0) {
			return false;
		}

		int keyboardModifiers = KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK
				| KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK
				| KeyEvent.ALT_GRAPH_DOWN_MASK;
		int relevantModifiers = modifiers & keyboardModifiers;
		if (relevantModifiers == KeyEvent.ALT_DOWN_MASK) {
			toggleCandidateValueFilterFromShortcut(candidate, false);
		} else if (relevantModifiers == (KeyEvent.ALT_DOWN_MASK | KeyEvent.META_DOWN_MASK)) {
			toggleCandidateValueFilterFromShortcut(candidate, true);
		}

		event.consume();
		return true;
	}

	boolean handleMacUnitHighlightKeyPressed(KeyEvent event) {
		if (!SudokuUtil.isMacOS()) {
			return false;
		}

		int keyboardModifiers = KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK
				| KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK
				| KeyEvent.ALT_GRAPH_DOWN_MASK;
		int requiredModifiers = KeyEvent.SHIFT_DOWN_MASK | KeyEvent.ALT_DOWN_MASK;
		if ((event.getModifiersEx() & keyboardModifiers) != requiredModifiers) {
			return false;
		}

		int keyCode = event.getKeyCode();
		SudokuTextReference.Kind kind = unitHighlightKindForKey(keyCode);
		if (kind != null) {
			if (keyboardUnitHighlightKind != kind) {
				clearTransientReferenceHighlight();
				keyboardUnitHighlightDigitKey = KeyEvent.VK_UNDEFINED;
			}
			keyboardUnitHighlightKind = kind;
			event.consume();
			return true;
		}

		int number = numberForKeyCode(keyCode);
		if (keyboardUnitHighlightKind == null || number == 0) {
			return false;
		}

		char prefix = keyboardUnitHighlightKind == SudokuTextReference.Kind.ROWS ? 'r'
				: keyboardUnitHighlightKind == SudokuTextReference.Kind.COLUMNS ? 'c' : 'b';
		setTransientReferenceHighlight(SudokuReferenceParser.parse(prefix + Integer.toString(number)).get(0));
		keyboardUnitHighlightDigitKey = keyCode;
		event.consume();
		return true;
	}

	boolean handleMacUnitHighlightKeyReleased(KeyEvent event) {
		if (keyboardUnitHighlightKind == null) {
			return false;
		}
		int keyCode = event.getKeyCode();
		if (keyCode == keyboardUnitHighlightDigitKey) {
			clearTransientReferenceHighlight();
			keyboardUnitHighlightDigitKey = KeyEvent.VK_UNDEFINED;
			event.consume();
			return true;
		} else if (keyCode == KeyEvent.VK_ALT || keyCode == KeyEvent.VK_SHIFT
				|| unitHighlightKindForKey(keyCode) == keyboardUnitHighlightKind) {
			resetKeyboardUnitHighlight();
			event.consume();
			return true;
		}
		return false;
	}

	private void resetKeyboardUnitHighlight() {
		if (keyboardUnitHighlightKind != null) {
			clearTransientReferenceHighlight();
		}
		keyboardUnitHighlightKind = null;
		keyboardUnitHighlightDigitKey = KeyEvent.VK_UNDEFINED;
	}

	private SudokuTextReference.Kind unitHighlightKindForKey(int keyCode) {
		switch (keyCode) {
		case KeyEvent.VK_R:
			return SudokuTextReference.Kind.ROWS;
		case KeyEvent.VK_C:
			return SudokuTextReference.Kind.COLUMNS;
		case KeyEvent.VK_B:
			return SudokuTextReference.Kind.BLOCKS;
		default:
			return null;
		}
	}

	private int numberForKeyCode(int keyCode) {
		if (keyCode >= KeyEvent.VK_1 && keyCode <= KeyEvent.VK_9) {
			return keyCode - KeyEvent.VK_0;
		}
		if (keyCode >= KeyEvent.VK_NUMPAD1 && keyCode <= KeyEvent.VK_NUMPAD9) {
			return keyCode - KeyEvent.VK_NUMPAD0;
		}
		return 0;
	}

	private boolean handleMacCandidateCountFilterShortcut(KeyEvent event, int modifiers, int keyCode) {
		if (!SudokuUtil.isMacOS() || (keyCode != KeyEvent.VK_0 && keyCode != KeyEvent.VK_NUMPAD0)) {
			return false;
		}

		int keyboardModifiers = KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK
				| KeyEvent.META_DOWN_MASK | KeyEvent.ALT_DOWN_MASK
				| KeyEvent.ALT_GRAPH_DOWN_MASK;
		int relevantModifiers = modifiers & keyboardModifiers;
		if (relevantModifiers == KeyEvent.ALT_DOWN_MASK) {
			toggleBivalueFilter();
		} else if (relevantModifiers == (KeyEvent.ALT_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK)) {
			toggleTrivalueFilter();
		} else {
			return false;
		}

		event.consume();
		return true;
	}

	/**
	 * Clears a selected region of cells
	 */
	private void clearRegion() {
		if (cellSelection.isEmpty()) {
			shiftRow = -1;
			shiftCol = -1;
			return;
		}

		clearSelection();
		shiftRow = -1;
		shiftCol = -1;
		
		Integer index = Integer.valueOf(Sudoku2.getIndex(getActiveRow(), getActiveCol()));
		if (!cellSelection.contains(index)) {
			//cellSelection.add(index);
			setActiveCell(index);
		}
	}

	/**
	 * Select all cells in the rectangle defined by {@link #activeRow}/{@link #activeCol}
	 * and row/col
	 *
	 * @param row
	 * @param col
	 */
	private void selectRegion(int row, int col) {
		
		Integer startIndex = Integer.valueOf(Sudoku2.getIndex(getFirstRow(), getFirstCol()));
		Integer endIndex = Integer.valueOf(Sudoku2.getIndex(getActiveRow(), getActiveCol()));
		
		//clearSelection();
		if (row == getActiveRow() && col == getActiveCol()) {
			// same cell clicked twice -> no region selected -> do nothing
		} else {
			
			// every cell in the region gets selected, aktRow and aktCol are not changed
			int cStart = col < getActiveCol() ? col : getActiveCol();
			int rStart = row < getActiveRow() ? row : getActiveRow();
			int cEnd = cStart + Math.abs(col - getActiveCol());
			int rEnd = rStart + Math.abs(row - getActiveRow());
			
			// make sure selection starts with the same index.
			cellSelection.clear();
			cellSelection.add(startIndex);
			
			for (int c = cStart; c <= cEnd; c++) {
				for (int r = rStart; r <= rEnd; r++) {
					Integer cv = Integer.valueOf(Sudoku2.getIndex(r, c));
					if (!cellSelection.contains(cv)) {
						cellSelection.add(cv);
					}
				}
			}
			
			// fix the end index (just in case)
			setActiveCell(endIndex);
			/*
			if (!cellSelection.contains(endIndex)) {
				cellSelection.add(endIndex);
			} else {
				cellSelection.remove(endIndex);
				cellSelection.add(endIndex);
			}*/
		}
	}

	/**
	 * Initializes {@link #shiftRow}/{@link #shiftCol} for selecting regions of
	 * cells using the keyboard
	 */
	private void setShift() {
		if (shiftRow == -1) {
			shiftRow = getFirstRow();
			shiftCol = getFirstCol();
		}
	}

	/**
	 * Finds the next colored cell, if filters are applied. mode gives the direction
	 * in which to search (as KeyEvent). The search wraps at sudoku boundaries.
	 *
	 * @param row
	 * @param col
	 * @param mode
	 * @return
	 */
	private int findNextHintCandidate(int row, int col, int mode) {

		int index = Sudoku2.getIndex(row, col);
		int showHintCellValue = getShowHintCellValue();

		if (showHintCellValue == 0) {
			return index;
		}

		switch (mode) {
		case KeyEvent.VK_DOWN:
			
			// let's start with the next row
			row++;
			if (row == Sudoku2.UNITS) {
				row = 0;
				col++;
				if (col == Sudoku2.UNITS) {
					return index;
				}
			}
			
			for (int i = col; i < Sudoku2.UNITS; i++) {
				int j = i == col ? row : 0;
				for (; j < Sudoku2.UNITS; j++) {
					if (sudoku.getValue(j, i) == 0 && sudoku.isCandidate(j, i, showHintCellValue, !showCandidates)) {
						return Sudoku2.getIndex(j, i);
					}
				}
			}
			
			break;
		case KeyEvent.VK_UP:
			// let's start with the previous row
			row--;
			if (row < 0) {
				row = 8;
				col--;
				if (col < 0) {
					return index;
				}
			}
			
			for (int i = col; i >= 0; i--) {
				int j = i == col ? row : 8;
				for (; j >= 0; j--) {
					if (sudoku.getValue(j, i) == 0 && sudoku.isCandidate(j, i, showHintCellValue, !showCandidates)) {
						return Sudoku2.getIndex(j, i);
					}
				}
			}
			break;
		case KeyEvent.VK_LEFT:
			// lets start left
			index--;
			if (index < 0) {
				return index + 1;
			}
			
			while (index >= 0) {
				if (sudoku.getValue(index) == 0 && sudoku.isCandidate(index, showHintCellValue, !showCandidates)) {
					return index;
				}
				index--;
			}
			
			if (index < 0) {
				index = Sudoku2.getIndex(row, col);
			}
			
			break;
		case KeyEvent.VK_RIGHT:
			// lets start right
			index++;
			if (index >= sudoku.getCells().length) {
				return index - 1;
			}
			
			while (index < sudoku.getCells().length) {
				if (sudoku.getValue(index) == 0 && sudoku.isCandidate(index, showHintCellValue, !showCandidates)) {
					return index;
				}
				index++;
			}
			
			if (index >= sudoku.getCells().length) {
				index = Sudoku2.getIndex(row, col);
			}
			
			break;
		}
		return index;
	}
	
	public void clearCandidateColors() {
		coloringCandidateMap.clear();
		updateCellZoomPanel();
		mainFrame.check();
	}
	
	public void clearCellColors() {
		coloringMap.clear();
		updateCellZoomPanel();
		mainFrame.check();
	}

	/**
	 * Removes all coloring info
	 */
	public void clearColoring() {
		coloringMap.clear();
		coloringCandidateMap.clear();
		updateCellZoomPanel();
		mainFrame.check();
	}

	/** Clears user coloring as one undoable action, without changing Sudoku data. */
	public void clearColoringWithUndo() {
		clearColoringWithUndo(true);
	}

	private boolean clearColoringWithUndo(boolean refreshUi) {
		if (!hasColoring()) {
			return false;
		}
		pushColoringUndo();
		coloringMap.clear();
		coloringCandidateMap.clear();
		if (refreshUi) {
			updateCellZoomPanel();
			mainFrame.check();
		}
		return true;
	}

	private void pushColoringUndo() {
        refreshAnnotationTimeline();
		if (coloringUndoStack.size() == ANNOTATION_UNDO_LIMIT) {
			coloringUndoStack.remove(0);
		}
		coloringUndoStack.push(new ColoringSnapshot(coloringMap, coloringCandidateMap));
		coloringRedoStack.clear();
	}

	private void restoreColoring(ColoringSnapshot snapshot) {
		coloringMap = new TreeMap<Integer, Color>(snapshot.cells);
		coloringCandidateMap = new TreeMap<Integer, Color>(snapshot.candidates);
		updateCellZoomPanel();
		mainFrame.check();
		repaint();
	}

	private void undoColoring() {
		if (coloringUndoStack.isEmpty()) {
			return;
		}
		if (coloringRedoStack.size() == ANNOTATION_UNDO_LIMIT) {
			coloringRedoStack.remove(0);
		}
		coloringRedoStack.push(new ColoringSnapshot(coloringMap, coloringCandidateMap));
		restoreColoring(coloringUndoStack.pop());
	}

	private void redoColoring() {
		if (coloringRedoStack.isEmpty()) {
			return;
		}
		if (coloringUndoStack.size() == ANNOTATION_UNDO_LIMIT) {
			coloringUndoStack.remove(0);
		}
		coloringUndoStack.push(new ColoringSnapshot(coloringMap, coloringCandidateMap));
		restoreColoring(coloringRedoStack.pop());
	}

	/**
	 * The key handler already pushed the current Sudoku before dispatching R. Reuse
	 * that entry so one R press creates exactly one undoable action.
	 */
	private boolean clearColoringUsingExistingUndoEntry() {
		if (!hasColoring()) {
			return false;
		}
		Sudoku2 before = undoStack.isEmpty() ? sudoku.clone() : undoStack.peek();
		if (undoStack.isEmpty()) {
			undoStack.push(before);
		}
		rememberColoringClear(before);
		clearColoring();
		return true;
	}

	private void rememberColoringClear(Sudoku2 before) {
		undoColoringStates.put(before, new ColoringSnapshot(coloringMap, coloringCandidateMap));
		redoStack.clear();
		redoColoringStates.clear();
	}

	public boolean hasColoring() {
		return !coloringMap.isEmpty() || !coloringCandidateMap.isEmpty();
	}

	/**
	 * Handles coloring for all selected cells, delegates to
	 * {@link #handleColoring(int, int, int, int)} (see description there).
	 *
	 * @param candidate
	 * @param colorNumber
	 */
	public void handleColoring(int candidate, Color color) {
		if (cellSelection.isEmpty()) {
			return;
		} else {
			pushColoringUndo();
			for (int index : cellSelection) {
				handleColoringInternal(Sudoku2.getRow(index), Sudoku2.getCol(index), candidate, color);
			}
		}
	}

	public Color getActiveColor() {
		
		if (cellZoomPanel.isDefaultMouse()) {
			return null;
		}
		
		return cellZoomPanel.getPrimaryColor();
	}
	
	/**
	 * Toggles Color for candidate in active cell; only called from
	 * {@link CellZoomPanel}.
	 *
	 * @param candidate
	 */
	public void handleColoring(int candidate) {
		if (!hasActiveCell()) {
			return;
		}
		handleColoring(getActiveRow(), getActiveCol(), candidate, cellZoomPanel.getPrimaryColor());
		updateCellZoomPanel();
		mainFrame.fixFocus();
		repaint();
	}

	/**
	 * Handles the coloring of a cell or a candidate. If candidate equals -1, a cell
	 * is to be coloured, else a candidate. If the target is already colored and the
	 * new color matches the old one, coloring is removed, else it is set to the new
	 * color.<br>
	 * {@link Options#colorValues} decides, whether cells, that have already been
	 * set, may be colored.
	 *
	 * @param row
	 * @param col
	 * @param candidate
	 * @param colorNumber
	 */
	public void handleColoring(int row, int col, int candidate, Color color) {
		if (!Sudoku2.isValidIndex(row, col) || color == null) {
			return;
		}
		
		if (!Options.getInstance().isColorValues() && sudoku.getValue(row, col) != 0) {
			return;
		}
		pushColoringUndo();
		handleColoringInternal(row, col, candidate, color);
	}

	private void handleColoringInternal(int row, int col, int candidate, Color color) {
		
		SortedMap<Integer, Color> map = coloringMap;
		int key = Sudoku2.getIndex(row, col);
		if (candidate != -1) {
			key = key * 10 + candidate;
			map = coloringCandidateMap;
		}
		
		if (map.containsKey(key) && map.get(key).equals(color)) {
			// pressing the same key on the same cell twice removes the coloring
			map.remove(key);
		} else {
			// either newly colored cell or change of cell color
			map.put(key, color);
		}
		
		updateCellZoomPanel();
	}

	/**
	 * Handles "set value" done in {@link CellZoomPanel}. Should not be used
	 * otherwise.
	 *
	 * @param number
	 */
	public void setCellFromCellZoomPanel(int number) {
		if (annotationTool != AnnotationTool.DEFAULT_MOUSE || !hasActiveCell()) {
			return;
		}
		
		undoStack.push(sudoku.clone());
		if (cellSelection.isEmpty()) {
			setCell(getActiveRow(), getActiveCol(), number);
		} else {
			for (int index : cellSelection) {
				setCell(Sudoku2.getRow(index), Sudoku2.getCol(index), number);
			}
		}
		
		updateCellZoomPanel();
		mainFrame.check();
		repaint();
	}

	public void setCell(int row, int col, int number) {
		if (!Sudoku2.isValidIndex(row, col)) {
			return;
		}

		int index = Sudoku2.getIndex(row, col);
		if (!sudoku.isFixed(index) && sudoku.getValue(index) != number) {
			
			if (sudoku.getValue(index) != 0) {
				sudoku.setCell(row, col, 0);
			}
			
			sudoku.setCell(row, col, number);
			repaint();
			mainFrame.sudokuStateChanged();
			
			if (sudoku.isSolved() && Options.getInstance().isShowSudokuSolved()) {
				JOptionPane.showMessageDialog(
					this,
					java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.sudoku_solved"),
					java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.congratulations"),
					JOptionPane.INFORMATION_MESSAGE
				);
			}
		}
	}

	/**
	 * Toggles candidate in all active cells (all cells in {@link #cellSelection} or
	 * cell denoted by {@link #activeRow}/{@link #activeCol} if {@link #cellSelection} is
	 * empty).<br>
	 *
	 * @param candidate
	 * @return <code>true</code>, if at least one cell was changed
	 */
	private boolean toggleCandidateInAktCells(int candidate) {
		
		boolean changed = false;
		if (cellSelection.isEmpty()) {
			return false;
		} else {
			
			boolean candPresent = false;
			for (int index : cellSelection) {
				if (sudoku.getValue(index) == 0 && sudoku.isCandidate(index, candidate, !showCandidates)) {
					candPresent = true;
					break;
				}
			}
			
			for (int index : cellSelection) {
				if (candPresent) {
					if (sudoku.getValue(index) == 0 && sudoku.isCandidate(index, candidate, !showCandidates)) {
						sudoku.setCandidate(index, candidate, false, !showCandidates);
						changed = true;
					}
				} else {
					if (sudoku.getValue(index) == 0 && !sudoku.isCandidate(index, candidate, !showCandidates)) {
						sudoku.setCandidate(index, candidate, true, !showCandidates);
						changed = true;
					}
				}
			}
		}
		
		updateCellZoomPanel();
		return changed;
	}

	/**
	 * Toggles candidate in the cell denoted by row/col. Uses
	 * {@link #candidateMode}.
	 *
	 * @param row
	 * @param col
	 * @param candidate
	 */
	private void toggleCandidateInCell(int row, int col, int candidate) {
		if (!Sudoku2.isValidIndex(row, col)) {
			return;
		}
		
		int index = Sudoku2.getIndex(row, col);
		if (sudoku.getValue(index) == 0) {
			if (sudoku.isCandidate(index, candidate, !showCandidates)) {
				sudoku.setCandidate(index, candidate, false, !showCandidates);
			} else {
				sudoku.setCandidate(index, candidate, true, !showCandidates);
			}
		}
		
		updateCellZoomPanel();
	}

	/**
	 * Creates an image of the current sudoku in the given size.
	 *
	 * @param size
	 * @return
	 */
	public BufferedImage getSudokuImage(int size) {
		return getSudokuImage(size, false);
	}

	/**
	 * Creates an image of the current sudoku in the given size.
	 *
	 * @param size
	 * @param allBlack
	 * @return
	 */
	public BufferedImage getSudokuImage(int size, boolean allBlack) {
		BufferedImage fileImage = new BufferedImage(size, size, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = fileImage.createGraphics();
		this.g2 = g;
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, size, size);
		drawPage(size, size, true, false, allBlack, 1.0, true);
		return fileImage;
	}

	/**
	 * Prints the current sudoku into the graphics context <code>g</code> at the
	 * position <code>x</code>/ <code>y</code> with size <code>size</code>.
	 *
	 * @param g
	 * @param x
	 * @param y
	 * @param size
	 * @param allBlack
	 * @param scale
	 */
	public void printSudoku(Graphics2D g, int x, int y, int size, boolean allBlack, double scale) {
		Graphics2D oldG2 = this.g2;
		this.g2 = g;
		AffineTransform trans = g.getTransform();
		g.translate(x, y);
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, size, size);
		drawPage(size, size, true, true, allBlack, scale, false);
		g.setTransform(trans);
		this.g2 = oldG2;
	}

	/**
	 * Writes an image of the current sudoku as png into a file. The image is
	 * <code>size</code> pixels wide and high, the resolution in the png file is set
	 * to <code>dpi</code>.
	 *
	 * @param file
	 * @param size
	 * @param dpi
	 */
	public void saveSudokuAsPNG(File file, int size, int dpi) {
		BufferedImage fileImage = getSudokuImage(size);
		writePNG(fileImage, dpi, file);
	}

	@Override
	public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
		
		if (pageIndex > 0) {
			return Printable.NO_SUCH_PAGE;
		}

		// CAUTION: The Graphics2D object is created with the native printer
		// resolution, but scaled down to 72dpi using an AffineTransform.
		// To print in high resolution this downscaling has to be reverted.
		Graphics2D printG2 = (Graphics2D) graphics;
		double scale = SudokuUtil.adjustGraphicsForPrinting(printG2);
		
		printG2.translate((int) (pageFormat.getImageableX() * scale), (int) (pageFormat.getImageableY() * scale));
		int printWidth = (int) (pageFormat.getImageableWidth() * scale);
		int printHeight = (int) (pageFormat.getImageableHeight() * scale);

		// scale fonts up too fit the printer resolution
		Font tmpFont = Options.getInstance().getBigFont();
		bigFont = new Font(tmpFont.getName(), tmpFont.getStyle(), (int) (tmpFont.getSize() * scale));
		tmpFont = Options.getInstance().getSmallFont();
		smallFont = new Font(tmpFont.getName(), tmpFont.getStyle(), (int) (tmpFont.getSize() * scale));
		printG2.setFont(bigFont);
		String title = MainFrame.VERSION;
		FontMetrics metrics = printG2.getFontMetrics();
		int textWidth = metrics.stringWidth(title);
		int textHeight = metrics.getHeight();
		int y = 2 * textHeight;
		printG2.drawString(title, (printWidth - textWidth) / 2, textHeight);

		// Level
		printG2.setFont(smallFont);
		if (sudoku != null && sudoku.getLevel() != null) {
			title = sudoku.getLevel().getName() + " (" + sudoku.getScore() + ")";
			metrics = printG2.getFontMetrics();
			textWidth = metrics.stringWidth(title);
			textHeight = metrics.getHeight();
			printG2.drawString(title, (printWidth - textWidth) / 2, y);
			y += textHeight;
		}

		printG2.translate(0, y);
		this.g2 = printG2;
		drawPage(printWidth, printHeight, true, true, false, scale, false);
		return Printable.PAGE_EXISTS;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (ApplicationAppearance.isDark()) {
			g.setColor(SudokuAppearancePalette.forRendering(false).getWindowBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
		}
		g2 = (Graphics2D) g;
		drawPage(getBounds().width, getBounds().height, false, true, false, 1.0, true);
	}

	/**
	 * Draws the sudoku in its current state on the graphics context denoted by
	 * {@link #g2} (code>g2</code> has to be set before calling this method). The
	 * graphics context can belong to a <code>Component</code> (basic redraw), a
	 * <code>BufferedImage</code> (save Sudoku as image) or to a print canvas.<br>
	 * <br>
	 *
	 * Sudokus are always drawn as quads, even if <code>totalWidth</code> and
	 * <code>totalHeight</code> are not the same. The quadrat is then center within
	 * the available space.
	 *
	 * @param totalWidth  The width of the sudoku in pixel
	 * @param totalHeight The height of the sudoku in pixel
	 * @param isPrint     The sudoku is drawn on a print canvas: always draw at the
	 *                    upper left corner and dont draw a cursor
	 * @param withBorder  A white border of at least {@link #DELTA_RAND} pixels is
	 *                    drawn around the sudoku.
	 * @param allBlack    Replace all colors with black. Should only be used, if
	 *                    filters, steps or coloring are not used.
	 * @param scale       Necessary for high resolution printing
	 * @param includeAnnotations draw user-created marks; images include board-only
	 *                    doodles while printed pages omit them
	 */
	private void drawPage(
			int totalWidth, 
			int totalHeight, 
			boolean isPrint, 
			boolean withBorder, 
			boolean allBlack,
			double scale,
			boolean includeAnnotations) {

		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		SudokuAppearancePalette appearance = SudokuAppearancePalette.forRendering(isPrint);
        refreshAnnotationTimeline();annotationPaintLayer=0;
		boolean renderGeneratedStep = step != null && (!isPrint || reasoningProposal == null);
        java.awt.geom.Area generatedCandidateForeground=new java.awt.geom.Area();
		
		if (lastCursorChanged == -1) {
			lastCursorChanged = System.currentTimeMillis();
		}
		
		gridRegion = calculateGridRegion(new Rectangle(0, 0, totalWidth, totalHeight), isPrint, withBorder);

		int colorKuCellSize = (int) (cellSize * 0.9);

		// get the fonts every time the size of the grid changes or
		// the user selects a different font in the preferences dialog
		Font tmpFont = Options.getInstance().getDefaultValueFont();
		if (valueFont != null) {
			if (!valueFont.getName().equals(tmpFont.getName()) || 
				valueFont.getStyle() != tmpFont.getStyle() || 
				valueFont.getSize() != ((int) (cellSize * Options.getInstance().getValueFontFactor()))) {
				valueFont = new Font(
					tmpFont.getName(), 
					tmpFont.getStyle(),
					(int) (cellSize * Options.getInstance().getValueFontFactor())
				);
			}
		}

		tmpFont = Options.getInstance().getDefaultCandidateFont();
		if (candidateFont != null) {
			if (!candidateFont.getName().equals(tmpFont.getName()) || 
				candidateFont.getStyle() != tmpFont.getStyle() || 
				candidateFont.getSize() != ((int) (cellSize * Options.getInstance().getCandidateFontFactor()))) {
				
				int oldCandidateHeight = candidateHeight;
				candidateFont = new Font(
					tmpFont.getName(), 
					tmpFont.getStyle(),
					(int) (cellSize * Options.getInstance().getCandidateFontFactor())
				);
				
				FontMetrics cm = getFontMetrics(candidateFont);
				candidateHeight = (int) ((cm.getAscent() - cm.getDescent()) * 1.3);
				
				if (candidateHeight != oldCandidateHeight) {
					resetColorKuImages();
				}
			}
		}

		if (oldWidth != gridRegion.width) {
			
			int oldCandidateHeight = candidateHeight;
			oldWidth = gridRegion.width;
			valueFont = new Font(
				Options.getInstance().getDefaultValueFont().getName(),
				Options.getInstance().getDefaultValueFont().getStyle(),
				(int) (cellSize * Options.getInstance().getValueFontFactor())
			);
			candidateFont = new Font(
				Options.getInstance().getDefaultCandidateFont().getName(),
				Options.getInstance().getDefaultCandidateFont().getStyle(),
				(int) (cellSize * Options.getInstance().getCandidateFontFactor())
			);
			
			FontMetrics cm = getFontMetrics(candidateFont);
			candidateHeight = (int) ((cm.getAscent() - cm.getDescent()) * 1.3);
			
			if (candidateHeight != oldCandidateHeight) {
				resetColorKuImages();
			}
		}

		// draw the cells
		// dx, dy: Offset in a cell for drawing values
		// dcx, dcy: Offset in one nineth of a cell for drawing candidates
		// ddx, ddy: Height and width of the background circle for a candidate
		// more specifically: ddy is the diameter of the background circle
		double dx = 0, dy = 0, dcx = 0, dcy = 0, ddy = 0;
		for (int row = 0; row < Sudoku2.UNITS; row++) {
			for (int col = 0; col < Sudoku2.UNITS; col++) {

				// background first (ignore allBlack here!)
				Color cellBackground = appearance.getDefaultCellColor();
				if (Sudoku2.getBlock(Sudoku2.getIndex(row, col)) % 2 != 0) {
					// every other block may have a different background color
					cellBackground = appearance.getAlternateCellColor();
				}

				int cellIndex = Sudoku2.getIndex(row, col);
				boolean isSelected = 
					(row == getActiveRow() && col == getActiveCol()) || 
					cellSelection.contains(Integer.valueOf(cellIndex));
				
				// the cell doesn't count as selected, if the last change of the cursor has been a while
				if (isSelected && cellSelection.size() == 1 && Options.getInstance().isDeleteCursorDisplay()) {
					if ((System.currentTimeMillis() - lastCursorChanged) > Options.getInstance().getDeleteCursorDisplayLength()) {
						isSelected = false;
					}
				}
				
				// don't paint the whole cell yellow, just a small frame, if onlySmallCursors is set
				if (isSelected && !isPrint && !Options.getInstance().isOnlySmallCursors()
						&& !appearance.isDark()) {
					setColor(g2, allBlack, appearance.getActiveCellColor());
				}
				
				// check if the candidate denoted by showHintCellValue is a valid candidate; if
				// showCandidates == true,
				// this can be done by SudokuCell.isCandidateValid(); if it is false, candidates
				// entered by the user
				// are highlighted, regardless of validity
				// CHANGE: no filters if showCandiates == false
				boolean candidateValueMatch = candidateValueFilterMatches(cellIndex);
				boolean candidateCountMatch = candidateCountFilterMatches(cellIndex);
				boolean filterOverlap = candidateValueMatch && candidateCountMatch;
				boolean filterMatch = candidateValueMatch || candidateCountMatch;
				
				// highlight (filter)
				if (isShowInvalidOrPossibleCells()) {
					
					// highlighting
					if (!isInvalidCells()) {
						
						if (sudoku.getValue(cellIndex) == 0 &&
								filterMatch &&
							!Options.getInstance().isOnlySmallFilters()) {
							if (candidateCountMatch) {
								cellBackground = candidateCountFilterColor(cellIndex, appearance);
							} else {
								cellBackground = appearance.getPossibleCellColor();
							}
						} else if (Options.getInstance().isHighlightingGivens() && 
								   sudoku.getValue(cellIndex) != 0 &&
								   hasCandidateValueFilter() &&
								   showHintCellValues[sudoku.getValue(cellIndex)]) {
							cellBackground = appearance.getPossibleFixedCellColor();
						}
					}
					
					// inverse highlight
					if (isInvalidCells() && 
						(sudoku.getValue(cellIndex) != 0 || !filterMatch)) {
						cellBackground = appearance.getInvalidCellColor();
					}
				}

				// coloring
				boolean hasUserCellColoring = coloringMap.containsKey(cellIndex) &&
					(sudoku.getValue(cellIndex) == 0 || 
					Options.getInstance().isColorValues()) &&
					isColoringVisible;
				if (hasUserCellColoring) {
					cellBackground = annotationColor("cell:"+cellIndex,coloringMap.get(cellIndex),cellBackground);
				}
				boolean transientReferenceCell = !isPrint && transientReferenceHighlight != null
						&& transientReferenceHighlight.containsCell(cellIndex);
				if (transientReferenceCell) {
					cellBackground = appearance.getReferenceHighlightBackground(cellBackground);
				}
				boolean showFilterFrame = hasUserCellColoring && !isPrint
						&& !isInvalidCells() && sudoku.getValue(cellIndex) == 0
						&& filterMatch && !Options.getInstance().isOnlySmallFilters();
				
				// draw the cell background
				int cellX = getX(row, col);
				int cellY = getY(row, col);
				setColor(g2, allBlack, cellBackground);
				g2.fillRect(cellX, cellY, cellSize, cellSize);
				if (!isPrint && includeAnnotations) {
					drawBoxReasoningCellUnderlay(g2, cellIndex, cellX, cellY,
							appearance, cellBackground);
				}
				if (transientReferenceCell) {
					setColor(g2, allBlack, appearance.getReferenceHighlightColor());
					drawTransientReferenceFrame(cellIndex, cellX, cellY);
				}
				if (!isPrint && techniquePreviewCells.contains(cellIndex)) {
					setColor(g2, allBlack, appearance.getReferenceHighlightColor());
					drawCellFrame(cellX, cellY, cellSize, Math.max(2, Math.min(4, cellSize / 24)));
				}
				
				boolean drawSelectionFrame = isSelected && !isPrint && (appearance.isDark()
						|| !g2.getColor().equals(appearance.getActiveCellColor()));
				int frameSize = getCellFrameSize(row, col);
				if (showFilterFrame) {
					Color markerColor = candidateCountMatch
							? candidateCountFilterColor(cellIndex, appearance)
							: appearance.getFilterMarkerColor(cellBackground);
					if (drawSelectionFrame) {
						// The yellow and filter frames share the original cursor band. The
						// inner filter portion never advances into candidate positions.
						int greenFrameSize = Math.max(2, frameSize * 3 / 7);
						greenFrameSize = Math.min(greenFrameSize, Math.max(1, frameSize - 2));
						int yellowFrameSize = frameSize - greenFrameSize;
						drawSelectionFrame(cellX, cellY, cellSize, frameSize,
								allBlack, appearance);
						setColor(g2, allBlack, markerColor);
						drawCellFrame(cellX + yellowFrameSize, cellY + yellowFrameSize,
								cellSize - 2 * yellowFrameSize, greenFrameSize);
					} else {
						setColor(g2, allBlack, markerColor);
						drawCellFrame(cellX, cellY, cellSize, Math.max(2, Math.min(4, frameSize)));
					}
				} else if (drawSelectionFrame) {
					drawSelectionFrame(cellX, cellY, cellSize, frameSize,
							allBlack, appearance);
				}

				if (showCandidateHighlight()) {

					// draw candidate mouse highlight
					if (lastCandidateMouseOn != null && 
						lastCandidateMouseOn.getIndex() >= 0 && 
						lastCandidateMouseOn.getIndex() < Sudoku2.LENGTH) {

						int cellColumn = lastCandidateMouseOn.getIndex() % Sudoku2.UNITS;
						int cellRow = lastCandidateMouseOn.getIndex() / Sudoku2.UNITS;

						if (row == cellRow && 
							col == cellColumn && 
							sudoku.getValue(lastCandidateMouseOn.getIndex()) == 0) {

							int startX = getX(cellRow, cellColumn);
							int startY = getY(cellRow, cellColumn);
							double third = cellSize / 3.0;
							double shiftX = ((lastCandidateMouseOn.getValue() - 1) % 3) * third;
							double shiftY = ((lastCandidateMouseOn.getValue() - 1) / 3) * third;
							FontMetrics cm = getFontMetrics(candidateFont);
							g2.setFont(candidateFont);
							int candidate = lastCandidateMouseOn.getValue();
							int cw = (int) (third - g2.getFontMetrics().stringWidth(Integer.toString(candidate)));
							int ch = (int) ((cm.getAscent() - cm.getDescent()) * 1.3);
							int ccx = (int) Math.round(startX + shiftX + third / 2.0 - cw / 2.0);
							int ccy = (int) Math.round(startY + shiftY + third / 2.0 - ch / 2.0);
							dcx = (third - g2.getFontMetrics().stringWidth("8")) / 2.0;
							dcy = (third + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2.0;

							if (!sudoku.isCandidate(lastCandidateMouseOn.getIndex(), lastCandidateMouseOn.getValue())) {
								g2.setColor(appearance.getHoverMissingCandidateColor());
								g2.drawString(
									Integer.toString(candidate), 
									(int) Math.round(startX + dcx + shiftX),
									(int) Math.round(startY + dcy + shiftY)
								);
							}

							g2.setColor(appearance.getHoverBackgroundColor());
							g2.fillRect(ccx, ccy, cw, ch);
							g2.setColor(appearance.getHoverBorderColor());
							g2.drawRect(ccx, ccy, cw, ch);
						}
					}
				}

				// background is done, draw the value
				int startX = getX(row, col);
				int startY = getY(row, col);
				Color offColor = null;
				int offCand = 0;
				if (sudoku.getValue(cellIndex) != 0) {
					
					// value set in cell: draw it
					setColor(g2, allBlack, appearance.getUserValueColor());
					if (sudoku.isFixed(cellIndex)) {
						setColor(g2, allBlack, appearance.getFixedValueColor());
					} else if (isShowWrongValues() && !sudoku.isValidValue(row, col, sudoku.getValue(cellIndex))) {
						offColor = Options.getInstance().getColorKuColor(10);
						offCand = 10;
						setColor(g2, allBlack, appearance.getWrongValueColor());
					} else if (isShowDeviations() && sudoku.isSolutionSet()	&& sudoku.getValue(cellIndex) != sudoku.getSolution(cellIndex)) {
						offColor = Options.getInstance().getColorKuColor(11);
						offCand = 11;
						setColor(g2, allBlack, appearance.getDeviationColor());
					}
					
					g2.setFont(valueFont);
					dx = (cellSize - g2.getFontMetrics().stringWidth("8")) / 2.0;
					dy = (cellSize + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2.0;
					int value = sudoku.getValue(cellIndex);
					if (Options.getInstance().isShowColorKuAct()) {
						
						// draw the corresponding icon
						drawColorBox(
							value, g2, 
							getX(row, col) + (cellSize - colorKuCellSize) / 2,
							getY(row, col) + (cellSize - colorKuCellSize) / 2, 
							colorKuCellSize, true
						);

						if (offColor != null) {
							// invalid values or deviations are shown with an "X" in different colors
							setColor(g2, allBlack, offColor);
							g2.drawString("X", (int) (startX + dx), (int) (startY + dy));
						}
						
					} else {
						// draw the value
						g2.drawString(Integer.toString(value), (int) (startX + dx), (int) (startY + dy));
					}

				} else {
					
					// draw the candidates equally distributed within the cell
					// if showCandidates is false, the candidates are drawn anyway, if
					// the user presses <shift><ctrl> (current cell - showAllCandidatesAkt)
					// or <shift><alt> (all cells - showAllCandidates)
					g2.setFont(candidateFont);
					boolean userCandidates = !showCandidates;
					if (showAllCandidates || showAllCandidatesAkt && row == getActiveRow() && col == getActiveCol()) {
						userCandidates = false;
					}
					
					// calculate the width of the space for one candidate
					double third = cellSize / 3.0;
					dcx = (third - g2.getFontMetrics().stringWidth("8")) / 2.0;
					dcy = (third + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2.0;
					ddy = (g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) * Options.getInstance().getHintBackFactor();

					for (int i = 1; i <= Sudoku2.UNITS; i++) {
						
						offColor = null;
						// one candidate at a time
						if (sudoku.isCandidate(cellIndex, i, userCandidates) || 
							(showCandidates && showDeviations && sudoku.isSolutionSet() && i == sudoku.getSolution(cellIndex))) {
							
							Color hintColor = null;
							Color candColor = appearance.getCandidateColor();
							Color candidateBackground = cellBackground;
							double shiftX = ((i - 1) % 3) * third;
							double shiftY = ((i - 1) / 3) * third;
							
							if (Options.getInstance().isShowColorKuAct()) {
								// Colorku has to be drawm here, or filters, coloring, hints wont be visible
								int ccx = (int) Math.round(startX + shiftX + third / 2.0 - candidateHeight / 2.0);
								int ccy = (int) Math.round(startY + shiftY + third / 2.0 - candidateHeight / 2.0);
								drawColorBox(i, g2, ccx, ccy, candidateHeight, false);
							}

							if (renderGeneratedStep) {
								
								int index = Sudoku2.getIndex(row, col);
								if (step.getIndices().indexOf(index) >= 0 && step.getValues().indexOf(i) >= 0) {
									hintColor = appearance.getHintBackgroundColor();
									candColor = appearance.getHintForegroundColor(
											Options.getInstance().getHintCandidateColor(), Options.HINT_CANDIDATE_COLOR);
								}
								
								int alsIndex = step.getAlsIndex(index, chainIndex);
								if (alsIndex != -1 && ((chainIndex == -1 && !step.getType().isKrakenFish())
										|| alsToShow.contains(alsIndex))) {
									int alsColorIndex = alsIndex
											% Options.getInstance().getHintCandidateAlsBackColors().length;
									hintColor = appearance.getAlsBackgroundColor(alsColorIndex,
											Options.getInstance().getHintCandidateAlsBackColors()[alsColorIndex]);
									candColor = appearance.getHintForegroundColor(
											Options.getInstance().getHintCandidateAlsColors()[alsColorIndex],
											Options.HINT_CANDIDATE_ALS_COLORS[alsColorIndex]);
								}
								
								for (int k = 0; k < step.getChains().size(); k++) {
									
									if (step.getType().isKrakenFish() && chainIndex == -1) {
										// Index 0 means show no chain at all
										continue;
									}
									
									if (chainIndex != -1 && k != chainIndex) {
										// show only one chain in Forcing Chains/Nets
										continue;
									}
									
									Chain chain = step.getChains().get(k);
									for (int j = chain.getStart(); j <= chain.getEnd(); j++) {
										if (chain.getChain()[j] == Integer.MIN_VALUE) {
											// Trennmarker fï¿½r mins -> ignorieren
											continue;
										}
										
										int chainEntry = Math.abs(chain.getChain()[j]);
										int index1 = -1, index2 = -1, index3 = -1;
										if (Chain.getSNodeType(chainEntry) == Chain.NORMAL_NODE) {
											index1 = Chain.getSCellIndex(chainEntry);
										}
										
										if (Chain.getSNodeType(chainEntry) == Chain.GROUP_NODE) {
											index1 = Chain.getSCellIndex(chainEntry);
											index2 = Chain.getSCellIndex2(chainEntry);
											index3 = Chain.getSCellIndex3(chainEntry);
										}
										
										if ((index == index1 || index == index2 || index == index3)
												&& Chain.getSCandidate(chainEntry) == i) {
											if (Chain.isSStrong(chainEntry)) {
												// strong link
												hintColor = appearance.getHintBackgroundColor();
												candColor = appearance.getHintForegroundColor(
														Options.getInstance().getHintCandidateColor(),
														Options.HINT_CANDIDATE_COLOR);
											} else {
												hintColor = appearance.getHintFinBackgroundColor();
												candColor = appearance.getHintForegroundColor(
														Options.getInstance().getHintCandidateFinColor(),
														Options.HINT_CANDIDATE_FIN_COLOR);
											}
										}
									}
								}
								
								for (Candidate cand : step.getFins()) {
									if (cand.getIndex() == index && cand.getValue() == i) {
									hintColor = appearance.getHintFinBackgroundColor();
									candColor = appearance.getHintForegroundColor(
											Options.getInstance().getHintCandidateFinColor(), Options.HINT_CANDIDATE_FIN_COLOR);
									}
								}
								
								for (Candidate cand : step.getEndoFins()) {
									if (cand.getIndex() == index && cand.getValue() == i) {
									hintColor = appearance.getHintEndoFinBackgroundColor();
									candColor = appearance.getHintForegroundColor(
											Options.getInstance().getHintCandidateEndoFinColor(),
											Options.HINT_CANDIDATE_ENDO_FIN_COLOR);
									}
								}
								
								if (step.getValues().contains(i) && step.getColorCandidates().containsKey(index)) {
									hintColor = Options.getInstance().getColoringColors()[step.getColorCandidates()
											.get(index)];
									candColor = appearance.getCandidateColor();
								}
								
								for (Candidate cand : step.getCandidatesToDelete()) {
									if (cand.getIndex() == index && cand.getValue() == i) {
									hintColor = appearance.getHintDeleteBackgroundColor();
									candColor = appearance.getHintForegroundColor(
											Options.getInstance().getHintCandidateDeleteColor(),
											Options.HINT_CANDIDATE_DELETE_COLOR);
									}
								}
								
								for (Candidate cand : step.getCannibalistic()) {
									if (cand.getIndex() == index && cand.getValue() == i) {
									hintColor = appearance.getHintCannibalisticBackgroundColor();
									candColor = appearance.getHintForegroundColor(
											Options.getInstance().getHintCandidateCannibalisticColor(),
											Options.HINT_CANDIDATE_CANNIBALISTIC_COLOR);
									}
								}
							}
							
							if (isShowWrongValues() == true && !sudoku.isCandidateValid(cellIndex, i, userCandidates)) {
								offColor = Options.getInstance().getColorKuColor(10);
								offCand = 10;
								candColor = appearance.getWrongValueColor();
							}
							
							if (!sudoku.isCandidate(cellIndex, i, userCandidates) && isShowDeviations()
									&& sudoku.isSolutionSet() && i == sudoku.getSolution(cellIndex)) {
								offColor = Options.getInstance().getColorKuColor(11);
								offCand = 11;
								candColor = appearance.getDeviationColor();
							}
							
					boolean selectedCandidateValue = hasCandidateValueFilter() && showHintCellValues[i];
					boolean showOverlapCandidateMarker = filterOverlap && selectedCandidateValue
							&& !isPrint && !isInvalidCells();
					boolean showValueCandidateMarker = !filterOverlap
							&& candidateValueMatch && selectedCandidateValue
							&& !isPrint && !isInvalidCells()
							&& Options.getInstance().isOnlySmallFilters();
					boolean showCountCandidateMarker = candidateCountMatch
							&& !isPrint && !isInvalidCells()
							&& Options.getInstance().isOnlySmallFilters()
							&& !showOverlapCandidateMarker;

					// highlight/filters candidates instead of cells
					if (showOverlapCandidateMarker || showValueCandidateMarker || showCountCandidateMarker) {

						if (showOverlapCandidateMarker || showValueCandidateMarker) {
							setColor(g2, allBlack, appearance.getPossibleCellColor());
							candidateBackground = appearance.getPossibleCellColor();
						} else {
							Color countColor = candidateCountFilterColor(cellIndex, appearance);
							setColor(g2, allBlack, countColor);
							candidateBackground = countColor;
						}
								
					g2.fillRect(
									(int) Math.round(startX + shiftX + third / 2.0 - ddy / 2.0),
									(int) Math.round(startY + shiftY + third / 2.0 - ddy / 2.0),
									(int) Math.round(ddy), (int) Math.round(ddy)
								);
					}

							// Coloring
							Color coloringColor = null;
							if (isColoringVisible && coloringCandidateMap.containsKey(cellIndex * 10 + i)) {
								coloringColor = annotationColor("candidate:"+(cellIndex*10+i),coloringCandidateMap.get(cellIndex * 10 + i),cellBackground);
							}

							if (coloringColor != null) {
								setColor(g2, allBlack, coloringColor);
								candidateBackground = coloringColor;
								g2.fillRect(
									(int) Math.round(startX + shiftX + third / 2.0 - ddy / 2.0),
									(int) Math.round(startY + shiftY + third / 2.0 - ddy / 2.0),
									(int) Math.round(ddy), (int) Math.round(ddy)
								);
							}
							
							if (hintColor != null) {
								setColor(g2, allBlack, hintColor);
								candidateBackground = hintColor;
								int hintX = (int) Math.round(startX + shiftX + third / 2.0 - ddy / 2.0);
								int hintY = (int) Math.round(startY + shiftY + third / 2.0 - ddy / 2.0);
								int hintSize = (int) Math.round(ddy);
                                // Keep the native hint glyph/background above manual overlays.
                                generatedCandidateForeground.add(new java.awt.geom.Area(
                                    new java.awt.Rectangle(hintX-2,hintY-2,hintSize+4,hintSize+4)));
								if (appearance.isDark()) {
									int arc = Math.max(2, hintSize / 3);
									g2.fillRoundRect(hintX, hintY, hintSize, hintSize, arc, arc);
								} else {
									g2.fillOval(hintX, hintY, hintSize, hintSize);
								}
							}
							
							if (showOverlapCandidateMarker && hintColor == null && offColor == null) {
								// Cross-group overlap remains visible even if explicit candidate
								// coloring owns the candidate background.
								g2.setFont(candidateFont.deriveFont(Font.BOLD));
								candColor = appearance.getReadableForeground(candidateBackground,
										new Color(31, 34, 38));
							} else {
								g2.setFont(candidateFont);
							}
							if (!Color.WHITE.equals(candColor)) {
								candColor = appearance.getReadableForeground(candidateBackground, candColor);
							}
							setColor(g2, allBlack, candColor);
							if (!Options.getInstance().isShowColorKuAct()) {
								g2.drawString(
									Integer.toString(i), 
									(int) Math.round(startX + dcx + shiftX),
									(int) Math.round(startY + dcy + shiftY)
								);
							} else {
								if (offColor != null) {
									int ccx = (int) Math.round(startX + shiftX + third / 2.0 - candidateHeight / 2.0);
									int ccy = (int) Math.round(startY + shiftY + third / 2.0 - candidateHeight / 2.0);
									drawColorBox(offCand, g2, ccx, ccy, candidateHeight, false);
								}
							}

						}
					}
				}
			}
		}

		switch (Options.getInstance().getDrawMode()) {
		case 0:
			
			if (allBlack) {
				g2.setStroke(new BasicStroke(strokeWidth / 2));
			} else {
				g2.setStroke(new BasicStroke(strokeWidth));
			}
			
			setColor(g2, allBlack, appearance.getInnerGridColor());
			drawBlockLine(delta + gridRegion.x, 1 * delta + gridRegion.y, true);
			drawBlockLine(delta + gridRegion.x, 2 * delta + gridRegion.y + 3 * cellSize, true);
			drawBlockLine(delta + gridRegion.x, 3 * delta + gridRegion.y + 6 * cellSize, true);
			setColor(g2, allBlack, appearance.getGridColor());
			g2.setStroke(new BasicStroke(boxStrokeWidth));
			g2.drawRect(gridRegion.x, gridRegion.y, gridRegion.width, gridRegion.height);
			
			for (int i = 0; i < 3; i++) {
				
				g2.drawRect(
					(i + 1) * delta + gridRegion.x + i * 3 * cellSize,
					1 * delta + gridRegion.y,
					3 * cellSize,
					3 * cellSize
				);
				
				g2.drawRect(
					(i + 1) * delta + gridRegion.x + i * 3 * cellSize,
					2 * delta + gridRegion.y + 3 * cellSize,
					3 * cellSize,
					3 * cellSize
				);
				
				g2.drawRect(
					(i + 1) * delta + gridRegion.x + i * 3 * cellSize,
					3 * delta + gridRegion.y + 6 * cellSize,
					3 * cellSize,
					3 * cellSize
				);
			}
			
			break;
			
		case 1:
			
			if (allBlack) {
				g2.setStroke(new BasicStroke(strokeWidth / 2));
			} else {
				g2.setStroke(new BasicStroke(strokeWidth));
			}
			
			setColor(g2, allBlack, appearance.getInnerGridColor());
			drawBlockLine(delta + gridRegion.x, 1 * delta + gridRegion.y, false);
			drawBlockLine(delta + gridRegion.x, 2 * delta + gridRegion.y + 3 * cellSize, false);
			drawBlockLine(delta + gridRegion.x, 3 * delta + gridRegion.y + 6 * cellSize, false);
			setColor(g2, allBlack, appearance.getGridColor());
			g2.setStroke(new BasicStroke(boxStrokeWidth));
			g2.drawRect(gridRegion.x, gridRegion.y, gridRegion.width, gridRegion.height);
			
			for (int i = 0; i < 3; i++) {
				g2.drawLine(gridRegion.x, gridRegion.y + i * 3 * cellSize, gridRegion.x + Sudoku2.UNITS * cellSize, gridRegion.y + i * 3 * cellSize);
				g2.drawLine(gridRegion.x + i * 3 * cellSize, gridRegion.y, gridRegion.x + i * 3 * cellSize, gridRegion.y + Sudoku2.UNITS * cellSize);
			}
			
			break;
		}

		if (includeAnnotations) {
            java.awt.Shape annotationClip=g2.getClip();
            if(renderGeneratedStep && !generatedCandidateForeground.isEmpty()) {
                java.awt.geom.Area behind=new java.awt.geom.Area(annotationClip==null
                    ? new java.awt.Rectangle(0,0,totalWidth,totalHeight):annotationClip);
                behind.subtract(generatedCandidateForeground);g2.setClip(behind);
            }
			if (!isPrint) {
				drawBoxReasoningDragRubberBand(g2);
			}
			drawDoodles(g2, totalWidth, totalHeight, isPrint);
            if (!isPrint) drawDeletionGesture(g2);
			if (!isPrint) lastUserChainRouteDiameter=ddy>0?ddy:Math.max(1.0,candidateHeight);
            drawUserChains(g2, appearance, ddy);
            g2.setClip(annotationClip);
		}

		if (renderGeneratedStep && !step.getChains().isEmpty()) {
			
			points.clear();

			for (int ci = 0; ci < step.getChainAnz(); ci++) {
				
				if (step.getType().isKrakenFish() && chainIndex == -1) {
					continue;
				}
				
				if (chainIndex != -1 && chainIndex != ci) {
					continue;
				}
				
				Chain chain = step.getChains().get(ci);
				for (int i = chain.getStart(); i <= chain.getEnd(); i++) {
					int che = Math.abs(chain.getChain()[i]);
					points.add(getCandKoord(Chain.getSCellIndex(che), Chain.getSCandidate(che), cellSize));
					if (Chain.getSNodeType(che) == Chain.GROUP_NODE) {
						int indexC = Chain.getSCellIndex2(che);
						if (indexC != -1) {
							points.add(getCandKoord(indexC, Chain.getSCandidate(che), cellSize));
						}
						indexC = Chain.getSCellIndex3(che);
						if (indexC != -1) {
							points.add(getCandKoord(indexC, Chain.getSCandidate(che), cellSize));
						}
					}
				}
			}
			
			for (Candidate cand : step.getCandidatesToDelete()) {
				points.add(getCandKoord(cand.getIndex(), cand.getValue(), cellSize));
			}

			for (int ai = 0; ai < step.getAlses().size(); ai++) {
				
				if (step.getType().isKrakenFish() && chainIndex == -1) {
					continue;
				}
				
				if (chainIndex != -1 && !alsToShow.contains(ai)) {
					continue;
				}
				
				AlsInSolutionStep als = step.getAlses().get(ai);
				for (int i = 0; i < als.getIndices().size(); i++) {
					int index = als.getIndices().get(i);
					int[] cands = sudoku.getAllCandidates(index);
					for (int j = 0; j < cands.length; j++) {
						points.add(getCandKoord(index, cands[j], cellSize));
					}
				}
			}

			for (int ci = 0; ci < step.getChainAnz(); ci++) {
				
				if (step.getType().isKrakenFish() && chainIndex == -1) {
					continue;
				}
				
				if (chainIndex != -1 && ci != chainIndex) {
					continue;
				}
				
				Chain chain = step.getChains().get(ci);
				drawChain(g2, chain, cellSize, ddy, allBlack, appearance);
			}
		}

        if(includeAnnotations && renderGeneratedStep) {
            annotationPaintLayer=2;
            drawLaterCandidateColors(g2,appearance,ddy);
            for(int c=0;c<81;c++)drawBoxReasoningCellUnderlay(g2,c,getX(c/9,c%9),getY(c/9,c%9),appearance,appearance.getDefaultCellColor());
            drawUserChains(g2,appearance,ddy);
            drawDoodles(g2,totalWidth,totalHeight,isPrint);
            if(!isPrint){drawBoxReasoningDragRubberBand(g2);drawDeletionGesture(g2);}
            annotationPaintLayer=0;
        }
		if (!isPrint) {
			drawUnitHandles(appearance);
		}
	}

	private void drawTransientReferenceFrame(int cellIndex, int x, int y) {
		int frame = Math.max(1, Math.min(3, cellSize / 30));
		if (transientReferenceHighlight.getKind() == SudokuTextReference.Kind.CELLS) {
			drawCellFrame(x, y, cellSize, frame);
			return;
		}
		int row = Sudoku2.getRow(cellIndex);
		int col = Sudoku2.getCol(cellIndex);
		if (row == 0 || !transientReferenceHighlight.containsCell(cellIndex - 9)) {
			g2.fillRect(x, y, cellSize, frame);
		}
		if (row == 8 || !transientReferenceHighlight.containsCell(cellIndex + 9)) {
			g2.fillRect(x, y + cellSize - frame, cellSize, frame);
		}
		if (col == 0 || !transientReferenceHighlight.containsCell(cellIndex - 1)) {
			g2.fillRect(x, y, frame, cellSize);
		}
		if (col == 8 || !transientReferenceHighlight.containsCell(cellIndex + 1)) {
			g2.fillRect(x + cellSize - frame, y, frame, cellSize);
		}
	}

	private void drawUnitHandles(SudokuAppearancePalette appearance) {
		int fontSize = Math.max(9, Math.min(12, cellSize / 4));
		Font handleFont = getFont().deriveFont(Font.PLAIN, (float) fontSize);
		g2.setFont(handleFont);
		FontMetrics metrics = g2.getFontMetrics();
		for (int unit = 0; unit < Sudoku2.UNITS; unit++) {
			String columnLabel = "C" + Integer.toString(unit + 1);
			String rowLabel = "R" + Integer.toString(unit + 1);
			boolean columnHover = hoveredUnitHandleKind == SudokuTextReference.Kind.COLUMNS
					&& hoveredUnitHandle == unit;
			g2.setColor(appearance.getUnitHandleColor(columnHover));
			int columnCenter = getX(0, unit) + cellSize / 2;
			int topBaseline = gridRegion.y - (UNIT_HANDLE_SIZE - metrics.getAscent()) / 2 - metrics.getDescent();
			g2.drawString(columnLabel, columnCenter - metrics.stringWidth(columnLabel) / 2, topBaseline);

			boolean rowHover = hoveredUnitHandleKind == SudokuTextReference.Kind.ROWS
					&& hoveredUnitHandle == unit;
			g2.setColor(appearance.getUnitHandleColor(rowHover));
			int rowCenter = getY(unit, 0) + cellSize / 2;
			int leftX = gridRegion.x - (UNIT_HANDLE_SIZE + metrics.stringWidth(rowLabel)) / 2;
			int leftBaseline = rowCenter + (metrics.getAscent() - metrics.getDescent()) / 2;
			g2.drawString(rowLabel, leftX, leftBaseline);
		}
	}

	private int getCellFrameSize(int row, int col) {
		int frameSize = (int) (cellSize * Options.getInstance().getCursorFrameSize());
		if (row != getActiveRow() || col != getActiveCol()) {
			frameSize = frameSize / 2 + 1;
		}
		return Math.max(1, frameSize);
	}

	private Color candidateCountFilterColor(int cellIndex, SudokuAppearancePalette palette) {
		int count = sudoku.getAllCandidates(cellIndex, !showCandidates).length;
		if (count == 2 && showBivalueCells) {
			return palette.getBivalueFilterColor();
		}
		return palette.getTrivalueFilterColor();
	}

	/** Draws only within the existing cell-frame band, leaving candidate positions intact. */
	private void drawCellFrame(int x, int y, int size, int frameSize) {
		((Graphics) g2).fillRect(x, y, size, frameSize + 1);
		((Graphics) g2).fillRect(x, y, frameSize + 1, size);
		((Graphics) g2).fillRect(x + size - frameSize, y, frameSize, size);
		((Graphics) g2).fillRect(x, y + size - frameSize, size, frameSize);
	}

	private void drawSelectionFrame(int x, int y, int size, int frameSize,
			boolean allBlack, SudokuAppearancePalette appearance) {
		setColor(g2, allBlack, appearance.getActiveCellColor());
		drawCellFrame(x, y, size, frameSize);
		if (!allBlack && !appearance.getActiveCellBorderColor()
				.equals(appearance.getActiveCellColor())) {
			setColor(g2, false, appearance.getActiveCellBorderColor());
			// The outermost pixel is later occupied by grid lines, so keep the
			// contrast edge one pixel inside the cell where it remains visible.
			drawCellFrame(x + 1, y + 1, size - 2, 1);
		}
	}

	/**
	 * Convenience method to make printing in all black easier.
	 *
	 * @param g2
	 * @param color
	 * @param allBlack
	 */
	private void setColor(Graphics2D g2, boolean allBlack, Color color) {
		if (allBlack) {
			g2.setColor(Color.BLACK);
		} else {
			g2.setColor(color);
		}
	}

	/**
	 * Draws a chain.
	 * <ul>
	 * <li>Calculate the end points of each link</li>
	 * <li>Check, if another node is on the direct line between the end points</li>
	 * <li>If so, draw a Bezier curve instead of a line (tangents are 45 degrees of
	 * the direct line)</li>
	 * <li>If the length is very small, the link is ommitted</li>
	 * </ul>
	 *
	 * @param g2
	 * @param chain
	 * @param cellSize
	 * @param ddy
	 * @param allBlack
	 */
	private void drawChain(Graphics2D g2, Chain chain, int cellSize, double ddy, boolean allBlack,
			SudokuAppearancePalette appearance) {
		// Calculate the coordinates of the startpoint for every link
		int[] ch = chain.getChain();
		List<Point2D.Double> points1 = new ArrayList<Point2D.Double>(chain.getEnd() + 1);
		for (int i = 0; i <= chain.getEnd(); i++) {
			if (i < chain.getStart()) {
				// belongs to some other chain-> ignore!
				points1.add(null);
				continue;
			}
			int che = Math.abs(ch[i]);
			points1.add(getCandKoord(Chain.getSCellIndex(che), Chain.getSCandidate(che), cellSize));
		}
		
		Stroke oldStroke = g2.getStroke();
		int oldChe = 0;
		int oldIndex = 0;
		int index = 0;
		for (int i = chain.getStart(); i < chain.getEnd(); i++) {
			// link is only drawn between different cells
			if (ch[i + 1] == Integer.MIN_VALUE) {
				// end point of a net branch -> ignore
				continue;
			}
			
			index = i;
			int che = Math.abs(ch[i]);
			int che1 = Math.abs(ch[i + 1]);
			if (ch[i] > 0 && ch[i + 1] < 0) {
				oldChe = che;
				oldIndex = i;
			}
			
			if (ch[i] == Integer.MIN_VALUE && ch[i + 1] < 0) {
				che = oldChe;
				index = oldIndex;
			}
			
			if (ch[i] < 0 && ch[i + 1] > 0) {
				che = oldChe;
				index = oldIndex;
			}
			
			if (Chain.getSCellIndex(che) == Chain.getSCellIndex(che1)) {
				// same cell -> ignore
				continue;
			}
			
			setColor(g2, allBlack, appearance.getArrowColor());
			if (Chain.isSStrong(che1)) {
				g2.setStroke(strongLinkStroke);
			} else {
				g2.setStroke(weakLinkStroke);
			}
			
			drawArrow(g2, index, i + 1, cellSize, ddy, points1);
		}
		
		g2.setStroke(oldStroke);

	}

	private void drawArrow(Graphics2D graphics, int index1, int index2, int cellSize, double ddy,
			List<Point2D.Double> chainPoints) {
		ChainRouteGeometry.Route route = ChainRouteGeometry.createLaneZero(
				chainPoints.get(index1), chainPoints.get(index2), ddy, points);
		drawChainRoute(graphics, route, cellSize, ddy);
	}

	private void drawChainRoute(Graphics2D graphics, ChainRouteGeometry.Route route,
			int cellSize, double candidateDiameter) {
        drawChainRoute(graphics,route,cellSize,candidateDiameter,true);
    }
    private void drawChainRoute(Graphics2D graphics, ChainRouteGeometry.Route route,
            int cellSize,double candidateDiameter,boolean arrowVisible) {
		Point2D.Double start = route.getStart();
		Point2D.Double end = route.getEnd();
		if (route.isCurved()) {
			Point2D.Double control1 = route.getControl1();
			Point2D.Double control2 = route.getControl2();
			graphics.draw(new CubicCurve2D.Double(start.x, start.y, control1.x, control1.y,
					control2.x, control2.y, end.x, end.y));
		} else {
			graphics.drawLine((int) Math.round(start.x), (int) Math.round(start.y),
					(int) Math.round(end.x), (int) Math.round(end.y));
		}

        if(!arrowVisible)return;
		double arrowLength = cellSize * arrowLengthFactor;
		if (route.getRawLength() <= arrowLength * 2.0 + candidateDiameter) {
			return;
		}
		Point2D.Double arrowBase = route.getArrowBase(arrowLength);
		double angle = Math.atan2(end.y - arrowBase.y, end.x - arrowBase.x);
		double arrowHeight = arrowLength * arrowHeightFactor;
		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		double deltaX = sin * arrowHeight;
		double deltaY = cos * arrowHeight;
		graphics.setStroke(arrowStroke);
		arrow.reset();
		arrow.addPoint((int) Math.round(arrowBase.x - deltaX),
				(int) Math.round(arrowBase.y + deltaY));
		arrow.addPoint((int) Math.round(end.x), (int) Math.round(end.y));
		arrow.addPoint((int) Math.round(arrowBase.x + deltaX),
				(int) Math.round(arrowBase.y - deltaY));
		graphics.fill(arrow);
		graphics.draw(arrow);
	}

	/**
	 * Returns the center of the position of a candidate in the grid.
	 *
	 * @param index
	 * @param cand
	 * @param cellSize
	 * @return
	 */
	private Point2D.Double getCandKoord(int index, int cand, int cellSize) {
		double third = cellSize / 3;
		double startX = getX(Sudoku2.getRow(index), Sudoku2.getCol(index));
		double startY = getY(Sudoku2.getRow(index), Sudoku2.getCol(index));
		double shiftX = ((cand - 1) % 3) * third;
		double shiftY = ((cand - 1) / 3) * third;
		double x = startX + shiftX + third / 2.0;
		double y = startY + shiftY + third / 2.0;
		return new Point2D.Double(x, y);
	}

	public int getX(int row, int col) {
		
		int x = col * cellSize + delta + gridRegion.x;
		if (col > 2) {
			x += delta;
		}
		
		if (col > 5) {
			x += delta;
		}
		
		return x;
	}

	public int getY(int row, int col) {
		
		int y = row * cellSize + delta + gridRegion.y;
		if (row > 2) {
			y += delta;
		}
		
		if (row > 5) {
			y += delta;
		}
		
		return y;
	}

	private int getRow(Point p) {
		
		double tmp = p.y - gridRegion.y - delta;
		if ((tmp >= 3 * cellSize && tmp <= 3 * cellSize + delta)
				|| (tmp >= 6 * cellSize + delta && tmp <= 6 * cellSize + 2 * delta)) {
			return -1;
		}
		
		if (tmp > 3 * cellSize) {
			tmp -= delta;
		}
		
		if (tmp > 6 * cellSize) {
			tmp -= delta;
		}
		
		return (int) Math.ceil((tmp / cellSize) - 1);
	}

	private int getCol(Point p) {
		
		double tmp = p.x - gridRegion.x - delta;
		
		if ((tmp >= 3 * cellSize && tmp <= 3 * cellSize + delta)
				|| (tmp >= 6 * cellSize + delta && tmp <= 6 * cellSize + 2 * delta)) {
			return -1;
		}
		
		if (tmp > 3 * cellSize) {
			tmp -= delta;
		}
		
		if (tmp > 6 * cellSize) {
			tmp -= delta;
		}
		
		return (int) Math.ceil((tmp / cellSize) - 1);
	}

	/**
	 * Checks whether a candidate has been clicked. The correct values for font
	 * metrics and candidate factors are ignored: the valid candidate region is
	 * simple the corresponding ninth of the cell.<br>
	 * <br>
	 *
	 * @param p    The point of a mouse click
	 * @param row The row, in which p lies (may be -1 for "invalid")
	 * @param col  The column, in which p lies (may be -1 for "invalid")
	 * @return The number of a candidate, if a click could be confirmed, or else -1
	 */
	public int getCandidate(Point p, int row, int col) {
		
		// check if a cell was clicked
		if (row < 0 || col < 0) {
			// clicked between cells -> cant mean a candidate
			return -1;
		}
		
		// calculate the coordinates of the left upper corner of the cell
		double startX = gridRegion.x + col * cellSize;
		if (col > 2) {
			startX += delta;
		}
		
		if (col > 5) {
			startX += delta;
		}
		
		double startY = gridRegion.y + row * cellSize;
		if (row > 2) {
			startY += delta;
		}
		
		if (row > 5) {
			startY += delta;
		}
		
		// now check if a candidate was clicked
		int candidate = -1;
		double cs3 = cellSize / 3.0;		
		double dx = cs3;
		double leftDx = 0;

		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				
				double sx = startX + i * cs3 + leftDx;
				double sy = startY + j * cs3 + leftDx;

				// candidate was clicked
				if (p.x >= sx && p.x <= sx + dx && p.y >= sy && p.y <= sy + dx) {
					candidate = j * 3 + i + 1;
					return candidate;
				}
			}
		}
		
		return -1;
	}

	public void setActiveColor(Color color) {
        if (color != null) cellZoomPanel.setPrimaryColor(color);
        updateColorCursor();

		// no region selects are allowed in coloring
		clearRegion();
		updateCellZoomPanel();
	}

	public void resetActiveColor() {
		Color temp = getActiveColor();
		setActiveColor(null);
		setActiveColor(temp);
	}

	private void drawBlockLine(int x, int y, boolean withRect) {
		drawBlock(x, y, withRect);
		drawBlock(x + 3 * cellSize + delta, y, withRect);
		drawBlock(x + 6 * cellSize + 2 * delta, y, withRect);
	}

	private void drawBlock(int x, int y, boolean withRect) {
		
		if (withRect) {
			g2.drawRect(x, y, 3 * cellSize, 3 * cellSize);
		}
		
		g2.drawLine(x, y + 1 * cellSize, x + 3 * cellSize, y + 1 * cellSize);
		g2.drawLine(x, y + 2 * cellSize, x + 3 * cellSize, y + 2 * cellSize);
		g2.drawLine(x + 1 * cellSize, y, x + 1 * cellSize, y + 3 * cellSize);
		g2.drawLine(x + 2 * cellSize, y, x + 2 * cellSize, y + 3 * cellSize);
	}

	public Sudoku2 getSudoku() {
		return sudoku;
	}

	public boolean isShowCandidates() {
		return showCandidates;
	}

	public final void setShowCandidates(boolean showCandidates) {
		this.showCandidates = showCandidates;
		repaint();
	}

	public boolean isShowWrongValues() {
		return showWrongValues;
	}

	public void setShowWrongValues(boolean showWrongValues) {
		this.showWrongValues = showWrongValues;
		repaint();
	}

	public boolean undoPossible() {
		return undoStack.size() > 0;
	}

	public boolean redoPossible() {
		return redoStack.size() > 0;
	}

	public void undo() {
		if (undoPossible()) {
			redoStack.push(sudoku);
			Sudoku2 previous = undoStack.pop();
			ColoringSnapshot coloringState = undoColoringStates.remove(previous);
			if (coloringState != null) {
				redoColoringStates.put(sudoku, coloringState);
				sudoku = previous;
				coloringMap = new TreeMap<Integer, Color>(coloringState.cells);
				coloringCandidateMap = new TreeMap<Integer, Color>(coloringState.candidates);
			} else {
				redoColoringStates.remove(sudoku);
				sudoku = previous;
			}
			updateCellZoomPanel();
			checkProgress();
			mainFrame.setCurrentLevel(sudoku.getLevel());
			mainFrame.setCurrentScore(sudoku.getScore());
			mainFrame.check();
			mainFrame.sudokuStateChanged();
			repaint();
		}
	}

	public void redo() {
		if (redoPossible()) {
			undoStack.push(sudoku);
			Sudoku2 next = redoStack.pop();
			ColoringSnapshot coloringState = redoColoringStates.remove(next);
			if (coloringState != null) {
				undoColoringStates.put(sudoku, coloringState);
				sudoku = next;
				coloringMap.clear();
				coloringCandidateMap.clear();
			} else {
				undoColoringStates.remove(sudoku);
				sudoku = next;
			}
			updateCellZoomPanel();
			checkProgress();
			mainFrame.setCurrentLevel(sudoku.getLevel());
			mainFrame.setCurrentScore(sudoku.getScore());
			mainFrame.check();
			mainFrame.sudokuStateChanged();
			repaint();
		}
	}

	/**
	 * Clears undo/redo. Is called from {@link SolutionPanel} when a step is double
	 * clicked.
	 */
	public void clearUndoRedo() {
		undoStack.clear();
		redoStack.clear();
		undoColoringStates.clear();
		redoColoringStates.clear();
	}

	public void setSudoku(Sudoku2 newSudoku) {
		setSudoku(newSudoku.getSudoku(ClipboardMode.PM_GRID, null), false);
	}

	public void setSudoku(Sudoku2 newSudoku, boolean alreadySolved) {
		setSudoku(newSudoku.getSudoku(ClipboardMode.PM_GRID, null), alreadySolved);
	}

	public void setSudoku(String init) {
		setSudoku(init, false);
	}

	public void setSudoku(String init, boolean alreadySolved) {

		mainFrame.resetSelectedHintTechnique();
		generatedStepOwnerWillChange();
		step = null;
		setChainInStep(-1);
		undoStack.clear();
		redoStack.clear();
		coloringMap.clear();
		coloringCandidateMap.clear();
		clearAnnotationsForNewPuzzle();
		resetShowHintCellValues();
		showBivalueCells = false;
		showTrivalueCells = false;
		
		clearDragSelection();
		cellSelection.clear();
		cellSelection.add(Integer.valueOf(Sudoku2.getIndex(4, 4)));
		emptySelectionAnchor = Sudoku2.getIndex(4, 4);
		isCtrlDown = false;
		lastPressedRow = -1;
		lastPressedCol = -1;
		lastPressedCandidate = -1;
		lastClickedRow = -1;
		lastClickedCol = -1;
		lastClickedCandidate = -1;
		lastClickedTime = -1;
		shiftRow = -1;
		shiftCol = -1;
		lastHighlightedDigit = 0;
		isColoringVisible = true;

		if (init == null || init.length() == 0) {
			sudoku = new Sudoku2();
		} else {
			sudoku.setSudoku(init);
			// the sudoku must be set in the solver to reset the step list
			// (otherwise the result panels are not updated correctly)
			sudoku.setLevel(Options.getInstance().getDifficultyLevels()[DifficultyType.EASY.ordinal()]);
			sudoku.setScore(0);
			Sudoku2 tmpSudoku = sudoku.clone();
			if (!alreadySolved) {
				getSolver().setSudoku(tmpSudoku);
			}
			// boolean unique = generator.validSolution(sudoku);
			int anzSolutions = generator.getNumberOfSolutions(sudoku, 1);
			if (anzSolutions == 0) {
				JOptionPane.showMessageDialog(this,
						java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.no_solution"),
						java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.invalid_puzzle"),
						JOptionPane.ERROR_MESSAGE);
				sudoku.setStatus(SudokuStatus.INVALID);
			} else if (anzSolutions > 1) {
				JOptionPane.showMessageDialog(this,
						java.util.ResourceBundle.getBundle("intl/SudokuPanel")
								.getString("SudokuPanel.multiple_solutions"),
						java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.invalid_puzzle"),
						JOptionPane.ERROR_MESSAGE);
				sudoku.setStatus(SudokuStatus.MULTIPLE_SOLUTIONS);
			} else {
				if (!sudoku.checkSudoku()) {
					JOptionPane.showMessageDialog(this,
							java.util.ResourceBundle.getBundle("intl/SudokuPanel")
									.getString("SudokuPanel.wrong_values"),
							java.util.ResourceBundle.getBundle("intl/SudokuPanel")
									.getString("SudokuPanel.invalid_puzzle"),
							JOptionPane.ERROR_MESSAGE);
					sudoku.setStatus(SudokuStatus.INVALID);
				} else {
					sudoku.setStatus(SudokuStatus.VALID);
					if (sudoku.getFixedCellsAnz() > 17) {
						Sudoku2 fixedOnly = new Sudoku2();
						fixedOnly.setSudoku(sudoku.getSudoku(ClipboardMode.CLUES_ONLY));
						int anzFixedSol = generator.getNumberOfSolutions(fixedOnly, 1);
						sudoku.setStatusGivens(anzFixedSol);
					}

					if (!alreadySolved) {
						tmpSudoku.setStatus(SudokuStatus.VALID);
						tmpSudoku.setStatusGivens(sudoku.getStatusGivens());
						tmpSudoku.setSolution(sudoku.getSolution());
						getSolver().solve(true);
					}

					sudoku.setLevel(getSolver().getSudoku().getLevel());
					sudoku.setScore(getSolver().getSudoku().getScore());
				}
			}
		}

		updateCellZoomPanel();
		
		if (mainFrame != null) {
			mainFrame.setCurrentLevel(sudoku.getLevel());
			mainFrame.setCurrentScore(sudoku.getScore());
			mainFrame.check();
		}

		repaint();
	}

	private void clearAnnotationsForNewPuzzle() {
		if (annotationToolPointerCaptured || boxDragStart != null || activeDoodleStroke != null) {
			suppressNextAnnotationPointerRelease = true;
		}
		cancelReasoningState(false, null);
		coloringUndoStack.clear();
		coloringRedoStack.clear();
		doodleStrokes.clear();
		doodleUndoStack.clear();
		doodleRedoStack.clear();
		cancelDoodleGesture();
		userChains.clear();
		userChainUndoStack.clear();
		userChainRedoStack.clear();
		activeUserChain = null;
		nextUserChainStrong = true;
		confirmedUserChainSourceId = 0L;
		nextUserChainSourceId = 1L;
		userChainReasoningRevision++;
		for (SudokuSet group : boxReasoningGroups) group.clear();
		boxReasoningUndoStack.clear();
		boxReasoningRedoStack.clear();
		clearBoxReasoningDragState();
		boxReasoningRevision++;
		reasoningBoardRevision++;
		lastNoMatchIdentity = null;
		annotationTool = AnnotationTool.DEFAULT_MOUSE;
		stickyAnnotationTool = AnnotationTool.DEFAULT_MOUSE;
		settledAnnotationToolOrigins.clear();
		clearActiveAnnotationToolKeyGesture();
		clearPendingAnnotationToolTap();
		pendingAnnotationToolRestore = null;
		annotationToolPointerCaptured = false;
		if (cellZoomPanel != null) {
			cellZoomPanel.selectAnnotationTool(annotationTool);
			cellZoomPanel.updateChainRelationSelection(nextUserChainStrong);
		}
        if (mainFrame != null) mainFrame.chainRelationUiChanged();
	}

	/** Discards transient user annotations when a different puzzle file is opened. */
	public void discardAnnotations() {
		clearAnnotationsForNewPuzzle();
		repaint();
	}

	public String getSudokuString(ClipboardMode mode) {
		return sudoku.getSudoku(mode, step);
	}

	public SudokuSolver getSolver() {
		return solver;
	}

	/**
	 * Solves the sudoku to a certain point: if game mode is playing, the sudoku is
	 * solved until the first non progress step is reached; in all other modes the
	 * solving stops, when the first training step has been reached.
	 */
	public void solveUpTo() {
		generatedStepOwnerWillChange();
		SolutionStep actStep = null;
		boolean changed = false;
		undoStack.push(sudoku.clone());
		GameMode gm = Options.getInstance().getGameMode();
		
		while ((actStep = solver.getHint(sudoku, false)) != null) {
			
			if (actStep.isGiveUp()) {
				// should display a message saying it failed, but I don;t know where the log UI is located.
				break;
			} else if (gm == GameMode.PLAYING) {
				if (!actStep.getType().getStepConfig().isEnabledProgress()) {
					// solving stops
					break;
				}
			} else {
				if (actStep.getType().getStepConfig().isEnabledTraining()) {
					// solving stops
					break;
				}
			}
			
			// still here? do the step
			getSolver().doStep(sudoku, actStep);
			changed = true;
		}
			
		/*
		if (actStep.isGiveUp()) {
			JOptionPane.showMessageDialog(
				this,
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.dont_know"),
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.error"),
				JOptionPane.ERROR_MESSAGE
			);
			break;
		}*/
		
		if (changed) {
			redoStack.clear();
		} else {
			undoStack.pop();
		}
		
		step = null;
		setChainInStep(-1);
		updateCellZoomPanel();
		checkProgress();
		mainFrame.check();
		mainFrame.sudokuStateChanged();
		repaint();
	}

	/**
	 * When solving manually, the sudoku can be in an invalid state. This should be
	 * handled before calling this method.
	 *
	 * @param singlesOnly
	 * @return
	 */
	public SolutionStep getNextStep(boolean singlesOnly) {
		return findNextStep(singlesOnly, true);
	}

	private SolutionStep findNextStep(boolean singlesOnly, boolean previewStep) {
		generatedStepOwnerWillChange();
		step = solver.getHint(sudoku, singlesOnly);
		setChainInStep(-1, previewStep);
		return step;
	}

	/** Applies every currently exposed Single and stops if one cannot change the board. */
	boolean setAllSingles() {
		sudoku.rebuildInternalData();
		boolean sudokuChanged = false;
		boolean stoppedWithoutProgress = false;
		try {
			SolutionStep nextSingle;
			while ((nextSingle = findNextStep(true, false)) != null
					&& nextSingle.getType().isSingle()) {
				if (!doStepForSinglesBatch()) {
					stoppedWithoutProgress = true;
					break;
				}
				sudokuChanged = true;
			}
			return sudokuChanged;
		} finally {
			finishSinglesBatch(sudokuChanged);
			if (stoppedWithoutProgress) {
				mainFrame.announceStatus("MainFrame.singles.noProgress");
			}
		}
	}

	/** Finds the next instance of one explicitly selected technique. */
	public SolutionStep getNextStep(SolutionType type) {
		if (type == null) {
			return getNextStep(false);
		}
		generatedStepOwnerWillChange();
		SudokuSolver isolatedSolver = SudokuSolverFactory.getInstance();
		try {
			isolatedSolver.setSudoku(sudoku.clone());
			step = isolatedSolver.getStepFinder().getStep(type);
		} finally {
			SudokuSolverFactory.giveBack(isolatedSolver);
		}
		setChainInStep(-1);
		repaint();
		return step;
	}

	/** Displays the exact cached instance selected in the technique chooser. */
	public SolutionStep getNextStep(SolutionStep selectedStep) {
		generatedStepOwnerWillChange();
		step = selectedStep == null ? null : (SolutionStep) selectedStep.clone();
		setChainInStep(-1);
		repaint();
		return step;
	}

	public void setStep(SolutionStep step) {
		generatedStepOwnerWillChange();
		this.step = step;
		setChainInStep(-1);
		repaint();
	}

	public SolutionStep getStep() {
		return step;
	}

	public void setTransientReferenceHighlight(SudokuTextReference reference) {
		transientReferenceHighlight = reference;
		repaint();
	}

	public void clearTransientReferenceHighlight() {
		if (transientReferenceHighlight != null) {
			transientReferenceHighlight = null;
			repaint();
		}
	}

	public boolean hasTransientReferenceHighlight() {
		return transientReferenceHighlight != null;
	}

	void setTechniquePreviewCells(Set<Integer> cells) {
		techniquePreviewCells.clear();
		if (cells != null) techniquePreviewCells.addAll(cells);
		repaint();
	}

	boolean hasTechniquePreviewCells() {
		return !techniquePreviewCells.isEmpty();
	}

	boolean colorTechniquePreviewCells(Color color) {
		if (color == null || techniquePreviewCells.isEmpty()) return false;
		List<Integer> cells = new ArrayList<Integer>();
		for (Integer index : techniquePreviewCells) {
			if (index != null && index.intValue() >= 0 && index.intValue() < Sudoku2.LENGTH) {
				cells.add(index);
			}
		}
		if (cells.isEmpty()) return false;
		pushColoringUndo();
		for (Integer index : cells) {
			handleColoringInternal(Sudoku2.getRow(index.intValue()),
					Sudoku2.getCol(index.intValue()), -1, color);
		}
		updateCellZoomPanel();
		mainFrame.check();
		repaint();
		return true;
	}

	void clearTechniquePreviewCells() {
		if (!techniquePreviewCells.isEmpty()) {
			techniquePreviewCells.clear();
			repaint();
		}
	}

	Set<Integer> getSelectedCellsForTechniqueMatching() {
		return new HashSet<Integer>(cellSelection);
	}

	Set<Integer> getColoredCellsForTechniqueMatching() {
		return new HashSet<Integer>(coloringMap.keySet());
	}

	Set<Integer> getColoredCandidatesForTechniqueMatching() {
		return new HashSet<Integer>(coloringCandidateMap.keySet());
	}

	List<UserChain> getUserChainsForTechniqueMatching() {
		return copyUserChains(userChains);
	}

	public SudokuTextReference.Kind getTransientReferenceKind() {
		return transientReferenceHighlight == null ? null : transientReferenceHighlight.getKind();
	}

	public void setChainInStep(int chainIndex) {
		setChainInStep(chainIndex, true);
	}

	private void setChainInStep(int chainIndex, boolean repaintBoard) {
        refreshAnnotationTimeline();
		
		if (step == null) {
			chainIndex = -1;
		} else if (step.getType().isKrakenFish() && chainIndex > -1) {
			chainIndex--;
		}
		
		if (chainIndex >= 0 && chainIndex > step.getChainAnz() - 1) {
			chainIndex = -1;
		}
		
		this.chainIndex = chainIndex;
		alsToShow.clear();
		if (chainIndex != -1) {
			Chain chain = step.getChains().get(chainIndex);
			for (int i = chain.getStart(); i <= chain.getEnd(); i++) {
				if (chain.getNodeType(i) == Chain.ALS_NODE) {
					alsToShow.add(Chain.getSAlsIndex(chain.getChain()[i]));
				}
			}
		}

		if (repaintBoard) repaint();
	}

	public boolean doStep() {
		return executeDisplayedStep(true);
	}

	private boolean doStepForSinglesBatch() {
		return executeDisplayedStep(false);
	}

	private boolean executeDisplayedStep(boolean refreshUi) {
		
		if (step != null) {
			if (reasoningProposal != null && !executingReasoningStep) {
				mainFrame.setReasoningControls(true, true);
				mainFrame.announceReasoningStatus("MainFrame.reasoning.preview");
				return false;
			}
			generatedStepOwnerWillChange();
			Sudoku2 before = sudoku.clone();
			try {
				if(step.isAuthoredPlacement()) {
                    SolutionStep executable=(SolutionStep)step.clone();executable.setType(SolutionType.FORCING_CHAIN_VERITY);
                    getSolver().doStep(sudoku,executable);
                }else getSolver().doStep(sudoku, step);
			} catch (RuntimeException | Error ex) {
				restoreSudokuAfterFailedStep(before, ex, refreshUi);
				throw ex;
			}
			if (!hasLogicalBoardStateChanged(before, sudoku)) {
				restoreSudokuAfterFailedStep(before, null, refreshUi);
				return false;
			}
			undoStack.push(before);
			redoStack.clear();
			clearExecutedStep();
			if (refreshUi) {
				checkProgress();
				refreshStepExecutionUi(true);
			}
			return true;
		}
		return false;
	}

	/** PM_GRID renders an unset sole candidate like the same filled digit. */
	private static boolean hasLogicalBoardStateChanged(Sudoku2 before, Sudoku2 after) {
		return !Arrays.equals(before.getValues(), after.getValues())
				|| !Arrays.equals(before.getCells(), after.getCells())
				|| !Arrays.equals(before.getUserCells(), after.getUserCells())
				|| !Arrays.equals(before.getFixed(), after.getFixed());
	}

	private void finishSinglesBatch(boolean sudokuChanged) {
		clearExecutedStep();
		if (sudokuChanged) progressChecker.startCheck(sudoku, true);
		refreshStepExecutionUi(sudokuChanged);
	}

	private void clearExecutedStep() {
		step = null;
		setChainInStep(-1, false);
	}

	private void refreshStepExecutionUi(boolean sudokuChanged) {
		updateCellZoomPanel();
		mainFrame.check();
		repaint();
		if (sudokuChanged) mainFrame.sudokuStateChanged();
		if (sudokuChanged && sudoku.isSolved() && Options.getInstance().isShowSudokuSolved()) {
			JOptionPane.showMessageDialog(
				this,
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.sudoku_solved"),
				java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.congratulations"),
				JOptionPane.INFORMATION_MESSAGE
			);
		}
	}

	private void restoreSudokuAfterFailedStep(Sudoku2 before, Throwable cause, boolean refreshUi) {
		// Keep the shared Sudoku2 identity stable: AllStepsPanel and other views retain
		// this object reference between explicit puzzle replacements.
		sudoku.set(before);
		try {
			getSolver().setSudoku(sudoku);
		} catch (RuntimeException | Error resetFailure) {
			if (cause == null) throw resetFailure;
			cause.addSuppressed(resetFailure);
		}
		if (refreshUi) {
			updateCellZoomPanel();
			mainFrame.check();
			repaint();
		}
	}

	public void abortStep() {
		generatedStepOwnerWillChange();
		step = null;
		setChainInStep(-1);
		repaint();
	}

	public int getSolvedCellsAnz() {
		return sudoku.getSolvedCellsAnz();
	}

	public void setNoClues() {
		sudoku.setNoClues();
		repaint();
	}

	public boolean isInvalidCells() {
		return invalidCells;
	}

	public void setInvalidCells(boolean invalidCells) {
		this.invalidCells = invalidCells;
        invalidateSearchPreview();
	}

	public boolean isShowInvalidOrPossibleCells() {
		return showInvalidOrPossibleCells;
	}

	public void setShowInvalidOrPossibleCells(boolean showInvalidOrPossibleCells) {
		this.showInvalidOrPossibleCells = showInvalidOrPossibleCells;
	}

	public boolean[] getShowHintCellValues() {
		return showHintCellValues;
	}

	public void setShowHintCellValues(boolean[] showHintCellValues) {
		this.showHintCellValues = new boolean[Sudoku2.UNITS + 1];
		if (showHintCellValues != null) {
			for (int i = 1; i <= Sudoku2.UNITS && i < showHintCellValues.length; i++) {
				this.showHintCellValues[i] = showHintCellValues[i];
			}
		}
		checkIsShowInvalidOrPossibleCells();
	}

	public void setShowHintCellValue(int candidate) {
		if (candidate < 1 || candidate > Sudoku2.UNITS) {
			resetCandidateValueFilters();
			return;
		}
		boolean active = showHintCellValues[candidate];
		if (active) {
			resetCandidateValueFilters();
		} else {
			resetCandidateValueFilters();
			showHintCellValues[candidate] = true;
		}
		checkIsShowInvalidOrPossibleCells();
	}

	private void resetCandidateValueFilters() {
		for (int i = 1; i <= Sudoku2.UNITS; i++) {
			showHintCellValues[i] = false;
		}
	}

	public void toggleCandidateValueFilter(int candidate, boolean additive) {
		if (candidate < 1 || candidate > Sudoku2.UNITS) {
			return;
		}
		if (!additive) {
			boolean active = showHintCellValues[candidate];
			resetCandidateValueFilters();
			if (!active) {
				showHintCellValues[candidate] = true;
			}
		} else {
			showHintCellValues[candidate] = !showHintCellValues[candidate];
		}
		checkIsShowInvalidOrPossibleCells();
	}

	/**
	 * Applies a candidate value filter from a keyboard shortcut. A shortcut must
	 * not replace an existing filter with a value that cannot currently be
	 * displayed. This also keeps the shortcut useful when no cell is selected.
	 */
	private boolean toggleCandidateValueFilterFromShortcut(int candidate, boolean additive) {
		if (candidate < 1 || candidate > Sudoku2.UNITS) {
			return false;
		}
		if (!showHintCellValues[candidate] && !isCandidateValueAvailableForFiltering(candidate)) {
			OperationSoundPlayer.play(OperationSoundPlayer.Sound.UNAVAILABLE);
			return false;
		}
		if (additive) {
			showHintCellValues[candidate] = !showHintCellValues[candidate];
			checkIsShowInvalidOrPossibleCells();
		} else {
			setShowHintCellValue(candidate);
		}
		return true;
	}

	private boolean isCandidateValueAvailableForFiltering(int candidate) {
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			if (sudoku.getValue(index) == 0 && sudoku.isCandidate(index, candidate, !showCandidates)) {
				return true;
			}
		}
		return false;
	}

	public void toggleBivalueFilter() {
		showBivalueCells = !showBivalueCells;
		checkIsShowInvalidOrPossibleCells();
	}

	public void toggleTrivalueFilter() {
		showTrivalueCells = !showTrivalueCells;
		checkIsShowInvalidOrPossibleCells();
	}

	public boolean isBivalueFilterActive() {
		return showBivalueCells;
	}

	public boolean isTrivalueFilterActive() {
		return showTrivalueCells;
	}

	public boolean hasCandidateValueFilter() {
		for (int i = 1; i <= Sudoku2.UNITS; i++) {
			if (showHintCellValues[i]) {
				return true;
			}
		}
		return false;
	}

	public boolean hasCandidateCountFilter() {
		return showBivalueCells || showTrivalueCells;
	}

	private boolean candidateValueFilterMatches(int index) {
		if (!hasCandidateValueFilter()) {
			return false;
		}
		if (sudoku.getValue(index) != 0) {
			return false;
		}
		return sudoku.areCandidatesValid(index, showHintCellValues, !showCandidates);
	}

	private boolean candidateCountFilterMatches(int index) {
		if (!hasCandidateCountFilter() || sudoku.getValue(index) != 0) {
			return false;
		}
		int count = sudoku.getAllCandidates(index, !showCandidates).length;
		return (showBivalueCells && count == 2) || (showTrivalueCells && count == 3);
	}

	public boolean hasAnyViewFilter() {
		return hasCandidateValueFilter() || hasCandidateCountFilter();
	}

	public void resetShowHintCellValues() {
		resetCandidateValueFilters();
		showBivalueCells = false;
		showTrivalueCells = false;
		showInvalidOrPossibleCells = false;
	}

	public boolean isShowDeviations() {
		return showDeviations;
	}

	public void setShowDeviations(boolean showDeviations) {
		this.showDeviations = showDeviations;
		mainFrame.check();
		repaint();
	}

	/**
	 * Schreibt ein BufferedImage in eine PNG-Datei. Dabei wird die Auflï¿½sung
	 * in die Metadaten der Datei geschrieben, was alles etwas kompliziert macht.
	 *
	 * @param bi       Zu zeichnendes Bild
	 * @param dpi      Auflï¿½sung in dots per inch
	 * @param fileName Pfad und Name der neuen Bilddatei
	 */
	private void writePNG(BufferedImage bi, int dpi, File file) {
		
		Iterator<ImageWriter> i = ImageIO.getImageWritersByFormatName("png");
		// are there any jpeg encoders available?

		// there's at least one ImageWriter, just use the first one
		if (i.hasNext()) {
			
			ImageWriter imageWriter = i.next();
			ImageWriteParam param = imageWriter.getDefaultWriteParam();
			ImageTypeSpecifier its = new ImageTypeSpecifier(bi.getColorModel(), bi.getSampleModel());
			IIOMetadata iomd = imageWriter.getDefaultImageMetadata(its, param);
			String formatName = "javax_imageio_png_1.0";
			Node node = iomd.getAsTree(formatName);

			int dpiRes = (int) (dpi / 2.54 * 100);
			IIOMetadataNode res = new IIOMetadataNode("pHYs");
			res.setAttribute("pixelsPerUnitXAxis", String.valueOf(dpiRes));
			res.setAttribute("pixelsPerUnitYAxis", String.valueOf(dpiRes));
			res.setAttribute("unitSpecifier", "meter");
			node.appendChild(res);

			try {
				iomd.setFromTree(formatName, node);
			} catch (IIOInvalidTreeException e) {
				JOptionPane.showMessageDialog(
					this, 
					e.getLocalizedMessage(),
					java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.error"),
					JOptionPane.ERROR_MESSAGE
				);
			}
			
			// attach the metadata to an image
			IIOImage iioimage = new IIOImage(bi, null, iomd);
			try {
				
				FileImageOutputStream out = new FileImageOutputStream(file);
				imageWriter.setOutput(out);
				imageWriter.write(iioimage);
				out.close();

				String companionFileName = file.getPath();
				if (companionFileName.toLowerCase().endsWith(".png")) {
					companionFileName = companionFileName.substring(0, companionFileName.length() - 4);
				}
				
				companionFileName += ".txt";
				PrintWriter cOut = new PrintWriter(new BufferedWriter(new FileWriter(companionFileName)));
				cOut.println(getSudokuString(ClipboardMode.CLUES_ONLY));
				cOut.println(getSudokuString(ClipboardMode.LIBRARY));
				cOut.println(getSudokuString(ClipboardMode.PM_GRID));
				if (step != null) {
					cOut.println(getSudokuString(ClipboardMode.PM_GRID_WITH_STEP));
				}
				
				cOut.close();
				
			} catch (IOException e) {
				JOptionPane.showMessageDialog(
					this, 
					e.getLocalizedMessage(),
					java.util.ResourceBundle.getBundle("intl/SudokuPanel").getString("SudokuPanel.error"),
					JOptionPane.ERROR_MESSAGE
				);
			}
		}
	}

	/**
	 * @param colorCells the colorCells to set
	 */
	/*
	public void setColorCells(boolean colorCells) {
		this.isColoringCells = colorCells;
		Options.getInstance().setColorCells(colorCells);
		updateCellZoomPanel();
	}*/
	
    /** Palette changes never attach color badges to the system pointer. */
    public void updateColorCursor() {
        annotationCursorBeforeDelete = null;
        setCursor(Cursor.getDefaultCursor());
        updateDeletionCursor();
    }

	/**
	 * Checks whether the candidate in the given cell is a Hidden Single.
	 *
	 * @param candidate
	 * @param row
	 * @param col
	 * @return
	 */
	private boolean isHiddenSingle(int candidate, int row, int col) {
		
		// sometimes the internal singles queues get corrupted
		sudoku.rebuildInternalData();
		SudokuStepFinder finder = SudokuSolverFactory.getDefaultSolverInstance().getStepFinder();
		List<SolutionStep> steps = finder.findAllHiddenSingles(sudoku);
		for (SolutionStep act : steps) {
			if (act.getType() == SolutionType.HIDDEN_SINGLE && 
				act.getValues().get(0) == candidate	&& 
				act.getIndices().get(0) == Sudoku2.getIndex(row, col)) {
				return true;
			}
		}
		
		return false;
	}

	private boolean showCandidateHighlight() {

		/*
		 * isCtrlDown was originally used to do two things: 1. toggle candidate
		 * visibility on double click 2. toggle cell selection on single click
		 * 
		 * Those functions don't leave any room for showCandidateHighlight. They
		 * conflict together, and such, I should come up with a new mechanism to toggle
		 * their state; however, for the time being, I will simply provide an option to
		 * keep it permanently on, or off. I find it too distracting.
		 */

		return Options.getInstance().isShowCandidateHighlight() /* || isCtrlDown */;
	}

	/**
	 * Collects the intersection or union of all valid candidates in all selected
	 * cells. Used to adjust the popup menu.
	 *
	 * @param intersection
	 * @return
	 */
	public SudokuSet collectCandidates(boolean intersection) {
		
		SudokuSet resultSet = new SudokuSet();
		SudokuSet tmpSet = new SudokuSet();
		
		if (intersection) {
			resultSet.setAll();
		}
		
		if (cellSelection.isEmpty()) {
			// A true empty selection has no cell to contribute to a context-menu
			// candidate set.  Do not turn (-1,-1) into an array index here.
			resultSet.clear();
			return resultSet;
		} else {
			// BUG: if all cells in the selection are set,
			// all candidates become valid for intersection == true
			boolean emptyCellsOnly = true;
			for (int index : cellSelection) {
				if (sudoku.getValue(index) == 0) {
					emptyCellsOnly = false;
					// get candidates only when cell is not set!
					sudoku.getCandidateSet(index, tmpSet);
					if (intersection) {
						resultSet.and(tmpSet);
					} else {
						resultSet.or(tmpSet);
					}
				}
			}
			
			if (intersection && emptyCellsOnly) {
				resultSet.clear();
			}
		}
		
		return resultSet;
	}

	/**
	 * Removes the candidate from all selected cells.
	 *
	 * @param candidate
	 * @return true if sudoku is changed, false otherwise
	 */
	public boolean removeCandidateFromActiveCells(int candidate) {
		
		boolean changed = false;
		if (cellSelection.isEmpty()) {
			return false;
		} else {
			for (int index : cellSelection) {
				if (sudoku.getValue(index) == 0 && sudoku.isCandidate(index, candidate, !showCandidates)) {
					sudoku.setCandidate(index, candidate, false, !showCandidates);
					changed = true;
				}
			}
		}
		
		return changed;
	}

	/**
	 * Handles candidate changed done in {@link CellZoomPanel}. Should not be used
	 * otherwise.
	 *
	 * @param candidate
	 */
	public void toggleOrRemoveCandidateFromCellZoomPanel(int candidate) {
		clearPendingAnnotationToolTap();
		if (annotationTool != AnnotationTool.DEFAULT_MOUSE) {
			return;
		}
		
		if (candidate != -1) {
			
			undoStack.push(sudoku.clone());
			boolean changed = false;
			
			changed = removeCandidateFromActiveCells(candidate);
			
			if (changed) {
				redoStack.clear();
				checkProgress();
				mainFrame.sudokuStateChanged();
			} else {
				undoStack.pop();
			}
			
			updateCellZoomPanel();
			mainFrame.check();
			repaint();
		}
	}

	/**
	 * @return the cellZoomPanel
	 */
	public CellZoomPanel getCellZoomPanel() {
		return cellZoomPanel;
	}

	/**
	 * @param cellZoomPanel the cellZoomPanel to set
	 */
	public void setCellZoomPanel(CellZoomPanel cellZoomPanel) {
		this.cellZoomPanel = cellZoomPanel;
	}
	
	public void clearCellColor(Color colorNumber) {
		
		for (int i = 0; i < Sudoku2.LENGTH; i++) {			
			SortedMap<Integer, Color> map = coloringMap;			
			if (map.containsKey(i) && map.get(i).equals(colorNumber)) {
				map.remove(i);
			}
		}

		updateCellZoomPanel();
		repaint();
	}
	
	
	public void clearCandidateColor(Color colorNumber) {

		SortedMap<Integer, Color> map = coloringCandidateMap;
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			for (int candidate = 1; candidate <= Sudoku2.UNITS; candidate++) {
				int key = index * 10 + candidate;				
				if (map.containsKey(key) && map.get(key).equals(colorNumber)) {
					map.remove(key);
				}		
			}
		}
		
		updateCellZoomPanel();
		repaint();
	}

	public void updateCellZoomPanel() {
		
		if (cellZoomPanel == null) {
			return;
		}
		
		if (cellZoomPanel.isColoring()) {
			
			cellZoomPanel.update(
				SudokuSetBase.EMPTY_SET,
				SudokuSetBase.EMPTY_SET,
				0,
				true,
				null,
				null
			);
			
			return;
		}

		if (cellSelection.isEmpty()) {
			cellZoomPanel.update(
				SudokuSetBase.EMPTY_SET,
				SudokuSetBase.EMPTY_SET,
				-1,
				false,
				null,
				null
			);
			return;
		}
		
		int index = Sudoku2.getIndex(getActiveRow(), getActiveCol());
		boolean singleCell = cellSelection.isEmpty() && sudoku.getValue(index) == 0;
		
		if (cellZoomPanel.isDefaultMouse()) {
			// normal operation -> collect candidates for selected cell(s)
			if (sudoku.getValue(index) != 0 && cellSelection.isEmpty()) {
				// cell is already set -> nothing can be selected
				cellZoomPanel.update(
					SudokuSetBase.EMPTY_SET,
					SudokuSetBase.EMPTY_SET,
					index,
					singleCell,
					null,
					null
				);
				
			} else {
				
				SudokuSet valueSet = collectCandidates(true);
				SudokuSet candSet = collectCandidates(false);
				
				cellZoomPanel.update(
					valueSet, 
					candSet, 
					index, 
					singleCell, 
					null, 
					null
				);
			}
			
		} else {
			
			if (!cellSelection.isEmpty() || (cellSelection.isEmpty() && sudoku.getValue(index) != 0)) {
				// no coloring, when set of cells is selected
				cellZoomPanel.update(
					SudokuSetBase.EMPTY_SET,
					SudokuSetBase.EMPTY_SET,
					index,
					singleCell,
					null,
					null
				);
				
			} else {
				
				SudokuSet valueSet = collectCandidates(true);
				SudokuSet candSet = collectCandidates(false);
				
				cellZoomPanel.update(
					valueSet, 
					candSet, 
					index,
					singleCell,
					coloringMap,
					coloringCandidateMap
				);
			}
		}
	}

	/**
	 * Gets a 81 character string. For every digit in that string, the corresponding
	 * cell is set as a given.
	 *
	 * @param givens
	 */
	public void setGivens(String givens) {
		undoStack.push(sudoku.clone());
		sudoku.setGivens(givens);
		updateCellZoomPanel();
		repaint();
		mainFrame.check();
	}

	/**
	 * Checks the progress of the current {@link #sudoku}. If the Sudoku is not yet
	 * valid, only the status of the sudoku is updated. If it is valid, a background
	 * progress check is scheduled.<br>
	 */
	public void checkProgress() {
		
		int anz = sudoku.getSolvedCellsAnz();

		if (anz == 0) {
			sudoku.setStatus(SudokuStatus.EMPTY);
			sudoku.setStatusGivens(SudokuStatus.EMPTY);
		} else if (anz <= 17) {
			sudoku.setStatus(SudokuStatus.INVALID);
			sudoku.setStatusGivens(SudokuStatus.INVALID);
		} else {
			
			if (sudoku.checkSudoku()) {				
				// we have to check!
				int anzSol = generator.getNumberOfSolutions(sudoku, 1000);
				sudoku.setStatus(anzSol);
				// the status of the givens is not changed here; it only changes
				// when the givens themselved are changed
				// sudoku.setStatusGivens(anzSol);
				if (anzSol == 1) {
					// the sudoku is valid -> check the progress
					progressChecker.startCheck(sudoku);
				}	
			}
		}
	}

	/**
	 * Checks the array {@link #showHintCellValues}. If only one value is selected,
	 * that value is returned. If no or more than one values are selected, 0 is
	 * returned.
	 *
	 * @return
	 */
	private int getShowHintCellValue() {
		
		int value = 0;
		for (int i = 1; i <= Sudoku2.UNITS; i++) {
			if (showHintCellValues[i]) {
				if (value == 0) {
					value = i;
				} else {
					// more than one value
					return 0;
				}
			}
		}
		
		return value;
	}

	public void checkIsShowInvalidOrPossibleCells() {
        invalidateSearchPreview();
		showInvalidOrPossibleCells = hasAnyViewFilter();
	}
	
	public void setColorIconsInPopupMenu() {
		rightClickMenu.setColorIconsInPopupMenu();
	}

	public void setShowColorKu() {
		rightClickMenu.setColorkuInPopupMenu(Options.getInstance().isShowColorKuAct());
		cellZoomPanel.calculateLayout();
		updateCellZoomPanel();
		repaint();
	}
	
	public void setColorsVisible(boolean isVisible) {
		isColoringVisible = isVisible;
	}

	public void resetColorKuImages() {
		for (int i = 0; i < colorKuImagesLarge.length; i++) {
			colorKuImagesLarge[i] = null;
			colorKuImagesSmall[i] = null;
		}
	}

	private void drawColorBox(int n, Graphics gc, int cx, int cy, int boxSize, boolean large) {
		
		BufferedImage[] images = null;
		
		if (large) {
			images = colorKuImagesLarge;
		} else {
			images = colorKuImagesSmall;
		}
		
		if (images[0] == null || images[0].getWidth() != boxSize) {
			for (int i = 0; i < images.length; i++) {
				images[i] = new ColorKuImage(boxSize, Options.getInstance().getColorKuColor(i + 1));
			}
		}

		gc.drawImage(images[n - 1], cx, cy, null);
	}

	/**
	 * Returns an array, that controls the display of the filter buttons in the
	 * toolbar. For every candidate, that is still present as candidate and thus can
	 * be filtered, the appropriate array element is <code>true</code>.<br>
	 * <br>
	 *
	 * Care has to be taken with prerequisites:
	 * <ul>
	 * <li>If "Show all candidates" is disabled, filtering is not possible</li>
	 * <li>...</li>
	 * </ul>
	 *
	 * @return
	 */
	public boolean[] getRemainingCandidates() {
		
		for (int i = 0; i < remainingCandidates.length; i++) {
			remainingCandidates[i] = false;
		}
		
		if (isShowCandidates()) {
			final int[] cands = Sudoku2.POSSIBLE_VALUES[sudoku.getRemainingCandidates()];
			for (int i = 0; i < cands.length; i++) {
				remainingCandidates[cands[i] - 1] = true;
			}
		}
		
		return remainingCandidates;
	}

	public void clearLastCandidateMouseOn() {
		lastCandidateMouseOn = null;
	}
}
