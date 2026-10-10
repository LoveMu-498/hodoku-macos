/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.io.*;
import java.util.*;

/** Frozen authored operation: premise, checked constraint endpoints and exact board effects.
 * No native chain topology is claimed or reconstructed from the color of a mark. */
final class DoodleReplayStep {
    enum Operation { SELECTED_CONCLUSION, ASSUMPTION_REFUTATION, UNIQUENESS_REFUTATION, OR_ADOPTION, CELL_FORCING }
    private static final int MAGIC=0x48445331,VERSION=2,MAX_ATOMS=729;
    final Operation operation;
    final int group, startKind;
    final boolean applied;
    private final Set<Integer> starts, trueMarks, falseMarks, conclusionsTrue, conclusionsFalse;
    private final Set<Integer> appliedFills, appliedDeletions, peerDeletions;
    private final List<DoodleContradictionBasis.Witness> witnesses;
    final List<DoodleConsensusBasis.Pair> coveragePairs;

    private DoodleReplayStep(Operation operation,int group,int startKind,boolean applied,
            Set<Integer> starts,Set<Integer> trueMarks,Set<Integer> falseMarks,
            Set<Integer> conclusionsTrue,Set<Integer> conclusionsFalse,
            Set<Integer> appliedFills,Set<Integer> appliedDeletions,Set<Integer> peerDeletions,
            List<DoodleContradictionBasis.Witness> witnesses){
        this(operation,group,startKind,applied,starts,trueMarks,falseMarks,conclusionsTrue,conclusionsFalse,
                appliedFills,appliedDeletions,peerDeletions,witnesses,Collections.<DoodleConsensusBasis.Pair>emptyList());
    }
    private DoodleReplayStep(Operation operation,int group,int startKind,boolean applied,
            Set<Integer> starts,Set<Integer> trueMarks,Set<Integer> falseMarks,
            Set<Integer> conclusionsTrue,Set<Integer> conclusionsFalse,
            Set<Integer> appliedFills,Set<Integer> appliedDeletions,Set<Integer> peerDeletions,
            List<DoodleContradictionBasis.Witness> witnesses,List<DoodleConsensusBasis.Pair> pairs){
        this.coveragePairs=Collections.unmodifiableList(new ArrayList<DoodleConsensusBasis.Pair>(pairs));
        this.operation=operation;this.group=group;this.startKind=startKind;this.applied=applied;
        this.starts=frozen(starts);this.trueMarks=frozen(trueMarks);this.falseMarks=frozen(falseMarks);
        this.conclusionsTrue=frozen(conclusionsTrue);this.conclusionsFalse=frozen(conclusionsFalse);
        this.appliedFills=frozen(appliedFills);this.appliedDeletions=frozen(appliedDeletions);this.peerDeletions=frozen(peerDeletions);
        this.witnesses=Collections.unmodifiableList(new ArrayList<DoodleContradictionBasis.Witness>(witnesses));
    }
    private static Set<Integer> frozen(Set<Integer> values){return Collections.unmodifiableSet(new TreeSet<Integer>(values));}
    Set<Integer> fills(){return appliedFills;}
    Set<Integer> deletions(){return appliedDeletions;}
    Set<Integer> conclusionAtoms(){Set<Integer> result=new TreeSet<Integer>(conclusionsTrue);result.addAll(conclusionsFalse);return result;}
    List<DoodleContradictionBasis.Witness> witnesses(){return witnesses;}
    String operationCode(){return operation.name();}
    boolean isAutomaticSelection(DoodleStroke mark){
        if(operation!=Operation.CELL_FORCING||!mark.isStandardCandidateMark())return false;
        int atom=mark.getAnchorCell()*10+mark.getAnchorDigit();
        for(DoodleConsensusBasis.Pair pair:coveragePairs)if((mark.getThoughtGroup()==pair.firstGroup||mark.getThoughtGroup()==pair.secondGroup)
                &&(mark.getCandidateMarkKind()==DoodleStroke.MARK_TRUE_CIRCLE?pair.truths:pair.falses).contains(atom))return true;
        return false;
    }

