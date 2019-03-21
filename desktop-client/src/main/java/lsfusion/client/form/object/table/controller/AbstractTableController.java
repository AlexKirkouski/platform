package lsfusion.client.form.object.table.controller;

import lsfusion.client.form.controller.ClientFormController;
import lsfusion.client.form.design.view.ClientFormLayout;
import lsfusion.client.form.filter.user.controller.FilterController;
import lsfusion.client.form.object.ClientObject;
import lsfusion.client.form.object.panel.controller.PanelController;
import lsfusion.client.form.object.table.ClientToolbar;
import lsfusion.client.form.object.table.view.ToolbarView;
import lsfusion.client.form.property.ClientPropertyDraw;

import java.awt.*;
import java.util.List;

public abstract class AbstractTableController implements TableController {
    protected final ClientFormController formController;
    protected final ClientFormLayout formLayout;

    protected PanelController panel;
    protected final ToolbarView toolbarView;
    protected FilterController filter;

    public AbstractTableController(ClientFormController formController, ClientFormLayout formLayout, ClientToolbar toolbar) {
        this.formController = formController;
        this.formLayout = formLayout;

        if (toolbar == null || !toolbar.visible) {
            toolbarView = null;
        } else {
            toolbarView = new ToolbarView(toolbar);
            formLayout.add(toolbar, toolbarView);
        }
    }

    public ClientFormController getFormController() {
        return formController;
    }

    @Override
    public List<ClientObject> getObjects() {
        return formController.form.getObjects();
    }

    public void addToToolbar(Component component) {
        toolbarView.addComponent(component);
    }

    public boolean hasPanelProperty(ClientPropertyDraw property) {
        return panel.containsProperty(property);
    }
}
