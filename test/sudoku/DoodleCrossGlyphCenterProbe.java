/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;

/** Compare authored circles/crosses against actual unmarked numeral pixels in every tool. */
public final class DoodleCrossGlyphCenterProbe {
    static int checks;
    static double worstX, worstY;
    static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(p, args);
    }
    static void write(String name, Object value) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField(name); field.setAccessible(true); field.set(p, value);
    }
    static int cell(int digit) { return (3 + (digit - 1) / 3) * 9 + 3 + (digit - 1) % 3; }
    static BufferedImage paint(int density) {
        BufferedImage image = new BufferedImage(p.getWidth() * density, p.getHeight() * density, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); graphics.scale(density, density); p.paint(graphics); graphics.dispose(); return image;
    }
    static Point2D inkCenter(BufferedImage image, int digit, int density, boolean dark) throws Exception {
        int cell = cell(digit), size = (Integer) read("cellSize"); double third = size / 3.0;
        double x = p.getX(Sudoku2.getRow(cell), Sudoku2.getCol(cell)) + (digit - 1) % 3 * third;
        double y = p.getY(Sudoku2.getRow(cell), Sudoku2.getCol(cell)) + (digit - 1) / 3 * third;
        // The first/last slots touch thick box borders. Exclude their actual width,
        // otherwise a border pixel becomes part of the supposed numeral bounds.
        double margin = Math.max(3, Math.ceil((Float)read("boxStrokeWidth") / 2.0) + 1);
        int left = (int)Math.ceil((x + margin) * density), right = (int)Math.floor((x + third - margin) * density);
        int top = (int)Math.ceil((y + margin) * density), bottom = (int)Math.floor((y + third - margin) * density);
        int minX = right, maxX = left, minY = bottom, maxY = top, count = 0;
        for (int yy = top; yy <= bottom; yy++) for (int xx = left; xx <= right; xx++) {
            Color color = new Color(image.getRGB(xx, yy));
            boolean numeral = Math.abs(color.getRed() - color.getGreen()) < 40
                    && Math.abs(color.getBlue() - color.getGreen()) < 40
                    && (dark ? color.getRed() > 150 && color.getGreen() > 150 && color.getBlue() > 150
                             : color.getRed() < 140 && color.getGreen() < 155 && color.getBlue() < 180);
            if (numeral) { count++; minX = Math.min(minX, xx); maxX = Math.max(maxX, xx); minY = Math.min(minY, yy); maxY = Math.max(maxY, yy); }
        }
        check(count > 8, "missing numeral pixels digit=" + digit);
        return new Point2D.Double((minX + maxX + 1) / (2.0 * density), (minY + maxY + 1) / (2.0 * density));
    }
    static Rectangle2D path(DoodleStroke mark) throws Exception {
        return ((Shape)invoke("projectedDoodlePath", new Class[]{DoodleStroke.class, int.class, int.class},
                mark, p.getWidth(), p.getHeight())).getBounds2D();
    }
    static Rectangle2D markPixels(BufferedImage image, Point2D numeral, int density) throws Exception {
        int radius = (Integer)read("cellSize") / 3;
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        for (int y = (int)((numeral.getY() - radius) * density); y <= (numeral.getY() + radius) * density; y++)
            for (int x = (int)((numeral.getX() - radius) * density); x <= (numeral.getX() + radius) * density; x++) {
                Color color = new Color(image.getRGB(x, y));
                if (color.getRed() - color.getGreen() > 35 && color.getRed() - color.getBlue() > 35) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
            }
        check(maxX >= minX && maxY >= minY, "missing actual mark pixels");
        return new Rectangle2D.Double(minX / (double)density, minY / (double)density,
                (maxX - minX + 1) / (double)density, (maxY - minY + 1) / (double)density);
    }
    static void compare(Rectangle2D cross, Point2D numeral, String context) {
        double dx = Math.abs(cross.getCenterX() - numeral.getX()), dy = Math.abs(cross.getCenterY() - numeral.getY());
        worstX = Math.max(worstX, dx); worstY = Math.max(worstY, dy); checks++;
        check(dx <= 0.65 && dy <= 0.65, "cross misses actual glyph center: " + context + " dx=" + dx + " dy=" + dy);
    }
    static double areaSize(Shape shape) {
        // Area boolean operations may retain zero-width floating-point slivers.
        // An area tolerance does not relax the independent .001 center constraint.
        PathIterator path=shape.getPathIterator(null,.0001);double[] xy=new double[6];
        double firstX=0,firstY=0,lastX=0,lastY=0,twice=0;
        while(!path.isDone()) {
            int kind=path.currentSegment(xy);
            if(kind==PathIterator.SEG_MOVETO){firstX=lastX=xy[0];firstY=lastY=xy[1];}
            else if(kind==PathIterator.SEG_LINETO){twice+=lastX*xy[1]-xy[0]*lastY;lastX=xy[0];lastY=xy[1];}
            else if(kind==PathIterator.SEG_CLOSE)twice+=lastX*firstY-firstX*lastY;
            path.next();
        }
        return Math.abs(twice)/2;
    }
    static boolean sameArea(Shape a, Shape b) {
        Area difference = new Area(a); difference.exclusiveOr(new Area(b)); return areaSize(difference)<.00001;
    }
    static Shape displayedGlyph(DoodleStroke mark, Rectangle2D source, int density) throws Exception {
        // TextLayout is independent of the product's cached GlyphVector table. Use the
        // font this fixture actually paints, at the already pixel-verified source anchor.
        Font font = (Font)read("candidateFont");
        if (((boolean[])read("doodleCandidateBold"))[mark.getAnchorCell()*10+mark.getAnchorDigit()])
            font = font.deriveFont(Font.BOLD);
        BufferedImage reference = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = reference.createGraphics();
        try {
            graphics.scale(density,density);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            java.awt.font.TextLayout text = new java.awt.font.TextLayout(Integer.toString(mark.getAnchorDigit()),
                    font,graphics.getFontRenderContext());
            Rectangle2D bounds = text.getBounds();
            return text.getOutline(AffineTransform.getTranslateInstance(source.getCenterX()-bounds.getCenterX(),
                    source.getCenterY()-bounds.getCenterY()));
        } finally { graphics.dispose(); }
    }
    static void glyphSurvivesRim(Shape glyph, Shape rim, BufferedImage bare, int density) throws Exception {
        BufferedImage overlaid = new BufferedImage(bare.getWidth(),bare.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = overlaid.createGraphics();
        try {
            graphics.drawImage(bare,0,0,null); graphics.scale(density,density);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(Color.MAGENTA); graphics.fill(rim);
        } finally { graphics.dispose(); }
        Rectangle2D bounds = glyph.getBounds2D(); int count=0,changed=0;
        boolean dark=ApplicationAppearance.isDark();
        java.util.Map<Integer,Integer> colors=new java.util.HashMap<Integer,Integer>();
        for(int y=(int)Math.floor(bounds.getMinY()*density);y<Math.ceil(bounds.getMaxY()*density);y++)
            for(int x=(int)Math.floor(bounds.getMinX()*density);x<Math.ceil(bounds.getMaxX()*density);x++) {
                if(!glyph.contains((x+.5)/density,(y+.5)/density))continue;
                Color color=new Color(bare.getRGB(x,y));
                boolean numeral=Math.abs(color.getRed()-color.getGreen())<40&&Math.abs(color.getBlue()-color.getGreen())<40
                        &&(dark?color.getRed()>150&&color.getGreen()>150&&color.getBlue()>150
                        :color.getRed()<140&&color.getGreen()<155&&color.getBlue()<180);
                if(numeral){int rgb=bare.getRGB(x,y);colors.put(rgb,colors.containsKey(rgb)?colors.get(rgb)+1:1);}
            }
        // Use the actual solid ink color, not partially covered AA edge pixels.
        // Bold filter text has another foreground, so derive it from the bare image.
        int coreColor=0,largest=0;
        for(java.util.Map.Entry<Integer,Integer> entry:colors.entrySet())if(entry.getValue()>largest){coreColor=entry.getKey();largest=entry.getValue();}
        for(int y=(int)Math.floor(bounds.getMinY()*density);y<Math.ceil(bounds.getMaxY()*density);y++)
            for(int x=(int)Math.floor(bounds.getMinX()*density);x<Math.ceil(bounds.getMaxX()*density);x++)
                if(glyph.contains((x+.5)/density,(y+.5)/density)&&bare.getRGB(x,y)==coreColor){
                    count++;if(bare.getRGB(x,y)!=overlaid.getRGB(x,y))changed++;
                }
        check(count>8,"actual glyph protection fixture has no numeral core");
        check(changed==0,"identity rim paints over actual numeral core: "+changed+"/"+count);
        checks+=2;
    }
    static void rimAndEraser(DoodleStroke mark, int density, BufferedImage bare, String context) throws Exception {
        Shape shape = (Shape)invoke("projectedDoodlePath", new Class[]{DoodleStroke.class, int.class, int.class}, mark, p.getWidth(), p.getHeight());
        float width = (Float)invoke("doodleStrokePixelWidth", new Class[]{DoodleStroke.class, int.class, int.class}, mark, p.getWidth(), p.getHeight());
        Shape rim = (Shape)invoke("doodleOuterRim", new Class[]{DoodleStroke.class, Shape.class, float.class, float.class, float.class}, mark, shape, width, .3f, .8f);
        Rectangle2D bounds = shape.getBounds2D(), outline = rim.getBounds2D();
        // A numeral such as 5 is asymmetric. Subtracting its footprint can change a
        // retained rim's bounding-box center without moving any of its contour.
        // Inspect the product's uncut contour with only the glyph mask unavailable;
        // no paint runs or input events occur while that cache reference is cleared.
        Object layout = read("doodleCandidateGlyphLayout"); Shape uncut;
        try {
            write("doodleCandidateGlyphLayout",null);
            uncut=(Shape)invoke("doodleOuterRim",new Class[]{DoodleStroke.class,Shape.class,float.class,float.class,float.class},
                    mark,shape,width,.3f,.8f);
        } finally { write("doodleCandidateGlyphLayout",layout); }
        Rectangle2D rawBounds=uncut.getBounds2D();
        check(Math.abs(bounds.getCenterX()-rawBounds.getCenterX())<.001
                &&Math.abs(bounds.getCenterY()-rawBounds.getCenterY())<.001,"uncut rim uses another center");
        Area outside=new Area(rim);outside.subtract(new Area(uncut));
        double outsideArea=areaSize(outside);
        check(outsideArea<.00001,"retained rim moved outside its centered source contour: "+outsideArea);checks+=2;
        if(mark.getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS) {
            Shape glyph=displayedGlyph(mark,bounds,density);
            Area protectedGlyph=new Area(glyph);
            protectedGlyph.add(new Area(new BasicStroke(1f).createStrokedShape(glyph)));
            Area expected=new Area(uncut);expected.subtract(protectedGlyph);
            double dx=outline.getCenterX()-bounds.getCenterX(),dy=outline.getCenterY()-bounds.getCenterY();
            if(Math.abs(dx)>=.001||Math.abs(dy)>=.001)
                System.out.println("RIM clipped bounds "+context+" dx="+dx+" dy="+dy+"; raw contour center unchanged");
            check(!expected.isEmpty(),"glyph protection removed the whole expected rim");
            check(sameArea(expected,rim),"rim differs from centered contour minus the actual displayed glyph");
            // A translated outline must fail even if a future accidental shift is
            // hidden by an asymmetric remaining bounding box.
            check(!sameArea(expected,AffineTransform.getTranslateInstance(.5,0).createTransformedShape(rim)),
                    "rim-shift negative control failed");checks+=3;
            glyphSurvivesRim(glyph,rim,bare,density);
        } else {check(sameArea(uncut,rim),"ordinary circle rim no longer uses its centered annulus");checks++;}
        p.setDoodleWholeStrokeEraser(true);
        Point2D edge = ((java.awt.geom.AffineTransform)invoke("doodleProjection", new Class[]{DoodleStroke.class, int.class, int.class},
                mark, p.getWidth(), p.getHeight())).transform(new Point2D.Double(mark.getPoints().get(0).getX(), mark.getPoints().get(0).getY()), null);
        Shape eraser = new Ellipse2D.Double(edge.getX() - .15, edge.getY() - .15, .3, .3);
        DoodleGeometry.ErasurePreview cut = (DoodleGeometry.ErasurePreview)invoke("subtractVisibleDoodleInk", new Class[]{java.util.List.class, Shape.class}, ink(), eraser);
        check(cut.removed.stream().anyMatch(s -> s.getAnchorCell() == mark.getAnchorCell() && s.getAnchorDigit() == mark.getAnchorDigit()), "eraser misses visible glyph-centered arm");
        p.setDoodleWholeStrokeEraser(false); checks++;
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        try { edt(() -> {
            ApplicationAppearance.initialize(AppearanceMode.LIGHT); initialize();
            Font original = Options.getInstance().getDefaultCandidateFont();
            boolean originalFilterOr = Options.getInstance().isUseOrInsteadOfAndForFilter();
            Options.getInstance().setUseOrInsteadOfAndForFilter(true);
            Path output = Paths.get(System.getProperty("hodoku.probe.output", "/tmp/hodoku-cross-glyph")); Files.createDirectories(output);
            for (AppearanceMode theme : new AppearanceMode[]{AppearanceMode.LIGHT, AppearanceMode.DARK})
                for (int style = 0; style < 4; style++) for (int size : new int[]{630, 810, 1134}) for (int density : new int[]{1, 2}) {
                    ApplicationAppearance.initialize(theme); p.setAnnotationTool(AnnotationTool.DOODLE); p.clearDoodlesWithUndo(); p.getSudoku().set(GroupedChainProbe.blank());
                    p.getCellZoomPanel().selectPaletteGroup(0); p.setSize(size, size); p.setDoodleWidthIndex(1);
                    Options.getInstance().setDefaultCandidateFont(new Font(style == 2 ? "Arial" : original.getName(), style == 1 ? Font.BOLD : Font.PLAIN, original.getSize()));
                    boolean[] values = (boolean[])read("showHintCellValues"); Arrays.fill(values, style == 3); write("showBivalueCells", style == 3);
                    if (style == 3) for (int digit = 1; digit <= 9; digit++) for (int value = 1; value <= 9; value++)
                        if (value != digit && value != digit % 9 + 1) p.getSudoku().delCandidate(cell(digit), value);
                    BufferedImage bare = paint(density); Point2D[] numerals = new Point2D[10];
                    for (int digit = 1; digit <= 9; digit++) numerals[digit] = inkCenter(bare, digit, density, theme == AppearanceMode.DARK);
                    for (int digit = 1; digit <= 9; digit++) mark(cell(digit), digit, DoodleStroke.MARK_FALSE_CROSS, 0);
                    p.cancelDoodleHypothesisStarts(); BufferedImage marked = paint(density);
                    Object layout = read("doodleCandidateGlyphLayout"); paint(density);
                    check(layout == read("doodleCandidateGlyphLayout"), "ordinary repaint rebuilt glyph layout");
                    for (int digit = 1; digit <= 9; digit++) {
                        check(((boolean[])read("doodleCandidateBold"))[cell(digit) * 10 + digit] == (style == 3), "overlap font fixture did not use actual bold branch: style=" + style + " digit=" + digit);
                        compare(path(find(cell(digit), digit, DoodleStroke.MARK_FALSE_CROSS, 0)), numerals[digit], theme + " style=" + style + " size=" + size + " density=" + density + " digit=" + digit);
                        compare(markPixels(marked, numerals[digit], density), numerals[digit], "actual cross pixels digit=" + digit);
                    }
                    rimAndEraser(find(cell(5), 5, DoodleStroke.MARK_FALSE_CROSS, 0),density,bare,
                            theme+" style="+style+" size="+size+" density="+density);
                    Rectangle2D compact = path(find(cell(5), 5, DoodleStroke.MARK_FALSE_CROSS, 0));
                    float compactWidth = (Float)invoke("doodleStrokePixelWidth", new Class[]{DoodleStroke.class, int.class, int.class},
                            find(cell(5), 5, DoodleStroke.MARK_FALSE_CROSS, 0), size, size);
                    for (AnnotationTool tool : AnnotationTool.values()) {
                        p.setAnnotationTool(tool); paint(density);
                        check(layout == read("doodleCandidateGlyphLayout"), "tool switch rebuilt glyph table: " + tool);
                        for (int digit = 1; digit <= 9; digit++) compare(path(find(cell(digit), digit, DoodleStroke.MARK_FALSE_CROSS, 0)),
                                numerals[digit], "cross " + tool + " " + theme + " size=" + size + " digit=" + digit);
                        check(compact.equals(path(find(cell(5), 5, DoodleStroke.MARK_FALSE_CROSS, 0))), "tool enlarged/moved cross: " + tool);
                        check(compactWidth == (Float)invoke("doodleStrokePixelWidth", new Class[]{DoodleStroke.class, int.class, int.class},
                                find(cell(5), 5, DoodleStroke.MARK_FALSE_CROSS, 0), size, size), "tool thickened cross: " + tool);
                        checks += 3;
                    }
                    p.setReplayReadOnly(true); paint(density);
                    check(compact.equals(path(find(cell(5), 5, DoodleStroke.MARK_FALSE_CROSS, 0))), "read-only enlarged/moved cross");
                    for (int digit = 1; digit <= 9; digit++) compare(path(find(cell(digit), digit, DoodleStroke.MARK_FALSE_CROSS, 0)), numerals[digit], "read-only cross");
                    check(layout == read("doodleCandidateGlyphLayout"), "read-only rebuilt glyph table"); checks += 2;
                    p.setReplayReadOnly(false);
                    p.setAnnotationTool(AnnotationTool.DOODLE); p.clearDoodlesWithUndo();
                    for (int digit = 1; digit <= 9; digit++) mark(cell(digit), digit, DoodleStroke.MARK_TRUE_CIRCLE, 0);
                    p.cancelDoodleHypothesisStarts(); BufferedImage circles = paint(density);
                    for (int digit = 1; digit <= 9; digit++) compare(markPixels(circles, numerals[digit], density), numerals[digit],
                            "actual circle pixels " + theme + " style=" + style + " size=" + size + " density=" + density + " digit=" + digit);
                    for (AnnotationTool tool : AnnotationTool.values()) {
                        p.setAnnotationTool(tool); paint(density);
                        check(layout == read("doodleCandidateGlyphLayout"), "circle tool switch rebuilt glyph table: " + tool);
                        for (int digit = 1; digit <= 9; digit++) compare(path(find(cell(digit), digit, DoodleStroke.MARK_TRUE_CIRCLE, 0)),
                                numerals[digit], "circle " + tool + " " + theme + " size=" + size + " digit=" + digit);
                        checks++;
                    }
                    p.setReplayReadOnly(true); paint(density);
                    for (int digit = 1; digit <= 9; digit++) compare(path(find(cell(digit), digit, DoodleStroke.MARK_TRUE_CIRCLE, 0)), numerals[digit], "read-only circle");
                    p.setReplayReadOnly(false); p.setAnnotationTool(AnnotationTool.DOODLE);
                    rimAndEraser(find(cell(5), 5, DoodleStroke.MARK_TRUE_CIRCLE, 0),density,bare,
                            theme+" circle style="+style+" size="+size+" density="+density);
                    for (int hit = 0; hit < 1000; hit++) invoke("doodleProjection", new Class[]{DoodleStroke.class, int.class, int.class},
                            find(cell(5), 5, DoodleStroke.MARK_TRUE_CIRCLE, 0), size, size);
                    check(layout == read("doodleCandidateGlyphLayout"), "repeated hit projections rebuilt glyph table"); checks++;
                    if (style == 0 && density == 2) {
                        String name = theme.name().toLowerCase() + "-" + size;
                        ImageIO.write(bare, "png", output.resolve(name + "-digits.png").toFile());
                        ImageIO.write(marked, "png", output.resolve(name + "-crosses.png").toFile());
                        ImageIO.write(circles, "png", output.resolve(name + "-circles.png").toFile());
                    }
                    System.out.println("PASS glyph pixels " + theme + " style=" + style + " size=" + size + " density=" + density);
                }
            Options.getInstance().setDefaultCandidateFont(original); Arrays.fill((boolean[])read("showHintCellValues"), false); write("showBivalueCells", false);
            Options.getInstance().setUseOrInsteadOfAndForFilter(originalFilterOr);
            p.clearDoodlesWithUndo(); p.getSudoku().set(GroupedChainProbe.blank()); paint(2);
            mark(cell(5), 5, DoodleStroke.MARK_FALSE_CROSS, 0); mark(cell(5), 5, DoodleStroke.MARK_FALSE_CROSS, 1); paint(2);
            Rectangle2D a = path(find(cell(5), 5, DoodleStroke.MARK_FALSE_CROSS, 0)), b = path(find(cell(5), 5, DoodleStroke.MARK_FALSE_CROSS, 1));
            check(a.equals(b), "shared source cross centers differ"); checks++;
            System.out.println("PASS " + checks + " glyph/rim/eraser/cache/shared checks; worst logical pixel distance x=" + worstX + " y=" + worstY);
            return null;
        }); } catch (Throwable error) { error.printStackTrace(); System.exit(1); }
        finally { if (f != null) edt(() -> { f.dispose(); return null; }); } System.exit(0);
    }
}
