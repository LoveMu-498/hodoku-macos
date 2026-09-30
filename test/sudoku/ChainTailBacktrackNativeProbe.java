package sudoku;
import java.awt.*;
import java.awt.event.*;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;
/** Native mouse delivery after Finish, followed by actual tail continuation. */
public final class ChainTailBacktrackNativeProbe {
    static Point screen(int cell)throws Exception{return edt(()->{Point v=point(cell),o=p.getLocationOnScreen();v.translate(o.x,o.y);return v;});}
    static void tap(Robot r,int cell,int button)throws Exception{Point v=screen(cell);r.mouseMove(v.x,v.y);r.mousePress(button);r.mouseRelease(button);r.waitForIdle();Thread.sleep(250);}
    public static void main(String[] args)throws Exception{try{
        Robot r=new Robot();r.setAutoDelay(80);
        edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);f.setSize(1000,760);f.setLocation(30,40);f.setVisible(true);f.toFront();f.fixFocus();return null;});
        Process activation=new ProcessBuilder("osascript","-e","tell application \"System Events\" to set frontmost of first process whose unix id is "+java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0]+" to true").start();
        check(activation.waitFor()==0,"native test window activation failed");
        r.waitForIdle();Thread.sleep(700);check(edt(()->f.isFocused()),"native test window has no focus");tap(r,40,InputEvent.BUTTON1_DOWN_MASK);
        edt(()->{p.setAnnotationTool(AnnotationTool.FREE_CHAIN);install(ChainTailBacktrackProbe.fixture(false));p.finishCurrentUserChain();f.toFront();f.fixFocus();p.addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){System.out.println("native press "+e.getButton()+" "+e.getPoint());}public void mouseReleased(MouseEvent e){System.out.println("native release "+e.getButton()+" "+e.getPoint());}});return null;});
        Thread.sleep(500);System.out.println(edt(()->"focus="+f.isFocused()+" origin="+p.getLocationOnScreen()+" size="+p.getSize()));tap(r,40,InputEvent.BUTTON3_DOWN_MASK);
        edt(()->{System.out.println("active="+active()+" done="+done().size()+" precise="+read("preciseChainCandidate"));check(active()!=null&&active().getNodes().size()==2&&done().isEmpty(),"native right did not reopen tail");return null;});
        tap(r,27,InputEvent.BUTTON1_DOWN_MASK);
        edt(()->{check(active()!=null&&active().getNodes().size()==3&&active().getNodes().get(2).getCellIndex()==27&&done().isEmpty(),"native left started new chain");return null;});
        System.out.println("PASS: native Robot right backtrack then left continues same completed chain");
    }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);}
}
