/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Real Swing save/quit paths, progress paints, repeated quit, partial failure and cancel. */
public final class SaveUiProbe {
    public static final class Response {int performed,cancelled;public void performQuit(){performed++;}public void cancelQuit(){cancelled++;}}
    private static int ticks,progressSeen,failureSeen;
    private static String failureText="";
    private static Path screenshots;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static java.util.List<Component> components(Container root){java.util.List<Component> result=new ArrayList<>();for(Component c:root.getComponents()){result.add(c);if(c instanceof Container)result.addAll(components((Container)c));}return result;}
    private static void screenshot(Window window,String name){try{if(screenshots!=null){window.toFront();javax.imageio.ImageIO.write(new Robot().createScreenCapture(window.getBounds()),"png",screenshots.resolve(name).toFile());}}catch(Exception e){throw new RuntimeException(e);}}
    private static void invoke(MainFrame frame,String method,Class<?>[] types,Object...args)throws Exception{Method action=MainFrame.class.getDeclaredMethod(method,types);action.setAccessible(true);try{action.invoke(frame,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception)throw(Exception)e.getCause();throw e;}}
    private static void quit(MainFrame frame,Response response)throws Exception{
        Throwable[] failure={null};MacOSApplication.QuitCoordinator coordinator=new MacOSApplication.QuitCoordinator(request->{try{invoke(frame,"requestQuit",new Class<?>[]{MacOSApplication.QuitRequest.class},request);}catch(Throwable e){failure[0]=e;}});
        javax.swing.Timer duplicate=new javax.swing.Timer(30,e->{((javax.swing.Timer)e.getSource()).stop();coordinator.requestQuit(response);});duplicate.setRepeats(false);duplicate.start();coordinator.requestQuit(response);if(failure[0]!=null)throw new AssertionError(failure[0]);
    }
    /** Exercises the real modal event loop and worker; no save implementation is replaced. */
    private static void progressFailure(Window owner,Throwable injected,boolean inPrepare)throws Exception{
        Set<Window> before=Collections.newSetFromMap(new IdentityHashMap<Window,Boolean>());
        before.addAll(Arrays.asList(Window.getWindows()));
        java.util.List<JDialog> created=new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean workRan=new java.util.concurrent.atomic.AtomicBoolean();
        final boolean[] timedOut={false};
        javax.swing.Timer watchdog=new javax.swing.Timer(5000,event->{
            timedOut[0]=true;
            for(Window window:Window.getWindows())
                if(window instanceof JDialog&&window.getOwner()==owner&&!before.contains(window))window.dispose();
        });
        watchdog.setRepeats(false);watchdog.start();
        IOException observed=null;
        try{
            SaveProgress.run(owner,false,()->{
                require(SwingUtilities.isEventDispatchThread(),"Prepare left EDT");
                for(Window window:Window.getWindows())
                    if(window instanceof JDialog&&window.getOwner()==owner&&!before.contains(window))created.add((JDialog)window);
                if(inPrepare)throwInjected(injected);
                return progress->{
                    require(!SwingUtilities.isEventDispatchThread(),"Work ran on EDT");
                    workRan.set(true);progress.update(SaveFailure.text("options"),20);throwInjected(injected);
                };
            });
        }catch(IOException failure){observed=failure;}
        finally{watchdog.stop();}
        require(!timedOut[0],"failure left the save dialog open: "+injected);
        require(observed!=null,"save failure disappeared: "+injected);
        if(injected instanceof IOException)require(observed==injected,"IOException identity was replaced");
        else require(observed.getCause()==injected,"save did not preserve the exact failure cause: "+injected);
        require(workRan.get()!=inPrepare,"unexpected worker execution after Prepare failure");
        require(!created.isEmpty(),"save did not create its real progress dialog");
        for(JDialog dialog:created)require(!dialog.isVisible()&&!dialog.isDisplayable(),"failed save dialog was not disposed");
        require(owner.isDisplayable(),"failed save disposed the application window");
    }
    private static void throwInjected(Throwable failure)throws Exception{
        if(failure instanceof Error)throw(Error)failure;
        if(failure instanceof Exception)throw(Exception)failure;
        throw new AssertionError(failure);
    }
    private static void progressRecovery(Window owner)throws Exception{
        final boolean[] timedOut={false};
        java.util.concurrent.atomic.AtomicBoolean completed=new java.util.concurrent.atomic.AtomicBoolean();
        Set<Window> before=Collections.newSetFromMap(new IdentityHashMap<Window,Boolean>());
        before.addAll(Arrays.asList(Window.getWindows()));
        javax.swing.Timer watchdog=new javax.swing.Timer(5000,event->{
            timedOut[0]=true;
            for(Window window:Window.getWindows())
                if(window instanceof JDialog&&window.getOwner()==owner&&!before.contains(window))window.dispose();
        });
        watchdog.setRepeats(false);watchdog.start();
        try{SaveProgress.run(owner,false,()->progress->{completed.set(true);progress.update(SaveFailure.text("done"),100);});}
        finally{watchdog.stop();}
        require(!timedOut[0]&&completed.get(),"save worker/dialog did not recover after thrown Error");
        for(Window window:Window.getWindows())if(window instanceof JDialog&&window.getOwner()==owner&&!before.contains(window))
            require(!window.isVisible()&&!window.isDisplayable(),"successful retry left a progress dialog open");
    }
    public static void main(String[] args)throws Exception{
        screenshots=args.length==0?null:Paths.get(args[0]);if(screenshots!=null)Files.createDirectories(screenshots);
        final Throwable[] failure={null};
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(()->{
            MainFrame frame=null;javax.swing.Timer observer=null;
            try{
                frame=new MainFrame(null);frame.setVisible(true);
                frame.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
                frame.getSudokuPanel().setCell(0,2,4);
                GuiState state=new GuiState(frame.getSudokuPanel(),frame.getSudokuPanel().getSolver(),frame.getSolutionPanel());state.setIncludeAnnotations(true);state.get(true);
                java.util.List<DoodleStroke> ink=new ArrayList<>();
                for(int i=0;i<60;i++){DoodleStroke stroke=new DoodleStroke(Color.BLUE,1);for(int j=0;j<60;j++)stroke.getPoints().add(new DoodlePoint(j/60.0,i/60.0));ink.add(stroke);}
                state.setDoodleStrokes(ink);state.set();
                observer=new javax.swing.Timer(20,e->{ticks++;for(Window window:Window.getWindows())if(window instanceof JDialog&&window.isVisible()){
                    JDialog dialog=(JDialog)window;
                    if(!dialog.getTitle().equals(SaveFailure.text("closing"))&&!dialog.getTitle().equals(SaveFailure.text("saving")))continue;
                    if(progressSeen++==2)screenshot(window,"saving.png");
                }});observer.start();
                progressFailure(frame,new IllegalStateException("injected worker runtime failure"),false);
                progressFailure(frame,new AssertionError("injected worker assertion failure"),false);
                progressFailure(frame,new IOException("injected worker IO failure"),false);
                progressFailure(frame,new IllegalArgumentException("injected Prepare runtime failure"),true);
                progressFailure(frame,new AssertionError("injected Prepare assertion failure"),true);
                progressRecovery(frame);
                Path file=ApplicationPaths.getDataDirectory().toPath().resolve("manual.hsol");
                invoke(frame,"saveToFile",new Class<?>[]{boolean.class,String.class,int.class},true,file.toString(),1);
                require(Files.isRegularFile(file),"manual puzzle not saved");
                Response success=new Response();quit(frame,success);
                require(success.performed==1&&success.cancelled==0,"repeated quit bypassed/duplicated save");
                require(progressSeen>0&&ticks>2,"progress never painted or EDT stopped");
                SessionSnapshot saved=new SessionStore(ApplicationPaths.getSessionFile()).load();
                require(saved.getGuiState().getSudoku().getValue(2)==4,"session lost entered value");
                require(saved.getGuiState().getDoodleStrokes().size()==60,"session lost annotations");
                frame.getReplayController().cancelQuit();
                Path replayDirectory=frame.getReplayController().directory();frame.getReplayController().flushPendingSaves();
                Path displaced=replayDirectory.resolveSibling("replays-original");Files.move(replayDirectory,displaced);Files.write(replayDirectory,new byte[]{1});
                MainFrame owner=frame;
                Map<JDialog,Long> failureVisibleAt=new IdentityHashMap<>();
                javax.swing.Timer cancelFailure=new javax.swing.Timer(40,e->{for(Window window:Window.getWindows())if(window instanceof JDialog&&window.isVisible()){
                    JDialog dialog=(JDialog)window;if(!dialog.getTitle().equals(ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.quitSaveFailure.title")))continue;
                    Long visibleAt=failureVisibleAt.get(dialog);
                    if(visibleAt==null){failureVisibleAt.put(dialog,System.nanoTime());failureSeen++;dialog.toFront();return;}
                    // Visibility can precede the first native paint. Let EDT paint before capture/cancel.
                    if(System.nanoTime()-visibleAt<java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(200))return;
                    StringBuilder content=new StringBuilder();for(Component c:components(dialog))if(c instanceof JTextArea)content.append(((JTextArea)c).getText()).append('\n');failureText=content.toString();screenshot(dialog,"save-failure.png");
                    for(Component c:components(dialog))if(c instanceof JButton&&((JButton)c).getText().equals(ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.quitSaveFailure.cancel"))){((JButton)c).doClick();((javax.swing.Timer)e.getSource()).stop();return;}
                }});cancelFailure.start();
                Response cancelled=new Response();quit(owner,cancelled);cancelFailure.stop();
                require(cancelled.cancelled==1&&cancelled.performed==0,"failed save did not cancel native quit");
                require(failureSeen==1&&failureText.contains(replayDirectory.toString())&&failureText.contains("FileAlreadyExistsException"),"failure omitted stage, destination or cause");
                require(failureText.contains(SaveFailure.text("session"))&&failureText.contains(SaveFailure.text("completed")),"independent successful saves not reported");
                require(frame.isDisplayable()&&frame.getSudokuPanel().getSudoku().getValue(2)==4,"cancel lost live content");
                Files.delete(replayDirectory);Files.move(displaced,replayDirectory);
                // Repair while the same failed quit dialog is open; Retry must retain the trace
                // and advance its attempt number, then return exactly one native response.
                Files.move(replayDirectory,displaced);Files.write(replayDirectory,new byte[]{1});
                javax.swing.Timer retryFailure=new javax.swing.Timer(40,e->{for(Window window:Window.getWindows())if(window instanceof JDialog&&window.isVisible()){
                    JDialog dialog=(JDialog)window;if(!dialog.getTitle().equals(ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.quitSaveFailure.title")))continue;
                    try{Files.delete(replayDirectory);Files.move(displaced,replayDirectory);}catch(IOException ex){throw new RuntimeException(ex);}
                    for(Component c:components(dialog))if(c instanceof JButton&&((JButton)c).getText().equals(ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.quitSaveFailure.retry"))){((JButton)c).doClick();((javax.swing.Timer)e.getSource()).stop();return;}
                }});retryFailure.start();
                Response retried=new Response();quit(frame,retried);retryFailure.stop();require(retried.performed==1&&retried.cancelled==0,"repaired destination did not recover on same-request retry");
            }catch(Throwable e){failure[0]=e;}finally{if(observer!=null)observer.stop();if(frame!=null)frame.dispose();}
        });
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}
        System.out.println("Real save UI: responsive progress, exact Work/Prepare exception causes, disposed dialogs, worker recovery, manual file, full session, repeated quit, precise partial failure, cancel and retry passed; EDT ticks="+ticks);
        System.exit(0);
    }
}
