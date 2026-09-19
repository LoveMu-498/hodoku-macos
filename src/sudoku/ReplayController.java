package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;
import javax.swing.*;

/** Records completed EDT actions, grouping all automatic candidate changes with their trigger. */
public final class ReplayController {
    private static final Logger LOG=Logger.getLogger(ReplayController.class.getName());
    private final MainFrame owner;
    private final SudokuPanel live;
    private final Path directory;
    private final ReplayLibrary library;
    private final Map<String,ReplaySession> failedSaves=new LinkedHashMap<String,ReplaySession>();
    private final JLabel status=new JLabel();
    private ReplaySession session;
    private ReplayClock clock=new ReplayClock();
    private final javax.swing.Timer timer;
    private ReplayBoard beforeEditing;
    private boolean forceNewAfterEditing;
    private ReplayViewer viewer;
    private Component priorFocus,priorGlass;
    private JPanel replayOverlay;
    private final Map<AbstractButton,Boolean> menuStates=new LinkedHashMap<AbstractButton,Boolean>();
    private int suspension;
    private boolean dispatching;
    private String error;
    private String evidenceWarning;
    public void reportEvidenceFailure(RuntimeException failure){evidenceWarning=ReplayText.text("evidenceWarning",failure.getMessage());LOG.log(Level.WARNING,evidenceWarning,failure);refreshStatus();}
    private final ReplayRecovery recovery;
    private final ReplaySleepMonitor sleepMonitor=new ReplaySleepMonitor();
    private boolean closing;
    private long lastCheckpointNanos;
    private Runnable changeListener;
    public ReplayController(MainFrame owner,SudokuPanel live,JPanel statusLine){
        this(owner,live,statusLine,true);
    }
    public ReplayController(MainFrame owner,SudokuPanel live,JPanel statusLine,boolean restore){
        this.owner=owner;this.live=live;
        statusLine.setLayout(new ReplayViewer.WrapLayout());
        directory=Paths.get(System.getProperty("hodoku.replay.dir",ApplicationPaths.getDataDirectory().toPath().resolve("replays").toString()));
        library=new ReplayLibrary(directory);
        recovery=new ReplayRecovery(directory);
        ReplaySession restored=null;String restoreError=null;
        try{restored=recovery.restore();}catch(Exception e){restoreError=ReplayText.text("restoreError",e.getMessage());LOG.log(Level.WARNING,restoreError,e);}
        if(restored!=null&&restore){
            // Only a clean checkpoint follows a successful native-session save. Equal clues
            // alone cannot establish attempt identity after a crash or same-puzzle restart.
            if(!recovery.cleanSnapshot||(!recovery.cleanEditing&&!restored.last().board.equals(new ReplayBoard(live.getSudoku()))))
                owner.restoreReplayAttempt(restored);
            replaceSession(restored);if(recovery.cleanEditing){owner.restoreReplayEditingMode();beforeEditing=restored.last().board;pauseClock("editing");}persist();
        }else{
            if(restored!=null){
                // Finalize from durable effective time; neither shutdown nor startup time
                // belongs to the superseded attempt.
                restored.endedAt=clock.wallTimeMillis();
                try{ReplayStore.write(library.path(restored),restored);
                    if(!restored.retained&&!restored.completed&&!restored.pinned)library.discard(restored);
                }catch(java.io.IOException e){failedSaves.put(restored.id,restored);LOG.log(Level.WARNING,"Unable to finalize superseded replay",e);}
            }
            beginSession(new ReplayBoard(live.getSudoku()));
        }
        if(restoreError!=null)error=restoreError;
        try{if(!sleepMonitor.install(()->checkpoint(false)))LOG.warning("System sleep notifications unavailable; macOS monotonic clock remains authoritative");}catch(Exception e){LOG.log(Level.WARNING,"System sleep listener unavailable",e);}
        if(owner.isInputMode())beginEditing(false);
        timer=new javax.swing.Timer(250,e->{updateElapsed();if(!closing&&!session.completed&&System.nanoTime()-lastCheckpointNanos>=1000000000L)checkpoint(false);refreshStatus();});timer.start();
        status.setBorder(BorderFactory.createEmptyBorder(0,8,0,6));
        status.getAccessibleContext().setAccessibleName(ReplayText.text("elapsedHint"));
        statusLine.add(status);refreshStatus();
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue(){
            @Override protected void dispatchEvent(AWTEvent event){
                if(blockInput(event))return;
                boolean outer=!dispatching; if(outer)dispatching=true;
                try{super.dispatchEvent(event);}finally{if(outer){dispatching=false;if(owner.isDisplayable())afterAction();}
                    if(event instanceof WindowEvent && event.getSource()==owner && event.getID()==WindowEvent.WINDOW_CLOSED){if(viewer!=null)viewer.disposeViewer();timer.stop();sleepMonitor.close();viewer=null;pop();}
                }
            }
        });
    }
    private boolean blockInput(AWTEvent event){
        if(viewer==null || !owner.isDisplayable())return false;
        Object source=event.getSource();
        // Native mouse events arrive on the heavyweight window BEFORE Swing routes them
        // to lightweight glass-pane controls. Let that routing happen; the full-window
        // glass pane absorbs background clicks and its board renderer is input-disabled.
        if(event instanceof MouseEvent && (source==owner || source==replayOverlay))return false;
        if(event instanceof InputEvent || event instanceof java.awt.event.InputMethodEvent || event instanceof ActionEvent){
            if(event instanceof ActionEvent && source instanceof JMenuItem && menuStates.containsKey(source))return true;
            if(source instanceof Component){
                Component c=(Component)source;
                if(c==viewer || SwingUtilities.isDescendingFrom(c,viewer))return viewer.isBoardInput(c);
                // A Window is its own event boundary. getWindowAncestor(dialog)
                // returns its owner and would incorrectly block every native dialog click.
                Window w=c instanceof Window?(Window)c:SwingUtilities.getWindowAncestor(c);
                if(w==owner || source==owner)return true;
            }
        }
        return false;
    }
    public MainFrame owner(){return owner;}
    public SudokuPanel livePanel(){return live;}
    public Path directory(){return directory;}
    public ReplaySession session(){return session;}
    public boolean isViewing(){return viewer!=null;}
    public ReplayViewer viewer(){return viewer;}
    public String lastError(){return error;}
    public void setChangeListener(Runnable listener){changeListener=listener;}
    public void suspend(){suspension++;}
    public void resume(){if(suspension==0)throw new IllegalStateException("Unbalanced replay resume");suspension--;}
    public ReplayClock clock(){return clock;}
    public long elapsedMillis(){updateElapsed();return session.elapsedMillis;}
    public long wallTimeMillis(){return clock.wallTimeMillis();}
    /** Clock replacement is a deterministic time boundary for verification and platform integration. */
    public void setClock(ReplayClock replacement){updateElapsed();long elapsed=session.elapsedMillis;clock=replacement;clock.reset(elapsed);if(beforeEditing!=null)clock.pause("editing");if(session.completed)clock.pause("completed");}
    public void updateElapsed(){if(session!=null&&!session.completed)session.elapsedMillis=clock.elapsedMillis();}
    public void pauseClock(String reason){updateElapsed();clock.pause(reason);}
    public void resumeClock(String reason){clock.resume(reason);}
    public boolean isEditing(){return beforeEditing!=null;}
    public void beginEditing(boolean forceNew){
        if(beforeEditing==null){capture("manual",ReplayText.text("manual"));beforeEditing=new ReplayBoard(live.getSudoku());pauseClock("editing");}
        forceNewAfterEditing|=forceNew;
    }
    public void finishEditing(){
        if(beforeEditing==null)return;
        ReplayBoard board=new ReplayBoard(live.getSudoku());boolean changed=forceNewAfterEditing||!beforeEditing.samePuzzle(board);
        beforeEditing=null;forceNewAfterEditing=false;
        if(changed)beginSession(board);else resumeClock("editing");
    }
    /** Explicit successful puzzle replacement, including reopening the identical puzzle. */
    public void startNewAttempt(){if(suspension>0)return;beforeEditing=null;forceNewAfterEditing=false;beginSession(new ReplayBoard(live.getSudoku()));}
    public void beginSession(ReplayBoard board){
        ReplaySession outgoing=session;
        if(session!=null){updateElapsed();if(session.endedAt==0)session.endedAt=clock.wallTimeMillis();persist();}
        clock.reset(0);session=new ReplaySession(board,clock.wallTimeMillis());persist();
        if(outgoing!=null&&!outgoing.retained&&!outgoing.completed&&!outgoing.pinned&&!failedSaves.containsKey(outgoing.id)){
            try{library.discard(outgoing);}catch(java.io.IOException e){reportLibraryError(e);}
        }
        rotateLibrary();
    }
    public void replaceSession(ReplaySession restored){session=restored;clock.reset(restored.elapsedMillis);if(restored.completed)clock.pause("completed");refreshStatus();}
    /** Called after a complete EDT action so a final proof/apply transaction lands before sealing. */
    public void afterAction(){
        if(closing)return;
        capture("manual",ReplayText.text("manual"));
        if(suspension==0&&!isEditing()&&!isViewing()&&!session.completed&&live.getSudoku().isSolved()&&live.getSudoku().checkSudoku()){
            updateElapsed();session.completed=true;session.retained=true;session.endedAt=clock.wallTimeMillis();clock.pause("completed");persist();rotateLibrary();
            if(changeListener!=null)changeListener.run();
        }
    }
    public void capture(String kind,String label){
        if(closing||suspension>0||isEditing()||isViewing()||session==null||session.completed)return;
        updateElapsed();
        ReplayBoard board=new ReplayBoard(live.getSudoku());
        if(board.equals(session.last().board))return;
        appendOperation(Collections.singletonList(new ReplayFrame(session.last().operationId+1,clock.wallTimeMillis(),session.elapsedMillis,kind,label,board,null)));
    }
    public void appendOperation(List<ReplayFrame> frames){
        if(session.completed||frames.isEmpty())return;
        session.append(frames);persist();if(changeListener!=null)changeListener.run();
    }
    public void persist(){
        long start=System.nanoTime();
        try{Path file=directory.resolve(session.id+".hrep");ReplayStore.write(file,session);recovery.checkpoint(session,false);lastCheckpointNanos=System.nanoTime();failedSaves.remove(session.id);error=failedSaves.isEmpty()?null:ReplayText.text("outstanding",failedSaves.size());
            LOG.log(Level.FINE,"Replay saved: frames={0}, bytes={1}, elapsedMicros={2}",new Object[]{session.frames().size(),Files.size(file),(System.nanoTime()-start)/1000});
        }catch(Exception e){failedSaves.put(session.id,session);error=e.getMessage();LOG.log(Level.WARNING,"Replay save failed",e);}
        refreshStatus();
    }
    public void checkpoint(boolean clean){
        if(closing&&!clean)return;
        updateElapsed();try{if((error!=null||!failedSaves.isEmpty())&&!clean){retrySaves();return;}recovery.checkpoint(session,clean);lastCheckpointNanos=System.nanoTime();}catch(Exception e){error=e.getMessage();LOG.log(Level.WARNING,"Replay checkpoint failed",e);}refreshStatus();
    }
    public void prepareQuit()throws java.io.IOException{
        if(!closing){afterAction();pauseClock("closing");closing=true;}
        retrySaves();if(error!=null||!failedSaves.isEmpty())throw new java.io.IOException(error==null?ReplayText.text("unsaved"):error);
    }
    public void completeQuit()throws java.io.IOException{recovery.checkpoint(session,true,isEditing());}
    public void cancelQuit(){closing=false;resumeClock("closing");checkpoint(false);}
    public ReplayLibrary library(){return library;}
    public List<ReplaySession> records()throws java.io.IOException{
        Map<String,ReplaySession> all=new LinkedHashMap<String,ReplaySession>();
        for(ReplaySession item:library.list())if(item.retained||item.id.equals(session.id)||failedSaves.containsKey(item.id))all.put(item.id,item);
        all.putAll(failedSaves);all.put(session.id,session);
        List<ReplaySession> result=new ArrayList<ReplaySession>(all.values());
        Collections.sort(result,(a,b)->Long.compare(b.startedAt,a.startedAt));return result;
    }
    public boolean hasPendingSave(String id){return failedSaves.containsKey(id);}
    public void retainCurrent(){capture("manual",ReplayText.text("manual"));session.retained=true;updateElapsed();persist();}
    public void createSavePointMarker(String name){
        if(session.completed)return;
        capture("manual",ReplayText.text("manual"));session.retained=true;
        session.addBookmark(new ReplayBookmark(session.frames().size()-1,name,wallTimeMillis(),elapsedMillis()));persist();
    }
    public void savePointRestored(){capture("restore-savepoint",ReplayText.text("restorePoint"));}
    public void retrySaves(){
        for(ReplaySession pending:new ArrayList<ReplaySession>(failedSaves.values())){
            try{ReplayStore.write(library.path(pending),pending);if(!pending.id.equals(session.id)&&pending.endedAt!=0&&!pending.retained&&!pending.completed&&!pending.pinned)library.discard(pending);failedSaves.remove(pending.id);}catch(java.io.IOException e){error=e.getMessage();}
        }
        persist();rotateLibrary();refreshStatus();
    }
    public void pinRecord(ReplaySession selected,boolean pinned)throws java.io.IOException{
        ReplaySession target=selected.id.equals(session.id)?session:failedSaves.containsKey(selected.id)?failedSaves.get(selected.id):selected;
        try{library.setPinned(target,pinned);if(target==session){recovery.checkpoint(session,false);lastCheckpointNanos=System.nanoTime();}failedSaves.remove(target.id);error=failedSaves.isEmpty()?null:ReplayText.text("retryOutstanding");}
        catch(java.io.IOException e){if(target.retained&&!(e instanceof ReplayLibrary.PinLimitException))failedSaves.put(target.id,target);reportLibraryError(e);throw e;}
        rotateLibrary();refreshStatus();
    }
    public void rotateLibrary(){try{library.rotate(session.id,failedSaves.keySet());}catch(java.io.IOException e){reportLibraryError(e);}}
    private void reportLibraryError(Exception e){error=e.getMessage();LOG.log(Level.WARNING,"Replay library operation failed",e);refreshStatus();}
    public void openLibrary(){new ReplayLibraryDialog(this).setVisible(true);}
    /** Feedback keeps the successful puzzle/savepoint save distinct from a failed replay write. */
    public void showRetentionFailure(String success){
        if(lastError()!=null)JOptionPane.showMessageDialog(owner,ReplayText.text("saveFailureDetail",success,lastError()),ReplayText.text("saveFailure"),JOptionPane.WARNING_MESSAGE);
    }
    public void refreshStatus(){
        long seconds=session.elapsedMillis/1000;
        String time=String.format("%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60);
        String state=session.completed?ReplayText.text("completed"):isEditing()?ReplayText.text("editing"):ReplayText.text("steps",Math.max(0,session.frames().size()-1));
        status.setText(time);
        String detail=ReplayText.text("elapsedHint")+" · "+state;
        if(!session.interruption.isEmpty())detail+=" · "+session.interruption;
        if(error!=null)detail+=" · "+error;
        status.setToolTipText(evidenceWarning==null?detail:detail+"；"+evidenceWarning);
    }
    /** Starts a new editable attempt only after every required persistence write succeeds. */
    public void branchFromFrame(ReplaySession source,int frameIndex)throws java.io.IOException{
        if(source==null||frameIndex<0||frameIndex>=source.frames().size())throw new java.io.IOException("Invalid branch frame");
        ReplayFrame frame=source.frames().get(frameIndex);
        byte[] evidence=frame.evidence(),raw=new byte[0];
        if(ReplayEvidence.authored(evidence)){
            ReplayEvidence authored=ReplayEvidence.decode(evidence);
            if(authored.boxes().size()>6)throw new java.io.IOException("Too many box groups");
            List<UserChain> geometry=authored.chains();for(UserChain chain:geometry)chain.setAnalysisResult(null);
            raw=ReplayEvidence.input(authored.source,authored.boxes(),geometry);
        }else if(frameIndex==0)raw=source.initialAnnotations();
        retrySaves();if(error!=null||!failedSaves.isEmpty())throw new java.io.IOException(error==null?ReplayText.text("unsaved"):error);
        ReplaySession next=new ReplaySession(frame.board,wallTimeMillis());next.sourceReplayId=source.id;next.sourceFrameIndex=frameIndex;next.setInitialAnnotations(raw);
        Sudoku2 selected=frame.board.toSudoku();
        if(selected.isSolved()&&selected.checkSudoku()){next.completed=true;next.retained=true;next.endedAt=next.startedAt;}
        ReplayFiles.validate(next);
        updateElapsed();ReplaySession outgoing=session;
        ReplaySession ended=ReplayFiles.snapshot(outgoing);ended.retained=outgoing.retained;ended.pinned=outgoing.pinned;ended.interruption=outgoing.interruption;
        if(ended.endedAt==0)ended.endedAt=wallTimeMillis();
        boolean outgoingWritten=false;
        try{
            ReplayStore.write(library.path(next),next);
            ReplayStore.write(library.path(ended),ended);outgoingWritten=true;
            recovery.checkpoint(next,false);
        }catch(java.io.IOException failure){
            if(outgoingWritten)try{ReplayStore.write(library.path(outgoing),outgoing);recovery.checkpoint(outgoing,false);}catch(java.io.IOException rollback){failedSaves.put(outgoing.id,outgoing);failure.addSuppressed(rollback);}
            try{Files.deleteIfExists(library.path(next));}catch(java.io.IOException cleanup){failure.addSuppressed(cleanup);}
            reportLibraryError(failure);throw failure;
        }
        suspend();
        try{
            try{owner.installReplayBranch(next);}catch(RuntimeException failure){
                java.io.IOException error=new java.io.IOException("Unable to install branch state",failure);
                try{ReplayStore.write(library.path(outgoing),outgoing);recovery.checkpoint(outgoing,false);Files.deleteIfExists(library.path(next));}catch(java.io.IOException rollback){failedSaves.put(outgoing.id,outgoing);error.addSuppressed(rollback);}
                reportLibraryError(error);throw error;
            }
            closeViewer();
            beforeEditing=null;forceNewAfterEditing=false;session=next;clock.reset(0);if(next.completed)clock.pause("completed");lastCheckpointNanos=System.nanoTime();
        }finally{resume();}
        if(!outgoing.retained&&!outgoing.completed&&!outgoing.pinned&&!failedSaves.containsKey(outgoing.id))try{library.discard(outgoing);}catch(java.io.IOException failure){reportLibraryError(failure);}
        rotateLibrary();owner.check();refreshStatus();live.requestFocusInWindow();
    }
    private void disableMenus(Component component){
        if(component instanceof AbstractButton){AbstractButton button=(AbstractButton)component;menuStates.put(button,button.isEnabled());button.setEnabled(false);}
        if(component instanceof JMenu)for(Component child:((JMenu)component).getMenuComponents())disableMenus(child);
        else if(component instanceof Container)for(Component child:((Container)component).getComponents())disableMenus(child);
    }
    public void openViewer(ReplaySession selected){
        if(viewer!=null)return;
        if(error!=null){retrySaves();if(error!=null)return;}
        priorFocus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();priorGlass=owner.getGlassPane();
        viewer=new ReplayViewer(this,selected);
        replayOverlay=new JPanel(new BorderLayout()){@Override public void doLayout(){if(viewer!=null){Component area=owner.replayArea();viewer.setBounds(SwingUtilities.convertRectangle(area.getParent(),area.getBounds(),this));viewer.revalidate();}}};
        replayOverlay.addComponentListener(new ComponentAdapter(){@Override public void componentResized(ComponentEvent event){replayOverlay.doLayout();}});
        replayOverlay.setOpaque(false);replayOverlay.addMouseListener(new MouseAdapter(){});replayOverlay.addMouseMotionListener(new MouseMotionAdapter(){});replayOverlay.add(viewer);
        owner.setGlassPane(replayOverlay);replayOverlay.setVisible(true);replayOverlay.doLayout();viewer.requestFocusInWindow();
        disableMenus(owner.getJMenuBar());
    }
    public void closeViewer(){
        if(viewer==null)return;
        viewer.disposeViewer();replayOverlay.setVisible(false);viewer=null;owner.setGlassPane(priorGlass);
        for(Map.Entry<AbstractButton,Boolean> entry:menuStates.entrySet())entry.getKey().setEnabled(entry.getValue());menuStates.clear();
        if(priorFocus!=null&&priorFocus.isShowing()&&priorFocus.isFocusable())priorFocus.requestFocusInWindow();else live.requestFocusInWindow();
    }
}
