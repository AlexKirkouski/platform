package lsfusion.server.form.entity.drilldown;

import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.form.view.ContainerView;
import lsfusion.server.form.view.DefaultFormView;
import lsfusion.server.form.view.FormView;
import lsfusion.server.logics.LogicsModule;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.property.ChangedProperty;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.PrevScope;

public class ChangedDrillDownFormEntity extends DrillDownFormEntity<ClassPropertyInterface, ChangedProperty<ClassPropertyInterface>> {

    private PropertyDrawEntity propertyDraw;
    private PropertyDrawEntity newPropertyDraw;
    private PropertyDrawEntity oldPropertyDraw;

    public ChangedDrillDownFormEntity(String canonicalName, LocalizedString caption, ChangedProperty property, LogicsModule LM) {
        super(canonicalName, caption, property, LM);
    }

    @Override
    protected void setupDrillDownForm() {
        Version version = LM.getVersion();
        propertyDraw = addPropertyDraw(property, interfaceObjects, version);
        newPropertyDraw = addPropertyDraw(property.property, interfaceObjects, version);
        oldPropertyDraw = addPropertyDraw(property.property.getOld(PrevScope.DB), interfaceObjects, version);
    }

    @Override
    public FormView createDefaultRichDesign(Version version) {
        DefaultFormView design = (DefaultFormView) super.createDefaultRichDesign(version);

        valueContainer.add(design.get(propertyDraw), version);
        ContainerView newValueContainer = design.createContainer(LocalizedString.create("{logics.property.drilldown.form.new.value}"), version);
        newValueContainer.add(design.get(newPropertyDraw), version);
        ContainerView oldValueContainer = design.createContainer(LocalizedString.create("{logics.property.drilldown.form.old.value}"), version);
        oldValueContainer.add(design.get(oldPropertyDraw), version);

        design.mainContainer.addAfter(newValueContainer, valueContainer, version);
        design.mainContainer.addAfter(oldValueContainer, newValueContainer, version);

        return design;
    }
}
