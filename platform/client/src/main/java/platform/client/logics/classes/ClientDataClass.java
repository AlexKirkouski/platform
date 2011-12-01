package platform.client.logics.classes;

import platform.base.BaseUtils;
import platform.client.ClientResourceBundle;
import platform.client.form.ClientFormController;
import platform.client.form.PropertyEditorComponent;
import platform.client.form.cell.CellView;
import platform.client.form.cell.TableCellView;
import platform.client.logics.ClientGroupObjectValue;
import platform.client.logics.ClientPropertyDraw;
import platform.gwt.view.classes.GStringType;
import platform.gwt.view.classes.GType;
import platform.interop.Compare;
import platform.interop.ComponentDesign;

import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.Format;

import static platform.interop.Compare.*;

public abstract class ClientDataClass extends ClientClass implements ClientType {

    protected ClientDataClass() {
    }

    ClientDataClass(DataInputStream inStream) throws IOException {
        super(inStream);
    }

    @Override
    public String getCode() {
        return getSID()+".instance";
    }

    @Override
    public void serialize(DataOutputStream outStream) throws IOException {
        outStream.writeByte(getTypeClass().getTypeId());
    }

    public boolean hasChildren() {
        return false;
    }

    public int getMinimumWidth(int minCharWidth, FontMetrics fontMetrics) {
        String minMask = minCharWidth != 0
                      ? BaseUtils.replicate('0', minCharWidth)
                      : getMinimumMask();

        return fontMetrics.stringWidth(minMask) + 8;
    }

    public int getPreferredWidth(int prefCharWidth, FontMetrics fontMetrics) {
        String prefMask = prefCharWidth != 0
                      ? BaseUtils.replicate('0', prefCharWidth)
                      : getPreferredMask();

        return fontMetrics.stringWidth(prefMask) + 8;
    }

    public int getMaximumWidth(int maxCharWidth, FontMetrics fontMetrics) {
        if (maxCharWidth != 0)
            return fontMetrics.stringWidth(BaseUtils.replicate('0', maxCharWidth)) + 8;
        else
            return Integer.MAX_VALUE;
    }

    @Override
    public int getPreferredHeight(FontMetrics fontMetrics) {
        return fontMetrics.getHeight() + 1;
    }

    @Override
    public int getMaximumHeight(FontMetrics fontMetrics) {
        return getPreferredHeight(fontMetrics);
    }

    public String getMinimumMask() {
        return getPreferredMask();
    }

    abstract public String getPreferredMask();

    protected abstract PropertyEditorComponent getComponent(Object value, Format format, ComponentDesign design);

    public CellView getPanelComponent(ClientPropertyDraw key, ClientGroupObjectValue columnKey, ClientFormController form) {
        return new TableCellView(key, columnKey, form);
    }

    public PropertyEditorComponent getEditorComponent(Component ownerComponent, ClientFormController form, ClientPropertyDraw property, Object value, Format format, ComponentDesign design) throws IOException, ClassNotFoundException {
        return getComponent(value, format, design);
    }

    public PropertyEditorComponent getObjectEditorComponent(Component ownerComponent, ClientFormController form, ClientPropertyDraw property, Object value, Format format, ComponentDesign design) throws IOException, ClassNotFoundException {
        return getEditorComponent(ownerComponent, form, property, value, format, design);
    }

    public PropertyEditorComponent getClassComponent(ClientFormController form, ClientPropertyDraw property, Object value, Format format) throws IOException, ClassNotFoundException {
        return getComponent(value, format, null);
    }

    public boolean shouldBeDrawn(ClientFormController form) {
        return true;
    }

    public String getConformedMessage() {
        return ClientResourceBundle.getString("logics.classes.do.you.really.want.to.edit.property");
    }

    public ClientType getType() {
        return this;
    }

    // за исключение классов динамической ширины - так как нету множественного наследования и не хочется каждому прописывать
    public ClientType getDefaultType() {
        return this;
    }

    public ClientClass getDefaultClass(ClientObjectClass baseClass) {
        return this;
    }

    public ClientTypeClass getTypeClass() {
        return (ClientTypeClass) this;
    }

    @Override
    public Compare[] getFilerCompares() {
        return new Compare[] {EQUALS, GREATER, LESS, GREATER_EQUALS, LESS_EQUALS, NOT_EQUALS};
    }

    @Override
    public Compare getDefaultCompare() {
        return EQUALS;
    }

    @Override
    public GType getGwtType() {
        return new GStringType();
    }
}
