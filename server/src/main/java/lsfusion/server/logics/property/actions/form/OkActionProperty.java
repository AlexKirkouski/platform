package lsfusion.server.logics.property.actions.form;

import lsfusion.server.data.SQLHandledException;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.instance.FormInstance;
import lsfusion.server.logics.BaseLogicsModule;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.property.actions.flow.ChangeFlowType;

import java.sql.SQLException;

public class OkActionProperty extends FormFlowActionProperty {
    private static LCP showIf = createShowIfProperty(new CalcProperty[] {FormEntity.isSync}, new boolean[] {false});
    
    public OkActionProperty(BaseLogicsModule lm) {
        super(lm);
    }

    protected void executeForm(FormInstance form, ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        form.formOk(context);
    }

    @Override
    protected LCP getShowIf() {
        return showIf;
    }
}
