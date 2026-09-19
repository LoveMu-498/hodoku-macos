package sudoku;

import java.io.*;
import java.util.*;
import solver.RestrictedCommon;

/** Versioned data-only proof snapshot. No native mutable object escapes its byte boundary. */
public final class ReplayProof {
    private static final int MAGIC=0x48525046, VERSION=1, LIMIT=16384;
    private ReplayProof() {}
    public static byte[] encode(SolutionStep s) {
        try { ByteArrayOutputStream b=new ByteArrayOutputStream(); DataOutputStream o=new DataOutputStream(b);
            o.writeInt(MAGIC);o.writeInt(VERSION);
            o.writeUTF(s.getType().name());o.writeUTF(s.getSubType()==null?"":s.getSubType().name());
            o.writeInt(s.getEntity());
            o.writeInt(s.getEntityNumber());
            o.writeInt(s.getEntity2());
            o.writeInt(s.getEntity2Number());
            o.writeInt(s.getProgressScore());
            o.writeInt(s.getProgressScoreSingles());
            o.writeInt(s.getProgressScoreSinglesOnly());
            o.writeBoolean(s.isIsSiamese());o.writeBoolean(s.isAuthoredPlacement());
            ints(o,s.getValues());
            ints(o,s.getIndices());
            candidates(o,s.getCandidatesToDelete());
            candidates(o,s.getCannibalistic());
            candidates(o,s.getFins());
            candidates(o,s.getEndoFins());
            entities(o,s.getBaseEntities());
            entities(o,s.getCoverEntities());
            o.writeInt(s.getChains().size());for(Chain c:s.getChains()){o.writeInt(c.getStart());o.writeInt(c.getEnd());int[] a=c.getChain();o.writeInt(a.length);for(int v:a)o.writeInt(v);}
            o.writeInt(s.getAlses().size());for(AlsInSolutionStep a:s.getAlses()){ints(o,a.getIndices());ints(o,a.getCandidates());o.writeInt(a.getChainPenalty());}
            o.writeInt(s.getColorCandidates().size());for(Map.Entry<Integer,Integer> e:s.getColorCandidates().entrySet()){o.writeInt(e.getKey());o.writeInt(e.getValue());}
            o.writeInt(s.getRestrictedCommons().size());for(RestrictedCommon r:s.getRestrictedCommons()){o.writeInt(r.getAls1());o.writeInt(r.getAls2());o.writeInt(r.getCand1());o.writeInt(r.getCand2());o.writeInt(r.getActualRC());}
            set(o,s.getPotentialCannibalisticEliminations());
            set(o,s.getPotentialEliminations());
            o.flush();byte[] result=b.toByteArray();decode(result);return result;
        }catch(IOException e){throw new IllegalArgumentException("Cannot snapshot proof",e);}
    }
    public static SolutionStep decode(byte[] data)throws IOException {
        if(data.length==0)return null;if(data.length>4*1024*1024)throw new IOException("Proof too large");
        try(DataInputStream i=new DataInputStream(new ByteArrayInputStream(data))){
            if(i.readInt()!=MAGIC||i.readInt()!=VERSION)throw new IOException("Unknown proof format");
            SolutionStep s=new SolutionStep(type(i.readUTF()));String sub=i.readUTF();if(!sub.isEmpty())s.setSubType(type(sub));
            s.setEntity(i.readInt());
            s.setEntityNumber(i.readInt());
            s.setEntity2(i.readInt());
            s.setEntity2Number(i.readInt());
            s.setProgressScore(i.readInt());
            s.setProgressScoreSingles(i.readInt());
            s.setProgressScoreSinglesOnly(i.readInt());
            s.setIsSiamese(i.readBoolean());s.setAuthoredPlacement(i.readBoolean());
            s.setValues(ints(i,1,9));
            s.setIndices(ints(i,0,80));
            s.setCandidatesToDelete(candidates(i));
            s.setCannibalistic(candidates(i));
            s.setFins(candidates(i));
            s.setEndoFins(candidates(i));
            s.setBaseEntities(entities(i));
            s.setCoverEntities(entities(i));
            for(int n=count(i);n>0;n--){int start=i.readInt(),end=i.readInt();int[] a=new int[count(i)];for(int x=0;x<a.length;x++)a[x]=i.readInt();if(start<0||end<start||end>=a.length)throw new IOException("Invalid proof chain range");s.getChains().add(new Chain(start,end,a));}
            for(int n=count(i);n>0;n--){AlsInSolutionStep a=new AlsInSolutionStep();a.setIndices(ints(i,0,80));a.setCandidates(ints(i,1,9));a.setChainPenalty(i.readInt());s.getAlses().add(a);}
            for(int n=count(i);n>0;n--){int cell=i.readInt(),color=i.readInt();if(cell<0||cell>80||color<0||color>=Options.getInstance().getColoringColors().length)throw new IOException("Invalid proof color");s.getColorCandidates().put(cell,color);}
            for(int n=count(i);n>0;n--){int a=i.readInt(),b=i.readInt(),c=i.readInt(),d=i.readInt(),rc=i.readInt();if(a<0||b<0||a>=s.getAlses().size()||b>=s.getAlses().size()||c<1||c>9||d<0||d>9)throw new IOException("Invalid restricted common");s.getRestrictedCommons().add(new RestrictedCommon(a,b,c,d,rc));}
            s.setPotentialCannibalisticEliminations(set(i));
            s.setPotentialEliminations(set(i));
            for(Chain c:s.getChains())for(int x=c.getStart();x<=c.getEnd();x++){
                int e=c.getChain()[x];if(e==Integer.MIN_VALUE)continue;int node=Math.abs(e);
                int kind=Chain.getSNodeType(node),digit=Chain.getSCandidate(node);
                if(kind<0||kind>Chain.ALS_NODE||digit<1||digit>9||Chain.getSCellIndex(node)>80)throw new IOException("Invalid proof node");
                if(kind==Chain.GROUP_NODE&&((Chain.getSCellIndex2(node)<0||Chain.getSCellIndex2(node)>80)||(Chain.getSCellIndex3(node)>80&&Chain.getSCellIndex3(node)!=-1)))throw new IOException("Invalid grouped node");
                if(Chain.getSNodeType(node)==Chain.ALS_NODE && (Chain.getSAlsIndex(node)<0||Chain.getSAlsIndex(node)>=s.getAlses().size()))throw new IOException("Invalid ALS reference");
            }
            if(i.read()!=-1)throw new IOException("Trailing proof data");return s;
        }catch(IllegalArgumentException|IndexOutOfBoundsException e){throw new IOException("Invalid proof",e);}
    }
    private static SolutionType type(String s)throws IOException {try{return SolutionType.valueOf(s);}catch(IllegalArgumentException e){throw new IOException("Unknown proof type",e);}}
    private static int count(DataInputStream i)throws IOException{int n=i.readInt();if(n<0||n>LIMIT)throw new IOException("Invalid proof collection size");return n;}
    private static void ints(DataOutputStream o,List<Integer> a)throws IOException{o.writeInt(a.size());for(int v:a)o.writeInt(v);}
    private static List<Integer> ints(DataInputStream i,int lo,int hi)throws IOException{List<Integer>a=new ArrayList<Integer>();for(int n=count(i);n>0;n--){int v=i.readInt();if(v<lo||v>hi)throw new IOException("Invalid proof value");a.add(v);}return a;}
    private static void candidates(DataOutputStream o,List<Candidate>a)throws IOException{o.writeInt(a.size());for(Candidate c:a){o.writeInt(c.getIndex());o.writeInt(c.getValue());}}
    private static List<Candidate> candidates(DataInputStream i)throws IOException{List<Candidate>a=new ArrayList<Candidate>();for(int n=count(i);n>0;n--){int cell=i.readInt(),digit=i.readInt();if(cell<0||cell>80||digit<1||digit>9)throw new IOException("Invalid proof candidate");a.add(new Candidate(cell,digit));}return a;}
    private static void entities(DataOutputStream o,List<Entity>a)throws IOException{o.writeInt(a.size());for(Entity e:a){o.writeInt(e.getEntityName());o.writeInt(e.getEntityNumber());}}
    private static List<Entity> entities(DataInputStream i)throws IOException{List<Entity>a=new ArrayList<Entity>();for(int n=count(i);n>0;n--){int name=i.readInt(),number=i.readInt();if(name<0||name>3||number<0||number>9)throw new IOException("Invalid proof entity");a.add(new Entity(name,number));}return a;}
    private static void set(DataOutputStream o,SudokuSet s)throws IOException{o.writeInt(s.size());for(int x=0;x<s.size();x++)o.writeInt(s.get(x));}
    private static SudokuSet set(DataInputStream i)throws IOException{SudokuSet s=new SudokuSet();for(int v:ints(i,0,80))s.add(v);return s;}
}
