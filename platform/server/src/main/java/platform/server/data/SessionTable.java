package platform.server.data;

import platform.base.BaseUtils;
import platform.server.classes.ConcreteClass;
import platform.server.data.where.classes.ClassWhere;
import platform.server.data.where.Where;
import platform.server.data.sql.SQLSyntax;
import platform.server.logics.DataObject;
import platform.server.logics.ObjectValue;
import platform.server.data.SQLSession;
import platform.server.data.expr.BaseExpr;
import platform.server.data.expr.Expr;
import platform.server.data.expr.ValueExpr;
import platform.server.data.expr.where.CompareWhere;
import platform.server.data.expr.cases.ExprCaseList;
import platform.server.data.query.Query;
import platform.server.caches.MapValues;
import platform.server.caches.HashValues;
import platform.server.caches.MapValuesIterable;

import java.sql.SQLException;
import java.util.*;

// временная таблица на момент сессии
public abstract class SessionTable<This extends SessionTable<This>> extends Table implements MapValues<This> {

    // конструктор чистой структуры
    protected SessionTable(String name) {
        super(name);
        rows = new HashMap<Map<KeyField, DataObject>, Map<PropertyField, ObjectValue>>();
    }

    // конструктор добавления записей
    protected SessionTable(String name, ClassWhere<KeyField> classes, Map<PropertyField, ClassWhere<Field>> propertyClasses, Map<Map<KeyField, DataObject>, Map<PropertyField, ObjectValue>> rows) {
        super(name,classes,propertyClasses);
        this.rows = rows;
    }

    public String getName(SQLSyntax Syntax) {
        return Syntax.getSessionTableName(name);
    }

    // в явную хранимые ряды
    public final static int MAX_ROWS = 1;
    protected final Map<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> rows;

    @Override
    public platform.server.data.query.Join<PropertyField> joinAnd(final Map<KeyField, ? extends BaseExpr> joinImplement) {
        if(rows==null) return super.joinAnd(joinImplement); // если рядов много то Join'им

        return new platform.server.data.query.Join<PropertyField>() {

            public Expr getExpr(PropertyField property) {
                ExprCaseList result = new ExprCaseList();
                for(Map.Entry<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> row : rows.entrySet())
                    result.add(CompareWhere.compareValues(joinImplement,row.getKey()),row.getValue().get(property).getExpr());
                return result.getExpr();                
            }

            public Where getWhere() {
                Where result = Where.FALSE;
                for(Map.Entry<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> row : rows.entrySet())
                    result = result.or(CompareWhere.compareValues(joinImplement,row.getKey()));
                return result;
            }

            public Collection<PropertyField> getProperties() {
                return properties;
            }
        };
    }

    public int hashValues(HashValues hashValues) {
        int hash = 0;
        if(rows!=null)
            for(Map.Entry<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> row : rows.entrySet())
                hash += MapValuesIterable.hash(row.getKey(),hashValues) ^ MapValuesIterable.hash(row.getValue(),hashValues); 
        return hash * 31 + super.hashCode();
    }

    public Set<ValueExpr> getValues() {
        Set<ValueExpr> result = new HashSet<ValueExpr>();
        if(rows!=null)
            for(Map.Entry<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> row : rows.entrySet()) {
                MapValuesIterable.enumValues(result,row.getKey());
                MapValuesIterable.enumValues(result,row.getValue());
            }
        return result;
    }

    public This translate(Map<ValueExpr, ValueExpr> mapValues) {
        Map<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> transRows = null;
        if(rows!=null) {
            transRows = new HashMap<Map<KeyField, DataObject>, Map<PropertyField, ObjectValue>>();
            for(Map.Entry<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> row : rows.entrySet())
                transRows.put(MapValuesIterable.translate(row.getKey(),mapValues),MapValuesIterable.translate(row.getValue(),mapValues));
        }
        return createThis(classes, propertyClasses, transRows);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && BaseUtils.nullEquals(rows,((SessionTable)obj).rows);
    }

    @Override
    public int hashCode() {
        return BaseUtils.nullHash(rows) * 31 + super.hashCode();
    }

