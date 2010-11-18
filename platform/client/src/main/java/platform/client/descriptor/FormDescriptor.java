package platform.client.descriptor;

import platform.base.BaseUtils;
import platform.client.Main;
import platform.base.context.ContextIdentityObject;
import platform.client.descriptor.filter.FilterDescriptor;
import platform.client.descriptor.filter.RegularFilterGroupDescriptor;
import platform.base.context.ApplicationContext;
import platform.base.context.IncrementView;
import platform.client.descriptor.property.PropertyDescriptor;
import platform.client.descriptor.property.PropertyInterfaceDescriptor;
import platform.client.logics.*;
import platform.client.logics.classes.ClientClass;
import platform.client.serialization.ClientIdentitySerializable;
import platform.client.serialization.ClientSerializationPool;
import platform.interop.form.layout.ContainerFactory;
import platform.interop.form.layout.FormContainerSet;
import platform.interop.form.layout.FunctionFactory;
import platform.interop.form.layout.GroupObjectContainerSet;
import platform.base.serialization.RemoteDescriptorInterface;

import java.io.*;
import java.util.*;

public class FormDescriptor extends ContextIdentityObject implements ClientIdentitySerializable {

    public ClientForm client;

    public String caption;
    public boolean isPrintForm;

    public List<GroupObjectDescriptor> groupObjects = new ArrayList<GroupObjectDescriptor>();
    public List<PropertyDrawDescriptor> propertyDraws = new ArrayList<PropertyDrawDescriptor>();
    public Set<FilterDescriptor> fixedFilters = new HashSet<FilterDescriptor>();
    public List<RegularFilterGroupDescriptor> regularFilterGroups = new ArrayList<RegularFilterGroupDescriptor>();
    public Map<PropertyDrawDescriptor, GroupObjectDescriptor> forceDefaultDraw = new HashMap<PropertyDrawDescriptor, GroupObjectDescriptor>();

    // по сути IncrementLazy
    IncrementView allPropertiesLazy;
    private List<PropertyObjectDescriptor> allProperties;
    public List<PropertyObjectDescriptor> getAllProperties() {
        if (allProperties == null)
            allProperties = getProperties(groupObjects, null);
        return allProperties;
    }

    IncrementView propertyObjectConstraint;
    IncrementView toDrawConstraint;
    IncrementView columnGroupConstraint;
    IncrementView propertyCaptionConstraint;

    IncrementView containerController;

    public FormDescriptor() {
        super();
    }

    // будем считать, что именно этот конструктор используется для создания новых форм
    public FormDescriptor(int ID) {
        super(ID);

        context = new ApplicationContext();
        client = new ClientForm(ID, context);

        initialize();

        setCaption("Новая форма (" + ID + ")");
        addFormDefaultContainers();
    }

    public List<PropertyDrawDescriptor> getAddPropertyDraws(GroupObjectDescriptor group) {
        List<PropertyDrawDescriptor> result = new ArrayList<PropertyDrawDescriptor>();
        if (group == null) return result; // предполагается, что для всей папки свойства, любое добавленное свойство будет в getGroupPropertyDraws
        for(PropertyDrawDescriptor propertyDraw : propertyDraws) // добавим новые свойства, предполагается что оно одно
            if(propertyDraw.getPropertyObject()==null && group.equals(propertyDraw.addGroup))
                result.add(propertyDraw);
        return result;
    }

    public List<PropertyDrawDescriptor> getGroupPropertyDraws(GroupObjectDescriptor group) {
        List<PropertyDrawDescriptor> result = new ArrayList<PropertyDrawDescriptor>();
        for (PropertyDrawDescriptor propertyDraw : propertyDraws)
            if (group == null || group.equals(propertyDraw.getGroupObject(groupObjects)))
                result.add(propertyDraw);
        return result;
    }

    public ApplicationContext getContext() {
        return context;
    }

    private abstract class IncrementPropertyConstraint implements IncrementView {

