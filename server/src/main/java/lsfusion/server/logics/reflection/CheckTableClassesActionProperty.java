package lsfusion.server.logics.reflection;

import lsfusion.base.Result;
import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.SQLSession;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ReflectionLogicsModule;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.service.RunService;
import lsfusion.server.logics.service.ServiceDBActionProperty;

import java.sql.SQLException;
import java.util.Iterator;

import static lsfusion.server.context.ThreadLocalContext.localize;

public class CheckTableClassesActionProperty extends ScriptingActionProperty {

    private final ClassPropertyInterface tableInterface;

    public CheckTableClassesActionProperty(ReflectionLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        tableInterface = i.next();
    }

    @Override
    public void executeCustom(final ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        DataObject tableObject = context.getDataKeyValue(tableInterface);
        boolean disableClasses = context.getBL().reflectionLM.disableClassesTable.read(context, tableObject) != null;
        if (!disableClasses) {
            final String tableName = (String) context.getBL().reflectionLM.sidTable.read(context, tableObject);
            final Result<String> message = new Result<>();
            ServiceDBActionProperty.run(context, new RunService() {
                public void run(SQLSession session, boolean isolatedTransaction) throws SQLException, SQLHandledException {
                    message.set(context.getDbManager().checkTableClasses(session, tableName.trim(), isolatedTransaction));
                }
            });

            context.delayUserInterfaction(new MessageClientAction(localize(LocalizedString.createFormatted("{logics.check.completed}", localize("{logics.checking.data.classes}"))) + '\n' + '\n' + message.result, localize("{logics.checking.data.classes}"), true));
        }
    }
}
