package lsfusion.server.logics.tasks.impl;

import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.server.data.SQLHandledException;
import lsfusion.server.data.SQLSession;
import lsfusion.server.logics.property.CalcProperty;
import lsfusion.server.logics.property.ExecutionContext;
import lsfusion.server.logics.table.ImplementTable;
import lsfusion.server.logics.tasks.GroupPropertiesSingleTask;
import lsfusion.server.logics.tasks.PublicTask;
import lsfusion.server.session.DataSession;
import org.antlr.runtime.RecognitionException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static lsfusion.base.BaseUtils.serviceLogger;

public class CheckClassesTask extends GroupPropertiesSingleTask{
    Boolean firstCheck = false;
    private final Object lock = new Object();

    public void init(ExecutionContext context) throws SQLException, SQLHandledException {
        setBL(context.getBL());
        initTasks();
        setDependencies(new HashSet<PublicTask>());
    }

    @Override
    protected void runTask(final Object property) throws RecognitionException {
        try {
            final SQLSession sqlSession = getBL().getDbManager().getThreadLocalSql();
            long start = System.currentTimeMillis();
            if(!firstCheck) {
                synchronized(lock) {
                    serviceLogger.info("Here we are");
                    firstCheck = true;
                    DataSession.checkClasses(sqlSession, getBL().LM.baseClass);
                }
            } else if (property instanceof ImplementTable) {
                DataSession.checkTableClasses((ImplementTable) property, sqlSession, getBL().LM.baseClass);
                long time = System.currentTimeMillis() - start;
                serviceLogger.info(String.format("Check Table Classes: %s, %sms", ((ImplementTable) property).getName(), time));
            } else if(property instanceof CalcProperty) {
                DataSession.checkClasses((CalcProperty) property, sqlSession, getBL().LM.baseClass);
                long time = System.currentTimeMillis() - start;
                serviceLogger.info(String.format("Check Classes: %s, %sms", ((CalcProperty) property).getSID(), time));
            }
        } catch (SQLException | SQLHandledException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected List getElements() {
        List elements = new ArrayList();
        elements.addAll(getBL().LM.tableFactory.getImplementTables().toJavaSet());
        elements.addAll(getBL().getStoredDataProperties(false).toJavaList());
        return elements;
    }

    @Override
    protected String getElementCaption(Object element) {
        return element instanceof ImplementTable ? ((ImplementTable) element).getName() :
                element instanceof CalcProperty ? ((CalcProperty) element).getSID() : null;
    }

    @Override
    protected String getErrorsDescription(Object element) {
        return "";
    }

    @Override
    protected ImSet<Object> getDependElements(Object key) {
        return SetFact.EMPTY();
    }
}
