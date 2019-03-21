package lsfusion.client.classes;

import lsfusion.client.ClientResourceBundle;
import lsfusion.client.classes.data.ClientDataClass;
import lsfusion.client.form.controller.ClientFormController;
import lsfusion.client.form.object.ClientGroupObjectValue;
import lsfusion.client.form.property.ClientPropertyDraw;
import lsfusion.client.form.property.cell.classes.controller.ActionPropertyEditor;
import lsfusion.client.form.property.cell.classes.controller.PropertyEditor;
import lsfusion.client.form.property.cell.classes.view.ActionPropertyRenderer;
import lsfusion.client.form.property.cell.view.PropertyRenderer;
import lsfusion.client.form.property.panel.view.ActionPanelView;
import lsfusion.client.form.property.panel.view.PanelView;
import lsfusion.interop.classes.DataType;

import java.text.ParseException;

public class ClientActionClass extends ClientDataClass implements ClientTypeClass {
    public final static ClientActionClass instance = new ClientActionClass();

    private ClientActionClass() {
    }

    public byte getTypeId() {
        return DataType.ACTION;
    }

    @Override
    public String getDefaultWidthString(ClientPropertyDraw propertyDraw) {
        return "1234567";
    }

    public PropertyRenderer getRendererComponent(ClientPropertyDraw property) {
        return new ActionPropertyRenderer(property);
    }

    public PanelView getPanelView(ClientPropertyDraw key, ClientGroupObjectValue columnKey, ClientFormController form) {
        return new ActionPanelView(key, columnKey, form);
    }

    protected PropertyEditor getDataClassEditorComponent(Object value, ClientPropertyDraw property) {
        return new ActionPropertyEditor(property);
    }

    public Object parseString(String s) throws ParseException {
        throw new ParseException(ClientResourceBundle.getString("logics.classes.actionclass.doesnt.support.convertation.from.string"), 0);
    }

    @Override
    public String formatString(Object obj) throws ParseException {
        throw new ParseException(ClientResourceBundle.getString("logics.classes.actionclass.doesnt.support.convertation.from.string"), 0);
    }

    @Override
    public String getConfirmMessage() {
        return ClientResourceBundle.getString("logics.classes.do.you.really.want.to.take.action");
    }

    @Override
    public String toString() {
        return ClientResourceBundle.getString("logics.classes.actionclass");
    }
}
