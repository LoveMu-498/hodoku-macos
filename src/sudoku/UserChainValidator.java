package sudoku;

import java.util.*;

/** Read-only implication proof over exactly the authored edges and target conflicts. */
final class UserChainValidator {
    enum Status { INCOMPLETE, INVALID, NO_CONCLUSION, PROVEN, ASSUMED }
    enum Problem { NONE, MISSING_CANDIDATE, DUPLICATE_NODE, RELATION_COUNT, INVALID_WEAK, INVALID_STRONG, DISCONNECTED_INPUT, BRANCHED_INPUT, CONFLICTING_EDGE, INVALID_GROUP }
    static final class Result {
        final Status status;
        final int invalidEdge;
        final List<SolutionStep> steps;
        final Set<String> invalidRelations = new HashSet<String>();
        final Problem problem;
        Result(Status status, int edge, List<SolutionStep> steps) {
            this(status, edge, steps, Problem.NONE);
        }
        Result(Status status, int edge, List<SolutionStep> steps, Problem problem) {
            this.status = status; this.invalidEdge = edge; this.steps = steps; this.problem = problem;
        }
    }
    static Result validate(Sudoku2 board, UserChain chain) { return analyze(board,chain,false); }
    static Result preview(Sudoku2 board, UserChain chain) { return analyze(board,chain,true); }
    private static Result analyze(Sudoku2 board, UserChain chain, boolean allowAssumptions) {
        List<SolutionStep> steps = new ArrayList<SolutionStep>();
        if (chain == null || chain.getNodes().size() < 2) return new Result(Status.INCOMPLETE, -1, steps);
        int n = chain.getNodes().size();
        int edges = n - (chain.isClosed() ? 0 : 1);
        if (chain.getStrongRelations().size() != edges) return new Result(Status.INVALID, -1, steps, Problem.RELATION_COUNT);
        Set<Integer> seen = new HashSet<Integer>();
        Set<String> nodeKeys=new HashSet<>();
        UserChainNode[] nodes = chain.getNodes().toArray(new UserChainNode[n]);
        for (int i = 0; i < n; i++) {
            UserChainNode node = nodes[i];
            if (!node.validShape()) return new Result(Status.INVALID, -1, steps, Problem.INVALID_GROUP);
            if(!nodeKeys.add(node.key()))return new Result(Status.INVALID,-1,steps,Problem.DUPLICATE_NODE);
            for (int atom : node.atoms()) {
                int cell=atom/10,digit=atom%10;
                if(board.getValue(cell)!=0 || !board.isCandidate(cell,digit))
                    return new Result(Status.INVALID,-1,steps,Problem.MISSING_CANDIDATE);
                seen.add(atom);
            }
        }
        Result invalid = null;
        Map<String,solver.Als> alsPremises=new HashMap<>();
        for (int i=0;i<edges;i++) {
            Boolean strong=chain.getStrongRelations().get(i);
            UserChainNode a=nodes[i],b=nodes[(i+1)%n];
            boolean valid=strong!=null && (strong ? ordinaryStrong(board,a,b) : weak(a,b));
            if(Boolean.TRUE.equals(strong) && !valid && a.sameDigit()>0 && b.sameDigit()>0 && a.sameDigit()!=b.sameDigit()) {
                solver.Als als=NativeAlsStrongLinks.forBoard(board).find(a,b);
                if(als!=null){valid=true;alsPremises.put(relationKey(a,b,true),als);}
            }
            if(!valid) {
                if(invalid==null) invalid=new Result(Status.INVALID,i,steps,
                    Boolean.TRUE.equals(strong)?Problem.INVALID_STRONG:Problem.INVALID_WEAK);
                invalid.invalidRelations.add(relationKey(nodes[i],nodes[(i+1)%n],Boolean.TRUE.equals(strong)));
            }
        }
        if(invalid!=null && !allowAssumptions)return invalid;
        SolutionType type = classify(board,chain);
        SolutionStep deletions = new SolutionStep(type);
        for (int cell = 0; cell < 81; cell++) for (int digit = 1; digit <= 9; digit++) {
            if (board.getValue(cell) != 0 || !board.isCandidate(cell, digit)) continue;
            if (Thread.currentThread().isInterrupted()) return new Result(Status.NO_CONCLUSION, -1, steps);
            int target = cell * 10 + digit;
            UserChainProof negativeProof = proof(chain, nodes, target, true);
            if (negativeProof != null) {
                deletions.addCandidateToDelete(cell, digit);
                negativeProof.addTo(deletions);
                addAlsPremises(deletions,negativeProof,alsPremises);
            }
            if (seen.contains(target)) {
                UserChainProof positiveProof = proof(chain, nodes, target, false);
                if (positiveProof != null) {
                    SolutionStep placement = new SolutionStep(type);
                    placement.addIndex(cell);placement.addValue(digit);placement.setAuthoredPlacement(true);
                    positiveProof.addTo(placement);
                    addAlsPremises(placement,positiveProof,alsPremises);
                    if (!placement.getCandidatesToDelete().isEmpty() || !placement.getValues().isEmpty()) steps.add(placement);
                }
            }
        }
        if (!deletions.getCandidatesToDelete().isEmpty()) steps.add(deletions);
        for(SolutionStep s:steps)if(!s.getGeneralizedProofs().isEmpty())s.setAuthoredChainDiagram(ChainTextCodec.copy(Collections.singletonList(chain)));
        Result result=new Result(steps.isEmpty() ? (invalid==null?Status.NO_CONCLUSION:Status.INVALID) : (invalid==null?Status.PROVEN:Status.ASSUMED), invalid==null?-1:invalid.invalidEdge, steps,invalid==null?Problem.NONE:invalid.problem);
        if(invalid!=null)result.invalidRelations.addAll(invalid.invalidRelations);
        return result;
    }
    private static SolutionType classify(Sudoku2 board,UserChain chain) {
        if (!chain.isClosed()) {
            boolean alternating=true,sameDigit=true,grouped=false,xy=true;
            int d=chain.getNodes().get(0).getCandidate();
            for(UserChainNode node:chain.getNodes()){sameDigit &= node.sameDigit()==d;grouped |= node.grouped();}
            for(int i=0;i<chain.getStrongRelations().size();i++) {
                boolean strong=Boolean.TRUE.equals(chain.getStrongRelations().get(i));
                if(i>0&&strong==Boolean.TRUE.equals(chain.getStrongRelations().get(i-1)))alternating=false;
                UserChainNode a=chain.getNodes().get(i),b=chain.getNodes().get(i+1);
                xy &= !a.grouped()&&!b.grouped()&&(strong ? a.getCellIndex()==b.getCellIndex()&&board.getAnzCandidates(a.getCellIndex())==2 : a.getCandidate()==b.getCandidate());
            }
            if(alternating && Boolean.TRUE.equals(chain.getStrongRelations().get(0)) && Boolean.TRUE.equals(chain.getStrongRelations().get(chain.getStrongRelations().size()-1))) {
                if(grouped)return SolutionType.GROUPED_AIC;
                if(sameDigit)return SolutionType.X_CHAIN;
                if(xy)return SolutionType.XY_CHAIN;
                return SolutionType.AIC;
            }
            return SolutionType.FORCING_CHAIN_CONTRADICTION;
        }
        int n=chain.getStrongRelations().size(), breaks=0;
        for(int i=0;i<n;i++) if(chain.getStrongRelations().get(i).equals(chain.getStrongRelations().get((i+1)%n))) breaks++;
        boolean grouped=false;for(UserChainNode node:chain.getNodes())grouped |= node.grouped();
        return breaks==0 ? (grouped?SolutionType.GROUPED_CONTINUOUS_NICE_LOOP:SolutionType.CONTINUOUS_NICE_LOOP) : breaks==1 ? (grouped?SolutionType.GROUPED_DISCONTINUOUS_NICE_LOOP:SolutionType.DISCONTINUOUS_NICE_LOOP)
                : SolutionType.FORCING_CHAIN_CONTRADICTION;
    }
    static boolean weak(int a, int b) {
        if (a == b) return false;
        int x = a / 10, y = b / 10;
        return x == y || a % 10 == b % 10 && (Sudoku2.getRow(x) == Sudoku2.getRow(y)
                || Sudoku2.getCol(x) == Sudoku2.getCol(y) || Sudoku2.getBlock(x) == Sudoku2.getBlock(y));
    }
    static boolean strong(Sudoku2 board, int a, int b) {
        if (!weak(a, b)) return false;
        int x = a / 10, y = b / 10, d = a % 10;
        if (x == y) return board.getAnzCandidates(x) == 2;
        for (int house = 0; house < 3; house++) {
            int unit = unit(x, house);
            if (unit != unit(y, house)) continue;
            int count = 0;
            for (int c = 0; c < 81; c++) if (unit(c, house) == unit && board.getValue(c) == 0
                    && board.isCandidate(c, d)) count++;
            if (count == 2) return true;
        }
        return false;
    }
    static String relationKey(UserChainNode a, UserChainNode b, boolean strong) {
        String x=a.key(), y=b.key(); return (x.compareTo(y)<0?x+":"+y:y+":"+x)+":"+strong;
    }
    static boolean weak(UserChainNode a, UserChainNode b) {
        for(int x:a.atoms())for(int y:b.atoms())if(!weak(x,y))return false;
        return true;
    }
    static boolean strong(Sudoku2 board, UserChainNode a, UserChainNode b) {
        return ordinaryStrong(board,a,b) || a.sameDigit()>0 && b.sameDigit()>0 && a.sameDigit()!=b.sameDigit()
                && NativeAlsStrongLinks.forBoard(board).find(a,b)!=null;
    }
    private static void addAlsPremises(SolutionStep step,UserChainProof proof,Map<String,solver.Als> premises) {
        for(int i=1;i<proof.getNodes().size();i++) {
            if(!proof.getTruths().get(i) || proof.getTruths().get(i-1))continue;
            solver.Als als=premises.get(relationKey(proof.getNodes().get(i-1),proof.getNodes().get(i),true));
            if(als==null)continue;
            boolean present=false;
            for(AlsInSolutionStep existing:step.getAlses()) {
                if(existing.getIndices().size()==als.indices.size() && existing.getIndices().containsAll(toList(als.indices)))present=true;
            }
            if(!present)step.addAls(als.indices,als.candidates);
        }
    }
    private static List<Integer> toList(SudokuSet set) {
        List<Integer> result=new ArrayList<>();for(int i=0;i<set.size();i++)result.add(set.get(i));return result;
    }
    static boolean ordinaryStrong(Sudoku2 board, UserChainNode a, UserChainNode b) {
        // Covering any complete cell or house is sufficient for A OR B, even when
        // the groups overlap. Strong does not imply mutually exclusive.
        Set<Integer> covered=new HashSet<>();for(int x:a.atoms())covered.add(x);for(int x:b.atoms())covered.add(x);
        for(int cell=0;cell<81;cell++)if(board.getValue(cell)==0){
            boolean complete=true;int count=0;
            for(int d=1;d<=9;d++)if(board.isCandidate(cell,d)){count++;if(!covered.contains(cell*10+d))complete=false;}
            if(count>0&&complete)return true;
        }
        for(int kind=0;kind<3;kind++)for(int u=0;u<9;u++)for(int d=1;d<=9;d++){
            boolean complete=true,placed=false;int count=0;
            for(int cell=0;cell<81;cell++)if(unit(cell,kind)==u){
                if(board.getValue(cell)==d)placed=true;
                if(board.getValue(cell)==0&&board.isCandidate(cell,d)){count++;if(!covered.contains(cell*10+d))complete=false;}
            }
            if(!placed&&count>0&&complete)return true;
        }
        return false;
    }

