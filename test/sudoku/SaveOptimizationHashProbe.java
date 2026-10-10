/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.lang.reflect.Method;

/** Canonical verification follows native membership while every actual value remains significant. */
public final class SaveOptimizationHashProbe {
    private static final Method HASH;
    static {
        try { HASH = SaveOptimizationLifecycleProbe.class.getDeclaredMethod("hash", Object.class); HASH.setAccessible(true); }
        catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    private static String hash(Object value) throws Exception { return (String) HASH.invoke(null, value); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        SudokuSet empty = new SudokuSet(); empty.clear(); String emptyHash = hash(empty);
        require(!empty.isInitialized(), "hash warmed the source iteration cache");
        empty.getValues(); require(emptyHash.equals(hash(empty)), "cache warmup changed empty-set hash");
        SudokuSet member = new SudokuSet(); member.add(0); member.add(80); String memberHash = hash(member);
        require(!member.isInitialized(), "hash warmed a populated source cache");
        member.getValues(); require(memberHash.equals(hash(member)), "cache warmup changed populated-set hash");
        member.add(17); require(!memberHash.equals(hash(member)), "low membership bit not validated");
        member.remove(17); member.remove(80); require(!memberHash.equals(hash(member)), "high membership bit not validated");
        SudokuSetShort candidates = new SudokuSetShort(); candidates.add(1); candidates.add(9); String shortHash = hash(candidates);
        candidates.getValues(); require(shortHash.equals(hash(candidates)), "short cache changed hash");
        candidates.remove(9); require(!shortHash.equals(hash(candidates)), "candidate membership not validated");
        DoodleStroke stroke = new DoodleStroke(java.awt.Color.BLUE, 1); stroke.getPoints().add(new DoodlePoint(0.0, 0.5));
        String ink = hash(stroke); stroke.getPoints().get(0).setX(-0.0); require(!ink.equals(hash(stroke)), "raw coordinate bits not validated");
        stroke.getPoints().get(0).setX(0.0); stroke.setProofOnly(true); require(!ink.equals(hash(stroke)), "ink metadata not validated");
        GuiState saved = new GuiState(); saved.setName("first"); saved.setDoodleStrokes(java.util.Collections.singletonList(stroke));
        String savedHash = hash(saved); saved.setName("second"); require(!savedHash.equals(hash(saved)), "savepoint name not validated");
        System.out.println("PASS canonical hash: no source mutation, cache-independent native membership, real cell/candidate bits and ink/savepoint fields remain strict");
    }
}
