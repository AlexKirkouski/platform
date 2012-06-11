package platform.server.form.instance.remote;

import com.google.common.base.Throwables;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.engine.xml.JRXmlWriter;
import org.apache.log4j.Logger;
import platform.base.BaseUtils;
import platform.base.ERunnable;
import platform.base.OrderedMap;
import platform.base.Pair;
import platform.interop.ClassViewType;
import platform.interop.Order;
import platform.interop.Scroll;
import platform.interop.action.ClientAction;
import platform.interop.action.ProcessFormChangesClientAction;
import platform.interop.action.UpdateCurrentClassClientAction;
import platform.interop.form.FormUserPreferences;
import platform.interop.form.RemoteFormInterface;
import platform.interop.form.ServerResponse;
import platform.server.ContextAwareDaemonThreadFactory;
import platform.server.RemoteContextObject;
import platform.server.classes.ConcreteClass;
import platform.server.classes.ConcreteCustomClass;
import platform.server.form.entity.CalcPropertyObjectEntity;
import platform.server.form.entity.FormEntity;
import platform.server.form.entity.GroupObjectHierarchy;
import platform.server.form.entity.ObjectEntity;
import platform.server.form.instance.*;
import platform.server.form.instance.filter.FilterInstance;
import platform.server.form.instance.listener.RemoteFormListener;
import platform.server.form.view.ContainerView;
import platform.server.form.view.FormView;
import platform.server.form.view.report.ReportDesignGenerator;
import platform.server.logics.BusinessLogics;
import platform.server.logics.DataObject;
import platform.server.logics.ObjectValue;
import platform.server.serialization.SerializationType;
import platform.server.serialization.ServerContext;
import platform.server.serialization.ServerSerializationPool;
import platform.server.session.DataSession;

import java.io.*;
import java.lang.ref.WeakReference;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static platform.base.BaseUtils.deserializeObject;
import static platform.base.BaseUtils.serializeObject;
import static platform.server.form.entity.GroupObjectHierarchy.ReportNode;
import static platform.server.logics.ServerResourceBundle.getString;

// фасад для работы с клиентом
public class RemoteForm<T extends BusinessLogics<T>, F extends FormInstance<T>> extends RemoteContextObject implements RemoteFormInterface {
    private final static Logger logger = Logger.getLogger(RemoteForm.class);

    public final F form;
    public final FormView richDesign;

    private final WeakReference<RemoteFormListener> weakRemoteFormListener;

    public RemoteForm(F form, FormView richDesign, int port, RemoteFormListener remoteFormListener) throws RemoteException {
        super(port);

        releaseCurrentRequestLock();

        this.form = form;
        this.richDesign = richDesign;

        this.weakRemoteFormListener = new WeakReference<RemoteFormListener>(remoteFormListener);
        if (remoteFormListener != null) {
            remoteFormListener.formCreated(this);
        }
    }

    public RemoteFormListener getRemoteFormListener() {
        return weakRemoteFormListener.get();
    }

    private void emitExceptionIfHasActiveInvocation() {
        if (currentInvocation != null && currentInvocation.isPaused()) {
            //стопаем старый рабочий поток, чтобы можно было запустить новый без ожидания окончания старого
            currentInvocation.cancel();
            currentInvocation = null;
            throw new RuntimeException("There is already invocation executing...");
        }
    }

