package lsfusion.server.logics.service.reflection;

import com.google.common.base.Throwables;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.context.ThreadLocalContext;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.ServiceLogicsModule;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Iterator;

public class PopSettingActionProperty extends ScriptingActionProperty {
    private ClassPropertyInterface nameInterface;

    public PopSettingActionProperty(ServiceLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        nameInterface = i.next();
    }
    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        try {
            String name = (String) context.getDataKeyValue(nameInterface).getValue();
            ThreadLocalContext.popSettings(name);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw Throwables.propagate(e);
        }

    }
}