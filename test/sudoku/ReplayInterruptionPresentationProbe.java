package sudoku;
import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
/** Persisted/imported interruption remains visible independently of the current live attempt. */
public final class ReplayInterruptionPresentationProbe {
 static final String SOL="534678912672195348198342567859761423426853791713924856961537284287419635345286179";
 static MainFrame owner;
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 public static void main(String[]args)throws Exception{
  Path data=Files.createTempDirectory("replay-interruption-ui-");System.setProperty("hodoku.data.dir",data.toString());
  Locale old=Locale.getDefault();
  try{SwingUtilities.invokeAndWait(()->{try{
   owner=new MainFrame(null);ReplayController controller=owner.getReplayController();
   Sudoku2 solved=new Sudoku2();solved.setSudoku(SOL);ReplaySession completed=new ReplaySession(new ReplayBoard(solved),1000);completed.completed=true;completed.retained=true;completed.endedAt=2000;completed.interruption="private recovery diagnostic";
   Sudoku2 puzzle=new Sudoku2();puzzle.setSudoku(SOL.substring(0,80)+"0");ReplaySession unfinished=new ReplaySession(new ReplayBoard(puzzle),3000);unfinished.retained=true;unfinished.endedAt=4000;unfinished.interruption="private recovery diagnostic";
   ReplayStore.write(controller.library().path(completed),completed);ReplayStore.write(controller.library().path(unfinished),unfinished);
   Path exported=data.resolve("shared.hrep");ReplayFiles.exportFile(exported,completed,controller.directory());ReplaySession importedCompleted=ReplayFiles.importFile(exported);
   ReplayFiles.exportFile(exported,unfinished,controller.directory());ReplaySession importedUnfinished=ReplayFiles.importFile(exported);
   for(Locale locale:new Locale[]{Locale.ENGLISH,Locale.SIMPLIFIED_CHINESE,Locale.GERMAN}){
    Locale.setDefault(locale);ResourceBundle.clearCache();String interrupted=ReplayText.text("interrupted");check(!interrupted.equals("interrupted"),"missing locale");
    ReplayLibraryDialog library=new ReplayLibraryDialog(controller);JTable table=(JTable)field(library,"table");int interruptedRows=0,completeRows=0,unfinishedRows=0;
    for(int row=0;row<table.getRowCount();row++){String state=String.valueOf(table.getValueAt(row,2));if(state.contains(interrupted)){interruptedRows++;if(state.contains(ReplayText.text("completed")))completeRows++;if(state.contains(ReplayText.text("unfinished")))unfinishedRows++;}}
    check(interruptedRows==2&&completeRows==1&&unfinishedRows==1,"history hides interrupted completion/unfinished state "+locale);library.dispose();
    for(ReplaySession record:new ReplaySession[]{completed,unfinished,importedCompleted,importedUnfinished,controller.session()}){
     ReplayViewer viewer=new ReplayViewer(controller,record);JTextArea notice=(JTextArea)field(viewer,"interruptionNotice");
     if(record.interruption.isEmpty())check(notice.getParent()==null,"ordinary replay shows warning");
     else{check(notice.getParent()!=null&&notice.getText().equals(ReplayText.text("interruptionNotice",ReplayText.text(record.completed?"completed":"unfinished"))),"viewer hides imported/complete interruption "+locale);check(!notice.getText().contains("private"),"diagnostic leaked into public wording");viewer.showFrame(0);check(!notice.getText().isEmpty(),"navigation erased warning");}
     viewer.setSize(680,760);viewer.doLayout();if(locale.equals(Locale.SIMPLIFIED_CHINESE)&&record==importedCompleted){java.awt.image.BufferedImage im=new java.awt.image.BufferedImage(680,760,1);layout(viewer);viewer.paint(im.getGraphics());javax.imageio.ImageIO.write(im,"png",data.resolve("imported-completed.png").toFile());}viewer.disposeViewer();
    }
    controller.session().interruption="active interruption";
    ReplayViewer current=new ReplayViewer(controller,controller.session());JTextArea currentNotice=(JTextArea)field(current,"interruptionNotice");check(currentNotice.getText().equals(ReplayText.text("interruptionNotice",ReplayText.text("current"))),"active record lost current/abnormal composition");current.disposeViewer();
    ReplayLibraryDialog currentLibrary=new ReplayLibraryDialog(controller);JTable currentTable=(JTable)field(currentLibrary,"table");boolean currentFlag=false;
    for(int row=0;row<currentTable.getRowCount();row++){String state=String.valueOf(currentTable.getValueAt(row,2));if(state.contains(ReplayText.text("current"))&&state.contains(interrupted))currentFlag=true;}check(currentFlag,"library lost current interruption");currentLibrary.dispose();controller.session().interruption="";
   }
   System.out.println("PASS EN/ZH/DE library completed/unfinished interruption and imported/native viewer disclosure; ordinary record unchanged: "+data);
  }catch(Exception e){throw new RuntimeException(e);}});}catch(Throwable e){e.printStackTrace();System.exit(1);}finally{Locale.setDefault(old);ResourceBundle.clearCache();if(owner!=null)SwingUtilities.invokeAndWait(()->owner.dispose());}System.exit(0);
 }
 static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child);}
}
