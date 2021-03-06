package lsfusion.server.logics.property;

import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetStaticValue;
import lsfusion.server.classes.StringClass;
import lsfusion.server.classes.sets.ResolveClassSet;
import lsfusion.server.data.expr.formula.StringJoinConcatenateFormulaImpl;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.property.infer.ExClassSet;
import lsfusion.server.logics.property.infer.InferType;
import lsfusion.server.logics.property.infer.Inferred;

public class StringConcatenateProperty extends FormulaImplProperty {

    public StringConcatenateProperty(LocalizedString caption, int intNum, String separator) {
        this(caption, intNum, separator, false);
    }

    public StringConcatenateProperty(LocalizedString caption, int intNum, String separator, boolean caseInsensitive) {
        super(caption, intNum, new StringJoinConcatenateFormulaImpl(separator, caseInsensitive));
    }

    @Override
    public Inferred<Interface> calcInferInterfaceClasses(ExClassSet commonValue, InferType inferType) {
        if (commonValue != null) {
            return new Inferred<>(ExClassSet.toEx(interfaces.mapValues(new GetStaticValue<ResolveClassSet>() {
                public ResolveClassSet getMapValue() {
                    return StringClass.get(0); // немного бред но ладно
                }
            })));
        }
        return super.calcInferInterfaceClasses(commonValue, inferType);
    }
    @Override
    public ExClassSet calcInferValueClass(ImMap<Interface, ExClassSet> inferred, InferType inferType) {
        return ExClassSet.toEx(StringClass.get(0)); // немного бред но ладно
    }
}
