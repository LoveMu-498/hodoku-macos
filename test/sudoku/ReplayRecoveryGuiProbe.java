package sudoku;
import javax.swing.*;
import java.nio.file.*;
import java.lang.reflect.*;
public final class ReplayRecoveryGuiProbe {
 static MainFrame frame;
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] a)throws Exception{
  Path dir=Paths.get(a[0]);System.setProperty("hodoku.data.dir",dir.toString());
  if(a.length==1){
   for(String mode:new String[]{"write","restore","editwrite","editrestore"}){
    Process p=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED","-cp",System.getProperty("java.class.path"),ReplayRecoveryGuiProbe.class.getName(),dir.toString(),mode).inheritIO().start();
    check(p.waitFor()==(mode.equals("write")?7:0),"GUI child failed "+mode);
   }System.out.println("PASS actual MainFrame crash/relaunch restores active identity, board, candidates, elapsed and native clean checkpoint");return;
  }
  SwingUtilities.invokeAndWait(()->{
   try{
    frame=new MainFrame(null);frame.setVisible(true);ReplayController r=frame.getReplayController();
    ReplaySleepMonitor monitor=new ReplaySleepMonitor();final int[] notifications={0};check(monitor.install(()->notifications[0]++),"platform sleep listener unsupported");
    Field listenerField=ReplaySleepMonitor.class.getDeclaredField("listener");listenerField.setAccessible(true);Object listener=listenerField.get(monitor);
    Class<?> sleep=Class.forName("java.awt.desktop.SystemSleepListener"),event=Class.forName("java.awt.desktop.SystemSleepEvent");
    sleep.getMethod("systemAboutToSleep",event).invoke(listener,new Object[]{null});sleep.getMethod("systemAwoke",event).invoke(listener,new Object[]{null});monitor.close();
    SwingUtilities.invokeLater(()->check(notifications[0]==2,"notification dispatch"));
    if(a[1].equals("write")){
     frame.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");frame.getSudokuPanel().setCell(0,2,4);r.afterAction();r.checkpoint(false);Files.write(dir.resolve("expected-id"),r.session().id.getBytes("UTF-8"));
    }else if(a[1].equals("editwrite")){
     frame.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
     Method edit=MainFrame.class.getDeclaredMethod("spielEditierenMenuItemActionPerformed",java.awt.event.ActionEvent.class);edit.setAccessible(true);edit.invoke(frame,new Object[]{null});
     frame.getSudokuPanel().setCell(0,2,4);Files.write(dir.resolve("edit-id"),r.session().id.getBytes("UTF-8"));
     Method save=MainFrame.class.getDeclaredMethod("saveApplicationState");save.setAccessible(true);save.invoke(frame);frame.dispose();
    }else if(a[1].equals("editrestore")){
     String original=new String(Files.readAllBytes(dir.resolve("edit-id")),"UTF-8");check(original.equals(r.session().id),"edit lost identity");check(r.isEditing()&&frame.isInputMode(),"edit pause/mode lost");check(frame.getSudokuPanel().getSudoku().getValue(2)==4,"editing draft lost");check(r.session().last().board.values()[2]==0,"editing counted as solving");
     Method play=MainFrame.class.getDeclaredMethod("spielSpielenMenuItemActionPerformed",java.awt.event.ActionEvent.class);play.setAccessible(true);play.invoke(frame,new Object[]{null});check(!original.equals(r.session().id),"changed draft did not start new attempt");frame.dispose();
    }else{
     check(r.session().id.equals(new String(Files.readAllBytes(dir.resolve("expected-id")),"UTF-8")),"identity changed");check(frame.getSudokuPanel().getSudoku().getValue(2)==4,"live board not restored");check(new ReplayBoard(frame.getSudokuPanel().getSudoku()).equals(r.session().last().board),"live candidates mismatch");check(!r.session().interruption.isEmpty(),"abnormal not reported");
     Method save=MainFrame.class.getDeclaredMethod("saveApplicationState");save.setAccessible(true);save.invoke(frame);ReplaySession clean=new ReplayRecovery(r.directory()).restore();check(clean!=null,"clean recovery missing");r.cancelQuit();frame.dispose();
    }
   }catch(Exception e){throw new RuntimeException(e);}
  });
  SwingUtilities.invokeAndWait(()->{});
  if(a[1].equals("write"))Runtime.getRuntime().halt(7);System.exit(0);
 }
}
