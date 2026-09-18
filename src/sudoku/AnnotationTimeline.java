package sudoku;

import java.util.*;

/** Session-only ordering relative to the currently displayed proof. */
final class AnnotationTimeline {
    private String proof;
    private Map<String,String> previous = new HashMap<>();
    private final Set<String> later = new HashSet<>();
    void observe(String proof, Map<String,String> objects) {
        if (!Objects.equals(this.proof,proof)) {
            this.proof=proof;later.clear();previous=new HashMap<>(objects);return;
        }
        if(proof==null){later.clear();previous=new HashMap<>(objects);return;}
        for(Map.Entry<String,String> entry:objects.entrySet())
            if(!Objects.equals(previous.get(entry.getKey()),entry.getValue()))later.add(entry.getKey());
        later.retainAll(objects.keySet());previous=new HashMap<>(objects);
    }
    boolean isLater(String key){return proof!=null&&later.contains(key);}
}
