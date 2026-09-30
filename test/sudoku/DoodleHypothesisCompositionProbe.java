package sudoku;

import java.awt.Color;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.click;

/** Real mark mutations and Swing mouse events; persistence is a fixture, not native quit evidence. */
public final class DoodleHypothesisCompositionProbe {
    @SuppressWarnings("unchecked")
    static List<DoodleStroke> ink() throws Exception { return (List<DoodleStroke>) read("doodleStrokes"); }
    static DoodleStroke find(int cell, int digit, int kind, int group) throws Exception {
        for (DoodleStroke s : ink()) if (s.isStandardCandidateMark() && s.getAnchorCell()==cell
                && s.getAnchorDigit()==digit && s.getCandidateMarkKind()==kind && s.getThoughtGroup()==group) return s;
        return null;
    }
    static void mark(int cell, int digit, int kind, int group) throws Exception {
        Method m=SudokuPanel.class.getDeclaredMethod("toggleCandidateHypothesisMark",int.class,int.class,int.class,int.class,Color.class);
        m.setAccessible(true); m.invoke(p,cell,digit,kind,group,group==0?Color.RED:Color.BLUE);
    }
    static int mask(int cell,int digit,int kind) { return p.getDoodleMarkSourceMask(cell,digit,kind); }
    static void outline(int cell,int digit) throws Exception {
        Point point=ChainOriginProbe.candidate(cell,digit);
        click(1,InputEvent.META_DOWN_MASK,point); p.flushMappedClick();
    }
    static void initialize() throws Exception {
        f=new MainFrame(null); p=f.getSudokuPanel(); p.setSudoku((String)null);
        p.getSudoku().set(GroupedChainProbe.blank()); p.setShowCandidates(true);
        p.setAnnotationTool(AnnotationTool.DOODLE); p.getCellZoomPanel().selectPaletteGroup(0);
        p.setSize(810,810); p.paint(new BufferedImage(810,810,BufferedImage.TYPE_INT_RGB).getGraphics());
    }
    static void starts() throws Exception {
        mark(0,1,1,0); check(find(0,1,1,0).isHypothesisStart(),"first standard mark is not a start");
        mark(10,2,2,0); check(!find(10,2,2,0).isHypothesisStart(),"ordinary mark became another automatic start");
        p.cancelDoodleHypothesisStarts();
        check(find(0,1,1,0)!=null&&!find(0,1,1,0).isHypothesisStart(),"cancel removed ink or kept start");
        mark(0,1,1,0); mark(10,2,2,0); // Delete all standard ink through the actual mutation path.
        mark(20,3,1,0); check(!find(20,3,1,0).isHypothesisStart(),"ordinary deletion restarted automatic start");
        p.toggleDoodleHypothesisEntry(); check(p.getDoodleHypothesisEntryMode()!=0,"explicit entry did not start");
        mark(30,4,1,0);
        int before = ink().size(); int undoBefore = ((java.util.Stack<?>)read("doodleUndoStack")).size();
        mark(40,5,2,0);
        check(find(40,5,2,0)==null && ink().size()==before
                && ((java.util.Stack<?>)read("doodleUndoStack")).size()==undoBefore,"opposite start mutated ink or undo");
        mark(50,6,1,0); mark(60,7,1,0); p.finishDoodleHypothesisEntry();
        DoodleStroke a=find(50,6,1,0),b=find(60,7,1,0);
        check(a.isHypothesisStart()&&b.isHypothesisStart()&&a.getHypothesisConditionId()>0
                &&a.getHypothesisConditionId()==b.getHypothesisConditionId()&&a.getHypothesisGroupKind()==1&&b.getHypothesisGroupKind()==1,"automatic OR group identity missing");
        p.getCellZoomPanel().selectPaletteGroup(2); p.toggleDoodleHypothesisEntry();
        mark(70,8,2,2); mark(80,9,2,2);
        before=ink().size(); undoBefore=((java.util.Stack<?>)read("doodleUndoStack")).size();
        mark(40,5,1,2);
        check(find(40,5,1,2)==null && ink().size()==before
                && ((java.util.Stack<?>)read("doodleUndoStack")).size()==undoBefore,"opposite circle start mutated ink or undo");
        p.finishDoodleHypothesisEntry(); p.getCellZoomPanel().selectPaletteGroup(0);
        check(find(70,8,2,2).getHypothesisGroupKind()==2
                &&find(70,8,2,2).getHypothesisConditionId()==find(80,9,2,2).getHypothesisConditionId()
                &&find(70,8,2,2).getHypothesisConditionId()!=a.getHypothesisConditionId(),"all-false group collapsed into OR");
    }
    static void composition() throws Exception {
        mark(12,2,2,0); mark(12,2,2,1); mark(22,3,2,0);
        check(mask(12,2,2)==3,"two groups not composed"); check(mask(12,2,1)==0,"cross inferred a circle");
        String board=TechniqueStepCatalog.createSignature(p.getSudoku());
        outline(12,2); outline(22,3);
        check(find(12,2,2,0).isConclusionOutlined()&&find(12,2,2,0).getConclusionSourceMask()==3,"compound selection did not bind its sources");
        check(find(22,3,2,0).isConclusionOutlined(),"unrelated single selection missing");
        mark(12,2,2,1);
        check(mask(12,2,2)==1&&!find(12,2,2,0).isConclusionOutlined(),"source deletion did not invalidate compound selection");
        check(find(22,3,2,0).isConclusionOutlined(),"source deletion invalidated unrelated single selection");
        p.undoCurrentAnnotation(); check(mask(12,2,2)==3&&find(12,2,2,0).isConclusionOutlined(),"undo did not restore sources and selection");
        p.redoCurrentAnnotation(); check(mask(12,2,2)==1&&!find(12,2,2,0).isConclusionOutlined(),"redo did not recompute sources");
        mark(12,2,2,0); check(mask(12,2,2)==0,"last source removal left historical cross");
        mark(13,3,1,0); mark(13,3,1,1); check(mask(13,3,1)==3,"circle composition missing");
        mark(13,3,2,1); check(mask(13,3,1)==3&&mask(13,3,2)==2,"circle/cross sources were merged");
        mark(13,3,1,0); check(mask(13,3,1)==2,"circle deletion retained old source");
        check(board.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"display or selection changed board");
    }
    static void persistence() throws Exception {
        GuiState state=new GuiState(); state.setIncludeAnnotations(true); p.getState(state,true);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(XMLEncoder e=new XMLEncoder(bytes)){e.setExceptionListener(x->{throw new AssertionError(x);});e.writeObject(state);}
        GuiState restored;
        try(XMLDecoder d=new XMLDecoder(new ByteArrayInputStream(bytes.toByteArray()))){restored=(GuiState)d.readObject();}
        SessionSnapshot snapshot=new SessionSnapshot(); snapshot.setGuiState(restored);
        File directory=Files.createTempDirectory("hodoku-hypothesis-probe-").toFile();
        SessionStore store=new SessionStore(new File(directory,"session.xml")); store.save(snapshot);
        p.setState(store.load().getGuiState());
        check(find(50,6,1,0).isHypothesisStart()&&find(50,6,1,0).getHypothesisGroupKind()==1
                &&find(50,6,1,0).getHypothesisConditionId()==find(60,7,1,0).getHypothesisConditionId(),"session copy lost OR metadata");
        check(find(70,8,2,2).getHypothesisGroupKind()==2,"session lost all-false metadata");
        check(mask(12,2,2)==0&&mask(13,3,1)==2&&mask(13,3,2)==2,"session restored stale source colors");
        check(find(22,3,2,0).isConclusionOutlined(),"session lost surviving selection");
        DoodleStroke legacy=new DoodleStroke(); check(!legacy.isHypothesisStart()&&legacy.getHypothesisConditionId()==0
                &&legacy.getHypothesisGroupKind()==0,"legacy bean defaults inferred starts");
        System.out.println("SessionStore fixture: "+directory);
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("apple.awt.UIElement","true");
        try {edt(()->{initialize(); starts(); composition(); persistence(); return null;});
            System.out.println("PASS: actual start mutation/cancel/group semantics, current circle/cross sources, Command compound invalidation, unrelated selection, undo/redo, XML and SessionStore fixture restore");
        } catch(Throwable t){t.printStackTrace();System.exit(1);}
        finally {if(f!=null)edt(()->{f.dispose();return null;});} System.exit(0);
    }
}
