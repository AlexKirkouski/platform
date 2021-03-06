package lsfusion.client.form;

import lsfusion.client.form.layout.ClientFormLayout;
import lsfusion.client.form.panel.PanelController;
import lsfusion.client.form.panel.ToolbarView;
import lsfusion.client.form.queries.FilterController;
import lsfusion.client.logics.ClientObject;
import lsfusion.client.logics.ClientPropertyDraw;
import lsfusion.client.logics.ClientToolbar;

import java.awt.*;
import java.util.List;

public abstract class AbstractGroupObjectController implements GroupObjectLogicsSupplier {
    protected final ClientFormController form;
    protected final ClientFormLayout formLayout;

    protected final LogicsSupplier logicsSupplier;

    protected PanelController panel;
    protected final ToolbarView toolbarView;
    protected FilterController filter;

    public AbstractGroupObjectController(ClientFormController form, LogicsSupplier logicsSupplier, ClientFormLayout formLayout, ClientToolbar toolbar) {
        this.form = form;
        this.logicsSupplier = logicsSupplier;
        this.formLayout = formLayout;

        if (toolbar == null || !toolbar.visible) {
            toolbarView = null;
        } else {
            toolbarView = new ToolbarView(toolbar);
            formLayout.add(toolbar, toolbarView);
        }
    }

    public ClientFormController getForm() {
        return form;
    }

    @Override
    public List<ClientObject> getObjects() {
        return logicsSupplier.getObjects();
    }

    public void addToToolbar(Component component) {
        toolbarView.addComponent(component);
    }

    public boolean hasPanelProperty(ClientPropertyDraw property) {
        return panel.containsProperty(property);
    }
}
