package sudoku;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Removes hit relations and retains connected paths, including opened cycles. */
final class UserChainCuts {
    private UserChainCuts() {}
    static List<UserChain> removeEdges(UserChain original, Set<Integer> removed) {
        List<UserChain> result=new ArrayList<UserChain>();
        int n=original.getNodes().size(), edges=original.getStrongRelations().size();
        if(n<2)return result;
        int start=0;
        if(original.isClosed()) {
            for(int edge:removed)if(edge>=0&&edge<edges){start=(edge+1)%n;break;}
        }
        UserChain fragment=null;
        for(int offset=0;offset<edges;offset++) {
            int edge=(start+offset)%edges;
            if(removed.contains(edge)){fragment=null;continue;}
            int from=edge,to=(edge+1)%n;
            if(fragment==null){fragment=new UserChain();fragment.setAnalysisResult("PENDING_NATIVE_MATCH");fragment.getNodes().add(copy(original.getNodes().get(from)));result.add(fragment);}
            fragment.getNodes().add(copy(original.getNodes().get(to)));
            fragment.getStrongRelations().add(original.getStrongRelations().get(edge));
            fragment.getRelationColors().add(edge<original.getRelationColors().size()?original.getRelationColors().get(edge):null);
            fragment.setNextStrong(!original.getStrongRelations().get(edge));
            if(original.isActive()&&to==n-1)fragment.setActive(true);
        }
        return result;
    }
    static List<UserChain> removeMembersAndEdges(UserChain original,Set<Integer> edges,
            java.util.Map<Integer,Set<Integer>> members) {
        UserChain copy=new UserChain();copy.setClosed(original.isClosed());copy.setActive(original.isActive());copy.setNextStrong(original.isNextStrong());
        for(UserChainNode node:original.getNodes())copy.getNodes().add(node.copy());
        copy.getStrongRelations().addAll(original.getStrongRelations());copy.getRelationColors().addAll(original.getRelationColors());
        Set<Integer> removed=new java.util.HashSet<Integer>(edges);
        int n=copy.getNodes().size();
        for(int i=0;i<n;i++) {
            UserChainNode node=copy.getNodes().get(i);Set<Integer> hit=members.get(node.identity());
            if(hit==null)continue;
            java.util.List<Integer> keep=new ArrayList<Integer>();for(int c:node.cells())if(!hit.contains(c))keep.add(c);
            if(keep.isEmpty()) {
                if(n==1)return new ArrayList<UserChain>();
                if(i>0)removed.add(i-1);else if(copy.isClosed())removed.add(n-1);
                if(i<copy.getStrongRelations().size())removed.add(i);
            } else {
                UserChainNode next=new UserChainNode(keep.get(0),node.getCandidate(),node.getColor());
                if(keep.size()>1){int[] c=new int[keep.size()];for(int j=0;j<c.length;j++)c[j]=keep.get(j);next.setGroupCells(c);}
                copy.getNodes().set(i,next);
            }
        }
        if(removed.isEmpty())return new ArrayList<UserChain>(java.util.Collections.singletonList(copy));
        return removeEdges(copy,removed);
    }
    private static UserChainNode copy(UserChainNode node){return node.copy();}
}
