/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.Color;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Independent common-V3 bytes exercise the real importer, atomic saves and sharing projection. */
public final class ReplayWindowsV3Probe {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final String MARKS="O -65536 1 2 4 1 0.1 0.1 0.2 0.2\nO -16711936 2 3 6 0 0.2 0.2 0.3 0.3\n";
    static ReplayBoard board(){Sudoku2 b=new Sudoku2();b.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");return new ReplayBoard(b);}
    static Path foreign(Path file,String overlay)throws Exception{
        ReplayBoard b=board();
        try(DataOutputStream out=new DataOutputStream(Files.newOutputStream(file))){
            out.writeInt(0x4852504c);out.writeInt(3);out.writeUTF("windows-v3-fixture");out.writeLong(100);out.writeLong(10);out.writeLong(0);out.writeBoolean(false);out.writeInt(1);
            out.writeLong(7);out.writeLong(110);out.writeLong(10);out.writeUTF("frozen Windows marks");out.writeUTF(overlay);out.writeBoolean(true);
            for(int i=0;i<81;i++){out.writeByte(b.values()[i]);out.writeBoolean(b.fixed()[i]);out.writeShort(b.candidates()[i]);out.writeShort(b.userCandidates()[i]);}
        }return file;
    }
    static void reject(String overlay)throws Exception{
        try{ReplayAnnotations.fromWindows(overlay);throw new AssertionError("Accepted malformed: "+overlay);}catch(IOException expected){checks++;}
    }
    static DoodleStroke mark(int cell,int digit,int kind,int group){
        DoodleStroke s=new DoodleStroke(Color.RED,.04f);s.setAnchorCell(cell);s.setAnchorDigit(digit);s.setCandidateMarkKind(kind);s.setThoughtGroup(group);s.setConclusionOutlined(true);
        if(kind==2){s.getPoints().add(new DoodlePoint(-.1,-.1));s.getPoints().add(new DoodlePoint(.1,.1));s.getPoints().add(new DoodlePoint(.1,-.1));s.getPoints().add(new DoodlePoint(-.1,.1));}
        else for(int i=0;i<=48;i++)s.getPoints().add(new DoodlePoint(.1*Math.cos(i*Math.PI/24),.1*Math.sin(i*Math.PI/24)));
        return s;
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&args[0].equals("--compare")){
            ReplaySession mac=ReplayFiles.importFile(Paths.get(args[1])),windows=ReplayFiles.importFile(Paths.get(args[2]));
            check(mac.id.equals(windows.id)&&mac.startedAt==windows.startedAt&&mac.elapsedMillis==windows.elapsedMillis&&mac.completed==windows.completed,"Windows codec changed header");
            check(mac.frames().size()==windows.frames().size()&&windows.foreignTimeline,"Windows codec changed timeline");
            for(int i=0;i<mac.frames().size();i++){ReplayFrame a=mac.frames().get(i),b=windows.frames().get(i);check(a.board.equals(b.board)&&a.operationId==b.operationId&&a.wallTimeMillis==b.wallTimeMillis&&a.elapsedMillis==b.elapsedMillis&&a.methodStep()==b.methodStep()&&a.label.equals(b.label)&&a.annotations.overlay.equals(b.annotations.overlay),"Windows codec changed common data "+i);check(b.annotations.forEditing().ink().isEmpty(),"Windows-returned outlines remain armed");}
            check(windows.bookmarks().isEmpty()&&windows.last().evidence().length==0,"pretending Windows kept native tail");
            System.out.println("PASS "+checks+" actual fixed-Windows codec common roundtrip checks; new Windows GUI is unverified");return;
        }
        Path dir=args.length==0?Files.createTempDirectory("windows-v3-"):Files.createDirectories(Paths.get(args[0]));
        String overlay=MARKS+"MT 1\nX_FUTURE exactly retained\n";
        Path input=foreign(dir.resolve("windows-new.hrep"),overlay);byte[] original=Files.readAllBytes(input);
        ReplaySession s=ReplayFiles.importFile(input);ReplayAnnotations a=s.last().annotations;
        check(a.ink().size()==2,"new circles/crosses not reconstructed");check(a.ink().get(0).getCandidateMarkKind()==1&&a.ink().get(1).getCandidateMarkKind()==2,"truth direction reversed");
        check(a.ink().get(0).isConclusionOutlined()&&a.ink().get(0).getThoughtGroup()==-1,"outline lost or RGB guessed as group");
        check(a.notice().contains(ReplayText.text("foreignMultiMissing")),"MT without frozen results silently hidden");
        check(s.last().board.equals(board())&&s.last().methodStep()&&s.last().elapsedMillis==10,"common frame changed");
        Path saved=dir.resolve("mac-resave.hrep");ReplayStore.write(saved,s);ReplaySession again=ReplayFiles.importFile(saved);
        check(again.last().annotations.overlay.equals(overlay)&&again.last().annotations.ink().size()==2,"raw unknown tags or marks lost on direct resave");
        check(Arrays.equals(original,Files.readAllBytes(input)),"import rewrote source");
        ReplayAnnotations edit=a.forEditing();check(edit.ink().isEmpty()&&!edit.overlay.contains("O ")&&!edit.overlay.contains("MT "),"foreign viewing marks remain executable in branch");
        ReplayAnnotations frozen=ReplayAnnotations.fromWindows("MT 1\nG 2 4\nR 3 6\nM 4 0\n");
        check(frozen.notice().contains(ReplayText.text("foreignMultiRecorded"))&&frozen.candidates().size()==3,"frozen results not displayed");
        check(frozen.forEditing().candidates().size()==1&&frozen.forEditing().candidates().containsKey(4),"proof coloring leaked or user coloring lost");
        StringBuilder mixed=new StringBuilder(MARKS);DoodleStroke full=mark(2,4,1,5);full.setHypothesisStart(true);ReplayAnnotations.appendStructuredMark(mixed,full,5,0);
        check(ReplayAnnotations.fromWindows(mixed.toString()).ink().size()==2,"DM1/O10 duplicated");
        check(ReplayAnnotations.fromWindows(mixed.toString()).ink().get(0).getThoughtGroup()==5,"complete DM1 did not win");
        check(!ReplayAnnotations.fromWindows("O -65536 10 20 30 40\n").notice().isEmpty(),"O6 silently guessed");
        check(!ReplayAnnotations.fromWindows("O -65536 0 -1 -1 0 0.1 0.1 0.2 0.2\n").notice().isEmpty(),"free O10 silently guessed");
        for(String bad:new String[]{"O -1 1 81 1 0 0 0 1 1\n","O -1 2 0 0 0 0 0 1 1\n","O -1 1 0 11 0 0 0 1 1\n","O -1 3 0 1 0 0 0 1 1\n","O -1 0 0 1 0 0 0 1 1\n","O -1 1 0 1 2 0 0 1 1\n","O -1 1 0 1 0 NaN 0 1 1\n","O -1 1 0 1 0 0 0 Infinity 1\n","O -1 1 0 1 0 -0.1 0 1 1\n","O -1 1 0 1 0 0 0 1 1 extra\n","O -1 0.1 2 3 4\n","MT 2\n","MT 1 extra\n"})reject(bad);
        DoodleStroke cross=mark(3,6,2,5),or=mark(2,4,1,4);or.setHypothesisGroupKind(1);or.setHypothesisStart(true);or.setHypothesisConditionId(1);
        StringBuilder nativeText=new StringBuilder("G 2 4\nX_FUTURE native\n");ReplayAnnotations.appendStructuredMark(nativeText,cross,5,3);nativeText.append("P -65536 1:2 3:4\nP -65536 3:2 1:4\n");ReplayAnnotations.appendStructuredMark(nativeText,or,5,1);nativeText.append("P -65536 5:6 7:8\nP -15242753 1:2 3:4\n");
        ReplayAnnotations nativeA=new ReplayAnnotations(nativeText.toString(),new byte[0],Collections.<Integer,Color>emptyMap(),Collections.<Integer,Color>emptyMap(),Arrays.asList(cross,or),5);
        ReplaySession nativeS=new ReplaySession(board(),100);nativeS.setInitialFrameAnnotations(nativeA);nativeS.addBookmark(new ReplayBookmark(0,"keep",100,0));
        Path managed=Files.createDirectories(dir.resolve("managed")),target=dir.resolve("windows-copy.hrep");ReplayFiles.exportWindowsV3File(target,nativeS,managed);
        ReplaySession result=ReplayFiles.importFile(target);String shared=result.last().annotations.overlay;
        check(shared.contains("O -65536 2 3 6 1 ")&&!shared.contains("O -65536 1 2 4"),"eligible cross missing or OR became individually true");
        check(shared.contains("G 2 4\n")&&shared.contains("X_FUTURE native\n"),"proof/unknown content changed");
        check(result.bookmarks().size()==1&&result.last().annotations.ink().get(0).getThoughtGroup()==5&&result.last().annotations.ink().get(1).getHypothesisGroupKind()==1,"Mac native tail degraded");
        check(nativeS.last().annotations.overlay.equals(nativeText.toString()),"export changed current record");
        check(!shared.contains("P -65536 1:2")&&shared.contains("P -65536 5:6"),"fallback removal damaged non-converted mark");
        check(!ReplayAnnotations.fromWindows(shared+"P -1 8:8 9:9\n").notice().isEmpty(),"stale fallback count concealed free ink");
        ReplayFiles.exportWindowsV3File(dir.resolve("second-copy.hrep"),result,managed);check(ReplayFiles.importFile(dir.resolve("second-copy.hrep")).last().annotations.overlay.equals(shared),"re-export duplicated glyphs or changed projection");
        ReplayFiles.exportFile(dir.resolve("native.hrep"),nativeS,managed);check(ReplayFiles.importFile(dir.resolve("native.hrep")).last().annotations.overlay.equals(nativeText.toString()),"ordinary export changed");
        ReplaySession older=new ReplaySession(board(),100);older.setInitialFrameAnnotations(new ReplayAnnotations("P -65536 1:2 3:4\n",new byte[0],Collections.<Integer,Color>emptyMap(),Collections.<Integer,Color>emptyMap(),Arrays.asList(cross),5));
        ReplayFiles.exportWindowsV3File(dir.resolve("older-native-copy.hrep"),older,managed);String legacyProjection=ReplayFiles.importFile(dir.resolve("older-native-copy.hrep")).last().annotations.overlay;
        check(legacyProjection.contains("O -65536 2 3 6 1 ")&&legacyProjection.contains("P -65536 1:2 3:4\n"),"older native anchors missing or unknown pixel source deleted");
        StringBuilder partial=new StringBuilder();ReplayAnnotations.appendStructuredMark(partial,cross,5,1);partial.append("P -65536 1:2 3:4\nP -65536 8:8 9:9\n");
        older.setInitialFrameAnnotations(new ReplayAnnotations(partial.toString(),new byte[0],Collections.<Integer,Color>emptyMap(),Collections.<Integer,Color>emptyMap(),Arrays.asList(cross),5));
        ReplayFiles.exportWindowsV3File(dir.resolve("partial-fallback.hrep"),older,managed);String partialProjection=ReplayFiles.importFile(dir.resolve("partial-fallback.hrep")).last().annotations.overlay;
        check(partialProjection.contains("P -65536 1:2 3:4\nP -65536 8:8 9:9\n"),"ambiguous fallback removed unrelated pixels");
        try{ReplayFiles.exportWindowsV3File(managed.resolve("bad.hrep"),nativeS,managed);throw new AssertionError("managed archive writable");}catch(IOException expected){checks++;}
        System.out.println("PASS "+checks+" Windows-new-V3 import/export, frozen display, raw preservation, native fidelity, branch isolation, malformed data and path checks; fixtures are synthetic");
    }
}
