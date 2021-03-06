package lsfusion.client;


import java.text.MessageFormat;
import java.util.ResourceBundle;

public class ClientResourceBundle {
    public static ResourceBundle clientResourceBundle = ResourceBundle.getBundle("ClientResourceBundle");

    public static String getString(String key) {
        return clientResourceBundle.getString(key);
    }

    public static String getString(String key, Object... params) {
        return MessageFormat.format(getString(key), params);
    }
}
