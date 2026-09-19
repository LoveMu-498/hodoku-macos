package sudoku;

import java.util.*;

/** Mutable aggregate owned by the Swing EDT. Frames and boards are immutable. */
public final class ReplaySession {
    public final String id;
    public final long startedAt;
    private final List<ReplayBookmark> bookmarks=new ArrayList<ReplayBookmark>();
    public List<ReplayBookmark> bookmarks(){return Collections.unmodifiableList(new ArrayList<ReplayBookmark>(bookmarks));}
    public void addBookmark(ReplayBookmark marker){if(marker.frameIndex>=frames.size())throw new IllegalArgumentException("Bookmark frame outside replay");bookmarks.add(marker);}
    private final List<ReplayFrame> frames=new ArrayList<ReplayFrame>();
    public long elapsedMillis;
    /** Zero until this attempt is finished or superseded. */
    public long endedAt;
    public boolean completed, retained, pinned;
    public String interruption="";
    public String sourceReplayId="";
    public int sourceFrameIndex=-1;
    private byte[] initialAnnotations=new byte[0];
    public byte[] initialAnnotations(){return initialAnnotations.clone();}
    public void setInitialAnnotations(byte[] bytes){initialAnnotations=bytes==null?new byte[0]:bytes.clone();}
    public ReplaySession(ReplayBoard initial,long now){this(UUID.randomUUID().toString(),now);frames.add(new ReplayFrame(0,now,0,"initial",ReplayText.text("initial"),initial,null));}
    ReplaySession(String id,long startedAt){this.id=id;this.startedAt=startedAt;}
    public List<ReplayFrame> frames(){return Collections.unmodifiableList(new ArrayList<ReplayFrame>(frames));}
    public ReplayFrame last(){return frames.get(frames.size()-1);}
    public void append(List<ReplayFrame> operation){
        if(completed)throw new IllegalStateException("Replay is sealed");
        if(operation.isEmpty())return;
        long id=operation.get(0).operationId;
        if(!frames.isEmpty()&&id<=last().operationId)throw new IllegalArgumentException("Operation IDs must increase");
        for(ReplayFrame f:operation)if(f.operationId!=id)throw new IllegalArgumentException("Mixed operation IDs");
        frames.addAll(operation);
    }
    void loadFrame(ReplayFrame frame){frames.add(frame);}
}
