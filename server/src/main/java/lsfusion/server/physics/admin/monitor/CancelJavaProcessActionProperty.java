package lsfusion.server.physics.admin.monitor;

import com.google.common.base.Throwables;
import lsfusion.server.base.ThreadUtils;
import lsfusion.server.data.DataObject;
import lsfusion.server.physics.dev.integration.internal.to.ScriptingAction;
import lsfusion.server.language.ScriptingLogicsModule;
import lsfusion.server.logics.action.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;

import java.sql.SQLException;
import java.util.Iterator;

public class CancelJavaProcessActionProperty extends ScriptingAction {
    private final ClassPropertyInterface integerInterface;

    public CancelJavaProcessActionProperty(ScriptingLogicsModule LM, ValueClass... classes) {
        super(LM, classes);

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        integerInterface = i.next();
    }

    @Override
    protected void executeCustom(ExecutionContext<ClassPropertyInterface> context) throws SQLException {
        try {
            DataObject currentObject = context.getDataKeyValue(integerInterface);
            Integer id = Integer.parseInt((String) findProperty("idThreadProcess[VARSTRING[10]]").read(context, currentObject));
            Thread thread = ThreadUtils.getThreadById(id);
            if (thread != null) {
                ThreadUtils.interruptThread(context, thread);
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
                     