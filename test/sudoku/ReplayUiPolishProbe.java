/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Native menu/import dialogs, real double-click pinning, selection identity and transport states. */
public final class ReplayUiPolishProbe {
    static MainFrame owner;static ReplayController controller;static ReplayLibraryDialog library;static Robot robot;static Path output;static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void edt(Runnable action)throws Exception{SwingUtilities.invokeAndWait(action);}
    static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new RuntimeException(e);}}
    static <T> T find(Container root,Class<T> type){for(Component c:root.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container){T nested=find((Container)c,type);if(nested!=null)return nested;}}return null;}
    static void capture(Component c,String name)throws Exception{BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();c.paint(g);g.dispose();ImageIO.write(image,"png",output.resolve(name+".png").toFile());}
    static javax.swing.Timer chooser(boolean approve,Path file){javax.swing.Timer t=new javax.swing.Timer(100,e->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing()){JFileChooser c=find((Container)w,JFileChooser.class);if(c!=null){if(approve){c.setSelectedFile(file.toFile());c.approveSelection();}else c.cancelSelection();((javax.swing.Timer)e.getSource()).stop();return;}}});t.start();return t;}
    static void clickPin(JTable table,int row,boolean twice)throws Exception{
        Point[] point={null};edt(()->{Rectangle cell=table.getCellRect(row,5,true);point[0]=table.getLocationOnScreen();point[0].translate(cell.x+cell.width/2,cell.y+cell.height/2);});
        robot.mouseMove(point[0].x,point[0].y);for(int i=0;i<(twice?2:1);i++){robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);if(i==0&&twice)Thread.sleep(70);}robot.waitForIdle();
    }
    static void nativeMenu(String title,String entry)throws Exception{
        String pid=java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        String script="on run argv\ntell application \"System Events\"\nset p to first application process whose unix id is (item 1 of argv as integer)\ntell p\nclick menu bar item (item 2 of argv) of menu bar 1\ndelay 0.15\nclick menu item (item 3 of argv) of menu 1 of menu bar item (item 2 of argv) of menu bar 1\nend tell\nend tell\nend run";
        Process process=new ProcessBuilder("/usr/bin/osascript","-e",script,pid,title,entry).inheritIO().start();
        check(process.waitFor()==0,"native File menu action failed");
    }
    static boolean waitPinned(String id,boolean expected)throws Exception{
        long deadline=System.nanoTime()+2000000000L;
        do{if(ReplayStore.read(controller.directory().resolve(id+".hrep")).pinned==expected)return true;Thread.sleep(20);}while(System.nanoTime()<deadline);
        return false;
    }
    public static void main(String[] args)throws Exception{
        output=Files.createDirectories(Paths.get(args[0]));System.setProperty("hodoku.data.dir",output.resolve("data").toString());Locale.setDefault(Locale.SIMPLIFIED_CHINESE);MacOSApplication.setStartupProperties("HoDoKu UI Probe",AppearanceMode.LIGHT);robot=new Robot();robot.setAutoDelay(35);
        try{
            edt(()->{owner=new MainFrame(null);owner.setSize(1060,880);owner.setVisible(true);controller=owner.getReplayController();
                ReplayBoard board=ReplayWindowsV3Probe.board();for(int i=0;i<3;i++){ReplaySession s=new ReplaySession(board,1000+i*1000);s.retained=true;s.endedAt=5000+i;s.elapsedMillis=12345+i*60000;try{ReplayStore.write(controller.library().path(s),s);}catch(Exception e){throw new RuntimeException(e);}}
                library=new ReplayLibraryDialog(controller);
            });
            SwingUtilities.invokeLater(()->library.setVisible(true));robot.waitForIdle();
            String pid=java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            check(new ProcessBuilder("/usr/bin/osascript","-e","use framework \"AppKit\"\non run argv\nset a to current application's NSRunningApplication's runningApplicationWithProcessIdentifier:((item 1 of argv) as integer)\na's activateWithOptions:3\nend run",pid).start().waitFor()==0,"isolated native activation failed");Thread.sleep(250);
            JTable table=(JTable)field(library,"table");String[] id={null};
            edt(()->{table.setRowSelectionInterval(2,2);try{Method selected=ReplayLibraryDialog.class.getDeclaredMethod("selected");selected.setAccessible(true);id[0]=((ReplaySession)selected.invoke(library)).id;}catch(Exception e){throw new RuntimeException(e);}check(table.getRowHeight()>=30,"dense rows unchanged");check(!table.isCellEditable(2,5),"pin edits on a single click");captureSafe(library,"library-before-pin");});
            Object multiClick=Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");long separateClick=multiClick instanceof Number?((Number)multiClick).longValue()+100:600;
            clickPin(table,2,false);Thread.sleep(separateClick);check(!ReplayStore.read(controller.directory().resolve(id[0]+".hrep")).pinned,"single click toggled pin");
            clickPin(table,2,true);check(waitPinned(id[0],true),"real double-click did not persist pin");
            edt(()->{check(table.getSelectedRow()==2,"pin reset selected row");check(Boolean.TRUE.equals(table.getValueAt(2,5)),"pin indicator not refreshed");captureSafe(library,"library-pinned");});
            edt(()->{javax.swing.Timer t=new javax.swing.Timer(100,e->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing()){JOptionPane p=find((Container)w,JOptionPane.class);if(p!=null){p.setValue(JOptionPane.YES_OPTION);((javax.swing.Timer)e.getSource()).stop();return;}}});t.start();});
            Thread.sleep(separateClick);clickPin(table,2,true);check(waitPinned(id[0],false),"double-click unpin failed");
            edt(()->{check(table.getSelectedRow()==2,"unpin lost selected record");table.clearSelection();check(!((JButton)field(library,"view")).isEnabled()&&!((JButton)field(library,"pin")).isEnabled(),"empty selection actions active");table.setRowSelectionInterval(2,2);javax.swing.Timer t=chooser(false,null);((JButton)field(library,"open")).doClick();t.stop();check(library.isShowing()&&table.getSelectedRow()==2,"cancel import closed history or lost selection");library.dispose();});
            ReplaySession sample=new ReplaySession(ReplayWindowsV3Probe.board(),100);Sudoku2 after=sample.last().board.toSudoku();after.setCell(2,4);sample.append(Collections.singletonList(new ReplayFrame(1,1100,1000,"manual","R1C3=4",new ReplayBoard(after),null)));sample.elapsedMillis=1000;Path input=output.resolve("external.hrep");ReplayStore.write(input,sample);
            edt(()->{JMenuItem open=(JMenuItem)field(owner,"openReplayMenuItem");JMenu files=(JMenu)field(owner,"fileMenu");check(open.getParent()==files.getPopupMenu(),"open replay not in File menu");javax.swing.Timer t=chooser(true,input);open.doClick();t.stop();check(controller.isViewing()&&controller.viewer().session().id.equals(sample.id),"File import did not open selected replay");
                ReplayViewer viewer=controller.viewer();JButton play=(JButton)field(viewer,"play"),next=(JButton)field(viewer,"next");check(play.getText().equals(ReplayText.text("play"))&&next.getText().isEmpty(),"play/frame remain ambiguous");check(open.isEnabled()&&files.isEnabled(),"safe File entry disabled in replay");check(!((JMenuItem)field(owner,"newMenuItem")).isEnabled(),"board-edit menu re-enabled");
                javax.swing.Timer cancel=chooser(false,null);open.doClick();cancel.stop();check(controller.viewer()==viewer,"cancel replaced current replay");viewer.setPlaying(true);check(play.getText().equals(ReplayText.text("pause")),"pause label stale");viewer.setPlaying(false);check(play.getText().equals(ReplayText.text("play")),"play label stale after pause");
            });
            javax.swing.Timer[] nativeCancel={null};String[] menuLabels={null,null};
            edt(()->{menuLabels[0]=((JMenu)field(owner,"fileMenu")).getText();menuLabels[1]=((JMenuItem)field(owner,"openReplayMenuItem")).getText();nativeCancel[0]=chooser(false,null);});
            nativeMenu(menuLabels[0],menuLabels[1]);robot.waitForIdle();edt(()->{nativeCancel[0].stop();check(controller.isViewing(),"native File cancel closed viewer");});
            for(int width:new int[]{980,680}){
                edt(()->{owner.setSize(width+30,860);owner.validate();controller.viewer().showFrame(0);});robot.waitForIdle();Thread.sleep(200);
                edt(()->{ReplayViewer viewer=controller.viewer();JButton play=(JButton)field(viewer,"play"),next=(JButton)field(viewer,"next");captureSafe(viewer,"viewer-"+width);check(viewer.getWidth()<=width+30,"resize not applied");check(play.getWidth()>next.getWidth(),"primary playback control indistinct");JLabel title=(JLabel)field(viewer,"frameTitle");check(title.getHeight()>=title.getPreferredSize().height&&title.getY()>=0,"wrapped toolbar clipped frame title: width="+width+" title="+title.getBounds()+" preferred="+title.getPreferredSize()+" header="+title.getParent().getBounds()+" preferred="+title.getParent().getPreferredSize()+" actions="+viewer.actionBar().getBounds()+" preferred="+viewer.actionBar().getPreferredSize());for(Component c:viewer.actionBar().getComponents())check(c.getX()>=0&&c.getX()+c.getWidth()<=viewer.actionBar().getWidth(),"toolbar control clipped");});
            }
            edt(()->{
                controller.closeViewer();check(((JMenuItem)field(owner,"newMenuItem")).isEnabled(),"menu not restored");
                library=new ReplayLibraryDialog(controller);javax.swing.Timer approve=chooser(true,input);((JButton)field(library,"open")).doClick();approve.stop();check(!library.isDisplayable()&&controller.isViewing()&&controller.viewer().session().id.equals(sample.id),"history import stayed modal or failed");controller.closeViewer();library.dispose();
            });
            System.out.println("PASS "+checks+" replay UI checks: File import/cancel and readonly safe menu, native double-click pin/unpin persistence, row identity, library import/cancel, play/pause distinction and narrow layout");
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{edt(()->{if(library!=null)library.dispose();if(owner!=null)owner.dispose();});}System.exit(0);
    }
    static void captureSafe(Component c,String name){try{capture(c,name);}catch(Exception e){throw new RuntimeException(e);}}
}
