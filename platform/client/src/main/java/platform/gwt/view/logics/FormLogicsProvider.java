package platform.gwt.view.logics;

import platform.gwt.view.GGroupObject;
import platform.gwt.view.GPropertyDraw;
import platform.gwt.view.changes.GGroupObjectValue;
import platform.gwt.view.changes.dto.ObjectDTO;

public interface FormLogicsProvider {
    boolean isEditingEnabled();

    void changeGroupObject(GGroupObject group, GGroupObjectValue key);
    void executeEditAction(GPropertyDraw property, String actionSID);
    void executeEditAction(GPropertyDraw property, GGroupObjectValue key, String actionSID);
    void changeProperty(GPropertyDraw property, ObjectDTO value);
    void selectObject(GPropertyDraw property, SelectObjectCallback selectObjectCallback);
}
