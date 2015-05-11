package lsfusion.server.logics.property;

import lsfusion.server.classes.ObjectValueClassSet;
import lsfusion.server.data.expr.BaseExpr;
import lsfusion.server.data.expr.SingleClassExpr;
import lsfusion.server.data.where.Where;

public interface IsClassField {

    BaseExpr getFollowExpr(BaseExpr joinExpr);

    Where getIsClassWhere(SingleClassExpr expr, ObjectValueClassSet set, boolean inconsistent);
}
