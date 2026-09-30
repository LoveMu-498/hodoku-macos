package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.*;
import javax.swing.*;
import static sudoku.GroupedChainTransactionProbe.*;

/** The user's candidate board through the actual frame dispatcher and unchanged singles engine. */
public final class GlobalSinglesShortcutProbe {
    static final String GRID="25 27 8 3 4 6 9 15 17 1 37 35 9 78 58 4 6 2 6 9 4 1 57 2 3 58 78 8 5 2 6 9 7 1 3 4 3 6 9 48 18 18 27 27 5 7 4 1 5 2 3 8 9 6 25 138 37 2 135 4 6 78 9 9 18 57 28 6 15 27 4 3 4 23 6 7 38 9 5 12 18";
    static KeyEventDispatcher dispatcher;
    static Object field(String name)throws Exception {Field x=MainFrame.class.getDeclaredField(name);x.setAccessible(true);return x.get(f);}
    static void reset() {
        String[] cells=GRID.split(" ");StringBuilder values=new StringBuilder();
        for(int i=0;i<81;i++)values.append(cells[i].length()==1&&i!=57?cells[i]:"0");
        p.setSudoku(values.toString());
        for(int i=0;i<81;i++)if(p.getSudoku().getValue(i)==0)for(int d=1;d<=9;d++)
            if(!cells[i].contains(""+d))p.getSudoku().delCandidate(i,d);
        p.setShowCandidates(true);f.check();
    }
    static void key(Component source,int id,int mods) {dispatcher.dispatchKeyEvent(new KeyEvent(source,id,System.currentTimeMillis(),mods,KeyEvent.VK_F11,KeyEvent.CHAR_UNDEFINED));}
    public static void main(String[] args)throws Exception {
        try {
            edt(()->{
                Options.getInstance().setShowSudokuSolved(false);f=new MainFrame(null);p=f.getSudokuPanel();
                dispatcher=(KeyEventDispatcher)field("annotationKeyDispatcher");
                reset();check(p.getSudoku().getValue(57)==0&&p.getSudoku().getAnzCandidates(57)==1,"r7c4 fixture is not a naked single");
                p.setAllSingles();String expected=TechniqueStepCatalog.createSignature(p.getSudoku());
                check(p.getSudoku().getValue(57)==2,"original engine did not fill r7c4");
                for(AnnotationTool tool:AnnotationTool.values())for(int focus=0;focus<4;focus++) {
                    reset();p.setAnnotationTool(tool);AnnotationTool selectedTool=p.getAnnotationTool();p.resetShowHintCellValues();p.setShowHintCellValue(7);
                    AnnotationTimelineProbe.ink().add(AnnotationTimelineProbe.line(500,Color.MAGENTA));
                    int inks=p.getDoodleStrokeCount();
                    SolutionStep unwanted=new SolutionStep(SolutionType.NAKED_SINGLE);unwanted.addIndex(57);unwanted.addValue(8);p.setStep(unwanted);
                    Component source=focus==0?p:focus==1?f:focus==2?(Component)field("undoToolButton"):f.getHintTextArea();
                    check(((JMenuItem)field("setAllSinglesMenuItem")).isEnabled(),"F11 menu disabled in fixture");
                    key(source,KeyEvent.KEY_PRESSED,0);
                    check(expected.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"F11 differed from whole-board engine: "+tool+" focus="+focus);
                    check(p.getAnnotationTool()==selectedTool&&p.getShowHintCellValues()[7]&&p.getDoodleStrokeCount()==inks,"F11 lost tool/filter/annotations: "+selectedTool+" -> "+p.getAnnotationTool()+", digit7="+p.getShowHintCellValues()[7]+", ink="+p.getDoodleStrokeCount()+"/"+inks);
                    key(source,KeyEvent.KEY_PRESSED,0);key(source,KeyEvent.KEY_RELEASED,0);
                    check(p.getStep()==null,"old preview retained");
                }
                reset();String before=TechniqueStepCatalog.createSignature(p.getSudoku());
                JDialog dialog=new JDialog(f);JTextField input=new JTextField();dialog.add(input);
                key(input,KeyEvent.KEY_PRESSED,0);key(input,KeyEvent.KEY_RELEASED,0);dialog.dispose();
                check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"dialog F11 changed board");
                key(p,KeyEvent.KEY_PRESSED,InputEvent.SHIFT_DOWN_MASK);key(p,KeyEvent.KEY_RELEASED,InputEvent.SHIFT_DOWN_MASK);
                check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"modified F11 changed board");
                ReplayController controller=f.getReplayController();controller.openViewer(new ReplaySession(new ReplayBoard(p.getSudoku()),1));
                key(p,KeyEvent.KEY_PRESSED,0);key(p,KeyEvent.KEY_RELEASED,0);
                ((JMenuItem)field("setAllSinglesMenuItem")).doClick();
                check(before.equals(TechniqueStepCatalog.createSignature(p.getSudoku())),"replay F11/menu changed live board");controller.closeViewer();
                System.out.println("F11: user r7c4=2; 6 tools x 4 focus owners; whole-board parity, filter/ink retention, preview cancellation, dialog/replay boundaries passed");
                return null;
            });
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        finally {if(f!=null)edt(()->{f.dispose();return null;});}
        System.exit(0);
    }
}
