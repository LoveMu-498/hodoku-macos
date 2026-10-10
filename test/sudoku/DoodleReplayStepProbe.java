/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;
import java.awt.Color;
import java.nio.file.*;
import java.util.*;

/** Real candidate constraints, frozen step data and public Windows labels, not solver guesses. */
public final class DoodleReplayStepProbe {
    private static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static DoodleStroke mark(int atom,int kind,boolean start,boolean outlined,boolean inverse){
        DoodleStroke s=new DoodleStroke(Color.MAGENTA,.03f);s.setAnchorCell(atom/10);s.setAnchorDigit(atom%10);s.setCandidateMarkKind(kind);s.setThoughtGroup(0);s.setHypothesisStart(start);s.setConclusionOutlined(outlined);s.setRefutationResult(inverse);s.setHypothesisConditionId(start?1:0);
        double r=.16;if(kind==2){s.getPoints().add(new DoodlePoint(-r,-r));s.getPoints().add(new DoodlePoint(r,r));s.getPoints().add(new DoodlePoint(r,-r));s.getPoints().add(new DoodlePoint(-r,r));}else for(int i=0;i<=48;i++){double a=2*Math.PI*i/48;s.getPoints().add(new DoodlePoint(r*Math.cos(a),r*Math.sin(a)));}return s;
    }
    static ReplayAnnotations annotations(List<DoodleStroke> marks){return new ReplayAnnotations("",new byte[0],Collections.<Integer,Color>emptyMap(),Collections.<Integer,Color>emptyMap(),marks,0);}
    static Sudoku2 blank(){return DoodleContradictionBasisProbe.board();}
    static String text(DoodleReplayStep step)throws Exception{return DoodleReplayStep.decode(step.encode()).atApplication().description();}
    static ReplaySession replay(Sudoku2 before,Sudoku2 after,ReplayAnnotations marks,DoodleReplayStep step)throws Exception{
        ReplaySession replay=new ReplaySession(new ReplayBoard(before),1000);replay.elapsedMillis=200;
        replay.append(Arrays.asList(new ReplayFrame(1,1100,100,"doodle-conclusion-input",step.sharedLabel(),new ReplayBoard(before),null,marks.withDoodleStep(step,marks.overlay)),new ReplayFrame(1,1200,200,"doodle-conclusion-result",step.atApplication().sharedLabel(),new ReplayBoard(after),null,ReplayAnnotations.EMPTY.withDoodleStep(step.atApplication(),marks.overlay))));
        ReplayFiles.validate(replay);return replay;
    }
    public static void main(String[] args){try{run(args);System.exit(0);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}}
    private static void run(String[] args)throws Exception{
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        Sudoku2 before=blank();List<DoodleStroke> marks=new ArrayList<DoodleStroke>();marks.add(mark(805,1,true,false,false));for(int cell=0;cell<9;cell++)marks.add(mark(cell*10+3,2,false,false,false));marks.add(mark(805,2,false,true,true));
        Sudoku2 after=before.clone();after.delCandidate(80,5);ReplayAnnotations source=annotations(marks);DoodleReplayStep row=DoodleReplayStep.capture(before,after,source,false);String rowText=text(row);
        check(row.operation==DoodleReplayStep.Operation.ASSUMPTION_REFUTATION,"row classified as manual");
        check(rowText.contains("R1C1≠3")&&rowText.contains("R1C9≠3")&&rowText.contains("无处填入 3"),"row endpoint missing exact cells/digit");
        check(rowText.contains("R9C9=5")&&rowText.contains("R9C9≠5")&&rowText.contains("删除候选 R9C9(5)"),"origin/inverse/applied action missing");
        check(!rowText.contains("强制列"),"invented technique term");
        ReplaySession recorded=replay(before,after,source,row);Path output=Files.createDirectories(Paths.get(args[0]));ReplayStore.write(output.resolve("row-refutation.hrep"),recorded);ReplaySession reopened=ReplayFiles.importFile(output.resolve("row-refutation.hrep"));check(reopened.frames().get(1).methodStep()&&!reopened.last().methodStep(),"Windows method frame flags");check(reopened.last().annotations.doodleStep.description().equals(rowText),"step disk roundtrip");
        check(reopened.frames().get(1).annotations.ink().size()==marks.size(),"input glyphs lost");check(reopened.last().annotations.ink().isEmpty(),"editable result polluted with proof ink");
        int[][] units={{0,9,18,27,36,45,54,63,72},{30,31,32,39,40,41,48,49,50}};
        for(int[] unit:units){List<DoodleStroke> next=new ArrayList<DoodleStroke>();next.add(mark(805,1,true,false,false));for(int cell:unit)next.add(mark(cell*10+3,2,false,false,false));next.add(mark(805,2,false,true,true));String result=text(DoodleReplayStep.capture(before,after,annotations(next),false));check(result.contains(unit[1]==9?"第 1 列":"第 5 宫")&&result.contains("无处填入 3"),"column/box endpoint mislabeled");}
        before=blank();DoodleContradictionBasisProbe.candidates(before,32,1,7,9);after=before.clone();after.delCandidate(80,5);marks=new ArrayList<DoodleStroke>();marks.add(mark(805,1,true,false,false));for(int digit:new int[]{1,7,9})marks.add(mark(320+digit,2,false,false,false));marks.add(mark(805,2,false,true,true));String cell=text(DoodleReplayStep.capture(before,after,annotations(marks),false));check(cell.contains("R4C6≠1")&&cell.contains("R4C6≠7")&&cell.contains("R4C6≠9")&&cell.contains("该格无数可填"),"cell all-false detail");marks.remove(3);check(DoodleReplayStep.capture(before,after,annotations(marks),false).operation==DoodleReplayStep.Operation.SELECTED_CONCLUSION,"incomplete explicit candidates called refutation");
        before=blank();after=before.clone();after.setCell(80,5);marks=Arrays.asList(mark(805,2,true,false,false),mark(11,1,false,false,false),mark(21,1,false,false,false),mark(805,1,false,true,true));DoodleReplayStep duplicate=DoodleReplayStep.capture(before,after,annotations(marks),false);String duplicateText=text(duplicate);check(duplicateText.contains("数字 1 出现多次")&&duplicateText.contains("填入 R9C9=5")&&duplicateText.contains("连带变化"),"duplicate truth / explicit placement");check(duplicate.deletions().isEmpty()&&duplicate.fills().equals(Collections.singleton(805)),"automatic effects mistaken for selected removals");
        before=blank();after=before.clone();after.delCandidate(80,5);marks=Arrays.asList(mark(805,2,false,true,false));DoodleReplayStep manual=DoodleReplayStep.capture(before,after,annotations(marks),false);check(manual.operation==DoodleReplayStep.Operation.SELECTED_CONCLUSION&&manual.witnesses().isEmpty()&&!text(manual).contains("反证结论"),"manual deletion assigned an invented proof");
        before=blank();DoodleContradictionBasisProbe.candidates(before,32,1,7,9);marks=new ArrayList<DoodleStroke>();DoodleStroke a=mark(805,2,true,false,false),b=mark(794,2,true,false,false);a.setHypothesisGroupKind(2);b.setHypothesisGroupKind(2);marks.add(a);marks.add(b);for(int digit:new int[]{1,7,9})marks.add(mark(320+digit,2,false,false,false));for(int atom:new int[]{805,794}){DoodleStroke inverse=mark(atom,1,false,true,true);inverse.setHypothesisGroupKind(1);marks.add(inverse);}DoodleReplayStep or=DoodleReplayStep.capture(before,before,annotations(marks),true);check(text(or).contains("至少一个为真")&&text(or).contains("本步没有填数或删数"),"OR adoption pretending all members placed");check(!text(or).contains("R9C8=4, R9C9=5"),"OR members individually asserted");
        before=blank();after=before.clone();after.delCandidate(80,5);
        marks=Arrays.asList(mark(805,1,true,false,false),mark(805,2,false,false,false),mark(805,2,false,true,true));check(text(DoodleReplayStep.capture(before,after,annotations(marks),false)).contains("被同时标为真和假"),"same candidate contradiction explanation");
        marks=Arrays.asList(mark(805,1,true,false,false),mark(11,1,false,false,false),mark(12,1,false,false,false),mark(805,2,false,true,true));check(text(DoodleReplayStep.capture(before,after,annotations(marks),false)).contains("R1C2 同时取多个不同数字"),"cell multiple-truth explanation");
        before.setCell(0,1);before.setCandidate(1,1,true,false);after=before.clone();after.delCandidate(80,5);marks=Arrays.asList(mark(805,1,true,false,false),mark(11,1,false,false,false),mark(805,2,false,true,true));check(text(DoodleReplayStep.capture(before,after,annotations(marks),false)).contains("盘面已有 R1C1=1"),"existing value conflict explanation");
        before=blank();after=before.clone();after.delCandidate(79,4);after.delCandidate(80,5);
        DoodleStroke left=mark(794,1,true,false,false),right=mark(805,1,true,false,false);left.setHypothesisGroupKind(1);right.setHypothesisGroupKind(1);
        marks=Arrays.asList(left,right,mark(794,2,false,false,false),mark(805,2,false,false,false),mark(794,2,false,true,true),mark(805,2,false,true,true));DoodleReplayStep negatedOr=DoodleReplayStep.capture(before,after,annotations(marks),false);String negated=text(negatedOr);check(negated.contains("否定了起点组")&&negatedOr.deletions().size()==2,"complete true OR negation / explicit removals");check(!negated.contains("本组标记：R9C8=4"),"OR roots expanded into simultaneous truths");
                before=DoodleUrContradictionProbe.fixture();after=before.clone();after.delCandidate(44,1);marks=new ArrayList<DoodleStroke>();for(int i=0;i<4;i++)marks.add(mark(DoodleUrContradictionProbe.CORNERS[i]*10+DoodleUrContradictionProbe.DIGITS[i],1,i==3,false,false));marks.add(mark(441,2,false,true,true));DoodleReplayStep ur=DoodleReplayStep.capture(before,after,annotations(marks),false);check(ur.operation==DoodleReplayStep.Operation.UNIQUENESS_REFUTATION&&text(ur).contains("致命矩形")&&text(ur).contains("题目当时已确认唯一解")&&text(ur).contains("删除候选 R5C9(1)"),"UR confused with ordinary cell contradiction");check(text(ur).contains("R4C4=1")&&text(ur).contains("R4C9=7")&&text(ur).contains("R5C4=7"),"UR exact witness coordinates");
        try{DoodleReplayStep.decode(Arrays.copyOf(ur.encode(),ur.encode().length-1));throw new AssertionError("truncated step accepted");}catch(java.io.IOException expected){checks++;}
        System.out.println("PASS DoodleReplayStepProbe: "+checks+" explicit endpoint/type/explanation/application/codec checks; "+output);
    }
}
