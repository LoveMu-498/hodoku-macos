package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Palette projections and clear actions on isolated real Swing controls. */
public final class ToolbarPalettePolishProbe {
    static Object field(Object o,String key) throws Exception { Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o); }
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void save(Component c,File file)throws Exception {
        BufferedImage img=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=img.createGraphics();c.paint(g);g.dispose();ImageIO.write(img,"png",file);
    }
    public static void main(String[] args)throws Exception {
        Throwable[] failure={null};
        SwingUtilities.invokeAndWait(()->{
            MainFrame f=null;
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                ApplicationAppearance.initialize(Boolean.getBoolean("hodoku.probe.dark")?AppearanceMode.DARK:AppearanceMode.LIGHT);
                f=new MainFrame(null);f.setSize(1120,850);f.setVisible(true);
                SudokuPanel p=f.getSudokuPanel();p.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
                CellZoomPanel owner=p.getCellZoomPanel();ToolbarColorPalette bar=owner.getToolbarPalette();
                JToggleButton[] groups=(JToggleButton[])field(bar,"groups");
                JButton current=(JButton)field(bar,"clearCurrent"),all=(JButton)field(bar,"clearAll");
                JLabel state=(JLabel)field(bar,"modeState");
                File out=new File(System.getProperty("hodoku.probe.output"));out.mkdirs();
                JToolBar toolbar=(JToolBar)field(f,"jToolBar1");
                for(AnnotationTool tool:new AnnotationTool[]{AnnotationTool.DEFAULT_MOUSE,AnnotationTool.CANDIDATE_COLORING,AnnotationTool.FREE_CHAIN,AnnotationTool.DOODLE,AnnotationTool.BOX_SELECTION}){
                    p.setAnnotationTool(tool);p.updateColorCursor();
                    require(p.getCursor().getType()==Cursor.DEFAULT_CURSOR,"color badge remained in "+tool);
                    require(groups[0].isEnabled()==(tool!=AnnotationTool.DEFAULT_MOUSE),"mode gating "+tool);
                    require(all.isEnabled(),"all-clear unavailable");
                    if(tool==AnnotationTool.DEFAULT_MOUSE){int before=owner.getPaletteGroup();groups[0].doClick();require(before==owner.getPaletteGroup(),"disabled swatch changed group");require(!current.isEnabled(),"current clear active in M");}
                    if(tool==AnnotationTool.FREE_CHAIN){
                        p.setNextUserChainStrong(false);require(state.getToolTipText().contains("（-）"),"weak state stale");save(toolbar,new File(out,"weak.png"));
                        p.setNextUserChainStrong(true);require(state.getToolTipText().contains("（=）"),"strong state stale");
                    }
                    if(tool==AnnotationTool.DOODLE){for(int i=0;i<4;i++){p.setDoodleWidthIndex(i);require(state.getToolTipText().contains((i+1)+" / 4"),"width stale");save(toolbar,new File(out,"width-"+i+".png"));}}
                    f.validate();save(toolbar,new File(out,tool.name()+".png"));
                }
                p.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);
                owner.selectPaletteGroup(1);
                BufferedImage detail=new BufferedImage(bar.getWidth()*3,bar.getHeight()*3,BufferedImage.TYPE_INT_RGB);
                Graphics2D detailGraphics=detail.createGraphics();
                detailGraphics.setColor(SudokuAppearancePalette.forRendering(false).getSurfaceBackground());
                detailGraphics.fillRect(0,0,detail.getWidth(),detail.getHeight());
                detailGraphics.scale(3,3);bar.paint(detailGraphics);detailGraphics.dispose();
                ImageIO.write(detail,"png",new File(out,"palette-detail.png"));
                for(int key:new int[]{KeyEvent.VK_A,KeyEvent.VK_D,KeyEvent.VK_X,KeyEvent.VK_ALT}){
                    ToolbarPaletteProbe.key(p,key,key==KeyEvent.VK_ALT?InputEvent.ALT_DOWN_MASK:0);
                    require(p.getCursor().getType()==Cursor.DEFAULT_CURSOR,"key restored color badge");
                }
                p.updateDeletionModifier(new KeyEvent(p,KeyEvent.KEY_PRESSED,0,InputEvent.CTRL_DOWN_MASK,KeyEvent.VK_CONTROL,KeyEvent.CHAR_UNDEFINED));
                require(p.getCursor().getType()==Cursor.CUSTOM_CURSOR,"deletion cursor lost");
                p.updateColorCursor();require(p.getCursor().getType()==Cursor.CUSTOM_CURSOR,"palette reset deletion pointer");
                p.updateDeletionModifier(new KeyEvent(p,KeyEvent.KEY_RELEASED,0,0,KeyEvent.VK_CONTROL,KeyEvent.CHAR_UNDEFINED));
                require(p.getCursor().getType()==Cursor.DEFAULT_CURSOR,"deletion restore stale");
                Map<Integer,Color> cells=(Map<Integer,Color>)field(p,"coloringMap");
                List<DoodleStroke> ink=(List<DoodleStroke>)field(p,"doodleStrokes");
                List<UserChain> chains=(List<UserChain>)field(p,"userChains");
                List<SudokuSet> boxes=(List<SudokuSet>)field(p,"boxReasoningGroups");
                cells.put(2,Color.ORANGE);
                DoodleStroke stroke=new DoodleStroke(Color.BLUE,.005f);stroke.getPoints().add(new DoodlePoint(.1,.1));stroke.getPoints().add(new DoodlePoint(.2,.2));ink.add(stroke);
                chains.add(new UserChain());boxes.get(0).add(2);
                String puzzle=p.getSudoku().getSudoku(ClipboardMode.LIBRARY);
                p.setAnnotationTool(AnnotationTool.DOODLE);current.doClick();
                require(p.getDoodleStrokeCount()==0&&!cells.isEmpty()&&!chains.isEmpty()&&!p.getBoxReasoningFootprint().isEmpty(),"current clear crossed tool boundary");
                p.undoCurrentAnnotation();require(p.getDoodleStrokeCount()==1,"current clear not undoable");
                p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);all.doClick();
                require(!p.hasColoring()&&p.getDoodleStrokeCount()==0&&p.getUserChainCount()==0&&p.getBoxReasoningFootprint().isEmpty(),"all clear left annotations");
                require(puzzle.equals(p.getSudoku().getSudoku(ClipboardMode.LIBRARY)),"eraser changed puzzle");
                for(AnnotationTool t:new AnnotationTool[]{AnnotationTool.CANDIDATE_COLORING,AnnotationTool.DOODLE,AnnotationTool.FREE_CHAIN,AnnotationTool.BOX_SELECTION}){p.setAnnotationTool(t);p.undoCurrentAnnotation();}
                require(p.hasColoring()&&p.getDoodleStrokeCount()==1&&p.getUserChainCount()==1&&!p.getBoxReasoningFootprint().isEmpty(),"all clear histories lost");
                for(int w:new int[]{768,1120}){f.setSize(w,850);f.validate();require(bar.getX()+bar.getWidth()<=toolbar.getWidth(),"toolbar clipped");save(f,new File(out,"window-"+w+".png"));}
                System.out.println("PASS: mode dim/disable, paired swatch projection, live chain/width states, current/all eraser isolation and undo, plain base pointer with deletion cursor preserved, narrow layout");
            }catch(Throwable t){failure[0]=t;}finally{if(f!=null)f.dispose();}
        });
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}System.exit(0);
    }
}
