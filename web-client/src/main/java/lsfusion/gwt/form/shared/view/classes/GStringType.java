package lsfusion.gwt.form.shared.view.classes;

import lsfusion.gwt.base.shared.GwtSharedUtils;
import lsfusion.gwt.form.shared.view.GExtInt;
import lsfusion.gwt.form.shared.view.GFont;
import lsfusion.gwt.form.shared.view.GPropertyDraw;
import lsfusion.gwt.form.shared.view.filter.GCompare;
import lsfusion.gwt.form.shared.view.grid.EditManager;
import lsfusion.gwt.form.shared.view.grid.editor.GridCellEditor;
import lsfusion.gwt.form.shared.view.grid.editor.StringGridCellEditor;
import lsfusion.gwt.form.shared.view.grid.editor.TextGridCellEditor;
import lsfusion.gwt.form.shared.view.grid.editor.rich.RichTextGridCellEditor;
import lsfusion.gwt.form.shared.view.grid.renderer.GridCellRenderer;
import lsfusion.gwt.form.shared.view.grid.renderer.StringGridCellRenderer;
import lsfusion.gwt.form.shared.view.grid.renderer.TextGridCellRenderer;

import java.text.ParseException;

import static java.lang.Math.pow;
import static java.lang.Math.round;

public class GStringType extends GDataType {

    public boolean blankPadded;
    public boolean caseInsensitive;
    public boolean rich;
    protected GExtInt length = new GExtInt(50);

    @Override
    public GCompare[] getFilterCompares() {
        return GCompare.values();
    }

    @Override
    public Object parseString(String s, String pattern) throws ParseException {
        return s;
    }

    @Override
    public GCompare getDefaultCompare() {
        return caseInsensitive ? GCompare.CONTAINS : GCompare.EQUALS;
    }

    public GStringType() {}

    public GStringType(int length) {
        this(new GExtInt(length), false, true, false);
    }

    public GStringType(GExtInt length, boolean caseInsensitive, boolean blankPadded, boolean rich) {

        this.blankPadded = blankPadded;
        this.caseInsensitive = caseInsensitive;
        this.rich = rich;
        this.length = length;
    }

    @Override
    public int getDefaultCharWidth() {
        if(length.isUnlimited()) {
            return 15;
        } else {
            int lengthValue = length.getValue();
            return lengthValue <= 12 ? lengthValue : (int) round(12 + pow(lengthValue - 12, 0.7));
        }
    }

    @Override
    public int getDefaultHeight(GFont font) {
        if (length.isUnlimited()) {
            return super.getDefaultHeight(font) * 4;
        }
        return super.getDefaultHeight(font);
    }

    @Override
    public GridCellRenderer createGridCellRenderer(GPropertyDraw property) {
        if (length.isUnlimited()) {
            return new TextGridCellRenderer(property, rich);
        }
        return new StringGridCellRenderer(property, !blankPadded);
    }

    @Override
    public GridCellEditor createGridCellEditor(EditManager editManager, GPropertyDraw editProperty) {
        if (length.isUnlimited()) {
            return rich ? new RichTextGridCellEditor(editManager, editProperty) : new TextGridCellEditor(editManager, editProperty);
        }
        return new StringGridCellEditor(editManager, editProperty, !blankPadded, length.getValue());
    }

    @Override
    public String toString() {
        return "Строка" + (caseInsensitive ? " без регистра" : "") + (blankPadded ? " с паддингом" : "") + (rich ? " rich" : "") + "(" + length + ")";
    }
}
