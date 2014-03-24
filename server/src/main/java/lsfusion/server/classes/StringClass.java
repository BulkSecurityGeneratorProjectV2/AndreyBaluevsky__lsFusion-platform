package lsfusion.server.classes;

import lsfusion.base.BaseUtils;
import lsfusion.base.ExtInt;
import lsfusion.interop.Data;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.query.TypeEnvironment;
import lsfusion.server.data.sql.SQLSyntax;
import lsfusion.server.data.type.ParseException;
import lsfusion.server.data.type.Type;
import lsfusion.server.logics.ServerResourceBundle;

import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.Format;
import java.util.ArrayList;
import java.util.Collection;

import static java.lang.Math.max;

public class StringClass extends DataClass {

    private final static Collection<StringClass> strings = new ArrayList<StringClass>();

    public final static StringClass text = getv(ExtInt.UNLIMITED);
    public final boolean blankPadded;
    public final boolean caseInsensitive;
    public final ExtInt length;

    public Format getReportFormat() {
        return null;
    }

    public Class getReportJavaClass() {
        return String.class;
    }

    public Object getDefaultValue() {
        return "";
    }

    public String getString(Object value, SQLSyntax syntax) {
        return "'" + value + "'";
    }

    @Override
    public Object castValue(Object object, Type typeFrom) {
        if(!blankPadded && typeFrom instanceof StringClass && ((StringClass)typeFrom).blankPadded)
            return BaseUtils.rtrim((String)object);
        return super.castValue(object, typeFrom);
    }

    @Override
    public boolean isSafeType(Object value) { // при полиморфных функциях странно себя ведет без explicit cast'а
        return false;
    }

    public void writeParam(PreparedStatement statement, int num, Object value, SQLSyntax syntax, TypeEnvironment typeEnv) throws SQLException {
        statement.setString(num, (String) value);
    }

    public String parseString(String s) throws ParseException {
        return s;
    }

    @Override
    public void serialize(DataOutputStream outStream) throws IOException {
        super.serialize(outStream);
        outStream.writeBoolean(blankPadded);
        outStream.writeBoolean(caseInsensitive);
        length.serialize(outStream);
    }

    protected StringClass(boolean blankPadded, ExtInt length, boolean caseInsensitive) {
        this(caseInsensitive ? ServerResourceBundle.getString("classes.insensitive.string") : ServerResourceBundle.getString("classes.string") + (blankPadded ? " (bp)" : ""), blankPadded, length, caseInsensitive);
    }

    protected StringClass(String caption, boolean blankPadded, ExtInt length, boolean caseInsensitive) {
        super(caption);
        this.blankPadded = blankPadded;
        this.length = length;
        this.caseInsensitive = caseInsensitive;

//        assert !blankPadded || !this.length.isUnlimited();
    }

    public int getMinimumWidth() {
        return 30;
    }

    public int getPreferredWidth() {
        if(length.isUnlimited())
            return 200;
        return Math.min(200, max(30, length.getValue() * 2));
    }

    public byte getTypeID() {
        return Data.STRING;
    }

    public DataClass getCompatible(DataClass compClass, boolean or) {
        if (!(compClass instanceof StringClass)) return null;

        StringClass stringClass = (StringClass) compClass;
        return get(BaseUtils.cmp(blankPadded, stringClass.blankPadded, or), BaseUtils.cmp(caseInsensitive, stringClass.caseInsensitive, or), length.cmp(stringClass.length, or));
    }

    public String getDB(SQLSyntax syntax, TypeEnvironment typeEnv) {
        boolean isUnlimited = length.isUnlimited();
        if(blankPadded) {
            if(isUnlimited)
                return syntax.getBPTextType();
            int lengthValue = length.getValue();
            return syntax.getStringType(lengthValue==0 ? 1 : lengthValue);
        }
        if(isUnlimited)
            return syntax.getTextType();
        int lengthValue = length.getValue();
        return syntax.getVarStringType(lengthValue==0? 1 : lengthValue);
    }

    public int getSQL(SQLSyntax syntax) {
        boolean isUnlimited = length.isUnlimited();
        if(blankPadded) {
            if(isUnlimited)
                return syntax.getBPTextSQL();
            return syntax.getStringSQL();
        }
        if(isUnlimited)
            return syntax.getTextSQL();
        return syntax.getVarStringSQL();
    }

