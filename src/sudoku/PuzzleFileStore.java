/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.beans.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.Files;
import java.util.zip.*;

/** Existing six-object SudokuData format, written atomically and checked before replacing. */
final class PuzzleFileStore {
    private PuzzleFileStore(){}
    static void write(File file,Object[] state,String text)throws IOException{
        AtomicFileIO.write(file,target->{
            if(state==null){Files.write(target.toPath(),text.getBytes(StandardCharsets.UTF_8));return;}
            final IOException[] failure={null};
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)))){
                zip.putNextEntry(new ZipEntry("SudokuData"));
                try(XMLEncoder out=new XMLEncoder(zip)){
                    AnnotationXml.configure(out);
                    out.setExceptionListener(error->{IOException detail=new IOException("Unable to encode SudokuData",error);if(failure[0]==null)failure[0]=detail;else failure[0].addSuppressed(detail);});
                    for(Object object:state)out.writeObject(object);
                }
            }
            if(failure[0]!=null)throw failure[0];
        },target->{
            if(state==null){StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(target.toPath())));return;}
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(new FileInputStream(target)))){
                ZipEntry entry=zip.getNextEntry();if(entry==null||!"SudokuData".equals(entry.getName()))throw new IOException("Missing SudokuData entry");
                final IOException[] failure={null};
                try(XMLDecoder in=new XMLDecoder(zip,null,error->{IOException detail=new IOException("Unable to validate SudokuData",error);if(failure[0]==null)failure[0]=detail;else failure[0].addSuppressed(detail);})){
                    Object sudoku=in.readObject();if(!(sudoku instanceof Sudoku2))throw new IOException("Invalid SudokuData board");
                    if(!(in.readObject() instanceof int[]))throw new IOException("Invalid SudokuData step counts");
                    for(int i=0;i<4;i++)if(!(in.readObject() instanceof java.util.List))throw new IOException("Invalid SudokuData list "+i);
                }
                if(failure[0]!=null)throw failure[0];
            }
        },state==null?String.class:Sudoku2.class);
    }
}
