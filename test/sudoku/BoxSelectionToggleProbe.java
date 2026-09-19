package sudoku;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

/** Actual Swing pointer handlers, independent annotation history, and ALS candidate union. */
public final class BoxSelectionToggleProbe {
    private static SudokuPanel panel;
    private static MainFrame frame;
    private static Point cell(int index) throws Exception {
        Field size = SudokuPanel.class.getDeclaredField("cellSize"); size.setAccessible(true);
        int half = size.getInt(panel) / 2;
        return new Point(panel.getX(index / 9, index % 9) + half,
                panel.getY(index / 9, index % 9) + half);
    }
    private static void event(int id, Point point, int button, int modifiers) {
        panel.dispatchEvent(new MouseEvent(panel, id, System.currentTimeMillis(), modifiers,
                point.x, point.y, 1, false, button));
    }
    private static void click(int index, int button, int modifiers) throws Exception {
        event(MouseEvent.MOUSE_PRESSED, cell(index), button, modifiers);
        event(MouseEvent.MOUSE_RELEASED, cell(index), button, modifiers);
    }
    private static void history(String name) throws Exception {
        Method method = SudokuPanel.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(panel);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                frame = new MainFrame(null); panel = frame.getSudokuPanel();
                panel.setSudoku((String)null);
                panel.getSudoku().setSudoku(new String(new char[81]).replace('\0', '0')); 
                panel.setSize(720,720);
                java.awt.Graphics2D graphics = new BufferedImage(720,720,2).createGraphics();
                panel.paint(graphics); graphics.dispose();
                panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);
                panel.getCellZoomPanel().selectPaletteGroup(0);
                String board = TechniqueStepCatalog.createSignature(panel.getSudoku());
                click(0,1,0); click(0,1,0);
                require(panel.getBoxReasoningFootprint().isEmpty(), "left click must toggle off");
                click(0,1,0);
                panel.getCellZoomPanel().selectPaletteGroup(1);
                event(MouseEvent.MOUSE_PRESSED,cell(0),1,0);
                event(MouseEvent.MOUSE_DRAGGED,cell(10),0,InputEvent.BUTTON1_DOWN_MASK);
                Method predicted = SudokuPanel.class.getDeclaredMethod("isBoxReasoningCellPredicted",int.class,int.class);
                predicted.setAccessible(true);
                require(!(Boolean)predicted.invoke(panel,0,0) && (Boolean)predicted.invoke(panel,1,10), "preview must match toggle");
                event(MouseEvent.MOUSE_RELEASED,cell(10),1,0);
                require(!panel.getBoxReasoningFootprint().contains(0) && panel.getBoxReasoningGroupsSnapshot().get(1).size()==3, "mixed rectangle toggle");
                history("undoBoxReasoning");
                require(panel.getBoxReasoningGroupsSnapshot().get(0).contains(0) && panel.getBoxReasoningGroupsSnapshot().get(1).isEmpty(), "atomic undo");
                history("redoBoxReasoning");
                click(20,1,InputEvent.CTRL_DOWN_MASK);
                require(!panel.getBoxReasoningFootprint().contains(20), "Control cannot add");
                click(1,1,InputEvent.CTRL_DOWN_MASK);
                require(!panel.getBoxReasoningFootprint().contains(1), "Control removes");
                require(board.equals(TechniqueStepCatalog.createSignature(panel.getSudoku())), "annotation changed puzzle");
                panel.clearBoxReasoningWithUndo();
                panel.getSudoku().setCell(2,9);
                for(int digit=1;digit<=9;digit++) {
                    panel.getSudoku().setCandidate(0,digit,digit==1 || digit==2);
                    panel.getSudoku().setCandidate(1,digit,digit==2 || digit==3);
                }
                click(0,1,0);click(1,1,0);click(2,1,0);
                int[] counts=panel.getBoxReasoningCounts(1);
                require(counts[0]==2 && counts[1]==3, "ALS union must be 2 cells / 3 digits, excluding filled cell");
                Field status = MainFrame.class.getDeclaredField("statusLabelCellSelection");status.setAccessible(true);
                require(panel.getInspectedBoxGroup()==-1, "counts must be hidden by default");
                click(0,3,0);
                require(panel.getInspectedBoxGroup()==1, "right hit inspects group");
                String text=((JLabel)status.get(frame)).getText();
                require(text.contains("B") && text.contains("2") && text.contains("3"), "visible status counts: "+text);
                require(panel.getStep()==null, "inspection must not analyze");
                SudokuSet footprint=panel.getBoxReasoningFootprint();
                click(0,3,InputEvent.CTRL_DOWN_MASK);
                require(footprint.equals(panel.getBoxReasoningFootprint()), "Control-right cannot edit boxes");
                panel.getCellZoomPanel().selectPaletteGroup(0);
                require(panel.getInspectedBoxGroup()==-1, "palette change hides counts");
                click(20,3,0);
                require(panel.getInspectedBoxGroup()==-1, "empty current group miss does nothing");
                click(0,3,0);
                require(panel.getInspectedBoxGroup()==1 && panel.getActiveBoxReasoningGroup()==0, "hit group overrides current color without changing it");
                click(20,1,0);
                require(panel.getInspectedBoxGroup()==-1, "left edit hides counts");
                click(30,3,0);
                require(panel.getInspectedBoxGroup()==0, "miss falls back to nonempty current group");
                panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
                require(panel.getInspectedBoxGroup()==-1, "leaving box tool hides counts");
                System.out.println("Box toggle/preview/Control/history/ALS distinct candidate counts passed: "+text);
            } catch(Exception error) { throw new RuntimeException(error); }
            finally { if(frame!=null)frame.dispose(); }
        });
        System.exit(0);
    }
}
