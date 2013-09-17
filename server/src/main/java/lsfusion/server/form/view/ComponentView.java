package lsfusion.server.form.view;

import lsfusion.base.identity.IdentityObject;
import lsfusion.interop.ComponentDesign;
import lsfusion.interop.form.layout.*;
import lsfusion.server.caches.IdentityLazy;
import lsfusion.server.serialization.ServerIdentitySerializable;
import lsfusion.server.serialization.ServerSerializationPool;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static java.lang.Math.max;

public class ComponentView extends IdentityObject implements ServerIdentitySerializable, AbstractComponent<ContainerView, ComponentView> {

    public ComponentDesign design = new ComponentDesign();

    protected ContainerView container;

    public Dimension minimumSize;
    public Dimension maximumSize;
    public Dimension preferredSize;

    public double flex = 0;
    public FlexAlignment alignment = FlexAlignment.LEADING;

    public int marginTop;
    public int marginBottom;
    public int marginLeft;
    public int marginRight;

    public boolean defaultComponent = false;

    public ComponentView() {
    }

    public ComponentView(int ID) {
        this.ID = ID;
    }

    public void setFixedSize(Dimension size) {
        minimumSize = size;
        maximumSize = size;
        preferredSize = size;
    }

    public void setFlex(double flex) {
        this.flex = flex;
    }

    public void setAlignment(FlexAlignment alignment) {
        this.alignment = alignment;
    }

    public void setMarginTop(int marginTop) {
        this.marginTop = max(0, marginTop);
    }

    public void setMarginBottom(int marginBottom) {
        this.marginBottom = max(0, marginBottom);
    }

    public void setMarginLeft(int marginLeft) {
        this.marginLeft = max(0, marginLeft);
    }

    public void setMarginRight(int marginRight) {
        this.marginRight = max(0, marginRight);
    }

    public void setMargin(int margin) {
        setMarginTop(margin);
        setMarginBottom(margin);
        setMarginLeft(margin);
        setMarginRight(margin);
    }

    public ComponentView findById(int id) {
        if(ID==id)
            return this;
        return null;
    }

    public ContainerView getContainer() {
        return container;
    }

    @IdentityLazy
    public ComponentView getTabContainer() {
        ContainerView parent = getContainer();
        if(parent == null)
            return null;
        if(parent.getType() == ContainerType.TABBED_PANE)
            return this;
        return parent.getTabContainer();
    }

    public boolean isAncestorOf(ComponentView container) {
        return equals(container);
    }

    void setContainer(ContainerView container) {
        this.container = container;
    }

    public boolean removeFromParent() {
        return container != null && container.remove(this);
    }

    public void customSerialize(ServerSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        pool.writeObject(outStream, design);
        pool.serializeObject(outStream, container, serializationType);

        pool.writeObject(outStream, minimumSize);
        pool.writeObject(outStream, maximumSize);
        pool.writeObject(outStream, preferredSize);

        outStream.writeDouble(flex);
        pool.writeObject(outStream, alignment);
        outStream.writeInt(marginTop);
        outStream.writeInt(marginBottom);
        outStream.writeInt(marginLeft);
        outStream.writeInt(marginRight);

        outStream.writeBoolean(defaultComponent);

        pool.writeString(outStream, sID);
    }

    public void customDeserialize(ServerSerializationPool pool, DataInputStream inStream) throws IOException {
        design = pool.readObject(inStream);

        container = pool.deserializeObject(inStream);

        minimumSize = pool.readObject(inStream);
        maximumSize = pool.readObject(inStream);
        preferredSize = pool.readObject(inStream);

        flex = inStream.readDouble();
        alignment = pool.readObject(inStream);
        marginTop = inStream.readInt();
        marginBottom = inStream.readInt();
        marginLeft = inStream.readInt();
        marginRight = inStream.readInt();

        defaultComponent = inStream.readBoolean();

        sID = pool.readString(inStream);
    }
}
