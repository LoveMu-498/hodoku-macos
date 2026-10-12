/* Copyright (C) 2026 HoDoKu contributors; GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** External data-only interchange boundary; loading never inserts into the local replay library. */
public final class ReplayFiles {
    private ReplayFiles(){}
    public static ReplaySession snapshot(ReplaySession source)throws IOException{
        return snapshot(source,false);
    }
    private static ReplaySession snapshot(ReplaySession source,boolean windowsV3)throws IOException{
        ReplaySession result=new ReplaySession(source.id,source.startedAt);
        result.sourceReplayId=source.sourceReplayId;result.sourceFrameIndex=source.sourceFrameIndex;result.setInitialAnnotations(source.initialAnnotations());
        result.foreignTimeline=source.foreignTimeline;result.elapsedMillis=source.elapsedMillis;result.endedAt=source.endedAt;result.completed=source.completed;
        // Local pin/retention policy and recovery diagnostics are not portable identity or settings.
        result.interruption=source.interruption.isEmpty()?"":"异常中断";
        for(ReplayFrame frame:source.frames())result.loadFrame(windowsV3?frame.withAnnotations(frame.annotations.forWindowsV3()):frame);
        for(ReplayBookmark bookmark:source.bookmarks())result.addBookmark(bookmark);
        validate(result);return result;
    }
    public static void exportFile(Path file,ReplaySession source,Path managedDirectory)throws IOException{
        exportFile(file,source,managedDirectory,false);
    }
    public static void exportWindowsV3File(Path file,ReplaySession source,Path managedDirectory)throws IOException{
        exportFile(file,source,managedDirectory,true);
    }
    private static void exportFile(Path file,ReplaySession source,Path managedDirectory,boolean windowsV3)throws IOException{
        Path destination=file.toAbsolutePath().normalize();
        Path managed=managedDirectory.toAbsolutePath().normalize();
        Path parent=destination.getParent();
        if(parent==null)throw new IOException("Invalid export destination");
        // Prevent overwriting managed archives/checkpoints, including aliases through symlink parents.
        Path existing=parent;while(existing!=null&&!Files.exists(existing))existing=existing.getParent();
        Path resolvedParent=existing==null?parent:existing.toRealPath().resolve(existing.relativize(parent)).normalize();
        if(parent.startsWith(managed)||(Files.exists(managed)&&resolvedParent.startsWith(managed.toRealPath())))throw new IOException("Export outside the managed replay directory");
        if(Files.isSymbolicLink(destination))throw new IOException("Export destination must not be a symbolic link");
        ReplayStore.write(destination,snapshot(source,windowsV3));
    }
    public static ReplaySession importFile(Path file)throws IOException{
        if(!Files.isRegularFile(file))throw new IOException("Replay file is not a regular file");
        try{ReplaySession result=ReplayStore.read(file);validate(result);result.retained=false;result.pinned=false;return result;}
        catch(RuntimeException ex){throw new IOException("Invalid replay data",ex);}
    }
    public static void validate(ReplaySession session)throws IOException{
        List<ReplayFrame> frames=session.frames();
        if(frames.isEmpty()||session.elapsedMillis<0)fail("Empty or invalid replay");
        if((session.sourceReplayId.isEmpty()&&session.sourceFrameIndex!=-1)||(!session.sourceReplayId.isEmpty()&&(!session.sourceReplayId.matches("[a-zA-Z0-9-]{1,80}")||session.sourceFrameIndex<0||session.sourceFrameIndex>=100000)))fail("Invalid source frame identity");
        if(session.initialAnnotations().length>0){ReplayEvidence raw=ReplayEvidence.decode(session.initialAnnotations());if(!raw.status.equals("PENDING")||raw.proofBytes().length!=0||session.sourceReplayId.isEmpty())fail("Invalid initial raw annotations");}
        if(session.foreignTimeline){
            long id=-1,time=0;ReplayBoard initialBoard=frames.get(0).board;
            for(ReplayFrame f:frames){
                if(!f.kind.equals("foreign-method")&&!f.kind.equals("foreign-action"))fail("Invalid foreign frame kind");
                if(f.operationId<id||f.elapsedMillis<time||f.elapsedMillis>session.elapsedMillis||!preservesGivens(initialBoard,f.board))fail("Invalid foreign timeline");
                id=f.operationId;time=f.elapsedMillis;
            }
            if(session.completed&&(!session.last().board.toSudoku().isSolved()||!session.last().board.toSudoku().checkSudoku()))fail("Invalid completed board");
            return;
        }
        ReplayFrame initial=frames.get(0);
        if(initial.operationId!=0||!initial.kind.equals("initial")||initial.elapsedMillis!=0||initial.evidence().length!=0)fail("Invalid initial frame");
        ReplayBoard prior=initial.board;long operation=0,elapsed=0;
        for(int at=1;at<frames.size();){
            int end=at+1;ReplayFrame first=frames.get(at);if(first.operationId!=operation+1)fail("Invalid operation sequence");operation=first.operationId;
            while(end<frames.size()&&frames.get(end).operationId==operation)end++;
            for(int part=at;part<end;){
                ReplayFrame shown=frames.get(part);String kind=shown.kind;int width;
                if(kind.equals("proof")){
                    width=2;
                    if(part+width>end||!frames.get(part+1).kind.equals("apply")||shown.evidence().length==0||ReplayEvidence.authored(shown.evidence())||frames.get(part+1).evidence().length!=0)fail("Invalid technique operation");
                    if(!shown.board.equals(prior))fail("Proof board differs from pre-operation state");
                }else if(kind.equals("authored-input")){
                    width=3;
                    if(part+width>end||!frames.get(part+1).kind.equals("authored-result")||!frames.get(part+2).kind.equals("authored-apply")||!ReplayEvidence.authored(shown.evidence()))fail("Invalid authored operation");
                    if(!shown.board.equals(prior)||!frames.get(part+1).board.equals(prior))fail("Authored preview changed board");
                    if(!Arrays.equals(shown.evidence(),frames.get(part+1).evidence())||!Arrays.equals(shown.evidence(),frames.get(part+2).evidence()))fail("Authored source changed within operation");
                }else if(kind.equals("doodle-conclusion-input")){
                    width=2;
                    if(end-at!=2||part+width>end||!frames.get(part+1).kind.equals("doodle-conclusion-result")
                            ||shown.evidence().length!=0||frames.get(part+1).evidence().length!=0)
                        fail("Invalid candidate-mark conclusion operation");
                    if(!shown.board.equals(prior))fail("Candidate-mark evidence differs from pre-operation state");
                    int visibleGroup=shown.annotations.outlineVisibleGroup;
                    if(!shown.annotations.nativeData||visibleGroup<0)
                        fail("Candidate-mark evidence lacks its visible thought group");
                    boolean hasVisibleOutline=false;
                    for(DoodleStroke stroke:shown.annotations.ink())
                        hasVisibleOutline|=stroke.isStandardCandidateMark()&&stroke.isConclusionOutlined()
                                &&stroke.getThoughtGroup()==visibleGroup;
                    if(!hasVisibleOutline)fail("Candidate-mark evidence lacks an outlined conclusion");
                    validateRetainedDoodleStarts(shown,frames.get(part+1));
                }else if(kind.equals("doodle-or-input")){
                    width=2;
                    if(end-at!=2||part+width>end||!frames.get(part+1).kind.equals("doodle-or-result")
                            ||shown.evidence().length!=0||frames.get(part+1).evidence().length!=0)
                        fail("Invalid OR adoption operation");
                    if(!shown.board.equals(prior)||!frames.get(part+1).board.equals(prior))fail("OR adoption changed board");
                    validateDoodleOrAdoption(shown,frames.get(part+1));
                }else if(kind.equals("manual")||kind.equals("restore-savepoint")||kind.equals("evidence-unavailable")){
                    width=1;if(shown.evidence().length!=0||(!kind.equals("evidence-unavailable")&&end-at!=1))fail("Invalid manual operation");
                }else{fail("Unknown replay event: "+kind);return;}
                ReplayBoard after=frames.get(part+width-1).board;if(after.equals(prior)&&!kind.equals("doodle-or-input"))fail("Operation has no board change");prior=after;part+=width;
            }
            if(first.annotations.doodleStep!=null){
                if(end-at!=2||(!first.kind.equals("doodle-conclusion-input")&&!first.kind.equals("doodle-or-input")))fail("Doodle step attached to an unrelated operation");
                first.annotations.doodleStep.validatePair(first,frames.get(end-1));
            }else for(int n=at;n<end;n++)if(frames.get(n).annotations.doodleStep!=null)fail("Doodle result has no input step");
            for(int n=at;n<end;n++){
                ReplayFrame frame=frames.get(n);
                if(frame.elapsedMillis<elapsed||frame.elapsedMillis>session.elapsedMillis)fail("Invalid effective time");elapsed=frame.elapsedMillis;
                if(!initial.board.samePuzzle(frame.board))fail("Puzzle definition changed inside an attempt");
                if(frame.evidence().length>0)ReplayEvidence.validate(frame.evidence());
            }
            at=end;
        }
        for(ReplayBookmark marker:session.bookmarks())if(marker.frameIndex<0||marker.frameIndex>=frames.size()||marker.elapsedMillis<0||marker.elapsedMillis>session.elapsedMillis)fail("Invalid savepoint marker");
        if(session.completed&&(!prior.toSudoku().isSolved()||!prior.toSudoku().checkSudoku()))fail("Completed replay has no correct final board");
        // Incorrect intermediate fills/deletions are valid historical actions, never re-solve or correct them.
    }
    private static void validateRetainedDoodleStarts(ReplayFrame before,ReplayFrame after)throws IOException{
        int group=before.annotations.outlineVisibleGroup;
        List<DoodleStroke> input=before.annotations.ink(),result=after.annotations.ink();
        Sudoku2 post=after.board.toSudoku();
        Set<Integer> retainedSources=new HashSet<Integer>();
        for(DoodleStroke mark:result)if(mark.getThoughtGroup()==group&&!mark.isProofOnly()){
            if(!mark.isStandardCandidateMark()||!mark.isHypothesisStart()||mark.isConclusionOutlined()
                    ||mark.isRefutationResult()||mark.isContradictionWitness()||mark.getConclusionSourceMask()!=0
                    ||post.getValue(mark.getAnchorCell())!=0
                    ||!post.isCandidate(mark.getAnchorCell(),mark.getAnchorDigit())
                    &&!post.isCandidate(mark.getAnchorCell(),mark.getAnchorDigit(),true))
                fail("Applied thought group retained non-start or invalid ink");
            DoodleStroke origin=null;
            for(DoodleStroke old:input)if(!old.isProofOnly()&&!old.isRefutationResult()
                    &&old.isHypothesisStart()&&sameDoodleSource(old,mark)){origin=old;break;}
            if(origin==null)fail("Retained start has no original premise");
            for(DoodleStroke old:input)if(old.getThoughtGroup()==group&&old.isHypothesisStart()
                    &&!old.isProofOnly()&&!old.isRefutationResult()&&old.getCandidateMarkKind()==1
                    &&post.getValue(old.getAnchorCell())==old.getAnchorDigit()
                    &&(origin.getHypothesisGroupKind()==1&&origin.getHypothesisConditionId()!=0
                        &&old.getHypothesisConditionId()==origin.getHypothesisConditionId()
                        ||origin.getDerivedSourceId()!=0&&old.getDerivedSourceId()==origin.getDerivedSourceId()))
                fail("Satisfied OR remains as a new premise");
            if(mark.getDerivedSourceId()!=0)retainedSources.add(mark.getDerivedSourceId());
        }
        for(DoodleStroke mark:result)if(mark.getThoughtGroup()==group&&mark.isProofOnly()){
            boolean matched=false;
            for(DoodleStroke old:input)if(old.isProofOnly()&&sameDoodleSource(old,mark))matched=true;
            if(!matched||!retainedSources.contains(mark.getDerivedSourceId())||mark.isConclusionOutlined())
                fail("Retained start has unrelated source ink");
        }
    }
    private static boolean sameDoodleSource(DoodleStroke a,DoodleStroke b){
        if(a.getThoughtGroup()!=b.getThoughtGroup()||a.getAnchorCell()!=b.getAnchorCell()
                ||a.getAnchorDigit()!=b.getAnchorDigit()||a.getCandidateMarkKind()!=b.getCandidateMarkKind()
                ||a.getDerivedSourceId()!=b.getDerivedSourceId()||!a.getColor().equals(b.getColor())
                ||a.getWidthFactor()!=b.getWidthFactor()||a.pointCount()!=b.pointCount())return false;
        for(int i=0;i<a.pointCount();i++)if(a.pointX(i)!=b.pointX(i)
                ||a.pointY(i)!=b.pointY(i))return false;
        return true;
    }
    private static void validateDoodleOrAdoption(ReplayFrame before,ReplayFrame after)throws IOException{
        int group=before.annotations.outlineVisibleGroup;
        if(!before.annotations.nativeData||!after.annotations.nativeData||group<0
                ||after.annotations.outlineVisibleGroup!=group)fail("OR adoption lacks its thought group");
        List<DoodleStroke> input=before.annotations.ink();
        Sudoku2 board=before.board.toSudoku();
        DoodleContradictionBasis.Analysis basis=DoodleContradictionBasis.analyze(board,input,group);
        if(!basis.isOrConclusion()){
            DoodleContradictionBasis.Analysis possible=DoodleContradictionBasis.analyze(board,input,group,true);
            if(possible.isOrConclusion()){
                // ReplayBoard omits live validation status. Verify uniqueness only at import,
                // never during pointer/paint detection or by testing the trusted branch.
                generator.SudokuGenerator validator=generator.SudokuGeneratorFactory.getInstance();
                try{
                    if(validator.getNumberOfSolutions(board,1)!=1)fail("UR adoption requires a unique replay board");
                }finally{generator.SudokuGeneratorFactory.giveBack(validator);}
                basis=possible;
            }
        }
        if(!basis.isOrConclusion())fail("OR adoption lacks a current refuted all-false premise");
        Set<Integer> selected=new TreeSet<Integer>(),adopted=new TreeSet<Integer>();
        for(DoodleStroke stroke:input)if(stroke.getThoughtGroup()==group&&stroke.isRefutationResult()){
            if(!stroke.isConclusionOutlined()||stroke.getCandidateMarkKind()!=DoodleStroke.MARK_TRUE_CIRCLE
                    ||stroke.getHypothesisGroupKind()!=1)fail("OR adoption has a partial or wrong-direction result");
            selected.add(stroke.getAnchorCell()*10+stroke.getAnchorDigit());
        }
        int condition=0;
        for(DoodleStroke stroke:after.annotations.ink())if(stroke.getThoughtGroup()==group){
            if(!stroke.isStandardCandidateMark()||!stroke.isHypothesisStart()||stroke.isRefutationResult()
                    ||stroke.isContradictionWitness()||stroke.isConclusionOutlined()
                    ||stroke.getCandidateMarkKind()!=DoodleStroke.MARK_TRUE_CIRCLE
                    ||stroke.getHypothesisGroupKind()!=1||stroke.getHypothesisConditionId()<=0)
                fail("OR adoption retained old branch ink or lost its grouped starts");
            if(condition!=0&&condition!=stroke.getHypothesisConditionId())fail("OR adoption split its condition");
            condition=stroke.getHypothesisConditionId();
            adopted.add(stroke.getAnchorCell()*10+stroke.getAnchorDigit());
        }
        if(!selected.equals(basis.starts)||!adopted.equals(basis.starts))fail("OR adoption changed its complete member set");
    }

    private static boolean preservesGivens(ReplayBoard first,ReplayBoard next){
        boolean[] fixed=first.fixed(),later=next.fixed();int[] values=first.values(),after=next.values();
        for(int i=0;i<81;i++)if(fixed[i]&&(!later[i]||values[i]!=after[i]))return false;
        return true;
    }
    private static void fail(String message)throws IOException{throw new IOException(message);}
}
