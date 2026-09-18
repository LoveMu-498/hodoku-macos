package sudoku;
import java.util.*;import java.security.*;
public final class SearchParityProbe {
 public static void main(String[] args)throws Exception{
  for(String text:new String[]{CurrentReasoningProbe.PUZZLE,SwordfishBoxReasoningProbe.CANDIDATES,AnnotationReasoningScopeProbe.CANDIDATES}){
   Sudoku2 b=new Sudoku2();if(text.contains(" ")){String[] ts=text.split(" ");StringBuilder v=new StringBuilder();for(String t:ts)v.append(t.length()==1?t:"0");b.setSudoku(v.toString());for(int c=0;c<81;c++)if(ts[c].length()>1)for(int d=1;d<=9;d++)if(!ts[c].contains(""+d))b.delCandidate(c,d);}else b.setSudoku(text);
   long start=System.nanoTime();List<SolutionStep> steps=new TechniqueStepCatalog().findAllRawSteps(b,null);Set<String> keys=new TreeSet<String>();for(SolutionStep s:steps)keys.add(ReasoningStepIndex.identity(s));byte[] hash=MessageDigest.getInstance("SHA-256").digest(keys.toString().getBytes("UTF-8"));StringBuilder h=new StringBuilder();for(byte v:hash)h.append(String.format("%02x",v));System.out.println(keys.size()+" "+h+" "+((System.nanoTime()-start)/1000000)+"ms");
  }System.exit(0);
 }
}
