package paas.properties;

import paas.PaasBusinessLogics;
import platform.server.classes.ValueClass;
import platform.server.form.instance.FormInstance;
import platform.server.form.view.DefaultFormView;
import platform.server.form.view.PropertyDrawView;
import platform.server.logics.property.ActionProperty;
import platform.server.logics.property.ExecutionContext;

import java.sql.SQLException;

public class RefreshStatusActionProperty extends ActionProperty {

    private PaasBusinessLogics paas;

    public RefreshStatusActionProperty(PaasBusinessLogics paas, String sID, String caption) {
        super(sID, caption, new ValueClass[]{paas.paasLM.project});
        this.paas = paas;
    }

    @Override
    public void execute(ExecutionContext context) throws SQLException {
        paas.refreshConfigurationStatuses(context.getSingleKeyValue());
        FormInstance<?> form = context.getFormInstance();
        if (form != null) {
            form.refreshData();
        }
    }

    @Override
    public void proceedDefaultDesign(PropertyDrawView propertyView, DefaultFormView view) {
        super.proceedDefaultDesign(propertyView, view);
        propertyView.design.setIconPath("refresh.png");
    }
}
