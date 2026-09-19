package sudoku;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.ResourceBundle;
import javax.swing.*;

/** Real mouse/keyboard paths inside modal replay file dialogs; no synthetic approval/cancellation. */
public final class ReplayDialogMouseProbe {
    static MainFrame frame;static ReplayController controller;static Robot robot;
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static void edt(Runnable action)throws Exception{SwingUtilities.invokeAndWait(action);}
    static <T> T find(Container root,Class<T> type){for(Component c:root.getComponents()){if(type.isInstance(c)&&c.isShowing())return type.cast(c);if(c instanceof Container){T t=find((Container)c,type);if(t!=null)return t;}}return null;}
    static AbstractButton button(Container root,String label){for(Component c:root.getComponents()){if(c instanceof AbstractButton&&c.isShowing()&&label.equalsIgnoreCase(((AbstractButton)c).getText()))return (AbstractButton)c;if(c instanceof Container){AbstractButton b=button((Container)c,label);if(b!=null)return b;}}return null;}
    static String text(String key){return ResourceBundle.getBundle("intl/MainFrame").getString(key);}
    static void click(Component c)throws Exception{check(c!=null,"missing clickable control");final Point[] p={null};edt(()->{p[0]=c.getLocationOnScreen();p[0].translate(c.getWidth()/2,c.getHeight()/2);});robot.mouseMove(p[0].x,p[0].y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();Thread.sleep(200);}
    static JFileChooser chooser()throws Exception{final JFileChooser[] found={null};for(int i=0;i<30&&found[0]==null;i++){edt(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing()){JFileChooser f=find(w,JFileChooser.class);if(f!=null)found[0]=f;}});if(found[0]==null)Thread.sleep(100);}check(found[0]!=null,"chooser did not open");return found[0];}
    static void key(int modifier,int key){robot.keyPress(modifier);robot.keyPress(key);robot.keyRelease(key);robot.keyRelease(modifier);}
    static void typeName(String name){for(char c:name.toCharArray()){int k=KeyEvent.getExtendedKeyCodeForChar(c);robot.keyPress(k);robot.keyRelease(k);}}
    static AbstractButton chooserButton(JFileChooser c,String key,String fallback){String label=UIManager.getString(key);AbstractButton b=label==null?null:button(c,label);return b==null?button(c,fallback):b;}
    static Point filePoint(JFileChooser chooser,String name)throws Exception{
        final Point[] point={null};for(int attempt=0;attempt<30&&point[0]==null;attempt++){
            edt(()->{JTable table=find(chooser,JTable.class);if(table!=null)for(int row=0;row<table.getRowCount();row++)for(int col=0;col<table.getColumnCount();col++){
                Object value=table.getValueAt(row,col);if(value instanceof java.io.File&&((java.io.File)value).getName().equals(name)){
                    Rectangle r=table.getCellRect(row,col,true);table.scrollRectToVisible(r);point[0]=table.getLocationOnScreen();point[0].translate(r.x+Math.min(40,r.width/2),r.y+r.height/2);
                }
            }});if(point[0]==null)Thread.sleep(100);
        }check(point[0]!=null,"file row not found: "+name);return point[0];
    }
    static JFileChooser open(String key)throws Exception{System.out.println("OPEN "+key);click(button(controller.viewer(),text(key)));return chooser();}
    public static void run(MainFrame target,Path out,boolean baselineOnly)throws Exception{
        frame=target;controller=frame.getReplayController();robot=new Robot();robot.setAutoDelay(75);Files.createDirectories(out);
        edt(()->{frame.setSize(1000,850);frame.setVisible(true);frame.toFront();controller.openViewer(controller.session());});
        String pid=java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        Process activate=new ProcessBuilder("/usr/bin/osascript","-e","use framework \"AppKit\"\non run argv\nset targetApp to current application's NSRunningApplication's runningApplicationWithProcessIdentifier:((item 1 of argv) as integer)\ntargetApp's activateWithOptions:3\nend run",pid).start();check(activate.waitFor()==0,"test PID activation failed");Thread.sleep(350);
        final Point[] title={null};edt(()->{frame.toFront();title[0]=frame.getLocationOnScreen();title[0].translate(frame.getWidth()/2,12);});
        robot.mouseMove(title[0].x,title[0].y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();Thread.sleep(250);
        edt(()->check(frame.isActive(),"isolated test window is not active"));
        JFileChooser first=open("MainFrame.replayFiles.open");click(chooserButton(first,"FileChooser.cancelButtonText","Cancel"));
        check(!first.isShowing(),"REAL MOUSE Cancel ignored in owned file dialog (Window mistaken for parent MainFrame)");
        if(baselineOnly)return;
        ReplayBoard live=new ReplayBoard(frame.getSudokuPanel().getSudoku());String id=controller.session().id;
        for(String key:new String[]{"MainFrame.replayFiles.export","MainFrame.replayShare.button"}){JFileChooser c=open(key);click(chooserButton(c,"FileChooser.cancelButtonText","Cancel"));check(!c.isShowing(),"Cancel ignored for "+key);}
        JFileChooser save=open("MainFrame.replayFiles.export");edt(()->save.setCurrentDirectory(out.toFile()));robot.waitForIdle();Thread.sleep(300);
        // Directory contents and target are test setup; the filename edit and approval are real input.
        JTextField name=find(save,JTextField.class);check(name!=null,"filename field absent");click(name);key(KeyEvent.VK_META,KeyEvent.VK_A);typeName("mouseexport.hrep");robot.waitForIdle();
        edt(()->check("mouseexport.hrep".equals(name.getText()),"filename keyboard input intercepted"));
        click(chooserButton(save,"FileChooser.saveButtonText","Save"));check(!save.isShowing(),"Save click ignored");
        System.out.println("SAVED via real click");Path exported=out.resolve("mouseexport.hrep");check(Files.isRegularFile(exported),"Save did not create replay");ReplayFiles.importFile(exported);
        final JOptionPane[] message={null};edt(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing()){JOptionPane p=find(w,JOptionPane.class);if(p!=null)message[0]=p;}});check(message[0]!=null,"export success dialog absent");
        click(button(message[0],UIManager.getString("OptionPane.okButtonText")));check(!message[0].isShowing(),"success OK ignored");
        JFileChooser load=open("MainFrame.replayFiles.open");edt(()->load.setCurrentDirectory(out.toFile()));robot.waitForIdle();Thread.sleep(300);
        Point file=filePoint(load,"mouseexport.hrep");robot.mouseMove(file.x,file.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle();
        edt(()->check(exported.toFile().equals(load.getSelectedFile()),"real file row selection did not select export"));
        click(chooserButton(load,"FileChooser.openButtonText","Open"));check(!load.isShowing(),"Open click ignored");
        edt(()->{check(controller.viewer().session()!=controller.session(),"Open did not show imported replay");check(id.equals(controller.session().id)&&live.equals(new ReplayBoard(frame.getSudokuPanel().getSudoku())),"dialog changed current solve");});
        System.out.println("IMPORTED via real click");
        // Exercise directory navigation by double-clicking an actual folder in the chooser list.
        Path child=Files.createDirectories(out.resolve("foldercheck"));JFileChooser navigate=open("MainFrame.replayFiles.open");edt(()->navigate.setCurrentDirectory(out.toFile()));robot.waitForIdle();Thread.sleep(300);
        final JTable[] table={null};final Point[] row={null};edt(()->{table[0]=find(navigate,JTable.class);if(table[0]!=null){for(int r=0;r<table[0].getRowCount();r++)for(int col=0;col<table[0].getColumnCount();col++){Object value=table[0].getValueAt(r,col);if(value instanceof java.io.File&&((java.io.File)value).getName().equals("foldercheck")){Rectangle rect=table[0].getCellRect(r,col,true);row[0]=table[0].getLocationOnScreen();row[0].translate(rect.x+Math.min(35,rect.width/2),rect.y+rect.height/2);}}}});
        check(row[0]!=null,"folder row not found");robot.mouseMove(row[0].x,row[0].y);for(int i=0;i<2;i++){robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);}robot.waitForIdle();Thread.sleep(500);
        edt(()->check(navigate.getCurrentDirectory().toPath().equals(child),"directory double-click ignored"));click(chooserButton(navigate,"FileChooser.cancelButtonText","Cancel"));check(!navigate.isShowing(),"Cancel after navigation ignored");
        JFileChooser create=open("MainFrame.replayFiles.export");edt(()->create.setCurrentDirectory(out.toFile()));robot.waitForIdle();Thread.sleep(300);
        click(chooserButton(create,"FileChooser.newFolderButtonText","New Folder"));
        final JDialog[] folderDialog={null};final JTextField[] folderName={null};edt(()->{for(Window w:Window.getWindows())if(w instanceof JDialog&&w.isShowing()&&find(w,JFileChooser.class)==null){JTextField f=find(w,JTextField.class);if(f!=null){folderDialog[0]=(JDialog)w;folderName[0]=f;}}});
        check(folderDialog[0]!=null,"New Folder dialog did not open");click(folderName[0]);key(KeyEvent.VK_META,KeyEvent.VK_A);typeName("246813579");robot.waitForIdle();
        edt(()->check("246813579".equals(folderName[0].getText()),"New Folder name input failed: "+folderName[0].getText()));
        System.out.println("NEW FOLDER directory="+create.getCurrentDirectory()+" active="+folderDialog[0].isActive());
        String createLabel=UIManager.getString("FileChooser.createButtonText");click(button(folderDialog[0],createLabel==null?"Create":createLabel));for(int wait=0;wait<50&&!Files.isDirectory(out.resolve("246813579"));wait++)Thread.sleep(100);
        if(!Files.isDirectory(out.resolve("246813579")))edt(()->{try{JDialog d=folderDialog[0];java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(d.getWidth(),d.getHeight(),1);Graphics2D g=img.createGraphics();d.paint(g);g.dispose();javax.imageio.ImageIO.write(img,"png",out.resolve("folder-failure.png").toFile());System.err.println("NEW FOLDER still visible="+d.isShowing()+" field="+folderName[0].getText()+" selected="+create.getCurrentDirectory());}catch(Exception e){throw new RuntimeException(e);}});
        check(Files.isDirectory(out.resolve("246813579")),"New Folder confirmation ignored");
        click(chooserButton(create,"FileChooser.cancelButtonText","Cancel"));check(!create.isShowing(),"Cancel after New Folder ignored");
        edt(()->controller.closeViewer());System.out.println("PASS real Cancel for open/export/share, filename keyboard editing, Save writes valid replay, success OK, Open import, folder double-click navigation and New Folder creation; original solve unchanged");
    }
    public static void main(String[] args)throws Exception{Path data=Files.createTempDirectory("replay-dialog-");System.setProperty("hodoku.data.dir",data.resolve("data").toString());try{edt(()->frame=new MainFrame(null));run(frame,data.resolve("exports"),args.length>0);}catch(Throwable t){t.printStackTrace();System.exit(1);}finally{if(frame!=null)edt(()->frame.dispose());}System.exit(0);}
}
