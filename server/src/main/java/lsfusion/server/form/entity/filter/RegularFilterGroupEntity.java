package lsfusion.server.form.entity.filter;

import lsfusion.base.col.interfaces.immutable.ImList;
import lsfusion.base.identity.IdentityObject;
import lsfusion.server.form.entity.FormEntity;
import lsfusion.server.form.entity.GroupObjectEntity;
import lsfusion.server.form.entity.ObjectEntity;
import lsfusion.server.logics.mutables.NFFact;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.mutables.interfaces.NFOrderSet;
import lsfusion.server.logics.mutables.interfaces.NFProperty;

import java.util.HashSet;
import java.util.Set;

public class RegularFilterGroupEntity extends IdentityObject {

    public NFOrderSet<RegularFilterEntity> filters = NFFact.orderSet();

    private NFProperty<Integer> defaultFilterIndex = NFFact.property();

    // конструктор нельзя удалять - нужен для сериализации
    public RegularFilterGroupEntity() {
    }

    public RegularFilterGroupEntity(int iID, Version version) {
        ID = iID;
        defaultFilterIndex.set(-1, version);
    }

    public void addFilter(RegularFilterEntity filter, Version version) {
        filters.add(filter, version);
    }

    public void addFilter(RegularFilterEntity filter, boolean setDefault, Version version) {
        if (setDefault) {
            setDefault(filters.size(version), version);
        }
        filters.add(filter, version);
    }

    public void setDefault(int index, Version version) {
        defaultFilterIndex.set(index, version);
    }

    public int getDefault() {
        return defaultFilterIndex.get();
    }
    
    public int getFiltersCount(Version version) {
        return filters.size(version);
    }

    public ImList<RegularFilterEntity> getNFFilters(Version version) {
        return filters.getNFOrderSet(version);
    }

    public ImList<RegularFilterEntity> getFiltersList() {
        return filters.getList();
    }

    public GroupObjectEntity getToDraw(FormEntity form) {
        Set<ObjectEntity> groupObjects = new HashSet<>();

        // ищем самый нижний GroupObjectInstance, к которому применяется фильтр
        for (RegularFilterEntity regFilter : getFiltersList()) {
            groupObjects.addAll(regFilter.filter.getObjects());
        }

        return form.getApplyObject(groupObjects);
    }

    public GroupObjectEntity getNFToDraw(FormEntity form, Version version) {
        Set<ObjectEntity> groupObjects = new HashSet<>();

        // ищем самый нижний GroupObjectInstance, к которому применяется фильтр
        for (RegularFilterEntity regFilter : filters.getNFOrderSet(version)) {
            groupObjects.addAll(regFilter.filter.getObjects());
        }

        return form.getNFApplyObject(groupObjects, version);
    }

    public void finalizeAroundInit() {
        filters.finalizeChanges();
        defaultFilterIndex.finalizeChanges();
    }
}
