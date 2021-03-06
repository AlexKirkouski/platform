package lsfusion.server.form.window;

import lsfusion.interop.AbstractWindowType;
import lsfusion.server.logics.i18n.LocalizedString;

import javax.swing.*;
import java.io.DataOutputStream;
import java.io.IOException;

public class MenuNavigatorWindow extends NavigatorWindow {

    public int showLevel = 0;
    public int orientation = SwingConstants.HORIZONTAL;

    public MenuNavigatorWindow(String canonicalName, LocalizedString caption, int x, int y, int width, int height) {
        super(canonicalName, caption, x, y, width, height);
    }

    @Override
    public int getViewType() {
        return AbstractWindowType.MENU_VIEW;
    }

    @Override
    public void serialize(DataOutputStream outStream) throws IOException {
        super.serialize(outStream);
        outStream.writeInt(showLevel);
        outStream.writeInt(orientation);
    }
}
