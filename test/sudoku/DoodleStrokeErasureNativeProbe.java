/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;

/** Native held erase in an isolated owned frame, with focus-loss injection guard. */
public final class DoodleStrokeErasureNativeProbe {
    static Point screen(Point local)throws Exception{return edt(()->{Point x=new Point(local),origin=p.getLocationOnScreen();x.translate(origin.x,origin.y);return x;});}
    @SuppressWarnings("unchecked") static java.util.List<DoodleStroke> ink()throws Exception{return (java.util.List<DoodleStroke>)read("doodleStrokes");}
    static BufferedImage image()throws Exception{
        BufferedImage im=new BufferedImage(p.getWidth(),p.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D g=im.createGraphics();
        Method m=SudokuPanel.class.getDeclaredMethod("drawDoodles",Graphics2D.class,int.class,int.class,boolean.class);m.setAccessible(true);m.invoke(p,g,p.getWidth(),p.getHeight(),false);g.dispose();return im;
    }
    public static void main(String[] args)throws Exception{
        ChainEvidenceNativeProbe.output=new File(System.getProperty("hodoku.probe.output"));ChainEvidenceNativeProbe.output.mkdirs();
        Throwable failure = null;
        try{
            ChainEvidenceNativeProbe.initialize();
            edt(()->{p.cancelAnnotationToolInteractionOnDeactivation();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(0);p.setDoodleWholeStrokeEraser(false);p.requestFocusInWindow();return null;});
            ChainEvidenceNativeProbe.idle();
            Point toggle=edt(()->{javax.swing.JToggleButton button=DoodleStrokeErasureProbe.strategy();Point point=button.getLocationOnScreen();point.translate(button.getWidth()/2,button.getHeight()/2);return point;});
            ChainEvidenceNativeProbe.nativeTap(toggle);check(edt(()->p.isDoodleWholeStrokeEraser()),"native toolbar did not select whole strokes");
            ChainEvidenceNativeProbe.robot.delay(250);
            Point a=edt(()->new Point(p.getWidth()/5,p.getHeight()/2)),b=edt(()->new Point(p.getWidth()*4/5,p.getHeight()/2));
            ChainEvidenceNativeProbe.move(screen(a));ChainEvidenceNativeProbe.pressButton(InputEvent.BUTTON1_DOWN_MASK);ChainEvidenceNativeProbe.move(screen(b));ChainEvidenceNativeProbe.releaseButton(InputEvent.BUTTON1_DOWN_MASK);
            edt(()->{p.flushMappedClick();check(ink().size()==1,"native freehand fixture missing");return null;});
            int before=edt(()->((Stack<?>)read("doodleUndoStack")).size());Point start=new Point((a.x+b.x)/2,a.y-40),end=new Point(start.x,a.y+40);
            ChainEvidenceNativeProbe.robot.delay(250);ChainEvidenceNativeProbe.move(screen(start));ChainEvidenceNativeProbe.pressButton(InputEvent.BUTTON3_DOWN_MASK);ChainEvidenceNativeProbe.move(screen(end));ChainEvidenceNativeProbe.robot.delay(250);ChainEvidenceNativeProbe.idle();
            edt(()->{check(ink().size()==1&&((java.util.List<?>)read("doodleErasePreview")).isEmpty()&&((Stack<?>)read("doodleUndoStack")).size()==before,"native whole preview changed raw/history");int alpha=image().getRGB(a.x+20,a.y)>>>24;check(alpha>=62&&alpha<=65,"native far end not quarter-alpha: "+alpha);return null;});
            ChainEvidenceNativeProbe.screenshot("whole-stroke-held-preview");
            Rectangle window=edt(()->new Rectangle(f.getLocationOnScreen(),f.getSize()));
            javax.imageio.ImageIO.write(ChainEvidenceNativeProbe.robot.createScreenCapture(window),"png",new File(ChainEvidenceNativeProbe.output,"toolbar-and-held-preview.png"));
            ChainEvidenceNativeProbe.pressKey(KeyEvent.VK_ESCAPE);ChainEvidenceNativeProbe.releaseKey(KeyEvent.VK_ESCAPE);ChainEvidenceNativeProbe.releaseButton(InputEvent.BUTTON3_DOWN_MASK);
            edt(()->{check(ink().size()==1&&read("doodleErasePreview")==null&&((Stack<?>)read("doodleUndoStack")).size()==before,"native cancel changed stroke");return null;});
            ChainEvidenceNativeProbe.robot.delay(250);ChainEvidenceNativeProbe.move(screen(start));ChainEvidenceNativeProbe.pressButton(InputEvent.BUTTON3_DOWN_MASK);ChainEvidenceNativeProbe.move(screen(end));ChainEvidenceNativeProbe.releaseButton(InputEvent.BUTTON3_DOWN_MASK);
            edt(()->{check(ink().isEmpty()&&((Stack<?>)read("doodleUndoStack")).size()==before+1,"native release did not commit whole deletion once");p.undoCurrentAnnotation();check(ink().size()==1,"native operation undo missing original stroke");return null;});
            ChainEvidenceNativeProbe.screenshot("whole-stroke-undone");
            System.out.println("PASS DoodleStrokeErasureNativeProbe: actual Robot pen/whole eraser, full quarter-alpha held preview, Escape, release and one undo; "+ChainEvidenceNativeProbe.output);
        } catch(Throwable error) { failure = error; }
        finally { failure = ChainEvidenceNativeProbe.finish(failure); }
        if (failure != null) failure.printStackTrace();
        System.exit(failure == null ? 0 : 1);
    }
}
