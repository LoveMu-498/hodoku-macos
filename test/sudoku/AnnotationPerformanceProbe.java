/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.SortedMap;
import javax.swing.JComponent;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/** Guards the two annotation actions that must remain immediate on the EDT. */
public final class AnnotationPerformanceProbe {
	private static final int TOOL_SWITCH_DIRTY_REGION_LIMIT = 2_400;
	private static final int TOOLBAR_DIRTY_REGION_LIMIT = 1_200;
	private static final int SIDEBAR_DIRTY_REGION_LIMIT = 1_200;
	private static final int SHORTCUT_DIRTY_REGION_LIMIT = 1_200;
	private static final int CLEAR_DIRTY_REGION_LIMIT = 12;

	private AnnotationPerformanceProbe() {
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
					panel.setSize(900, 900);
					AnnotationTool[] tools = AnnotationTool.values();
					for (int i = 0; i < 18; i++) {
						panel.setAnnotationTool(tools[i % tools.length]);
					}
					CountingRepaintManager repaints = new CountingRepaintManager();
					RepaintManager previousRepaints = RepaintManager.currentManager(panel);
					RepaintManager.setCurrentManager(repaints);

					long switchStarted = System.nanoTime();
					for (int i = 0; i < 120; i++) {
						panel.setAnnotationTool(tools[i % tools.length]);
					}
					long switchMillis = elapsedMillis(switchStarted);
					int switchDirtyRegions = repaints.dirtyRegions;

					repaints.dirtyRegions = 0;
					long toolbarStarted = System.nanoTime();
					exerciseToolbarEntryPath(frame, panel);
					long toolbarMillis = elapsedMillis(toolbarStarted);
					int toolbarDirtyRegions = repaints.dirtyRegions;

					repaints.dirtyRegions = 0;
					long sidebarStarted = System.nanoTime();
					exerciseSidebarEntryPath(panel);
					long sidebarMillis = elapsedMillis(sidebarStarted);
					int sidebarDirtyRegions = repaints.dirtyRegions;

					repaints.dirtyRegions = 0;
					long shortcutsStarted = System.nanoTime();
					exerciseShortcutEntryPath(panel);
					long shortcutsMillis = elapsedMillis(shortcutsStarted);
					int shortcutDirtyRegions = repaints.dirtyRegions;

					populateAnnotations(panel);
					repaints.dirtyRegions = 0;
					long clearStarted = System.nanoTime();
					press(panel, KeyEvent.VK_R, System.currentTimeMillis());
					long clearMillis = elapsedMillis(clearStarted);
					int clearDirtyRegions = repaints.dirtyRegions;
					RepaintManager.setCurrentManager(previousRepaints);
					require(!panel.hasColoring() && panel.getDoodleStrokeCount() == 0
							&& panel.getUserChainCount() == 0
							&& panel.getBoxReasoningFootprint().isEmpty(),
							"R-style annotation clear left an annotation layer behind");
					require(switchDirtyRegions <= TOOL_SWITCH_DIRTY_REGION_LIMIT,
							"120 annotation tool switches scheduled " + switchDirtyRegions
									+ " Swing repaints");
					require(toolbarDirtyRegions <= TOOLBAR_DIRTY_REGION_LIMIT,
							"toolbar tool entry scheduled " + toolbarDirtyRegions + " Swing repaints");
					require(sidebarDirtyRegions <= SIDEBAR_DIRTY_REGION_LIMIT,
							"sidebar tool entry scheduled " + sidebarDirtyRegions + " Swing repaints");
					require(shortcutDirtyRegions <= SHORTCUT_DIRTY_REGION_LIMIT,
							"shortcut tool entry scheduled " + shortcutDirtyRegions + " Swing repaints");
					require(clearDirtyRegions <= CLEAR_DIRTY_REGION_LIMIT,
							"R key annotation clear scheduled " + clearDirtyRegions + " Swing repaints");
					System.out.println("Annotation performance: switches=" + switchMillis
							+ " ms/" + switchDirtyRegions + " dirty regions, toolbar="
							+ toolbarMillis + " ms/" + toolbarDirtyRegions + " dirty regions, sidebar="
							+ sidebarMillis + " ms/" + sidebarDirtyRegions + " dirty regions, shortcuts="
							+ shortcutsMillis + " ms/" + shortcutDirtyRegions + " dirty regions, clear="
							+ clearMillis + " ms/" + clearDirtyRegions + " dirty regions");
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
		System.exit(0);
	}

