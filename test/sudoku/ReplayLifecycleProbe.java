package sudoku;

import javax.swing.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;

/** Drives actual MainFrame routes with a controlled clock; no sleep/wake or synthetic elapsed waits. */
public final class ReplayLifecycleProbe {
    private static final String PUZZLE="530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    private static final String SOLUTION="534678912672195348198342567859761423426853791713924856961537284287419635345286179";
    static MainFrame frame;static ReplayController r;static SudokuPanel panel;
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    static void edt(Runnable runnable)throws Exception{SwingUtilities.invokeAndWait(runnable);}
    static void action(String name){try{Method method=MainFrame.class.getDeclaredMethod(name,java.awt.event.ActionEvent.class);method.setAccessible(true);method.invoke(frame,new Object[]{null});}catch(Exception e){throw new RuntimeException(e);}}
    public static void main(String[] args)throws Exception{
        System.setProperty("hodoku.data.dir",Files.createTempDirectory("replay-lifecycle-").toString());
        AtomicLong now=new AtomicLong(0),wall=new AtomicLong(100000);
        try{
            edt(()->{frame=new MainFrame(null);frame.setVisible(true);panel=frame.getSudokuPanel();r=frame.getReplayController();r.setClock(new ReplayClock(now::get,wall::get));frame.setPuzzle(PUZZLE);});
            String original=r.session().id;
            edt(()->{now.addAndGet(3000000000L);r.updateElapsed();check(r.session().elapsedMillis==3000,"idle elapsed");frame.setPuzzle(PUZZLE);check(!original.equals(r.session().id),"same puzzle paste must restart");});
            String samePuzzle=r.session().id;
            edt(()->{frame.setPuzzleFromHistory(PUZZLE);check(!samePuzzle.equals(r.session().id),"history must restart");});
            String beforeEditing=r.session().id;
            edt(()->{now.addAndGet(2000000000L);action("spielEditierenMenuItemActionPerformed");check(r.isEditing(),"edit start");now.addAndGet(5000000000L);r.updateElapsed();check(r.session().elapsedMillis==2000,"editing counted");action("spielSpielenMenuItemActionPerformed");check(!r.isEditing(),"edit finish");check(beforeEditing.equals(r.session().id),"unchanged givens restarted");});
            edt(()->{panel.setCell(0,2,4);r.afterAction();check(beforeEditing.equals(r.session().id),"ordinary fill restarted");action("spielEditierenMenuItemActionPerformed");action("spielSpielenMenuItemActionPerformed");check(!beforeEditing.equals(r.session().id),"answer promoted to given must restart");check(r.session().last().board.fixed()[2],"new given lost");});
            edt(()->{frame.setPuzzle(SOLUTION.substring(0,80)+"0");now.addAndGet(4000000000L);panel.setCell(8,8,9);r.afterAction();check(r.session().completed,"correct finish not sealed");check(r.session().frames().size()==2,"final fill lost or duplicated");check(r.session().elapsedMillis==4000,"completion clock wrong");check(r.session().last().board.values()[80]==9,"final state lost");});
            String completed=r.session().id;
            edt(()->{now.addAndGet(8000000000L);panel.undo();r.afterAction();r.updateElapsed();check(r.session().completed&&r.session().elapsedMillis==4000,"sealed timer resumed");check(r.session().frames().size()==2,"post-completion undo recorded");check(completed.equals(r.session().id),"undo created attempt");frame.setPuzzle(PUZZLE);r.openViewer(r.session());now.addAndGet(2000000000L);r.updateElapsed();check(r.session().elapsedMillis==2000,"viewing paused time");r.closeViewer();});
            ReplaySession disk=ReplayStore.read(r.directory().resolve(completed+".hrep"));check(disk.completed&&disk.frames().size()==2,"completion not durable");
            // The reusable monotonic interval seam is tested with explicit pause reasons, not real system sleep.
            ReplayClock clock=new ReplayClock(now::get,wall::get);now.addAndGet(1000000000L);wall.addAndGet(-500000);check(clock.elapsedMillis()==1000,"wall jump affected effective time");clock.pause("sleep");clock.pause("editing");now.addAndGet(3000000000L);clock.resume("sleep");now.addAndGet(1000000000L);check(clock.elapsedMillis()==1000,"nested pause resumed early");clock.resume("editing");now.addAndGet(1000000000L);check(clock.elapsedMillis()==2000,"resume interval wrong");
            System.out.println("Replay lifecycle: native puzzle/history/edit routes, unchanged/changed givens, idle/view clock, final mutation before seal, immutable completed history, real persistence and monotonic pause intervals passed");
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{if(frame!=null)edt(()->frame.dispose());}
        System.exit(0);
    }
}
