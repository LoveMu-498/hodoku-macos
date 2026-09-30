package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.util.Map;
import static sudoku.GroupedChainTransactionProbe.*;
import static sudoku.ChainEditingProbe.*;

/** Actual EDT timers: speed, exclusive double action, target tolerance and release deadline. */
public final class TplsClickLatencyProbe {
    static Map<Integer,Color> cells()throws Exception{return (Map<Integer,Color>)read("coloringMap");}
    static Map<Integer,Color> candidates()throws Exception{return (Map<Integer,Color>)read("coloringCandidateMap");}
    static void waitUntilCell(int cell,long start,long budget)throws Exception {
        while(!edt(()->cells().containsKey(cell))) {
            if((System.nanoTime()-start)/1000000>budget)throw new AssertionError("single exceeded "+budget+" ms");
            Thread.sleep(5);
        }
        System.out.println("Cell "+cell+" response: "+((System.nanoTime()-start)/1000000)+" ms");
    }
    public static void main(String[] args)throws Exception {
        try {
            edt(()->{f=new MainFrame(null);p=f.getSudokuPanel();p.setSudoku((String)null);
                p.getSudoku().set(GroupedChainProbe.blank());p.setShowCandidates(true);p.setSize(810,810);
                TplsInputMappingProbe.paintPanel();p.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);
                // A deliberately slow system setting must not make annotation clicks wait 900 ms.
                set("doubleClickSpeed",900L);return null;});
            Point a=edt(()->point(0)),b=edt(()->point(12));
            long start=System.nanoTime();edt(()->{click(1,0,a);return null;});waitUntilCell(0,start,320);
            edt(()->{p.clearColoringWithUndo();click(1,0,a);return null;});Thread.sleep(90);
            edt(()->{click(1,0,new Point(a.x+5,a.y));check(candidates().containsKey(1)&&cells().isEmpty(),"double flickered/committed cell or rejected small motion");return null;});
            Thread.sleep(230);edt(()->{check(cells().isEmpty(),"late single after double");p.clearColoringWithUndo();return null;});
            // Holding the first button consumes the deadline, not an extra timer after release.
            edt(()->{event(MouseEvent.MOUSE_PRESSED,1,0,b);return null;});Thread.sleep(240);
            start=System.nanoTime();edt(()->{event(MouseEvent.MOUSE_RELEASED,1,0,b);return null;});waitUntilCell(12,start,100);
            // Two clicks outside the short window are independent singles, regardless of native count.
            edt(()->{p.clearColoringWithUndo();click(1,0,a);return null;});Thread.sleep(250);
            edt(()->{click(1,0,a);return null;});Thread.sleep(250);
            edt(()->{check(cells().containsKey(0)&&candidates().isEmpty(),"slow pair became double");
                p.clearColoringWithUndo();event(MouseEvent.MOUSE_PRESSED,3,0,a);
                event(MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON3_DOWN_MASK,b);
                event(MouseEvent.MOUSE_RELEASED,3,0,b);check(read("pendingMappedClick")==null,"drag scheduled click");return null;});
            System.out.println("PASS: bounded single latency, same-candidate motion, exclusive double, press-based deadline, slow independent clicks, drag isolation");
        }catch(Throwable e){e.printStackTrace();System.exit(1);}
        finally{if(f!=null)edt(()->{f.dispose();return null;});}System.exit(0);
    }
}