    public abstract This createThis(ClassWhere<KeyField> classes, Map<PropertyField, ClassWhere<Field>> propertyClasses, Map<Map<KeyField, DataObject>, Map<PropertyField, ObjectValue>> rows);

    public This insertRecord(SQLSession session, Map<KeyField, DataObject> keyFields, Map<PropertyField, ObjectValue> propFields, boolean update) throws SQLException {

        Map<KeyField, ConcreteClass> keyClasses = DataObject.getMapClasses(keyFields);

        Map<PropertyField, ClassWhere<Field>> orPropertyClasses = new HashMap<PropertyField, ClassWhere<Field>>(); 
        for(Map.Entry<PropertyField,ObjectValue> propertyField : propFields.entrySet()) {
            ClassWhere<Field> existedPropertyClasses = propertyClasses.get(propertyField.getKey());
            if(propertyField.getValue() instanceof DataObject) {
                ClassWhere<Field> insertClasses = new ClassWhere<Field>(BaseUtils.merge(keyClasses,
                        Collections.singletonMap(propertyField.getKey(),((DataObject)propertyField.getValue()).objectClass)));
                if(existedPropertyClasses!=null)
                    insertClasses = insertClasses.or(existedPropertyClasses);
                orPropertyClasses.put(propertyField.getKey(),insertClasses);
            } else
                orPropertyClasses.put(propertyField.getKey(),existedPropertyClasses!=null?existedPropertyClasses:ClassWhere.<Field>STATIC(false));
        }

        Map<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> orRows = null;
        if(rows!=null) {
            orRows = new HashMap<Map<KeyField, DataObject>, Map<PropertyField, ObjectValue>>(rows);
            Map<PropertyField, ObjectValue> prevValue = orRows.put(keyFields,propFields);
            assert update || prevValue==null;

            if(orRows.size()>MAX_ROWS) {
                for(Map.Entry<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> row : orRows.entrySet())
                    session.insertRecord(this,row.getKey(),row.getValue());
                orRows = null;
            }
        } else
            if(update)
                session.updateInsertRecord(this,keyFields,propFields);
            else
                session.insertRecord(this,keyFields,propFields);
        
        return createThis(classes.or(new ClassWhere<KeyField>(keyClasses)), orPropertyClasses, orRows);
    }

    // "обновляет" ключи в таблице
    public This writeKeys(SQLSession session,List<Map<KeyField,DataObject>> writeRows) throws SQLException {
        session.deleteKeyRecords(this, new HashMap<KeyField, Object>());

        ClassWhere<KeyField> writeClasses = new ClassWhere<KeyField>();
        for(Map<KeyField, DataObject> row : writeRows) {
            writeClasses = writeClasses.or(new ClassWhere<KeyField>(DataObject.getMapClasses(row)));
            session.insertRecord(this,row,new HashMap<PropertyField, ObjectValue>());
        }

        return createThis(writeClasses, new HashMap<PropertyField, ClassWhere<Field>>(), null);
    }

    public This writeKeys(SQLSession session, Query<KeyField,PropertyField> query) throws SQLException {
        session.deleteKeyRecords(this, new HashMap<KeyField, Object>());
        
        session.insertSelect(new ModifyQuery(this,query));

        Map<PropertyField,ClassWhere<Field>> insertClasses = new HashMap<PropertyField, ClassWhere<Field>>();
        for(PropertyField field : query.properties.keySet())
            insertClasses.put(field,query.<Field>getClassWhere(Collections.singleton(field)));

        return createThis(query.<KeyField>getClassWhere(new ArrayList<PropertyField>()),insertClasses, null);
    }

    public This deleteRecords(SQLSession session, Map<KeyField,DataObject> keys) throws SQLException {
        assert keys.keySet().equals(keys);

        if(rows==null) {
            session.deleteKeyRecords(this, DataObject.getMapValues(keys));
            return (This)this;
        } else {
            Map<Map<KeyField,DataObject>,Map<PropertyField,ObjectValue>> removeRows = new HashMap<Map<KeyField, DataObject>, Map<PropertyField, ObjectValue>>(rows);
            removeRows.remove(keys);
            return createThis(classes,propertyClasses,removeRows);
        }
    }
}
