package lsfusion.server.logics.service.reflection;

import com.google.common.base.Throwables;
import lsfusion.server.Settings;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.ServiceLogicsModule;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingErrorLog;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Iterator;

import static org.apache.commons.lang3.StringUtils.trimToNull;

public class SaveSettingActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface settingInterface;
    private final ClassPropertyInterface userRoleInterface;

    public SaveSettingActionProperty(ServiceLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        settingInterface = i.next();
        userRoleInterface = i.next();
    }

    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {

            ObjectValue settingObject = context.getKeyValue(settingInterface);
            ObjectValue userRoleObject = context.getKeyValue(userRoleInterface);

            String nameSetting = trimToNull((String) findProperty("name[Setting]").read(context, settingObject));
            String valueSetting = trimToNull((String) findProperty("value[Setting, UserRole]").read(context, settingObject, userRoleObject));

            Settings settings = ThreadLocalContext.getRoleSettings((Long) userRoleObject.getValue());
            ThreadLocalContext.setPropertyValue(settings, nameSetting, valueSetting);

        } catch (ScriptingErrorLog.SemanticErrorException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | CloneNotSupportedException e) {
            throw Throwables.propagate(e);
        }

    }

    @Override
    protected boolean allowNulls() {
        return true;
    }
}
