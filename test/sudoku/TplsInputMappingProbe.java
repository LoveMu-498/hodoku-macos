package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Real Swing input listeners and timers; isolated board, plus native Option/middle input. */
public final class TplsInputMappingProbe {
    interface Action {void run() throws Exception;}
    static void ui(Action a)throws Exception{edt(()->{a.run();return null;});}
    static void settle()throws Exception {
        Object n=Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        Thread.sleep((n instanceof Integer?(Integer)n:500)+80);ui(()->{});
    }
    static void tap(int button,int mods,Point point)throws Exception{ui(()->click(button,mods,point));settle();}
    static void twice(int button,int mods,Point point)throws Exception {
        ui(()->{click(button,mods,point);click(button,mods,point);});settle();
    }
    static Map<Integer,Color> cells()throws Exception{return (Map<Integer,Color>)read("coloringMap");}
    static Map<Integer,Color> colors()throws Exception{return (Map<Integer,Color>)read("coloringCandidateMap");}
    static java.util.List<DoodleStroke> ink()throws Exception{return (java.util.List<DoodleStroke>)read("doodleStrokes");}
    static java.util.List<SudokuSet> boxes()throws Exception{return (java.util.List<SudokuSet>)read("boxReasoningGroups");}
    static void mode(AnnotationTool t)throws Exception{ui(()->p.setAnnotationTool(t));}
    static void key(int code,boolean down,int mods)throws Exception {
        ui(()->{KeyEvent e=new KeyEvent(p,down?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(),mods,code,KeyEvent.CHAR_UNDEFINED);
            if(down)p.handleAnnotationKeyPressed(e);else p.handleAnnotationToolKeyReleased(e);});
    }
    static void paintPanel() {
        BufferedImage im=new BufferedImage(p.getWidth(),p.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=im.createGraphics();p.paint(g);g.dispose();
    }
    public static void main(String[] args)throws Exception {
        try {
            ui(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();
                p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);
                p.setSize(810,810);paintPanel();});
            Point a=edt(()->point(0)),b=edt(()->point(12)),c=edt(()->point(30)),d=edt(()->point(50));
            Point blank=new Point(1,1),end=new Point(809,809);
            String board=edt(()->TechniqueStepCatalog.createSignature(p.getSudoku()));
            mode(AnnotationTool.CANDIDATE_COLORING);
            twice(1,0,a);ui(()->check(colors().containsKey(1)&&cells().isEmpty(),"T double click leaked cell"));
            tap(1,0,b);tap(1,0,c);twice(1,0,d);
            ui(()->{click(3,0,blank);p.flushMappedClick();check(!colors().containsKey(501)&&cells().size()==2,"T newest candidate");
                click(3,0,blank);p.flushMappedClick();check(!cells().containsKey(30)&&cells().containsKey(12),"T newest cell C");
                click(3,0,blank);p.flushMappedClick();check(cells().isEmpty()&&colors().containsKey(1),"T cell B");
                click(3,0,blank);p.flushMappedClick();check(colors().isEmpty(),"T candidate A");p.undoCurrentAnnotation();check(colors().containsKey(1),"T recent delete undo");});
            tap(1,0,a);twice(3,0,a);ui(()->check(cells().containsKey(0)&&!colors().containsKey(1),"T right double deleted cell"));
            tap(3,0,b);ui(()->check(cells().containsKey(0),"T local miss deleted remote"));
            tap(1,InputEvent.SHIFT_DOWN_MASK,a);ui(()->{drag(1,InputEvent.CTRL_DOWN_MASK,blank,end);check(cells().isEmpty()&&colors().isEmpty(),"T mixed rectangle");
                p.undoCurrentAnnotation();check(cells().containsKey(0)&&colors().containsKey(1),"T rectangle not atomic");});
            ui(()->{click(1,0,b);p.cancelAnnotationToolInteractionOnDeactivation();});settle();
            ui(()->check(!cells().containsKey(12),"T delayed write after focus loss"));
            ui(()->{click(1,0,b);p.handleEscapeVisualReset();});settle();
            ui(()->check(!cells().containsKey(12),"T delayed write after Escape"));
            // Recolor ordering survives undo/redo and is independent of the capped undo history.
            tap(1,0,b);ui(()->{p.undoCurrentAnnotation();p.redoCurrentAnnotation();click(3,0,blank);p.flushMappedClick();check(!cells().containsKey(12),"T redo ordering");});
            mode(AnnotationTool.DOODLE);
            twice(1,0,a);ui(()->check(ink().size()==1&&ink().get(0).getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS,"P double leaked circle"));
            tap(1,0,a);tap(1,0,a);ui(()->check(ink().size()==1,"P repeated circle did not toggle off"));tap(1,0,a);
            ui(()->{drag(1,0,b,c);check(ink().size()==3,"P free pen");drag(1,InputEvent.SHIFT_DOWN_MASK,b,d);check(ink().size()==4,"P ellipse");
                drag(1,InputEvent.CTRL_DOWN_MASK,blank,end);check(ink().isEmpty(),"P ordinary right rectangle");p.undoCurrentAnnotation();check(ink().size()==4,"P rectangle atomic undo");
                p.setDoodleFreeEraser(true);});
            tap(1,0,a);ui(()->check(ink().size()==2,"P free eraser candidate marks"));
            ui(()->{drag(1,InputEvent.CTRL_DOWN_MASK,blank,end);check(ink().isEmpty(),"P right rectangle while free eraser");p.undoCurrentAnnotation();p.setDoodleFreeEraser(false);});
            key(KeyEvent.VK_ALT,true,InputEvent.ALT_DOWN_MASK);
            ui(()->{check((Boolean)read("doodleThoughtPreviewHeld"),"P Option preview");int before=ink().size();click(1,InputEvent.ALT_DOWN_MASK,a);check(ink().size()==before,"P preview edited ink");
                event(MouseEvent.MOUSE_PRESSED,2,InputEvent.BUTTON2_DOWN_MASK|InputEvent.ALT_DOWN_MASK,a);});
            key(KeyEvent.VK_ALT,false,0);ui(()->{check((Boolean)read("doodleThoughtPreviewHeld"),"P combined preview released early");
                event(MouseEvent.MOUSE_RELEASED,2,0,a);check(!(Boolean)read("doodleThoughtPreviewHeld"),"P release stuck");});
            mode(AnnotationTool.BOX_SELECTION);ui(()->((CellZoomPanel)read("cellZoomPanel")).selectPaletteGroup(0));
            tap(1,0,a);tap(1,0,a);ui(()->check(boxes().get(0).contains(0),"S duplicate removed cell"));
            ui(()->{((CellZoomPanel)read("cellZoomPanel")).selectPaletteGroup(1);});tap(1,0,a);
            ui(()->check(boxes().get(0).contains(0)&&boxes().get(1).contains(0),"S cross group lost existing"));
            tap(3,0,a);ui(()->{check(!boxes().get(0).contains(0)&&!boxes().get(1).contains(0),"S delete all hit groups");p.undoCurrentAnnotation();
                drag(1,0,blank,end);check(boxes().get(1).size()==81,"S add rectangle");
                drag(1,InputEvent.CTRL_DOWN_MASK,blank,end);check(boxes().get(0).isEmpty()&&boxes().get(1).isEmpty(),"S delete rectangle");});
            mode(AnnotationTool.FREE_CHAIN);tap(1,0,a);tap(1,0,b);tap(1,0,c);
            ui(()->check(active().getNodes().size()==3,"L append"));
            twice(3,0,a);ui(()->check(active().getNodes().size()==3&&(Integer)read("preciseChainCandidate")==1,"L double retreated tail"));
            tap(3,0,b);ui(()->{check(active().getNodes().size()==2&&active().getNodes().get(0).contains(12,1),"L exact delete/orphan");p.undoCurrentAnnotation();});
            twice(1,0,a);tap(1,0,b);ui(()->{check(!active().getStrongRelations().get(0)&&active().getNodes().size()==3,"L exact flip/appended node");p.undoCurrentAnnotation();});
            ui(()->{drag(1,0,blank,end);check(active().getStrongRelations().equals(Arrays.asList(false,true)),"L rectangle flip");p.undoCurrentAnnotation();
                drag(1,InputEvent.CTRL_DOWN_MASK,blank,end);check(active()==null&&done().isEmpty(),"L rectangle delete");p.undoCurrentAnnotation();});
            tap(3,0,a);ui(()->{check(active().getNodes().size()==2,"L right on candidate retreat");p.undoCurrentAnnotation();});
            twice(1,0,blank);ui(()->check(active().getStrongRelations().get(1),"L blank double tail flip"));
            tap(1,0,blank);ui(()->check(p.isNextUserChainStrong(),"L blank single next state"));
            twice(1,InputEvent.SHIFT_DOWN_MASK,d);ui(()->{check(done().size()==1&&active().getNodes().size()==1,"L Shift double restart");p.undoCurrentAnnotation();});
            key(KeyEvent.VK_ALT,true,InputEvent.ALT_DOWN_MASK);
            ui(()->{check((Boolean)read("chainRelationPreviewHeld")&&!(Boolean)read("chainOptionDown"),"L Option preview aliases old restart");
                event(MouseEvent.MOUSE_PRESSED,2,InputEvent.BUTTON2_DOWN_MASK|InputEvent.ALT_DOWN_MASK,a);});
            key(KeyEvent.VK_ALT,false,0);ui(()->{check((Boolean)read("chainRelationPreviewHeld"),"combined preview released early");
                event(MouseEvent.MOUSE_RELEASED,2,0,a);check(!(Boolean)read("chainRelationPreviewHeld"),"middle release stuck");});
            key(KeyEvent.VK_H,true,0);key(KeyEvent.VK_ALT,true,InputEvent.ALT_DOWN_MASK);key(KeyEvent.VK_H,false,InputEvent.ALT_DOWN_MASK);
            ui(()->check((Boolean)read("chainRelationPreviewHeld"),"H release ended Option"));key(KeyEvent.VK_ALT,false,0);
            ui(()->check(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"annotation changed Sudoku"));
            // A held tool borrowed from another annotation mode survives the click timer.
            mode(AnnotationTool.DOODLE);key(KeyEvent.VK_T,true,0);
            ui(()->click(1,0,b));key(KeyEvent.VK_T,false,0);settle();
            ui(()->check(p.getAnnotationTool()==AnnotationTool.DOODLE&&cells().containsKey(12),"borrowed tool lost pending click"));
            mode(AnnotationTool.FREE_CHAIN);
            // All-layer clear and independent recovery use the existing per-tool transactions.
            ui(()->{p.clearAllAnnotationsWithUndo();check(!p.hasColoring()&&ink().isEmpty()&&active()==null,"R clear layers");
                p.undoCurrentAnnotation();check(active()!=null&&!p.hasColoring()&&ink().isEmpty(),"L undo restored other tools");});
            System.out.println("PASS: T chronology/local/mixed erase; P marks/pen/ellipse/erasers; S add/remove; L exact/batch/tail/restart; cancellation, preview sources, independent undo; Sudoku unchanged");
            ui(()->{f.setVisible(true);f.toFront();p.requestFocusInWindow();});
            Robot robot=new Robot();robot.setAutoDelay(35);robot.waitForIdle();
            Point nativePoint=edt(()->{Point q=p.getLocationOnScreen();q.translate(p.getWidth()/2,p.getHeight()/2);return q;});
            robot.mouseMove(nativePoint.x,nativePoint.y);robot.keyPress(KeyEvent.VK_ALT);robot.waitForIdle();
            ui(()->check((Boolean)read("chainRelationPreviewHeld"),"native Option press"));
            robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);robot.keyRelease(KeyEvent.VK_ALT);robot.waitForIdle();
            ui(()->check((Boolean)read("chainRelationPreviewHeld"),"native middle continuation"));
            robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);robot.waitForIdle();
            ui(()->check(!(Boolean)read("chainRelationPreviewHeld"),"native middle release"));
            System.out.println("PASS: native Robot Option/middle overlap through MainFrame dispatcher");
            ui(()->{p.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);p.clearColoringWithUndo();paintPanel();});
            Point nativeCandidate=edt(()->{Point q=point(0),origin=p.getLocationOnScreen();q.translate(origin.x,origin.y);return q;});
            robot.mouseMove(nativeCandidate.x,nativeCandidate.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);settle();
            ui(()->check(colors().containsKey(1)&&cells().isEmpty(),"native double leaked cell"));
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);settle();
            ui(()->check(colors().isEmpty(),"native right double did not erase candidate"));
            ui(()->{p.setAnnotationTool(AnnotationTool.DOODLE);f.validate();BufferedImage screenshot=new BufferedImage(f.getWidth(),f.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics=screenshot.createGraphics();f.paint(graphics);graphics.dispose();
                javax.imageio.ImageIO.write(screenshot,"png",new java.io.File(System.getProperty("java.io.tmpdir"), "doodle-controls.png"));});
            System.out.println("PASS: native left/right double-click exclusivity;  control screenshot saved");
        } catch(Throwable error) {error.printStackTrace();System.exit(1);}
        finally {if(f!=null)ui(()->f.dispose());}
        System.exit(0);
    }
}
