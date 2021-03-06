package lsfusion.server.logics.reflection;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.SQLSession;
import lsfusion.server.logics.DataObject;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.ReflectionLogicsModule;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.service.RunService;
import lsfusion.server.logics.service.ServiceDBActionProperty;
import lsfusion.server.session.DataSession;

import java.sql.SQLException;
import java.util.Iterator;

import static lsfusion.server.context.ThreadLocalContext.localize;

public class RecalculateTableColumnActionProperty extends ScriptingActionProperty {

    private final ClassPropertyInterface tableColumnInterface;

    public RecalculateTableColumnActionProperty(ReflectionLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        tableColumnInterface = i.next();
    }

    @Override
    public void executeCustom(final ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        DataObject tableColumnObject = context.getDataKeyValue(tableColumnInterface);
        final ObjectValue propertyObject = context.getBL().reflectionLM.propertyTableColumn.readClasses(context, tableColumnObject);
        final String propertyCanonicalName = (String) context.getBL().reflectionLM.canonicalNameProperty.read(context, propertyObject);
        boolean disableAggregations = context.getBL().reflectionLM.disableAggregationsTableColumn.read(context, tableColumnObject) != null;
        if(!disableAggregations) {
            try (DataSession dataSession = context.createSession()) {
                ServiceDBActionProperty.run(context, new RunService() {
                    public void run(SQLSession session, boolean isolatedTransaction) throws SQLException, SQLHandledException {
                        context.getDbManager().recalculateAggregationTableColumn(dataSession, session, propertyCanonicalName.trim(), isolatedTransaction);
                    }
                });
                dataSession.apply(context);
            }

            context.delayUserInterfaction(new MessageClientAction(localize(LocalizedString.createFormatted("{logics.recalculation.completed}",
                    localize("{logics.recalculation.aggregations}"))), localize("{logics.recalculation.aggregations}")));
        }
    }
}