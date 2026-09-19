package sudoku;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/** Managed history, with visible save failures and separate pin capacity. */
public final class ReplayLibraryDialog extends JDialog {
    private final ReplayController controller;
    private final JTable table=new JTable();
    private final JLabel status=new JLabel(" ");
    private final JButton view=new JButton(ReplayText.text("view")),pin=new JButton(ReplayText.text("pinToggle"));
    private List<ReplaySession> records=new ArrayList<ReplaySession>();
    public ReplayLibraryDialog(ReplayController controller){
        super(controller.owner(),ReplayText.text("library"),true);this.controller=controller;
        status.putClientProperty("html.disable",Boolean.TRUE);
        setLayout(new BorderLayout(8,8));setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        add(new JScrollPane(table),BorderLayout.CENTER);JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton retry=new JButton(ReplayText.text("retry")),close=new JButton(ReplayText.text("close"));buttons.add(status);buttons.add(retry);buttons.add(pin);buttons.add(view);buttons.add(close);add(buttons,BorderLayout.SOUTH);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.getSelectionModel().addListSelectionListener(e->{boolean selected=table.getSelectedRow()>=0;view.setEnabled(selected);pin.setEnabled(selected);});
        retry.addActionListener(e->{controller.retrySaves();refresh();});close.addActionListener(e->dispose());
        view.addActionListener(e->{ReplaySession s=selected();if(s!=null){dispose();controller.openViewer(s);}});
        pin.addActionListener(e->{ReplaySession s=selected();if(s==null)return;
            if(s.pinned&&JOptionPane.showConfirmDialog(this,ReplayText.text("unpinWarning"),ReplayText.text("unpin"),JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
            try{controller.pinRecord(s,!s.pinned);}catch(Exception failure){JOptionPane.showMessageDialog(this,failure.getMessage(),ReplayText.text("library"),JOptionPane.WARNING_MESSAGE);}refresh();});
        refresh();setSize(850,380);setLocationRelativeTo(controller.owner());
    }
    private ReplaySession selected(){int row=table.getSelectedRow();return row<0||row>=records.size()?null:records.get(row);}
    public void refresh(){
        try{records=controller.records();DefaultTableModel model=new DefaultTableModel(new String[]{ReplayText.text("started"),ReplayText.text("initial"),ReplayText.text("state"),ReplayText.text("elapsed"),ReplayText.text("markers"),ReplayText.text("pin")},0){@Override public boolean isCellEditable(int row,int column){return false;}};
            SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            for(ReplaySession item:records){int clues=0,answers=0;ReplayBoard initial=item.frames().get(0).board;int[] values=initial.values();boolean[] fixed=initial.fixed();for(int i=0;i<81;i++)if(values[i]!=0){if(fixed[i])clues++;else answers++;}
                long seconds=item.elapsedMillis/1000;String state=controller.hasPendingSave(item.id)?ReplayText.text("pending"):item.completed?ReplayText.text("completed"):item.id.equals(controller.session().id)?ReplayText.text("current"): ReplayText.text("unfinished");
                if(!item.interruption.isEmpty())state+= " · "+ReplayText.text("interrupted");
                model.addRow(new Object[]{date.format(new Date(item.startedAt)),ReplayText.text("origin",clues,answers),state,String.format("%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60),item.bookmarks().size(),item.pinned?ReplayText.text("pinned"):""});}
            table.setModel(model);if(!records.isEmpty())table.setRowSelectionInterval(0,0);view.setEnabled(!records.isEmpty());pin.setEnabled(!records.isEmpty());
            status.setText(controller.lastError()!=null?controller.lastError():controller.library().warnings().isEmpty()?ReplayText.text("quota"): ReplayText.text("corrupt"));
            status.setToolTipText(controller.library().warnings().toString());
        }catch(Exception e){status.setText(ReplayText.text("readError",e.getMessage()));view.setEnabled(false);pin.setEnabled(false);}
    }
}