        public abstract boolean updateProperty(PropertyDrawDescriptor property);

        public void update(Object updateObject, String updateField) {
            List<PropertyDrawDescriptor> checkProperties;
            if (updateObject instanceof PropertyDrawDescriptor)
                checkProperties = Collections.singletonList((PropertyDrawDescriptor) updateObject);
            else
                checkProperties = new ArrayList<PropertyDrawDescriptor>(propertyDraws);

            for(PropertyDrawDescriptor checkProperty : checkProperties)
                if(!updateProperty(checkProperty)) // удаляем propertyDraw
                    removeFromPropertyDraws(checkProperty);
        }
    }

    IncrementView containerMover;

    // класс, который отвечает за автоматическое перемещение компонент внутри контейнеров при каких-либо изменениях структуры groupObject
    private class ContainerMover implements IncrementView {
        public void update(Object updateObject, String updateField) {
            moveContainer(propertyDraws);
            moveContainer(regularFilterGroups);
        }

        private <T extends ContainerMovable> void moveContainer(List<T> objects) {

            ClientContainer mainContainer = client.mainContainer;

            for (T object : objects) {
                ClientContainer newContainer = object.getDestinationContainer(mainContainer, groupObjects);
                if (newContainer != null && !newContainer.isAncestorOf(object.getClientComponent(mainContainer).container)) {
                    int insIndex = -1;
                    // сначала пробуем вставить перед объектом, который идет следующим в этом контейнере
                    for (int propIndex = objects.indexOf(object) + 1; propIndex < objects.size(); propIndex++) {
                        ClientComponent comp = objects.get(propIndex).getClientComponent(mainContainer);
                        if (newContainer.equals(comp.container)) {
                            insIndex = newContainer.children.indexOf(comp);
                            if (insIndex != -1)
                                break;
                        }
                    }
                    if (insIndex == -1) {
                        // затем пробуем вставить после объекта, который идет перед вставляемым в этом контейнере
                        for (int propIndex = objects.indexOf(object) - 1; propIndex >= 0; propIndex--) {
                            ClientComponent comp = objects.get(propIndex).getClientComponent(mainContainer);
                            if (newContainer.equals(comp.container)) {
                                insIndex = newContainer.children.indexOf(comp);
                                if (insIndex != -1) {
                                    insIndex++;
                                    break;
                                }
                            }
                        }
                    }

                    // если объект свойство не нашлось куда добавить, то его надо добавлять самым первым в контейнер
                    // иначе свойства будут идти после управляющих объектов
                    if (insIndex == -1) insIndex = 0;
                    newContainer.addToChildren(insIndex, object.getClientComponent(mainContainer));
                }
            }
        }
    }

    IncrementView containerRenamer;

    // класс, который отвечает за автоматическое перемещение компонент внутри контейнеров при каких-либо изменениях структуры groupObject
    private class ContainerRenamer implements IncrementView {
        public void update(Object updateObject, String updateField) {
            renameGroupObjectContainer();
        }

        private void renameGroupObjectContainer() {

            for (GroupObjectDescriptor group : groupObjects) {
                // по сути дублирует логику из GroupObjectContainerSet в плане установки caption для контейнера
                ClientContainer groupContainer = group.getClientComponent(client.mainContainer);
                if (groupContainer != null)
                    groupContainer.setTitle(group.client.getCaption());
            }
        }
    }

    public void customSerialize(ClientSerializationPool pool, DataOutputStream outStream, String serializationType) throws IOException {
        pool.writeString(outStream, caption);
        outStream.writeBoolean(isPrintForm);

        pool.serializeCollection(outStream, groupObjects);
        pool.serializeCollection(outStream, propertyDraws);
        pool.serializeCollection(outStream, fixedFilters);
        pool.serializeCollection(outStream, regularFilterGroups);
        pool.serializeMap(outStream, forceDefaultDraw);
    }

