package sudoku;

import java.beans.*;
import java.io.*;
import java.util.*;
import static sudoku.GroupedChainProbe.*;

/** Independent OR truth-table, lossless persistence and all-input graph checks. */
public final class GeneralizedChainProbe {
    static UserChainNode atoms(int... a){return UserChainNode.fromAtoms(a,null);}
    static void same(UserChainNode a,UserChainNode b){check(a.key().equals(b.key()),"lost atoms "+a.key()+" -> "+b.key());}
    public static void main(String[] args)throws Exception {
        Sudoku2 board=blank();UserChainNode one=node(3,0),eight=node(3,1,2,9,10,11,18,19,20);
        check(eight.validShape()&&!eight.nativeEncodable(),"large group encoding");
        check(UserChainValidator.weak(one,eight)&&UserChainValidator.strong(board,one,eight),"nine box candidates must cover unit and conflict");
        check(!eight.key().equals(node(3,1,2).key()),"three-slot collision");
        try{eight.encoded(false);throw new AssertionError("truncated native encoding");}catch(IllegalStateException expected){}
        UserChainNode mixed=atoms(1,2,3,4,5,6,7,8),nine=atoms(9);
        check(UserChainValidator.strong(board,mixed,nine)&&UserChainValidator.weak(mixed,nine),"full cell mixed group");
        check(!UserChainValidator.weak(atoms(1,401),atoms(11)),"partial conflict must not suffice");
        check(!atoms(1,1).validShape(),"duplicate atom");
        check(atoms(1,405,809).validShape(),"cross-unit mixed members");
        UserChain c=chain(false,new UserChainNode[]{mixed,nine},true);
        UserChainValidator.Result r=UserChainValidator.preview(board,c);
        for(SolutionStep s:r.steps)check(s.getValues().isEmpty(),"OR truth filled arbitrary member");
        UserChain ring=chain(true,new UserChainNode[]{atoms(1,12,403),atoms(22),atoms(33)},false,true,false);
        r=UserChainValidator.preview(board,ring);
        check(r.status==UserChainValidator.Status.ASSUMED&&!r.invalidRelations.isEmpty(),"conditional preview with red edges missing");
        Set<Integer> deletes=new HashSet<>();SolutionStep generalized=null;
        for(SolutionStep s:r.steps){for(Candidate d:s.getCandidatesToDelete())deletes.add(d.getIndex()*10+d.getValue());if(!s.getGeneralizedProofs().isEmpty())generalized=s;}
        check(deletes.containsAll(Arrays.asList(1,12,403)),"false mixed group did not delete every member");
        check(generalized!=null,"generalized proof missing");
        check(ReasoningStepIndex.identity(generalized).equals(ReasoningStepIndex.identity(ReplayProof.decode(ReplayProof.encode(generalized)))),"proof replay loses identity");
        check(ReasoningStepIndex.identity(generalized).equals(ReasoningStepIndex.identity((SolutionStep)generalized.clone())),"proof clone");
        check(generalized.toString(2).contains("r5c5"),"proof explanation loses mixed member");
        String text=ChainTextCodec.format(board,Arrays.asList(c,chain(false,new UserChainNode[]{eight,atoms(405,809)},false)),null);
        ChainTextCodec.Document doc=ChainTextCodec.parse(text);check(doc!=null,"mixed text parse");check(text.equals(doc.text()),"text is not canonical reversible");
        same(eight,doc.chains().get(1).getNodes().get(0));
        byte[] evidence=ReplayEvidence.input("FREE_CHAIN",Collections.<SudokuSet>emptyList(),doc.chains());
        same(eight,ReplayEvidence.decode(evidence).chains().get(1).getNodes().get(0));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();XMLEncoder e=new XMLEncoder(bytes);e.setExceptionListener(x->{throw new AssertionError(x);});e.writeObject(eight);e.writeObject(atoms(1,405,809));e.close();
        XMLDecoder d=new XMLDecoder(new ByteArrayInputStream(bytes.toByteArray()));same(eight,(UserChainNode)d.readObject());same(atoms(1,405,809),(UserChainNode)d.readObject());d.close();
        UserChain overlap=chain(false,new UserChainNode[]{atoms(1,12),atoms(1),atoms(22)},false,true);
        check(UserChainAssembly.assemble(Arrays.asList(overlap)).chain!=null,"single/group identities merged");
        check(UserChainValidator.preview(board,overlap).problem!=UserChainValidator.Problem.DUPLICATE_NODE,"shared atom rejected as duplicate proposition");
        Map<String,Set<Integer>> cut=new HashMap<>();cut.put(mixed.key(),new HashSet<>(Arrays.asList(1,2)));
        UserChain rest=UserChainCuts.removeMembersAndEdges(c,Collections.<Integer>emptySet(),cut).get(0);
        same(atoms(3,4,5,6,7,8),rest.getNodes().get(0));
        check(UserChainAssembly.assemble(Arrays.asList(c,ring)).chain==null,"multiple disconnected chains accepted");
        SolutionStep nativeStep=new SolutionStep(SolutionType.X_CHAIN);nativeStep.addCandidateToDelete(3,3);
        byte[] v2=ReplayProof.encode(nativeStep);int diagramSize=ReplayEvidence.input("FREE_CHAIN",Collections.<SudokuSet>emptyList(),Collections.<UserChain>emptyList()).length;
        byte[] v1=Arrays.copyOf(v2,v2.length-8-diagramSize);v1[7]=1;
        check(ReplayProof.decode(v1).getCandidatesToDelete().size()==1,"legacy v1 native proof");
        List<Integer> all=new ArrayList<>();for(int cell=0;cell<81;cell++)for(int digit=1;digit<=9;digit++)all.add(cell*10+digit);
        UserChainNode largest=atoms(all.stream().mapToInt(Integer::intValue).toArray());
        String maximum=ChainTextCodec.format(board,Collections.singletonList(chain(false,new UserChainNode[]{largest})),null);
        check(ChainTextCodec.parse(maximum)!=null&&maximum.equals(ChainTextCodec.parse(maximum).text()),"all possible atoms round trip");
        String preview=ChainTextCodec.formatNativeStep(board,generalized);
        check(preview!=null&&ChainTextCodec.parse(preview)!=null,"generalized proof source cannot be shared");
        ByteArrayOutputStream oldBytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(oldBytes);
        out.writeInt(0x48524157);out.writeInt(1);out.writeUTF("FREE_CHAIN");out.writeUTF("PENDING");out.writeUTF("NONE");out.writeInt(-1);out.writeInt(0);out.writeInt(0);out.writeInt(1);
        out.writeLong(1);out.writeBoolean(false);out.writeBoolean(false);out.writeBoolean(true);out.writeUTF("");out.writeInt(1);
        out.writeInt(0);out.writeInt(3);out.writeBoolean(false);out.writeInt(2);out.writeInt(0);out.writeInt(1);out.writeInt(0);out.writeInt(0);out.writeInt(0);out.close();
        same(node(3,0,1),ReplayEvidence.decode(oldBytes.toByteArray()).chains().get(0).getNodes().get(0));
        System.out.println("Generalized OR relations, conditional conclusions, full proof, codec, replay, beans, cuts and identities passed");
    }
}
