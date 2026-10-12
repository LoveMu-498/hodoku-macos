/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuSelectionManager;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
import static sudoku.ChainEvidencePreviewProbe.*;

/** Native Robot delivery to an explicitly focused, isolated test frame.
 * Fixture preparation uses Swing helpers; acceptance inputs below use Robot.
 * Only the expected popup owned by this panel/frame may temporarily own focus.
 * Other focus loss aborts the run, releases its own inputs and never resumes injection. */
public final class ChainEvidenceNativeProbe {
    static Robot robot;
    static OwnedProbeInputs inputs;
    static final AtomicBoolean focusLost = new AtomicBoolean();
    static volatile boolean monitoringFocus;
    static volatile boolean expectingOwnedPopup;
    static volatile boolean selectingOwnedPopup;
    static volatile boolean ownedPopupConfirmed;
    static java.awt.Window lastOppositeWindow;
    static int windowFocusLossCount;
    static javax.swing.JFrame deactivationFrame;
    static JPopupMenu expectedPopup;
    static File output;

    static boolean isTestPopupOnEdt(JPopupMenu menu) {
        return menu != null && menu.isVisible() && menu.getInvoker() == p
                && javax.swing.SwingUtilities.getWindowAncestor(menu.getInvoker()) == f;
    }
    static JPopupMenu ownedPopupOnEdt() {
        if (!expectingOwnedPopup) return null;
        javax.swing.MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();
        if (path.length > 0 && path[0] instanceof JPopupMenu && isTestPopupOnEdt((JPopupMenu) path[0]))
            return (JPopupMenu) path[0];
        return ownedPopupConfirmed && isTestPopupOnEdt(expectedPopup) ? expectedPopup : null;
    }
    static JPopupMenu nativePopup() throws Exception {
        JPopupMenu menu = (JPopupMenu) read("chainPreviewChooser");
        UserChain chain = active();
        check(isTestPopupOnEdt(menu), "native complete-group popup missing [nodes=" + (chain == null ? -1 : chain.getNodes().size())
                + ", tail=" + (chain == null ? "null" : tail().key()) + ", previewHeld=" + read("chainRelationPreviewHeld")
                + ", chooser=" + (menu == null ? "null" : "visible=" + menu.isVisible())
                + ", menuPath=" + MenuSelectionManager.defaultManager().getSelectedPath().length + "]");
        expectedPopup = menu; ownedPopupConfirmed = true; return menu;
    }
    static boolean ownedPopupSurfaceOnEdt(java.awt.Component component) {
        JPopupMenu menu = ownedPopupOnEdt();
        return f.isActive() && menu != null && component != null
                && (component == f.getRootPane() || component == menu
                    || javax.swing.SwingUtilities.isDescendingFrom(component, menu));
    }
    static void verifyFocus() throws Exception {
        Object[] state = edt(() -> new Object[]{f.isActive(), f.isFocused(), f.isShowing(), p.isFocusOwner(),
                f.getFocusOwner() == null ? "null" : f.getFocusOwner().getClass().getName(),
                ownedPopupSurfaceOnEdt(f.getFocusOwner())});
        boolean focused = Boolean.TRUE.equals(state[0])
                && (Boolean.TRUE.equals(state[3]) || Boolean.TRUE.equals(state[5]));
        check(!focusLost.get() && focused,
                "native input stopped: isolated test frame is not active/panel focus owner or focus was lost"
                + " [active=" + state[0] + ", focused=" + state[1] + ", showing=" + state[2]
                + ", panelFocusOwner=" + state[3] + ", focusOwner=" + state[4]
                + ", expectedOwnedPopupFocus=" + state[5] + ", permanentFocusLoss=" + focusLost.get() + "]");
    }
    static void idle() throws Exception {
        robot.waitForIdle();
        if (selectingOwnedPopup && ownedPopupConfirmed) {
            edt(() -> {
                if (!focusLost.get() && f.isActive() && ownedPopupOnEdt() == null
                        && f.getFocusOwner() == f.getRootPane()) p.requestFocusInWindow();
                return null;
            }); robot.waitForIdle();
        }
        verifyFocus();
    }
    static void pressKey(int key) throws Exception {
        verifyFocus(); inputs.keyPress(key); idle();
    }
    static void releaseKey(int key) throws Exception {
        verifyFocus(); inputs.keyRelease(key); idle();
    }
    static void pressButton(int mask) throws Exception {
        verifyFocus(); inputs.mousePress(mask); idle();
    }
    static void releaseButton(int mask) throws Exception {
        verifyFocus(); inputs.mouseRelease(mask); idle();
    }
    static void releaseAllInputs() {
        if (inputs != null) inputs.releaseAll();
    }
    /** Runs even after a test/focus error; no System.exit may bypass this scope. */
    static Throwable finish(Throwable failure) {
        try { if (inputs != null) inputs.close(); }
        catch (Throwable cleanup) { failure = OwnedProbeInputs.append(failure, cleanup); }
        monitoringFocus = false;
        try {
            if (f != null) edt(() -> {
                MenuSelectionManager.defaultManager().clearSelectedPath();
                if (deactivationFrame != null) deactivationFrame.dispose();
                f.dispose(); return null;
            });
        } catch (Throwable cleanup) { failure = OwnedProbeInputs.append(failure, cleanup); }
        return failure;
    }
    static Point candidateOnScreen(int cell, int digit) throws Exception {
        verifyFocus(); return edt(() -> {
            Point point = ChainOriginProbe.candidate(cell, digit), panel = p.getLocationOnScreen();
            point.translate(panel.x, panel.y); return point;
        });
    }
    static void move(Point point) throws Exception {
        verifyFocus(); robot.mouseMove(point.x, point.y); idle();
    }
    static void nativeTap(Point point) throws Exception {
        // Space deliberate single clicks beyond the existing 200ms classifier.
        // No timing claim is made by this probe; gesture latency has separate coverage.
        robot.delay(230); verifyFocus(); move(point);
        pressButton(InputEvent.BUTTON1_DOWN_MASK); releaseButton(InputEvent.BUTTON1_DOWN_MASK);
    }
    static void nativeTap(int cell, int digit) throws Exception { nativeTap(candidateOnScreen(cell, digit)); }
    static void holdPreview(boolean option) throws Exception {
        if (option) pressKey(KeyEvent.VK_ALT);
        else {
            Point point = edt(() -> { Point q = p.getLocationOnScreen(); q.translate(90, 90); return q; });
            move(point); pressButton(InputEvent.BUTTON2_DOWN_MASK);
        }
        check(edt(() -> (Boolean) read("chainRelationPreviewHeld")), "native preview hold did not reach panel");
    }
    static void releasePreview(boolean option) throws Exception {
        if (option) releaseKey(KeyEvent.VK_ALT); else releaseButton(InputEvent.BUTTON2_DOWN_MASK);
        check(!edt(() -> (Boolean) read("chainRelationPreviewHeld")), "native preview release stuck");
    }
    static void paintActualPanel() {
        BufferedImage image = new BufferedImage(p.getWidth(), p.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics(); p.paint(graphics); graphics.dispose();
    }
    static void fixture(boolean als) throws Exception {
        verifyFocus();
        edt(() -> {
            if (als) ChainEvidencePreviewProbe.als(); else { reset(); origin(1); }
            f.validate(); paintActualPanel(); p.repaint(); return null;
        });
        idle();
    }
    static void screenshot(String name) throws Exception {
        idle(); Rectangle bounds = edt(() -> {
            Rectangle region = new Rectangle(p.getLocationOnScreen(), p.getSize());
            return region.intersection(f.getGraphicsConfiguration().getBounds());
        });
        check(bounds.width > 0 && bounds.height > 0, "native screenshot has no visible test panel");
        BufferedImage capture = robot.createScreenCapture(bounds);
        verifyFocus(); ImageIO.write(capture, "png", new File(output, name + ".png"));
    }
    static void noAppend(int history, String message) throws Exception {
        edt(() -> { assertNoAppend(1, history, message); return null; });
    }
    static void completeGroup(int clickedCell, boolean option) throws Exception {
        fixture(true); final String before = edt(() -> board());
        edt(() -> { p.setNextUserChainStrong(true); return null; }); holdPreview(option);
        screenshot((option ? "option" : "middle") + "-group-before-" + clickedCell);
        nativeTap(clickedCell, 1);
        edt(() -> {
            check(active().getNodes().size() == 2 && tail().key().equals(atoms(1, 11).key()),
                    "native member click did not append complete ALS group, member " + clickedCell);
            check(active().getStrongRelations().get(0), "native group lost strong edge type");
            check(done().isEmpty(), "Option unexpectedly started a new chain segment");
            check(before.equals(board()), "native group click changed board"); return null;
        });
        screenshot((option ? "option" : "middle") + "-group-after-" + clickedCell); releasePreview(option);
    }
    static void commandSingletons(boolean option) throws Exception {
        fixture(true); holdPreview(option); pressKey(KeyEvent.VK_META);
        check(edt(() -> (Boolean) read("chainPreviewSingleHeld")), "native Command did not show singleton preview");
        int history = edt(() -> undoSize()); nativeTap(0, 1);
        noAppend(history, "native Command borrowed the complete ALS group's strong relation");
        screenshot((option ? "option" : "middle") + "-command-ineligible"); nativeTap(1, 1);
        edt(() -> {
            check(active().getNodes().size() == 2 && tail().key().equals(atoms(11).key())
                    && active().getStrongRelations().get(0), "native Command failed independently conjugate singleton");
            check(done().isEmpty(), "native modifier leaked an Alt new-chain start"); return null;
        });
        screenshot((option ? "option" : "middle") + "-command-singleton");
        releaseKey(KeyEvent.VK_META); releasePreview(option);
        check(!edt(() -> (Boolean) read("chainPreviewSingleHeld")), "native Command release left singleton intent");
    }
    static void blockedGroupAndWeakSingleton() throws Exception {
        fixture(true); edt(() -> { p.setNextUserChainStrong(false); return null; }); holdPreview(false);
        int history = edt(() -> undoSize()); nativeTap(0, 1); noAppend(history, "native strong-only group accepted as weak");
        nativeTap(1, 1); noAppend(history, "native ordinary click fell back from blocked group to weak singleton");
        screenshot("middle-blocked-group-no-fallback"); pressKey(KeyEvent.VK_META); nativeTap(1, 1);
        edt(() -> {
            check(tail().key().equals(atoms(11).key()) && !active().getStrongRelations().get(0),
                    "native middle+Command did not honor independent weak singleton qualification"); return null;
        });
        screenshot("middle-command-weak-singleton"); releaseKey(KeyEvent.VK_META); releasePreview(false);
    }
    static void ordinaryGates() throws Exception {
        fixture(false); holdPreview(false); int history = edt(() -> undoSize());
        nativeTap(1, 1); noAppend(history, "native ordinary weak target accepted as strong");
        screenshot("middle-ordinary-weak-blocked"); edt(() -> { p.setNextUserChainStrong(false); return null; });
        nativeTap(1, 1); edt(() -> {
            check(active().getNodes().size() == 2 && !active().getStrongRelations().get(0),
                    "native ordinary weak target failed as weak"); return null;
        }); releasePreview(false);
        for (boolean strong : new boolean[]{false, true}) {
            fixture(false); edt(() -> {
                for (int cell : new int[]{2, 9, 10, 11, 18, 19, 20}) p.getSudoku().delCandidate(cell, 1);
                p.setNextUserChainStrong(strong); return null;
            }); holdPreview(false); nativeTap(1, 1); edt(() -> {
                check(active().getNodes().size() == 2 && active().getStrongRelations().get(0) == strong,
                        "native both-qualified target ignored requested edge type"); return null;
            }); releasePreview(false);
        }
    }
    static void popupMouseTrace(String phase, java.awt.event.MouseEvent event) {
        try {
            System.out.println("NATIVE popup " + phase + " [button=" + event.getButton()
                    + ", modifiersEx=" + event.getModifiersEx() + ", previewHeld=" + read("chainRelationPreviewHeld")
                    + ", menuPath=" + MenuSelectionManager.defaultManager().getSelectedPath().length + "]");
        } catch (Exception failure) { System.out.println("NATIVE popup trace unavailable: " + failure.getClass().getName()); }
    }
    static void popupState(String phase, JPopupMenu menu, Point target) throws Exception {
        edt(() -> {
            System.out.println("NATIVE popup " + phase + " [target=" + target + ", pointer="
                    + java.awt.MouseInfo.getPointerInfo().getLocation() + ", menuVisible=" + menu.isVisible()
                    + ", menuPath=" + MenuSelectionManager.defaultManager().getSelectedPath().length
                    + ", panelFocus=" + p.isFocusOwner() + "]"); return null;
        });
    }
    static void selectPopup(Point point, JPopupMenu menu) throws Exception {
        robot.delay(230); verifyFocus(); robot.mouseMove(point.x, point.y); robot.waitForIdle();
        popupState("afterMove", menu, point); verifyFocus();
        check(edt(() -> menu.isVisible() && ownedPopupOnEdt() == menu), "native popup disappeared before selection press");
        inputs.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
        popupState("afterPress", menu, point); verifyFocus();
        selectingOwnedPopup = true;
        inputs.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
        popupState("afterRelease", menu, point); idle();
    }
    static void nativeChooser(boolean option) throws Exception {
        String mode = option ? "option" : "middle";
        verifyFocus(); edt(() -> {
            reset(); origin(1, 11); circleSource(0, 1, 11, 403, 805); circleSource(1, 1, 11, 403, 506);
            f.validate(); paintActualPanel(); p.repaint(); return null;
        }); idle(); holdPreview(option); expectingOwnedPopup = true; nativeTap(40, 3);
        final JPopupMenu menu = edt(() -> nativePopup());
        final int[] actions = {0};
        Point selection = edt(() -> {
            check(ownedPopupOnEdt() == menu, "native chooser is not the expected popup owned by test panel/frame");
            ownedPopupConfirmed = true;
            check(menu.getComponentCount() == 2, "native chooser included singleton or omitted complete groups");
            String expected = ChainTextCodec.node(atoms(403, 805));
            for (int index = 0; index < menu.getComponentCount(); index++) {
                JMenuItem item = (JMenuItem) menu.getComponent(index);
                if (expected.equals(item.getToolTipText())) {
                    item.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override public void mousePressed(java.awt.event.MouseEvent event) { popupMouseTrace("mousePressed", event); }
                        @Override public void mouseReleased(java.awt.event.MouseEvent event) { popupMouseTrace("mouseReleased", event); }
                    });
                    item.addActionListener(event -> {
                        actions[0]++;
                        try { System.out.println("NATIVE popup item ActionEvent [previewHeld=" + read("chainRelationPreviewHeld") + "]"); }
                        catch (Exception failure) { System.out.println("NATIVE popup action trace unavailable: " + failure.getClass().getName()); }
                    });
                    Point location = item.getLocationOnScreen(); location.translate(item.getWidth() / 2, item.getHeight() / 2);
                    System.out.println("NATIVE popup item [screen=" + new Rectangle(item.getLocationOnScreen(), item.getSize())
                            + ", robotTarget=" + location + "]");
                    return location;
                }
            }
            throw new AssertionError("native chooser omitted expected complete group");
        }); screenshot(mode + "-overlap-native-popup");
        java.awt.event.AWTEventListener trace = event -> {
            if (!(event instanceof java.awt.event.MouseEvent)) return;
            java.awt.event.MouseEvent mouse = (java.awt.event.MouseEvent) event;
            if (!(mouse.getSource() instanceof java.awt.Component)
                    || javax.swing.SwingUtilities.getWindowAncestor((java.awt.Component) mouse.getSource()) != f) return;
            System.out.println("NATIVE owned-window mouse [id=" + mouse.getID() + ", source=" + mouse.getSource().getClass().getName()
                    + ", point=" + mouse.getLocationOnScreen() + ", button=" + mouse.getButton()
                    + ", modifiersEx=" + mouse.getModifiersEx() + ", menuPath="
                    + MenuSelectionManager.defaultManager().getSelectedPath().length + "]");
        };
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(trace,
                java.awt.AWTEvent.MOUSE_EVENT_MASK | java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK);
        try { selectPopup(selection, menu); }
        finally { java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(trace); }
        screenshot(mode + "-overlap-after-native-click");
        edt(() -> {
            check(active().getNodes().size() == 2 && tail().key().equals(atoms(403, 805).key()),
                    "native popup selection lost complete endpoint identity [actions=" + actions[0]
                    + ", nodes=" + active().getNodes().size() + ", tail=" + tail().key()
                    + ", previewHeld=" + read("chainRelationPreviewHeld")
                    + ", nextStrong=" + read("nextUserChainStrong")
                    + ", selectedMenuPath=" + MenuSelectionManager.defaultManager().getSelectedPath().length + "]");
            p.requestFocusInWindow(); return null;
        }); idle();
        check(edt(() -> p.isFocusOwner()), "native popup selection did not restore test panel focus");
        expectingOwnedPopup = false; selectingOwnedPopup = false; ownedPopupConfirmed = false; expectedPopup = null;
        screenshot(mode + "-overlap-selected-group"); releasePreview(option);
    }
    static void nativePopupCancel(boolean escape, boolean option) throws Exception {
        verifyFocus(); edt(() -> {
            reset(); origin(1, 11); circleSource(0, 1, 11, 403, 805); circleSource(1, 1, 11, 403, 506);
            f.validate(); paintActualPanel(); p.repaint(); return null;
        }); idle(); holdPreview(option); expectingOwnedPopup = true; nativeTap(40, 3);
        final JPopupMenu menu = edt(() -> nativePopup());
        int history = edt(() -> undoSize());
        if (escape) {
            pressKey(KeyEvent.VK_ESCAPE); releaseKey(KeyEvent.VK_ESCAPE);
            check(!edt(() -> menu.isVisible()), "first native Escape did not cancel owned menu");
            noAppend(history, "first native Escape changed chain/history");
            screenshot("middle-popup-first-escape-cancel");
            // Preserve the existing native escape order: menu first, then preview.
            pressKey(KeyEvent.VK_ESCAPE); releaseKey(KeyEvent.VK_ESCAPE);
            check(!edt(() -> (Boolean) read("chainRelationPreviewHeld")), "second native Escape left relation preview held");
        }
        releasePreview(option);
        check(!edt(() -> menu.isVisible()), "native popup survived " + (escape ? "Escape" : "middle release"));
        noAppend(history, "native popup cancellation appended or changed undo history");
        expectingOwnedPopup = false; selectingOwnedPopup = false; ownedPopupConfirmed = false; expectedPopup = null;
        screenshot(escape ? "middle-popup-escape-cancel" : (option ? "option" : "middle") + "-popup-release-cancel");
    }
    static String chainState() throws Exception {
        StringBuilder state = new StringBuilder();
        for (UserChain chain : p.currentReasoningChains()) {
            state.append(chain.getSourceId()).append(':').append(chain.isClosed()).append(':');
            for (UserChainNode node : chain.getNodes()) state.append(node.key()).append('/');
            state.append(chain.getStrongRelations()).append(':').append(chain.getRelationColors()).append(';');
        }
        return state.append("next=").append(read("nextUserChainStrong")).toString();
    }
    static void nativeDeactivation() throws Exception {
        verifyFocus(); edt(() -> {
            reset(); origin(1, 11); circleSource(0, 1, 11, 403, 805); circleSource(1, 1, 11, 403, 506);
            f.validate(); paintActualPanel(); p.repaint(); return null;
        }); idle();
        edt(() -> { System.out.println("DEACTIVATE fixture [state=" + chainState() + ", active=" + (active() != null)
                + ", tool=" + read("annotationTool") + "]"); return null; });
        holdPreview(false);
        edt(() -> { System.out.println("DEACTIVATE held [state=" + chainState() + ", active=" + (active() != null)
                + ", tool=" + read("annotationTool") + "]"); return null; });
        expectingOwnedPopup = true; nativeTap(40, 3);
        edt(() -> { System.out.println("DEACTIVATE opening [state=" + chainState() + ", active=" + (active() != null)
                + ", tool=" + read("annotationTool") + "]"); return null; });
        final JPopupMenu menu = edt(() -> nativePopup());
        final int history = edt(() -> undoSize()), lossBefore = edt(() -> windowFocusLossCount);
        final String before = edt(() -> chainState());
        verifyFocus();
        // From here onward, this scenario makes no Robot press/move/input calls.
        // Only releaseAllInputs is allowed after the real owned-window transfer.
        edt(() -> {
            deactivationFrame = new javax.swing.JFrame("HoDoKu isolated Chain Evidence focus-transfer test");
            javax.swing.JButton focusTarget = new javax.swing.JButton("Owned test focus target");
            deactivationFrame.add(focusTarget); deactivationFrame.setSize(320, 160);
            deactivationFrame.setLocation(f.getX() + 50, f.getY() + 50);
            deactivationFrame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
            deactivationFrame.setVisible(true); deactivationFrame.toFront(); focusTarget.requestFocusInWindow();
            return null;
        });
        long deadline = System.currentTimeMillis() + 3000;
        boolean transferred;
        do {
            transferred = edt(() -> windowFocusLossCount > lossBefore && lastOppositeWindow == deactivationFrame
                    && deactivationFrame.isActive() && !f.isActive());
            if (transferred) break;
            Thread.sleep(25);
        } while (System.currentTimeMillis() < deadline);
        releaseAllInputs();
        check(transferred, "owned native window focus transfer did not occur within bounded wait");
        edt(() -> {
            check(focusLost.get(), "native owned-window deactivation did not permanently trip focus guard");
            check(!(Boolean) read("chainRelationPreviewHeld") && !menu.isVisible(),
                    "native WindowLostFocus left preview or chooser open");
            check(before.equals(chainState()) && history == undoSize(), "native deactivation changed chain/history");
            System.out.println("NATIVE WindowLostFocus [source=" + f.getTitle() + ", opposite="
                    + deactivationFrame.getTitle() + ", actualOppositeIsOwnedTestFrame="
                    + (lastOppositeWindow == deactivationFrame) + ", lossCount=" + windowFocusLossCount
                    + ", originalActive=" + f.isActive() + ", targetActive=" + deactivationFrame.isActive()
                    + ", permanentFocusLoss=" + focusLost.get() + ", previewHeld=" + read("chainRelationPreviewHeld")
                    + ", chooserVisible=" + menu.isVisible() + "]"); return null;
        });
        expectingOwnedPopup = false; selectingOwnedPopup = false; ownedPopupConfirmed = false; expectedPopup = null;
        System.out.println("PASS: native owned-window deactivation closes held preview/chooser without chain/history changes");
    }
    static void initialize() throws Exception {
        edt(() -> {
            ApplicationAppearance.initialize(AppearanceMode.LIGHT); f = new MainFrame(null); p = f.getSudokuPanel();
            f.setTitle("HoDoKu isolated Chain Evidence native test");
            Rectangle screen = f.getGraphicsConfiguration().getBounds();
            f.setSize(Math.min(1150, screen.width - 40), Math.min(900, screen.height - 70));
            f.setLocation(screen.x + 20, screen.y + 20);
            f.addWindowFocusListener(new WindowAdapter() {
                @Override public void windowLostFocus(WindowEvent event) {
                    if (monitoringFocus) {
                        lastOppositeWindow = event.getOppositeWindow(); windowFocusLossCount++; focusLost.set(true);
                    }
                }
            });
            p.addFocusListener(new FocusAdapter() {
                @Override public void focusLost(FocusEvent event) {
                    if (monitoringFocus && !ownedPopupSurfaceOnEdt(event.getOppositeComponent())) focusLost.set(true);
                }
            });
            f.setVisible(true); f.toFront(); p.requestFocusInWindow(); return null;
        });
        // Activate only this showing test JVM, before monitoring/input begins.
        // NSRunningApplication activation avoids changing or addressing any other app.
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        check(pid.matches("[0-9]+") && edt(() -> f.isShowing()) && !monitoringFocus,
                "cannot identify showing test process for initialization activation");
        Process activation = new ProcessBuilder("/usr/bin/osascript", "-e", "use framework \"AppKit\"\n"
                + "set testApp to current application's NSRunningApplication's runningApplicationWithProcessIdentifier:" + pid + "\n"
                + "testApp's activateWithOptions:3").redirectErrorStream(true).start();
        if (!activation.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            activation.destroy(); throw new AssertionError("owned test-process activation timed out");
        }
        System.out.println("Initialization activation of test PID " + pid + ": exit " + activation.exitValue());
        robot = new Robot(); inputs = OwnedProbeInputs.forRobot(robot); robot.setAutoDelay(60); robot.waitForIdle();
        long deadline = System.currentTimeMillis() + 2500;
        while (!edt(() -> f.isActive() && p.isFocusOwner()) && System.currentTimeMillis() < deadline) {
            edt(() -> { f.toFront(); p.requestFocusInWindow(); return null; }); robot.delay(100); robot.waitForIdle();
        }
        verifyFocus(); monitoringFocus = true;
    }
    public static void main(String[] args) throws Exception {
        output = new File(System.getProperty("hodoku.probe.output", "build/chain-evidence-native/screenshots"));
        check(output.isDirectory() || output.mkdirs(), "cannot create isolated native screenshot directory");
        Throwable failure = null;
        try {
            initialize();
            if (args.length > 0 && "--deactivate-only".equals(args[0])) nativeDeactivation();
            else {
                if (args.length == 0 || !args[0].startsWith("--popup")) {
                    completeGroup(0, false); completeGroup(1, false); completeGroup(0, true);
                    System.out.println("PASS: native middle/Option group preview, member clicks preserve complete group and board");
                    commandSingletons(true); commandSingletons(false); blockedGroupAndWeakSingleton();
                    System.out.println("PASS: native Option+Command/middle+Command, independent singleton gates and no ordinary fallback");
                    ordinaryGates(); System.out.println("PASS: native only-strong/only-weak/both edge gates");
                }
                if (args.length > 0 && "--popup-option-only".equals(args[0])) nativeChooser(true);
                else if (args.length > 0 && "--popup-only".equals(args[0])) nativeChooser(false);
                else {
                    nativeChooser(false); nativeChooser(true); nativePopupCancel(false, false);
                    nativePopupCancel(false, true); nativePopupCancel(true, false);
                }
                System.out.println("PASS: native overlapping complete-group popup selection"
                        + (args.length == 0 ? ", middle/Option release/Escape cancellation without history" : ""));
                if (args.length == 0) nativeDeactivation();
            }
            System.out.println("Evidence: " + output.getAbsolutePath());
            System.out.println("SCOPE: current source/JDK native Robot test frame with isolated home/data; packaged app remains separate");
        } catch (Throwable error) {
            failure = error;
        } finally {
            failure = finish(failure);
        }
        if (failure != null) failure.printStackTrace();
        System.exit(failure == null ? 0 : 1);
    }
}
