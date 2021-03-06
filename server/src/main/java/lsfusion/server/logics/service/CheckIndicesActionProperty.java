package lsfusion.server.logics.service;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.ServiceLogicsModule;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;

import static lsfusion.server.context.ThreadLocalContext.localize;

public class CheckIndicesActionProperty extends ScriptingActionProperty {
    public CheckIndicesActionProperty(ServiceLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(final ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        try(DataSession session = context.createSession()) {
            context.getBL().checkIndices(session.sql);
        }
        context.delayUserInterfaction(new MessageClientAction(localize(LocalizedString.createFormatted("{logics.check.completed}", localize("{logics.checking.indices}"))), localize("{logics.checking.indices}")));
    }
}