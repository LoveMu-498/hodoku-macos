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
    private int[] memberCandidates;

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
    /** Full candidate atoms, cell * 10 + digit. Null preserves legacy bean sessions. */
    public int[] getMemberCandidates() { return memberCandidates == null ? null : memberCandidates.clone(); }
    public void setMemberCandidates(int[] value) { memberCandidates=value==null?null:value.clone(); }
    public int[] atoms() {
        int[] result;
        if(memberCandidates!=null) result=memberCandidates.clone();
        else { int[] c=groupCells==null?new int[]{cellIndex}:groupCells;result=new int[c.length];
            for(int i=0;i<c.length;i++)result[i]=c[i]*10+candidate; }
        java.util.Arrays.sort(result);return result;
    }
    public static UserChainNode fromAtoms(int[] atoms, Color color) {
        if(atoms.length==0)throw new IllegalArgumentException("Empty candidate group");
        int[] sorted=atoms.clone();java.util.Arrays.sort(sorted);
        UserChainNode node=new UserChainNode(sorted[0]/10,sorted[0]%10,color);
        node.setMemberCandidates(sorted);return node;
    }
    /** Position projection only; use atoms() whenever candidate identity matters. */
    public int[] cells() { return java.util.Arrays.stream(atoms()).map(a->a/10).distinct().toArray(); }
    public boolean grouped() { return atoms().length > 1; }
    public boolean contains(int cell, int digit) { return java.util.Arrays.binarySearch(atoms(),cell*10+digit)>=0; }
    public int sameDigit() { int[] a=atoms();if(a.length==0)return 0;int d=a[0]%10;for(int x:a)if(x%10!=d)return 0;return d; }
    public boolean validShape() {
        int[] a=atoms();if(a.length==0 || a.length>729)return false;
        for(int i=0;i<a.length;i++)if(a[i]<1||a[i]/10>80||a[i]%10<1||a[i]%10>9||(i>0&&a[i]==a[i-1]))return false;
        return true;
    }
    public boolean nativeEncodable() {return validShape() && sameDigit()!=0 && atoms().length<=3;}
    public int encoded(boolean strong) {
        if(!nativeEncodable())throw new IllegalStateException("Generalized group requires full candidate representation");
        int[] c=cells();int d=sameDigit();
        return c.length==1?Chain.makeSEntry(c[0],d,strong):Chain.makeSEntry(c[0],c[1],c.length==3?c[2]:-1,d,strong,Chain.GROUP_NODE);
    }
    public String key() { return java.util.Arrays.toString(atoms()); }
    /** Legacy native identity, never a generalized group key. */
    public int identity() { return grouped()?encoded(false):atoms()[0]; }
    public UserChainNode copy() {
        UserChainNode n=new UserChainNode(cellIndex,candidate,color);n.setGroupCells(groupCells);n.setMemberCandidates(memberCandidates);return n;
    }

	public int getCellIndex() { return cellIndex; }
	public void setCellIndex(int cellIndex) { this.cellIndex = cellIndex; }
	public int getCandidate() { return candidate; }
	public void setCandidate(int candidate) { this.candidate = candidate; }
	public Color getColor() { return color; }
	public void setColor(Color color) { this.color = color; }
}
