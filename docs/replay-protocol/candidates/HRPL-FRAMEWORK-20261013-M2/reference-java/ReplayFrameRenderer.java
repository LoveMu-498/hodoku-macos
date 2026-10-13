/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;

import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Paints validated HRPL-FRAME/0.1 frozen geometry without touching native marks or a solver. */
final class ReplayFrameRenderer {
    private ReplayFrameRenderer() { }

    static boolean knownType(String type) {
        return "mark".equals(type) || "highlight".equals(type) || "line".equals(type)
                || "region".equals(type) || "text".equals(type);
    }

    /** A full-frame fallback replaces the whole view; it is not an overlay on the live board. */
    static boolean usePreview(Map<String, Object> frame) {
        if (frame.get("preview") == null) return false;
        for (Object value : list(frame.get("display"))) {
            Map<String, Object> object = map(value);
            if (!knownType((String) object.get("type")) && Boolean.TRUE.equals(object.get("important"))) return true;
        }
        return false;
    }

    /** The codec has already verified the PNG carrier and its frame binding before this call. */
    static BufferedImage preview(Map<String, Object> frame) throws IOException {
        if (frame.get("preview") == null) return null;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode((String) map(frame.get("preview")).get("data"));
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid frozen frame preview", ex);
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("Cannot decode frozen frame preview");
        return image;
    }

