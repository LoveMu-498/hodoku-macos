/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.DoodleContradictionWorkflowProbe.*;

/** Actual panel marks, pixels, native Enter, session files and persisted replay. */
public final class DoodleConsensusWorkflowProbe {
    private static int checks;
    private static void verify(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static int bluePixels(BufferedImage image,Point point){
        int count=0;for(int y=point.y-16;y<=point.y+16;y++)for(int x=point.x-16;x<=point.x+16;x++){
            Color c=new Color(image.getRGB(x,y));
            if(c.getBlue()>200&&c.getBlue()-c.getRed()>100&&c.getBlue()-c.getGreen()>50)count++;
        }return count;
    }
    static DoodleConsensusBasis.Analysis consensus()throws Exception{
        Method m=SudokuPanel.class.getDeclaredMethod("doodleConsensus",int.class,boolean.class);m.setAccessible(true);
        return (DoodleConsensusBasis.Analysis)m.invoke(p,p.getCellZoomPanel().getPaletteGroup(),true);
    }
    static void setup(int kind,int otherDigit,int otherKind)throws Exception{
        initialize(0);mark(31,5,kind,0);
        p.getCellZoomPanel().selectPaletteGroup(1);mark(31,otherDigit,otherKind,1);
        p.getCellZoomPanel().selectPaletteGroup(0);paint(null);
    }
    static void common(boolean batch)throws Exception{
        for(int group=0;group<2;group++){
            p.getCellZoomPanel().selectPaletteGroup(group);
            mark(32,1,2,group);
            if(batch){mark(32,9,2,group);mark(59,7,2,group);mark(70,9,1,group);}
        }
        p.getCellZoomPanel().selectPaletteGroup(0);paint(null);
    }
    static void checkReplay(ReplaySession replay,int at)throws Exception{
        ReplayFrame input=replay.frames().get(at),result=replay.frames().get(at+1);
        DoodleReplayStep step=input.annotations.doodleStep;
        verify(step.operation==DoodleReplayStep.Operation.CELL_FORCING&&step.coveragePairs.size()==1,"replay failed to freeze complementary branch pair");
        String text=result.annotations.doodleStep.description();
        verify(text.contains("单元格强制链")&&text.contains("R4C5=5")&&text.contains("R4C5≠5")
                &&text.contains("R4C6≠1")&&text.contains("R4C6≠9")&&text.contains("R8C8=9")&&text.contains("不重建中间推理"),"replay lost title, origin coverage or explicit shared conclusions");
        verify(input.evidence().length==0&&result.evidence().length==0,"doodle inference received a native proof certificate");
        verify(step.fills().equals(Collections.singleton(709))&&step.deletions().equals(new TreeSet<Integer>(Arrays.asList(321,329,597))),"explicit effects confused with peer deletions");
        verify(input.annotations.forEditing().ink().stream().noneMatch(DoodleStroke::isConclusionOutlined),"automatic replay selection became manual input on resume");
        // Windows readers stop before the Mac tail but must retain complete public labels.
        Path path=output.resolve("windows-prefix.hrep");Path nativeFile=f.getReplayController().directory().resolve(f.getReplayController().session().id+".hrep");
        byte[] data=Files.readAllBytes(nativeFile);int cut=-1;for(int i=0;i<data.length-4;i++)if(data[i]==0x48&&data[i+1]==0x4d&&data[i+2]==0x58&&data[i+3]==0x31){cut=i;break;}
        verify(cut>0,"missing common HRPL prefix");Files.write(path,Arrays.copyOf(data,cut));ReplaySession foreign=ReplayFiles.importFile(path);
        verify(foreign.frames().get(at).label.contains("单元格强制链")&&foreign.frames().get(at).label.contains("色组 B"),"Windows prefix omitted coverage explanation");
        verify(foreign.frames().get(at).annotations.forEditing().ink().stream().noneMatch(DoodleStroke::isConclusionOutlined),"public-only replay converted automatic outlines to manual selections");
        // v1 inner records (no coverage appendix) remain readable.
        Sudoku2 b=DoodleContradictionBasisProbe.board(),a=b.clone();a.delCandidate(80,5);
        DoodleStroke mark=DoodleReplayStepProbe.mark(805,2,false,true,false);
        byte[] old=DoodleReplayStep.capture(b,a,DoodleReplayStepProbe.annotations(Collections.singletonList(mark)),false).encode();
        verify(old[7]==1,"existing noncoverage operations unnecessarily changed inner version");
        verify(DoodleReplayStep.decode(old).operation==DoodleReplayStep.Operation.SELECTED_CONCLUSION,"previous inner replay version rejected");
        // Tampering with authored counterpart cannot retain the frozen necessary claim.
        java.util.List<DoodleStroke> corrupt=input.annotations.ink();corrupt.removeIf(s->s.getThoughtGroup()==1&&s.getAnchorCell()==32&&s.getAnchorDigit()==1);
        ReplayAnnotations annotation=new ReplayAnnotations("",input.annotations.geometry(),input.annotations.cells(),input.annotations.candidates(),corrupt,0).withDoodleStep(step,"");
        try{step.validatePair(input.withAnnotations(annotation),result);throw new AssertionError("missing counterpart accepted");}catch(java.io.IOException expected){checks++;}
    }
    static void workflow()throws Exception{
        setup(1,5,2);
        mark(31,7,2,0);command(ChainOriginProbe.candidate(31,7));verify(find(31,7,2,0).isConclusionOutlined(),"manual fixture was not selected");
        common(true);
        verify(consensus().truths.equals(Collections.singleton(709))&&consensus().falses.size()==3,"batch common claims missing");
        verify(ink().stream().noneMatch(DoodleStroke::isConclusionOutlined),"derived selection polluted raw ink or left manual selection");
        String before=TechniqueStepCatalog.createSignature(p.getSudoku());BufferedImage image=paint("common-blue");
        for(int[] target:new int[][]{{32,1},{32,9},{59,7},{70,9}})verify(bluePixels(image,ChainOriginProbe.candidate(target[0],target[1]))>8,"automatic blue not rendered at "+Arrays.toString(target));
        call("beginDoodleThoughtPreview");image=paint("common-held-preview");verify(pixels(image,ChainOriginProbe.candidate(70,9),new Color(0x1769FF))>8,"preview hid automatic common truth");call("clearDoodleThoughtPreview");
        verify(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"detection/preview changed board");
        int depth=((Stack<?>)read("doodleUndoStack")).size();command(ChainOriginProbe.candidate(32,1));verify(((Stack<?>)read("doodleUndoStack")).size()==depth,"Cmd changed automatic selection");
        p.getCellZoomPanel().selectPaletteGroup(2);image=paint(null);verify(pixels(image,ChainOriginProbe.candidate(32,1),new Color(0x1769FF))==0&&!consensus().hasConclusions(),"unrelated color displayed necessary blue");p.getCellZoomPanel().selectPaletteGroup(0);
        // Withdraw only the current contribution with real candidate click; undo restores it.
        ChainEditingProbe.click(1,InputEvent.SHIFT_DOWN_MASK,ChainOriginProbe.candidate(32,1));p.flushMappedClick();paint(null);
        verify(!consensus().falses.contains(321)&&find(32,1,2,1)!=null,"editing automatic mark was locked or erased another source");
        p.undoCurrentAnnotation();paint(null);verify(consensus().falses.contains(321),"undo did not restore derived common result");
        // Actual SessionStore preserves raw authored records, then computes the same blue selection.
        GuiState state=new GuiState();state.setIncludeAnnotations(true);p.getState(state,true);verify(state.getDoodleStrokes().stream().noneMatch(DoodleStroke::isConclusionOutlined),"session saved derived blue as raw selection");
        SessionSnapshot snapshot=new SessionSnapshot();snapshot.setGuiState(state);SessionStore store=new SessionStore(output.resolve("common-session.xml").toFile());store.save(snapshot);p.setState(store.load().getGuiState());p.setAnnotationTool(AnnotationTool.DOODLE);p.getCellZoomPanel().selectPaletteGroup(0);paint("common-session-restored");
        verify(consensus().falses.size()==3&&consensus().truths.size()==1,"session reopening lost branch coverage");
        f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));int frames=f.getReplayController().session().frames().size();int boardUndo=((Stack<?>)read("undoStack")).size();enter();paint("common-applied");
        verify(!p.getSudoku().isCandidate(32,1)&&!p.getSudoku().isCandidate(32,9)&&!p.getSudoku().isCandidate(59,7)&&p.getSudoku().getValue(70)==9,"Enter did not apply whole shared batch");
        verify(p.getSudoku().getValue(31)==0&&p.getSudoku().getValue(32)==0,"Enter applied origins or hidden manual circle");
        verify(((Stack<?>)read("undoStack")).size()==boardUndo+1,"batch did not use one native board undo");
        verify(ink().stream().filter(s->s.getThoughtGroup()==0).count()==1
                &&find(31,5,1,0)!=null&&find(31,5,1,0).isHypothesisStart()
                &&find(31,5,2,1)!=null,"application cleanup lost a valid origin or destroyed other branch");
        ReplaySession replay=savedReplay();verify(replay.frames().size()==frames+2,"batch did not append one input/result pair");checkReplay(replay,frames);
        p.undo();verify(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"one native undo failed to restore entire board");
    }
    static void otherCases()throws Exception{
        for(int kind:new int[]{1,2}){setup(kind,7,kind);common(false);verify(consensus().hasConclusions(),"bivalue pattern did not select common false");f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));enter();verify(!p.getSudoku().isCandidate(32,1)&&p.getSudoku().getValue(31)==0,"bivalue Enter applied an origin");ReplaySession replay=savedReplay();verify(replay.last().annotations.doodleStep.title().equals("单元格强制链"),"bivalue replay misnamed");}
        setup(1,7,1);common(false);p.getSudoku().setCandidate(31,9,true);paint(null);verify(!consensus().hasConclusions(),"third backend candidate ignored");
        setup(1,5,2);common(false);paint(null);p.getCellZoomPanel().selectPaletteGroup(1);p.cancelDoodleHypothesisStarts();p.getCellZoomPanel().selectPaletteGroup(0);
        String before=TechniqueStepCatalog.createSignature(p.getSudoku());enter();verify(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"stale displayed consensus Enter fell back");
        setup(1,5,2);common(false);mark(32,7,2,0);mark(32,9,2,0);paint(null);verify(basis().hasRefutation(),"refutation fixture not recognized");f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));enter();verify(!p.getSudoku().isCandidate(31,5)&&p.getSudoku().isCandidate(32,1),"consensus overrode refutation priority");
    }
    static void nativeEnter()throws Exception{
        edt(()->{setup(1,5,2);common(true);f.getReplayController().beginSession(new ReplayBoard(p.getSudoku()));f.setSize(1100,950);f.setLocation(70,70);f.setVisible(true);f.toFront();p.requestFocusInWindow();return null;});
        Process activation=new ProcessBuilder("osascript","-e","tell application \"System Events\" to set frontmost of first process whose unix id is "+java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]+" to true").start();verify(activation.waitFor()==0,"native activation failed");
        Robot robot=new Robot();robot.setAutoDelay(35);robot.delay(500);edt(()->{p.requestFocusInWindow();return null;});int frames=edt(()->f.getReplayController().session().frames().size());robot.keyPress(KeyEvent.VK_ENTER);robot.waitForIdle();
        String after=edt(()->{verify(p.getSudoku().getValue(70)==9&&!p.getSudoku().isCandidate(32,1)&&!p.getSudoku().isCandidate(32,9),"native Enter did not apply shared batch");return TechniqueStepCatalog.createSignature(p.getSudoku());});
        robot.keyPress(KeyEvent.VK_ENTER);robot.keyPress(KeyEvent.VK_ENTER);robot.waitForIdle();edt(()->{verify(after.equals(TechniqueStepCatalog.createSignature(p.getSudoku()))&&f.getReplayController().session().frames().size()==frames+2,"held native Enter repeated batch");return null;});robot.keyRelease(KeyEvent.VK_ENTER);robot.waitForIdle();
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("apple.awt.UIElement","true");Locale.setDefault(Locale.SIMPLIFIED_CHINESE);output=Files.createDirectories(Paths.get(System.getProperty("hodoku.probe.output",System.getProperty("java.io.tmpdir"))));
        try{edt(()->{ApplicationAppearance.initialize(AppearanceMode.LIGHT);f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);workflow();otherCases();return null;});
            if(args.length==0||!args[0].equals("--listeners-only"))nativeEnter();
            System.out.println("PASS DoodleConsensusWorkflowProbe: "+checks+" panel/pixel/session/replay checks"+(args.length==0||!args[0].equals("--listeners-only")?" plus native Robot Enter":" (listener/rendering scope)")+"; "+output);
        }catch(Throwable e){e.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
