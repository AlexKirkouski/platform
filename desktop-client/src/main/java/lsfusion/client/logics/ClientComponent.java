package lsfusion.client.logics;

import lsfusion.base.context.ContextIdentityObject;
import lsfusion.base.serialization.IdentitySerializable;
import lsfusion.client.serialization.ClientSerializationPool;
import lsfusion.interop.ComponentDesign;
import lsfusion.interop.form.layout.AbstractComponent;
import lsfusion.interop.form.layout.FlexAlignment;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static javax.swing.BorderFactory.createCompoundBorder;
import static javax.swing.BorderFactory.createEmptyBorder;

public abstract class ClientComponent extends ContextIdentityObject implements IdentitySerializable<ClientSerializationPool>, AbstractComponent {

    public ComponentDesign design;

    public ClientContainer container;
    
    public Dimension size;
    
    public boolean autoSize;

    public double flex;
    public FlexAlignment alignment;

    public int marginTop;
    public int marginBottom;
    public int marginLeft;
    public int marginRight;

    public boolean defaultComponent;

    public ClientComponent() {
    }

    public void customSerialize(ClientSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        pool.writeObject(outStream, design);
        pool.serializeObject(outStream, container);

        pool.writeObject(outStream, size);
        
        outStream.writeBoolean(autoSize);

        outStream.writeDouble(flex);
        pool.writeObject(outStream, alignment);
        outStream.writeInt(marginTop);
        outStream.writeInt(marginBottom);
        outStream.writeInt(marginLeft);
        outStream.writeInt(marginRight);

        outStream.writeBoolean(defaultComponent);

        pool.writeString(outStream, sID);
    }

    public void customDeserialize(ClientSerializationPool pool, DataInputStream inStream) throws IOException {
        design = pool.readObject(inStream);

        container = pool.deserializeObject(inStream);

        size = pool.readObject(inStream);
        
        autoSize = inStream.readBoolean();

        flex = inStream.readDouble();
        alignment = pool.readObject(inStream);
        marginTop = inStream.readInt();
        marginBottom = inStream.readInt();
        marginLeft = inStream.readInt();
        marginRight = inStream.readInt();

        defaultComponent = inStream.readBoolean();

        sID = pool.readString(inStream);
    }

    public double getFlex() {
        return flex;
    }

    public void setFlex(double flex) {
        this.flex = flex;
        updateDependency(this, "flex");
    }

    public FlexAlignment getAlignment() {
        return alignment;
    }

    public void setAlignment(FlexAlignment alignment) {
        this.alignment = alignment;
        updateDependency(this, "alignment");
    }

    public int getMarginTop() {
        return marginTop;
    }

    public void setMarginTop(int marginTop) {
        this.marginTop = marginTop;
        updateDependency(this, "marginTop");
    }

    public int getMarginBottom() {
        return marginBottom;
    }

    public void setMarginBottom(int marginBottom) {
        this.marginBottom = marginBottom;
        updateDependency(this, "marginBottom");
    }

    public int getMarginLeft() {
        return marginLeft;
    }

    public void setMarginLeft(int marginLeft) {
        this.marginLeft = marginLeft;
        updateDependency(this, "marginLeft");
    }

    public int getMarginRight() {
        return marginRight;
    }

    public void setMarginRight(int marginRight) {
        this.marginRight = marginRight;
        updateDependency(this, "marginRight");
    }

    public void setMargin(int margin) {
        setMarginTop(margin);
        setMarginBottom(margin);
        setMarginLeft(margin);
        setMarginRight(margin);
    }

    public void installMargins(JComponent view) {
        if (marginTop != 0 || marginLeft != 0 || marginBottom != 0 || marginRight != 0) {
            Border marginBorder = createEmptyBorder(marginTop, marginLeft, marginBottom, marginRight);
            Border originalBorder = view.getBorder();
            view.setBorder(createCompoundBorder(marginBorder, originalBorder));
        }
    }

    public abstract String getCaption();

    public int getVerticalMargin() {
        return marginTop + marginBottom;
    }

    public int getHorizontalMargin() {
        return marginLeft + marginRight;
    }
}
