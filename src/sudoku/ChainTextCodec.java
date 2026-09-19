/* Copyright (C) 2026 HoDoKu contributors. GPL-3.0-or-later. */
package sudoku;

import java.util.*;
import java.util.regex.*;

/** Deterministic explicit Eureka interchange; parsing never reads the live board or options. */
public final class ChainTextCodec {
    private static final String COORD="r[1-9]+c[1-9]+(?:,r[1-9]+c[1-9]+)*";
    private static final Pattern NODE=Pattern.compile("\\(([1-9])\\)("+COORD+")");
    private ChainTextCodec() {}
    public static final class Document {
        private final Sudoku2 board;
        private final List<UserChain> chains;
        private final SolutionStep step;
        private Document(Sudoku2 b,List<UserChain> c,SolutionStep s){board=b.clone();chains=copy(c);step=(SolutionStep)s.clone();}
        public Sudoku2 board(){return board.clone();}
        public List<UserChain> chains(){return copy(chains);}
        public SolutionStep step(){return (SolutionStep)step.clone();}
        public boolean hasConclusion(){return !step.getCandidatesToDelete().isEmpty()||!step.getValues().isEmpty();}
        public boolean matches(Sudoku2 b){return Arrays.equals(board.getValues(),b.getValues())&&Arrays.equals(board.getCells(),b.getCells());}
        public String text(){return format(board,chains,step);}
    }
    private static List<UserChain> copy(List<UserChain> source){
        List<UserChain> out=new ArrayList<>();for(UserChain c:source){UserChain n=new UserChain();n.setClosed(c.isClosed());
            for(UserChainNode p:c.getNodes())n.getNodes().add(p.copy());n.getStrongRelations().addAll(c.getStrongRelations());out.add(n);}return out;
    }
    private static String cells(int[] cells){List<Integer> c=new ArrayList<>();for(int x:cells)c.add(x);return SolutionStep.getCompactCellPrint(c);}
    private static String node(UserChainNode n){return "("+n.getCandidate()+")"+cells(n.cells());}
    public static String format(Sudoku2 board,List<UserChain> chains,SolutionStep step){
        StringBuilder out=new StringBuilder();String border="+-------------------------+-------------------------+-------------------------+\n";
        out.append(border);
        for(int r=0;r<9;r++){out.append('|');for(int c=0;c<9;c++){
            int at=r*9+c;String token="";
            if(board.getValue(at)!=0)token=""+board.getValue(at);else{
                for(int d=1;d<=9;d++)if(board.isCandidate(at,d))token+=d;
                if(token.length()==1)token="{"+token+"}";
                if(token.isEmpty())token="{}";
            }
            out.append(' ').append(token);for(int s=token.length();s<9;s++)out.append(' ');
            if(c%3==2)out.append('|');
        }out.append('\n');if(r%3==2)out.append(border);}
        for(UserChain chain:chains){if(chain.getNodes().isEmpty())continue;
            out.append("Chain: ").append(node(chain.getNodes().get(0)));
            for(int i=0;i<chain.getStrongRelations().size();i++)out.append(chain.getStrongRelations().get(i)?" = ":" - ").append(node(chain.getNodes().get((i+1)%chain.getNodes().size())));
            out.append('\n');
        }
        if(step!=null){
            TreeSet<String> premises=new TreeSet<>();
            for(AlsInSolutionStep a:step.getAlses()){
                String digits="";for(int d:new TreeSet<Integer>(a.getCandidates()))digits+=d;
                premises.add("ALS: ("+digits+")"+SolutionStep.getCompactCellPrint(a.getIndices()));
            }
            for(String p:premises)out.append(p).append('\n');
            TreeSet<String> conclusions=new TreeSet<>();
            for(Candidate c:step.getCandidatesToDelete())conclusions.add(cells(new int[]{c.getIndex()})+" <> "+c.getValue());
            for(int i=0;i<step.getIndices().size()&&i<step.getValues().size();i++)conclusions.add(cells(new int[]{step.getIndices().get(i)})+" = "+step.getValues().get(i));
            if(!conclusions.isEmpty())out.append("=> ").append(String.join(", ",conclusions)).append('\n');
        }
        return out.toString();
    }
    /** Native non-branching chains can share the same complete format; unsupported nets stay native text. */
    static String formatNativeStep(Sudoku2 board,SolutionStep step){
        if(step.getChains().isEmpty())return null;
        List<UserChain> chains=new ArrayList<>();
        for(Chain raw:step.getChains()){
            UserChain c=new UserChain();
            for(int i=raw.getStart();i<=raw.getEnd();i++){
                int entry=raw.getChain()[i];if(entry<0)return null;
                int type=Chain.getSNodeType(entry);if(type!=Chain.NORMAL_NODE&&type!=Chain.GROUP_NODE)return null;
                UserChainNode n=new UserChainNode(Chain.getSCellIndex(entry),Chain.getSCandidate(entry));
                if(type==Chain.GROUP_NODE){int third=Chain.getSCellIndex3(entry);n.setGroupCells(third<0
                    ?new int[]{Chain.getSCellIndex(entry),Chain.getSCellIndex2(entry)}
                    :new int[]{Chain.getSCellIndex(entry),Chain.getSCellIndex2(entry),third});}
                if(!n.validShape())return null;
                c.getNodes().add(n);if(i>raw.getStart())c.getStrongRelations().add(Chain.isSStrong(entry));
            }
            int last=c.getNodes().size()-1;if(last>1&&c.getNodes().get(0).identity()==c.getNodes().get(last).identity()){
                c.getNodes().remove(last);c.setClosed(true);
            }
            chains.add(c);
        }
        SolutionStep claims=(SolutionStep)step.clone();
        // For chain techniques, Values can name participating digits, not placements.
        if(!step.isAuthoredPlacement() && step.getType()!=SolutionType.FORCING_CHAIN_VERITY
                && step.getType()!=SolutionType.FORCING_CHAIN_CONTRADICTION){claims.getIndices().clear();claims.getValues().clear();}
        String text=format(board,chains,claims);Document parsed=parse(text);return parsed==null?null:parsed.text();
    }

