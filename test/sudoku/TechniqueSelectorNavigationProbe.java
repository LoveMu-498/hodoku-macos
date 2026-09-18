/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Exercises popup navigation at the application dispatcher and real list event seams. */
public final class TechniqueSelectorNavigationProbe {

	private TechniqueSelectorNavigationProbe() { }

	public static void main(String[] args) throws Exception {
		final MainFrame[] frames = new MainFrame[1];
		final List<Throwable> failures = new ArrayList<Throwable>();
		SwingUtilities.invokeAndWait(() -> {
			try {
				MainFrame frame = new MainFrame(null);
				frames[0] = frame;
				frame.setSize(1000, 700);
				frame.setVisible(true);
				frame.getSudokuPanel().clearAllCellSelection();
				frame.getSudokuPanel().setActiveCell(30);
				Map<SolutionType, List<SolutionStep>> steps = fixtureSteps();
				setField(frame, "cachedTechniqueSteps", steps);
				setField(frame, "cachedTechniqueTypes", new ArrayList<SolutionType>(steps.keySet()));
				setField(frame, "cachedTechniqueSignature", invoke(frame,
						"getTechniqueScanSignature", new Class<?>[] { Sudoku2.class },
						frame.getSudokuPanel().getSudoku()));
				setField(frame, "cachedTechniqueScanComplete", Boolean.TRUE);
				JMenuItem item = (JMenuItem) readField(frame, "selectTechniqueMenuItem");
				ActionEvent shortcut = new ActionEvent(item, ActionEvent.ACTION_PERFORMED,
						"shortcut", InputEvent.META_MASK | InputEvent.ALT_MASK);
				for (java.awt.event.ActionListener listener : item.getActionListeners()) {
					listener.actionPerformed(shortcut);
				}
			} catch (Throwable failure) {
				failures.add(failure);
			}
		});
		// The accelerator itself defers opening until Aqua's menu teardown completes.
		SwingUtilities.invokeAndWait(() -> {
			MainFrame frame = frames[0];
			if (frame == null) return;
			try {
				verifyDispatcherNavigation(frame);
			} catch (Throwable failure) {
				failures.add(failure);
			}
			try {
				verifyClickedRowWheel(frame);
			} catch (Throwable failure) {
				failures.add(failure);
			} finally {
				frame.dispose();
			}
		});
		for (Throwable failure : failures) failure.printStackTrace();
		if (!failures.isEmpty()) System.exit(1);
		System.out.println("Technique selector navigation checks passed");
		System.exit(0);
	}

	private static Map<SolutionType, List<SolutionStep>> fixtureSteps() {
		Map<SolutionType, List<SolutionStep>> result =
				new LinkedHashMap<SolutionType, List<SolutionStep>>();
		SolutionType[] types = { SolutionType.NAKED_PAIR, SolutionType.HIDDEN_PAIR,
				SolutionType.NAKED_TRIPLE, SolutionType.HIDDEN_TRIPLE,
				SolutionType.NAKED_QUADRUPLE, SolutionType.HIDDEN_QUADRUPLE,
				SolutionType.X_WING, SolutionType.SWORDFISH, SolutionType.JELLYFISH,
				SolutionType.SKYSCRAPER, SolutionType.TURBOT_FISH,
				SolutionType.XY_WING, SolutionType.XYZ_WING, SolutionType.W_WING,
				SolutionType.X_CHAIN, SolutionType.XY_CHAIN, SolutionType.AIC,
				SolutionType.ALS_XZ, SolutionType.ALS_XY_WING, SolutionType.ALS_XY_CHAIN };
		for (SolutionType type : types) {
			List<SolutionStep> instances = new ArrayList<SolutionStep>();
			for (int cell : new int[] { 10, 20, 30 }) {
				SolutionStep step = new SolutionStep(type);
				step.addCandidateToDelete(cell, 1);
				instances.add(step);
			}
			result.put(type, instances);
		}
		return result;
	}

