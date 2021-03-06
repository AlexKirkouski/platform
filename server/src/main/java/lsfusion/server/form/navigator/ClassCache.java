package lsfusion.server.form.navigator;

import lsfusion.base.OrderedMap;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.classes.CustomClass;

import java.util.Map;

class ClassCache extends OrderedMap<ConcreteCustomClass, Long> {

    public ClassCache() {
    }

    public ClassCache(ClassCache classCache) {
        super(classCache);
    }

    public Long put(ConcreteCustomClass cls, Long value) {

        if (cls == null) {
            throw new RuntimeException("Unable to put null key to cache");
        }

        if (containsKey(cls)) remove(cls);

        if (value != null)
            return super.put(cls, value);
        else
            return null;
    }

    public Long getObject(CustomClass cls) {

        Long objectID = null;
        for (Map.Entry<ConcreteCustomClass,Long> entry : entrySet())
            if (entry.getKey().isChild(cls))
                objectID = entry.getValue();

        return objectID;
    }
}