    private static int[] parseCells(String text){
        TreeSet<Integer> result=new TreeSet<>();for(String part:text.split(",")){
            Matcher m=Pattern.compile("r([1-9]+)c([1-9]+)").matcher(part);if(!m.matches())throw new IllegalArgumentException();
            if(m.group(1).length()>1&&m.group(2).length()>1)throw new IllegalArgumentException();
            for(char r:m.group(1).toCharArray())for(char c:m.group(2).toCharArray())if(!result.add((r-'1')*9+c-'1'))throw new IllegalArgumentException();
        }
        int[] out=new int[result.size()];int i=0;for(int c:result)out[i++]=c;return out;
    }
    private static UserChain parseChain(String text){
        // Compact within-cell Eureka is an explicit sequence, not a board-dependent inference.
        Matcher compact=Pattern.compile("\\(([1-9](?:[=-][1-9])+)\\)(r[1-9]c[1-9])").matcher(text);
        StringBuffer expanded=new StringBuffer();while(compact.find()){
            String sequence=compact.group(1),cell=compact.group(2),replacement="";
            for(int i=0;i<sequence.length();i++)replacement+=i%2==0?"("+sequence.charAt(i)+")"+cell:" "+sequence.charAt(i)+" ";
            compact.appendReplacement(expanded,Matcher.quoteReplacement(replacement));
        }compact.appendTail(expanded);text=expanded.toString();
        UserChain chain=new UserChain();int pos=0;
        while(pos<text.length()){
            Matcher m=NODE.matcher(text);m.region(pos,text.length());if(!m.lookingAt())throw new IllegalArgumentException();
            int[] cs=parseCells(m.group(2));UserChainNode n=new UserChainNode(cs[0],Integer.parseInt(m.group(1)));if(cs.length>1)n.setGroupCells(cs);
            if(!n.validShape())throw new IllegalArgumentException();chain.getNodes().add(n);pos=m.end();
            while(pos<text.length()&&Character.isWhitespace(text.charAt(pos)))pos++;
            if(pos==text.length())break;
            char op=text.charAt(pos++);if(op!='='&&op!='-')throw new IllegalArgumentException();chain.getStrongRelations().add(op=='=');
            while(pos<text.length()&&Character.isWhitespace(text.charAt(pos)))pos++;
            if(pos==text.length())throw new IllegalArgumentException();
        }
        int size=chain.getNodes().size();if(size==0)throw new IllegalArgumentException();
        if(size>2&&chain.getNodes().get(0).identity()==chain.getNodes().get(size-1).identity()){chain.getNodes().remove(size-1);chain.setClosed(true);}
        Set<Integer> seen=new HashSet<>();for(UserChainNode n:chain.getNodes())for(int c:n.cells())if(!seen.add(c*10+n.getCandidate()))throw new IllegalArgumentException();
        return chain;
    }
    public static Document parse(String text){
        if(text==null||text.length()>200000)return null;
        try{
            List<String> tokens=new ArrayList<>(), body=new ArrayList<>();boolean afterGrid=false;
            for(String raw:text.replace("\r","").replace("=>", "\n=>").split("\n")){
                String line=raw.trim();if(line.isEmpty())continue;
                if(line.startsWith("|")){if(afterGrid)throw new IllegalArgumentException();
                    String[] row=line.replace('|',' ').trim().split("\\s+");if(row.length!=9)throw new IllegalArgumentException();tokens.addAll(Arrays.asList(row));
                }else if(line.matches("[+.:;'\\-]+")){}else{afterGrid=true;body.add(line);}
            }
            if(tokens.size()!=81||body.isEmpty())return null;
            Sudoku2 board=new Sudoku2();board.setSudoku(new String(new char[81]).replace('\0','0'));
            int[] masks=new int[81];boolean[] filled=new boolean[81];
            for(int c=0;c<81;c++){
                String t=tokens.get(c);if(!t.matches("[A-Z*#@+!{}1-9-]+"))return null;
                String digits=t.replaceAll("[^1-9]","");if(digits.isEmpty()&&!t.equals("{}"))return null;
                for(char d:digits.toCharArray()){int bit=1<<(d-'1');if((masks[c]&bit)!=0)return null;masks[c]|=bit;}
                filled[c]=digits.length()==1&&!t.contains("{")&&!t.contains("-")&&!t.contains("*");
                if(filled[c])board.setCell(c,digits.charAt(0)-'0');
            }
            for(int c=0;c<81;c++)if(!filled[c])for(int d=1;d<=9;d++)board.setCandidate(c,d,(masks[c]&(1<<(d-1)))!=0);
            List<UserChain> chains=new ArrayList<>();SolutionStep step=new SolutionStep(SolutionType.AIC);
            for(String line:body){
                if(line.startsWith("Chain: "))chains.add(parseChain(line.substring(7)));
                else if(line.startsWith("("))chains.add(parseChain(line));
                else if(line.startsWith("ALS: ")){
                    Matcher m=Pattern.compile("ALS: \\(([1-9]+)\\)("+COORD+")").matcher(line);if(!m.matches())return null;
                    SudokuSet positions=new SudokuSet();for(int c:parseCells(m.group(2)))positions.add(c);
                    short mask=0;for(char d:m.group(1).toCharArray())mask|=1<<(d-'1');step.addAls(positions,mask);
                }else if(line.startsWith("=> ")){
                    Matcher m=Pattern.compile("r([1-9])c([1-9])\\s*(<>|=)\\s*([1-9])").matcher(line.substring(3));int end=0;
                    while(m.find()){
                        if(!line.substring(3+end,3+m.start()).matches("[ ,]*"))return null;
                        int cell=(Integer.parseInt(m.group(1))-1)*9+Integer.parseInt(m.group(2))-1,d=Integer.parseInt(m.group(4));
                        if(board.getValue(cell)!=0||!board.isCandidate(cell,d))return null;
                        if(m.group(3).equals("<>"))step.addCandidateToDelete(cell,d);else{step.addIndex(cell);step.addValue(d);}end=m.end();
                    }
                    if(end==0||end!=line.length()-3)return null;
                }else return null;
            }
            if(chains.isEmpty())return null;
            for(UserChain c:chains){int n=c.getNodes().size();int[] enc=new int[n+(c.isClosed()?1:0)];
                for(int i=0;i<enc.length;i++)enc[i]=c.getNodes().get(i%n).encoded(i>0&&c.getStrongRelations().get(i-1));
                step.addChain(0,enc.length-1,enc);
                for(UserChainNode node:c.getNodes())for(int cell:node.cells())if(board.getValue(cell)!=0||!board.isCandidate(cell,node.getCandidate()))return null;
            }
            Map<Integer,Integer> placements=new HashMap<>();for(int i=0;i<step.getValues().size();i++){
                Integer old=placements.put(step.getIndices().get(i),step.getValues().get(i));if(old!=null&&!old.equals(step.getValues().get(i)))return null;
            }
            for(Candidate c:step.getCandidatesToDelete())if(Integer.valueOf(c.getValue()).equals(placements.get(c.getIndex())))return null;
            step.setAuthoredPlacement(!step.getValues().isEmpty());
            return new Document(board,chains,step);
        }catch(IllegalArgumentException|IndexOutOfBoundsException ex){return null;}
    }
}
