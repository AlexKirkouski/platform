package lsfusion.server.data.query;

import lsfusion.server.data.expr.InnerExpr;
import lsfusion.server.data.expr.query.StatType;
import lsfusion.server.data.query.stat.InnerBaseJoin;
import lsfusion.server.data.query.stat.StatKeys;
import lsfusion.server.data.query.stat.WhereJoin;

public interface InnerJoin<K, IJ extends InnerJoin<K, IJ>> extends WhereJoin<K, IJ>, InnerBaseJoin<K> {

    InnerFollows<K> getInnerFollows();

    InnerExpr getInnerExpr(WhereJoin join);

    boolean isValue();

    StatKeys<K> getInnerStatKeys(StatType type);
}
