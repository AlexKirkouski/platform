package lsfusion.server.form.navigator;

import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.ActionProperty;

public class NavigatorAction extends NavigatorElement {
    private final ActionProperty action;
    private final FormEntity form;

    public NavigatorAction(ActionProperty action, String canonicalName, LocalizedString caption, FormEntity form, String icon, DefaultIcon defaultIcon) {
        super(canonicalName, caption);
        
        this.action = action;
        this.form = form;
        setImage(icon, defaultIcon);
    }

    @Override
    public boolean isLeafElement() {
        return true;
    }

    @Override
    public byte getTypeID() {
        return 2;
    }

    public FormEntity getForm() {
        return form;
    }

    public ActionProperty getAction() {
        return action;
    }
}