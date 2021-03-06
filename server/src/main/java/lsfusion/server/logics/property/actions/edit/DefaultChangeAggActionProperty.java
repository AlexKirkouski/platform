package lsfusion.server.logics.property.actions.edit;

import lsfusion.base.col.MapFact;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.server.classes.CustomClass;
import lsfusion.server.classes.DataClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.data.SQLCallable;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.KeyExpr;
import lsfusion.server.data.expr.query.GroupExpr;
import lsfusion.server.data.expr.query.GroupType;
import lsfusion.server.data.type.Type;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.*;
import lsfusion.server.logics.property.actions.flow.AroundAspectActionProperty;
import lsfusion.server.logics.property.actions.flow.FlowResult;

import java.sql.SQLException;

public class DefaultChangeAggActionProperty<P extends PropertyInterface> extends AroundAspectActionProperty {

    private final CalcProperty<P> aggProp; // assert что один интерфейс и aggProp
    private final ValueClass aggClass;

    public DefaultChangeAggActionProperty(LocalizedString caption, ImOrderSet<JoinProperty.Interface> listInterfaces, CalcProperty<P> aggProp, ValueClass aggClass, ActionPropertyMapImplement<?, JoinProperty.Interface> changeAction) {
        super(caption, listInterfaces, changeAction);
        this.aggProp = aggProp;
        this.aggClass = aggClass;
        
        finalizeInit();
    }

    @Override // сам выполняет request поэтому на inRequest не смотрим
    public Type getSimpleRequestInputType(boolean optimistic, boolean inRequest) {
        Type type = aggProp.getType();
        return type instanceof DataClass ? type : null;
    }

    @Override
    protected FlowResult aroundAspect(final ExecutionContext<PropertyInterface> context) throws SQLException, SQLHandledException {
        ObjectValue readValue = null;

        Type type = aggProp.getType();
        if (type instanceof DataClass) {
            readValue = context.requestUserData((DataClass) type, null);
        } else {
            context.requestUserObject(
                    context.getFormFlowInstance().createObjectDialogRequest((CustomClass) aggProp.getValueClass(ClassType.editValuePolicy), context.stack)
            );
        }

        if (readValue != null) {
            // пока тупо MGProp'им назад
            KeyExpr keyExpr = new KeyExpr("key");
            Expr aggExpr = aggProp.getExpr(MapFact.singleton(aggProp.interfaces.single(), keyExpr), context.getModifier());
            Expr groupExpr;
            GroupType groupType = GroupType.ASSERTSINGLE_CHANGE();
            if(readValue.isNull()) {
                groupExpr = GroupExpr.create(
                        MapFact.<String, Expr>EMPTY(),
                        keyExpr,
                        keyExpr.isUpClass(aggClass).and(aggExpr.getWhere().not()),
                        groupType,
                        MapFact.<String, Expr>EMPTY()
                );
            } else {
                groupExpr = GroupExpr.create(
                        MapFact.singleton(0, aggExpr),
                        keyExpr,
                        keyExpr.isUpClass(aggClass),
                        groupType,
                        MapFact.singleton(0, readValue.getExpr())
                );
            }

            ObjectValue convertWYSValue = Expr.readObjectValue(context.getSession().sql, context.getSession().baseClass, groupExpr, context.getQueryEnv());
            return context.pushRequestedValue(convertWYSValue, aggClass.getType(), new SQLCallable<FlowResult>() {
                public FlowResult call() throws SQLException, SQLHandledException {
                    return proceed(context);
                }
            });
        }
        return FlowResult.FINISH;
    }
}
