package lsfusion.server.base.lifecycle;

import lsfusion.server.base.context.ExecutionStack;
import lsfusion.server.base.context.ThreadLocalContext;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.logics.BusinessLogics;
import lsfusion.server.logics.action.session.DataSession;

import java.sql.SQLException;

// составные части LogicsInstance
public abstract class LogicsManager extends LifecycleAdapter {

    protected ExecutionStack getStack() {
        ThreadLocalContext.assureLifecycle(ThreadLocalContext.getLogicsInstance()); // в остальных местах ExecutionStack должен быть параметром
        return ThreadLocalContext.getStack();
    }

    public LogicsManager() {
    }

    public LogicsManager(int order) {
        super(order);
    }

    protected abstract BusinessLogics getBusinessLogics();

    protected void apply(DataSession session) throws SQLException, SQLHandledException {
        apply(session, getStack());
    }
    protected void apply(DataSession session, ExecutionStack stack) throws SQLException, SQLHandledException {
        session.applyException(getBusinessLogics(), stack);
    }
}
