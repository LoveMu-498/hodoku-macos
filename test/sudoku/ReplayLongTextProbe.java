/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.nio.file.*;
import java.util.*;

/** Exercises the real codec with Chinese text above the modified-UTF limit. */
public final class ReplayLongTextProbe {
    public static void main(String[] args) throws Exception {
        Path file=Files.createTempDirectory("hodoku-long-text-").resolve("long.hrep");
        Sudoku2 sudoku=new Sudoku2();sudoku.clearSudoku();ReplayBoard board=new ReplayBoard(sudoku);
        ReplaySession session=new ReplaySession(board,1000);
        StringBuilder text=new StringBuilder();for(int i=0;i<4000;i++)text.append("变化见下一帧。😀");
        String label=text.toString(),overlay="UNKNOWN "+label+"\n";
        ReplayAnnotations annotations=new ReplayAnnotations(overlay,new byte[0],Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList());
        session.append(Collections.singletonList(new ReplayFrame(1,1001,1,"manual",label,board,null,annotations)));
        session.addBookmark(new ReplayBookmark(1,"中文书签",1001,1));session.interruption=label;
        ReplayStore.write(file,session);
        ReplaySession loaded=ReplayStore.read(file);
        require(label.equals(loaded.last().label),"long label changed");
        require(overlay.equals(loaded.last().annotations.overlay),"long overlay changed");
        require(overlay.equals(loaded.last().annotations.forEditing().overlay),"editable overlay changed");
        require("中文书签".equals(loaded.bookmarks().get(0).name)&&label.equals(loaded.interruption),"bookmark/interruption changed");
        byte[] good=Files.readAllBytes(file);
        StringBuilder huge=new StringBuilder();for(int i=0;i<1500000;i++)huge.append('中');
        session.append(Collections.singletonList(new ReplayFrame(2,1002,2,"manual",huge.toString(),board,null)));
        try{ReplayStore.write(file,session);throw new AssertionError("unbounded item accepted");}
        catch(java.io.IOException expected){require(Arrays.equals(good,Files.readAllBytes(file)),"failed save replaced last good file");}
        System.out.println("Long Unicode labels, viewing/editing overlays roundtrip; oversized save preserves last good file");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
