package lsfusion.server.data.query;

import lsfusion.base.ArrayInstancer;
import lsfusion.base.BaseUtils;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.server.Settings;
import lsfusion.server.caches.AbstractOuterContext;
import lsfusion.server.caches.ParamExpr;
import lsfusion.server.data.ParseValue;
import lsfusion.server.data.Table;
import lsfusion.server.data.Value;
import lsfusion.server.data.expr.IsClassExpr;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.expr.KeyType;
import lsfusion.server.data.expr.query.QueryExpr;
import lsfusion.server.data.sql.DataAdapter;
import lsfusion.server.data.translator.ExprTranslator;
import lsfusion.server.data.type.ObjectType;
import lsfusion.server.data.type.Type;
import lsfusion.server.data.where.Where;

abstract public class AbstractSourceJoin<T extends SourceJoin<T>> extends AbstractOuterContext<T> implements SourceJoin<T> {

    protected static class ToString extends CompileSource  {
        public ToString(ImSet<Value> values) {
            super(new KeyType() {
                public Type getKeyType(ParamExpr expr) {
                    return ObjectType.instance;
                }
            }, Where.FALSE, BaseUtils.<ImSet<ParseValue>>immutableCast(values).mapRevValues(new GetValue<String, ParseValue>() {
                public String getMapValue(ParseValue value) {
                    return value.toString();
                }}), DataAdapter.debugSyntax, StaticExecuteEnvironmentImpl.MVOID);
        }

        public String getSource(KeyExpr expr) {
            return expr.toString();
        }

        public String getSource(Table.Join.Expr expr) {
            return expr.toString();
        }

        public String getSource(Table.Join.IsIn where) {
            return where.toString();
        }

        public String getSource(QueryExpr queryExpr, boolean needValue) {
            return queryExpr.toString();
        }

        public String getSource(IsClassExpr classExpr, boolean needValue) {
            return "class(" + classExpr.expr.getSource(this) + ")";
        }
    }

    public final static ArrayInstancer<SourceJoin> instancer = new ArrayInstancer<SourceJoin>() {
        public SourceJoin[] newArray(int size) {
            return new SourceJoin[size];
        }
    };

    // упрощаем зная where == false
    public abstract T followFalse(Where falseWhere, boolean pack);

    public T calculatePack() {
        return followFalse(Where.FALSE, true);
    }

    public boolean needMaterialize() {
        if(getComplexity(false) > Settings.get().getLimitMaterializeComplexity())
            return true;

        return false;
    }

    public T translateExpr(ExprTranslator translator) {
        return aspectTranslate(translator);
    }

    protected T aspectTranslate(ExprTranslator translator) {
        T translated = translator.translate((T)this);
        if(translated != null)
            return translated;
        return translate(translator);
    }

    protected abstract T translate(ExprTranslator translator);
}
