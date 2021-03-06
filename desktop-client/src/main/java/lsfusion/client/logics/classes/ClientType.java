package lsfusion.client.logics.classes;

import lsfusion.client.form.ClientFormController;
import lsfusion.client.form.EditBindingMap;
import lsfusion.client.form.PropertyEditor;
import lsfusion.client.form.PropertyRenderer;
import lsfusion.client.form.cell.PanelView;
import lsfusion.client.logics.ClientGroupObjectValue;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.Compare;

import java.awt.*;
import java.io.IOException;
import java.text.ParseException;

public interface ClientType {

    PropertyRenderer getRendererComponent(ClientPropertyDraw property);

    PanelView getPanelView(ClientPropertyDraw key, ClientGroupObjectValue columnKey, ClientFormController form);

    PropertyEditor getChangeEditorComponent(Component ownerComponent, ClientFormController form, ClientPropertyDraw property, Object value);

    PropertyEditor getObjectEditorComponent(Component ownerComponent, ClientFormController form, ClientPropertyDraw property, Object value) throws IOException, ClassNotFoundException;

    PropertyEditor getValueEditorComponent(ClientFormController form, ClientPropertyDraw property, Object value);

    Object parseString(String s) throws ParseException;

    String formatString(Object obj) throws ParseException;

    boolean shouldBeDrawn(ClientFormController form);
    
    String getConfirmMessage();

    ClientTypeClass getTypeClass();

    Compare[] getFilterCompares();

    Compare getDefaultCompare();

    EditBindingMap.EditEventFilter getEditEventFilter();

    // добавляет поправку на кнопки и другие элементы 
    int getFullWidthString(String widthString, FontMetrics fontMetrics, ClientPropertyDraw propertyDraw);

    int getDefaultWidth(FontMetrics fontMetrics, ClientPropertyDraw property);

    int getDefaultHeight(FontMetrics fontMetrics);    
}
