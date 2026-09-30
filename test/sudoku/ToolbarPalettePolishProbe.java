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
                JButton all=(JButton)field(bar,"clearAll"), swap=(JButton)field(bar,"swap");
                JButton state=(JButton)field(bar,"modeState");
                File out=new File(System.getProperty("hodoku.probe.output"));out.mkdirs();
                JToolBar toolbar=(JToolBar)field(f,"jToolBar1");
                for(AnnotationTool tool:new AnnotationTool[]{AnnotationTool.DEFAULT_MOUSE,AnnotationTool.CANDIDATE_COLORING,AnnotationTool.FREE_CHAIN,AnnotationTool.DOODLE,AnnotationTool.BOX_SELECTION}){
                    p.setAnnotationTool(tool);p.updateColorCursor();
                    require(p.getCursor().getType()==Cursor.DEFAULT_CURSOR,"color badge remained in "+tool);
                    require(groups[0].isEnabled()==(tool!=AnnotationTool.DEFAULT_MOUSE),"mode gating "+tool);
                    require(all.isEnabled(),"all-clear unavailable");
                    if(tool==AnnotationTool.DEFAULT_MOUSE){int before=owner.getPaletteGroup();groups[0].doClick();require(before==owner.getPaletteGroup(),"disabled swatch changed group");require(!swap.isEnabled(),"swap active in M");}
                    if(tool==AnnotationTool.FREE_CHAIN){
                        p.setNextUserChainStrong(false);require(state.getToolTipText().contains("双弱 0"),"weak state stale");save(toolbar,new File(out,"weak.png"));
                        p.setNextUserChainStrong(true);require(state.getToolTipText().contains("双强 0"),"strong state stale");
                    }
                    if(tool==AnnotationTool.DOODLE){for(int i=0;i<4;i++){p.setDoodleWidthIndex(i);require(state.getToolTipText().contains((i+1)+" / 4"),"width stale");save(toolbar,new File(out,"width-"+i+".png"));}}
                    f.validate();save(toolbar,new File(out,tool.name()+".png"));
                }
                p.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);
                owner.selectPaletteGroup(1);
                BufferedImage detail=new BufferedImage(bar.getStatusPalette().getWidth()*3,bar.getStatusPalette().getHeight()*3,BufferedImage.TYPE_INT_RGB);
                Graphics2D detailGraphics=detail.createGraphics();
                detailGraphics.setColor(SudokuAppearancePalette.forRendering(false).getSurfaceBackground());
                detailGraphics.fillRect(0,0,detail.getWidth(),detail.getHeight());
                detailGraphics.scale(3,3);bar.getStatusPalette().paint(detailGraphics);detailGraphics.dispose();
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
                Color[] colors=Options.getInstance().getColoringColors();
                p.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);owner.setPrimaryColor(colors[0]);
                require(groups.length==6,"missing sixth group");
                for(int i=0;i<6;i++){
                    groups[i].doClick();require(owner.getPaletteGroup()==i,"bottom click not wired");
                    require(owner.getDisplayedPaletteColor(i).equals(colors[2*i]),"primary projection");
                }
                swap.doClick();
                for(int i=0;i<6;i++)require(owner.getDisplayedPaletteColor(i).equals(colors[2*i+1]),"swap projection");
                p.updateDeletionModifier(new KeyEvent(p,KeyEvent.KEY_PRESSED,0,InputEvent.ALT_DOWN_MASK,KeyEvent.VK_ALT,KeyEvent.CHAR_UNDEFINED));
                for(int i=0;i<6;i++)require(owner.getDisplayedPaletteColor(i).equals(colors[2*i]),"Option projection");
                p.updateDeletionModifier(new KeyEvent(p,KeyEvent.KEY_RELEASED,0,0,KeyEvent.VK_ALT,KeyEvent.CHAR_UNDEFINED));
                require(owner.getDisplayedPaletteColor(5).equals(colors[11]),"Option release stale");
                for(AnnotationTool tool:new AnnotationTool[]{AnnotationTool.FREE_CHAIN,AnnotationTool.BOX_SELECTION}){
                    p.setAnnotationTool(tool);Color beforeSwap=owner.getPrimaryColor();
                    require(!swap.isEnabled(),"fixed pair swap enabled");swap.doClick();
                    require(beforeSwap.equals(owner.getPrimaryColor()),"fixed pair changed");
                }
                p.setAnnotationTool(AnnotationTool.CANDIDATE_COLORING);
                require(owner.getPaletteGroup()==5&&owner.getPrimaryColor().equals(colors[11]),"tool memory lost");
                p.setAnnotationTool(AnnotationTool.DOODLE);
                p.updateDeletionModifier(new KeyEvent(p,KeyEvent.KEY_PRESSED,0,InputEvent.CTRL_DOWN_MASK,KeyEvent.VK_CONTROL,KeyEvent.CHAR_UNDEFINED));
                require(state.getToolTipText().contains("5.0%"),"eraser radius not visible");
                DoodleWheelProbe.wheel(p,InputEvent.CTRL_DOWN_MASK,10000);
                require(state.getToolTipText().contains("6.0%"),"eraser radius stale after wheel");
                save(toolbar,new File(out,"eraser.png"));
                int width=p.getDoodleWidthIndex();state.doClick();require(p.getDoodleWidthIndex()==width,"eraser state changed pen width");
                p.cancelAnnotationToolInteractionOnDeactivation();
                require(!p.isDoodleErasing()&&state.getToolTipText().contains("涂鸦粗细"),"modifier state stuck after deactivation");
                JToggleButton[] tools=(JToggleButton[])field(f,"annotationToolButtons");
                for(JToggleButton tool:tools)if(tool.isVisible())require(tool.getParent()==bar.getParent(),"tool outside shared group");
                require(bar.getComponentCount()==1,"top palette or erasers retained");
                require(all.getParent()==bar.getStatusPalette(),"all clear not in bottom");
                Map<Integer,Color> cells=(Map<Integer,Color>)field(p,"coloringMap");
                List<DoodleStroke> ink=(List<DoodleStroke>)field(p,"doodleStrokes");
                List<UserChain> chains=(List<UserChain>)field(p,"userChains");
                List<SudokuSet> boxes=(List<SudokuSet>)field(p,"boxReasoningGroups");
                cells.put(2,Color.ORANGE);
                DoodleStroke stroke=new DoodleStroke(Color.BLUE,.005f);stroke.getPoints().add(new DoodlePoint(.1,.1));stroke.getPoints().add(new DoodlePoint(.2,.2));ink.add(stroke);
                chains.add(new UserChain());boxes.get(0).add(2);
                String puzzle=p.getSudoku().getSudoku(ClipboardMode.LIBRARY);
                p.setAnnotationTool(AnnotationTool.DOODLE);ToolbarPaletteProbe.key(p,KeyEvent.VK_R,InputEvent.SHIFT_DOWN_MASK);
                require(p.getDoodleStrokeCount()==0&&!cells.isEmpty()&&!chains.isEmpty()&&!p.getBoxReasoningFootprint().isEmpty(),"current clear crossed tool boundary");
                p.undoCurrentAnnotation();require(p.getDoodleStrokeCount()==1,"current clear not undoable");
                p.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);all.doClick();
                require(!p.hasColoring()&&p.getDoodleStrokeCount()==0&&p.getUserChainCount()==0&&p.getBoxReasoningFootprint().isEmpty(),"all clear left annotations");
                require(puzzle.equals(p.getSudoku().getSudoku(ClipboardMode.LIBRARY)),"eraser changed puzzle");
                for(AnnotationTool t:new AnnotationTool[]{AnnotationTool.CANDIDATE_COLORING,AnnotationTool.DOODLE,AnnotationTool.FREE_CHAIN,AnnotationTool.BOX_SELECTION}){p.setAnnotationTool(t);p.undoCurrentAnnotation();}
                require(p.hasColoring()&&p.getDoodleStrokeCount()==1&&p.getUserChainCount()==1&&!p.getBoxReasoningFootprint().isEmpty(),"all clear histories lost");
                for(int w:new int[]{768,1120}){f.setSize(w,850);f.validate();require(bar.getX()+bar.getWidth()<=toolbar.getWidth(),"toolbar clipped");save(f,new File(out,"window-"+w+".png"));save(bar.getStatusPalette(),new File(out,"status-"+w+".png"));}
                System.out.println("PASS: mode dim/disable, paired swatch projection, live chain/width states, current/all eraser isolation and undo, six groups, primary/Option projection, fixed pairs, independent tool memory, eraser diameter, grouped attributes, narrow layout");
            }catch(Throwable t){failure[0]=t;}finally{if(f!=null)f.dispose();}
        });
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}System.exit(0);
    }
}
