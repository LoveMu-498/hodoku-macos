/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JWindow;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;

/** Real MainFrame dispatcher/listener checks; this is not Robot/native-input evidence. */
public final class ChainTranscriptionEnterDispatchProbe {
    private static KeyEventDispatcher dispatcher;
    private static long clock;

    private static boolean press(Component source, int modifiers) {
        return dispatcher.dispatchKeyEvent(new KeyEvent(source, KeyEvent.KEY_PRESSED,
                ++clock, modifiers, KeyEvent.VK_ENTER, '\n'));
    }

    private static void release() {
        dispatcher.dispatchKeyEvent(new KeyEvent(p, KeyEvent.KEY_RELEASED,
                ++clock, 0, KeyEvent.VK_ENTER, '\n'));
    }

    @SuppressWarnings("unchecked")
    private static void preview() throws Exception {
        release();
        p.setSudoku((String) null);
        p.getSudoku().set(ChainAdvisoryProbe.board(false));
        p.setShowCandidates(true);
        p.clearDoodlesWithUndo();
        p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
        UserChain chain = ChainAdvisoryProbe.ring();
        chain.setSourceId(7101L);
        List<UserChain> chains = (List<UserChain>) read("userChains");
        chains.clear(); chains.add(chain);
        Field active = SudokuPanel.class.getDeclaredField("activeUserChain");
        active.setAccessible(true); active.set(p, null);
        call("noteUserChainReasoningChanged");
        SolutionStep first = UserChainValidator.preview(p.getSudoku(), chain).steps.get(0);
        p.previewCurrentReasoning(first, null, chain, true);
        check(p.hasChainTranscriptionShortcutContext(), "fixture has no current authored preview");
    }

    private static void focusLoss(java.awt.Window opposite) {
        WindowEvent event = new WindowEvent(f, WindowEvent.WINDOW_LOST_FOCUS, opposite);
        for (WindowFocusListener listener : f.getWindowFocusListeners()) listener.windowLostFocus(event);
    }

