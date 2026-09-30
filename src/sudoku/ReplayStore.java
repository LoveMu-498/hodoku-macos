package sudoku;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Windows HRPL v3 prefix plus optional, bounded Mac extension. No legacy Mac decoder. */
public final class ReplayStore {
    private static final int MAGIC=0x4852504c, VERSION=3, EXTENSION=0x484d5831, MAX_FRAMES=100000;
    public static final long MAX_BYTES=128L*1024*1024;
    private ReplayStore(){}
    public static void write(Path path,ReplaySession session)throws IOException{
        Path parent=path.toAbsolutePath().getParent();Files.createDirectories(parent);Path temp=Files.createTempFile(parent,"replay-",".tmp");
        try{
            try(FileOutputStream file=new FileOutputStream(temp.toFile());DataOutputStream out=new DataOutputStream(new BufferedOutputStream(file))){
                out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(session.id);out.writeLong(session.startedAt);out.writeLong(session.elapsedMillis);out.writeLong(session.endedAt);out.writeBoolean(session.completed);
                List<ReplayFrame> frames=session.frames();if(frames.size()<1||frames.size()>MAX_FRAMES)throw new IOException("Replay frame limit exceeded");out.writeInt(frames.size());
                for(ReplayFrame f:frames){
                    out.writeLong(f.operationId);out.writeLong(f.wallTimeMillis);out.writeLong(f.elapsedMillis);out.writeUTF(f.label);out.writeUTF(f.annotations.overlay);out.writeBoolean(f.methodStep());
                    int[] v=f.board.values();boolean[] fixed=f.board.fixed();short[] c=f.board.candidates(),u=f.board.userCandidates();
                    for(int i=0;i<81;i++){out.writeByte(v[i]);out.writeBoolean(fixed[i]);out.writeShort(c[i]);out.writeShort(u[i]);}
                    limit(out);
                }
                // The reference Windows reader stops after the common frames. Its writer
                // intentionally drops this optional tail when the user saves there.
                out.writeInt(EXTENSION);out.writeInt(3);out.writeBoolean(session.foreignTimeline);out.writeBoolean(session.retained);out.writeBoolean(session.pinned);out.writeUTF(session.interruption);out.writeInt(frames.size());
                for(ReplayFrame f:frames){out.writeUTF(f.kind);ReplayAnnotations.writeBytes(out,f.evidence());f.annotations.write(out);limit(out);}
                List<ReplayBookmark> markers=session.bookmarks();if(markers.size()>10000)throw new IOException("Too many savepoints");out.writeInt(markers.size());
                for(ReplayBookmark marker:markers){out.writeInt(marker.frameIndex);out.writeUTF(marker.name);out.writeLong(marker.wallTimeMillis);out.writeLong(marker.elapsedMillis);}
                out.writeUTF(session.sourceReplayId);out.writeInt(session.sourceFrameIndex);ReplayAnnotations.writeBytes(out,session.initialAnnotations());limit(out);out.flush();file.getFD().sync();
            }
            try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static void limit(DataOutputStream out)throws IOException{if(out.size()>MAX_BYTES)throw new IOException("Replay size limit exceeded");}
    public static ReplaySession read(Path path)throws IOException{
        if(Files.size(path)>MAX_BYTES)throw new IOException("Replay file is too large");
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))){
            if(in.readInt()!=MAGIC)throw new IOException("Unknown replay format");int version=in.readInt();if(version!=3)throw new IOException("Unsupported replay format; old Mac recordings are retired");
            String id=in.readUTF();if(!id.matches("[a-zA-Z0-9-]{1,80}"))throw new IOException("Invalid replay identity");
            ReplaySession s=new ReplaySession(id,in.readLong());s.elapsedMillis=in.readLong();s.endedAt=in.readLong();s.completed=in.readBoolean();s.foreignTimeline=true;
            int count=ReplayAnnotations.count(in,MAX_FRAMES);if(count==0||s.elapsedMillis<0)throw new IOException("Invalid replay header");
            List<ReplayFrame> base=new ArrayList<ReplayFrame>();long lastId=-1,lastTime=0;
            for(int f=0;f<count;f++){
                long operation=in.readLong(),wall=in.readLong(),elapsed=in.readLong();String label=in.readUTF(),overlay=in.readUTF();boolean method=in.readBoolean();
                if(operation<lastId||elapsed<lastTime)throw new IOException("Invalid frame order");lastId=operation;lastTime=elapsed;
                int[] v=new int[81];boolean[] fixed=new boolean[81];short[] c=new short[81],u=new short[81];for(int i=0;i<81;i++){v[i]=in.readUnsignedByte();fixed[i]=in.readBoolean();c[i]=in.readShort();u[i]=in.readShort();}
                base.add(new ReplayFrame(operation,wall,elapsed,method?"foreign-method":"foreign-action",label,new ReplayBoard(v,fixed,c,u),null,ReplayAnnotations.fromWindows(overlay)));
            }
            int next=in.read();
            if(next!=-1){
                int magic=(next<<24)|(in.readUnsignedByte()<<16)|(in.readUnsignedByte()<<8)|in.readUnsignedByte();int extensionVersion=in.readInt();if(magic!=EXTENSION||(extensionVersion<1||extensionVersion>3))throw new IOException("Unknown replay extension");
                s.foreignTimeline=in.readBoolean();s.retained=in.readBoolean();s.pinned=in.readBoolean();s.interruption=in.readUTF();if(in.readInt()!=count)throw new IOException("Extension frame count mismatch");
                for(ReplayFrame b:base){String kind=in.readUTF();byte[] evidence=ReplayAnnotations.readBytes(in);if(evidence.length>0)ReplayEvidence.validate(evidence);ReplayAnnotations annotations=ReplayAnnotations.read(in,b.annotations.overlay,extensionVersion);ReplayFrame frame=new ReplayFrame(b.operationId,b.wallTimeMillis,b.elapsedMillis,kind,b.label,b.board,evidence,annotations);if(frame.methodStep()!=b.methodStep())throw new IOException("Extension method flag mismatch");s.loadFrame(frame);}
                for(int n=ReplayAnnotations.count(in,10000);n>0;n--)s.addBookmark(new ReplayBookmark(in.readInt(),in.readUTF(),in.readLong(),in.readLong()));
                s.sourceReplayId=in.readUTF();s.sourceFrameIndex=in.readInt();s.setInitialAnnotations(ReplayAnnotations.readBytes(in));if(in.read()!=-1)throw new IOException("Trailing replay bytes");
            }else{
                for(ReplayFrame frame:base)s.loadFrame(frame);
                // Windows leaves elapsedMillis at zero. Recorded frame times are the
                // authority; endedAt-startedAt includes an unrecorded trailing interval.
                s.elapsedMillis=Math.max(s.elapsedMillis,lastTime);
            }
            return s;
        }catch(IllegalArgumentException ex){throw new IOException("Invalid replay data",ex);}
    }
}