    private static int unit(int cell, int kind) {
        return kind == 0 ? Sudoku2.getRow(cell) : kind == 1 ? Sudoku2.getCol(cell) : Sudoku2.getBlock(cell);
    }
    private static void link(List<List<Integer>> graph, int a, int b, boolean strong) {
        // Literal 2*i is false, 2*i+1 true. Include both directions of each inference.
        graph.get(2 * a + (strong ? 0 : 1)).add(2 * b + (strong ? 1 : 0));
        graph.get(2 * b + (strong ? 0 : 1)).add(2 * a + (strong ? 1 : 0));
    }
    private static UserChainProof proof(UserChain chain, UserChainNode[] nodes, int target, boolean assumption) {
        List<UserChainNode> ids = new ArrayList<UserChainNode>(Arrays.asList(nodes));
        int targetIndex=-1;
        for(int i=0;i<nodes.length;i++)if(!nodes[i].grouped() && nodes[i].contains(target/10,target%10))targetIndex=i;
        if(targetIndex<0){targetIndex=ids.size();ids.add(new UserChainNode(target/10,target%10));}
        List<List<Integer>> graph = new ArrayList<List<Integer>>();
        for (int i = 0; i < ids.size() * 2; i++) graph.add(new ArrayList<Integer>());
        for (int i = 0; i < chain.getStrongRelations().size(); i++)
            link(graph, i, (i + 1) % nodes.length, chain.getStrongRelations().get(i));
        // Only the tested target may supply additional weak links; never another solving chain.
        for (int i=0;i<nodes.length;i++) {
            if(weak(ids.get(targetIndex),nodes[i]))link(graph,targetIndex,i,false);
            if(nodes[i].grouped() && nodes[i].contains(target/10,target%10)) {
                graph.get(targetIndex*2+1).add(i*2+1);
                graph.get(i*2).add(targetIndex*2);
            }
        }
        int start = targetIndex * 2 + (assumption ? 1 : 0), end = start ^ 1;
        int[] parent = new int[graph.size()]; Arrays.fill(parent, -1); parent[start] = start;
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>(); queue.add(start);
        while (!queue.isEmpty() && parent[end] < 0) {
            int current = queue.remove();
            for (int next : graph.get(current)) if (parent[next] < 0) {
                parent[next] = current; queue.add(next);
            }
        }
        if (parent[end] < 0) return null;
        List<Integer> path = new ArrayList<Integer>();
        for (int at = end;; at = parent[at]) { path.add(at); if (at == start) break; }
        Collections.reverse(path);
        UserChainProof result=new UserChainProof();
        for(int literal:path){result.getNodes().add(ids.get(literal/2).copy());result.getTruths().add(literal%2==1);}
        return result;
    }
}