    private static void scope() throws Exception {
        preview();
        JButton button = new JButton("button");
        JTextField text = new JTextField();
        f.getLayeredPane().add(button); f.getLayeredPane().add(text);
        check(!press(button, KeyEvent.SHIFT_DOWN_MASK), "button Shift+Enter was intercepted");
        check(!p.hasPendingChainTranscription(), "button started transcription"); release();
        check(!press(text, KeyEvent.SHIFT_DOWN_MASK), "text Shift+Enter was intercepted");
        check(!p.hasPendingChainTranscription(), "text started transcription"); release();
        JPopupMenu menu = new JPopupMenu();
        MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{menu});
        check(!press(p, KeyEvent.SHIFT_DOWN_MASK), "open-menu Shift+Enter was intercepted");
        check(!p.hasPendingChainTranscription(), "open menu started transcription");
        MenuSelectionManager.defaultManager().clearSelectedPath(); release();
        check(press(f, KeyEvent.SHIFT_DOWN_MASK), "frame Shift+Enter not consumed");
        check(p.hasPendingChainTranscription(), "frame Shift+Enter did not start transcription");
        release(); p.cancelChainTranscriptionOnExternalFocusLoss();
        f.getLayeredPane().remove(button); f.getLayeredPane().remove(text);
    }

    private static void physicalPress() throws Exception {
        preview();
        String before = TechniqueStepCatalog.createSignature(p.getSudoku());
        check(press(p, KeyEvent.SHIFT_DOWN_MASK), "board Shift+Enter not consumed");
        check(p.hasPendingChainTranscription() && p.getAnnotationTool() == AnnotationTool.DOODLE,
                "Shift+Enter did not enter P preparation");
        Field statusField = MainFrame.class.getDeclaredField("statusLabelCellSelection");
        statusField.setAccessible(true);
        javax.swing.JLabel status = (javax.swing.JLabel) statusField.get(f);
        p.getCellZoomPanel().selectPaletteGroup(1);
        check(status.getText().equals(p.chainTranscriptionStatusText()),
                "palette change left a stale transcription destination in the status bar");
        p.getCellZoomPanel().selectPaletteGroup(0);
        mark(30, 4, DoodleStroke.MARK_FALSE_CROSS, 0);
        check(press(p, 0), "Enter repeat after Shift release was not consumed");
        check(p.hasPendingChainTranscription(), "held Enter confirmed preparation after Shift release");
        check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())), "held Enter changed board");
        release();
        check(press(p, 0), "fresh confirming Enter not consumed");
        check(!p.hasPendingChainTranscription(), "fresh Enter failed to finish transcription");
        check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())), "transcription changed board");
        for (DoodleStroke stroke : ink()) if (!stroke.isHypothesisStart()) stroke.setConclusionOutlined(true);
        check(press(p, 0), "post-transcription held Enter was not consumed");
        check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),
                "post-transcription repeat fell through to P application");
        release();
    }

    private static void windowBoundary() throws Exception {
        preview(); press(p, KeyEvent.SHIFT_DOWN_MASK); release();
        JWindow owned = new JWindow(f);
        try {
            focusLoss(owned);
            check(p.hasPendingChainTranscription(), "owned window canceled preparation");
        } finally { owned.dispose(); }
        JFrame external = new JFrame();
        try {
            focusLoss(external);
            check(!p.hasPendingChainTranscription(), "external window retained preparation");
            check(!p.currentReasoningChains().isEmpty(), "focus cancellation consumed source chain");
        } finally { external.dispose(); }
        preview(); press(p, KeyEvent.SHIFT_DOWN_MASK); release();
        focusLoss(null);
        check(!p.hasPendingChainTranscription(), "null external focus retained preparation");
    }

    private static void translations() {
        for (Locale locale : new Locale[]{Locale.ENGLISH, Locale.CHINESE, Locale.GERMAN}) {
            ResourceBundle bundle = ResourceBundle.getBundle("intl/MainFrame", locale);
            for (String state : new String[]{"ready", "missingStart", "busy", "canceled", "invalidated", "done"}) {
                String text = bundle.getString("MainFrame.chainTranscription." + state);
                String rendered = new MessageFormat(text, locale).format(new Object[]{2, 3, "B"});
                check(!rendered.isEmpty() && !rendered.contains("{0}") && !rendered.contains("{1}")
                        && !rendered.contains("{2}"), "unrendered translation " + locale + " " + state);
            }
        }
    }

    private static void nativeEnter() throws Exception {
        edt(() -> {
            preview(); f.setSize(1100, 950); f.setLocation(70, 70);
            f.setVisible(true); f.toFront(); p.requestFocusInWindow(); return null;
        });
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        Process activation = new ProcessBuilder("osascript", "-e",
                "tell application \"System Events\" to set frontmost of first process whose unix id is "
                + pid + " to true").start();
        check(activation.waitFor() == 0, "isolated probe activation failed");
        Robot robot = new Robot(); robot.setAutoDelay(90); robot.waitForIdle();
        edt(() -> { p.requestFocusInWindow(); return null; }); robot.waitForIdle();
        AtomicBoolean deactivated = new AtomicBoolean();
        edt(() -> {
            f.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override public void windowLostFocus(WindowEvent event) { deactivated.set(true); }
            }); return null;
        });
        String before = edt(() -> TechniqueStepCatalog.createSignature(p.getSudoku()));
        boolean enterHeld = false, shiftHeld = false;
        try {
            requireNativeFocus(deactivated);
            robot.keyPress(KeyEvent.VK_SHIFT); shiftHeld = true;
            robot.keyPress(KeyEvent.VK_ENTER); enterHeld = true; robot.waitForIdle();
            requireNativeFocus(deactivated);
            edt(() -> {
                check(p.hasPendingChainTranscription(), "native Shift+Enter did not start preparation");
                p.getCellZoomPanel().selectPaletteGroup(0);
                mark(0, 1, DoodleStroke.MARK_FALSE_CROSS, 0);
                return null;
            });
            robot.keyRelease(KeyEvent.VK_SHIFT); shiftHeld = false;
            robot.keyPress(KeyEvent.VK_ENTER); robot.waitForIdle();
            requireNativeFocus(deactivated);
            check(edt(() -> p.hasPendingChainTranscription()), "native held Enter confirmed after Shift release");
            robot.keyRelease(KeyEvent.VK_ENTER); enterHeld = false; robot.waitForIdle();
            requireNativeFocus(deactivated);
            robot.keyPress(KeyEvent.VK_ENTER); enterHeld = true; robot.waitForIdle();
            requireNativeFocus(deactivated);
            check(!edt(() -> p.hasPendingChainTranscription()), "fresh native Enter failed to transcribe");
            // The opposite start and transcribed result now give P an automatic
            // refutation selection. A held Enter must not apply that to the board.
            robot.keyPress(KeyEvent.VK_ENTER); robot.waitForIdle();
            requireNativeFocus(deactivated);
            check(before.equals(edt(() -> TechniqueStepCatalog.createSignature(p.getSudoku()))),
                    "native held Enter fell through into P board application");
            robot.keyRelease(KeyEvent.VK_ENTER); enterHeld = false; robot.waitForIdle();
            requireNativeFocus(deactivated);
            System.out.println("PASS native Shift+Enter, Shift release with held Enter, explicit final Enter, and post-transfer repeat suppression");
        } finally {
            if (enterHeld) robot.keyRelease(KeyEvent.VK_ENTER);
            if (shiftHeld) robot.keyRelease(KeyEvent.VK_SHIFT);
        }
    }

    private static void requireNativeFocus(AtomicBoolean deactivated) throws Exception {
        check(!deactivated.get() && edt(() -> f.isActive() && f.isFocused() && p.isFocusOwner()),
                "native input aborted: isolated frame/panel lost focus");
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement", "true");
        clock = System.currentTimeMillis();
        try {
            edt(() -> {
                f = new MainFrame(null); p = f.getSudokuPanel();
                Field field = MainFrame.class.getDeclaredField("annotationKeyDispatcher");
                field.setAccessible(true); dispatcher = (KeyEventDispatcher) field.get(f);
                scope(); physicalPress(); windowBoundary(); translations();
                return null;
            });
            System.out.println("PASS transcription dispatcher scope, physical Enter lock, owned/external focus, and three-language MessageFormat");
            if (args.length == 1 && "--native".equals(args[0])) nativeEnter();
        } finally {
            MenuSelectionManager.defaultManager().clearSelectedPath();
            if (f != null) edt(() -> { f.dispose(); return null; });
        }
        System.exit(0);
    }
}
