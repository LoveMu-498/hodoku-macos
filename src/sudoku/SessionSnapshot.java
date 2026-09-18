/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Bean persisted as the app-managed last work session. */
public final class SessionSnapshot {

	private int formatVersion = 1;
	private GuiState guiState;
	private List<GuiState> savePoints = new ArrayList<GuiState>();
	private int activeRow = 4;
	private int activeCol = 4;
	private boolean completed;
	private int completedLevel;
	private String completedGameMode = GameMode.PLAYING.name();
	private boolean historyEligible;
	private boolean completionRecorded;
	private boolean previouslySolved;
	private Color primaryColor;
	private Color secondaryColor;
	private int coloringMode;

	public SessionSnapshot() {
	}

	public int getFormatVersion() {
		return formatVersion;
	}

	public void setFormatVersion(int formatVersion) {
		this.formatVersion = formatVersion;
	}

	public GuiState getGuiState() {
		return guiState;
	}

	public void setGuiState(GuiState guiState) {
		this.guiState = guiState;
	}

	public List<GuiState> getSavePoints() {
		return savePoints;
	}

	public void setSavePoints(List<GuiState> savePoints) {
		this.savePoints = savePoints == null ? new ArrayList<GuiState>() : savePoints;
	}

	public int getActiveRow() {
		return activeRow;
	}

	public void setActiveRow(int activeRow) {
		this.activeRow = activeRow;
	}

	public int getActiveCol() {
		return activeCol;
	}

	public void setActiveCol(int activeCol) {
		this.activeCol = activeCol;
	}

	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	public int getCompletedLevel() {
		return completedLevel;
	}

	public void setCompletedLevel(int completedLevel) {
		this.completedLevel = completedLevel;
	}

	public String getCompletedGameMode() {
		return completedGameMode;
	}

	public void setCompletedGameMode(String completedGameMode) {
		this.completedGameMode = completedGameMode == null ? GameMode.PLAYING.name() : completedGameMode;
	}

	public boolean isHistoryEligible() {
		return historyEligible;
	}

	public void setHistoryEligible(boolean historyEligible) {
		this.historyEligible = historyEligible;
	}

	public boolean isCompletionRecorded() {
		return completionRecorded;
	}

	public void setCompletionRecorded(boolean completionRecorded) {
		this.completionRecorded = completionRecorded;
	}

	public boolean isPreviouslySolved() {
		return previouslySolved;
	}

	public void setPreviouslySolved(boolean previouslySolved) {
		this.previouslySolved = previouslySolved;
	}

	public Color getPrimaryColor() {
		return primaryColor;
	}

	public void setPrimaryColor(Color primaryColor) {
		this.primaryColor = primaryColor;
	}

	public Color getSecondaryColor() {
		return secondaryColor;
	}

	public void setSecondaryColor(Color secondaryColor) {
		this.secondaryColor = secondaryColor;
	}

	/** 0 default, 1 cell coloring, 2 candidate coloring. */
	public int getColoringMode() {
		return coloringMode;
	}

	public void setColoringMode(int coloringMode) {
		this.coloringMode = coloringMode;
	}
}
