/*
 * Copyright (C) 2026 HoDoKu contributors
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.Stack;
import javax.swing.AbstractButton;
import javax.swing.JTextField;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;

/** Actual dispatcher and pointer transactions, plus native G gestures in an isolated process. */
public final class DoodleHypothesisKeyGestureProbe {
    private static long time = System.currentTimeMillis();
    private static KeyEventDispatcher dispatcher;

    private static boolean key(Component source, int id, int code, int modifiers, long when) {
        return dispatcher.dispatchKeyEvent(new KeyEvent(source, id, when, modifiers, code, (char) code));
    }
    private static void down() { check(key(p, KeyEvent.KEY_PRESSED, KeyEvent.VK_G, 0, time), "G down not consumed"); }
    private static void up(long duration) {
        check(key(p, KeyEvent.KEY_RELEASED, KeyEvent.VK_G, 0, time + duration), "G up not consumed");
        time += duration;
    }
    private static void tap(long gap) { time += gap; down(); up(20); }
    private static AbstractButton button(String name) throws Exception {
        Field field = MainFrame.class.getDeclaredField(name); field.setAccessible(true);
        return (AbstractButton) field.get(f);
    }
    private static void entry(boolean expected, String message) throws Exception {
        check(p.isDoodleHypothesisEntryActive() == expected, message);
        check(button("doodleHypothesisEntryButton").isSelected() == expected, "toolbar state: " + message);
    }
    private static int undoSize() throws Exception { return ((Stack<?>) read("doodleUndoStack")).size(); }
    private static void deterministic() throws Exception {
        Field field = MainFrame.class.getDeclaredField("annotationKeyDispatcher"); field.setAccessible(true);
        dispatcher = (KeyEventDispatcher) field.get(f);
        String board = TechniqueStepCatalog.createSignature(p.getSudoku());
        tap(500); entry(true, "short G did not latch");
        time += 301; down(); entry(true, "second short G switched off before release"); up(249);
        entry(false, "short G did not toggle off");
        time += 500; down(); entry(true, "hold was not immediate");
        check(key(p, KeyEvent.KEY_PRESSED, KeyEvent.VK_G, 0, time + 100), "repeat not consumed");
        entry(true, "repeat toggled off"); up(250); entry(false, "hold release latched");
        button("doodleHypothesisEntryButton").doClick(); entry(true, "mouse click did not toggle");
        time += 500; down(); entry(true, "hold disabled latched entry"); up(400);
        entry(false, "hold release restored latched entry instead of ending");
        button("doodleHypothesisEntryButton").doClick();
        button("doodleHypothesisEntryButton").doClick(); entry(false, "mouse toggle changed");

        mark(0, 1, 1, 0); mark(10, 2, 2, 0); mark(20, 3, 2, 1);
        int count = ink().size(), beforeUndo = undoSize();
        tap(500); tap(300); entry(false, "double G retained entry");
        check(ink().size() == count && !find(0, 1, 1, 0).isHypothesisStart()
                && find(10, 2, 2, 0) != null && find(20, 3, 2, 1).isHypothesisStart(),
                "double G removed ink or another color start");
        check(undoSize() == beforeUndo + 1, "double G did not use one undo transaction");
        p.undoCurrentAnnotation(); check(find(0, 1, 1, 0).isHypothesisStart(), "undo lost start");
        p.redoCurrentAnnotation(); check(!find(0, 1, 1, 0).isHypothesisStart(), "redo lost cancellation");
        mark(30, 4, 1, 0); check(!find(30, 4, 1, 0).isHypothesisStart(), "cancel automatically promoted a mark");
        p.undoCurrentAnnotation(); p.undoCurrentAnnotation();
        button("doodleHypothesisEntryButton").doClick();
        tap(500); entry(false, "short from mouse latch did not end"); tap(100);
        check(!find(0, 1, 1, 0).isHypothesisStart(), "double from latched entry did not cancel");
        p.undoCurrentAnnotation();
        tap(500); tap(301);
        check(find(0, 1, 1, 0).isHypothesisStart(), "outside double window canceled start");
        tap(500); p.setAnnotationTool(AnnotationTool.DOODLE); tap(100);
        check(find(0, 1, 1, 0).isHypothesisStart(), "toolbar tool click retained double G");

        p.finishDoodleHypothesisEntry(); time += 500; down();
        key(p, KeyEvent.KEY_PRESSED, KeyEvent.VK_5, 0, time + 10);
        up(50); entry(false, "short G plus board use latched");
        check(find(40, 5, 1, 0) != null && find(40, 5, 1, 0).isHypothesisStart(), "held numeric mark lost start");
        time += 500; down();
        java.awt.Point point = ChainOriginProbe.candidate(50, 6);
        for (java.awt.event.MouseListener listener : p.getMouseListeners())
            listener.mousePressed(new MouseEvent(p, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    InputEvent.BUTTON1_DOWN_MASK, point.x, point.y, 1, false, MouseEvent.BUTTON1));
        up(300);
        for (java.awt.event.MouseListener listener : p.getMouseListeners())
            listener.mouseReleased(new MouseEvent(p, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                    0, point.x, point.y, 1, false, MouseEvent.BUTTON1));
        p.flushMappedClick(); entry(false, "pointer transaction retained entry after commit");
        check(find(50, 6, 1, 0).isHypothesisStart(), "G release before mouse release lost start");

        tap(500); key(p, KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK, time + 10);
        tap(100); check(find(0, 1, 1, 0).isHypothesisStart(), "intervening key did not break double G");
        p.finishDoodleHypothesisEntry(); time += 500; down(); p.handleEscapeVisualReset();
        down(); up(20); entry(false, "Escape then repeat/release restarted entry");
        tap(100); check(find(0, 1, 1, 0).isHypothesisStart(), "Escape retained pending double");
        time += 500; down(); p.getCellZoomPanel().selectPaletteGroup(1); down(); up(20);
        entry(false, "palette then repeat/release restarted entry");
        tap(100); check(find(20, 3, 2, 1).isHypothesisStart(), "palette switch canceled new color start");
        p.finishDoodleHypothesisEntry(); time += 500; down(); p.setAnnotationTool(AnnotationTool.BOX_SELECTION);
        up(20); p.setAnnotationTool(AnnotationTool.DOODLE); entry(false, "tool switch restarted entry");
        tap(500); p.cancelAnnotationToolInteractionOnDeactivation(); tap(100);
        check(find(20, 3, 2, 1).isHypothesisStart(), "deactivation retained double tap");
        p.finishDoodleHypothesisEntry();
        JTextField text = new JTextField(); f.getLayeredPane().add(text);
        key(text, KeyEvent.KEY_PRESSED, KeyEvent.VK_G, 0, time + 500); entry(false, "text G intercepted");
        for (int modifier : new int[]{InputEvent.META_DOWN_MASK, InputEvent.CTRL_DOWN_MASK,
                InputEvent.ALT_DOWN_MASK, InputEvent.SHIFT_DOWN_MASK}) {
            key(p, KeyEvent.KEY_PRESSED, KeyEvent.VK_G, modifier, time + 600); entry(false, "modified G intercepted");
        }
        p.setReplayReadOnly(true); key(p, KeyEvent.KEY_PRESSED, KeyEvent.VK_G, 0, time + 700);
        entry(false, "read-only G edited state"); p.setReplayReadOnly(false);
        check(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())), "G gestures changed Sudoku");
        System.out.println("PASS: dispatcher short/hold/repeat/double thresholds; toolbar parity; current-color-only cancellation, ink, undo/redo; held numeric/pointer transactions; Escape/palette/tool/focus/text/modifier/read-only boundaries; Sudoku unchanged");
    }
    private static void nativeGestures() throws Exception {
        edt(() -> { p.finishDoodleHypothesisEntry(); f.setSize(1100, 950); f.setLocation(70, 70);
            f.setVisible(true); f.toFront(); p.requestFocusInWindow(); return null; });
        Process activation = new ProcessBuilder("osascript", "-e",
                "tell application \"System Events\" to set frontmost of first process whose unix id is "
                + java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0] + " to true").start();
        check(activation.waitFor() == 0, "native activation failed");
        Robot robot = new Robot(); robot.setAutoDelay(35); robot.delay(600);
        edt(() -> { p.requestFocusInWindow(); return null; });
        robot.keyPress(KeyEvent.VK_G); robot.keyRelease(KeyEvent.VK_G); robot.waitForIdle();
        edt(() -> { entry(true, "native short G did not latch"); return null; });
        robot.delay(400); robot.keyPress(KeyEvent.VK_G); robot.waitForIdle();
        edt(() -> { entry(true, "native held G disabled entry"); return null; });
        robot.delay(350); robot.keyRelease(KeyEvent.VK_G); robot.waitForIdle();
        edt(() -> { entry(false, "native hold release latched"); p.getCellZoomPanel().selectPaletteGroup(0); return null; });
        robot.delay(400);
        int before = edt(() -> undoSize());
        for (int i = 0; i < 2; i++) { robot.keyPress(KeyEvent.VK_G); robot.keyRelease(KeyEvent.VK_G); }
        robot.waitForIdle();
        edt(() -> { entry(false, "native double retained entry");
            check(!find(0, 1, 1, 0).isHypothesisStart() && find(0, 1, 1, 0) != null
                    && find(20, 3, 2, 1).isHypothesisStart() && undoSize() == before + 1, "native double cancellation");
            return null; });
        System.out.println("PASS: native Robot G short/held/released/double gestures through MainFrame dispatcher");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        try {
            edt(() -> { initialize(); p.setActiveCell(4, 4); deterministic(); return null; });
            if (args.length == 0 || !"--listeners-only".equals(args[0])) nativeGestures();
        } catch (Throwable error) {
            error.printStackTrace(); System.exit(1);
        } finally { if (f != null) edt(() -> { f.dispose(); return null; }); }
        System.exit(0);
    }
}
