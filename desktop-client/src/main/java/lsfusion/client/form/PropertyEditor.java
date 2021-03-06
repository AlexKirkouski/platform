package lsfusion.client.form;

import lsfusion.client.form.cell.PropertyTableCellEditor;

import java.awt.*;
import java.util.EventObject;

public interface PropertyEditor {

    void setTableEditor(PropertyTableCellEditor tableEditor);

    Component getComponent(Point tableLocation, Rectangle cellRectangle, EventObject editEvent);

    Object getCellEditorValue();

    boolean stopCellEditing();
}


