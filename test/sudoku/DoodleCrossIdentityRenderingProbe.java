/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Actual panel paints on synthetic data. Does not show a window or send native input. */
public final class DoodleCrossIdentityRenderingProbe {
    private static final int CELL = 40, DENSITY = 2;
    private static final int[] DIGITS = {1, 4, 5, 8};
    private static final Color SOURCE = new Color(210, 70, 165);
    private static final Color NODE = new Color(50, 90, 145);
    private static final List<String> failures = new ArrayList<String>();
    private static final StringBuilder metrics = new StringBuilder(
            "theme,size,digit,withL,pen,state,sourceArea,greenArea,blueArea,greenArms,blueArms,glyphVisible,glyphTotal\n");
    private static MainFrame frame;
    private static SudokuPanel panel;
    private static BufferedImage canvas;
    private static Path output;
    private static String context;
    private static int checks;

    private DoodleCrossIdentityRenderingProbe() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        final int[] sizes = sizes(System.getProperty("hodoku.probe.sizes", "630,1134"));
        output = Paths.get(System.getProperty("hodoku.probe.output", "/tmp/hodoku-cross-identity"));
        Files.createDirectories(output);
        FutureTask<Void> task = new FutureTask<Void>(() -> {
            try {
                ApplicationAppearance.initialize(AppearanceMode.LIGHT);
                frame = new MainFrame(null);
                panel = frame.getSudokuPanel();
                Options.getInstance().setShowColorKuAct(false);
                for (AppearanceMode theme : new AppearanceMode[]{AppearanceMode.LIGHT, AppearanceMode.DARK}) {
                    ApplicationAppearance.initialize(theme);
                    for (int size : sizes) {
                        for (int level = 0; level < 4; level++) {
                            List<BufferedImage[]> rows = new ArrayList<BufferedImage[]>();
                            List<String> labels = new ArrayList<String>();
                            for (int digit : DIGITS) for (boolean chain : new boolean[]{false, true}) {
                                context = theme.name().toLowerCase(Locale.ROOT) + "," + size + ","
                                        + digit + "," + chain + "," + (level + 1);
                                try {
                                    rows.add(fourStates(size, digit, chain, level));
                                    labels.add(digit + (chain ? " + L" : ""));
                                } catch (Throwable error) {
                                    failures.add(context + " fixture: " + error);
                                    error.printStackTrace();
                                }
                            }
                            saveSheet(theme.name().toLowerCase(Locale.ROOT) + "-" + size + "-pen-"
                                    + (level + 1), rows, labels);
                        }
                        for (int digit : DIGITS) {
                            context = theme.name().toLowerCase(Locale.ROOT) + "," + size + "," + digit + ",font";
                            try { whiteText(size, digit); }
                            catch (Throwable error) {
                                failures.add(context + " fixture: " + error);
                                error.printStackTrace();
                            }
                        }
                    }
                }
            } finally {
                if (frame != null) frame.dispose();
            }
            return null;
        });
        SwingUtilities.invokeAndWait(task);
        try { task.get(); }
        catch (Throwable error) { error.printStackTrace(); System.exit(2); }
        StringBuilder report = new StringBuilder("Actual SudokuPanel.paint at 2x density; hidden frame, synthetic board.\n")
                .append(sizes.length * 64).append(" cross fixtures: digits 1/4/5/8, with/without L, light/dark, sizes ")
                .append(Arrays.toString(sizes)).append(", four pen widths.\n")
                .append("Identity checks require visible band area and four-arm distribution; pixel existence alone is insufficient.\n")
                .append("Sheets include 1x logical-size views and 2x Retina samples. White-text checks use the production painters.\n")
                .append(checks).append(" assertions; ").append(failures.size()).append(" failures.\n");
        for (String failure : failures) report.append("FAIL ").append(failure).append('\n');
        Files.write(output.resolve("results.txt"), report.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(output.resolve("metrics.csv"), metrics.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(report);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static int[] sizes(String setting) {
        String[] values = setting.split(",");
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = Integer.parseInt(values[index].trim());
            if (result[index] <= 0) throw new IllegalArgumentException("Probe sizes must be positive");
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) failures.add(context + " " + message);
    }

    private static Object read(String name) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(panel);
    }

