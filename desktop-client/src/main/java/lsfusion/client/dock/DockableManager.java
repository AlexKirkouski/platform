package lsfusion.client.dock;

import bibliothek.gui.dock.common.CControl;
import bibliothek.gui.dock.common.CWorkingArea;
import bibliothek.gui.dock.common.MultipleCDockableFactory;
import bibliothek.gui.dock.common.event.CDockableAdapter;
import bibliothek.gui.dock.common.event.CDockableLocationEvent;
import bibliothek.gui.dock.common.event.CDockableLocationListener;
import bibliothek.gui.dock.common.intern.CDockable;
import bibliothek.gui.dock.common.mode.ExtendedMode;
import lsfusion.client.EditReportInvoker;
import lsfusion.client.MainFrame;
import lsfusion.client.navigator.ClientNavigator;
import lsfusion.interop.form.RemoteFormInterface;
import lsfusion.interop.form.ReportGenerationData;
import net.sf.jasperreports.engine.JRException;

import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DockableManager {
    private CControl control;

    private ClientDockableFactory dockableFactory;

    private CWorkingArea formArea;

    private DockableRepository forms;
    
    private ExtendedMode mode = ExtendedMode.NORMALIZED;
    private boolean internalModeChangeOnSetVisible = false;

    public List<ClientDockable> openedForms = new ArrayList<>();

    public DockableManager(CControl control, ClientNavigator mainNavigator) {
        this.control = control;
        this.dockableFactory = new ClientDockableFactory(mainNavigator);
        this.formArea = control.createWorkingArea("Form area");
        this.forms = new DockableRepository();

        control.addMultipleDockableFactory("page", dockableFactory);
    }

    public DockableRepository getForms() {
        return forms;
    }

    public CWorkingArea getFormArea() {
        return formArea;
    }

    public ClientDockableFactory getDockableFactory() {
        return dockableFactory;
    }

    public CControl getControl() {
        return control;
    }

    private void openForm(final ClientDockable page) {
        page.addCDockableStateListener(new DockableVisibilityListener());
        page.addCDockableLocationListener(new CDockableLocationListener() {
            @Override
            public void changed(CDockableLocationEvent event) {
                if (event.getOldShowing() != event.getNewShowing()) {
                    page.onShowingChanged(event.getOldShowing(), event.getNewShowing());
                }
            }
        });

        formArea.add(page);

        internalModeChangeOnSetVisible = true;
        page.setVisible(true);
        internalModeChangeOnSetVisible = false;
        
        page.setExtendedMode(mode);
        page.toFront();
        page.requestFocusInWindow();

        page.onOpened();
        openedForms.add(page);
    }

    public ClientFormDockable openForm(ClientNavigator navigator, String canonicalName, String formSID, boolean forbidDuplicate, RemoteFormInterface remoteForm, byte[] firstChanges, MainFrame.FormCloseListener closeListener) throws IOException, ClassNotFoundException, JRException {
        ClientFormDockable page;
        if (MainFrame.forbidDuplicateForms && forbidDuplicate && forms.getFormsList().contains(formSID)) {
            page = (ClientFormDockable) control.getCDockable(control.getCDockableCount() - forms.getFormsList().size() + forms.getFormsList().indexOf(formSID));
            if(page != null) {
                page.toFront();
                page.requestFocusInWindow();
            }
        } else {
            page = new ClientFormDockable(navigator, canonicalName, formSID, remoteForm, this, closeListener, firstChanges);
            openForm(page);
        }
        return page;
    }

    public Integer openReport(ReportGenerationData generationData, String printerName, EditReportInvoker editInvoker) throws IOException, ClassNotFoundException {
        ClientReportDockable page = new ClientReportDockable(generationData, this, printerName, editInvoker);
        openForm(page);
        return page.pageCount;
    }

    public void openReport(File file) throws JRException {
        openForm(new ClientReportDockable(file, this));
    }

    private class ClientDockableFactory implements MultipleCDockableFactory<ClientDockable, ClientDockableLayout> {
        ClientNavigator mainNavigator;

        public ClientDockableFactory(ClientNavigator mainNavigator) {
            this.mainNavigator = mainNavigator;
        }

        public ClientDockableLayout create() {
            return new ClientDockableLayout();
        }

        public ClientDockable read(ClientDockableLayout layout) {
            return null;
        }

        public ClientDockableLayout write(ClientDockable dockable) {
            return new ClientDockableLayout(dockable.getCanonicalName());
        }

        public boolean match(ClientDockable dockable, ClientDockableLayout layout) {
            return false;
        }
    }

    private class DockableVisibilityListener extends CDockableAdapter {
        @Override
        public void visibilityChanged(CDockable cdockable) {
            ClientDockable dockable = (ClientDockable) cdockable;

            String canonicalName = dockable.getCanonicalName();
            if (dockable.isVisible()) {
                forms.add(canonicalName);
            } else {
                forms.remove(canonicalName);
                control.removeDockable(dockable);

                dockable.onClosed();
                openedForms.remove(dockable);
            }
        }

        @Override
        public void maximized(CDockable dockable) {
            if (!internalModeChangeOnSetVisible) {
                DockableManager.this.mode = forms.getFormsList().isEmpty() ? ExtendedMode.NORMALIZED : ExtendedMode.MAXIMIZED;
            }
        }

        @Override
        public void normalized(CDockable dockable) {
            if (!internalModeChangeOnSetVisible) {
                DockableManager.this.mode = ExtendedMode.NORMALIZED;
            }
        }
    }
}
