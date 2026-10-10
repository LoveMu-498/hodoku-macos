/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Real Swing paint output on synthetic annotations; no Robot, visible window, or user's session. */
public final class AnnotationVisibilityProbe {
    private static final int CELL = 39, DIGIT = 5, DENSITY = 2;
    private static final List<String> failures = new ArrayList<String>();
    private static MainFrame frame;
    private static SudokuPanel panel;
    private static Path output, baseline;
    private static String context;
    private static int checks;

    private AnnotationVisibilityProbe() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        output = Paths.get(System.getProperty("hodoku.probe.output", "/tmp/hodoku-annotation-visibility"));
        String reference = System.getProperty("hodoku.probe.baseline");
        baseline = reference == null ? null : Paths.get(reference);
        Files.createDirectories(output);
        FutureTask<Void> task = new FutureTask<Void>(() -> {
            try {
                ApplicationAppearance.initialize(AppearanceMode.LIGHT);
                frame = new MainFrame(null);
                panel = frame.getSudokuPanel();
                if (args.length == 2 && "--write-session".equals(args[0])) {
                    context = "session-fixture";
                    writeSessionFixture(Paths.get(args[1]));
                    return null;
                }
                boolean exportOnly = args.length == 1 && "--export-only".equals(args[0]);
                for (AppearanceMode theme : new AppearanceMode[]{AppearanceMode.LIGHT, AppearanceMode.DARK}) {
                    ApplicationAppearance.initialize(theme);
                    for (int size : new int[]{630, 1134}) {
                        context = theme.name().toLowerCase() + "-" + size;
                        if (exportOnly) {
                            run("image-export", () -> imageExport(size));
                            run("crowded-native-circle", () -> crowdedNativeCircle(size));
                            continue;
                        }
                        run("same-color", () -> sameColor(size));
                        run("chain-sources", () -> chainSources(size));
                        run("native-hint", () -> nativeHint(size));
                        run("authored-timeline", () -> authoredTimeline(size));
                        run("cross", () -> cross(size));
                        run("mouse-box", () -> mouseAndBox(size));
                        run("free-ink", () -> freeInk(size));
                        run("colorku", () -> colorKu(size));
                        run("image-export", () -> imageExport(size));
                        run("crowded-native-circle", () -> crowdedNativeCircle(size));
                    }
                }
                if (exportOnly) return null;
                context = "session-fixture";
                writeSessionFixture(output.resolve("fixture-data/last-session.xml"));
            } finally {
                if (frame != null) frame.dispose();
            }
            return null;
        });
        SwingUtilities.invokeAndWait(task);
        try { task.get(); }
        catch (Throwable error) { error.printStackTrace(); System.exit(2); }
        StringBuilder report = new StringBuilder("Actual SudokuPanel paint/image export; hidden Swing frame.\n")
                .append(checks).append(" assertions; ").append(failures.size()).append(" failures.\n");
        for (String failure : failures) report.append("FAIL ").append(failure).append('\n');
        Files.write(output.resolve("results.txt"), report.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(report);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private interface Scenario { void run() throws Exception; }
    private static void run(String name, Scenario scenario) {
        try { scenario.run(); }
        catch (Throwable error) {
            failures.add(context + " " + name + " fixture: " + error);
            error.printStackTrace();
        }
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(context + " " + message);
    }
    private static Object read(String name) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField(name);
        field.setAccessible(true); return field.get(panel);
    }
    private static void write(String name, Object value) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField(name);
        field.setAccessible(true); field.set(panel, value);
    }
    private static Object invoke(String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(panel, arguments);
    }
    @SuppressWarnings("unchecked")
    private static List<DoodleStroke> ink() throws Exception { return (List<DoodleStroke>) read("doodleStrokes"); }
    @SuppressWarnings("unchecked")
    private static List<UserChain> chains() throws Exception { return (List<UserChain>) read("userChains"); }
    @SuppressWarnings("unchecked")
    private static List<SudokuSet> boxes() throws Exception { return (List<SudokuSet>) read("boxReasoningGroups"); }
    @SuppressWarnings("unchecked")
    private static Map<Integer, Color> colors(String name) throws Exception { return (Map<Integer, Color>) read(name); }
    @SuppressWarnings("unchecked")
    private static void reset(int size) throws Exception {
        panel.setSudoku((String) null);
        panel.getSudoku().setSudoku(new String(new char[81]).replace('\0', '0'));
        panel.setShowCandidates(true);
        panel.setReplayReadOnly(false);
        panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
        panel.getCellZoomPanel().selectPaletteGroup(0);
        panel.setSize(size, size);
        panel.setDoodleWidthIndex(1);
        ink().clear(); chains().clear(); write("activeUserChain", null);
        colors("coloringMap").clear(); colors("coloringCandidateMap").clear();
        for (SudokuSet group : boxes()) group.clear();
        ((List<Integer>) read("cellSelection")).clear();
        Arrays.fill((boolean[]) read("showHintCellValues"), false);
        paint();
    }
    private static BufferedImage paint() {
        BufferedImage result = new BufferedImage(panel.getWidth() * DENSITY,
                panel.getHeight() * DENSITY, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try { graphics.scale(DENSITY, DENSITY); panel.paint(graphics); }
        finally { graphics.dispose(); }
        return result;
    }
    private static Color color(int group) { return Options.getInstance().getColoringColors()[group * 2]; }
    private static UserChain node(int group) throws Exception {
        UserChain chain = new UserChain();
        chain.getNodes().add(new UserChainNode(CELL, DIGIT, color(group)));
        chains().add(chain); return chain;
    }
    private static DoodleStroke mark(int digit, int kind, int group) throws Exception {
        DoodleStroke result = (DoodleStroke) invoke("createCandidateHypothesisMark",
                new Class<?>[]{int.class, int.class, int.class, int.class, Color.class},
                CELL, digit, kind, group, color(group));
        ink().add(result); return result;
    }
    private static Point2D center(int digit) throws Exception {
        return (Point2D) invoke("doodleCandidateGlyphCenter", new Class<?>[]{int.class, int.class}, CELL, digit);
    }
    private static Rectangle candidateArea() throws Exception {
        Point2D point = center(DIGIT);
        double half = (Integer) read("cellSize") / 6.0;
        return pixels(point.getX() - half, point.getY() - half, half * 2, half * 2);
    }
    private static Rectangle cellArea() throws Exception {
        int size = (Integer) read("cellSize");
        return pixels(panel.getX(CELL / 9, CELL % 9), panel.getY(CELL / 9, CELL % 9), size, size);
    }
    private static Rectangle pixels(double x, double y, double width, double height) {
        return new Rectangle((int) Math.floor(x * DENSITY), (int) Math.floor(y * DENSITY),
                (int) Math.ceil(width * DENSITY), (int) Math.ceil(height * DENSITY));
    }
    private static int distance(int a, int b) {
        return Math.abs((a >> 16 & 255) - (b >> 16 & 255))
                + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
    }
    private static int difference(BufferedImage a, BufferedImage b, Rectangle area, int threshold) {
        int count = 0;
        for (int y = area.y; y < area.y + area.height; y++)
            for (int x = area.x; x < area.x + area.width; x++)
                if (distance(a.getRGB(x, y), b.getRGB(x, y)) > threshold) count++;
        return count;
    }
    private static void save(String name, BufferedImage image) throws Exception {
        ImageIO.write(image, "png", output.resolve(context + "-" + name + ".png").toFile());
        Rectangle crop = cellArea();
        ImageIO.write(image.getSubimage(crop.x, crop.y, crop.width, crop.height), "png",
                output.resolve(context + "-" + name + "-cell.png").toFile());
    }

    private static void sameColor(int size) throws Exception {
        reset(size);
        Color pink = Options.getInstance().getColoringColors()[5];
        colors("coloringMap").put(CELL, pink);
        BufferedImage cellOnly = paint();
        colors("coloringCandidateMap").put(CELL * 10 + DIGIT, pink);
        BufferedImage colored = paint();
        check(difference(cellOnly, colored, candidateArea(), 30) > 12,
                "same-color T candidate is indistinguishable from T cell");
        node(3);
        DoodleStroke circle = mark(DIGIT, DoodleStroke.MARK_TRUE_CIRCLE, 0);
        DoodleStroke cross = mark(DIGIT, DoodleStroke.MARK_FALSE_CROSS, 1);
        for (AnnotationTool tool : new AnnotationTool[]{AnnotationTool.DEFAULT_MOUSE, AnnotationTool.CELL_COLORING,
                AnnotationTool.DOODLE, AnnotationTool.FREE_CHAIN, AnnotationTool.BOX_SELECTION}) {
            panel.setAnnotationTool(tool); panel.getCellZoomPanel().selectPaletteGroup(0);
            BufferedImage full = paint();
            colors("coloringCandidateMap").remove(CELL * 10 + DIGIT);
            check(difference(full, paint(), candidateArea(), 25) > 12, tool + " loses T candidate under L/P");
            colors("coloringCandidateMap").put(CELL * 10 + DIGIT, pink);
            for (DoodleStroke stroke : new DoodleStroke[]{circle, cross}) {
                ink().remove(stroke);
                check(difference(full, paint(), candidateArea(), 20) > 5,
                        tool + " obscures P " + (stroke == circle ? "circle" : "cross") + " with L");
                ink().add(stroke);
            }
            if (tool == AnnotationTool.DEFAULT_MOUSE || tool == AnnotationTool.DOODLE)
                save("same-color-" + tool.name().toLowerCase(), full);
        }
    }

    private static void chainSources(int size) throws Exception {
        for (int count : new int[]{2, 6}) {
            reset(size);
            for (int group = 0; group < count; group++) node(group);
            BufferedImage full = paint();
            Point2D center = center(DIGIT);
            double coreRadius = (Integer) read("cellSize") / 13.0;
            Rectangle interior = pixels(center.getX() - coreRadius, center.getY() - coreRadius,
                    coreRadius * 2, coreRadius * 2);
            List<UserChain> originals = new ArrayList<UserChain>(chains());
            for (int group = 0; group < count; group++) {
                chains().remove(originals.get(group));
                // A covered source may leak through antialiased perimeter pixels. Require
                // real area inside the solid disk, where the new source sectors must live.
                check(difference(full, paint(), interior, 30) > 12,
                        count + "-source L loses source " + group);
                chains().clear(); chains().addAll(originals);
            }
            int white = 0;
            Rectangle area = interior;
            for (int y = area.y; y < area.y + area.height; y++)
                for (int x = area.x; x < area.x + area.width; x++) {
                    Color pixel = new Color(full.getRGB(x, y));
                    if (pixel.getRed() > 245 && pixel.getGreen() > 245 && pixel.getBlue() > 245) white++;
                }
            check(white > 6, count + "-source L has no readable white numeral pixels");
            save("chain-" + count + "-sources", full);
        }
    }

    private static void nativeHint(int size) throws Exception {
        for (String role : new String[]{"placement", "delete", "fin", "endo-fin"}) {
            reset(size); BufferedImage ordinary = paint();
            for (boolean later : new boolean[]{false, true}) {
                String label = role + (later ? "-later" : "-older");
                BufferedImage hintOnly = hintFixture(size, role, later);
                BufferedImage sourceA = hintFixture(size, role, later, 0);
                BufferedImage sourceD = hintFixture(size, role, later, 3);
                BufferedImage withChain = hintFixture(size, role, later, 0, 3);
                Rectangle central = centralArea();
                check(difference(withChain, hintOnly, central, 8) == 0,
                        label + " L overwrites native semantic center");
                assertNativeFootprintPreserved(ordinary, hintOnly, withChain, label);
                assertDistinctRims(withChain, hintOnly, sourceA, sourceD, label);
                save("native-" + label, withChain);
                String before = annotationFingerprint();
                panel.abortStep(); BufferedImage restored = paint();
                check(difference(withChain, restored, central, 25) > 12,
                        label + " cancel did not restore ordinary L center");
                check(before.equals(annotationFingerprint()), label + " cancel changed source annotation data");
            }
            BufferedImage originalHint = hintFixture(size, role, false);
            BufferedImage tintOnly = hintWithLaterTint(size, role);
            BufferedImage tintedA = hintWithLaterTint(size, role, 0);
            BufferedImage tintedD = hintWithLaterTint(size, role, 3);
            BufferedImage tintedBoth = hintWithLaterTint(size, role, 0, 3);
            assertDistinctRims(tintedBoth, tintOnly, tintedA, tintedD, role + "-later-T");
            assertNativeFootprintPreserved(ordinary, originalHint, tintedBoth, role + "-later-T");
            check(difference(originalHint, tintOnly, candidateArea(), 25) > 12,
                    role + " later T candidate tint is not visible");
            save("native-" + role + "-later-tint", tintedBoth);
            nativeCrossTimeline(size, role);
        }
    }

    private static void assertNativeFootprintPreserved(BufferedImage ordinary, BufferedImage hintOnly,
            BufferedImage annotated, String label) throws Exception {
        Rectangle region = candidateArea();
        boolean[][] footprint = new boolean[region.height][region.width];
        // Recover the hint's actual painted silhouette from pixels. Fill each scanline
        // between its first and last changed pixel so numeral holes stay protected too.
        // This independently catches a circular L rim cutting into a dark rounded square.
        for (int y = 0; y < region.height; y++) {
            int first = region.width, last = -1;
            for (int x = 0; x < region.width; x++) {
                if (distance(ordinary.getRGB(region.x + x, region.y + y),
                        hintOnly.getRGB(region.x + x, region.y + y)) > 20) {
                    first = Math.min(first, x); last = x;
                }
            }
            for (int x = first; x <= last; x++) footprint[y][x] = true;
        }
        int inset = DENSITY; // Exclude one logical pixel of antialiasing around the silhouette.
        int protectedPixels = 0, changed = 0;
        for (int y = inset; y < region.height - inset; y++)
            for (int x = inset; x < region.width - inset; x++) {
                boolean inside = true;
                for (int dy = -inset; dy <= inset && inside; dy++)
                    for (int dx = -inset; dx <= inset; dx++)
                        if (!footprint[y + dy][x + dx]) { inside = false; break; }
                if (!inside) continue;
                protectedPixels++;
                if (distance(hintOnly.getRGB(region.x + x, region.y + y),
                        annotated.getRGB(region.x + x, region.y + y)) > 8) changed++;
            }
        check(protectedPixels > 20, label + " hint silhouette fixture too small: " + protectedPixels);
        check(changed == 0, label + " L cuts into native solid silhouette: " + changed + "/" + protectedPixels);
    }

    private static SolutionStep hint(String role) {
        SolutionStep result = new SolutionStep(SolutionType.NAKED_PAIR);
        if ("placement".equals(role)) { result.addIndex(CELL); result.addValue(DIGIT); }
        else if ("delete".equals(role)) result.addCandidateToDelete(CELL, DIGIT);
        else if ("fin".equals(role)) result.addFin(CELL, DIGIT);
        else if ("endo-fin".equals(role)) result.addEndoFin(CELL, DIGIT);
        else throw new IllegalArgumentException(role);
        return result;
    }
    private static BufferedImage hintFixture(int size, String role, boolean later, int... sources) throws Exception {
        reset(size);
        if (later) { panel.setStep(hint(role)); paint(); }
        for (int group : sources) node(group);
        paint();
        if (!later) panel.setStep(hint(role));
        return paint();
    }
    private static BufferedImage hintWithLaterTint(int size, String role, int... sources) throws Exception {
        hintFixture(size, role, false, sources);
        colors("coloringCandidateMap").put(CELL * 10 + DIGIT, Options.getInstance().getColoringColors()[5]);
        return paint();
    }
    private static Rectangle centralArea() throws Exception {
        Point2D point = center(DIGIT);
        double core = Math.max(3.0, (Integer) read("cellSize") / 15.0);
        return pixels(point.getX() - core, point.getY() - core, core * 2, core * 2);
    }
    private static void assertDistinctRims(BufferedImage combined, BufferedImage bare,
            BufferedImage a, BufferedImage d, String label) throws Exception {
        Rectangle region = candidateArea();
        int[] visible = new int[2];
        for (int y = region.y; y < region.y + region.height; y++)
            for (int x = region.x; x < region.x + region.width; x++) {
                int first = a.getRGB(x, y), second = d.getRGB(x, y), background = bare.getRGB(x, y);
                // Compare actual single-source renderings, not a particular implementation's
                // alpha or palette constant. A source must have stable ink distinct from both
                // the underlying picture and the other source; AA fringe alone is insufficient.
                if (distance(first, second) < 90) continue;
                int actual = combined.getRGB(x, y);
                if (distance(first, background) >= 75 && distance(actual, first) <= 24) visible[0]++;
                if (distance(second, background) >= 75 && distance(actual, second) <= 24) visible[1]++;
            }
        check(visible[0] >= 6 && visible[1] >= 6,
                label + " L source rims not separately legible: A=" + visible[0] + " D=" + visible[1]);
    }
    private static void nativeCrossTimeline(int size, String role) throws Exception {
        reset(size); node(0);
        DoodleStroke older = mark(DIGIT, DoodleStroke.MARK_FALSE_CROSS, 1);
        paint(); panel.setStep(hint(role)); BufferedImage oldImage = paint();
        ink().remove(older); BufferedImage bare = paint();
        check(difference(oldImage, bare, centralArea(), 8) == 0, role + " older P overwrites protected native core");
        DoodleStroke later = mark(DIGIT, DoodleStroke.MARK_FALSE_CROSS, 1);
        BufferedImage laterImage = paint();
        check(difference(laterImage, bare, centralArea(), 35) > 6, role + " later P cross is hidden by native core");
        save("native-" + role + "-later-cross", laterImage);
        String before = fingerprint(later);
        panel.abortStep(); paint();
        check(before.equals(fingerprint(later)), role + " cancel modified later P source data");
    }

    /** Installs a synthetic display token only; this does not claim solver/controller validation. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void installAuthoredDisplay(SolutionStep step, UserChain source) throws Exception {
        panel.setStep(step);
        Class<?> kindType = Class.forName("sudoku.SudokuPanel$ReasoningSourceKind");
        Object kind = Enum.valueOf((Class) kindType, "FREE_CHAIN");
        Class<?> requestType = Class.forName("sudoku.SudokuPanel$ReasoningRequest");
        Constructor<?> requestConstructor = requestType.getDeclaredConstructor(long.class, kindType,
                long.class, long.class, long.class, long.class, String.class, SudokuSet.class, UserChain.class);
        requestConstructor.setAccessible(true);
        Object request = requestConstructor.newInstance(1L, kind, 0L, 0L, 0L, 0L,
                TechniqueStepCatalog.createSignature(panel.getSudoku()), null, source);
        Class<?> proposalType = Class.forName("sudoku.SudokuPanel$ReasoningProposal");
        Constructor<?> proposalConstructor = proposalType.getDeclaredConstructor(requestType,
                NativeReasoningMatcher.NativeStepKey.class, long.class);
        proposalConstructor.setAccessible(true);
        Object proposal = proposalConstructor.newInstance(request, null, 0L);
        Field display = proposalType.getDeclaredField("authoredChainDisplay");
        display.setAccessible(true); display.set(proposal, true);
        write("reasoningProposal", proposal);
    }
    private static void authoredTimeline(int size) throws Exception {
        for (String role : new String[]{"placement", "delete"}) {
            reset(size);
            Color pink = Options.getInstance().getColoringColors()[5];
            colors("coloringCandidateMap").put(CELL * 10 + DIGIT, pink);
            UserChain source = node(0);
            DoodleStroke older = mark(DIGIT, DoodleStroke.MARK_FALSE_CROSS, 1);
            paint(); SolutionStep step = hint(role); step.setAuthoredPlacement("placement".equals(role));
            installAuthoredDisplay(step, source); BufferedImage oldImage = paint();
            ink().remove(older); BufferedImage bare = paint();
            check(difference(oldImage, bare, centralArea(), 8) == 0,
                    "authored " + role + " older P overwrites protected conclusion core");
            DoodleStroke later = mark(DIGIT, DoodleStroke.MARK_FALSE_CROSS, 1);
            BufferedImage laterImage = paint();
            check(difference(laterImage, bare, centralArea(), 35) > 6,
                    "authored " + role + " final conclusion repaint hides later P cross");
            save("authored-" + role + "-later-cross", laterImage);
            String before = fingerprint(later);
            write("reasoningProposal", null); panel.abortStep(); paint();
            check(before.equals(fingerprint(later)), "authored cancel changed later P source data");
        }
    }

    private static void cross(int size) throws Exception {
        reset(size); panel.setAnnotationTool(AnnotationTool.DOODLE);
        panel.getCellZoomPanel().selectPaletteGroup(0);
        BufferedImage bare = paint();
        DoodleStroke cross = mark(DIGIT, DoodleStroke.MARK_FALSE_CROSS, 1);
        BufferedImage foreign = paint();
        Shape shape = (Shape) invoke("projectedDoodlePath", new Class<?>[]{DoodleStroke.class, int.class, int.class},
                cross, size, size);
        double rawSpan = Math.abs(cross.getPoints().get(1).getX() - cross.getPoints().get(0).getX());
        double expected = rawSpan * (Integer) read("cellSize") * 0.434;
        check(Math.abs(shape.getBounds2D().getWidth() - expected) <= 0.55,
                "P cross geometry is not old-P 70%: actual=" + shape.getBounds2D().getWidth() + " expected=" + expected);
        Rectangle area = candidateArea();
        int strongDifferences = difference(bare, foreign, area, 85);
        check(strongDifferences > 5, "non-current P cross lacks visible contrast: pixels=" + strongDifferences);
        save("foreign-cross", foreign);
        panel.getCellZoomPanel().selectPaletteGroup(1); BufferedImage own = paint();
        for (AnnotationTool tool : new AnnotationTool[]{AnnotationTool.DEFAULT_MOUSE, AnnotationTool.FREE_CHAIN,
                AnnotationTool.CELL_COLORING, AnnotationTool.BOX_SELECTION}) {
            panel.setAnnotationTool(tool); paint();
            Shape projected = (Shape) invoke("projectedDoodlePath",
                    new Class<?>[]{DoodleStroke.class, int.class, int.class}, cross, size, size);
            check(shape.getBounds2D().equals(projected.getBounds2D()), "cross geometry jumps in " + tool);
        }
        save("own-cross", own);
    }

    private static void mouseAndBox(int size) throws Exception {
        reset(size); BufferedImage bare = paint();
        for (SudokuSet group : boxes()) group.add(CELL);
        panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
        panel.setActiveBoxReasoningGroup(0); panel.setActiveCell(CELL);
        panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE); BufferedImage first = paint();
        panel.setAnnotationTool(AnnotationTool.BOX_SELECTION); panel.setActiveBoxReasoningGroup(5);
        panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE); BufferedImage second = paint();
        check(difference(first, second, cellArea(), 2) == 0, "M retains prior S group emphasis");
        for (int group = 0; group < 6; group++) {
            boxes().get(group).remove(CELL);
            check(difference(second, paint(), cellArea(), 25) > 6, "M/S loses box source " + group);
            boxes().get(group).add(CELL);
        }
        int candidate = SudokuAppearancePalette.forRendering(false).getCandidateColor().getRGB();
        for (int digit : new int[]{1, 3, 7, 9}) {
            Point2D point = center(digit);
            double half = (Integer) read("cellSize") / 9.0;
            Rectangle area = pixels(point.getX() - half, point.getY() - half, half * 2, half * 2);
            int core = 0, preserved = 0;
            for (int y = area.y; y < area.y + area.height; y++)
                for (int x = area.x; x < area.x + area.width; x++)
                    if (distance(bare.getRGB(x, y), candidate) < 8) {
                        core++;
                        if (distance(second.getRGB(x, y), candidate) < 25) preserved++;
                    }
            check(core > 4 && preserved >= core * 0.9, "M/S hides corner digit " + digit + ": " + preserved + "/" + core);
        }
        save("mouse-six-boxes", second);
        panel.setAnnotationTool(AnnotationTool.BOX_SELECTION); save("box-current-F", paint());
    }

    private static void colorKu(int size) throws Exception {
        boolean previous = Options.getInstance().isShowColorKuAct();
        try {
            reset(size); // Warm a numeral glyph cache before enabling ColorKu.
            Options.getInstance().setShowColorKuAct(true);
            BufferedImage bare = paint();
            colors("coloringCandidateMap").put(CELL * 10 + DIGIT, Options.getInstance().getColoringColors()[5]);
            BufferedImage tile = paint();
            Rectangle changed = changedBounds(bare, tile, candidateArea());
            Point2D expected = slotCenter(DIGIT);
            check(Math.abs(changed.getCenterX() / DENSITY - expected.getX()) <= .75
                    && Math.abs(changed.getCenterY() / DENSITY - expected.getY()) <= .75,
                    "ColorKu T tile retained a numeral glyph offset");
            node(0); node(3);
            DoodleStroke circle = mark(DIGIT, DoodleStroke.MARK_TRUE_CIRCLE, 1);
            DoodleStroke cross = mark(DIGIT, DoodleStroke.MARK_FALSE_CROSS, 2);
            BufferedImage full = paint();
            assertColorKuScene(bare, full, circle, cross, "initial");
            save("colorku-composite", full);
            int resized = size == 630 ? 1134 : 630;
            panel.setSize(resized, resized); paint();
            List<UserChain> sources = new ArrayList<UserChain>(chains());
            List<DoodleStroke> marks = new ArrayList<DoodleStroke>(ink());
            Color tint = colors("coloringCandidateMap").remove(CELL * 10 + DIGIT);
            chains().clear(); ink().clear(); BufferedImage resizedBare = paint();
            chains().addAll(sources); ink().addAll(marks);
            colors("coloringCandidateMap").put(CELL * 10 + DIGIT, tint);
            BufferedImage afterResize = paint();
            assertColorKuScene(resizedBare, afterResize, circle, cross, "resized-" + resized);
            save("colorku-resized-" + resized, afterResize);
            if (size == 630) {
                BufferedImage warmLarge = colorKuAfterNumerals(1134);
                BufferedImage warmSmall = colorKuAfterNumerals(630);
                check(difference(warmLarge, warmSmall,
                        new Rectangle(0, 0, warmSmall.getWidth(), warmSmall.getHeight()), 0) == 0,
                        "ColorKu630 actual glyphs depend on stale normal1134 vs normal630 cache");
                save("colorku-normal1134-to-630", warmLarge);
                save("colorku-normal630-to-630", warmSmall);
            }
        } finally { Options.getInstance().setShowColorKuAct(previous); }
    }
    private static BufferedImage colorKuAfterNumerals(int normalSize) throws Exception {
        Options.getInstance().setShowColorKuAct(false);
        reset(normalSize); paint();
        Options.getInstance().setShowColorKuAct(true);
        panel.setSize(630, 630); paint();
        colors("coloringCandidateMap").put(CELL * 10 + DIGIT, Options.getInstance().getColoringColors()[5]);
        node(0); node(3);
        mark(DIGIT, DoodleStroke.MARK_TRUE_CIRCLE, 1);
        mark(DIGIT, DoodleStroke.MARK_FALSE_CROSS, 2);
        return paint();
    }
    private static Point2D slotCenter(int digit) throws Exception {
        double third = (Integer) read("cellSize") / 3.0;
        return new Point2D.Double(panel.getX(CELL / 9, CELL % 9) + ((digit - 1) % 3 + .5) * third,
                panel.getY(CELL / 9, CELL % 9) + ((digit - 1) / 3 + .5) * third);
    }
    private static Rectangle changedBounds(BufferedImage a, BufferedImage b, Rectangle region) {
        int left = region.x + region.width, right = -1, top = region.y + region.height, bottom = -1;
        for (int y = region.y; y < region.y + region.height; y++)
            for (int x = region.x; x < region.x + region.width; x++)
                if (distance(a.getRGB(x, y), b.getRGB(x, y)) > 30) {
                    left = Math.min(left, x); right = Math.max(right, x);
                    top = Math.min(top, y); bottom = Math.max(bottom, y);
                }
        return right < left ? new Rectangle() : new Rectangle(left, top, right - left + 1, bottom - top + 1);
    }
    private static void assertColorKuScene(BufferedImage bare, BufferedImage full, DoodleStroke circle,
            DoodleStroke cross, String label) throws Exception {
        int cellSize = (Integer) read("cellSize");
        for (int digit = 1; digit <= 9; digit++) {
            Point2D expected = slotCenter(digit);
            Point2D chain = (Point2D) invoke("getCandKoord", new Class<?>[]{int.class, int.class, int.class},
                    CELL, digit, cellSize);
            check(center(digit).distance(expected) < .001 && chain.distance(expected) < .001,
                    "ColorKu " + label + " digit " + digit + " retained glyph-centered positioning");
            if (digit != DIGIT) {
                double half = cellSize / 12.0;
                Rectangle core = pixels(expected.getX() - half, expected.getY() - half, half * 2, half * 2);
                check(difference(bare, full, core, 8) == 0,
                        "ColorKu " + label + " composite covers adjacent token " + digit);
            }
        }
        for (DoodleStroke stroke : new DoodleStroke[]{circle, cross}) {
            Shape shape = (Shape) invoke("projectedDoodlePath", new Class<?>[]{DoodleStroke.class, int.class, int.class},
                    stroke, panel.getWidth(), panel.getHeight());
            Point2D expected = slotCenter(DIGIT);
            check(Point2D.distance(shape.getBounds2D().getCenterX(), shape.getBounds2D().getCenterY(),
                    expected.getX(), expected.getY()) < .001, "ColorKu " + label + " P projection drift");
            ink().remove(stroke);
            check(difference(full, paint(), candidateArea(), 25) > 5, "ColorKu " + label + " loses P source");
            ink().add(stroke);
        }
        Color tint = colors("coloringCandidateMap").remove(CELL * 10 + DIGIT);
        check(difference(full, paint(), candidateArea(), 25) > 10, "ColorKu " + label + " loses T source");
        colors("coloringCandidateMap").put(CELL * 10 + DIGIT, tint);
        List<UserChain> sources = new ArrayList<UserChain>(chains());
        for (UserChain source : sources) {
            chains().remove(source);
            check(difference(full, paint(), centralArea(), 25) > 6, "ColorKu " + label + " loses L source");
            chains().clear(); chains().addAll(sources);
        }
    }

    private static Rectangle exportCandidateArea() throws Exception {
        Point2D point = center(DIGIT); double half = (Integer) read("cellSize") / 6.0;
        return new Rectangle((int) Math.floor(point.getX() - half), (int) Math.floor(point.getY() - half),
                (int) Math.ceil(half * 2), (int) Math.ceil(half * 2));
    }
    private static void crowdedNativeCircle(int size) throws Exception {
        reset(size); node(0); node(3); paint();
        panel.setStep(hint("delete")); paint();
        panel.setAnnotationTool(AnnotationTool.DOODLE); panel.getCellZoomPanel().selectPaletteGroup(0);
        panel.setDoodleWidthIndex(3);
        BufferedImage bare = paint();
        DoodleStroke circle = mark(DIGIT, DoodleStroke.MARK_TRUE_CIRCLE, 0);
        BufferedImage sourceCircle = paint();
        circle.setHypothesisStart(true); circle.setConclusionOutlined(true);
        BufferedImage marked = paint();
        int candidate = SudokuAppearancePalette.forRendering(false).getCandidateColor().getRGB();
        for (int digit = 1; digit <= 9; digit++) if (digit != DIGIT) {
            Point2D point = center(digit); double half = (Integer) read("cellSize") / 9.0;
            Rectangle area = pixels(point.getX() - half, point.getY() - half, half * 2, half * 2);
            int core = 0, preserved = 0, sourcePreserved = 0;
            for (int y = area.y; y < area.y + area.height; y++)
                for (int x = area.x; x < area.x + area.width; x++)
                    if (distance(bare.getRGB(x, y), candidate) < 8) {
                        core++;
                        if (distance(marked.getRGB(x, y), candidate) < 25) preserved++;
                        if (distance(sourceCircle.getRGB(x, y), candidate) < 25) sourcePreserved++;
                    }
            check(core > 4 && preserved >= core * .9,
                    "native L + widest current P circle/green/blue hides digit " + digit + ": " + preserved + "/" + core
                            + " (source circle alone " + sourcePreserved + "/" + core + ")");
        }
        save("native-widest-P-circle-no-identities", sourceCircle);
        save("native-widest-P-circle-both-identities", marked);
    }
    private static void imageExport(int size) throws Exception {
        for (boolean nativeHint : new boolean[]{false, true}) for (boolean black : new boolean[]{false, true}) {
            reset(size);
            Color pink = Options.getInstance().getColoringColors()[5];
            colors("coloringMap").put(CELL, pink);
            colors("coloringCandidateMap").put(CELL * 10 + DIGIT, pink);
            node(0); node(3); paint();
            SolutionStep step = nativeHint ? hint("delete") : null;
            if (step != null) { panel.setStep(step); paint(); }
            DoodleStroke circle = mark(DIGIT, DoodleStroke.MARK_TRUE_CIRCLE, 1);
            DoodleStroke cross = mark(DIGIT, DoodleStroke.MARK_FALSE_CROSS, 2);
            String sourceBefore = annotationFingerprint();
            String boardBefore = TechniqueStepCatalog.createSignature(panel.getSudoku());
            BufferedImage full = panel.getSudokuImage(size, black);
            String label = (nativeHint ? "native" : "ordinary") + (black ? "-allBlack" : "-color");
            check(full.getWidth() == size && full.getHeight() == size, "export " + label + " image dimensions changed");
            Rectangle area = exportCandidateArea();
            List<UserChain> sources = new ArrayList<UserChain>(chains());
            for (int i = 0; i < sources.size(); i++) {
                chains().remove(sources.get(i));
                check(difference(full, panel.getSudokuImage(size, black), area, 30) > 3,
                        "export " + label + " loses L source " + i);
                chains().clear(); chains().addAll(sources);
            }
            for (DoodleStroke stroke : new DoodleStroke[]{circle, cross}) {
                ink().remove(stroke);
                check(difference(full, panel.getSudokuImage(size, black), area, 25) > 3,
                        "export " + label + " loses P " + (stroke == circle ? "circle" : "cross"));
                ink().add(stroke);
            }
            if (!black) {
                colors("coloringCandidateMap").remove(CELL * 10 + DIGIT);
                check(difference(full, panel.getSudokuImage(size, false), area, 25) > 3,
                        "export " + label + " loses T candidate perimeter");
                colors("coloringCandidateMap").put(CELL * 10 + DIGIT, pink);
            }
            check(sourceBefore.equals(annotationFingerprint()), "export " + label + " changed annotation source data");
            check(boardBefore.equals(TechniqueStepCatalog.createSignature(panel.getSudoku())),
                    "export " + label + " changed Sudoku values/candidates");
            check(panel.getStep() == step, "export " + label + " changed the displayed step");
            ImageIO.write(full, "png", output.resolve(context + "-export-" + label + ".png").toFile());
            int cellSize = (Integer) read("cellSize");
            ImageIO.write(full.getSubimage(panel.getX(CELL / 9, CELL % 9), panel.getY(CELL / 9, CELL % 9),
                    cellSize, cellSize), "png", output.resolve(context + "-export-" + label + "-cell.png").toFile());
        }
    }

    private static String fingerprint(DoodleStroke stroke) {
        StringBuilder value = new StringBuilder().append(stroke.getColor().getRGB()).append('|')
                .append(stroke.getWidthFactor()).append('|').append(stroke.getAnchorCell()).append('|')
                .append(stroke.getAnchorDigit()).append('|').append(stroke.getCandidateMarkKind()).append('|')
                .append(stroke.getThoughtGroup()).append('|').append(stroke.isHypothesisStart()).append('|')
                .append(stroke.isConclusionOutlined());
        for (DoodlePoint point : stroke.getPoints()) value.append('|').append(point.getX()).append(',').append(point.getY());
        return value.toString();
    }
    private static String annotationFingerprint() throws Exception {
        StringBuilder result = new StringBuilder().append(colors("coloringMap")).append(colors("coloringCandidateMap"));
        for (DoodleStroke stroke : ink()) result.append(fingerprint(stroke));
        for (UserChain chain : chains()) {
            for (UserChainNode node : chain.getNodes()) result.append(node.key()).append(node.getColor());
            result.append(chain.getStrongRelations()).append(chain.getRelationColors()).append(chain.isClosed());
        }
        for (SudokuSet group : boxes()) result.append(group);
        return result.toString();
    }
    private static GuiState capture(String name) {
        GuiState state = new GuiState(panel, panel.getSolver(), frame.getSolutionPanel());
        state.setIncludeAnnotations(true); state.get(true); state.setName(name);
        state.setTimestamp(new java.util.Date(0L));
        return state;
    }
    /** Generates a real SessionStore input for a separately launched, isolated packaged app. */
    private static void writeSessionFixture(Path file) throws Exception {
        ApplicationAppearance.initialize(AppearanceMode.LIGHT);
        reset(900);
        Color pink = Options.getInstance().getColoringColors()[5];
        colors("coloringMap").put(CELL, pink);
        colors("coloringCandidateMap").put(CELL * 10 + DIGIT, pink);
        colors("coloringMap").put(0, pink); colors("coloringMap").put(1, pink);
        colors("coloringCandidateMap").put(1, pink); colors("coloringCandidateMap").put(15, pink);
        for (int group = 0; group < 6; group++) node(group);
        UserChain linked = chains().get(3);
        linked.getNodes().add(0, new UserChainNode(3, DIGIT, color(3)));
        linked.getStrongRelations().add(false);
        linked.getRelationColors().add(Options.getInstance().getColoringColors()[7]);
        for (int group = 0; group < 2; group++) {
            UserChain pair = new UserChain();
            pair.getNodes().add(new UserChainNode(CELL + 1, DIGIT, color(group))); chains().add(pair);
        }
        DoodleStroke circle = mark(DIGIT, DoodleStroke.MARK_TRUE_CIRCLE, 0);
        circle.setHypothesisStart(true); circle.setConclusionOutlined(true);
        mark(DIGIT, DoodleStroke.MARK_FALSE_CROSS, 1);
        mark(1, DoodleStroke.MARK_FALSE_CROSS, 2);
        for (SudokuSet group : boxes()) group.add(CELL);
        for (int row = 3; row <= 5; row++) for (int col = 2; col <= 5; col++) boxes().get(0).add(row * 9 + col);
        boxes().get(1).add(6);
        panel.setActiveCell(CELL); BufferedImage ordinary = paint();
        GuiState ordinaryState = capture("Visibility fixture - ordinary T/L/P/S");
        List<GuiState> savepoints = new ArrayList<GuiState>();
        savepoints.add(ordinaryState);
        for (String role : new String[]{"placement", "delete", "fin", "endo-fin"}) {
            SolutionStep preview=hint(role);
            if(preview.getIndices().isEmpty()) preview.addIndex(CELL-1);
            if(preview.getIndices().size()<2) preview.addIndex(CELL+1);
            if(preview.getValues().isEmpty()) preview.addValue(DIGIT);
            if(preview.getValues().size()<2) preview.addValue(6);
            // MainFrame also renders the explanatory text, unlike panel-only pixel fixtures.
            preview.toString();
            panel.setStep(preview); paint();
            savepoints.add(capture("Visibility fixture - native " + role));
            panel.abortStep();
        }
        if (Boolean.getBoolean("hodoku.probe.native.start")) ordinaryState=savepoints.get(2);
        ordinaryState.initialize(panel, panel.getSolver(), frame.getSolutionPanel()); ordinaryState.set();
        panel.setActiveCell(CELL);
        ordinary=paint();
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.setGuiState(ordinaryState); snapshot.setSavePoints(savepoints);
        snapshot.setActiveRow(CELL / 9); snapshot.setActiveCol(CELL % 9);
        snapshot.setCompleted(false); snapshot.setHistoryEligible(false);
        snapshot.setPrimaryColor(color(0)); snapshot.setSecondaryColor(Options.getInstance().getColoringColors()[1]);
        Files.createDirectories(file.toAbsolutePath().getParent());
        SessionStore store = new SessionStore(file.toFile()); store.save(snapshot);
        SessionSnapshot loaded = store.load();
        check(loaded.getGuiState().getUserChains().size() == 8, "session fixture lost L sources");
        check(loaded.getGuiState().getDoodleStrokes().size() == 3, "session fixture lost P marks");
        check(loaded.getSavePoints().size() == 5, "session fixture lost native role savepoints");
        loaded.getGuiState().initialize(panel, panel.getSolver(), frame.getSolutionPanel());
        frame.setState(loaded.getGuiState()); panel.setActiveCell(CELL);
        check(difference(ordinary, paint(), cellArea(), 0) == 0, "SessionStore round-trip changed composite picture");
        ImageIO.write(paint(), "png", file.toAbsolutePath().getParent().resolve("fixture-preview.png").toFile());
        System.out.println("SESSION_FIXTURE " + file.toAbsolutePath());
    }
    private static void freeInk(int size) throws Exception {
        reset(size);
        DoodleStroke free = new DoodleStroke(Color.MAGENTA, .006f);
        free.getPoints().add(new DoodlePoint(.24, .28));
        free.getPoints().add(new DoodlePoint(.38, .36));
        free.getPoints().add(new DoodlePoint(.30, .43));
        DoodleStroke ellipse = new DoodleStroke(Color.CYAN, .004f);
        for (int i = 0; i <= 48; i++) {
            double angle = 2 * Math.PI * i / 48;
            ellipse.getPoints().add(new DoodlePoint(.62 + .08 * Math.cos(angle), .64 + .05 * Math.sin(angle)));
        }
        ink().add(free); ink().add(ellipse);
        String before = fingerprint(free) + fingerprint(ellipse);
        BufferedImage rendered = paint();
        String name = context + "-free-ink.png";
        ImageIO.write(rendered, "png", output.resolve(name).toFile());
        if (baseline != null) {
            Path file = baseline.resolve(name);
            check(Files.isRegularFile(file), "missing baseline free-ink image: " + file);
            if (Files.isRegularFile(file)) {
                BufferedImage original = ImageIO.read(file.toFile());
                check(difference(original, rendered, pixels(size * .22, size * .25, size * .5, size * .47), 0) == 0,
                        "freehand/hand ellipse standalone rendering changed from before source");
            }
        }
        for (AnnotationTool tool : AnnotationTool.values()) { panel.setAnnotationTool(tool); paint(); }
        check(before.equals(fingerprint(free) + fingerprint(ellipse)), "painting/mode switch changed free-ink data");
    }
}
