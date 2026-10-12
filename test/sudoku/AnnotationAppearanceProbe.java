/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Contrast and source-preservation checks for annotation colors before board integration. */
public final class AnnotationAppearanceProbe {
    private static int checks;

    public static void main(String[] args) {
        Color[] savedPalette = Options.getInstance().getColoringColors().clone();
        Color[] sources = {new Color(255, 245, 60), new Color(255, 182, 220),
                new Color(90, 230, 185), new Color(200, 230, 255), new Color(0, 135, 145),
                new Color(85, 35, 160), Color.WHITE, Color.BLACK, new Color(255, 200, 80, 90)};
        for (AppearanceMode mode : new AppearanceMode[]{AppearanceMode.LIGHT, AppearanceMode.DARK}) {
            ApplicationAppearance.initialize(mode);
            SudokuAppearancePalette palette = SudokuAppearancePalette.forRendering(false);
            Color[] backgrounds = {palette.getDefaultCellColor(), palette.getAlternateCellColor(),
                    new Color(245, 178, 205), new Color(125, 125, 125), Color.WHITE, Color.BLACK};
            for (Color source : sources) {
                int original = source.getRGB();
                Color fill = palette.getUserChainNodeFillColor(source);
                require(fill.getAlpha() == 255, "node fill is not opaque");
                require(contrast(painted(fill, backgrounds[0]), Color.WHITE) >= 4.5,
                        "white digit cannot contrast against painted node fill");
                require(fill.getRed() <= source.getRed() && fill.getGreen() <= source.getGreen()
                        && fill.getBlue() <= source.getBlue(), "node fill was lightened");
                if (contrast(source, Color.WHITE) >= 4.5)
                    require((fill.getRGB() & 0xffffff) == (original & 0xffffff), "readable source was recolored");
                float[] before = Color.RGBtoHSB(source.getRed(), source.getGreen(), source.getBlue(), null);
                float[] after = Color.RGBtoHSB(fill.getRed(), fill.getGreen(), fill.getBlue(), null);
                if (before[1] > 0.1f) {
                    double hueDistance = Math.abs(before[0] - after[0]);
                    require(Math.min(hueDistance, 1.0 - hueDistance) < 0.01,
                            "node fill changed source hue");
                }
                Color same = new Color(source.getRed(), source.getGreen(), source.getBlue());
                Color near = new Color(Math.max(0, source.getRed() - 5),
                        Math.max(0, source.getGreen() - 5), Math.max(0, source.getBlue() - 5));
                for (Color background : new Color[]{same, near}) {
                    Color separator = palette.getAnnotationSeparator(same, background);
                    require(contrast(painted(separator, background), background) >= 3.0,
                            "same/near-color separator disappears on its underlay");
                    require(contrast(separator, same) >= 3.0,
                            "same/near-color separator disappears against source ink");
                }
                for (Color background : backgrounds) {
                    Color stroke = palette.getAnnotationStrokeColor(source, background);
                    require(contrast(painted(stroke, background), background) >= 3.0,
                            "annotation stroke lacks underlay contrast");
                    if (contrast(same, background) >= 3.0)
                        require(stroke.equals(same), "already readable stroke source was recolored");
                    require(contrast(palette.getAnnotationSeparator(same, background), background) >= 3.0,
                            "separator lacks actual-background contrast");
                }
                require(source.getRGB() == original, "source color was mutated");
            }
        }
        require(Arrays.equals(savedPalette, Options.getInstance().getColoringColors()),
                "display adjustment changed the saved palette");
        System.out.println("PASS: " + checks + " annotation color checks; opaque white-digit fills, "
                + "same/near-color separators, light/dark underlays, source hue and palette preservation");
    }

    private static Color painted(Color foreground, Color background) {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(background); graphics.fillRect(0, 0, 1, 1);
        graphics.setColor(foreground); graphics.fillRect(0, 0, 1, 1); graphics.dispose();
        return new Color(image.getRGB(0, 0));
    }

    private static double contrast(Color first, Color second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * linear(color.getRed()) + 0.7152 * linear(color.getGreen())
                + 0.0722 * linear(color.getBlue());
    }

    private static double linear(int value) {
        double channel = value / 255.0;
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
