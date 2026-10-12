/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Exact coordinates and the legacy mutable List/point contract across shared undo snapshots. */
public final class DoodleCoordinatesProbe {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void bits(double expected, double actual, String message) {
        check(Double.doubleToRawLongBits(expected) == Double.doubleToRawLongBits(actual), message);
    }
    private static DoodleStroke stroke() {
        DoodleStroke result = new DoodleStroke(new Color(20, 60, 110, 80), .017f);
        result.addPoint(.1, .2); result.addPoint(.3, .4); result.addPoint(.5, .6);
        return result;
    }
    private static void copiesAndBits() {
        DoodleStroke source = stroke();
        source.setAnchorCell(7); source.setAnchorDigit(4);
        source.setCandidateMarkKind(DoodleStroke.MARK_FALSE_CROSS);
        source.setThoughtGroup(2); source.setConclusionOutlined(true);
        source.setHypothesisStart(true); source.setHypothesisConditionId(5);
        source.setHypothesisGroupKind(1); source.setConclusionSourceMask(37);
        source.setProofOnly(true); source.setDerivedSourceId(8);
        source.setContradictionWitness(true); source.setRefutationResult(true);
        DoodleCoordinates frozen = source.coordinateSnapshot();
        DoodleStroke copy = source.copy();
        check(frozen == copy.coordinateSnapshot(), "copy must share immutable coordinate storage");
        check(copy.getColor().equals(source.getColor()) && copy.getWidthFactor() == source.getWidthFactor()
            && copy.getAnchorCell() == 7 && copy.getAnchorDigit() == 4 && copy.getCandidateMarkKind() == 2
            && copy.getThoughtGroup() == 2 && copy.isConclusionOutlined() && copy.isHypothesisStart()
            && copy.getHypothesisConditionId() == 5 && copy.getHypothesisGroupKind() == 1
            && copy.getConclusionSourceMask() == 37 && copy.isProofOnly() && copy.getDerivedSourceId() == 8
            && copy.isContradictionWitness() && copy.isRefutationResult(), "copy lost mark metadata");
        source.setPoint(0, .7, .8);
        bits(.1, frozen.x(0), "source mutated snapshot"); bits(.1, copy.pointX(0), "source mutated copy");
        copy.getPoints().get(1).setY(.9);
        bits(.4, source.pointY(1), "copy point handle mutated source"); bits(.4, frozen.y(1), "copy mutated frozen data");
        copy.addPoint(1.1, 1.2);
        check(source.pointCount() == 3 && copy.pointCount() == 4, "append contaminated source");
        double nan = Double.longBitsToDouble(0x7ff8000000000021L);
        source.addPoint(-0.0, nan);
        DoodleStroke exact = source.copy();
        bits(-0.0, exact.pointX(3), "negative zero changed"); bits(nan, exact.pointY(3), "NaN payload changed");
        double[] raw = exact.coordinateSnapshot().toArray(); raw[0] = 100;
        bits(.7, exact.pointX(0), "exported array exposed snapshot");
        double[] caller = {-.0, nan};
        DoodleCoordinates external = DoodleCoordinates.copyOf(caller); caller[0] = 9;
        bits(-.0, external.x(0), "snapshot retained caller array"); bits(nan, external.y(0), "factory changed raw bits");
    }
    private static void mutableHandles() {
        DoodleStroke source = stroke();
        List<DoodlePoint> list = source.getPoints();
        DoodlePoint retained = list.get(1);
        DoodleCoordinates old = source.coordinateSnapshot();
        list.add(0, new DoodlePoint(-1, -2));
        retained.setX(8);
        bits(8, source.pointX(2), "insert detached retained point from its element");
        bits(.3, old.x(1), "retained handle contaminated previous snapshot");
        list.remove(0); retained.setY(9);
        bits(9, source.pointY(1), "remove moved retained point incorrectly");
        DoodlePoint removed = list.remove(1); removed.setX(20);
        bits(.5, source.pointX(1), "removed point still attached to current index");
        DoodlePoint previous = list.set(0, new DoodlePoint(30, 31)); previous.setY(99);
        bits(31, source.pointY(0), "replaced point still attached");
        DoodlePoint beforeClear = list.get(0);
        DoodleCoordinates before = source.coordinateSnapshot(); list.clear();
        beforeClear.setX(77); source.addPoint(40, 41);
        bits(30, before.x(0), "clear invalidated snapshot"); bits(40, source.pointX(0), "clear retained stale listener");
        for (int i = 0; i < 12; i++) source.addPoint(i, i + .1);
        DoodlePoint afterRange = list.get(10); list.subList(2, 8).clear(); afterRange.setX(123);
        bits(123, source.pointX(4), "subList clear failed to reindex handle");
        Collections.reverse(list);
        for (DoodlePoint point : list) point.setY(point.getX() + 1);
        for (int i = 0; i < source.pointCount(); i++) bits(source.pointX(i) + 1, source.pointY(i), "list reverse lost binding");
    }
    private static void aliases() {
        DoodlePoint shared = new DoodlePoint(1, 2);
        DoodleStroke a = new DoodleStroke(), b = new DoodleStroke();
        a.getPoints().add(shared); a.getPoints().add(shared); b.getPoints().add(shared);
        DoodleStroke aCopy = a.copy(), bCopy = b.copy();
        shared.setX(3);
        bits(3, a.pointX(0), "supplied point alias lost"); bits(3, a.pointX(1), "duplicate alias lost");
        bits(3, b.pointX(0), "cross-list alias lost"); bits(1, aCopy.pointX(0), "alias corrupted first copy");
        bits(1, bCopy.pointX(0), "alias corrupted second copy");
        a.setPoint(1, 4, 5);
        bits(4, a.pointX(0), "primitive set did not respect same-list point alias"); bits(5, b.pointY(0), "primitive set lost cross-list alias");
        a.getPoints().remove(0); shared.setY(7);
        bits(7, a.pointY(0), "removing one alias removed remaining binding");
        List<DoodlePoint> external = new ArrayList<DoodlePoint>(Arrays.asList(shared));
        DoodleStroke legacy = new DoodleStroke(); legacy.setPoints(external);
        DoodleStroke independent = legacy.copy(); external.add(new DoodlePoint(6, 8)); shared.setX(9);
        check(legacy.pointCount() == 2 && independent.pointCount() == 1, "setPoints list alias contract changed");
        bits(9, legacy.pointX(0), "setPoints external point mutation lost");
        bits(4, independent.pointX(0), "external list contaminated copy");
    }
    private static void historyStorage() {
        List<DoodleStroke> current = new ArrayList<DoodleStroke>();
        List<List<DoodleStroke>> history = new ArrayList<List<DoodleStroke>>();
        long logicalPoints = 0;
        for (int edit = 0; edit < 100; edit++) {
            List<DoodleStroke> snapshot = new ArrayList<DoodleStroke>();
            for (DoodleStroke line : current) { snapshot.add(line.copy()); logicalPoints += line.pointCount(); }
            history.add(snapshot);
            DoodleStroke line = new DoodleStroke();
            for (int p = 0; p < 120; p++) line.addPoint(p / 119.0, edit / 100.0);
            current.add(line);
        }
        java.util.IdentityHashMap<DoodleCoordinates, Boolean> unique = new java.util.IdentityHashMap<DoodleCoordinates, Boolean>();
        for (List<DoodleStroke> frame : history) for (DoodleStroke line : frame) unique.put(line.coordinateSnapshot(), Boolean.TRUE);
        for (DoodleStroke line : current) unique.put(line.coordinateSnapshot(), Boolean.TRUE);
        check(history.size() == 100 && logicalPoints == 594000, "history fixture lost retained edits");
        check(unique.size() == 100, "unchanged history duplicated coordinate buffers: " + unique.size());
        current.get(0).getPoints().get(0).setX(42);
        bits(0, history.get(99).get(0).pointX(0), "100-layer history corrupted by live point");
        System.out.println("HISTORY logical_points=" + (logicalPoints + 12000) + " unique_points=12000 snapshots=" + history.size());
    }
    private static void legacyXml() {
        DoodleStroke source = stroke();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (XMLEncoder out = new XMLEncoder(bytes)) {
            out.setExceptionListener(ex -> { throw new AssertionError("Legacy bean encoding failed", ex); });
            out.writeObject(source);
        }
        DoodleStroke restored;
        try (XMLDecoder in = new XMLDecoder(new ByteArrayInputStream(bytes.toByteArray()))) {
            in.setExceptionListener(ex -> { throw new AssertionError("Legacy bean decoding failed", ex); });
            restored = (DoodleStroke)in.readObject();
        }
        check(restored.pointCount() == source.pointCount(), "legacy XML changed list size");
        for (int i = 0; i < source.pointCount(); i++) {
            bits(source.pointX(i), restored.pointX(i), "legacy XML changed x");
            bits(source.pointY(i), restored.pointY(i), "legacy XML changed y");
        }
    }
    public static void main(String[] args) {
        copiesAndBits(); mutableHandles(); aliases(); historyStorage(); legacyXml();
        System.out.println("PASS DoodleCoordinatesProbe " + checks + " checks");
    }
}
