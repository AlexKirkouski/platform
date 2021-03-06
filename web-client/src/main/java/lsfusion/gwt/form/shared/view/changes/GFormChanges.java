package lsfusion.gwt.form.shared.view.changes;

import lsfusion.gwt.form.shared.view.*;
import lsfusion.gwt.form.shared.view.changes.dto.GFormChangesDTO;
import lsfusion.gwt.form.shared.view.changes.dto.GPropertyReaderDTO;
import lsfusion.gwt.form.shared.view.reader.GPropertyReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class GFormChanges {
    public final HashMap<GGroupObject, GClassViewType> classViews = new HashMap<>();
    public final HashMap<GGroupObject, GGroupObjectValue> objects = new HashMap<>();
    public final HashMap<GGroupObject, ArrayList<GGroupObjectValue>> gridObjects = new HashMap<>();
    public final HashMap<GGroupObject, ArrayList<GGroupObjectValue>> parentObjects = new HashMap<>();
    public final HashMap<GGroupObject, HashMap<GGroupObjectValue, Boolean>> expandables = new HashMap<>();
    public final HashMap<GPropertyReader, HashMap<GGroupObjectValue, Object>> properties = new HashMap<>();
    public final HashSet<GPropertyReader> panelProperties = new HashSet<>();
    public final HashSet<GPropertyDraw> dropProperties = new HashSet<>();

    public final ArrayList<GComponent> activateTabs = new ArrayList<>();
    public final ArrayList<GPropertyDraw> activateProps = new ArrayList<>();

    public final HashSet<GPropertyDraw> updateProperties = new HashSet<>();

    public static GFormChanges remap(GForm form, GFormChangesDTO dto) {
        GFormChanges remapped = new GFormChanges();

        for (int i = 0; i < dto.classViewsGroupIds.length; i++) {
            remapped.classViews.put(form.getGroupObject(dto.classViewsGroupIds[i]), dto.classViews[i]);
        }

        for (int i = 0; i < dto.objectsGroupIds.length; i++) {
            remapped.objects.put(form.getGroupObject(dto.objectsGroupIds[i]), dto.objects[i]);
        }

        for (int i = 0; i < dto.gridObjectsGroupIds.length; i++) {
            remapped.gridObjects.put(form.getGroupObject(dto.gridObjectsGroupIds[i]), dto.gridObjects[i]);
        }

        for (int i = 0; i < dto.parentObjectsGroupIds.length; i++) {
            remapped.parentObjects.put(form.getGroupObject(dto.parentObjectsGroupIds[i]), dto.parentObjects[i]);
        }

        for (int i = 0; i < dto.expandablesGroupIds.length; i++) {
            remapped.expandables.put(form.getGroupObject(dto.expandablesGroupIds[i]), dto.expandables[i]);
        }

        for (int i = 0; i < dto.properties.length; i++) {
            remapped.properties.put(remapPropertyReader(form, dto.properties[i]), dto.propertiesValues[i]);
        }

        for (int propertyId : dto.panelPropertiesIds) {
            remapped.panelProperties.add(form.getProperty(propertyId));
        }

        for (Integer propertyID : dto.dropPropertiesIds) {
            remapped.dropProperties.add(form.getProperty(propertyID));
        }

        for (int activateTab : dto.activateTabsIds) {
            remapped.activateTabs.add(form.findContainerByID(activateTab));
        }

        for (int activateProp : dto.activatePropsIds) {
            remapped.activateProps.add(form.getProperty(activateProp));
        }

        return remapped;
    }

    private static GPropertyReader remapPropertyReader(GForm form, GPropertyReaderDTO readerDTO) {
        return remapPropertyReader(form, readerDTO.type, readerDTO.readerID);
    }

    private static GPropertyReader remapPropertyReader(GForm form, int typeId, int readerId) {
        switch (typeId) {
            case GPropertyReadType.DRAW:
                return form.getProperty(readerId);
            case GPropertyReadType.CAPTION:
                return form.getProperty(readerId).captionReader;
            case GPropertyReadType.SHOWIF:
                return form.getProperty(readerId).showIfReader;
            case GPropertyReadType.READONLY:
                return form.getProperty(readerId).readOnlyReader;
            case GPropertyReadType.CELL_BACKGROUND:
                return form.getProperty(readerId).backgroundReader;
            case GPropertyReadType.CELL_FOREGROUND:
                return form.getProperty(readerId).foregroundReader;
            case GPropertyReadType.FOOTER:
                return form.getProperty(readerId).footerReader;
            case GPropertyReadType.ROW_BACKGROUND:
                return form.getGroupObject(readerId).rowBackgroundReader;
            case GPropertyReadType.ROW_FOREGROUND:
                return form.getGroupObject(readerId).rowForegroundReader;
            default:
                return null;
        }
    }

    public class GPropertyReadType {
        public final static byte DRAW = 0;
        public final static byte CAPTION = 1;
        public final static byte SHOWIF = 2;
        public final static byte FOOTER = 3;
        public final static byte READONLY = 4;
        public final static byte CELL_BACKGROUND = 5;
        public final static byte CELL_FOREGROUND = 6;
        public final static byte ROW_BACKGROUND = 7;
        public final static byte ROW_FOREGROUND = 8;
    }
}
