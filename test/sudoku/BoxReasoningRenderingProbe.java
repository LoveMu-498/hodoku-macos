/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Checks traceable Box contours, actual shared edges, and protected candidate ink in rendered panels. */
public final class BoxReasoningRenderingProbe {
    private static final int[] INDEPENDENT_A = {10, 11, 19, 20};
    private static final int[] INDEPENDENT_B = {50, 51, 59, 60};
    private static final int[] SHARED = {30, 31, 39, 40};
    private static final int TOP = 0, RIGHT = 1, BOTTOM = 2, LEFT = 3;
    private static int checks;

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        final String platformName = System.getProperty("os.name");
        final Throwable[] failure = new Throwable[1];
        final Path output = Paths.get(System.getProperty("hodoku.probe.output",
                Paths.get(System.getProperty("java.io.tmpdir"), "hodoku-box-rendering").toString()));
        Files.createDirectories(output);
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = null;
            try {
                frame = new MainFrame(null);
                SudokuPanel panel = frame.getSudokuPanel();
                panel.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
                panel.getSudoku().setSudoku(new String(new char[81]).replace('\0', '0'));
                panel.getSudoku().setUserCells(new short[Sudoku2.LENGTH]);
                panel.clearSelection(80);
                // Real host AWT/fonts are initialized. Exercise both custom-painted
                // macOS appearance branches without claiming native Aqua acceptance.
                System.setProperty("os.name", "Mac OS X");
                for (AppearanceMode mode : new AppearanceMode[]{AppearanceMode.LIGHT, AppearanceMode.DARK}) {
                    ApplicationAppearance.initialize(mode);
                    require(ApplicationAppearance.isDark() == (mode == AppearanceMode.DARK),
                            "requested appearance branch was not selected: " + mode);
                    for (int size : new int[]{360, 630, 900}) checkFixture(panel, mode, size, output);
                }
            } catch (Throwable ex) {
                failure[0] = ex;
            } finally {
                if (frame != null) frame.dispose();
                System.setProperty("os.name", platformName);
                ApplicationAppearance.initialize(AppearanceMode.LIGHT);
            }
        });
        if (failure[0] != null) { failure[0].printStackTrace(); System.exit(1); }
        System.out.println("PASS: " + checks + " Box rendering checks; independent complete contours, "
                + "two/six shared sources on every edge, protected glyphs, M/S ordinary consistency, "
                + "solid S focus and preview attenuation; images=" + output);
        System.exit(0);
    }

    private static void checkFixture(SudokuPanel panel, AppearanceMode mode, int size, Path output) throws Exception {
        String context = mode + " " + size;
        panel.abortStep();
        panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
        panel.setSize(size, size);
        List<SudokuSet> groups = boxGroups(panel);
        clear(groups);
        panel.setShowCandidates(false);
        BufferedImage noDigits = paint(panel, size);
        panel.setShowCandidates(true);
        BufferedImage bare = paint(panel, size);
        int cellSize = panel.getX(0, 1) - panel.getX(0, 0);
        require(groups.size() == 6, "fixture lost one of the six Box sources");
        ImageIO.write(bare, "png", output.resolve(mode + "-" + size + "-bare.png").toFile());
        if (size == 360) require(cellSize < 48, "small-cell fixture is not small enough");

        add(groups.get(0), INDEPENDENT_A); add(groups.get(1), INDEPENDENT_B);
        BufferedImage independent = paint(panel, size);
        ImageIO.write(independent, "png", output.resolve(mode + "-" + size + "-independent.png").toFile());
        groups.get(0).clear();
        BufferedImage onlyB = paint(panel, size);
        ImageIO.write(onlyB, "png", output.resolve(mode + "-" + size + "-only-B.png").toFile());
        add(groups.get(0), INDEPENDENT_A);
        require(equalCells(panel, independent, onlyB, INDEPENDENT_B),
                context + " removing distant A changed independent B's contour");
        traceSource(panel, independent, bare, groups, 0, mode, size, 3, false, context + " independent A");
        traceSource(panel, independent, bare, groups, 1, mode, size, 3, false, context + " independent B");
        protectCandidates(panel, bare, noDigits, independent, INDEPENDENT_A, context);
        protectCandidates(panel, bare, noDigits, independent, INDEPENDENT_B, context);

        panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
        panel.getCellZoomPanel().selectPaletteGroup(0); panel.setActiveBoxReasoningGroup(0);
        BufferedImage currentA = paint(panel, size);
        require(equalCells(panel, independent, currentA, INDEPENDENT_B),
                context + " S noncurrent B differs from its M ordinary display");
        // A solid focused side has more longitudinal ink than the ordinary dashed side.
        // Use a non-anchor cell so component labels cannot satisfy the contour assertion.
        BufferedImage recoloredA = recolorSource(panel, 0, mode, size);
        BufferedImage recoloredOrdinaryA;
        panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
        recoloredOrdinaryA = recolorSource(panel, 0, mode, size);
        int focused = edgeInk(panel, currentA, recoloredA, bare, 11, TOP, 0, 1);
        int ordinary = edgeInk(panel, independent, recoloredOrdinaryA, bare, 11, TOP, 0, 1);
        require(focused >= (cellSize - 6) * .85 && focused >= ordinary + Math.max(2, cellSize / 12),
                context + " current S side is not visibly solid: focused=" + focused + " ordinary=" + ordinary);

        BufferedImage crowded = null;
        for (int count : new int[]{2, 6}) {
            clear(groups);
            for (int group = 0; group < count; group++) add(groups.get(group), SHARED);
            panel.setActiveBoxReasoningGroup(0);
            crowded = paint(panel, size);
            ImageIO.write(crowded, "png", output.resolve(mode + "-" + size + "-shared-" + count + ".png").toFile());
            for (int group = 0; group < count; group++)
                traceSource(panel, crowded, bare, groups, group, mode, size, 1, count == 2,
                        context + " shared " + count + " source " + (char)('A' + group));
            protectCandidates(panel, bare, noDigits, crowded, SHARED, context + " shared " + count);
            if (count == 2) {
                panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
                panel.getCellZoomPanel().selectPaletteGroup(5); panel.setActiveBoxReasoningGroup(5);
                require(equalCells(panel, crowded, paint(panel, size), SHARED),
                        context + " M and S with empty current group disagree on shared ordinary contours");
                panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
            }
        }
        panel.setActiveBoxReasoningGroup(5);
        require(equalRegion(crowded, paint(panel, size), 0, 0, size, size),
                context + " M display still focuses the previously selected S group");
        panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
        panel.getCellZoomPanel().selectPaletteGroup(0); panel.setActiveBoxReasoningGroup(0);
        BufferedImage focusA = paint(panel, size);
        panel.getCellZoomPanel().selectPaletteGroup(5); panel.setActiveBoxReasoningGroup(5);
        BufferedImage focusF = paint(panel, size);
        require(!equalRegion(focusA, focusF, 0, 0, size, size), context + " S group focus has no visible effect");
        for (int group = 0; group < groups.size(); group++)
            traceSource(panel, focusF, bare, groups, group, mode, size, 1, false,
                    context + " S-F shared 6 source " + (char)('A' + group));
        ImageIO.write(focusF, "png", output.resolve(mode + "-" + size + "-S-F.png").toFile());
        panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
        BufferedImage ordinaryMarks = paint(panel, size);
        panel.setStep(new SolutionStep(SolutionType.NAKED_PAIR));
        BufferedImage preview = paint(panel, size);
        panel.abortStep();
        require(cellDifference(panel, bare, preview, SHARED[0]) < cellDifference(panel, bare, ordinaryMarks, SHARED[0]),
                context + " native-step preview did not subordinate the existing Box marks");
    }

    private static void clear(List<SudokuSet> groups) {
        for (SudokuSet group : groups) group.clear();
    }

    private static void add(SudokuSet group, int[] cells) {
        for (int cell : cells) group.add(cell);
    }

    private static void traceSource(SudokuPanel panel, BufferedImage marked, BufferedImage bare,
            List<SudokuSet> groups, int group, AppearanceMode mode, int size, int sections,
            boolean repeated, String context) throws Exception {
        BufferedImage recolored = recolorSource(panel, group, mode, size);
        SudokuSet cells = groups.get(group);
        int edges = 0;
        for (int i = 0; i < cells.size(); i++) {
            int cell = cells.get(i), row = cell / 9, col = cell % 9;
            for (int edge = TOP; edge <= LEFT; edge++) {
                int neighbor = edge == TOP ? cell - 9 : edge == RIGHT ? cell + 1 : edge == BOTTOM ? cell + 9 : cell - 1;
                boolean boardEdge = edge == TOP && row == 0 || edge == RIGHT && col == 8
                        || edge == BOTTOM && row == 8 || edge == LEFT && col == 0;
                if (!boardEdge && cells.contains(neighbor)) continue;
                edges++;
                boolean[] profile = edgeProfile(panel, marked, recolored, bare, cell, edge);
                for (int part = 0; part < sections; part++) {
                    int pixels = profileInk(profile, part, sections);
                    if (pixels < 2) {
                        String destination = System.getProperty("hodoku.probe.output");
                        if (destination != null) ImageIO.write(recolored, "png",
                                Paths.get(destination, context.replace(' ', '-') + "-recolored.png").toFile());
                    }
                    require(pixels >= 2, context + " contour missing: cell=" + cell + " edge="
                            + new String[]{"top", "right", "bottom", "left"}[edge]
                            + " section=" + part + "/" + sections + " visible positions=" + pixels);
                }
                if (repeated) {
                    // Two sources must recur across the edge. Fixed thirds can split
                    // a perfectly visible dash at a section boundary, so use runs and span.
                    int runs = 0, runLength = 0, first = profile.length, last = -1;
                    for (int j = 0; j <= profile.length; j++) {
                        if (j < profile.length && profile[j]) {
                            runLength++; first = Math.min(first, j); last = j;
                        } else {
                            if (runLength >= 2) runs++;
                            runLength = 0;
                        }
                    }
                    require(runs >= 2 && last - first + 1 >= profile.length * .5,
                            context + " source does not recur across edge: cell=" + cell + " edge=" + edge
                                    + " runs=" + runs + " span=" + (last - first + 1));
                }
            }
        }
        require(edges >= 8, context + " fixture does not exercise a complete multi-cell perimeter");
    }

    // Changing just this source's color leaves overlap membership and dash placement intact.
    // Removing its cells would let a renderer redistribute other sources and create a false attribution.
    private static BufferedImage recolorSource(SudokuPanel panel, int group, AppearanceMode mode, int size) {
        Color[] original = Options.getInstance().getColoringColors();
        Color[] changed = original.clone();
        changed[group * 2] = mode == AppearanceMode.DARK ? Color.WHITE : Color.BLACK;
        try {
            Options.getInstance().setColoringColors(changed);
            return paint(panel, size);
        } finally {
            Options.getInstance().setColoringColors(original);
        }
    }

    private static int edgeInk(SudokuPanel panel, BufferedImage marked, BufferedImage recolored,
            BufferedImage bare, int cell, int edge, int part, int sections) {
        return profileInk(edgeProfile(panel, marked, recolored, bare, cell, edge), part, sections);
    }

    private static int profileInk(boolean[] profile, int part, int sections) {
        int count = 0;
        for (int i = profile.length * part / sections; i < profile.length * (part + 1) / sections; i++)
            if (profile[i]) count++;
        return count;
    }

    private static boolean[] edgeProfile(SudokuPanel panel, BufferedImage marked, BufferedImage recolored,
            BufferedImage bare, int cell, int edge) {
        int size = panel.getX(0, 1) - panel.getX(0, 0);
        int x = panel.getX(cell / 9, cell % 9), y = panel.getY(cell / 9, cell % 9);
        int band = Math.max(4, (int)Math.ceil(size * .12));
        boolean[] profile = new boolean[size - 6];
        for (int along = 3; along < size - 3; along++) {
            boolean visible = false;
            // At small sizes Java2D can rasterize the outer stroke onto the cell
            // boundary itself; source-color differencing distinguishes it from the grid.
            for (int inward = 0; inward <= band; inward++) {
                int xx = edge == LEFT ? x + inward : edge == RIGHT ? x + size - inward : x + along;
                int yy = edge == TOP ? y + inward : edge == BOTTOM ? y + size - inward : y + along;
                Color pixel = new Color(marked.getRGB(xx, yy));
                if (colorDistanceSquared(pixel, new Color(recolored.getRGB(xx, yy))) >= 25 * 25
                        && colorDistanceSquared(pixel, new Color(bare.getRGB(xx, yy))) >= 25 * 25) {
                    visible = true;
                    break;
                }
            }
            profile[along - 3] = visible;
        }
        return profile;
    }

    private static void protectCandidates(SudokuPanel panel, BufferedImage bare, BufferedImage noDigits,
            BufferedImage marked, int[] cells, String context) throws Exception {
        int size = panel.getX(0, 1) - panel.getX(0, 0);
        for (int cell : cells) {
            for (int digit = 1; digit <= 9; digit++)
                require(glyphPixels(panel, bare, noDigits, cell, digit, marked) > 0,
                        context + " missing bare candidate " + digit + " in cell " + cell);
            int safeInset = actualGlyphEdgeDistance(panel, bare, noDigits, cell);
            // The protection rectangle comes from all actual bare-glyph pixels,
            // including host-font AA. A fixed 3px margin describes the old Mac
            // fixture, not every host font. A smaller measured inset enlarges the
            // exact-equality region below: candidate protection remains stricter.
            require(safeInset > 0 && 2 * safeInset < size,
                    context + " fixture has no measurable candidate interior: inset=" + safeInset + " cell=" + cell);
            if (safeInset < 3) {
                Field fontField = SudokuPanel.class.getDeclaredField("candidateFont"); fontField.setAccessible(true);
                java.awt.Font font = (java.awt.Font) fontField.get(panel);
                System.out.println(context + " measured glyph inset=" + safeInset + " cellSize=" + size
                        + " cell=" + cell + " font=" + font.getName() + " family=" + font.getFamily() + " size=" + font.getSize());
            }
            int x = panel.getX(cell / 9, cell % 9), y = panel.getY(cell / 9, cell % 9);
            require(equalRegion(bare, marked, x + safeInset, y + safeInset,
                    size - 2 * safeInset, size - 2 * safeInset),
                    context + " Box contour or labels entered the candidate interior at cell " + cell);
        }
    }

    private static boolean equalCells(SudokuPanel panel, BufferedImage first, BufferedImage second, int[] cells) {
        int size = panel.getX(0, 1) - panel.getX(0, 0);
        for (int cell : cells)
            if (!equalRegion(first, second, panel.getX(cell / 9, cell % 9), panel.getY(cell / 9, cell % 9), size, size))
                return false;
        return true;
    }

    private static int colorDistanceSquared(Color a, Color b) {
        int red=a.getRed()-b.getRed(),green=a.getGreen()-b.getGreen(),blue=a.getBlue()-b.getBlue();
        return red*red+green*green+blue*blue;
    }

    private static int glyphPixels(SudokuPanel panel, BufferedImage digits, BufferedImage empty,
            int cell, int digit, BufferedImage marked) {
        int size = panel.getX(0, 1) - panel.getX(0, 0);
        int x = panel.getX(cell / 9, cell % 9), y = panel.getY(cell / 9, cell % 9), count = 0;
        int left = x + (digit - 1) % 3 * size / 3, top = y + (digit - 1) / 3 * size / 3;
        for (int yy = top + 1; yy < top + size / 3; yy++) for (int xx = left + 1; xx < left + size / 3; xx++) {
            if (digits.getRGB(xx, yy) == empty.getRGB(xx, yy)) continue;
            count++;
            if (marked != null) require(digits.getRGB(xx, yy) == marked.getRGB(xx, yy),
                    "Box marks or M frame changed actual candidate ink: cell=" + cell + " digit=" + digit
                            + " pixel=" + xx + "," + yy + " panel=" + digits.getWidth());
        }
        return count;
    }

    private static int actualGlyphEdgeDistance(SudokuPanel panel, BufferedImage digits, BufferedImage empty, int cell) {
        int size = panel.getX(0, 1) - panel.getX(0, 0), distance = size;
        int x = panel.getX(cell / 9, cell % 9), y = panel.getY(cell / 9, cell % 9);
        for (int yy = 1; yy < size; yy++) for (int xx = 1; xx < size; xx++)
            if (digits.getRGB(x + xx, y + yy) != empty.getRGB(x + xx, y + yy))
                distance = Math.min(distance, Math.min(Math.min(xx, size - xx), Math.min(yy, size - yy)));
        return distance;
    }

    private static long cellDifference(SudokuPanel panel, BufferedImage first, BufferedImage second, int cell) {
        int size = panel.getX(0, 1) - panel.getX(0, 0), x = panel.getX(cell / 9, cell % 9), y = panel.getY(cell / 9, cell % 9);
        long difference = 0;
        for (int yy = y; yy <= y + size; yy++) for (int xx = x; xx <= x + size; xx++) {
            Color a = new Color(first.getRGB(xx, yy)), b = new Color(second.getRGB(xx, yy));
            difference += Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen())
                    + Math.abs(a.getBlue() - b.getBlue());
        }
        return difference;
    }

    private static boolean equalRegion(BufferedImage first, BufferedImage second, int x, int y, int width, int height) {
        for (int yy = y; yy < y + height; yy++) for (int xx = x; xx < x + width; xx++)
            if (first.getRGB(xx, yy) != second.getRGB(xx, yy)) return false;
        return true;
    }

    @SuppressWarnings("unchecked")
    private static List<SudokuSet> boxGroups(SudokuPanel panel) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField("boxReasoningGroups");
        field.setAccessible(true); return (List<SudokuSet>)field.get(panel);
    }

    private static BufferedImage paint(SudokuPanel panel, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); panel.paint(graphics); graphics.dispose(); return image;
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
