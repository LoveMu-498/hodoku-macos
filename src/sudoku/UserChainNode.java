/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Color;

/** One user-selected candidate node in an annotation chain. */
public class UserChainNode {
	private int cellIndex;
	private int candidate;
	private Color color;
	private int[] groupCells;

	public UserChainNode() {
	}

	public UserChainNode(int cellIndex, int candidate) {
		this(cellIndex, candidate, null);
	}

	public UserChainNode(int cellIndex, int candidate, Color color) {
		this.cellIndex = cellIndex;
		this.candidate = candidate;
		this.color = color;
	}

    /** Null in old JavaBean sessions: the legacy cellIndex remains a single node. */
    public int[] getGroupCells() { return groupCells == null ? null : groupCells.clone(); }
    public void setGroupCells(int[] value) { groupCells = value == null ? null : value.clone(); }
    public int[] cells() {
        int[] result = groupCells == null ? new int[] {cellIndex} : groupCells.clone();
        java.util.Arrays.sort(result); return result;
    }
    public boolean grouped() { return cells().length > 1; }
    public boolean contains(int cell, int digit) {
        if (candidate != digit) return false;
        for (int c : cells()) if (c == cell) return true;
        return false;
    }
    public boolean validShape() {
        int[] c = cells();
        if (candidate < 1 || candidate > 9 || c.length < 1 || c.length > 3) return false;
        boolean row = true, col = true;
        for (int i=0;i<c.length;i++) {
            if (c[i]<0 || c[i]>=81 || (i>0 && c[i]==c[i-1])) return false;
            if (Sudoku2.getBlock(c[i])!=Sudoku2.getBlock(c[0])) return false;
            row &= Sudoku2.getRow(c[i])==Sudoku2.getRow(c[0]);
            col &= Sudoku2.getCol(c[i])==Sudoku2.getCol(c[0]);
        }
        return row || col;
    }
    public int encoded(boolean strong) {
        int[] c=cells();
        return c.length==1 ? Chain.makeSEntry(c[0],candidate,strong)
            : Chain.makeSEntry(c[0],c[1],c.length==3?c[2]:-1,candidate,strong,Chain.GROUP_NODE);
    }
    public int identity() { return grouped() ? encoded(false) : cellIndex*10+candidate; }
    public UserChainNode copy() {
        UserChainNode n=new UserChainNode(cellIndex,candidate,color);n.setGroupCells(groupCells);return n;
    }

	public int getCellIndex() { return cellIndex; }
	public void setCellIndex(int cellIndex) { this.cellIndex = cellIndex; }
	public int getCandidate() { return candidate; }
	public void setCandidate(int candidate) { this.candidate = candidate; }
	public Color getColor() { return color; }
	public void setColor(Color color) { this.color = color; }
}
