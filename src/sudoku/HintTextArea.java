/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;

/** Read-only hint surface with press-and-hold Sudoku references. */
public final class HintTextArea extends JTextArea {

	private static final long serialVersionUID = 1L;
	private static final Cursor DEFAULT_CURSOR = Cursor.getDefaultCursor();
	private static final Cursor LINK_CURSOR = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
	private static final String EXTERNAL_PASTE_ACTION = "externalHintPaste";

	private SudokuPanel sudokuPanel;
	private List<SudokuTextReference> references = Collections.emptyList();
	private SudokuTextReference hoveredReference;
	private SudokuTextReference pressedReference;
	private final Border idleBorder = BorderFactory.createEmptyBorder(1, 1, 1, 1);

	public HintTextArea() {
		setEditable(false);
		setBorder(idleBorder);
		setCursor(DEFAULT_CURSOR);
		addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent event) {
				updateFocusBorder();
			}

			@Override
			public void focusLost(FocusEvent event) {
				clearPressedReference();
				updateFocusBorder();
			}
		});
	}

	public void setSudokuPanel(SudokuPanel sudokuPanel) {
		this.sudokuPanel = sudokuPanel;
	}

	public void setExternalPasteAction(Action action) {
		KeyStroke shortcut = KeyStroke.getKeyStroke(KeyEvent.VK_V, SudokuUtil.getMenuShortcutMask());
		getInputMap(JComponent.WHEN_FOCUSED).put(shortcut, EXTERNAL_PASTE_ACTION);
		getActionMap().put(EXTERNAL_PASTE_ACTION, action);
	}

	public void setReferenceText(String text) {
		super.setText(text == null ? "" : text);
		references = SudokuReferenceParser.parse(getText());
		hoveredReference = null;
		clearPressedReference();
		getCaret().setVisible(false);
		setCursor(DEFAULT_CURSOR);
		repaint();
	}

	public List<SudokuTextReference> getReferences() {
		return references;
	}

	@Override
	protected void processMouseEvent(MouseEvent event) {
		switch (event.getID()) {
		case MouseEvent.MOUSE_PRESSED:
			requestFocusInWindow();
			pressedReference = referenceAt(event.getPoint());
			if (pressedReference != null && sudokuPanel != null) {
				sudokuPanel.setTransientReferenceHighlight(pressedReference);
			} else {
				showChainForLine(event.getPoint());
			}
			event.consume();
			return;
		case MouseEvent.MOUSE_RELEASED:
			clearPressedReference();
			event.consume();
			return;
		case MouseEvent.MOUSE_CLICKED:
			event.consume();
			return;
		case MouseEvent.MOUSE_EXITED:
			hoveredReference = null;
			setCursor(DEFAULT_CURSOR);
			clearPressedReference();
			repaint();
			break;
		default:
			break;
		}
		super.processMouseEvent(event);
		getCaret().setVisible(false);
	}

	@Override
	protected void processMouseMotionEvent(MouseEvent event) {
		if (event.getID() == MouseEvent.MOUSE_DRAGGED && pressedReference != null
				&& referenceAt(event.getPoint()) != pressedReference) {
			clearPressedReference();
			hoveredReference = null;
			setCursor(DEFAULT_CURSOR);
			repaint();
		}
		if (event.getID() == MouseEvent.MOUSE_MOVED) {
			SudokuTextReference reference = referenceAt(event.getPoint());
			if (reference != hoveredReference) {
				hoveredReference = reference;
				setCursor(reference == null ? DEFAULT_CURSOR : LINK_CURSOR);
				repaint();
			}
		}
		event.consume();
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		for (SudokuTextReference reference : references) {
			boolean active = reference == hoveredReference || reference == pressedReference;
			Color color = active
					? SudokuAppearancePalette.forRendering(false).getReferenceHighlightColor()
					: getForeground();
			graphics.setColor(color);
			if (active) {
				paintReferenceText(graphics, reference);
			}
			paintUnderline(graphics, reference);
		}
	}

	private void paintReferenceText(Graphics graphics, SudokuTextReference reference) {
		for (int position = reference.getStart(); position < reference.getEnd(); position++) {
			Rectangle bounds = characterBounds(position);
			if (bounds != null) {
				graphics.drawString(String.valueOf(getText().charAt(position)), bounds.x,
						bounds.y + getFontMetrics(getFont()).getAscent());
			}
		}
	}

	private void paintUnderline(Graphics graphics, SudokuTextReference reference) {
		for (int position = reference.getStart(); position < reference.getEnd(); position++) {
			Rectangle bounds = characterBounds(position);
			if (bounds != null) {
				graphics.drawLine(bounds.x, bounds.y + bounds.height - 1,
						bounds.x + Math.max(1, bounds.width), bounds.y + bounds.height - 1);
			}
		}
	}

	private SudokuTextReference referenceAt(Point point) {
		for (SudokuTextReference reference : references) {
			for (int position = reference.getStart(); position < reference.getEnd(); position++) {
				Rectangle bounds = characterBounds(position);
				if (bounds != null && bounds.contains(point)) {
					return reference;
				}
			}
		}
		return null;
	}

	private Rectangle characterBounds(int position) {
		try {
			Rectangle start = modelToView(position);
			Rectangle end = modelToView(position + 1);
			if (start == null) {
				return null;
			}
			int right = end != null && end.y == start.y ? end.x
					: start.x + getFontMetrics(getFont()).charWidth(getText().charAt(position));
			return new Rectangle(start.x, start.y, Math.max(1, right - start.x), start.height);
		} catch (BadLocationException ex) {
			return null;
		}
	}

	private void showChainForLine(Point point) {
		if (sudokuPanel == null || sudokuPanel.getStep() == null) {
			return;
		}
		try {
			int position = viewToModel(point);
			int line = getLineOfOffset(position);
			sudokuPanel.setChainInStep(line == 0 ? -1 : line - 1);
		} catch (BadLocationException ex) {
			sudokuPanel.setChainInStep(-1);
		}
	}

	private void clearPressedReference() {
		pressedReference = null;
		if (sudokuPanel != null) {
			sudokuPanel.clearTransientReferenceHighlight();
		}
		repaint();
	}

	private void updateFocusBorder() {
		if (isFocusOwner()) {
			Color color = SudokuAppearancePalette.forRendering(false).getReferenceHighlightColor();
			setBorder(BorderFactory.createLineBorder(color));
		} else {
			setBorder(idleBorder);
		}
		getCaret().setVisible(false);
		repaint();
	}
}
