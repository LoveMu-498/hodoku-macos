package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;

/** Current-input search hosted by the original Mac technique selector popup. */
final class CurrentReasoningMenu extends JPopupMenu {
    private final MainFrame frame;
    private final SudokuPanel panel;
    private final ResourceBundle bundle = ResourceBundle.getBundle("intl/MainFrame");
    private final JPanel body = new JPanel(new BorderLayout(0, 6));
    private final JPanel results = new JPanel(new BorderLayout());
    private final Map<SolutionStep, Integer> masks = new IdentityHashMap<SolutionStep, Integer>();
    private final Set<SolutionStep> verified = Collections.newSetFromMap(new IdentityHashMap<SolutionStep, Boolean>());
    private final Map<Integer, List<Color>> colors = new HashMap<Integer, List<Color>>();
    private List<UserChain> chainInputs = Collections.emptyList();
    private SudokuSet boxInput;
    private Set<Integer> selectionInput;
    private Set<Integer> coloredCells, coloredCandidates;
    private UserChain selectedChain;
    private String identity;
    private int digit, generation;
    private int listFilter;
    private final boolean quick;
    private Thread worker;
    private final javax.swing.Timer freshness;
    private boolean closed;
    private JComponent selector;

    CurrentReasoningMenu(MainFrame frame) {this(frame,false);}
    boolean isQuick(){return quick;}
    CurrentReasoningMenu(MainFrame frame,boolean quick) {
        this.quick=quick;this.frame = frame; panel = frame.getSudokuPanel();
        setName("currentReasoningMenu");
        setLightWeightPopupEnabled(false);
        body.add(results, BorderLayout.CENTER); add(body);
        addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) { SwingUtilities.invokeLater(() -> frame.updateTechniqueMenuOpacity(CurrentReasoningMenu.this, frame.isTechniqueOptionHeld())); }
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) { release(); }
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) { release(); }
        });
        freshness = new javax.swing.Timer(250, e -> {
            if (identity != null && !identity.equals(panel.currentReasoningInputIdentity())) {
                generation++; if (worker != null) worker.interrupt();
                if (selector != null) { selector.setEnabled(false); selector.setToolTipText(text("stale")); }
            }
        });
        freshness.start(); scan();
    }

    JComponent selector() { return selector; }
    private String text(String key) { return bundle.getString("MainFrame.currentReasoning." + key); }
    private void captureInputs() {
        identity = panel.currentReasoningInputIdentity(); digit = panel.currentReasoningDigit();
        boxInput = panel.getBoxReasoningFootprint(); chainInputs = panel.currentReasoningChains();
        selectedChain = UserChainAssembly.assemble(chainInputs).chain;
        selectionInput = panel.getSelectedCellsForTechniqueMatching();
        coloredCells=panel.currentReasoningColoredCells();coloredCandidates=panel.currentReasoningColoredCandidates();
        if(quick) {
            if(!chainInputs.isEmpty() || !boxInput.isEmpty() || !coloredCells.isEmpty() || !coloredCandidates.isEmpty())listFilter=8;
            else if(digit>0)listFilter=2;
            else if(panel.isBivalueFilterActive())listFilter=3;
            else listFilter=selectionInput.isEmpty()?7:6;
        }
        colors.clear();
        colors.put(4,Collections.singletonList(new Color(160,104,56)));
        colors.put(0, Collections.singletonList(new Color(30, 132, 220)));
        colors.put(1, Collections.singletonList(new Color(168, 80, 201)));
        colors.put(2, Collections.singletonList(new Color(45, 153, 95)));
        colors.put(3, Collections.singletonList(new Color(225, 148, 24)));

    }
    private void scan() {
        if (closed) return;
        final int request = ++generation;
        if (worker != null) worker.interrupt();
        captureInputs();
        masks.clear(); verified.clear(); selector = null; results.removeAll();
        JLabel pending = new JLabel(text("searching"), SwingConstants.CENTER);
        pending.setPreferredSize(new Dimension(610, 56));
        results.add(pending); results.add(modeHeader(), BorderLayout.NORTH); frame.refreshTechniquePopupLayout(this);
        final int selectedDigit = digit;
        final int filter=listFilter;
        final Set<Integer> markedCells=new TreeSet<Integer>(coloredCells),markedCandidates=new TreeSet<Integer>(coloredCandidates);
        final Sudoku2 snapshot = panel.getSudoku().clone();
        final UserChain chain = selectedChain;
        final Set<Integer> cells = new TreeSet<Integer>(), nodes = new TreeSet<Integer>();
        final Set<Integer> selectedCells = new TreeSet<Integer>(selectionInput);
        for (int i = 0; i < boxInput.size(); i++) cells.add(boxInput.get(i));
        for (UserChain input : chainInputs) for (UserChainNode n : input.getNodes()) for(int cell:n.cells())nodes.add(cell * 10 + n.getCandidate());
        worker = new Thread(() -> {
            List<SolutionStep> found = new ArrayList<SolutionStep>();
            Map<SolutionStep, Integer> related = new IdentityHashMap<SolutionStep, Integer>();
            Set<SolutionStep> proved = Collections.newSetFromMap(new IdentityHashMap<SolutionStep, Boolean>());
            UserChainValidator.Result validation = null; Throwable failure = null; int unsupported = 0;
            try {
                if (chain != null) {
                    validation = UserChainValidator.validate(snapshot, chain);
                    for (SolutionStep step : validation.steps) { if(filter!=8&&!acceptContext(step,snapshot,cells,nodes,selectedCells,selectedDigit,filter))continue; found.add(step); related.put(step, relationMask(step, snapshot, cells, nodes, selectedCells, selectedDigit) | 2); proved.add(step); }
                }
                for (SolutionStep step : (filter == 8
                        ? frame.getTechniqueStepCatalog().findMatchingSteps(snapshot,
                            step -> acceptAnnotations(step,snapshot,cells,nodes,markedCells,markedCandidates))
                        : filter == 1 ? frame.getTechniqueStepCatalog().findBoxSteps(snapshot, cells)
                        : frame.getTechniqueStepCatalog().findAllRawSteps(snapshot, null))) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (!SudokuPanel.isNativeConclusionExecutable(step, snapshot) || !(filter==8?acceptAnnotations(step,snapshot,cells,nodes,markedCells,markedCandidates):acceptContext(step,snapshot,cells,nodes,selectedCells,selectedDigit,filter))) continue;
                    ReasoningStepIndex index = ReasoningStepIndex.from(step, snapshot);
                    int mask = relationMask(step, snapshot, cells, nodes, selectedCells, selectedDigit);
                    if(!Collections.disjoint(index.premiseCells,markedCells)||!Collections.disjoint(index.conclusionCells,markedCells)||!Collections.disjoint(index.premiseNodes,markedCandidates)||!Collections.disjoint(index.conclusionNodes,markedCandidates))mask|=16;
                    if (!index.supported) unsupported++;
                    found.add(step); related.put(step, mask);
                }
            } catch (Throwable error) { failure = error; }
            if (Thread.currentThread().isInterrupted()) return;
            final Throwable error = failure; final int missing = unsupported; 
            SwingUtilities.invokeLater(() -> {
                if (closed || request != generation || !identity.equals(panel.currentReasoningInputIdentity())) return;
                results.removeAll();
                if (error != null) { results.add(modeHeader(), BorderLayout.NORTH); results.add(new JLabel(text("failed"))); frame.refreshTechniquePopupLayout(this); return; }
                masks.putAll(related); verified.addAll(proved);
                found.sort(Comparator.comparingInt((SolutionStep s) -> s.getType().getStepConfig().getIndex()));
                Map<SolutionType, List<SolutionStep>> grouped = new LinkedHashMap<SolutionType, List<SolutionStep>>();
                for (SolutionStep step : found) grouped.computeIfAbsent(step.getType(), k -> new ArrayList<SolutionStep>()).add(step);
                if (found.isEmpty()) {
                    if(quick)OperationSoundPlayer.play(OperationSoundPlayer.Sound.NO_RESULTS);
                    JLabel empty = new JLabel(text(quick?"quick.noResults":"empty")); empty.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8)); results.add(empty); results.add(modeHeader(), BorderLayout.NORTH);
                } else { selector = frame.createCurrentReasoningSelector(this, grouped); results.add(selector); }
                if (selector != null) selector.setToolTipText(MessageFormat.format(text("menuCount"), found.size())
                        + (missing > 0 ? " " + text("partial") : ""));
                results.invalidate();body.invalidate();
                frame.refreshTechniquePopupLayout(this);
            });
        }, "hodoku-current-reasoning");
        worker.setDaemon(true); worker.start();
    }
    JComponent modeHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.add(frame.createTechniqueModeSwitch(this, true), BorderLayout.NORTH);
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 2));
        legend.setName("reasoningLegend");
        String[] names = {"box", "chain", "digit", "selection", "coloring"};
        for (int i : new int[]{0, 1, 4, 3, 2}) {
            JLabel label = new JLabel(text(names[i]), badgeIcon(1 << i), SwingConstants.LEADING);
            legend.add(label);
        }
        header.add(legend, BorderLayout.SOUTH);
        if(quick) {
            String scope=listFilter==8?annotationScope():listFilter==1?text("filter.box"):listFilter==2?text("filter.digit")+" "+digit:listFilter==3?text("filter.xy"):listFilter==5?text("chain"):listFilter==6?text("selection"):text("quick.empty");
            header.add(new JLabel(text("quick.title")+" · "+scope),BorderLayout.CENTER);
        }else {
            JPanel choices=new JPanel(new FlowLayout(FlowLayout.LEADING,4,0));choices.setName("reasoningListFilter");ButtonGroup group=new ButtonGroup();
            String[] labels={text("filter.all"),text("filter.box"),text("filter.digit"),text("filter.xy")};
            for(int i=0;i<labels.length;i++) {
                final int choice=i;JRadioButton button=new JRadioButton(labels[i],i==listFilter);button.setName("reasoningFilter"+i);
                button.setEnabled(i!=1||boxInput!=null&&!boxInput.isEmpty());if(i==2)button.setEnabled(digit>0);
                if(!button.isEnabled())button.setToolTipText(text("filter.missing"));
                button.addActionListener(e->{if(choice!=listFilter){listFilter=choice;SwingUtilities.invokeLater(()->{if(!closed)scan();});}});
                group.add(button);choices.add(button);
            }
            header.add(choices,BorderLayout.CENTER);
        }
        return header;
    }

    private String annotationScope() {
        List<String> names=new ArrayList<String>();
        if(!boxInput.isEmpty())names.add(text("box"));
        if(!chainInputs.isEmpty())names.add(text("chain"));
        if(!coloredCells.isEmpty()||!coloredCandidates.isEmpty())names.add(text("coloring"));
        return String.join(" / ",names);
    }
    static boolean acceptAnnotations(SolutionStep step,Sudoku2 board,Set<Integer> boxes,Set<Integer> chain,Set<Integer> coloredCells,Set<Integer> coloredCandidates) {
        ReasoningStepIndex index=ReasoningStepIndex.from(step,board);
        if(!index.supported)return false;
        Set<Integer> cells=new HashSet<Integer>(index.premiseCells);cells.addAll(index.conclusionCells);
        Set<Integer> nodes=new HashSet<Integer>(index.premiseNodes);nodes.addAll(index.conclusionNodes);
        return !boxes.isEmpty()&&cells.containsAll(boxes)
                || !chain.isEmpty()&&nodes.containsAll(chain)
                || (!coloredCells.isEmpty()||!coloredCandidates.isEmpty())&&cells.containsAll(coloredCells)&&nodes.containsAll(coloredCandidates);
    }

    static boolean acceptContext(SolutionStep step,Sudoku2 board,Set<Integer> boxes,Set<Integer> chain,Set<Integer> selection,int digit,int filter) {
        if(filter==7)return false;
        if(filter==5){ReasoningStepIndex i=ReasoningStepIndex.from(step,board);Set<Integer> nodes=new HashSet<Integer>(i.premiseNodes);nodes.addAll(i.conclusionNodes);return i.supported&&!chain.isEmpty()&&nodes.containsAll(chain);}
        if(filter==6){ReasoningStepIndex i=ReasoningStepIndex.from(step,board);Set<Integer> cells=new HashSet<Integer>(i.premiseCells);cells.addAll(i.conclusionCells);return !Collections.disjoint(cells,selection);}
        return acceptFilter(step,board,boxes,digit,filter);
    }

    static boolean acceptFilter(SolutionStep step,Sudoku2 board,Set<Integer> cells,int digit,int filter) {
        if(filter==0)return true;
        if(filter==1){if(cells.isEmpty())return false;ReasoningStepIndex i=ReasoningStepIndex.from(step,board);Set<Integer> involved=new HashSet<Integer>(i.premiseCells);involved.addAll(i.conclusionCells);return i.supported&&involved.containsAll(cells);}
        if(filter==3)return EnumSet.of(SolutionType.XY_WING,SolutionType.XYZ_WING,SolutionType.W_WING,SolutionType.XY_CHAIN,SolutionType.REMOTE_PAIR,SolutionType.ALS_XY_WING,SolutionType.ALS_XY_CHAIN).contains(step.getType());
        if(digit<=0)return false;
        if(ReasoningStepIndex.singleDigit(step,digit))return true;
        ReasoningStepIndex index=ReasoningStepIndex.from(step,board);
        if(index.supported&&!index.premiseNodes.isEmpty()&&!index.conclusionNodes.isEmpty()) {
            Set<Integer> used=new HashSet<Integer>(index.premiseNodes);used.addAll(index.conclusionNodes);
            boolean same=true;for(int node:used)if(node%10!=digit)same=false;
            if(same)return true;
        }
        SolutionType t=step.getType();
        if(!t.isFish() && t!=SolutionType.X_CHAIN && t!=SolutionType.SIMPLE_COLORS_TRAP && t!=SolutionType.SIMPLE_COLORS_WRAP)return false;
        if(step.getCandidatesToDelete().isEmpty())return false;
        for(Candidate c:step.getCandidatesToDelete())if(c.getValue()!=digit)return false;
        return true;
    }

    static int relationMask(SolutionStep step, Sudoku2 board, Set<Integer> boxes,
            Set<Integer> chains, Set<Integer> selected, int digit) {
        ReasoningStepIndex index = ReasoningStepIndex.from(step, board);
        Set<Integer> cells = new HashSet<Integer>(index.premiseCells);cells.addAll(index.conclusionCells);
        Set<Integer> nodes = new HashSet<Integer>(index.premiseNodes);nodes.addAll(index.conclusionNodes);
        return (!Collections.disjoint(cells, boxes) ? 1 : 0)
                | (!Collections.disjoint(nodes, chains) ? 2 : 0)
                | (digit > 0 && ReasoningStepIndex.singleDigit(step, digit) ? 4 : 0)
                | (!Collections.disjoint(cells, selected) ? 8 : 0);
    }

    Icon badgeIcon(final int mask) {
        return new Icon() {
            public int getIconWidth() { return Integer.bitCount(mask & 31) * 15; }
            public int getIconHeight() { return 14; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int offset=0;
                for(int bit:new int[]{0,1,4,3,2}) if((mask & (1<<bit))!=0) {
                    g.setColor(Color.WHITE);g.fillOval(x+offset,y+1,12,12);
                    g.setColor(colors.get(bit).get(0));g.fillOval(x+offset+1,y+2,10,10);
                    g.setColor(new Color(0,0,0,90));g.drawOval(x+offset+1,y+2,10,10);offset+=15;
                }
                g.dispose();
            }
        };
    }

    int relatedMask(SolutionStep step) { Integer mask = masks.get(step); return mask == null ? 0 : mask; }
    int priority(SolutionStep step) { return verified.contains(step) ? -1 : 0; }
    String badges(int mask) {
        StringBuilder out = new StringBuilder("<font size='-1'>");
        for (int i = 0; i < 5; i++) if ((mask & 1 << i) != 0) {
            for (Color color : colors.get(i)) out.append("<font color='#").append(String.format("%06x", color.getRGB() & 0xffffff)).append("'>●</font>");
            out.append("&nbsp;");
        }
        return out.append("</font>").toString();
    }
    String relatedDescription(int mask) {
        List<String> names = new ArrayList<String>();
        if ((mask & 1) != 0) names.add(text("box"));
        if ((mask & 2) != 0) names.add(text("chain"));
        if ((mask & 4) != 0) names.add(text("digit") + " " + digit);
        if ((mask & 8) != 0) names.add(text("selection"));
        if ((mask & 16) != 0) names.add(text("coloring"));
        return String.join(" · ", names);
    }
    void describe(SolutionStep step) {
        if (selector != null) selector.setToolTipText(verified.contains(step) ? text("verified")
                : relatedDescription(relatedMask(step)) + " · " + text("native"));
    }
    void confirm(SolutionStep step, int instance, int count) {
        if (closed || identity == null || !identity.equals(panel.currentReasoningInputIdentity()) || !masks.containsKey(step)) return;
        SudokuSet boxes = step.getType().isBasicFish() && !boxInput.isEmpty() ? boxInput.clone() : null;
        UserChain chain = verified.contains(step) ? selectedChain : null;
        boolean proof = verified.contains(step);
        dispose(); frame.selectCurrentReasoning(step, boxes, chain, proof, instance, count);
    }
    void dispose() { frame.updateTechniqueMenuOpacity(this, false); setVisible(false); release(); }
    private void release() {
        if (closed) return; frame.updateTechniqueMenuOpacity(this, false); closed = true; generation++;
        if (worker != null) worker.interrupt(); freshness.stop(); frame.currentReasoningMenuClosed(this);
    }
}
