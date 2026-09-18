package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** User video regression: delete 2=3 while preserving 3-6=6. */
public final class ChainEdgeDeletionProbe {
    private static MainFrame frame;private static Robot robot;
    public static void main(String[] args)throws Exception {
        try {
            verifyCuts();robot=new Robot();robot.setAutoDelay(80);
            edt(()->{frame=new MainFrame(null);frame.setSize(1000,700);frame.setLocation(10,30);frame.setVisible(true);
                SudokuPanel p=frame.getSudokuPanel();p.setSudoku("250480009810052040470001285987214536142365090635879400361500004794000050528040000");p.setShowCandidates(true);frame.toFront();frame.fixFocus();return null;});
            robot.waitForIdle();Point focus=point(40,5);robot.mouseMove(focus.x,focus.y);click();
            edt(()->{frame.getSudokuPanel().setAnnotationTool(AnnotationTool.FREE_CHAIN);return null;});
            int[][] nodes={{58,2},{22,3},{12,6},{11,6}};
            for(int[] node:nodes){Point p=point(node[0],node[1]);robot.mouseMove(p.x,p.y);click();}
            require(edt(()->frame.getSudokuPanel().currentReasoningChains().get(0).getNodes().size())==4,"video chain fixture");
            String board=edt(()->TechniqueStepCatalog.createSignature(frame.getSudokuPanel().getSudoku()));
            Point a=point(58,2),b=point(22,3);int x=(a.x+b.x)/2,y=(a.y+b.y)/2;
            robot.keyPress(KeyEvent.VK_CONTROL);robot.mouseMove(x-5,y-5);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseMove(x+5,y+5);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.keyRelease(KeyEvent.VK_CONTROL);robot.waitForIdle();
            edt(()->{List<UserChain> chains=frame.getSudokuPanel().currentReasoningChains();require(chains.size()==1,"remaining graph split unexpectedly");UserChain c=chains.get(0);
                require(c.getNodes().size()==3&&c.getNodes().get(0).getCandidate()==3&&c.getNodes().get(1).getCandidate()==6&&c.getNodes().get(2).getCandidate()==6,"3-6=6 was not preserved");
                require(c.getStrongRelations().equals(Arrays.asList(false,true)),"remaining link strengths changed");return null;});
            File image=new File(System.getProperty("hodoku.probe.output","/tmp/hodoku-edge-delete"),"edge-deleted.png");image.getParentFile().mkdirs();ImageIO.write(robot.createScreenCapture(edt(()->frame.getBounds())),"png",image);
            robot.keyPress(KeyEvent.VK_META);robot.keyPress(KeyEvent.VK_Z);robot.keyRelease(KeyEvent.VK_Z);robot.keyRelease(KeyEvent.VK_META);robot.waitForIdle();
            require(edt(()->frame.getSudokuPanel().currentReasoningChains().get(0).getNodes().size())==4,"undo did not restore all four nodes");
            require(board.equals(edt(()->TechniqueStepCatalog.createSignature(frame.getSudokuPanel().getSudoku()))),"chain deletion changed Sudoku");
            System.out.println("Chain edge deletion passed: real Control hit on 2=3 preserves 3-6=6; undo; middle splits; loop opens; multi-edge cuts");
        }catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(frame!=null)edt(()->{frame.dispose();return null;});}System.exit(0);
    }
    private static void verifyCuts(){
        UserChain c=new UserChain();for(int i=0;i<5;i++)c.getNodes().add(new UserChainNode(i,1,Color.ORANGE));c.getStrongRelations().addAll(Arrays.asList(true,false,true,false));c.getRelationColors().addAll(Arrays.asList(Color.RED,Color.BLUE,Color.GREEN,Color.ORANGE));c.setActive(true);
        List<UserChain> fragments=UserChainCuts.removeEdges(c,Collections.singleton(1));require(fragments.size()==2&&fragments.get(0).getNodes().size()==2&&fragments.get(1).getNodes().size()==3,"middle removal lost outside paths");require(fragments.get(1).isActive()&&!fragments.get(0).isActive(),"active endpoint not preserved");require(fragments.get(1).getRelationColors().get(0).equals(Color.GREEN),"edge color changed");
        c.setClosed(true);c.getStrongRelations().add(true);c.getRelationColors().add(Color.PINK);
        fragments=UserChainCuts.removeEdges(c,Collections.singleton(1));require(fragments.size()==1&&!fragments.get(0).isClosed()&&fragments.get(0).getNodes().size()==5&&fragments.get(0).getStrongRelations().size()==4,"loop did not open");
        fragments=UserChainCuts.removeEdges(c,new HashSet<Integer>(Arrays.asList(1,3)));require(fragments.size()==2&&fragments.get(0).getStrongRelations().size()+fragments.get(1).getStrongRelations().size()==3,"multiple cuts lost edges");
        require(UserChainCuts.removeEdges(c,new HashSet<Integer>(Arrays.asList(0,1,2,3,4))).isEmpty(),"all-edge deletion left orphan nodes");
    }
    private static Point point(int cell,int digit)throws Exception{return edt(()->{SudokuPanel p=frame.getSudokuPanel();Point o=p.getLocationOnScreen();int s=p.getX(0,1)-p.getX(0,0);o.translate(p.getX(cell/9,cell%9)+(int)(((digit-1)%3+.5)*s/3),p.getY(cell/9,cell%9)+(int)(((digit-1)/3+.5)*s/3));return o;});}
    private static void click(){robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static <T>T edt(Callable<T> c)throws Exception{FutureTask<T> t=new FutureTask<T>(c);SwingUtilities.invokeAndWait(t);return t.get();}
}
