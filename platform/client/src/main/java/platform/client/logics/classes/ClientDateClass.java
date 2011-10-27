package platform.client.logics.classes;

import jxl.write.DateTime;
import platform.client.ClientResourceBundle;
import platform.client.Main;
import platform.client.form.PropertyEditorComponent;
import platform.client.form.PropertyRendererComponent;
import platform.client.form.editor.DatePropertyEditor;
import platform.client.form.renderer.DatePropertyRenderer;
import platform.gwt.view.classes.GDateType;
import platform.gwt.view.classes.GType;
import platform.interop.ComponentDesign;
import platform.interop.Data;

import java.sql.Date;
import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;

public class ClientDateClass extends ClientDataClass implements ClientTypeClass {

    public final static ClientDateClass instance = new ClientDateClass();

    private final String sID = "DateClass";

    @Override
    public String getSID() {
        return sID;
    }

    public byte getTypeId() {
        return Data.DATE;
    }

    @Override
    public String getPreferredMask() {
        return "01.01.2001"; // пока так, хотя надо будет переделать в зависимости от Locale  
    }

    public Format getDefaultFormat() {
        return getSimpleDateFormat();
    }

    public PropertyRendererComponent getRendererComponent(Format format, String caption, ComponentDesign design) {
        return new DatePropertyRenderer(format, design);
    }

    public PropertyEditorComponent getComponent(Object value, Format format, ComponentDesign design) {
        return new DatePropertyEditor(value, (SimpleDateFormat) format, design);
    }

    private DateFormat getSimpleDateFormat() {
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
        if (Main.timeZone != null) {
            dateFormat.setTimeZone(Main.timeZone);
        }
        return dateFormat;
    }

    public Object parseString(String s) throws ParseException {
        try {
            return new java.sql.Date(getSimpleDateFormat().parse(s).getTime());
        } catch (Exception e) {
            throw new ParseException(s +  ClientResourceBundle.getString("logics.classes.can.not.be.converted.to.date"), 0);
        }
    }

    @Override
    public String formatString(Object obj) throws ParseException {
        if (obj != null) {
            return getSimpleDateFormat().format((Date) obj);
        }
        else return "";
    }

    @Override
    public String toString() {
        return ClientResourceBundle.getString("logics.classes.date");
    }

    @Override
    public GType getGwtType() {
        return GDateType.instance;
    }
}
