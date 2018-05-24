package lsfusion.gwt.form.shared.view;

import com.google.gwt.dom.client.NativeEvent;
import lsfusion.gwt.base.client.ui.GKeyStroke;
import lsfusion.gwt.form.shared.view.grid.EditEvent;
import lsfusion.gwt.form.shared.view.grid.InternalEditEvent;
import lsfusion.gwt.form.shared.view.grid.NativeEditEvent;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static com.google.gwt.dom.client.BrowserEvents.CLICK;
import static com.google.gwt.dom.client.BrowserEvents.KEYDOWN;
import static lsfusion.gwt.base.client.ui.GKeyStroke.*;

public class GEditBindingMap implements Serializable {
    public static final String CHANGE = "change";
    public static final String GROUP_CHANGE = "groupChange";
    public static final String EDIT_OBJECT = "editObject";
    public static final String CHANGE_WYS = "change_wys";

    public interface EditEventFilter {
        boolean accept(NativeEvent e);
    }

    public static final transient EditEventFilter numberEventFilter = new GEditBindingMap.EditEventFilter() {
        @Override
        public boolean accept(NativeEvent e) {
            return isCommonNumberEditEvent(e);
        }
    };

    private HashMap<GKeyStroke, String> keyBindingMap;
    private LinkedHashMap<String, String> contextMenuBindingMap;
    private String mouseBinding;

    public GEditBindingMap() {
    }

    public GEditBindingMap(HashMap<GKeyStroke, String> keyBindingMap, LinkedHashMap<String, String> contextMenuBindingMap, String mouseBinding) {
        this.keyBindingMap = keyBindingMap;
        this.contextMenuBindingMap = contextMenuBindingMap;
        this.mouseBinding = mouseBinding;
    }

    public String getKeyPressAction(EditEvent editEvent) {
        NativeEvent nativeEvent = ((NativeEditEvent) editEvent).getNativeEvent();
        if (KEYDOWN.equals(nativeEvent.getType())) {
            return getKeyAction(getKeyStroke(nativeEvent));
        }
        return null;
    }

    public String getAction(EditEvent event, EditEventFilter editEventFilter, boolean hasEditAction) {
        if (event instanceof NativeEditEvent) {
            NativeEvent nativeEvent = ((NativeEditEvent) event).getNativeEvent();
            String eventType = nativeEvent.getType();
            if (CLICK.equals(eventType)) {
                return mouseBinding;
            } else if (isPossibleEditKeyEvent(nativeEvent)) {
                if (editEventFilter != null && !editEventFilter.accept(nativeEvent)) {
                    return null;
                }

                if (hasEditAction && isEditObjectEvent(nativeEvent)) {
                    return EDIT_OBJECT;
                }

                if (isCommonEditKeyEvent(nativeEvent)) {
                    return CHANGE;
                }
            }
        } else if (event instanceof InternalEditEvent) {
            return ((InternalEditEvent) event).getAction();
        }

        return null;
    }

    public void setMouseAction(String actionSID) {
        mouseBinding = actionSID;
    }

    public String getMouseAction() {
        return mouseBinding;
    }

    public void setKeyAction(GKeyStroke key, String actionSID) {
        createKeyBindingMap().put(key, actionSID);
    }

    public String getKeyAction(GKeyStroke key) {
        return keyBindingMap != null ? keyBindingMap.get(key) : null;
    }

    public void setContextMenuAction(String actionSID, String caption) {
        createContextMenuItems().put(actionSID, caption);
    }

    private HashMap<GKeyStroke,String> createKeyBindingMap() {
        if (keyBindingMap == null) {
            keyBindingMap = new HashMap<>();
        }
        return keyBindingMap;
    }

    public LinkedHashMap<String, String> createContextMenuItems() {
        if (contextMenuBindingMap == null) {
            contextMenuBindingMap = new LinkedHashMap<>();
        }
        return contextMenuBindingMap;
    }

    public LinkedHashMap<String, String> getContextMenuItems() {
        return contextMenuBindingMap;
    }

    public static boolean isEditableAwareEditEvent(String actionSID) {
        return CHANGE.equals(actionSID)
                || CHANGE_WYS.equals(actionSID)
                || EDIT_OBJECT.equals(actionSID)
                || GROUP_CHANGE.equals(actionSID);
    }

    public static String getPropertyEditActionSID(EditEvent e, GPropertyDraw property, GEditBindingMap overrideMap) {
        String actionSID = null;
        if (property != null) {
            EditEventFilter eventFilter = property.changeType == null ? null : property.changeType.getEditEventFilter();

            if (property.editBindingMap != null) {
                actionSID = property.editBindingMap.getAction(e, eventFilter, property.hasEditObjectAction);
            }

            if (actionSID == null && overrideMap != null) {
                actionSID = overrideMap.getAction(e, eventFilter, property.hasEditObjectAction);
            }
        }
        return actionSID;
    }

    public static String getPropertyKeyPressActionSID(EditEvent e, GPropertyDraw property) {
        if (property.editBindingMap != null) {
            return property.editBindingMap.getKeyPressAction(e);
        }
        return null;
    }
}
