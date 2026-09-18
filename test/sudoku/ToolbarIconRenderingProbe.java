/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JToggleButton;

/** Pixel-level optical alignment checks for the unified main-toolbar glyphs. */
public final class ToolbarIconRenderingProbe {

	private ToolbarIconRenderingProbe() {
	}

	public static void main(String[] args) {
		for (AppearanceMode mode : new AppearanceMode[] { AppearanceMode.LIGHT, AppearanceMode.DARK }) {
			ApplicationAppearance.initialize(mode);
			Icon digit = new CandidateFilterIcon(3, true, 32);
			Icon xy = ToolbarIcons.xyFilter();
			Icon xyz = ToolbarIcons.xyzFilter();
			double digitCenter = alphaCentroidY(digit);
			double xyCenter = alphaCentroidY(xy);
			double xyzCenter = alphaCentroidY(xyz);
			require(Math.abs(xyCenter - digitCenter) <= 1.0,
					"XY filter is not optically centered with the digit filters in " + mode
							+ ": digit=" + digitCenter + ", xy=" + xyCenter);
			require(Math.abs(xyzCenter - digitCenter) <= 1.0,
					"XYZ filter is not optically centered with the digit filters in " + mode
							+ ": digit=" + digitCenter + ", xyz=" + xyzCenter);
			JToggleButton selectedFilter = new JToggleButton();
			selectedFilter.setSelected(true);
			int selectedForeground = SudokuAppearancePalette.forRendering(false)
					.getControlSelectionForeground().getRGB();
			require(containsRgb(xy, selectedFilter, selectedForeground)
					&& containsRgb(xyz, selectedFilter, selectedForeground),
					"XY/XYZ filters do not share the digit filters' selected foreground in " + mode);

			Icon[] hintActions = {
				ToolbarIcons.vagueHint(), ToolbarIcons.concreteHint(), ToolbarIcons.nextStep(),
				ToolbarIcons.execute(), ToolbarIcons.abort()
			};
			double actionCenter = alphaCentroidY(hintActions[0]);
			for (Icon icon : hintActions) {
				require(icon.getIconWidth() == 32 && icon.getIconHeight() == 32,
						"hint/action icon escaped the shared 32-pixel canvas");
				double iconCenter = alphaCentroidY(icon);
				require(Math.abs(iconCenter - actionCenter) <= 0.8,
						"hint/action icons do not share one visual center in " + mode
								+ ": baseline=" + actionCenter + ", icon=" + iconCenter);
			}
		}
		require(new AnnotationToolIcon(AnnotationTool.DEFAULT_MOUSE, 27).getIconWidth() == 27,
				"toolbar annotation icon did not retain the reduced visual size");
		System.out.println("Toolbar icon rendering checks passed");
		System.exit(0);
	}

	private static double alphaCentroidY(Icon icon) {
		BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		icon.paintIcon(null, graphics, 0, 0);
		graphics.dispose();
		double weightedY = 0.0;
		double alphaTotal = 0.0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int alpha = image.getRGB(x, y) >>> 24;
				weightedY += (y + 0.5) * alpha;
				alphaTotal += alpha;
			}
		}
		require(alphaTotal > 0.0, "toolbar icon painted no visible pixels");
		return weightedY / alphaTotal;
	}

	private static boolean containsRgb(Icon icon, JToggleButton component, int rgb) {
		BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
				BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		icon.paintIcon(component, graphics, 0, 0);
		graphics.dispose();
		int expected = rgb & 0x00ffffff;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) & 0x00ffffff) == expected) return true;
			}
		}
		return false;
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
