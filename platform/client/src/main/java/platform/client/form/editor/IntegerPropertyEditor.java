package platform.client.form.editor;

import platform.interop.ComponentDesign;

import javax.swing.*;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.DocumentFilter;
import javax.swing.text.InternationalFormatter;
import java.text.NumberFormat;
import java.text.ParseException;

public class IntegerPropertyEditor extends TextFieldPropertyEditor {

    public IntegerPropertyEditor(Object value, NumberFormat format, ComponentDesign design, Class<?> valueClass) {
        super(design);

        InternationalFormatter formatter = new InternationalFormatter(format) {
            @Override
            protected DocumentFilter getDocumentFilter() {
                return filter;
            }

            private DocumentFilter filter = new IntFilter();
        };
        formatter.setValueClass(valueClass);

        this.setHorizontalAlignment(JTextField.RIGHT);

        setFormatterFactory(new DefaultFormatterFactory(formatter));

        if (value != null) {
            setValue(value);
        }

        // выглядит странно, но где-то внутри это позволяет
        // обойти баг со сбрасыванием выделения из-за форматтера 
        setText(getText());
    }

    public Object getCellEditorValue() {

        try {
            commitEdit();
        } catch (ParseException e) {
            return null;
        }

        return this.getValue();
    }

}
