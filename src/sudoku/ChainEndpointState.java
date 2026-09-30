/* Copyright (C) 2026 HoDoKu contributors. GPL-3.0-or-later. */
package sudoku;
import java.util.*;

/** Whole authored topology, oriented by the actual continuation rather than graph sort order. */
final class ChainEndpointState {
    enum Kind { EMPTY, SINGLE, OPEN, CLOSED, DISCONNECTED, BRANCHED, CONFLICT, INTERIOR }
    final Kind kind;
    final UserChainNode start,end,previous;
    private ChainEndpointState(Kind kind,UserChainNode start,UserChainNode end,UserChainNode previous){this.kind=kind;this.start=start;this.end=end;this.previous=previous;}
    static ChainEndpointState from(List<UserChain> chains){
        UserChain latest=null;Set<String> keys=new HashSet<>();
        for(UserChain c:chains){if(latest==null&&!c.getNodes().isEmpty())latest=c;for(UserChainNode n:c.getNodes())keys.add(n.key());}
        if(latest==null)return new ChainEndpointState(Kind.EMPTY,null,null,null);
        UserChainNode tail=latest.getNodes().get(latest.getNodes().size()-1);
        UserChainAssembly.Result assembled=UserChainAssembly.assemble(chains);
        if(assembled.problem!=UserChainValidator.Problem.NONE){
            Kind k=assembled.problem==UserChainValidator.Problem.DISCONNECTED_INPUT?Kind.DISCONNECTED:assembled.problem==UserChainValidator.Problem.BRANCHED_INPUT?Kind.BRANCHED:Kind.CONFLICT;
            return new ChainEndpointState(k,null,null,null);
        }
        if(keys.size()==1)return new ChainEndpointState(Kind.SINGLE,null,tail,null);
        UserChain path=assembled.chain;if(path==null)return new ChainEndpointState(Kind.EMPTY,null,null,null);
        List<UserChainNode> nodes=path.getNodes();
        if(path.isClosed())return new ChainEndpointState(Kind.CLOSED,latest.getNodes().get(0),null,null);
        int last=nodes.size()-1;
        if(tail.key().equals(nodes.get(last).key()))return new ChainEndpointState(Kind.OPEN,nodes.get(0),tail,nodes.get(last-1));
        if(tail.key().equals(nodes.get(0).key()))return new ChainEndpointState(Kind.OPEN,nodes.get(last),tail,nodes.get(1));
        return new ChainEndpointState(Kind.INTERIOR,null,null,null);
    }
    String problem(){switch(kind){case DISCONNECTED:return "多段未连接";case BRANCHED:return "存在分叉";case CONFLICT:return "链关系冲突";case INTERIOR:return "请从端点续画";default:return "";}}
}
