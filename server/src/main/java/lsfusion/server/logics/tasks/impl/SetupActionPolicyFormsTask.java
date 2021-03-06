package lsfusion.server.logics.tasks.impl;

import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.ActionProperty;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.Property;

public class SetupActionPolicyFormsTask extends SetupActionOrPropertyPolicyFormsTask {

    public String getCaption() {
        return "Setup action policy";
    }

    @Override
    protected FormEntity getForm() {
        return getBL().securityLM.actionPolicyForm;
    }

    @Override
    protected LCP getCanonicalName() {
        return getBL().reflectionLM.actionCanonicalName;
    }

    @Override
    protected void runTask(Property property) {
        if(property instanceof ActionProperty)
            getBL().setupPropertyPolicyForms(setupPolicyByCN, property, true);
    }
}