    public byte[] getReportHierarchyByteArray() {
        return processRMIRequest(new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                Map<String, List<String>> dependencies = form.entity.getReportHierarchy().getReportHierarchyMap();
                return getReportHierarchyByteArray(dependencies);
            }
        });
    }

    public byte[] getSingleGroupReportHierarchyByteArray(final int groupId) {
        return processRMIRequest(new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                Map<String, List<String>> dependencies = form.entity.getSingleGroupReportHierarchy(groupId).getReportHierarchyMap();
                return getReportHierarchyByteArray(dependencies);
            }
        });
    }

    private byte[] getReportHierarchyByteArray(Map<String, List<String>> dependencies) {
        try {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ObjectOutputStream objOut = new ObjectOutputStream(outStream);
            objOut.writeUTF(GroupObjectHierarchy.rootNodeName);
            objOut.writeObject(dependencies);
            return outStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getReportDesignsByteArray(final boolean toExcel, final FormUserPreferences userPreferences) {
        return processRMIRequest(new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                return getReportDesignsByteArray(toExcel, null, userPreferences);
            }
        });
    }

    /// Отчет по одной группе
    public byte[] getSingleGroupReportDesignByteArray(final boolean toExcel, final int groupId, final FormUserPreferences userPreferences) {
        return processRMIRequest(new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                return getReportDesignsByteArray(toExcel, groupId, userPreferences);
            }
        });
    }

    private byte[] getReportDesignsByteArray(boolean toExcel, Integer groupId, FormUserPreferences userPreferences) {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        try {
            ObjectOutputStream objOut = new ObjectOutputStream(outStream);
            Map<String, JasperDesign> res = getReportDesigns(toExcel, groupId, userPreferences);
            objOut.writeObject(res);
            return outStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Set<Integer> getGridGroups(Integer groupId) {
        Set<Integer> gridGroupsId = new HashSet<Integer>();
        for (GroupObjectInstance group : form.groups) {
            if (group.curClassView == ClassViewType.GRID && (groupId == null || groupId == group.getID())) {
                gridGroupsId.add(group.getID());
            }
        }
        return gridGroupsId;
    }

    public byte[] getReportSourcesByteArray() {
        return processRMIRequest(new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                GroupObjectHierarchy.ReportHierarchy hierarchy = form.entity.getReportHierarchy();
                ReportSourceGenerator<T> sourceGenerator = new ReportSourceGenerator<T>(form, hierarchy, getGridGroups(null));
                return getReportSourcesByteArray(sourceGenerator);
            }
        });
    }

    public byte[] getSingleGroupReportSourcesByteArray(final int groupId) throws RemoteException {
        return processRMIRequest(new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                ReportSourceGenerator<T> sourceGenerator = new ReportSourceGenerator<T>(form, form.entity.getSingleGroupReportHierarchy(groupId),
                                                                                        form.entity.getReportHierarchy(), getGridGroups(groupId));
                return getReportSourcesByteArray(sourceGenerator);
            }
        });
    }

    private byte[] getReportSourcesByteArray(ReportSourceGenerator<T> sourceGenerator) {
        try {
            Map<String, ReportData> sources = sourceGenerator.generate();
            ReportSourceGenerator.ColumnGroupCaptionsData columnGroupCaptions = sourceGenerator.getColumnGroupCaptions();
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            DataOutputStream dataStream = new DataOutputStream(outStream);

            dataStream.writeInt(sources.size());
            for (Map.Entry<String, ReportData> source : sources.entrySet()) {
                dataStream.writeUTF(source.getKey());
                source.getValue().serialize(dataStream);
            }

            int columnPropertiesCount = columnGroupCaptions.propertyObjects.size();
            dataStream.writeInt(columnPropertiesCount);

            serializePropertyObjects(dataStream, columnGroupCaptions.propertyObjects);

            dataStream.writeInt(columnGroupCaptions.data.size());
            for (Map.Entry<String, Map<List<Object>, Object>> entry : columnGroupCaptions.data.entrySet()) {
                dataStream.writeUTF(entry.getKey());
                Map<List<Object>, Object> value = entry.getValue();
                dataStream.writeInt(value.size());
                for (Map.Entry<List<Object>, Object> valueEntry : value.entrySet()) {
                    for (Object obj : valueEntry.getKey()) {
                        serializeObject(dataStream, obj);
                    }
                    serializeObject(dataStream, valueEntry.getValue());
                }
            }

            serializePropertyObjects(dataStream, columnGroupCaptions.columnObjects);

            for (Map.Entry<String, LinkedHashSet<List<Object>>> entry : columnGroupCaptions.columnData.entrySet()) {
                dataStream.writeUTF(entry.getKey());
                LinkedHashSet<List<Object>> value = entry.getValue();
                dataStream.writeInt(value.size());
                for (List<Object> list : value) {
                    for (Object obj : list) {
                        serializeObject(dataStream, obj);
                    }
                }
            }

            return outStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void serializePropertyObjects(DataOutputStream stream, Map<String, List<ObjectInstance>> objects) throws IOException {
        for (Map.Entry<String, List<ObjectInstance>> entry : objects.entrySet()) {
            stream.writeUTF(entry.getKey());
            stream.writeInt(entry.getValue().size());
            for (ObjectInstance object : entry.getValue()) {
                stream.writeInt(object.getID());
            }
        }
    }

    private GroupObjectHierarchy.ReportHierarchy getReportHierarchy(Integer groupId) {
        if (groupId == null) {
            return form.entity.getReportHierarchy();
        } else {
            return form.entity.getSingleGroupReportHierarchy(groupId);
        }
    }

    private InputStream getCustomReportInputStream(String sid, ReportNode node, boolean toExcel, Integer groupId) throws SQLException {
        InputStream iStream = null;
        if (node != null) {
            CalcPropertyObjectEntity reportPathProp = node.getGroupList().get(0).reportPathProp;
            if (reportPathProp != null) {
                CalcPropertyObjectInstance propInstance = form.instanceFactory.getInstance(reportPathProp);
                String reportPath = (String) propInstance.read(form);
                if (reportPath != null) {
                    String resourceName = "/" + getVariableCustomReportName(getReportPrefix(toExcel, groupId) + reportPath.trim());
                    iStream = getClass().getResourceAsStream(resourceName);
                }
            }
        }
        if (iStream == null) {
            String resourceName = "/" + getCustomReportName(sid, getDefaultReportSID(toExcel, groupId));
            iStream = getClass().getResourceAsStream(resourceName);
        }
        return iStream;
    }

    private Map<String, JasperDesign> getCustomReportDesigns(boolean toExcel, Integer groupId) {
        try {
            GroupObjectHierarchy.ReportHierarchy hierarchy = getReportHierarchy(groupId);
            Map<String, JasperDesign> designs = new HashMap<String, JasperDesign>();
            List<Pair<String, ReportNode>> nodes = new ArrayList<Pair<String, ReportNode>>();
            nodes.add(new Pair<String, ReportNode>(GroupObjectHierarchy.rootNodeName, null));
            for (GroupObjectHierarchy.ReportNode node : hierarchy.getAllNodes()) {
                nodes.add(new Pair<String, ReportNode>(node.getID(), node));
            }
            for (Pair<String, ReportNode> node : nodes) {
                InputStream iStream = getCustomReportInputStream(node.first, node.second, toExcel, groupId);
                // Если не нашли custom design для xls, пробуем найти обычный
                if (toExcel && iStream == null) {
                    iStream = getCustomReportInputStream(node.first, node.second, false, groupId);
                }
                if (iStream == null) {
                    return null;
                }
                JasperDesign subreport = JRXmlLoader.load(iStream);
                designs.put(node.first, subreport);
            }
            return designs;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, String> getReportPath(final boolean toExcel, final Integer groupId, final FormUserPreferences userPreferences) {
        return processRMIRequest(new Callable<Map<String, String>>() {
            @Override
            public Map<String, String> call() throws Exception {
                Map<String, String> ret = new HashMap<String, String>();

                String sid = getDefaultReportSID(toExcel, groupId);
                Map<String, JasperDesign> customDesigns = getCustomReportDesigns(toExcel, groupId);
                if (customDesigns != null) {
                    Set<String> keySet = customDesigns.keySet();
                    for (String key : keySet){
                        ret.put(
                                System.getProperty("user.dir") + "/src/main/resources/" + getCustomReportName(key, sid),
                                System.getProperty("user.dir") + "/target/classes/" + getCustomReportName(key, sid)
                        );
                    }
                } else {
                    Set<Integer> hidedGroupsId = new HashSet<Integer>();
                    for (GroupObjectInstance group : form.groups) {
                        if (group.curClassView == ClassViewType.HIDE || groupId != null && groupId != group.getID()) {
                            hidedGroupsId.add(group.getID());
                        }
                    }
                    try {
                        ReportDesignGenerator generator = new ReportDesignGenerator(richDesign, getReportHierarchy(groupId), hidedGroupsId, userPreferences, toExcel);
                        Map<String, JasperDesign> designs = generator.generate();
                        String reportName;
                        for (Map.Entry<String, JasperDesign> entry : designs.entrySet()) {
                            String id = entry.getKey();
                            reportName = getAutoReportName(id, sid);
                            new File(reportName).getParentFile().mkdirs();
                            JRXmlWriter.writeReport(JasperCompileManager.compileReport(entry.getValue()), reportName, "UTF-8");
                            ret.put(
                                    System.getProperty("user.dir") + "/" + reportName,
                                    System.getProperty("user.dir") + "/target/classes/reports/custom/" + sid
                            );

                        }

                    } catch (JRException e) {
                        throw new RuntimeException(getString("form.instance.error.creating.design"), e);
                    }
                }
                return ret;
            }
        });
    }

    private static final String xlsPrefix = "xls_";
    private static final String tablePrefix = "table";

    private String getReportPrefix(boolean toExcel, Integer groupId) {
        String prefix = (toExcel ? xlsPrefix : "");
        return prefix + (groupId == null ? "" : tablePrefix + form.getGroupObjectInstance(groupId).getSID() + "_");
    }

    private String getDefaultReportSID(boolean toExcel, Integer groupId) {
        return getReportPrefix(toExcel, groupId) + getSID();
    }

    private Map<String, JasperDesign> getReportDesigns(boolean toExcel, Integer groupId, FormUserPreferences userPreferences) {
        String sid = getDefaultReportSID(toExcel, groupId);
        Map<String, JasperDesign> customDesigns = getCustomReportDesigns(toExcel, groupId);
        if (customDesigns != null) {
            return customDesigns;
        }

        Set<Integer> hidedGroupsId = new HashSet<Integer>();
        for (GroupObjectInstance group : form.groups) {
            if (group.curClassView == ClassViewType.HIDE || groupId != null && groupId != group.getID()) {
                hidedGroupsId.add(group.getID());
            }
        }
        try {
            ReportDesignGenerator generator = new ReportDesignGenerator(richDesign, getReportHierarchy(groupId), hidedGroupsId, userPreferences, toExcel);
            Map<String, JasperDesign> designs = generator.generate();
            for (Map.Entry<String, JasperDesign> entry : designs.entrySet()) {
                String id = entry.getKey();
                String reportName = getAutoReportName(id, sid);

                new File(reportName).getParentFile().mkdirs();

                JRXmlWriter.writeReport(JasperCompileManager.compileReport(entry.getValue()), reportName, "UTF-8");
            }
            return designs;
        } catch (JRException e) {
            throw new RuntimeException(getString("form.instance.error.creating.design"), e);
        }
    }

    private String getVariableCustomReportName(String name) {
        if (!name.endsWith(".jrxml")) {
            name = name + ".jrxml";
        }
        return "reports/custom/" + name;
    }

    private String getCustomReportName(String name, String sid) {
        if (name.equals(GroupObjectHierarchy.rootNodeName)) {
            return "reports/custom/" + sid + ".jrxml";
        } else {
            return "reports/custom/" + sid + "_" + name + ".jrxml";
        }
    }

    private String getAutoReportName(String name, String sid) {
        if (name.equals(GroupObjectHierarchy.rootNodeName)) {
            return "reports/auto/" + sid + ".jrxml";
        } else {
            return "reports/auto/" + sid + "_" + name + ".jrxml";
        }
    }

    public byte[] getRichDesignByteArray() {
        return processRMIRequest(new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                //будем использовать стандартный OutputStream, чтобы кол-во передаваемых данных было бы как можно меньше
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                try {
                    new ServerSerializationPool(new ServerContext(richDesign)).serializeObject(new DataOutputStream(outStream), richDesign, SerializationType.GENERAL);
                    //            richDesign.serialize(new DataOutputStream(outStream));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return outStream.toByteArray();
            }
        });
    }

    public ServerResponse changePageSize(final int groupID, final Integer pageSize) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                GroupObjectInstance groupObject = form.getGroupObjectInstance(groupID);
                form.changePageSize(groupObject, pageSize);
            }
        });
    }

    public void gainedFocus() {
        processRMIRequest(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                form.gainedFocus();
                return null;
            }
        });
    }

    private byte[] getFormChangesByteArray() {
        try {
            FormChanges formChanges = form.endApply();

            if (logger.isDebugEnabled()) {
                formChanges.logChanges(form, logger);
            }

            return formChanges.serialize();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    public ServerResponse getRemoteChanges() throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                //ничего не делаем, просто даём по завершению выполниться prepareRemoteChangesResponse
            }
        });
    }

    private long formChangesIndexStamp = 0;
    private ServerResponse prepareRemoteChangesResponse(List<ClientAction> pendingActions) {
        if (rmiRequestsCount.get() > 1) {
            //todo: возможно стоит сохранять количество пропущенных изменений, и высылать таки их, если пропустили слишком много
            return new ServerResponse(pendingActions.toArray(new ClientAction[pendingActions.size()]), false);
        }

        byte[] formChanges = getFormChangesByteArray();

        List<ClientAction> resultActions = new ArrayList<ClientAction>();
        resultActions.add(new ProcessFormChangesClientAction(formChangesIndexStamp++, formChanges));

        if (updateCurrentClass != null) {
            ConcreteCustomClass currentClass = form.getObjectClass(updateCurrentClass);
            RemoteFormListener remoteFormListener = getRemoteFormListener();
            if (currentClass != null && remoteFormListener != null && remoteFormListener.currentClassChanged(currentClass)) {
                resultActions.add(new UpdateCurrentClassClientAction(currentClass.ID));
            }

            updateCurrentClass = null;
        }

        resultActions.addAll(pendingActions);
        return new ServerResponse(resultActions.toArray(new ClientAction[resultActions.size()]), false);
    }

    private Map<ObjectInstance, Object> deserializeKeysValues(byte[] keysArray) throws IOException {
        DataInputStream inStream = new DataInputStream(new ByteArrayInputStream(keysArray));

        Map<ObjectInstance, Object> mapValues = new HashMap<ObjectInstance, Object>();
        int cnt = inStream.readInt();
        for (int i = 0 ; i < cnt; ++i) {
            mapValues.put(form.getObjectInstance(inStream.readInt()), deserializeObject(inStream));
        }

        return mapValues;
    }

    private Map<ObjectInstance, DataObject> deserializePropertyKeys(PropertyDrawInstance<?> propertyDraw, byte[] columnKeys) throws IOException, SQLException {
        Map<ObjectInstance, DataObject> keys = new HashMap<ObjectInstance, DataObject>();
        Map<ObjectInstance, Object> dataKeys = deserializeKeysValues(columnKeys);

        for (Map.Entry<ObjectInstance, Object> e : dataKeys.entrySet()) {
            if (e.getValue() != null) {
                keys.put(e.getKey(), form.session.getDataObject(e.getValue(), e.getKey().getType()));
            }
        }

        return keys;
    }

    private Map<ObjectInstance, DataObject> deserializeGroupObjectKeys(GroupObjectInstance group, byte[] treePathKeys) throws IOException {
        return group.findGroupObjectValue(deserializeKeysValues(treePathKeys));
    }

    public ServerResponse changeGroupObject(final int groupID, final byte[] value) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                GroupObjectInstance groupObject = form.getGroupObjectInstance(groupID);
                Map<ObjectInstance, DataObject> valueToSet = deserializeGroupObjectKeys(groupObject, value);
                if (valueToSet == null) {
                    return;
                }

                groupObject.change(form.session, valueToSet);

                updateCurrentClass = groupObject.objects.iterator().next();

                if (logger.isInfoEnabled()) {
                    logger.info(String.format("changeGroupObject: [ID: %1$d]", groupObject.getID()));
                    logger.info("   keys: ");
                    for (Map.Entry<ObjectInstance, DataObject> entry : valueToSet.entrySet()) {
                        logger.info(String.format("     %1$s == %2$s", entry.getKey(), entry.getValue()));
                    }
                }
            }
        });
    }

    public ServerResponse expandGroupObject(final int groupId, final byte[] groupValues) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                GroupObjectInstance group = form.getGroupObjectInstance(groupId);
                Map<ObjectInstance, DataObject> valueToSet = deserializeGroupObjectKeys(group, groupValues);
                if (valueToSet != null) {
                    form.expandGroupObject(group, valueToSet);
                }
            }
        });
    }

    public ServerResponse collapseGroupObject(final int groupId, final byte[] groupValues) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                GroupObjectInstance group = form.getGroupObjectInstance(groupId);
                Map<ObjectInstance, DataObject> valueToSet = deserializeGroupObjectKeys(group, groupValues);
                if (valueToSet != null) {
                    form.collapseGroupObject(group, valueToSet);
                }
            }
        });
    }

    public ServerResponse moveGroupObject(final int parentGroupId, final byte[] parentKey, final int childGroupId, final byte[] childKey, final int index) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                GroupObjectInstance parentGroup = form.getGroupObjectInstance(parentGroupId);
                GroupObjectInstance childGroup = form.getGroupObjectInstance(childGroupId);
                //todo:
