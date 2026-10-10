/*
 * Copyright (C) 2026 HoDoKu contributors
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import javax.swing.JDialog;
import javax.swing.JTextField;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
import static sudoku.DoodleHypothesisCompositionProbe.*;

/** Real dispatcher and pointer transactions, with optional macOS Robot input. */
public final class PaletteHeldDeletionProbe {
    private static KeyEventDispatcher dispatcher;
    private static long time=System.currentTimeMillis();
    private static Color[] colors;
    private static final Point START=new Point(1,1), END=new Point(809,809);
    private static long mouseTime=System.currentTimeMillis()+5000;
    private static void event(int id,int button,int mods,Point pt) {
        p.dispatchEvent(new MouseEvent(p,id,++mouseTime,mods,pt.x,pt.y,1,false,button));
    }
    private static void click(int button,int mods,Point point) {
        event(MouseEvent.MOUSE_PRESSED,button,mods,point);event(MouseEvent.MOUSE_RELEASED,button,mods,point);
    }
    private static void drag(int button,int mods,Point start,Point end) {
        event(MouseEvent.MOUSE_PRESSED,button,mods,start);
        event(MouseEvent.MOUSE_DRAGGED,0,mods|(button==3?InputEvent.BUTTON3_DOWN_MASK:InputEvent.BUTTON1_DOWN_MASK),end);
        event(MouseEvent.MOUSE_RELEASED,button,mods,end);
    }
    private static void mappedTap(int button,int mods,Point point) {click(button,mods,point);p.flushMappedClick();}
    private static void mappedDouble(int button,int mods,Point point) {click(button,mods,point);click(button,mods,point);}
    private static boolean key(Component source,int id,int code,int mods) {
        return dispatcher.dispatchKeyEvent(new KeyEvent(source,id,++time,mods,code,(char)code));
    }
    private static void down(int group) { check(key(p,KeyEvent.KEY_PRESSED,KeyEvent.VK_A+group,0),"palette press not handled"); }
    private static void up(int group) { check(key(p,KeyEvent.KEY_RELEASED,KeyEvent.VK_A+group,0),"palette release not handled"); }
    @SuppressWarnings("unchecked")
    private static Map<Integer,Color> cells()throws Exception{return (Map<Integer,Color>)read("coloringMap");}
    @SuppressWarnings("unchecked")
    private static Map<Integer,Color> candidates()throws Exception{return (Map<Integer,Color>)read("coloringCandidateMap");}
    @SuppressWarnings("unchecked")
    private static List<SudokuSet> boxes()throws Exception{return (List<SudokuSet>)read("boxReasoningGroups");}
    private static void clean(AnnotationTool tool)throws Exception {
        p.cancelAnnotationToolInteractionOnDeactivation();p.setAnnotationTool(tool);
        p.clearAllAnnotationsWithUndo();p.getCellZoomPanel().selectPaletteGroup(0);
    }
    private static void colorFixture()throws Exception {
        cells().put(0,colors[0]);cells().put(1,colors[1]);cells().put(2,colors[2]);cells().put(3,colors[3]);
        candidates().put(405,colors[0]);candidates().put(415,colors[1]);candidates().put(425,colors[2]);
    }
    private static DoodleStroke line(int group,int shade) {
        DoodleStroke s=new DoodleStroke(colors[group*2+shade],0.003f);s.setThoughtGroup(group);
        s.getPoints().add(new DoodlePoint(.2,.4));s.getPoints().add(new DoodlePoint(.6,.4));return s;
    }
    private static UserChain chain(int group,int... atoms) {
        UserChain c=new UserChain();c.setSourceId(100+group);
        for(int i=0;i<atoms.length;i++)c.getNodes().add(new UserChainNode(atoms[i]/10,atoms[i]%10,colors[group*2+i%2]));
        for(int i=1;i<atoms.length;i++){c.getStrongRelations().add(i%2==1);c.getRelationColors().add(colors[group*2+(i%2==1?0:1)]);}
        return c;
    }
    private static List<UserChain> allChains()throws Exception {
        List<UserChain> result=new ArrayList<UserChain>(done());if(active()!=null)result.add(active());return result;
    }
    private static int edges(Color color)throws Exception {
        int count=0;for(UserChain c:allChains())for(Color stored:c.getRelationColors())if(color.equals(stored))count++;return count;
    }
    private static boolean hasNode(int atom,Color color)throws Exception {
        for(UserChain c:allChains())for(UserChainNode n:c.getNodes())if(n.contains(atom/10,atom%10)&&color.equals(n.getColor()))return true;
        return false;
    }
    private static void rectangle(int button,int modifiers,boolean releaseKey)throws Exception {
        event(MouseEvent.MOUSE_PRESSED,button,modifiers,START);
        event(MouseEvent.MOUSE_DRAGGED,0,modifiers|(button==3?InputEvent.BUTTON3_DOWN_MASK:InputEvent.BUTTON1_DOWN_MASK),END);
        if(releaseKey)up(0);
        event(MouseEvent.MOUSE_RELEASED,button,modifiers,END);p.flushMappedClick();
    }
    private static void doubleRectangle() {
        click(3,0,START);
        event(MouseEvent.MOUSE_PRESSED,3,0,START);
        event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,END);
        event(MouseEvent.MOUSE_RELEASED,3,0,END);p.flushMappedClick();
    }
    private static void deterministic()throws Exception {
        String board=TechniqueStepCatalog.createSignature(p.getSudoku());
        for(AnnotationTool tool:new AnnotationTool[]{AnnotationTool.CANDIDATE_COLORING,AnnotationTool.DOODLE,
                AnnotationTool.FREE_CHAIN,AnnotationTool.BOX_SELECTION}) {
            clean(tool);
            for(int group=0;group<4;group++) {
                down(group);check(p.getCellZoomPanel().getPaletteGroup()==group,"tap selection must be immediate: "+tool);
                up(group);check(p.getCellZoomPanel().getPaletteGroup()==group,"release reverted selection: "+tool);
            }
        }
        clean(AnnotationTool.CANDIDATE_COLORING);colorFixture();down(0);
        int history=((Stack<?>)read("coloringUndoStack")).size();
        doubleRectangle();
        check(cells().keySet().equals(new java.util.HashSet<Integer>(Arrays.asList(2,3))),"T double rectangle removed other colors or missed secondary");
        check(candidates().keySet().equals(java.util.Collections.singleton(425)),"T candidate colors not filtered");
        check(((Stack<?>)read("coloringUndoStack")).size()==history+1,"T rectangle undo not atomic");
        p.undoCurrentAnnotation();check(cells().size()==4&&candidates().size()==3,"T undo did not restore both maps");
        rectangle(1,InputEvent.CTRL_DOWN_MASK,true);
        check(cells().size()==2&&candidates().size()==1,"early A release broadened T Ctrl rectangle");
        rectangle(1,InputEvent.CTRL_DOWN_MASK,false);check(cells().isEmpty()&&candidates().isEmpty(),"released hold persisted");
        colorFixture();down(1);click(3,0,ChainOriginProbe.candidate(0,5));up(1);p.flushMappedClick();
        check(cells().containsKey(0),"deferred wrong-group right click removed cell after key release");
        down(0);click(3,0,START);up(0);p.flushMappedClick();
        check(cells().size()+candidates().size()==6,"recent deletion failed to skip other colors");

        clean(AnnotationTool.DOODLE);ink().add(line(0,0));ink().add(line(0,1));ink().add(line(1,0));
        done().add(chain(0,1,11));down(0);
        event(MouseEvent.MOUSE_PRESSED,3,0,new Point(162,324));
        event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,new Point(486,324));
        @SuppressWarnings("unchecked") List<DoodleStroke> ghosts=(List<DoodleStroke>)read("doodleEraseGhosts");
        check(!ghosts.isEmpty()&&ghosts.stream().allMatch(s->s.getThoughtGroup()==0),"P sweep preview ghost includes another group");
        up(0);event(MouseEvent.MOUSE_RELEASED,3,0,new Point(486,324));
        check(ink().size()==1&&ink().get(0).getThoughtGroup()==1,"P sweep not group scoped");
        check(done().size()==1&&done().get(0).getStrongRelations().size()==1,"P deleted L same group");
        p.undoCurrentAnnotation();check(ink().size()==3,"P scoped undo");down(0);doubleRectangle();up(0);
        check(ink().size()==1&&ink().get(0).getThoughtGroup()==1,"P double rectangle not filtered");
        p.undoCurrentAnnotation();down(0);rectangle(1,InputEvent.CTRL_DOWN_MASK,true);
        check(ink().size()==1&&done().size()==1,"P Ctrl rectangle or layer isolation");
        p.undoCurrentAnnotation();down(0);
        drag(3,0,new Point(320,310),new Point(320,340));
        check(ink().stream().filter(s->s.getThoughtGroup()==0).count()==4,"P partial fragments lost group identity");up(0);
        clean(AnnotationTool.DOODLE);mark(40,5,1,0);mark(40,5,1,1);
        down(0);doubleRectangle();up(0);
        check(find(40,5,1,0)==null&&find(40,5,1,1)!=null&&mask(40,5,1)==2,"P multi-source erasure removed peer source");

        clean(AnnotationTool.FREE_CHAIN);done().add(chain(0,1,11,21));done().add(chain(1,1,11,21));
        ink().add(line(0,0));down(0);rectangle(3,0,true);
        check(edges(colors[0])==0&&edges(colors[1])==0&&edges(colors[2])==1&&edges(colors[3])==1,"L rectangle did not filter strong/weak edges");
        check(ink().size()==1,"L deleted P same group");p.undoCurrentAnnotation();
        down(0);rectangle(1,InputEvent.CTRL_DOWN_MASK,true);check(edges(colors[2])==1&&edges(colors[3])==1,"L Ctrl rectangle deleted B");
        clean(AnnotationTool.FREE_CHAIN);done().add(chain(0,1,11));done().add(chain(1,1,11));
        down(0);mappedDouble(3,0,ChainOriginProbe.candidate(0,1));mappedTap(3,0,ChainOriginProbe.candidate(1,1));up(0);
        check(edges(colors[0])==0&&edges(colors[2])==1,"L precise overlapping B edge was removed or blocked A");
        clean(AnnotationTool.FREE_CHAIN);UserChain mixed=chain(1,1,11,21);
        mixed.getRelationColors().set(0,colors[0]);done().add(mixed);down(0);rectangle(3,0,true);
        check(edges(colors[0])==0&&edges(colors[3])==1&&hasNode(1,colors[2]),"mixed L cut removed protected orphan or edge");
        clean(AnnotationTool.FREE_CHAIN);done().add(chain(0,1,11));done().add(chain(1,21,31));
        down(0);mappedTap(3,0,START);up(0);check(edges(colors[0])==0&&edges(colors[2])==1,"L right backtrack did not skip other group");
        check(active()!=null&&active().getNodes().size()==1&&active().getNodes().get(0).contains(0,1),"scoped L backtrack lost start/drawing cursor");
        p.undoCurrentAnnotation();check(active()==null&&edges(colors[0])==1&&edges(colors[2])==1,"scoped L backtrack undo did not restore completed source");

        clean(AnnotationTool.BOX_SELECTION);boxes().get(0).add(40);boxes().get(1).add(40);down(0);
        rectangle(3,0,true);check(boxes().get(0).isEmpty()&&boxes().get(1).contains(40),"S right rectangle removed other group");
        p.undoCurrentAnnotation();down(0);rectangle(1,InputEvent.CTRL_DOWN_MASK,true);
        check(boxes().get(0).isEmpty()&&boxes().get(1).contains(40),"S Ctrl rectangle removed other group");

        for(AnnotationTool tool:new AnnotationTool[]{AnnotationTool.CANDIDATE_COLORING,AnnotationTool.DOODLE,
                AnnotationTool.FREE_CHAIN,AnnotationTool.BOX_SELECTION})for(int modifier:new int[]{0,InputEvent.SHIFT_DOWN_MASK}) {
            clean(tool);colorFixture();ink().add(line(0,0));ink().add(line(1,0));
            done().add(chain(0,1,11));done().add(chain(1,21,31));boxes().get(0).add(40);boxes().get(1).add(40);
            down(0);key(p,KeyEvent.KEY_PRESSED,KeyEvent.VK_R,modifier);up(0);
            check(!cells().containsKey(0)==(tool==AnnotationTool.CANDIDATE_COLORING),"held R wrong T layer");
            check(ink().stream().noneMatch(s->s.getThoughtGroup()==0)==(tool==AnnotationTool.DOODLE),"held R wrong P layer");
            check((edges(colors[0])==0)==(tool==AnnotationTool.FREE_CHAIN),"held R wrong L layer");
            check(boxes().get(0).isEmpty()==(tool==AnnotationTool.BOX_SELECTION),"held R wrong S layer");
            check(cells().containsKey(2)&&ink().stream().anyMatch(s->s.getThoughtGroup()==1)
                    &&edges(colors[2])==1&&boxes().get(1).contains(40),"held R removed other group");
            p.undoCurrentAnnotation();check(cells().containsKey(0)&&ink().stream().anyMatch(s->s.getThoughtGroup()==0)
                    &&edges(colors[0])==1&&boxes().get(0).contains(40),"held R undo failed");
        }
        clean(AnnotationTool.DOODLE);down(0);click(3,0,START);
        Object pending=read("pendingMappedClick");check(pending!=null,"repeat fixture pending click missing");
        key(p,KeyEvent.KEY_PRESSED,KeyEvent.VK_A,0);
        check(read("pendingMappedClick")==pending,"repeat flushed double click transaction");
        p.setAnnotationTool(AnnotationTool.BOX_SELECTION);p.getCellZoomPanel().selectPaletteGroup(1);
        key(p,KeyEvent.KEY_PRESSED,KeyEvent.VK_A,0);check(p.getCellZoomPanel().getPaletteGroup()==1,"repeat changed new tool palette");up(0);
        boxes().get(0).add(40);boxes().get(1).add(40);rectangle(3,0,false);check(boxes().get(1).isEmpty(),"tool switch retained old scope");
        JTextField text=new JTextField();f.getLayeredPane().add(text);
        clean(AnnotationTool.BOX_SELECTION);down(0);
        check(key(text,KeyEvent.KEY_RELEASED,KeyEvent.VK_A,InputEvent.CTRL_DOWN_MASK),"field release bypassed reset");
        check(!key(text,KeyEvent.KEY_PRESSED,KeyEvent.VK_B,0),"text B intercepted");
        down(0);down(1);up(0);boxes().get(0).add(40);boxes().get(1).add(40);
        rectangle(3,0,false);check(boxes().get(0).contains(40)&&boxes().get(1).isEmpty(),"older key release canceled newer held B");up(1);
        JDialog dialog=new JDialog(f);down(0);
        check(key(dialog,KeyEvent.KEY_RELEASED,KeyEvent.VK_A,0),"dialog release bypassed reset");dialog.dispose();
        down(0);p.cancelAnnotationToolInteractionOnDeactivation();
        boxes().get(0).add(40);boxes().get(1).add(40);rectangle(3,0,false);check(boxes().get(1).isEmpty(),"focus loss retained scope");
        check(!key(p,KeyEvent.KEY_PRESSED,KeyEvent.VK_A,InputEvent.CTRL_DOWN_MASK),"Ctrl+A intercepted");
        p.setReplayReadOnly(true);key(p,KeyEvent.KEY_PRESSED,KeyEvent.VK_B,0);p.setReplayReadOnly(false);
        check(p.getCellZoomPanel().getPaletteGroup()==0,"read-only changed palette");
        check(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"annotation erasure changed Sudoku");
        System.out.println("PASS: immediate A-D taps, repeat suppression, all TPLS erasers, secondary colors, release during drag/deferred click, atomic undo, overlapping sources/edges, mixed chain protected orphan, scoped R/Shift-R, layer isolation, tool/focus/field/dialog/modifier/read-only guards");
    }
    private static void nativeInput()throws Exception {
        edt(()->{clean(AnnotationTool.BOX_SELECTION);boxes().get(0).add(40);boxes().get(1).add(40);
            f.setSize(1100,950);f.setLocation(70,70);f.setVisible(true);f.toFront();p.requestFocusInWindow();return null;});
        Process activate=new ProcessBuilder("osascript","-e","tell application \"System Events\" to set frontmost of first process whose unix id is "
                +java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]+" to true").start();
        check(activate.waitFor()==0,"native activation failed");
        Robot robot=new Robot();robot.setAutoDelay(40);robot.delay(500);
        edt(()->{p.requestFocusInWindow();return null;});
        try {
            robot.keyPress(KeyEvent.VK_A);robot.waitForIdle();
            edt(()->{check(p.getCellZoomPanel().getPaletteGroup()==0,"native A press did not select immediately");return null;});
            robot.delay(650);robot.waitForIdle();
            Point center=edt(()->new Point(p.getX(4,4)+(Integer)read("cellSize")/2,p.getY(4,4)+(Integer)read("cellSize")/2));
            Point origin=edt(()->p.getLocationOnScreen());robot.mouseMove(origin.x+center.x,origin.y+center.y);
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.keyRelease(KeyEvent.VK_A);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);robot.waitForIdle();
            edt(()->{check(boxes().get(0).isEmpty()&&boxes().get(1).contains(40),"native held A early release deleted B");return null;});
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);robot.waitForIdle();
            edt(()->{check(boxes().get(1).isEmpty(),"native release left scope latched");return null;});
            robot.keyPress(KeyEvent.VK_D);robot.keyRelease(KeyEvent.VK_D);robot.waitForIdle();
            edt(()->{check(p.getCellZoomPanel().getPaletteGroup()==3,"native D tap failed");return null;});
            System.out.println("PASS native: A key down + right erasure + key release before mouse release, subsequent unfiltered erase, immediate D tap");
        } finally {robot.keyRelease(KeyEvent.VK_A);robot.keyRelease(KeyEvent.VK_D);robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);}
    }
    public static void main(String[] args)throws Exception {
        try {
            edt(()->{initialize();colors=Options.getInstance().getColoringColors();
                Field field=MainFrame.class.getDeclaredField("annotationKeyDispatcher");field.setAccessible(true);
                dispatcher=(KeyEventDispatcher)field.get(f);deterministic();return null;});
            if(args.length==0||!"--listeners-only".equals(args[0]))nativeInput();
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
