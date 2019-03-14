package lsfusion.server.logics;

import lsfusion.server.ServerLoggers;
import lsfusion.server.base.lifecycle.LifecycleEvent;
import lsfusion.server.base.lifecycle.LogicsManager;
import lsfusion.server.base.task.PublicTask;
import lsfusion.server.base.task.TaskRunner;
import lsfusion.server.physics.exec.DBManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

public class ModulesHashManager extends LogicsManager implements InitializingBean {

    public static final Logger startLogger = ServerLoggers.startLogger;

    private BusinessLogics businessLogics;

    @Override
    protected BusinessLogics getBusinessLogics() {
        return businessLogics;
    }

    private DBManager dbManager;

    private PublicTask initTask;

    public void setBusinessLogics(BusinessLogics businessLogics) {
        this.businessLogics = businessLogics;
    }

    public void setDbManager(DBManager dbManager) {
        this.dbManager = dbManager;
    }

    public void setInitTask(PublicTask initTask) {
        this.initTask = initTask;
    }

    private BaseLogicsModule LM;

    public void afterPropertiesSet() throws Exception {
        Assert.notNull(initTask, "initTask must be specified");
    }

    @Override
    protected void onInit(LifecycleEvent event) {
        this.LM = businessLogics.LM;
    }
    
    @Override
    protected void onStarted(LifecycleEvent event) {
        try {
            new TaskRunner(businessLogics).runTask(initTask, startLogger);
        } catch (Exception e) {
            throw new RuntimeException("Error starting ReflectionManager: ", e);
        }
    }
}
