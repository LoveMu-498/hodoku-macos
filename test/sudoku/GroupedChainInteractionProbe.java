package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.lang.reflect.*;
import java.io.*;
import javax.swing.*;
import javax.imageio.ImageIO;

/** Exercises real panel handlers on a disposable board and captures the Swing renderer. */
public final class GroupedChainInteractionProbe {
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void click(SudokuPanel p,int cell,int d,boolean shift)throws Exception {
        Method coords=SudokuPanel.class.getDeclaredMethod("getCandKoord",int.class,int.class,int.class);coords.setAccessible(true);
        Point2D point=(Point2D)coords.invoke(p,cell,d,((Integer)field(p,"cellSize")).intValue());
        Method m=SudokuPanel.class.getDeclaredMethod("handleFreeChainMousePressed",MouseEvent.class);m.setAccessible(true);
        m.invoke(p,new MouseEvent(p,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),shift?InputEvent.SHIFT_DOWN_MASK:0,(int)Math.round(point.getX()),(int)Math.round(point.getY()),1,false,MouseEvent.BUTTON1));
    }
    static BufferedImage paint(SudokuPanel p){BufferedImage im=new BufferedImage(720,720,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();p.paint(g);g.dispose();return im;}
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(()->{MainFrame f=null;try {
            ApplicationAppearance.initialize(args.length>0&&args[0].equals("dark")?AppearanceMode.DARK:AppearanceMode.LIGHT);
            f=new MainFrame(null);SudokuPanel p=f.getSudokuPanel();p.setSize(720,720);
            p.setSudoku((String)null);p.getSudoku().setSudoku(new String(new char[81]).replace('\0','0'));p.setShowCandidates(true);paint(p);p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
            click(p,9,3,false);click(p,10,3,true);click(p,11,3,true);
            UserChain chain=(UserChain)field(p,"activeUserChain");check(chain.getNodes().size()==1&&chain.getNodes().get(0).cells().length==3,"Shift does not extend group");
            click(p,12,3,true);check(chain.getNodes().get(0).cells().length==3,"fourth member accepted");
            Method undo=SudokuPanel.class.getDeclaredMethod("undoUserChains");undo.setAccessible(true);undo.invoke(p);
            chain=(UserChain)field(p,"activeUserChain");check(chain.getNodes().get(0).cells().length==2,"undo loses member granularity");
            Method redo=SudokuPanel.class.getDeclaredMethod("redoUserChains");redo.setAccessible(true);redo.invoke(p);
            click(p,22,3,false);click(p,31,3,false);click(p,10,3,false);
            chain=(UserChain)field(p,"activeUserChain");check(chain.isClosed(),"first-group member fails to close");
            check(field(p,"reasoningProposal")==null,"close starts validation");
            UserChainValidator.Result result=UserChainValidator.validate(p.getSudoku(),chain);
            p.showUserChainValidation(result);
            check(!((java.util.Set<?>)field(p,"invalidUserChainRelations")).isEmpty(),"invalid edge missing");
            @SuppressWarnings("unchecked") java.util.List<UserChain> stored=(java.util.List<UserChain>)field(p,"userChains");
            UserChain cross=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(8,1,19),GroupedChainProbe.node(8,29)},false);
            stored.add(cross);
            ImageIO.write(paint(p),"png",new File("/tmp/hodoku-group-build/group-"+(args.length>0?args[0]:"light")+".png"));
            p.reasoningBoardChanged();check(((java.util.Set<?>)field(p,"invalidUserChainRelations")).isEmpty(),"board change leaves stale red edges");
        }catch(Exception e){throw new RuntimeException(e);}finally{if(f!=null)f.dispose();}});
        System.out.println("Grouped input, member undo/redo, close timing, error and Swing rendering checks passed");System.exit(0);
    }
}
