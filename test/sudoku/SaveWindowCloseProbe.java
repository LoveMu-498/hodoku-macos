/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** A real window-closing event terminates the child only after committing long replay text. */
public final class SaveWindowCloseProbe {
    private static String longText(){StringBuilder text=new StringBuilder();for(int i=0;i<5000;i++)text.append("变化见下一帧。");return text.toString();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        if(args[0].equals("--verify")){
            SessionSnapshot saved=new SessionStore(ApplicationPaths.getSessionFile()).load();
            require(saved.getGuiState().getSudoku().getValue(2)==4,"window close lost board");
            Path checkpoint=ApplicationPaths.getDataDirectory().toPath().resolve("replays/active.checkpoint");
            try(DataInputStream in=new DataInputStream(Files.newInputStream(checkpoint))){
                require(in.readInt()==0x48524331,"invalid checkpoint");String id=in.readUTF();int count=in.readInt();in.readLong();require(in.readBoolean(),"close not marked clean");
                ReplaySession replay=ReplayStore.read(checkpoint.resolveSibling(id+".hrep"));require(replay.frames().size()==count,"checkpoint not current");require(longText().equals(replay.last().label),"window close lost long text");
            }
            System.out.println("Window close child: complete board, long text and clean checkpoint restored");return;
        }
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(()->{
            MainFrame frame=new MainFrame(null);frame.setVisible(true);frame.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
            frame.getSudokuPanel().setCell(0,2,4);ReplayController controller=frame.getReplayController();controller.afterAction();
            ReplaySession replay=controller.session();controller.appendOperation(Collections.singletonList(new ReplayFrame(replay.last().operationId+1,System.currentTimeMillis(),controller.elapsedMillis(),"manual",longText(),replay.last().board,null)));
            System.out.println("Dispatching real WINDOW_CLOSING with long-text replay");
            frame.dispatchEvent(new WindowEvent(frame,WindowEvent.WINDOW_CLOSING));
        });
        throw new AssertionError("window close did not exit");
    }
}
