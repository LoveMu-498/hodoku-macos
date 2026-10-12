/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.event.*;

/** Managed history, with visible save failures and separate pin capacity. */
public final class ReplayLibraryDialog extends JDialog {
    private final ReplayController controller;
    private final JTable table=new JTable(){
        @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer,int row,int column){
            Component cell=super.prepareRenderer(renderer,row,column);
            if(!isRowSelected(row))cell.setBackground(row%2==0?getBackground():blend(getBackground(),getForeground(),.025));
            return cell;
        }
    };
    private final JLabel status=new JLabel(" "),summary=new JLabel();
    private final JButton open=new JButton(java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.replayFiles.open"));
    private final JButton view=new JButton(ReplayText.text("view")),pin=new JButton(ReplayText.text("pinToggle"));
    private boolean readFailed;
    private List<ReplaySession> records=new ArrayList<ReplaySession>();
    public ReplayLibraryDialog(ReplayController controller){
        super(controller.owner(),ReplayText.text("library"),true);this.controller=controller;
        status.putClientProperty("html.disable",Boolean.TRUE);
        setLayout(new BorderLayout(12,12));setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content=new JPanel(new BorderLayout(12,12));content.setBorder(BorderFactory.createEmptyBorder(16,16,12,16));setContentPane(content);
        JLabel title=new JLabel(ReplayText.text("library"));title.setFont(title.getFont().deriveFont(Font.BOLD,18f));
        summary.setForeground(UIManager.getColor("Label.disabledForeground"));summary.setBorder(BorderFactory.createEmptyBorder(4,0,0,0));
        JPanel heading=new JPanel(new BorderLayout());heading.add(title,BorderLayout.NORTH);heading.add(summary,BorderLayout.SOUTH);
        open.setIcon(new ReplayIcon(ReplayIcon.Kind.OPEN,16));JPanel header=new JPanel(new BorderLayout(12,0));header.add(heading,BorderLayout.CENTER);JPanel importActions=new JPanel(new FlowLayout(FlowLayout.RIGHT,0,4));importActions.add(open);header.add(importActions,BorderLayout.EAST);content.add(header,BorderLayout.NORTH);
        table.setRowHeight(34);table.setIntercellSpacing(new Dimension(0,0));table.setShowGrid(false);table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.getSelectionModel().addListSelectionListener(e->updateActions());
        table.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){
            if(!SwingUtilities.isLeftMouseButton(e)||e.getClickCount()!=2)return;
            int row=table.rowAtPoint(e.getPoint()),column=table.columnAtPoint(e.getPoint());if(row<0||column<0)return;
            table.setRowSelectionInterval(row,row);
            if(table.convertColumnIndexToModel(column)==5)togglePin();else watch();
        }});
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"watchReplay");
        table.getActionMap().put("watchReplay",new AbstractAction(){public void actionPerformed(ActionEvent e){watch();}});
        JScrollPane scroll=new JScrollPane(table);scroll.setBorder(BorderFactory.createLineBorder(table.getGridColor()));content.add(scroll,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(8,8));JLabel hint=new JLabel(ReplayText.text("libraryHint"));hint.setForeground(UIManager.getColor("Label.disabledForeground"));footer.add(hint,BorderLayout.NORTH);
        JButton retry=new JButton(ReplayText.text("retry")),close=new JButton(ReplayText.text("close"));JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));buttons.add(retry);buttons.add(pin);buttons.add(view);buttons.add(close);
        JPanel bottom=new JPanel(new BorderLayout(8,0));bottom.add(status,BorderLayout.CENTER);bottom.add(buttons,BorderLayout.EAST);footer.add(bottom,BorderLayout.SOUTH);content.add(footer,BorderLayout.SOUTH);
        retry.addActionListener(e->{controller.retrySaves();refresh();});close.addActionListener(e->dispose());
        view.addActionListener(e->watch());pin.addActionListener(e->togglePin());
        open.addActionListener(e->{ReplaySession imported=ReplayFileActions.chooseImport(this);if(imported!=null){dispose();ReplayFileActions.showImported(controller,imported);}});
        getRootPane().setDefaultButton(view);getRootPane().registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),JComponent.WHEN_IN_FOCUSED_WINDOW);
        refresh();setSize(940,460);setMinimumSize(new Dimension(760,340));setLocationRelativeTo(controller.owner());
    }
    private static Color blend(Color background,Color foreground,double amount){return new Color((int)(background.getRed()*(1-amount)+foreground.getRed()*amount),(int)(background.getGreen()*(1-amount)+foreground.getGreen()*amount),(int)(background.getBlue()*(1-amount)+foreground.getBlue()*amount));}
    private void watch(){if(!view.isEnabled())return;ReplaySession s=selected();if(s!=null){dispose();controller.closeViewer();controller.openViewer(s);}}
    private void togglePin(){
        ReplaySession s=selected();if(s==null||!pin.isEnabled())return;
        if(s.pinned&&JOptionPane.showConfirmDialog(this,ReplayText.text("unpinWarning"),ReplayText.text("unpin"),JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try{controller.pinRecord(s,!s.pinned);}catch(Exception failure){JOptionPane.showMessageDialog(this,failure.getMessage(),ReplayText.text("library"),JOptionPane.WARNING_MESSAGE);}refresh();
    }
    private void updateActions(){ReplaySession s=selected();view.setEnabled(s!=null&&!readFailed);pin.setEnabled(s!=null&&!readFailed);pin.setText(ReplayText.text(s!=null&&s.pinned?"unpin":"pin"));pin.setIcon(new ReplayIcon(ReplayIcon.Kind.PIN,16,s!=null&&s.pinned));}
    private ReplaySession selected(){int row=table.getSelectedRow();return row<0||row>=table.getRowCount()||table.convertRowIndexToModel(row)>=records.size()?null:records.get(table.convertRowIndexToModel(row));}
    public void refresh(){
        ReplaySession prior=selected();String selectedId=prior==null?null:prior.id;
        try{records=controller.records();DefaultTableModel model=new DefaultTableModel(new String[]{ReplayText.text("started"),ReplayText.text("initial"),ReplayText.text("state"),ReplayText.text("elapsed"),ReplayText.text("markers"),ReplayText.text("pin")},0){@Override public boolean isCellEditable(int row,int column){return false;} @Override public Class<?> getColumnClass(int column){return column==4?Integer.class:column==5?Boolean.class:String.class;}};
            SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for(ReplaySession item:records){int clues=0,answers=0;ReplayBoard initial=item.frames().get(0).board;int[] values=initial.values();boolean[] fixed=initial.fixed();for(int i=0;i<81;i++)if(values[i]!=0){if(fixed[i])clues++;else answers++;}
                long seconds=item.elapsedMillis/1000;String state=controller.hasPendingSave(item.id)?ReplayText.text("pending"):item.completed?ReplayText.text("completed"):item.id.equals(controller.session().id)?ReplayText.text("current"): ReplayText.text("unfinished");
                if(!item.interruption.isEmpty())state+= " · "+ReplayText.text("interrupted");
                model.addRow(new Object[]{date.format(new Date(item.startedAt)),ReplayText.text("origin",clues,answers),state,String.format("%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60),item.bookmarks().size(),item.pinned});}
            readFailed=false;table.setModel(model);configureColumns();
            int selected=records.isEmpty()?-1:0,pinned=0;for(int i=0;i<records.size();i++){if(records.get(i).id.equals(selectedId))selected=i;if(records.get(i).pinned)pinned++;}
            if(selected>=0){int row=table.convertRowIndexToView(selected);table.setRowSelectionInterval(row,row);table.scrollRectToVisible(table.getCellRect(row,0,true));}updateActions();
            summary.setText(ReplayText.text("libraryCount",records.size(),pinned));
            status.setText(controller.lastError()!=null?controller.lastError():controller.library().warnings().isEmpty()?ReplayText.text("quota"): ReplayText.text("corrupt"));
            status.setToolTipText(status.getText()+" "+controller.library().warnings().toString());
        }catch(Exception e){readFailed=true;status.setText(ReplayText.text("readError",e.getMessage()));view.setEnabled(false);pin.setEnabled(false);}
    }
    private void configureColumns(){
        int[] widths={185,160,220,110,75,56};for(int c=0;c<widths.length;c++)table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        for(int c=0;c<5;c++){
            final int column=c;DefaultTableCellRenderer renderer=new DefaultTableCellRenderer(){
                @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int col){
                    super.getTableCellRendererComponent(table,value,selected,focus,row,col);if(column==3)setFont(new Font(Font.MONOSPACED,Font.PLAIN,table.getFont().getSize()));setBorder(BorderFactory.createCompoundBorder(getBorder(),BorderFactory.createEmptyBorder(0,10,0,10)));setToolTipText(String.valueOf(value));return this;
                }};
            renderer.putClientProperty("html.disable",Boolean.TRUE);renderer.setHorizontalAlignment(c==3?SwingConstants.RIGHT:c==4?SwingConstants.CENTER:SwingConstants.LEFT);
            if(column==3)renderer.setFont(new Font(Font.MONOSPACED,Font.PLAIN,table.getFont().getSize()));table.getColumnModel().getColumn(c).setCellRenderer(renderer);
        }
        javax.swing.table.TableColumn pinned=table.getColumnModel().getColumn(5);pinned.setMinWidth(56);pinned.setMaxWidth(56);
        pinned.setCellRenderer(new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column){
                super.getTableCellRendererComponent(table,"",selected,focus,row,column);boolean active=Boolean.TRUE.equals(value);setIcon(new ReplayIcon(ReplayIcon.Kind.PIN,18,active));setHorizontalAlignment(SwingConstants.CENTER);setToolTipText(ReplayText.text("pinDoubleClick"));getAccessibleContext().setAccessibleName(ReplayText.text(active?"pinned":"pin"));return this;
            }});
    }
}
