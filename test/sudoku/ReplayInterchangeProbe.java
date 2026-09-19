package sudoku;
import java.nio.file.*;
import java.util.*;
import java.io.*;

public final class ReplayInterchangeProbe {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 interface IO {void run()throws Exception;}
 static void reject(IO action)throws Exception{try{action.run();throw new AssertionError("Malformed data accepted");}catch(IOException expected){}}
 static ReplaySession fixture(){
  Sudoku2 b=new Sudoku2();b.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
  ReplaySession s=new ReplaySession(new ReplayBoard(b),100);List<ReplayFrame> batch=new ArrayList<ReplayFrame>();
  for(int cell:new int[]{2,3}){SolutionStep proof=new SolutionStep(SolutionType.NAKED_SINGLE);proof.addIndex(cell);proof.addValue(cell==2?4:6);byte[] bytes=ReplayProof.encode(proof);batch.add(new ReplayFrame(1,100+cell,cell,"proof","step",new ReplayBoard(b),bytes));b.setCell(cell,cell==2?4:6);batch.add(new ReplayFrame(1,100+cell,cell,"apply","apply",new ReplayBoard(b),null));}
  s.append(batch);SudokuSet box=new SudokuSet();box.add(0);box.add(1);byte[] input=ReplayEvidence.input("BOX",Collections.singletonList(box),Collections.<UserChain>emptyList());
  SolutionStep proof=new SolutionStep(SolutionType.NAKED_SINGLE);proof.addIndex(5);proof.addValue(1);
  byte[] evidence=ReplayEvidence.result(input,ReplayProof.encode(proof),null,"NATIVE_MATCH");ReplayBoard before=new ReplayBoard(b);b.setCell(5,1); // Wrong historical fill is still recorded, no correctness rejection.
  s.append(Arrays.asList(new ReplayFrame(2,104,4,"authored-input","input",before,evidence),new ReplayFrame(2,105,5,"authored-result","result",before,evidence),new ReplayFrame(2,106,6,"authored-apply","apply",new ReplayBoard(b),evidence)));
  s.elapsedMillis=10;s.addBookmark(new ReplayBookmark(7,"保存点",107,7));s.retained=true;s.pinned=true;s.interruption="diagnostic /private/user/path";return s;
 }
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("replay-interchange-");System.setProperty("hodoku.data.dir",root.resolve("data").toString());
  Path managed=Files.createDirectory(root.resolve("managed")),external=Files.createDirectory(root.resolve("分享 文件"));ReplaySession source=fixture();Path file=external.resolve("过程.hrep");
  ReplayFiles.exportFile(file,source,managed);ReplaySession read=ReplayFiles.importFile(file);
  check(read.frames().size()==source.frames().size()&&read.bookmarks().size()==1,"frames/markers lost");check(!read.retained&&!read.pinned&&!read.completed,"local policy leaked");check(source.pinned&&source.retained&&!source.completed,"source mutated");
  for(int i=0;i<source.frames().size();i++){ReplayFrame a=source.frames().get(i),b=read.frames().get(i);check(a.board.equals(b.board)&&Arrays.equals(a.evidence(),b.evidence())&&a.elapsedMillis==b.elapsedMillis&&a.wallTimeMillis==b.wallTimeMillis,"frame mismatch");}
  check(!new String(Files.readAllBytes(file),"ISO-8859-1").contains("/private/user/path"),"private diagnostic exported");
  byte[] unchanged=Files.readAllBytes(file);ReplayFiles.importFile(file);check(Arrays.equals(unchanged,Files.readAllBytes(file)),"read modified external file");check(Files.list(managed).count()==0,"import retained automatically");
  reject(()->ReplayFiles.exportFile(managed.resolve("overwrite.hrep"),source,managed));
  Path link=external.resolve("alias");Files.createSymbolicLink(link,managed);reject(()->ReplayFiles.exportFile(link.resolve("overwrite.hrep"),source,managed));
  ReplaySession invalid=fixture();invalid.loadFrame(new ReplayFrame(3,110,9,"unknown-event","bad",invalid.last().board,null));Path bad=external.resolve("bad.hrep");ReplayStore.write(bad,invalid);reject(()->ReplayFiles.importFile(bad));
  invalid=fixture();invalid.elapsedMillis=0;ReplayStore.write(bad,invalid);reject(()->ReplayFiles.importFile(bad));
  invalid=fixture();Sudoku2 regressed=invalid.last().board.toSudoku();regressed.setCell(6,2);invalid.loadFrame(new ReplayFrame(3,90,5,"manual","regressed effective time",new ReplayBoard(regressed),null));ReplayStore.write(bad,invalid);reject(()->ReplayFiles.importFile(bad));
  ReplaySession dateChange=fixture();Sudoku2 forward=dateChange.last().board.toSudoku();forward.setCell(6,2);dateChange.loadFrame(new ReplayFrame(3,90,8,"manual","backward wall clock",new ReplayBoard(forward),null));ReplayStore.write(bad,dateChange);check(ReplayFiles.importFile(bad).last().wallTimeMillis==90,"valid wall-clock reversal rejected");
  ReplaySession broken=new ReplaySession(new ReplayBoard(new Sudoku2()),1);broken.append(Collections.singletonList(new ReplayFrame(1,2,0,"proof","dangling",broken.last().board,ReplayProof.encode(new SolutionStep(SolutionType.NAKED_SINGLE)))));ReplayStore.write(bad,broken);reject(()->ReplayFiles.importFile(bad));
  byte[] bytes=unchanged.clone();bytes[7]=127;Files.write(bad,bytes);reject(()->ReplayFiles.importFile(bad));Files.write(bad,Arrays.copyOf(unchanged,unchanged.length-2));reject(()->ReplayFiles.importFile(bad));
  try(RandomAccessFile oversized=new RandomAccessFile(bad.toFile(),"rw")){oversized.setLength(ReplayStore.MAX_BYTES+1);}reject(()->ReplayFiles.importFile(bad));
  System.out.println("PASS batch native/authored/incorrect-action/bookmark roundtrip, privacy, prefix snapshot, external immutability, managed path guards, unknown kind/version/partial group/time/size rejection");
 }
}
