/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.Stack;
import static sudoku.GroupedChainTransactionProbe.*;

/** Native held mouse input and displayed frames from an isolated test JVM.
 * Uses the existing focus guard; any unexpected focus loss stops injection. */
public final class DragFeedbackNativeProbe {
    @SuppressWarnings("unchecked") private static List<DoodleStroke> ink() throws Exception {
        return (List<DoodleStroke>)read("doodleStrokes");
    }
    private static int history() throws Exception {return ((Stack<?>)read("doodleUndoStack")).size();}
    private static Point screen(Point point) throws Exception {
        return edt(()->{Point result=new Point(point),origin=p.getLocationOnScreen();result.translate(origin.x,origin.y);return result;});
    }
    private static void reset(AnnotationTool tool) throws Exception {
        ChainEvidenceNativeProbe.verifyFocus();edt(()->{
            p.cancelAnnotationToolInteractionOnDeactivation();p.setSudoku((String)null);
            p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);
            p.setAnnotationTool(tool);p.getCellZoomPanel().selectPaletteGroup(0);
            ChainEvidenceNativeProbe.paintActualPanel();p.repaint();return null;
        });ChainEvidenceNativeProbe.idle();
    }
    private static void press(int button) throws Exception {ChainEvidenceNativeProbe.pressButton(button);}
    private static void release(int button) throws Exception {ChainEvidenceNativeProbe.releaseButton(button);}
    private static void move(Point point) throws Exception {ChainEvidenceNativeProbe.move(point);}
    private static void shot(String name) throws Exception {ChainEvidenceNativeProbe.screenshot(name);}
    private static void nativeSecondHold() throws Exception {
        reset(AnnotationTool.DOODLE);Point at=ChainEvidenceNativeProbe.candidateOnScreen(40,5);move(at);
        int before=edt(()->history());press(InputEvent.BUTTON1_DOWN_MASK);release(InputEvent.BUTTON1_DOWN_MASK);
        press(InputEvent.BUTTON1_DOWN_MASK);
        check(edt(()->(Boolean)read("mappedSecondClick")),"native second press did not qualify within 200ms");
        ChainEvidenceNativeProbe.robot.delay(350);ChainEvidenceNativeProbe.idle();
        check(edt(()->ink().size()==1&&ink().get(0).isStandardCandidateMark()&&read("activeDoodleStroke")==null),
                "native second hold lost the first circle before dragging");shot("p-second-held");
        move(new Point(at.x+90,at.y+50));
        check(edt(()->ink().isEmpty()&&read("doodleGesture").toString().equals("ELLIPSE")&&read("activeDoodleStroke")!=null),
                "native drag did not replace first circle with ellipse preview");shot("p-second-drag-ellipse");
        release(InputEvent.BUTTON1_DOWN_MASK);
        check(edt(()->ink().size()==1&&!ink().get(0).isStandardCandidateMark()&&history()==before+1),"native ellipse release/history");
        System.out.println("PASS: native second press within 200ms, 350ms held circle, drag ellipse handoff and one release undo");
    }
    private static void nativeBox() throws Exception {
        reset(AnnotationTool.BOX_SELECTION);Point a=ChainEvidenceNativeProbe.candidateOnScreen(20,5),b=ChainEvidenceNativeProbe.candidateOnScreen(40,5);
        move(a);press(InputEvent.BUTTON1_DOWN_MASK);ChainEvidenceNativeProbe.robot.delay(250);
        check(edt(()->p.getBoxReasoningFootprint().isEmpty()&&read("boxDragPreview")==null),"native S press flashed committed cell");shot("s-held");
        move(b);check(edt(()->p.getBoxReasoningFootprint().isEmpty()&&(Boolean)read("boxDragging")),"native S drag edited before release");shot("s-drag-contour");
        release(InputEvent.BUTTON1_DOWN_MASK);check(edt(()->p.getBoxReasoningFootprint().size()>=4),"native S release missed rectangle");shot("s-released");
        System.out.println("PASS: native S held press/drag show contour only; release adds selection");
    }
    private static void nativeSweep() throws Exception {
        reset(AnnotationTool.DOODLE);Point a=edt(()->new Point(p.getWidth()/5,p.getHeight()/2)),b=edt(()->new Point(p.getWidth()*4/5,p.getHeight()/2));
        move(screen(a));press(InputEvent.BUTTON1_DOWN_MASK);move(screen(b));release(InputEvent.BUTTON1_DOWN_MASK);
        edt(()->{p.flushMappedClick();return null;});check(edt(()->ink().size()==1),"native freehand fixture");
        int before=edt(()->history());Point start=new Point((a.x+b.x)/2,a.y-40),end=new Point(start.x,a.y+40);
        ChainEvidenceNativeProbe.robot.delay(250);move(screen(start));press(InputEvent.BUTTON3_DOWN_MASK);move(screen(end));
        ChainEvidenceNativeProbe.robot.delay(250);ChainEvidenceNativeProbe.idle();
        check(edt(()->ink().size()==1&&history()==before&&!((List<?>)read("doodleEraseGhosts")).isEmpty()),"native sweep preview mutated raw/history");shot("p-sweep-held-half-alpha");
        ChainEvidenceNativeProbe.pressKey(KeyEvent.VK_ESCAPE);ChainEvidenceNativeProbe.releaseKey(KeyEvent.VK_ESCAPE);
        release(InputEvent.BUTTON3_DOWN_MASK);
        check(edt(()->ink().size()==1&&history()==before&&read("doodleErasePreview")==null),"native Esc committed sweep");shot("p-sweep-cancelled");
        ChainEvidenceNativeProbe.robot.delay(250);move(screen(start));press(InputEvent.BUTTON3_DOWN_MASK);move(screen(end));release(InputEvent.BUTTON3_DOWN_MASK);
        check(edt(()->ink().size()>1&&history()==before+1&&((List<?>)read("doodleEraseGhosts")).isEmpty()),"native sweep release/history");shot("p-sweep-released");
        System.out.println("PASS: native right sweep held preview, Esc restore and release-only erasure/history");
    }
    public static void main(String[] args) throws Exception {
        ChainEvidenceNativeProbe.output=new File(System.getProperty("hodoku.probe.output"));
        check(ChainEvidenceNativeProbe.output.isDirectory()||ChainEvidenceNativeProbe.output.mkdirs(),"cannot create native evidence directory");
        Throwable failure = null;
        try {
            ChainEvidenceNativeProbe.initialize();ChainEvidenceNativeProbe.robot.setAutoDelay(20);
            nativeSecondHold();nativeBox();nativeSweep();
            System.out.println("SCOPE: native Robot input and screenshots from isolated test frame loaded from packaged JAR; launcher/Quit/relaunch verified separately");
        } catch(Throwable error) { failure = error; }
        finally { failure = ChainEvidenceNativeProbe.finish(failure); }
        if (failure != null) failure.printStackTrace();
        System.exit(failure == null ? 0 : 1);
    }
}
