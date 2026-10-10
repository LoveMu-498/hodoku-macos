/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.*;
import java.io.IOException;
import java.util.concurrent.*;
import javax.swing.*;

/** Modal input protection with a responsive EDT and disk work on a bounded worker. */
final class SaveProgress {
    private static final ThreadPoolExecutor SAVER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(2),r->{Thread t=new Thread(r,"HoDoKu session save");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    static { SAVER.allowCoreThreadTimeOut(true); }
    interface Progress {void update(String stage,int percent);}
    interface Work {void run(Progress progress)throws Exception;}
    interface Prepare {Work capture()throws Exception;}
    static void run(Window owner,boolean quitting,Prepare prepare)throws IOException{
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Save UI must start on EDT");
        try(SaveTrace.Operation operation=SaveTrace.operation(quitting?"quit_save":"save");SaveTrace.Scope attempt=SaveTrace.attempt()){
        try {
        final SaveTrace.Context context=SaveTrace.capture();
        final SaveTrace.Span firstPaint=SaveTrace.phase("ui.first_progress_paint");
        final java.util.concurrent.atomic.AtomicBoolean painted=new java.util.concurrent.atomic.AtomicBoolean();
        SaveTrace.Span construct=SaveTrace.phase("ui.progress_construct");
        JDialog dialog=new JDialog(owner,SaveFailure.text(quitting?"closing":"saving"),Dialog.ModalityType.APPLICATION_MODAL){
            @Override public void paint(Graphics graphics){super.paint(graphics);if(painted.compareAndSet(false,true))firstPaint.close();}
        };
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);dialog.setResizable(false);
        JPanel content=new JPanel(new BorderLayout(0,12));content.setBorder(BorderFactory.createEmptyBorder(20,24,20,24));
        JLabel title=new JLabel(SaveFailure.text(quitting?"closing":"saving"));title.setFont(title.getFont().deriveFont(Font.BOLD));content.add(title,BorderLayout.NORTH);
        JLabel stage=new JLabel(SaveFailure.text("snapshot"));JProgressBar bar=new JProgressBar(0,100);bar.setIndeterminate(true);bar.setStringPainted(true);
        JPanel progressPanel=new JPanel(new BorderLayout(0,8));progressPanel.add(stage,BorderLayout.NORTH);progressPanel.add(bar,BorderLayout.CENTER);content.add(progressPanel,BorderLayout.CENTER);
        content.add(new JLabel(SaveFailure.text("wait")),BorderLayout.SOUTH);content.setPreferredSize(new Dimension(450,150));dialog.setContentPane(content);dialog.pack();dialog.setLocationRelativeTo(owner);
        construct.close();
        final Throwable[] failure=new Throwable[1];
        Progress progress=(name,percent)->{
            SaveTrace.event("progress.requested",name);
            SwingUtilities.invokeLater(()->{try(SaveTrace.Scope scope=SaveTrace.bind(context)){stage.setText(name);bar.setIndeterminate(percent<100);bar.setValue(percent);bar.setString(SaveFailure.text(percent<100?"saving":"done"));SaveTrace.event("progress.applied",name);}});
        };
        final SaveTrace.Span captureQueue=SaveTrace.phase("ui.capture_queue_wait");
        SwingUtilities.invokeLater(()->{
            try(SaveTrace.Scope scope=SaveTrace.bind(context)){
            captureQueue.close();
            try{
                Work work;
                try(SaveTrace.Span capture=SaveTrace.phase("ui.capture")){try{work=prepare.capture();}catch(Throwable e){capture.fail(e);throw e;}}
                final SaveTrace.Span workerQueue=SaveTrace.phase("session.queue_wait");
                Runnable worker=()->{
                        Throwable problem=null;
                        try(SaveTrace.Scope background=SaveTrace.bind(context)){
                            workerQueue.close();
                            try(SaveTrace.Span body=SaveTrace.phase("save.work")){try{work.run(progress);}catch(Throwable e){body.fail(e);problem=e;}}
                            final Throwable result=problem;
                            final SaveTrace.Span doneQueue=SaveTrace.phase("ui.worker_done_queue_wait");
                            // SwingWorker.done() uses a ~33 ms batching timer in the bundled JDK.
                            // Completion has no batch to coalesce: enqueue it immediately on EDT.
                            SwingUtilities.invokeLater(()->{try(SaveTrace.Scope ui=SaveTrace.bind(context);SaveTrace.Span completion=SaveTrace.phase("ui.worker_done")){
                                doneQueue.close();
                                try{if(result!=null){failure[0]=result;completion.fail(result);}}finally{dialog.dispose();}
                            }});
                        }
                };
                try{SAVER.execute(worker);}catch(RuntimeException e){workerQueue.fail(e);workerQueue.close();throw e;}
            }catch(Throwable e){failure[0]=e;dialog.dispose();}
            }
        });
        try(SaveTrace.Span modal=SaveTrace.phase("ui.modal_wait")){dialog.setVisible(true);if(failure[0]!=null)modal.fail(failure[0]);}
        if(!painted.get()){firstPaint.outcome("not_painted");firstPaint.close();}
        if(failure[0]!=null){operation.outcome("failed");if(failure[0] instanceof IOException)throw(IOException)failure[0];throw new IOException(SaveFailure.text("unexpected"),failure[0]);}
        }catch(IOException|RuntimeException|Error e){operation.outcome("failed");throw e;}
        }
    }
}
