package lsfusion.server.integration;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.type.Type;
import lsfusion.server.logics.DataObject;

public interface ImportFieldInterface {
    DataObject getDataObject(ImportTable.Row row);

    Expr getExpr(ImMap<ImportField, ? extends Expr> importKeys);

    Type getType();
}
