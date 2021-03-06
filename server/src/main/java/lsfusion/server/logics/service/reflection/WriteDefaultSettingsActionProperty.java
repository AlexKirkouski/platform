package lsfusion.server.logics.service.reflection;

import com.google.common.base.Throwables;
import lsfusion.server.Settings;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.integration.*;
import lsfusion.server.logics.ServiceLogicsModule;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;
import lsfusion.server.session.DataSession;
import org.apache.commons.beanutils.PropertyUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WriteDefaultSettingsActionProperty extends ScriptingActionProperty {

    public WriteDefaultSettingsActionProperty(ServiceLogicsModule LM) {
        super(LM);
    }

    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            Settings settings = Settings.get();
            Field[] attributes = settings.getClass().getDeclaredFields();

            List<List<Object>> data = new ArrayList<>();
            for (Field field : attributes) {
                String name = field.getName();
                String value = String.valueOf(PropertyUtils.getSimpleProperty(settings, field.getName()));
                data.add(Arrays.asList((Object) name, value));
            }

            List<ImportProperty<?>> props = new ArrayList<>();
            List<ImportField> fields = new ArrayList<>();
            List<ImportKey<?>> keys = new ArrayList<>();

            ImportField nameSettingField = new ImportField(findProperty("name[Setting]"));
            ImportKey<?> settingKey = new ImportKey((CustomClass) findClass("Setting"),
                    findProperty("setting[VARSTRING[100]]").getMapping(nameSettingField));
            keys.add(settingKey);
            props.add(new ImportProperty(nameSettingField, findProperty("name[Setting]").getMapping(settingKey)));
            fields.add(nameSettingField);

            ImportField defaultValueSettingField = new ImportField(findProperty("defaultValue[Setting]"));
            props.add(new ImportProperty(defaultValueSettingField, findProperty("defaultValue[Setting]").getMapping(settingKey)));
            fields.add(defaultValueSettingField);

            ImportTable table = new ImportTable(fields, data);

            try (DataSession session = context.createSession()) {
                session.pushVolatileStats("RP_R");
                IntegrationService service = new IntegrationService(session, table, keys, props);
                service.synchronize(true, false);
                session.apply(context);
                session.popVolatileStats();
            }

        } catch (InvocationTargetException | NoSuchMethodException | ScriptingErrorLog.SemanticErrorException | IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }
}
