/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.lang.reflect.*;
import java.nio.file.*;
import javax.swing.*;

/** A newer saved native session must survive an older replay after a partial quit save. */
public final class PartialSaveRecoveryProbe {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        final Throwable[] failure={null};
        SwingUtilities.invokeAndWait(()->{
            MainFrame original=null,restored=null;
            try{
                original=new MainFrame(null);original.setPuzzle("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
                ReplayController replay=original.getReplayController();replay.flushPendingSaves();String id=replay.session().id;
                Path directory=replay.directory(),moved=directory.resolveSibling("replay-before-failure");Files.move(directory,moved);Files.write(directory,new byte[]{1});
                original.getSudokuPanel().setCell(0,2,4);original.getSudokuPanel().handleColoring(0,3,6,Color.BLUE);replay.afterAction();
                Method save=MainFrame.class.getDeclaredMethod("saveApplicationState");save.setAccessible(true);
                try{save.invoke(original);throw new AssertionError("blocked replay save succeeded");}catch(InvocationTargetException expected){require(expected.getCause() instanceof java.io.IOException,"failure escaped as another type");}
                require(new SessionStore(ApplicationPaths.getSessionFile()).load().getGuiState().getSudoku().getValue(2)==4,"independent native save missing");
                Files.delete(directory);Files.move(moved,directory);original.dispose();original=null;
                restored=new MainFrame(null);
                require(restored.getSudokuPanel().getSudoku().getValue(2)==4,"stale replay overwrote newer native board");
                GuiState ink=new GuiState();restored.getSudokuPanel().getState(ink,true);require(Color.BLUE.equals(ink.getColoringCandidateMap().get(36)),"newer annotations lost");
                require(restored.getReplayController().session().id.equals(id),"recovery changed attempt identity");
                require(restored.getReplayController().session().last().kind.equals("evidence-unavailable"),"missing history was claimed as full proof");
                restored.getReplayController().flushPendingSaves();ReplayFiles.validate(restored.getReplayController().session());
            }catch(Throwable e){failure[0]=e;}finally{if(original!=null)original.dispose();if(restored!=null)restored.dispose();}
        });
        if(failure[0]!=null){failure[0].printStackTrace();System.exit(1);}
        System.out.println("Partial save recovery: newer native board/annotations win over older replay; missing historical evidence is explicit");System.exit(0);
    }
}
