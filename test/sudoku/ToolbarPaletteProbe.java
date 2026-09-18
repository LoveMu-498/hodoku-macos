package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Real Swing components, isolated preferences; no running session is loaded. */
public final class ToolbarPaletteProbe {
    public static void main(String[] args) throws Exception {
        final Throwable[] failure = {null};
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = null;
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                ApplicationAppearance.initialize(Boolean.getBoolean("hodoku.probe.dark") ? AppearanceMode.DARK : AppearanceMode.LIGHT);
                frame = new MainFrame(null);
                frame.setSize(1120, 850); frame.setVisible(true);
                SudokuPanel board = frame.getSudokuPanel();
                board.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
                CellZoomPanel palette = board.getCellZoomPanel();
                Color[] colors = Options.getInstance().getColoringColors();
                board.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);
                palette.setPrimaryColor(colors[0]); palette.setSecondaryColor(colors[7]);
                String before = board.getSudoku().getSudoku(ClipboardMode.LIBRARY);
                for (int k = 0; k < 4; k++) {
                    key(board, KeyEvent.VK_A + k, 0);
                    pair(palette, colors, k, false);
                }
                require(before.equals(board.getSudoku().getSudoku(ClipboardMode.LIBRARY)), "color keys mutated puzzle");
                palette.swapColors(); pair(palette, colors, 3, true);
                for (int i = 0; i < 6; i++) {
                    Field last = CellZoomPanel.class.getDeclaredField("lastPaletteWheelAt"); last.setAccessible(true); last.setLong(palette, 0L);
                    board.dispatchEvent(new MouseWheelEvent(board, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                            i % 2 == 0 ? InputEvent.ALT_DOWN_MASK : 0, 50, 50, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1));
                    pair(palette, colors, (4 + i) % 6, true);
                }
                key(board, KeyEvent.VK_A, InputEvent.META_DOWN_MASK);
                pair(palette, colors, 3, true);
                board.setAnnotationTool(AnnotationTool.CELL_COLORING);
                palette.setPrimaryColor(colors[0]); key(board, KeyEvent.VK_B, 0); pair(palette, colors, 1, false);
                board.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING); pair(palette, colors, 1, false);
                board.setAnnotationTool(AnnotationTool.FREE_CHAIN); palette.selectPaletteGroup(5); pair(palette, colors, 5, false);
                palette.swapColors(); pair(palette, colors, 5, false);
                board.setAnnotationTool(AnnotationTool.BOX_SELECTION); palette.selectPaletteGroup(4); pair(palette, colors, 4, false);
                board.setAnnotationTool(AnnotationTool.DOODLE);
                palette.setPrimaryColor(new Color(117,80,208));
                // Ensure a deliberate gesture after any prior wheel debounce.
                Field last = CellZoomPanel.class.getDeclaredField("lastPaletteWheelAt"); last.setAccessible(true); last.setLong(palette,0);
                palette.cyclePaletteColor(1,true); pair(palette,colors,2,false);
                palette.swapColors();
                key(board,KeyEvent.VK_D,0);pair(palette,colors,3,true);
                key(board,KeyEvent.VK_A,0);pair(palette,colors,0,true);
                key(board,KeyEvent.VK_B,InputEvent.SHIFT_DOWN_MASK);pair(palette,colors,1,true);
                board.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING); pair(palette,colors,1,false);
                JToolBar toolbar = (JToolBar)read(frame,"jToolBar1");
                ToolbarColorPalette compact = palette.getToolbarPalette();
                require(compact.getParent() == toolbar,"palette not in toolbar");
                require(!((Component)read(palette,"colorPalette")).isVisible(),"duplicate sidebar palette");
                palette.setToolbarPaletteVisible(false);
                require(((Component)read(palette,"colorPalette")).isVisible(),"hidden-toolbar fallback lost");
                palette.setToolbarPaletteVisible(true);
                for (int width : new int[]{1120,768}) {
                    frame.setSize(width,850); frame.validate();
                    require(compact.getBounds().x + compact.getWidth() <= toolbar.getWidth(),"palette clipped at " + width);
                    for(Component c : compact.getComponents()) require(c.getX()+c.getWidth()<=compact.getWidth(),"palette child clipped");
                    File out = new File(System.getProperty("hodoku.probe.output"),"palette-"+width+".png");
                    BufferedImage img = new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);
                    Graphics2D g=img.createGraphics();frame.paint(g);g.dispose();ImageIO.write(img,"png",out);
                }
                System.out.println("PASS: paired wheel (including Opt), A-D without puzzle mutation, swap orientation, tool memory, group-only tools, custom nearest color, toolbar and fallback, 1120/768 layout");
            } catch(Throwable t) { failure[0]=t; } finally { if(frame!=null)frame.dispose(); }
        });
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);} System.exit(0);
    }
    static void pair(CellZoomPanel p, Color[] c,int group,boolean flipped) {
        require(p.getPrimaryColor().equals(c[group*2+(flipped?1:0)]) && p.getSecondaryColor().equals(c[group*2+(flipped?0:1)]),"pair mismatch group "+group+" flipped "+flipped);
    }
    static void key(SudokuPanel board,int key,int modifiers) {
        KeyEvent pressed=new KeyEvent(board,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),modifiers,key,(char)key);
        KeyEvent released=new KeyEvent(board,KeyEvent.KEY_RELEASED,System.currentTimeMillis(),modifiers,key,(char)key);
        for(KeyListener l:board.getKeyListeners()){l.keyPressed(pressed);l.keyReleased(released);}
    }
    static Object read(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
