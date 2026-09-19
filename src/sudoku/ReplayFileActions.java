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
    }
    private static JFileChooser chooser(){JFileChooser c=new JFileChooser();c.setFileFilter(new FileNameExtensionFilter(text("type"),"hrep"));return c;}
    public static void importFile(ReplayController controller){
        JFileChooser dialog=chooser();if(dialog.showOpenDialog(controller.owner())!=JFileChooser.APPROVE_OPTION)return;
        try{ReplaySession imported=ReplayFiles.importFile(dialog.getSelectedFile().toPath());showImported(controller,imported);}
        catch(Exception failure){error(controller,failure);}
    }
    static void showImported(ReplayController controller,ReplaySession imported){controller.closeViewer();controller.openViewer(imported);}
    public static Path exportFile(ReplayController controller,ReplaySession viewed){
        JFileChooser dialog=chooser();dialog.setSelectedFile(new java.io.File("HoDoKu-"+viewed.id.substring(0,Math.min(8,viewed.id.length()))+".hrep"));
        if(dialog.showSaveDialog(controller.owner())!=JFileChooser.APPROVE_OPTION)return null;
        Path target=dialog.getSelectedFile().toPath();if(!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".hrep"))target=target.resolveSibling(target.getFileName()+".hrep");
        if(Files.exists(target)&&JOptionPane.showConfirmDialog(controller.owner(),text("replace"),text("export"),JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;
        try{if(viewed==controller.session())controller.updateElapsed();ReplayFiles.exportFile(target,viewed,controller.directory());JOptionPane.showMessageDialog(controller.owner(),text(viewed.completed?"savedComplete":"savedIncomplete")+"\n"+target.toAbsolutePath());return target;}
        catch(Exception failure){error(controller,failure);return null;}
    }
    private static void error(ReplayController controller,Exception failure){JOptionPane.showMessageDialog(controller.owner(),text("failed")+"\n"+failure.getMessage(),text("title"),JOptionPane.ERROR_MESSAGE);}
}
