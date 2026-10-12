/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/** Test-only paired painting: preserve the exact background/annotation rules while omitting text. */
final class GlyphBackgroundImage extends BufferedImage {
    final BufferedImage background;
    private GlyphBackgroundImage(SudokuPanel panel, int width, int height) {
        super(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = createGraphics();
        try { panel.paint(graphics); } finally { graphics.dispose(); }
        background = background(panel, width, height);
    }
    static BufferedImage capture(SudokuPanel panel, int width, int height) {
        return new GlyphBackgroundImage(panel, width, height);
    }
    static BufferedImage background(SudokuPanel panel, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = new WithoutText(image.createGraphics());
        try { panel.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }
    /** Only text is suppressed. create() keeps the wrapper across Swing's graphics copies. */
    private static final class WithoutText extends Graphics2D {
        private final Graphics2D g;
        WithoutText(Graphics2D delegate) { g = delegate; }
        @Override public Graphics create() { return new WithoutText((Graphics2D)g.create()); }
        @Override public void drawString(String s, int x, int y) {}
        @Override public void drawString(String s, float x, float y) {}
        @Override public void drawString(AttributedCharacterIterator s, int x, int y) {}
        @Override public void drawString(AttributedCharacterIterator s, float x, float y) {}
        @Override public void drawGlyphVector(GlyphVector v, float x, float y) {}
        @Override public void drawChars(char[] data, int offset, int length, int x, int y) {}
        @Override public void drawBytes(byte[] data, int offset, int length, int x, int y) {}
        @Override public void draw(Shape s) { g.draw(s); }
        @Override public void fill(Shape s) { g.fill(s); }
        @Override public boolean drawImage(Image i, AffineTransform t, ImageObserver o) { return g.drawImage(i,t,o); }
        @Override public void drawImage(BufferedImage i, BufferedImageOp op, int x, int y) { g.drawImage(i,op,x,y); }
        @Override public void drawRenderedImage(RenderedImage i, AffineTransform t) { g.drawRenderedImage(i,t); }
        @Override public void drawRenderableImage(RenderableImage i, AffineTransform t) { g.drawRenderableImage(i,t); }
        @Override public boolean hit(Rectangle r, Shape s, boolean stroke) { return g.hit(r,s,stroke); }
        @Override public GraphicsConfiguration getDeviceConfiguration() { return g.getDeviceConfiguration(); }
        @Override public void setComposite(Composite c) { g.setComposite(c); }
        @Override public void setPaint(Paint p) { g.setPaint(p); }
        @Override public void setStroke(Stroke s) { g.setStroke(s); }
        @Override public void setRenderingHint(RenderingHints.Key k, Object v) { g.setRenderingHint(k,v); }
        @Override public Object getRenderingHint(RenderingHints.Key k) { return g.getRenderingHint(k); }
        @Override public void setRenderingHints(Map<?,?> h) { g.setRenderingHints(h); }
        @Override public void addRenderingHints(Map<?,?> h) { g.addRenderingHints(h); }
        @Override public RenderingHints getRenderingHints() { return g.getRenderingHints(); }
        @Override public void translate(int x, int y) { g.translate(x,y); }
        @Override public void translate(double x, double y) { g.translate(x,y); }
        @Override public void rotate(double a) { g.rotate(a); }
        @Override public void rotate(double a, double x, double y) { g.rotate(a,x,y); }
        @Override public void scale(double x, double y) { g.scale(x,y); }
        @Override public void shear(double x, double y) { g.shear(x,y); }
        @Override public void transform(AffineTransform t) { g.transform(t); }
        @Override public void setTransform(AffineTransform t) { g.setTransform(t); }
        @Override public AffineTransform getTransform() { return g.getTransform(); }
        @Override public Paint getPaint() { return g.getPaint(); }
        @Override public Composite getComposite() { return g.getComposite(); }
        @Override public void setBackground(Color c) { g.setBackground(c); }
        @Override public Color getBackground() { return g.getBackground(); }
        @Override public Stroke getStroke() { return g.getStroke(); }
        @Override public void clip(Shape s) { g.clip(s); }
        @Override public FontRenderContext getFontRenderContext() { return g.getFontRenderContext(); }
        @Override public Color getColor() { return g.getColor(); }
        @Override public void setColor(Color c) { g.setColor(c); }
        @Override public void setPaintMode() { g.setPaintMode(); }
        @Override public void setXORMode(Color c) { g.setXORMode(c); }
        @Override public Font getFont() { return g.getFont(); }
        @Override public void setFont(Font f) { g.setFont(f); }
        @Override public FontMetrics getFontMetrics(Font f) { return g.getFontMetrics(f); }
        @Override public Rectangle getClipBounds() { return g.getClipBounds(); }
        @Override public void clipRect(int x, int y, int w, int h) { g.clipRect(x,y,w,h); }
        @Override public void setClip(int x, int y, int w, int h) { g.setClip(x,y,w,h); }
        @Override public Shape getClip() { return g.getClip(); }
        @Override public void setClip(Shape s) { g.setClip(s); }
        @Override public void copyArea(int x, int y, int w, int h, int dx, int dy) { g.copyArea(x,y,w,h,dx,dy); }
        @Override public void drawLine(int x, int y, int x2, int y2) { g.drawLine(x,y,x2,y2); }
        @Override public void fillRect(int x, int y, int w, int h) { g.fillRect(x,y,w,h); }
        @Override public void clearRect(int x, int y, int w, int h) { g.clearRect(x,y,w,h); }
        @Override public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) { g.drawRoundRect(x,y,w,h,aw,ah); }
        @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) { g.fillRoundRect(x,y,w,h,aw,ah); }
        @Override public void drawOval(int x, int y, int w, int h) { g.drawOval(x,y,w,h); }
        @Override public void fillOval(int x, int y, int w, int h) { g.fillOval(x,y,w,h); }
        @Override public void drawArc(int x, int y, int w, int h, int a, int b) { g.drawArc(x,y,w,h,a,b); }
        @Override public void fillArc(int x, int y, int w, int h, int a, int b) { g.fillArc(x,y,w,h,a,b); }
        @Override public void drawPolyline(int[] x, int[] y, int n) { g.drawPolyline(x,y,n); }
        @Override public void drawPolygon(int[] x, int[] y, int n) { g.drawPolygon(x,y,n); }
        @Override public void fillPolygon(int[] x, int[] y, int n) { g.fillPolygon(x,y,n); }
        @Override public boolean drawImage(Image i, int x, int y, ImageObserver o) { return g.drawImage(i,x,y,o); }
        @Override public boolean drawImage(Image i, int x, int y, int w, int h, ImageObserver o) { return g.drawImage(i,x,y,w,h,o); }
        @Override public boolean drawImage(Image i, int x, int y, Color c, ImageObserver o) { return g.drawImage(i,x,y,c,o); }
        @Override public boolean drawImage(Image i, int x, int y, int w, int h, Color c, ImageObserver o) { return g.drawImage(i,x,y,w,h,c,o); }
        @Override public boolean drawImage(Image i, int x, int y, int x2, int y2, int a, int b, int a2, int b2, ImageObserver o) { return g.drawImage(i,x,y,x2,y2,a,b,a2,b2,o); }
        @Override public boolean drawImage(Image i, int x, int y, int x2, int y2, int a, int b, int a2, int b2, Color c, ImageObserver o) { return g.drawImage(i,x,y,x2,y2,a,b,a2,b2,c,o); }
        @Override public void dispose() { g.dispose(); }
    }
}
