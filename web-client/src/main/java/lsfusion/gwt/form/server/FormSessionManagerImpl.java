package lsfusion.gwt.form.server;

import lsfusion.client.logics.ClientForm;
import lsfusion.client.logics.ClientFormChanges;
import lsfusion.client.serialization.ClientSerializationPool;
import lsfusion.gwt.base.server.LogicsAwareDispatchServlet;
import lsfusion.gwt.base.server.spring.BusinessLogicsProvider;
import lsfusion.gwt.base.server.spring.InvalidateListener;
import lsfusion.gwt.form.server.convert.ClientComponentToGwtConverter;
import lsfusion.gwt.form.server.convert.ClientFormChangesToGwtConverter;
import lsfusion.gwt.form.shared.view.*;
import lsfusion.interop.RemoteLogicsInterface;
import lsfusion.interop.action.FormClientAction;
import lsfusion.interop.form.ColumnUserPreferences;
import lsfusion.interop.form.FormUserPreferences;
import lsfusion.interop.form.GroupObjectUserPreferences;
import lsfusion.interop.form.RemoteFormInterface;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.*;

import static java.util.Collections.synchronizedMap;
import static lsfusion.gwt.form.server.convert.StaticConverters.convertFont;

public class FormSessionManagerImpl implements FormSessionManager, InitializingBean, DisposableBean, InvalidateListener {
    @Autowired
    private BusinessLogicsProvider blProvider;

    private int nextFormId = 0;
    private final Map<String, FormSessionObject> currentForms = synchronizedMap(new HashMap<String, FormSessionObject>());

    public FormSessionManagerImpl() {}

    public GForm createForm(String canonicalName, String formSID, RemoteFormInterface remoteForm, Object[] immutableMethods, byte[] firstChanges, String tabSID, LogicsAwareDispatchServlet<RemoteLogicsInterface> servlet) throws IOException {
        FormClientAction.methodNames = FormClientAction.methodNames; // чтобы не потерять
        ClientForm clientForm = new ClientSerializationPool().deserializeObject(new DataInputStream(new ByteArrayInputStream(immutableMethods != null ? (byte[])immutableMethods[2] : remoteForm.getRichDesignByteArray())));

        GForm gForm = new ClientComponentToGwtConverter().convertOrCast(clientForm);

        gForm.sID = formSID;
        gForm.canonicalName = canonicalName;
        gForm.sessionID = nextFormSessionID();

        if (firstChanges != null) {
            gForm.initialFormChanges = ClientFormChangesToGwtConverter.getInstance().convertOrCast(
                    new ClientFormChanges(new DataInputStream(new ByteArrayInputStream(firstChanges)), clientForm),
                    -1,
                    blProvider
            );
        }

        FormUserPreferences formUP = immutableMethods != null ? (FormUserPreferences)immutableMethods[0] : remoteForm.getUserPreferences();
        
        if (formUP != null) {
            gForm.userPreferences = new GFormUserPreferences(convertUserPreferences(gForm, formUP.getGroupObjectGeneralPreferencesList()), 
                                                            convertUserPreferences(gForm, formUP.getGroupObjectUserPreferencesList()));
        }

        currentForms.put(gForm.sessionID, new FormSessionObject(clientForm, remoteForm, tabSID));

        return gForm;
    }
    
    private List<GGroupObjectUserPreferences> convertUserPreferences(GForm gForm,  List<GroupObjectUserPreferences> groupObjectUserPreferences) {
        ArrayList<GGroupObjectUserPreferences> gGroupObjectUPList = new ArrayList<>();
        for (GroupObjectUserPreferences groupObjectUP : groupObjectUserPreferences) {
            HashMap<String, GColumnUserPreferences> gColumnUPMap = new HashMap<>();
            for (Map.Entry<String, ColumnUserPreferences> entry : groupObjectUP.getColumnUserPreferences().entrySet()) {
                ColumnUserPreferences columnUP = entry.getValue();
                gColumnUPMap.put(entry.getKey(), new GColumnUserPreferences(columnUP.userHide, columnUP.userCaption, columnUP.userPattern, columnUP.userWidth, columnUP.userOrder, columnUP.userSort, columnUP.userAscendingSort));
            }
            GFont userFont = convertFont(groupObjectUP.fontInfo);
            GGroupObject groupObj = gForm.getGroupObject(groupObjectUP.groupObjectSID);
            if (groupObj != null && groupObj.grid.font != null && groupObj.grid.font.size != 0) {
                if (userFont.size == 0) {
                    userFont.size = groupObj.grid.font.size;
                }
                userFont.family = groupObj.grid.font.family;
            } else {
                if (userFont.size == 0) {
                    userFont.size = GFont.DEFAULT_FONT_SIZE;
                }
                userFont.family = GFont.DEFAULT_FONT_FAMILY;
            }
            gGroupObjectUPList.add(new GGroupObjectUserPreferences(gColumnUPMap, groupObjectUP.groupObjectSID, userFont, groupObjectUP.pageSize, groupObjectUP.headerHeight, groupObjectUP.hasUserPreferences));
            gForm.addFont(userFont); // добавляем к используемым шрифтам с целью подготовить FontMetrics
        }
        return gGroupObjectUPList;
    } 

    private String nextFormSessionID() {
        return "form" + nextFormId++ ;
    }

    @Override
    public void onInvalidate() {
//        cleanSessionForms();
    }

    private void cleanSessionForms() {
        currentForms.clear();
    }

    public FormSessionObject getFormSessionObject(String formSessionID) {
        FormSessionObject formObject = getFormSessionObjectOrNull(formSessionID);

        if (formObject == null) {
            throw new RuntimeException("Форма не найдена.");
        }

        return formObject;
    }

    @Override
    public FormSessionObject getFormSessionObjectOrNull(String formSessionID) {
        return currentForms.get(formSessionID);
    }

    public FormSessionObject removeFormSessionObject(String formSessionID) {
        return currentForms.remove(formSessionID);
    }

    @Override
    public void removeFormSessionObjects(String tabSID) {
        Collection<String> sessionIDs = new HashSet<>(currentForms.keySet());
        for (String sessionID : sessionIDs) {
            if (currentForms.get(sessionID).tabSID.equals(tabSID)) {
                currentForms.remove(sessionID); // по хорошему надо вызывать remoteForm.close (по аналогии с RemoteNavigator), если остались открытые вкладки (так как если их нет, всю работу выполнит RemoteNavigator.close) - но это редкий и нестандартный случай так что пока делать не будем 
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(blProvider, "businessLogicProvider must be specified");
        blProvider.addInvalidateListener(this);
    }

    @Override
    public void destroy() throws Exception {
        blProvider.removeInvalidateListener(this);
    }
}
