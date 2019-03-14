package lsfusion.server.physics.dev.debug.init;

import com.google.common.base.Throwables;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.file.IOUtils;
import lsfusion.server.base.task.GroupSplitTask;
import lsfusion.server.physics.dev.debug.ActionPropertyDebugger;
import lsfusion.server.physics.dev.debug.DebugInfo;
import org.apache.log4j.Logger;

import java.io.File;

public class InitActionDebuggerTask extends GroupSplitTask<String> {
    @Override
    public String getCaption() {
        return "Initializing actions' debugger";
    }

    File sourceDir;
    ImMap<String, ImSet<DebugInfo>> groupDelegates;
    
    @Override
    protected void runGroupTask(ImSet<String> objSet, Logger logger) {
        try {
            ActionPropertyDebugger.getInstance().compileDelegatesHolders(sourceDir, groupDelegates.filter(objSet));
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    @Override
    protected ImSet<String> getObjects() {
        return groupDelegates.keys();
    }

    @Override
    protected boolean prerun() {
        
        if(!ActionPropertyDebugger.getInstance().isEnabled())
            return false;

        groupDelegates = ActionPropertyDebugger.getInstance().getGroupDelegates();
        try {
            sourceDir = IOUtils.createTempDirectory("lsfusiondebug");
        } catch (Exception e) {
            Throwables.propagate(e);
        }

        return true;
    }
}
