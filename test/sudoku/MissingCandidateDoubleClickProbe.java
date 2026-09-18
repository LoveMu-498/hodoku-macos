package sudoku;

import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

/** Regression: a red missing solution digit remains an explicit mouse entry target. */
public final class MissingCandidateDoubleClickProbe {
    public static void main(String[] args) throws Exception {
        final Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame(null);
            try {
                SudokuPanel p = frame.getSudokuPanel();p.setSize(600,600);
                for(int remaining : new int[]{2,1,0}) {
                    p.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
                    p.setShowCandidates(true);p.setShowDeviations(true);
                    require(p.getSudoku().getSolution(2)==4,"fixture solution");
                    p.getSudoku().setCandidate(2,4,false);
                    if(remaining<2)p.getSudoku().setCandidate(2,2,false);
                    if(remaining<1)p.getSudoku().setCandidate(2,1,false);
                    p.paint(new BufferedImage(600,600,BufferedImage.TYPE_INT_ARGB).getGraphics());
                    int size=p.getX(0,1)-p.getX(0,0);
                    int x=p.getX(0,2)+size/6,y=p.getY(0,2)+size/2;
                    MouseEvent event=new MouseEvent(p,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,x,y,2,false,MouseEvent.BUTTON1);
                    SudokuPanel.MouseClickDTO dto=p.new MouseClickDTO(event,p,true);
                    require(dto.candidate==4,"pointer target");
                    if(remaining==2) {
                        p.setShowDeviations(false);p.onDoubleLeftClick(dto,2);
                        require(p.getSudoku().getValue(2)==0,"invisible missing candidate accepted");
                        p.setShowDeviations(true);
                    }
                    require(p.onDoubleLeftClick(dto,2),"click rejected");
                    require(p.getSudoku().getValue(2)==4,"red 4 not entered with remaining="+remaining);
                }
                System.out.println("Missing candidate double-click checks passed: 2/1/0 remaining; hidden target unchanged");
            } catch(Throwable t) {failure[0]=t;} finally {frame.dispose();}
        });
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}System.exit(0);
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
