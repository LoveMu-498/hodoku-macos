/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingUtilities;

/** Compare frame lookup with the real source/reconcile/composite behavior on isolated ink. */
public final class DoodleFrameIndexProbe {
    private static void check(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }

    private static DoodleStroke mark(int cell, int digit, int kind, int group, Color color) {
        DoodleStroke stroke = new DoodleStroke(color, 0.04f);
        stroke.setAnchorCell(cell);
        stroke.setAnchorDigit(digit);
        stroke.setCandidateMarkKind(kind);
        stroke.setThoughtGroup(group);
        if (kind == DoodleStroke.MARK_FALSE_CROSS) {
            stroke.getPoints().add(new DoodlePoint(-0.08, -0.08));
            stroke.getPoints().add(new DoodlePoint(0.08, 0.08));
            stroke.getPoints().add(new DoodlePoint(0.08, -0.08));
            stroke.getPoints().add(new DoodlePoint(-0.08, 0.08));
        } else {
            for (int i = 0; i <= 48; i++) {
                double angle = 2 * Math.PI * i / 48;
                stroke.getPoints().add(new DoodlePoint(0.08 * Math.cos(angle), 0.08 * Math.sin(angle)));
            }
        }
        return stroke;
    }

    @SuppressWarnings("unchecked")
    private static List<DoodleStroke> panelInk(SudokuPanel panel) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField("doodleStrokes");
        field.setAccessible(true);
        return (List<DoodleStroke>) field.get(panel);
    }

    private static void compareMasks(SudokuPanel panel, DoodleFrameIndex frame) {
        for (int cell = -1; cell <= 81; cell++)
            for (int digit = 0; digit <= 10; digit++)
                for (int kind = 0; kind <= 3; kind++)
                    check(frame.sourceMask(cell, digit, kind) == panel.getDoodleMarkSourceMask(cell, digit, kind),
                            "source-mask mismatch at " + cell + "/" + digit + "/" + kind);
    }

    private static BufferedImage composite(SudokuPanel panel, DoodleStroke base, List<DoodleStroke> ink) throws Exception {
        BufferedImage image = new BufferedImage(810, 810, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            Method draw = SudokuPanel.class.getDeclaredMethod("drawDoodleComposite",
                    Graphics2D.class, DoodleStroke.class, List.class, int.class, int.class);
            draw.setAccessible(true);
            draw.invoke(panel, graphics, base, ink, 810, 810);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void compareComposite(SudokuPanel panel, DoodleStroke base, List<DoodleStroke> ink,
            DoodleFrameIndex frame) throws Exception {
        BufferedImage old = composite(panel, base, ink);
        BufferedImage indexed = composite(panel, base, frame.visibleRoleMarks(base));
        check(Arrays.equals(old.getRGB(0, 0, 810, 810, null, 0, 810),
                indexed.getRGB(0, 0, 810, 810, null, 0, 810)), "indexed source list changed composite pixels");
    }

    private static void run() throws Exception {
        SudokuPanel panel = new SudokuPanel(null, true);
        panel.setSize(810, 810);
        Method geometry = SudokuPanel.class.getDeclaredMethod("calculateGridRegion", Rectangle.class, boolean.class, boolean.class);
        geometry.setAccessible(true);
        geometry.invoke(panel, new Rectangle(0, 0, 810, 810), false, false);
        List<DoodleStroke> raw = panelInk(panel);
        DoodleStroke first = mark(12, 7, 1, 2, Color.ORANGE);
        DoodleStroke legacy = mark(12, 7, 1, -1, Color.GRAY);
        DoodleStroke last = mark(12, 7, 1, 2, Color.MAGENTA);
        DoodleStroke sharedColor = mark(12, 7, 1, 0, Color.MAGENTA);
        DoodleStroke cross = mark(12, 7, 2, 5, Color.BLUE);
        DoodleStroke sharedCross = mark(12, 7, 2, 1, Color.RED);
        DoodleStroke boundary = mark(80, 9, 2, 3, Color.GREEN);
        DoodleStroke freehand = new DoodleStroke(Color.BLACK, 0.02f);
        freehand.setThoughtGroup(4);
        // Candidate-anchored legacy MARK_NONE still means a standard circle.
        DoodleStroke oldCircle = mark(0, 1, 0, 4, Color.CYAN);
        raw.addAll(Arrays.asList(first, legacy, cross, sharedColor, last, sharedCross, freehand, oldCircle, boundary));
        first.setConclusionOutlined(true);
        first.setConclusionSourceMask(5);
        last.setHypothesisStart(true);
        DoodleFrameIndex full = new DoodleFrameIndex(raw, raw);
        compareMasks(panel, full);
        check(full.sourceMask(first) == 5 && full.sourceMask(cross) == 34,
                "same colors or opposite statuses merged distinct branch masks");
        check(full.visibleSources(first).equals(Arrays.asList(legacy, sharedColor, last)),
                "legacy/group sort or same-group last-mark selection changed");
        check(DoodleFrameIndex.candidateKey(freehand) < 0 && full.visibleSources(freehand).isEmpty(),
                "freehand was indexed as a candidate mark");
        compareComposite(panel, first, raw, full);
        compareComposite(panel, cross, raw, full);
        compareComposite(panel, oldCircle, raw, full);

        // Erase preview can omit raw sources and contain copies of the remaining stroke.
        DoodleStroke preview = first.copy();
        List<DoodleStroke> visible = new ArrayList<DoodleStroke>(Arrays.asList(preview, cross.copy(), freehand.copy()));
        DoodleFrameIndex erased = new DoodleFrameIndex(raw, visible);
        compareMasks(panel, erased);
        check(erased.sourceMask(preview) == 5 && erased.visibleSources(preview).equals(Arrays.asList(preview)),
                "erase preview replaced the authoritative raw source mask");
        check(erased.sourceMask(boundary) == 8 && erased.visibleSources(boundary).isEmpty(),
                "raw-only target leaked into visible composition");
        compareComposite(panel, preview, visible, erased);
        preview.setConclusionOutlined(false);
        check(!erased.visibleSources(preview).get(0).isConclusionOutlined(), "frame copied stroke metadata instead of references");

        Method reconcile = SudokuPanel.class.getDeclaredMethod("reconcileDoodleComposition");
        reconcile.setAccessible(true);
        reconcile.invoke(panel);
        check(first.isConclusionOutlined(), "matching source selection was invalidated");
        raw.remove(sharedColor);
        // A new synchronous pass must observe source mutation and clear the stale frame.
        DoodleFrameIndex next = new DoodleFrameIndex(raw, raw);
        compareMasks(panel, next);
        reconcile.invoke(panel);
        check(next.sourceMask(first) == 4 && !first.isConclusionOutlined() && first.getConclusionSourceMask() == 0,
                "new source state did not invalidate the previous conclusion outline");

        boolean rejected = false;
        try { first.setThoughtGroup(6); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "invalid public thought group was accepted");
        // Defensive parity with the original scan if a corrupt/reflection fixture bypasses the bean setter.
        Field group = DoodleStroke.class.getDeclaredField("thoughtGroup");
        group.setAccessible(true);
        DoodleStroke corrupt = mark(20, 3, 1, 1, Color.PINK);
        group.setInt(corrupt, 9);
        raw.add(corrupt);
        DoodleFrameIndex defensive = new DoodleFrameIndex(raw, raw);
        compareMasks(panel, defensive);
        check(defensive.sourceMask(corrupt) == 512 && defensive.visibleSources(corrupt).get(0) == corrupt,
                "unexpected stored group lost original mask/composite behavior");
        System.out.println("PASS: real source-mask/reconcile parity, sorted last-source composites and identical circle/cross pixels, raw/erase-preview separation, legacy/invalid groups, shared colors, next-pass source changes");
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        try {
            SwingUtilities.invokeAndWait(() -> {
                try { run(); } catch (Exception failure) { throw new RuntimeException(failure); }
            });
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
