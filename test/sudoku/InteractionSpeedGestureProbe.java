/*
 * Copyright (C) 2026 HoDoKu contributors
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import javax.swing.MenuSelectionManager;
import javax.swing.Timer;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Real panel listeners with explicit input timestamps; no desktop input or sleep-based intervals. */
public final class InteractionSpeedGestureProbe {
    private interface Scenario { void run() throws Exception; }
    private static final List<String> failures = new ArrayList<String>();
    private static int passed;

    private static Object invoke(String name, Class<?>[] types, Object... values) throws Exception {
        Method method=SudokuPanel.class.getDeclaredMethod(name,types);
        method.setAccessible(true);return method.invoke(p,values);
    }
    private static void input(int id,int button,int modifiers,Point at,long when) {
        p.dispatchEvent(new MouseEvent(p,id,when,modifiers,at.x,at.y,1,false,button));
    }
    private static void tap(int button,int modifiers,Point at,long when) {
        input(MouseEvent.MOUSE_PRESSED,button,modifiers,at,when);
        input(MouseEvent.MOUSE_RELEASED,button,modifiers,at,when+1);
    }
    private static void secondPress(int button,int modifiers,Point at,long when) {
        input(MouseEvent.MOUSE_PRESSED,button,modifiers,at,when);
    }
    private static void dragTo(int button,int modifiers,Point at,long when) {
        int held=button==MouseEvent.BUTTON1?InputEvent.BUTTON1_DOWN_MASK:InputEvent.BUTTON3_DOWN_MASK;
        input(MouseEvent.MOUSE_DRAGGED,MouseEvent.NOBUTTON,modifiers|held,at,when);
    }
    private static Point candidate(int cell,int digit) throws Exception {
        return ChainOriginProbe.candidate(cell,digit);
    }
    private static int atom(Point at) throws Exception {
        return (Integer)invoke("mappedCandidate",new Class<?>[]{MouseEvent.class},
                new MouseEvent(p,MouseEvent.MOUSE_PRESSED,0,0,at.x,at.y,1,false,MouseEvent.BUTTON1));
    }
    private static void render() {
        BufferedImage image=new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();p.paint(graphics);graphics.dispose();
    }
    private static void reset(AnnotationTool tool) throws Exception {
        MenuSelectionManager.defaultManager().clearSelectedPath();
        p.cancelAnnotationToolInteractionOnDeactivation();
        p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());
        call("cancelMappedActiveGesture");
        for(String flag:new String[]{"optionPreviewHeld","middlePreviewHeld","spacePreviewHeld",
                "chainRelationPreviewHeld","doodleThoughtPreviewHeld","suppressNextAnnotationPointerRelease"})set(flag,false);
        set("doubleClickSpeed",200L);p.setShowCandidates(true);p.setSize(810,810);
        p.setAnnotationTool(tool);p.getCellZoomPanel().selectPaletteGroup(0);render();
        check((Integer)invoke("mappedClickInterval",new Class<?>[]{})==200,"fixture must use W=200");
    }
    @SuppressWarnings("unchecked") private static Map<Integer,Color> cells() throws Exception {
        return (Map<Integer,Color>)read("coloringMap");
    }
    @SuppressWarnings("unchecked") private static Map<Integer,Color> colors() throws Exception {
        return (Map<Integer,Color>)read("coloringCandidateMap");
    }
    @SuppressWarnings("unchecked") private static List<DoodleStroke> ink() throws Exception {
        return (List<DoodleStroke>)read("doodleStrokes");
    }
    private static int history(String field) throws Exception {return ((Stack<?>)read(field)).size();}
    private static int precise() throws Exception {return (Integer)read("preciseChainCandidate");}
    private static void circle(int cell,long when) throws Exception {
        tap(1,0,candidate(cell,5),when);p.flushMappedClick();
    }
    private static UserChain chain(boolean grouped) {
        UserChain result=GroupedChainProbe.chain(false,new UserChainNode[]{
                grouped?GroupedChainProbe.node(1,0,1):GroupedChainProbe.node(1,0),
                GroupedChainProbe.node(1,12),GroupedChainProbe.node(1,24)},true,false);
        Color[] palette=Options.getInstance().getColoringColors();
        result.getRelationColors().add(palette[0]);result.getRelationColors().add(palette[1]);
        result.setSourceId(700);return result;
    }
    private static String chainState() throws Exception {
        StringBuilder out=new StringBuilder();
        for(UserChain chain:done())appendChain(out,chain);
        if(active()!=null)appendChain(out,active());
        return out.append("next=").append(p.isNextUserChainStrong()).toString();
    }
    private static void appendChain(StringBuilder out,UserChain chain) {
        // Completed-chain snapshots carry the panel's global next strength in their last
        // dormant record. Compare that global setting below, and the active record here.
        out.append(chain.getSourceId()).append(':').append(chain.isActive()).append(':')
                .append(chain.isClosed()).append(':').append(chain.isActive()?chain.isNextStrong():"dormant").append(':');
        for(UserChainNode node:chain.getNodes())out.append(Arrays.toString(node.atoms())).append('/').append(node.getColor());
        out.append(chain.getStrongRelations()).append(chain.getRelationColors()).append(';');
    }
    private static void scenario(String name,Scenario scenario) throws Exception {
        try {edt(()->{scenario.run();return null;});passed++;System.out.println("PASS: "+name);}
        catch(Throwable failure) {
            Throwable cause=failure;
            while(cause.getCause()!=null)cause=cause.getCause();
            failures.add(name+": "+cause);System.out.println("FAIL: "+name+": "+cause);
        }
    }

    private static void timestampBoundary(int interval) throws Exception {
        reset(AnnotationTool.DOODLE);Point a=candidate(0,1);long when=System.currentTimeMillis();
        tap(1,0,a,when);check(ink().size()==1,"first circle must be immediate");
        tap(1,0,a,when+interval);p.flushMappedClick();
        if(interval>=0&&interval<=200)
            check(ink().size()==1&&ink().get(0).getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS,
                    "valid press interval must replace first circle with cross: "+interval);
        else check(ink().isEmpty(),"out-of-window pair must stay two circle toggles: "+interval);
    }
    private static void crossCandidateClick(AnnotationTool tool) throws Exception {
        reset(tool);Point a=candidate(0,1),b=candidate(12,1);long when=System.currentTimeMillis();
        tap(1,0,a,when);secondPress(1,0,b,when+150);
        check(tool==AnnotationTool.DOODLE?ink().size()==1:cells().containsKey(0),
                "cross-target second press must retain first effect");
        input(MouseEvent.MOUSE_RELEASED,1,0,b,when+151);p.flushMappedClick();
        if(tool==AnnotationTool.DOODLE) {
            check(ink().size()==2,"different P candidates must remain two marks");
            for(DoodleStroke stroke:ink())check(stroke.getCandidateMarkKind()==DoodleStroke.MARK_TRUE_CIRCLE,
                    "different candidates must not become a cross");
        } else check(cells().containsKey(0)&&cells().containsKey(12)&&colors().isEmpty(),
                "different T candidates must remain two cell colors");
    }
    private static void ellipse(boolean cancel) throws Exception {
        reset(AnnotationTool.DOODLE);long when=System.currentTimeMillis();
        if(cancel){circle(80,when-1000);p.undoCurrentAnnotation();}
        int undo=history("doodleUndoStack"),redo=history("doodleRedoStack");
        Point first=candidate(0,1),second=candidate(40,1);
        tap(1,0,first,when);secondPress(1,0,second,when+150);
        check(ink().size()==1,"second press prematurely rolled back first circle");
        dragTo(1,0,new Point(second.x+3,second.y),when+151);
        check(ink().size()==1&&read("doodleGesture").toString().equals("NONE"),"3px must not start drag");
        dragTo(1,0,new Point(second.x+4,second.y),when+152);
        check(ink().isEmpty()&&read("doodleGesture").toString().equals("ELLIPSE"),
                "4px cross-position drag must restore first circle and select ellipse");
        check(second.equals(read("doodleGestureStart")),"ellipse must anchor at second press");
        Point end=new Point(second.x+100,second.y+60);dragTo(1,0,end,when+160);
        if(cancel) {
            p.handleEscapeVisualReset();input(MouseEvent.MOUSE_RELEASED,1,0,end,when+161);
            check(ink().isEmpty()&&history("doodleUndoStack")==undo&&history("doodleRedoStack")==redo,
                    "cancel must restore pre-first undo and redo without committing circle/ellipse");
        } else {
            input(MouseEvent.MOUSE_RELEASED,1,0,end,when+161);
            check(ink().size()==1&&!ink().get(0).isStandardCandidateMark(),"ellipse leaked first circle");
            check(history("doodleUndoStack")==undo+1,"ellipse must own exactly one undo");
            p.undoCurrentAnnotation();check(ink().isEmpty(),"ellipse undo must reach pre-first state");
            p.redoCurrentAnnotation();check(ink().size()==1&&!ink().get(0).isStandardCandidateMark(),"ellipse redo");
        }
    }
    private static void rectangle(AnnotationTool tool,boolean cancel) throws Exception {
        reset(tool);long when=System.currentTimeMillis();Point first=candidate(0,5),mark=candidate(40,5);
        if(tool==AnnotationTool.DOODLE){circle(0,when-1000);circle(40,when-500);}
        else {p.handleColoring(0,0,-1,Color.RED);p.handleColoring(4,4,-1,Color.BLUE);}
        String stack=tool==AnnotationTool.DOODLE?"doodleUndoStack":"coloringUndoStack";
        int undo=history(stack);Point second=new Point(mark.x-25,mark.y-25),end=new Point(mark.x+25,mark.y+25);
        tap(3,0,first,when);
        check(tool==AnnotationTool.DOODLE?ink().size()==1:!cells().containsKey(0),"first right erase must be immediate");
        secondPress(3,0,second,when+150);
        check(tool==AnnotationTool.DOODLE?ink().size()==1:!cells().containsKey(0),
                "cross-position second press must retain first erase until drag");
        dragTo(3,0,new Point(second.x+4,second.y),when+151);
        if(tool==AnnotationTool.DOODLE) {
            check(ink().size()==2&&read("doodleGesture").toString().equals("RECT_ERASER"),"P cross-position rectangle restore");
            check(second.equals(read("doodleGestureStart")),"P rectangle anchor");
        } else {
            check(cells().containsKey(0)&&read("coloringSweep")==null,"T cross-position rectangle restore / mode");
            check(second.equals(read("coloringPress")),"T rectangle anchor");
        }
        dragTo(3,0,end,when+155);
        if(cancel) {
            p.handleEscapeVisualReset();input(MouseEvent.MOUSE_RELEASED,3,0,end,when+156);
            check((tool==AnnotationTool.DOODLE?ink().size()==2:cells().size()==2)&&history(stack)==undo,
                    "cancel rectangle must restore both original marks and history");
        } else {
            input(MouseEvent.MOUSE_RELEASED,3,0,end,when+156);
            check(tool==AnnotationTool.DOODLE?ink().size()==1&&ink().get(0).getAnchorCell()==0:
                    cells().containsKey(0)&&!cells().containsKey(40),"rectangle must erase second target and keep first");
            check(history(stack)==undo+1,"rectangle must leave only its own undo");
            p.undoCurrentAnnotation();check(tool==AnnotationTool.DOODLE?ink().size()==2:cells().size()==2,"rectangle undo");
            p.redoCurrentAnnotation();check(tool==AnnotationTool.DOODLE?ink().size()==1:cells().size()==1,"rectangle redo");
        }
    }
    private static void globalLeft(int interval,boolean onGridBlank) throws Exception {
        reset(AnnotationTool.FREE_CHAIN);install(chain(false));String before=chainState();
        int undo=history("userChainUndoStack");long when=System.currentTimeMillis();
        Point a=candidate(40,1),b=candidate(80,1);
        if(onGridBlank) {
            p.getSudoku().delCandidate(40,1);p.getSudoku().delCandidate(80,1);
            check(atom(a)<0&&atom(b)<0,"fixture needs two board blanks");
        }
        tap(1,0,a,when);if(!onGridBlank)check(active().getNodes().size()==4,"first chain node must be immediate");
        tap(1,0,b,when+interval);p.flushMappedClick();check(precise()<0,"global double must not enter precision");
        if(interval<=200) {
            check(active().getNodes().size()==3&&active().getStrongRelations().equals(Arrays.asList(true,true)),
                    "global left double must replace first node with last-edge flip");
            check(active().getSourceId()==700&&!p.isNextUserChainStrong(),"global flip source / next strength");
            check(active().getRelationColors().get(1).equals(Options.getInstance().getColoringColors()[0]),"flip relation color");
            check(history("userChainUndoStack")==undo+1,"global flip must own one undo");
            String after=chainState();p.undoCurrentAnnotation();check(before.equals(chainState()),"global flip undo state");
            p.redoCurrentAnnotation();check(after.equals(chainState()),"global flip redo state");
        } else check(active().getNodes().size()==5&&history("userChainUndoStack")==undo+2,
                "W+1 cross-candidate clicks must be two chain singles");
    }
    private static void globalRight(boolean closed,boolean finished) throws Exception {
        reset(AnnotationTool.FREE_CHAIN);UserChain chain=chain(false);
        if(closed){chain.setClosed(true);chain.getStrongRelations().add(true);chain.getRelationColors().add(Color.RED);}
        install(chain);if(finished)p.finishCurrentUserChain();
        String before=chainState();int undo=history("userChainUndoStack");long when=System.currentTimeMillis();
        tap(3,0,candidate(40,1),when);check(active()!=null,"first right must immediately backtrack");
        tap(3,0,candidate(80,1),when+150);p.flushMappedClick();
        check(precise()<0,"non-precise right double selected endpoint");
        check(active()!=null&&active().getSourceId()==700&&done().isEmpty(),"backtrack source / completed reopen");
        check(active().getNodes().size()==(closed?3:2)&&active().getStrongRelations().size()==(closed?2:1)
                &&!active().isClosed(),"whole right double must backtrack one edge only");
        check(history("userChainUndoStack")==undo+1,"whole right double must own one undo");
        String after=chainState();p.undoCurrentAnnotation();check(before.equals(chainState()),"right double undo lost original active/completed/source state; before="+before+" restored="+chainState());
        p.redoCurrentAnnotation();check(after.equals(chainState()),"right double redo state");
    }
    private static void preciseEndpoint(boolean deleting,boolean grouped,int firstCell,boolean missing,boolean ambiguous) throws Exception {
        reset(AnnotationTool.FREE_CHAIN);install(chain(grouped));
        if(ambiguous){UserChain duplicate=chain(grouped);duplicate.setSourceId(701);duplicate.setActive(false);done().add(duplicate);}
        String before=chainState();int undo=history("userChainUndoStack");long when=System.currentTimeMillis();
        Point first=candidate(firstCell,1);tap(deleting?3:1,0,first,when);
        tap(deleting?3:1,0,first,when+150);
        check(precise()==firstCell*10+1,"same pre-existing edge endpoint must enter precision");
        check(before.equals(chainState())&&history("userChainUndoStack")==undo,"precision entry retained first mutation/history");
        Point second=candidate(missing?80:firstCell==12?0:12,1);
        tap(deleting?3:1,0,second,when+180);p.flushMappedClick();
        check(precise()<0,"precise second endpoint must take priority over global pairing");
        if(missing||ambiguous)check(before.equals(chainState())&&history("userChainUndoStack")==undo,
                "missing/ambiguous edge must not mutate model/history");
        else {
            check(history("userChainUndoStack")==undo+1,"unique precise edit must own one undo");
            if(!deleting)check(active().getStrongRelations().equals(Arrays.asList(false,false)),"unique precise first-edge flip");
            else check(active()!=null&&active().getStrongRelations().size()==1,"unique precise first-edge delete");
            String after=chainState();p.undoCurrentAnnotation();check(before.equals(chainState()),"precise undo");
            p.redoCurrentAnnotation();check(after.equals(chainState()),"precise redo");
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement","true");
        boolean success=false;
        try {
            edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();return null;});
            for(final int interval:new int[]{199,200,201,-1})scenario("P input press interval "+interval+"ms",()->timestampBoundary(interval));
            scenario("effective system interval clips W below 200ms",()->{
                for(int interval:new int[]{99,100,101}) {
                    reset(AnnotationTool.DOODLE);set("doubleClickSpeed",100L);
                    check((Integer)invoke("mappedClickInterval",new Class<?>[]{})==100,"W must respect shorter effective system interval");
                    long when=System.currentTimeMillis();Point at=candidate(0,1);tap(1,0,at,when);tap(1,0,at,when+interval);p.flushMappedClick();
                    check(interval<=100?ink().size()==1&&ink().get(0).getCandidateMarkKind()==2:ink().isEmpty(),"short system interval boundary "+interval);
                }
            });
            scenario("queued input uses press deadline",()->{
                reset(AnnotationTool.DOODLE);Point a=candidate(0,1);long when=System.currentTimeMillis()-150;
                tap(1,0,a,when);int remaining=((Timer)read("mappedClickTimer")).getInitialDelay();
                check(remaining<=50,"EDT backlog added full double window: "+remaining);
                tap(1,0,a,when+170);check(ink().size()==1&&ink().get(0).getCandidateMarkKind()==2,"timestamp-valid queued double");
            });
            for(final AnnotationTool tool:new AnnotationTool[]{AnnotationTool.CANDIDATE_COLORING,AnnotationTool.DOODLE}) {
                scenario(tool+" different candidates stay two singles",()->crossCandidateClick(tool));
                scenario(tool+" cross-position rectangle",()->rectangle(tool,false));
                scenario(tool+" cross-position rectangle cancel",()->rectangle(tool,true));
            }
            scenario("P cross-position ellipse threshold / atomic history",()->ellipse(false));
            scenario("P cross-position ellipse cancel retains prior redo",()->ellipse(true));
            scenario("T distant canvas blanks clear mixed last two",()->{
                reset(AnnotationTool.CANDIDATE_COLORING);long when=System.currentTimeMillis();
                tap(1,0,candidate(0,1),when-1000);p.flushMappedClick();
                tap(1,InputEvent.SHIFT_DOWN_MASK,candidate(12,1),when-500);
                Point a=new Point(2,2),b=new Point(805,2);
                check(!(Boolean)invoke("isOnGrid",new Class<?>[]{Point.class},a)&&!(Boolean)invoke("isOnGrid",new Class<?>[]{Point.class},b),"canvas blank fixture");
                tap(3,0,a,when);check(cells().containsKey(0)&&colors().isEmpty(),"first blank immediate chronology");
                secondPress(3,0,b,when+150);check((Boolean)read("mappedSecondClick"),"distant canvas blanks must classify as a double");
                input(MouseEvent.MOUSE_RELEASED,3,0,b,when+151);p.flushMappedClick();check(cells().isEmpty()&&colors().isEmpty(),"distant blank double failed two removals");
            });
            scenario("T grid-local right double protects remote newest color",()->{
                reset(AnnotationTool.CANDIDATE_COLORING);p.handleColoring(0,0,-1,Color.RED);p.handleColoring(0,0,1,Color.BLUE);p.handleColoring(8,8,-1,Color.GREEN);
                long when=System.currentTimeMillis();Point a=candidate(0,1);tap(3,0,a,when);tap(3,0,a,when+150);p.flushMappedClick();
                check(cells().containsKey(0)&&cells().containsKey(80)&&!colors().containsKey(1),"grid double erased nonlocal or retained first local erase");
            });
            for(final int interval:new int[]{199,200,201})scenario("L global cross-candidate interval "+interval+"ms",()->globalLeft(interval,false));
            scenario("L global board noncandidate left double",()->globalLeft(150,true));
            scenario("L distant canvas and mixed board-canvas left double",()->{
                for(boolean mixed:new boolean[]{false,true}) {
                    reset(AnnotationTool.FREE_CHAIN);install(chain(false));String before=chainState();long when=System.currentTimeMillis();
                    Point first=mixed?candidate(40,1):new Point(2,2),second=new Point(805,2);
                    tap(1,0,first,when);tap(1,0,second,when+150);p.flushMappedClick();
                    check(active().getNodes().size()==3&&active().getStrongRelations().equals(Arrays.asList(true,true))&&precise()<0
                            &&history("userChainUndoStack")==1,"full canvas/mixed double must flip one old tail edge");
                    p.undoCurrentAnnotation();check(before.equals(chainState()),"canvas/mixed double undo");
                }
            });
            scenario("L global right double one edge / source / undo",()->globalRight(false,false));
            scenario("L completed global right double reopens one edge",()->globalRight(false,true));
            scenario("L closed global right double removes closing edge only",()->globalRight(true,true));
            scenario("L distant canvas right double removes one edge",()->{
                reset(AnnotationTool.FREE_CHAIN);install(chain(false));String before=chainState();long when=System.currentTimeMillis();
                tap(3,0,new Point(2,2),when);tap(3,0,new Point(805,2),when+150);p.flushMappedClick();
                check(active()!=null&&active().getNodes().size()==2&&precise()<0&&history("userChainUndoStack")==1,"canvas right double removed more than one edge");
                p.undoCurrentAnnotation();check(before.equals(chainState()),"canvas right double undo");
            });
            scenario("L same temporary candidate uses global flip",()->{
                reset(AnnotationTool.FREE_CHAIN);install(chain(false));int undo=history("userChainUndoStack");long when=System.currentTimeMillis();Point at=candidate(40,1);
                tap(1,0,at,when);check(active().getNodes().size()==4,"temporary node fixture");tap(1,0,at,when+150);p.flushMappedClick();
                check(precise()<0&&active().getNodes().size()==3&&active().getStrongRelations().equals(Arrays.asList(true,true))&&history("userChainUndoStack")==undo+1,
                        "first click's new endpoint must not manufacture precise eligibility");
            });
            scenario("L empty same-candidate double does not retain temporary start",()->{
                reset(AnnotationTool.FREE_CHAIN);long when=System.currentTimeMillis();Point at=candidate(0,1);tap(1,0,at,when);tap(1,0,at,when+150);p.flushMappedClick();
                check(active()==null&&precise()<0&&history("userChainUndoStack")==0,"empty double manufactured precise endpoint/start/history");
            });
            scenario("L singleton left double is not precise",()->{
                reset(AnnotationTool.FREE_CHAIN);UserChain single=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0)});single.setActive(true);set("activeUserChain",single);
                long when=System.currentTimeMillis();Point at=candidate(0,1);tap(1,0,at,when);tap(1,0,at,when+150);p.flushMappedClick();
                check(active()!=null&&active().getNodes().size()==1&&precise()<0&&history("userChainUndoStack")==0,"isolated start has no precise edge/no global flip");
            });
            scenario("L singleton right double removes only the start",()->{
                reset(AnnotationTool.FREE_CHAIN);UserChain single=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0)});single.setActive(true);set("activeUserChain",single);
                long when=System.currentTimeMillis();Point at=candidate(0,1);tap(3,0,at,when);tap(3,0,at,when+150);p.flushMappedClick();
                check(active()==null&&precise()<0&&history("userChainUndoStack")==1,"singleton right double restores phantom precision or extra undo");
                p.undoCurrentAnnotation();check(active()!=null&&active().getNodes().size()==1,"singleton right double undo");
            });
            scenario("L middle endpoint precise / fast second endpoint",()->preciseEndpoint(false,false,12,false,false));
            scenario("L grouped member precise flip",()->preciseEndpoint(false,true,1,false,false));
            scenario("L grouped member precise deletion",()->preciseEndpoint(true,true,1,false,false));
            scenario("L removed-on-first-click tail retains pre-first eligibility",()->{
                reset(AnnotationTool.FREE_CHAIN);install(chain(false));String before=chainState();long when=System.currentTimeMillis();Point tail=candidate(24,1);
                tap(3,0,tail,when);check(active().getNodes().size()==2,"first right tail backtrack fixture");tap(3,0,tail,when+150);
                check(precise()==241&&before.equals(chainState())&&history("userChainUndoStack")==0,"deleted first-click endpoint lost captured precise eligibility");
            });
            scenario("L precise missing edge leaves state/history",()->preciseEndpoint(false,false,0,true,false));
            scenario("L precise duplicate-edge ambiguity leaves state/history",()->preciseEndpoint(false,false,0,false,true));
            scenario("button and modifier changes never pair",()->{
                reset(AnnotationTool.FREE_CHAIN);install(chain(false));long when=System.currentTimeMillis();Point a=candidate(40,1),b=candidate(41,1);
                tap(1,0,a,when);tap(1,InputEvent.SHIFT_DOWN_MASK,b,when+150);p.flushMappedClick();
                check(active().getNodes().size()==4&&active().getNodes().get(3).atoms().length==2&&precise()<0,"modifier mismatch paired globally");
                reset(AnnotationTool.FREE_CHAIN);install(chain(false));String before=chainState();when=System.currentTimeMillis();
                tap(1,0,a,when);tap(3,0,b,when+150);p.flushMappedClick();
                check(before.equals(chainState())&&history("userChainUndoStack")==2,"button mismatch swallowed independent singles");
            });
            scenario("L Shift same-candidate double retains separate-segment entry",()->{
                reset(AnnotationTool.FREE_CHAIN);install(chain(false));long when=System.currentTimeMillis();Point at=candidate(40,1);
                tap(1,InputEvent.SHIFT_DOWN_MASK,at,when);tap(1,InputEvent.SHIFT_DOWN_MASK,at,when+150);p.flushMappedClick();
                check(done().size()==1&&active()!=null&&active().getNodes().size()==1&&active().getNodes().get(0).contains(40,1)&&precise()<0,"Shift restart replaced by global flip");
            });
            scenario("L Ctrl-left precise deletion alias retained",()->{
                reset(AnnotationTool.FREE_CHAIN);install(chain(false));long when=System.currentTimeMillis();Point first=candidate(0,1),second=candidate(12,1);
                tap(1,InputEvent.CTRL_DOWN_MASK,first,when);tap(1,InputEvent.CTRL_DOWN_MASK,first,when+150);
                check(precise()==1&&(Boolean)read("preciseChainDelete"),"Ctrl-left precise alias changed");tap(1,InputEvent.CTRL_DOWN_MASK,second,when+180);
                check(active()!=null&&active().getStrongRelations().size()==1,"Ctrl-left precise second endpoint changed");
            });
            scenario("tool switch / focus loss retain immediate first mark",()->{
                reset(AnnotationTool.CANDIDATE_COLORING);long when=System.currentTimeMillis();Point at=candidate(0,1);tap(1,0,at,when);
                p.setAnnotationTool(AnnotationTool.DOODLE);tap(1,0,at,when+150);p.cancelAnnotationToolInteractionOnDeactivation();
                check(cells().containsKey(0)&&ink().size()==1,"tool/focus change discarded immediate first effects");
            });
            scenario("released temporary T/P tool survives cross-target second click until final arbitration",()->{
                for(AnnotationTool borrowed:new AnnotationTool[]{AnnotationTool.CANDIDATE_COLORING,AnnotationTool.DOODLE}) {
                    AnnotationTool preceding=borrowed==AnnotationTool.DOODLE?AnnotationTool.CANDIDATE_COLORING:AnnotationTool.DOODLE;
                    reset(preceding);long when=System.currentTimeMillis();Point first=candidate(0,1),second=candidate(12,1);
                    int key=borrowed==AnnotationTool.DOODLE?KeyEvent.VK_P:KeyEvent.VK_T;
                    check(p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,when-10,0,key,KeyEvent.CHAR_UNDEFINED)),"temporary tool key must be handled");
                    check(p.getAnnotationTool()==borrowed,"temporary tool key did not borrow "+borrowed);
                    tap(1,0,first,when);check((Boolean)read("annotationToolPointerCaptured"),"provisional first click must retain pointer capture");
                    check(p.handleAnnotationToolKeyReleased(new KeyEvent(p,KeyEvent.KEY_RELEASED,when+100,0,key,KeyEvent.CHAR_UNDEFINED)),"temporary key release must be handled");
                    check(read("pendingAnnotationToolRestore")==preceding&&p.getAnnotationTool()==borrowed,
                            "physical key release must defer tool restoration while pointer is captured");
                    secondPress(1,0,second,when+150);check((Boolean)read("annotationToolPointerCaptured")
                            &&read("pendingAnnotationToolRestore")==preceding&&p.getAnnotationTool()==borrowed,
                            "cross-target second press must retain capture and the already-pending tool restore");
                    input(MouseEvent.MOUSE_RELEASED,1,0,second,when+160);
                    check(p.getAnnotationTool()==borrowed&&read("pendingAnnotationToolRestore")==preceding
                            &&read("pendingMappedClick")!=null,"confirming first click must not restore tool before second-click arbitration");
                    if(borrowed==AnnotationTool.DOODLE)
                        check(ink().size()==2&&ink().get(0).getAnchorCell()==0&&ink().get(1).getAnchorCell()==12&&cells().isEmpty()
                                &&history("doodleUndoStack")==2,"both independent clicks must belong to borrowed P, each with its own undo");
                    else check(cells().containsKey(0)&&cells().containsKey(12)&&colors().isEmpty()&&ink().isEmpty()
                            &&history("coloringUndoStack")==2,"both independent clicks must belong to borrowed T, each with its own undo");
                    p.flushMappedClick();check(p.getAnnotationTool()==preceding&&read("pendingAnnotationToolRestore")==null
                            &&!(Boolean)read("annotationToolPointerCaptured"),"final arbitration must restore preceding tool and release capture");
                }
            });
            scenario("expired borrowed T/P click restores M/S and dispatches the next press",()->{
                for(AnnotationTool preceding:new AnnotationTool[]{AnnotationTool.DEFAULT_MOUSE,AnnotationTool.BOX_SELECTION}) {
                    AnnotationTool borrowed=preceding==AnnotationTool.DEFAULT_MOUSE?AnnotationTool.CANDIDATE_COLORING:AnnotationTool.DOODLE;
                    reset(preceding);long when=System.currentTimeMillis();Point first=candidate(0,1),second=candidate(12,5);
                    int key=borrowed==AnnotationTool.DOODLE?KeyEvent.VK_P:KeyEvent.VK_T;
                    p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,when-10,0,key,KeyEvent.CHAR_UNDEFINED));
                    tap(1,0,first,when);p.handleAnnotationToolKeyReleased(new KeyEvent(p,KeyEvent.KEY_RELEASED,when+100,0,key,KeyEvent.CHAR_UNDEFINED));
                    check(read("pendingAnnotationToolRestore")==preceding,"expired-input fixture needs a pending tool restore");
                    secondPress(1,0,second,when+201);
                    check(p.getAnnotationTool()==preceding&&read("pendingAnnotationToolRestore")==null&&read("mappedPress")==null,
                            "W+1 press must restore M/S and leave mapped arbitration");
                    if(preceding==AnnotationTool.DEFAULT_MOUSE) {
                        check(p.lastPressedRow==1&&p.lastPressedCol==3&&p.lastPressedCandidate==5,
                                "restored M must receive native onMouseDown for the same press");
                        input(MouseEvent.MOUSE_RELEASED,1,0,second,when+202);
                        check(cells().containsKey(0)&&!cells().containsKey(12),"restored M click must keep the first T mark without adding another");
                    } else {
                        check(second.equals(read("boxDragStart")),"restored S must start its native drag from the same press");
                        Point end=candidate(24,5);end.translate(2,2);dragTo(1,0,end,when+210);input(MouseEvent.MOUSE_RELEASED,1,0,end,when+211);
                        check(p.getBoxReasoningGroupsSnapshot().get(0).contains(12)&&p.getBoxReasoningGroupsSnapshot().get(0).contains(24)
                                &&ink().size()==1&&ink().get(0).getAnchorCell()==0,"restored S drag must commit S cells and retain only the first P mark");
                    }
                }
            });
            scenario("Escape while borrowed P second press is held restores M and suppresses only its release",()->{
                reset(AnnotationTool.DEFAULT_MOUSE);Options options=Options.getInstance();boolean single=options.isSingleClickMode();
                try {
                    options.setSingleClickMode(true);String board=TechniqueStepCatalog.createSignature(p.getSudoku());
                    long when=System.currentTimeMillis();Point first=candidate(0,1),second=candidate(12,1);
                    p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,when-10,0,KeyEvent.VK_P,KeyEvent.CHAR_UNDEFINED));
                    tap(1,0,first,when);p.handleAnnotationToolKeyReleased(new KeyEvent(p,KeyEvent.KEY_RELEASED,when+100,0,KeyEvent.VK_P,KeyEvent.CHAR_UNDEFINED));
                    secondPress(1,0,second,when+150);
                    check(p.getAnnotationTool()==AnnotationTool.DOODLE&&read("pendingAnnotationToolRestore")==AnnotationTool.DEFAULT_MOUSE
                            &&read("mappedPress")!=null,"Escape fixture must hold a borrowed P second press");
                    check(p.handleEscapeVisualReset(),"held pointer Escape must be handled");
                    check(p.getAnnotationTool()==AnnotationTool.DEFAULT_MOUSE&&(Boolean)read("suppressNextAnnotationPointerRelease"),
                            "Escape must restore M with a one-shot release guard");
                    input(MouseEvent.MOUSE_RELEASED,1,0,second,when+160);
                    check(!(Boolean)read("suppressNextAnnotationPointerRelease")&&board.equals(TechniqueStepCatalog.createSignature(p.getSudoku()))
                            &&p.getSudoku().getValue(12)==0&&p.getSudoku().isCandidate(12,1),"cancelled P release must not become an M value/candidate edit, and must clear its guard");
                } finally {options.setSingleClickMode(single);}
            });
            scenario("Option held preview retains clicks without editing chain",()->{
                reset(AnnotationTool.FREE_CHAIN);install(chain(false));String before=chainState();long when=System.currentTimeMillis();
                p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,when,InputEvent.ALT_DOWN_MASK,KeyEvent.VK_ALT,KeyEvent.CHAR_UNDEFINED));
                check((Boolean)read("chainRelationPreviewHeld"),"Option preview not held");tap(3,InputEvent.ALT_DOWN_MASK,candidate(40,1),when+10);
                check(before.equals(chainState()),"held preview right click edited chain");
                p.handleAnnotationToolKeyReleased(new KeyEvent(p,KeyEvent.KEY_RELEASED,when+20,0,KeyEvent.VK_ALT,KeyEvent.CHAR_UNDEFINED));
                check(!(Boolean)read("chainRelationPreviewHeld"),"Option release kept preview");
            });
            scenario("overlapping-group first click defers chooser, precision replaces it",()->{
                reset(AnnotationTool.FREE_CHAIN);install(chain(false));
                UserChain a=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0,1),GroupedChainProbe.node(1,50)},true);
                UserChain b=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(1,0,9),GroupedChainProbe.node(1,60)},false);a.setSourceId(701);b.setSourceId(702);done().add(a);done().add(b);
                String before=chainState();long when=System.currentTimeMillis();Point at=candidate(0,1);tap(1,0,at,when);
                check(before.equals(chainState())&&read("mappedSingleRollback")==null&&MenuSelectionManager.defaultManager().getSelectedPath().length==0,
                        "overlapping groups must not open chooser or mutate on provisional first click");
                tap(1,0,at,when+150);check(precise()==1&&before.equals(chainState()),"double failed to replace deferred group chooser with precision");
            });
            scenario("real in-flight analysis defers first click until final global edit",()->{
                reset(AnnotationTool.FREE_CHAIN);p.getSudoku().set(ChainAdvisoryProbe.board(false));install(ChainAdvisoryProbe.ring());render();
                call("handleReasoningEnter");Object request=read("reasoningRequest");check(request!=null,"real analysis fixture must be in flight");
                String before=chainState();List<Integer> targets=new ArrayList<Integer>();
                check(active()==null&&done().size()==1,"analysis fixture must have completed one chain");
                long source=done().get(0).getSourceId();int nodes=done().get(0).getNodes().size();
                List<Boolean> expected=new ArrayList<Boolean>(done().get(0).getStrongRelations());
                expected.set(expected.size()-1,!expected.get(expected.size()-1));int undo=history("userChainUndoStack");
                for(int cell=0;cell<81&&targets.size()<2;cell++)for(int digit=1;digit<=9&&targets.size()<2;digit++) {
                    if(!p.getSudoku().isCandidate(cell,digit))continue;boolean member=false;
                    for(UserChain chain:done())for(UserChainNode node:chain.getNodes())member|=node.contains(cell,digit);
                    if(!member)targets.add(cell*10+digit);
                }
                check(targets.size()==2,"real analysis fixture needs external candidate pair");long when=System.currentTimeMillis();
                int first=targets.get(0),second=targets.get(1);tap(1,0,candidate(first/10,first%10),when);
                check(read("reasoningRequest")==request&&before.equals(chainState())&&read("mappedSingleRollback")==null,
                        "provisional single invalidated in-flight result or altered chain");
                tap(1,0,candidate(second/10,second%10),when+150);p.flushMappedClick();
                check(precise()<0&&!before.equals(chainState())&&read("reasoningRequest")==null,
                        "final global edit must change source and cancel only then");
                check(active()==null&&done().size()==1&&done().get(0).getSourceId()==source&&done().get(0).getNodes().size()==nodes
                        &&done().get(0).getStrongRelations().equals(expected)&&history("userChainUndoStack")==undo+1,
                        "in-flight double must replace deferred single with only the global last-edge flip");
            });
            scenario("S immediate idempotent clicks bypass double arbitration",()->{
                reset(AnnotationTool.BOX_SELECTION);long when=System.currentTimeMillis();Point at=candidate(0,1);tap(1,0,at,when);tap(1,0,at,when+150);
                check(p.getBoxReasoningGroupsSnapshot().get(0).contains(0)&&read("pendingMappedClick")==null,"S add gained delay/toggle");
                tap(3,0,at,when+180);check(!p.getBoxReasoningGroupsSnapshot().get(0).contains(0),"S immediate removal");
            });
            System.out.println("RESULT: "+passed+" passed, "+failures.size()+" failed; explicit input press timestamps, default W=200ms, shorter system interval checked.");
            if(!failures.isEmpty())throw new AssertionError(failures.size()+" gesture contracts failed: "+failures);
            success=true;
        } catch(Throwable failure) {failure.printStackTrace();}
        finally {if(f!=null)edt(()->{p.cancelAnnotationToolInteractionOnDeactivation();f.dispose();return null;});}
        System.exit(success?0:1);
    }
}
