package lsfusion.server.logics.property;

import lsfusion.base.col.ListFact;
import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.server.data.type.Type;
import lsfusion.server.logics.ObjectValue;
import lsfusion.server.logics.linear.LCP;

public class RequestResult {
    public final ObjectValue chosenValue;
    public final Type type;
    public final LCP targetProp;

    public RequestResult(ObjectValue chosenValue, Type type, LCP targetProp) {
        this.chosenValue = chosenValue;
        this.type = type;
        this.targetProp = targetProp;
    }

    public static ImList<RequestResult> get(ObjectValue chosenValue, Type type, LCP targetProp) {
        if(chosenValue == null) // cancel
            return null;

        return ListFact.singleton(new RequestResult(chosenValue, type, targetProp));
    }
}
