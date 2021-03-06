package lsfusion.server.session;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.data.OperationOwner;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.SQLSession;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.PropertyInterface;

import java.sql.SQLException;

public class IncrementTableProps extends IncrementProps {

    public final TableProps tableProps = new TableProps();

    @Override
    public String toString() {
        return tableProps.toString();
    }

    public IncrementTableProps() {
    }
    
    public boolean isEmpty() {
        return getProperties().isEmpty();
    }

    public <P extends PropertyInterface> IncrementTableProps(CalcProperty<P> property, PropertyChangeTableUsage<P> table) throws SQLException, SQLHandledException {
        add(property, table);
    }

    public ImSet<CalcProperty> getProperties() {
        return tableProps.getProperties();
    }

    public boolean contains(CalcProperty property) {
        return tableProps.contains(property);
    }

    public <P extends PropertyInterface> PropertyChangeTableUsage<P> getTable(CalcProperty<P> property) {
        return tableProps.getTable(property);
    }

    public <P extends PropertyInterface> PropertyChange<P> getPropertyChange(CalcProperty<P> property) {
        return tableProps.getPropertyChange(property);
    }

    public <P extends PropertyInterface> void add(CalcProperty<P> property, PropertyChangeTableUsage<P> changeTable) throws SQLException, SQLHandledException {
        assert !tableProps.contains(property);
        tableProps.add(property, changeTable);

        eventChange(property, true);
    }

    public <P extends PropertyInterface> void remove(CalcProperty<P> property, SQLSession sql, OperationOwner owner) throws SQLException, SQLHandledException {
        assert tableProps.contains(property);
        tableProps.remove(property, sql, owner);

        eventChange(property, true);
    }

    public void clear(SQLSession session, OperationOwner owner) throws SQLException, SQLHandledException {
        eventChanges(tableProps.getProperties());

        tableProps.clear(session, owner);
    }

    public long getMaxCount(CalcProperty property) {
        PropertyChangeTableUsage table = tableProps.getTable(property);
        if(table != null)
            return table.getCount();
        return 0;
    }

    @Override
    public String out() {
        return "\nincrementprops : " + BaseUtils.tab(tableProps.out());
    }
}