    public boolean isSafeString(Object value) {
        return !value.toString().contains("'") && !value.toString().contains("\\");
    }

    public String read(Object value) {
        if (value == null) return null;

        if(blankPadded) {
            if(length.isUnlimited())
                return ((String)value);
            return BaseUtils.padr((String) value, length.getValue());
        }

        if(length.isUnlimited())
            return (String) value;
        return BaseUtils.truncate((String) value, length.getValue());
    }

    @Override
    public ExtInt getCharLength() {
        return length;
    }

    @Override
    public String getSID() {
        if (length == ExtInt.UNLIMITED) {
            return caseInsensitive ? "ITEXT" : "TEXT";
        }
        String sid = caseInsensitive ? "ISTRING" : "STRING";
        if (!blankPadded) {
            sid = "VAR" + sid;
        }
        return sid + "_" + length;
    }

    @Override
    public String getUserSID() {
        String userSID = getSID();
        if (length == ExtInt.UNLIMITED) {
            return userSID;
        } else {
            return userSID.replaceFirst("_", "[") + "]";
        }
    }
    
    @Override
    public Stat getTypeStat() {
        if(length.isUnlimited())
            return new Stat(100, 400);
        return new Stat(100, length.getValue());
    }

    public boolean calculateStat() {
        return length.less(new ExtInt(400));
    }

    public StringClass extend(int times) {
        if(length.isUnlimited())
            return this;
        return get(blankPadded, caseInsensitive, new ExtInt(length.getValue() * times));
    }

    public String toString() {
        return (caseInsensitive ? ServerResourceBundle.getString("classes.insensitive.string") : ServerResourceBundle.getString("classes.string")) + (blankPadded ? " (bp)" : "") + " " + length;
    }

    public static StringClass[] getArray(int... lengths) {
        StringClass[] result = new StringClass[lengths.length];
        for (int i = 0; i < lengths.length; i++) {
            result[i] = StringClass.get(lengths[i]);
        }
        return result;
    }

    public static StringClass get(final int length) {
        return get(new ExtInt(length));
    }

    public static StringClass get(final ExtInt length) {
        return get(false, length);
    }

    public static StringClass geti(final int length) {
        return geti(new ExtInt(length));
    }

    public static StringClass geti(final ExtInt length) {
        return get(true, length);
    }

    public static StringClass getv(final int length) {
        return getv(false, length);
    }

    public static StringClass getv(final ExtInt length) {
        return getv(false, length);
    }

    public static StringClass getvi(final ExtInt length) {
        return getv(true, length);
    }

    public static StringClass get(boolean blankPadded, boolean caseInsensitive, final int length) {
        return get(blankPadded, caseInsensitive, new ExtInt(length));
    }

    public static StringClass get(boolean blankPadded, boolean caseInsensitive, final ExtInt length) {
        return getCached(strings, length, blankPadded, caseInsensitive);
    }

    public static StringClass get(boolean caseInsensitive, final int length) {
        return get(caseInsensitive, new ExtInt(length));
    }

    public static StringClass get(boolean caseInsensitive, final ExtInt length) {
        return get(true, caseInsensitive, length);
    }

    public static StringClass getv(boolean caseInsensitive, final int length) {
        return getv(caseInsensitive, new ExtInt(length));
    }

    public static StringClass getv(boolean caseInsensitive, final ExtInt length) {
        return get(false, caseInsensitive, length);
    }

    private static StringClass getCached(Collection<StringClass> cached, ExtInt length, boolean blankPadded, boolean caseInsensitive) {
        for (StringClass string : cached) {
            if (string.length.equals(length) && string.blankPadded == blankPadded && string.caseInsensitive == caseInsensitive) {
                return string;
            }
        }

        StringClass string = new StringClass(blankPadded, length, caseInsensitive);

        cached.add(string);
        DataClass.storeClass(string);
        return string;
    }

    @Override
    public Object getInfiniteValue(boolean min) {
        if(min)
            return "";

        return super.getInfiniteValue(min);
    }
}
