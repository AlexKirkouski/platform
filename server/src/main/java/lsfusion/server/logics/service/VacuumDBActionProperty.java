package lsfusion.server.logics.service;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.ServiceLogicsModule;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;

import static lsfusion.server.context.ThreadLocalContext.localize;

public class VacuumDBActionProperty extends ScriptingActionProperty {
    public VacuumDBActionProperty(ServiceLogicsModule LM) {
        super(LM);
    }

    @Override
    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try (DataSession session = context.createSession()) {
            context.getDbManager().vacuumDB(session.sql);
            session.apply(context);
        }

        context.delayUserInterfaction(new MessageClientAction(localize("{logics.vacuum.db.was.completed}"), localize("{logics.vacuum.db}")));
    }
}