package sudoku;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class ReplayRetirementProbe {
 public static void main(String[] args)throws Exception{
  Path dir=Files.createTempDirectory("replay-retirement-");String id=UUID.randomUUID().toString();Path retired=dir.resolve(id+".hrep");
  try(DataOutputStream o=new DataOutputStream(Files.newOutputStream(retired))){o.writeInt(0x4852504c);o.writeInt(4);o.writeUTF(id);o.writeLong(1);o.writeLong(0);o.writeLong(0);o.writeBoolean(false);o.writeBoolean(false);o.writeBoolean(false);o.writeUTF("");o.writeInt(1);}
  Path unrelated=dir.resolve("manual-export.hrep");Files.copy(retired,unrelated);Path symlink=dir.resolve(UUID.randomUUID()+".hrep");Files.createSymbolicLink(symlink,unrelated);
  try(DataOutputStream o=new DataOutputStream(Files.newOutputStream(dir.resolve("active.checkpoint")))){o.writeInt(0x48524331);o.writeUTF(id);}
  ReplaySession current=new ReplaySession(new ReplayBoard(new Sudoku2()),1);Path modern=dir.resolve(current.id+".hrep");ReplayStore.write(modern,current);ReplayRetirement.clean(dir);
  if(Files.exists(retired)||Files.exists(dir.resolve("active.checkpoint"))||!Files.exists(unrelated)||!Files.isSymbolicLink(symlink)||!Files.exists(modern))throw new AssertionError("cleanup ownership");
  ReplayRetirement.clean(dir);System.out.println("PASS retired UUID ownership, checkpoint cleanup, current/renamed/symlink preservation, idempotence");
 }
}
