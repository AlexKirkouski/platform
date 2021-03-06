package lsfusion.gwt.form.server;

import lsfusion.gwt.base.server.LogicsAwareDispatchServlet;
import lsfusion.gwt.form.server.form.handlers.*;
import lsfusion.gwt.form.server.navigator.handlers.*;
import lsfusion.interop.RemoteLogicsInterface;
import net.customware.gwt.dispatch.server.InstanceActionHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

import java.rmi.RemoteException;

public class FormDispatchServlet extends LogicsAwareDispatchServlet<RemoteLogicsInterface> {
    @Autowired
    private FormSessionManager formSessionManager;

    @Override
    protected void addHandlers(InstanceActionHandlerRegistry registry) {
        // navigator
        registry.addHandler(new CleanHandler(this));
        registry.addHandler(new ClientMessageHandler(this));
        registry.addHandler(new ContinueNavigatorActionHandler(this));
        registry.addHandler(new ExecuteNavigatorActionHandler(this));
        registry.addHandler(new ForbidDuplicateFormsHandler(this));
        registry.addHandler(new GetNavigatorInfoHandler(this));
        registry.addHandler(new GenerateIDHandler(this));
        registry.addHandler(new GetLocaleHandler(this));
        registry.addHandler(new GetClientSettingsHandler(this));
        registry.addHandler(new IsConfigurationAccessAllowedHandler(this));
        registry.addHandler(new LogClientExceptionActionHandler(this));
        registry.addHandler(new RegisterTabActionHandler(this));
        registry.addHandler(new SetCurrentFormHandler(this));
        registry.addHandler(new ThrowInNavigatorActionHandler(this));
        registry.addHandler(new GetRemoteNavigatorActionMessageHandler(this));
        registry.addHandler(new GetRemoteNavigatorActionMessageListHandler(this));
        registry.addHandler(new InterruptNavigatorHandler(this));
        registry.addHandler(new CheckApiVersionHandler(this));

        //form
        registry.addHandler(new CalculateSumHandler(this));
        registry.addHandler(new ChangeClassViewHandler(this));
        registry.addHandler(new ChangeGroupObjectHandler(this));
        registry.addHandler(new ChangePageSizeHandler(this));
        registry.addHandler(new ChangePropertyHandler(this));
        registry.addHandler(new ChangePropertyOrderHandler(this));
        registry.addHandler(new ClearPropertyOrdersHandler(this));
        registry.addHandler(new ClosePressedHandler(this));
        registry.addHandler(new CollapseGroupObjectHandler(this));
        registry.addHandler(new ContinueInvocationHandler(this));
        registry.addHandler(new CountRecordsHandler(this));
        registry.addHandler(new ExecuteEditActionHandler(this));
        registry.addHandler(new ExecuteNotificationHandler(this));
        registry.addHandler(new ExpandGroupObjectHandler(this));
        registry.addHandler(new FormHiddenHandler(this));
        registry.addHandler(new GetInitialFilterPropertyHandler(this));
        registry.addHandler(new GetRemoteActionMessageHandler(this));
        registry.addHandler(new GetRemoteActionMessageListHandler(this));
        registry.addHandler(new GetRemoteChangesHandler(this));
        registry.addHandler(new GroupReportHandler(this));
        registry.addHandler(new InterruptHandler(this));
        registry.addHandler(new OkPressedHandler(this));
        registry.addHandler(new PasteExternalTableHandler(this));
        registry.addHandler(new PasteSingleCellValueHandler(this));
        registry.addHandler(new RefreshUPHiddenPropsActionHandler(this));
        registry.addHandler(new SaveUserPreferencesActionHandler(this));
        registry.addHandler(new ScrollToEndHandler(this));
        registry.addHandler(new SetRegularFilterHandler(this));
        registry.addHandler(new SetTabVisibleHandler(this));
        registry.addHandler(new SetUserFiltersHandler(this));
        registry.addHandler(new ThrowInInvocationHandler(this));
    }

    public FormSessionManager getFormSessionManager() {
        return formSessionManager;
    }

    @Override
    public void tabClosed(String tabSID) throws RemoteException {
        formSessionManager.removeFormSessionObjects(tabSID);
        super.tabClosed(tabSID);
    }
}
