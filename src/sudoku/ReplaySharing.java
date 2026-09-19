package sudoku;

import java.io.*;
import java.nio.file.*;
import java.util.ResourceBundle;
import javax.swing.*;

/** Bundled Cocoa process bridge. User-exported files survive all service outcomes. */
public final class ReplaySharing {
    private static final ResourceBundle TEXT=ResourceBundle.getBundle("intl/MainFrame");
    private static Process active;
    private ReplaySharing(){}
    private static String text(String key){return TEXT.getString("MainFrame.replayShare."+key);}
    public static void install(JPanel controls,ReplayController controller,ReplaySession viewed){
        JButton share=new JButton(text("button"));share.addActionListener(e->{
            synchronized(ReplaySharing.class){if(active!=null&&active.isAlive()){JOptionPane.showMessageDialog(controller.owner(),text("alreadyOpen"));return;}}
            Path file=ReplayFileActions.exportFile(controller,viewed);if(file!=null)share(controller,file);
        });controls.add(share);
    }
    static Path helper()throws Exception{
        String override=System.getProperty("hodoku.replay.shareHelper");if(override!=null)return Paths.get(override);
        Path jar=Paths.get(ReplaySharing.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return jar.getParent().getParent().resolve("MacOS").resolve("HoDoKuShare");
    }
    static Process launch(Path executable,Path file)throws IOException{
        if(!Files.isRegularFile(executable)||!Files.isExecutable(executable))throw new IOException("Sharing helper unavailable");
        if(!Files.isRegularFile(file)||!Files.isReadable(file))throw new IOException("Replay file unavailable");
        return new ProcessBuilder(executable.toAbsolutePath().toString(),file.toAbsolutePath().toString()).redirectErrorStream(true).start();
    }
    public static void share(ReplayController controller,Path file){
        final Process process;
        try{synchronized(ReplaySharing.class){if(active!=null&&active.isAlive())return;process=launch(helper(),file);active=process;}}
        catch(Exception ex){JOptionPane.showMessageDialog(controller.owner(),text("fallback")+"\n"+file.toAbsolutePath(),text("title"),JOptionPane.WARNING_MESSAGE);return;}
        SwingWorker<Integer,Void> worker=new SwingWorker<Integer,Void>(){
            protected Integer doInBackground()throws Exception{
                boolean completed=false;try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){String line;while((line=reader.readLine())!=null)if(line.equals("COMPLETED"))completed=true;}
                int code=process.waitFor();return code==0&&!completed?5:code;
            }
            protected void done(){synchronized(ReplaySharing.class){if(active==process)active=null;}
                try{int result=get();if(result==2)return;JOptionPane.showMessageDialog(controller.owner(),text(result==0?"completed":"fallback")+(result==0?"":"\n"+file.toAbsolutePath()),text("title"),result==0?JOptionPane.INFORMATION_MESSAGE:JOptionPane.WARNING_MESSAGE);}
                catch(Exception e){JOptionPane.showMessageDialog(controller.owner(),text("fallback")+"\n"+file.toAbsolutePath());}
            }
        };worker.execute();
    }
}
