package sudoku;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;

/** Focused Swing-event coverage for preview, Command outlines, apply, cleanup and replay. */
public final class DoodleConclusionWorkflowProbe {
    private static MainFrame frame;
    private static SudokuPanel panel;
    private static ReplayController replay;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
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
    private static Object read(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static Point candidate(int cell, int digit) throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod("getCandKoord", int.class, int.class, int.class);
        method.setAccessible(true);
        Point2D point = (Point2D) method.invoke(panel, cell, digit, (Integer) read("cellSize"));
        return new Point((int) Math.round(point.getX()), (int) Math.round(point.getY()));
    }
    private static void click(Point point, int button, int modifiers) {
        for (int id : new int[] { MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED }) {
            MouseEvent event = new MouseEvent(panel, id, System.currentTimeMillis(), modifiers,
                    point.x, point.y, 1, false, button);
            for (java.awt.event.MouseListener listener : panel.getMouseListeners()) {
                if (id == MouseEvent.MOUSE_PRESSED) listener.mousePressed(event);
                else listener.mouseReleased(event);
            }
        }
    }
    private static boolean hasConflictRed(BufferedImage image, Point center) {
        for (int y = Math.max(0, center.y - 16); y < Math.min(image.getHeight(), center.y + 17); y++) {
            for (int x = Math.max(0, center.x - 16); x < Math.min(image.getWidth(), center.x + 17); x++) {
                int color = image.getRGB(x, y);
                int red = (color >>> 16) & 0xff, green = (color >>> 8) & 0xff, blue = color & 0xff;
                if (red >= 170 && green < 70 && blue < 70) return true;
            }
        }
        return false;
    }
    private static boolean isOutlineBlue(int color) {
        int red = (color >>> 16) & 0xff, green = (color >>> 8) & 0xff, blue = color & 0xff;
        return blue > 190 && green > 65 && red < 100;
    }
    private static int countOutlineBlue(BufferedImage image, Point center, double minRadius, double maxRadius) {
        int count = 0;
        int bound = (int) Math.ceil(maxRadius);
        for (int y = Math.max(0, center.y - bound); y < Math.min(image.getHeight(), center.y + bound + 1); y++) {
            for (int x = Math.max(0, center.x - bound); x < Math.min(image.getWidth(), center.x + bound + 1); x++) {
                double distance = Point.distance(center.x, center.y, x, y);
                if (distance >= minRadius && distance <= maxRadius && isOutlineBlue(image.getRGB(x, y))) count++;
            }
        }
        return count;
    }
    private static boolean hasOutlineBlueNear(BufferedImage image, Point center, double radius) {
        return countOutlineBlue(image, center, 0.0, radius) > 10;
    }
    private static double maxOutlineBlueRadius(BufferedImage image, Point center, int bound) {
        double max = 0.0;
        for (int y = Math.max(0, center.y - bound); y < Math.min(image.getHeight(), center.y + bound + 1); y++) {
            for (int x = Math.max(0, center.x - bound); x < Math.min(image.getWidth(), center.x + bound + 1); x++) {
                if (isOutlineBlue(image.getRGB(x, y))) max = Math.max(max, Point.distance(center.x, center.y, x, y));
            }
        }
        return max;
    }
    private static double candidateProjectionScale(DoodleStroke stroke) throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod("doodleProjection", DoodleStroke.class,
                int.class, int.class);
        method.setAccessible(true);
        java.awt.geom.AffineTransform projection = (java.awt.geom.AffineTransform) method.invoke(panel,
                stroke, panel.getWidth(), panel.getHeight());
        return projection.getScaleX() / ((Integer) read("cellSize")).doubleValue();
    }
    private static DoodleStroke mark(int cell, int digit, int kind, int group) throws Exception {
        @SuppressWarnings("unchecked") List<DoodleStroke> marks = (List<DoodleStroke>) read("doodleStrokes");
        for (DoodleStroke stroke : marks) {
            if (stroke.isStandardCandidateMark() && stroke.getAnchorCell() == cell
                    && stroke.getAnchorDigit() == digit && stroke.getCandidateMarkKind() == kind
                    && stroke.getThoughtGroup() == group) return stroke;
        }
        return null;
    }
    private static void key(int id, int code, int modifiers, char value) {
        panel.handleAnnotationKeyPressed(new KeyEvent(panel, id, System.currentTimeMillis(), modifiers, code, value));
    }
    private static <T> T edt(java.util.concurrent.Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<T>(callable);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    public static void main(String[] args) throws Exception {
        File output = new File(System.getProperty("hodoku.probe.output", "/tmp/hodoku-doodle-conclusion"));
        output.mkdirs();
        Path replayDirectory = new File(output, "replays").toPath();
        Files.createDirectories(replayDirectory);
        System.setProperty("hodoku.replay.dir", replayDirectory.toString());
        Throwable[] failure = { null };
        SwingUtilities.invokeAndWait(() -> {
            try {
                ApplicationAppearance.initialize(AppearanceMode.LIGHT);
                frame = new MainFrame(null);
                panel = frame.getSudokuPanel();
                replay = frame.getReplayController();
                panel.setSize(720, 720);
                panel.setSudoku((String) null);
                panel.getSudoku().setSudoku(new String(new char[81]).replace('\0', '0'));
                panel.setShowCandidates(true);
                panel.setAnnotationTool(AnnotationTool.DOODLE);
                panel.getCellZoomPanel().selectPaletteGroup(0);
                replay.beginSession(new ReplayBoard(panel.getSudoku()));
                panel.paint(new java.awt.image.BufferedImage(720, 720, 1).createGraphics());

                Point c01 = candidate(0, 1);
                Point c02 = candidate(0, 2);
                Point c11 = candidate(1, 1);
                Point c104 = candidate(10, 4);
                Point c115 = candidate(11, 5);
                Point c208 = candidate(20, 8);

                click(c01, MouseEvent.BUTTON3, 0);
                click(c02, MouseEvent.BUTTON3, 0);
                click(c11, MouseEvent.BUTTON3, 0);
                click(c104, MouseEvent.BUTTON3, InputEvent.SHIFT_DOWN_MASK);
                click(c115, MouseEvent.BUTTON3, 0);
                click(c115, MouseEvent.BUTTON3, InputEvent.SHIFT_DOWN_MASK);
                DoodleStroke circle = mark(0, 1, DoodleStroke.MARK_TRUE_CIRCLE, 0);
                DoodleStroke blockedSameCell = mark(0, 2, DoodleStroke.MARK_TRUE_CIRCLE, 0);
                DoodleStroke pairedCircle = mark(11, 5, DoodleStroke.MARK_TRUE_CIRCLE, 0);
                DoodleStroke pairedCross = mark(11, 5, DoodleStroke.MARK_FALSE_CROSS, 0);
                check(circle != null && blockedSameCell != null && pairedCircle != null && pairedCross != null,
                        "standard marks were not created");

                click(c01, MouseEvent.BUTTON1, InputEvent.META_DOWN_MASK);
                check(circle.isConclusionOutlined(), "Command click did not outline true circle");
                click(c02, MouseEvent.BUTTON1, InputEvent.META_DOWN_MASK);
                check(!blockedSameCell.isConclusionOutlined(), "same-cell second outline was accepted");
                click(c11, MouseEvent.BUTTON1, InputEvent.META_DOWN_MASK);
                check(!mark(1, 1, DoodleStroke.MARK_TRUE_CIRCLE, 0).isConclusionOutlined(),
                        "same-digit peer true circle outline was accepted");
                click(c104, MouseEvent.BUTTON1, InputEvent.META_DOWN_MASK);
                check(mark(10, 4, DoodleStroke.MARK_FALSE_CROSS, 0).isConclusionOutlined(),
                        "cross outline was blocked by an unrelated peer circle");
                click(c115, MouseEvent.BUTTON1, InputEvent.META_DOWN_MASK);
                check(!pairedCircle.isConclusionOutlined() && !pairedCross.isConclusionOutlined(),
                        "paired circle/cross candidate accepted a Command outline");

                panel.getCellZoomPanel().selectPaletteGroup(1);
                click(c208, MouseEvent.BUTTON3, 0);
                DoodleStroke otherGroup = mark(20, 8, DoodleStroke.MARK_TRUE_CIRCLE, 1);
                click(c208, MouseEvent.BUTTON1, InputEvent.META_DOWN_MASK);
                check(otherGroup != null && otherGroup.isConclusionOutlined(), "second group's independent outline missing");
                panel.getCellZoomPanel().selectPaletteGroup(0);
                click(c02, MouseEvent.BUTTON3, 0);
                click(c11, MouseEvent.BUTTON3, 0);

                BufferedImage glyphOutline = new BufferedImage(720, 720, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D outlineGraphics = glyphOutline.createGraphics();
                panel.paint(outlineGraphics);
                outlineGraphics.dispose();
                javax.imageio.ImageIO.write(glyphOutline, "png", new File(output, "glyph-outline.png"));
                int cellSize = (Integer) read("cellSize");
                double circleRadius = Math.hypot(circle.getPoints().get(0).getX(), circle.getPoints().get(0).getY()) * cellSize;
                float circleWidth = Math.max(2.0f, cellSize * circle.getWidthFactor());
                DoodleStroke crossOutline = mark(10, 4, DoodleStroke.MARK_FALSE_CROSS, 0);
                check(crossOutline != null && crossOutline.isConclusionOutlined(),
                        "expected an outlined false-cross before measuring its scaled contour");
                check(Math.abs(candidateProjectionScale(crossOutline) - 0.88) < 0.001,
                        "standard false-cross geometry was not scaled down with its anchor projection");
                check(countOutlineBlue(glyphOutline, c01, circleRadius + circleWidth / 2.0 - 0.5,
                                circleRadius + circleWidth / 2.0 + cellSize / 42.0 + 1.5) > 16,
                        "Command outline did not trace the outer edge of the circle glyph");
                check(hasOutlineBlueNear(glyphOutline, c104, circleRadius + cellSize / 3.0),
                        "Command outline did not trace the false-cross glyph");
				double crossSourceRadius = Math.hypot(crossOutline.getPoints().get(0).getX(),
						crossOutline.getPoints().get(0).getY()) * cellSize;
				float crossWidth = circleWidth * 0.88f;
				double crossEdge = Math.max(1.25f, Math.min(2.4f, cellSize / 42.0f)) * 0.88;
				double expectedCrossContour = crossSourceRadius * 0.88 + crossWidth / 2.0 + crossEdge;
				check(Math.abs(maxOutlineBlueRadius(glyphOutline, c104, cellSize / 3) - expectedCrossContour) < 2.0,
                        "selected false-cross contour did not shrink with the glyph");
                double outsideGlyph = cellSize / 4.0;
                check(!isOutlineBlue(glyphOutline.getRGB(c01.x + (int) outsideGlyph, c01.y))
                                && !isOutlineBlue(glyphOutline.getRGB(c01.x - (int) outsideGlyph, c01.y))
                                && !isOutlineBlue(glyphOutline.getRGB(c104.x, c104.y + (int) outsideGlyph))
                                && !isOutlineBlue(glyphOutline.getRGB(c104.x, c104.y - (int) outsideGlyph)),
                        "Command outline extended to a candidate-cell frame");

                List<DoodleStroke> strokes = (List<DoodleStroke>) read("doodleStrokes");
                DoodleStroke freehand = new DoodleStroke(Options.getInstance().getColoringColors()[0], .01f);
                freehand.getPoints().add(new DoodlePoint(.2, .2));
                freehand.getPoints().add(new DoodlePoint(.25, .25));
                freehand.setThoughtGroup(0);
                strokes.add(freehand);

                String boardBeforePreview = TechniqueStepCatalog.createSignature(panel.getSudoku());
                write("lastMousePosition", c01);
                key(KeyEvent.KEY_PRESSED, KeyEvent.VK_SPACE, 0, ' ');
                // drawPage builds the immutable display projection; paint it once while Space is held.
                BufferedImage previewFrame = new BufferedImage(720, 720, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D graphics = previewFrame.createGraphics();
                panel.paint(graphics);
                graphics.dispose();
                Object projection = read("doodleThoughtPreviewProjection");
                @SuppressWarnings("unchecked") java.util.Map<Integer, Integer> filled =
                        (java.util.Map<Integer, Integer>) read(projection, "filledCells");
                @SuppressWarnings("unchecked") java.util.Set<Integer> removed =
                        (java.util.Set<Integer>) read(projection, "removedCandidates");
                @SuppressWarnings("unchecked") java.util.Set<Integer> conflicts =
                        (java.util.Set<Integer>) read(projection, "conflicts");
                check(projection != null && filled.get(0).intValue() == 1,
                        "Space did not project the true circle as a filled value");
                check(removed.contains(Integer.valueOf(104)), "Space did not project the false cross deletion");
                check(conflicts.contains(Integer.valueOf(115)), "circle/cross preview conflict was not marked");
                check(hasConflictRed(previewFrame, candidate(11, 5)),
                        "preview conflict did not render as a bright red cross");
				long[] paintMicros = new long[30];
				for (int i = 0; i < paintMicros.length; i++) {
					Point hover = (i % 2 == 0) ? c01 : c208;
					MouseEvent moved = new MouseEvent(panel, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(),
							0, hover.x, hover.y, 0, false);
					for (java.awt.event.MouseMotionListener listener : panel.getMouseMotionListeners()) listener.mouseMoved(moved);
					long start = System.nanoTime();
					java.awt.Graphics2D movingPaint = new java.awt.image.BufferedImage(720, 720, 1).createGraphics();
					panel.paint(movingPaint);
					movingPaint.dispose();
					paintMicros[i] = (System.nanoTime() - start) / 1000L;
				}
				java.util.Arrays.sort(paintMicros);
				System.out.println("Space preview render: p50=" + paintMicros[15] + "us p95="
						+ paintMicros[28] + "us max=" + paintMicros[29] + "us at 720x720");
                String boardDuringPreview = TechniqueStepCatalog.createSignature(panel.getSudoku());
                check(boardBeforePreview.equals(boardDuringPreview), "preview changed the live board");
                panel.handleKeys(new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                        KeyEvent.VK_F1, KeyEvent.CHAR_UNDEFINED));
                check(panel.getShowHintCellValues()[1] && !panel.getShowHintCellValues()[2],
                        "F1 did not show its ordinary single-candidate filter during preview");
                panel.handleKeys(new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                        KeyEvent.VK_F2, KeyEvent.CHAR_UNDEFINED));
                check(!panel.getShowHintCellValues()[1] && panel.getShowHintCellValues()[2],
                        "F2 did not replace F1 using its ordinary filter behavior during preview: "
                                + java.util.Arrays.toString(panel.getShowHintCellValues())
                                + ", cell0 candidates=" + java.util.Arrays.toString(panel.getSudoku().getAllCandidates(0)));
                check(boardBeforePreview.equals(TechniqueStepCatalog.createSignature(panel.getSudoku()))
                                && read("doodleThoughtPreviewProjection") != null,
                        "F1/F2 during preview changed the board or ended the display projection");
                panel.handleKeys(new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                        KeyEvent.VK_F2, KeyEvent.CHAR_UNDEFINED));
                check(!panel.getShowHintCellValues()[1] && !panel.getShowHintCellValues()[2],
                        "repeating F2 did not clear the filter after the preview test");
                panel.handleAnnotationToolKeyReleased(new KeyEvent(panel, KeyEvent.KEY_RELEASED,
                        System.currentTimeMillis(), 0, KeyEvent.VK_SPACE, ' '));
                check(read("doodleThoughtPreviewProjection") == null, "Space release left the preview active");

                // Keep only the two conclusions used below after testing the conflict overlay.
                click(c115, MouseEvent.BUTTON3, 0);
                click(c115, MouseEvent.BUTTON3, InputEvent.SHIFT_DOWN_MASK);

                int frameCount = replay.session().frames().size();
                panel.getCellZoomPanel().selectPaletteGroup(1);
                check(mark(20, 8, DoodleStroke.MARK_TRUE_CIRCLE, 1) != null,
                        "second-group mark disappeared before applying the other group");
                panel.getCellZoomPanel().selectPaletteGroup(0);
                check(panel.getCellZoomPanel().getPaletteGroup() == 0, "test did not return to group zero");
                key(KeyEvent.KEY_PRESSED, KeyEvent.VK_BACK_SPACE, 0, '\b');
                check(panel.getSudoku().getValue(0) == 1, "Backspace did not apply outlined true circle");
                check(panel.getSudoku().getValue(20) == 0, "Backspace applied another group's framed circle");
                check(!panel.getSudoku().isCandidate(10, 4), "Backspace did not delete outlined false cross candidate");
                check(mark(20, 8, DoodleStroke.MARK_TRUE_CIRCLE, 1) != null,
                        "applying one group erased another group's stored mark: "
                                + strokes.stream().map(s -> s.getThoughtGroup() + "/" + s.getAnchorCell()
                                        + "/" + s.getAnchorDigit()).collect(java.util.stream.Collectors.joining(",")));
                check(replay.session().frames().size() == frameCount + 2, "batch apply did not create one two-frame replay operation");
                ReplayFrame input = replay.session().frames().get(frameCount);
                ReplayFrame result = replay.session().frames().get(frameCount + 1);
                check(input.operationId == result.operationId, "evidence and result were not one replay operation");
                check(input.annotations.ink().stream().anyMatch(s -> s.isConclusionOutlined() && s.getThoughtGroup() == 0),
                        "pre-apply replay frame lost current-group outline evidence");
                check(input.annotations.outlineVisibleGroup == 0, "replay evidence lost its visible outline group");
                check(java.util.Arrays.stream(input.annotations.overlay.split("\\n"))
						.filter(line -> line.startsWith("P " + new java.awt.Color(0x1769FF).getRGB() + " ")).count() >= 2,
						"Windows-compatible evidence did not include glyph-contour outline paths");
                check(input.board.values()[0] == 0 && result.board.values()[0] == 1,
                        "replay frames did not capture before and after boards");
                check(read("lastBoardBefore") != null && read("lastBoardAfter") != null,
						"Q review did not capture the mixed board action");
                int boardUndoDepth = ((java.util.Stack<?>) read("undoStack")).size();
                int doodleUndoDepth = ((java.util.Stack<?>) read("doodleUndoStack")).size();
                String boardAfterApply = TechniqueStepCatalog.createSignature(panel.getSudoku());
                key(KeyEvent.KEY_PRESSED, KeyEvent.VK_BACK_SPACE, 0, '\b');
                check(boardAfterApply.equals(TechniqueStepCatalog.createSignature(panel.getSudoku()))
                                && replay.session().frames().size() == frameCount + 2
                                && ((java.util.Stack<?>) read("undoStack")).size() == boardUndoDepth
                                && ((java.util.Stack<?>) read("doodleUndoStack")).size() == doodleUndoDepth,
                        "repeat Backspace after consuming the group created an empty operation");
                check(mark(20, 8, DoodleStroke.MARK_TRUE_CIRCLE, 1) != null,
                        "no-op Backspace changed another thought group's mark");
                Path file = replay.directory().resolve(replay.session().id + ".hrep");
                ReplaySession loaded = ReplayStore.read(file);
                ReplayFrame loadedInput = loaded.frames().get(frameCount);
                check(loadedInput.annotations.outlineVisibleGroup == 0
                                && loadedInput.annotations.ink().stream().anyMatch(s -> s.isConclusionOutlined()),
                        "saved replay did not retain outlined evidence");
                ReplaySession imported = ReplayFiles.importFile(file);
                check(imported.frames().get(frameCount).kind.equals("doodle-conclusion-input")
                                && imported.frames().get(frameCount + 1).kind.equals("doodle-conclusion-result"),
                        "replay import rejected the paired candidate-mark operation");

                panel.undoCurrentAnnotation();
                check(strokes.stream().noneMatch(s -> s.isStandardCandidateMark() && s.getThoughtGroup() == 0),
                        "annotation undo restored candidate-bound marks with deleted candidates");
                check(strokes.stream().anyMatch(s -> !s.isCandidateAnchored() && s.getThoughtGroup() == 0),
                        "annotation undo lost a still-valid freehand annotation");
                panel.undo();
                check(panel.getSudoku().getValue(0) == 0 && panel.getSudoku().isCandidate(10, 4),
                        "M board undo did not restore the whole mixed operation");
                check(strokes.stream().noneMatch(s -> s.isStandardCandidateMark() && s.getThoughtGroup() == 0),
                        "board undo resurrected marks removed by annotation undo");

                // A later M-to-annotation transition prunes dead candidate anchors and both adjacent links.
                panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
                DoodleStroke stale = new DoodleStroke(Options.getInstance().getColoringColors()[2], .02f);
                stale.setAnchorCell(20); stale.setAnchorDigit(8); stale.setCandidateMarkKind(DoodleStroke.MARK_TRUE_CIRCLE);
                stale.setThoughtGroup(1); stale.getPoints().add(new DoodlePoint(-.1, 0)); stale.getPoints().add(new DoodlePoint(.1, 0));
                strokes.add(stale);
                @SuppressWarnings("unchecked") java.util.Map<Integer, java.awt.Color> candidateColors =
                        (java.util.Map<Integer, java.awt.Color>) read("coloringCandidateMap");
                candidateColors.put(Integer.valueOf(20 * 10 + 8), java.awt.Color.RED);
                UserChain chain = new UserChain();
                chain.getNodes().add(new UserChainNode(31, 2));
                chain.getNodes().add(new UserChainNode(32, 3));
                chain.getNodes().add(new UserChainNode(33, 4));
                chain.getStrongRelations().add(Boolean.TRUE); chain.getStrongRelations().add(Boolean.FALSE);
                @SuppressWarnings("unchecked") List<UserChain> chains = (List<UserChain>) read("userChains");
                chains.add(chain);
                panel.getSudoku().setCandidate(20, 8, false, true);
                panel.getSudoku().setCandidate(32, 3, false, true);
                panel.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);
                check(!strokes.contains(stale) && !candidateColors.containsKey(Integer.valueOf(208)),
                        "M-to-annotation entry retained stale candidate marks/colors");
                List<UserChain> remaining = (List<UserChain>) read("userChains");
                check(remaining.size() == 2, "stale chain node did not split its incoming/outgoing links");
                for (UserChain part : remaining) {
                    check(part.getStrongRelations().isEmpty() && part.getNodes().size() == 1,
                            "cleanup connected across a removed chain candidate");
                    check(part.getNodes().get(0).getCellIndex() != 32, "cleanup retained a missing chain candidate");
                }
                check(remaining.stream().anyMatch(c -> c.getNodes().get(0).getCellIndex() == 31)
                                && remaining.stream().anyMatch(c -> c.getNodes().get(0).getCellIndex() == 33),
                        "cleanup lost valid isolated chain endpoints");
                System.out.println("Doodle conclusion workflow passed: group outlines, projection, Backspace transaction, dual undo, replay round trip, and stale-anchor cleanup");
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                if (frame != null) frame.dispose();
            }
        });
        if (failure[0] != null) {
            failure[0].printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
