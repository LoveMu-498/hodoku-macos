package sudoku;

import java.io.*;
import java.nio.file.*;

/** Small atomic active-record checkpoint; replay files already commit whole operation groups. */
final class ReplayRecovery {
    private final Path directory;
    boolean cleanEditing,cleanSnapshot;
    ReplayRecovery(Path directory){this.directory=directory;}
    void checkpoint(ReplaySession s,boolean clean)throws IOException{checkpoint(s,clean,false);}
    void checkpoint(ReplaySession s,boolean clean,boolean editing)throws IOException{
        Files.createDirectories(directory);Path temp=Files.createTempFile(directory,"active-",".tmp");
        try{
            try(FileOutputStream file=new FileOutputStream(temp.toFile());DataOutputStream out=new DataOutputStream(file)){
                out.writeInt(0x48524331);out.writeUTF(s.id);out.writeInt(s.frames().size());out.writeLong(s.elapsedMillis);out.writeBoolean(clean);out.writeBoolean(editing);out.flush();file.getFD().sync();
            }
            Files.move(temp,directory.resolve("active.checkpoint"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    ReplaySession restore()throws IOException{
        Path path=directory.resolve("active.checkpoint");if(!Files.exists(path))return null;
        try(DataInputStream in=new DataInputStream(Files.newInputStream(path))){
            if(in.readInt()!=0x48524331)throw new IOException("Invalid recovery checkpoint");
            String id=in.readUTF();if(!id.matches("[a-zA-Z0-9-]{1,80}"))throw new IOException("Invalid recovery identity");
            int count=in.readInt();long elapsed=in.readLong();boolean clean=in.readBoolean();boolean editing=in.readBoolean();cleanEditing=clean&&editing;cleanSnapshot=clean;
            if(in.read()!=-1||elapsed<0)throw new IOException("Invalid recovery checkpoint");
            ReplaySession s=ReplayStore.read(directory.resolve(id+".hrep"));
            if(s.completed||s.endedAt!=0)return null;
            // A crash between operation commit and checkpoint is safe: committed group wins.
            if(count>s.frames().size())throw new IOException("Recovery checkpoint is ahead of committed replay");
            if(count==s.frames().size())s.elapsedMillis=Math.max(s.elapsedMillis,elapsed);
            if(!clean)s.interruption=ReplayText.text("interruption");
            return s;
        }
    }
}
