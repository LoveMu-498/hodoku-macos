/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Stack;
import java.util.TreeMap;

/** Real v1/v2 persistence, repeated undo history, exact bits and detached worker data. */
public final class SessionCompactCodecProbe {
    private static final String PUZZLE = "530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) throws Exception {
        File directory = Files.createTempDirectory("hodoku-session-compact-").toFile();
        SessionSnapshot original = fixture();
        java.util.List<SudokuSet> groups = new ArrayList<SudokuSet>();
        for (int group = 0; group < 6; group++) { SudokuSet set = new SudokuSet(); set.clear(); groups.add(set); }
        groups.get(1).add(0); groups.get(1).add(17); groups.get(1).add(80);
        original.getGuiState().setBoxReasoningGroups(groups);
        SessionSnapshot detached = SessionSnapshots.detached(original);
        check(SessionSnapshots.sameContent(original, detached), "detached graph changed semantic content");
        check(original.getGuiState() != detached.getGuiState(), "GUI state leaked");
        check(original.getGuiState().getStep().getCandidatesToDelete().get(0)
                != detached.getGuiState().getStep().getCandidatesToDelete().get(0), "candidate leaked through shallow SolutionStep clone");
        check(original.getGuiState().getDoodleStrokes().get(0).coordinateSnapshot()
                == detached.getGuiState().getDoodleStrokes().get(0).coordinateSnapshot(), "immutable ink was recopied");

        File compact = new File(directory, "last-session.xml");
        SessionStore store = new SessionStore(compact);
        store.save(detached);
        check(!original.getGuiState().getBoxReasoningGroups().get(0).isInitialized(), "fixture cache unexpectedly warm");
        check(detached.getGuiState().getBoxReasoningGroups().get(0).isInitialized(), "encoding did not exercise the lazy set cache");
        check(SessionSnapshots.sameContent(original, detached), "XML encoding cache warmup made unchanged membership dirty");
        check(SessionSnapshots.sameWorkingContent(original, detached), "working comparison included derived set cache");
        SessionSnapshot lowBitChange = SessionSnapshots.detached(original);
        lowBitChange.getGuiState().getBoxReasoningGroups().get(1).add(25);
        check(!SessionSnapshots.sameWorkingContent(original, lowBitChange), "low-mask membership change ignored");
        SessionSnapshot highBitChange = SessionSnapshots.detached(original);
        highBitChange.getGuiState().getBoxReasoningGroups().get(1).remove(80);
        check(!SessionSnapshots.sameWorkingContent(original, highBitChange), "high-mask membership change ignored");
        SudokuSet hot = detached.getGuiState().getBoxReasoningGroups().get(1);
        check(hot.size() == 3 && hot.contains(0) && hot.contains(17) && hot.contains(80), "native set semantics changed after encoding");
        java.lang.reflect.Method dataSame = SessionSnapshots.class.getDeclaredMethod("same", Object.class, Object.class, java.util.IdentityHashMap.class);
        dataSame.setAccessible(true);
        SudokuSetShort shortCold = new SudokuSetShort(); shortCold.add(1); shortCold.add(9);
        SudokuSetShort shortHot = new SudokuSetShort(shortCold); shortHot.getValues();
        check((Boolean)dataSame.invoke(null, shortCold, shortHot, new java.util.IdentityHashMap<Object, Object>()), "short-set cache warmup changed identity");
        shortHot.remove(9);
        check(!(Boolean)dataSame.invoke(null, shortCold, shortHot, new java.util.IdentityHashMap<Object, Object>()), "short-set membership change ignored");
        File membershipFile = new File(directory, "membership-change.xml"); SessionStore memberships = new SessionStore(membershipFile);
        memberships.save(SessionSnapshots.detached(original)); byte[] beforeMemberChange = Files.readAllBytes(membershipFile.toPath());
        memberships.save(lowBitChange);
        check(!Arrays.equals(beforeMemberChange, Files.readAllBytes(membershipFile.toPath())), "membership edit did not change XML");
        check(memberships.load().getGuiState().getBoxReasoningGroups().get(1).contains(25), "membership edit was not persisted");
        check(!new SessionStore(AtomicFileIO.backupFor(membershipFile)).load().getGuiState().getBoxReasoningGroups().get(1).contains(25), "backup lost pre-edit set membership");
        SessionSnapshot restored = store.load();
        check(detached.getFormatVersion() == 1, "saving mutated caller version");
        check(restored.getFormatVersion() == 2, "wire version was omitted");
        assertPersisted(detached, restored);
        String xml = new String(Files.readAllBytes(compact.toPath()), StandardCharsets.UTF_8);
        check(xml.contains("property=\"formatVersion\""), "wire lacks explicit version");
        check(!xml.contains("class=\"sudoku.DoodlePoint\""), "coordinates still use per-point bean encoding");
        check(occurrences(xml, "method=\"coordinates\"") == 2, "coordinate dictionary failed: " + occurrences(xml, "method=\"coordinates\""));
        check(xml.contains("method=\"copy\""), "repeated history strokes were not shared");

