/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
package sudoku;

import java.awt.Color;
import java.io.*;
import java.util.*;

/** Data-only annotation snapshot. Windows overlay text is preserved even for unknown tags. */
final class ReplayAnnotations {
    static final ReplayAnnotations EMPTY=new ReplayAnnotations("",new byte[0],Collections.<Integer,Color>emptyMap(),Collections.<Integer,Color>emptyMap(),Collections.<DoodleStroke>emptyList());
    final String overlay;
    private final byte[] geometry;
    private final Map<Integer,Color> cells,candidates;
    private final List<DoodleStroke> ink;
    final int outlineVisibleGroup;
    final boolean nativeData;
    static final int[] PALETTE={0xffffc059,0xfff7de8f,0xffb1a5f3,0xffdcd4fc,0xfff7a5a7,0xffffd2d2,0xff86e8d0,0xffcefbed,0xff86f280,0xffd7ffd7};
    ReplayAnnotations(String overlay,byte[] geometry,Map<Integer,Color> cells,Map<Integer,Color> candidates,List<DoodleStroke> ink){this(overlay,geometry,cells,candidates,ink,-1,true);}
    ReplayAnnotations(String overlay,byte[] geometry,Map<Integer,Color> cells,Map<Integer,Color> candidates,List<DoodleStroke> ink,int outlineVisibleGroup){this(overlay,geometry,cells,candidates,ink,outlineVisibleGroup,true);}
    private ReplayAnnotations(String overlay,byte[] geometry,Map<Integer,Color> cells,Map<Integer,Color> candidates,List<DoodleStroke> ink,int outlineVisibleGroup,boolean nativeData){
        if(outlineVisibleGroup < -1 || outlineVisibleGroup > 5)throw new IllegalArgumentException("Invalid visible thought group");
        this.overlay=overlay;this.geometry=geometry.clone();this.cells=new TreeMap<Integer,Color>(cells);this.candidates=new TreeMap<Integer,Color>(candidates);this.ink=new ArrayList<DoodleStroke>();for(DoodleStroke s:ink)this.ink.add(s.copy());this.outlineVisibleGroup=outlineVisibleGroup;this.nativeData=nativeData;
    }
    ReplayAnnotations withProof(byte[] bytes) {
        try{
            SolutionStep proof=ReplayEvidence.proof(bytes);if(proof==null)return this;
            StringBuilder text=new StringBuilder(overlay);
            text.append("CD ").append(proof.toString(2).replace('\n',' ').replace('\r',' ')).append('\n');
            Map<Integer,Set<Integer>> red=new TreeMap<Integer,Set<Integer>>(),green=new TreeMap<Integer,Set<Integer>>();
            for(Candidate c:proof.getCandidatesToDelete())addMark(red,c.getIndex(),c.getValue());
            for(int cell:proof.getIndices())for(int digit:proof.getValues())addMark(green,cell,digit);
            appendMarks(text,"R",red);appendMarks(text,"G",green);
            return new ReplayAnnotations(text.toString(),geometry,cells,candidates,ink,outlineVisibleGroup,nativeData);
        }catch(IOException ex){throw new IllegalArgumentException(ex);}
    }
    private static void addMark(Map<Integer,Set<Integer>> map,int cell,int digit){if(!map.containsKey(cell))map.put(cell,new TreeSet<Integer>());map.get(cell).add(digit);}
    private static void appendMarks(StringBuilder text,String tag,Map<Integer,Set<Integer>> map){for(Map.Entry<Integer,Set<Integer>> e:map.entrySet()){text.append(tag).append(' ').append(e.getKey());for(int digit:e.getValue())text.append(' ').append(digit);text.append('\n');}}
    byte[] geometry(){return geometry.clone();}
    Map<Integer,Color> cells(){return new TreeMap<Integer,Color>(cells);}
    Map<Integer,Color> candidates(){return new TreeMap<Integer,Color>(candidates);}
    List<DoodleStroke> ink(){List<DoodleStroke> result=new ArrayList<DoodleStroke>();for(DoodleStroke s:ink)result.add(s.copy());return result;}
    String notice(){return !nativeData&&(overlay.startsWith("P ")||overlay.startsWith("O ")||overlay.contains("\nP ")||overlay.contains("\nO "))?ReplayText.text("foreignInkHidden"):"";}
    String description(){StringBuilder s=new StringBuilder();for(String line:overlay.split("\n"))if(line.startsWith("CD "))s.append(line.substring(3)).append('\n');return s.toString();}
    static int nearest(Color c){int best=0;long distance=Long.MAX_VALUE;for(int i=0;i<PALETTE.length;i++){Color p=new Color(PALETTE[i],true);long d=(long)(c.getRed()-p.getRed())*(c.getRed()-p.getRed())+(long)(c.getGreen()-p.getGreen())*(c.getGreen()-p.getGreen())+(long)(c.getBlue()-p.getBlue())*(c.getBlue()-p.getBlue());if(d<distance){best=i;distance=d;}}return best;}
    static Color palette(int index)throws IOException{if(index<0||index>=PALETTE.length)throw new IOException("Invalid overlay palette index");return new Color(PALETTE[index],true);}
    static int integer(String value)throws IOException{try{return Integer.parseInt(value);}catch(NumberFormatException ex){throw new IOException("Invalid overlay integer",ex);}}
    static int cell(int n)throws IOException{if(n<0||n>=81)throw new IOException("Invalid overlay cell");return n;}
    static int atom(int n)throws IOException{cell(n/10);if(n<0||n%10<1||n%10>9)throw new IOException("Invalid overlay candidate");return n;}
    private static UserChainNode node(String[] t,int start)throws IOException{if(t.length-start<1||t.length-start>729)throw new IOException("Invalid overlay group");int[] atoms=new int[t.length-start];for(int i=0;i<atoms.length;i++)atoms[i]=atom(integer(t[i+start]));UserChainNode n=new UserChainNode(atoms[0]/10,atoms[0]%10,new Color(0xff4667bd,true));if(atoms.length>1)n.setMemberCandidates(atoms);if(!n.validShape())throw new IOException("Invalid overlay group shape");return n;}
    private static boolean strength(String s)throws IOException{if(!s.equals("0")&&!s.equals("1"))throw new IOException("Invalid link strength");return s.equals("1");}
    private static void finish(UserChain c,List<UserChain> chains)throws IOException{if(c==null||c.getNodes().isEmpty())return;int n=c.getNodes().size();if(n>2&&Arrays.equals(c.getNodes().get(0).atoms(),c.getNodes().get(n-1).atoms())){c.getNodes().remove(n-1);n--;}if(c.getStrongRelations().size()>n)throw new IOException("Too many links");c.setClosed(n>2&&c.getStrongRelations().size()==n);chains.add(c);}
    static ReplayAnnotations fromWindows(String text)throws IOException{
        if(text.length()>65535)throw new IOException("Overlay too large");
        List<SudokuSet> boxes=new ArrayList<SudokuSet>();for(int i=0;i<6;i++)boxes.add(new SudokuSet());
        List<UserChain> chains=new ArrayList<UserChain>();UserChain current=new UserChain(),completed=null;List<UserChainNode> fallback=new ArrayList<UserChainNode>();
        Map<Integer,Color> cells=new TreeMap<Integer,Color>(),candidates=new TreeMap<Integer,Color>();
        int objects=0;
        for(String line:text.split("\n")){
            if(line.trim().isEmpty())continue;if(++objects>4096)throw new IOException("Too many overlay objects");String[] t=line.trim().split("\\s+");
            try{switch(t[0]){
            case "B": if(t.length<6)throw new IOException("Invalid box");for(int i=1;i<5;i++)integer(t[i]);int color=integer(t[5]);palette(color);for(int i=6;i<t.length;i++)boxes.get(color/2).add(cell(integer(t[i])));break;
            case "K": cells.put(cell(integer(t[1])),palette(integer(t[2])));break;
            case "M": candidates.put(atom(integer(t[1])),palette(integer(t[2])));break;
            case "G":case "R":case "C":int at=cell(integer(t[1]));for(int i=2;i<t.length;i++){int a=atom(at*10+integer(t[i]));candidates.put(a,new Color(t[0].equals("G")?0xff44a35a:t[0].equals("R")?0xffdf6666:0xff44bfc4,true));}break;
            case "CG":current.getNodes().add(node(t,1));break;
            case "CS":current.getStrongRelations().add(strength(t[1]));break;
            case "CH":if(integer(t[1])<0||integer(t[1])>9)throw new IOException("Invalid chain digit");break;
            case "UC":if(completed!=null){if(completed.getNodes().isEmpty())completed.getNodes().addAll(fallback);finish(completed,chains);}completed=new UserChain();fallback.clear();integer(t[1]);strength(t[2]);break;
            case "UN":fallback.clear();for(int i=1;i<t.length;i++)fallback.add(node(new String[]{t[i]},0));break;
            case "UG":if(completed==null)throw new IOException("Group outside chain");completed.getNodes().add(node(t,1));break;
            case "UL":if(completed==null)throw new IOException("Links outside chain");for(int i=1;i<t.length;i++)completed.getStrongRelations().add(strength(t[i]));break;
            case "CL":int p=2;integer(t[1]);for(int set=0;set<2;set++){int count=integer(t[p++]);if(count<0||count>729||p+count>t.length)throw new IOException("Invalid loop result");while(count-->0)candidates.put(atom(integer(t[p++])),new Color(set==0?0xffffa347:0xffdf6666,true));}break;
            case "P":integer(t[1]);for(int i=2;i<t.length;i++){String[] xy=t[i].split(":");if(xy.length!=2)throw new IOException("Invalid ink point");integer(xy[0]);integer(xy[1]);}break;
            case "O":if(t.length!=6)throw new IOException("Invalid circle");for(int i=1;i<6;i++)integer(t[i]);break;
            default:break; // Foreign extensions are data, not commands.
            }}catch(IndexOutOfBoundsException|IllegalArgumentException ex){throw new IOException("Malformed overlay: "+t[0],ex);}
        }
        finish(current,chains);if(completed!=null){if(completed.getNodes().isEmpty())completed.getNodes().addAll(fallback);finish(completed,chains);}
        byte[] geometry=ReplayEvidence.input("FREE_CHAIN",boxes,chains);
        return new ReplayAnnotations(text,geometry,cells,candidates,Collections.<DoodleStroke>emptyList(),-1,false);
    }
    void write(DataOutputStream out)throws IOException{
        out.writeBoolean(nativeData);writeBytes(out,geometry);writeColors(out,cells);writeColors(out,candidates);out.writeInt(outlineVisibleGroup);out.writeInt(ink.size());
        for(DoodleStroke s:ink){out.writeInt(s.getColor().getRGB());out.writeFloat(s.getWidthFactor());out.writeInt(s.getAnchorCell());out.writeInt(s.getAnchorDigit());out.writeByte(s.getCandidateMarkKind());out.writeInt(s.getThoughtGroup());out.writeBoolean(s.isConclusionOutlined());out.writeInt(s.getPoints().size());for(DoodlePoint p:s.getPoints()){out.writeDouble(p.getX());out.writeDouble(p.getY());}}
    }
    static ReplayAnnotations read(DataInputStream in,String overlay,int extensionVersion)throws IOException{
        boolean nativeData=in.readBoolean();byte[] geometry=readBytes(in);if(geometry.length>0){ReplayEvidence e=ReplayEvidence.decode(geometry);if(!e.status.equals("PENDING")||e.proofBytes().length!=0||e.boxes().size()>6)throw new IOException("Invalid annotation geometry");
            for(UserChain c:e.chains()){int nodes=c.getNodes().size();if(c.getStrongRelations().size()>(c.isClosed()?nodes:Math.max(0,nodes-1)))throw new IOException("Invalid annotation link count");}}
        Map<Integer,Color> cells=readColors(in,false),candidates=readColors(in,true);
        int outlineVisibleGroup=extensionVersion>=3?in.readInt():-1;
        if(outlineVisibleGroup < -1 || outlineVisibleGroup > 5)throw new IOException("Invalid visible thought group");
        List<DoodleStroke> ink=new ArrayList<DoodleStroke>();int count=count(in,4096);
        for(int i=0;i<count;i++){
            DoodleStroke s=new DoodleStroke(new Color(in.readInt(),true),in.readFloat());s.setAnchorCell(in.readInt());s.setAnchorDigit(in.readInt());
            if(extensionVersion>=2){s.setCandidateMarkKind(in.readUnsignedByte());s.setThoughtGroup(in.readInt());}
            if(extensionVersion>=3)s.setConclusionOutlined(in.readBoolean());
            if(!Float.isFinite(s.getWidthFactor())||s.getWidthFactor()<=0||s.getWidthFactor()>10||(s.getAnchorCell()!=-1&&!s.isCandidateAnchored()))throw new IOException("Invalid ink geometry");
            if(s.getCandidateMarkKind()!=DoodleStroke.MARK_NONE&&!s.isCandidateAnchored())throw new IOException("Unanchored candidate mark");
            int points=count(in,100000);if(s.getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS&&points!=4)throw new IOException("Invalid cross geometry");
            for(int j=0;j<points;j++){double x=in.readDouble(),y=in.readDouble();if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>100000||Math.abs(y)>100000)throw new IOException("Invalid ink coordinate");s.getPoints().add(new DoodlePoint(x,y));}
            ink.add(s);
        }
        return new ReplayAnnotations(overlay,geometry,cells,candidates,ink,outlineVisibleGroup,nativeData);
    }
    static void writeBytes(DataOutputStream out,byte[] b)throws IOException{if(b.length>4*1024*1024)throw new IOException("Replay extension item too large");out.writeInt(b.length);out.write(b);}
    static byte[] readBytes(DataInputStream in)throws IOException{byte[] b=new byte[count(in,4*1024*1024)];in.readFully(b);return b;}
    static int count(DataInputStream in,int limit)throws IOException{int n=in.readInt();if(n<0||n>limit)throw new IOException("Invalid extension size");return n;}
    private static void writeColors(DataOutputStream out,Map<Integer,Color> map)throws IOException{out.writeInt(map.size());for(Map.Entry<Integer,Color> e:map.entrySet()){out.writeInt(e.getKey());out.writeInt(e.getValue().getRGB());}}
    private static Map<Integer,Color> readColors(DataInputStream in,boolean candidate)throws IOException{Map<Integer,Color> map=new TreeMap<Integer,Color>();for(int n=count(in,729);n>0;n--){int key=in.readInt();if(candidate)atom(key);else cell(key);map.put(key,new Color(in.readInt(),true));}return map;}
}
