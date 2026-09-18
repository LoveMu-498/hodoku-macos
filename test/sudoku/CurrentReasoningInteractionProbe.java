package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Live macOS acceptance for the unified native-style technique menu. */
public final class CurrentReasoningInteractionProbe {
    private static MainFrame frame;
    private static Robot robot;
    public static void main(String[] args) throws Exception {
        robot = new Robot(); robot.setAutoDelay(80);
        try {
            edt(() -> {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                ApplicationAppearance.initialize(Boolean.getBoolean("hodoku.probe.dark") ? AppearanceMode.DARK : AppearanceMode.LIGHT);
                frame = new MainFrame(null); frame.setSize(1120,850); frame.setLocation(40,50); frame.setVisible(true);
                SudokuPanel panel = frame.getSudokuPanel(); panel.setSudoku(CurrentReasoningProbe.PUZZLE); panel.setShowCandidates(true);
                frame.toFront();frame.fixFocus();return null;
            });
            robot.waitForIdle();
            edt(() -> {
                SudokuPanel panel=frame.getSudokuPanel();panel.setShowHintCellValue(1);
                panel.setAnnotationTool(AnnotationTool.BOX_SELECTION);node(panel,2,5);
                panel.setAnnotationTool(AnnotationTool.FREE_CHAIN);node(panel,2,1);node(panel,6,1);
                panel.setAnnotationTool(AnnotationTool.DEFAULT_MOUSE);
                require(panel.getBoxReasoningFootprint().contains(2) && !panel.currentReasoningChains().isEmpty(),"annotation fixture missing");
                return null;
            });
            String original = edt(() -> TechniqueStepCatalog.createSignature(frame.getSudokuPanel().getSudoku()));
            JButton entry = edt(() -> (JButton)read(frame,"selectTechniqueToggleButton"));
            click(entry);
            await(() -> edt(() -> ordinaryList() != null),"ordinary mouse menu missing");
            edt(() -> {
                JPopupMenu popup = popup(); JList<?> list = ordinaryList();
                require(read(list.getModel().getElementAt(0),"type") == null,"ordinary default choice removed");
                require(!component(popup,"techniqueModeSwitch",JCheckBox.class).isSelected(),"ordinary mode wrong");
                require(component(frame,"currentReasoningToolbarButton",JButton.class)==null,"second toolbar menu remains");
                render(popup,"ordinary-menu.png");return null;
            });
            // Switching mode must not commit the ordinary row highlighted during browsing.
            edt(() -> { ordinaryList().setSelectedIndex(1);return null; });
            click(edt(() -> component(popup(),"techniqueModeSwitch",JCheckBox.class)));
            await(() -> edt(() -> current()!=null && current().isVisible() && current().selector()!=null),"mode switch did not open current reasoning");
            edt(() -> {
                require(read(frame,"selectedHintStep")==null,"mode switching committed an ordinary selection");
                CurrentReasoningMenu menu=current();
                require(component(menu,"reasoningScopes",JPanel.class)==null && component(menu,"reasoningChain",JComboBox.class)==null,"scope/group controls remain");
                JList<?> list=component(menu,"reasoningTechniques",JList.class); int instances=0,related=0;
                for(int i=0;i<list.getModel().getSize();i++) {
                    java.util.List<?> candidates=(java.util.List<?>)read(list.getModel().getElementAt(i),"instances");instances+=candidates.size();
                    for(Object c:candidates) if(menu.relatedMask((SolutionStep)read(c,"step"))!=0)related++;
                }
                require(instances>1000 && related>0 && instances>related,"current mode filtered the whole-board catalog");
                require(component(menu,"reasoningLegend",JPanel.class)!=null,"source legend missing");
                require(menu.badgeIcon(15).getIconWidth()==60,"overlapping sources did not expose four visible dots");
                Sudoku2 relationBoard=new Sudoku2();relationBoard.setSudoku(new String(new char[81]).replace('\0','0'));
                SolutionStep pair=new SolutionStep(SolutionType.NAKED_PAIR);pair.addIndex(1);pair.addIndex(2);pair.addValue(2);pair.addValue(3);
                int relationMask=CurrentReasoningMenu.relationMask(pair,relationBoard,
                        new java.util.HashSet<Integer>(java.util.Arrays.asList(1,70)),
                        new java.util.HashSet<Integer>(java.util.Arrays.asList(22,701)),
                        new java.util.HashSet<Integer>(java.util.Arrays.asList(2)),0);
                require(relationMask==11,"partial overlapping box/chain/selection sources disappeared");
                String dots=menu.badges(7);require(dots.contains("●")&&!dots.contains("框选")&&!dots.contains("画链"),"badges still contain scope words");
                list.setSelectedIndex(0);list.requestFocusInWindow();render(menu,"current-menu.png");return null;
            });
            robot.keyPress(KeyEvent.VK_ALT);robot.waitForIdle();
            await(() -> edt(() -> SwingUtilities.getWindowAncestor(current()).getOpacity()<0.5f),"Option did not make the real popup translucent");
            edt(() -> {require(frame.getSudokuPanel().hasTechniquePreviewCells(),"Option lost instance preview");return null;});
            capture("option-transparent.png");
            robot.keyRelease(KeyEvent.VK_ALT);robot.waitForIdle();
            await(() -> edt(() -> SwingUtilities.getWindowAncestor(current()).getOpacity()==1f),"Option release did not restore opacity");
            capture("option-restored.png");
            key(KeyEvent.VK_ESCAPE);
            require(original.equals(edt(() -> TechniqueStepCatalog.createSignature(frame.getSudokuPanel().getSudoku()))),"cancel changed board");
            openCurrent();
            edt(() -> {component(current(),"reasoningTechniques",JList.class).requestFocusInWindow();return null;});
            key(KeyEvent.VK_ENTER); // original multi-instance navigation
            await(() -> edt(() -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof JTextField),"instance field did not receive focus");
            if(edt(() -> current().isVisible())) {
                robot.keyPress(KeyEvent.VK_ENTER);robot.waitForIdle();
            } else throw new AssertionError("fixture expected a multi-instance row");
            await(() -> edt(() -> !current().isVisible() && frame.getSudokuPanel().getStep()!=null),"instance confirmation did not preview");
            robot.keyPress(KeyEvent.VK_ENTER);robot.waitForIdle();
            require(original.equals(edt(() -> TechniqueStepCatalog.createSignature(frame.getSudokuPanel().getSudoku()))),"held Enter crossed apply boundary");
            robot.keyRelease(KeyEvent.VK_ENTER);robot.waitForIdle();
            key(KeyEvent.VK_F12);
            edt(() -> {require(read(frame.getSudokuPanel(),"reasoningProposal")!=null,"F12 lost source verification");return null;});
            key(KeyEvent.VK_ENTER);
            await(() -> edt(() -> frame.getSudokuPanel().getStep()==null && frame.getSudokuPanel().undoPossible()),"confirmed native step did not apply");
            edt(() -> {
                require(frame.getSudokuPanel().getBoxReasoningFootprint().contains(2) && !frame.getSudokuPanel().currentReasoningChains().isEmpty(),"whole-board apply consumed unrelated input");
                frame.getSudokuPanel().undo();return null;
            });
            require(original.equals(edt(() -> TechniqueStepCatalog.createSignature(frame.getSudokuPanel().getSudoku()))),"undo did not restore board");
            openCurrent();
            edt(() -> {frame.getSudokuPanel().setShowHintCellValue(2);return null;});
            await(() -> edt(() -> !current().selector().isEnabled()),"stale selection remained enabled");
            key(KeyEvent.VK_ESCAPE);
            openCurrent();
            click(edt(() -> component(current(),"techniqueModeSwitch",JCheckBox.class)));
            await(() -> edt(() -> ordinaryList()!=null),"switch back to ordinary menu failed");
            robot.keyPress(KeyEvent.VK_ALT);robot.waitForIdle();
            await(() -> edt(() -> SwingUtilities.getWindowAncestor(popup()).getOpacity()<0.5f),"ordinary menu did not share opacity behavior");
            robot.keyRelease(KeyEvent.VK_ALT);key(KeyEvent.VK_ESCAPE);
            System.out.println("Unified menu checks passed: one mouse entry, bidirectional mode switch, whole-board colored dots, native popup opacity, Option preview, cancel, Enter barrier, apply/undo and stale input");
        } catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
        finally {if(frame!=null)edt(() -> {frame.dispose();return null;});}
        System.exit(0);
    }
    private static void openCurrent() throws Exception {
        edt(() -> {frame.toFront();frame.fixFocus();return null;});robot.keyPress(KeyEvent.VK_SHIFT);key(KeyEvent.VK_F12);robot.keyRelease(KeyEvent.VK_SHIFT);
        await(() -> edt(() -> current()!=null && current().isVisible() && current().selector()!=null),"Shift+F12 did not select current mode");
    }
    private static CurrentReasoningMenu current()throws Exception{return (CurrentReasoningMenu)read(frame,"currentReasoningMenu");}
    private static JPopupMenu popup()throws Exception{return (JPopupMenu)read(frame,"techniqueSelectorPopup");}
    private static JList<?> ordinaryList()throws Exception{JPopupMenu p=popup();return p==null || p instanceof CurrentReasoningMenu ?null:component(p,"techniqueList",JList.class);}
    private static void click(Component c)throws Exception{Point p=edt(() -> {Point v=c.getLocationOnScreen();v.translate(c.getWidth()/2,c.getHeight()/2);return v;});robot.mouseMove(p.x,p.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();}
    private static void key(int k){robot.keyPress(k);robot.keyRelease(k);robot.waitForIdle();}
    private static void node(SudokuPanel p,int cell,int d){int size=p.getX(0,1)-p.getX(0,0);double third=size/3.0;int x=(int)Math.round(p.getX(cell/9,cell%9)+((d-1)%3+0.5)*third),y=(int)Math.round(p.getY(cell/9,cell%9)+((d-1)/3+0.5)*third);p.dispatchEvent(new MouseEvent(p,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,x,y,1,false,MouseEvent.BUTTON1));p.dispatchEvent(new MouseEvent(p,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,x,y,1,false,MouseEvent.BUTTON1));}
    private static Object read(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static <T extends Component>T component(Container root,String name,Class<T> type){for(Component c:root.getComponents()){if(name.equals(c.getName()))return type.cast(c);if(c instanceof Container){T found=component((Container)c,name,type);if(found!=null)return found;}}return null;}
    private static void render(Component c,String name)throws Exception{BufferedImage i=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=i.createGraphics();c.paint(g);g.dispose();save(i,name);}
    private static void capture(String name)throws Exception{Rectangle r=edt(() -> {Rectangle a=frame.getBounds();JPopupMenu p=popup();a.add(new Rectangle(p.getLocationOnScreen(),p.getSize()));return a;});save(robot.createScreenCapture(r),name);}
    private static void save(BufferedImage i,String n)throws Exception{File dir=new File(System.getProperty("hodoku.probe.output","/tmp/hodoku-unified-build/evidence"));dir.mkdirs();ImageIO.write(i,"png",new File(dir,(ApplicationAppearance.isDark()?"dark-":"light-")+n));}
    private static void require(boolean ok,String m){if(!ok)throw new AssertionError(m);}
    private static void await(Callable<Boolean> c,String m)throws Exception{long end=System.currentTimeMillis()+20000;while(System.currentTimeMillis()<end){if(c.call())return;Thread.sleep(50);}throw new AssertionError(m);}
    private static <T>T edt(Callable<T> c)throws Exception{java.util.concurrent.FutureTask<T> t=new java.util.concurrent.FutureTask<T>(c);SwingUtilities.invokeAndWait(t);return t.get();}
}
