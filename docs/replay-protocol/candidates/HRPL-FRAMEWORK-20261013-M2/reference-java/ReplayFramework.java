/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Application adapter for inert common frames. Native solving and recording stay independent. */
final class ReplayFramework {
    private ReplayFramework(){}
    static final String FORMAT="HRPL-FRAME/0.2";
    static final class Source {
        final ReplayFrameCodec.Document document;
        final List<ReplayFrame> frames;
        final long elapsed,ended;
        final boolean completed;
        Source(ReplayFrameCodec.Document document,ReplaySession session){this.document=document;frames=session.frames();elapsed=session.elapsedMillis;ended=session.endedAt;completed=session.completed;}
        boolean unchanged(ReplaySession session){return elapsed==session.elapsedMillis&&ended==session.endedAt&&completed==session.completed&&frames.equals(session.frames())&&session.bookmarks().isEmpty();}
    }
    static ReplaySession read(Path file)throws IOException{return read(ReplayFrameCodec.read(file));}
    static ReplaySession read(ReplayFrameCodec.Document document)throws IOException{
        document=ReplayFrameCodec.read(document.original);
        Map<String,Object> root=document.root,meta=ReplayFrameCodec.map(root.get("session"));
        // External printable IDs are data, never filesystem path components.
        String id=UUID.nameUUIDFromBytes(("hrpl-frame:"+meta.get("id")).getBytes(StandardCharsets.UTF_8)).toString();
        ReplaySession session=new ReplaySession(id,n(meta.get("startedAtMs")));
        session.elapsedMillis=n(meta.get("elapsedMs"));session.endedAt=n(meta.get("endedAtMs"));session.completed=(Boolean)meta.get("completed");session.foreignTimeline=true;
        long operation=-1;String previous=null;
        for(Object value:ReplayFrameCodec.list(root.get("frames"))){
            Map<String,Object> f=immutableMap(ReplayFrameCodec.map(value));String group=(String)f.get("operationGroup");if(!group.equals(previous)){operation++;previous=group;}
            String phase=(String)f.get("phase");
            session.loadFrame(new ReplayFrame(operation,n(f.get("wallTimeMs")),n(f.get("elapsedMs")),"input".equals(phase)||"evidence".equals(phase)||"result".equals(phase)?"foreign-method":"foreign-action",(String)f.get("phaseLabel"),board(f),null,ReplayAnnotations.EMPTY,f));
        }
        session.frameworkSource=new Source(document,session);return session;
    }
    static ReplayBoard board(Map<String,Object> f)throws IOException{
        Map<String,Object> b=ReplayFrameCodec.map(f.get("board"));List<Object> v=l(b.get("values")),fixed=l(b.get("fixed")),c=l(b.get("candidates")),u=l(b.get("userCandidates"));
        int[] values=new int[81];boolean[] givens=new boolean[81];short[] candidates=new short[81],user=new short[81];
        for(int i=0;i<81;i++){values[i]=(int)n(v.get(i));givens[i]=(Boolean)fixed.get(i);candidates[i]=(short)n(c.get(i));user[i]=(short)n(u.get(i));}
        return new ReplayBoard(values,givens,candidates,user);
    }
    static Map<String,Object> board(ReplayBoard board){
        List<Object> v=new ArrayList<>(),f=new ArrayList<>(),c=new ArrayList<>(),u=new ArrayList<>();int[] values=board.values();boolean[] fixed=board.fixed();short[] candidates=board.candidates(),user=board.userCandidates();
        for(int i=0;i<81;i++){v.add((long)values[i]);f.add(fixed[i]);c.add((long)candidates[i]);u.add((long)user[i]);}
        return object("values",v,"fixed",f,"candidates",c,"userCandidates",u);
    }
    static Map<String,Object> document(ReplaySession session)throws IOException{
        if(session.frameworkSource!=null&&session.frameworkSource.unchanged(session))return session.frameworkSource.document.root;
        List<Object> frames=new ArrayList<>(),losses=new ArrayList<>(),sourceIds=new ArrayList<>();boolean edited=session.frameworkSource!=null;
        String identity=edited?UUID.randomUUID().toString():session.id;
        for(int index=0;index<session.frameCount();index++){
            ReplayFrame frame=session.frames().get(index);Map<String,Object> f;
            if(frame.framework!=null){
                f=ReplayFrameCodec.deepCopy(frame.framework);String old=(String)f.get("id");sourceIds.add(old);
                f.put("id",UUID.randomUUID().toString());f.put("wallTimeMs",frame.wallTimeMillis);f.put("elapsedMs",frame.elapsedMillis);f.put("board",board(frame.board));f.put("phaseLabel",frame.label);
                for(Object item:l(f.get("native")))losses.add(object("frameId",f.get("id"),"namespace",ReplayFrameCodec.map(item).get("namespace"),"reason","stale-binding"));
                f.put("native",new ArrayList<Object>());
                if(f.get("preview")!=null){
                    boolean changedView=!Arrays.equals(ReplayFrameCodec.canonical(frame.framework.get("board")),ReplayFrameCodec.canonical(f.get("board")))
                            ||!Objects.equals(frame.framework.get("phaseLabel"),f.get("phaseLabel"));
                    if(changedView&&ReplayFrameRenderer.usePreview(frame.framework))throw new IOException("Cannot change a frame whose unsupported display is available only as a frozen image; create a new attempt from its board instead");
                    // Regenerate from this frame's actual frozen view; a binding is never blindly transplanted.
                    ReplaySession view=new ReplaySession("view",frame.wallTimeMillis);Map<String,Object> display=ReplayFrameCodec.deepCopy(f);display.put("preview",changedView?null:f.get("preview"));
                    view.loadFrame(new ReplayFrame(0,frame.wallTimeMillis,frame.elapsedMillis,"foreign-action",frame.label,frame.board,null,ReplayAnnotations.EMPTY,display));
                    ReplayFrameCapture.Capture capture=ReplayFrameCapture.capture(view,0);
                    f.put("preview",preview(capture.previewPng,ReplayFrameCodec.binding(f)));
                }
            }else{
                ReplayFrameCapture.Capture capture=ReplayFrameCapture.capture(session,index);
                String description=capture.description;
                List<Map<String,Object>> display=capture.display;
                f=object("id",identity+":"+index,"operationGroup","operation:"+frame.operationId,"phase",phase(frame.kind),"phaseLabel",description,"wallTimeMs",frame.wallTimeMillis,"elapsedMs",frame.elapsedMillis,"board",board(frame.board),"display",display,"native",new ArrayList<Object>(),"preview",null);
                String binding=ReplayFrameCodec.binding(f);
                f.put("preview",preview(capture.previewPng,binding));
                byte[] payload=nativeFrame(session,index);List<Object> nativeItems=new ArrayList<>();
                for(int offset=0,part=0;offset<payload.length;offset+=8*1024*1024,part++){
                    byte[] chunk=Arrays.copyOfRange(payload,offset,Math.min(payload.length,offset+8*1024*1024));
                    nativeItems.add(object("namespace","org.hodoku.mac.frame.part"+part,"version",1L,"binding",binding,"data",Base64.getEncoder().encodeToString(chunk)));
                }
                f.put("native",nativeItems);
            }
            frames.add(f);
        }
        Object source=edited?object("fileSha256",ReplayFrameCodec.sha256(session.frameworkSource.document.original),"frameIds",sourceIds):null;
        return object("format",FORMAT,"session",object("id",identity,"startedAtMs",session.startedAt,"endedAtMs",session.endedAt,"elapsedMs",session.elapsedMillis,"completed",session.completed),"frames",frames,"source",source,"losses",losses);
    }
    static Map<String,Object> preview(byte[] png,String binding){return object("mime","image/png","coverage","full-frame","binding",binding,"data",Base64.getEncoder().encodeToString(png));}
    private static String phase(String kind){if(kind.endsWith("input"))return "input";if(kind.endsWith("result"))return "result";if(kind.equals("proof"))return "evidence";if(kind.endsWith("apply")||kind.equals("manual")||kind.equals("restore-savepoint"))return "applied";return "other";}
    private static byte[] nativeFrame(ReplaySession session,int index)throws IOException{
        ReplayFrame f=session.frames().get(index);ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x48464e31);out.writeInt(1);out.writeLong(f.operationId);ReplayTextCodec.write(out,f.kind);ReplayTextCodec.write(out,f.label);ReplayTextCodec.write(out,f.annotations.overlay);ReplayAnnotations.writeBytes(out,f.evidence());f.annotations.write(out,6);
        out.writeBoolean(index==0);
        if(index==0){ReplayTextCodec.write(out,session.sourceReplayId);out.writeInt(session.sourceFrameIndex);ReplayAnnotations.writeBytes(out,session.initialAnnotations());out.writeInt(session.bookmarks().size());for(ReplayBookmark b:session.bookmarks()){out.writeInt(b.frameIndex);ReplayTextCodec.write(out,b.name);out.writeLong(b.wallTimeMillis);out.writeLong(b.elapsedMillis);}}
        out.flush();return bytes.toByteArray();
    }
    static void write(Path destination,ReplaySession session)throws IOException{
        Source source=session.frameworkSource;
        if(source!=null&&source.unchanged(session)){atomicWrite(destination,source.document.original);return;}
        byte[] encoded=ReplayFrameCodec.encode(document(session));
        if(source!=null)archiveOriginal(destination.toAbsolutePath().getParent().resolve("replay-sources"),session);
        atomicWrite(destination,encoded);
    }
    static Path archiveOriginal(Path directory,ReplaySession session)throws IOException{
        if(session.frameworkSource==null)return null;byte[] original=session.frameworkSource.document.original;
        Files.createDirectories(directory);Path file=directory.resolve(ReplayFrameCodec.sha256(original)+".hrep");
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){
            if(Files.isSymbolicLink(file)||Files.size(file)!=original.length||!Arrays.equals(original,Files.readAllBytes(file)))throw new IOException("Original replay archive differs");
        }else{
            Path temp=Files.createTempFile(directory,"replay-source-",".tmp");
            try{try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(original);out.getFD().sync();}
                // Publish a complete inode without replacing an existing source archive.
                try{Files.createLink(file,temp);}catch(FileAlreadyExistsException exists){if(Files.isSymbolicLink(file)||Files.size(file)!=original.length||!Arrays.equals(original,Files.readAllBytes(file)))throw new IOException("Original replay archive differs",exists);}
            }finally{Files.deleteIfExists(temp);}
        }
        return file;
    }
    static void atomicWrite(Path destination,byte[] bytes)throws IOException{
        if(bytes.length>ReplayStore.MAX_BYTES)throw new IOException("Replay file exceeds 128 MiB");
        Path absolute=destination.toAbsolutePath(),parent=absolute.getParent();Files.createDirectories(parent);
        if(Files.isSymbolicLink(absolute))throw new IOException("Replay destination must not be a symbolic link");
        Path temp=Files.createTempFile(parent,"replay-frame-",".tmp");
        try{try(FileOutputStream out=new FileOutputStream(temp.toFile())){out.write(bytes);out.getFD().sync();}
            Files.move(temp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temp);}
    }
    static String notice(ReplayFrame frame)throws IOException{
        if(frame.framework==null)return frame.annotations.notice();
        boolean unknown=false,important=false;
        for(Object value:l(frame.framework.get("display"))){Map<String,Object> o=ReplayFrameCodec.map(value);if(!Arrays.asList("mark","highlight","line","region","text").contains(o.get("type"))){unknown=true;important|=(Boolean)o.get("important");}}
        return important?(frame.framework.get("preview")==null?"部分观看：缺少重要显示内容。":"使用已冻结的完整画面。"):(unknown?"已保留未识别的可选数据。":"");
    }
    static Map<String,Object> object(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    private static Map<String,Object> immutableMap(Map<String,Object> source){Map<String,Object> result=new LinkedHashMap<>();for(Map.Entry<String,Object> e:source.entrySet())result.put(e.getKey(),immutable(e.getValue()));return Collections.unmodifiableMap(result);}
    @SuppressWarnings("unchecked") private static Object immutable(Object value){if(value instanceof Map)return immutableMap((Map<String,Object>)value);if(value instanceof List){List<Object> result=new ArrayList<>();for(Object item:(List<?>)value)result.add(immutable(item));return Collections.unmodifiableList(result);}return value;}
    static long n(Object v)throws IOException{return ReplayFrameCodec.number(v);}
    static List<Object> l(Object v)throws IOException{return ReplayFrameCodec.list(v);}
}
