package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.*;

/** Continuous clock, frame boundaries, pause/resume and real native reference press/release. */
public final class ReplayPlaybackHintProbe {
    static MainFrame frame;static ReplayViewer view;static ReplayController controller;
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void edt(Runnable task)throws Exception{SwingUtilities.invokeAndWait(task);}
    public static void run(MainFrame target,Path output)throws Exception{
        frame=target;controller=frame.getReplayController();AtomicLong nanos=new AtomicLong();
        final ReplayBoard[] live={null};final String[] hint={null};
        edt(()->{
            frame.setSize(1000,850);frame.setVisible(true);frame.toFront();
            frame.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
            live[0]=new ReplayBoard(frame.getSudokuPanel().getSudoku());hint[0]=frame.getHintTextArea().getText();
            ReplaySession s=new ReplaySession(live[0],1);
            s.append(Arrays.asList(new ReplayFrame(1,5001,5000,"manual","观察 R1C2",live[0],null),new ReplayFrame(1,5001,5000,"manual","同一时刻 R1C3",live[0],null)));s.append(Collections.singletonList(new ReplayFrame(2,9001,9000,"manual","最后操作 R2C3",live[0],null)));s.elapsedMillis=10000;
            view=new ReplayViewer(controller,s,nanos::get);
            view.setPlaying(true);nanos.addAndGet(2500000000L);view.advancePlayback();
            require(view.playbackMillis()==2500&&view.frameIndex()==0&&view.timeline().getValue()==250000,"clock/slider frozen between events");
            try{java.lang.reflect.Field position=ReplayViewer.class.getDeclaredField("position");position.setAccessible(true);require(((JLabel)position.get(view)).getText().contains("00:00:02 / 00:00:10"),"visible current/total clock not refreshed");}catch(Exception e){throw new RuntimeException(e);}
            view.setPlaying(false);nanos.addAndGet(200000000000L);view.advancePlayback();require(view.playbackMillis()==2500,"pause counted wall gap");
            view.setPlaying(true);nanos.addAndGet(2500000000L);view.advancePlayback();require(view.frameIndex()==1&&view.playbackMillis()==5000,"resume restarted interval");
            nanos.addAndGet(600000000L);view.advancePlayback();require(view.frameIndex()==1,"same-time proof skipped");nanos.addAndGet(100000000L);view.advancePlayback();require(view.frameIndex()==2&&view.playbackMillis()==5000,"same-time ordering/time wrong");
            nanos.addAndGet(4000000000L);view.advancePlayback();require(view.frameIndex()==3&&view.isPlaying(),"last operation discarded idle tail");
            nanos.addAndGet(1000000000L);view.advancePlayback();require(!view.isPlaying()&&view.playbackMillis()==10000&&view.timeline().getValue()==1000000,"total duration/end wrong");
            view.setPlaying(true);require(view.frameIndex()==0&&view.playbackMillis()==0,"play at end did not restart");view.setPlaying(false);view.showFrame(1);require(view.playbackMillis()==5000,"step did not seek to operation");view.disposeViewer();
            try{
                SolutionStep step=frame.getSudokuPanel().getSolver().getHint(live[0].toSudoku(),false);require(step!=null,"no native proof");
                ReplaySession proof=new ReplaySession(live[0],1);proof.append(Arrays.asList(new ReplayFrame(1,2,5000,"proof","证明",live[0],ReplayProof.encode(step)),new ReplayFrame(1,2,5000,"apply","应用",live[0],null)));proof.elapsedMillis=8000;
                controller.openViewer(proof);view=controller.viewer();view.showFrame(1);String text=view.hintPanel().getText();require(text.equals(step.toString(2))&&!view.hintPanel().getReferences().isEmpty(),"native hint text/reference parsing not reused");view.showFrame(2);require(text.equals(view.hintPanel().getText()),"application lost prior proof explanation");
                view.hintPanel().setReferenceText("观察 R1C2，按住坐标查看对应单元格。");
            }catch(Exception e){throw new RuntimeException(e);}
        });
        String pid=java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        Process activate=new ProcessBuilder("/usr/bin/osascript","-e","use framework \"AppKit\"\non run argv\nset targetApp to current application's NSRunningApplication's runningApplicationWithProcessIdentifier:((item 1 of argv) as integer)\ntargetApp's activateWithOptions:3\nend run",pid).start();require(activate.waitFor()==0,"test window activation failed");
        Robot robot=new Robot();robot.setAutoDelay(70);Thread.sleep(300);robot.waitForIdle();final Point[] ref={null};
        edt(()->{try{HintTextArea text=view.hintPanel();Rectangle r=text.modelToView(text.getReferences().get(0).getStart());ref[0]=text.getLocationOnScreen();ref[0].translate(r.x+3,r.y+r.height/2);}catch(Exception e){throw new RuntimeException(e);}});
        robot.mouseMove(ref[0].x,ref[0].y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();
        edt(()->{require(view.boardPanel().hasTransientReferenceHighlight(),"real hint press did not highlight replay board");require(!frame.getSudokuPanel().hasTransientReferenceHighlight(),"reference leaked to live board");
            try{Files.createDirectories(output);BufferedImage image=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();frame.paint(g);g.dispose();javax.imageio.ImageIO.write(image,"png",output.resolve("reference-held.png").toFile());}catch(Exception e){throw new RuntimeException(e);}});
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();
        edt(()->{require(!view.boardPanel().hasTransientReferenceHighlight(),"reference release stuck");view.showFrame(1);require(!view.boardPanel().hasTransientReferenceHighlight(),"frame change retained highlight");view.showFrame(0);view.setPlaying(true);});
        Thread.sleep(1200);edt(()->{require(view.playbackMillis()>=1000&&view.playbackMillis()<3000&&view.frameIndex()==0&&view.timeline().getValue()>0,"actual Swing timer not moving during long gap");view.setPlaying(false);});
        Files.createDirectories(output);edt(()->{try{BufferedImage image=new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();frame.paint(g);g.dispose();javax.imageio.ImageIO.write(image,"png",output.resolve("playback-hint.png").toFile());controller.closeViewer();require(live[0].equals(new ReplayBoard(frame.getSudokuPanel().getSudoku()))&&hint[0].equals(frame.getHintTextArea().getText()),"viewer changed original board/hint");}catch(Exception e){throw new RuntimeException(e);}});
        System.out.println("PASS continuous clock and slider, pause/resume, same-time frames, final idle tail, replay-at-end, native proof hint reuse, real reference press/release isolation, actual Swing playback timer");
    }
    public static void main(String[] args)throws Exception{Path data=Files.createTempDirectory("replay-playback-");System.setProperty("hodoku.data.dir",data.toString());try{edt(()->frame=new MainFrame(null));run(frame,data);System.out.println(data);}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(frame!=null)edt(()->frame.dispose());}System.exit(0);}
}
