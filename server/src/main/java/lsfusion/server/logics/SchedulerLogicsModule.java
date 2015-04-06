package lsfusion.server.logics;

import org.antlr.runtime.RecognitionException;
import lsfusion.server.classes.ConcreteCustomClass;
import lsfusion.server.logics.linear.LCP;
import lsfusion.server.logics.scripted.ScriptingLogicsModule;

import java.io.IOException;

public class SchedulerLogicsModule extends ScriptingLogicsModule {

    public ConcreteCustomClass scheduledTask;
    public ConcreteCustomClass scheduledTaskLog;
    public ConcreteCustomClass scheduledClientTaskLog;

    public LCP runAtStartScheduledTask;
    public LCP startDateScheduledTask;
    public LCP periodScheduledTask;
    public LCP schedulerStartTypeScheduledTask;
    public LCP activeScheduledTask;
    public LCP ignoreExceptionsScheduledTask;
    public LCP activeScheduledTaskDetail;
    public LCP orderScheduledTaskDetail;
    public LCP scheduledTaskScheduledTaskDetail;

    public LCP canonicalNamePropertyScheduledTaskDetail;
    
    public LCP resultScheduledTaskLog;
    public LCP propertyScheduledTaskLog;
    public LCP dateScheduledTaskLog;
    public LCP scheduledTaskScheduledTaskLog;
    public LCP scheduledTaskLogScheduledClientTaskLog;
    public LCP messageScheduledClientTaskLog;

    public SchedulerLogicsModule(BusinessLogics BL, BaseLogicsModule baseLM) throws IOException {
        super(SchedulerLogicsModule.class.getResourceAsStream("/lsfusion/system/Scheduler.lsf"), "/lsfusion/system/Scheduler.lsf", baseLM, BL);
        setBaseLogicsModule(baseLM);
    }

    @Override
    public void initClasses() throws RecognitionException {
        super.initClasses();

        scheduledTask = (ConcreteCustomClass) findClass("ScheduledTask");
        scheduledTaskLog = (ConcreteCustomClass) findClass("ScheduledTaskLog");
        scheduledClientTaskLog = (ConcreteCustomClass) findClass("ScheduledClientTaskLog");
    }

    @Override
    public void initProperties() throws RecognitionException {
        super.initProperties();

        runAtStartScheduledTask = findProperty("runAtStartScheduledTask");
        startDateScheduledTask = findProperty("startDateScheduledTask");
        periodScheduledTask = findProperty("periodScheduledTask");
        schedulerStartTypeScheduledTask = findProperty("schedulerStartTypeScheduledTask");
        activeScheduledTask = findProperty("activeScheduledTask");
        ignoreExceptionsScheduledTask = findProperty("ignoreExceptionsScheduledTask");
        activeScheduledTaskDetail = findProperty("activeScheduledTaskDetail");
        orderScheduledTaskDetail = findProperty("orderScheduledTaskDetail");
        scheduledTaskScheduledTaskDetail = findProperty("scheduledTaskScheduledTaskDetail");

        canonicalNamePropertyScheduledTaskDetail = findProperty("canonicalNamePropertyScheduledTaskDetail");
        
        resultScheduledTaskLog = findProperty("resultScheduledTaskLog");
        propertyScheduledTaskLog = findProperty("propertyScheduledTaskLog");
        dateScheduledTaskLog = findProperty("dateScheduledTaskLog");
        scheduledTaskScheduledTaskLog = findProperty("scheduledTaskScheduledTaskLog");
        scheduledTaskLogScheduledClientTaskLog = findProperty("scheduledTaskLogScheduledClientTaskLog");
        messageScheduledClientTaskLog = findProperty("messageScheduledClientTaskLog");
    }
}
