/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import javax.swing.*;
import javax.imageio.ImageIO;

/** Actual Swing renderer, branch persistence, Enter and post-branch recording in isolated data. */
public final class ReplayWindowsV3GuiProbe {
    static MainFrame frame;static int checks;static Path output;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static Object field(SudokuPanel panel,String name)throws Exception{Field f=SudokuPanel.class.getDeclaredField(name);f.setAccessible(true);return f.get(panel);}
    static BufferedImage render(SudokuPanel panel,int size,String name)throws Exception{
        panel.setSize(size,size);BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();panel.paint(g);g.dispose();ImageIO.write(image,"png",output.resolve(name+".png").toFile());return image;
    }
    static int pixels(BufferedImage image,Point center,int kind){
        int count=0;for(int y=Math.max(0,center.y-24);y<Math.min(image.getHeight(),center.y+25);y++)for(int x=Math.max(0,center.x-24);x<Math.min(image.getWidth(),center.x+25);x++){
            Color c=new Color(image.getRGB(x,y));boolean match=kind==0?c.getRed()>180&&c.getGreen()<90&&c.getBlue()<90:kind==1?c.getGreen()>180&&c.getRed()<90&&c.getBlue()<90:kind==2?c.getBlue()>180&&c.getBlue()>c.getRed()+50&&c.getBlue()>c.getGreen()+30:c.getRed()>150&&c.getBlue()>150&&c.getGreen()<90;
            if(match)count++;
        }return count;
    }
    static <T> T find(Container root,Class<T> type){for(Component c:root.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container){T child=find((Container)c,type);if(child!=null)return child;}}return null;}
    static void exportThroughDialogs(ReplayController controller)throws Exception{
        DoodleStroke cross=ReplayWindowsV3Probe.mark(3,6,2,5);StringBuilder text=new StringBuilder();ReplayAnnotations.appendStructuredMark(text,cross,5,0);
        ReplaySession source=new ReplaySession(ReplayWindowsV3Probe.board(),100);source.setInitialFrameAnnotations(new ReplayAnnotations(text.toString(),new byte[0],java.util.Collections.<Integer,Color>emptyMap(),java.util.Collections.<Integer,Color>emptyMap(),java.util.Arrays.asList(cross),5));
        controller.openViewer(source);Path target=output.resolve("ui-windows-copy.hrep");java.nio.file.Files.deleteIfExists(target);
        int[] stage={0};Throwable[] failure={null};
        javax.swing.Timer timer=new javax.swing.Timer(80,e->{try{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing()){
            if(stage[0]==1){JFileChooser chooser=find((Container)w,JFileChooser.class);if(chooser!=null){chooser.setSelectedFile(target.toFile());stage[0]++;chooser.approveSelection();return;}}
            else{JOptionPane pane=find((Container)w,JOptionPane.class);if(pane!=null){if(stage[0]==0)check(pane.getMessage().toString().contains("Windows"),"compatibility notice missing");stage[0]++;pane.setValue(JOptionPane.OK_OPTION);if(stage[0]==3)((javax.swing.Timer)e.getSource()).stop();return;}}
        }}catch(Throwable t){failure[0]=t;((javax.swing.Timer)e.getSource()).stop();for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing())w.dispose();}});timer.start();
        String label=java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.replayFiles.windowsExport");JButton action=null;
        for(Component c:controller.viewer().actionBar().getComponents())if(c instanceof JButton&&((JButton)c).getText().equals(label))action=(JButton)c;
        check(action!=null,"copy button missing");action.doClick();timer.stop();if(failure[0]!=null)throw new AssertionError(failure[0]);
        check(stage[0]==3&&Files.isRegularFile(target),"native save dialogs did not write the copy");check(ReplayFiles.importFile(target).last().annotations.overlay.contains("O -65536 2 3 6 1 "),"UI called ordinary export instead of Windows profile");
    }
    public static void main(String[] args)throws Exception{
        output=Files.createDirectories(Paths.get(args[0]));System.setProperty("hodoku.data.dir",output.resolve("data").toString());java.util.Locale.setDefault(java.util.Locale.SIMPLIFIED_CHINESE);
        String text=ReplayWindowsV3Probe.MARKS+"O -65281 1 2 4 0 0.1 0.1 0.2 0.2\nMT 1\nG 2 4\nR 3 6\nM 41 0\nB 0 0 0 0 0 2 3\n";
        ReplaySession imported=ReplayFiles.importFile(ReplayWindowsV3Probe.foreign(output.resolve("windows.hrep"),text));
        Sudoku2 removed=imported.last().board.toSudoku();removed.delCandidate(2,4);
        ReplaySession source=new ReplaySession("windows-removed",100);source.foreignTimeline=true;source.elapsedMillis=10;
        source.loadFrame(new ReplayFrame(7,110,10,"foreign-method","removed candidate evidence",new ReplayBoard(removed),null,imported.last().annotations));
        try{
            SwingUtilities.invokeAndWait(()->{try{
                ApplicationAppearance.initialize(AppearanceMode.LIGHT);frame=new MainFrame(null);frame.setSize(1000,900);frame.setVisible(true);ReplayController controller=frame.getReplayController();SudokuPanel live=frame.getSudokuPanel();ReplayBoard original=new ReplayBoard(live.getSudoku());controller.openViewer(source);
                SudokuPanel view=controller.viewer().boardPanel();view.setShowCandidates(false);
                for(int size:new int[]{810,540}){
                    BufferedImage image=render(view,size,"foreign-"+size);Point circle=DoodleReplayGuiProbe.point(view,2,4),cross=DoodleReplayGuiProbe.point(view,3,6);
                    check(pixels(image,circle,0)>4,"true circle not red at semantic anchor "+size);check(pixels(image,circle,3)>4,"second unassigned color collapsed "+size);check(pixels(image,cross,1)>4,"false cross not green at semantic anchor "+size);check(pixels(image,circle,2)>3,"foreign outline absent "+size);
                    check(!view.getSudoku().isCandidate(2,4)&&new ReplayBoard(view.getSudoku()).equals(source.last().board),"view restored deleted candidate");
                }
                check(new ReplayBoard(live.getSudoku()).equals(original),"view changed live puzzle");
                boolean found;
                // UI entry and data path are checked independently of a modal save dialog.
                String export=java.util.ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.replayFiles.windowsExport");found=false;for(Component c:controller.viewer().actionBar().getComponents())if(c instanceof JButton&&((JButton)c).getText().equals(export))found=true;check(found,"Windows copy action missing");
                controller.branchFromFrame(source,0);check(new ReplayBoard(live.getSudoku()).equals(source.last().board),"branch changed exact board masks");
                check(((java.util.List<?>)field(live,"doodleStrokes")).isEmpty(),"historical glyphs became current marks");check(live.getBoxReasoningGroupsSnapshot().get(0).size()==2,"known box geometry dropped");
                check(!live.captureReplayAnnotations().overlay.contains("G 2 4")&&!live.captureReplayAnnotations().overlay.contains("R 3 6"),"historical proof coloring survived into new recording");
                live.setAnnotationTool(AnnotationTool.DOODLE);ReplayBoard before=new ReplayBoard(live.getSudoku());Method enter=SudokuPanel.class.getDeclaredMethod("handleReasoningEnter");enter.setAccessible(true);enter.invoke(live);check(new ReplayBoard(live.getSudoku()).equals(before),"Enter applied imported conclusions");
                int count=controller.session().frames().size();live.getSudoku().delCandidate(3,6);controller.afterAction();check(controller.session().frames().size()==count+1,"branch edit not recorded");
                Path next=output.resolve("branch-export.hrep");ReplayFiles.exportWindowsV3File(next,controller.session(),controller.directory());ReplaySession reopened=ReplayFiles.importFile(next);check(!reopened.last().annotations.overlay.contains("O ")&&!reopened.last().annotations.overlay.contains("MT "),"editing recapture resurrected old evidence");
                check(new ReplayBoard(live.getSudoku()).equals(reopened.last().board),"branch export changed board");controller.closeViewer();
                ReplayBoard liveBeforeSave=new ReplayBoard(live.getSudoku());exportThroughDialogs(controller);check(new ReplayBoard(live.getSudoku()).equals(liveBeforeSave),"export dialogs changed live attempt");controller.closeViewer();
            }catch(Exception failure){throw new RuntimeException(failure);}});
            System.out.println("PASS "+checks+" native Swing checks: true/cross/multicolor/outline at two sizes, removed-candidate evidence, exact branch, Enter isolation, edit/re-export and native Windows-copy save dialogs; synthetic new-Windows fixture");
        }catch(Throwable failure){failure.printStackTrace();System.exit(1);}finally{if(frame!=null)SwingUtilities.invokeAndWait(()->frame.dispose());}System.exit(0);
    }
}