    static DoodleReplayStep capture(Sudoku2 before,Sudoku2 after,ReplayAnnotations input,boolean orAdoption){
        int group=input.outlineVisibleGroup;
        DoodleContradictionBasis.Analysis basis=DoodleContradictionBasis.analyze(before,input.ink(),group);
        Set<Integer> truths=new TreeSet<Integer>(),falses=new TreeSet<Integer>(),selectedTrue=new TreeSet<Integer>(),selectedFalse=new TreeSet<Integer>();
        for(DoodleStroke mark:input.ink()){
            if(!mark.isStandardCandidateMark()||mark.isProofOnly())continue;
            int atom=mark.getAnchorCell()*10+mark.getAnchorDigit();
            if(mark.getThoughtGroup()==group&&!mark.isRefutationResult()){
                if(mark.getCandidateMarkKind()==DoodleStroke.MARK_FALSE_CROSS)falses.add(atom);
                else if(mark.getHypothesisGroupKind()!=1)truths.add(atom);
            }
            if(mark.isConclusionOutlined()&&(mark.getThoughtGroup()==group||mark.getConclusionSourceMask()!=0))(mark.getCandidateMarkKind()==DoodleStroke.MARK_TRUE_CIRCLE?selectedTrue:selectedFalse).add(atom);
        }
        if(basis.startKind()==DoodleStroke.MARK_TRUE_CIRCLE&&basis.starts.size()>1)truths.removeAll(basis.starts);
        if(basis.hasRefutation()){
            selectedTrue.clear();selectedFalse.clear();
            (basis.conclusionKind()==DoodleStroke.MARK_TRUE_CIRCLE?selectedTrue:selectedFalse).addAll(basis.starts);
        }
        DoodleConsensusBasis.Analysis consensus=basis.hasRefutation()?new DoodleConsensusBasis.Analysis(group)
                :DoodleConsensusBasis.analyze(before,input.ink(),group);
        DoodleCompletionBasis.Analysis completion=DoodleCompletionBasis.analyze(before,input.ink(),group);
        if(completion.complete()) {
            selectedTrue.clear();selectedTrue.addAll(completion.fills);selectedFalse.clear();
            consensus=new DoodleConsensusBasis.Analysis(group);
        }
        if(consensus.hasConclusions()){
            selectedTrue.clear();selectedTrue.addAll(consensus.truths);
            selectedFalse.clear();selectedFalse.addAll(consensus.falses);
        }
        Set<Integer> fills=actualFills(before,after),removed=actualDeletions(before,after);
        Set<Integer> direct=new TreeSet<Integer>(removed);direct.retainAll(selectedFalse);
        Set<Integer> peers=new TreeSet<Integer>(removed);peers.removeAll(direct);
        Operation operation=orAdoption?Operation.OR_ADOPTION:basis.hasRefutation()
                ?basis.hasUniquenessConflict()?Operation.UNIQUENESS_REFUTATION:Operation.ASSUMPTION_REFUTATION:consensus.hasConclusions()?Operation.CELL_FORCING:Operation.SELECTED_CONCLUSION;
        return new DoodleReplayStep(operation,group,basis.hasRefutation()?basis.startKind():0,false,
                basis.hasRefutation()?basis.starts:Collections.<Integer>emptySet(),truths,falses,
                selectedTrue,selectedFalse,fills,direct,peers,basis.hasRefutation()?basis.witnesses():Collections.<DoodleContradictionBasis.Witness>emptyList(),consensus.pairs);
    }
    DoodleReplayStep atApplication(){return new DoodleReplayStep(operation,group,startKind,true,starts,trueMarks,falseMarks,conclusionsTrue,conclusionsFalse,appliedFills,appliedDeletions,peerDeletions,witnesses,coveragePairs);}
    private static Set<Integer> actualFills(Sudoku2 before,Sudoku2 after){Set<Integer> result=new TreeSet<Integer>();for(int cell=0;cell<81;cell++)if(before.getValue(cell)==0&&after.getValue(cell)!=0)result.add(cell*10+after.getValue(cell));return result;}
    private static Set<Integer> actualDeletions(Sudoku2 before,Sudoku2 after){Set<Integer> result=new TreeSet<Integer>();for(int cell=0;cell<81;cell++)for(int digit=1;digit<=9;digit++)if(before.isCandidate(cell,digit)&&!after.isCandidate(cell,digit))result.add(cell*10+digit);return result;}