    public void customDeserialize(ClientSerializationPool pool, DataInputStream inStream) throws IOException {
        caption = pool.readString(inStream);
        isPrintForm = inStream.readBoolean();

        groupObjects = pool.deserializeList(inStream);
        propertyDraws = pool.deserializeList(inStream);
        fixedFilters = pool.deserializeSet(inStream);
        regularFilterGroups = pool.deserializeList(inStream);
        forceDefaultDraw = pool.deserializeMap(inStream);

        client = pool.context;

        initialize();
    }

    @Override
    public String toString() {
        return client.caption;
    }

    private void initialize() {

        allPropertiesLazy = new IncrementView() {
            public void update(Object updateObject, String updateField) {
                allProperties = null;
            }
        };

        addDependency("baseClass", allPropertiesLazy);
        addDependency("objects", allPropertiesLazy);
        addDependency(this, "groupObjects", allPropertiesLazy);

        // propertyObject подходит по интерфейсу и т.п.
        propertyObjectConstraint = new IncrementPropertyConstraint() {
            public boolean updateProperty(PropertyDrawDescriptor property) {
                return getAllProperties().contains(property.getPropertyObject());
            }
        };
        addDependency("propertyObject", propertyObjectConstraint);
        addDependency("baseClass", propertyObjectConstraint);
        addDependency("objects", propertyObjectConstraint);
        addDependency(this, "groupObjects", propertyObjectConstraint);

        // toDraw должен быть из groupObjects (можно убрать)
        toDrawConstraint = new IncrementPropertyConstraint() {
            public boolean updateProperty(PropertyDrawDescriptor property) {
                GroupObjectDescriptor toDraw = property.getToDraw();
                if (toDraw != null && property.getPropertyObject() != null && !property.getPropertyObject().getGroupObjects().contains(toDraw))
                    property.setToDraw(null);
                return true;
            }
        };
        addDependency("toDraw", toDrawConstraint);
        addDependency("propertyObject", toDrawConstraint);

        // toDraw должен быть из groupObjects (можно убрать)
        columnGroupConstraint = new IncrementPropertyConstraint() {
            public boolean updateProperty(PropertyDrawDescriptor property) {
                List<GroupObjectDescriptor> upGroups = property.getUpGroupObjects(groupObjects);
                List<GroupObjectDescriptor> columnGroups = property.getColumnGroupObjects();

                List<GroupObjectDescriptor> constrainedColumnGroups = BaseUtils.filterList(columnGroups, upGroups);
                if (!constrainedColumnGroups.equals(columnGroups))
                    property.setColumnGroupObjects(constrainedColumnGroups);
                return true;
            }
        };
        addDependency("propertyObject", columnGroupConstraint);
        addDependency("toDraw", columnGroupConstraint);
        addDependency("objects", columnGroupConstraint);
        addDependency(this, "groupObjects", columnGroupConstraint); // порядок тоже важен


        // propertyObject подходит по интерфейсу и т.п.
        propertyCaptionConstraint = new IncrementPropertyConstraint() {
            public boolean updateProperty(PropertyDrawDescriptor property) {
                PropertyObjectDescriptor propertyCaption = property.getPropertyCaption();
                if (propertyCaption != null && !getProperties(property.getColumnGroupObjects(), null).contains(propertyCaption))
                    property.setPropertyCaption(null);
                return true;
            }
        };
        addDependency("propertyObject", propertyCaptionConstraint);
        addDependency("propertyCaption", propertyCaptionConstraint);
        addDependency("columnGroupObjects", propertyCaptionConstraint);
        addDependency("baseClass", propertyCaptionConstraint);
        addDependency("objects", propertyCaptionConstraint);
        addDependency(this, "groupObjects", propertyCaptionConstraint);

        containerMover = new ContainerMover();
        addDependency("groupObjects", containerMover);
        addDependency("toDraw", containerMover);
        addDependency("filters", containerMover);
        addDependency("filter", containerMover);
        addDependency("propertyDraws", containerMover);
        addDependency("property", containerMover);
        addDependency("propertyObject", containerMover);
        addDependency("value", containerMover); // нужно, чтобы перемещать regularFilterGroup, при использовании фильтра Сравнение

        containerRenamer = new ContainerRenamer();
        addDependency("groupObjects", containerRenamer);
        addDependency("objects", containerRenamer);
        addDependency("baseClass", containerRenamer);

    }

