package platform.gwt.main.server.form.handlers;

import net.customware.gwt.dispatch.server.ExecutionContext;
import net.customware.gwt.dispatch.shared.DispatchException;
import platform.client.logics.ClientGroupObjectValue;
import platform.gwt.base.server.FormSessionObject;
import platform.gwt.main.server.RemoteServiceImpl;
import platform.gwt.main.shared.actions.form.ChangeProperty;
import platform.gwt.main.shared.actions.form.ServerResponseResult;

import java.io.IOException;

import static platform.base.BaseUtils.serializeObject;

public class ChangePropertyHandler extends ServerResponseActionHandler<ChangeProperty> {
    public ChangePropertyHandler(RemoteServiceImpl servlet) {
        super(servlet);
    }

    @Override
    public ServerResponseResult executeEx(ChangeProperty action, ExecutionContext context) throws DispatchException, IOException {
        FormSessionObject form = getFormSessionObject(action.formSessionID);
        return getServerResponseResult(
                form,
                form.remoteForm.changeProperty(
                        action.requestIndex,
                        action.propertyId,
                        new ClientGroupObjectValue().serialize(),
                        serializeObject(action.value.getValue())
                )
        );
    }
}
