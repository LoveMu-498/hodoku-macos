package sudoku;

/** A saved-position marker in the complete attempt, not a truncated recording. */
public final class ReplayBookmark {
    public final int frameIndex;
    public final String name;
    public final long wallTimeMillis, elapsedMillis;
    public ReplayBookmark(int frameIndex,String name,long wallTimeMillis,long elapsedMillis){
        if(frameIndex<0||name==null||name.length()>1000||elapsedMillis<0)throw new IllegalArgumentException("Invalid replay bookmark");
        this.frameIndex=frameIndex;this.name=name;this.wallTimeMillis=wallTimeMillis;this.elapsedMillis=elapsedMillis;
    }
}
