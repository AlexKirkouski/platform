package lsfusion.client.form;

import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.KeyStrokes;
import lsfusion.interop.form.ServerResponse;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;

import static lsfusion.interop.KeyStrokes.getKeyStrokeForEvent;

public class EditBindingMap {
    private Map<KeyStroke, String> keyBindingMap = new HashMap<>();
    private LinkedHashMap<String, String> contextMenuBindingMap = new LinkedHashMap<>();

    //пока одно значение, возможно в будущем расширится до мэпа (типа клик, дабл клик и т. д.)
    private String mouseBinding;

    public EditBindingMap() {
        setMouseAction(ServerResponse.CHANGE);
    }

    public String getAction(EventObject editEvent, EditEventFilter editEventFilter, boolean hasEditAction) {
        if (editEvent instanceof KeyEvent) {
            if (hasEditAction && KeyStrokes.isEditObjectEvent(editEvent)) {
                return ServerResponse.EDIT_OBJECT;
            }

            if (!KeyStrokes.isSuitableEditKeyEvent(editEvent)) {
                return null;
            }
        } else if (editEvent instanceof MouseEvent) {
            return mouseBinding;
        } else if (editEvent instanceof InternalEditEvent) {
            return ((InternalEditEvent) editEvent).action;
        }

        if (editEventFilter != null && !editEventFilter.accept(editEvent)) {
            return null;
        }

        return ServerResponse.CHANGE;
    }

    public String getKeyPressAction(KeyStroke keyStroke) {
        return keyBindingMap.get(keyStroke);
    }

    public String getKeyPressAction(EventObject editEvent) {
        if (editEvent instanceof KeyEvent) {
            return getKeyPressAction(getKeyStrokeForEvent((KeyEvent) editEvent));
        }
        return null;
    }

    public void setKeyAction(KeyStroke keyStroke, String actionSID) {
        keyBindingMap.put(keyStroke, actionSID);
    }

    public Map<KeyStroke, String> getKeyBindingMap() {
        return Collections.unmodifiableMap(keyBindingMap);
    }

    public void setContextMenuAction(String actionSID, String caption) {
        contextMenuBindingMap.put(actionSID, caption);
    }

    public LinkedHashMap<String, String> getContextMenuItems() {
        return contextMenuBindingMap;
    }

    public void setMouseAction(String actionSID) {
        mouseBinding = actionSID;
    }

    public String getMouseAction() {
        return mouseBinding;
    }

    public static boolean isEditableAwareEditEvent(String actionSID) {
        return ServerResponse.CHANGE.equals(actionSID)
                || ServerResponse.CHANGE_WYS.equals(actionSID)
                || ServerResponse.EDIT_OBJECT.equals(actionSID)
                || ServerResponse.GROUP_CHANGE.equals(actionSID);
    }

    public static String getPropertyEditActionSID(EventObject e, ClientPropertyDraw property, EditBindingMap overrideMap) {
        EditEventFilter eventFilter = property.changeType == null ? null : property.changeType.getEditEventFilter();

        String actionSID = null;
        if (property.editBindingMap != null) {
            actionSID = property.editBindingMap.getAction(e, eventFilter, property.hasEditObjectAction);
        }

        if (actionSID == null) {
            actionSID = overrideMap.getKeyPressAction(e);
            if (actionSID == null) {
                actionSID = overrideMap.getAction(e, eventFilter, property.hasEditObjectAction);
            }
        }
        return actionSID;
    }

    public static String getPropertyKeyPressActionSID(EventObject e, ClientPropertyDraw property) {
        if (property.editBindingMap != null) {
            return property.editBindingMap.getKeyPressAction(e);
        }
        return null;
    }

    public static String getPropertyKeyPressActionSID(KeyStroke keyStroke, ClientPropertyDraw property) {
        if (property.editBindingMap != null) {
            return property.editBindingMap.getKeyPressAction(keyStroke);
        }
        return null;
    }

    public interface EditEventFilter {
        boolean accept(EventObject e);
    }
}
