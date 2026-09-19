package sudoku;
import java.nio.file.*;
import java.util.*;

/** Real bounded library files, separate pin quota, bookmarks, external-file protection. */
public final class ReplayRetentionProbe {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[]args)throws Exception{
  Path dir=Files.createTempDirectory("replay-retention-");ReplayLibrary library=new ReplayLibrary(dir);Sudoku2 board=new Sudoku2();board.clearSudoku();ReplayBoard snapshot=new ReplayBoard(board);
  List<ReplaySession> rows=new ArrayList<ReplaySession>();
  for(int i=0;i<123;i++){ReplaySession s=new ReplaySession(snapshot,1000+i);s.retained=true;s.endedAt=2000+i;s.pinned=i<20;ReplayStore.write(library.path(s),s);rows.add(s);}
  ReplaySession current=new ReplaySession(snapshot,5000);ReplayStore.write(library.path(current),current);
  Path external=dir.resolve("my-export.hrep");Files.copy(library.path(rows.get(20)),external);
  library.rotate(current.id,Collections.<String>emptySet());List<ReplaySession> records=library.list();
  check(records.size()==121,"expected 100 ordinary +20 pinned + current");check(Files.exists(external),"external noncanonical export removed");
  check(!Files.exists(library.path(rows.get(20)))&&!Files.exists(library.path(rows.get(21)))&&!Files.exists(library.path(rows.get(22))),"oldest records not rotated");
  try{library.setPinned(current,true);throw new AssertionError("pin overflow accepted");}catch(ReplayLibrary.PinLimitException expected){}
  library.setPinned(rows.get(0),false);library.rotate(current.id,Collections.<String>emptySet());check(!Files.exists(library.path(rows.get(0))),"unpin ignored original end ordering");
  library.setPinned(current,true);check(current.pinned&&current.retained,"current pin not retained");
  ReplaySession marked=rows.get(122);marked.addBookmark(new ReplayBookmark(0,"首次保存",1234,44));ReplayStore.write(library.path(marked),marked);ReplaySession loaded=ReplayStore.read(library.path(marked));check(loaded.bookmarks().size()==1&&loaded.bookmarks().get(0).frameIndex==0,"bookmark roundtrip");
  ReplaySession temporary=new ReplaySession(snapshot,6000);ReplayStore.write(library.path(temporary),temporary);library.discard(temporary);check(!Files.exists(library.path(temporary)),"temporary not discarded");
  Files.write(dir.resolve("damaged.hrep"),new byte[]{1,2,3});library.rotate(current.id,Collections.<String>emptySet());check(Files.exists(dir.resolve("damaged.hrep")),"damaged file removed");check(!library.warnings().isEmpty(),"corruption not surfaced");
  System.out.println("Replay retention: real 100+20+current bounds, original end-order unpin, external/damaged files protected, bookmark roundtrip, discard and pin limit passed: "+dir);
 }
}