    public ObjectDescriptor getObject(int objectID) {
        for (GroupObjectDescriptor group : groupObjects)
            for (ObjectDescriptor object : group.objects)
                if (object.getID() == objectID)
                    return object;
        return null;
    }

    public List<PropertyObjectDescriptor> getProperties(GroupObjectDescriptor groupObject) {
        if (groupObject == null) return getAllProperties();
        return getProperties(groupObjects.subList(0, groupObjects.indexOf(groupObject) + 1), groupObject);
    }

    public static List<PropertyObjectDescriptor> getProperties(Collection<GroupObjectDescriptor> groupObjects, GroupObjectDescriptor toDraw) {
        Collection<ObjectDescriptor> objects = new ArrayList<ObjectDescriptor>();
        Map<Integer, Integer> objectMap = new HashMap<Integer, Integer>();
        for (GroupObjectDescriptor groupObject : groupObjects) {
            objects.addAll(groupObject.objects);
            for (ObjectDescriptor object : groupObject.objects) {
                objectMap.put(object.getID(), groupObject.getID());
            }
        }
        return getProperties(objects, toDraw == null ? new ArrayList<ObjectDescriptor>() : toDraw.objects, Main.remoteLogics, objectMap, false, false);
    }

    public static List<PropertyObjectDescriptor> getProperties(Collection<GroupObjectDescriptor> groupObjects, RemoteDescriptorInterface remote, ArrayList<GroupObjectDescriptor> toDraw, boolean isCompulsory, boolean isAny) {
        Collection<ObjectDescriptor> objects = new ArrayList<ObjectDescriptor>();
        Map<Integer, Integer> objectMap = new HashMap<Integer, Integer>();
        for (GroupObjectDescriptor groupObject : groupObjects) {
            objects.addAll(groupObject.objects);
            for (ObjectDescriptor object : groupObject.objects) {
                objectMap.put(object.getID(), groupObject.getID());
            }
        }
        ArrayList<ObjectDescriptor> objList = new ArrayList<ObjectDescriptor>();
        for (GroupObjectDescriptor groupObject : toDraw) {
            objList.addAll(groupObject.objects);
        }
        return getProperties(objects, objList, remote, objectMap, isCompulsory, isAny);
    }

    public static List<PropertyObjectDescriptor> getProperties(Collection<ObjectDescriptor> objects, Collection<ObjectDescriptor> atLeastOne, RemoteDescriptorInterface remote, Map<Integer, Integer> objectMap, boolean isCompulsory, boolean isAny) {
        Map<Integer, ObjectDescriptor> idToObjects = new HashMap<Integer, ObjectDescriptor>();
        Map<Integer, ClientClass> classes = new HashMap<Integer, ClientClass>();
        for (ObjectDescriptor object : objects) {
            ClientClass cls = object.getBaseClass();
            if (cls != null) {
                idToObjects.put(object.getID(), object);
                classes.put(object.getID(), object.getBaseClass());
            }
        }

        List<PropertyObjectDescriptor> result = new ArrayList<PropertyObjectDescriptor>();
        for (PropertyDescriptorImplement<Integer> implement : getProperties(remote, classes, BaseUtils.filterValues(idToObjects, atLeastOne).keySet(), objectMap, isCompulsory, isAny))
            result.add(new PropertyObjectDescriptor(implement.property, BaseUtils.join(implement.mapping, idToObjects)));
        return result;
    }

