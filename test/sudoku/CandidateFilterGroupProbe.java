package sudoku;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;
import javax.swing.*;
public final class CandidateFilterGroupProbe {
 static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static BufferedImage icon(JToggleButton b){Icon i=b.getIcon();BufferedImage image=new BufferedImage(i.getIconWidth(),i.getIconHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();i.paintIcon(b,g,0,0);g.dispose();return image;}
 static long opacity(BufferedImage image){long a=0;for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)a+=image.getRGB(x,y)>>>24;return a;}
 static void save(Component c,File out)throws Exception{BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();c.paint(g);g.dispose();ImageIO.write(image,"png",out);}
 public static void main(String[] args)throws Exception{
  Throwable[] failure={null};SwingUtilities.invokeAndWait(()->{MainFrame f=null;try{
   UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());ApplicationAppearance.initialize(Boolean.getBoolean("hodoku.probe.dark")?AppearanceMode.DARK:AppearanceMode.LIGHT);
   f=new MainFrame(null);f.setSize(1200,850);f.setVisible(true);SudokuPanel p=f.getSudokuPanel();p.setSudoku(CurrentReasoningProbe.PUZZLE);p.setShowCandidates(true);f.check();
   JToggleButton[] digits=(JToggleButton[])field(f,"toggleButtons");JToggleButton nine=digits[8];Container group=nine.getParent();
   check(group.getComponentCount()==12,"filter group does not contain exactly red/green, 1-9, XY/XYZ");
   for(int i=0;i<9;i++)check(digits[i].getParent()==group,"digit escaped group");
   for(String name:new String[]{"redGreenToggleButton","fxyToggleButton","fxyzToggleButton"})check(((Component)field(f,name)).getParent()==group,"filter escaped group");
   long normal=opacity(icon(nine));int last=-1;String solution="534678912672195348198342567859761423426853791713924856961537284287419635345286179";
   for(int cell=0;cell<81;cell++)if(solution.charAt(cell)=='9'&&p.getSudoku().getValue(cell)==0){p.setCell(cell/9,cell%9,9);last=cell;}
   f.check();check(Boolean.TRUE.equals(nine.getClientProperty("candidateComplete")),"nine not marked complete");
   check(nine.getIcon() instanceof DimmedCandidateIcon,"nine not dimmed");check(opacity(icon(nine))<normal*.6,"completed glyph not sufficiently dimmed");
   nine.doClick();check(nine.isSelected()&&p.getShowHintCellValues()[9],"completed digit lost selection/filter interaction");
   p.setShowCandidates(false);f.check();check(Boolean.TRUE.equals(nine.getClientProperty("candidateComplete")),"hidden candidates lost completion");check(!(digits[0].getIcon() instanceof DimmedCandidateIcon),"hiding candidates dimmed unfinished digit");p.setShowCandidates(true);f.check();
   p.saveState();p.setCell(last/9,last%9,0);f.check();check(!Boolean.TRUE.equals(nine.getClientProperty("candidateComplete")),"deletion left completion mark");
   p.undo();f.check();check(Boolean.TRUE.equals(nine.getClientProperty("candidateComplete")),"undo did not restore completion");p.redo();f.check();check(!Boolean.TRUE.equals(nine.getClientProperty("candidateComplete")),"redo left stale completion");p.undo();f.check();
   JToggleButton xy=(JToggleButton)field(f,"fxyToggleButton"),xyz=(JToggleButton)field(f,"fxyzToggleButton");xy.doClick();xyz.doClick();check(p.isBivalueFilterActive()&&p.isTrivalueFilterActive(),"XY/XYZ actions changed");
   File out=new File(System.getProperty("hodoku.probe.output"));out.mkdirs();JToolBar toolbar=(JToolBar)field(f,"jToolBar1");
   for(int width:new int[]{760,1000,1400}){f.setSize(width,850);f.validate();for(Component c:toolbar.getComponents())if(c.isVisible())check(c.getX()+c.getWidth()<=toolbar.getWidth()&&c.getY()+c.getHeight()<=toolbar.getHeight(),"toolbar clipped at "+width);save(toolbar,new File(out,"toolbar-"+width+".png"));}
   Options.getInstance().setShowColorKuAct(true);p.setShowColorKu();f.check();check(nine.getIcon() instanceof DimmedCandidateIcon,"ColorKu completion missing");save(group,new File(out,"colorku.png"));
   System.out.println("PASS: 12-control grouping, completed dim/badge, selected completed filter, candidate visibility, deletion/undo/redo, XY/XYZ, ColorKu, 760/1000/1400 layout");
  }catch(Throwable t){failure[0]=t;}finally{if(f!=null)f.dispose();}});if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}System.exit(0);
 }
}
