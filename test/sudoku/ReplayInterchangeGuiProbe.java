package sudoku;
import java.nio.file.*;
import javax.swing.*;
public final class ReplayInterchangeGuiProbe {
 static MainFrame frame;
 public static void main(String[] args)throws Exception{
  Path data=Files.createTempDirectory("replay-import-gui-");System.setProperty("hodoku.data.dir",data.toString());
  try{SwingUtilities.invokeAndWait(()->{try{
   frame=new MainFrame(null);frame.setVisible(true);ReplayController c=frame.getReplayController();ReplayBoard before=new ReplayBoard(frame.getSudokuPanel().getSudoku());String id=c.session().id;
   Path file=data.resolve("external.hrep");ReplayFiles.exportFile(file,ReplayInterchangeProbe.fixture(),c.directory());ReplaySession imported=ReplayFiles.importFile(file);
   ReplayFileActions.showImported(c,imported);ReplayInterchangeProbe.check(c.isViewing()&&c.viewer().session()==imported,"import not viewed");
   for(int n=0;n<imported.frames().size();n++)c.viewer().showFrame(n);
   ReplayInterchangeProbe.check(id.equals(c.session().id)&&before.equals(new ReplayBoard(frame.getSudokuPanel().getSudoku())),"import mutated live puzzle");
   c.closeViewer();ReplayInterchangeProbe.check(id.equals(c.session().id),"exit import changed current");frame.dispose();
  }catch(Exception e){throw new RuntimeException(e);}});System.out.println("PASS imported complete prefix displayed in native readonly viewer, all frame kinds, current puzzle and identity unchanged");}catch(Throwable t){t.printStackTrace();System.exit(1);}System.exit(0);
 }
}