        // Restore must preserve independently mutable histories while sharing immutable coordinates.
        DoodleStroke current = restored.getGuiState().getDoodleStrokes().get(0);
        DoodleStroke historical = restored.getGuiState().getDoodleUndoHistory().get(0).get(0);
        check(current != historical, "restore aliased editable strokes across history");
        check(current.coordinateSnapshot() == historical.coordinateSnapshot(), "restored immutable history geometry not shared");
        double before = historical.pointX(0);
        current.setPoint(0, 42.0, 43.0);
        check(Double.doubleToRawLongBits(historical.pointX(0)) == Double.doubleToRawLongBits(before), "COW editing changed undo history");

        // Existing v1 sessions remain readable. A new save upgrades only the isolated destination.
        File legacy = new File(directory, "v1.xml");
        try (XMLEncoder encoder = new XMLEncoder(new FileOutputStream(legacy))) {
            AnnotationXml.configure(encoder);
            encoder.setExceptionListener(error -> { throw new AssertionError(error); });
            encoder.writeObject(detached);
        }
        SessionSnapshot legacyRead = new SessionStore(legacy).load();
        check(legacyRead.getFormatVersion() == 1, "v1 version changed while reading");
        assertPersisted(original, legacyRead);
        check(compact.length() < legacy.length() / 3, "compact session unexpectedly large");

        // All live mutation shapes are isolated from the pending background save.
        original.getGuiState().getSudoku().setCell(0, 2, 4);
        original.getGuiState().getStep().getCandidatesToDelete().get(0).setValue(9);
        original.getGuiState().getStep().getChains().get(0).getChain()[0] = 900;
        original.getGuiState().getDoodleStrokes().get(0).setPoint(0, 99, 100);
        original.getGuiState().getTimestamp().setTime(12345);
        original.getSavePoints().get(0).getColoringMap().clear();
        original.getGuiState().getAnzSteps()[0] = 999;
        check(!SessionSnapshots.sameContent(original, detached), "deep change was missed");
        check(detached.getGuiState().getStep().getCandidatesToDelete().get(0).getValue() == 3, "candidate snapshot changed");
        check(detached.getGuiState().getStep().getChains().get(0).getChain()[0] == 12, "chain array snapshot changed");
        check(detached.getGuiState().getTimestamp().getTime() == 1000, "date snapshot changed");
        check(detached.getSavePoints().get(0).getColoringMap().size() == 1, "savepoint map snapshot changed");
        check(detached.getGuiState().getAnzSteps()[0] == 7, "summary snapshot changed");

        SessionSnapshot same = SessionSnapshots.detached(detached);
        same.getGuiState().getDoodleStrokes().get(0).setPoint(0, -0.0, 2.0);
        check(!SessionSnapshots.sameContent(same, detached), "raw signed zero was not compared");
        same = SessionSnapshots.detached(detached);
        same.setReplayElapsedMillis(same.getReplayElapsedMillis() + 1);
        check(!SessionSnapshots.sameContent(same, detached), "replay elapsed change missed");
        check(SessionSnapshots.sameWorkingContent(same, detached), "ticking replay clock invalidated working session");
        same.setReplayFrameCount(same.getReplayFrameCount() + 1);
        check(!SessionSnapshots.sameWorkingContent(same, detached), "new replay operation was ignored");
        same = SessionSnapshots.detached(detached);
        same.getSavePoints().get(0).setName("changed");
        check(!SessionSnapshots.sameContent(same, detached), "savepoint name change missed");

