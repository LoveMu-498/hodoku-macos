/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Ensures an explicitly selected technique uses an isolated solver and returns a matching step. */
public final class TechniqueSelectorProbe {
	private static final String PUZZLE = "530070000600195000098000060800060003400803001700020006060000280000419005000080079";

	private TechniqueSelectorProbe() {
	}

	public static void main(String[] args) throws Exception {
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					panel.setSudoku(PUZZLE);
					verifyChainPreviewUsesOnlyActualCells(frame);
					SolutionStep defaultStep = panel.getNextStep(false);
					require(defaultStep != null, "fixture did not produce a normal next step");
					SolutionType type = defaultStep.getType();
					panel.abortStep();
					SolutionStep selectedStep = panel.getNextStep(type);
					require(selectedStep != null && selectedStep.getType() == type,
							"selected technique did not return a step of the requested type");
					panel.abortStep();
					Method find = MainFrame.class.getDeclaredMethod("findAvailableTechniqueSteps",
							Sudoku2.class);
					find.setAccessible(true);
					@SuppressWarnings("unchecked")
					Map<SolutionType, java.util.List<SolutionStep>> available =
							(Map<SolutionType, java.util.List<SolutionStep>>) find.invoke(frame,
									panel.getSudoku().clone());
					require(!available.isEmpty(), "native All Steps result was not exposed to the selector");
					verifyRawStepCatalogReuse(panel.getSudoku().clone());
					verifyCompactInstanceNavigator(frame, available);
					verifyOptionPreviewColoringIsIsolated(frame, available);
					verifyOptionStateClearsOnDeactivation(frame);
					verifyPendingPopupNeverBecomesBlank(frame);
					verifyMatchMarkers();
					verifyCooperativeCancellationPreservesInterrupt(panel.getSudoku().clone());
					verifyCompletedExactStepRestoresDefaultHint(frame, available);
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) frame.dispose();
				}
			}
		});
		if (failure[0] != null) {
			failure[0].printStackTrace();
			System.exit(1);
		}
		verifyFirstVisiblePopupReplacementUsesResultSize();
		System.out.println("Technique selector checks passed");
		System.exit(0);
	}

	private static void verifyChainPreviewUsesOnlyActualCells(MainFrame frame) throws Exception {
		SolutionStep ordinary = new SolutionStep(SolutionType.X_CHAIN);
		ordinary.addChain(0, 2, new int[] {
				Chain.makeSEntry(10, 4, false), Chain.makeSEntry(13, 4, true),
				-Chain.makeSEntry(40, 4, false) });
		assertChainPreviewCells(frame, ordinary, 10, 13, 40);

		// Index zero is a real cell in a grouped node, not a value to filter out.
		SolutionStep grouped = new SolutionStep(SolutionType.GROUPED_AIC);
		grouped.addChain(0, 1, new int[] {
				Chain.makeSEntry(10, 0, -1, 4, false, Chain.GROUP_NODE),
				Chain.makeSEntry(13, 22, 31, 4, true, Chain.GROUP_NODE) });
		assertChainPreviewCells(frame, grouped, 0, 10, 13, 22, 31);

		SolutionStep alsStep = new SolutionStep(SolutionType.ALS_XY_CHAIN);
		for (int index = 0; index < 2; index++) {
			AlsInSolutionStep als = new AlsInSolutionStep();
			als.addIndex(33 + index);
			als.addCandidate(4);
			alsStep.getAlses().add(als);
		}
		// ALS reference 1 occupies the same packed fields as grouped cell indices.
		alsStep.addChain(0, 0, new int[] {
				Chain.makeSEntry(33, 1, 4, true, Chain.ALS_NODE) });
		assertChainPreviewCells(frame, alsStep, 33, 34);

		SolutionStep realFirstCell = new SolutionStep(SolutionType.X_CHAIN);
		realFirstCell.addChain(0, 1, new int[] {
				Chain.makeSEntry(0, 4, false), Chain.makeSEntry(9, 4, true) });
		assertChainPreviewCells(frame, realFirstCell, 0, 9);
	}

	private static void assertChainPreviewCells(MainFrame frame, SolutionStep step,
			Integer... expectedCells) throws Exception {
		Object previousSteps = readField(frame, "cachedTechniqueSteps");
		Object previousFootprints = readField(frame, "cachedTechniqueFootprints");
		Object previousOption = readField(frame, "optionKeyDown");
		try {
			Map<SolutionType, List<SolutionStep>> steps =
					new java.util.LinkedHashMap<SolutionType, List<SolutionStep>>();
			steps.put(step.getType(), java.util.Collections.singletonList(step));
			setField(frame, "cachedTechniqueSteps", steps);
			Method footprints = MainFrame.class.getDeclaredMethod("buildTechniqueFootprints",
					Map.class, Sudoku2.class);
			footprints.setAccessible(true);
			setField(frame, "cachedTechniqueFootprints", footprints.invoke(frame, steps,
					frame.getSudokuPanel().getSudoku().clone()));
			setField(frame, "optionKeyDown", Boolean.TRUE);
			Method populate = MainFrame.class.getDeclaredMethod("populateTechniqueSelector",
					JPopupMenu.class, List.class, ResourceBundle.class);
			populate.setAccessible(true);
			JPopupMenu popup = new PopupLayoutProbe();
			populate.invoke(frame, popup, java.util.Collections.singletonList(step.getType()),
					ResourceBundle.getBundle("intl/MainFrame"));
			JList<?> list = (JList<?>) findComponent(
					(java.awt.Container) popup.getComponent(0), JList.class);
			list.setSelectedIndex(1);
			Set<Integer> actual = readIntegerSet(frame.getSudokuPanel(), "techniquePreviewCells");
			Set<Integer> expected = new HashSet<Integer>(java.util.Arrays.asList(expectedCells));
			require(expected.equals(actual), "Option preview decoded non-cell chain fields as cells: "
					+ step.getType() + "; expected " + expected + ", got " + actual);
		} finally {
			setField(frame, "cachedTechniqueSteps", previousSteps);
			setField(frame, "cachedTechniqueFootprints", previousFootprints);
			setField(frame, "optionKeyDown", previousOption);
			frame.getSudokuPanel().clearTechniquePreviewCells();
		}
	}

	private static void verifyCompactInstanceNavigator(MainFrame frame,
			Map<SolutionType, List<SolutionStep>> available) throws Exception {
		SolutionType type = SolutionType.HIDDEN_QUADRUPLE;
		List<SolutionStep> instances = available.get(type);
		require(instances != null && instances.size() >= 3,
				"fixture did not provide the expected multi-instance Hidden Quadruple");

		Field stepsField = MainFrame.class.getDeclaredField("cachedTechniqueSteps");
		stepsField.setAccessible(true);
		stepsField.set(frame, available);
		Method populate = MainFrame.class.getDeclaredMethod("populateTechniqueSelector",
				JPopupMenu.class, List.class, ResourceBundle.class);
		populate.setAccessible(true);
		JPopupMenu popup = new PopupLayoutProbe();
		installPopupLifecycle(frame, popup);
		List<SolutionType> types = new ArrayList<SolutionType>();
		types.add(type);
		populate.invoke(frame, popup, types, ResourceBundle.getBundle("intl/MainFrame"));
		require(popup.getComponentCount() == 1,
				"compact selector did not install a single content panel");
		java.awt.Container selector = (java.awt.Container) popup.getComponent(0);
		require(countComponents(selector, JScrollPane.class) == 1,
				"selector restored the obsolete full-height instance list");

		JList<?> list = (JList<?>) findComponent(selector, JList.class);
		require(list != null && list.getModel().getSize() == 2,
				"compact selector did not expose automatic and requested technique rows");
		list.setSelectedIndex(1);
		Field navigatorField = selector.getClass().getDeclaredField("instanceNavigator");
		navigatorField.setAccessible(true);
		javax.swing.JPanel navigator = (javax.swing.JPanel) navigatorField.get(selector);
		require(navigator.isVisible(), "multi-instance technique did not reveal the bottom navigator");
		JTextField originalIndex = (JTextField) findComponent(navigator, JTextField.class);
		require(originalIndex != null && !originalIndex.getText().isEmpty(),
				"bottom navigator did not expose the stable original instance number");
		require(originalIndex.isEditable(),
				"bottom navigator no longer accepts exact original instance numbers");
		String firstNumber = originalIndex.getText();
		java.awt.event.MouseWheelEvent wheel = new java.awt.event.MouseWheelEvent(originalIndex,
				java.awt.event.MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
				1, 1, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1);
		for (java.awt.event.MouseWheelListener listener : originalIndex.getMouseWheelListeners()) {
			listener.mouseWheelMoved(wheel);
		}
		String secondNumber = originalIndex.getText();
		require(!firstNumber.equals(secondNumber),
				"mouse wheel over n/m did not move through similarity-ranked instances");
		javax.swing.KeyStroke up = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0);
		Object actionKey = originalIndex.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(up);
		javax.swing.Action action = actionKey == null ? null : originalIndex.getActionMap().get(actionKey);
		require(action != null, "focused n/m navigator did not bind the Up key");
		action.actionPerformed(new java.awt.event.ActionEvent(originalIndex,
				java.awt.event.ActionEvent.ACTION_PERFORMED, "previous"));
		require(firstNumber.equals(originalIndex.getText()),
				"Up key did not follow the same similarity-ranked order as the mouse wheel");

		// Changing the stable original number without pressing Enter must still be
		// committed when the user dismisses the popup.
		originalIndex.setText("3");
		setField(frame, "techniqueSelectorPopup", popup);
		firePopupClosed(popup);
		assertCommittedInstance(frame, type, instances, 2);

		// Reopening must restore the exact committed instance. Closing again without
		// touching n/m commits the currently displayed instance as-is.
		JPopupMenu reopened = new PopupLayoutProbe();
		installPopupLifecycle(frame, reopened);
		populate.invoke(frame, reopened, types, ResourceBundle.getBundle("intl/MainFrame"));
		java.awt.Container reopenedSelector = (java.awt.Container) reopened.getComponent(0);
		JList<?> reopenedList = (JList<?>) findComponent(reopenedSelector, JList.class);
		require(reopenedList != null && reopenedList.getSelectedIndex() == 1,
				"reopened selector did not retain the committed technique");
		JTextField reopenedIndex = (JTextField) findComponent(reopenedSelector, JTextField.class);
		require(reopenedIndex != null && "3".equals(reopenedIndex.getText()),
				"reopened selector did not retain the committed 3/" + instances.size() + " instance");
		setField(frame, "techniqueSelectorPopup", reopened);
		firePopupClosed(reopened);
		assertCommittedInstance(frame, type, instances, 2);
		assertNextStepHintUsesExactInstance(frame, instances.get(2));
	}

	private static void verifyOptionPreviewColoringIsIsolated(MainFrame frame,
			Map<SolutionType, List<SolutionStep>> available) throws Exception {
		SolutionType type = SolutionType.HIDDEN_QUADRUPLE;
		List<SolutionStep> instances = available.get(type);
		require(instances != null && instances.size() >= 3,
				"preview-color fixture did not provide Hidden Quadruple 3/n");

		Field stepsField = MainFrame.class.getDeclaredField("cachedTechniqueSteps");
		stepsField.setAccessible(true);
		stepsField.set(frame, available);
		Method populate = MainFrame.class.getDeclaredMethod("populateTechniqueSelector",
				JPopupMenu.class, List.class, ResourceBundle.class);
		populate.setAccessible(true);
		JPopupMenu popup = new PopupLayoutProbe();
		List<SolutionType> types = new ArrayList<SolutionType>();
		types.add(type);
		setField(frame, "optionKeyDown", Boolean.TRUE);
		populate.invoke(frame, popup, types, ResourceBundle.getBundle("intl/MainFrame"));
		java.awt.Container selector = (java.awt.Container) popup.getComponent(0);
		JList<?> list = (JList<?>) findComponent(selector, JList.class);
		require(list != null, "preview-color selector did not expose its technique list");
		list.setSelectedIndex(1);
		JTextField originalIndex = (JTextField) findComponent(selector, JTextField.class);
		require(originalIndex != null && "3".equals(originalIndex.getText()),
				"preview-color selector did not reopen on the committed third instance");

		setField(frame, "techniqueSelectorPopup", popup);
		KeyEventDispatcher dispatcher = (KeyEventDispatcher) readField(frame, "annotationKeyDispatcher");
		java.awt.Component source = frame.getRootPane();
		int option = InputEvent.ALT_DOWN_MASK;
		Set<Integer> previewCells = readIntegerSet(frame.getSudokuPanel(), "techniquePreviewCells");
		require(!previewCells.isEmpty(),
				"Option held while opening the selector did not expose the selected step footprint");

		int oldSelection = 2; // r1c3 is empty and outside Hidden Quadruple 3/5.
		require(!previewCells.contains(Integer.valueOf(oldSelection)),
				"preview fixture unexpectedly included the old ordinary selection");
		SudokuPanel panel = frame.getSudokuPanel();
		panel.clearColoring();
		panel.clearAllCellSelection();
		panel.setActiveCell(oldSelection);
		Set<Integer> selectionBefore = new HashSet<Integer>(panel.getSelectedCells());
		AnnotationTool toolBefore = panel.getAnnotationTool();

		for (int offset = 0; offset < 4; offset++) {
			int keyCode = KeyEvent.VK_A + offset;
			Color expected = Options.getInstance().getColoringColors()[offset * 2];
			require(dispatch(dispatcher, source, KeyEvent.KEY_PRESSED, keyCode,
					option, Character.toLowerCase((char) keyCode)),
					"Option+" + KeyEvent.getKeyText(keyCode) + " leaked past the selector");
			assertPreviewColoring(panel, previewCells, oldSelection, expected);

			// Native key repeat must not immediately toggle the same coloring back off.
			require(dispatch(dispatcher, source, KeyEvent.KEY_PRESSED, keyCode,
					option, Character.toLowerCase((char) keyCode)),
					"repeated Option color key leaked past the selector");
			assertPreviewColoring(panel, previewCells, oldSelection, expected);
			require(dispatch(dispatcher, source, KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED,
					option, optionCharacter(offset)),
					"Option color KEY_TYPED event leaked into the focused selector control");
			require(dispatch(dispatcher, source, KeyEvent.KEY_RELEASED, keyCode,
					option, Character.toLowerCase((char) keyCode)),
					"Option color key release leaked past the selector");
		}

		Map<Integer, Color> beforeUnsupportedChord = getCellColoring(panel);
		int shiftedOption = option | InputEvent.SHIFT_DOWN_MASK;
		require(dispatch(dispatcher, source, KeyEvent.KEY_PRESSED, KeyEvent.VK_A,
				shiftedOption, 'A'), "Option+Shift+A leaked past the selector");
		require(beforeUnsupportedChord.equals(getCellColoring(panel)),
				"Option+Shift+A unexpectedly changed preview coloring");
		dispatch(dispatcher, source, KeyEvent.KEY_RELEASED, KeyEvent.VK_A,
				shiftedOption, 'A');

		require(dispatch(dispatcher, source, KeyEvent.KEY_RELEASED, KeyEvent.VK_ALT,
				0, KeyEvent.CHAR_UNDEFINED), "selector did not consume Option release");
		require(Boolean.FALSE.equals(readField(frame, "optionKeyDown")),
				"selector retained a stale global Option-down state after release");
		require(readIntegerSet(panel, "techniquePreviewCells").isEmpty(),
				"releasing Option left the technique footprint preview visible");
		require(selectionBefore.equals(new HashSet<Integer>(panel.getSelectedCells())),
				"technique preview coloring replaced the ordinary board selection");
		require(panel.getAnnotationTool() == toolBefore,
				"technique preview coloring changed the active pointer tool");
		require("3".equals(originalIndex.getText()),
				"Option+A-D KEY_TYPED input replaced the focused original instance number");
		assertPreviewColoring(panel, previewCells, oldSelection,
				Options.getInstance().getColoringColors()[6]);
	}

	private static void installPopupLifecycle(MainFrame frame, JPopupMenu popup) throws Exception {
		Method install = MainFrame.class.getDeclaredMethod(
				"installTechniqueSelectorPopupLifecycle", JPopupMenu.class);
		install.setAccessible(true);
		install.invoke(frame, popup);
	}

	private static void verifyOptionStateClearsOnDeactivation(MainFrame frame) throws Exception {
		setField(frame, "optionKeyDown", Boolean.TRUE);
		javax.swing.JWindow ownedPopup = new javax.swing.JWindow(frame);
		try {
			java.awt.event.WindowEvent ownedEvent = new java.awt.event.WindowEvent(frame,
					java.awt.event.WindowEvent.WINDOW_LOST_FOCUS, ownedPopup);
			for (java.awt.event.WindowFocusListener listener : frame.getWindowFocusListeners()) {
				listener.windowLostFocus(ownedEvent);
			}
			require(Boolean.TRUE.equals(readField(frame, "optionKeyDown")),
					"moving focus into an owned popup discarded the held Option state");
		} finally {
			ownedPopup.dispose();
		}
		java.awt.event.WindowEvent externalEvent = new java.awt.event.WindowEvent(frame,
				java.awt.event.WindowEvent.WINDOW_LOST_FOCUS, null);
		for (java.awt.event.WindowFocusListener listener : frame.getWindowFocusListeners()) {
			listener.windowLostFocus(externalEvent);
		}
		require(Boolean.FALSE.equals(readField(frame, "optionKeyDown")),
				"losing the application left a stale Option-down selector state");
	}

	private static void firePopupClosed(JPopupMenu popup) {
		javax.swing.event.PopupMenuEvent event = new javax.swing.event.PopupMenuEvent(popup);
		for (javax.swing.event.PopupMenuListener listener : popup.getPopupMenuListeners()) {
			listener.popupMenuWillBecomeInvisible(event);
		}
	}

	private static void assertCommittedInstance(MainFrame frame, SolutionType type,
			List<SolutionStep> instances, int originalIndex) throws Exception {
		require(readField(frame, "selectedHintTechnique") == type,
				"dismissing the selector did not commit the selected technique");
		require(Integer.valueOf(originalIndex).equals(readField(frame, "selectedHintInstance")),
				"dismissing the selector did not commit original instance " + (originalIndex + 1));
		require(Integer.valueOf(instances.size()).equals(readField(frame, "selectedHintInstanceCount")),
				"committed selector instance count no longer matches the native catalog");
		SolutionStep selected = (SolutionStep) readField(frame, "selectedHintStep");
		SolutionStep expected = instances.get(originalIndex);
		require(selected != null && selected.getType() == type
				&& selected.compareTo(expected) == 0 && expected.compareTo(selected) == 0,
				"selector committed only a technique type instead of the exact native step");
		javax.swing.JButton button = (javax.swing.JButton) readField(frame, "selectTechniqueToggleButton");
		require(button != null && button.getText().contains(
				(originalIndex + 1) + "/" + instances.size()),
				"selector button did not expose the committed stable instance number");
	}

	private static void assertNextStepHintUsesExactInstance(MainFrame frame,
			SolutionStep expected) throws Exception {
		Method getHint = MainFrame.class.getDeclaredMethod("getHint", int.class);
		getHint.setAccessible(true);
		getHint.invoke(frame, Integer.valueOf(2));
		SolutionStep shown = frame.getSudokuPanel().getStep();
		require(shown != null && shown.compareTo(expected) == 0 && expected.compareTo(shown) == 0,
				"F12 hint routing ignored the selector's exact committed instance");
		frame.getSudokuPanel().abortStep();
	}

	private static void verifyCompletedExactStepRestoresDefaultHint(MainFrame frame,
			Map<SolutionType, List<SolutionStep>> available) throws Exception {
		SolutionType selectedType = null;
		SolutionStep selectedStep = null;
		int selectedIndex = -1;
		int selectedCount = 0;
		for (Map.Entry<SolutionType, List<SolutionStep>> entry : available.entrySet()) {
			List<SolutionStep> instances = entry.getValue();
			for (int i = 0; instances != null && i < instances.size(); i++) {
				if (instances.get(i).getCandidatesToDelete().size() >= 2) {
					selectedType = entry.getKey();
					selectedStep = instances.get(i);
					selectedIndex = i;
					selectedCount = instances.size();
					break;
				}
			}
			if (selectedStep != null) break;
		}
		require(selectedStep != null,
				"fixture exposed no exact technique with multiple candidate eliminations");

		Method apply = MainFrame.class.getDeclaredMethod("applyTechniqueInstance",
				SolutionType.class, SolutionStep.class, int.class, int.class);
		apply.setAccessible(true);
		apply.invoke(frame, selectedType, selectedStep,
				Integer.valueOf(selectedIndex), Integer.valueOf(selectedCount));
		require(readField(frame, "selectedHintTechnique") == selectedType,
				"fixture could not commit an exact technique before manual completion");
		frame.setSolutionStep((SolutionStep) selectedStep.clone(), true);

		SudokuPanel panel = frame.getSudokuPanel();
		List<Candidate> eliminations = selectedStep.getCandidatesToDelete();
		Candidate first = eliminations.get(0);
		panel.getSudoku().delCandidate(first.getIndex(), first.getValue());
		frame.sudokuStateChanged();
		require(readField(frame, "selectedHintTechnique") == selectedType,
				"partially completing an exact step reset the selector too early");
		require(panel.getStep() != null,
				"partially completing an exact step cleared its detailed preview too early");

		for (int i = 1; i < eliminations.size() - 1; i++) {
			Candidate candidate = eliminations.get(i);
			panel.getSudoku().delCandidate(candidate.getIndex(), candidate.getValue());
			frame.sudokuStateChanged();
		}
		Candidate last = eliminations.get(eliminations.size() - 1);
		panel.clearAllCellSelection();
		panel.setActiveCell(last.getIndex());
		panel.toggleOrRemoveCandidateFromCellZoomPanel(last.getValue());
		require(readField(frame, "selectedHintTechnique") == null
				&& readField(frame, "selectedHintStep") == null,
				"the Cell Zoom candidate path did not restore the default hint mode");
		require(panel.getStep() == null,
				"completed exact step remained as a stale board overlay");
		require(!((javax.swing.JButton) readField(frame, "hinweisAusfuehrenButton")).isEnabled()
				&& !((javax.swing.JButton) readField(frame, "hinweisAbbrechenButton")).isEnabled(),
				"completed exact step left its execute or cancel controls enabled");
		javax.swing.JButton selectorButton = (javax.swing.JButton) readField(
				frame, "selectTechniqueToggleButton");
		String defaultLabel = ResourceBundle.getBundle("intl/MainFrame")
				.getString("MainFrame.techniqueSelector.automatic");
		require("\u9ed8\u8ba4\u63d0\u793a\uff08Default Hint\uff09".equals(defaultLabel)
				&& selectorButton.getText().startsWith(defaultLabel),
				"selector did not expose the renamed default hint mode");

		Method getHint = MainFrame.class.getDeclaredMethod("getHint", int.class);
		getHint.setAccessible(true);
		getHint.invoke(frame, Integer.valueOf(2));
		SolutionStep next = panel.getStep();
		require(next != null && (next.compareTo(selectedStep) != 0
				|| selectedStep.compareTo(next) != 0),
				"the next detailed hint reused the manually completed exact step");
		panel.abortStep();
	}

	private static void assertPreviewColoring(SudokuPanel panel, Set<Integer> previewCells,
			int oldSelection, Color expected) {
		Map<Integer, Color> coloring = getCellColoring(panel);
		require(coloring.keySet().equals(previewCells),
				"preview coloring targeted cells outside the visible technique footprint: " + coloring.keySet());
		require(!coloring.containsKey(Integer.valueOf(oldSelection)),
				"preview coloring leaked into the pre-existing ordinary selection");
		for (Color actual : coloring.values()) {
			require(expected.equals(actual), "preview coloring used the wrong A-D palette group");
		}
	}

	private static Map<Integer, Color> getCellColoring(SudokuPanel panel) {
		GuiState state = new GuiState();
		panel.getState(state, true);
		return state.getColoringMap();
	}

	private static char optionCharacter(int offset) {
		return (char) ('a' + offset);
	}

	private static boolean dispatch(KeyEventDispatcher dispatcher, java.awt.Component source,
			int id, int keyCode, int modifiers, char keyChar) {
		return dispatcher.dispatchKeyEvent(new KeyEvent(source, id, System.currentTimeMillis(),
				modifiers, keyCode, keyChar));
	}

	@SuppressWarnings("unchecked")
	private static Set<Integer> readIntegerSet(Object target, String name) throws Exception {
		return new HashSet<Integer>((Set<Integer>) readField(target, name));
	}

	private static Object readField(Object target, String name) throws Exception {
		Field field = findField(target.getClass(), name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void setField(Object target, String name, Object value) throws Exception {
		Field field = findField(target.getClass(), name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
		Class<?> current = type;
		while (current != null) {
			try {
				return current.getDeclaredField(name);
			} catch (NoSuchFieldException ex) {
				current = current.getSuperclass();
			}
		}
		throw new NoSuchFieldException(name);
	}

	private static int countComponents(java.awt.Container root, Class<?> type) {
		int count = 0;
		for (java.awt.Component component : root.getComponents()) {
			if (type.isInstance(component)) count++;
			if (component instanceof java.awt.Container) {
				count += countComponents((java.awt.Container) component, type);
			}
		}
		return count;
	}

	private static java.awt.Component findComponent(java.awt.Container root, Class<?> type) {
		for (java.awt.Component component : root.getComponents()) {
			if (type.isInstance(component)) return component;
			if (component instanceof java.awt.Container) {
				java.awt.Component found = findComponent((java.awt.Container) component, type);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static void verifyFirstVisiblePopupReplacementUsesResultSize() throws Exception {
		final Throwable[] failure = new Throwable[1];
		final Dimension[] popupSize = new Dimension[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override public void run() {
				MainFrame frame = null;
				try {
					frame = new MainFrame(null);
					frame.getSudokuPanel().setSudoku(PUZZLE);
					frame.setSize(1000, 700);
					frame.setVisible(true);

					SolutionStep step = frame.getSudokuPanel().getNextStep(false);
					require(step != null, "visible selector fixture did not produce a step");
					frame.getSudokuPanel().abortStep();
					Map<SolutionType, List<SolutionStep>> steps =
							new java.util.LinkedHashMap<SolutionType, List<SolutionStep>>();
					List<SolutionStep> instances = new ArrayList<SolutionStep>();
					instances.add(step);
					steps.put(step.getType(), instances);
					Field stepsField = MainFrame.class.getDeclaredField("cachedTechniqueSteps");
					stepsField.setAccessible(true);
					stepsField.set(frame, steps);

					JPopupMenu popup = new JPopupMenu();
					javax.swing.JMenuItem loading = new javax.swing.JMenuItem("loading");
					loading.setEnabled(false);
					popup.add(loading);
					popup.show(frame.getRootPane(), 20, 20);
					require(popup.getSize().height < 100,
							"visible selector fixture did not start at loading-row size");
					Method populate = MainFrame.class.getDeclaredMethod("populateTechniqueSelector",
							JPopupMenu.class, List.class, ResourceBundle.class);
					populate.setAccessible(true);
					List<SolutionType> types = new ArrayList<SolutionType>();
					for (int i = 0; i < 30; i++) types.add(step.getType());
					populate.invoke(frame, popup, types, ResourceBundle.getBundle("intl/MainFrame"));
					require(popup.isVisible() && popup.getComponentCount() == 1
							&& popup.getComponent(0).getClass().getName()
									.contains("TechniqueSelectorPanel"),
							"visible technique selector did not replace its loading row");
					popupSize[0] = popup.getSize();
					popup.setVisible(false);
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) frame.dispose();
				}
			}
		});
		if (failure[0] != null) throw new AssertionError(failure[0]);
		require(popupSize[0] != null && popupSize[0].width >= 500
				&& popupSize[0].height >= 180 && popupSize[0].height <= 500,
				"first asynchronous technique selector used an unusable size " + popupSize[0]);
	}

	private static void verifyRawStepCatalogReuse(Sudoku2 sudoku) {
		TechniqueStepCatalog catalog = new TechniqueStepCatalog();
		List<SolutionStep> first = catalog.findAllRawSteps(sudoku, null);
		int enumerations = catalog.getEnumerationRunCount();
		require(!first.isEmpty(), "native All Steps catalog returned no steps for the fixture");
		List<SolutionStep> second = catalog.findAllRawSteps(sudoku, null);
		require(catalog.getEnumerationRunCount() == enumerations,
				"same-board native All Steps request was enumerated twice");
		require(second.size() == first.size(), "cached native All Steps result changed size");
		verifyConcurrentCatalogReuse(sudoku);
		verifyAllStepsSettingsInvalidateCatalogSignature(sudoku);
		verifySolverSettingsInvalidateCatalogSignature(sudoku);
	}

	private static void verifyConcurrentCatalogReuse(final Sudoku2 sudoku) {
		final TechniqueStepCatalog catalog = new TechniqueStepCatalog();
		final CountDownLatch start = new CountDownLatch(1);
		final CountDownLatch finished = new CountDownLatch(2);
		final Throwable[] failures = new Throwable[2];
		for (int i = 0; i < 2; i++) {
			final int index = i;
			new Thread(new Runnable() {
				@Override public void run() {
					try {
						start.await();
						List<SolutionStep> result = catalog.findAllRawSteps(sudoku.clone(), null);
						require(!result.isEmpty(), "concurrent catalog request lost its result");
					} catch (Throwable ex) {
						failures[index] = ex;
					} finally {
						finished.countDown();
					}
				}
			}, "technique-catalog-probe-" + i).start();
		}
		start.countDown();
		try {
			require(finished.await(30, TimeUnit.SECONDS), "concurrent catalog requests did not finish");
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AssertionError(ex);
		}
		for (Throwable failure : failures) {
			if (failure != null) throw new AssertionError(failure);
		}
		require(catalog.getEnumerationRunCount() == 1,
				"overlapping same-board requests executed the same solver search twice");
	}

	private static void verifyAllStepsSettingsInvalidateCatalogSignature(Sudoku2 sudoku) {
		Options options = Options.getInstance();
		boolean oldValue = options.isAllStepsSearchFish();
		try {
			String before = TechniqueStepCatalog.createSignature(sudoku);
			options.setAllStepsSearchFish(!oldValue);
			String after = TechniqueStepCatalog.createSignature(sudoku);
			require(!before.equals(after), "All Steps setting change did not invalidate raw-step cache");
		} finally {
			options.setAllStepsSearchFish(oldValue);
		}
	}

	private static void verifySolverSettingsInvalidateCatalogSignature(Sudoku2 sudoku) {
		Options options = Options.getInstance();
		int oldLength = options.getRestrictChainLength();
		try {
			String before = TechniqueStepCatalog.createSignature(sudoku);
			options.setRestrictChainLength(oldLength + 1);
			String after = TechniqueStepCatalog.createSignature(sudoku);
			require(!before.equals(after), "solver setting change did not invalidate raw-step cache");
		} finally {
			options.setRestrictChainLength(oldLength);
		}
	}

	private static void verifyPendingPopupNeverBecomesBlank(MainFrame frame) throws Exception {
		Method populate = MainFrame.class.getDeclaredMethod("populateTechniqueSelector",
				JPopupMenu.class, List.class, ResourceBundle.class);
		populate.setAccessible(true);
		ResourceBundle bundle = ResourceBundle.getBundle("intl/MainFrame");
		for (int attempt = 0; attempt < 2; attempt++) {
			PopupLayoutProbe popup = new PopupLayoutProbe();
			populate.invoke(frame, popup, new ArrayList<SolutionType>(), bundle);
			require(popup.getComponentCount() == 1,
					"reopened technique selector became blank while a scan was pending");
			Dimension pendingSize = popup.getComponent(0).getPreferredSize();
			require(pendingSize.width >= 500 && pendingSize.height >= 180,
					"pending selector can still collapse into a loading-row strip " + pendingSize);
			require(popup.resizeRequested,
					"visible technique selector was not resized after asynchronous content replacement");
		}
	}

	private static void verifyMatchMarkers() throws Exception {
		Method level = MainFrame.class.getDeclaredMethod("getTechniqueMatchLevel", double.class);
		level.setAccessible(true);
		require(Integer.valueOf(0).equals(level.invoke(null, Double.valueOf(0.0))),
				"zero match score did not remain unmarked");
		require(Integer.valueOf(1).equals(level.invoke(null, Double.valueOf(1.0)))
				&& Integer.valueOf(2).equals(level.invoke(null, Double.valueOf(3.0)))
				&& Integer.valueOf(3).equals(level.invoke(null, Double.valueOf(8.0))),
				"match score thresholds no longer map to zero through three marks");
		Method marker = MainFrame.class.getDeclaredMethod("createTechniqueMatchMarker",
				int.class, boolean.class);
		marker.setAccessible(true);
		require("\u2022\u2022".equals(marker.invoke(null, Integer.valueOf(2), Boolean.FALSE)),
				"ordinary two-level match did not use two dots");
		require("\u25c6\u25c6".equals(marker.invoke(null, Integer.valueOf(2), Boolean.TRUE)),
				"highest two-level match did not use a distinct tied-best shape");
	}

	private static final class PopupLayoutProbe extends JPopupMenu {
		private static final long serialVersionUID = 1L;
		private boolean resizeRequested;

		@Override public boolean isVisible() { return true; }

		@Override public void setPopupSize(Dimension size) { resizeRequested = true; }

		@Override public void pack() {
			throw new AssertionError("visible technique selector must not be repacked");
		}
	}

	private static void verifyCooperativeCancellationPreservesInterrupt(Sudoku2 sudoku) throws Exception {
		final Throwable[] failure = new Throwable[1];
		Thread worker = new Thread(new Runnable() {
			@Override public void run() {
				solver.SudokuSolver isolated = solver.SudokuSolverFactory.getInstance();
				try {
					List<SolutionStep> steps = new ArrayList<SolutionStep>();
					FindAllSteps finder = new FindAllSteps(steps, sudoku, null,
							isolated.getStepFinder());
					finder.setTestType(new ArrayList<SolutionType>());
					Thread.currentThread().interrupt();
					finder.run();
					require(Thread.currentThread().isInterrupted(),
							"FindAllSteps cleared the cooperative cancellation flag");
					require(steps.isEmpty(), "cancelled scan unexpectedly produced steps");
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					solver.SudokuSolverFactory.giveBack(isolated);
				}
			}
		}, "technique-cancellation-probe");
		worker.start();
		worker.join(3000);
		require(!worker.isAlive(), "cancelled scan did not return promptly at a phase boundary");
		if (failure[0] != null) throw new AssertionError(failure[0]);
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
