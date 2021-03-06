package lsfusion.gwt.form.shared.view;

import lsfusion.gwt.base.client.ui.GFlexAlignment;
import lsfusion.gwt.base.client.ui.GKeyStroke;
import lsfusion.gwt.base.shared.GwtSharedUtils;
import lsfusion.gwt.form.client.MainFrame;
import lsfusion.gwt.form.client.form.ui.GFormController;
import lsfusion.gwt.form.shared.view.changes.GGroupObjectValue;
import lsfusion.gwt.form.shared.view.classes.*;
import lsfusion.gwt.form.shared.view.filter.GCompare;
import lsfusion.gwt.form.shared.view.grid.EditManager;
import lsfusion.gwt.form.shared.view.grid.editor.GridCellEditor;
import lsfusion.gwt.form.shared.view.grid.renderer.FormatGridCellRenderer;
import lsfusion.gwt.form.shared.view.grid.renderer.GridCellRenderer;
import lsfusion.gwt.form.shared.view.logics.GGroupObjectLogicsSupplier;
import lsfusion.gwt.form.shared.view.panel.PanelRenderer;
import lsfusion.gwt.form.shared.view.reader.*;

import java.io.Serializable;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class GPropertyDraw extends GComponent implements GPropertyReader {
    public int ID;
    public String sID;
    public String namespace;
    public String caption;
    public String canonicalName;
    public String propertyFormName;

    public String toolTip;
    public String tableName;
    public String[] interfacesCaptions;
    public GClass[] interfacesTypes;
    public String creationScript;
    public String creationPath;
    public String formPath;

    public GGroupObject groupObject;
    public String columnsName;
    public ArrayList<GGroupObject> columnGroupObjects;

    public GType baseType;
    public String pattern;
    public String defaultPattern;
    public GClass returnClass;

    public GType changeWYSType;
    public GType changeType;

    public AddRemove addRemove;
    public boolean askConfirm;
    public String askConfirmMessage;
    
    public boolean hasEditObjectAction;
    public boolean hasChangeAction;

    public GEditBindingMap editBindingMap;

    public ImageDescription icon;
    public boolean focusable;
    public boolean checkEquals;
    public GPropertyEditType editType = GPropertyEditType.EDITABLE;

    public boolean echoSymbols;
    public boolean noSort;
    public GCompare defaultCompare;

    public GKeyStroke editKey;
    public boolean showEditKey;

    public boolean drawAsync;

    public GCaptionReader captionReader;
    public GShowIfReader showIfReader;
    public GFooterReader footerReader;
    public GReadOnlyReader readOnlyReader;
    public GBackgroundReader backgroundReader;
    public GForegroundReader foregroundReader;

    public GPropertyDraw quickFilterProperty;

    public int charWidth;

    public int valueWidth = -1;
    public int valueHeight = -1;

    public boolean panelCaptionAbove;
    
    public boolean hide;

    private transient GridCellRenderer cellRenderer;
    
    public boolean notNull;

    public static class AddRemove implements Serializable {
        public GObject object;
        public boolean add;

        public AddRemove() {}

        public AddRemove(GObject object, boolean add) {
            this.object = object;
            this.add = add;
        }
    }

    public GPropertyDraw(){}

    public void update(GGroupObjectLogicsSupplier controller, Map<GGroupObjectValue, Object> values, boolean updateKeys) {
        controller.updatePropertyDrawValues(this, values, updateKeys);
    }

    public PanelRenderer createPanelRenderer(GFormController form, GGroupObjectValue columnKey) {
        return baseType.createPanelRenderer(form, this, columnKey);
    }

    public GridCellRenderer getGridCellRenderer() {
        if (cellRenderer == null) {
            cellRenderer = baseType.createGridCellRenderer(this);
        }
        return cellRenderer;
    }

    public GridCellEditor createGridCellEditor(EditManager editManager) {
        return baseType.createGridCellEditor(editManager, this);
    }

    public GridCellEditor createValueCellEdtor(EditManager editManager) {
        return baseType.createValueCellEditor(editManager, this);
    }

    public void setUserPattern(String pattern) {
        if(baseType instanceof GFormatType) {
            this.pattern = pattern != null ? pattern : defaultPattern;

            GridCellRenderer renderer = getGridCellRenderer();
            if (renderer instanceof FormatGridCellRenderer) {
                ((FormatGridCellRenderer) renderer).updateFormat();
            } else
                assert false;
        }
    }

    public Object parseChangeValueOrNull(String s) {
        if (s == null || changeWYSType == null) {
            return null;
        }
        try {
            return changeWYSType.parseString(s, pattern);
        } catch (ParseException pe) {
            return null;
        }
    }

    public boolean canUseChangeValueForRendering() {
        return changeType != null && baseType.getClass() == changeType.getClass();
    }

    @Override
    public int getGroupObjectID() {
        return groupObject != null ? groupObject.ID : -1;
    }

    public String getCaptionOrEmpty() {
        return caption == null ? "" : caption;
    }

    public String getDynamicCaption(Object caption) {
        return caption == null ? "" : caption.toString().trim();
    }

    public String getEditCaption(String caption) {
        if (caption == null) {
            caption = this.caption;
        }

        return showEditKey && editKey != null ? caption + " (" + editKey + ")" : caption;
    }

    public String getEditCaption() {
        return getEditCaption(caption);
    }

    public String getNotEmptyCaption() {
        if (caption == null || caption.trim().length() == 0) {
            return "Неопределённое свойство";
        } else {
            return caption;
        }
    }

    public static final String TOOL_TIP_FORMAT =
            "<html><b>%s</b><br>%s";

    public static final String DETAILED_TOOL_TIP_FORMAT =
            "<hr>" + 
            "<b>Каноническое имя:</b> %s<br>" +
            "<b>Таблица:</b> %s<br>" +
            "<b>Объекты:</b> %s<br>" +
            "<b>Сигнатура:</b> %s (%s)<br>" +
            "<b>Скрипт:</b> %s<br>" +
            "<b>Путь:</b> %s<br>" +
            "<hr>" +
            "<b>Имя на форме:</b> %s<br>" +
            "<b>Объявление на форме:</b> %s" +
            "</html>";

    public static final String DETAILED_ACTION_TOOL_TIP_FORMAT =
            "<hr>" +
            "<b>sID:</b> %s<br>" +
            "<b>Объекты:</b> %s<br>" +
            "<b>Путь:</b> %s<br>" +
            "<hr>" +
            "<b>Имя на форме:</b> %s<br>" +
            "<b>Объявление на форме:</b> %s" +
            "</html>";

    public static final String EDIT_KEY_TOOL_TIP_FORMAT =
            "<hr><b>Горячая клавиша:</b> %s<br>";

    public String getTooltipText(String caption) {
        String propCaption = GwtSharedUtils.nullTrim(!GwtSharedUtils.isRedundantString(toolTip) ? toolTip : caption);
        String editKeyText = editKey == null ? "" : GwtSharedUtils.stringFormat(EDIT_KEY_TOOL_TIP_FORMAT, editKey.toString());

        if (!MainFrame.configurationAccessAllowed) {
            return GwtSharedUtils.stringFormat(TOOL_TIP_FORMAT, propCaption, editKeyText);
        } else {
            String ifaceObjects = GwtSharedUtils.toString(", ", interfacesCaptions);
            String scriptPath = creationPath != null ? creationPath.replace("\n", "<br>") : "";
            String scriptFormPath = formPath != null ? formPath.replace("\n", "<br>") : "";
            
            if (baseType instanceof GActionType) {
                return GwtSharedUtils.stringFormat(TOOL_TIP_FORMAT + DETAILED_ACTION_TOOL_TIP_FORMAT,
                        propCaption, editKeyText, canonicalName, ifaceObjects, scriptPath, propertyFormName, scriptFormPath);
            } else {
                String tableName = this.tableName != null ? this.tableName : "&lt;none&gt;";
                String returnClass = this.returnClass.toString();
                String ifaceClasses = GwtSharedUtils.toString(", ", interfacesTypes);
                String script = creationScript != null ? escapeHTML(creationScript).replace("\n", "<br>") : "";
                
                return GwtSharedUtils.stringFormat(TOOL_TIP_FORMAT + DETAILED_TOOL_TIP_FORMAT,
                        propCaption, editKeyText, canonicalName, tableName, ifaceObjects, returnClass, ifaceClasses, 
                        script, scriptPath, propertyFormName, scriptFormPath);
            }
        }
    }

    private String escapeHTML(String value) {
        return value.replace("<", "&lt;").replace(">", "&gt;");
    }

    public String getIconPath(boolean enabled) {
        if (icon == null) {
            return null;
        }
        if (!enabled && icon.url != null) {
            int dotInd = icon.url.lastIndexOf(".");
            if (dotInd != -1) {
                return icon.url.substring(0, dotInd) + "_Disabled" + icon.url.substring(dotInd);
            }
        }

        return icon.url;
    }

    public boolean isReadOnly() {
        return editType == GPropertyEditType.READONLY;
    }

    public boolean isEditableNotNull() {
        return notNull && !isReadOnly();
    }

    public double getFlex() {
        if (flex == -2) {
            return getValueWidth(null);
        }
        return flex;
    }

    public GFlexAlignment getAlignment() {
        return alignment;
    }

    public int getValueWidth(GFont parentFont) {
        return getValueWidth(parentFont, null);
    }

    public int getValueWidth(GFont parentFont, GWidthStringProcessor widthStringProcessor) {
        if (valueWidth != -1) {
            return valueWidth;
        }

        GFont font = this.font != null ? this.font : parentFont;

        String widthString = null;
        if(widthString == null && charWidth != 0)
            widthString = GwtSharedUtils.replicate('0', charWidth);
        if(widthString != null)
            return baseType.getFullWidthString(widthString, font, widthStringProcessor);

        return baseType.getDefaultWidth(font, this, widthStringProcessor);
    }

    public Object getFormat() {
        return (baseType instanceof GObjectType ? GLongType.instance : ((GFormatType)baseType)).getFormat(pattern);
    }

    public int getValueHeight(GFont parentFont) {
        if (valueHeight != -1) {
            return valueHeight;
        }

        return baseType.getDefaultHeight(font != null ? font : parentFont);
    }

    public int getLabelHeight() {
        return new GStringType().getDefaultHeight(captionFont);
    }

    public LinkedHashMap<String, String> getContextMenuItems() {
        return editBindingMap == null ? null : editBindingMap.getContextMenuItems();
    }

    @Override
    public int hashCode() {
        return ID;
    }

    @Override
    public String toString() {
        return "GPropertyDraw{" +
                "sID='" + sID + '\'' +
                ", caption='" + caption + '\'' +
                ", baseType=" + baseType +
                ", changeType=" + changeType +
                ", imagePath='" + icon + '\'' +
                ", focusable=" + focusable +
                ", checkEquals=" + checkEquals +
                ", editType=" + editType +
                '}';
    }
}
