package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;

/** Read-only solve-area view. Every frame uses an isolated instance of the native board renderer. */
public final class ReplayViewer extends JPanel {
    private final ReplayController controller;
    private final ReplaySession session;
    private final List<ReplayFrame> frames;
    private final SudokuPanel board;
    private final HintTextArea description=new HintTextArea();
    private final JLabel frameTitle=new JLabel();
    private final JTextArea interruptionNotice=new JTextArea();
    private final JLabel position=new JLabel(),error=new JLabel();
    private final JPanel actions=new JPanel(new WrapLayout());
    private final JButton first=button("|◀","first"),previousGroup=button("◀◀","previousGroup"),previous=button("◀","previous"),play=button("▶","play"),next=button("▶","next"),nextGroup=button("▶▶","nextGroup"),last=button("▶|","last");
    private final JSlider timeline=new JSlider(0,1000000,0);
    private final JComboBox<String> bookmarks=new JComboBox<String>();
    private final javax.swing.Timer playback;
    private final java.util.function.LongSupplier nanoTime;
    private long playbackStart,segmentStart,cursorMillis,maxTime;
    private boolean updating,playing;
    private int index;
    public ReplayViewer(ReplayController controller,ReplaySession session){
        this(controller,session,System::nanoTime);
    }
    ReplayViewer(ReplayController controller,ReplaySession session,java.util.function.LongSupplier nanoTime){
        super(new BorderLayout(6,6));this.controller=controller;this.session=session;this.nanoTime=nanoTime;frames=session.frames();
        setOpaque(true);setBackground(UIManager.getColor("Panel.background"));setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        setFocusable(true);board=new SudokuPanel(controller.owner(),true);
        position.putClientProperty("html.disable",Boolean.TRUE);error.putClientProperty("html.disable",Boolean.TRUE);
        DefaultListCellRenderer bookmarkRenderer=new DefaultListCellRenderer();bookmarkRenderer.putClientProperty("html.disable",Boolean.TRUE);bookmarks.setRenderer(bookmarkRenderer);
        JPanel controls=actions;
        JButton close=new JButton(ReplayText.text("return"));close.addActionListener(e->controller.closeViewer());controls.add(close);
        JLabel mode=new JLabel(ReplayText.text("readOnly"));mode.setFont(mode.getFont().deriveFont(Font.BOLD));controls.add(mode);
        ReplayFileActions.install(controls,controller,session);
        JButton branch=new JButton(ReplayText.text("branch"));branch.setEnabled(!frames.isEmpty());branch.addActionListener(e->{
            setPlaying(false);
            if(JOptionPane.showConfirmDialog(this,ReplayText.text("branchConfirm"),ReplayText.text("branch"),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            try{controller.branchFromFrame(session,index);}catch(java.io.IOException failure){JOptionPane.showMessageDialog(this,ReplayText.text("branchFailure",failure.getMessage()),ReplayText.text("branch"),JOptionPane.ERROR_MESSAGE);}
        });controls.add(branch);
        JPanel header=new JPanel(new BorderLayout());header.add(controls,BorderLayout.NORTH);
        frameTitle.putClientProperty("html.disable",Boolean.TRUE);header.add(frameTitle,BorderLayout.CENTER);
        description.setSudokuPanel(board);description.setFont(controller.owner().getHintTextArea().getFont());
        description.setLineWrap(true);description.setWrapStyleWord(true);description.setRows(4);
        description.setToolTipText(ReplayText.text("referenceHint"));
        interruptionNotice.setEditable(false);interruptionNotice.setFocusable(false);interruptionNotice.setLineWrap(true);interruptionNotice.setWrapStyleWord(true);interruptionNotice.setOpaque(false);
        if(!session.interruption.isEmpty()){
            String state=session.completed?ReplayText.text("completed"):session.id.equals(controller.session().id)?ReplayText.text("current"):ReplayText.text("unfinished");
            interruptionNotice.setText(ReplayText.text("interruptionNotice",state));interruptionNotice.setRows(2);header.add(interruptionNotice,BorderLayout.SOUTH);
        }
        add(header,BorderLayout.NORTH);
        JScrollPane hintScroll=new JScrollPane(description);hintScroll.setMinimumSize(new Dimension(0,65));
        board.setMinimumSize(new Dimension(160,160));
        JSplitPane content=new JSplitPane(JSplitPane.VERTICAL_SPLIT,board,hintScroll);content.setResizeWeight(1.0);content.setContinuousLayout(true);content.setBorder(null);
        JPanel center=new JPanel(new BorderLayout());center.add(content,BorderLayout.CENTER);error.setHorizontalAlignment(SwingConstants.CENTER);center.add(error,BorderLayout.SOUTH);add(center,BorderLayout.CENTER);
        first.addActionListener(e->showFrame(0));last.addActionListener(e->showFrame(frames.size()-1));previous.addActionListener(e->showFrame(index-1));next.addActionListener(e->showFrame(index+1));previousGroup.addActionListener(e->jumpOperation(-1));nextGroup.addActionListener(e->jumpOperation(1));play.addActionListener(e->setPlaying(!playing));
        JPanel navigation=new JPanel(new WrapLayout());for(JButton button:new JButton[]{first,previousGroup,previous,play,next,nextGroup,last})navigation.add(button);
        bookmarks.addItem(ReplayText.text("bookmarks"));for(ReplayBookmark marker:session.bookmarks())bookmarks.addItem(marker.name);bookmarks.setPrototypeDisplayValue("000000000000");bookmarks.setEnabled(!session.bookmarks().isEmpty());bookmarks.addActionListener(e->{int selected=bookmarks.getSelectedIndex()-1;if(!updating&&selected>=0)showFrame(session.bookmarks().get(selected).frameIndex);});JPanel detailRow=new JPanel(new BorderLayout(6,0));detailRow.add(position,BorderLayout.WEST);detailRow.add(bookmarks,BorderLayout.EAST);
        JPanel footer=new JPanel(new BorderLayout());footer.add(timeline,BorderLayout.NORTH);footer.add(navigation,BorderLayout.CENTER);footer.add(detailRow,BorderLayout.SOUTH);add(footer,BorderLayout.SOUTH);
        for(ReplayFrame frame:frames)maxTime=Math.max(maxTime,frame.elapsedMillis);
        maxTime=Math.max(maxTime,session.elapsedMillis);
        timeline.setToolTipText(ReplayText.text("timeline"));timeline.setEnabled(frames.size()>1||maxTime>0);timeline.addChangeListener(e->{if(!updating)snapToTime(Math.round(timeline.getValue()/1000000.0*maxTime));});
        playback=new javax.swing.Timer(40,e->advancePlayback());
        addMouseListener(new MouseAdapter(){});addMouseMotionListener(new MouseMotionAdapter(){});
        display(0);
    }
    private static JButton button(String glyph,String label){JButton b=new JButton(glyph);b.setToolTipText(ReplayText.text(label));b.getAccessibleContext().setAccessibleName(ReplayText.text(label));return b;}
    public JPanel actionBar(){return actions;}
    public ReplaySession session(){return session;}
    public int frameIndex(){return index;}
    public ReplayFrame selectedFrame(){return frames.isEmpty()?null:frames.get(index);}
    public SudokuPanel boardPanel(){return board;}
    public JSlider timeline(){return timeline;}
    public boolean isPlaying(){return playing;}
    public boolean isBoardInput(Component component){return component==board||SwingUtilities.isDescendingFrom(component,board);}
    public void showFrame(int position){setPlaying(false);display(position);}
    public void jumpOperation(int direction){
        if(frames.isEmpty())return;long id=frames.get(index).operationId;int target=index;
        if(direction>0){while(target<frames.size()&&frames.get(target).operationId==id)target++;if(target>=frames.size())return;}
        else{while(target>0&&frames.get(target-1).operationId==id)target--;if(target>0){target--;long previousId=frames.get(target).operationId;while(target>0&&frames.get(target-1).operationId==previousId)target--;}}
        showFrame(target);
    }
    public void snapToTime(long millis){
        if(frames.isEmpty())return;int nearest=0;long distance=Math.abs(frames.get(0).elapsedMillis-millis);
        for(int i=0;i<frames.size();i++){long candidate=Math.abs(frames.get(i).elapsedMillis-millis);if(candidate<distance){distance=candidate;nearest=i;}}
        showFrame(nearest);
    }
    public long playbackMillis(){return cursorMillis;}
    public long durationMillis(){return maxTime;}
    public HintTextArea hintPanel(){return description;}
    public void setPlaying(boolean value){
        if(!value&&playing)advancePlayback();
        if(value&&!playing&&!frames.isEmpty()&&index==frames.size()-1&&cursorMillis>=maxTime)display(0);
        playing=value&&!frames.isEmpty()&&(index<frames.size()-1||cursorMillis<maxTime);
        play.setText(playing?"Ⅱ":"▶");play.setToolTipText(ReplayText.text(playing?"pause":"play"));
        play.getAccessibleContext().setAccessibleName(ReplayText.text(playing?"pause":"play"));
        if(playing){segmentStart=cursorMillis;playbackStart=nanoTime.getAsLong();playback.start();}else playback.stop();
    }
    /** Continuous playback position; stored frames change only at their operation boundaries. */
    void advancePlayback(){
        if(!playing)return;
        long elapsed=Math.max(0,(nanoTime.getAsLong()-playbackStart)/1000000);
        long target=index+1<frames.size()?frames.get(index+1).elapsedMillis:maxTime;
        long span=Math.max(0,target-segmentStart);
        // Equal-time proof/apply frames retain a short viewing hold without inventing solve time.
        if(index+1<frames.size()&&span==0){
            if(elapsed<700){updateProgress();return;}
        }else{
            cursorMillis=segmentStart+Math.min(span,elapsed);updateProgress();
            if(elapsed<span)return;
        }
        if(index+1<frames.size()){
            display(index+1);segmentStart=cursorMillis;playbackStart=nanoTime.getAsLong();
        }
        if(index==frames.size()-1&&cursorMillis>=maxTime){
            playing=false;playback.stop();play.setText("▶");play.setToolTipText(ReplayText.text("play"));play.getAccessibleContext().setAccessibleName(ReplayText.text("play"));
        }
    }
    private void updateProgress(){
        position.setText(ReplayText.text("playbackPosition",index+1,frames.size(),ReplayText.time(cursorMillis),ReplayText.time(maxTime)));
        updating=true;try{timeline.setValue(maxTime==0?0:(int)Math.round(cursorMillis/(double)maxTime*1000000));}finally{updating=false;}
    }
    public void disposeViewer(){setPlaying(false);description.setReferenceText("");}
    /** Called before application-wide shortcuts; only viewing commands or focus traversal escape. */
    public boolean handleKeyEvent(KeyEvent event){
        if(event.getKeyCode()==KeyEvent.VK_TAB)return false;
        if(event.getID()==KeyEvent.KEY_PRESSED&&event.getModifiersEx()==0){switch(event.getKeyCode()){
            case KeyEvent.VK_ESCAPE:controller.closeViewer();break;
            case KeyEvent.VK_LEFT:showFrame(index-1);break;case KeyEvent.VK_RIGHT:showFrame(index+1);break;
            case KeyEvent.VK_HOME:showFrame(0);break;case KeyEvent.VK_END:showFrame(frames.size()-1);break;
            case KeyEvent.VK_ENTER:case KeyEvent.VK_SPACE:if(event.getComponent() instanceof AbstractButton)((AbstractButton)event.getComponent()).doClick();else setPlaying(!playing);break;
            default:break;
        }}
        event.consume();return true;
    }
    private void display(int positionIndex){
        if(frames.isEmpty()){description.setReferenceText(ReplayText.text("empty"));board.setVisible(false);for(JButton b:new JButton[]{first,previousGroup,previous,play,next,nextGroup,last})b.setEnabled(false);return;}
        index=Math.max(0,Math.min(frames.size()-1,positionIndex));ReplayFrame frame=frames.get(index);
        try{ReplayFrame rendered=index==0&&session.initialAnnotations().length>0?new ReplayFrame(frame.operationId,frame.wallTimeMillis,frame.elapsedMillis,"authored-input",frame.label,frame.board,session.initialAnnotations()):frame;board.displayReplayFrame(rendered);SolutionStep proof=ReplayEvidence.proof(frame.evidence());
            if(proof==null&&"apply".equals(frame.kind))for(int prior=index-1;prior>=0&&frames.get(prior).operationId==frame.operationId;prior--){proof=ReplayEvidence.proof(frames.get(prior).evidence());if(proof!=null)break;}
            frameTitle.setText(frame.label);frameTitle.setToolTipText(" "+frame.label);description.setReferenceText(proof==null?frame.label:proof.toString(2));description.setCaretPosition(0);board.setVisible(true);error.setText("");}
        catch(Exception failure){board.setVisible(false);error.setText(ReplayText.text("frameError",failure.getMessage()));description.setReferenceText(frame.label);playing=false;playback.stop();play.setText("▶");}
        cursorMillis=frame.elapsedMillis;updateProgress();position.setToolTipText(ReplayText.text("wallTime",new java.util.Date(frame.wallTimeMillis).toString()));
        first.setEnabled(index>0);previousGroup.setEnabled(index>0);previous.setEnabled(index>0);next.setEnabled(index<frames.size()-1);nextGroup.setEnabled(frames.get(index).operationId!=frames.get(frames.size()-1).operationId);last.setEnabled(index<frames.size()-1);play.setEnabled(frames.size()>1||maxTime>0);

    }
    /** Flow rows wrap instead of clipping action buttons in a narrow window. */
    static final class WrapLayout extends FlowLayout {
        WrapLayout(){super(FlowLayout.LEFT,4,3);}
        @Override public Dimension preferredLayoutSize(Container target){synchronized(target.getTreeLock()){int width=target.getParent()!=null?target.getParent().getWidth():target.getWidth();if(width<=0)width=700;Insets in=target.getInsets();int available=Math.max(1,width-in.left-in.right-getHgap()*2),x=0,rowHeight=0,height=getVgap()*2;for(Component c:target.getComponents())if(c.isVisible()){Dimension d=c.getPreferredSize();if(x>0&&x+d.width>available){height+=rowHeight+getVgap();x=0;rowHeight=0;}x+=d.width+getHgap();rowHeight=Math.max(rowHeight,d.height);}return new Dimension(width,height+rowHeight+in.top+in.bottom);}}
    }
}
