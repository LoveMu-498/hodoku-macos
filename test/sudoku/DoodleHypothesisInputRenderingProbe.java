package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JTextField;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;

/** Native G/Escape input plus deterministic listener, rendering and OR safety checks. */
public final class DoodleHypothesisInputRenderingProbe {
    static final Path OUTPUT=Paths.get(System.getProperty("hodoku.probe.output", "/tmp/hodoku-input-render"));
    static KeyEvent key(Component c,int id,int modifiers) {
        return new KeyEvent(c,id,System.currentTimeMillis(),modifiers,KeyEvent.VK_G,'g');
    }
    static BufferedImage paint() {
        BufferedImage image=new BufferedImage(p.getWidth(),p.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();p.paint(g);g.dispose();return image;
    }
    static int count(BufferedImage image,Point center,Color color) {
        int result=0;
        for(int y=Math.max(0,center.y-30);y<Math.min(image.getHeight(),center.y+31);y++)
            for(int x=Math.max(0,center.x-30);x<Math.min(image.getWidth(),center.x+31);x++)
                { Color pixel=new Color(image.getRGB(x,y));
                  if(Math.abs(pixel.getRed()-color.getRed())<55&&Math.abs(pixel.getGreen()-color.getGreen())<55
                          &&Math.abs(pixel.getBlue()-color.getBlue())<55)result++; }
        return result;
    }
    static void deterministic() throws Exception {
        Field controls=MainFrame.class.getDeclaredField("annotationToolButtons");controls.setAccessible(true);
        javax.swing.JToggleButton[] tools=(javax.swing.JToggleButton[])controls.get(f);
        Field appendField=MainFrame.class.getDeclaredField("doodleHypothesisEntryButton");appendField.setAccessible(true);
        Field cancelField=MainFrame.class.getDeclaredField("doodleHypothesisCancelButton");cancelField.setAccessible(true);
        javax.swing.AbstractButton append=(javax.swing.AbstractButton)appendField.get(f),cancel=(javax.swing.AbstractButton)cancelField.get(f);
        check(append.getParent()==tools[AnnotationTool.DOODLE.ordinal()].getParent()&&cancel.getParent()==append.getParent(),"start icons outside reserved group");
        for(javax.swing.AbstractButton button:new javax.swing.AbstractButton[]{append,cancel})
            check(button.getIcon()!=null&&(button.getText()==null||button.getText().isEmpty())&&button.getPreferredSize().width==38,"start action is not icon-only");
        for(AnnotationTool tool:AnnotationTool.values()){
            p.setAnnotationTool(tool);check(append.isVisible()==(tool==AnnotationTool.DOODLE)&&cancel.isVisible()==append.isVisible(),"P icon leaked into other mode");
        }
        p.setAnnotationTool(AnnotationTool.DOODLE);
        append.doClick();check(append.isSelected()&&p.isDoodleHypothesisEntryActive(),"append icon state not synchronized");
        append.doClick();check(!append.isSelected()&&!p.isDoodleHypothesisEntryActive(),"append icon toggle not synchronized");
        p.clearDoodlesWithUndo();p.getCellZoomPanel().selectPaletteGroup(0);mark(0,1,2,0);p.toggleDoodleHypothesisEntry();
        int countBefore=ink().size(),undoBefore=((java.util.Stack<?>)read("doodleUndoStack")).size();
        Point newStart=ChainOriginProbe.candidate(10,2);
        sudoku.ChainEditingProbe.click(1,0,newStart);
        check(ink().size()==countBefore&&((java.util.Stack<?>)read("doodleUndoStack")).size()==undoBefore,"rejected immediate circle wrote state");
        sudoku.ChainEditingProbe.click(1,0,newStart);p.flushMappedClick();
        check(find(10,2,1,0)==null&&find(10,2,2,0)!=null&&find(10,2,2,0).isHypothesisStart()
                &&find(10,2,2,0).getHypothesisGroupKind()==2,"double-click cross lost homogeneous start after rejected first circle");
        check(((java.util.Stack<?>)read("doodleUndoStack")).size()==undoBefore+1,"double-click cross was not one undo");
        p.clearDoodlesWithUndo();
        p.finishDoodleHypothesisEntry();p.handleAnnotationToolKeyReleased(key(p,KeyEvent.KEY_RELEASED,0));
        p.handleAnnotationKeyPressed(key(p,KeyEvent.KEY_PRESSED,0));
        p.handleAnnotationKeyPressed(key(p,KeyEvent.KEY_PRESSED,0));
        check(p.isDoodleHypothesisEntryActive(),"G repeat toggled entry back off");
        p.handleAnnotationToolKeyReleased(key(p,KeyEvent.KEY_RELEASED,0));
        p.handleAnnotationKeyPressed(key(p,KeyEvent.KEY_PRESSED,0));check(!p.isDoodleHypothesisEntryActive(),"released second G did not toggle off");
        p.handleAnnotationToolKeyReleased(key(p,KeyEvent.KEY_RELEASED,0));
        p.handleAnnotationKeyPressed(key(new JTextField(),KeyEvent.KEY_PRESSED,0));check(!p.isDoodleHypothesisEntryActive(),"text G intercepted");
        p.handleAnnotationKeyPressed(key(p,KeyEvent.KEY_PRESSED,InputEvent.META_DOWN_MASK));check(!p.isDoodleHypothesisEntryActive(),"Command G intercepted");
        p.finishDoodleHypothesisEntry();p.getCellZoomPanel().selectPaletteGroup(0);
        p.toggleDoodleHypothesisEntry();p.getCellZoomPanel().selectPaletteGroup(1);check(!p.isDoodleHypothesisEntryActive(),"color change retained entry");
        p.toggleDoodleHypothesisEntry();p.setAnnotationTool(AnnotationTool.BOX_SELECTION);check(!p.isDoodleHypothesisEntryActive(),"tool change retained entry");
        p.setAnnotationTool(AnnotationTool.DOODLE);p.toggleDoodleHypothesisEntry();p.cancelAnnotationToolInteractionOnDeactivation();check(!p.isDoodleHypothesisEntryActive(),"focus loss retained entry");
        p.getCellZoomPanel().selectPaletteGroup(0);
        mark(0,1,1,0);mark(0,1,1,0);p.clearDoodlesWithUndo();mark(0,1,1,0);
        check(find(0,1,1,0).isHypothesisStart(),"explicit empty clear failed to rearm first start");
        mark(0,1,1,1);mark(10,2,2,0);mark(10,2,2,1);
        BufferedImage before=paint();Point circle=ChainOriginProbe.candidate(0,1),cross=ChainOriginProbe.candidate(10,2);
        ImageIO.write(before,"png",OUTPUT.resolve("segmented-sources.png").toFile());
        check(count(before,circle,Color.RED)>2&&count(before,circle,Color.BLUE)>2,"composed circle lacks both source colors");
        check(count(before,cross,Color.RED)>2&&count(before,cross,Color.BLUE)>2,"composed cross lacks both source colors: red="+count(before,cross,Color.RED)+" blue="+count(before,cross,Color.BLUE));
        outline(0,1);BufferedImage selected=paint();
        check(count(selected,circle,new Color(0,215,45))>2,"start green frame missing");
        check(count(selected,circle,new Color(0x1769ff))>2,"selected blue frame missing");
        ImageIO.write(before,"png",OUTPUT.resolve("segmented-sources.png").toFile());
        ImageIO.write(selected,"png",OUTPUT.resolve("start-and-selection.png").toFile());
        p.clearDoodlesWithUndo();p.toggleDoodleHypothesisEntry();mark(20,3,1,0);mark(30,4,1,0);p.finishDoodleHypothesisEntry();
        check(mask(20,3,1)==1&&mask(30,4,1)==1,"OR identity removed visual ink source");
        Method preview=SudokuPanel.class.getDeclaredMethod("buildDoodleThoughtPreviewProjection",int.class);preview.setAccessible(true);
        Object projection=preview.invoke(p,0);Field fills=projection.getClass().getDeclaredField("filledCells");fills.setAccessible(true);
        check(((Map<?,?>)fills.get(projection)).isEmpty(),"OR preview filled individual members");
        outline(20,3);check(find(20,3,1,0).isConclusionOutlined(),"OR identity prevented independent blue selection");
        // Even persisted or legacy selection metadata must not turn OR members into definite values.
        find(20,3,1,0).setConclusionOutlined(true);
        String board=TechniqueStepCatalog.createSignature(p.getSudoku());call("applyOutlinedDoodleConclusions");
        check(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"OR application filled individual member");
        BufferedImage group=paint();check(count(group,ChainOriginProbe.candidate(20,3),Color.RED)>2,"raw OR circle disappeared");
        ImageIO.write(group,"png",OUTPUT.resolve("or-group.png").toFile());
    }
    public static void main(String[] args)throws Exception {
        System.setProperty("apple.awt.UIElement","true");Files.createDirectories(OUTPUT);
        try {
            edt(()->{initialize();f.setSize(1100,950);f.setLocation(70,70);f.setVisible(true);f.toFront();p.requestFocusInWindow();return null;});
            // UIElement probe processes have no Dock icon; explicitly activate this test process.
            Process activation=new ProcessBuilder("osascript","-e","tell application \"System Events\" to set frontmost of first process whose unix id is "
                    +java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]+" to true").start();
            activation.waitFor();
            Robot robot=new Robot();robot.setAutoDelay(90);robot.waitForIdle();robot.delay(600);
            Point target=edt(()->{Point origin=p.getLocationOnScreen();origin.translate(p.getWidth()/2,p.getHeight()/2);return origin;});
            robot.mouseMove(target.x,target.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();
            edt(()->{p.requestFocusInWindow();return null;});robot.delay(300);
            robot.keyPress(KeyEvent.VK_G);robot.keyRelease(KeyEvent.VK_G);robot.waitForIdle();
            boolean nativeEntered=edt(()->p.isDoodleHypothesisEntryActive());
            robot.keyPress(KeyEvent.VK_ESCAPE);robot.keyRelease(KeyEvent.VK_ESCAPE);robot.waitForIdle();
            boolean nativeEscaped=!edt(()->p.isDoodleHypothesisEntryActive());
            System.out.println("Native Robot G entered="+nativeEntered+"; Escape ended="+nativeEscaped
                    +"; native focus owner="+edt(()->KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()));
            check(nativeEntered&&nativeEscaped,"native Robot G/Escape could not be verified after probe activation");
            edt(()->{deterministic();return null;});
            Rectangle window=edt(()->f.getBounds());ImageIO.write(robot.createScreenCapture(window),"png",OUTPUT.resolve("native-window.png").toFile());
            System.out.println("PASS: event repeat/text/Command/color/tool/focus boundaries; explicit empty-clear rearm; rendered segmented red-blue circle/cross and green-blue contours; OR source/preview/selection/application safety. Native G/Escape verified="+(nativeEntered&&nativeEscaped)+". Images: "+OUTPUT);
        }catch(Throwable t){t.printStackTrace();System.exit(1);}
        finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
