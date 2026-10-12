/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.io.IOException;
import java.util.*;

/** Data-only adapter for the reported Windows O10/MT variant; never a reasoning engine. */
final class ReplayWindowsV3 {
    private ReplayWindowsV3(){}
    static DoodleStroke readMark(String[] t)throws IOException{
        if(t.length!=10)throw new IOException("Invalid Windows circle layout");
        int rgb=ReplayAnnotations.integer(t[1]),kind=ReplayAnnotations.integer(t[2]),cell=ReplayAnnotations.integer(t[3]),digit=ReplayAnnotations.integer(t[4]),outline=ReplayAnnotations.integer(t[5]);
        if(kind<0||kind>2||outline<0||outline>1)throw new IOException("Invalid Windows mark type");
        for(int i=6;i<10;i++){
            double coordinate;try{coordinate=Double.parseDouble(t[i]);}catch(NumberFormatException ex){throw new IOException("Invalid Windows coordinate",ex);}
            if(!Double.isFinite(coordinate)||coordinate<0||coordinate>1)throw new IOException("Invalid Windows coordinate");
        }
        if(kind==0){if(cell!=-1||digit!=-1)throw new IOException("Invalid free Windows circle anchor");return null;}
        if(digit<1||digit>9)throw new IOException("Invalid Windows anchor digit");
        ReplayAnnotations.atom(ReplayAnnotations.cell(cell)*10+digit);
        DoodleStroke s=new DoodleStroke(new Color(rgb,true),.035f);s.setAnchorCell(cell);s.setAnchorDigit(digit);s.setCandidateMarkKind(kind);s.setConclusionOutlined(outline==1);
        // The viewport reference is unspecified. The semantic anchor permits a
        // standard glyph, but not reconstruction of the foreign pen dimensions.
        double radius=.1;
        if(kind==2){s.getPoints().add(new DoodlePoint(-radius,-radius));s.getPoints().add(new DoodlePoint(radius,radius));s.getPoints().add(new DoodlePoint(radius,-radius));s.getPoints().add(new DoodlePoint(-radius,radius));}
        else for(int i=0;i<=48;i++){double angle=i*Math.PI/24;s.getPoints().add(new DoodlePoint(radius*Math.cos(angle),radius*Math.sin(angle)));}
        return s;
    }
    static String key(DoodleStroke s){return s.getAnchorCell()+":"+s.getAnchorDigit()+":"+s.getCandidateMarkKind()+":"+s.getColor().getRGB();}
    static boolean multiEnabled(String overlay){
        boolean enabled=false;for(String line:overlay.split("\n")){String[] t=line.trim().split("\\s+");if(t.length==2&&t[0].equals("MT"))enabled=t[1].equals("1");}return enabled;
    }
    static boolean hasFrozenColors(String overlay){
        for(String line:overlay.split("\n")){String[] t=line.trim().split("\\s+");if(t.length>=3&&(t[0].equals("G")||t[0].equals("R")||t[0].equals("C")))return true;}return false;
    }
    static boolean eligible(DoodleStroke s){return s.isStandardCandidateMark()&&!s.isProofOnly()&&s.getHypothesisGroupKind()==0&&!s.isContradictionWitness()&&!s.isRefutationResult();}
    static String overlay(ReplayAnnotations annotations)throws IOException{
        if(!annotations.nativeData)return annotations.overlay;
        String[] lines=annotations.overlay.split("\n",-1);
        Set<Integer> skip=new HashSet<Integer>();Map<Integer,String> additions=new HashMap<Integer,String>();
        Map<Integer,Integer> removed=new HashMap<Integer,Integer>();Map<Integer,String[]> metadata=new HashMap<Integer,String[]>();
        Set<String> existing=new HashSet<String>(),represented=new HashSet<String>();
        for(String text:lines){String[] t=text.trim().split("\\s+");if(t[0].equals("O")&&t.length==10){DoodleStroke mark=readMark(t);if(mark!=null)existing.add(key(mark));}}
        List<Integer> contours=new ArrayList<Integer>();int lastMark=-1;
        for(int i=0;i<lines.length;i++){
            String[] fields=lines[i].trim().split("\\s+");if(!fields[0].equals("DM1"))continue;
            DoodleStroke s=ReplayAnnotations.readStructuredMark(fields);int paths=ReplayAnnotations.integer(fields[12]);
            represented.add(key(s));
            int base=paths==0?0:s.getCandidateMarkKind()==2?2:1;boolean convert=eligible(s);lastMark=i;
            if(paths>base)contours.add(i);
            if(!convert)continue;
            metadata.put(i,fields);removed.put(i,0);
            if(existing.add(key(s)))additions.put(i,line(s,s.isConclusionOutlined()&&s.getThoughtGroup()==annotations.outlineVisibleGroup));
            // Only remove a known, complete immediate fallback. Free ink is kept.
            boolean associated=paths>=base&&i+base<lines.length;
            for(int p=1;p<=base&&associated;p++)associated=lines[i+p].startsWith("P ");
            if(associated){for(int p=1;p<=base;p++)skip.add(i+p);removed.put(i,base);}
        }
        List<DoodleStroke> missing=new ArrayList<DoodleStroke>();
        for(DoodleStroke mark:annotations.ink())if(eligible(mark)&&represented.add(key(mark)))missing.add(mark);
        if(metadata.isEmpty()&&missing.isEmpty())return annotations.overlay;
        // Native capture places visible outline fallbacks after all base paths.
        // Confirm that whole suffix before removing any converted contour.
        List<Integer> pixels=new ArrayList<Integer>();for(int i=lastMark+1;i<lines.length;i++)if(lines[i].startsWith("P "))pixels.add(i);
        int start=pixels.size()-contours.size();boolean suffix=start>=0;
        for(int i=Math.max(0,start);i<pixels.size()&&suffix;i++)suffix=lines[pixels.get(i)].startsWith("P -15242753 ");
        if(suffix)for(int i=0;i<contours.size();i++)if(metadata.containsKey(contours.get(i))){int mark=contours.get(i);skip.add(pixels.get(start+i));removed.put(mark,removed.get(mark)+1);}
        StringBuilder result=new StringBuilder();
        for(int i=0;i<lines.length;i++){
            if(skip.contains(i))continue;
            if(metadata.containsKey(i)&&removed.get(i)>0){String[] fields=metadata.get(i);fields[12]=Integer.toString(ReplayAnnotations.integer(fields[12])-removed.get(i));result.append(String.join(" ",fields));}
            else result.append(lines[i]);
            if(i<lines.length-1||additions.containsKey(i))result.append('\n');
            if(additions.containsKey(i))result.append(additions.get(i));
        }
        // Older native tails may have anchors without a public DM1 projection.
        // Preserve their unassociated pixels; never guess which paths to remove.
        for(DoodleStroke mark:missing){
            if(result.length()>0&&result.charAt(result.length()-1)!='\n')result.append('\n');
            ReplayAnnotations.appendStructuredMark(result,mark,annotations.outlineVisibleGroup,0);
            if(existing.add(key(mark)))result.append(line(mark,mark.isConclusionOutlined()&&mark.getThoughtGroup()==annotations.outlineVisibleGroup));
        }
        // Validate the projected text before the atomic writer touches its target.
        ReplayAnnotations.fromWindows(result.toString());return result.toString();
    }
    private static String line(DoodleStroke s,boolean outlined){
        int cell=s.getAnchorCell(),digit=s.getAnchorDigit()-1;
        double x=(cell%9+(digit%3+.5)/3)/9,y=(cell/9+(digit/3+.5)/3)/9,r=.1/9;
        return "O "+s.getColor().getRGB()+" "+s.getCandidateMarkKind()+" "+cell+" "+s.getAnchorDigit()+" "+(outlined?1:0)+" "+(x-r)+" "+(y-r)+" "+(x+r)+" "+(y+r)+"\n";
    }
}
