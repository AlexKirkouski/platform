package lsfusion.server.form.entity;

import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImOrderSet;
import lsfusion.interop.PropertyEditType;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.form.entity.filter.NotNullFilterEntity;
import lsfusion.server.form.entity.filter.OrFilterEntity;
import lsfusion.server.logics.SystemEventsLogicsModule;
import lsfusion.server.logics.i18n.LocalizedString;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.property.*;

import java.util.Arrays;

import static lsfusion.server.logics.PropertyUtils.mapCalcImplement;
import static lsfusion.server.logics.PropertyUtils.readCalcImplements;

/// Common usage:
/// LP<?> property - logging property
/// LP<?> logProperty = addLProp(property);
/// ...
/// LogFormEntity logForm = new LogFormEntity("FormSID", "FormCaption", property, logProperty, SomeBusinessLogics.this);
/// addPropertyDraw(addMFAProp("Caption", logForm, logForm.params), paramObjectEntities);

public class LogFormEntity extends FormEntity {
    public ObjectEntity[] params;
    SystemEventsLogicsModule systemEventsLM;
    ObjectEntity[] entities;
    ObjectEntity objSession;
    LCP<?> logProperty;
    LCP<?> logDropProperty;
    LCP<?> property;
    public boolean lazyInit;

    public LogFormEntity(String canonicalName, LocalizedString caption, LCP<?> property, LCP<?> logProperty, LCP<?> logDropProperty, SystemEventsLogicsModule systemEventsLM) {
        super(canonicalName, caption, systemEventsLM.getVersion());

        this.systemEventsLM = systemEventsLM;
        this.logProperty = logProperty;
        this.logDropProperty = logDropProperty;
        this.property = property;

        Version version = getVersion();

        ValueClass[] classes = getValueClassesList(property);
        entities = new ObjectEntity[classes.length + 1];

        // не в одном group Object так как при поиске значений может давать "декартово произведение" (не все субд умеют нормально разбирать такие случаи), вообще должно решаться в общем случае, но пока так
        int index = 1;
        for (ValueClass valueClass : classes) {
            String sID = "param" + index;

            GroupObjectEntity paramGroup = new GroupObjectEntity(genID(), sID + "Group");
            paramGroup.setPanelClassView();

            ObjectEntity obj = new ObjectEntity(genID(), sID, valueClass, valueClass.getCaption());
            entities[index-1] = obj;
            paramGroup.add(obj);
            index++;

            addGroupObject(paramGroup, version);
        }

        params = Arrays.copyOf(entities, classes.length);

        GroupObjectEntity logGroup = new GroupObjectEntity(genID(), "logGroup");
        objSession = new ObjectEntity(genID(), "session", systemEventsLM.session, LocalizedString.create("{form.entity.session}"));
        entities[classes.length] = objSession;
        logGroup.add(objSession);

        addGroupObject(logGroup, version);

        initProperties();
        
        // finalizeInit внутри initProperties
    }

    private Version getVersion() {
        return systemEventsLM.getVersion();
    }

    public void initProperties() {
        Version version = getVersion();

        for (ObjectEntity obj : entities) {
            addPropertyDraw(obj, version, systemEventsLM.baseLM.recognizeGroup, true);
        }

        addPropertyDraw(logProperty, version, entities);

        ImList<PropertyClassImplement> recognizePropImpls =
                systemEventsLM.baseLM.recognizeGroup.getProperties(new ValueClassWrapper(property.property.getValueClass(ClassType.logPolicy)), true, version);

        for (PropertyClassImplement impl : recognizePropImpls) {
            if(impl instanceof CalcPropertyClassImplement) {
                CalcPropertyClassImplement<?> calcImpl = ((CalcPropertyClassImplement)impl);
                int paramCnt = logProperty.property.interfaces.size();
                ImOrderSet<JoinProperty.Interface> listInterfaces = JoinProperty.getInterfaces(paramCnt);

                LCP lpMainProp = new LCP(calcImpl.property);

                Object[] params = new Object[paramCnt + 1];
                params[0] = logProperty;
                for (int i = 0; i < paramCnt; i++) {
                    params[i+1] = i+1;
                }
                JoinProperty<?> jProp = new JoinProperty(impl.property.caption,
                        listInterfaces, mapCalcImplement(lpMainProp, readCalcImplements(listInterfaces, params)));
                jProp.drawOptions.inheritDrawOptions(impl.property.drawOptions);
                LCP<?> ljProp = new LCP<>(jProp, listInterfaces);
                addPropertyDraw(ljProp, version, entities);
            }
        }

        addFixedFilter(new OrFilterEntity(new NotNullFilterEntity(addPropertyObject(logProperty, entities)), new NotNullFilterEntity(addPropertyObject(logDropProperty, entities))), version);

        setNFEditType(PropertyEditType.READONLY, version);

        finalizeInit(version);
    }

    private static ValueClass[] getValueClassesList(LCP<?> property) {
        ImMap<PropertyInterface, ValueClass> interfaces = (ImMap<PropertyInterface, ValueClass>) property.property.getInterfaceClasses(ClassType.logPolicy);
        ValueClass[] classes = new ValueClass[interfaces.size()];
        int index = 0;
        for (PropertyInterface pi : property.property.interfaces) {
            classes[index++] = interfaces.get(pi);
        }
        return classes;
    }
}
