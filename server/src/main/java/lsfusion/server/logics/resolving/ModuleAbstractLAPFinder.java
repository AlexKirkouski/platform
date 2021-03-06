package lsfusion.server.logics.resolving;

import lsfusion.server.logics.LogicsModule;
import lsfusion.server.logics.linear.LAP;
import lsfusion.server.logics.linear.LP;
import lsfusion.server.logics.property.ActionProperty;
import lsfusion.server.logics.property.CaseUnionProperty;
import lsfusion.server.logics.property.Property;
import lsfusion.server.logics.property.actions.flow.ListCaseActionProperty;

public class ModuleAbstractLAPFinder extends ModuleAbstractLPFinder<LAP<?>> {
    @Override
    protected Iterable<LAP<?>> getSourceList(LogicsModule module, String name) {
        return module.getNamedActions(name);
    }    

    @Override
    protected boolean isAbstract(Property property) {
        assert property instanceof ActionProperty;
        return property instanceof ListCaseActionProperty && ((ListCaseActionProperty) property).isAbstract();
    }
}
