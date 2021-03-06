package lsfusion.server.data.type;

import lsfusion.server.classes.IntegerClass;
import lsfusion.server.data.SQLSession;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.SQLSyntax;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public abstract class AbstractType<T> extends AbstractReader<T> implements Type<T> {

    public boolean isSafeType() {
        return true;
    }

    public String getCast(String value, SQLSyntax syntax, TypeEnvironment typeEnv) {
        return getCast(value, syntax, typeEnv, null);
    }
    public String getCast(String value, SQLSyntax syntax, TypeEnvironment typeEnv, Type typeFrom) {
        return "CAST(" + value + " AS " + getDB(syntax, typeEnv) + ")";
    }

    // CAST который возвращает NULL, если не может этого сделать 
    public String getSafeCast(String value, SQLSyntax syntax, TypeEnvironment typeEnv, Type typeFrom) {
        if(hasSafeCast()) {
            typeEnv.addNeedSafeCast(this);
            return syntax.getSafeCastNameFnc(this) + "(" + value + ")";
        }
        return getCast(value, syntax, typeEnv, typeFrom);
    }
    
    public boolean hasSafeCast() {
        return false;
    }

    protected abstract void writeParam(PreparedStatement statement, int num, Object value, SQLSyntax syntax) throws SQLException;
    public void writeParam(PreparedStatement statement, SQLSession.ParamNum num, Object value, SQLSyntax syntax) throws SQLException {
        writeParam(statement, num.get(), value, syntax);
    }
    public void writeNullParam(PreparedStatement statement, SQLSession.ParamNum num, SQLSyntax syntax) throws SQLException {
        statement.setNull(num.get(), getSQL(syntax));
    }

    public String getParamFunctionDB(SQLSyntax syntax, TypeEnvironment typeEnv) {
        return getDB(syntax, typeEnv);
    }

    public void write(DataOutputStream out) throws IOException {
        out.writeUTF(getSID());
    }

    protected abstract int getBaseDotNetSize();

    @Override
    public int getDotNetSize() {
        return getBaseDotNetSize() + 1; // для boolean
    }

    public boolean useIndexedJoin() {
        return false;
    }

    @Override
    public boolean isFlex() {
        return false;
    }

    protected static boolean isParseNullValue(String value) {
        return value.equals("");
    }

    protected static String getParseNullValue() {
        return "";
    }

    public static Type getUnknownTypeNull() { // хак для общения нетипизированными параметрами
        return IntegerClass.instance;
    }

    @Override
    public T parseHTTP(Object o, Charset charset) throws ParseException {
        String s = (String) o;
        if(isParseNullValue(s))
            return null;
        return parseString(s);
    }

    @Override
    public Object formatHTTP(T value, Charset charset) {
        if(value == null)
            return getParseNullValue();
        return formatString(value);
    }
}
