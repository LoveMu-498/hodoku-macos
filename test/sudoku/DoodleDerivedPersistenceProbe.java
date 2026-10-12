/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** No window: verifies source snapshots remain isolated across copies and storage. */
public final class DoodleDerivedPersistenceProbe {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static DoodleStroke mark(int group, boolean proofOnly) {
        DoodleStroke mark = new DoodleStroke(Color.BLUE, .003f);
        mark.setAnchorCell(0); mark.setAnchorDigit(1);
        mark.setCandidateMarkKind(DoodleStroke.MARK_FALSE_CROSS);
        mark.setThoughtGroup(group); mark.setHypothesisStart(true);
        mark.setHypothesisConditionId(7); mark.setHypothesisGroupKind(2);
        mark.setProofOnly(proofOnly); mark.setDerivedSourceId(12);
        mark.getPoints().add(new DoodlePoint(.4, .4));
        mark.getPoints().add(new DoodlePoint(.6, .6));
        mark.getPoints().add(new DoodlePoint(.6, .4));
        mark.getPoints().add(new DoodlePoint(.4, .6));
        return mark;
    }

    private static void checkSnapshot(DoodleStroke mark) {
        check(mark.isProofOnly() && mark.getDerivedSourceId() == 12,
                "source metadata lost across copy or session storage");
        check(mark.isStandardCandidateMark() && mark.isHypothesisStart()
                && mark.getHypothesisConditionId() == 7 && mark.getHypothesisGroupKind() == 2,
                "captured source condition or original mark semantics changed");
        check(mark.getPoints().size() == 4 && mark.getColor().equals(Color.BLUE),
                "source geometry or color lost");
    }

    private static void copyAndIndexChecks() {
        DoodleStroke legacy = new DoodleStroke();
        check(!legacy.isProofOnly() && legacy.getDerivedSourceId() == 0,
                "legacy defaults changed");
        DoodleStroke source = mark(1, true), active = mark(0, false);
        DoodleStroke copy = source.copy(); checkSnapshot(copy);
        copy.getPoints().get(0).setX(.9);
        check(source.getPoints().get(0).getX() == .4, "snapshot geometry aliases source");
        copy.setDerivedSourceId(-1);
        check(copy.getDerivedSourceId() == 0 && source.getDerivedSourceId() == 12,
                "source id setter or independent copy failed");
        List<DoodleStroke> ink = Arrays.asList(active, source);
        DoodleFrameIndex same = new DoodleFrameIndex(ink, ink);
        check(same.sourceMask(active) == 1 && same.visibleSources(active).size() == 1,
                "hidden source participated in mark composition");
        DoodleFrameIndex separate = new DoodleFrameIndex(ink, Collections.singletonList(source));
        check(separate.sourceMask(active) == 1 && separate.visibleSources(active).isEmpty(),
                "hidden source entered a distinct visible-ink input");
        List<DoodleStroke> erased = DoodleGeometry.subtract(ink, new Rectangle(0, 0, 100, 100),
                100, 100);
        check(erased.size() == 1, "eraser did not isolate hidden snapshot from ordinary ink");
        checkSnapshot(erased.get(0));
        check(erased.get(0) != source, "eraser retained a mutable source alias");
        System.out.println("PASS: copied metadata, legacy defaults, active source masks, hidden-ink erasure isolation");
    }

    private static void sessionChecks(Path directory) throws Exception {
        DoodleStroke source = mark(1, true), active = mark(1, false);
        active.setCandidateMarkKind(DoodleStroke.MARK_TRUE_CIRCLE);
        GuiState state = new GuiState(); state.setSudoku(new Sudoku2());
        state.setIncludeAnnotations(true);
        state.setDoodleStrokes(new ArrayList<DoodleStroke>(Arrays.asList(source, active)));
        state.setDoodleUndoHistory(new ArrayList<List<DoodleStroke>>(Collections.singletonList(
                new ArrayList<DoodleStroke>(Collections.singletonList(source.copy())))));
        state.setDoodleRedoHistory(new ArrayList<List<DoodleStroke>>(Collections.singletonList(
                new ArrayList<DoodleStroke>(Arrays.asList(source.copy(), active.copy())))));
        SessionSnapshot snapshot = new SessionSnapshot(); snapshot.setGuiState(state);
        SessionStore store = new SessionStore(directory.resolve("derived-session.xml").toFile());
        store.save(snapshot); GuiState restored = store.load().getGuiState();
        check(restored.getDoodleStrokes().size() == 2, "session lost current annotations");
        checkSnapshot(restored.getDoodleStrokes().get(0));
        checkSnapshot(restored.getDoodleUndoHistory().get(0).get(0));
        checkSnapshot(restored.getDoodleRedoHistory().get(0).get(0));
        DoodleStroke adopted = restored.getDoodleStrokes().get(1);
        check(!adopted.isProofOnly() && adopted.getDerivedSourceId() == 12,
                "session lost active circle/source association");
        restored.getDoodleUndoHistory().get(0).get(0).getPoints().get(0).setX(.9);
        check(restored.getDoodleStrokes().get(0).getPoints().get(0).getX() == .4,
                "session current state aliases undo geometry");
        System.out.println("PASS: actual SessionStore save/reopen preserves hidden source, active association, undo and redo snapshots");
    }

    private static void replayChecks() throws Exception {
        List<DoodleStroke> ink = Arrays.asList(mark(0, false), mark(1, true));
        ReplayAnnotations annotations = new ReplayAnnotations("", new byte[0],
                Collections.<Integer, Color>emptyMap(), Collections.<Integer, Color>emptyMap(), ink);
        checkSnapshot(annotations.ink().get(1));
        for (int version : new int[]{3, 4, 5}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            annotations.write(new DataOutputStream(bytes), version);
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            ReplayAnnotations restored = ReplayAnnotations.read(input, "", version);
            check(restored.ink().size() == 1 && input.available() == 0,
                    "v" + version + " replay count or payload retained hidden source ink");
            DoodleStroke active = restored.ink().get(0);
            check(!active.isProofOnly() && active.getDerivedSourceId() == 0,
                    "existing replay acquired snapshot metadata");
            check(annotations.ink().size() == 2 && annotations.ink().get(1).isProofOnly(),
                    "replay serialization mutated in-memory evidence");
        }
        System.out.println("PASS: replay extensions 3/4/5 exclude hidden sources and keep in-memory snapshot metadata");
    }

    public static void main(String[] args) throws Exception {
        Path directory = args.length == 0 ? Files.createTempDirectory("hodoku-derived-persistence-")
                : Files.createDirectories(java.nio.file.Paths.get(args[0]));
        copyAndIndexChecks(); sessionChecks(directory); replayChecks();
        System.out.println("PASS DoodleDerivedPersistenceProbe: " + directory);
    }
}
