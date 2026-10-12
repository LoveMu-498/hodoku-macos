/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.io.*;
import java.util.*;

/** Data-only annotation snapshot. Windows overlay text is preserved even for unknown tags. */
final class ReplayAnnotations {
    static final ReplayAnnotations EMPTY=new ReplayAnnotations("",new byte[0],Collections.<Integer,Color>emptyMap(),Collections.<Integer,Color>emptyMap(),Collections.<DoodleStroke>emptyList());
    final String overlay;
    private final String editableOverlay;
    private final byte[] geometry;
    private final Map<Integer,Color> cells,candidates;
    private final List<DoodleStroke> ink;
    final int outlineVisibleGroup;
    final DoodleReplayStep doodleStep;
    final boolean nativeData;
    static final int[] PALETTE={0xffffc059,0xfff7de8f,0xffb1a5f3,0xffdcd4fc,0xfff7a5a7,0xffffd2d2,0xff86e8d0,0xffcefbed,0xff86f280,0xffd7ffd7};
    ReplayAnnotations(String overlay,byte[] geometry,Map<Integer,Color> cells,Map<Integer,Color> candidates,List<DoodleStroke> ink){this(overlay,geometry,cells,candidates,ink,-1,true);}
    ReplayAnnotations(String overlay,byte[] geometry,Map<Integer,Color> cells,Map<Integer,Color> candidates,List<DoodleStroke> ink,int outlineVisibleGroup){this(overlay,geometry,cells,candidates,ink,outlineVisibleGroup,true);}
    private ReplayAnnotations(String overlay,byte[] geometry,Map<Integer,Color> cells,Map<Integer,Color> candidates,List<DoodleStroke> ink,int outlineVisibleGroup,boolean nativeData){
        this(overlay,geometry,cells,candidates,ink,outlineVisibleGroup,nativeData,null);
    }
    private ReplayAnnotations(String overlay,byte[] geometry,Map<Integer,Color> cells,Map<Integer,Color> candidates,List<DoodleStroke> ink,int outlineVisibleGroup,boolean nativeData,DoodleReplayStep doodleStep){
        this(overlay,geometry,cells,candidates,ink,outlineVisibleGroup,nativeData,doodleStep,overlay);
    }
    private ReplayAnnotations(String overlay,byte[] geometry,Map<Integer,Color> cells,Map<Integer,Color> candidates,List<DoodleStroke> ink,int outlineVisibleGroup,boolean nativeData,DoodleReplayStep doodleStep,String editableOverlay){
        this.editableOverlay=editableOverlay;
        this.doodleStep=doodleStep;
        if(outlineVisibleGroup < -1 || outlineVisibleGroup > 5)throw new IllegalArgumentException("Invalid visible thought group");
        this.overlay=overlay;this.geometry=geometry.clone();this.cells=new TreeMap<Integer,Color>(cells);this.candidates=new TreeMap<Integer,Color>(candidates);this.ink=new ArrayList<DoodleStroke>();for(DoodleStroke s:ink)this.ink.add(s.copy());this.outlineVisibleGroup=outlineVisibleGroup;this.nativeData=nativeData;
    }
    ReplayAnnotations withProof(byte[] bytes) {
        return withProof(bytes,null);
    }
    ReplayAnnotations withProof(byte[] bytes,Sudoku2 before) {
        try{
            SolutionStep proof=ReplayEvidence.proof(bytes);if(proof==null)return this;
            StringBuilder text=new StringBuilder(overlay);
            text.append("CD ").append(proof.toString(2).replace('\n',' ').replace('\r',' ')).append('\n');
            Map<Integer,Set<Integer>> red=new TreeMap<Integer,Set<Integer>>(),green=new TreeMap<Integer,Set<Integer>>(),fins=new TreeMap<Integer,Set<Integer>>();
            for(Candidate c:proof.getCandidatesToDelete())addMark(red,c.getIndex(),c.getValue());
            for(int cell:proof.getIndices())for(int digit:proof.getValues())
                if(before==null||before.isCandidate(cell,digit))addMark(green,cell,digit);
            for(Candidate c:proof.getFins())appendFin(text,fins,red,c,false);
            for(Candidate c:proof.getEndoFins())appendFin(text,fins,red,c,true);
            appendMarks(text,"G",green);appendMarks(text,"C",fins);appendMarks(text,"R",red);
            return new ReplayAnnotations(text.toString(),geometry,cells,candidates,ink,outlineVisibleGroup,nativeData,doodleStep,editableOverlay);
        }catch(IOException ex){throw new IllegalArgumentException(ex);}
    }
    ReplayAnnotations withDoodleStep(DoodleReplayStep step,String viewingOverlay){
        String text=viewingOverlay+"DT "+step.operationCode()+" "+(step.applied?"APPLIED":"EVIDENCE")+"\nCD "+step.description().replace('\n',' ').replace('\r',' ')+"\n";
        return new ReplayAnnotations(text,geometry,cells,candidates,ink,outlineVisibleGroup,nativeData,step,editableOverlay);
    }
    /** Viewing copies never become the editable result state's annotations. */
    ReplayAnnotations forEditing(){
        if(!nativeData){
            // Foreign marks have no trustworthy authored/result distinction.
            // Keep supported source geometry and explicit user palette coloring;
            // historical glyphs/results must never arm the next Enter transaction.
            StringBuilder source=new StringBuilder();
            for(String line:overlay.split("\n")){
                String tag=line.trim().split("\\s+")[0];
                if(Arrays.asList("O","P","DM1","MT","DT","CD","PF1","G","R","C","CL").contains(tag))continue;
                if(!line.isEmpty())source.append(line).append('\n');
            }
            try{return fromWindows(source.toString());}catch(IOException invalid){throw new IllegalArgumentException("Invalid editable foreign overlay",invalid);}
        }
        String editable=editableOverlay;
        if(nativeData){
            // Earlier tails did not separate public proof text from editable
            // source text. Native capture uses K/M for user coloring, not G/R/C.
            StringBuilder source=new StringBuilder();
            for(String line:editable.split("\n"))
                if(!line.startsWith("CD ")&&!line.startsWith("DT ")&&!line.startsWith("PF1 ")&&!line.startsWith("G ")&&!line.startsWith("R ")&&!line.startsWith("C "))
                    if(!line.isEmpty())source.append(line).append('\n');
            editable=source.toString();
        }
        List<DoodleStroke> authored=ink();
        boolean coverage=isCoverageEvidence();
        if(coverage)for(DoodleStroke mark:authored)if(isAutomaticSelection(mark)){mark.setConclusionOutlined(false);mark.setConclusionSourceMask(0);}
        return doodleStep==null&&overlay.equals(editable)&&!coverage?this:new ReplayAnnotations(editable,geometry,cells,candidates,authored,outlineVisibleGroup,nativeData,null);
    }
    private boolean isCoverageEvidence(){
        if(doodleStep!=null)return doodleStep.operation==DoodleReplayStep.Operation.CELL_FORCING;
        for(String line:overlay.split("\n"))if(line.startsWith("DT CELL_FORCING "))return true;
        return false;
    }
    boolean isAutomaticSelection(DoodleStroke mark){
        return doodleStep!=null?doodleStep.isAutomaticSelection(mark)
                :isCoverageEvidence()&&mark.isStandardCandidateMark()&&mark.isConclusionOutlined();
    }
    /** Shared proof marks, distinct from user palette marks (M). */
    Set<Integer> proofCandidateAtoms()throws IOException{
        Set<Integer> result=new TreeSet<Integer>();
        for(String line:overlay.split("\n")){
            String[] fields=line.trim().split("\\s+");
            if(fields[0].equals("PF1")){result.add(finAtom(fields));continue;}
            if(fields.length<3||(!fields[0].equals("G")&&!fields[0].equals("R")&&!fields[0].equals("C")))continue;
            int cell=cell(integer(fields[1]));
            for(int i=2;i<fields.length;i++)result.add(atom(cell*10+integer(fields[i])));
        }
        return result;
    }
    Set<Integer> proofFinAtoms(boolean endo)throws IOException{
        Set<Integer> result=new TreeSet<Integer>();
        for(String line:overlay.split("\n"))if(line.startsWith("PF1 ")){
            String[] fields=line.trim().split("\\s+");int atom=finAtom(fields);
            if(fields[2].equals(endo?"ENDO_FIN":"FIN"))result.add(atom);
        }
        return result;
    }
    private static int finAtom(String[] fields)throws IOException{
        if(fields.length!=3||(!fields[2].equals("FIN")&&!fields[2].equals("ENDO_FIN")))throw new IOException("Invalid proof fin role");
        return atom(integer(fields[1]));
    }
    private static void appendFin(StringBuilder text,Map<Integer,Set<Integer>> fins,Map<Integer,Set<Integer>> deleted,Candidate fin,boolean endo){
        if(deleted.containsKey(fin.getIndex())&&deleted.get(fin.getIndex()).contains(fin.getValue()))return;
        addMark(fins,fin.getIndex(),fin.getValue());
        text.append("PF1 ").append(fin.getIndex()*10+fin.getValue()).append(endo?" ENDO_FIN\n":" FIN\n");
    }
    ReplayAnnotations withViewingInk(List<DoodleStroke> display,int group){
        return new ReplayAnnotations(overlay,geometry,cells,candidates,display,group,nativeData,doodleStep,editableOverlay);
    }
    ReplayAnnotations forWindowsV3()throws IOException{
        return new ReplayAnnotations(ReplayWindowsV3.overlay(this),geometry,cells,candidates,ink,outlineVisibleGroup,nativeData,doodleStep,editableOverlay);
    }
    private static void addMark(Map<Integer,Set<Integer>> map,int cell,int digit){if(!map.containsKey(cell))map.put(cell,new TreeSet<Integer>());map.get(cell).add(digit);}
    private static void appendMarks(StringBuilder text,String tag,Map<Integer,Set<Integer>> map){for(Map.Entry<Integer,Set<Integer>> e:map.entrySet()){text.append(tag).append(' ').append(e.getKey());for(int digit:e.getValue())text.append(' ').append(digit);text.append('\n');}}
    byte[] geometry(){return geometry.clone();}
    Map<Integer,Color> cells(){return new TreeMap<Integer,Color>(cells);}
    Map<Integer,Color> candidates(){return new TreeMap<Integer,Color>(candidates);}
    List<DoodleStroke> ink(){List<DoodleStroke> result=new ArrayList<DoodleStroke>();for(DoodleStroke s:ink)result.add(s.copy());return result;}
    String notice(){
        if(nativeData)return "";
        StringBuilder notice=new StringBuilder();
        if(hasUnanchoredInk(overlay))notice.append(ReplayText.text("foreignInkHidden"));
        if(ReplayWindowsV3.multiEnabled(overlay)){
            if(notice.length()>0)notice.append(' ');
            notice.append(ReplayText.text(ReplayWindowsV3.hasFrozenColors(overlay)?"foreignMultiRecorded":"foreignMultiMissing"));
        }
        return notice.toString();
    }
    private static boolean hasUnanchoredInk(String text){
        int pixels=0,anchored=0;boolean circle=false;
        for(String line:text.split("\n")){
            line=line.trim();
            if(line.startsWith("O ")){String[] fields=line.split("\\s+");if(fields.length==6||fields.length==10&&Integer.parseInt(fields[2])==0)circle=true;}
            if(line.startsWith("P "))pixels++;
            if(line.startsWith("DM1 ")){String[] fields=line.trim().split("\\s+");try{anchored+=Integer.parseInt(fields[12]);}catch(RuntimeException ignored){return true;}}
        }
        return circle||pixels>anchored;
    }
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
        if(text.length()>4*1024*1024||text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4*1024*1024)throw new IOException("Overlay exceeds 4194304 bytes");
        List<SudokuSet> boxes=new ArrayList<SudokuSet>();for(int i=0;i<6;i++)boxes.add(new SudokuSet());
        List<UserChain> chains=new ArrayList<UserChain>();UserChain current=new UserChain(),completed=null;List<UserChainNode> fallback=new ArrayList<UserChainNode>();
        Map<Integer,Color> cells=new TreeMap<Integer,Color>(),candidates=new TreeMap<Integer,Color>();
        int objects=0,visibleOutlineGroup=-1;List<DoodleStroke> structuredInk=new ArrayList<DoodleStroke>(),windowsInk=new ArrayList<DoodleStroke>();
        for(String line:text.split("\n")){
            if(line.trim().isEmpty())continue;if(++objects>4096)throw new IOException("Too many overlay objects");String[] t=line.trim().split("\\s+");
            try{switch(t[0]){
            case "PF1":finAtom(t);break;
            case "DM1":
                DoodleStroke mark=readStructuredMark(t);structuredInk.add(mark);
                int visible=integer(t[11]);if(visible < -1 || visible > 5)throw new IOException("Invalid structured ink group");
                if(visibleOutlineGroup!=-1&&visible!=-1&&visible!=visibleOutlineGroup)throw new IOException("Inconsistent structured ink group");
                if(visible!=-1)visibleOutlineGroup=visible;
                break;
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
            case "O":
                if(t.length==6){for(int i=1;i<6;i++)integer(t[i]);}
                else {DoodleStroke windowsMark=ReplayWindowsV3.readMark(t);if(windowsMark!=null)windowsInk.add(windowsMark);}
                break;
            case "MT":if(t.length!=2)throw new IOException("Invalid multi-group flag");strength(t[1]);break;
            default:break; // Foreign extensions are data, not commands.
            }}catch(IndexOutOfBoundsException|IllegalArgumentException ex){throw new IOException("Malformed overlay: "+t[0],ex);}
        }
        finish(current,chains);if(completed!=null){if(completed.getNodes().isEmpty())completed.getNodes().addAll(fallback);finish(completed,chains);}
        Set<String> known=new HashSet<String>();for(DoodleStroke mark:structuredInk)known.add(ReplayWindowsV3.key(mark));
        for(DoodleStroke mark:windowsInk)if(known.add(ReplayWindowsV3.key(mark)))structuredInk.add(mark);
        byte[] geometry=ReplayEvidence.input("FREE_CHAIN",boxes,chains);
        return new ReplayAnnotations(text,geometry,cells,candidates,structuredInk,visibleOutlineGroup,false);
    }
    /** Candidate-anchored standard glyph. Windows ignores this tag and keeps its P fallback. */
    static void appendStructuredMark(StringBuilder text,DoodleStroke s,int visibleGroup,int paths){
        double radius=0;for(int i=0;i<s.pointCount();i++)radius=Math.max(radius,Math.max(Math.abs(s.pointX(i)),Math.abs(s.pointY(i))));
        int flags=(s.isConclusionOutlined()?1:0)|(s.isHypothesisStart()?2:0)|(s.isContradictionWitness()?4:0)|(s.isRefutationResult()?8:0);
        text.append("DM1 ").append(s.getAnchorCell()).append(' ').append(s.getAnchorDigit()).append(' ').append(s.getCandidateMarkKind()).append(' ').append(s.getThoughtGroup()).append(' ').append(s.getColor().getRGB()).append(' ').append(s.getWidthFactor()).append(' ').append(radius).append(' ').append(flags).append(' ').append(s.getHypothesisConditionId()).append(' ').append(s.getHypothesisGroupKind()).append(' ').append(visibleGroup).append(' ').append(paths).append('\n');
    }
    static DoodleStroke readStructuredMark(String[] fields)throws IOException{
        if(fields.length!=13)throw new IOException("Invalid structured mark");
        int cell=cell(integer(fields[1])),digit=integer(fields[2]),kind=integer(fields[3]),group=integer(fields[4]),rgb=integer(fields[5]);atom(cell*10+digit);
        float width;double radius;try{width=Float.parseFloat(fields[6]);radius=Double.parseDouble(fields[7]);}catch(NumberFormatException ex){throw new IOException("Invalid mark dimensions",ex);}
        int flags=integer(fields[8]),condition=integer(fields[9]),groupKind=integer(fields[10]),paths=integer(fields[12]);
        if(kind<1||kind>2||group < -1||group>5||!Float.isFinite(width)||width<=0||width>10||!Double.isFinite(radius)||radius<=0||radius>100||flags<0||flags>15||condition<0||groupKind<0||groupKind>2||paths<0||paths>3)throw new IOException("Invalid structured mark metadata");
        DoodleStroke s=new DoodleStroke(new Color(rgb,true),width);s.setAnchorCell(cell);s.setAnchorDigit(digit);s.setCandidateMarkKind(kind);s.setThoughtGroup(group);s.setConclusionOutlined((flags&1)!=0);s.setHypothesisStart((flags&2)!=0);s.setContradictionWitness((flags&4)!=0);s.setRefutationResult((flags&8)!=0);s.setHypothesisConditionId(condition);s.setHypothesisGroupKind(groupKind);
        if((s.isHypothesisStart()||s.isRefutationResult()||s.isContradictionWitness())&&group<0||s.isRefutationResult()&&(!s.isConclusionOutlined()||s.isHypothesisStart()))throw new IOException("Invalid structured mark roles");
        if(kind==DoodleStroke.MARK_FALSE_CROSS){s.addPoint(-radius,-radius);s.addPoint(radius,radius);s.addPoint(radius,-radius);s.addPoint(-radius,radius);}
        else for(int i=0;i<=48;i++){double angle=2*Math.PI*i/48;s.addPoint(radius*Math.cos(angle),radius*Math.sin(angle));}
        return s;
    }
    void write(DataOutputStream out)throws IOException{
        write(out, 5);
    }
    void write(DataOutputStream out,int extensionVersion)throws IOException{
        // The existing replay format records effective annotations, not hidden
        // derivation snapshots. Session XML retains those snapshots separately.
        int visibleCount=0;for(DoodleStroke s:ink)if(!s.isProofOnly())visibleCount++;
        out.writeBoolean(nativeData);writeBytes(out,geometry);writeColors(out,cells);writeColors(out,candidates);out.writeInt(outlineVisibleGroup);out.writeInt(visibleCount);
        for(DoodleStroke s:ink){if(s.isProofOnly())continue;out.writeInt(s.getColor().getRGB());out.writeFloat(s.getWidthFactor());out.writeInt(s.getAnchorCell());out.writeInt(s.getAnchorDigit());out.writeByte(s.getCandidateMarkKind());out.writeInt(s.getThoughtGroup());out.writeBoolean(s.isConclusionOutlined());
            if(extensionVersion>=4){out.writeBoolean(s.isHypothesisStart());out.writeInt(s.getHypothesisConditionId());out.writeByte(s.getHypothesisGroupKind());out.writeBoolean(s.isContradictionWitness());out.writeBoolean(s.isRefutationResult());}
            out.writeInt(s.pointCount());for(int i=0;i<s.pointCount();i++){out.writeDouble(s.pointX(i));out.writeDouble(s.pointY(i));}}
        if(extensionVersion>=5){writeBytes(out,doodleStep==null?new byte[0]:doodleStep.encode());if(extensionVersion>=6)ReplayTextCodec.write(out,editableOverlay);else out.writeUTF(editableOverlay);}
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
            if(extensionVersion>=4){
                s.setHypothesisStart(in.readBoolean());int condition=in.readInt(),kind=in.readUnsignedByte();
                if(condition<0||kind>2)throw new IOException("Invalid hypothesis metadata");
                s.setHypothesisConditionId(condition);s.setHypothesisGroupKind(kind);
                s.setContradictionWitness(in.readBoolean());s.setRefutationResult(in.readBoolean());
                if((s.isHypothesisStart()||s.isContradictionWitness()||s.isRefutationResult())
                        &&(!s.isStandardCandidateMark()||s.getThoughtGroup()<0))throw new IOException("Unanchored hypothesis display");
                if(s.isRefutationResult()&&(s.isHypothesisStart()||!s.isConclusionOutlined()))throw new IOException("Invalid reverse conclusion display");
            }
            if(!Float.isFinite(s.getWidthFactor())||s.getWidthFactor()<=0||s.getWidthFactor()>10||(s.getAnchorCell()!=-1&&!s.isCandidateAnchored()))throw new IOException("Invalid ink geometry");
            if(s.getCandidateMarkKind()!=DoodleStroke.MARK_NONE&&!s.isCandidateAnchored())throw new IOException("Unanchored candidate mark");
            int points=count(in,100000);if(s.getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS&&points!=4)throw new IOException("Invalid cross geometry");
            for(int j=0;j<points;j++){double x=in.readDouble(),y=in.readDouble();if(!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>100000||Math.abs(y)>100000)throw new IOException("Invalid ink coordinate");s.addPoint(x,y);}
            ink.add(s);
        }
        byte[] step=extensionVersion>=5?readBytes(in):new byte[0];
        String editableOverlay=extensionVersion>=6?ReplayTextCodec.read(in):extensionVersion>=5?in.readUTF():overlay;
        return new ReplayAnnotations(overlay,geometry,cells,candidates,ink,outlineVisibleGroup,nativeData,step.length==0?null:DoodleReplayStep.decode(step),editableOverlay);
    }
    boolean needsLongText(){return ReplayTextCodec.needsExtension(editableOverlay);}
    static void writeBytes(DataOutputStream out,byte[] b)throws IOException{if(b.length>4*1024*1024)throw new IOException("Replay extension item too large: "+b.length+" bytes (maximum 4194304)");out.writeInt(b.length);out.write(b);}
    static byte[] readBytes(DataInputStream in)throws IOException{byte[] b=new byte[count(in,4*1024*1024)];in.readFully(b);return b;}
    static int count(DataInputStream in,int limit)throws IOException{int n=in.readInt();if(n<0||n>limit)throw new IOException("Invalid extension size");return n;}
    private static void writeColors(DataOutputStream out,Map<Integer,Color> map)throws IOException{out.writeInt(map.size());for(Map.Entry<Integer,Color> e:map.entrySet()){out.writeInt(e.getKey());out.writeInt(e.getValue().getRGB());}}
    private static Map<Integer,Color> readColors(DataInputStream in,boolean candidate)throws IOException{Map<Integer,Color> map=new TreeMap<Integer,Color>();for(int n=count(in,729);n>0;n--){int key=in.readInt();if(candidate)atom(key);else cell(key);map.put(key,new Color(in.readInt(),true));}return map;}
}
