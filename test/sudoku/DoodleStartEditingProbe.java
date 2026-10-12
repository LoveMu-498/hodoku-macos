/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleContradictionWorkflowProbe.*;

/** Actual dispatcher/listeners, Enter, session and replay; no desktop input injection. */
public final class DoodleStartEditingProbe {
    private static int checks;
    private static void verify(boolean value,String message){checks++;check(value,message);}
    private static int depth()throws Exception{return ((Stack<?>)read("doodleUndoStack")).size();}
    private static void reset()throws Exception{p.finishDoodleHypothesisEntry();initialize(0);}
    private static void edit(int cell,int digit,int kind)throws Exception{
        ChainEditingProbe.click(1,kind==2?InputEvent.SHIFT_DOWN_MASK:0,ChainOriginProbe.candidate(cell,digit));p.flushMappedClick();
    }
    private static Field playback()throws Exception{Field field=OperationSoundPlayer.class.getDeclaredField("lastPlayMillis");field.setAccessible(true);return field;}
    private static void silence()throws Exception{playback().setLong(null,0L);}
    private static long played()throws Exception{return playback().getLong(null);}
    private static void keyTap(int code,int modifiers,long when)throws Exception{
        Field field=MainFrame.class.getDeclaredField("annotationKeyDispatcher");field.setAccessible(true);
        KeyEventDispatcher dispatcher=(KeyEventDispatcher)field.get(f);
        dispatcher.dispatchKeyEvent(new KeyEvent(p,KeyEvent.KEY_PRESSED,when,modifiers,code,(char)code));
        dispatcher.dispatchKeyEvent(new KeyEvent(p,KeyEvent.KEY_RELEASED,when+20,modifiers,code,(char)code));
    }
    private static void rejectedResult(ReplayFrame before,ReplayFrame after,DoodleStroke extra)throws Exception{
        java.util.List<DoodleStroke> bad=after.annotations.ink();bad.add(extra);
        ReplayAnnotations annotations=new ReplayAnnotations(after.annotations.overlay,after.annotations.geometry(),
                after.annotations.cells(),after.annotations.candidates(),bad,after.annotations.outlineVisibleGroup);
        Method validator=ReplayFiles.class.getDeclaredMethod("validateRetainedDoodleStarts",ReplayFrame.class,ReplayFrame.class);validator.setAccessible(true);
        try{validator.invoke(null,before,after.withAnnotations(annotations));throw new AssertionError("invalid retained ink accepted");}
        catch(InvocationTargetException expected){verify(expected.getCause() instanceof java.io.IOException,"wrong retained-ink rejection");}
    }
    private static void roles()throws Exception{
        for(int kind:new int[]{1,2}){
            reset();mark(31,5,kind,0);p.cancelDoodleHypothesisStarts();
            DoodleStroke original=find(31,5,kind,0);original.setColor(new Color(17,101,139));
            command(ChainOriginProbe.candidate(31,5));verify(original.isConclusionOutlined(),"outline fixture");
            int points=original.getPoints().size();double width=original.getWidthFactor();int before=depth();
            p.toggleDoodleHypothesisEntry();edit(31,5,kind);
            DoodleStroke promoted=find(31,5,kind,0);
            verify(promoted!=null&&promoted.isHypothesisStart()&&promoted.isConclusionOutlined()
                    &&promoted.getColor().equals(new Color(17,101,139))&&promoted.getPoints().size()==points
                    &&promoted.getWidthFactor()==width,"same-type promotion changed ink, source, geometry or blue");
            verify(depth()==before+1,"role promotion undo count");edit(31,5,kind);
            verify(find(31,5,kind,0)!=null&&!find(31,5,kind,0).isHypothesisStart(),"role cancellation erased ink");
            p.undoCurrentAnnotation();verify(find(31,5,kind,0).isHypothesisStart(),"role undo");
            p.redoCurrentAnnotation();verify(!find(31,5,kind,0).isHypothesisStart(),"role redo");
            p.finishDoodleHypothesisEntry();command(ChainOriginProbe.candidate(31,5));edit(31,5,kind);
            verify(find(31,5,kind,0)==null,"ordinary toggle no longer erases ink");
        }
        for(int oldKind:new int[]{1,2}){
            reset();mark(31,5,oldKind,0);p.cancelDoodleHypothesisStarts();
            p.getCellZoomPanel().selectPaletteGroup(1);mark(31,5,oldKind,1);p.getCellZoomPanel().selectPaletteGroup(0);
            command(ChainOriginProbe.candidate(31,5));int before=depth();p.toggleDoodleHypothesisEntry();edit(31,5,3-oldKind);
            verify(find(31,5,oldKind,0)==null&&find(31,5,3-oldKind,0).isHypothesisStart()
                    &&!find(31,5,3-oldKind,0).isConclusionOutlined()&&find(31,5,oldKind,1)!=null,"direction replacement/other color");
            verify(depth()==before+1,"replacement undo count");p.undoCurrentAnnotation();
            verify(find(31,5,oldKind,0).isConclusionOutlined()&&find(31,5,3-oldKind,0)==null,"replacement undo lost selected original");
            p.redoCurrentAnnotation();verify(find(31,5,3-oldKind,0).isHypothesisStart(),"replacement redo");
        }
        reset();mark(31,5,1,0);p.toggleDoodleHypothesisEntry();edit(31,5,2);
        verify(find(31,5,1,0)==null&&find(31,5,2,0).isHypothesisStart(),"sole opposite start not replaceable");
        reset();mark(31,5,1,0);p.toggleDoodleHypothesisEntry();mark(70,9,1,0);int before=depth();edit(31,5,2);
        verify(find(31,5,1,0).isHypothesisStart()&&find(70,9,1,0).isHypothesisStart()
                &&find(31,5,2,0)==null&&depth()==before,"incompatible replacement mutated group");
        reset();mark(31,5,1,0);p.cancelDoodleHypothesisStarts();mark(31,5,2,0);p.toggleDoodleHypothesisEntry();edit(31,5,2);
        verify(find(31,5,2,0).isHypothesisStart()&&find(31,5,1,0)!=null,"coexisting types changed ordinary opposite");
        p.getCellZoomPanel().selectPaletteGroup(1);p.getCellZoomPanel().selectPaletteGroup(0);
        verify(find(31,5,2,0).isHypothesisStart()&&!p.isDoodleHypothesisEntryActive(),"switch lost authored role");
    }
    private static void feedback()throws Exception{
        Options.getInstance().setOperationSoundsEnabled(true);
        reset();mark(31,5,2,0);p.cancelDoodleHypothesisStarts();p.toggleDoodleHypothesisEntry();silence();
        Point point=ChainOriginProbe.candidate(31,5);ChainEditingProbe.click(1,0,point);
        verify(played()==0&&read("pendingMappedDoodleFeedback")!=null,"provisional replacement sounded immediately");
        ChainEditingProbe.click(1,0,point);p.flushMappedClick();
        verify(played()==0&&find(31,5,2,0).isHypothesisStart()&&find(31,5,1,0)==null,"double kept provisional sound/ink");
        reset();mark(31,5,1,0);p.cancelDoodleHypothesisStarts();p.toggleDoodleHypothesisEntry();silence();
        ChainEditingProbe.mappedDouble(1,0,ChainOriginProbe.candidate(31,5));p.flushMappedClick();
        verify(played()>0&&find(31,5,2,0).isHypothesisStart(),"final double replacement missing feedback");
        reset();mark(31,5,2,0);p.toggleDoodleHypothesisEntry();silence();
        ChainEditingProbe.mappedDouble(1,0,ChainOriginProbe.candidate(59,7));p.flushMappedClick();
        verify(played()==0&&read("pendingMappedDoodleFeedback")==null&&find(59,7,2,0).isHypothesisStart(),"double rejection feedback was not discarded");
        reset();mark(31,5,2,0);p.cancelDoodleHypothesisStarts();p.toggleDoodleHypothesisEntry();silence();
        point=ChainOriginProbe.candidate(31,5);ChainEditingProbe.click(1,0,point);p.handleEscapeVisualReset();
        verify(played()>0&&find(31,5,1,0).isHypothesisStart()&&!p.isDoodleHypothesisEntryActive(),"Escape entry exit did not finalize single");
        reset();mark(31,5,2,0);p.cancelDoodleHypothesisStarts();p.toggleDoodleHypothesisEntry();silence();
        point=ChainOriginProbe.candidate(31,5);ChainEditingProbe.click(1,0,point);call("cancelMappedPointer");
        verify(played()==0&&find(31,5,2,0)!=null,"pointer rollback did not discard provisional replacement");
        reset();mark(31,5,2,0);p.cancelDoodleHypothesisStarts();p.toggleDoodleHypothesisEntry();silence();
        ChainEditingProbe.click(1,0,ChainOriginProbe.candidate(31,5));p.getCellZoomPanel().selectPaletteGroup(1);
        verify(played()>0&&find(31,5,1,0).isHypothesisStart(),"switch did not finalize committed single feedback");
        reset();mark(31,5,1,0);silence();p.cancelDoodleHypothesisStarts();verify(played()>0,"cancel starts sound");
        silence();int before=depth();p.cancelDoodleHypothesisStarts();verify(played()==0&&depth()==before,"empty cancel sound/undo");
        mark(59,7,2,0);silence();p.clearCurrentAnnotationWithUndo();verify(played()>0,"current-tool clear sound");
        silence();p.clearAllAnnotationsWithUndo();verify(played()==0,"empty all clear sound");
        mark(31,5,1,0);silence();p.clearAllAnnotationsWithUndo();verify(played()>0,"all clear sound");
        reset();mark(31,5,1,0);silence();before=depth();long when=System.currentTimeMillis();
        keyTap(KeyEvent.VK_P,0,when);keyTap(KeyEvent.VK_P,0,when+100);
        verify(played()>0&&ink().isEmpty()&&depth()==before+1,"double mode key clear feedback/undo");
        p.setAnnotationTool(AnnotationTool.DOODLE);mark(31,5,1,0);silence();keyTap(KeyEvent.VK_R,InputEvent.SHIFT_DOWN_MASK,when+500);
        verify(played()>0&&ink().isEmpty(),"Shift+R clear feedback");
        mark(31,5,1,0);silence();keyTap(KeyEvent.VK_R,0,when+1000);
        verify(played()>0&&ink().isEmpty(),"R clear feedback");
        mark(31,5,1,0);silence();keyTap(KeyEvent.VK_G,0,when+1500);keyTap(KeyEvent.VK_G,0,when+1600);
        verify(played()>0&&find(31,5,1,0)!=null&&!find(31,5,1,0).isHypothesisStart(),"double G clear feedback/ink");
        Options.getInstance().setOperationSoundsEnabled(false);reset();mark(31,5,1,0);silence();p.cancelDoodleHypothesisStarts();
        p.toggleDoodleHypothesisEntry();edit(31,5,2);p.clearCurrentAnnotationWithUndo();
        verify(played()==0,"sound ignored global disabled switch");
    }
    private static void lifetime()throws Exception{
        reset();mark(31,5,1,0);mark(59,7,2,0);command(ChainOriginProbe.candidate(59,7));
        p.getCellZoomPanel().selectPaletteGroup(1);mark(31,5,2,1);p.getCellZoomPanel().selectPaletteGroup(0);
        f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));enter();
        verify(!p.getSudoku().isCandidate(59,7)&&find(31,5,1,0).isHypothesisStart()
                &&ink().stream().filter(s->s.getThoughtGroup()==0).count()==1&&find(31,5,2,1).isHypothesisStart(),"manual Enter origin lifetime");
        ReplaySession replay=savedReplay();verify(replay.last().annotations.forEditing().ink().stream()
                .anyMatch(s->s.getThoughtGroup()==0&&s.isHypothesisStart()&&s.getAnchorCell()==31),"result replay lost valid root");
        ReplayFrame input=replay.frames().get(replay.frames().size()-2),result=replay.last();
        DoodleStroke bad=find(31,5,1,0).copy();bad.setConclusionOutlined(true);rejectedResult(input,result,bad);
        bad=find(31,5,1,0).copy();bad.setHypothesisStart(false);rejectedResult(input,result,bad);
        bad=find(31,5,1,0).copy();bad.setAnchorCell(32);bad.setAnchorDigit(1);rejectedResult(input,result,bad);
        bad=find(31,5,1,0).copy();bad.setColor(Color.BLACK);rejectedResult(input,result,bad);
        GuiState state=new GuiState();state.setIncludeAnnotations(true);p.getState(state,true);
        SessionSnapshot snapshot=new SessionSnapshot();snapshot.setGuiState(state);SessionStore store=new SessionStore(output.resolve("retained-start.xml").toFile());
        store.save(snapshot);p.setState(store.load().getGuiState());p.setAnnotationTool(AnnotationTool.DOODLE);
        verify(find(31,5,1,0).isHypothesisStart()&&find(31,5,2,1).isHypothesisStart(),"session lost complementary roots");
        mark(32,1,2,0);verify(!find(32,1,2,0).isHypothesisStart(),"retained root reset consumed status");
        reset();mark(31,5,1,0);p.toggleDoodleHypothesisEntry();mark(70,9,1,0);p.finishDoodleHypothesisEntry();
        command(ChainOriginProbe.candidate(31,5));enter();verify(p.getSudoku().getValue(31)==0,"individual OR member was applied");
        // OR members are not individually applicable. Exercise the post-mutation
        // cleanup boundary with a genuinely filled member, without arming that member.
        p.getSudoku().setCell(31,5);
        Method cleanup=SudokuPanel.class.getDeclaredMethod("clearCurrentDoodleThoughtGroup",int.class);cleanup.setAccessible(true);cleanup.invoke(p,0);
        verify(p.getSudoku().getValue(31)==5&&ink().stream().noneMatch(s->s.getThoughtGroup()==0),"satisfied OR became remaining true premise");
        reset();mark(31,5,2,0);p.toggleDoodleHypothesisEntry();mark(59,7,2,0);p.finishDoodleHypothesisEntry();
        command(ChainOriginProbe.candidate(31,5));enter();
        verify(find(31,5,2,0)==null&&find(59,7,2,0).isHypothesisStart()&&!find(59,7,2,0).isConclusionOutlined(),"all-false valid remainder lost");
        reset();mark(31,5,2,0);p.toggleDoodleHypothesisEntry();mark(59,7,2,0);p.finishDoodleHypothesisEntry();emptyCell(0);enter();
        verify(ink().stream().anyMatch(DoodleStroke::isProofOnly),"derived OR fixture missing evidence");
        mark(27,1,2,0);command(ChainOriginProbe.candidate(27,1));enter();
        verify(find(31,5,1,0).isHypothesisStart()&&find(59,7,1,0).isHypothesisStart()
                &&ink().stream().anyMatch(DoodleStroke::isProofOnly)&&find(27,1,2,0)==null,"valid derived OR lost roots/evidence");
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("apple.awt.UIElement","true");Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        output=Files.createDirectories(Paths.get(System.getProperty("hodoku.probe.output",System.getProperty("java.io.tmpdir"))));
        try{edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);
            roles();feedback();lifetime();return null;});System.out.println("PASS DoodleStartEditingProbe: "+checks+" input/feedback/lifetime/session/replay checks");
        }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
