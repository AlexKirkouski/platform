package platform.server.logics.property;

import platform.base.BaseUtils;
import platform.base.QuickSet;
import platform.server.caches.IdentityLazy;
import platform.server.classes.ValueClass;
import platform.server.data.expr.Expr;
import platform.server.data.where.WhereBuilder;
import platform.server.data.where.classes.ClassWhere;
import platform.server.session.*;

import java.util.*;

// чисто для оптимизации
public class ExclusiveUnionProperty extends ExclusiveCaseUnionProperty {

    private final Collection<PropertyMapImplement<?,Interface>> operands;

    @IdentityLazy
    protected Iterable<Case> getCases() {
        assert finalized;
        Collection<Case> result = new ArrayList<Case>();
        for(PropertyMapImplement<?, Interface> operand : operands)
            result.add(new Case(operand, operand));
        return result;
    }

    @Override
    public Set<OldProperty> getOldDepends() {
        if(isAbstract())
            return new HashSet<OldProperty>();

        return super.getOldDepends();
    }

    public ExclusiveUnionProperty(String sID, String caption, List<Interface> interfaces, Collection<PropertyMapImplement<?, Interface>> operands) {
        super(sID, caption, interfaces);
        this.operands = operands;

        finalizeInit();
    }

    @Override
    protected QuickSet<Property> calculateUsedDataChanges(StructChanges propChanges) {
        assert finalized;

        return propChanges.getUsedDataChanges(getDepends());
    }

    @Override
    protected MapDataChanges<Interface> calculateDataChanges(PropertyChange<Interface> change, WhereBuilder changedWhere, PropertyChanges propChanges) {
        assert finalized;

        MapDataChanges<Interface> result = new MapDataChanges<Interface>();
        for(PropertyMapImplement<?, Interface> operand : operands)
            result = result.add(operand.mapDataChanges(change, changedWhere, propChanges));
        return result;
    }

    @Override
    protected boolean checkWhere() {
        return false;
    }

    public boolean isAbstract() {
        return classValueWhere != null;
    }
    // для постзадания
    private ClassWhere<Object> classValueWhere;
    public ExclusiveUnionProperty(String sID, String caption, List<Interface> interfaces, ValueClass valueClass, Map<Interface, ValueClass> interfaceClasses) {
        super(sID, caption, interfaces);
        operands = new ArrayList<PropertyMapImplement<?, Interface>>();

        classValueWhere = new ClassWhere<Object>(BaseUtils.<Object, ValueClass>add(interfaceClasses, "value", valueClass), true);
    }
    public void addOperand(PropertyMapImplement<?,Interface> operand) {
        assert isAbstract();

        operands.add(operand);
    }

    public ClassWhere<Object> getClassValueWhere() {
        if(isAbstract())
            return classValueWhere;

        return super.getClassValueWhere();    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected Expr calculateNewExpr(Map<Interface, ? extends Expr> joinImplement, boolean propClasses, PropertyChanges propChanges, WhereBuilder changedWhere) {
        if(isAbstract() && propClasses)
            return getClassTableExpr(joinImplement);

        assert finalized;
        return super.calculateNewExpr(joinImplement, propClasses, propChanges, changedWhere);    //To change body of overridden methods use File | Settings | File Templates.
    }

    @Override
    protected Expr calculateIncrementExpr(Map<Interface, ? extends Expr> joinImplement, PropertyChanges propChanges, Expr prevExpr, WhereBuilder changedWhere) {
        assert finalized;

        return super.calculateIncrementExpr(joinImplement, propChanges, prevExpr, changedWhere);    //To change body of overridden methods use File | Settings | File Templates.
    }

    protected boolean checkFull() {
        return true;
    }

    public boolean checkClasses() {
        assert isAbstract();

        ClassWhere<Object> calcClassValueWhere = super.getClassValueWhere();
        return calcClassValueWhere.means(classValueWhere) && (!checkFull() || classValueWhere.means(calcClassValueWhere));

    }
}
