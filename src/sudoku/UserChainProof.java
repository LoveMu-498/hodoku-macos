/* Copyright (C) 2026 HoDoKu contributors. GPL-3.0-or-later. */
package sudoku;

import java.util.*;

/** A literal implication path with lossless OR nodes; native three-slot chains remain supported. */
public class UserChainProof {
    private List<UserChainNode> nodes=new ArrayList<>();
    private List<Boolean> truths=new ArrayList<>();
    public UserChainProof() {}
    public List<UserChainNode> getNodes(){return nodes;}
    public void setNodes(List<UserChainNode> value){nodes=value;}
    public List<Boolean> getTruths(){return truths;}
    public void setTruths(List<Boolean> value){truths=value;}
    public UserChainProof copy(){UserChainProof p=new UserChainProof();for(UserChainNode n:nodes)p.nodes.add(n.copy());p.truths.addAll(truths);return p;}
    public String key(){StringBuilder s=new StringBuilder();for(int i=0;i<nodes.size();i++)s.append(nodes.get(i).key()).append(truths.get(i)).append(';');return s.toString();}
    public boolean nativeEncodable(){for(UserChainNode n:nodes)if(!n.nativeEncodable())return false;return true;}
    public void addTo(SolutionStep step){
        if(nativeEncodable()){int[] entries=new int[nodes.size()];for(int i=0;i<entries.length;i++)entries[i]=nodes.get(i).encoded(truths.get(i));step.addChain(0,entries.length-1,entries);}
        else step.getGeneralizedProofs().add(copy());
    }
}
