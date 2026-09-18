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
        UserChainNode[] nodes = chain.getNodes().toArray(new UserChainNode[n]);
        for (int i = 0; i < n; i++) {
            UserChainNode node = nodes[i];
            if (!node.validShape()) return new Result(Status.INVALID, -1, steps, Problem.INVALID_GROUP);
            for (int cell : node.cells()) {
                int digit=node.getCandidate();
                if(board.getValue(cell)!=0 || !board.isCandidate(cell,digit))
                    return new Result(Status.INVALID,-1,steps,Problem.MISSING_CANDIDATE);
                if(!seen.add(cell*10+digit)) return new Result(Status.INVALID,-1,steps,Problem.DUPLICATE_NODE);
            }
        }
        Result invalid = null;
        for (int i=0;i<edges;i++) {
            Boolean strong=chain.getStrongRelations().get(i);
            if(strong==null || !(strong ? strong(board,nodes[i],nodes[(i+1)%n]) : weak(nodes[i],nodes[(i+1)%n]))) {
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
            int[] negativeProof = proof(chain, nodes, target, true);
            if (negativeProof != null) {
                deletions.addCandidateToDelete(cell, digit);
                deletions.addChain(0, negativeProof.length - 1, negativeProof);
            }
            if (seen.contains(target)) {
                int[] positiveProof = proof(chain, nodes, target, false);
                if (positiveProof != null) {
                    SolutionStep placement = new SolutionStep(type);
                    placement.addIndex(cell);placement.addValue(digit);placement.setAuthoredPlacement(true);
                    placement.addChain(0, positiveProof.length - 1, positiveProof);
                    if (!placement.getCandidatesToDelete().isEmpty() || !placement.getValues().isEmpty()) steps.add(placement);
                }
            }
        }
        if (!deletions.getCandidatesToDelete().isEmpty()) steps.add(deletions);
        Result result=new Result(steps.isEmpty() ? (invalid==null?Status.NO_CONCLUSION:Status.INVALID) : (invalid==null?Status.PROVEN:Status.ASSUMED), invalid==null?-1:invalid.invalidEdge, steps,invalid==null?Problem.NONE:invalid.problem);
        if(invalid!=null)result.invalidRelations.addAll(invalid.invalidRelations);
        return result;
    }
    private static SolutionType classify(Sudoku2 board,UserChain chain) {
        if (!chain.isClosed()) {
            boolean alternating=true,sameDigit=true,grouped=false,xy=true;
            int d=chain.getNodes().get(0).getCandidate();
            for(UserChainNode node:chain.getNodes()){sameDigit &= node.getCandidate()==d;grouped |= node.grouped();}
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
        int x=a.identity(), y=b.identity(); return Math.min(x,y)+":"+Math.max(x,y)+":"+strong;
    }
    static boolean weak(UserChainNode a, UserChainNode b) {
        for(int x:a.cells())for(int y:b.cells())if(!weak(x*10+a.getCandidate(),y*10+b.getCandidate()))return false;
        return true;
    }
    static boolean strong(Sudoku2 board, UserChainNode a, UserChainNode b) {
        if(!a.grouped() && !b.grouped())return strong(board,a.getCellIndex()*10+a.getCandidate(),b.getCellIndex()*10+b.getCandidate());
        if(!weak(a,b) || a.getCandidate()!=b.getCandidate())return false;
        Set<Integer> covered=new HashSet<Integer>();
        for(int x:a.cells())covered.add(x);for(int x:b.cells())covered.add(x);
        for(int kind=0;kind<3;kind++) {
            int u=unit(a.cells()[0],kind);boolean shared=true;
            for(int x:covered)if(unit(x,kind)!=u)shared=false;
            if(!shared)continue;
            boolean complete=true;
            for(int x=0;x<81;x++)if(unit(x,kind)==u && board.getValue(x)==0 && board.isCandidate(x,a.getCandidate()) && !covered.contains(x))complete=false;
            if(complete)return true;
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
    private static int[] proof(UserChain chain, UserChainNode[] nodes, int target, boolean assumption) {
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
        int[] encoded = new int[path.size()];
        for (int i = 0; i < encoded.length; i++) {
            int literal = path.get(i);
            encoded[i] = ids.get(literal / 2).encoded(literal % 2 == 1);
        }
        return encoded;
    }
}
