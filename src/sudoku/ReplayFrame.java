package sudoku;

/** One immutable viewing frame. Several frames can belong to one operation. */
public final class ReplayFrame {
    public final long operationId, wallTimeMillis, elapsedMillis;
    public final String kind, label;
    public final ReplayBoard board;
    private final byte[] evidence;
    final ReplayAnnotations annotations;
    boolean methodStep(){return kind.equals("proof")||kind.equals("authored-input")||kind.equals("authored-result")||kind.equals("foreign-method");}
    ReplayFrame withAnnotations(ReplayAnnotations value){return new ReplayFrame(operationId,wallTimeMillis,elapsedMillis,kind,label,board,evidence,value);}
    public ReplayFrame(long operationId,long wallTimeMillis,long elapsedMillis,String kind,String label,ReplayBoard board,byte[] evidence){
        this(operationId,wallTimeMillis,elapsedMillis,kind,label,board,evidence,ReplayAnnotations.EMPTY);
    }
    ReplayFrame(long operationId,long wallTimeMillis,long elapsedMillis,String kind,String label,ReplayBoard board,byte[] evidence,ReplayAnnotations annotations){
        this.annotations=annotations;
        if(operationId<0 || elapsedMillis<0 || board==null || kind==null || label==null)throw new IllegalArgumentException("Invalid replay frame");
        this.operationId=operationId;this.wallTimeMillis=wallTimeMillis;this.elapsedMillis=elapsedMillis;
        this.kind=kind;this.label=label;this.board=board;this.evidence=evidence==null?new byte[0]:evidence.clone();
    }
    public byte[] evidence(){return evidence.clone();}
}
