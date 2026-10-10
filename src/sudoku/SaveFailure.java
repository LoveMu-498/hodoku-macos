/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Preserves the failing stage, destination and all nested/suppressed errors. */
final class SaveFailure extends IOException {
    SaveFailure(String stage,Path path,Throwable cause){super(text("stage")+": "+stage+"\n"+text("destination")+": "+path.toAbsolutePath(),cause);}
    static String text(String key){return ResourceBundle.getBundle("intl/MainFrame").getString("MainFrame.save."+key);}
    static String details(Throwable failure){
        StringBuilder out=new StringBuilder();Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        describe(failure,out,seen,"");
        return out.toString();
    }
    private static void describe(Throwable failure,StringBuilder out,Set<Throwable> seen,String indent){
        if(failure==null||!seen.add(failure))return;
        out.append(indent).append(failure.getClass().getSimpleName()).append(": ").append(failure.getMessage()).append('\n');
        if(failure instanceof AccessDeniedException)out.append(text("permissionHint")).append('\n');
        if(failure instanceof FileAlreadyExistsException)out.append(text("pathHint")).append('\n');
        if(failure instanceof java.io.UTFDataFormatException)out.append(text("capacityHint")).append('\n');
        if(failure instanceof FileSystemException){String reason=((FileSystemException)failure).getReason();if(reason!=null&&reason.toLowerCase(Locale.ROOT).contains("space"))out.append(text("spaceHint")).append('\n');}
        for(Throwable suppressed:failure.getSuppressed())describe(suppressed,out,seen,indent+"  ");
        describe(failure.getCause(),out,seen,indent+"  ");
    }
    static JPanel panel(String message,Throwable failure){
        JPanel panel=new JPanel(new java.awt.BorderLayout(0,10));
        JTextArea heading=new JTextArea(message);heading.setEditable(false);heading.setLineWrap(true);heading.setWrapStyleWord(true);heading.setOpaque(false);panel.add(heading,java.awt.BorderLayout.NORTH);
        JTextArea details=new JTextArea(details(failure),12,64);details.setEditable(false);details.setLineWrap(true);details.setWrapStyleWord(true);details.setCaretPosition(0);panel.add(new JScrollPane(details),java.awt.BorderLayout.CENTER);
        JButton copy=new JButton(text("copyDetails"));copy.addActionListener(e->java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(message+"\n"+details(failure)),null));panel.add(copy,java.awt.BorderLayout.SOUTH);
        return panel;
    }
}
