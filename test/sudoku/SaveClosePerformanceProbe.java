/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.*;
import java.awt.event.WindowEvent;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Measures the real quit pipeline and a real WINDOW_CLOSING child, with complete readback. */
public final class SaveClosePerformanceProbe {
    private static final String PUZZLE="530070000600195000098000060800060003400803001700020006060000280000419005000080079";
    private static final int STROKES=80,POINTS=120,FRAMES=1200;
    private static final java.util.concurrent.CountDownLatch PAINTED=new java.util.concurrent.CountDownLatch(1);
    private static final class PaintedFrame extends MainFrame {
        PaintedFrame(){super(null);}
        @Override public void paint(Graphics graphics){super.paint(graphics);PAINTED.countDown();}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        String mode=args[0];Path report=Paths.get(args[1]);Files.createDirectories(report);
        if(mode.equals("verify")||mode.equals("verify-completed")){
            SessionSnapshot saved=new SessionStore(ApplicationPaths.getSessionFile()).load();
            require(saved.getGuiState().getSudoku().getValue(2)==4,"lost board");
            require(saved.getGuiState().getDoodleStrokes().size()==STROKES,"lost ink");
            for(int i=0;i<STROKES;i++){DoodleStroke stroke=saved.getGuiState().getDoodleStrokes().get(i);require(stroke.getPoints().size()==POINTS,"lost points");for(int at=0;at<POINTS;at++){DoodlePoint point=stroke.getPoints().get(at);require(point.getX()==at/(double)POINTS&&point.getY()==i/(double)STROKES,"changed geometry");}}
            ReplaySession replay=ReplayStore.read(ApplicationPaths.getDataDirectory().toPath().resolve("replays").resolve(saved.getReplayId()+".hrep"));
            require(replay.frames().size()==FRAMES+(mode.equals("verify-completed")?2:1),"lost frames");
            ReplayFiles.validate(replay);
            System.out.println("VERIFIED board, 9600 points and "+replay.frames().size()+" valid frames");return;
        }
        if(mode.equals("enqueue")){
            System.setProperty("java.awt.headless","true");
            for(int count:new int[]{1000,4000,16000}){
                ReplaySession session=new ReplaySession(new ReplayBoard(new Sudoku2()),1);
                long started=System.nanoTime();
                for(int i=1;i<=count;i++){session.append(Collections.singletonList(new ReplayFrame(i,i,0,"manual","step",session.last().board,null)));session.persistenceSnapshot();}
                System.out.printf(Locale.ROOT,"SNAPSHOTS frames=%d ms=%.3f%n",count,(System.nanoTime()-started)/1e6);
            }return;
        }
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        Throwable[] failure={null};
        MainFrame[] ready={null};
        SwingUtilities.invokeAndWait(()->{ready[0]=new PaintedFrame();ready[0].setVisible(true);});
        require(PAINTED.await(15,java.util.concurrent.TimeUnit.SECONDS),"native main window did not finish painting");
        SwingUtilities.invokeAndWait(()->Toolkit.getDefaultToolkit().sync());
        if(mode.equals("completed-close"))SwingUtilities.invokeAndWait(()->{
            MainFrame frame=ready[0];String solution="534678912672195348198342567859761423426853791713924856961537284287419635345286179";
            for(int at=0;at<81;at++)if(frame.getSudokuPanel().getSudoku().getValue(at)==0)frame.getSudokuPanel().setCell(at/9,at%9,solution.charAt(at)-'0');
            frame.getReplayController().afterAction();try{frame.getReplayController().flushPendingSaves();}catch(Exception ex){throw new RuntimeException(ex);}
        });
        if(mode.equals("close")||mode.equals("completed-close")){
            String observerReady=System.getenv("HODOKU_OBSERVER_READY");
            if(observerReady!=null){long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);while(!Files.exists(Paths.get(observerReady))&&System.nanoTime()<deadline)Thread.sleep(10);require(Files.exists(Paths.get(observerReady)),"external observer not ready");}
            // Finish the current board's painting before the measured close event.
            SwingUtilities.invokeAndWait(()->{JRootPane pane=ready[0].getRootPane();pane.paintImmediately(0,0,pane.getWidth(),pane.getHeight());Toolkit.getDefaultToolkit().sync();});
            SwingUtilities.invokeAndWait(()->{});
        }
        SwingUtilities.invokeAndWait(()->{
            MainFrame frame=ready[0];
            try{

                if(mode.equals("seed")){
                    frame.setPuzzle(PUZZLE);frame.getSudokuPanel().setCell(0,2,4);
                    frame.getReplayController().startNewAttempt();
                    GuiState state=new GuiState(frame.getSudokuPanel(),frame.getSudokuPanel().getSolver(),frame.getSolutionPanel());state.setIncludeAnnotations(true);state.get(true);
                    java.util.List<DoodleStroke> ink=new ArrayList<>();
                    for(int i=0;i<STROKES;i++){DoodleStroke stroke=new DoodleStroke(Color.BLUE,1);for(int p=0;p<POINTS;p++)stroke.getPoints().add(new DoodlePoint(p/(double)POINTS,i/(double)STROKES));ink.add(stroke);}
                    state.setDoodleStrokes(ink);state.set();ReplaySession replay=frame.getReplayController().session();
                    ReplayBoard entered=new ReplayBoard(frame.getSudokuPanel().getSudoku());Sudoku2 blank=new Sudoku2();blank.setSudoku(PUZZLE);ReplayBoard cleared=new ReplayBoard(blank);long now=System.currentTimeMillis();
                    for(int i=1;i<=FRAMES;i++)frame.getReplayController().appendOperation(Collections.singletonList(new ReplayFrame(i,now,0,"manual","synthetic undo/reentry",i%2==1?cleared:entered,null)));
                }
                MainFrame owner=frame;
                if(mode.equals("close")||mode.equals("completed-close")){
                    long start=System.nanoTime();
                    javax.swing.Timer observer=new javax.swing.Timer(15,e->{
                        for(Window window:Window.getWindows())if(window instanceof JDialog&&window.isVisible()){
                            String name=((JDialog)window).getTitle();
                            if(name.equals(SaveFailure.text("closing")))try{Files.write(report.resolve("first-feedback-ms"),String.valueOf((System.nanoTime()-start)/1e6).getBytes(StandardCharsets.UTF_8));((javax.swing.Timer)e.getSource()).stop();}catch(Exception ex){throw new RuntimeException(ex);}
                        }
                    });observer.start();
                    System.out.println("CLOSE_START wall_epoch_ms="+System.currentTimeMillis());System.out.flush();
                    frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));
                    throw new AssertionError("close did not terminate");
                }
                Method prepare=MainFrame.class.getDeclaredMethod("prepareApplicationSave");prepare.setAccessible(true);
                long captureStart=System.nanoTime();SaveProgress.Work work=(SaveProgress.Work)prepare.invoke(owner);long captureNanos=System.nanoTime()-captureStart;
                // Run the actual save body outside EDT; keep this measurement independent of UI sampling.
                final long[] last={System.nanoTime()};final String[] prior={"capture"};final Throwable[] ioFailure={null};StringBuilder stages=new StringBuilder("capture="+captureNanos/1e6+"\n");
                Thread io=new Thread(()->{try{work.run((stage,percent)->{long now=System.nanoTime();stages.append(prior[0]).append('=').append((now-last[0])/1e6).append('\n');prior[0]=stage;last[0]=now;});stages.append(prior[0]).append('=').append((System.nanoTime()-last[0])/1e6).append('\n');}catch(Throwable ex){ioFailure[0]=ex;}},"close-stage-measurement");
                io.start();io.join();if(ioFailure[0]!=null)throw new AssertionError(ioFailure[0]);Files.write(report.resolve(mode+"-stages.txt"),stages.toString().getBytes(StandardCharsets.UTF_8));System.out.println(stages);
            }catch(Throwable ex){failure[0]=ex;}finally{if(frame!=null)frame.dispose();}
        });
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}System.exit(0);
    }
}
