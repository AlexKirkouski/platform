package lsfusion.server.logics.tasks.impl;

import lsfusion.server.logics.LogicsModule;
import lsfusion.server.logics.tasks.GroupModuleTask;
import org.antlr.runtime.RecognitionException;

public class InitTablesTask extends GroupModuleTask {

    protected boolean isGraph() {
        return true; // нужен граф, так как алгоритм include'а таблиц, меняет parents и поэтому например remove может стать раньше add
    }

    public String getCaption() {
        return "Initializing tables";
    }

    protected void runTask(LogicsModule module) throws RecognitionException {
        module.initTables();
    }
}
