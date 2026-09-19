package sudoku;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Real child-process abrupt termination and bounded checkpoint/codec failure behavior. */
public final class ReplayRecoveryProbe {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] a)throws Exception{
        Path dir=Paths.get(a[0]);Files.createDirectories(dir);System.setProperty("hodoku.data.dir",dir.resolve("data").toString());
        if(a.length>1){
            Sudoku2 board=new Sudoku2();board.setSudoku("530070000600195000098000060800060003400803001700020006060000280000419005000080079");
            ReplaySession s=new ReplaySession(new ReplayBoard(board),1);ReplayStore.write(dir.resolve(s.id+".hrep"),s);
            ReplayRecovery r=new ReplayRecovery(dir);r.checkpoint(s,false);
            board.setCell(2,4);s.append(Collections.singletonList(new ReplayFrame(1,2,1000,"manual","fill",new ReplayBoard(board),null)));
            s.elapsedMillis=1000;ReplayStore.write(dir.resolve(s.id+".hrep"),s);s.elapsedMillis=2000;r.checkpoint(s,a[1].equals("clean"));
            Files.write(dir.resolve("id"),s.id.getBytes("UTF-8"));
            if(a[1].equals("crash"))Runtime.getRuntime().halt(7);return;
        }
        for(String mode:new String[]{"crash","clean"}){
            Path target=dir.resolve(mode);
            Process p=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-cp",System.getProperty("java.class.path"),ReplayRecoveryProbe.class.getName(),target.toString(),mode).inheritIO().start();
            check(p.waitFor()==(mode.equals("crash")?7:0),"child status");
            ReplayRecovery recovery=new ReplayRecovery(target);ReplaySession restored=recovery.restore();
            check(restored.last().board.values()[2]==4&&restored.frames().size()==2,"committed group lost");check(restored.elapsedMillis==2000,"elapsed checkpoint lost");
            check(restored.interruption.isEmpty()==mode.equals("clean"),"interruption flag");
            Path replay=target.resolve(restored.id+".hrep");long modified=Files.getLastModifiedTime(replay).toMillis();byte[] original=Files.readAllBytes(replay);
            restored.elapsedMillis=5000;recovery.checkpoint(restored,false);check(Arrays.equals(original,Files.readAllBytes(replay))&&modified==Files.getLastModifiedTime(replay).toMillis(),"heartbeat rewrote payload");
            Files.write(replay,new byte[]{1,2});try{recovery.restore();throw new AssertionError("corruption accepted");}catch(java.io.IOException expected){}
        }
        Path broken=dir.resolve("not-directory");Files.write(broken,new byte[]{0});try{new ReplayRecovery(broken).checkpoint(new ReplaySession(new ReplayBoard(new Sudoku2()),1),false);throw new AssertionError("write failure hidden");}catch(java.io.IOException expected){}
        AtomicLong nano=new AtomicLong(),wall=new AtomicLong();ReplayClock c=new ReplayClock(nano::get,wall::get);
        nano.set(1000000000L);check(c.elapsedMillis()==1000,"active");wall.set(9999999);check(c.elapsedMillis()==1000,"sleep/date affected nanos");nano.set(2000000000L);check(c.elapsedMillis()==2000,"wake");
        System.out.println("PASS child crash and clean restart, groups/elapsed, unchanged payload heartbeat, corruption/write fault and injected sleep-clock behavior; no real sleep");
    }
}
