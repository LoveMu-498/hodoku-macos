package sudoku;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** OS-dispatched macOS workflow check in an isolated HoDoKu window and data directory. */
public final class DoodleConclusionNativeProbe {
    private static MainFrame frame;
    private static SudokuPanel panel;
    private static ReplayController replay;

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<T>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static Object field(String name) throws Exception {
        Field field = SudokuPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(panel);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Point candidateOnScreen(int cell, int digit) throws Exception {
        return edt(() -> {
            Method method = SudokuPanel.class.getDeclaredMethod("getCandKoord", int.class, int.class, int.class);
            method.setAccessible(true);
            Point2D local = (Point2D) method.invoke(panel, cell, digit, field("cellSize"));
            Point point = new Point((int) Math.round(local.getX()), (int) Math.round(local.getY()));
            SwingUtilities.convertPointToScreen(point, panel);
            return point;
        });
    }

    private static void ensureActiveWindow(Window expected) throws Exception {
        check(edt(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == expected),
                "Robot input stopped because the isolated HoDoKu test window is not active");
    }

    private static void click(Robot robot, Point point, int buttonMask, int modifierKey) throws Exception {
        ensureActiveWindow(frame);
        robot.mouseMove(point.x, point.y);
        if (modifierKey != 0) robot.keyPress(modifierKey);
        robot.mousePress(buttonMask);
        robot.mouseRelease(buttonMask);
        if (modifierKey != 0) robot.keyRelease(modifierKey);
        robot.waitForIdle();
        robot.delay(100);
    }

    private static boolean hasMark(int cell, int digit, int kind, boolean outlined) throws Exception {
        return hasMark(cell, digit, kind, 0, outlined);
    }

    private static boolean hasMark(int cell, int digit, int kind, int group, boolean outlined) throws Exception {
        return edt(() -> {
            @SuppressWarnings("unchecked") List<DoodleStroke> strokes = (List<DoodleStroke>) field("doodleStrokes");
            for (DoodleStroke stroke : strokes) {
                if (stroke.isStandardCandidateMark() && stroke.getAnchorCell() == cell
                        && stroke.getAnchorDigit() == digit && stroke.getCandidateMarkKind() == kind
                        && stroke.getThoughtGroup() == group && stroke.isConclusionOutlined() == outlined) return true;
            }
            return false;
        });
    }

    private static void commandKey(Robot robot, int keyCode) throws Exception {
        ensureActiveWindow(frame);
        robot.keyPress(KeyEvent.VK_META);
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
        robot.keyRelease(KeyEvent.VK_META);
        robot.waitForIdle();
        robot.delay(150);
    }

    private static void capture(Robot robot, Path path) throws Exception {
        Rectangle bounds = edt(() -> frame.getBounds());
        javax.imageio.ImageIO.write(robot.createScreenCapture(bounds), "png", path.toFile());
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Native probe requires a macOS desktop session");
        Path data = Files.createTempDirectory("hodoku-doodle-native-");
        Path replayDirectory = Files.createDirectories(data.resolve("replays"));
        System.setProperty("hodoku.data.dir", data.toString());
        System.setProperty("hodoku.replay.dir", replayDirectory.toString());
        Throwable failure = null;
        try {
            edt(() -> {
                ApplicationAppearance.initialize(AppearanceMode.LIGHT);
                frame = new MainFrame(null);
                frame.setBounds(new Rectangle(80, 80, 1180, 860));
                panel = frame.getSudokuPanel();
                replay = frame.getReplayController();
                panel.setSudoku((String) null);
                panel.getSudoku().setSudoku(new String(new char[81]).replace('\0', '0'));
                panel.setShowCandidates(true);
                panel.setAnnotationTool(AnnotationTool.DOODLE);
                panel.getCellZoomPanel().selectPaletteGroup(0);
                replay.beginSession(new ReplayBoard(panel.getSudoku()));
                frame.setVisible(true);
                frame.toFront();
                panel.requestFocusInWindow();
                return null;
            });

            Robot robot = new Robot();
            robot.setAutoDelay(45);
            robot.waitForIdle();
            robot.delay(500);
            edt(() -> { panel.requestFocusInWindow(); return null; });
            robot.delay(250);
            ensureActiveWindow(frame);

            Point circle = candidateOnScreen(0, 1);
            Point cross = candidateOnScreen(10, 4);
            Point contradiction = candidateOnScreen(20, 3);
            click(robot, circle, InputEvent.BUTTON3_DOWN_MASK, 0);
            boolean circleCreated = hasMark(0, 1, DoodleStroke.MARK_TRUE_CIRCLE, false);
            if (!circleCreated) {
                System.out.println("Native click diagnostic: point=" + circle + ", panel="
                        + edt(() -> panel.getBounds()) + ", tool=" + edt(() -> panel.getAnnotationTool())
                        + ", group=" + edt(() -> panel.getCellZoomPanel().getPaletteGroup())
                        + ", doodles=" + edt(() -> panel.getDoodleStrokeCount()));
                capture(robot, data.resolve("native-click-failure.png"));
            }
            check(circleCreated, "native right click did not create a true circle");
            click(robot, circle, InputEvent.BUTTON1_DOWN_MASK, KeyEvent.VK_META);
            check(hasMark(0, 1, DoodleStroke.MARK_TRUE_CIRCLE, true), "native Command-click did not outline the circle");
            click(robot, cross, InputEvent.BUTTON3_DOWN_MASK, KeyEvent.VK_SHIFT);
            check(hasMark(10, 4, DoodleStroke.MARK_FALSE_CROSS, false), "native Shift-right-click did not create a false cross");
            click(robot, cross, InputEvent.BUTTON1_DOWN_MASK, KeyEvent.VK_META);
            check(hasMark(10, 4, DoodleStroke.MARK_FALSE_CROSS, true), "native Command-click did not outline the cross");
            robot.keyPress(KeyEvent.VK_B);
            robot.keyRelease(KeyEvent.VK_B);
            robot.waitForIdle();
            robot.delay(120);
            check(edt(() -> panel.getCellZoomPanel().getPaletteGroup()) == 1,
                    "native B did not select the second thought group");
            Point otherGroupCircle = candidateOnScreen(20, 8);
            click(robot, otherGroupCircle, InputEvent.BUTTON3_DOWN_MASK, 0);
            check(hasMark(20, 8, DoodleStroke.MARK_TRUE_CIRCLE, 1, false),
                    "native second-group right-click lost its group identity");
            robot.keyPress(KeyEvent.VK_A);
            robot.keyRelease(KeyEvent.VK_A);
            robot.waitForIdle();
            robot.delay(120);
            check(edt(() -> panel.getCellZoomPanel().getPaletteGroup()) == 0
                            && hasMark(0, 1, DoodleStroke.MARK_TRUE_CIRCLE, true)
                            && hasMark(20, 8, DoodleStroke.MARK_TRUE_CIRCLE, 1, false),
                    "switching groups changed the original outline or lost the other group's mark");
            click(robot, contradiction, InputEvent.BUTTON3_DOWN_MASK, 0);
            click(robot, contradiction, InputEvent.BUTTON3_DOWN_MASK, KeyEvent.VK_SHIFT);
            check(hasMark(20, 3, DoodleStroke.MARK_TRUE_CIRCLE, false)
                            && hasMark(20, 3, DoodleStroke.MARK_FALSE_CROSS, false),
                    "native thought group did not retain a contradictory circle/cross pair");
            click(robot, contradiction, InputEvent.BUTTON1_DOWN_MASK, KeyEvent.VK_META);
            check(!hasMark(20, 3, DoodleStroke.MARK_TRUE_CIRCLE, true)
                            && !hasMark(20, 3, DoodleStroke.MARK_FALSE_CROSS, true),
                    "native Command-click acted on a same-candidate circle/cross pair");
            capture(robot, data.resolve("native-outline.png"));

            robot.mouseMove(circle.x, circle.y);
            ensureActiveWindow(frame);
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.waitForIdle();
            robot.delay(100);
            edt(() -> { panel.paint(panel.getGraphics()); return null; });
            check(edt(() -> field("doodleThoughtPreviewProjection")) != null,
                    "native Space did not activate thought preview");
            @SuppressWarnings("unchecked") java.util.Set<Integer> projectedConflicts =
                    (java.util.Set<Integer>) field(edt(() -> field("doodleThoughtPreviewProjection")), "conflicts");
            check(projectedConflicts.contains(Integer.valueOf(203)),
                    "native Space did not include the contradictory thought-group candidate");
            capture(robot, data.resolve("native-preview.png"));
            robot.keyRelease(KeyEvent.VK_SPACE);
            robot.waitForIdle();
            robot.delay(100);
            check(edt(() -> field("doodleThoughtPreviewProjection")) == null,
                    "native Space release left preview active");

            robot.mouseMove(circle.x, circle.y);
            ensureActiveWindow(frame);
            robot.keyPress(KeyEvent.VK_SPACE);
            robot.waitForIdle();
            robot.delay(100);
            JFrame focusTarget = edt(() -> {
                JFrame window = new JFrame();
                window.setUndecorated(true);
                window.setBounds(420, 260, 180, 90);
                window.setVisible(true);
                window.toFront();
                window.requestFocusInWindow();
                return window;
            });
            robot.waitForIdle();
            robot.delay(150);
            Point focusPoint = edt(() -> {
                Point point = new Point(focusTarget.getWidth() / 2, focusTarget.getHeight() / 2);
                SwingUtilities.convertPointToScreen(point, focusTarget);
                return point;
            });
            robot.mouseMove(focusPoint.x, focusPoint.y);
            ensureActiveWindow(focusTarget);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(150);
            check(edt(() -> field("doodleThoughtPreviewProjection")) == null,
                    "native focus loss left the Space projection active");
            robot.keyRelease(KeyEvent.VK_SPACE);
            edt(() -> {
                focusTarget.dispose();
                frame.toFront();
                panel.requestFocusInWindow();
                return null;
            });
            robot.waitForIdle();
            robot.delay(250);
            ensureActiveWindow(frame);

            ensureActiveWindow(frame);
            robot.keyPress(KeyEvent.VK_L);
            robot.waitForIdle();
            robot.delay(350);
            check(edt(() -> panel.getAnnotationTool()) == AnnotationTool.FREE_CHAIN,
                    "native held L did not temporarily enter Free Chain");
            robot.keyRelease(KeyEvent.VK_L);
            robot.waitForIdle();
            robot.delay(180);
            check(edt(() -> panel.getAnnotationTool()) == AnnotationTool.DOODLE,
                    "native held-tool shortcut did not restore Doodle on release");

            int beforeFrames = edt(() -> replay.session().frames().size());
            ensureActiveWindow(frame);
            robot.keyPress(KeyEvent.VK_DELETE);
            robot.keyRelease(KeyEvent.VK_DELETE);
            robot.waitForIdle();
            robot.delay(120);
            check(edt(() -> panel.getSudoku().getValue(0)) == 0
                            && edt(() -> panel.getSudoku().isCandidate(10, 4))
                            && edt(() -> replay.session().frames().size()) == beforeFrames,
                    "native Forward Delete entered the conclusion-apply transaction");
            ensureActiveWindow(frame);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
            robot.waitForIdle();
            robot.delay(250);
            check(edt(() -> panel.getSudoku().getValue(0)) == 1, "native Enter did not fill outlined circle");
            check(edt(() -> panel.getSudoku().getValue(20)) == 0,
                    "native Enter applied another thought group's outlined circle");
            check(!edt(() -> panel.getSudoku().isCandidate(10, 4)), "native Enter did not delete outlined cross candidate");
            check(edt(() -> replay.session().frames().size()) == beforeFrames + 2,
                    "native apply did not produce one evidence/result replay operation");
            capture(robot, data.resolve("native-applied.png"));

            ensureActiveWindow(frame);
            robot.keyPress(KeyEvent.VK_Q);
            robot.waitForIdle();
            robot.delay(100);
            check((Boolean) edt(() -> field("reviewLastBoardChange")),
                    "native Q did not show the latest board change");
            robot.keyRelease(KeyEvent.VK_Q);
            robot.waitForIdle();
            robot.delay(100);
            check(!(Boolean) edt(() -> field("reviewLastBoardChange")),
                    "native Q release did not restore the board");

            commandKey(robot, KeyEvent.VK_Z);
            check(edt(() -> panel.getSudoku().getValue(0)) == 1,
                    "Command-Z in Doodle incorrectly undid the board transaction");
            ensureActiveWindow(frame);
            robot.keyPress(KeyEvent.VK_M);
            robot.keyRelease(KeyEvent.VK_M);
            robot.waitForIdle();
            robot.delay(150);
            check(edt(() -> panel.getAnnotationTool()) == AnnotationTool.DEFAULT_MOUSE,
                    "native M did not return to Default Mouse");
            commandKey(robot, KeyEvent.VK_Z);
            check(edt(() -> panel.getSudoku().getValue(0)) == 0
                            && edt(() -> panel.getSudoku().isCandidate(10, 4)),
                    "Command-Z in M did not undo the whole board transaction");

            Path file = edt(() -> replay.directory().resolve(replay.session().id + ".hrep"));
            ReplaySession reopened = ReplayFiles.importFile(file);
            check(reopened.frames().size() == beforeFrames + 3,
                    "saved replay did not reopen with apply evidence/result and later board-undo frames");
            ReplayFrame input = reopened.frames().get(beforeFrames);
            ReplayFrame result = reopened.frames().get(beforeFrames + 1);
            check(input.operationId == result.operationId && input.annotations.overlay.contains("P "),
                    "reopened replay lost same-operation outline evidence");
            check(input.board.values()[0] == 0 && result.board.values()[0] == 1,
                    "reopened replay lost the before/after board states");

            edt(() -> {
                replay.openViewer(reopened);
                replay.viewer().showFrame(beforeFrames);
                SudokuPanel viewerBoard = replay.viewer().boardPanel();
                @SuppressWarnings("unchecked") List<DoodleStroke> savedMarks =
                        (List<DoodleStroke>) field(viewerBoard, "doodleStrokes");
                check((Integer) field(viewerBoard, "replayOutlineVisibleGroup") == 0
                                && savedMarks.stream().anyMatch(DoodleStroke::isConclusionOutlined),
                        "reopened replay viewer did not restore outlined candidate evidence");
                check(viewerBoard.getSudoku().getValue(0) == 0,
                        "replay evidence frame opened on the result board");
                return null;
            });
            capture(robot, data.resolve("native-replay-evidence.png"));
            edt(() -> {
                replay.viewer().showFrame(beforeFrames + 1);
                check(replay.viewer().boardPanel().getSudoku().getValue(0) == 1,
                        "replay result frame did not show the filled value");
                return null;
            });
            capture(robot, data.resolve("native-replay-result.png"));

            capture(robot, data.resolve("native-workflow.png"));
            System.out.println("Native macOS event workflow passed: right/Shift-right, Command outlines, Space preview, Enter, Q, split undo and replay reopen; data=" + data);
        } catch (Throwable thrown) {
            failure = thrown;
        } finally {
            if (frame != null) edt(() -> {
                if (replay != null && replay.isViewing()) replay.closeViewer();
                frame.dispose();
                return null;
            });
        }
        if (failure != null) {
            failure.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }
}
