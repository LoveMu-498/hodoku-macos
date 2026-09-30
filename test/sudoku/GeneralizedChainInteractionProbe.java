package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Real panel mouse handlers, frame popup, undo and proof rendering on isolated boards. */
public final class GeneralizedChainInteractionProbe {
    static Point at(int cell,int digit)throws Exception{return ChainOriginProbe.candidate(cell,digit);}
    static void reset()throws Exception{p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);ChainOriginProbe.render(null);}
    public static void main(String[] args)throws Exception{
        try{edt(()->{
            ApplicationAppearance.initialize(args.length==0?AppearanceMode.LIGHT:AppearanceMode.DARK);f=new MainFrame(null);p=f.getSudokuPanel();f.setVisible(true);reset();
            click(1,0,at(0,3));for(int c:new int[]{1,2,9,10,11,18,19,20})click(1,InputEvent.SHIFT_DOWN_MASK,at(c,3));
            check(active().getNodes().get(0).atoms().length==9,"nine members input lost");
            click(1,InputEvent.SHIFT_DOWN_MASK,at(40,5));check(active().getNodes().get(0).atoms().length==10,"mixed Shift rejected");
            String whole=active().getNodes().get(0).key();p.undoCurrentAnnotation();check(active().getNodes().get(0).atoms().length==9,"mixed undo");p.redoCurrentAnnotation();check(active().getNodes().get(0).key().equals(whole),"mixed redo");
            click(1,0,at(80,9));ChainOriginProbe.render("generalized-large-mixed");
            p.finishCurrentUserChain(); // Current toolbar end-segment action.
            click(1,InputEvent.META_DOWN_MASK,at(0,3));check(active().getNodes().get(0).atoms().length==1,"Command did not select single atom");
            check(done().get(0).getNodes().get(0).atoms().length==10,"Command split original group");
            reset();click(1,0,at(0,1));click(1,0,at(1,1));click(1,0,at(2,1));p.finishCurrentUserChain();
            click(1,0,at(3,1));click(1,0,at(2,1));
            check(done().isEmpty()&&active().getNodes().size()==4,"endpoint reconnect did not assemble path");
            check(p.currentChainOrigin().contains(0,1)&&active().getNodes().get(0).contains(3,1),"reconnect endpoint orientation");
            java.util.Map<UserChain,java.util.Set<Integer>> flipped=new IdentityHashMap<>();flipped.put(active(),Collections.singleton(1));
            java.lang.reflect.Method flip=SudokuPanel.class.getDeclaredMethod("flipChainEdges",java.util.Map.class);flip.setAccessible(true);flip.invoke(p,flipped);
            check(p.currentChainOrigin().contains(0,1),"middle flip moved terminal");
            call("removeLastUserChainNode");check(p.currentChainOrigin().contains(1,1),"tail delete origin");p.undoCurrentAnnotation();check(p.currentChainOrigin().contains(0,1),"tail undo origin");
            ChainOriginProbe.render("generalized-rejoined");
            reset();done().add(GroupedChainProbe.chain(false,new UserChainNode[]{GeneralizedChainProbe.atoms(1,12),GeneralizedChainProbe.atoms(405)},true));
            done().add(GroupedChainProbe.chain(false,new UserChainNode[]{GeneralizedChainProbe.atoms(1,23),GeneralizedChainProbe.atoms(506)},false));
            click(1,0,at(0,1));p.flushMappedClick();MenuElement[] path=MenuSelectionManager.defaultManager().getSelectedPath();check(path.length>0,"overlapping group chooser missing");
            JPopupMenu popup=(JPopupMenu)path[0];check(popup.getComponentCount()==3,"chooser lacks single and both full groups");
            ((JMenuItem)popup.getComponent(2)).setArmed(true);ChainOriginProbe.render("generalized-choice-hover");
            ((JMenuItem)popup.getComponent(2)).doClick();popup.setVisible(false);check(active().getNodes().get(0).key().equals(GeneralizedChainProbe.atoms(1,23).key()),"chooser changed proposition");
            reset();UserChain ring=GroupedChainProbe.chain(true,new UserChainNode[]{GeneralizedChainProbe.atoms(1,12,403),GeneralizedChainProbe.atoms(22),GeneralizedChainProbe.atoms(33)},false,true,false);
            String text=ChainTextCodec.format(p.getSudoku(),Collections.singletonList(ring),UserChainValidator.preview(p.getSudoku(),ring).steps.get(0));
            ChainTextCodec.Document doc=ChainTextCodec.parse(text);check(doc!=null,"generalized preview parse");
            p.showImportedChainText(doc);ChainOriginProbe.render("generalized-proof-preview");
            check(p.copyChainText().equals(doc.text()),"preview copy lost source or conclusion");
            String before=TechniqueStepCatalog.createSignature(p.getSudoku());
            p.handleAnnotationKeyPressed(new KeyEvent(p,KeyEvent.KEY_PRESSED,0,0,KeyEvent.VK_ENTER,'\n'));
            check(!p.getSudoku().isCandidate(0,1)&&!p.getSudoku().isCandidate(1,2)&&!p.getSudoku().isCandidate(40,3),"generalized imported preview not applied");
            p.undo();check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"generalized apply undo");
            reset();ring.setActive(true);set("activeUserChain",ring);call("noteUserChainReasoningChanged");call("handleReasoningEnter");return null;
        });await(true);
        edt(()->{check(p.getStep()!=null&&!p.getStep().getGeneralizedProofs().isEmpty(),"Enter lost generalized proof");
            check(p.getSudoku().isCandidate(0,1),"first Enter applied early");ChainOriginProbe.render("generalized-authored-preview");call("handleReasoningEnter");return null;});await(false);
        edt(()->{check(!p.getSudoku().isCandidate(0,1)&&!p.getSudoku().isCandidate(1,2)&&!p.getSudoku().isCandidate(40,3),"second Enter lost group members");p.undo();check(p.getSudoku().isCandidate(40,3),"authored apply undo");return null;});
        System.out.println("Generalized mouse groups, chooser, endpoints, undo, imported and authored Enter preview/apply passed");
        }catch(Throwable e){e.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