    String title(){
        String name=ReplayText.text("doodleStep."+(operation==Operation.CELL_FORCING?"cellForcing":operation==Operation.SELECTED_CONCLUSION?"selected":operation==Operation.UNIQUENESS_REFUTATION?"uniqueness":"refutation"));
        if(witnesses.isEmpty()||operation==Operation.UNIQUENESS_REFUTATION)return name;
        DoodleContradictionBasis.Witness w=witnesses.get(0);
        if(w.kind==DoodleContradictionBasis.ConflictKind.CELL_NO_CANDIDATE)name+=" · "+ReplayText.text("doodleStep.cellEmptyShort",cell(w.location));
        else if(w.kind==DoodleContradictionBasis.ConflictKind.HOUSE_NO_DIGIT)name+=" · "+ReplayText.text("doodleStep.houseEmptyShort",house(w.location),w.digit);
        return name;
    }
    String conclusion(){
        if(operation==Operation.OR_ADOPTION)return ReplayText.text("doodleStep.atLeastOne",plainAtoms(conclusionsTrue));
        List<String> parts=new ArrayList<String>();if(!conclusionsTrue.isEmpty())parts.add(atoms(conclusionsTrue,true));if(!conclusionsFalse.isEmpty())parts.add(atoms(conclusionsFalse,false));return join(parts);
    }
    String compactTitle(){return ReplayText.text("doodleStep."+(applied?"appliedTitle":"evidenceTitle"),title(),conclusion());}
    /** The unmodified Windows viewer displays frame.label; keep the complete meaning there. */
    String sharedLabel(){return description().replace('\r',' ').replace('\n',' ');}
    String description(){
        StringBuilder text=new StringBuilder(title()).append(" ⇒ ").append(conclusion()).append('\n');
        text.append(ReplayText.text("doodleStep."+(operation==Operation.CELL_FORCING?"coverageSource":"source"))).append('\n');
        for(DoodleConsensusBasis.Pair pair:coveragePairs){
            String first=atoms(Collections.singleton(pair.firstAtom),pair.firstKind==1),second=atoms(Collections.singleton(pair.secondAtom),pair.secondKind==1);
            text.append(ReplayText.text("doodleStep.coveragePair",Character.toString((char)('A'+pair.firstGroup)),first,
                    Character.toString((char)('A'+pair.secondGroup)),second,ReplayText.text("doodleStep.coverage."+pair.coverage.name()))).append('\n');
            List<String> shared=new ArrayList<String>();if(!pair.truths.isEmpty())shared.add(atoms(pair.truths,true));if(!pair.falses.isEmpty())shared.add(atoms(pair.falses,false));
            text.append(ReplayText.text("doodleStep.sharedMarks",join(shared))).append('\n');
        }
        if(!starts.isEmpty()){
            String premise=startKind==DoodleStroke.MARK_TRUE_CIRCLE&&starts.size()>1?ReplayText.text("doodleStep.atLeastOne",plainAtoms(starts)):atoms(starts,startKind==DoodleStroke.MARK_TRUE_CIRCLE);
            text.append(ReplayText.text("doodleStep.premise",premise)).append('\n');
        }
        List<String> marks=new ArrayList<String>();if(!trueMarks.isEmpty())marks.add(atoms(trueMarks,true));if(!falseMarks.isEmpty())marks.add(atoms(falseMarks,false));
        if(!marks.isEmpty())text.append(ReplayText.text("doodleStep.authoredMarks",join(marks))).append('\n');
        for(DoodleContradictionBasis.Witness witness:witnesses)text.append(ReplayText.text("doodleStep.conflict",explain(witness))).append('\n');
        text.append(ReplayText.text("doodleStep."+(operation==Operation.CELL_FORCING?"necessaryConclusion":operation==Operation.SELECTED_CONCLUSION?"selectedConclusion":"inverseConclusion"),conclusion())).append('\n');
        if(applied){
            if(operation==Operation.OR_ADOPTION)text.append(ReplayText.text("doodleStep.adoptedOr",plainAtoms(conclusionsTrue)));
            else{
                List<String> effects=new ArrayList<String>();
                if(!appliedFills.isEmpty())effects.add(ReplayText.text("doodleStep.fill",atoms(appliedFills,true)));
                if(!appliedDeletions.isEmpty())effects.add(ReplayText.text("doodleStep.remove",plainAtoms(appliedDeletions)));
                text.append(ReplayText.text("doodleStep.applied",join(effects)));
                if(!peerDeletions.isEmpty())text.append('\n').append(ReplayText.text("doodleStep.automatic",peerDeletions.size()));
            }
        }else text.append(ReplayText.text("doodleStep.beforeApplication"));
        return text.toString();
    }
    private static String explain(DoodleContradictionBasis.Witness w){
        switch(w.kind){
            case CELL_NO_CANDIDATE:return ReplayText.text("doodleStep.cellEmpty",cell(w.location),atoms(w.atoms(),false));
            case HOUSE_NO_DIGIT:return ReplayText.text("doodleStep.houseEmpty",house(w.location),w.digit,atoms(w.atoms(),false));
            case ATOM_BOTH_TRUE_FALSE:return ReplayText.text("doodleStep.both",plainAtoms(w.atoms()));
            case CELL_MULTIPLE_TRUE:return ReplayText.text("doodleStep.cellMultiple",cell(w.location),atoms(w.atoms(),true));
            case HOUSE_DUPLICATE_TRUE:return ReplayText.text("doodleStep.houseDuplicate",house(w.location),w.digit,atoms(w.atoms(),true));
            case EXISTING_VALUE_CONFLICT:return ReplayText.text("doodleStep.existing",cell(w.location),w.digit,atoms(w.atoms(),true));
            case OR_PREMISE_ALL_FALSE:return ReplayText.text("doodleStep.orFalse",atoms(w.atoms(),false));
            case DEADLY_RECTANGLE:
                Set<Integer> rows=new TreeSet<Integer>(),cols=new TreeSet<Integer>(),boxes=new TreeSet<Integer>(),digits=new TreeSet<Integer>();
                for(int atom:w.atoms()){rows.add(Sudoku2.getRow(atom/10)+1);cols.add(Sudoku2.getCol(atom/10)+1);boxes.add(Sudoku2.getBlock(atom/10)+1);digits.add(atom%10);}
                return ReplayText.text("doodleStep.rectangle",atoms(w.atoms(),true),numbers(rows),numbers(cols),numbers(boxes),numbers(digits));
            default:throw new IllegalStateException("Unknown contradiction endpoint");
        }
    }
    static String cell(int cell){return "R"+(cell/9+1)+"C"+(cell%9+1);}
    private static String house(int unit){return ReplayText.text("doodleStep."+(unit<9?"row":unit<18?"column":"box"),unit%9+1);}
    private static String atoms(Set<Integer> values,boolean trueValue){List<String> parts=new ArrayList<String>();for(int atom:values)parts.add(cell(atom/10)+(trueValue?"=":"≠")+(atom%10));return join(parts);}
    private static String plainAtoms(Set<Integer> values){List<String> parts=new ArrayList<String>();for(int atom:values)parts.add(cell(atom/10)+"("+(atom%10)+")");return join(parts);}
    private static String numbers(Set<Integer> values){List<String> parts=new ArrayList<String>();for(int value:values)parts.add(Integer.toString(value));return join(parts);}
    private static String join(List<String> parts){return String.join(", ",parts);}

