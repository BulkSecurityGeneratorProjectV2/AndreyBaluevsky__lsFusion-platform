package lsfusion.server.logics.classes;

import lsfusion.base.col.interfaces.mutable.MSet;
import lsfusion.server.data.ObjectValue;
import lsfusion.server.logics.classes.sets.ObjectClassSet;

public interface ConcreteObjectClass extends ConcreteClass,ObjectClass,ObjectClassSet {

    void getDiffSet(ConcreteObjectClass diffClass, MSet<CustomClass> mAddClasses, MSet<CustomClass> mRemoveClasses);

    ObjectValue getClassObject();

}
