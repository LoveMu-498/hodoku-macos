package sudoku;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** External data-only interchange boundary; loading never inserts into the local replay library. */
public final class ReplayFiles {
    private ReplayFiles(){}
    public static ReplaySession snapshot(ReplaySession source)throws IOException{
        ReplaySession result=new ReplaySession(source.id,source.startedAt);
        result.sourceReplayId=source.sourceReplayId;result.sourceFrameIndex=source.sourceFrameIndex;result.setInitialAnnotations(source.initialAnnotations());
        result.elapsedMillis=source.elapsedMillis;result.endedAt=source.endedAt;result.completed=source.completed;
        // Local pin/retention policy and recovery diagnostics are not portable identity or settings.
        result.interruption=source.interruption.isEmpty()?"":"异常中断";
        for(ReplayFrame frame:source.frames())result.loadFrame(frame);
        for(ReplayBookmark bookmark:source.bookmarks())result.addBookmark(bookmark);
        validate(result);return result;
    }
    public static void exportFile(Path file,ReplaySession source,Path managedDirectory)throws IOException{
        Path destination=file.toAbsolutePath().normalize();
        Path managed=managedDirectory.toAbsolutePath().normalize();
        Path parent=destination.getParent();
        if(parent==null)throw new IOException("Invalid export destination");
        // Prevent overwriting managed archives/checkpoints, including aliases through symlink parents.
        Path existing=parent;while(existing!=null&&!Files.exists(existing))existing=existing.getParent();
        Path resolvedParent=existing==null?parent:existing.toRealPath().resolve(existing.relativize(parent)).normalize();
        if(parent.startsWith(managed)||(Files.exists(managed)&&resolvedParent.startsWith(managed.toRealPath())))throw new IOException("Export outside the managed replay directory");
        if(Files.isSymbolicLink(destination))throw new IOException("Export destination must not be a symbolic link");
        ReplayStore.write(destination,snapshot(source));
    }
    public static ReplaySession importFile(Path file)throws IOException{
        if(!Files.isRegularFile(file))throw new IOException("Replay file is not a regular file");
        try{ReplaySession result=ReplayStore.read(file);validate(result);result.retained=false;result.pinned=false;return result;}
        catch(RuntimeException ex){throw new IOException("Invalid replay data",ex);}
    }
    public static void validate(ReplaySession session)throws IOException{
        List<ReplayFrame> frames=session.frames();
        if(frames.isEmpty()||session.elapsedMillis<0)fail("Empty or invalid replay");
        if((session.sourceReplayId.isEmpty()&&session.sourceFrameIndex!=-1)||(!session.sourceReplayId.isEmpty()&&(!session.sourceReplayId.matches("[a-zA-Z0-9-]{1,80}")||session.sourceFrameIndex<0||session.sourceFrameIndex>=100000)))fail("Invalid source frame identity");
        if(session.initialAnnotations().length>0){ReplayEvidence raw=ReplayEvidence.decode(session.initialAnnotations());if(!raw.status.equals("PENDING")||raw.proofBytes().length!=0||session.sourceReplayId.isEmpty())fail("Invalid initial raw annotations");}
        ReplayFrame initial=frames.get(0);
        if(initial.operationId!=0||!initial.kind.equals("initial")||initial.elapsedMillis!=0||initial.evidence().length!=0)fail("Invalid initial frame");
        ReplayBoard prior=initial.board;long operation=0,elapsed=0;
        for(int at=1;at<frames.size();){
            int end=at+1;ReplayFrame first=frames.get(at);if(first.operationId!=operation+1)fail("Invalid operation sequence");operation=first.operationId;
            while(end<frames.size()&&frames.get(end).operationId==operation)end++;
            for(int part=at;part<end;){
                ReplayFrame shown=frames.get(part);String kind=shown.kind;int width;
                if(kind.equals("proof")){
                    width=2;
                    if(part+width>end||!frames.get(part+1).kind.equals("apply")||shown.evidence().length==0||ReplayEvidence.authored(shown.evidence())||frames.get(part+1).evidence().length!=0)fail("Invalid technique operation");
                    if(!shown.board.equals(prior))fail("Proof board differs from pre-operation state");
                }else if(kind.equals("authored-input")){
                    width=3;
                    if(part+width>end||!frames.get(part+1).kind.equals("authored-result")||!frames.get(part+2).kind.equals("authored-apply")||!ReplayEvidence.authored(shown.evidence()))fail("Invalid authored operation");
                    if(!shown.board.equals(prior)||!frames.get(part+1).board.equals(prior))fail("Authored preview changed board");
                    if(!Arrays.equals(shown.evidence(),frames.get(part+1).evidence())||!Arrays.equals(shown.evidence(),frames.get(part+2).evidence()))fail("Authored source changed within operation");
                }else if(kind.equals("manual")||kind.equals("restore-savepoint")||kind.equals("evidence-unavailable")){
                    width=1;if(shown.evidence().length!=0||(!kind.equals("evidence-unavailable")&&end-at!=1))fail("Invalid manual operation");
                }else{fail("Unknown replay event: "+kind);return;}
                ReplayBoard after=frames.get(part+width-1).board;if(after.equals(prior))fail("Operation has no board change");prior=after;part+=width;
            }
            for(int n=at;n<end;n++){
                ReplayFrame frame=frames.get(n);
                if(frame.elapsedMillis<elapsed||frame.elapsedMillis>session.elapsedMillis)fail("Invalid effective time");elapsed=frame.elapsedMillis;
                if(!initial.board.samePuzzle(frame.board))fail("Puzzle definition changed inside an attempt");
                if(frame.evidence().length>0)ReplayEvidence.validate(frame.evidence());
            }
            at=end;
        }
        for(ReplayBookmark marker:session.bookmarks())if(marker.frameIndex<0||marker.frameIndex>=frames.size()||marker.elapsedMillis<0||marker.elapsedMillis>session.elapsedMillis)fail("Invalid savepoint marker");
        if(session.completed&&(!prior.toSudoku().isSolved()||!prior.toSudoku().checkSudoku()))fail("Completed replay has no correct final board");
        // Incorrect intermediate fills/deletions are valid historical actions, never re-solve or correct them.
    }
    private static void fail(String message)throws IOException{throw new IOException(message);}
}