    /**
     * Common viewing board: a uniform white 9x9 grid, black sans-serif digits and the recorded
     * ordinary candidate mask. It is independent of local ColorKu, theme, filtering or draw mode.
     * The separate userCandidates array remains editable snapshot data, not a replacement mask.
     */
    static void paintBoard(Graphics2D graphics, Map<String, Object> frame, double boardX, double boardY, double side) {
        if (!(side > 0) || !Double.isFinite(side)) return;
        Map<String, Object> board = map(frame.get("board"));
        List<?> values = list(board.get("values")), fixed = list(board.get("fixed"));
        List<?> candidates = list(board.get("candidates"));
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setComposite(AlphaComposite.SrcOver);
            g.translate(boardX, boardY);
            g.scale(side / 900.0, side / 900.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(Color.WHITE);
            g.fill(new Rectangle2D.Double(0, 0, 900, 900));
            g.setColor(Color.BLACK);
            for (int cell = 0; cell < 81; cell++) {
                int value = ((Number) values.get(cell)).intValue();
                double x = (cell % 9) * 100.0, y = (cell / 9) * 100.0;
                if (value != 0) {
                    paintDigit(g, value, x + 50, y + 50, 64,
                            Boolean.TRUE.equals(fixed.get(cell)) ? Font.BOLD : Font.PLAIN);
                } else {
                    int mask = ((Number) candidates.get(cell)).intValue();
                    for (int digit = 1; digit <= 9; digit++) if ((mask & (1 << (digit - 1))) != 0)
                        paintDigit(g, digit, x + ((digit - 1) % 3 + .5) * 100.0 / 3,
                                y + ((digit - 1) / 3 + .5) * 100.0 / 3, 24, Font.PLAIN);
                }
            }
            for (int line = 0; line <= 9; line++) {
                g.setStroke(new BasicStroke(line % 3 == 0 ? 3f : 1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                double position = line * 100.0;
                g.draw(new java.awt.geom.Line2D.Double(position, 0, position, 900));
                g.draw(new java.awt.geom.Line2D.Double(0, position, 900, position));
            }
        } finally {
            g.dispose();
        }
    }

    private static void paintDigit(Graphics2D g, int digit, double x, double y, double em, int style) {
        TextLayout text = new TextLayout(Integer.toString(digit), new Font(Font.SANS_SERIF, style, 1000), g.getFontRenderContext());
        Shape outline = text.getOutline(null);
        Rectangle2D bounds = outline.getBounds2D();
        AffineTransform transform = AffineTransform.getTranslateInstance(x, y);
        transform.scale(em / 1000.0, em / 1000.0);
        transform.translate(-bounds.getCenterX(), -bounds.getCenterY());
        g.fill(transform.createTransformedShape(outline));
    }

    /**
     * Paints the five generic primitives in board-relative coordinates. The caller owns the board
     * and surrounding canvas; no board clipping is introduced, so recorded margins remain visible.
     * Input must have passed the codec. Anchors, roles and group identities are inert metadata.
     */
    static void paint(Graphics2D graphics, Map<String, Object> frame, double boardX, double boardY, double side) {
        if (!(side > 0) || !Double.isFinite(side)) return;
        List<Map<String, Object>> objects = new ArrayList<Map<String, Object>>();
        for (Object value : list(frame.get("display"))) objects.add(map(value));
        // Collections.sort is stable: equal z values retain the original array order.
        Collections.sort(objects, new Comparator<Map<String, Object>>() {
            @Override public int compare(Map<String, Object> a, Map<String, Object> b) {
                return Integer.compare(((Number) a.get("z")).intValue(), ((Number) b.get("z")).intValue());
            }
        });
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setComposite(AlphaComposite.SrcOver);
            g.translate(boardX, boardY);
            g.scale(side / 1000000.0, side / 1000000.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            for (Map<String, Object> object : objects) paintObject(g, object);
        } finally {
            g.dispose();
        }
    }

    /** Micro-board bounds of the board and all visible geometry, including stroke, arrow and text. */
    static Rectangle2D displayBounds(Graphics2D graphics, Map<String, Object> frame) {
        Rectangle2D result = new Rectangle2D.Double(0, 0, 1000000, 1000000);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            for (Object value : list(frame.get("display"))) {
                Map<String, Object> o = map(value);
                String type = (String) o.get("type");
                if ("mark".equals(type)) {
                    Rectangle2D b = bounds(o.get("bounds"));
                    Shape mark = markShape(b, "circle".equals(o.get("shape")));
                    result.add(stroke(number(o.get("width"))).createStrokedShape(mark).getBounds2D());
                    if (o.get("outline") != null) result.add(outlineShape(o, b, mark).getBounds2D());
                } else if ("highlight".equals(type)) {
                    result.add(bounds(o.get("bounds")));
                } else if ("line".equals(type)) {
                    List<?> points = list(o.get("points"));
                    double width = number(o.get("width"));
                    result.add(stroke((float) width).createStrokedShape(path(points, false)).getBounds2D());
                    String arrow = (String) o.get("arrow");
                    if ("start".equals(arrow) || "both".equals(arrow)) result.add(arrowShape(points, 0, 1, width).getBounds2D());
                    if ("end".equals(arrow) || "both".equals(arrow)) result.add(arrowShape(points, points.size() - 1, -1, width).getBounds2D());
                } else if ("region".equals(type)) {
                    result.add(stroke(number(o.get("width"))).createStrokedShape(path(list(o.get("points")), true)).getBounds2D());
                } else if ("text".equals(type) && !((String) o.get("text")).isEmpty()) {
                    result.add(textShape(g, o).getBounds2D());
                }
            }
        } finally {
            g.dispose();
        }
        return result;
    }

    private static void paintObject(Graphics2D g, Map<String, Object> o) {
        String type = (String) o.get("type");
        if ("mark".equals(type)) {
            paintMark(g, o);
        } else if ("highlight".equals(type)) {
            g.setColor(color(o.get("fill")));
            g.fill(bounds(o.get("bounds")));
        } else if ("line".equals(type)) {
            List<?> points = list(o.get("points"));
            float width = number(o.get("width"));
            g.setColor(color(o.get("color")));
            g.setStroke("dashed".equals(o.get("dash"))
                    ? new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                            10f, new float[] {5 * width, 3 * width}, 0f) : stroke(width));
            g.draw(path(points, false));
            String arrow = (String) o.get("arrow");
            if ("start".equals(arrow) || "both".equals(arrow)) g.fill(arrowShape(points, 0, 1, width));
            if ("end".equals(arrow) || "both".equals(arrow)) g.fill(arrowShape(points, points.size() - 1, -1, width));
        } else if ("region".equals(type)) {
            Path2D polygon = path(list(o.get("points")), true);
            g.setColor(color(o.get("fill")));
            g.fill(polygon);
            g.setColor(color(o.get("stroke")));
            g.setStroke(stroke(number(o.get("width"))));
            g.draw(polygon);
        } else if ("text".equals(type)) {
            g.setColor(color(o.get("color")));
            if (!((String) o.get("text")).isEmpty()) g.fill(textShape(g, o));
        }
    }

    private static Shape textShape(Graphics2D g, Map<String, Object> o) {
        // Shaped outlines preserve all Unicode script runs under the board transform; native
        // drawString drops runs at some extreme logical-font/device-scale combinations on macOS.
        TextLayout layout = new TextLayout((String) o.get("text"), new Font(Font.SANS_SERIF, Font.PLAIN, 1000),
                g.getFontRenderContext());
        double advance = layout.getAdvance();
        double offset = "center".equals(o.get("align")) ? -advance / 2 : "right".equals(o.get("align")) ? -advance : 0;
        List<?> at = list(o.get("at"));
        AffineTransform transform = AffineTransform.getTranslateInstance(coordinate(at, 0), coordinate(at, 1));
        double scale = number(o.get("fontSize")) / 1000.0;
        transform.scale(scale, scale);
        transform.translate(offset, 0);
        return layout.getOutline(transform);
    }

