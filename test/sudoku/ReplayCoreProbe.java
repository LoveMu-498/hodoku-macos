package sudoku;

import java.nio.file.*;
import java.util.*;

/** Real codec roundtrip checks exact board channels, independent of UI fixture setup. */
public final class ReplayCoreProbe {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Sudoku2 b=new Sudoku2();b.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
        b.setCandidate(2,1,false,false);b.setCandidate(2,2,true,true);
        ReplayBoard initial=new ReplayBoard(b);ReplaySession session=new ReplaySession(initial,1000);
        b.setCell(2,4,false,true);ReplayBoard applied=new ReplayBoard(b);
        session.append(Collections.singletonList(new ReplayFrame(1,1100,100,"manual","填数",applied,null)));
        session.append(Collections.singletonList(new ReplayFrame(2,1200,200,"undo","撤销",initial,null)));
        b.setCell(3,6,false,true);
        require(!applied.equals(new ReplayBoard(b)),"snapshot aliases live board");
        Path dir=Files.createTempDirectory("replay-core-");Path file=dir.resolve("roundtrip.hrep");ReplayStore.write(file,session);ReplaySession read=ReplayStore.read(file);
        require(read.frames().size()==3,"frame count");require(read.frames().get(0).board.equals(initial),"initial board channels");
        require(read.frames().get(1).board.equals(applied),"applied board channels");require(read.last().board.equals(initial),"undo preserves path");
        require(new ReplayBoard(initial.toSudoku()).equals(initial),"native reconstruction changed candidate masks");
        require(initial.toSudoku().getUnsolvedCellsAnz()==session.frames().get(0).board.toSudoku().getUnsolvedCellsAnz(),"unsolved count");
        int[] exposed=read.last().board.values();exposed[0]=9;require(read.last().board.values()[0]==5,"mutable accessor");
        session.completed=true;try{session.append(Collections.singletonList(new ReplayFrame(3,1300,300,"manual","bad",initial,null)));throw new AssertionError("sealed append accepted");}catch(IllegalStateException expected){}
        byte[] data=Files.readAllBytes(file);Files.write(dir.resolve("truncated.hrep"),Arrays.copyOf(data,data.length-3));
        try{ReplayStore.read(dir.resolve("truncated.hrep"));throw new AssertionError("truncation accepted");}catch(java.io.IOException expected){}
        data[7]=127;Files.write(dir.resolve("future.hrep"),data);try{ReplayStore.read(dir.resolve("future.hrep"));throw new AssertionError("future version accepted");}catch(java.io.IOException expected){}
        System.out.println("Replay core: immutable complete board, actual file roundtrip, retained undo, sealed writes and invalid formats passed; "+file);
    }
}