    private static void write(String name, Object value) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(panel, value);
    }

    private static Object invoke(String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(panel, arguments);
    }

    @SuppressWarnings("unchecked")
    private static List<DoodleStroke> ink() throws Exception { return (List<DoodleStroke>) read("doodleStrokes"); }
    @SuppressWarnings("unchecked")
    private static List<UserChain> chains() throws Exception { return (List<UserChain>) read("userChains"); }

    @SuppressWarnings("unchecked")
    private static void reset(int size) throws Exception {
        panel.setSudoku((String) null);
        panel.getSudoku().setSudoku(new String(new char[81]).replace('\0', '0'));
        panel.setShowCandidates(true);
        panel.setReplayReadOnly(false);
        panel.setDoodlesVisible(true);
        panel.setAnnotationTool(AnnotationTool.DOODLE);
        panel.getCellZoomPanel().selectPaletteGroup(0);
        panel.setSize(size, size);
        ink().clear(); chains().clear(); write("activeUserChain", null);
        ((Map<Integer, Color>) read("coloringMap")).clear();
        ((Map<Integer, Color>) read("coloringCandidateMap")).clear();
        for (SudokuSet group : (List<SudokuSet>) read("boxReasoningGroups")) group.clear();
        ((List<Integer>) read("cellSelection")).clear();
        Arrays.fill((boolean[]) read("showHintCellValues"), false);
        paintCell();
    }

    private static BufferedImage paintCell() throws Exception {
        int width = panel.getWidth() * DENSITY, height = panel.getHeight() * DENSITY;
        if (canvas == null || canvas.getWidth() != width || canvas.getHeight() != height)
            canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try { g.scale(DENSITY, DENSITY); panel.paint(g); }
        finally { g.dispose(); }
        int side = (Integer) read("cellSize") * DENSITY;
        BufferedImage copy = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
        g = copy.createGraphics();
        try { g.drawImage(canvas, -panel.getX(CELL / 9, CELL % 9) * DENSITY,
                -panel.getY(CELL / 9, CELL % 9) * DENSITY, null); }
        finally { g.dispose(); }
        return copy;
    }

    private static Point2D.Double center(int digit) throws Exception {
        Point2D point = (Point2D) invoke("getCandKoord", new Class<?>[]{int.class, int.class, int.class},
                CELL, digit, (Integer) read("cellSize"));
        return new Point2D.Double(point.getX() - panel.getX(CELL / 9, CELL % 9),
                point.getY() - panel.getY(CELL / 9, CELL % 9));
    }

    private static UserChain addNode(int digit) throws Exception {
        UserChain chain = new UserChain();
        chain.getNodes().add(new UserChainNode(CELL, digit, NODE));
        chains().add(chain);
        return chain;
    }

    private static DoodleStroke addCross(int digit) throws Exception {
        DoodleStroke cross = (DoodleStroke) invoke("createCandidateHypothesisMark",
                new Class<?>[]{int.class, int.class, int.class, int.class, Color.class},
                CELL, digit, DoodleStroke.MARK_FALSE_CROSS, 0, SOURCE);
        ink().add(cross);
        return cross;
    }

    private static BufferedImage[] fourStates(int size, int digit, boolean chain, int level) throws Exception {
        reset(size);
        panel.setDoodleWidthIndex(level);
        if (chain) addNode(digit);
        BufferedImage bare = paintCell();
        DoodleStroke cross = addCross(digit);
        String sourceData = fingerprint(cross);
        panel.setDoodlesVisible(false);
        BufferedImage glyphOnly = paintCell();
        panel.setDoodlesVisible(true);
        BufferedImage[] states = new BufferedImage[4];
        for (int state = 0; state < states.length; state++) {
            cross.setHypothesisStart((state & 1) != 0);
            cross.setConclusionOutlined((state & 2) != 0);
            states[state] = paintCell();
        }
        Point2D.Double c = center(digit);
        Shape path = (Shape) invoke("projectedDoodlePath", new Class<?>[]{DoodleStroke.class, int.class, int.class},
                cross, size, size);
        double span = path.getBounds2D().getWidth();
        double rawSpan = Math.abs(cross.getPoints().get(1).getX() - cross.getPoints().get(0).getX());
        check(Math.abs(span - rawSpan * (Integer) read("cellSize") * .434) <= .55,
                "cross arm geometry changed from 0.434");
        check(sourceData.equals(fingerprint(cross)), "paint/state switch changed source color, points or width");

        double original = (Double) read("lastCandidateMarkDiameter");
        double diameter = (Double) invoke("candidateNodeDisplayDiameter",
                new Class<?>[]{int.class, int.class, double.class, boolean.class}, CELL, digit, original, true);
        int side = states[0].getWidth();
        boolean[] glyph = new boolean[side * side], source = new boolean[side * side];
        int glyphTotal = 0, sourceTotal = 0;
        double halfSlot = (Integer) read("cellSize") / 6.0 - 1;
        Color plain = SudokuAppearancePalette.forRendering(false).getCandidateColor();
        for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
            double dx = (x + .5) / DENSITY - c.x, dy = (y + .5) / DENSITY - c.y;
            if (Math.abs(dx) > halfSlot || Math.abs(dy) > halfSlot) continue;
            int index = y * side + x;
            Color before = new Color(bare.getRGB(x, y));
            glyph[index] = chain ? Math.hypot(dx, dy) < diameter / 2 - 1
                    && before.getRed() > 240 && before.getGreen() > 240 && before.getBlue() > 240
                    : distance(before.getRGB(), plain.getRGB()) < 18;
            if (glyph[index]) glyphTotal++;
            // Exact source cores are invariant; partially covered AA edge pixels legitimately
            // change when their underlay gains an identity band.
            source[index] = states[0].getRGB(x, y) == SOURCE.getRGB();
            if (source[index]) sourceTotal++;
        }
        check(glyphTotal >= 8, "fixture has no measurable original numeral");
        check(sourceTotal / 4.0 >= Math.max(6, span), "cross source has insufficient solid ink area");
        int noneVisible = 0;
        for (int state = 0; state < states.length; state++) {
            int visible = 0, preservedSource = 0;
            for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
                int index = y * side + x;
                if (glyph[index] && distance(states[state].getRGB(x, y), glyphOnly.getRGB(x, y)) <= 8) visible++;
                if (source[index] && states[state].getRGB(x, y) == states[0].getRGB(x, y)) preservedSource++;
            }
            if (state == 0) noneVisible = visible;
            check(visible * 3 >= glyphTotal, "state " + state + " hides too much numeral: " + visible + "/" + glyphTotal);
            check(state == 0 || visible >= noneVisible * .9,
                    "identity removes already visible numeral: state " + state + " " + visible + "/" + noneVisible);
            check(preservedSource == sourceTotal, "identity overwrites source ink: state " + state);
            Band green = (state & 1) != 0 ? band(states[state], states[state & ~1], c, true) : new Band();
            Band blue = (state & 2) != 0 ? band(states[state], states[state & ~2], c, false) : new Band();
            // A normal-size identity must occupy a meaningful band, distributed around the X.
            // The minimum scales with its arm span rather than accepting a few AA color points.
            double minimumArea = Math.max(12, span * 1.6);
            if ((state & 1) != 0) assertBand(green, minimumArea, "green", state);
            if ((state & 2) != 0) assertBand(blue, minimumArea, "blue", state);
            metrics.append(context).append(',').append(state).append(',').append(sourceTotal / 4.0)
                    .append(',').append(green.area()).append(',').append(blue.area())
                    .append(',').append(green.arms()).append(',').append(blue.arms())
                    .append(',').append(visible).append(',').append(glyphTotal).append('\n');
        }
        return states;
    }

    private static final class Band {
        int pixels;
        final int[] quadrants = new int[4];
        double area() { return pixels / 4.0; }
        int arms() { int n = 0; for (int count : quadrants) if (count >= 6) n++; return n; }
    }

    private static Band band(BufferedImage image, BufferedImage without, Point2D.Double center, boolean green) {
        Band result = new Band();
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            Color color = new Color(image.getRGB(x, y));
            boolean hue = green ? color.getGreen() > 130 && color.getGreen() > color.getRed() + 50
                    && color.getGreen() > color.getBlue() + 50
                    : color.getBlue() > 150 && color.getBlue() > color.getGreen() + 45
                    && color.getBlue() > color.getRed() + 45;
            if (!hue || distance(image.getRGB(x, y), without.getRGB(x, y)) < 70) continue;
            result.pixels++;
            int quadrant = ((x + .5) / DENSITY < center.x ? 0 : 1)
                    + ((y + .5) / DENSITY < center.y ? 0 : 2);
            result.quadrants[quadrant]++;
        }
        return result;
    }

    private static void assertBand(Band band, double minimum, String name, int state) {
        check(band.area() >= minimum, name + " identity band too weak in state " + state + ": "
                + band.area() + " logical px², requires " + minimum);
        check(band.arms() == 4, name + " identity is fragmented/lost on X arms: state " + state
                + " quadrant pixels=" + Arrays.toString(band.quadrants));
    }

    private static void whiteText(int size, int digit) throws Exception {
        reset(size);
        panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
        addNode(digit);
        BufferedImage node = paintCell();
        Point2D.Double c = center(digit);
        double original = (Double) read("lastCandidateMarkDiameter");
        double diameter = (Double) invoke("candidateNodeDisplayDiameter",
                new Class<?>[]{int.class, int.class, double.class, boolean.class}, CELL, digit, original, true);
        Color fill = SudokuAppearancePalette.forRendering(false).getUserChainNodeFillColor(NODE);
        checkWhiteFont(node, c, diameter, digit, fill, "L");

        // Invoke the real authored conclusion painter after warming the board's fonts and geometry.
        // This is a rendering contract, not a synthetic solver/controller proof.
        ((double[]) read("nativeCandidateMarkerSizes"))[CELL * 10 + digit] = diameter;
        Color conclusion = new Color(145, 38, 43);
        BufferedImage authored = new BufferedImage(node.getWidth(), node.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = authored.createGraphics();
        try {
            prepare(g); g.translate(-panel.getX(CELL / 9, CELL % 9), -panel.getY(CELL / 9, CELL % 9));
            invoke("drawAuthoredConclusion", new Class<?>[]{Graphics2D.class, int.class, int.class, double.class, Color.class},
                    g, CELL, digit, original, conclusion);
        } finally { g.dispose(); }
        checkWhiteFont(authored, c, diameter, digit, conclusion, "authored");
    }

    private static void checkWhiteFont(BufferedImage actual, Point2D.Double c, double diameter,
            int digit, Color fill, String role) throws Exception {
        BufferedImage expected = new BufferedImage(actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = expected.createGraphics();
        try {
            prepare(g); g.setColor(fill); g.fillRect(0, 0, actual.getWidth(), actual.getHeight());
            Font base = (Font) read("candidateFont");
            Font font = base.deriveFont(base.getSize2D() * .9f);
            g.setFont(font); g.setColor(Color.WHITE);
            Rectangle2D bounds = font.createGlyphVector(g.getFontRenderContext(), Integer.toString(digit)).getVisualBounds();
            g.drawString(Integer.toString(digit), (float) (c.x - bounds.getCenterX()), (float) (c.y - bounds.getCenterY()));
        } finally { g.dispose(); }
        int union = 0, intersection = 0;
        for (int y = 0; y < actual.getHeight(); y++) for (int x = 0; x < actual.getWidth(); x++) {
            if (Math.hypot((x + .5) / DENSITY - c.x, (y + .5) / DENSITY - c.y) >= diameter / 2 - 1) continue;
            boolean a = white(actual.getRGB(x, y)), e = white(expected.getRGB(x, y));
            if (a || e) union++;
            if (a && e) intersection++;
        }
        check(union >= 8 && intersection >= union * .85,
                role + " white text is not centered base-font 90% without extra bold: overlap=" + intersection + "/" + union);
    }

    private static void prepare(Graphics2D g) {
        g.scale(DENSITY, DENSITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static boolean white(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 210 && c.getGreen() > 210 && c.getBlue() > 210;
    }

    private static int distance(int a, int b) {
        return Math.abs((a >> 16 & 255) - (b >> 16 & 255))
                + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a & 255) - (b & 255));
    }

    private static String fingerprint(DoodleStroke stroke) {
        StringBuilder value = new StringBuilder().append(stroke.getColor().getRGB()).append('|')
                .append(stroke.getWidthFactor()).append('|').append(stroke.getAnchorCell()).append('|')
                .append(stroke.getAnchorDigit()).append('|').append(stroke.getCandidateMarkKind()).append('|')
                .append(stroke.getThoughtGroup());
        for (DoodlePoint point : stroke.getPoints()) value.append('|').append(point.getX()).append(',').append(point.getY());
        return value.toString();
    }

    private static void saveSheet(String name, List<BufferedImage[]> rows, List<String> labels) throws Exception {
        if (rows.isEmpty()) return;
        int side = rows.get(0)[0].getWidth(), left = 72, top = 36;
        BufferedImage retina = new BufferedImage(left * DENSITY + side * 4,
                top * DENSITY + rows.size() * (side + 8), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = retina.createGraphics();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, retina.getWidth(), retina.getHeight());
            g.setColor(Color.BLACK); g.setFont(new Font("Dialog", Font.PLAIN, 22));
            String[] names = {"None", "Start", "CMD", "Both"};
            for (int state = 0; state < 4; state++) g.drawString(names[state], left * DENSITY + state * side + 8, 38);
            for (int row = 0; row < rows.size(); row++) {
                int y = top * DENSITY + row * (side + 8);
                g.drawString(labels.get(row), 10, y + side / 2);
                for (int state = 0; state < 4; state++) g.drawImage(rows.get(row)[state], left * DENSITY + state * side, y, null);
            }
        } finally { g.dispose(); }
        ImageIO.write(retina, "png", output.resolve(name + "-2x.png").toFile());
        BufferedImage normal = new BufferedImage(retina.getWidth() / DENSITY, retina.getHeight() / DENSITY, BufferedImage.TYPE_INT_RGB);
        g = normal.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(retina, 0, 0, normal.getWidth(), normal.getHeight(), null);
        } finally { g.dispose(); }
        ImageIO.write(normal, "png", output.resolve(name + "-normal-size.png").toFile());
    }
}
