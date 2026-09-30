package sudoku;
import java.nio.file.*;
import java.util.*;
import java.awt.Color;

/** Exercises real foreign fixture, independent Windows writer output and Mac extension fidelity. */
public final class ReplayWindowsFormatProbe {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  Path dir=Paths.get(args[1]);Files.createDirectories(dir);
  ReplaySession foreign=ReplayFiles.importFile(Paths.get(args[0]));
  check(foreign.frames().size()==128,"sample count");check(foreign.foreignTimeline,"foreign timeline");
  check(foreign.elapsedMillis==7111763,"unmaintained Windows duration must use last recorded time");
  check(foreign.frames().get(0).elapsedMillis==15467,"must not invent initial state");
  int methods=0,boxes=0,chains=0,hidden=0;
  for(ReplayFrame f:foreign.frames()){if(f.methodStep())methods++;ReplayEvidence a=ReplayEvidence.decode(f.annotations.geometry());for(SudokuSet b:a.boxes())if(!b.isEmpty())boxes++;chains+=a.chains().size();if(!f.annotations.notice().isEmpty())hidden++;}
  check(methods==52&&boxes>0&&chains>0&&hidden>0,"foreign semantic annotations");
  Path exported=dir.resolve("foreign-roundtrip.hrep");ReplayStore.write(exported,foreign);ReplaySession read=ReplayFiles.importFile(exported);compare(foreign,read);
  Sudoku2 board=new Sudoku2();board.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");ReplaySession local=new ReplaySession(new ReplayBoard(board),1000);
  List<SudokuSet> groups=new ArrayList<SudokuSet>();for(int i=0;i<6;i++)groups.add(new SudokuSet());groups.get(0).add(2);
  UserChain chain=new UserChain();UserChainNode group=new UserChainNode(2,1,Color.BLUE);group.setMemberCandidates(new int[]{21,22,31,32});chain.getNodes().add(group);chain.getNodes().add(new UserChainNode(11,1,Color.RED));chain.getStrongRelations().add(false);
  DoodleStroke ink=new DoodleStroke(Color.MAGENTA,.03f);ink.setAnchorCell(2);ink.setAnchorDigit(1);ink.setCandidateMarkKind(DoodleStroke.MARK_FALSE_CROSS);ink.setThoughtGroup(4);ink.getPoints().add(new DoodlePoint(-.2,-.2));ink.getPoints().add(new DoodlePoint(.2,.2));ink.getPoints().add(new DoodlePoint(.2,-.2));ink.getPoints().add(new DoodlePoint(-.2,.2));
  String overlay="B 0 0 0 0 0 2\nUC 1 1\nUN 21 22 31 32 111\nUL 0\nUG 21 22 31 32\nUG 111\nP -65536 12:13 14:15\nK 3 0\nM 21 2\n";
  ReplayAnnotations annotations=new ReplayAnnotations(overlay,ReplayEvidence.input("FREE_CHAIN",groups,Arrays.asList(chain)),Collections.singletonMap(3,Color.ORANGE),Collections.singletonMap(21,Color.BLUE),Arrays.asList(ink));
  board.setCell(2,4,false,true);local.append(Collections.singletonList(new ReplayFrame(1,1100,100,"manual","R1C3=4",new ReplayBoard(board),null,annotations)));local.elapsedMillis=100;local.addBookmark(new ReplayBookmark(1,"mark",1100,100));
  SolutionStep proof=new SolutionStep(SolutionType.NAKED_PAIR);proof.addValue(1);proof.addValue(2);proof.addIndex(0);proof.addIndex(1);proof.addCandidateToDelete(2,1);proof.addCandidateToDelete(2,2);
  String projected=annotations.withProof(ReplayProof.encode(proof)).overlay;check(projected.contains("R 2 1 2\n"),"Windows candidate map would overwrite multiple marks in a cell");
  Path localFile=dir.resolve("mac-export.hrep");ReplayStore.write(localFile,local);ReplaySession restored=ReplayFiles.importFile(localFile);compare(local,restored);check(restored.bookmarks().size()==1,"bookmark loss");DoodleStroke restoredMark=restored.last().annotations.ink().get(0);check(restoredMark.isCandidateAnchored()&&restoredMark.getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS&&restoredMark.getThoughtGroup()==4,"candidate cross semantics loss");check(restored.last().annotations.candidates().get(21).equals(Color.BLUE),"exact color loss");
  ReplayAnnotations legacy=readLegacyInk();check(legacy.ink().get(0).getCandidateMarkKind()==DoodleStroke.MARK_TRUE_CIRCLE&&legacy.ink().get(0).getThoughtGroup()==-1,"old anchored circle migration");check(legacy.ink().get(1).getCandidateMarkKind()==DoodleStroke.MARK_NONE,"old freehand misclassified");
  check(ReplayEvidence.decode(restored.last().annotations.geometry()).chains().get(0).getNodes().get(0).atoms().length==4,"group truncated");
  if(args.length>2){ReplaySession windows=ReplayFiles.importFile(Paths.get(args[2]));check(windows.foreignTimeline&&windows.bookmarks().isEmpty(),"Windows resave extension policy");check(windows.last().board.equals(local.last().board)&&windows.last().annotations.overlay.equals(overlay),"Windows re-save common data loss");check(!windows.last().annotations.notice().isEmpty(),"lost viewport must not pretend accurate ink");}
  byte[] bytes=Files.readAllBytes(localFile);bytes[7]=4;Path bad=dir.resolve("legacy.hrep");Files.write(bad,bytes);reject(bad);
  Files.write(bad,Arrays.copyOf(Files.readAllBytes(localFile),Files.readAllBytes(localFile).length-1));reject(bad);
  try{ReplayAnnotations.fromWindows("CG 9999\n");throw new AssertionError("bad candidate accepted");}catch(java.io.IOException expected){}
  try{ReplayAnnotations.fromWindows("CL 1 9999\n");throw new AssertionError("bad count accepted");}catch(java.io.IOException expected){}
  System.out.println("PASS 128 Windows frames, 52 method frames, real timeline, boxes/chains, raw ink retention, native groups/anchors/colors/bookmarks, malformed/legacy rejection");
 }
 static void reject(Path p)throws Exception{try{ReplayFiles.importFile(p);throw new AssertionError("bad file accepted");}catch(java.io.IOException expected){}}
 static ReplayAnnotations readLegacyInk()throws Exception{
  java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();java.io.DataOutputStream out=new java.io.DataOutputStream(bytes);
  out.writeBoolean(true);ReplayAnnotations.writeBytes(out,new byte[0]);out.writeInt(0);out.writeInt(0);out.writeInt(2);
  out.writeInt(Color.GREEN.getRGB());out.writeFloat(.02f);out.writeInt(4);out.writeInt(5);out.writeInt(2);out.writeDouble(-.1);out.writeDouble(0);out.writeDouble(.1);out.writeDouble(0);
  out.writeInt(Color.BLUE.getRGB());out.writeFloat(.02f);out.writeInt(-1);out.writeInt(0);out.writeInt(2);out.writeDouble(.1);out.writeDouble(.1);out.writeDouble(.2);out.writeDouble(.2);out.flush();
  return ReplayAnnotations.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())),"",1);
 }
 static void compare(ReplaySession a,ReplaySession b){check(a.frames().size()==b.frames().size(),"count");for(int i=0;i<a.frames().size();i++){ReplayFrame x=a.frames().get(i),y=b.frames().get(i);check(x.board.equals(y.board)&&x.elapsedMillis==y.elapsedMillis&&x.operationId==y.operationId&&x.annotations.overlay.equals(y.annotations.overlay),"frame mismatch "+i);}}
}
