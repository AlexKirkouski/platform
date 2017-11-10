package lsfusion.server.classes;

import lsfusion.interop.Data;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.SQLSyntax;
import lsfusion.server.data.type.ParseException;
import lsfusion.server.logics.i18n.LocalizedString;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LongClass extends IntClass<Long> {

    public final static LongClass instance = new LongClass();

    static {
        DataClass.storeClass(instance);
    }

    private LongClass() { super(LocalizedString.create("{classes.long.integer}")); }

    public int getPreferredWidth() { return 65; }

    public Class getReportJavaClass() {
        return Long.class;
    }

    public byte getTypeID() {
        return Data.LONG;
    }

    public int getWhole() {
        return 20;
    }

    public int getPrecision() {
        return 0;
    }

    @Override
    public boolean isSafeType() {
        return false; // в рекурсии например не safetype, когда 1 скажем по умолчанию integer и они не кастятся друг к другу
    }

    @Override
    protected boolean isNegative(Long value) {
        return value < 0;
    }

    public String getDB(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return syntax.getLongType();
    }

    public String getDotNetType(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return "SqlInt64";
    }

    public String getDotNetRead(String reader) {
        return reader + ".ReadInt64()";
    }
    public String getDotNetWrite(String writer, String value) {
        return writer + ".Write(" + value + ");";
    }

    @Override
    public int getBaseDotNetSize() {
        return 8;
    }

    public int getSQL(SQLSyntax syntax) {
        return syntax.getLongSQL();
    }

    public Long read(Object value) {
        if(value==null) return null;
        return ((Number)value).longValue();
    }

    @Override
    public Long read(ResultSet set, SQLSyntax syntax, String name) throws SQLException {
        long anLong = set.getLong(name);
        if(set.wasNull())
            return null;
        return anLong;
    }

    public void writeParam(PreparedStatement statement, int num, Object value, SQLSyntax syntax) throws SQLException {
        statement.setLong(num, (Long)value);
    }

    public Long getDefaultValue() {
        return 0L;
    }

    public Long parseString(String s) throws ParseException {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            throw new ParseException("error parsing long", e);
        }
    }

    public String getSID() {
        return "LONG";
    }

    @Override
    public Number getInfiniteValue(boolean min) {
        return min ? Long.MIN_VALUE : Long.MAX_VALUE;
    }

    @Override
    public Stat getTypeStat() {
        return new Stat(Long.MAX_VALUE);
    }
}
