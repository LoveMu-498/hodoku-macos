/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;

import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Analytic pixel checks for the independent generic renderer, separate from real viewer acceptance. */
public final class ReplayFrameRendererProbe {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++;
        if (!result) throw new AssertionError(message);
    }
    private static Map<String, Object> map(Object... fields) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < fields.length; i += 2) result.put((String) fields[i], fields[i + 1]);
        return result;
    }
    private static List<Integer> xy(int... values) {
        List<Integer> result = new ArrayList<Integer>();
        for (int value : values) result.add(value);
        return result;
    }
    private static Map<String, Object> shape(String id, String type, int z, Object... fields) {
        Map<String, Object> result = map("id", id, "type", type, "required", false,
                "important", true, "z", z, "group", null);
        result.putAll(map(fields));
        return result;
    }
    @SafeVarargs private static Map<String, Object> frame(Map<String, Object>... shapes) {
        return map("display", Arrays.asList(shapes), "preview", null);
    }
    private static BufferedImage render(Map<String, Object> frame, int side) {
        BufferedImage image = new BufferedImage(side * 2, side * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.MAGENTA);
        g.setComposite(AlphaComposite.SrcOver.derive(.2f));
        AffineTransform transform = g.getTransform();
        ReplayFrameRenderer.paint(g, frame, side / 2.0, side / 2.0, side);
        check(g.getColor().equals(Color.MAGENTA) && g.getTransform().equals(transform)
                && g.getComposite().equals(AlphaComposite.SrcOver.derive(.2f)), "renderer changed caller graphics state");
        g.dispose();
        return image;
    }
    private static int pixel(BufferedImage image, int side, double x, double y) {
        return image.getRGB((int) Math.round(side * (.5 + x)), (int) Math.round(side * (.5 + y)));
    }
    private static void color(BufferedImage image, int side, double x, double y, int expected, String message) {
        int value = pixel(image, side, x, y);
        check(value == expected, message + " side=" + side + " got=" + Integer.toHexString(value));
    }
    private static int ink(BufferedImage image, int x1, int y1, int x2, int y2) {
        int total = 0;
        for (int y = y1; y < y2; y++) for (int x = x1; x < x2; x++)
            if (image.getRGB(x, y) != Color.WHITE.getRGB()) total++;
        return total;
    }
    private static boolean identical(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++)
            if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }
    private static BufferedImage renderBoard(Map<String, Object> frame, int side) {
        BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.MAGENTA);
        ReplayFrameRenderer.paintBoard(g, frame, 0, 0, side);
        check(g.getColor().equals(Color.MAGENTA), "common board changed caller paint");
        g.dispose();
        return image;
    }
    private static int cellInk(BufferedImage image, int cell, double left, double top, double right, double bottom) {
        double unit = image.getWidth() / 9.0;
        return ink(image, (int) ((cell % 9 + left) * unit), (int) ((cell / 9 + top) * unit),
                (int) ((cell % 9 + right) * unit), (int) ((cell / 9 + bottom) * unit));
    }

    public static void main(String[] args) throws Exception {
        Path output = Files.createDirectories(Paths.get(args.length == 0 ? ".scratch/replay-frame-renderer" : args[0]));
        Map<String, Object> circle = shape("circle", "mark", 5,
                "shape", "circle", "bounds", xy(100000, 100000, 300000, 300000),
                "anchor", map("cell", 80, "digit", 9), "color", "#FFFF0000", "width", 20000,
                "initial", true, "conclusion", true,
                "outline", map("color", "#FF00AA00", "gap", 40000, "width", 20000));
        Map<String, Object> cross = shape("cross", "mark", 1,
                "shape", "cross", "bounds", xy(500000, 100000, 700000, 300000),
                "anchor", null, "color", "#FF000000", "width", 20000, "initial", false, "conclusion", false,
                "outline", map("color", "#FF0000FF", "gap", 20000, "width", 10000));
        Map<String, Object> dashes = shape("dash", "line", 2,
                "points", Arrays.asList(xy(100000, 400000), xy(800000, 400000)),
                "color", "#FF000000", "width", 10000, "dash", "dashed", "arrow", "none");
        Map<String, Object> arrow = shape("arrow", "line", 2,
                "points", Arrays.asList(xy(100000, 550000), xy(100000, 550000), xy(400000, 550000), xy(400000, 550000)),
                "color", "#FFAA00AA", "width", 10000, "dash", "solid", "arrow", "both");
        Map<String, Object> region = shape("region", "region", 2,
                "points", Arrays.asList(xy(500000, 500000), xy(900000, 500000), xy(900000, 900000), xy(500000, 900000),
                        xy(500000, 500000), xy(600000, 600000), xy(800000, 600000), xy(800000, 800000), xy(600000, 800000), xy(600000, 600000)),
                "fill", "#FFFF9900", "stroke", "#FF000000", "width", 2000);
        Map<String, Object> highlight = shape("margin", "highlight", -5,
                "bounds", xy(-200000, 200000, -100000, 400000), "anchor", map("cell", 0, "digit", 1), "fill", "#80FF0000");
        Map<String, Object> text = shape("text", "text", 10,
                "at", xy(500000, 1100000), "text", "Frozen 冻结 4", "color", "#FF000000", "fontSize", 60000, "align", "center");
        Map<String, Object> scene = frame(circle, cross, dashes, arrow, region, highlight, text);
        Graphics2D measurement = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        Rectangle2D sceneBounds = ReplayFrameRenderer.displayBounds(measurement, scene);
        check(sceneBounds.getMinX() == -200000 && sceneBounds.getMaxY() > 1000000, "display bounds omitted margin or text");
        Map<String, Object> expanded = new LinkedHashMap<String, Object>(circle);
        expanded.put("bounds", xy(1400000, 1400000, 1500000, 1500000));
        expanded.put("width", 100000);
        expanded.put("outline", map("color", "#FF00AA00", "gap", 100000, "width", 100000));
        Rectangle2D expandedBounds = ReplayFrameRenderer.displayBounds(measurement, frame(expanded));
        check(expandedBounds.getMaxX() == 1700000 && expandedBounds.getMaxY() == 1700000,
                "display bounds clipped stroke/rim beyond coordinate limits");
        measurement.dispose();
        for (int side : new int[] {300, 600}) {
            BufferedImage image = render(scene, side);
            ImageIO.write(image, "png", output.resolve("generic-" + side + ".png").toFile());
            color(image, side, .1, .2, 0xFFFF0000, "circle ignored recorded geometry or width");
            color(image, side, .2, .2, 0xFFFFFFFF, "circle was filled");
            color(image, side, .05, .2, 0xFF00AA00, "circle expanded outline missing");
            color(image, side, .075, .2, 0xFFFFFFFF, "circle outline gap filled");
            color(image, side, .6, .2, 0xFF000000, "cross base missing");
            color(image, side, .6, .25, 0xFF0000FF, "cross outline ring missing");
            color(image, side, .6, .23, 0xFFFFFFFF, "cross outline gap filled");
            color(image, side, .12, .4, 0xFF000000, "dash first interval missing");
            color(image, side, .165, .4, 0xFFFFFFFF, "dash gap missing");
            color(image, side, .195, .4, 0xFF000000, "dash second interval misplaced");
            color(image, side, .35, .557, 0xFFAA00AA, "end arrow ignored nearest nonzero segment");
            color(image, side, .15, .557, 0xFFAA00AA, "start arrow direction incorrect");
            color(image, side, .55, .7, 0xFFFF9900, "region body absent");
            color(image, side, .7, .7, 0xFFFFFFFF, "even-odd region hole filled");
            color(image, side, -.15, .3, 0xFFFF7F7F, "alpha or off-board coordinates changed");
            check(ink(image, (int) (side * .7), (int) (side * 1.53), (int) (side * 1.3), (int) (side * 1.61)) > side / 3,
                    "Unicode text outside the board was clipped");
            // Initial/conclusion roles and anchor metadata cannot change the recorded appearance.
            Map<String, Object> otherCircle = new LinkedHashMap<String, Object>(circle);
            otherCircle.put("initial", false); otherCircle.put("conclusion", false); otherCircle.put("anchor", null);
            check(identical(render(frame(circle), side), render(frame(otherCircle), side)), "metadata replaced frozen circle geometry");
            Map<String, Object> low = shape("low", "highlight", -1, "bounds", xy(0, 0, 200000, 200000), "anchor", null, "fill", "#FFFF0000");
            Map<String, Object> high = shape("high", "highlight", 4, "bounds", xy(0, 0, 200000, 200000), "anchor", null, "fill", "#FF0000FF");
            Map<String, Object> layered = frame(high, low);
            color(render(layered, side), side, .1, .1, 0xFF0000FF, "z paint order incorrect");
            check(((List<?>) layered.get("display")).get(0) == high, "renderer reordered input data");
            low.put("z", 4);
            color(render(frame(high, low), side), side, .1, .1, 0xFFFF0000, "equal-z sort was not stable");
            // Alignment must move the same text, rather than changing font size or fitting its box.
            int[] extents = new int[3];
            String[] alignments = {"left", "center", "right"};
            for (int a = 0; a < alignments.length; a++) {
                Map<String, Object> aligned = new LinkedHashMap<String, Object>(text);
                aligned.put("align", alignments[a]);
                BufferedImage textImage = render(frame(aligned), side);
                int first = textImage.getWidth(), last = -1;
                for (int y = side; y < textImage.getHeight(); y++) for (int x = 0; x < textImage.getWidth(); x++)
                    if (textImage.getRGB(x, y) != Color.WHITE.getRGB()) { first = Math.min(first, x); last = Math.max(last, x); }
                check(last >= first, "text alignment lost glyphs");
                extents[a] = first + last;
            }
            check(extents[0] > extents[1] && extents[1] > extents[2], "text alignment not honored");
            check(Math.abs((extents[0] - extents[1]) - (extents[1] - extents[2])) <= 4, "center justification not half advance");
            Map<String, Object> prefix = new LinkedHashMap<String, Object>(text);
            prefix.put("align", "left"); prefix.put("text", "Frozen 冻结 ");
            Map<String, Object> suffix = new LinkedHashMap<String, Object>(prefix);
            suffix.put("text", "Frozen 冻结 4");
            BufferedImage prefixImage = render(frame(prefix), side), suffixImage = render(frame(suffix), side);
            check(ink(suffixImage, 0, side, side * 2, side * 2) > ink(prefixImage, 0, side, side * 2, side * 2) + side / 30,
                    "mixed-script text dropped trailing ASCII glyph");
            // The fitted extent must contain every actual pixel, with one antialiasing pixel allowance.
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == Color.WHITE.getRGB()) continue;
                double mx = ((double) x / side - .5) * 1000000, my = ((double) y / side - .5) * 1000000;
                if (mx < sceneBounds.getMinX() - 1000000.0 / side || mx > sceneBounds.getMaxX() + 1000000.0 / side
                        || my < sceneBounds.getMinY() - 1000000.0 / side || my > sceneBounds.getMaxY() + 1000000.0 / side)
                    throw new AssertionError("displayBounds omitted a painted pixel");
            }
            check(true, "fitted bounds containment");
        }
        Map<String, Object> unknown = shape("future", "future-proof", 0, "vendor", "inert");
        Map<String, Object> fallback = frame(unknown);
        check(!ReplayFrameRenderer.usePreview(fallback), "missing preview claimed as full viewing");
        BufferedImage source = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFFFF0000); source.setRGB(1, 0, 0xFF0000FF);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(source, "png", encoded);
        fallback.put("preview", map("mime", "image/png", "coverage", "full-frame", "binding", "validated-by-codec",
                "data", Base64.getEncoder().encodeToString(encoded.toByteArray())));
        check(ReplayFrameRenderer.usePreview(fallback), "unknown important object did not select fallback");
        check(identical(source, ReplayFrameRenderer.preview(fallback)), "preview pixels or aspect changed during decode");
        unknown.put("important", false);
        check(!ReplayFrameRenderer.usePreview(fallback), "nonimportant unknown forced full fallback");
        check(ink(render(fallback, 300), 0, 0, 600, 600) == 0, "unknown object executed or painted speculative geometry");
        Path fixture = Paths.get(args.length > 1 ? args[1]
                : ".scratch/replay-frame-framework-20261013/windows-w1/files/fixtures/all-content.hrep");
        ReplayFrameCodec.Document document = ReplayFrameCodec.read(fixture);
        Map<String, Object> windows = ReplayFrameCodec.map(ReplayFrameCodec.list(document.root.get("frames")).get(1));
        byte[] before = ReplayFrameCodec.canonical(windows);
        for (int side : new int[] {300, 600}) {
            BufferedImage image = render(windows, side);
            ImageIO.write(image, "png", output.resolve("windows-w1-" + side + ".png").toFile());
            for (Object object : ReplayFrameCodec.list(windows.get("display"))) {
                Map<String, Object> drawing = ReplayFrameCodec.map(object);
                BufferedImage individual = render(frame(drawing), side);
                check(ink(individual, 0, 0, side * 2, side * 2) > 0, "W1 object invisible: " + drawing.get("id"));
            }
        }
        check(Arrays.equals(before, ReplayFrameCodec.canonical(windows)), "painting changed original W1 frame data");
        List<Integer> values = new ArrayList<Integer>(), candidates = new ArrayList<Integer>(), userCandidates = new ArrayList<Integer>();
        List<Boolean> fixed = new ArrayList<Boolean>();
        for (int cell = 0; cell < 81; cell++) { values.add(0); candidates.add(0); userCandidates.add(0); fixed.add(false); }
        values.set(0, 5); fixed.set(0, true); values.set(1, 5);
        candidates.set(2, 1 | 256); userCandidates.set(2, 8); userCandidates.set(3, 8);
        Map<String, Object> commonBoard = frame();
        commonBoard.put("board", map("values", values, "fixed", fixed, "candidates", candidates, "userCandidates", userCandidates));
        byte[] boardBefore = ReplayFrameCodec.canonical(commonBoard);
        for (int side : new int[] {450, 900}) {
            BufferedImage boardImage = renderBoard(commonBoard, side);
            ImageIO.write(boardImage, "png", output.resolve("common-board-" + side + ".png").toFile());
            check(cellInk(boardImage, 0, .1, .1, .9, .9) > cellInk(boardImage, 1, .1, .1, .9, .9), "given bold styling missing");
            check(cellInk(boardImage, 1, .1, .1, .9, .9) > 10, "nonfixed value not drawn");
            check(cellInk(boardImage, 2, .04, .04, .29, .29) > 0 && cellInk(boardImage, 2, .71, .71, .96, .96) > 0,
                    "candidate digits1/9 not centered in recorded3x3 slots");
            check(cellInk(boardImage, 2, .04, .38, .29, .62) == 0 && cellInk(boardImage, 3, .1, .1, .9, .9) == 0,
                    "user candidate mask replaced or added to common ordinary mask");
            check(cellInk(boardImage, 4, .1, .1, .9, .9) == 0, "empty board cell invented visible content");
            int thin = ink(boardImage, side / 9 - 4, side / 2 - 4, side / 9 + 4, side / 2 + 4);
            int thick = ink(boardImage, side / 3 - 4, side / 2 - 4, side / 3 + 4, side / 2 + 4);
            check(thick > thin, "3x3 box line not heavier than ordinary cell line");
            Map<String, Object> altered = ReplayFrameCodec.map(ReplayFrameCodec.parseJson(ReplayFrameCodec.canonical(commonBoard)));
            ReplayFrameCodec.list(ReplayFrameCodec.map(altered.get("board")).get("userCandidates")).set(2, 511);
            check(identical(boardImage, renderBoard(altered, side)), "private candidate display altered common board geometry");
        }
        check(Arrays.equals(boardBefore, ReplayFrameCodec.canonical(commonBoard)), "common board renderer modified snapshot arrays");
        System.out.println("PASS " + checks + " generic renderer checks at board sides 300 and 600; images=" + output);
    }
}