//            form.moveGroupObject(parentGroup, deserializeGroupObjectKeys(parentGroup, parentKey));
            }
        });
    }

    public ServerResponse changeGroupObject(final int groupID, final byte changeType) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                GroupObjectInstance groupObject = form.getGroupObjectInstance(groupID);
                form.changeGroupObject(groupObject, Scroll.deserialize(changeType));
                updateCurrentClass = groupObject.objects.iterator().next();
            }
        });
    }

    private ObjectInstance updateCurrentClass = null;

    public ServerResponse pasteExternalTable(final List<Integer> propertyIDs, final List<List<Object>> table) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                form.pasteExternalTable(propertyIDs, table);
            }
        });
    }

    public ServerResponse pasteMulticellValue(final Map<Integer, List<Map<Integer, Object>>> cells, final Object value) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                form.pasteMulticellValue(cells, value);
            }
        });
    }

    public ServerResponse changeGridClass(final int objectID, final int idClass) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                ((CustomObjectInstance) form.getObjectInstance(objectID)).changeGridClass(idClass);
            }
        });
    }

    public ServerResponse changeClassView(final int groupID, final ClassViewType classView) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                form.changeClassView(form.getGroupObjectInstance(groupID), classView);
            }
        });
    }

    public ServerResponse changePropertyOrder(final int propertyID, final byte modiType, final byte[] columnKeys) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                PropertyDrawInstance<?> propertyDraw = form.getPropertyDraw(propertyID);
                Map<ObjectInstance, DataObject> keys = deserializePropertyKeys(propertyDraw, columnKeys);
                propertyDraw.toDraw.changeOrder(((CalcPropertyObjectInstance<?>) propertyDraw.propertyObject).getRemappedPropertyObject(keys), Order.deserialize(modiType));
            }
        });
    }

    public int countRecords(final int groupObjectID) {
        return processRMIRequest(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return form.countRecords(groupObjectID);
            }
        });
    }

    public Object calculateSum(final int propertyID, final byte[] columnKeys) {
        return processRMIRequest(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                PropertyDrawInstance<?> propertyDraw = form.getPropertyDraw(propertyID);
                Map<ObjectInstance, DataObject> keys = deserializePropertyKeys(propertyDraw, columnKeys);
                return form.calculateSum(propertyDraw, keys);
            }
        });
    }

    public Map<List<Object>, List<Object>> groupData(final Map<Integer, List<byte[]>> groupMap, final Map<Integer, List<byte[]>> sumMap,
                                                     final Map<Integer, List<byte[]>> maxMap, final boolean onlyNotNull) {
        return processRMIRequest(new Callable<Map<List<Object>, List<Object>>>() {
            @Override
            public Map<List<Object>, List<Object>> call() throws Exception {
                List<Map<Integer, List<byte[]>>> inMaps = new ArrayList<Map<Integer, List<byte[]>>>(BaseUtils.toList(groupMap, sumMap, maxMap));
                List<Map<PropertyDrawInstance, List<Map<ObjectInstance, DataObject>>>> outMaps = new ArrayList<Map<PropertyDrawInstance, List<Map<ObjectInstance, DataObject>>>>();
                for (Map<Integer, List<byte[]>> one : inMaps) {
                    Map<PropertyDrawInstance, List<Map<ObjectInstance, DataObject>>> outMap = new OrderedMap<PropertyDrawInstance, List<Map<ObjectInstance, DataObject>>>();
                    for (Integer id : one.keySet()) {
                        PropertyDrawInstance<?> propertyDraw = form.getPropertyDraw(id);
                        List<Map<ObjectInstance, DataObject>> list = new ArrayList<Map<ObjectInstance, DataObject>>();
                        if (propertyDraw != null) {
                            for (byte[] columnKeys : one.get(id)) {
                                list.add(deserializePropertyKeys(propertyDraw, columnKeys));
                            }
                        }
                        outMap.put(propertyDraw, list);
                    }
                    outMaps.add(outMap);
                }
                return form.groupData(outMaps.get(0), outMaps.get(1), outMaps.get(2), onlyNotNull);
            }
        });
    }

    public ServerResponse setUserFilters(final byte[][] filters) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                for (GroupObjectInstance group : form.groups) {
                    group.clearUserFilters();
                }
                for (byte[] state : filters) {
                    FilterInstance filter = FilterInstance.deserialize(new DataInputStream(new ByteArrayInputStream(state)), form);
                    filter.getApplyObject().addUserFilter(filter);
                }
            }
        });
    }

    public ServerResponse setRegularFilter(final int groupID, final int filterID) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                form.setRegularFilter(form.getRegularFilterGroup(groupID), filterID);
            }
        });
    }

    public int getID() {
        return form.entity.getID();
    }

    public String getSID() {
        return processRMIRequest(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return form.entity.getSID();
            }
        });
    }

    public ServerResponse closedPressed() throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                form.formClose();
            }
        });
    }

    public ServerResponse okPressed() throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                form.formOk();
            }
        });
    }

    public ServerResponse setTabVisible(final int tabPaneID, final int tabIndex) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                form.setTabVisible((ContainerView) richDesign.findById(tabPaneID), richDesign.findById(tabIndex));
            }
        });
    }

    @Override
    public void saveUserPreferences(final FormUserPreferences preferences, final Boolean forAllUsers) throws RemoteException {
        processRMIRequest(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                form.saveUserPreferences(preferences, forAllUsers);
                return null;
            }
        });
    }

    @Override
    public FormUserPreferences loadUserPreferences() throws RemoteException {
        return processRMIRequest(new Callable<FormUserPreferences>() {
            @Override
            public FormUserPreferences call() throws Exception {
                return form.loadUserPreferences();
            }
        });
    }

    @Override
    public RemoteForm createRemoteForm(FormInstance formInstance) {
        try {
            return new RemoteForm<T, FormInstance<T>>(formInstance, formInstance.entity.getRichDesign(), exportPort, getRemoteFormListener());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public RemoteDialog createRemoteDialog(DialogInstance dialogInstance) {
        try {
            return new RemoteDialog(dialogInstance, dialogInstance.entity.getRichDesign(), exportPort, getRemoteFormListener());
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FormInstance createFormInstance(FormEntity formEntity, Map<ObjectEntity, DataObject> mapObjects, DataSession session, boolean isModal, boolean newSession, boolean checkOnOk, boolean interactive) throws SQLException {
        return form.createForm(formEntity, mapObjects, session, isModal, newSession, checkOnOk, interactive);
    }

    @Override
    public BusinessLogics getBL() {
        return form.BL;
    }

    /**
     * готовит форму для восстановленного подключения
     */
    public void invalidate() throws SQLException {
        form.refreshData();
        if (currentInvocation != null && currentInvocation.isPaused()) {
            try {
                currentInvocation.cancel();
            } catch (Exception e) {
                logger.warn("Exception was thrown, while invalidating form", e);
            }
        }
    }

    public ServerResponse changeProperty(final int propertyID, final byte[] fullKey, final byte[] value) throws RemoteException {
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                PropertyDrawInstance propertyDraw = form.getPropertyDraw(propertyID);
                Map<ObjectInstance, DataObject> keys = deserializePropertyKeys(propertyDraw, fullKey);
                ObjectValue changeValue = DataObject.getValue(deserializeObject(value), (ConcreteClass) propertyDraw.getEntity().propertyObject.property.getValueClass());
                form.executeEditAction(propertyDraw, ServerResponse.CHANGE, keys, changeValue);
            }
        });
    }

    public ServerResponse executeEditAction(final int propertyID, final byte[] columnKey, final String actionSID) throws RemoteException {
        emitExceptionIfHasActiveInvocation();
        return executeWithFormChanges(new ERunnable() {
            @Override
            public void run() throws Exception {
                PropertyDrawInstance propertyDraw = form.getPropertyDraw(propertyID);
                Map<ObjectInstance, DataObject> keys = deserializePropertyKeys(propertyDraw, columnKey);

                form.executeEditAction(propertyDraw, actionSID, keys);

                if (logger.isInfoEnabled()) {
                    logger.info(String.format("executeEditAction: [ID: %1$d, SID: %2$s]", propertyDraw.getID(), propertyDraw.getsID()));
                    if (keys.size() > 0) {
                        logger.info("   columnKeys: ");
                        for (Map.Entry<ObjectInstance, DataObject> entry : keys.entrySet()) {
                            logger.info(String.format("     %1$s == %2$s", entry.getKey(), entry.getValue()));
                        }
                    }

                    if (logger.isDebugEnabled()) {
                        logger.debug("   current object's values: ");
                        for (ObjectInstance obj : form.getObjects()) {
                            logger.debug(String.format("     %1$s == %2$s", obj, obj.getObjectValue()));
                        }
                    }
                }
            }
        });
    }

    private final ExecutorService pausablesExecutor = Executors.newCachedThreadPool(new ContextAwareDaemonThreadFactory(this));
    private RemotePausableInvocation currentInvocation = null;
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private ArrayBlockingQueue currentRequestLock = new ArrayBlockingQueue(1, true);
    private AtomicInteger rmiRequestsCount = new AtomicInteger();

    public void takeCurrentRequestLock() {
        try {
            currentRequestLock.take();
        } catch (InterruptedException e) {
            Throwables.propagate(e);
        }
    }

    public void releaseCurrentRequestLock() {
        try {
            currentRequestLock.put(this);
        } catch (InterruptedException e) {
            Throwables.propagate(e);
        }
    }

    private <T> T processRMIRequest(Callable<T> request) {
        takeCurrentRequestLock();
        try {
            return request.call();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            releaseCurrentRequestLock();
        }
    }

    private ServerResponse executeServerInvocation(RemotePausableInvocation invocation) throws RemoteException {
        rmiRequestsCount.incrementAndGet();
        takeCurrentRequestLock();

        currentInvocation = invocation;
        return invocation.execute();
    }

    private ServerResponse executeWithFormChanges(final ERunnable runnable) throws RemoteException {
        return executeServerInvocation(new RemotePausableInvocation(pausablesExecutor, this) {
            @Override
            protected ServerResponse callInvocation() throws Exception {
                runnable.run();
                return prepareRemoteChangesResponse(delayedActions);
            }

            @Override
            protected ServerResponse handleFinished() throws RemoteException {
                unlockNextRmiRequest();
                return super.handleFinished();
            }

            @Override
            protected ServerResponse handleThrows(Throwable t) throws RemoteException {
                unlockNextRmiRequest();
                return super.handleThrows(t);
            }

            private void unlockNextRmiRequest() {
                currentInvocation = null;
                int left = rmiRequestsCount.decrementAndGet();
                assert left >= 0;
                releaseCurrentRequestLock();
            }
        });
    }

    public ServerResponse continueServerInvocation(Object[] actionResults) throws RemoteException {
        return currentInvocation.resumeAfterUserInteraction(actionResults);
    }

    public ServerResponse throwInServerInvocation(Exception clientException) throws RemoteException {
        return currentInvocation.resumWithException(clientException);
    }

    public String getLogMessage() {
        return currentInvocation.getLogMessage();
    }

    public void delayUserInteraction(ClientAction action) {
        currentInvocation.delayUserInterfaction(action);
    }

    public Object[] requestUserInteraction(ClientAction... actions) {
        return currentInvocation.pauseForUserInteraction(actions);
    }
}