    private static void paintMark(Graphics2D g, Map<String, Object> o) {
        Rectangle2D b = bounds(o.get("bounds"));
        boolean circle = "circle".equals(o.get("shape"));
        Shape mark = markShape(b, circle);
        float width = number(o.get("width"));
        g.setColor(color(o.get("color")));
        g.setStroke(stroke(width));
        g.draw(mark);
        if (o.get("outline") == null) return;
        g.setColor(color(map(o.get("outline")).get("color")));
        g.fill(outlineShape(o, b, mark));
    }

    private static Shape markShape(Rectangle2D b, boolean circle) {
        if (circle) {
            return new Ellipse2D.Double(b.getX(), b.getY(), b.getWidth(), b.getHeight());
        } else {
            Path2D cross = new Path2D.Double();
            cross.moveTo(b.getMinX(), b.getMinY()); cross.lineTo(b.getMaxX(), b.getMaxY());
            cross.moveTo(b.getMaxX(), b.getMinY()); cross.lineTo(b.getMinX(), b.getMaxY());
            return cross;
        }
    }

    private static Shape outlineShape(Map<String, Object> o, Rectangle2D b, Shape mark) {
        float width = number(o.get("width"));
        Map<String, Object> outline = map(o.get("outline"));
        double gap = number(outline.get("gap")), rim = number(outline.get("width"));
        Area ring;
        if ("circle".equals(o.get("shape"))) {
            ring = new Area(expandedEllipse(b, gap + rim));
            ring.subtract(new Area(expandedEllipse(b, gap)));
        } else {
            ring = new Area(stroke((float) (width + 2 * (gap + rim))).createStrokedShape(mark));
            ring.subtract(new Area(stroke((float) (width + 2 * gap)).createStrokedShape(mark)));
        }
        return ring;
    }

    private static Shape expandedEllipse(Rectangle2D b, double expand) {
        return new Ellipse2D.Double(b.getX() - expand, b.getY() - expand,
                b.getWidth() + 2 * expand, b.getHeight() + 2 * expand);
    }

    private static Shape arrowShape(List<?> points, int tipIndex, int direction, double width) {
        List<?> tip = list(points.get(tipIndex));
        double x = coordinate(tip, 0), y = coordinate(tip, 1), dx = 0, dy = 0;
        for (int i = tipIndex + direction; i >= 0 && i < points.size(); i += direction) {
            List<?> point = list(points.get(i));
            dx = x - coordinate(point, 0); dy = y - coordinate(point, 1);
            if (dx != 0 || dy != 0) break;
        }
        double length = Math.hypot(dx, dy);
        if (length == 0) return new Path2D.Double(); // The validator rejects wholly degenerate lines.
        dx /= length; dy /= length;
        double bx = x - 8 * width * dx, by = y - 8 * width * dy, half = 2 * width;
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(x, y);
        triangle.lineTo(bx - half * dy, by + half * dx);
        triangle.lineTo(bx + half * dy, by - half * dx);
        triangle.closePath();
        return triangle;
    }

    private static Path2D path(List<?> points, boolean closed) {
        Path2D result = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (int i = 0; i < points.size(); i++) {
            List<?> p = list(points.get(i));
            if (i == 0) result.moveTo(coordinate(p, 0), coordinate(p, 1));
            else result.lineTo(coordinate(p, 0), coordinate(p, 1));
        }
        if (closed) result.closePath();
        return result;
    }

    private static BasicStroke stroke(float width) {
        return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }
    private static Rectangle2D bounds(Object value) {
        List<?> b = list(value);
        return new Rectangle2D.Double(coordinate(b, 0), coordinate(b, 1),
                coordinate(b, 2) - coordinate(b, 0), coordinate(b, 3) - coordinate(b, 1));
    }
    private static double coordinate(List<?> list, int index) { return ((Number) list.get(index)).doubleValue(); }
    private static float number(Object value) { return ((Number) value).floatValue(); }
    private static Color color(Object value) { return new Color((int) Long.parseLong(((String) value).substring(1), 16), true); }
    private static List<?> list(Object value) { return (List<?>) value; }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
}
