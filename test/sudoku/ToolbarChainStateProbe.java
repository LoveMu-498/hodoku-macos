package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Real mouse/keyboard checks for reachable menus and the live next-link glyph. */
public final class ToolbarChainStateProbe {
    private static MainFrame frame;
    private static Robot robot;
    public static void main(String[] args) throws Exception {
        robot = new Robot(); robot.setAutoDelay(90);
        try {
            edt(() -> {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                ApplicationAppearance.initialize(Boolean.getBoolean("hodoku.probe.dark") ? AppearanceMode.DARK : AppearanceMode.LIGHT);
                frame = new MainFrame(null); frame.setSize(1120, 850); frame.setLocation(40, 50); frame.setVisible(true);
                frame.getSudokuPanel().setSudoku(CurrentReasoningProbe.PUZZLE); frame.getSudokuPanel().setShowCandidates(true);
                frame.toFront(); frame.fixFocus(); return null;
            });
            robot.waitForIdle();
            JToolBar toolbar = edt(() -> (JToolBar)read(frame, "jToolBar1"));
            JButton ordinary = edt(() -> (JButton)read(frame, "selectTechniqueToggleButton"));
            click(ordinary);
            await(() -> edt(() -> read(frame, "techniqueSelectorPopup") != null && ((JPopupMenu)read(frame, "techniqueSelectorPopup")).isVisible()), "mouse unified menu failed");
            key(KeyEvent.VK_ESCAPE);
            JToggleButton chain = edt(() -> ((JToggleButton[])read(frame, "annotationToolButtons"))[AnnotationTool.FREE_CHAIN.ordinal()]);
            click(chain);
            edt(() -> { require(frame.getSudokuPanel().getAnnotationTool() == AnnotationTool.FREE_CHAIN, "mouse did not enter chain tool"); return null; });
            click(chain);
            edt(() -> {
                require(!frame.getSudokuPanel().isNextUserChainStrong(), "mouse could not preselect a weak first edge");
                node(frame.getSudokuPanel(), 2, 1);
                require(!frame.getSudokuPanel().currentReasoningChains().get(0).isNextStrong(), "first node discarded the displayed next edge");
                return null;
            });
            click(chain);
            BufferedImage strong = edt(() -> icon(chain));
            key(KeyEvent.VK_SPACE);
            edt(() -> { require(!frame.getSudokuPanel().isNextUserChainStrong(), "Space did not set weak link");
                require(chain.getToolTipText().contains("(-)"), "weak tooltip stale"); return null; });
            BufferedImage weak = edt(() -> icon(chain));
            int changed = 0;
            for (int y = 0; y < strong.getHeight(); y++) for (int x = 0; x < strong.getWidth(); x++)
                if (strong.getRGB(x,y) != weak.getRGB(x,y)) changed++;
            require(changed > 10, "strong and weak glyphs look identical");
            // The bottom-right shortcut letter must remain exactly the same.
            for (int y = strong.getHeight()-8; y < strong.getHeight(); y++) for (int x = strong.getWidth()-7; x < strong.getWidth(); x++)
                require(strong.getRGB(x,y) == weak.getRGB(x,y), "L shortcut changed with relation state");
            click(chain);
            edt(() -> { require(frame.getSudokuPanel().isNextUserChainStrong(), "mouse did not restore strong link");
                require(chain.getToolTipText().contains("(=)"), "strong tooltip stale");
                node(frame.getSudokuPanel(), 6, 1);
                require(!frame.getSudokuPanel().isNextUserChainStrong(), "automatic alternation not reflected");
                frame.getSudokuPanel().undoCurrentAnnotation();
                require(frame.getSudokuPanel().isNextUserChainStrong() && chain.getToolTipText().contains("(=)"), "undo did not restore displayed next relation");
                frame.getSudokuPanel().redoCurrentAnnotation();
                require(!frame.getSudokuPanel().isNextUserChainStrong(), "redo did not restore next relation");
                save(toolbar, "toolbar-weak.png");
                frame.getSudokuPanel().setNextUserChainStrong(true); save(toolbar, "toolbar-strong.png");
                saveIcon(strong, "chain-strong.png"); saveIcon(weak, "chain-weak.png"); return null; });
            for (int width : new int[]{1120, 760, 1400}) {
                edt(() -> { frame.setSize(width, 850); frame.validate(); return null; }); robot.waitForIdle();
                edt(() -> {
                    for (Component c : toolbar.getComponents()) if (c.isVisible())
                        require(c.getX() >= 0 && c.getY() >= 0 && c.getX()+c.getWidth() <= toolbar.getWidth()
                                && c.getY()+c.getHeight() <= toolbar.getHeight(), "toolbar control clipped at " + width + ": " + c);
                    require(ordinary.isShowing(), "mouse menu entry hidden");
                    save(toolbar, "toolbar-"+width+".png"); return null;
                });
            }
            edt(() -> {
                Options.getInstance().setShowHintButtonsInToolbar(true);
                java.lang.reflect.Method method = MainFrame.class.getDeclaredMethod("setShowHintButtonsInToolbar");
                method.setAccessible(true); method.invoke(frame); frame.setSize(760, 850); frame.validate(); return null;
            });
            robot.waitForIdle();
            edt(() -> {
                for (Component c : toolbar.getComponents()) if (c.isVisible())
                    require(c.getX()+c.getWidth() <= toolbar.getWidth() && c.getY()+c.getHeight() <= toolbar.getHeight(),
                            "optional hint control clipped at narrow width");
                save(toolbar, "toolbar-with-hint-buttons.png"); return null;
            });
            System.out.println("Toolbar checks passed: unified mouse menu, =/- glyphs and L, Space/click/automatic alternation, undo/redo, 760/1120/1400 widths");
        } catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        finally { if (frame != null) edt(() -> { frame.dispose(); return null; }); }
        System.exit(0);
    }
    private static void click(Component c) throws Exception {
        Point point = edt(() -> { Point p = c.getLocationOnScreen(); p.translate(c.getWidth()/2,c.getHeight()/2); return p; });
        robot.mouseMove(point.x, point.y); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
    }
    private static void key(int key) { robot.keyPress(key); robot.keyRelease(key); robot.waitForIdle(); }
    private static void node(SudokuPanel panel, int cell, int digit) {
        int size = panel.getX(0,1)-panel.getX(0,0); double third=size/3.0;
        int x=(int)Math.round(panel.getX(cell/9,cell%9)+((digit-1)%3+0.5)*third);
        int y=(int)Math.round(panel.getY(cell/9,cell%9)+((digit-1)/3+0.5)*third);
        panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,x,y,1,false,MouseEvent.BUTTON1));
        panel.dispatchEvent(new MouseEvent(panel,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,x,y,1,false,MouseEvent.BUTTON1));
    }
    private static BufferedImage icon(AbstractButton button) {
        Icon icon=button.getIcon(); BufferedImage image=new BufferedImage(icon.getIconWidth(),icon.getIconHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();icon.paintIcon(button,g,0,0);g.dispose();return image;
    }
    private static void save(Component c,String name) throws Exception {
        BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();c.paint(g);g.dispose();saveIcon(image,name);
    }
    private static void saveIcon(BufferedImage image,String name) throws Exception {
        File root=new File("/tmp/hodoku-toolbar-build/evidence");root.mkdirs();
        ImageIO.write(image,"png",new File(root,(ApplicationAppearance.isDark()?"dark-":"light-")+name));
    }
    private static Object read(Object o,String name) throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void await(Callable<Boolean> test,String message)throws Exception{long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){if(test.call())return;Thread.sleep(50);}throw new AssertionError(message);}
    private static <T>T edt(Callable<T> work)throws Exception{java.util.concurrent.FutureTask<T> task=new java.util.concurrent.FutureTask<T>(work);SwingUtilities.invokeAndWait(task);return task.get();}
}
