package lsfusion.server.logics;

import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.OldProperty;

public class ApplyUpdatePrevEvent implements ApplySingleEvent {
    public final OldProperty property;

    @Override
    public CalcProperty getProperty() {
        return property;
    }

    public ApplyUpdatePrevEvent(OldProperty property) {
        this.property = property;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof ApplyUpdatePrevEvent && property.equals(((ApplyUpdatePrevEvent) o).property);
    }

    @Override
    public int hashCode() {
        return property.hashCode();
    }

    @Override
    public String toString() {
        return property.toString();
    }
}
