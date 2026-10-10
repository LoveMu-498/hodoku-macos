/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.nio.file.*;
import java.util.ResourceBundle;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Small UI adapter kept independent of viewer layout. File parsing never touches the live board. */
public final class ReplayFileActions {
    private static final ResourceBundle TEXT=ResourceBundle.getBundle("intl/MainFrame");
    private ReplayFileActions(){}
    private static String text(String key){return TEXT.getString("MainFrame.replayFiles."+key);}
    public static void install(JPanel controls,ReplayController controller,ReplaySession viewed){
        JButton open=new JButton(text("open")),save=new JButton(text("export"));
        open.addActionListener(e->importFile(controller));save.addActionListener(e->exportFile(controller,viewed));
        controls.add(open);controls.add(save);ReplaySharing.install(controls,controller,viewed);
        JButton windows=new JButton(text("windowsExport"));windows.addActionListener(e->exportFile(controller,viewed,true));controls.add(windows);
    }
    private static JFileChooser chooser(){JFileChooser c=new JFileChooser();c.setFileFilter(new FileNameExtensionFilter(text("type"),"hrep"));return c;}
    public static void importFile(ReplayController controller){
        if(controller.viewer()!=null)controller.viewer().setPlaying(false);
        ReplaySession imported=chooseImport(controller.owner());if(imported!=null)showImported(controller,imported);
    }
    static ReplaySession chooseImport(java.awt.Component parent){
        JFileChooser dialog=chooser();if(dialog.showOpenDialog(parent)!=JFileChooser.APPROVE_OPTION)return null;
        try{return ReplayFiles.importFile(dialog.getSelectedFile().toPath());}
        catch(Exception failure){JOptionPane.showMessageDialog(parent,text("failed")+"\n"+failure.getMessage(),text("title"),JOptionPane.ERROR_MESSAGE);return null;}
    }
    static void showImported(ReplayController controller,ReplaySession imported){controller.closeViewer();controller.openViewer(imported);}
    public static Path exportFile(ReplayController controller,ReplaySession viewed){
        return exportFile(controller,viewed,false);
    }
    static Path exportFile(ReplayController controller,ReplaySession viewed,boolean windowsV3){
        if(windowsV3&&JOptionPane.showConfirmDialog(controller.owner(),text("windowsNotice"),text("windowsExport"),JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return null;
        JFileChooser dialog=chooser();dialog.setSelectedFile(new java.io.File("HoDoKu-"+viewed.id.substring(0,Math.min(8,viewed.id.length()))+(windowsV3?"-windows-v3":"")+".hrep"));
        if(dialog.showSaveDialog(controller.owner())!=JFileChooser.APPROVE_OPTION)return null;
        Path target=dialog.getSelectedFile().toPath();if(!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".hrep"))target=target.resolveSibling(target.getFileName()+".hrep");
        if(Files.exists(target)&&JOptionPane.showConfirmDialog(controller.owner(),text("replace"),text("export"),JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;
        try{if(viewed==controller.session())controller.updateElapsed();final Path destination=target;
            SaveProgress.run(controller.owner(),false,()->{ReplaySession snapshot=viewed.persistenceSnapshot();return progress->{
                progress.update(SaveFailure.text("replay"),10);
                try{if(windowsV3)ReplayFiles.exportWindowsV3File(destination,snapshot,controller.directory());else ReplayFiles.exportFile(destination,snapshot,controller.directory());}
                catch(Exception failure){throw new SaveFailure(SaveFailure.text("replay"),destination,failure);}
                progress.update(SaveFailure.text("done"),100);
            };});
            JOptionPane.showMessageDialog(controller.owner(),text(viewed.completed?"savedComplete":"savedIncomplete")+"\n"+target.toAbsolutePath());return target;}
        catch(Exception failure){error(controller,failure);return null;}
    }
    private static void error(ReplayController controller,Exception failure){JOptionPane.showMessageDialog(controller.owner(),SaveFailure.panel(text("failed"),failure),text("title"),JOptionPane.ERROR_MESSAGE);}
}
