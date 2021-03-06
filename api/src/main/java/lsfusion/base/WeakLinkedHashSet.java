package lsfusion.base;

import java.util.*;

public class WeakLinkedHashSet<L> {
    
    private long maxIndex = 0;
    private WeakHashMap<L, Long> map = new WeakHashMap<>();

    public void add(L item) {
        if(!map.containsKey(item))
            map.put(item, maxIndex++);
    }

    public void addExcl(L item) {
        Long prevIndex = map.put(item, maxIndex++);
        assert prevIndex == null;
    }

    public void remove(L item) {
        map.remove(item);
    }
    
    public boolean isEmpty() {
        return map.isEmpty();
    }

    public L first() {
        L first = null;
        long minIndex = Long.MAX_VALUE;
        for(Map.Entry<L, Long> entry : map.entrySet()) {
            Long entryIndex = entry.getValue();
            if(entryIndex < minIndex) {
                first = entry.getKey();
                minIndex = entryIndex;
            }
        }
        
        return first;
    }
}
