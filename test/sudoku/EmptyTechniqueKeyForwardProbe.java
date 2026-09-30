package sudoku;

import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/** Empty result popups must release the first board key, but pending scans must not. */
public final class EmptyTechniqueKeyForwardProbe {
    private static final String PUZZLE =
            "530678912672195348198342567859761423426853791713924856961537284287419635345286179";
    private static MainFrame frame;
    private static SudokuPanel panel;
    private static KeyEventDispatcher dispatcher;

    private static Object read(String name) throws Exception {
        Field field = MainFrame.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(frame);
    }

    private static void write(String name, Object value) throws Exception {
        Field field = MainFrame.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(frame, value);
    }

    private static void invoke(String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = MainFrame.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(frame, arguments);
    }

    private static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    private static void key(Component source, int code, char character) {
        key(source, code, character, 0);
    }

    private static void key(Component source, int code, char character, int modifiers) {
        KeyEvent event = new KeyEvent(source, KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), modifiers, code, character);
        require(dispatcher.dispatchKeyEvent(event) && event.isConsumed(), "key not routed");
    }

    private static void onEdt(Checked action) throws Exception {
        final Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try { action.run(); } catch (Throwable error) { failure[0] = error; }
        });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private interface Checked { void run() throws Exception; }

    private static JPopupMenu popup() throws Exception {
        return (JPopupMenu) read("techniqueSelectorPopup");
    }

    private static void awaitEmptyCurrent() throws Exception {
        long until = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < until) {
            final boolean[] ready = new boolean[1];
            onEdt(() -> ready[0] = popup() != null && popup().isVisible()
                    && Boolean.TRUE.equals(popup().getClientProperty("emptyTechniqueResults")));
            if (ready[0]) return;
            Thread.sleep(50);
        }
        final String[] state = new String[1];
        onEdt(() -> {
            JPopupMenu current = popup();
            state[0] = "popup=" + current + ", visible=" + (current != null && current.isVisible())
                    + ", empty=" + (current == null ? null : current.getClientProperty("emptyTechniqueResults"))
                    + ", status=" + panel.getSudoku().getStatus() + ", candidates=" + panel.isShowCandidates();
        });
        throw new AssertionError("Tab no-result scan did not finish: " + state[0]);
    }

    public static void main(String[] args) throws Exception {
        try {
            onEdt(() -> {
                frame = new MainFrame(null);
                frame.setSize(1050, 800);
                frame.setVisible(true);
                panel = frame.getSudokuPanel();
                panel.setSudoku(PUZZLE);
                panel.setShowCandidates(true);
                panel.setActiveCell(2);
                dispatcher = (KeyEventDispatcher) read("annotationKeyDispatcher");
                Method signature = MainFrame.class.getDeclaredMethod("getTechniqueScanSignature", Sudoku2.class);
                signature.setAccessible(true);
                write("cachedTechniqueSignature", signature.invoke(frame, panel.getSudoku()));
                write("cachedTechniqueTypes", new ArrayList<SolutionType>());
                write("cachedTechniqueScanComplete", Boolean.TRUE);
                write("cachedTechniqueScanFailed", Boolean.FALSE);
                invoke("showTechniqueSelector", new Class<?>[0]);
                JPopupMenu empty = popup();
                require(empty.isVisible() && Boolean.TRUE.equals(empty.getClientProperty("emptyTechniqueResults")),
                        "ordinary empty state missing");
                Component source = empty.getComponent(0);
                key(source, KeyEvent.VK_4, '4');
                require(panel.getSudoku().getValue(2) == 4 && !empty.isVisible(),
                        "ordinary empty popup swallowed board digit");
                write("cachedTechniqueSignature", signature.invoke(frame, panel.getSudoku()));
                write("cachedTechniqueTypes", new ArrayList<SolutionType>());
                write("cachedTechniqueScanComplete", Boolean.TRUE);
                write("cachedTechniqueScanFailed", Boolean.FALSE);
                invoke("showTechniqueSelector", new Class<?>[0]);
                JPopupMenu emptyForUndo = popup();
                key(emptyForUndo.getComponent(0), KeyEvent.VK_Z, 'z', java.awt.event.InputEvent.META_DOWN_MASK);
                require(!emptyForUndo.isVisible() && panel.getSudoku().getValue(2) == 0,
                        "ordinary empty popup swallowed Undo shortcut");
                write("cachedTechniqueSignature", signature.invoke(frame, panel.getSudoku()));
                write("cachedTechniqueTypes", new ArrayList<SolutionType>());
                write("cachedTechniqueScanComplete", Boolean.TRUE);
                write("cachedTechniqueScanFailed", Boolean.FALSE);
                invoke("showTechniqueSelector", new Class<?>[0]);
                JPopupMenu emptyForTool = popup();
                require(emptyForTool.isVisible()
                        && Boolean.TRUE.equals(emptyForTool.getClientProperty("emptyTechniqueResults")),
                        "ordinary empty state missing after board change");
                key(emptyForTool.getComponent(0), KeyEvent.VK_L, 'l');
                require(!emptyForTool.isVisible() && panel.getAnnotationTool() == AnnotationTool.FREE_CHAIN,
                        "ordinary empty popup swallowed annotation shortcut");
                panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
                JPopupMenu pending = new JPopupMenu();
                JLabel loading = new JLabel("searching");
                pending.add(loading);
                pending.putClientProperty("emptyTechniqueResults", Boolean.FALSE);
                pending.show(panel, 0, 0);
                write("techniqueSelectorPopup", pending);
                KeyEvent waitingKey = new KeyEvent(loading, KeyEvent.KEY_PRESSED,
                        System.currentTimeMillis(), 0, KeyEvent.VK_5, '5');
                dispatcher.dispatchKeyEvent(waitingKey);
                require(pending.isVisible() && panel.getSudoku().getValue(2) == 0,
                        "searching popup leaked a key to the board");
                pending.setVisible(false);
                write("techniqueSelectorPopup", null);
            });
            onEdt(() -> {
                panel.setSudoku(PUZZLE);
                panel.clearAllCellSelection();
                panel.resetShowHintCellValues();
                frame.showCurrentReasoning(true);
            });
            awaitEmptyCurrent();
            onEdt(() -> {
                JPopupMenu empty = popup();
                Component source = empty.getComponent(0);
                key(source, KeyEvent.VK_TAB, '\t');
                require(!empty.isVisible() && popup() == null, "Tab reopened empty popup");
                require(panel.getSudoku().getValue(2) == 0, "dismiss changed board");
                frame.showCurrentReasoning(true);
            });
            awaitEmptyCurrent();
            onEdt(() -> {
                JPopupMenu empty = popup();
                String before = TechniqueStepCatalog.createSignature(panel.getSudoku());
                key(empty.getComponent(0), KeyEvent.VK_F11, KeyEvent.CHAR_UNDEFINED);
                require(!empty.isVisible() && !before.equals(TechniqueStepCatalog.createSignature(panel.getSudoku())),
                        "Tab empty popup swallowed F11");
            });
            System.out.println("Empty ordinary/Tab popups release digit, Undo, tool L and F11; pending scan keeps keys, Tab dismisses without reopening");
        } finally {
            if (frame != null) onEdt(() -> frame.dispose());
        }
        System.exit(0);
    }
}
