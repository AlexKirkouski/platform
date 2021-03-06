package lsfusion.client.logics.classes;

import lsfusion.base.BaseUtils;
import lsfusion.client.ClientResourceBundle;
import lsfusion.client.form.ClientFormController;
import lsfusion.client.form.EditBindingMap;
import lsfusion.client.form.PropertyEditor;
import lsfusion.client.form.cell.DataPanelView;
import lsfusion.client.form.cell.PanelView;
import lsfusion.client.logics.ClientGroupObjectValue;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.Compare;

import java.awt.*;
import java.io.DataOutputStream;
import java.io.IOException;

import static lsfusion.interop.Compare.*;

public abstract class ClientDataClass extends ClientClass implements ClientType {

    protected ClientDataClass() {
    }

    @Override
    public void serialize(DataOutputStream outStream) throws IOException {
        outStream.writeByte(getTypeClass().getTypeId());
    }

    public boolean hasChildren() {
        return false;
    }

    // добавляет поправку на кнопки и другие элементы 
    public int getFullWidthString(String widthString, FontMetrics fontMetrics, ClientPropertyDraw propertyDraw) {
        return fontMetrics.stringWidth(widthString) + 8;
    }
    
    public int getDefaultWidth(FontMetrics fontMetrics, ClientPropertyDraw property) {
        return getFullWidthString(getDefaultWidthString(property), fontMetrics, property);
    }

    protected int getDefaultCharWidth() {
        return 0;
    }

    protected String getDefaultWidthString(ClientPropertyDraw propertyDraw) {
        int defaultCharWidth = getDefaultCharWidth();
        if(defaultCharWidth != 0)
            return BaseUtils.replicate('0', defaultCharWidth);
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDefaultHeight(FontMetrics fontMetrics) {
        return fontMetrics.getHeight() + 1;
    }

    public PanelView getPanelView(ClientPropertyDraw key, ClientGroupObjectValue columnKey, ClientFormController form) {
        return new DataPanelView(form, key, columnKey);
    }

    public PropertyEditor getChangeEditorComponent(Component ownerComponent, ClientFormController form, ClientPropertyDraw property, Object value) {
        return getDataClassEditorComponent(value, property);
    }

    public PropertyEditor getObjectEditorComponent(Component ownerComponent, ClientFormController form, ClientPropertyDraw property, Object value) throws IOException, ClassNotFoundException {
        return getDataClassEditorComponent(value, property);
    }

    public PropertyEditor getValueEditorComponent(ClientFormController form, ClientPropertyDraw property, Object value) {
        return getDataClassEditorComponent(value, property);
    }

    protected abstract PropertyEditor getDataClassEditorComponent(Object value, ClientPropertyDraw property);

    public boolean shouldBeDrawn(ClientFormController form) {
        return true;
    }

    public String getConfirmMessage() {
        return ClientResourceBundle.getString("logics.classes.do.you.really.want.to.edit.property");
    }

    // за исключение классов динамической ширины - так как нету множественного наследования и не хочется каждому прописывать
    @SuppressWarnings("UnusedDeclaration")
    public ClientType getDefaultType() {
        return this;
    }

    public ClientTypeClass getTypeClass() {
        return (ClientTypeClass) this;
    }

    @Override
    public Compare[] getFilterCompares() {
        return new Compare[] {EQUALS, GREATER, LESS, GREATER_EQUALS, LESS_EQUALS, NOT_EQUALS};
    }

    @Override
    public Compare getDefaultCompare() {
        return EQUALS;
    }

    @Override
    public EditBindingMap.EditEventFilter getEditEventFilter() {
        return null;
    }
}
