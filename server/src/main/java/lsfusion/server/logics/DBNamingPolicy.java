package lsfusion.server.logics;

import lsfusion.server.logics.classes.ValueClass;
import lsfusion.server.logics.classes.sets.ResolveClassSet;

import java.util.List;

public interface DBNamingPolicy {
    String createPropertyName(String namespaceName, String name, List<ResolveClassSet> signature);

    String createAutoTableName(List<ValueClass> classes);
    
    String transformPropertyCNToDBName(String canonicalName);
    
    String transformTableCNToDBName(String canonicalName);
}
