package sudoku;
import javax.swing.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
/** Real isolated child launches covering previous native snapshots and active replay boundaries. */
public final class ReplayRecoveryAuditProbe {
 static final String A="530070000600195000098000060800060003400803001700020006060000280000419005000080079";
 static final String SOL="534678912672195348198342567859761423426853791713924856961537284287419635345286179";
 static final String B=SOL.substring(0,79)+"00";
 static MainFrame f;static ReplayController r;static SudokuPanel p;
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void save()throws Exception{Method m=MainFrame.class.getDeclaredMethod("saveApplicationState");m.setAccessible(true);m.invoke(f);}
 static void point(String name){GuiState s=new GuiState(p,p.getSolver(),f.getSolutionPanel());s.get(true);s.setName(name);s.setTimestamp(new Date());f.getSavePoints().add(s);r.createSavePointMarker(name);}
 static CompletionTransition completion()throws Exception{Field x=MainFrame.class.getDeclaredField("completionTransition");x.setAccessible(true);return (CompletionTransition)x.get(f);}
 static void child(Path dir,String mode)throws Exception{
  Process process=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","--add-exports=java.desktop/com.apple.laf=ALL-UNNAMED","-cp",System.getProperty("java.class.path"),ReplayRecoveryAuditProbe.class.getName(),dir.toString(),mode).inheritIO().start();
  if(!process.waitFor(40,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("Child timeout (possible recovery modal): "+mode);}
  check(process.exitValue()==(mode.contains("crash")?7:0),"child failed "+mode+": "+process.exitValue());
 }
 public static void main(String[] args)throws Exception{
  Path dir=Paths.get(args[0]);Files.createDirectories(dir);System.setProperty("hodoku.data.dir",dir.toString());
  if(args.length==1){
   for(String scenario:new String[]{"different","same"}){Path d=dir.resolve(scenario);Files.createDirectories(d);child(d,"clean-a");child(d,"crash-"+scenario);child(d,"recover-"+scenario);}
   for(String clean:new String[]{"clean","crash"})for(String retained:new String[]{"retained","discard"}){Path d=dir.resolve("launch-"+clean+"-"+retained);Files.createDirectories(d);child(d,"prior-"+clean+"-"+retained);child(d,"launch-"+retained);}
   child(dir.resolve("sealed"),"sealed");System.out.println("PASS native prior-clean + same/different-puzzle crash reconciliation, wrong-entry restore, bookmark ownership, explicit launch retained/discard finalization, sealed post-undo savepoint");return;
  }
  String mode=args[1];
  try{SwingUtilities.invokeAndWait(()->{try{
   String launch=null;if(mode.startsWith("launch-")){Path file=dir.resolve("launch.txt");Files.write(file,B.getBytes("UTF-8"));launch=file.toString();}
   f=new MainFrame(launch);r=f.getReplayController();p=f.getSudokuPanel();
   if(mode.equals("clean-a")){
    f.setPuzzle(A);point("foreign old attempt");completion().restore(true,true,true);save();
   }else if(mode.startsWith("crash-")){
    check(f.getSavePoints().size()==1,"clean native savepoint absent");f.setPuzzle(mode.endsWith("same")?A:B);point("own durable point");
    p.setCell(mode.endsWith("same")?0:8,mode.endsWith("same")?2:7,mode.endsWith("same")?5:1);r.afterAction();r.checkpoint(false);Files.write(dir.resolve("attempt-id"),r.session().id.getBytes("UTF-8"));
   }else if(mode.startsWith("recover-")){
    check(r.session().id.equals(new String(Files.readAllBytes(dir.resolve("attempt-id")),"UTF-8")),"recovery attempt changed");
    int cell=mode.endsWith("same")?2:79,wrong=mode.endsWith("same")?5:1;Sudoku2 board=p.getSudoku();
    check(board.getValue(cell)==wrong&&!board.isFixed(cell),"wrong user entry or editable mask lost");check(board.getStatus()==SudokuStatus.VALID&&board.getStatusGivens()==SudokuStatus.VALID&&board.isSolutionSet(),"definition metadata lost");check(board.getSolution()[cell]!=wrong,"wrong entry became solution");check(!board.checkSudoku(),"wrong entry unexpectedly correct");
    check(f.getSavePoints().size()==1&&f.getSavePoints().get(0).getName().equals("own durable point"),"foreign attempt savepoints survived");check(!completion().isCompletedRecorded()&&!completion().wasPreviouslySolved(),"foreign completion state survived");
    ReplayBoard before=new ReplayBoard(board);GuiState point=f.getSavePoints().get(0);check(new ReplayBoard(point.getSudoku()).samePuzzle(before),"bookmark given identity changed");f.setState(point);r.savePointRestored();check(p.getSudoku().getValue(cell)==0&&!p.getSudoku().isFixed(cell),"own restored point incorrect");check(p.getNextStep(true)!=null,"restored point cannot solve");
   }else if(mode.startsWith("prior-")){
    f.setPuzzle(A);AtomicLong nano=new AtomicLong();r.setClock(new ReplayClock(nano::get,()->10000L));if(mode.endsWith("retained"))point("retain");nano.set(4000000000L);r.updateElapsed();r.checkpoint(false);Files.write(dir.resolve("prior-id"),r.session().id.getBytes("UTF-8"));Files.write(dir.resolve("prior-elapsed"),Long.toString(r.session().elapsedMillis).getBytes("UTF-8"));if(mode.contains("clean"))save();
   }else if(mode.startsWith("launch-")){
    String id=new String(Files.readAllBytes(dir.resolve("prior-id")),"UTF-8");check(!id.equals(r.session().id),"launch continued old attempt");Path old=r.directory().resolve(id+".hrep");
    if(mode.endsWith("retained")){ReplaySession ended=ReplayStore.read(old);check(ended.endedAt>0,"prior not ended");check(ended.elapsedMillis==Long.parseLong(new String(Files.readAllBytes(dir.resolve("prior-elapsed")),"UTF-8")),"closed duration counted: "+ended.elapsedMillis);check(ended.retained,"retention lost");}else check(!Files.exists(old),"unretained orphan survived");
   }else if(mode.equals("sealed")){
    f.setPuzzle(SOL.substring(0,80)+"0");p.saveState();p.setCell(8,8,9);r.afterAction();check(r.session().completed,"completion failed");Path file=r.directory().resolve(r.session().id+".hrep");byte[] sealed=Files.readAllBytes(file);int bookmarks=r.session().bookmarks().size();p.undo();point("after completion undo");check(r.session().bookmarks().size()==bookmarks,"sealed bookmark changed");check(Arrays.equals(sealed,Files.readAllBytes(file)),"sealed archive rewritten");check(f.getSavePoints().get(0).getSudoku().getValue(80)==0,"ordinary postcompletion savepoint broken");
   }
  }catch(Exception e){throw new RuntimeException(e);}});
  if(mode.contains("crash"))Runtime.getRuntime().halt(7);
  }catch(Throwable e){e.printStackTrace();System.exit(1);}finally{if(f!=null)SwingUtilities.invokeAndWait(()->f.dispose());}System.exit(0);
 }
}
