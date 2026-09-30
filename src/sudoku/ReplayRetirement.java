/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Removes only positively identified, UUID-owned retired Mac v4 archives at startup. */
final class ReplayRetirement {
    static void clean(Path directory)throws IOException{
        if(!Files.isDirectory(directory)||Files.isSymbolicLink(directory))return;
        Set<String> retired=new HashSet<String>();
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(directory,"*.hrep")){
            for(Path file:stream){
                if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>ReplayStore.MAX_BYTES)continue;
                String id;
                try(DataInputStream in=new DataInputStream(Files.newInputStream(file))){
                    if(in.readInt()!=0x4852504c||in.readInt()!=4)continue;id=in.readUTF();
                    if(!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")||!file.getFileName().toString().equals(id+".hrep"))continue;
                    // The retired header must be complete; a magic prefix alone is not ownership.
                    in.readLong();in.readLong();in.readLong();in.readBoolean();in.readBoolean();in.readBoolean();in.readUTF();int count=in.readInt();if(count<1||count>100000)continue;
                }catch(EOFException|UTFDataFormatException malformed){continue;}
                retired.add(id);
            }
        }
        Path checkpoint=directory.resolve("active.checkpoint");
        if(Files.isRegularFile(checkpoint,LinkOption.NOFOLLOW_LINKS)){
            String id=null;try(DataInputStream in=new DataInputStream(Files.newInputStream(checkpoint))){if(in.readInt()==0x48524331)id=in.readUTF();}catch(EOFException|UTFDataFormatException malformed){}
            // Remove checkpoint first: interruption cannot leave a pointer to a deleted archive.
            if(retired.contains(id))Files.delete(checkpoint);
        }
        for(String id:retired)Files.delete(directory.resolve(id+".hrep"));
    }
}
