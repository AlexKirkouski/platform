package lsfusion.server.logics.reflection;

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
import lsfusion.server.session.DataSession;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.Set;

import static lsfusion.server.context.ThreadLocalContext.localize;

public class RecalculateTableStatsActionProperty extends ScriptingActionProperty {

    private final ClassPropertyInterface tableInterface;

    public RecalculateTableStatsActionProperty(ReflectionLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        tableInterface = i.next();
    }

    @Override
    public void executeCustom(final ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {
        DataObject tableObject = context.getDataKeyValue(tableInterface);
        final String tableName = (String) context.getBL().reflectionLM.sidTable.read(context, tableObject);
        final Set<String> disableStatsTableColumnSet = context.getDbManager().getDisableStatsTableColumnSet();
        boolean disableStats = context.getBL().reflectionLM.disableStatsTable.read(context, tableObject) != null;
        if (!disableStats) {
            ServiceDBActionProperty.run(context, new RunService() {
                        public void run(SQLSession session, boolean isolatedTransaction) throws SQLException, SQLHandledException {
                            try (DataSession dataSession = context.getDbManager().createSession()) {
                                context.getBL().LM.tableFactory.getImplementTablesMap().get(tableName).recalculateStat(context.getBL().reflectionLM,
                                        disableStatsTableColumnSet, dataSession);
                                applySession(context, dataSession);
                            }
                        }
                    }
            );
            context.delayUserInterfaction(new MessageClientAction(localize(LocalizedString.createFormatted("{logics.recalculation.completed}", localize("{logics.recalculation.stats}"))), localize("{logics.recalculation.stats}")));
        }
    }
}