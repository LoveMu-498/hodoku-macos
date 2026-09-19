package sudoku;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

/** Uses the actual native save/savepoint dialogs, including their live preview and cancel route. */
public final class ReplayRetentionNativeProbe {
 static MainFrame f;static ReplayController r;static SudokuPanel p;
 static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void edt(Runnable action)throws Exception{SwingUtilities.invokeAndWait(action);}
 static Object field(Object target,String name){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(Exception e){throw new RuntimeException(e);}}
 static void action(String method){try{Method m=MainFrame.class.getDeclaredMethod(method,java.awt.event.ActionEvent.class);m.setAccessible(true);m.invoke(f,new Object[]{null});}catch(Exception e){throw new RuntimeException(e);}}
 static JOptionPane pane(Container root){for(Component c:root.getComponents()){if(c instanceof JOptionPane)return (JOptionPane)c;if(c instanceof Container){JOptionPane p=pane((Container)c);if(p!=null)return p;}}return null;}
 static javax.swing.Timer dialogTimer(Runnable callback){javax.swing.Timer timer=new javax.swing.Timer(120,e->callback.run());timer.setRepeats(false);timer.start();return timer;}
 static RestoreSavePointDialog restoreDialog(){for(Window w:Window.getWindows())if(w instanceof RestoreSavePointDialog&&w.isVisible())return (RestoreSavePointDialog)w;throw new AssertionError("restore dialog absent");}
 public static void main(String[]args)throws Exception{
  Path data=Files.createTempDirectory("replay-retention-native-");System.setProperty("hodoku.data.dir",data.toString());
  try{
   edt(()->{f=new MainFrame(null);f.setVisible(true);p=f.getSudokuPanel();r=f.getReplayController();f.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");p.setCell(0,2,4);r.afterAction();
    dialogTimer(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isVisible()){JOptionPane pane=pane((Container)w);if(pane!=null){pane.setInputValue("checkpoint");pane.setValue(JOptionPane.OK_OPTION);return;}}throw new AssertionError("savepoint dialog absent");});action("createSavePointMenuItemActionPerformed");
    require(r.session().retained&&r.session().bookmarks().size()==1,"savepoint failed retention/marker");p.setCell(0,3,6);r.afterAction();});
   String retainedId=r.session().id;int frames=r.session().frames().size();ReplayBoard before=new ReplayBoard(p.getSudoku());
   edt(()->{dialogTimer(()->{RestoreSavePointDialog d=restoreDialog();((JTable)field(d,"savePointTable")).setRowSelectionInterval(0,0);require(p.getSudoku().getValue(3)==0,"preview did not restore");require(r.session().frames().size()==frames,"preview recorded");((JButton)field(d,"cancelButton")).doClick();});action("restoreSavePointMenuItemActionPerformed");require(new ReplayBoard(p.getSudoku()).equals(before),"cancel failed original restore");require(r.session().frames().size()==frames,"cancel recorded");});
   edt(()->{dialogTimer(()->{RestoreSavePointDialog d=restoreDialog();((JTable)field(d,"savePointTable")).setRowSelectionInterval(0,0);((JButton)field(d,"okButton")).doClick();});action("restoreSavePointMenuItemActionPerformed");require(r.session().frames().size()==frames+1,"confirm must append exactly one");require(r.session().last().kind.equals("restore-savepoint"),"restore label");f.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");});
   require(ReplayStore.read(r.directory().resolve(retainedId+".hrep")).frames().size()==frames+1,"whole attempt not retained");
   String discardId=r.session().id;edt(()->f.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079"));require(!Files.exists(r.directory().resolve(discardId+".hrep")),"unretained change not discarded");
   edt(()->{try{Method save=MainFrame.class.getDeclaredMethod("saveToFile",boolean.class,String.class,int.class);save.setAccessible(true);save.invoke(f,true,data.resolve("puzzle.txt").toString(),2);}catch(Exception e){throw new RuntimeException(e);}require(r.session().retained,"manual successful save not retained");});
   String beforeHistory=r.session().id;
   edt(()->{dialogTimer(()->{for(Window w:Window.getWindows())if(w instanceof HistoryDialog&&w.isVisible()){JTable table=(JTable)field(w,"historyTable");require(table.getRowCount()>0,"history empty");table.setRowSelectionInterval(0,0);require(r.session().id.equals(beforeHistory),"history preview split attempt");((JButton)field(w,"cancelButton")).doClick();return;}throw new AssertionError("history dialog absent");});action("historyMenuItemActionPerformed");require(r.session().id.equals(beforeHistory),"history cancel split attempt");});
   String failedId=r.session().id;Path library=r.directory(),saved=data.resolve("saved-library");Files.move(library,saved);Files.write(library,new byte[]{0});
   edt(()->{p.setCell(0,2,4);r.retainCurrent();require(r.hasPendingSave(failedId),"failed write not retained in memory");f.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");require(r.hasPendingSave(failedId),"switch lost failed saved attempt");});
   Files.delete(library);Files.move(saved,library);edt(()->r.retrySaves());require(!r.hasPendingSave(failedId),"retry failed");require(ReplayStore.read(library.resolve(failedId+".hrep")).last().board.values()[2]==4,"retry lost last mutation");
   System.out.println("Native savepoint create/preview/cancel/confirm, whole-attempt retention, default discard, manual save, switched pending-write recovery passed: "+data);
  }catch(Throwable e){e.printStackTrace();System.exit(1);}finally{if(f!=null)edt(()->f.dispose());}System.exit(0);
 }
}
