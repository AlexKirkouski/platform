package lsfusion.server.logics.property.actions;

import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.ActionProperty;
import lsfusion.server.logics.property.PropertyInterface;

public abstract class BaseActionProperty<P extends PropertyInterface> extends ActionProperty<P> {

    protected BaseActionProperty(LocalizedString caption, ImOrderSet<P> interfaces) {
        super(caption, interfaces);
    }

    public ImSet<ActionProperty> getDependActions() {
        return SetFact.EMPTY();
    }
}
