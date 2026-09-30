package sudoku;
import java.util.*;
public final class ChainJunctionProbe {
 static UserChain chain(boolean closed, int[] cells, boolean... edges) {
  UserChain c=new UserChain();c.setClosed(closed);
  for(int cell:cells)c.getNodes().add(new UserChainNode(cell,1));
  for(boolean edge:edges)c.getStrongRelations().add(edge);
  return c;
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static void count(UserChain c,int strong,int weak){ChainJunctionSummary s=ChainJunctionSummary.from(Arrays.asList(c));check(s.available()&&s.strong.size()==strong&&s.weak.size()==weak,"count "+strong+"/"+weak);}
 public static void main(String[] args){
  count(chain(false,new int[]{0}),0,0);
  count(chain(false,new int[]{0,1,2,3,4},true,false,true,false),0,0);
  count(chain(false,new int[]{0,1,2},true,true),1,0);
  count(chain(false,new int[]{0,1,2,3},true,true,true),2,0);
  count(chain(false,new int[]{0,1,2,3,4},true,true,false,false),1,1);
  count(chain(true,new int[]{0,1,2},true,false,true),1,0);
  count(chain(true,new int[]{0,1,2},false,true,false),0,1);
  count(chain(true,new int[]{0,1,2,3},true,false,true,false),0,0);
  UserChain active=chain(false,new int[]{2,3},true),saved=chain(false,new int[]{0,1,2},false,true),old=chain(false,new int[]{60,61,62},false,false);
  ChainJunctionSummary s=ChainJunctionSummary.from(Arrays.asList(active,old,saved));
  check(s.strong.size()==1&&s.weak.isEmpty()&&s.strong.get(0).getCellIndex()==2,"connected continuation or old chain scope");
  s=ChainJunctionSummary.from(Arrays.asList(active,saved,saved));check(s.strong.size()==1,"duplicate segment counted twice");
  s=ChainJunctionSummary.from(Arrays.asList(active,saved,chain(false,new int[]{2,5},false)));check(!s.available(),"branch reported a count");
  s=ChainJunctionSummary.from(Arrays.asList(active,chain(false,new int[]{3,2},false)));check(!s.available(),"conflicting relation reported count");
  UserChain grouped=chain(false,new int[]{0,2,3},true,true);grouped.getNodes().get(1).setGroupCells(new int[]{1,2});
  s=ChainJunctionSummary.from(Arrays.asList(grouped));check(s.strong.size()==1&&s.strong.get(0).cells().length==2,"group counted per cell");
  check(s.description().contains("未验证"),"structure falsely claims proof");
  System.out.println("PASS: alternating, double/triple runs, closure seam, connected continuation, unrelated/duplicate segments, branch/conflict, grouped node");
 }
}
