package lsfusion.client.logics.classes;

import lsfusion.client.ClientResourceBundle;
import lsfusion.client.StartupProperties;
import lsfusion.client.form.PropertyEditor;
import lsfusion.client.form.editor.DoublePropertyEditor;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.Data;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;

public class ClientDoubleClass extends ClientIntegralClass implements ClientTypeClass {

    public final static ClientDoubleClass instance = new ClientDoubleClass();

    protected ClientDoubleClass() {
    }

    @Override
    protected int getLength() {
        return 10;
    }

    public byte getTypeId() {
        return Data.DOUBLE;
    }

    public NumberFormat getDefaultFormat() {
        NumberFormat format = super.getDefaultFormat();
        format.setMaximumFractionDigits(10);

        if (StartupProperties.dotSeparator) {
            DecimalFormat decimalFormat = (DecimalFormat) format;
            DecimalFormatSymbols dfs = decimalFormat.getDecimalFormatSymbols();
            if (dfs.getGroupingSeparator() != '.') {
                dfs.setDecimalSeparator('.');
            }
            decimalFormat.setDecimalFormatSymbols(dfs);
        }

        return format;
    }

    public Object parseString(String s) throws ParseException {
        try {
            Number n = parseWithDefaultFormat(s);
            return n.doubleValue();
        } catch (NumberFormatException nfe) {
            throw new ParseException(s + ClientResourceBundle.getString("logics.classes.can.not.be.converted.to.double"), 0);
        }
    }

    public PropertyEditor getDataClassEditorComponent(Object value, ClientPropertyDraw property) {
        return new DoublePropertyEditor(value, property.maxValue, getEditFormat(property), property.design, Double.class, property.hasMask());
    }

    @Override
    public String toString() {
        return ClientResourceBundle.getString("logics.classes.real.number");
    }
}
