package lsfusion.gwt.form.server.navigator.handlers;

import lsfusion.gwt.form.server.spring.LSFusionDispatchServlet;
import lsfusion.gwt.form.server.convert.GwtToClientConverter;
import lsfusion.gwt.form.server.navigator.NavigatorServerResponseActionHandler;
import lsfusion.gwt.form.shared.actions.form.ServerResponseResult;
import lsfusion.gwt.form.shared.actions.navigator.ContinueNavigatorAction;
import net.customware.gwt.dispatch.server.ExecutionContext;
import net.customware.gwt.dispatch.shared.DispatchException;

import java.io.IOException;

public class ContinueNavigatorActionHandler extends NavigatorServerResponseActionHandler<ContinueNavigatorAction> {
    private final GwtToClientConverter gwtConverter = GwtToClientConverter.getInstance();

    public ContinueNavigatorActionHandler(LSFusionDispatchServlet servlet) {
        super(servlet);
    }

    @Override
    public ServerResponseResult executeEx(ContinueNavigatorAction action, ExecutionContext context) throws DispatchException, IOException {
        Object actionResults[] = new Object[action.actionResults.length];
        for (int i = 0; i < actionResults.length; ++i) {
            actionResults[i] = gwtConverter.convertOrCast(action.actionResults[i], servlet.getBLProvider());
        }

        return getServerResponseResult(action, servlet.getNavigator().continueNavigatorAction(actionResults));
    }
}
