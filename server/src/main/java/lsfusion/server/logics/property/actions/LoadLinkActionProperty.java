package lsfusion.server.logics.property.actions;

import lsfusion.interop.action.LoadLinkClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.BaseLogicsModule;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;

import java.sql.SQLException;

public class LoadLinkActionProperty extends ScriptingActionProperty {

    public LoadLinkActionProperty(BaseLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        context.getBL().LM.baseLM.networkPath.change((String)context.requestUserInteraction(new LoadLinkClientAction()), context);
    }
}