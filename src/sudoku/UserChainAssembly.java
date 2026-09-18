package sudoku;

import java.awt.Color;
import java.util.*;

/** Treats every authored segment as one graph, never selecting a convenient subchain. */
final class UserChainAssembly {
    static final class Result {
        final UserChain chain;
        final UserChainValidator.Problem problem;
        Result(UserChain chain, UserChainValidator.Problem problem) {this.chain=chain;this.problem=problem;}
    }
    private static final class Edge {
        final int a,b;final boolean strong;final Color color;
        Edge(int a,int b,boolean strong,Color color){this.a=a;this.b=b;this.strong=strong;this.color=color;}
        int other(int node){return node==a?b:a;}
    }
    static Result assemble(List<UserChain> segments) {
        Map<Integer,UserChainNode> nodes=new TreeMap<Integer,UserChainNode>();
        Map<Integer,List<Edge>> links=new TreeMap<Integer,List<Edge>>();
        Map<String,Edge> edges=new LinkedHashMap<String,Edge>();
        for(UserChain segment:segments) {
            int n=segment.getNodes().size();
            if(segment.getStrongRelations().size()!=Math.max(0,n-(segment.isClosed()?0:1)))return fail(UserChainValidator.Problem.RELATION_COUNT);
            for(UserChainNode node:segment.getNodes()) {if(node==null || !node.validShape())return fail(UserChainValidator.Problem.INVALID_GROUP);int key=key(node);nodes.put(key,node);if(!links.containsKey(key))links.put(key,new ArrayList<Edge>());}
            for(int i=0;i<segment.getStrongRelations().size();i++) {
                int a=key(segment.getNodes().get(i)),b=key(segment.getNodes().get((i+1)%n));
                if(a==b)return fail(UserChainValidator.Problem.DUPLICATE_NODE);
                String id=Math.min(a,b)+":"+Math.max(a,b);
                Boolean strong=segment.getStrongRelations().get(i);
                if(strong==null)return fail(UserChainValidator.Problem.RELATION_COUNT);
                Edge old=edges.get(id);
                if(old!=null){if(old.strong!=strong)return fail(UserChainValidator.Problem.CONFLICTING_EDGE);continue;}
                Edge edge=new Edge(a,b,strong,i<segment.getRelationColors().size()?segment.getRelationColors().get(i):null);
                edges.put(id,edge);links.get(a).add(edge);links.get(b).add(edge);
            }
        }
        if(nodes.size()<2)return new Result(null,UserChainValidator.Problem.NONE);
        int endpoints=0,start=nodes.keySet().iterator().next();
        for(Map.Entry<Integer,List<Edge>> entry:links.entrySet()) {
            if(entry.getValue().size()>2)return fail(UserChainValidator.Problem.BRANCHED_INPUT);
            if(entry.getValue().isEmpty())return fail(UserChainValidator.Problem.DISCONNECTED_INPUT);
            if(entry.getValue().size()==1){if(endpoints==0)start=entry.getKey();endpoints++;}
        }
        if(endpoints!=0&&endpoints!=2)return fail(UserChainValidator.Problem.DISCONNECTED_INPUT);
        UserChain chain=new UserChain();chain.setSourceId(-1L);chain.setClosed(endpoints==0);
        Set<Edge> used=new HashSet<Edge>();Set<Integer> visited=new HashSet<Integer>();int current=start;
        while(visited.add(current)) {
            UserChainNode node=nodes.get(current);chain.getNodes().add(node.copy());
            Edge next=null;for(Edge edge:links.get(current))if(!used.contains(edge)){next=edge;break;}
            if(next==null)break;
            used.add(next);chain.getStrongRelations().add(next.strong);chain.getRelationColors().add(next.color);current=next.other(current);
        }
        if(used.size()!=edges.size()||visited.size()!=nodes.size())return fail(UserChainValidator.Problem.DISCONNECTED_INPUT);
        return new Result(chain,UserChainValidator.Problem.NONE);
    }
    private static int key(UserChainNode node){return node.identity();}
    private static Result fail(UserChainValidator.Problem problem){return new Result(null,problem);}
}
