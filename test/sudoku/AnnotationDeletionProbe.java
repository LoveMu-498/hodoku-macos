package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Exercises independent modifiers, partial erasure, box subtraction and all-segment input. */
public final class AnnotationDeletionProbe {
    private static int deletion=SudokuUtil.getDeletionModifierMask();
    public static void main(String[] args)throws Exception {
        final Throwable[] failure=new Throwable[1];
        SwingUtilities.invokeAndWait(()->{
            MainFrame frame=new MainFrame(null);
            try {
                SudokuPanel p=frame.getSudokuPanel();p.setSize(600,600);
                p.setSudoku(CurrentReasoningProbe.PUZZLE);p.setShowCandidates(true);
                p.paint(new BufferedImage(600,600,BufferedImage.TYPE_INT_ARGB).getGraphics());
                require(!SudokuUtil.isMenuShortcutDown(deletion),"delete modifier aliases menu modifier");
                p.setAnnotationTool(AnnotationTool.DOODLE);
                drag(p,new Point(50,100),new Point(550,100),MouseEvent.BUTTON1,0);
                require(p.getDoodleStrokeCount()==1,"pen stroke missing");
                drag(p,new Point(250,50),new Point(350,150),MouseEvent.BUTTON3,deletion);
                require(p.getDoodleStrokeCount()==2,"rectangle erased whole stroke rather than split");
                List<DoodleStroke> cut=doodles(p);
                require(close(cut.get(0).getPoints().get(1).getX(),250.0/600),"left fragment boundary");
                require(close(cut.get(1).getPoints().get(0).getX(),350.0/600),"right fragment boundary");
                p.undoCurrentAnnotation();require(p.getDoodleStrokeCount()==1,"eraser undo");
                p.redoCurrentAnnotation();require(p.getDoodleStrokeCount()==2,"eraser redo");
                p.undoCurrentAnnotation();
                drag(p,new Point(300,80),new Point(300,120),MouseEvent.BUTTON1,deletion);
                require(p.getDoodleStrokeCount()==2,"circular sweep did not split");
                p.undoCurrentAnnotation();require(p.getDoodleStrokeCount()==1,"one circle gesture needs one undo");
                float width=doodles(p).get(0).getWidthFactor();
                drag(p,new Point(200,200),new Point(400,300),MouseEvent.BUTTON3,0);
                DoodleStroke ellipse=doodles(p).get(1);require(ellipse.getPoints().size()>30,"right drag did not draw ellipse");
                require(ellipse.getWidthFactor()==width,"ellipse brush width changed");
                double minX=1,maxX=0,minY=1,maxY=0;
                for(DoodlePoint point:ellipse.getPoints()){minX=Math.min(minX,point.getX());maxX=Math.max(maxX,point.getX());minY=Math.min(minY,point.getY());maxY=Math.max(maxY,point.getY());}
                require(Math.abs(minX*600-200)<1&&Math.abs(maxX*600-400)<1&&Math.abs(minY*600-200)<1&&Math.abs(maxY*600-300)<1,"ellipse bounds");
                int count=p.getDoodleStrokeCount();
                press(p,new Point(250,50),MouseEvent.BUTTON3,deletion);move(p,new Point(350,150),deletion);
                p.handleEscapeVisualReset();release(p,new Point(350,150),MouseEvent.BUTTON3,deletion);
                require(p.getDoodleStrokeCount()==count,"Escape committed rectangle erase");
                p.setAnnotationTool(AnnotationTool.BOX_SELECTION);
                Point a=cell(p,2),b=cell(p,3);
                drag(p,a,a,MouseEvent.BUTTON1,0);p.setActiveBoxReasoningGroup(1);drag(p,b,b,MouseEvent.BUTTON1,0);
                Point low=new Point(Math.min(a.x,b.x)-10,a.y-10),high=new Point(Math.max(a.x,b.x)+10,a.y+10);
                drag(p,low,high,MouseEvent.BUTTON1,deletion);
                require(p.getBoxReasoningFootprint().isEmpty(),"Control did not subtract across groups");
                p.undoCurrentAnnotation();require(p.getBoxReasoningFootprint().size()==2,"box undo");
                p.setAnnotationTool(AnnotationTool.FREE_CHAIN);
                clickNode(p,2,1);clickNode(p,6,1);
                drag(p,new Point(10,10),new Point(10,10),MouseEvent.BUTTON3,InputEvent.SHIFT_DOWN_MASK);
                require(p.getUserChainCount()==1,"right click deleted old segment");
                clickNode(p,6,1);clickNode(p,7,1);
                UserChainAssembly.Result assembled=UserChainAssembly.assemble(p.currentReasoningChains());
                require(assembled.chain!=null&&assembled.chain.getNodes().size()==3,"shared endpoint not assembled");
                Point start=node(p,2,1),end=node(p,6,1);Point middle=new Point((start.x+end.x)/2,(start.y+end.y)/2);
                drag(p,new Point(middle.x-8,middle.y-8),new Point(middle.x+8,middle.y+8),MouseEvent.BUTTON1,deletion);
                require(p.getUserChainCount()==0&&!p.currentReasoningChains().isEmpty(),"segment crossing did not delete whole old chain only");
                p.undoCurrentAnnotation();require(p.getUserChainCount()==1,"chain delete undo");
                verifyAssembly();verifyLoops();
                System.out.println("Annotation deletion checks passed: modifiers, ellipse, circular/rectangular clipping, undo/redo/Escape, cross-group subtraction, crossing-chain delete, all-segment assembly");
            }catch(Throwable t){failure[0]=t;}finally{frame.dispose();}
        });
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}System.exit(0);
    }
    private static void verifyAssembly(){
        UserChain a=segment(11,21,true),b=segment(21,31,false),c=segment(31,41,true),d=segment(41,11,false);
        UserChainAssembly.Result result=UserChainAssembly.assemble(Arrays.asList(c,a,d,b));
        require(result.chain!=null&&result.chain.isClosed()&&result.chain.getStrongRelations().size()==4,"segments did not close into one loop");
        require(UserChainAssembly.assemble(Arrays.asList(a,c)).problem==UserChainValidator.Problem.DISCONNECTED_INPUT,"disconnected input silently selected subset");
        require(UserChainAssembly.assemble(Arrays.asList(a,b,segment(21,51,true))).problem==UserChainValidator.Problem.BRANCHED_INPUT,"branch silently selected subset");
        require(UserChainAssembly.assemble(Arrays.asList(a,segment(11,21,false))).problem==UserChainValidator.Problem.CONFLICTING_EDGE,"conflicting edge ignored");
    }
    private static void verifyLoops() {
        Sudoku2 board=new Sudoku2();board.setSudoku(new String(new char[81]).replace('\0','0'));
        for(int col=0;col<9;col++)if(col!=0&&col!=3){board.setCandidate(col,1,false);board.setCandidate(9+col,1,false);}
        UserChainAssembly.Result loop=UserChainAssembly.assemble(Arrays.asList(segment(1,31,true),segment(31,121,false),segment(121,91,true),segment(91,1,false)));
        UserChainValidator.Result continuous=UserChainValidator.validate(board,loop.chain);
        require(continuous.status==UserChainValidator.Status.PROVEN,"continuous loop no proof");
        boolean removed=false;for(SolutionStep step:continuous.steps){require(step.getType()==SolutionType.CONTINUOUS_NICE_LOOP,"continuous type");for(Candidate c:step.getCandidatesToDelete())if(c.getIndex()==18&&c.getValue()==1)removed=true;}
        require(removed,"continuous loop omitted outside weak-link target");
        board=new Sudoku2();board.setSudoku(new String(new char[81]).replace('\0','0'));
        for(int i=0;i<9;i++){if(i!=0&&i!=1)board.setCandidate(i,1,false);if(i!=0&&i!=1)board.setCandidate(i*9+1,1,false);}
        loop=UserChainAssembly.assemble(Arrays.asList(segment(1,11,true),segment(11,101,true),segment(101,1,false)));
        UserChainValidator.Result discontinuous=UserChainValidator.validate(board,loop.chain);
        require(discontinuous.status==UserChainValidator.Status.PROVEN,"discontinuous loop no proof");
        boolean forces=false;for(SolutionStep step:discontinuous.steps){require(step.getType()==SolutionType.DISCONTINUOUS_NICE_LOOP,"discontinuous type");int count=0;for(Candidate c:step.getCandidatesToDelete())if(c.getIndex()==1&&c.getValue()!=1)count++;if(count==8)forces=true;}
        require(forces,"two strong links did not force discontinuity digit through native deletions");
    }
    private static UserChain segment(int a,int b,boolean strong){UserChain c=new UserChain();c.getNodes().add(new UserChainNode(a/10,a%10));c.getNodes().add(new UserChainNode(b/10,b%10));c.getStrongRelations().add(strong);return c;}
    @SuppressWarnings("unchecked") private static List<DoodleStroke> doodles(SudokuPanel p)throws Exception{Field f=SudokuPanel.class.getDeclaredField("doodleStrokes");f.setAccessible(true);return (List<DoodleStroke>)f.get(p);}
    private static boolean close(double a,double b){return Math.abs(a-b)<1e-6;}
    private static Point cell(SudokuPanel p,int i){int s=p.getX(0,1)-p.getX(0,0);return new Point(p.getX(i/9,i%9)+s/2,p.getY(i/9,i%9)+s/2);}
    private static Point node(SudokuPanel p,int i,int d){int s=p.getX(0,1)-p.getX(0,0);return new Point(p.getX(i/9,i%9)+(int)(((d-1)%3+.5)*s/3),p.getY(i/9,i%9)+(int)(((d-1)/3+.5)*s/3));}
    private static void clickNode(SudokuPanel p,int i,int d){Point q=node(p,i,d);drag(p,q,q,MouseEvent.BUTTON1,0);}
    private static void drag(SudokuPanel p,Point a,Point b,int button,int mods){press(p,a,button,mods);move(p,b,mods);release(p,b,button,mods);}
    private static void press(SudokuPanel p,Point q,int button,int mods){p.dispatchEvent(new MouseEvent(p,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),mods|InputEvent.getMaskForButton(button),q.x,q.y,1,false,button));}
    private static void move(SudokuPanel p,Point q,int mods){p.dispatchEvent(new MouseEvent(p,MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),mods,q.x,q.y,0,false,MouseEvent.NOBUTTON));}
    private static void release(SudokuPanel p,Point q,int button,int mods){p.dispatchEvent(new MouseEvent(p,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),mods,q.x,q.y,1,false,button));}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
