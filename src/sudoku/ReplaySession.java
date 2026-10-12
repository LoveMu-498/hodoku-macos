/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.util.*;

/** Mutable aggregate owned by the Swing EDT. Frames and boards are immutable. */
public final class ReplaySession {
    public final String id;
    public final long startedAt;
    private ReplaySnapshotList<ReplayBookmark> bookmarks=new ReplaySnapshotList<ReplayBookmark>();
    public List<ReplayBookmark> bookmarks(){return Collections.unmodifiableList(bookmarks.snapshot());}
    public void addBookmark(ReplayBookmark marker){if(marker.frameIndex>=frames.size())throw new IllegalArgumentException("Bookmark frame outside replay");bookmarks.add(marker);}
    private ReplaySnapshotList<ReplayFrame> frames=new ReplaySnapshotList<ReplayFrame>();
    public long elapsedMillis;
    /** Zero until this attempt is finished or superseded. */
    public long endedAt;
    public boolean completed, retained, pinned;
    boolean foreignTimeline;
    public String interruption="";
    public String sourceReplayId="";
    public int sourceFrameIndex=-1;
    private byte[] initialAnnotations=new byte[0];
    public byte[] initialAnnotations(){return initialAnnotations.clone();}
    public void setInitialAnnotations(byte[] bytes){initialAnnotations=bytes==null?new byte[0]:bytes.clone();}
    public ReplaySession(ReplayBoard initial,long now){this(UUID.randomUUID().toString(),now);frames.add(new ReplayFrame(0,now,0,"initial",ReplayText.text("initial"),initial,null));}
    ReplaySession(String id,long startedAt){this.id=id;this.startedAt=startedAt;}
    public List<ReplayFrame> frames(){return Collections.unmodifiableList(frames.snapshot());}
    public int frameCount(){return frames.size();}
    public ReplayFrame last(){return frames.get(frames.size()-1);}
    public void append(List<ReplayFrame> operation){
        if(completed)throw new IllegalStateException("Replay is sealed");
        if(operation.isEmpty())return;
        long id=operation.get(0).operationId;
        if(!frames.isEmpty()&&id<=last().operationId)throw new IllegalArgumentException("Operation IDs must increase");
        for(ReplayFrame f:operation)if(f.operationId!=id)throw new IllegalArgumentException("Mixed operation IDs");
        frames.addAll(operation);
    }
    void setInitialFrameAnnotations(ReplayAnnotations annotations){frames.set(0,frames.get(0).withAnnotations(annotations));}
    void loadFrame(ReplayFrame frame){frames.add(frame);}
    boolean sameDiskState(ReplaySession other){
        return id.equals(other.id)&&startedAt==other.startedAt&&elapsedMillis==other.elapsedMillis&&endedAt==other.endedAt
                &&completed==other.completed&&retained==other.retained&&pinned==other.pinned&&foreignTimeline==other.foreignTimeline
                &&interruption.equals(other.interruption)&&sourceReplayId.equals(other.sourceReplayId)&&sourceFrameIndex==other.sourceFrameIndex
                &&initialAnnotations==other.initialAnnotations&&frames.samePrefix(other.frames)&&bookmarks.samePrefix(other.bookmarks);
    }
    /** Frames are immutable; copy aggregate metadata without export normalization. */
    ReplaySession persistenceSnapshot(){
        ReplaySession result=new ReplaySession(id,startedAt);result.frames=frames.snapshot();result.bookmarks=bookmarks.snapshot();
        result.elapsedMillis=elapsedMillis;result.endedAt=endedAt;result.completed=completed;result.retained=retained;result.pinned=pinned;
        result.foreignTimeline=foreignTimeline;result.interruption=interruption;result.sourceReplayId=sourceReplayId;result.sourceFrameIndex=sourceFrameIndex;result.initialAnnotations=initialAnnotations;return result;
    }
}
