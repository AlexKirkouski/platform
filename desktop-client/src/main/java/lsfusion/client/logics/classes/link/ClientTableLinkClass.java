package lsfusion.client.logics.classes.link;

import lsfusion.client.ClientResourceBundle;
import lsfusion.client.form.PropertyRenderer;
import lsfusion.client.form.renderer.link.TableLinkPropertyRenderer;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.form.property.DataType;

public class ClientTableLinkClass extends ClientStaticFormatLinkClass {

    public final static ClientTableLinkClass instance = new ClientTableLinkClass(false);

    public ClientTableLinkClass(boolean multiple) {
        super(multiple);
    }

    public PropertyRenderer getRendererComponent(ClientPropertyDraw property) {
        return new TableLinkPropertyRenderer(property);
    }

    public byte getTypeId() {
        return DataType.TABLELINK;
    }

    @Override
    public String toString() {
        return ClientResourceBundle.getString("logics.classes.table.link");
    }
}