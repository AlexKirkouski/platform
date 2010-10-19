package platform.server.logics.property.group;

import platform.server.classes.ConcreteCustomClass;
import platform.server.classes.ValueClass;
import platform.server.logics.property.Property;
import platform.server.logics.linear.LP;
import platform.server.caches.IdentityLazy;
import platform.server.serialization.ServerIdentitySerializable;
import platform.server.serialization.ServerSerializationPool;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AbstractGroup extends AbstractNode implements ServerIdentitySerializable {


    public String caption;
    public boolean createContainer = true;
    private int ID;

    public AbstractGroup(int iID, String caption) {
        this.caption = caption;
        this.ID = iID;
    }

    public AbstractGroup(String caption) {
        this.ID = IDShift();
        this.caption = caption;
    }

    private static int currentID = 0;
    private int IDShift() {
        return currentID++;
    }

    List<AbstractNode> children = new ArrayList<AbstractNode>();
    public void add(AbstractNode prop) {
        children.add(prop);
        prop.parent = this;
    }

    protected void setChildOrder(LP<?> child, LP<?> childRel, boolean before) {
        setChildOrder(child.property, childRel.property, before);
    }

    protected void setChildOrder(Property child, Property childRel, boolean before) {

        int indProp = children.indexOf(child);
        int indPropRel = children.indexOf(childRel);

        if (before) {
            if (indPropRel < indProp) {
                for (int i = indProp; i >= indPropRel + 1; i--)
                    children.set(i, children.get(i - 1));
                children.set(indPropRel, child);
            }
        }
    }


    @IdentityLazy
    public List<ConcreteCustomClass> getClasses() {
        List<ConcreteCustomClass> result = new ArrayList<ConcreteCustomClass>();
        for (AbstractNode child : children)
            result.addAll(child.getClasses());
        return result;
    }

    public boolean hasChild(Property prop) {
        for (AbstractNode child : children)
            if(child.hasChild(prop))
                return true;
        return false;
    }

    @IdentityLazy
    public List<Property> getProperties(ValueClass[] classes) {
        List<Property> result = new ArrayList<Property>();
        for (AbstractNode child : children)
            result.addAll(child.getProperties(classes));
        return result;
    }

    public int getID() {
        return ID;
    }

    public void customSerialize(ServerSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        pool.serializeObject(outStream, getParent());

        List<ServerIdentitySerializable> serializableChildren = new ArrayList<ServerIdentitySerializable>();
        for (AbstractNode child : children) {
            if (child instanceof ServerIdentitySerializable) {
                serializableChildren.add((ServerIdentitySerializable) child);
            }
        }

        pool.serializeCollection(outStream, serializableChildren);
    }

    public void customDeserialize(ServerSerializationPool pool, int iID, DataInputStream inStream) throws IOException {
        //todo:

    }
}
