package lsfusion.server.logics.i18n;

import lsfusion.base.ResourceUtils;

import java.util.*;
import java.util.regex.Pattern;

public class DefaultLocalizer extends AbstractLocalizer {
    private Collection<String> resourceBundleNames; 
    
    public DefaultLocalizer() {
        resourceBundleNames = new ArrayList<>();
        Pattern pattern = Pattern.compile("/([^/]*ResourceBundle\\.properties)"); // возможно нужен другой regexp
        Collection<String> filenames = ResourceUtils.getResources(pattern);
        for (String filename : filenames) {
            resourceBundleNames.add(filename.substring(0, filename.lastIndexOf('.')));
        }
    }
    
    @Override
    public String localizeKey(String key, Locale locale) {
        for (String bundleName : resourceBundleNames) {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle(bundleName, locale);
                return bundle.getString(key);
            } catch (MissingResourceException | ClassCastException ignored) {}
        }
        return key;
    }
}
