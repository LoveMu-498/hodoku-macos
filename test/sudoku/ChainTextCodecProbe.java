package sudoku;
import java.util.*;
public final class ChainTextCodecProbe {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        Sudoku2 board=GroupedChainProbe.blank();
        for(int d=2;d<=9;d++)board.delCandidate(0,d);
        UserChain c=GroupedChainProbe.chain(false,new UserChainNode[]{GroupedChainProbe.node(5,38),GroupedChainProbe.node(3,38,42)},true);
        String text=ChainTextCodec.format(board,Arrays.asList(c),null);
        check(text.contains("(5)r5c3 = (3)r5c37"),"explicit Eureka missing");
        ChainTextCodec.Document doc=ChainTextCodec.parse(text);
        check(doc!=null&&doc.matches(board),"round trip board lost sole candidate");
        check(doc.chains().get(0).getNodes().get(1).cells().length==2,"group lost");
        check(text.equals(doc.text()),"canonical round trip unstable");
        check(ChainTextCodec.parse(text+"\nunknown trailing instruction")==null,"partial parse accepted");
        UserChain loop=GroupedChainProbe.chain(true,new UserChainNode[]{GroupedChainProbe.node(2,1),GroupedChainProbe.node(3,1),GroupedChainProbe.node(3,10)},true,false,true);
        SolutionStep result=new SolutionStep(SolutionType.AIC);result.addCandidateToDelete(30,4);result.addIndex(40);result.addValue(5);
        SudokuSet als=new SudokuSet();als.add(38);als.add(42);result.addAls(als,(short)21);
        String closed=ChainTextCodec.format(board,Arrays.asList(c,loop),result);
        ChainTextCodec.Document restored=ChainTextCodec.parse(closed);
        check(restored!=null&&closed.equals(restored.text())&&restored.chains().get(1).isClosed(),"closed multi-chain/ALS/conclusion round trip");
        check(ChainTextCodec.parse(closed.replace("(2)r1c2 = (3)r1c2", "(2=3)r1c2"))!=null,"explicit compact same-cell syntax rejected");
        java.util.Locale original=java.util.Locale.getDefault();java.util.Locale.setDefault(java.util.Locale.GERMAN);
        check(closed.equals(ChainTextCodec.format(board,Arrays.asList(c,loop),result)),"locale changed canonical output");java.util.Locale.setDefault(original);
        check(ChainTextCodec.parse(closed.replace("=> ","=> r5c5 <> 5, "))==null,"conflicting placement/deletion accepted");
        for(int d=1;d<=9;d++)board.delCandidate(80,d);
        check(ChainTextCodec.parse(ChainTextCodec.format(board,Arrays.asList(c),null))!=null,"empty candidate cell cannot round trip");
        System.out.println("Chain text deterministic round trip passed");
    }
}
