package lsfusion.server.physics.admin.logging.init;

import lsfusion.server.logics.property.Property;
import lsfusion.server.logics.property.init.GroupPropertiesTask;
import lsfusion.server.logics.property.oraction.ActionOrProperty;

public class FinishLogInitTask extends GroupPropertiesTask {
    public String getCaption() {
        return "Setup loggables";
    }

    protected void runTask(ActionOrProperty property) {
        if(property instanceof Property)
            getBL().finishLogInit((Property) property);
    }
}
