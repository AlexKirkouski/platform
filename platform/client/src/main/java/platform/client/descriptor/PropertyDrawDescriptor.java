package platform.client.descriptor;

import platform.base.BaseUtils;
import platform.client.Main;
import platform.client.descriptor.increment.IncrementDependency;
import platform.client.logics.ClientGroupObject;
import platform.client.logics.ClientPropertyDraw;
import platform.client.serialization.ClientIdentitySerializable;
import platform.client.serialization.ClientSerializationPool;
import platform.interop.ClassViewType;
import platform.interop.ClassViewTypeEnum;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PropertyDrawDescriptor extends IdentityDescriptor implements ClientIdentitySerializable {
    public ClientPropertyDraw client = new ClientPropertyDraw();

    private PropertyObjectDescriptor propertyObject;

    public PropertyDrawDescriptor() {
    }

    public PropertyDrawDescriptor(PropertyObjectDescriptor propertyObject) {
        setID(Main.generateNewID());
        setPropertyObject(propertyObject);
    }

    //todo: временно public...
    public GroupObjectDescriptor toDraw;
    private boolean shouldBeLast;
    private ClassViewTypeEnum forceViewType;

    private PropertyObjectDescriptor propertyCaption;

    private List<GroupObjectDescriptor> columnGroupObjects = new ArrayList<GroupObjectDescriptor>();

    public void setPropertyObject(PropertyObjectDescriptor propertyObject) { // usage через reflection
        this.propertyObject = propertyObject;
        if (propertyObject != null) {
            client.caption = propertyObject.property.caption;
        }
        IncrementDependency.update(this, "propertyObject");
    }

    public PropertyObjectDescriptor getPropertyObject() {
        return propertyObject;
    }

    public void setToDraw(GroupObjectDescriptor toDraw) { // usage через reflection
        this.toDraw = toDraw;
        IncrementDependency.update(this, "toDraw");
    }

    public GroupObjectDescriptor getToDraw() {
        return toDraw;
    }

    public void setShouldBeLast(boolean shouldBeLast) {
        this.shouldBeLast = shouldBeLast;
    }

    public boolean getShouldBeLast() {
        return shouldBeLast;
    }

    public void setForceViewType(String forceViewType) {
        this.forceViewType = ClassViewTypeEnum.valueOf(forceViewType);
        IncrementDependency.update(this, "forceViewType");
    }

    public ClassViewTypeEnum getForceViewType() {
        return forceViewType;
    }

    public GroupObjectDescriptor getGroupObject(List<GroupObjectDescriptor> groupList) {
        if (toDraw != null) {
            return toDraw;
        } else {
            return propertyObject != null
                   ? propertyObject.getGroupObject(groupList)
                   : null;
        }
    }

    public List<GroupObjectDescriptor> getUpGroupObjects(List<GroupObjectDescriptor> groupList) {
        if (getPropertyObject() == null) {
            return new ArrayList<GroupObjectDescriptor>();
        }

        List<GroupObjectDescriptor> groupObjects = getPropertyObject().getGroupObjects(groupList);
        if (toDraw == null) {
            if (groupObjects.size() > 0) {
                return groupObjects.subList(0, groupObjects.size() - 1);
            } else {
                return groupObjects;
            }
        } else {
            return BaseUtils.removeList(groupObjects, Collections.singleton(toDraw));
        }
    }

    public List<GroupObjectDescriptor> getColumnGroupObjects() { // usage через reflection
        return columnGroupObjects;
    }

    public void setColumnGroupObjects(List<GroupObjectDescriptor> columnGroupObjects) {
        this.columnGroupObjects = columnGroupObjects;

        client.columnGroupObjects = new ArrayList<ClientGroupObject>();
        for (GroupObjectDescriptor group : columnGroupObjects) {
            client.columnGroupObjects.add(group.client);
        }
        
        IncrementDependency.update(this, "columnGroupObjects");
    }

    public PropertyObjectDescriptor getPropertyCaption() { // usage через reflection
        return propertyCaption;
    }

    public void setPropertyCaption(PropertyObjectDescriptor propertyCaption) {
        this.propertyCaption = propertyCaption;
        IncrementDependency.update(this, "propertyCaption");
    }

    public void setOverridenCaption(String caption) { // usage через reflection
        client.overridenCaption = caption;
        IncrementDependency.update(this, "overridenCaption");
    }

    public String getOverridenCaption() {
        return client.overridenCaption;
    }

    @Override
    public String toString() {
        return client.getResultingCaption();
    }

    @Override
    public void setID(int ID) {
        super.setID(ID);
        client.setID(ID);
    }

    public void customSerialize(ClientSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        pool.serializeObject(outStream, propertyObject);
        pool.serializeObject(outStream, toDraw);
        pool.serializeCollection(outStream, columnGroupObjects);
        pool.serializeObject(outStream, propertyCaption);

        outStream.writeBoolean(shouldBeLast);
        outStream.writeBoolean(forceViewType != null);
        if (forceViewType != null) {
            if(forceViewType == ClassViewTypeEnum.valueOf("Panel")){
                outStream.writeByte(ClassViewType.PANEL);
            }
            else if(forceViewType == ClassViewTypeEnum.valueOf("Grid")){
                outStream.writeByte(ClassViewType.GRID);
            }
            else if(forceViewType == ClassViewTypeEnum.valueOf("Hide")){
                outStream.writeByte(ClassViewType.HIDE);
            }                                                   
        }
    }

    public void customDeserialize(ClientSerializationPool pool, DataInputStream inStream) throws IOException {
        propertyObject = (PropertyObjectDescriptor) pool.deserializeObject(inStream);
        toDraw = (GroupObjectDescriptor) pool.deserializeObject(inStream);
        columnGroupObjects = pool.deserializeList(inStream);
        propertyCaption = (PropertyObjectDescriptor) pool.deserializeObject(inStream);

        shouldBeLast = inStream.readBoolean();
        if (inStream.readBoolean()) {
            Byte type = inStream.readByte();
            if(type == ClassViewType.PANEL){
                forceViewType = ClassViewTypeEnum.valueOf("Panel");
            }
            if(type == ClassViewType.GRID){
                forceViewType = ClassViewTypeEnum.valueOf("Grid");
            }
            if(type == ClassViewType.HIDE){
                forceViewType = ClassViewTypeEnum.valueOf("Hide");
            }
        }

        client = pool.context.getProperty(ID);
    }
}
