/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Compares exact fragment geometry/metadata with a separately compiled pre-change geometry class. */
public final class DoodleGeometryParityProbe {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    @SuppressWarnings("unchecked") private static List<DoodleStroke> result(Object preview, String field) throws Exception {
        Field f = preview.getClass().getDeclaredField(field); f.setAccessible(true);
        return (List<DoodleStroke>)f.get(preview);
    }
    private static void compare(List<DoodleStroke> expected, List<DoodleStroke> actual, String label) throws Exception {
        check(expected.size() == actual.size(), label + " fragment count");
        String[] properties = {"getColor", "getWidthFactor", "getAnchorCell", "getAnchorDigit", "getCandidateMarkKind",
            "getThoughtGroup", "isConclusionOutlined", "isHypothesisStart", "getHypothesisConditionId",
            "getHypothesisGroupKind", "getConclusionSourceMask", "isProofOnly", "getDerivedSourceId",
            "isContradictionWitness", "isRefutationResult"};
        for (int i = 0; i < expected.size(); i++) {
            DoodleStroke a = expected.get(i), b = actual.get(i);
            for (String property : properties) {
                Method getter = DoodleStroke.class.getMethod(property);
                check(java.util.Objects.equals(getter.invoke(a), getter.invoke(b)), label + " " + property);
            }
            check(a.pointCount() == b.pointCount(), label + " point count");
            for (int p = 0; p < a.pointCount(); p++) {
                check(Double.doubleToRawLongBits(a.pointX(p)) == Double.doubleToRawLongBits(b.pointX(p)), label + " x");
                check(Double.doubleToRawLongBits(a.pointY(p)) == Double.doubleToRawLongBits(b.pointY(p)), label + " y");
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Class<?> baseline = Class.forName("sudoku.DoodleGeometryBaseline");
        Method before = baseline.getDeclaredMethod("previewSubtract", List.class, Shape.class, int.class,
                int.class, Function.class, ToDoubleFunction.class, boolean.class);
        before.setAccessible(true);
        Random random = new Random(419631);
        for (int sample = 0; sample < 80; sample++) {
            List<DoodleStroke> input = new ArrayList<DoodleStroke>();
            for (int ink = 0; ink < 7; ink++) {
                DoodleStroke line = new DoodleStroke(new Color(27, 140, 63), .003f + random.nextFloat() * .04f);
                line.setThoughtGroup(ink % 6);
                double cx = random.nextDouble(), cy = random.nextDouble();
                if (ink == 0) {
                    line.setAnchorCell(40); line.setAnchorDigit(5); line.setCandidateMarkKind(DoodleStroke.MARK_FALSE_CROSS);
                    line.setHypothesisStart(true); line.setHypothesisGroupKind(1); line.setConclusionOutlined(true);
                    line.addPoint(-.2, -.2); line.addPoint(.2, .2); line.addPoint(.2, -.2); line.addPoint(-.2, .2);
                } else {
                    for (int i = 0; i < 48; i++) {
                        double angle = i * Math.PI / 18;
                        line.addPoint(cx + .3 * Math.sin(angle), cy + .2 * Math.cos(angle * 1.5));
                    }
                }
                if (ink == 6) { line.setProofOnly(true); line.setDerivedSourceId(2); }
                input.add(line);
            }
            Shape eraser = sample % 2 == 0 ? new Ellipse2D.Double(random.nextDouble()*700, random.nextDouble()*700, 120, 160)
                    : new Rectangle2D.Double(random.nextDouble()*700, random.nextDouble()*700, 90, 170);
            Function<DoodleStroke, AffineTransform> projection = s -> s.isCandidateAnchored()
                    ? new AffineTransform(200, 0, 0, 200, 400, 400) : AffineTransform.getScaleInstance(810, 810);
            ToDoubleFunction<DoodleStroke> width = s -> Math.max(2, s.getWidthFactor() * 810);
            for (boolean whole : new boolean[]{false, true}) {
                Object expected = before.invoke(null, input, eraser, 810, 810, projection, width, whole);
                DoodleGeometry.ErasurePreview actual = DoodleGeometry.previewSubtract(input, eraser, 810, 810, projection, width, whole);
                compare(result(expected, "retained"), actual.retained, "retained sample=" + sample + " whole=" + whole);
                compare(result(expected, "removed"), actual.removed, "removed sample=" + sample + " whole=" + whole);
            }
        }
        System.out.println("PASS DoodleGeometryParityProbe " + checks + " exact baseline checks across 160 erasures");
    }
}
