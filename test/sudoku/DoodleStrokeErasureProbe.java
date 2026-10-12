/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import static sudoku.GroupedChainTransactionProbe.*;

/** Real panel pointer transactions and pixels for the accepted deletion units. */
public final class DoodleStrokeErasureProbe {
    static int checks;static Path output;
    static void verify(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Object feedback(String method,Class<?>[] types,Object... args)throws Exception{
        Method m=DragFeedbackPreviewProbe.class.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(null,args);
    }
    static void reset()throws Exception{feedback("reset",new Class<?>[]{AnnotationTool.class},AnnotationTool.DOODLE);p.setDoodleWholeStrokeEraser(false);}
    @SuppressWarnings("unchecked") static java.util.List<DoodleStroke> ink()throws Exception{return (java.util.List<DoodleStroke>)read("doodleStrokes");}
    @SuppressWarnings("unchecked") static java.util.List<DoodleStroke> retained()throws Exception{return (java.util.List<DoodleStroke>)read("doodleErasePreview");}
    static int history()throws Exception{return ((Stack<?>)read("doodleUndoStack")).size();}
    static void input(int id,Point pt,long offset)throws Exception{feedback("input",new Class<?>[]{int.class,int.class,Point.class,long.class},id,3,pt,offset);}
    static BufferedImage paint(String name)throws Exception{
        BufferedImage im=(BufferedImage)feedback("paintInk",new Class<?>[]{});
        if(name!=null)javax.imageio.ImageIO.write(im,"png",output.resolve(name+".png").toFile());return im;
    }
    static DoodleStroke line(int group,int x1,int y1,int x2,int y2,float width){
        DoodleStroke s=new DoodleStroke(new Color(170,88,0),width);s.setThoughtGroup(group);
        s.getPoints().add(new DoodlePoint(x1/810.0,y1/810.0));s.getPoints().add(new DoodlePoint(x2/810.0,y2/810.0));return s;
    }
    static DoodleStroke ellipse(int group){
        DoodleStroke s=new DoodleStroke(new Color(170,88,0),.01f);s.setThoughtGroup(group);
        for(int i=0;i<=100;i++){double a=i*2*Math.PI/100;s.getPoints().add(new DoodlePoint((400+100*Math.cos(a))/810,(400+100*Math.sin(a))/810));}return s;
    }
    static int alpha(BufferedImage im,int x,int y){return im.getRGB(x,y)>>>24;}
    static void quarter(int a,String message){verify(a>=62&&a<=65,message+": "+a);}
    static JToggleButton strategy()throws Exception{
        ToolbarColorPalette bar=(ToolbarColorPalette)p.getCellZoomPanel().getToolbarPalette();
        Field field=ToolbarColorPalette.class.getDeclaredField("eraseStrategy");field.setAccessible(true);return (JToggleButton)field.get(bar);
    }
    static void focusAndButton()throws Exception{
        reset();ink().add(line(1,100,100,650,100,.01f));ink().add(ellipse(1));
        p.getCellZoomPanel().selectPaletteGroup(1);DoodleContradictionWorkflowProbe.mark(60,5,1,1);p.cancelDoodleHypothesisStarts();p.getCellZoomPanel().selectPaletteGroup(0);
        BufferedImage im=paint("palette-focus");verify(alpha(im,350,100)==255&&alpha(im,500,400)==255,"free stroke or ellipse faded with other palette");
        Point c=ChainOriginProbe.candidate(60,5);int max=0;for(int y=c.y-24;y<=c.y+24;y++)for(int x=c.x-24;x<=c.x+24;x++)max=Math.max(max,alpha(im,x,y));
        p.getCellZoomPanel().selectPaletteGroup(1);BufferedImage current=paint("palette-current");int currentMax=0;
        for(int y=c.y-24;y<=c.y+24;y++)for(int x=c.x-24;x<=c.x+24;x++)currentMax=Math.max(currentMax,alpha(current,x,y));
        p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);BufferedImage ordinary=paint("palette-mouse");int ordinaryMax=0;boolean sameOrdinary=true;
        for(int y=c.y-24;y<=c.y+24;y++)for(int x=c.x-24;x<=c.x+24;x++){
            ordinaryMax=Math.max(ordinaryMax,alpha(ordinary,x,y));
            if(im.getRGB(x,y)!=ordinary.getRGB(x,y))sameOrdinary=false;
        }
        verify(max>=96&&currentMax>=96&&ordinaryMax>=96,"same circle must remain discernible across palette and M states: other="+max+", current="+currentMax+", M="+ordinaryMax);
        verify(sameOrdinary,"other-group circle differs from the same source's actual M ordinary display");
        p.setAnnotationTool(AnnotationTool.DOODLE);
        p.getCellZoomPanel().selectPaletteGroup(0);
        JToggleButton button=strategy();verify(button.isVisible()&&!button.isSelected()&&!p.isDoodleWholeStrokeEraser(),"initial toolbar policy not range");
        button.doClick();verify(p.isDoodleWholeStrokeEraser()&&button.isSelected(),"toolbar did not change deletion policy");
        Insets insets=button.getInsets();verify((button.getText()==null||button.getText().isEmpty())&&button.getIcon()!=null&&button.getPreferredSize().equals(new Dimension(38,38))
                &&button.getIcon().getIconWidth()+insets.left+insets.right<=38,"compact strategy icon would be clipped: text="+button.getText()+", preferred="+button.getPreferredSize()+", insets="+insets+", icon="+button.getIcon());
        p.getCellZoomPanel().selectPaletteGroup(2);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);verify(!button.isVisible(),"P attribute visible in other tool");
        p.setAnnotationTool(AnnotationTool.DOODLE);verify(button.isVisible()&&button.isSelected()&&p.isDoodleWholeStrokeEraser(),"tool/palette switch lost current-run strategy");
    }
    static void wholeSweep()throws Exception{
        reset();p.setDoodleWholeStrokeEraser(true);ink().add(line(0,150,380,650,380,.015f));ink().add(line(0,400,180,400,650,.015f));
        int before=history();String board=TechniqueStepCatalog.createSignature(p.getSudoku());
        input(MouseEvent.MOUSE_PRESSED,new Point(400,350),0);input(MouseEvent.MOUSE_DRAGGED,new Point(400,410),10);
        verify(ink().size()==2&&retained().isEmpty()&&history()==before,"whole preview changed source or failed to delete both crossing strokes");
        BufferedImage im=paint("whole-crossing-preview");quarter(alpha(im,600,380),"far end of whole stroke not faded");quarter(alpha(im,400,600),"far end of second stroke not faded");quarter(alpha(im,400,380),"crossing ghosts accumulated opacity");
        input(MouseEvent.MOUSE_DRAGGED,new Point(500,380),20);input(MouseEvent.MOUSE_DRAGGED,new Point(400,380),30);
        quarter(alpha(paint("repeated-whole-preview"),400,380),"repeat sweep deepened preview");
        input(MouseEvent.MOUSE_RELEASED,new Point(400,380),40);verify(ink().isEmpty()&&history()==before+1,"whole release not one transaction");
        p.undoCurrentAnnotation();verify(ink().size()==2&&alpha(paint(null),400,380)==255,"whole undo failed");p.redoCurrentAnnotation();verify(ink().isEmpty(),"whole redo failed");verify(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"eraser changed Sudoku");
    }
    static void widthEllipseAndFragments()throws Exception{
        reset();p.setDoodleWholeStrokeEraser(true);ink().add(line(0,150,200,650,200,.04f));
        Field radius=SudokuPanel.class.getDeclaredField("doodleEraserRadius");radius.setAccessible(true);radius.setFloat(p,.006f);
        input(MouseEvent.MOUSE_PRESSED,new Point(350,218),0);input(MouseEvent.MOUSE_DRAGGED,new Point(355,218),10);
        verify(retained().isEmpty(),"visible thick edge was missed because centerline was outside eraser");p.handleEscapeVisualReset();verify(ink().size()==1&&alpha(paint(null),600,200)==255,"edge preview cancellation failed");
        reset();p.setDoodleWholeStrokeEraser(true);ink().add(ellipse(0));input(MouseEvent.MOUSE_PRESSED,new Point(400,400),0);input(MouseEvent.MOUSE_DRAGGED,new Point(410,400),10);
        verify(retained().size()==1,"empty ellipse interior counted as ink");input(MouseEvent.MOUSE_DRAGGED,new Point(500,400),20);verify(retained().isEmpty(),"ellipse edge did not remove complete ellipse");quarter(alpha(paint("whole-ellipse-preview"),300,400),"opposite ellipse edge not faded");p.handleEscapeVisualReset();
        reset();ink().add(line(0,150,380,650,380,.01f));input(MouseEvent.MOUSE_PRESSED,new Point(400,350),0);input(MouseEvent.MOUSE_DRAGGED,new Point(400,410),10);input(MouseEvent.MOUSE_RELEASED,new Point(400,410),20);
        verify(ink().size()==2,"range eraser did not create independent remnants");p.setDoodleWholeStrokeEraser(true);input(MouseEvent.MOUSE_PRESSED,new Point(200,350),300);input(MouseEvent.MOUSE_DRAGGED,new Point(200,410),310);input(MouseEvent.MOUSE_RELEASED,new Point(200,410),320);
        verify(ink().size()==1&&alpha(paint(null),600,380)==255&&alpha(paint(null),200,380)==0,"whole deletion followed nonexistent original-pen lineage");
    }
    static void rangeOverlapAndRectangle()throws Exception{
        reset();ink().add(line(0,150,380,650,380,.015f));ink().add(line(0,400,180,400,650,.015f));
        input(MouseEvent.MOUSE_PRESSED,new Point(390,380),0);input(MouseEvent.MOUSE_DRAGGED,new Point(410,380),10);input(MouseEvent.MOUSE_DRAGGED,new Point(390,380),20);
        BufferedImage im=paint("range-overlap-preview");quarter(alpha(im,400,380),"range ghost overlap became dark");verify(alpha(im,600,380)==255,"range preview faded retained ink");p.handleEscapeVisualReset();
        reset();p.setDoodleWholeStrokeEraser(true);ink().add(line(0,150,380,650,380,.01f));
        // Use the existing second-right-press rectangle route.
        input(MouseEvent.MOUSE_PRESSED,new Point(50,50),0);input(MouseEvent.MOUSE_RELEASED,new Point(50,50),1);
        input(MouseEvent.MOUSE_PRESSED,new Point(350,340),100);input(MouseEvent.MOUSE_DRAGGED,new Point(450,420),110);
        verify(retained().isEmpty(),"rectangle did not use whole policy");quarter(alpha(paint("whole-rectangle-preview"),600,380),"rectangle faded only covered area");
        input(MouseEvent.MOUSE_DRAGGED,new Point(355,345),120);verify(retained().size()==1&&alpha(paint(null),600,380)==255,"shrinking whole rectangle did not restore target");p.handleEscapeVisualReset();
    }
    static void persistence()throws Exception{
        reset();p.setDoodleWholeStrokeEraser(true);ink().add(ellipse(0));GuiState state=new GuiState();state.setIncludeAnnotations(true);p.getState(state,true);
        SessionSnapshot snapshot=new SessionSnapshot();snapshot.setGuiState(state);SessionStore store=new SessionStore(output.resolve("stroke-session.xml").toFile());store.save(snapshot);p.setState(store.load().getGuiState());
        verify(p.isDoodleWholeStrokeEraser()&&ink().size()==1,"same-window restore lost run strategy or ink");
        MainFrame fresh=new MainFrame(null);try{fresh.getSudokuPanel().setState(store.load().getGuiState());verify(!fresh.getSudokuPanel().isDoodleWholeStrokeEraser(),"whole strategy persisted to new panel");}finally{fresh.dispose();}
    }
    static void curvedSweep()throws Exception{
        for(boolean whole:new boolean[]{false,true}) {
            reset();p.setDoodleWholeStrokeEraser(whole);
            DoodleStroke curve=new DoodleStroke(new Color(170,88,0),.012f);curve.setThoughtGroup(0);
            java.util.List<Point> samples=new ArrayList<Point>();
            for(int i=0;i<=80;i++) {
                double t=i*Math.PI/40;Point point=new Point((int)Math.round(400+220*Math.sin(t)),(int)Math.round(400+130*Math.sin(2*t)));
                samples.add(point);curve.getPoints().add(new DoodlePoint(point.x/810.0,point.y/810.0));
            }
            ink().add(curve);int before=history();
            input(MouseEvent.MOUSE_PRESSED,samples.get(0),0);
            for(int i=1;i<samples.size();i++)input(MouseEvent.MOUSE_DRAGGED,samples.get(i),i*10);
            verify(retained().isEmpty()&&ink().size()==1&&history()==before,"curved sweep did not predict full erasure without committing");
            for(int i=samples.size()-2;i>=0;i--)input(MouseEvent.MOUSE_DRAGGED,samples.get(i),(160-i)*10);
            BufferedImage image=paint(whole?"whole-self-cross-preview":"range-self-cross-preview");int max=0;
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)max=Math.max(max,alpha(image,x,y));
            quarter(max,"repeated curved sweep or self crossing deepened deletion preview");
            p.handleEscapeVisualReset();verify(ink().size()==1&&history()==before&&alpha(paint(null),400,400)==255,"curved sweep cancel did not restore original");
        }
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("apple.awt.UIElement","true");output=Files.createDirectories(Paths.get(System.getProperty("hodoku.probe.output",System.getProperty("java.io.tmpdir"))));
        try{edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();focusAndButton();wholeSweep();widthEllipseAndFragments();rangeOverlapAndRectangle();curvedSweep();persistence();return null;});
            System.out.println("PASS DoodleStrokeErasureProbe: "+checks+" actual panel/pixel/transaction/session checks; "+output);
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