    byte[] encode()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){
            int version=operation==Operation.CELL_FORCING?VERSION:1;
            out.writeInt(MAGIC);out.writeInt(version);out.writeUTF(operation.name());out.writeInt(group);out.writeInt(startKind);out.writeBoolean(applied);
            for(Set<Integer> values:Arrays.asList(starts,trueMarks,falseMarks,conclusionsTrue,conclusionsFalse,appliedFills,appliedDeletions,peerDeletions))writeAtoms(out,values);
            if(witnesses.size()>4096)throw new IOException("Too many doodle contradiction endpoints");out.writeInt(witnesses.size());
            for(DoodleContradictionBasis.Witness witness:witnesses){out.writeUTF(witness.kind.name());out.writeInt(witness.location);out.writeInt(witness.digit);writeAtoms(out,witness.atoms());}
            if(version>=2){
                out.writeInt(coveragePairs.size());
                for(DoodleConsensusBasis.Pair pair:coveragePairs){out.writeInt(pair.firstGroup);out.writeInt(pair.secondGroup);out.writeInt(pair.firstAtom);out.writeInt(pair.secondAtom);out.writeByte(pair.firstKind);out.writeByte(pair.secondKind);out.writeUTF(pair.coverage.name());writeAtoms(out,pair.truths);writeAtoms(out,pair.falses);}
            }
        }return bytes.toByteArray();
    }
    static DoodleReplayStep decode(byte[] bytes)throws IOException{
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))){
            if(in.readInt()!=MAGIC)throw new IOException("Unknown doodle step");int version=in.readInt();
            if(version<1||version>VERSION)throw new IOException("Unknown doodle step version");
            Operation operation=Operation.valueOf(in.readUTF());int group=in.readInt(),startKind=in.readInt();boolean applied=in.readBoolean();
            if(group<0||group>5||startKind<0||startKind>2)throw new IOException("Invalid doodle step group/premise");
            Set<Integer> starts=readAtoms(in),truths=readAtoms(in),falses=readAtoms(in),trueConclusion=readAtoms(in),falseConclusion=readAtoms(in),fills=readAtoms(in),deletions=readAtoms(in),peers=readAtoms(in);
            List<DoodleContradictionBasis.Witness> witnesses=new ArrayList<DoodleContradictionBasis.Witness>();for(int count=ReplayAnnotations.count(in,4096);count>0;count--){
                DoodleContradictionBasis.ConflictKind kind=DoodleContradictionBasis.ConflictKind.valueOf(in.readUTF());int location=in.readInt(),digit=in.readInt();Set<Integer> atoms=readAtoms(in);
                boolean house=kind==DoodleContradictionBasis.ConflictKind.HOUSE_NO_DIGIT||kind==DoodleContradictionBasis.ConflictKind.HOUSE_DUPLICATE_TRUE;
                boolean noLocation=kind==DoodleContradictionBasis.ConflictKind.OR_PREMISE_ALL_FALSE||kind==DoodleContradictionBasis.ConflictKind.DEADLY_RECTANGLE;
                if(atoms.isEmpty()||digit<0||digit>9||(noLocation?location!=-1:location<0||location>=(house?27:81)))throw new IOException("Invalid doodle contradiction endpoint");
                if((house||kind==DoodleContradictionBasis.ConflictKind.ATOM_BOTH_TRUE_FALSE||kind==DoodleContradictionBasis.ConflictKind.EXISTING_VALUE_CONFLICT)?digit==0:digit!=0)throw new IOException("Invalid contradiction digit");
                if(kind==DoodleContradictionBasis.ConflictKind.DEADLY_RECTANGLE&&atoms.size()!=4)throw new IOException("Invalid rectangle endpoint");
                witnesses.add(new DoodleContradictionBasis.Witness(kind,location,digit,atoms));
            }
            List<DoodleConsensusBasis.Pair> pairs=new ArrayList<DoodleConsensusBasis.Pair>();
            if(version>=2)for(int n=ReplayAnnotations.count(in,5);n>0;n--){
                int a=in.readInt(),b=in.readInt(),aa=ReplayAnnotations.atom(in.readInt()),ba=ReplayAnnotations.atom(in.readInt()),ak=in.readUnsignedByte(),bk=in.readUnsignedByte();
                DoodleConsensusBasis.Coverage coverage=DoodleConsensusBasis.Coverage.valueOf(in.readUTF());
                Set<Integer> ct=readAtoms(in),cf=readAtoms(in);
                if(a!=group||b<0||b>5||a==b||ak<1||ak>2||bk<1||bk>2||ct.isEmpty()&&cf.isEmpty())throw new IOException("Invalid complementary branch pair");
                pairs.add(new DoodleConsensusBasis.Pair(a,b,aa,ba,ak,bk,coverage,ct,cf));
            }
            if(in.read()!=-1)throw new IOException("Trailing doodle step data");
            if(operation!=Operation.CELL_FORCING&&!pairs.isEmpty()||operation==Operation.CELL_FORCING&&(pairs.isEmpty()||startKind!=0||!starts.isEmpty()||!witnesses.isEmpty()))throw new IOException("Invalid coverage step roles");
            if(operation!=Operation.SELECTED_CONCLUSION&&operation!=Operation.CELL_FORCING&&(starts.isEmpty()||startKind==0||witnesses.isEmpty()))throw new IOException("Refutation lacks its premise or endpoint");
            if(operation==Operation.OR_ADOPTION&&(startKind!=DoodleStroke.MARK_FALSE_CROSS||starts.size()<2||!trueConclusion.equals(starts)||!falseConclusion.isEmpty()||!fills.isEmpty()||!deletions.isEmpty()||!peers.isEmpty()))throw new IOException("Invalid OR adoption step");
            if(trueConclusion.isEmpty()&&falseConclusion.isEmpty())throw new IOException("Doodle step lacks its conclusion");
            return new DoodleReplayStep(operation,group,startKind,applied,starts,truths,falses,trueConclusion,falseConclusion,fills,deletions,peers,witnesses,pairs);
        }catch(IllegalArgumentException ex){throw new IOException("Invalid doodle step",ex);}
    }
    void validatePair(ReplayFrame before,ReplayFrame after)throws IOException{
        DoodleReplayStep result=after.annotations.doodleStep;
        if(applied||result==null||!result.applied||!Arrays.equals(atApplication().encode(),result.encode()))throw new IOException("Doodle step changed within its operation");
        Sudoku2 pre=before.board.toSudoku(),post=after.board.toSudoku();
        if(operation==Operation.CELL_FORCING){
            DoodleConsensusBasis.Analysis basis=DoodleConsensusBasis.analyze(pre,before.annotations.ink(),group);
            Set<Integer> ct=new TreeSet<Integer>(),cf=new TreeSet<Integer>(),otherGroups=new HashSet<Integer>();
            for(DoodleConsensusBasis.Pair pair:coveragePairs){
                boolean matched=false;for(DoodleConsensusBasis.Pair live:basis.pairs)if(samePair(pair,live))matched=true;
                if(!matched||!otherGroups.add(pair.secondGroup))throw new IOException("Coverage lacks its explicit complementary branches");
                ct.addAll(pair.truths);cf.addAll(pair.falses);
            }
            if(!ct.equals(conclusionsTrue)||!cf.equals(conclusionsFalse))throw new IOException("Coverage conclusion differs from common branch marks");
            Set<Integer> selectedTrue=new TreeSet<Integer>(),selectedFalse=new TreeSet<Integer>();
            for(DoodleStroke mark:before.annotations.ink())if(mark.getThoughtGroup()==group&&mark.isStandardCandidateMark()&&mark.isConclusionOutlined())(mark.getCandidateMarkKind()==1?selectedTrue:selectedFalse).add(mark.getAnchorCell()*10+mark.getAnchorDigit());
            if(!selectedTrue.equals(ct)||!selectedFalse.equals(cf))throw new IOException("Coverage selection differs from the necessary batch");
        }
        if(!actualFills(pre,post).equals(appliedFills))throw new IOException("Recorded doodle fills differ from board changes");
        Set<Integer> all=new TreeSet<Integer>(appliedDeletions);all.addAll(peerDeletions);
        if(!actualDeletions(pre,post).equals(all))throw new IOException("Recorded doodle deletions differ from board changes");
        if(!conclusionsTrue.containsAll(appliedFills)||!conclusionsFalse.containsAll(appliedDeletions))throw new IOException("Doodle effects have no selected conclusion");
    }
    private static boolean samePair(DoodleConsensusBasis.Pair a,DoodleConsensusBasis.Pair b){return a.firstGroup==b.firstGroup&&a.secondGroup==b.secondGroup&&a.firstAtom==b.firstAtom&&a.secondAtom==b.secondAtom&&a.firstKind==b.firstKind&&a.secondKind==b.secondKind&&a.coverage==b.coverage&&a.truths.equals(b.truths)&&a.falses.equals(b.falses);}
    private static void writeAtoms(DataOutputStream out,Set<Integer> atoms)throws IOException{if(atoms.size()>MAX_ATOMS)throw new IOException("Too many candidate atoms");out.writeInt(atoms.size());for(int atom:atoms){ReplayAnnotations.atom(atom);out.writeInt(atom);}}
    private static Set<Integer> readAtoms(DataInputStream in)throws IOException{Set<Integer> atoms=new TreeSet<Integer>();for(int n=ReplayAnnotations.count(in,MAX_ATOMS);n>0;n--)if(!atoms.add(ReplayAnnotations.atom(in.readInt())))throw new IOException("Duplicate candidate atom");return atoms;}
}
