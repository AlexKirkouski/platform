package lsfusion.utils.utils;

import com.google.common.base.Throwables;
import lsfusion.base.IOUtils;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.scripted.ScriptingActionProperty;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.Iterator;

public class AppendToFileActionProperty extends ScriptingActionProperty {
    private final ClassPropertyInterface pathInterface;
    private final ClassPropertyInterface textInterface;
    private final ClassPropertyInterface charsetInterface;

    public AppendToFileActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        pathInterface = i.next();
        textInterface = i.next();
        charsetInterface = i.next();
    }

    public void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        String filePath = (String) context.getDataKeyValue(pathInterface).getValue();
        String text = (String) context.getDataKeyValue(textInterface).getValue();
        String charset = (String) context.getDataKeyValue(charsetInterface).getValue();

        try {
            if (new File(filePath).exists()) {
                Files.write(Paths.get(filePath), text.getBytes(charset), StandardOpenOption.APPEND);
            } else {
                IOUtils.putFileBytes(new File(filePath), text.getBytes(charset));
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}