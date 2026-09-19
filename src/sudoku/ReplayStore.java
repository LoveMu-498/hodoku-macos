package sudoku;

import java.io.*;
import java.nio.file.*;
import java.util.List;

/** Versioned bounded data codec. Never executes Java serialization or XML beans. */
public final class ReplayStore {
    private static final int MAGIC=0x4852504c, VERSION=4, MAX_FRAMES=100000, MAX_EVIDENCE=4*1024*1024;
    public static final long MAX_BYTES=128L*1024*1024;
    private ReplayStore(){}
    public static void write(Path path,ReplaySession session)throws IOException{
        Path parent=path.toAbsolutePath().getParent();Files.createDirectories(parent);
        Path temp=Files.createTempFile(parent,"replay-",".tmp");
        try{
            try(FileOutputStream file=new FileOutputStream(temp.toFile());DataOutputStream out=new DataOutputStream(new BufferedOutputStream(file))){
                out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(session.id);out.writeLong(session.startedAt);
                out.writeLong(session.elapsedMillis);out.writeLong(session.endedAt);out.writeBoolean(session.completed);out.writeBoolean(session.retained);out.writeBoolean(session.pinned);out.writeUTF(session.interruption);
                List<ReplayFrame> frames=session.frames();if(frames.size()>MAX_FRAMES)throw new IOException("Replay frame limit exceeded");out.writeInt(frames.size());
                for(ReplayFrame f:frames){
                    out.writeLong(f.operationId);out.writeLong(f.wallTimeMillis);out.writeLong(f.elapsedMillis);out.writeUTF(f.kind);out.writeUTF(f.label);
                    int[] v=f.board.values();boolean[] fixed=f.board.fixed();short[] c=f.board.candidates(),u=f.board.userCandidates();
                    for(int i=0;i<81;i++){out.writeByte(v[i]);out.writeBoolean(fixed[i]);out.writeShort(c[i]);out.writeShort(u[i]);}
                    byte[] evidence=f.evidence();if(evidence.length>MAX_EVIDENCE)throw new IOException("Replay evidence limit exceeded");out.writeInt(evidence.length);out.write(evidence);
                }
                List<ReplayBookmark> markers=session.bookmarks();if(markers.size()>10000)throw new IOException("Too many savepoint markers");out.writeInt(markers.size());
                for(ReplayBookmark marker:markers){out.writeInt(marker.frameIndex);out.writeUTF(marker.name);out.writeLong(marker.wallTimeMillis);out.writeLong(marker.elapsedMillis);}
                out.writeUTF(session.sourceReplayId);out.writeInt(session.sourceFrameIndex);
                byte[] initialAnnotations=session.initialAnnotations();if(initialAnnotations.length>MAX_EVIDENCE)throw new IOException("Initial annotations too large");out.writeInt(initialAnnotations.length);out.write(initialAnnotations);
                out.flush();file.getFD().sync();
            }
            if(Files.size(temp)>MAX_BYTES)throw new IOException("Replay size limit exceeded");
            try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    public static ReplaySession read(Path path)throws IOException{
        if(Files.size(path)>MAX_BYTES)throw new IOException("Replay file is too large");
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))){
            if(in.readInt()!=MAGIC)throw new IOException("Unknown replay format");
            int version=in.readInt();if(version<1||version>VERSION)throw new IOException("Unknown replay version");
            String id=in.readUTF();if(!id.matches("[a-zA-Z0-9-]{1,80}"))throw new IOException("Invalid replay identity");
            ReplaySession s=new ReplaySession(id,in.readLong());s.elapsedMillis=in.readLong();s.endedAt=version>=2?in.readLong():0;s.completed=in.readBoolean();s.retained=in.readBoolean();s.pinned=in.readBoolean();s.interruption=in.readUTF();
            int count=in.readInt();if(count<1||count>MAX_FRAMES||s.elapsedMillis<0)throw new IOException("Invalid replay header");
            long lastId=-1;
            for(int f=0;f<count;f++){
                long operation=in.readLong(),wall=in.readLong(),elapsed=in.readLong();String kind=in.readUTF(),label=in.readUTF();
                if(operation<lastId||elapsed<0)throw new IOException("Invalid frame order");lastId=operation;
                int[] v=new int[81];boolean[] fixed=new boolean[81];short[] c=new short[81],u=new short[81];
                for(int i=0;i<81;i++){v[i]=in.readUnsignedByte();fixed[i]=in.readBoolean();c[i]=in.readShort();u[i]=in.readShort();}
                int n=in.readInt();if(n<0||n>MAX_EVIDENCE)throw new IOException("Invalid evidence size");byte[] evidence=new byte[n];in.readFully(evidence);if(evidence.length>0)ReplayEvidence.validate(evidence);
                try{s.loadFrame(new ReplayFrame(operation,wall,elapsed,kind,label,new ReplayBoard(v,fixed,c,u),evidence));}catch(IllegalArgumentException e){throw new IOException("Invalid replay board",e);}
            }
            if(version>=3){int markers=in.readInt();if(markers<0||markers>10000)throw new IOException("Invalid savepoint count");
                for(int i=0;i<markers;i++){try{s.addBookmark(new ReplayBookmark(in.readInt(),in.readUTF(),in.readLong(),in.readLong()));}catch(IllegalArgumentException e){throw new IOException("Invalid savepoint marker",e);}}
            }
            if(version>=4){s.sourceReplayId=in.readUTF();s.sourceFrameIndex=in.readInt();
                if((s.sourceReplayId.isEmpty()&&s.sourceFrameIndex!=-1)||(!s.sourceReplayId.isEmpty()&&(!s.sourceReplayId.matches("[a-zA-Z0-9-]{1,80}")||s.sourceFrameIndex<0||s.sourceFrameIndex>=MAX_FRAMES)))throw new IOException("Invalid source frame identity");
                int length=in.readInt();if(length<0||length>MAX_EVIDENCE)throw new IOException("Invalid initial annotation size");byte[] annotations=new byte[length];in.readFully(annotations);
                if(length>0){ReplayEvidence raw=ReplayEvidence.decode(annotations);if(!raw.status.equals("PENDING")||raw.proofBytes().length!=0)throw new IOException("Initial annotations contain historical authorization");}
                s.setInitialAnnotations(annotations);
            }
            if(in.read()!=-1)throw new IOException("Unexpected trailing replay data");return s;
        }
    }
}
