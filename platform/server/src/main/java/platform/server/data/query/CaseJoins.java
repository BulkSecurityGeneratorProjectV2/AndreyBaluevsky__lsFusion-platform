package platform.server.data.query;

import platform.server.data.DataSource;
import platform.server.data.query.exprs.*;
import platform.server.data.query.exprs.cases.CaseWhere;
import platform.server.data.query.exprs.cases.MapCase;
import platform.server.where.Where;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class CaseJoins<J,U> extends HashMap<MapCase<J>,Map<U,? extends AndExpr>> implements CaseWhere<MapCase<J>> {

    private final Collection<ParsedJoin> translatedJoins;
    private final DataSource<J,U> joinSource;
    private final boolean noAlias;

    CaseJoins(Collection<ParsedJoin> iTranslatedJoins, DataSource<J,U> iJoinSource,boolean iNoAlias) {
        translatedJoins = iTranslatedJoins;
        joinSource = iJoinSource;
        noAlias = iNoAlias;
    }

    public Where getCaseWhere(MapCase<J> cCase) {
        if(SourceExpr.containsNull(cCase.data)) { // если есть null просто все null'им
            Map<U, AndExpr> exprs = new HashMap<U, AndExpr>();
            for(U expr : joinSource.getProperties())
                exprs.put(expr, joinSource.getType(expr).getExpr(null));
            put(cCase,exprs);
            return Where.FALSE;
        }

        for(ParsedJoin<?> join : translatedJoins) {
            Map<U, JoinExpr> mergeExprs = join.merge(joinSource, cCase.data);
            if(mergeExprs!=null) {
                put(cCase,mergeExprs);
                return join.inJoin;
            }
        }

        // создаем новый
        ParsedJoin<J> addJoin = new ParsedJoin<J>((DataSource<J,Object>) joinSource, cCase.data, noAlias);
        translatedJoins.add(addJoin);
        put(cCase, (Map<U,? extends AndExpr>) addJoin.exprs);
        return addJoin.inJoin;
    }
}
