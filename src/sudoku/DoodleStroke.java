/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.util.List;

/** Freehand points use panel fractions; candidate-bound points use cell-size offsets from the native candidate center. */
public final class DoodleStroke {
	public static final int MARK_NONE = 0;
	public static final int MARK_TRUE_CIRCLE = 1;
	public static final int MARK_FALSE_CROSS = 2;
	private Color color;
    private int anchorCell = -1;
    private int anchorDigit;
	private int candidateMarkKind;
	private int thoughtGroup = -1;
	private boolean conclusionOutlined;
    private boolean hypothesisStart;
    private int hypothesisConditionId;
    private int hypothesisGroupKind;
    private int conclusionSourceMask;
	/** Captured source ink is read-only evidence, never a current branch mark. */
	private boolean proofOnly;
	/** Puzzle-local link between an adopted start set and its captured source ink. */
	private int derivedSourceId;
    /** Presentation metadata in replay snapshots; never an authored premise. */
    private boolean contradictionWitness;
    private boolean refutationResult;
	private float widthFactor;
	private List<DoodlePoint> points = new DoodlePoints();

	public DoodleStroke() {
	}

	public DoodleStroke(Color color, float widthFactor) {
		this.color = color;
		this.widthFactor = widthFactor;
	}

    public int getAnchorCell() { return anchorCell; }
    public void setAnchorCell(int value) { anchorCell = value; }
    public int getAnchorDigit() { return anchorDigit; }
    public void setAnchorDigit(int value) { anchorDigit = value; }
    public boolean isCandidateAnchored() { return anchorCell >= 0 && anchorCell < 81 && anchorDigit >= 1 && anchorDigit <= 9; }
	/** Old candidate-anchored doodle circles predate the explicit mark kind. */
	public int getCandidateMarkKind() {
		return candidateMarkKind == MARK_NONE && isCandidateAnchored() ? MARK_TRUE_CIRCLE : candidateMarkKind;
	}
	public void setCandidateMarkKind(int value) {
		if (value < MARK_NONE || value > MARK_FALSE_CROSS) throw new IllegalArgumentException("Invalid candidate mark kind");
		candidateMarkKind = value;
	}
	public boolean isStandardCandidateMark() {
		return isCandidateAnchored() && getCandidateMarkKind() != MARK_NONE;
	}
	public int getThoughtGroup() { return thoughtGroup; }
	public void setThoughtGroup(int value) {
		if (value < -1 || value > 5) throw new IllegalArgumentException("Invalid candidate thought group");
		thoughtGroup = value;
	}
	/** A high-contrast frame that explicitly arms this candidate conclusion. */
	public boolean isConclusionOutlined() { return conclusionOutlined; }
	public void setConclusionOutlined(boolean value) { conclusionOutlined = value; }

    public boolean isHypothesisStart() { return hypothesisStart; }
    public void setHypothesisStart(boolean value) { hypothesisStart = value; }
    public int getHypothesisConditionId() { return hypothesisConditionId; }
    public void setHypothesisConditionId(int value) { hypothesisConditionId = Math.max(0, value); }
    /** 0: individual premise; 1: group at least one true; 2: group all false. */
    public int getHypothesisGroupKind() { return hypothesisGroupKind; }
    public void setHypothesisGroupKind(int value) { hypothesisGroupKind = value >= 0 && value <= 2 ? value : 0; }
    public int getConclusionSourceMask() { return conclusionSourceMask; }
    public void setConclusionSourceMask(int value) { conclusionSourceMask = value & 63; }
	public boolean isProofOnly() { return proofOnly; }
	public void setProofOnly(boolean value) { proofOnly = value; }
	public int getDerivedSourceId() { return derivedSourceId; }
	public void setDerivedSourceId(int value) { derivedSourceId = Math.max(0, value); }
    public boolean isContradictionWitness() { return contradictionWitness; }
    public void setContradictionWitness(boolean value) { contradictionWitness = value; }
    public boolean isRefutationResult() { return refutationResult; }
    public void setRefutationResult(boolean value) { refutationResult = value; }

	public Color getColor() {
		return color;
	}

	public void setColor(Color color) {
		this.color = color;
	}

	public float getWidthFactor() {
		return widthFactor;
	}

	public void setWidthFactor(float widthFactor) {
		this.widthFactor = widthFactor;
	}

	public List<DoodlePoint> getPoints() {
		return points;
	}

	public void setPoints(List<DoodlePoint> points) {
		this.points = points == null ? new DoodlePoints() : points;
	}

	/** Primitive access avoids allocating compatibility point objects during paint and encoding. */
	public int pointCount() { return points.size(); }
	public double pointX(int index) { return points instanceof DoodlePoints ? ((DoodlePoints)points).x(index) : points.get(index).getX(); }
	public double pointY(int index) { return points instanceof DoodlePoints ? ((DoodlePoints)points).y(index) : points.get(index).getY(); }
	public void addPoint(double x, double y) {
		if (points instanceof DoodlePoints) ((DoodlePoints)points).append(x, y);
		else points.add(new DoodlePoint(x, y));
	}
	public void setPoint(int index, double x, double y) {
		if (points instanceof DoodlePoints) ((DoodlePoints)points).setCoordinates(index, x, y);
		else { DoodlePoint point = points.get(index); point.setX(x); point.setY(y); }
	}
	public DoodleCoordinates coordinateSnapshot() {
		if (points instanceof DoodlePoints) return ((DoodlePoints)points).snapshot();
		// Caller-supplied mutable lists keep their legacy alias semantics, so cannot be shared safely.
		double[] values = new double[points.size() * 2];
		for (int i = 0; i < points.size(); i++) { values[i * 2] = pointX(i); values[i * 2 + 1] = pointY(i); }
		return new DoodleCoordinates(values, points.size());
	}
	public void replaceCoordinates(DoodleCoordinates coordinates) {
		if (coordinates == null) throw new NullPointerException("coordinates");
		points = new DoodlePoints(coordinates);
	}

	public DoodleStroke copy() {
		DoodleStroke copy = new DoodleStroke(color, widthFactor);
        copy.anchorCell = anchorCell; copy.anchorDigit = anchorDigit;
		copy.candidateMarkKind = getCandidateMarkKind();
		copy.thoughtGroup = thoughtGroup;
		copy.conclusionOutlined = conclusionOutlined;
        copy.hypothesisStart = hypothesisStart;
        copy.hypothesisConditionId = hypothesisConditionId;
        copy.hypothesisGroupKind = hypothesisGroupKind;
        copy.conclusionSourceMask = conclusionSourceMask;
		copy.proofOnly = proofOnly;
		copy.derivedSourceId = derivedSourceId;
        copy.contradictionWitness = contradictionWitness;
        copy.refutationResult = refutationResult;
		copy.replaceCoordinates(coordinateSnapshot());
		return copy;
	}
}
