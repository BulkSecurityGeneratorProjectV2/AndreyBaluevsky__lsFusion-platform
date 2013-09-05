package lsfusion.server.data.where.classes;

import lsfusion.base.BaseUtils;
import lsfusion.base.col.SetFact;
import lsfusion.base.col.interfaces.immutable.ImCol;
import lsfusion.base.col.interfaces.immutable.ImMap;
import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.base.col.interfaces.immutable.ImSet;
import lsfusion.base.col.interfaces.mutable.AddValue;
import lsfusion.base.col.interfaces.mutable.SimpleAddValue;
import lsfusion.base.col.interfaces.mutable.SymmAddValue;
import lsfusion.base.col.interfaces.mutable.mapvalue.GetValue;
import lsfusion.server.classes.DataClass;
import lsfusion.server.classes.ValueClass;
import lsfusion.server.classes.sets.AndClassSet;
import lsfusion.server.data.expr.BaseExpr;
import lsfusion.server.data.expr.Expr;
import lsfusion.server.data.expr.query.Stat;
import lsfusion.server.data.where.Where;

public class ClassWhere<K> extends AbstractClassWhere<K, ClassWhere<K>> {

    private boolean compatible(ClassWhere<K> where) {
        return where.isFalse() || isFalse() || wheres[0].compatible(where.wheres[0]);
    }
    // в некоторых случаях как при проверке интерфейсов, классы могут быть не compatible и тогда нарушится инвариант с OrClassSet -
    // что это либо OrObjectClassSet или DataClass поэтому пока так
    public ClassWhere<K> andCompatible(ClassWhere<K> where) {
        if(compatible(where))
            return and(where);
        else
            return FALSE();
    }

    public boolean meansCompatible(ClassWhere<K> where) {
        return compatible(where) && means(where, true); // с implicit cast'ом
    }

    public ClassWhere() {
        this(false);
    }

    public ClassWhere(boolean isTrue) {
        super(isTrue);
    }

    public ClassWhere(And<K> where) {
        super(where);
    }

    private final static ClassWhere<Object> TRUE = new ClassWhere<Object>(true);
    public static <K> ClassWhere<K> TRUE() {
        return (ClassWhere<K>) TRUE;
    }
    public static <K> ClassWhere<K> STATIC(boolean isTrue) {
        return isTrue ? ClassWhere.<K>TRUE() : ClassWhere.<K>FALSE();
    }
    private final static ClassWhere<Object> FALSE = new ClassWhere<Object>(false);
    public static <K> ClassWhere<K> FALSE() {
        return (ClassWhere<K>) FALSE;
    }

    protected ClassWhere<K> FALSETHIS() {
        return ClassWhere.FALSE();
    }

    public static <M,K> ImMap<M,ClassWhere<K>> STATIC(ImSet<M> keys, boolean isTrue) {
        return keys.toMap(ClassWhere.<K>STATIC(isTrue));
    }

    private ClassWhere(And<K>[] iWheres) {
        super(iWheres);
    }
    protected ClassWhere<K> createThis(And<K>[] wheres) {
        return new ClassWhere<K>(wheres);
    }

    public ClassWhere(K key, AndClassSet classes) {
        super(key, classes);
    }

    public ClassWhere(ImMap<K, ValueClass> mapClasses,boolean up) {
        super(mapClasses, up);
    }



    public ClassWhere(ImMap<K, ? extends AndClassSet> mapSets) {
        super(mapSets);
    }

    public ClassExprWhere mapClasses(ImMap<K, BaseExpr> map) {
        ClassExprWhere result = ClassExprWhere.FALSE;
        for(And<K> andWhere : wheres) {
            ClassExprWhere joinWhere = ClassExprWhere.TRUE;
            for(int i=0,size=map.size();i<size;i++)
                joinWhere = joinWhere.and(map.getValue(i).getClassWhere(andWhere.get(map.getKey(i))));
            result = result.or(joinWhere);
        }
        return result;
    }

    public <V> ClassWhere(ClassWhere<V> classes, ImRevMap<V, K> map) {
        super(classes, map);
    }

    public Stat getTypeStat(K key) {
        return wheres[0].get(key).getTypeStat();
    }

    // аналогичный метод в ClassExprWhere
    public ClassWhere<K> remove(ImSet<? extends K> keys) {
        ClassWhere<K> result = ClassWhere.FALSE();
        for(And<K> andWhere : wheres)
            result = result.or(new ClassWhere<K>(andWhere.remove(keys)));
        return result;
    }

    public <T extends K> ClassWhere<T> filterKeys(ImSet<T> keys) {
        ClassWhere<T> result = ClassWhere.FALSE();
        for(And<K> andWhere : wheres)
            result = result.or(new ClassWhere<T>(andWhere.filterKeys(keys)));
        return result;
    }

    public Where getWhere(ImMap<K, ? extends Expr> mapExprs) {
        return getWhere((GetValue<Expr, K>) mapExprs.fnGetValue());
    }

    public Where getWhere(GetValue<Expr, K> mapExprs) {
        Where result = Where.FALSE;
        for(And<K> andWhere : wheres)
            result = result.or(andWhere.getWhere(mapExprs));
        return result;
    }

    public <T> ClassWhere<T> remap(ImRevMap<K, ? extends T> map) {
        And<T>[] remapWheres = new And[wheres.length];
        for(int i=0;i<wheres.length;i++)
            remapWheres[i] = wheres[i].remap(map);
        return new ClassWhere<T>(remapWheres);
    }

    private final static AddValue<Object, ClassWhere<Object>> addOr = new SymmAddValue<Object, ClassWhere<Object>>() {
        public ClassWhere<Object> addValue(Object key, ClassWhere<Object> prevValue, ClassWhere<Object> newValue) {
            return prevValue.or(newValue);
        }
    };
    public static <K, V> AddValue<K, ClassWhere<V>> addOr() {
        return BaseUtils.immutableCast(addOr);
    }

    // потом на containsAll переделать
    public boolean fitDataClasses(ImMap<K, DataClass> checkClasses) {
        if(isFalse())
            return true;
        for (And<K> where : wheres) {
            for (int i=0,size=checkClasses.size();i<size;i++)
                if(!BaseUtils.hashEquals(where.get(checkClasses.getKey(i)), checkClasses.getValue(i)))
                    return false;
        }
        return true;
    }

    public boolean isFull(ImCol<? extends K> checkInterfaces) {
        if(isFalse())
            return true;
        for (And<K> where : wheres) {
            for (K i : checkInterfaces)
                if(where.get(i)==null)
                    return false;
        }
        return true;
    }

    public ImSet<K> getFullInterfaces() {
        ImSet<K> result = null;
        for (And<K> where : wheres)
            if(result == null)
                result = where.keys();
            else
                result = result.filter(where.keys());
        return result;
    }

    public boolean isEqual(ImSet<K> interfaces) {
        return isFalse() || BaseUtils.hashEquals(getFullInterfaces(), interfaces);
    }
}

