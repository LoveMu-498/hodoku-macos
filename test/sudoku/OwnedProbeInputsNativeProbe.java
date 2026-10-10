/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Actual owned-frame Control/button failure, followed by OS release-event assertions. */
public final class OwnedProbeInputsNativeProbe {
    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<T>(action); SwingUtilities.invokeAndWait(task); return task.get();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final AtomicBoolean controlDown = new AtomicBoolean(), mouseDown = new AtomicBoolean(), lostFocus = new AtomicBoolean();
    private static final AtomicInteger controlReleased = new AtomicInteger(), mouseReleased = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        require(args.length == 0, "Usage: OwnedProbeInputsNativeProbe");
        final JFrame[] frame = {null}; final JPanel[] panel = {null};
        OwnedProbeInputs inputs = null; Robot robot = null; Throwable failure = null;
        try {
            edt(() -> {
                frame[0] = new JFrame("HoDoKu isolated input cleanup regression"); panel[0] = new JPanel();
                panel[0].setFocusable(true);
                panel[0].addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent event) { if (event.getKeyCode() == KeyEvent.VK_CONTROL) controlDown.set(true); }
                    @Override public void keyReleased(KeyEvent event) {
                        if (event.getKeyCode() == KeyEvent.VK_CONTROL) { controlDown.set(false); controlReleased.incrementAndGet(); }
                    }
                });
                panel[0].addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent event) { mouseDown.set(true); }
                    @Override public void mouseReleased(MouseEvent event) { mouseDown.set(false); mouseReleased.incrementAndGet(); }
                });
                frame[0].add(panel[0]); frame[0].setSize(400, 240); frame[0].setLocationRelativeTo(null);
                frame[0].setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); frame[0].setVisible(true);
                frame[0].toFront(); panel[0].requestFocusInWindow(); return null;
            });
            if (System.getProperty("os.name", "").startsWith("Mac")) {
                String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
                require(pid.matches("[0-9]+"), "cannot identify owned native test process");
                Process activation = new ProcessBuilder("/usr/bin/osascript", "-e", "use framework \"AppKit\"\n"
                        + "set testApp to current application's NSRunningApplication's runningApplicationWithProcessIdentifier:" + pid + "\n"
                        + "testApp's activateWithOptions:3").redirectErrorStream(true).start();
                if (!activation.waitFor(5, TimeUnit.SECONDS)) { activation.destroy(); throw new AssertionError("owned test activation timed out"); }
                require(activation.exitValue() == 0, "owned test activation failed");
            }
            robot = new Robot(); robot.setAutoDelay(50); inputs = OwnedProbeInputs.forRobot(robot);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!edt(() -> frame[0].isActive() && panel[0].isFocusOwner()) && System.nanoTime() < deadline) {
                edt(() -> { frame[0].toFront(); panel[0].requestFocusInWindow(); return null; }); robot.delay(50);
            }
            require(edt(() -> frame[0].isActive() && panel[0].isFocusOwner()), "owned panel never acquired native focus");
            edt(() -> { frame[0].addWindowFocusListener(new WindowAdapter() {
                @Override public void windowLostFocus(WindowEvent event) { lostFocus.set(true); }
            }); return null; });
            Point point = edt(() -> { Point origin = panel[0].getLocationOnScreen(); origin.translate(100, 100); return origin; });
            robot.mouseMove(point.x, point.y); robot.waitForIdle();
            require(!lostFocus.get() && edt(() -> frame[0].isActive() && panel[0].isFocusOwner()), "focus lost before input");
            AssertionError injected = new AssertionError("intentional failure while Control and mouse button are held");
            Throwable caught = null;
            try {
                inputs.keyPress(KeyEvent.VK_CONTROL); robot.waitForIdle();
                require(controlDown.get(), "native Control press was not delivered");
                require(!lostFocus.get() && edt(() -> frame[0].isActive() && panel[0].isFocusOwner()), "focus lost before mouse press");
                inputs.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
                require(mouseDown.get(), "native mouse press was not delivered");
                throw injected;
            } catch (Throwable error) { caught = error; }
            finally {
                try { inputs.close(); }
                catch (Throwable cleanup) { caught = OwnedProbeInputs.append(caught, cleanup); }
            }
            robot.waitForIdle();
            require(caught == injected && caught.getSuppressed().length == 0, "injected failure or cleanup differed: " + caught);
            require(!inputs.hasHeldInputs(), "scope still owns pressed inputs after failure");
            require(!lostFocus.get(), "focus lost during native cleanup verification");
            require(!controlDown.get() && controlReleased.get() > 0, "actual Control release did not reach owned panel");
            require(!mouseDown.get() && mouseReleased.get() > 0, "actual mouse release did not reach owned panel");
            System.out.println("PASS NATIVE owned input fault: injected assertion preserved; Control release events="
                    + controlReleased.get() + ", mouse release events=" + mouseReleased.get() + "; owned held inputs=0");
        } catch (Throwable error) { failure = error; }
        finally {
            try { if (inputs != null) inputs.close(); }
            catch (Throwable cleanup) { failure = OwnedProbeInputs.append(failure, cleanup); }
            try { edt(() -> { if (frame[0] != null) frame[0].dispose(); return null; }); }
            catch (Throwable cleanup) { failure = OwnedProbeInputs.append(failure, cleanup); }
        }
        if (failure != null) failure.printStackTrace();
        else {
            require(!edt(() -> frame[0].isDisplayable()), "owned native frame survived cleanup");
            System.out.println("PASS OwnedProbeInputsNativeProbe: owned window disposed after failure cleanup. This is a test-process cleanup check, not product quit timing.");
        }
        System.exit(failure == null ? 0 : 1);
    }
}