	private static void verifyDispatcherNavigation(MainFrame frame) throws Exception {
		JPopupMenu popup = (JPopupMenu) readField(frame, "techniqueSelectorPopup");
		require(popup != null && popup.isVisible(), "accelerator did not open the selector");
		JList<?> list = (JList<?>) findComponent(popup, JList.class);
		require(list != null, "accelerator opened no technique list");
		KeyEventDispatcher dispatcher = (KeyEventDispatcher) readField(frame, "annotationKeyDispatcher");
		int before = list.getSelectedIndex();
		List<Integer> cellsBefore = new ArrayList<Integer>(frame.getSudokuPanel().getSelectedCells());
		// A native popup can temporarily leave focus on the board/previous component.
		// Its dispatcher must still own navigation instead of relying on focus timing.
		KeyEvent down = key(frame.getSudokuPanel(), KeyEvent.KEY_PRESSED, KeyEvent.VK_DOWN, 0);
		boolean consumed = dispatcher.dispatchKeyEvent(down);
		require(consumed && list.getSelectedIndex() == before + 1,
				"shortcut-opened selector did not route Down while focus remained on the board"
				+ " (consumed=" + consumed + ", row=" + list.getSelectedIndex() + ")");
		require(cellsBefore.equals(new ArrayList<Integer>(frame.getSudokuPanel().getSelectedCells())),
				"selector Down key changed the ordinary board selection");
		KeyEvent optionDown = key(frame.getRootPane(), KeyEvent.KEY_PRESSED,
				KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK);
		require(dispatcher.dispatchKeyEvent(optionDown) && list.getSelectedIndex() == before + 2,
				"holding Option prevented keyboard navigation of the previewed techniques");
		JTextField number = (JTextField) findComponent(popup, JTextField.class);
		String original = number.getText();
		require(dispatcher.dispatchKeyEvent(key(number, KeyEvent.KEY_PRESSED, KeyEvent.VK_DOWN, 0))
				&& !original.equals(number.getText()),
				"focused instance-number Down no longer uses ranked instance navigation");
		require(number.isEditable(), "exact original instance input became read-only");
	}

	private static void verifyClickedRowWheel(MainFrame frame) throws Exception {
		JPopupMenu popup = (JPopupMenu) readField(frame, "techniqueSelectorPopup");
		JList<?> list = (JList<?>) findComponent(popup, JList.class);
		JScrollPane scroll = (JScrollPane) findComponent(popup, JScrollPane.class);
		require(list != null && scroll != null, "wheel fixture has no list/scroll container");
		list.setSelectedIndex(1);
		list.ensureIndexIsVisible(1);
		Rectangle row = list.getCellBounds(1, 1);
		require(row != null, "wheel fixture has no selected-row bounds");
		MouseEvent click = new MouseEvent(list, MouseEvent.MOUSE_CLICKED,
				System.currentTimeMillis(), 0, row.x + 8, row.y + row.height / 2,
				1, false, MouseEvent.BUTTON1);
		for (java.awt.event.MouseListener listener : list.getMouseListeners()) listener.mouseClicked(click);
		JTextField number = (JTextField) findComponent(popup, JTextField.class);
		require("3".equals(number.getText()), "ranked fixture did not put original instance 3 first");
		int scrollBefore = scroll.getVerticalScrollBar().getValue();
		MouseWheelEvent selectedWheel = wheel(list, row, 1);
		list.dispatchEvent(selectedWheel);
		require("1".equals(number.getText()),
				"wheel over the clicked technique row did not advance its ranked instances");
		require(scroll.getVerticalScrollBar().getValue() == scrollBefore,
				"instance wheel also scrolled the technique list");
		Rectangle otherRow = list.getCellBounds(2, 2);
		list.dispatchEvent(wheel(list, otherRow, 1));
		require("1".equals(number.getText()), "wheel over another row changed the selected instance");
		require(scroll.getVerticalScrollBar().getValue() > scrollBefore,
				"wheel over another row no longer scrolls the technique list");
		number.setText("2");
		popup.setVisible(false);
		require(Integer.valueOf(1).equals(readField(frame, "selectedHintInstance")),
				"closing after manual original-number input did not commit that exact instance");
	}

	private static KeyEvent key(Component source, int id, int keyCode, int modifiers) {
		return new KeyEvent(source, id, System.currentTimeMillis(), modifiers,
				keyCode, KeyEvent.CHAR_UNDEFINED);
	}

	private static MouseWheelEvent wheel(JList<?> list, Rectangle row, int direction) {
		return new MouseWheelEvent(list, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
				row.x + 8, row.y + row.height / 2, 0, false,
				MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, direction);
	}

	private static Object invoke(Object target, String name, Class<?>[] parameters,
			Object... arguments) throws Exception {
		Method method = target.getClass().getDeclaredMethod(name, parameters);
		method.setAccessible(true);
		return method.invoke(target, arguments);
	}

	private static Object readField(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Component findComponent(Container root, Class<?> type) {
		for (Component component : root.getComponents()) {
			if (type.isInstance(component)) return component;
			if (component instanceof Container) {
				Component found = findComponent((Container) component, type);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