	private static void exerciseToolbarEntryPath(MainFrame frame, SudokuPanel panel) throws Exception {
		JToggleButton[] toolbarButtons = (JToggleButton[]) readField(frame, "annotationToolButtons");
		AnnotationTool[] tools = AnnotationTool.values();
		for (int i = 0; i < 24; i++) {
			AnnotationTool target = tools[i % tools.length];
			toolbarButtons[target.ordinal()].doClick(0);
			require(panel.getAnnotationTool() == target,
					"toolbar did not select " + target);
		}
	}

	private static void exerciseSidebarEntryPath(SudokuPanel panel) throws Exception {
		String[] sidebarFields = { "radioButtonDefault", "radioButtonColorCandidates",
				"radioButtonColorCells", "radioButtonDoodle", "radioButtonFreeChain",
				"radioButtonBoxSelection" };
		for (int i = 0; i < 24; i++) {
			JRadioButton button = (JRadioButton) readField(panel.getCellZoomPanel(),
					sidebarFields[i % sidebarFields.length]);
			button.doClick(0);
		}
	}

	private static void exerciseShortcutEntryPath(SudokuPanel panel) {
		int[] shortcutKeys = { KeyEvent.VK_M, KeyEvent.VK_P, KeyEvent.VK_L,
				KeyEvent.VK_S, KeyEvent.VK_T };
		long when = System.currentTimeMillis() + 1_000L;
		for (int i = 0; i < 25; i++) {
			press(panel, shortcutKeys[i % shortcutKeys.length], when);
			when += 500L;
		}
	}

	private static void press(SudokuPanel panel, int keyCode, long when) {
		KeyEvent pressed = new KeyEvent(panel, KeyEvent.KEY_PRESSED, when, 0,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener listener : panel.getKeyListeners()) {
			listener.keyPressed(pressed);
		}
		KeyEvent released = new KeyEvent(panel, KeyEvent.KEY_RELEASED, when + 20L, 0,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener listener : panel.getKeyListeners()) {
			listener.keyReleased(released);
		}
	}

	@SuppressWarnings("unchecked")
	private static void populateAnnotations(SudokuPanel panel) throws Exception {
		SortedMap<Integer, Color> cellColors = (SortedMap<Integer, Color>) readField(panel, "coloringMap");
		SortedMap<Integer, Color> candidateColors =
				(SortedMap<Integer, Color>) readField(panel, "coloringCandidateMap");
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			cellColors.put(index, Color.BLUE);
			for (int candidate = 1; candidate <= Sudoku2.UNITS; candidate++) {
				candidateColors.put(index * 10 + candidate, Color.GREEN);
			}
		}

		List<DoodleStroke> doodles = (List<DoodleStroke>) readField(panel, "doodleStrokes");
		for (int strokeIndex = 0; strokeIndex < 300; strokeIndex++) {
			DoodleStroke stroke = new DoodleStroke(Color.BLUE, 0.005f);
			for (int point = 0; point < 100; point++) {
				stroke.getPoints().add(new DoodlePoint(point / 100.0, strokeIndex / 300.0));
			}
			doodles.add(stroke);
		}

		List<UserChain> chains = (List<UserChain>) readField(panel, "userChains");
		for (int chainIndex = 0; chainIndex < 300; chainIndex++) {
			UserChain chain = new UserChain();
			for (int node = 0; node < 18; node++) {
				chain.getNodes().add(new UserChainNode(node % Sudoku2.LENGTH, node % Sudoku2.UNITS + 1));
				if (node > 0) {
					chain.getStrongRelations().add(Boolean.valueOf(node % 2 == 0));
					chain.getRelationColors().add(Color.BLUE);
				}
			}
			chains.add(chain);
		}

		List<SudokuSet> groups = (List<SudokuSet>) readField(panel, "boxReasoningGroups");
		for (SudokuSet group : groups) {
			for (int index = 0; index < Sudoku2.LENGTH; index++) group.add(index);
		}
	}

	private static Object readField(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static long elapsedMillis(long started) {
		return Math.round((System.nanoTime() - started) / 1_000_000.0);
	}

	private static final class CountingRepaintManager extends RepaintManager {
		private int dirtyRegions;

		@Override
		public synchronized void addDirtyRegion(JComponent component, int x, int y, int width, int height) {
			dirtyRegions++;
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
