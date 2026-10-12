/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Verifies user-chain route obstruction and node-over-edge rendering. */
public final class UserChainRenderingProbe {
	private static final String PUZZLE =
			"530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    private static long eventTime = System.currentTimeMillis();

	private UserChainRenderingProbe() {
	}

	public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
		final Throwable[] failure = new Throwable[1];
		SwingUtilities.invokeAndWait(new Runnable() {
			@Override
			public void run() {
				MainFrame frame = null;
				try {
					ApplicationAppearance.initialize(AppearanceMode.LIGHT);
					frame = new MainFrame(null);
					SudokuPanel panel = frame.getSudokuPanel();
					panel.setSize(900, 900);
					panel.setSudoku(PUZZLE);
					panel.setShowCandidates(true);
					paint(panel);

					int cellSize = panel.getX(0, 1) - panel.getX(0, 0);
					int[][] line = findCollinearCandidates(panel);
					int[] sideNode = findCandidateOutsideRow(panel, line[1][0]);
					panel.setAnnotationTool(AnnotationTool.FREE_CHAIN);
					panel.getCellZoomPanel().selectPaletteGroup(1);
					clickCandidate(panel, line[1][0], line[1][1], line[1][2], cellSize);
					clickCandidate(panel, sideNode[0], sideNode[1], sideNode[2], cellSize);
					press(panel, KeyEvent.VK_ENTER);

					panel.getCellZoomPanel().selectPaletteGroup(0);
					clickCandidate(panel, line[0][0], line[0][1], line[0][2], cellSize);
                    BufferedImage withoutCrossing = paint(panel);
					clickCandidate(panel, line[2][0], line[2][1], line[2][2], cellSize);
					BufferedImage rendered = paint(panel);
                    Path output = Paths.get(System.getProperty("hodoku.probe.output", System.getProperty("java.io.tmpdir")));
                    Files.createDirectories(output);
                    ImageIO.write(withoutCrossing,"png",output.resolve("node-without-crossing.png").toFile());
                    ImageIO.write(rendered,"png",output.resolve("node-with-crossing.png").toFile());
                    verifyNodeAndRoute(panel, line, cellSize, withoutCrossing, rendered);
				} catch (Throwable ex) {
					failure[0] = ex;
				} finally {
					if (frame != null) {
						frame.dispose();
					}
				}
			}
		});
		if (failure[0] != null) {
			throw new AssertionError("user-chain rendering check failed", failure[0]);
		}
		System.out.println("User-chain rendering checks passed");
		System.exit(0);
	}

	private static BufferedImage paint(SudokuPanel panel) {
		BufferedImage image = new BufferedImage(900, 900, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		panel.paint(graphics);
		graphics.dispose();
		return image;
	}

	private static int[][] findCollinearCandidates(SudokuPanel panel) {
		for (int row = 0; row < Sudoku2.UNITS; row++) {
			for (int candidate = 1; candidate <= Sudoku2.UNITS; candidate++) {
				int[][] result = new int[3][3];
				int found = 0;
				for (int col = 0; col < Sudoku2.UNITS && found < result.length; col++) {
					int index = Sudoku2.getIndex(row, col);
					if (panel.getSudoku().isCandidate(index, candidate)) {
						result[found++] = new int[] { row, col, candidate };
					}
				}
				if (found == result.length) {
					return result;
				}
			}
		}
		throw new AssertionError("probe puzzle has no three collinear candidates");
	}

	private static int[] findCandidateOutsideRow(SudokuPanel panel, int excludedRow) {
		for (int index = 0; index < Sudoku2.LENGTH; index++) {
			if (Sudoku2.getRow(index) == excludedRow) {
				continue;
			}
			int[] candidates = panel.getSudoku().getAllCandidates(index);
			if (candidates.length > 0) {
				return new int[] { Sudoku2.getRow(index), Sudoku2.getCol(index), candidates[0] };
			}
		}
		throw new AssertionError("probe puzzle has no side candidate");
	}

	private static void clickCandidate(SudokuPanel panel, int row, int col, int candidate,
			int cellSize) {
        eventTime += 300; // Deliberate separate clicks, outside the L double-click window.
		Point2D.Double center = candidateCenter(panel, row, col, candidate, cellSize);
		int x = (int) Math.round(center.x);
		int y = (int) Math.round(center.y);
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_PRESSED, x, y));
		panel.dispatchEvent(mouse(panel, MouseEvent.MOUSE_RELEASED, x, y));
	}

	private static Point2D.Double candidateCenter(SudokuPanel panel, int row, int col,
			int candidate, int cellSize) {
		double third = cellSize / 3.0;
		return new Point2D.Double(
				panel.getX(row, col) + ((candidate - 1) % 3) * third + third / 2.0,
				panel.getY(row, col) + ((candidate - 1) / 3) * third + third / 2.0);
	}

	private static MouseEvent mouse(SudokuPanel panel, int id, int x, int y) {
		return new MouseEvent(panel, id, eventTime++, 0, x, y, 1, false,
				MouseEvent.BUTTON1);
	}

	private static void press(SudokuPanel panel, int keyCode) {
		KeyEvent event = new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
				keyCode, KeyEvent.CHAR_UNDEFINED);
		for (java.awt.event.KeyListener listener : panel.getKeyListeners()) {
			listener.keyPressed(event);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

    private static void verifyNodeAndRoute(SudokuPanel panel, int[][] line, int cellSize,
            BufferedImage before, BufferedImage after) throws Exception {
        int middleCell = Sudoku2.getIndex(line[1][0], line[1][1]), digit = line[1][2];
        Point2D.Double middle = (Point2D.Double)invoke(panel,"getCandKoord",
                new Class<?>[]{int.class,int.class,int.class},middleCell,digit,cellSize);
        Field diameterField=SudokuPanel.class.getDeclaredField("lastUserChainRouteDiameter");
        diameterField.setAccessible(true);double routeDiameter=diameterField.getDouble(panel);
        double diameter=(Double)invoke(panel,"candidateNodeDisplayDiameter",
                new Class<?>[]{int.class,int.class,double.class,boolean.class},middleCell,digit,routeDiameter,true);
        int same=0, white=0, colored=0;
        for(int y=(int)(middle.y-diameter/2);y<=middle.y+diameter/2;y++)
            for(int x=(int)(middle.x-diameter/2);x<=middle.x+diameter/2;x++) {
                if(middle.distance(x+.5,y+.5)>diameter/2-2)continue;
                require(before.getRGB(x,y)==after.getRGB(x,y),"crossing edge changed opaque node interior at "+x+","+y);
                same++;Color pixel=new Color(after.getRGB(x,y));
                if(pixel.getRed()>235&&pixel.getGreen()>235&&pixel.getBlue()>235)white++;
                if(Math.max(pixel.getRed(),Math.max(pixel.getGreen(),pixel.getBlue()))
                        -Math.min(pixel.getRed(),Math.min(pixel.getGreen(),pixel.getBlue()))>30)colored++;
            }
        require(same>30&&white>3&&colored>10,"node fixture lacks an opaque colored interior and white numeral");
        List<?> segments=(List<?>)invoke(panel,"collectUserChainSegments",new Class<?>[]{});
        List<?> obstacles=(List<?>)invoke(panel,"collectUserChainObstacles",new Class<?>[]{});
        ChainRouteGeometry.Route route=null;
        int fromCell=Sudoku2.getIndex(line[0][0],line[0][1]), toCell=Sudoku2.getIndex(line[2][0],line[2][1]);
        for(Object segment:segments) {
            Field fromField=segment.getClass().getDeclaredField("from"),toField=segment.getClass().getDeclaredField("to");
            fromField.setAccessible(true);toField.setAccessible(true);
            UserChainNode from=(UserChainNode)fromField.get(segment),to=(UserChainNode)toField.get(segment);
            if(from.contains(fromCell,digit)&&to.contains(toCell,digit))
                route=(ChainRouteGeometry.Route)invoke(panel,"userChainRoute",
                        new Class<?>[]{segment.getClass(),double.class,List.class,double.class},segment,routeDiameter,obstacles,0.0);
        }
        require(route!=null&&route.isCurved(),"actual crossing relation did not route around the middle candidate");
        int routePixels=0;
        for(int sample=2;sample<=8;sample++) {
            double t=sample/10.0,u=1-t;
            double x=u*u*u*route.getStart().x+3*u*u*t*route.getControl1().x
                    +3*u*t*t*route.getControl2().x+t*t*t*route.getEnd().x;
            double y=u*u*u*route.getStart().y+3*u*u*t*route.getControl1().y
                    +3*u*t*t*route.getControl2().y+t*t*t*route.getEnd().y;
            for(int yy=(int)y-2;yy<=(int)y+2;yy++)for(int xx=(int)x-2;xx<=(int)x+2;xx++)
                if(before.getRGB(xx,yy)!=after.getRGB(xx,yy))routePixels++;
        }
        require(routePixels>10,"computed crossing route did not produce visible relation ink");
        Point2D.Double from=(Point2D.Double)invoke(panel,"getCandKoord",
                new Class<?>[]{int.class,int.class,int.class},fromCell,digit,cellSize);
        Point2D.Double to=(Point2D.Double)invoke(panel,"getCandKoord",
                new Class<?>[]{int.class,int.class,int.class},toCell,digit,cellSize);
        require(route.getStart().distance(from)>routeDiameter/2&&route.getEnd().distance(to)>routeDiameter/2,
                "relation endpoints entered the solid candidate nodes");
    }

    private static Object invoke(SudokuPanel panel,String name,Class<?>[] types,Object... args) throws Exception {
        Method method=SudokuPanel.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(panel,args);
    }
}
