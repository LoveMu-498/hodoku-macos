package sudoku;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
public final class ReplaySharingProbe {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  Path helper=Paths.get(args[0]),file=Paths.get(args[1]);byte[] before=Files.readAllBytes(file);
  try{ReplaySharing.launch(helper.resolveSibling("missing-helper"),file);throw new AssertionError("missing helper accepted");}catch(IOException expected){}
  try{ReplaySharing.launch(helper,file.resolveSibling("missing-replay"));throw new AssertionError("missing replay accepted");}catch(IOException expected){}
  Process invalid=new ProcessBuilder(helper.toString(),file.resolveSibling("missing-replay").toString()).start();check(invalid.waitFor()==4,"native invalid file status");
  Process nativeProcess=ReplaySharing.launch(helper,file);BufferedReader output=new BufferedReader(new InputStreamReader(nativeProcess.getInputStream(),"UTF-8"));String line;boolean ready=false;
  while((line=output.readLine())!=null){if(line.equals("READY")){ready=true;break;}}
  check(ready,"helper did not show UI");final boolean[] responsive={false};SwingUtilities.invokeAndWait(()->responsive[0]=true);check(responsive[0],"EDT blocked");
  nativeProcess.getOutputStream().write("cancel\n".getBytes("UTF-8"));nativeProcess.getOutputStream().flush();boolean cancelled=false,completed=false;
  while((line=output.readLine())!=null){cancelled|=line.equals("CANCELLED");completed|=line.equals("COMPLETED");}
  check(nativeProcess.waitFor()==2&&cancelled&&!completed,"cancel falsely completed");check(Arrays.equals(before,Files.readAllBytes(file)),"share changed exported file");
  System.out.println("PASS missing helper/file fallback seam, native invalid status, real helper ready/cancel, responsive EDT, cancellation not completed, durable file unchanged");System.exit(0);
 }
}
