/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Actual Swing pointer transitions, paint alpha, and release/cancel history. */
public final class DragFeedbackPreviewProbe {
    private static long clock;
    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method=SudokuPanel.class.getDeclaredMethod(name,types); method.setAccessible(true);
        return method.invoke(p,args);
    }
    @SuppressWarnings("unchecked") private static List<DoodleStroke> ink() throws Exception {
        return (List<DoodleStroke>)read("doodleStrokes");
    }
    @SuppressWarnings("unchecked") private static List<DoodleStroke> ghosts() throws Exception {
        return (List<DoodleStroke>)read("doodleEraseGhosts");
    }
    @SuppressWarnings("unchecked") private static List<DoodleStroke> retained() throws Exception {
        return (List<DoodleStroke>)read("doodleErasePreview");
    }
    @SuppressWarnings("unchecked") private static Map<Integer,Color> cells() throws Exception {
        return (Map<Integer,Color>)read("coloringMap");
    }
    private static int history(String name) throws Exception {return ((Stack<?>)read(name)).size();}
    private static void input(int id,int button,Point point,long offset) {
        int modifiers=id==MouseEvent.MOUSE_DRAGGED ? button==1?InputEvent.BUTTON1_DOWN_MASK:InputEvent.BUTTON3_DOWN_MASK : 0;
        p.dispatchEvent(new MouseEvent(p,id,clock+offset,modifiers,point.x,point.y,1,false,
                id==MouseEvent.MOUSE_DRAGGED?0:button));
    }
    private static void tap(int button,Point point) {input(MouseEvent.MOUSE_PRESSED,button,point,0);input(MouseEvent.MOUSE_RELEASED,button,point,1);}
    private static BufferedImage paintInk() throws Exception {
        BufferedImage image=new BufferedImage(810,810,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        invoke("drawDoodles",new Class<?>[]{Graphics2D.class,int.class,int.class,boolean.class},graphics,810,810,false);
        graphics.dispose();return image;
    }
    private static void reset(AnnotationTool tool) throws Exception {
        p.cancelAnnotationToolInteractionOnDeactivation();p.setSudoku((String)null);
        p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setSize(810,810);
        set("doubleClickSpeed",200L);set("suppressNextAnnotationPointerRelease",false);
        p.setAnnotationTool(tool);p.getCellZoomPanel().selectPaletteGroup(0);
        Graphics2D graphics=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB).createGraphics();p.paint(graphics);graphics.dispose();
        clock=System.currentTimeMillis();
    }
    private static Point candidate(int cell,int digit) throws Exception {return ChainOriginProbe.candidate(cell,digit);}
    private static void circle(int cell) throws Exception {tap(1,candidate(cell,5));p.flushMappedClick();clock+=250;}
    private static boolean predicted(int group,int cell) throws Exception {
        return (Boolean)invoke("isBoxReasoningCellPredicted",new Class<?>[]{int.class,int.class},group,cell);
    }
    private static void boxFeedback() throws Exception {
        reset(AnnotationTool.BOX_SELECTION);Point a=candidate(0,1),b=candidate(10,9);
        int before=history("boxReasoningUndoStack");
        input(MouseEvent.MOUSE_PRESSED,1,a,0);
        check(!predicted(0,0)&&read("boxDragPreview")==null,"S press flashed a formal first cell");
        input(MouseEvent.MOUSE_DRAGGED,1,new Point(a.x+3,a.y),10);
        check(!predicted(0,0)&&!(Boolean)read("boxDragging"),"S jitter committed or latched");
        input(MouseEvent.MOUSE_DRAGGED,1,b,20);
        check((Boolean)read("boxDragging")&&!predicted(0,10),"S drag must show only its contour");
        input(MouseEvent.MOUSE_DRAGGED,1,a,30);
        check((Boolean)read("boxDragging"),"S return to origin lost the drag latch");
        input(MouseEvent.MOUSE_RELEASED,1,a,40);
        check(p.getBoxReasoningFootprint().isEmpty()&&history("boxReasoningUndoStack")==before,
                "zero-size returned drag became a click");
        clock+=250;tap(1,a);check(p.getBoxReasoningFootprint().contains(0),"S click did not commit immediately");
        before=history("boxReasoningUndoStack");clock+=250;
        input(MouseEvent.MOUSE_PRESSED,3,a,0);input(MouseEvent.MOUSE_DRAGGED,3,b,10);
        check(predicted(0,0)&&history("boxReasoningUndoStack")==before,"S erase preview changed committed selection");
        input(MouseEvent.MOUSE_RELEASED,3,b,20);
        check(!p.getBoxReasoningFootprint().contains(0)&&history("boxReasoningUndoStack")==before+1,"S erase release/history");
        p.undoCurrentAnnotation();check(p.getBoxReasoningFootprint().contains(0),"S undo");
        p.redoCurrentAnnotation();check(!p.getBoxReasoningFootprint().contains(0),"S redo");
        System.out.println("PASS: S press/jitter/drag/return, release-only add/remove, undo/redo");
    }
    private static void boxHits() throws Exception {
        reset(AnnotationTool.BOX_SELECTION);int size=(Integer)read("cellSize"),x=p.getX(4,4),y=p.getY(4,4);
        int before=history("boxReasoningUndoStack");
        Point top=new Point(x,y),underHalf=new Point(x+size/2-1,y+size);
        input(MouseEvent.MOUSE_PRESSED,1,top,0);input(MouseEvent.MOUSE_DRAGGED,1,underHalf,10);input(MouseEvent.MOUSE_RELEASED,1,underHalf,20);
        check(!p.getBoxReasoningFootprint().contains(40)&&history("boxReasoningUndoStack")==before,"S below 50 percent without center hit");
        clock+=250;Point half=new Point(x+(size+1)/2,y+size);
        input(MouseEvent.MOUSE_PRESSED,1,half,0);input(MouseEvent.MOUSE_DRAGGED,1,top,10);input(MouseEvent.MOUSE_RELEASED,1,top,20);
        check(p.getBoxReasoningFootprint().contains(40),"S half-area/reversed boundary missed");p.undoCurrentAnnotation();
        clock+=250;Point center=new Point(x+size/2,y+size/2);
        Point first=new Point(center.x-3,center.y-3),last=new Point(center.x+3,center.y+3);
        input(MouseEvent.MOUSE_PRESSED,1,first,0);input(MouseEvent.MOUSE_DRAGGED,1,last,10);input(MouseEvent.MOUSE_RELEASED,1,last,20);
        check(p.getBoxReasoningFootprint().contains(40),"S center should hit independently of small area");
        System.out.println("PASS: S under-half rejection, 50 percent/reversed boundary, independent center inclusion");
    }
    private static void secondHold() throws Exception {
        reset(AnnotationTool.DOODLE);Point a=candidate(0,1);int before=history("doodleUndoStack");
        tap(1,a);input(MouseEvent.MOUSE_PRESSED,1,a,100);
        check(ink().size()==1&&ink().get(0).isStandardCandidateMark(),"P second hold erased first circle");
        input(MouseEvent.MOUSE_DRAGGED,1,new Point(a.x+3,a.y),101);
        check(ink().size()==1&&read("activeDoodleStroke")==null,"P 3px jitter replaced circle");
        input(MouseEvent.MOUSE_DRAGGED,1,new Point(a.x+4,a.y),102);
        check(ink().isEmpty()&&read("doodleGesture").toString().equals("ELLIPSE")&&read("activeDoodleStroke")!=null,
                "P 4px must roll back and create contour in one handler");
        Point end=new Point(a.x+100,a.y+60);input(MouseEvent.MOUSE_DRAGGED,1,end,110);input(MouseEvent.MOUSE_RELEASED,1,end,120);
        check(ink().size()==1&&!ink().get(0).isStandardCandidateMark()&&history("doodleUndoStack")==before+1,"P ellipse release/one undo");
        p.undoCurrentAnnotation();check(ink().isEmpty(),"P ellipse undo");p.redoCurrentAnnotation();check(ink().size()==1,"P ellipse redo");
        reset(AnnotationTool.DOODLE);circle(0);a=candidate(0,5);tap(3,a);input(MouseEvent.MOUSE_PRESSED,3,a,100);
        check(ink().isEmpty(),"P right second hold restored erase too soon");
        input(MouseEvent.MOUSE_DRAGGED,3,new Point(a.x+4,a.y),101);
        check(ink().size()==1&&read("doodleGesture").toString().equals("RECT_ERASER"),"P right drag rollback/rectangle");
        p.handleEscapeVisualReset();check(ink().size()==1&&ghosts().isEmpty(),"P rectangle cancel");
        reset(AnnotationTool.CANDIDATE_COLORING);a=candidate(0,1);tap(1,a);p.flushMappedClick();clock+=250;tap(3,a);
        input(MouseEvent.MOUSE_PRESSED,3,a,100);check(!cells().containsKey(0),"T hold restored first erase");
        input(MouseEvent.MOUSE_DRAGGED,3,new Point(a.x+4,a.y),101);
        check(cells().containsKey(0)&&read("coloringPress")!=null,"T drag did not restore and show rectangle together");
        p.handleEscapeVisualReset();check(cells().containsKey(0),"T cancel failed pre-first restore");
        reset(AnnotationTool.FREE_CHAIN);install(fixture());Point blank=new Point(2,2);boolean strength=p.isNextUserChainStrong();
        tap(1,blank);boolean first=p.isNextUserChainStrong();check(first!=strength,"L fixture first effect");
        input(MouseEvent.MOUSE_PRESSED,1,blank,100);check(p.isNextUserChainStrong()==first,"L second hold lost first effect");
        input(MouseEvent.MOUSE_DRAGGED,1,new Point(6,2),101);
        check(p.isNextUserChainStrong()==strength&&read("chainFlipStart")!=null,"L drag handoff");
        p.handleEscapeVisualReset();
        System.out.println("PASS: same-target P ellipse/eraser, T erase and L handoff retain first effect until 4px");
    }
    private static void sweepPreview(boolean cancel) throws Exception {
        reset(AnnotationTool.DOODLE);Point a=new Point(150,380),b=new Point(650,380);
        input(MouseEvent.MOUSE_PRESSED,1,a,0);input(MouseEvent.MOUSE_DRAGGED,1,b,10);input(MouseEvent.MOUSE_RELEASED,1,b,20);
        p.flushMappedClick();check(ink().size()==1,"freehand fixture");int before=history("doodleUndoStack");
        BufferedImage original=paintInk();clock+=250;Point start=new Point(350,340),end=new Point(350,420);
        input(MouseEvent.MOUSE_PRESSED,3,start,0);input(MouseEvent.MOUSE_DRAGGED,3,end,10);
        check(ink().size()==1&&history("doodleUndoStack")==before&&!ghosts().isEmpty(),"sweep preview mutated raw/history or has no ghost");
        BufferedImage preview=paintInk();int erased=preview.getRGB(350,380)>>>24,kept=preview.getRGB(600,380)>>>24;
        javax.imageio.ImageIO.write(preview,"png",new java.io.File(System.getProperty("java.io.tmpdir"),"sweep-quarter-alpha.png"));
        check(erased>=62&&erased<=65&&kept==255,"freehand erased alpha/retained ink: "+erased+"/"+kept);
        Point next=new Point(500,380);input(MouseEvent.MOUSE_DRAGGED,3,next,20);
        preview=paintInk();check((preview.getRGB(350,380)>>>24)>0&&(preview.getRGB(500,380)>>>24)>=62,"sweep forgot earlier erased path");
        if(cancel) {
            p.handleEscapeVisualReset();input(MouseEvent.MOUSE_RELEASED,3,next,30);
            check(ghosts().isEmpty()&&retained()==null&&history("doodleUndoStack")==before&&ink().size()==1,"cancel committed sweep");
            check(paintInk().getRGB(350,380)==original.getRGB(350,380),"cancel did not restore opaque ink");
        } else {
            input(MouseEvent.MOUSE_RELEASED,3,next,30);
            check(ghosts().isEmpty()&&history("doodleUndoStack")==before+1&&(paintInk().getRGB(350,380)>>>24)==0,"release did not remove ghost once");
            p.undoCurrentAnnotation();check(ink().size()==1&&(paintInk().getRGB(350,380)>>>24)==255,"sweep undo");
            p.redoCurrentAnnotation();check((paintInk().getRGB(350,380)>>>24)==0,"sweep redo");
        }
        System.out.println("PASS: sweep partial-ink quarter alpha, retained opacity, cumulative path, "+(cancel?"cancel":"release/undo/redo"));
    }
    private static int alphaForColor(BufferedImage image,Color color) {
        int alpha=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            int pixel=image.getRGB(x,y);
            // Premultiplied channels can round RGB at quarter alpha.
            if(Math.abs((pixel>>16&255)-color.getRed())<=2&&Math.abs((pixel>>8&255)-color.getGreen())<=2
                    &&Math.abs((pixel&255)-color.getBlue())<=2)alpha=Math.max(alpha,pixel>>>24);
        }return alpha;
    }
    private static void sharedAndRectangle() throws Exception {
        reset(AnnotationTool.DOODLE);circle(40);p.cancelDoodleHypothesisStarts();
        DoodleStroke first=ink().get(0);Color firstColor=first.getColor();
        p.getCellZoomPanel().selectPaletteGroup(1);circle(40);p.cancelDoodleHypothesisStarts();
        Color secondColor=ink().get(1).getColor();int before=history("doodleUndoStack");Point center=candidate(40,5);
        BufferedImage original=paintInk();Point a=new Point(center.x-30,center.y-30),end=new Point(center.x+30,center.y+30);
        // Cross-position second right press selects the existing rectangular eraser.
        tap(3,new Point(100,100));input(MouseEvent.MOUSE_PRESSED,3,a,100);input(MouseEvent.MOUSE_DRAGGED,3,end,110);
        check(ink().size()==2&&retained().size()==1&&ghosts().size()==1,"rectangle source-specific atomic cut");
        BufferedImage image=paintInk();
        javax.imageio.ImageIO.write(image,"png",new java.io.File(System.getProperty("java.io.tmpdir"),"shared-quarter-alpha.png"));
        check(alphaForColor(image,firstColor)==alphaForColor(original,firstColor),"other source dimmed with erased source");
        check(alphaForColor(image,secondColor)>=62&&alphaForColor(image,secondColor)<=65,"erased source did not keep its quarter-alpha color slot");
        Point shrink=new Point(a.x+5,a.y+5);input(MouseEvent.MOUSE_DRAGGED,3,shrink,120);
        check(retained().size()==2&&ghosts().isEmpty(),"shrinking rectangle failed to restore ink");
        input(MouseEvent.MOUSE_DRAGGED,3,end,130);input(MouseEvent.MOUSE_RELEASED,3,end,140);
        check(ink().size()==1&&ink().get(0).getThoughtGroup()==0&&history("doodleUndoStack")==before+1,"rectangle release removed peer or history");
        p.undoCurrentAnnotation();check(ink().size()==2,"rectangle undo");p.redoCurrentAnnotation();check(ink().size()==1,"rectangle redo");
        reset(AnnotationTool.DOODLE);circle(40);DoodleStroke outlined=ink().get(0);outlined.setHypothesisStart(true);outlined.setConclusionOutlined(true);
        original=paintInk();center=candidate(40,5);a=new Point(center.x-30,center.y);end=new Point(center.x+30,center.y);
        input(MouseEvent.MOUSE_PRESSED,3,a,0);input(MouseEvent.MOUSE_DRAGGED,3,end,10);image=paintInk();
        java.lang.reflect.Field outlineColor=SudokuPanel.class.getDeclaredField("DOODLE_CONCLUSION_OUTLINE_COLOR");outlineColor.setAccessible(true);
        Color green=new Color(0,215,45);Color blue=(Color)outlineColor.get(null);
        check(alphaForColor(original,green)==255&&alphaForColor(image,green)>=62&&alphaForColor(image,green)<=65,"green ghost outline must have quarter alpha");
        check(alphaForColor(original,blue)==255&&alphaForColor(image,blue)>=62&&alphaForColor(image,blue)<=65,"blue ghost outline must have quarter alpha: original="+alphaForColor(original,blue)+", preview="+alphaForColor(image,blue));
        p.handleEscapeVisualReset();
        System.out.println("PASS: atomic standard marks, color-source slots, rectangle shrink/release/history, green/blue ghost outlines");
    }
    private static void geometryProtection() throws Exception {
        DoodleStroke free=new DoodleStroke(Color.RED,.01f);free.setThoughtGroup(2);free.getPoints().add(new DoodlePoint(.1,.5));free.getPoints().add(new DoodlePoint(.9,.5));
        DoodleStroke proof=free.copy();proof.setProofOnly(true);
        List<DoodleStroke> raw=java.util.Arrays.asList(free,proof);java.awt.Shape cut=new java.awt.Rectangle(40,0,20,100);
        DoodleGeometry.ErasurePreview preview=DoodleGeometry.previewSubtract(raw,cut,100,100,
                stroke->java.awt.geom.AffineTransform.getScaleInstance(100,100));
        List<DoodleStroke> finalInk=DoodleGeometry.subtract(raw,cut,100,100);
        check((Boolean)invoke("sameDoodles",new Class<?>[]{List.class,List.class},preview.retained,finalInk),"preview and commit geometry disagree");
        check(preview.removed.size()==1&&!preview.removed.get(0).isProofOnly()&&preview.retained.get(preview.retained.size()-1).isProofOnly(),"proof-only source erased");
        check(preview.removed.get(0).getThoughtGroup()==2,"freehand ghost lost source-group opacity");
        System.out.println("PASS: preview/commit share cutter; proof-only source is preserved");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement","true");
        try {edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();boxFeedback();boxHits();secondHold();sweepPreview(true);sweepPreview(false);sharedAndRectangle();geometryProtection();return null;});}
        catch(Throwable error){error.printStackTrace();System.exit(1);}
        finally {if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
