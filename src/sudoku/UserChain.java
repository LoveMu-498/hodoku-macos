/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** A persisted free-chain diagram. Each relation belongs to the later node. */
public class UserChain {
	private List<UserChainNode> nodes = new ArrayList<UserChainNode>();
	private List<Boolean> strongRelations = new ArrayList<Boolean>();
	private List<Color> relationColors = new ArrayList<Color>();
	private boolean closed;
	/** Snapshot marker used only inside the shared chain undo/redo history. */
	private boolean active;
	private String analysisResult;
	private boolean nextStrong = true;
	/** Stable puzzle-local identity of an explicitly Enter-confirmed chain. */
	private long sourceId;

	public UserChain() {
	}

	public List<UserChainNode> getNodes() { return nodes; }
	public void setNodes(List<UserChainNode> nodes) { this.nodes = nodes; }
	public List<Boolean> getStrongRelations() { return strongRelations; }
	public void setStrongRelations(List<Boolean> strongRelations) { this.strongRelations = strongRelations; }
	public List<Color> getRelationColors() { return relationColors; }
	public void setRelationColors(List<Color> relationColors) { this.relationColors = relationColors; }
	public boolean isClosed() { return closed; }
	public void setClosed(boolean closed) { this.closed = closed; }
	public boolean isActive() { return active; }
	public void setActive(boolean active) { this.active = active; }
	public String getAnalysisResult() { return analysisResult; }
	public void setAnalysisResult(String analysisResult) { this.analysisResult = analysisResult; }
	public boolean isNextStrong() { return nextStrong; }
	public void setNextStrong(boolean nextStrong) { this.nextStrong = nextStrong; }
	public long getSourceId() { return sourceId; }
	public void setSourceId(long sourceId) { this.sourceId = sourceId; }
}
