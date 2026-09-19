package sudoku;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Only owns canonical UUID replay files inside its application directory. */
public final class ReplayLibrary {
    public static final int ORDINARY_LIMIT=100,PIN_LIMIT=20;
    public static final class PinLimitException extends IOException {PinLimitException(String message){super(message);}}
    private final Path directory;
    private final List<String> warnings=new ArrayList<String>();
    public ReplayLibrary(Path directory){this.directory=directory;}
    public Path path(ReplaySession s){return directory.resolve(s.id+".hrep");}
    public List<String> warnings(){return new ArrayList<String>(warnings);}
    public List<ReplaySession> list()throws IOException{
        warnings.clear();List<ReplaySession> result=new ArrayList<ReplaySession>();
        if(!Files.exists(directory))return result;
        try(DirectoryStream<Path> files=Files.newDirectoryStream(directory,"*.hrep")){
            for(Path file:files){
                if(Files.isSymbolicLink(file)||!Files.isRegularFile(file))continue;
                try{ReplaySession s=ReplayStore.read(file);if(file.getFileName().toString().equals(s.id+".hrep"))result.add(s);}
                catch(IOException e){warnings.add(file.getFileName()+": "+e.getMessage());}
            }
        }
        Collections.sort(result,(a,b)->{int order=Long.compare(b.startedAt,a.startedAt);return order!=0?order:a.id.compareTo(b.id);});
        return result;
    }
    public void discard(ReplaySession s)throws IOException{
        if(s.retained||s.pinned||s.completed)throw new IllegalArgumentException("Cannot discard retained replay");
        Files.deleteIfExists(path(s));
    }
    public void rotate(String currentId,Set<String> protectedIds)throws IOException{
        List<ReplaySession> ordinary=new ArrayList<ReplaySession>();
        for(ReplaySession s:list())if(!s.id.equals(currentId)&&!protectedIds.contains(s.id)&&!s.pinned&&s.retained&&s.endedAt!=0)ordinary.add(s);
        Collections.sort(ordinary,(a,b)->{int c=Long.compare(b.endedAt,a.endedAt);return c!=0?c:a.id.compareTo(b.id);});
        for(int i=ORDINARY_LIMIT;i<ordinary.size();i++)Files.deleteIfExists(path(ordinary.get(i)));
    }
    public void setPinned(ReplaySession s,boolean pinned)throws IOException{
        if(pinned&&!s.pinned){int count=0;for(ReplaySession item:list())if(item.pinned&&!item.id.equals(s.id))count++;
            if(count>=PIN_LIMIT)throw new PinLimitException(ReplayText.text("pinLimit"));}
        boolean old=s.pinned;s.pinned=pinned;s.retained=true;
        try{ReplayStore.write(path(s),s);}catch(IOException e){s.pinned=old;throw e;}
    }
}
