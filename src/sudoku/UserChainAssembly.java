package sudoku;

import java.awt.Color;
import java.util.*;

/** Treats every authored segment as one graph, never selecting a convenient subchain. */
final class UserChainAssembly {
    static final class Result {
        final UserChain chain;
        final UserChainValidator.Problem problem;
        final List<UserChainNode> componentAnchors;
        Result(UserChain chain, UserChainValidator.Problem problem) {this(chain,problem,Collections.<UserChainNode>emptyList());}
        Result(UserChain chain, UserChainValidator.Problem problem,List<UserChainNode> anchors) {
            this.chain=chain;this.problem=problem;this.componentAnchors=Collections.unmodifiableList(anchors);
        }
    }
    private static final class Edge {
        final String a,b;final boolean strong;final Color color;
        Edge(String a,String b,boolean strong,Color color){this.a=a;this.b=b;this.strong=strong;this.color=color;}
        String other(String node){return node.equals(a)?b:a;}
    }
    static Result assemble(List<UserChain> segments) {
        Map<String,UserChainNode> nodes=new TreeMap<String,UserChainNode>();
        Map<String,List<Edge>> links=new TreeMap<String,List<Edge>>();
        Map<String,Edge> edges=new LinkedHashMap<String,Edge>();
        for(UserChain segment:segments) {
            int n=segment.getNodes().size();
            if(segment.getStrongRelations().size()!=Math.max(0,n-(segment.isClosed()?0:1)))return fail(UserChainValidator.Problem.RELATION_COUNT);
            for(UserChainNode node:segment.getNodes()) {if(node==null || !node.validShape())return fail(UserChainValidator.Problem.INVALID_GROUP);String key=key(node);nodes.put(key,node);if(!links.containsKey(key))links.put(key,new ArrayList<Edge>());}
            for(int i=0;i<segment.getStrongRelations().size();i++) {
                String a=key(segment.getNodes().get(i)),b=key(segment.getNodes().get((i+1)%n));
                if(a.equals(b))return fail(UserChainValidator.Problem.DUPLICATE_NODE);
                String id=(a.compareTo(b)<0?a+":"+b:b+":"+a);
                Boolean strong=segment.getStrongRelations().get(i);
                if(strong==null)return fail(UserChainValidator.Problem.RELATION_COUNT);
                Edge old=edges.get(id);
                if(old!=null){if(old.strong!=strong)return fail(UserChainValidator.Problem.CONFLICTING_EDGE);continue;}
                Edge edge=new Edge(a,b,strong,i<segment.getRelationColors().size()?segment.getRelationColors().get(i):null);
                edges.put(id,edge);links.get(a).add(edge);links.get(b).add(edge);
            }
        }
        if(nodes.size()<2)return new Result(null,UserChainValidator.Problem.NONE);
        List<UserChainNode> anchors=new ArrayList<>();Set<String> connected=new HashSet<>();
        for(String seed:nodes.keySet())if(connected.add(seed)) {
            anchors.add(nodes.get(seed).copy());ArrayDeque<String> pending=new ArrayDeque<>();pending.add(seed);
            while(!pending.isEmpty())for(Edge edge:links.get(pending.remove())) {
                if(connected.add(edge.a))pending.add(edge.a);
                if(connected.add(edge.b))pending.add(edge.b);
            }
        }
        if(anchors.size()>1)return new Result(null,UserChainValidator.Problem.DISCONNECTED_INPUT,anchors);
        int endpoints=0;String start=nodes.keySet().iterator().next();
        for(Map.Entry<String,List<Edge>> entry:links.entrySet()) {
            if(entry.getValue().size()>2)return fail(UserChainValidator.Problem.BRANCHED_INPUT);
            if(entry.getValue().isEmpty())return fail(UserChainValidator.Problem.DISCONNECTED_INPUT);
            if(entry.getValue().size()==1){if(endpoints==0)start=entry.getKey();endpoints++;}
        }
        if(endpoints!=0&&endpoints!=2)return fail(UserChainValidator.Problem.DISCONNECTED_INPUT);
        UserChain chain=new UserChain();chain.setSourceId(-1L);chain.setClosed(endpoints==0);
        Set<Edge> used=new HashSet<Edge>();Set<String> visited=new HashSet<String>();String current=start;
        while(visited.add(current)) {
            UserChainNode node=nodes.get(current);chain.getNodes().add(node.copy());
            Edge next=null;for(Edge edge:links.get(current))if(!used.contains(edge)){next=edge;break;}
            if(next==null)break;
            used.add(next);chain.getStrongRelations().add(next.strong);chain.getRelationColors().add(next.color);current=next.other(current);
        }
        if(used.size()!=edges.size()||visited.size()!=nodes.size())return fail(UserChainValidator.Problem.DISCONNECTED_INPUT);
        return new Result(chain,UserChainValidator.Problem.NONE);
    }
    /** Ignores orientation, colors, segment ownership and the next-edge setting. */
    static boolean sameStructure(UserChain a,UserChain b){
        return a!=null&&b!=null&&a.isClosed()==b.isClosed()&&structure(a).equals(structure(b));
    }
    private static Set<String> structure(UserChain c){
        Set<String> result=new TreeSet<>();
        for(UserChainNode n:c.getNodes())result.add("N:"+n.key());
        for(int i=0;i<c.getStrongRelations().size();i++){
            String a=c.getNodes().get(i).key(),b=c.getNodes().get((i+1)%c.getNodes().size()).key();
            result.add("E:"+(a.compareTo(b)<0?a+":"+b:b+":"+a)+":"+c.getStrongRelations().get(i));
        }
        return result;
    }
    private static String key(UserChainNode node){return node.key();}
    private static Result fail(UserChainValidator.Problem problem){return new Result(null,problem);}
}