    public static <K> Collection<PropertyDescriptorImplement<K>> getProperties(RemoteDescriptorInterface remote, Map<K, ClientClass> classes) {
        // todo:
        return new ArrayList<PropertyDescriptorImplement<K>>();
    }

    public static Collection<PropertyDescriptorImplement<Integer>> getProperties(RemoteDescriptorInterface remote, Map<Integer, ClientClass> classes, Collection<Integer> atLeastOne, Map<Integer, Integer> objectMap, boolean isCompulsory, boolean isAny) {
        try {
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();

            DataOutputStream dataStream = new DataOutputStream(outStream);

            dataStream.writeInt(classes.size());
            for (Map.Entry<Integer, ClientClass> intClass : classes.entrySet()) {
                dataStream.writeInt(intClass.getKey());
                intClass.getValue().serialize(dataStream);
                if (atLeastOne.contains(intClass.getKey())) {
                    dataStream.writeInt(objectMap.get(intClass.getKey()));
                } else {
                    dataStream.writeInt(-1);
                }
            }

            DataInputStream inStream = new DataInputStream(new ByteArrayInputStream(remote.getPropertyObjectsByteArray(outStream.toByteArray(), isCompulsory, isAny)));
            ClientSerializationPool pool = new ClientSerializationPool();

            List<PropertyDescriptorImplement<Integer>> result = new ArrayList<PropertyDescriptorImplement<Integer>>();
            int size = inStream.readInt();
            for (int i = 0; i < size; i++) {
                PropertyDescriptor implementProperty = (PropertyDescriptor) pool.deserializeObject(inStream);
                Map<PropertyInterfaceDescriptor, Integer> mapInterfaces = new HashMap<PropertyInterfaceDescriptor, Integer>();
                for (int j = 0; j < implementProperty.interfaces.size(); j++)
                    mapInterfaces.put((PropertyInterfaceDescriptor) pool.deserializeObject(inStream), inStream.readInt());
                result.add(new PropertyDescriptorImplement<Integer>(implementProperty, mapInterfaces));
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean moveGroupObject(GroupObjectDescriptor groupFrom, GroupObjectDescriptor groupTo) {
        return moveGroupObject(groupFrom, groupObjects.indexOf(groupTo) + (groupObjects.indexOf(groupFrom) > groupObjects.indexOf(groupTo) ? 0 : 1));
    }

    public boolean moveGroupObject(GroupObjectDescriptor groupFrom, int index) {

        moveClientComponent(groupFrom.getClientComponent(client.mainContainer), getElementTo(groupObjects, groupFrom, index).getClientComponent(client.mainContainer));

        BaseUtils.moveElement(groupObjects, groupFrom, index);
        BaseUtils.moveElement(client.groupObjects, groupFrom.client, index);

        updateDependency(this, "groupObjects");

        return true;
    }

    public boolean movePropertyDraw(PropertyDrawDescriptor propFrom, PropertyDrawDescriptor propTo) {
        return movePropertyDraw(propFrom, propertyDraws.indexOf(propTo) + (propertyDraws.indexOf(propFrom) > propertyDraws.indexOf(propTo) ? 0 : 1));
    }

    public boolean movePropertyDraw(PropertyDrawDescriptor propFrom, int index) {

        moveClientComponent(propFrom.client, getElementTo(propertyDraws, propFrom, index).client);

        BaseUtils.moveElement(propertyDraws, propFrom, index);
        BaseUtils.moveElement(client.propertyDraws, propFrom.client, index);

        updateDependency(this, "propertyDraws");
        return true;
    }

    private static <T> T getElementTo(List<T> list, T elemFrom, int index) {
        if (index == -1) {
            return list.get(list.size() - 1);
        } else {
            return list.get(index + (list.indexOf(elemFrom) >= index ? 0 : -1));
        }
    }

    public void setCaption(String caption) {
        this.caption = caption;
        client.caption = caption;

        updateDependency(this, "caption");
    }

    public String getCaption() {
        return caption;
    }

    private static void moveClientComponent(ClientComponent compFrom, ClientComponent compTo) {
        if (compFrom.container.equals(compTo.container)) {
            compFrom.container.moveChild(compFrom, compTo);
        }
    }

    public boolean addToPropertyDraws(PropertyDrawDescriptor propertyDraw) {
        propertyDraws.add(propertyDraw);
        client.propertyDraws.add(propertyDraw.client);

        updateDependency(this, "propertyDraws");
        return true;
    }

    public boolean removeFromPropertyDraws(PropertyDrawDescriptor propertyDraw) {
        propertyDraws.remove(propertyDraw);
        client.removePropertyDraw(propertyDraw.client);

        updateDependency(this, "propertyDraws");
        return true;
    }

    public boolean addToGroupObjects(GroupObjectDescriptor groupObject) {

        groupObjects.add(groupObject);
        client.groupObjects.add(groupObject.client);

        addGroupObjectDefaultContainers(groupObject, groupObjects);

        updateDependency(this, "groupObjects");
        return true;
    }

    public boolean removeFromGroupObjects(GroupObjectDescriptor groupObject) {
        groupObjects.remove(groupObject);
        client.removeGroupObject(groupObject.client);

        updateDependency(this, "groupObjects");
        return true;
    }

    public List<RegularFilterGroupDescriptor> getRegularFilterGroups() {
        return regularFilterGroups;
    }

    public void addToRegularFilterGroups(RegularFilterGroupDescriptor filterGroup) {
        regularFilterGroups.add(filterGroup);
        client.addToRegularFilterGroups(filterGroup.client);
        updateDependency(this, "regularFilterGroups");
    }

    public void removeFromRegularFilterGroups(RegularFilterGroupDescriptor filterGroup) {
        regularFilterGroups.remove(filterGroup);
        client.removeFromRegularFilterGroups(filterGroup.client);
        updateDependency(this, "regularFilterGroups");
    }


    public static byte[] serialize(FormDescriptor form) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        DataOutputStream dataStream = new DataOutputStream(outStream);
        new ClientSerializationPool().serializeObject(dataStream, form);
        new ClientSerializationPool().serializeObject(dataStream, form.client);

        return outStream.toByteArray();
}

    public static FormDescriptor deserialize(byte[] richDesignByteArray, byte[] formEntityByteArray) throws IOException {
        ApplicationContext context = new ApplicationContext();
        ClientForm richDesign = new ClientSerializationPool(context)
                .deserializeObject(
                        new DataInputStream(
                                new ByteArrayInputStream(richDesignByteArray)));

        return new ClientSerializationPool(richDesign, context)
                .deserializeObject(
                        new DataInputStream(
                                new ByteArrayInputStream(formEntityByteArray)));
    }

    private class FormContainerFactory implements ContainerFactory<ClientContainer> {
        public ClientContainer createContainer() {
            return new ClientContainer(getContext());
        }
    }

    private class FormFunctionFactory implements FunctionFactory<ClientFunction> {
        public ClientFunction createFunction() {
            return new ClientFunction(getContext());
        }
    }

    private void addFormDefaultContainers() {
        FormContainerSet.fillContainers(client, new FormContainerFactory(), new FormFunctionFactory());
    }

    private void addGroupObjectDefaultContainers(GroupObjectDescriptor group, List<GroupObjectDescriptor> groupObjects) {

        GroupObjectContainerSet<ClientContainer, ClientComponent> set = GroupObjectContainerSet.create(group.client, new FormContainerFactory());

        // вставляем контейнер после предыдущего
        int groupIndex = groupObjects.indexOf(group);
        int index = -1;
        if (groupIndex > 0) {
            index = client.mainContainer.children.indexOf(groupObjects.get(groupIndex-1).getClientComponent(client.mainContainer));
            if (index != -1)
                index++;
            else
                index = client.mainContainer.children.size();
        } else
            index = 0;

        client.mainContainer.add(index, set.getGroupContainer());
    }
}
