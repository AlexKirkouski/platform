package lsfusion.gwt.form.shared.view.classes;

import com.google.gwt.i18n.client.LocaleInfo;
import com.google.gwt.i18n.client.NumberFormat;
import lsfusion.gwt.form.shared.view.GEditBindingMap;
import lsfusion.gwt.form.shared.view.GPropertyDraw;
import lsfusion.gwt.form.shared.view.grid.renderer.GridCellRenderer;
import lsfusion.gwt.form.shared.view.grid.renderer.NumberGridCellRenderer;

import java.text.ParseException;

import static java.lang.Math.pow;
import static java.lang.Math.round;

public abstract class GIntegralType extends GFormatType<NumberFormat> {
    public final static String UNBREAKABLE_SPACE = "\u00a0";
    
    @Override
    public GridCellRenderer createGridCellRenderer(GPropertyDraw property) {
        return new NumberGridCellRenderer(property);
    }

    protected abstract int getLength();

    @Override
    public int getDefaultCharWidth() {
        int lengthValue = getLength();
        return Math.min(lengthValue <= 6 ? lengthValue : (int) round(6 + pow(lengthValue - 6, 0.7)), 10);
    }

    protected Double parseToDouble(String s, String pattern) throws ParseException {
        assert s != null;
        try {
            String groupingSeparator = LocaleInfo.getCurrentLocale().getNumberConstants().groupingSeparator();
            if (UNBREAKABLE_SPACE.equals(groupingSeparator)) {
                s = s.replace(" ", UNBREAKABLE_SPACE);
            }
            String decimalSeparator = LocaleInfo.getCurrentLocale().getNumberConstants().decimalSeparator();
            if (s.contains(",") && !groupingSeparator.equals(",") && decimalSeparator.equals("."))
                s = s.replace(",", ".");
            else if (s.contains(".") && !groupingSeparator.equals(".") && decimalSeparator.equals(","))
                s = s.replace(".", ",");
            return getFormat(pattern).parse(s);
        } catch (NumberFormatException e) {
            throw new ParseException("string " + s + "can not be converted to double", 0);
        }
    }

    @Override
    public GEditBindingMap.EditEventFilter getEditEventFilter() {
        return GEditBindingMap.numberEventFilter;
    }

    public NumberFormat getFormat(String pattern) {
        return NumberFormat.getDecimalFormat();
    }
}
