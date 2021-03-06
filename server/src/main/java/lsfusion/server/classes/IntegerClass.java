package lsfusion.server.classes;

import lsfusion.interop.Data;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.SQLSyntax;
import lsfusion.server.data.type.ParseException;
import lsfusion.server.logics.i18n.LocalizedString;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class IntegerClass extends IntClass<Integer> {

    public final static IntegerClass instance = new IntegerClass();

    static {
        DataClass.storeClass(instance);
    }

    protected IntegerClass() { super(LocalizedString.create("{classes.integer}")); }

    public Class getReportJavaClass() {
        return Integer.class;
    }

    public byte getTypeID() {
        return Data.INTEGER;
    }

    public int getWhole() {
        return 10;
    }

    public int getPrecision() {
        return 0;
    }

    @Override
    protected boolean isNegative(Integer value) {
        return value < 0;
    }
    @Override
    public boolean isPositive(Integer value) {
        return value > 0;
    }

    public String getDB(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return syntax.getIntegerType();
    }

    public String getDotNetType(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return "SqlInt32";
    }

    public String getDotNetRead(String reader) {
        return reader + ".ReadInt32()";
    }
    public String getDotNetWrite(String writer, String value) {
        return writer + ".Write(" + value + ");";
    }

    @Override
    public int getBaseDotNetSize() {
        return 4;
    }

    public int getSQL(SQLSyntax syntax) {
        return syntax.getIntegerSQL();
    }

    public Integer read(Object value) {
        if(value==null) return null;
        return ((Number)value).intValue();
    }

    @Override
    public Integer read(ResultSet set, SQLSyntax syntax, String name) throws SQLException {
        int anInt = set.getInt(name);
        if(set.wasNull())
            return null;
        return anInt;
    }

    public void writeParam(PreparedStatement statement, int num, Object value, SQLSyntax syntax) throws SQLException {
        statement.setInt(num, (Integer)value);
    }

    public Integer getDefaultValue() {
        return 0;
    }

    public Integer parseString(String s) throws ParseException {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            throw new ParseException("error parsing int", e);
        }
    }

    public String getSID() {
        return "INTEGER";
    }

    @Override
    public Number getInfiniteValue(boolean min) {
        return min ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
}
