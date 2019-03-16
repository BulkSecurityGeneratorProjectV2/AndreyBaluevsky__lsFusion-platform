package lsfusion.server.physics.admin.service.action;

import lsfusion.interop.action.MessageClientAction;
import lsfusion.server.data.sql.SQLSession;
import lsfusion.server.data.sql.exception.SQLHandledException;
import lsfusion.server.data.value.ObjectValue;
import lsfusion.server.logics.action.controller.context.ExecutionContext;
import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.property.classes.ClassPropertyInterface;
import lsfusion.server.physics.admin.service.RunService;
import lsfusion.server.physics.admin.service.ServiceLogicsModule;
import lsfusion.server.physics.dev.integration.internal.to.InternalAction;
import lsfusion.server.physics.exec.db.controller.manager.DBManager;

import java.sql.SQLException;
import java.util.Iterator;

import static lsfusion.server.base.controller.thread.ThreadLocalContext.localize;

public class ServiceDBMultiThreadActionProperty extends InternalAction {
    private ClassPropertyInterface threadCountInterface;
    ServiceLogicsModule serviceLM;
    public ServiceDBMultiThreadActionProperty(ServiceLogicsModule LM, ValueClass... classes) {
        super(LM, classes);
        this.serviceLM = LM;

        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        threadCountInterface = i.next();
    }

    @Override
    public void executeInternal(final ExecutionContext<ClassPropertyInterface> context) throws SQLException, SQLHandledException {

        final ObjectValue threadCountObject = context.getKeyValue(threadCountInterface);

        run(context, new RunService() {
            public void run(SQLSession session, boolean isolatedTransaction) throws SQLException, SQLHandledException {
                serviceLM.recalculateMultiThreadAction.execute(context, threadCountObject);
            }});

        run(context, new RunService() {
            public void run(SQLSession session, boolean isolatedTransaction) throws SQLException, SQLHandledException {
                serviceLM.recalculateClassesMultiThreadAction.execute(context, threadCountObject);
            }
        });

        context.getDbManager().analyzeDB(context.getSession().sql);

        run(context, new RunService() {
            public void run(SQLSession session, boolean isolatedTransaction) throws SQLException, SQLHandledException {
                serviceLM.recalculateFollowsMultiThreadAction.execute(context, threadCountObject);
            }
        });

        run(context, new RunService() {
            public void run(SQLSession session, boolean isolatedTransaction) throws SQLException, SQLHandledException {
                serviceLM.recalculateStatsMultiThreadAction.execute(context, threadCountObject);
            }
        });

        context.delayUserInterfaction(new MessageClientAction(localize("{logics.service.db.completed}"), localize("{logics.service.db}")));
    }

    public static void run(ExecutionContext<?> context, final RunService run) throws SQLException, SQLHandledException {
        // транзакция в Service Action'ах не особо нужна, так как действия атомарные
        final boolean singleTransaction = singleTransaction(context); 
        SQLSession sql = context.getSession().sql;
        DBManager.run(sql, singleTransaction, new DBManager.RunService() {
            public void run(SQLSession sql) throws SQLException, SQLHandledException {
                run.run(sql, !singleTransaction);
            }});
    }

    public static boolean singleTransaction(ExecutionContext context) throws SQLException, SQLHandledException {
        return context.getBL().serviceLM.singleTransaction.read(context)!=null;
    }
}