        // A bad compact entry is rejected and the previously valid backup remains usable.
        store.save(detached);
        Files.write(compact.toPath(), xml.replaceFirst("method=\"coordinates\"", "method=\"missingCoordinates\"").getBytes(StandardCharsets.UTF_8));
        assertPersisted(detached, store.load());
        boolean failed = false;
        try { SessionInkCodec.coordinates("AQAAAAE="); } catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "truncated coordinate payload accepted");
        DoodleCoordinates special = DoodleCoordinates.copyOf(new double[]{-0.0,
                Double.longBitsToDouble(0x7ff8000000000042L), Double.MIN_VALUE, Double.POSITIVE_INFINITY});
        check(Arrays.equals(raw(special), raw(SessionInkCodec.coordinates(SessionInkCodec.encodeCoordinates(special)))),
                "coordinate codec normalized double bits");
        String legacyXml = new String(Files.readAllBytes(legacy.toPath()), StandardCharsets.UTF_8);
        check(!legacyXml.contains("sudoku.DoodlePoints") && !legacyXml.contains("sudoku.DoodleCoordinates")
                && !legacyXml.contains("sudoku.SessionInkCodec"), "ordinary XML gained a new-type dependency");
        System.out.println("PASS compact session: v1 read, v2 exact coordinates/metadata/undo/redo/savepoints/steps, detached graph, corrupt-main backup");
        System.out.println("fixture current_points=512 history_layers=30 legacy_bytes=" + legacy.length() + " compact_bytes=" + xml.getBytes(StandardCharsets.UTF_8).length);
    }

    private static SessionSnapshot fixture() {
        GuiState state = new GuiState();
        Sudoku2 sudoku = new Sudoku2(); sudoku.setSudoku(PUZZLE); state.setSudoku(sudoku);
        state.setIncludeAnnotations(true); state.setName("当前工作"); state.setTimestamp(new Date(1000));
        state.setColoringMap(new TreeMap<Integer, Color>()); state.getColoringMap().put(5, Color.PINK);
        state.setColoringCandidateMap(new TreeMap<Integer, Color>());
        state.setUndoStack(new Stack<Sudoku2>()); state.getUndoStack().push(sudoku.clone());
        state.setRedoStack(new Stack<Sudoku2>()); state.getRedoStack().push(sudoku.clone());
        DoodleStroke one = new DoodleStroke(new Color(1, 2, 3, 99), 1.25f);
        one.setAnchorCell(2); one.setAnchorDigit(4); one.setCandidateMarkKind(2); one.setThoughtGroup(4);
        one.setConclusionOutlined(true); one.setHypothesisStart(true); one.setHypothesisConditionId(456);
        one.setHypothesisGroupKind(1); one.setConclusionSourceMask(17); one.setProofOnly(true);
        one.setDerivedSourceId(88); one.setContradictionWitness(true); one.setRefutationResult(true);
        one.addPoint(0.0, 2.0);
        for (int index = 1; index < 256; index++) one.addPoint(Math.nextUp(index / 7.0), -index / 13.0);
        DoodleStroke two = one.copy(); two.setColor(Color.BLUE);
        double[] copied = one.coordinateSnapshot().toArray(); copied[3] = Math.nextUp(copied[3]);
        two.replaceCoordinates(DoodleCoordinates.copyOf(copied));
        state.setDoodleStrokes(new ArrayList<DoodleStroke>(Arrays.asList(one, two)));
        state.setDoodleUndoHistory(new ArrayList<List<DoodleStroke>>()); state.setDoodleRedoHistory(new ArrayList<List<DoodleStroke>>());
        for (int layer = 0; layer < 30; layer++) {
            List<DoodleStroke> history = new ArrayList<DoodleStroke>(Arrays.asList(one.copy(), two.copy()));
            if (layer < 20) state.getDoodleUndoHistory().add(history); else state.getDoodleRedoHistory().add(history);
        }
        state.setDoodleHypothesisConsumedMask(5); state.setDoodleHypothesisUndoMasks(Arrays.asList(1, 2, 4));
        state.setDoodleHypothesisRedoMasks(Arrays.asList(3, 6));
        SolutionStep step = new SolutionStep(SolutionType.NAKED_SINGLE);
        step.getCandidatesToDelete().add(new Candidate(2, 3));
        step.getChains().add(new Chain(0, 1, new int[]{12, 34}));
        UserChain chain = new UserChain(); chain.getNodes().add(UserChainNode.fromAtoms(new int[]{13, 24}, Color.GREEN));
        step.getAuthoredChainDiagram().add(chain);
        state.setStep(step); state.setSteps(new ArrayList<SolutionStep>(Arrays.asList(step)));
        state.setTitels(Arrays.asList("路径")); state.setTabSteps(Arrays.asList(state.getSteps())); state.setAnzSteps(new int[]{7, 8});
        SessionSnapshot snapshot = new SessionSnapshot(); snapshot.setGuiState(state);
        GuiState saved = new GuiState(); saved.setSudoku(sudoku.clone()); saved.setName("存档");
        saved.setDoodleStrokes(new ArrayList<DoodleStroke>(Arrays.asList(one.copy(), two.copy())));
        saved.setColoringMap(new TreeMap<Integer, Color>(state.getColoringMap()));
        snapshot.setSavePoints(new ArrayList<GuiState>(Arrays.asList(saved)));
        snapshot.setReplayId("fixture"); snapshot.setReplayFrameCount(200); snapshot.setReplayElapsedMillis(998);
        snapshot.setActiveRow(3); snapshot.setActiveCol(4); snapshot.setPrimaryColor(Color.BLUE); return snapshot;
    }

    private static void assertPersisted(SessionSnapshot expected, SessionSnapshot actual) {
        check(actual.getGuiState().getSudoku().getSudoku(ClipboardMode.LIBRARY).equals(expected.getGuiState().getSudoku().getSudoku(ClipboardMode.LIBRARY)), "board changed");
        check(actual.getGuiState().getDoodleUndoHistory().size() == 20, "undo layers lost");
        check(actual.getGuiState().getDoodleRedoHistory().size() == 10, "redo layers lost");
        for (int index = 0; index < 2; index++) {
            DoodleStroke source = expected.getGuiState().getDoodleStrokes().get(index), read = actual.getGuiState().getDoodleStrokes().get(index);
            check(SessionInkCodec.metadata(source).equals(SessionInkCodec.metadata(read)), "ink metadata changed");
            check(Arrays.equals(raw(source.coordinateSnapshot()), raw(read.coordinateSnapshot())), "double coordinate bits changed");
            check(SessionInkCodec.metadata(source).equals(SessionInkCodec.metadata(actual.getSavePoints().get(0).getDoodleStrokes().get(index))), "savepoint ink changed");
        }
        check(actual.getGuiState().getStep().getCandidatesToDelete().get(0).getValue() == 3, "step candidate lost");
        check(Arrays.equals(actual.getGuiState().getStep().getChains().get(0).getChain(), new int[]{12, 34}), "step chain lost");
        check(actual.getGuiState().getStep().getAuthoredChainDiagram().get(0).getNodes().get(0).atoms().length == 2, "grouped authored chain lost");
        check(actual.getGuiState().getAnzSteps()[0] == 7 && actual.getGuiState().getTabSteps().size() == 1, "solution path lost");
        check(actual.getReplayElapsedMillis() == expected.getReplayElapsedMillis(), "replay metadata lost");
    }
    private static long[] raw(DoodleCoordinates coordinates) {
        long[] result = new long[coordinates.size() * 2];
        for (int index = 0; index < coordinates.size(); index++) {
            result[index * 2] = Double.doubleToRawLongBits(coordinates.x(index));
            result[index * 2 + 1] = Double.doubleToRawLongBits(coordinates.y(index));
        }
        return result;
    }
    private static int occurrences(String text, String value) {
        int count = 0, index = 0; while ((index = text.indexOf(value, index)) >= 0) { count++; index += value.length(); } return count;
    }
}
