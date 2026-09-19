package sudoku;
import java.io.*;
import java.awt.Color;
import java.util.*;

/** Authored input and result envelope. Every authored frame carries the same source for branching. */
public final class ReplayEvidence {
 private static final int MAGIC=0x48524157,VERSION=1,MAX=4096;
 public final String source,status,problem;public final int invalidEdge;
 private final byte[] proof;private final List<SudokuSet> boxes;private final List<UserChain> chains;private final Set<String> invalid;
 private ReplayEvidence(String source,String status,String problem,int edge,byte[] proof,List<SudokuSet> boxes,List<UserChain> chains,Set<String> invalid){this.source=source;this.status=status;this.problem=problem;invalidEdge=edge;this.proof=proof;this.boxes=boxes;this.chains=chains;this.invalid=invalid;}
 public static byte[] input(String source,List<SudokuSet> boxes,List<UserChain> chains){return encode(new ReplayEvidence(source,"PENDING","NONE",-1,new byte[0],boxes,chains,Collections.<String>emptySet()));}
 public static byte[] result(byte[] raw,byte[] proof,UserChainValidator.Result result,String fallback){
  try{ReplayEvidence e=decode(raw);return encode(new ReplayEvidence(e.source,result==null?fallback:result.status.name(),result==null?"NONE":result.problem.name(),result==null?-1:result.invalidEdge,proof,e.boxes,e.chains,result==null?Collections.<String>emptySet():result.invalidRelations));}catch(IOException ex){throw new IllegalArgumentException(ex);}
 }
 public static boolean authored(byte[] bytes){return bytes.length>=4&&((bytes[0]&255)<<24|(bytes[1]&255)<<16|(bytes[2]&255)<<8|(bytes[3]&255))==MAGIC;}
 public static void validate(byte[] bytes)throws IOException{if(authored(bytes))decode(bytes);else ReplayProof.decode(bytes);}
 public static SolutionStep proof(byte[] bytes)throws IOException{return authored(bytes)?ReplayProof.decode(decode(bytes).proof):ReplayProof.decode(bytes);}
 public byte[] proofBytes(){return proof.clone();}
 public List<SudokuSet> boxes(){List<SudokuSet> a=new ArrayList<SudokuSet>();for(SudokuSet b:boxes)a.add(b.clone());return a;}
 public List<UserChain> chains(){try{return decode(encode(this)).chains;}catch(IOException e){throw new IllegalStateException(e);}}
 public Set<String> invalidRelations(){return new HashSet<String>(invalid);}
 public String diagnostic(){return source+" · "+status+("NONE".equals(problem)?"":" · "+problem)+(invalidEdge<0?"":" · edge "+invalidEdge);}
 private static byte[] encode(ReplayEvidence e){try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream o=new DataOutputStream(bytes);o.writeInt(MAGIC);o.writeInt(VERSION);o.writeUTF(e.source);o.writeUTF(e.status);o.writeUTF(e.problem);o.writeInt(e.invalidEdge);o.writeInt(e.proof.length);o.write(e.proof);
  o.writeInt(e.boxes.size());for(SudokuSet b:e.boxes){o.writeInt(b.size());for(int n=0;n<b.size();n++)o.writeInt(b.get(n));}
  o.writeInt(e.chains.size());for(UserChain c:e.chains){o.writeLong(c.getSourceId());o.writeBoolean(c.isClosed());o.writeBoolean(c.isActive());o.writeBoolean(c.isNextStrong());o.writeUTF(c.getAnalysisResult()==null?"":c.getAnalysisResult());o.writeInt(c.getNodes().size());for(UserChainNode n:c.getNodes()){o.writeInt(n.getCellIndex());o.writeInt(n.getCandidate());color(o,n.getColor());int[] group=n.getGroupCells();o.writeInt(group==null?0:group.length);if(group!=null)for(int cell:group)o.writeInt(cell);}o.writeInt(c.getStrongRelations().size());for(Boolean strong:c.getStrongRelations())o.writeByte(strong==null?2:strong?1:0);o.writeInt(c.getRelationColors().size());for(Color color:c.getRelationColors())color(o,color);}
  o.writeInt(e.invalid.size());for(String s:new TreeSet<String>(e.invalid))o.writeUTF(s);o.flush();return bytes.toByteArray();}catch(IOException ex){throw new IllegalArgumentException(ex);}}
 public static ReplayEvidence decode(byte[] bytes)throws IOException{
  if(bytes.length>4*1024*1024)throw new IOException("Authored evidence too large");try(DataInputStream i=new DataInputStream(new ByteArrayInputStream(bytes))){if(i.readInt()!=MAGIC||i.readInt()!=VERSION)throw new IOException("Unknown authored evidence version");String source=i.readUTF(),status=i.readUTF(),problem=i.readUTF();if(!source.equals("BOX")&&!source.equals("FREE_CHAIN"))throw new IOException("Invalid authored source");if(!status.equals("PENDING")&&!status.equals("NATIVE_MATCH"))UserChainValidator.Status.valueOf(status);UserChainValidator.Problem.valueOf(problem);int edge=i.readInt();if(edge< -1||edge>MAX)throw new IOException("Invalid diagnostic edge");int length=i.readInt();if(length<0||length>4*1024*1024)throw new IOException("Invalid embedded proof size");byte[] proof=new byte[length];i.readFully(proof);ReplayProof.decode(proof);
   List<SudokuSet> boxes=new ArrayList<SudokuSet>();for(int n=count(i);n>0;n--){SudokuSet b=new SudokuSet();for(int m=count(i);m>0;m--)b.add(cell(i));boxes.add(b);}
   List<UserChain> chains=new ArrayList<UserChain>();for(int n=count(i);n>0;n--){UserChain c=new UserChain();c.setSourceId(i.readLong());c.setClosed(i.readBoolean());c.setActive(i.readBoolean());c.setNextStrong(i.readBoolean());c.setAnalysisResult(i.readUTF());for(int m=count(i);m>0;m--){int cell=cell(i),digit=i.readInt();if(digit<1||digit>9)throw new IOException("Invalid authored candidate");UserChainNode node=new UserChainNode(cell,digit,color(i));int size=count(i);if(size>3)throw new IOException("Invalid authored group");if(size>0){int[] group=new int[size];for(int x=0;x<size;x++)group[x]=cell(i);node.setGroupCells(group);}c.getNodes().add(node);}for(int m=count(i);m>0;m--){int strong=i.readUnsignedByte();if(strong>1)throw new IOException("Invalid relation");c.getStrongRelations().add(strong==2?null:strong==1);}for(int m=count(i);m>0;m--)c.getRelationColors().add(color(i));chains.add(c);}
   Set<String> invalid=new HashSet<String>();for(int n=count(i);n>0;n--)invalid.add(i.readUTF());if(i.read()!=-1)throw new IOException("Trailing authored data");return new ReplayEvidence(source,status,problem,edge,proof,boxes,chains,invalid);
  }catch(IllegalArgumentException e){throw new IOException("Invalid authored evidence",e);}
 }
 private static int count(DataInputStream i)throws IOException{int n=i.readInt();if(n<0||n>MAX)throw new IOException("Invalid authored collection size");return n;}
 private static int cell(DataInputStream i)throws IOException{int n=i.readInt();if(n<0||n>80)throw new IOException("Invalid authored cell");return n;}
 private static void color(DataOutputStream o,Color c)throws IOException{o.writeBoolean(c!=null);if(c!=null)o.writeInt(c.getRGB());}
 private static Color color(DataInputStream i)throws IOException{return i.readBoolean()?new Color(i.readInt(),true):null;}
}
