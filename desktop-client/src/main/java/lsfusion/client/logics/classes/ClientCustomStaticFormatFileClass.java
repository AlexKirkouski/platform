package lsfusion.client.logics.classes;

import lsfusion.base.BaseUtils;
import lsfusion.client.ClientResourceBundle;
import lsfusion.client.form.PropertyEditor;
import lsfusion.client.form.PropertyRenderer;
import lsfusion.client.form.editor.FilePropertyEditor;
import lsfusion.client.form.renderer.CustomStaticFormatFileRenderer;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.interop.Data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;

public class ClientCustomStaticFormatFileClass extends ClientStaticFormatFileClass {

    public final String filterDescription;
    public final String filterExtensions[];

    public ClientCustomStaticFormatFileClass(String filterDescription, String[] filterExtensions, boolean multiple, boolean storeName) {
        super(multiple, storeName);
        this.filterDescription = filterDescription;
        this.filterExtensions = filterExtensions;
    }

    @Override
    public String[] getExtensions() {
        return filterExtensions;
    }

    public byte getTypeId() {
        return Data.CUSTOMSTATICFORMATFILE;
    }

    public void serialize(DataOutputStream outStream) throws IOException {
        super.serialize(outStream);

        //todo:
    }

    public String formatString(Object obj) throws ParseException {
        return filterDescription;
    }

    public PropertyRenderer getRendererComponent(ClientPropertyDraw property) {
        return new CustomStaticFormatFileRenderer(property, filterExtensions[0]);
    }

    public PropertyEditor getDataClassEditorComponent(Object value, ClientPropertyDraw design) {
        return new FilePropertyEditor(multiple, storeName, filterDescription, filterExtensions);
    }

    @Override
    public String toString() {
        return ClientResourceBundle.getString("logics.classes.static.format.file", BaseUtils.toString(",", filterExtensions));
    }

    public static ClientCustomStaticFormatFileClass deserialize(DataInputStream inStream) throws IOException {
        Boolean multiple = inStream.readBoolean();
        Boolean storeName = inStream.readBoolean();
        String filterDescription = inStream.readUTF();
        String[] filterExtensions;
        int extCount = inStream.readInt();
        if (extCount <= 0) {
            filterExtensions = new String[1];
            filterExtensions[0] = "*";
        } else {
            filterExtensions = new String[extCount];

            for (int i = 0; i < extCount; ++i) {
                filterExtensions[i] = inStream.readUTF();
            }
        }

        return new ClientCustomStaticFormatFileClass(filterDescription, filterExtensions, multiple, storeName);
    }
}
