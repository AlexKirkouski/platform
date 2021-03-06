package lsfusion.client.form.renderer.link;

import lsfusion.client.form.renderer.LabelPropertyRenderer;
import lsfusion.client.logics.ClientPropertyDraw;

import javax.swing.*;

public abstract class LinkPropertyRenderer extends LabelPropertyRenderer {
    public LinkPropertyRenderer(ClientPropertyDraw property) {
        super(property);

        getComponent().setHorizontalAlignment(JLabel.CENTER);
        getComponent().setVerticalAlignment(JLabel.CENTER);
    }

    public void setValue(Object value) {
        if (value != null) {
            getComponent().setText(null);
        }
        super.setValue(getImageIcon()); // передаём суперу иконку, а не ссылку. из наличия ссылки не следует наличие иконки
    }
    
    protected ImageIcon getImageIcon() {
        return (ImageIcon) getComponent().getIcon();
    }
}