/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.TreeMap;

/** Focused persistence checks for the app-managed last work session. */
public final class SessionStoreProbe {

	private static final String PUZZLE =
			"530070000600195000098000060800060003400803001700020006060000280000419005000080079";

	private SessionStoreProbe() {
	}

	public static void main(String[] args) throws Exception {
		File directory = Files.createTempDirectory("hodoku-session-probe").toFile();
		File sessionFile = new File(directory, "last-session.xml");
		SessionStore store = new SessionStore(sessionFile);

		SessionSnapshot first = snapshotWithValue(4, Color.CYAN);
		store.save(first);
		SessionSnapshot loaded = store.load();
		assertSnapshot(loaded, 4, Color.CYAN);

		SessionSnapshot second = snapshotWithValue(8, Color.PINK);
		store.save(second);
		assertSnapshot(store.load(), 8, Color.PINK);

		try (FileOutputStream out = new FileOutputStream(sessionFile)) {
			out.write("broken".getBytes("UTF-8"));
		}
		assertSnapshot(store.load(), 4, Color.CYAN);
		store.save(second);
		try (FileOutputStream out = new FileOutputStream(sessionFile)) {
			out.write("broken-again".getBytes("UTF-8"));
		}
		assertSnapshot(store.load(), 4, Color.CYAN);

		store.clear();
		require(!store.exists(), "session file was not cleared");

		store.save(first);
		store.save(second);
		require(sessionFile.delete(), "main session was not removed for backup fallback check");
		assertSnapshot(store.load(), 4, Color.CYAN);
		store.clear();
		System.out.println("Session persistence checks passed");
	}

	private static SessionSnapshot snapshotWithValue(int value, Color color) {
		Sudoku2 sudoku = new Sudoku2();
		sudoku.setSudoku(PUZZLE);
		sudoku.setCell(0, 2, value);

		GuiState state = new GuiState();
		state.setSudoku(sudoku);
		Stack<Sudoku2> undo = new Stack<Sudoku2>();
		undo.push(sudoku.clone());
		state.setUndoStack(undo);
		state.setRedoStack(new Stack<Sudoku2>());
		TreeMap<Integer, Color> colors = new TreeMap<Integer, Color>();
		colors.put(Integer.valueOf(10), color);
		state.setColoringMap(colors);
		state.setColoringCandidateMap(new TreeMap<Integer, Color>());

		List<GuiState> savePoints = new ArrayList<GuiState>();
		GuiState savePoint = new GuiState();
		savePoint.setSudoku(sudoku.clone());
		savePoints.add(savePoint);

		SessionSnapshot snapshot = new SessionSnapshot();
		snapshot.setGuiState(state);
		snapshot.setSavePoints(savePoints);
		snapshot.setActiveRow(3);
		snapshot.setActiveCol(7);
		snapshot.setCompleted(false);
		snapshot.setHistoryEligible(true);
		snapshot.setCompletionRecorded(true);
		snapshot.setPreviouslySolved(false);
		snapshot.setPrimaryColor(color);
		snapshot.setSecondaryColor(Color.ORANGE);
		snapshot.setColoringMode(2);
		return snapshot;
	}

	private static void assertSnapshot(SessionSnapshot snapshot, int value, Color color) {
		require(snapshot != null, "session was not loaded");
		require(snapshot.getGuiState().getSudoku().getValue(2) == value, "grid value was not restored");
		require(snapshot.getGuiState().getUndoStack().size() == 1, "undo stack was not restored");
		require(color.equals(snapshot.getGuiState().getColoringMap().get(Integer.valueOf(10))),
				"cell coloring was not restored");
		require(snapshot.getSavePoints().size() == 1, "save points were not restored");
		require(snapshot.getActiveRow() == 3 && snapshot.getActiveCol() == 7,
				"active cell was not restored");
		require(!snapshot.isCompleted(), "completion flag changed");
		require(snapshot.isHistoryEligible(), "history eligibility was not restored");
		require(snapshot.isCompletionRecorded() && !snapshot.isPreviouslySolved(),
				"completion transition state was not restored");
		require(color.equals(snapshot.getPrimaryColor()) && Color.ORANGE.equals(snapshot.getSecondaryColor()),
				"palette colors were not restored");
		require(snapshot.getColoringMode() == 2, "coloring mode was not restored");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
