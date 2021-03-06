package lsfusion.server.data;

import lsfusion.server.data.query.EnsureTypeEnvironment;
import lsfusion.server.data.type.FunctionType;
import lsfusion.server.data.type.ParseInterface;

public interface ParseValue {

    ParseInterface getParseInterface(QueryEnvironment env, EnsureTypeEnvironment typeEnv);

    boolean isAlwaysSafeString(); // should be consistent with ParseInterface.isAlwaysSafeString, hack for recursions

    FunctionType getFunctionType();
}
