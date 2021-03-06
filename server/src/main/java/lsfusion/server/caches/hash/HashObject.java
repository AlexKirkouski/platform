package lsfusion.server.caches.hash;

import lsfusion.server.caches.AbstractHashContext;

import java.util.IdentityHashMap;

public abstract class HashObject {

    public abstract boolean isGlobal();

    private IdentityHashMap<AbstractHashContext, Integer> caches;
    public Integer aspectGetCache(AbstractHashContext context) {
        if(caches==null)
             caches = new IdentityHashMap<>();
        return caches.get(context);
    }
    public void aspectSetCache(AbstractHashContext context, Integer result) {
        if(caches==null)
             caches = new IdentityHashMap<>();
        caches.put(context, result);
    }

}
