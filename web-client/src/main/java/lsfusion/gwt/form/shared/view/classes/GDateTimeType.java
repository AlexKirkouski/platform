package lsfusion.gwt.form.shared.view.classes;

import com.google.gwt.i18n.shared.DateTimeFormat;
import lsfusion.gwt.base.shared.GwtSharedUtils;
import lsfusion.gwt.form.shared.view.GEditBindingMap;
import lsfusion.gwt.form.shared.view.GPropertyDraw;
import lsfusion.gwt.form.shared.view.grid.EditManager;
import lsfusion.gwt.form.shared.view.grid.editor.DateTimeGridCellEditor;
import lsfusion.gwt.form.shared.view.grid.editor.GridCellEditor;
import lsfusion.gwt.form.shared.view.grid.renderer.DateGridCellRenderer;
import lsfusion.gwt.form.shared.view.grid.renderer.GridCellRenderer;

import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Date;

import static lsfusion.gwt.base.shared.GwtSharedUtils.*;

public class GDateTimeType extends GFormatType<com.google.gwt.i18n.client.DateTimeFormat> {
    public static GDateTimeType instance = new GDateTimeType();

    @Override
    public com.google.gwt.i18n.client.DateTimeFormat getFormat(String pattern) {
        return GwtSharedUtils.getDateTimeFormat(pattern);
    }

    @Override
    public GridCellRenderer createGridCellRenderer(GPropertyDraw property) {
        return new DateGridCellRenderer(property);
    }

    @Override
    public GridCellEditor createGridCellEditor(EditManager editManager, GPropertyDraw editProperty) {
        return new DateTimeGridCellEditor(editManager, editProperty);
    }

    @Override
    public Timestamp parseString(String value, String pattern) throws ParseException {
        if (value.isEmpty()) {
            return null;
        }
        Date date = GDateType.parseDate(value, getDefaultDateTimeFormat(), getDefaultDateTimeShortFormat(), getDefaultDateFormat());
        return new Timestamp(date.getTime());
    }

    private static Date wideFormattableDateTime = null;

    public static Date getWideFormattableDateTime() {
        if(wideFormattableDateTime == null)
            wideFormattableDateTime = DateTimeFormat.getFormat("yyyy-MM-dd HH:mm:ss").parse("1991-11-21 10:55:55");
        return wideFormattableDateTime;
    }

    @Override
    protected Object getDefaultWidthValue() {
        return getWideFormattableDateTime();
    }

    @Override
    public String toString() {
        return "Дата со временем";
    }

    @Override
    public GEditBindingMap.EditEventFilter getEditEventFilter() {
        return GEditBindingMap.numberEventFilter;
    }
}
