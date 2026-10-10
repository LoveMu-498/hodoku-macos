/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Candidate-mark lookup for one synchronous paint or reconciliation pass.
 * Raw ink supplies source masks; visible ink supplies the color segments.
 * The index retains stroke references, not copied geometry, and must not be
 * kept after the pass or reused after a mark's identity changes.
 */
final class DoodleFrameIndex {
    static final int KEY_COUNT = 81 * 9 * 2;
    private final Target[] targets = new Target[KEY_COUNT];
    private final boolean unassignedColors;

    private static final class Target {
        int sourceMask;
        SortedMap<Long, DoodleStroke> byGroup;
        SortedMap<Long, DoodleStroke> byRole;
        List<DoodleStroke> visibleSources;
        List<DoodleStroke> visibleRoleMarks;
    }

    DoodleFrameIndex(List<DoodleStroke> rawInk, List<DoodleStroke> visibleInk) {
        this(rawInk,visibleInk,false);
    }
    /** Replay-only visual slots: distinct ARGB does not establish a logical group. */
    DoodleFrameIndex(List<DoodleStroke> rawInk, List<DoodleStroke> visibleInk, boolean unassignedColors) {
        this.unassignedColors=unassignedColors;
        boolean sameInk = rawInk == visibleInk;
        for (DoodleStroke stroke : rawInk) {
            if (stroke.isProofOnly()) continue;
            int key = candidateKey(stroke);
            if (key < 0) continue;
            Target target = target(key);
            // Legacy unassigned ink participates in composition, not sources.
            if (!stroke.isRefutationResult() && stroke.getThoughtGroup() >= 0)
                target.sourceMask |= 1 << stroke.getThoughtGroup();
            if (sameInk) addVisible(target, stroke);
        }
        if (!sameInk) {
            for (DoodleStroke stroke : visibleInk) {
                if (stroke.isProofOnly()) continue;
                int key = candidateKey(stroke);
                if (key >= 0) addVisible(target(key), stroke);
            }
        }
    }

    private Target target(int key) {
        Target target = targets[key];
        if (target == null) targets[key] = target = new Target();
        return target;
    }

    private void addVisible(Target target, DoodleStroke stroke) {
        if (target.byGroup == null) target.byGroup = new TreeMap<Long, DoodleStroke>();
        // Preserve the old scan: group order is sorted and its last mark wins.
        long slot=stroke.getThoughtGroup();
        if(unassignedColors&&slot<0)slot=(1L<<32)+Integer.toUnsignedLong(stroke.getColor().getRGB());
        target.byGroup.put(Long.valueOf(slot), stroke);
        if (target.byRole == null) target.byRole = new TreeMap<Long, DoodleStroke>();
        // One glyph/color source can carry both an OR-start role and an
        // independent atomic conclusion. Preserve the last mark within each role.
        target.byRole.put(Long.valueOf(slot * 2 + (stroke.isHypothesisStart() ? 1 : 0)), stroke);
    }

    static int candidateKey(DoodleStroke stroke) {
        return stroke.isStandardCandidateMark()
                ? candidateKey(stroke.getAnchorCell(), stroke.getAnchorDigit(), stroke.getCandidateMarkKind()) : -1;
    }

    private static int candidateKey(int cell, int digit, int kind) {
        if (cell < 0 || cell >= 81 || digit < 1 || digit > 9
                || kind < DoodleStroke.MARK_TRUE_CIRCLE || kind > DoodleStroke.MARK_FALSE_CROSS) return -1;
        return (cell * 9 + digit - 1) * 2 + kind - DoodleStroke.MARK_TRUE_CIRCLE;
    }

    int sourceMask(int cell, int digit, int kind) {
        int key = candidateKey(cell, digit, kind);
        return key < 0 || targets[key] == null ? 0 : targets[key].sourceMask;
    }

    int sourceMask(DoodleStroke stroke) {
        return sourceMask(stroke.getAnchorCell(), stroke.getAnchorDigit(), stroke.getCandidateMarkKind());
    }

    /** Original mark order still determines the base; this only supplies its segments. */
    List<DoodleStroke> visibleSources(DoodleStroke base) {
        int key = candidateKey(base);
        Target target = key < 0 ? null : targets[key];
        if (target == null || target.byGroup == null) return Collections.emptyList();
        if (target.visibleSources == null)
            target.visibleSources = Collections.unmodifiableList(new ArrayList<DoodleStroke>(target.byGroup.values()));
        return target.visibleSources;
    }

    /** Role records for outlines/selection; these are not extra color segments. */
    List<DoodleStroke> visibleRoleMarks(DoodleStroke base) {
        int key = candidateKey(base);
        Target target = key < 0 ? null : targets[key];
        if (target == null || target.byRole == null) return Collections.emptyList();
        if (target.visibleRoleMarks == null)
            target.visibleRoleMarks = Collections.unmodifiableList(new ArrayList<DoodleStroke>(target.byRole.values()));
        return target.visibleRoleMarks;
    }
}
