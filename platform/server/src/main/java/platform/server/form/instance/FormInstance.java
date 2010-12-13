package platform.server.form.instance;

import platform.base.BaseUtils;
import platform.base.OrderedMap;
import platform.interop.ClassViewType;
import platform.interop.Scroll;
import platform.interop.action.ClientAction;
import platform.interop.action.ResultClientAction;
import platform.interop.exceptions.ComplexQueryException;
import platform.interop.form.RemoteFormInterface;
import platform.server.auth.SecurityPolicy;
import platform.server.caches.ManualLazy;
import platform.server.classes.ConcreteCustomClass;
import platform.server.classes.CustomClass;
import platform.server.classes.DataClass;
import platform.server.data.expr.Expr;
import platform.server.data.expr.KeyExpr;
import platform.server.data.expr.where.EqualsWhere;
import platform.server.data.query.Query;
import platform.server.data.type.TypeSerializer;
import platform.server.data.where.Where;
import platform.server.form.entity.*;
import platform.server.form.entity.filter.FilterEntity;
import platform.server.form.entity.filter.RegularFilterGroupEntity;
import platform.server.form.instance.filter.FilterInstance;
import platform.server.form.instance.filter.RegularFilterGroupInstance;
import platform.server.form.instance.filter.RegularFilterInstance;
import platform.server.form.instance.listener.CustomClassListener;
import platform.server.form.instance.listener.FocusListener;
import platform.server.form.instance.remote.RemoteForm;
import platform.server.logics.BusinessLogics;
import platform.server.logics.DataObject;
import platform.server.logics.ObjectValue;
import platform.server.logics.NullValue;
import platform.server.logics.linear.LP;
import platform.server.logics.property.Property;
import platform.server.session.*;

import java.io.DataOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;
import java.lang.ref.WeakReference;

import static platform.interop.ClassViewType.*;
import static platform.server.form.instance.GroupObjectInstance.*;

// класс в котором лежит какие изменения произошли

// нужен какой-то объект который
//  разделит клиента и серверную часть кинув каждому свои данные
// так клиента волнуют панели на форме, список гридов в привязке, дизайн и порядок представлений
// сервера колышет дерево и св-ва предст. с привязкой к объектам

public class FormInstance<T extends BusinessLogics<T>> extends NoUpdateModifier {

    public final int sessionID;

    public final T BL;

    public DataSession session;

    public SessionChanges getSession() {
        return session.changes;
    }

    SecurityPolicy securityPolicy;

    public CustomClass getCustomClass(int classID) {
        return BL.baseClass.findClassID(classID);
    }

    private UsedChanges fullChanges;

    public Modifier<? extends Changes> update(final SessionChanges sessionChanges) {
        return new NoUpdateModifier(hintsNoUpdate) {
            public SessionChanges getSession() {
                return sessionChanges;
            }
        };
    }

    public Set<Property> getUpdateProperties(SessionChanges sessionChanges) {
        return getUpdateProperties(update(sessionChanges));
    }

    public Set<Property> getUpdateProperties(Modifier<? extends Changes> modifier) {
        Set<Property> properties = new HashSet<Property>();
        for (Property<?> updateProperty : getUpdateProperties())
            if (updateProperty.hasChanges(modifier))
                properties.add(updateProperty);
        return properties;
    }

    private final WeakReference<FocusListener<T>> weakFocusListener;
    public FocusListener<T> getFocusListener() {
        return weakFocusListener.get();
    }

    private final WeakReference<CustomClassListener> weakClassListener;
    public CustomClassListener getClassListener() {
        return weakClassListener.get();
    }

    public final FormEntity<T> entity;

    public final InstanceFactory instanceFactory;

    public FormInstance(FormEntity<T> entity, T BL, DataSession session, SecurityPolicy securityPolicy, FocusListener<T> focusListener, CustomClassListener classListener, PropertyObjectInterfaceInstance computer) throws SQLException {
        this(entity, BL, session, securityPolicy, focusListener, classListener, computer, new HashMap<ObjectEntity, Object>());
    }

    public FormInstance(FormEntity<T> entity, T BL, DataSession session, SecurityPolicy securityPolicy, FocusListener<T> focusListener, CustomClassListener classListener, PropertyObjectInterfaceInstance computer, Map<ObjectEntity, Object> mapObjects) throws SQLException {
        this.entity = entity;
        this.BL = BL;
        this.session = session;
        this.securityPolicy = securityPolicy;

        instanceFactory = new InstanceFactory(computer);

        this.weakFocusListener = new WeakReference<FocusListener<T>>(focusListener);
        this.weakClassListener = new WeakReference<CustomClassListener>(classListener);

        sessionID = this.session.generateID(entity.getID());

        hintsNoUpdate = entity.hintsNoUpdate;

        for (int i = 0; i < entity.groups.size(); i++) {
            GroupObjectInstance groupObject = instanceFactory.getInstance(entity.groups.get(i));
            groupObject.order = i;
            groupObject.setClassListener(classListener);
            groups.add(groupObject);
        }

        for (TreeGroupEntity treeGroup : entity.treeGroups) {
            treeGroups.add(instanceFactory.getInstance(treeGroup));
        }

        for (PropertyDrawEntity<?> propertyDrawEntity : entity.propertyDraws)
            if (this.securityPolicy.property.view.checkPermission(propertyDrawEntity.propertyObject.property)) {
                PropertyDrawInstance propertyDrawInstance = instanceFactory.getInstance(propertyDrawEntity);
                if (propertyDrawInstance.toDraw == null) // для Instance'ов проставляем не null, так как в runtime'е порядок меняться не будет
                    propertyDrawInstance.toDraw = instanceFactory.getInstance(propertyDrawEntity.getToDraw(entity));
                properties.add(propertyDrawInstance);
            }


        for (FilterEntity filterEntity : entity.fixedFilters) {
            FilterInstance filter = filterEntity.getInstance(instanceFactory);
            filter.getApplyObject().fixedFilters.add(filter);
        }

        for (RegularFilterGroupEntity filterGroupEntity : entity.regularFilterGroups) {
            regularFilterGroups.add(instanceFactory.getInstance(filterGroupEntity));
        }

        for (Entry<OrderEntity<?>, Boolean> orderEntity : entity.fixedOrders.entrySet()) {
            OrderInstance orderInstance = orderEntity.getKey().getInstance(instanceFactory);
            orderInstance.getApplyObject().fixedOrders.put(orderInstance, orderEntity.getValue());
        }

        addObjectOnTransaction();

        for (Entry<ObjectEntity, Object> mapObject : mapObjects.entrySet()) {
            ObjectInstance instance = instanceFactory.getInstance(mapObject.getKey());
            Map<OrderInstance, Object> seeks = userGroupSeeks.get(instance.groupTo);
            if (seeks == null) {
                seeks = new HashMap<OrderInstance, Object>();
                userGroupSeeks.put(instance.groupTo, seeks);
            }
            seeks.put(instance, mapObject.getValue());
        }
    }

    public List<GroupObjectInstance> groups = new ArrayList<GroupObjectInstance>();
    public List<TreeGroupInstance> treeGroups = new ArrayList<TreeGroupInstance>();
    public Map<GroupObjectInstance, GroupObjectTable> groupTables = new HashMap<GroupObjectInstance, GroupObjectTable>();
    public Map<GroupObjectInstance, GroupObjectTable> expandTables = new HashMap<GroupObjectInstance, GroupObjectTable>();

    // собсно этот объект порядок колышет столько же сколько и дизайн представлений
    public List<PropertyDrawInstance> properties = new ArrayList<PropertyDrawInstance>();

    private Collection<ObjectInstance> objects;

    @ManualLazy
    public Collection<ObjectInstance> getObjects() {
        if (objects == null) {
            objects = new ArrayList<ObjectInstance>();
            for (GroupObjectInstance group : groups)
                for (ObjectInstance object : group.objects)
                    objects.add(object);
        }
        return objects;
    }

    // ----------------------------------- Поиск объектов по ID ------------------------------ //

    public GroupObjectInstance getGroupObjectInstance(int groupID) {
        for (GroupObjectInstance groupObject : groups)
            if (groupObject.getID() == groupID)
                return groupObject;
        return null;
    }

    public ObjectInstance getObjectInstance(int objectID) {
        for (ObjectInstance object : getObjects())
            if (object.getID() == objectID)
                return object;
        return null;
    }

    public PropertyDrawInstance getPropertyDraw(int propertyID) {
        for (PropertyDrawInstance property : properties)
            if (property.getID() == propertyID)
                return property;
        return null;
    }

    public RegularFilterGroupInstance getRegularFilterGroup(int groupID) {
        for (RegularFilterGroupInstance filterGroup : regularFilterGroups)
            if (filterGroup.getID() == groupID)
                return filterGroup;
        return null;
    }

    public PropertyDrawInstance getPropertyDraw(Property<?> property, GroupObjectInstance group) {
        for (PropertyDrawInstance propertyDraw : properties)
            if (property.equals(propertyDraw.propertyObject.property) && (group==null || group.equals(propertyDraw.toDraw)))
                return propertyDraw;
        return null;
    }

    public PropertyDrawInstance getPropertyDraw(Property<?> property) {
        return getPropertyDraw(property, null);
    }

    public PropertyDrawInstance getPropertyDraw(LP<?> property) {
        return getPropertyDraw(property.property);
    }

    public PropertyDrawInstance getPropertyDraw(LP<?> property, GroupObjectInstance group) {
        return getPropertyDraw(property.property, group);
    }

    public void serializePropertyEditorType(DataOutputStream outStream, PropertyDrawInstance<?> propertyDraw) throws SQLException, IOException {

        PropertyObjectInstance<?> change = propertyDraw.propertyObject.getChangeInstance();
        if (securityPolicy.property.change.checkPermission(change.property) && change.getValueImplement().canBeChanged(this)) {
            outStream.writeBoolean(false);
            TypeSerializer.serializeType(outStream, change.getEditorType());
        } else
            outStream.writeBoolean(true);
    }

    // ----------------------------------- Навигация ----------------------------------------- //

    // поиски по свойствам\объектам
    // отличается когда содержит null (то есть end делается) и не содержит элемента
    public Map<GroupObjectInstance, Map<OrderInstance, Object>> userGroupSeeks = new HashMap<GroupObjectInstance, Map<OrderInstance, Object>>();

    public void changeGroupObject(GroupObjectInstance group, Scroll changeType) throws SQLException {
        switch (changeType) {
            case HOME:
                userGroupSeeks.put(group, new HashMap<OrderInstance, Object>());
                break;
            case END:
                userGroupSeeks.put(group, null);
                break;
        }
    }

    public void expandGroupObject(GroupObjectInstance group, Map<ObjectInstance, DataObject> value) throws SQLException {
        GroupObjectTable expandTable = expandTables.get(group);
        if(expandTable==null) {
            expandTable = new GroupObjectTable(group, "e", sessionID * RemoteFormInterface.GID_SHIFT + group.getID());
            session.createTemporaryTable(expandTable.table);
        }
        expandTables.put(group, expandTable.insertRecord(session, value, true));
        group.updated |= UPDATED_EXPANDS;
    }

    public void changeGroupObject(GroupObjectInstance group, Map<ObjectInstance, ? extends ObjectValue> value) throws SQLException {
        // проставим все объектам метки изменений
        assert value.keySet().equals(new HashSet<ObjectInstance>(GroupObjectInstance.getObjects(group.getUpTreeGroups())));
        for (Entry<ObjectInstance, ? extends ObjectValue> objectValue : value.entrySet())
            objectValue.getKey().changeValue(session, objectValue.getValue());
        for(ObjectInstance object : GroupObjectInstance.getObjects(group.getDownTreeGroups()))
            object.changeValue(session, NullValue.instance);
    }

    public void switchClassView(GroupObjectInstance group) {
        changeClassView(group, switchView(group.curClassView));
    }

    public void changeClassView(GroupObjectInstance group, ClassViewType show) {

        group.curClassView = show;
        group.updated = group.updated | UPDATED_CLASSVIEW;
    }

    // сстандартные фильтры
    public List<RegularFilterGroupInstance> regularFilterGroups = new ArrayList<RegularFilterGroupInstance>();
    private Map<RegularFilterGroupInstance, RegularFilterInstance> regularFilterValues = new HashMap<RegularFilterGroupInstance, RegularFilterInstance>();

    public void setRegularFilter(RegularFilterGroupInstance filterGroup, RegularFilterInstance filter) {

        RegularFilterInstance prevFilter = regularFilterValues.get(filterGroup);
        if (prevFilter != null)
            prevFilter.filter.getApplyObject().removeRegularFilter(prevFilter.filter);

        if (filter == null)
            regularFilterValues.remove(filterGroup);
        else {
            regularFilterValues.put(filterGroup, filter);
            filter.filter.getApplyObject().addRegularFilter(filter.filter);
        }

    }

    // -------------------------------------- Изменение данных ----------------------------------- //

    // пометка что изменились данные
    private boolean dataChanged = false;

    private DataObject createObject(ConcreteCustomClass cls) throws SQLException {

        if (!securityPolicy.cls.edit.add.checkPermission(cls)) return null;

        return session.addObject(cls, this);
    }

    private void resolveAddObject(CustomObjectInstance object, ConcreteCustomClass cls, DataObject addObject) throws SQLException {

        // резолвим все фильтры
        for (FilterInstance filter : object.groupTo.getSetFilters())
            if (!FilterInstance.ignoreInInterface || filter.isInInterface(object.groupTo)) // если ignoreInInterface проверить что в интерфейсе
                filter.resolveAdd(session, this, object, addObject);

        object.changeValue(session, addObject);

        // меняем вид, если при добавлении может получиться, что фильтр не выполнится, нужно как-то проверить в общем случае
//      changeClassView(object.groupTo, ClassViewType.PANEL);

        dataChanged = true;
    }

    // добавляет во все
    public DataObject addObject(ConcreteCustomClass cls) throws SQLException {

        DataObject addObject = createObject(cls);
        if (addObject == null) return addObject;

        for (ObjectInstance object : getObjects())
            if (object instanceof CustomObjectInstance && cls.isChild(((CustomObjectInstance) object).baseClass))
                resolveAddObject((CustomObjectInstance) object, cls, addObject);

        return addObject;
    }

    public DataObject addObject(CustomObjectInstance object, ConcreteCustomClass cls) throws SQLException {
        // пока тупо в базу

        DataObject addObject = createObject(cls);
        if (addObject == null) return addObject;

        resolveAddObject(object, cls, addObject);

        return addObject;
    }

    public void changeClass(CustomObjectInstance object, DataObject change, int classID) throws SQLException {
        if (securityPolicy.cls.edit.change.checkPermission(object.currentClass)) {
            object.changeClass(session, change, classID);
            dataChanged = true;
        }
    }

    public boolean canChangeClass(CustomObjectInstance object) {
        return securityPolicy.cls.edit.change.checkPermission(object.currentClass);
    }

    public List<ClientAction> changeProperty(PropertyDrawInstance<?> property, Object value) throws SQLException {
        return changeProperty(property.propertyObject, value, null);
    }

    public List<ClientAction> changeProperty(PropertyDrawInstance<?> property, Object value, RemoteForm executeForm, boolean all) throws SQLException {
        return changeProperty(property.propertyObject, value, executeForm, all ? property.toDraw : null);
    }

    public List<ClientAction> changeProperty(PropertyDrawInstance<?> property, Object value, RemoteForm executeForm, boolean all, Map<ObjectInstance, DataObject> mapDataValues) throws SQLException {
        return changeProperty(property.propertyObject.getRemappedPropertyObject(mapDataValues), value, executeForm, all ? property.toDraw : null);
    }

    public List<ClientAction> changeProperty(PropertyObjectInstance<?> property, Object value, RemoteForm executeForm) throws SQLException {
        return changeProperty(property, value, executeForm, null);
    }

    public List<ClientAction> changeProperty(PropertyObjectInstance<?> property, Object value, RemoteForm executeForm, GroupObjectInstance groupObject) throws SQLException {
        if (securityPolicy.property.change.checkPermission(property.property)) {
            dataChanged = true;
            // изменяем св-во
            return property.getChangeInstance().execute(session, value, this, executeForm, groupObject);
        } else {
            return null;
        }
    }

    // Обновление данных
    public void refreshData() throws SQLException {

        for (ObjectInstance object : getObjects())
            if (object instanceof CustomObjectInstance)
                ((CustomObjectInstance) object).refreshValueClass(session);
        refresh = true;
    }

    void addObjectOnTransaction() throws SQLException {
        for (ObjectInstance object : getObjects()) {
            if (object instanceof CustomObjectInstance) {
                CustomObjectInstance customObject = (CustomObjectInstance) object;
                if (customObject.isAddOnTransaction()) {
                    addObject(customObject, (ConcreteCustomClass) customObject.gridClass);
                }
            }
            if (object.isResetOnApply()) {
                object.setDefaultValue(session);
            }
        }
    }

    public void applyActionChanges(List<ClientAction> actions) throws SQLException {

        String check = checkApply();
        if (check != null)
            actions.add(new ResultClientAction(check, true));
        else {
            commitApply();

            actions.add(new ResultClientAction("Изменения были удачно записаны...", false));
        }
    }

    public String checkApply() throws SQLException {
        return session.check(BL);
    }

    public void commitApply() throws SQLException {
        session.write(BL);

        refreshData();
        addObjectOnTransaction();

        dataChanged = true; // временно пока applyChanges синхронен, для того чтобы пересылался факт изменения данных
    }

    public void cancelChanges() throws SQLException {
        session.restart(true);

        // пробежим по всем объектам
        for (ObjectInstance object : getObjects())
            if (object instanceof CustomObjectInstance)
                ((CustomObjectInstance) object).updateValueClass(session);
        addObjectOnTransaction();

        dataChanged = true;
    }

    // ------------------ Через эти методы сообщает верхним объектам об изменениях ------------------- //

    // В дальнейшем наверное надо будет переделать на Listener'ы...
    protected void objectChanged(ConcreteCustomClass cls, Integer objectID) {
    }

    public void changePageSize(GroupObjectInstance groupObject, Integer pageSize) {
        groupObject.setPageSize(pageSize);
    }

    public void gainedFocus() {
        dataChanged = true;
        FocusListener<T> focusListener = getFocusListener();
        if(focusListener!=null)
            focusListener.gainedFocus(this);
    }

    void close() throws SQLException {

        session.incrementChanges.remove(this);
        for (GroupObjectTable groupObjectTable : groupTables.values())
            session.dropTemporaryTable(groupObjectTable.table);
        for (GroupObjectTable groupObjectTable : expandTables.values())
            session.dropTemporaryTable(groupObjectTable.table);
    }

    // --------------------------------------------------------------------------------------- //
    // --------------------- Общение в обратную сторону с ClientForm ------------------------- //
    // --------------------------------------------------------------------------------------- //

    public ConcreteCustomClass getObjectClass(ObjectInstance object) {

        if (!(object instanceof CustomObjectInstance))
            return null;

        return ((CustomObjectInstance) object).currentClass;
    }

    public Collection<Property> getUpdateProperties() {

        Set<Property> result = new HashSet<Property>();
        for (PropertyDrawInstance<?> propView : properties) {
            result.add(propView.propertyObject.property);
            if (propView.propertyCaption != null)
                result.add(propView.propertyCaption.property);
        }
        for (GroupObjectInstance group : groups) {
            if (group.propertyHighlight != null)
                result.add(group.propertyHighlight.property);
            group.fillUpdateProperties(result);
        }
        return result;
    }

    public Collection<Property> getNoUpdateProperties() {
        return hintsNoUpdate;
    }

    public FormInstance<T> createForm(FormEntity<T> form, Map<ObjectEntity, DataObject> mapObjects) throws SQLException {
        return new FormInstance<T>(form, BL, session, securityPolicy, getFocusListener(), getClassListener(), instanceFactory.computer, DataObject.getMapValues(mapObjects));
    }

    public List<ClientAction> changeObject(ObjectInstance object, Object value, RemoteForm form) throws SQLException {

        object.changeValue(session, value);

        // запускаем все Action'ы, которые следят за этим объектом
        return executeAutoActions(object, form);
    }

    // транзакция для отката при exception'ах
    private class ApplyTransaction {

        private class Group {

            private abstract class Object<O extends ObjectInstance> {
                O object;
                int updated;

                private Object(O object) {
                    this.object = object;
                    updated = object.updated;
                }

                void rollback() {
                    object.updated = updated;
                }
            }

            private class Custom extends Object<CustomObjectInstance> {
                ObjectValue value;
                ConcreteCustomClass currentClass;

                private Custom(CustomObjectInstance object) {
                    super(object);
                    value = object.value;
                    currentClass = object.currentClass;
                }

                void rollback() {
                    super.rollback();
                    object.value = value;
                    object.currentClass = currentClass;
                }
            }

            private class Data extends Object<DataObjectInstance> {
                java.lang.Object value;

                private Data(DataObjectInstance object) {
                    super(object);
                    value = object.value;
                }

                void rollback() {
                    super.rollback();
                    object.value = value;
                }
            }

            GroupObjectInstance group;
            boolean upKeys, downKeys;
            Set<FilterInstance> filters;
            OrderedMap<OrderInstance, Boolean> orders;
            OrderedMap<Map<ObjectInstance, platform.server.logics.DataObject>, Map<OrderInstance, ObjectValue>> keys;
            int updated;

            Collection<Object> objects = new ArrayList<Object>();

            GroupObjectTable groupObjectTable;
            GroupObjectTable expandObjectTable;

            private Group(GroupObjectInstance iGroup) {
                group = iGroup;

                filters = new HashSet<FilterInstance>(group.filters);
                orders = new OrderedMap<OrderInstance, Boolean>(group.orders);
                upKeys = group.upKeys;
                downKeys = group.downKeys;
                keys = new OrderedMap<Map<ObjectInstance, DataObject>, Map<OrderInstance, ObjectValue>>(group.keys);
                updated = group.updated;

                for (ObjectInstance object : group.objects)
                    objects.add(object instanceof CustomObjectInstance ? new Custom((CustomObjectInstance) object) : new Data((DataObjectInstance) object));

                groupObjectTable = groupTables.get(group);
                expandObjectTable = expandTables.get(group);
            }

            void rollback() throws SQLException {
                group.filters = filters;
                group.orders = orders;
                group.upKeys = upKeys;
                group.downKeys = downKeys;
                group.keys = keys;
                group.updated = updated;

                for (Object object : objects)
                    object.rollback();

                // восстанавливаем ключи в сессии
                if (groupObjectTable == null) {
                    GroupObjectTable newTable = groupTables.get(group);
                    if (newTable != null) {
                        session.dropTemporaryTable(newTable.table);
                        groupTables.remove(group);
                    }
                } else {
                    groupObjectTable.rewrite(session, group.keys.keySet());
                    groupTables.put(group, groupObjectTable);
                }
                if (expandObjectTable == null) {
                    GroupObjectTable newTable = expandTables.get(group);
                    if (newTable != null) {
                        session.dropTemporaryTable(newTable.table);
                        expandTables.remove(group);
                    }
                } else {
                    expandObjectTable.rewrite(session, group.keys.keySet());
                    expandTables.put(group, expandObjectTable);
                }
            }
        }

        Collection<Group> groups = new ArrayList<Group>();
        Set<PropertyDrawInstance> isDrawed;
        Map<RegularFilterGroupInstance, RegularFilterInstance> regularFilterValues;

        Map<FormInstance, DataSession.UpdateChanges> incrementChanges;
        Map<FormInstance, DataSession.UpdateChanges> appliedChanges;
        Map<FormInstance, DataSession.UpdateChanges> updateChanges;

        SessionChanges changes;

        ApplyTransaction() {
            for (GroupObjectInstance group : FormInstance.this.groups)
                groups.add(new Group(group));
            isDrawed = new HashSet<PropertyDrawInstance>(FormInstance.this.isDrawed);
            regularFilterValues = new HashMap<RegularFilterGroupInstance, RegularFilterInstance>(FormInstance.this.regularFilterValues);

            if (dataChanged) {
                incrementChanges = new HashMap<FormInstance, DataSession.UpdateChanges>(session.incrementChanges);
                appliedChanges = new HashMap<FormInstance, DataSession.UpdateChanges>(session.appliedChanges);
                updateChanges = new HashMap<FormInstance, DataSession.UpdateChanges>(session.updateChanges);
                changes = session.changes;
            }
        }

        void rollback() throws SQLException {
            for (Group group : groups)
                group.rollback();
            FormInstance.this.isDrawed = isDrawed;
            FormInstance.this.regularFilterValues = regularFilterValues;

            if (dataChanged) {
                session.incrementChanges = incrementChanges;
                session.appliedChanges = appliedChanges;
                session.updateChanges = updateChanges;
                session.changes = changes;
            }
        }
    }

    private final static int DIRECTION_DOWN = 1;
    private final static int DIRECTION_UP = 2;
    private final static int DIRECTION_CENTER = 3;

    // оболочка изменения group, чтобы отослать клиенту
    private void updateGroupObject(GroupObjectInstance group, FormChanges changes, Map<ObjectInstance, ? extends ObjectValue> value) throws SQLException {

        changes.objects.put(group, value);

        changeGroupObject(group, value);
    }

    private Where getExpandWhere(GroupObjectInstance group, Map<ObjectInstance, ? extends Expr> mapKeys) {
        GroupObjectTable expandTable = expandTables.get(group);
        if(expandTable==null)
            return Where.FALSE;
        else
            return expandTable.getWhere(mapKeys);
    }

    private OrderedMap<Map<ObjectInstance, DataObject>, Map<ObjectInstance, ObjectValue>> executeTree(GroupObjectInstance group) throws SQLException {
        assert group.isInTree();

        Map<ObjectInstance, KeyExpr> mapKeys = KeyExpr.getMapKeys(GroupObjectInstance.getObjects(group.getUpTreeGroups()));

        Map<ObjectInstance,Expr> expandExprs = new HashMap<ObjectInstance, Expr>();

        Where expandWhere;
        if(group.getUpTreeGroup()!=null)
            expandWhere = getExpandWhere(group.getUpTreeGroup(), mapKeys); // для верхней группы брать только из expandTable'а
        else
            expandWhere = Where.TRUE;

        if(group.parent!=null) {
            for(Entry<ObjectInstance, PropertyObjectInstance> parentEntry : group.parent.entrySet())
                expandExprs.put(parentEntry.getKey(), parentEntry.getValue().getExpr(mapKeys, this));

            Where nullWhere = Where.FALSE;
            for(Expr expr : expandExprs.values())
                nullWhere = nullWhere.or(expr.getWhere().not());
            expandWhere = expandWhere.and(nullWhere).or(getExpandWhere(group,BaseUtils.override(mapKeys,expandExprs))); // если есть parent, то те чей parent равен null
        }

        OrderedMap<Expr, Boolean> orderExprs = new OrderedMap<Expr, Boolean>();
        for (Map.Entry<OrderInstance, Boolean> toOrder : group.orders.entrySet())
            orderExprs.put(toOrder.getKey().getExpr(mapKeys, this), toOrder.getValue());

        return new Query<ObjectInstance, ObjectInstance>(mapKeys, expandExprs, group.getWhere(mapKeys, this).and(expandWhere)).
                    executeClasses(session, BL.baseClass, orderExprs);
    }

    // возвращает OrderInstance из orderSeeks со значениями, а также если есть parent, то parent'ы
    private OrderedMap<Map<ObjectInstance, DataObject>, Map<OrderInstance, ObjectValue>> executeOrders(GroupObjectInstance group, Map<OrderInstance, ObjectValue> orderSeeks, int readSize, boolean down) throws SQLException {
        assert !group.isInTree();

        Map<ObjectInstance, KeyExpr> mapKeys = group.getMapKeys();

        // assertion что group.orders начинается с orderSeeks
        OrderedMap<OrderInstance, Boolean> orders;
        if (orderSeeks != null && readSize == 1) {
            orders = group.orders.moveStart(orderSeeks.keySet());
        } else {
            orders = group.orders;
        }

        assert !(orderSeeks != null && !orders.starts(orderSeeks.keySet()));

        Map<OrderInstance, Expr> orderExprs = new HashMap<OrderInstance, Expr>();
        for (Map.Entry<OrderInstance, Boolean> toOrder : orders.entrySet())
            orderExprs.put(toOrder.getKey(), toOrder.getKey().getExpr(mapKeys, this));

        Set<KeyExpr> usedContext = null;
        if (readSize == 1 && orderSeeks != null && down) { // в частном случае если есть "висячие" ключи не в фильтре и нужна одна запись ставим равно вместо >
            usedContext = new HashSet<KeyExpr>();
            group.getFilterWhere(mapKeys, this).enumKeys(usedContext); // именно после ff'са
            for (Expr expr : orderExprs.values()) {
                if (!(expr instanceof KeyExpr)) {
                    expr.enumKeys(usedContext);
                }
            }
        }

        Where orderWhere; // строим условия на упорядочивание
        if (orderSeeks != null) {
            orderWhere = Where.TRUE;
            for (Map.Entry<OrderInstance, Boolean> toOrder : orders.reverse().entrySet()) {
                ObjectValue toSeek = orderSeeks.get(toOrder.getKey());
                if (toSeek != null) {
                    Expr expr = orderExprs.get(toOrder.getKey());
                    if (readSize == 1
                            && down
                            && expr instanceof KeyExpr
                            && toSeek instanceof DataObject
                            && ((DataObject) toSeek).getType() instanceof DataClass
                            && !usedContext.contains((KeyExpr) expr)) {

                        orderWhere = orderWhere.and(new EqualsWhere((KeyExpr) expr, ((DataObject) toSeek).getExpr()));
                    } else {
                        orderWhere = toSeek.order(expr, toOrder.getValue(), orderWhere);
                    }
                }
            }
        } else {
            orderWhere = Where.FALSE;
        }

        return new Query<ObjectInstance, OrderInstance>(mapKeys, orderExprs, group.getWhere(mapKeys, this).and(down ? orderWhere : orderWhere.not())).
                    executeClasses(session, down ? orders : Query.reverseOrder(orders), readSize, BL.baseClass);
    }

    // считывает одну запись
    private Map.Entry<Map<ObjectInstance, DataObject>, Map<OrderInstance, ObjectValue>> readObjects(GroupObjectInstance group, Map<OrderInstance, ObjectValue> orderSeeks) throws SQLException {
        OrderedMap<Map<ObjectInstance, DataObject>, Map<OrderInstance, ObjectValue>> result = executeOrders(group, orderSeeks, 1, true);
        if (result.size() == 0)
            result = executeOrders(group, orderSeeks, 1, false);
        if (result.size() > 0)
            return result.singleEntry();
        else
            return null;
    }

    private Map<ObjectInstance, ? extends ObjectValue> readKeys(GroupObjectInstance group, Map<OrderInstance, ObjectValue> orderSeeks) throws SQLException {
        Entry<Map<ObjectInstance, DataObject>, Map<OrderInstance, ObjectValue>> objects = readObjects(group, orderSeeks);
        if (objects != null)
            return objects.getKey();
        else
            return group.getNulls();
    }

    private Map<OrderInstance, ObjectValue> readValues(GroupObjectInstance group, Map<OrderInstance, ObjectValue> orderSeeks) throws SQLException {
        Entry<Map<ObjectInstance, DataObject>, Map<OrderInstance, ObjectValue>> objects = readObjects(group, orderSeeks);
        if (objects != null)
            return objects.getValue();
        else
            return new HashMap<OrderInstance, ObjectValue>();
    }

    public static Map<ObjectInstance, DataObject> dataKeys(Map<ObjectInstance, ObjectValue> map) {
        return (Map<ObjectInstance, DataObject>) (Map<ObjectInstance, ? extends ObjectValue>) map;
    }

    // "закэшированная" проверка присутствия в интерфейсе, отличается от кэша тем что по сути функция от mutable объекта
    protected Set<PropertyDrawInstance> isDrawed = new HashSet<PropertyDrawInstance>();

    boolean refresh = true;

    private boolean classUpdated(Updated updated, GroupObjectInstance groupObject) {
        assert !refresh; // refresh нужен для инициализации (например DataObjectInstance) больше чем для самого refresh
        return updated.classUpdated(Collections.singleton(groupObject));
    }

    private boolean objectUpdated(Updated updated, GroupObjectInstance groupObject) {
        assert !refresh;
        return updated.objectUpdated(Collections.singleton(groupObject));
    }

    private boolean objectUpdated(Updated updated, Set<GroupObjectInstance> groupObjects) {
        for (GroupObjectInstance groupObject : groupObjects)
            if ((groupObject.updated & UPDATED_KEYS) != 0)
                return true;
        return updated.objectUpdated(groupObjects);
    }

    private boolean dataUpdated(Updated updated, Collection<Property> changedProps) {
        assert !refresh;
        return updated.dataUpdated(changedProps);
    }

    void applyFilters() {
        for (GroupObjectInstance group : groups)
            group.filters = group.getSetFilters();
    }

    void applyOrders() {
        for (GroupObjectInstance group : groups)
            group.orders = group.getSetOrders();
    }

    private class GroupObjectValue {
        private GroupObjectInstance group;
        private Map<ObjectInstance, ? extends ObjectValue> value;

        private GroupObjectValue(GroupObjectInstance group, Map<ObjectInstance, ? extends ObjectValue> value) {
            this.group = group;
            this.value = value;
        }
    }

    public FormChanges endApply() throws SQLException {

        ApplyTransaction transaction = new ApplyTransaction();

        final FormChanges result = new FormChanges();

        try {
            // если изменились данные, применяем изменения
            Collection<Property> changedProps;
            Collection<CustomClass> changedClasses = new HashSet<CustomClass>();
            if (dataChanged) {
                changedProps = session.update(this, changedClasses);
            } else {
                changedProps = new ArrayList<Property>();
            }

            GroupObjectValue updateGroupObject = null; // так как текущий groupObject идет относительно treeGroup, а не group
            for (GroupObjectInstance group : groups) {

                if ((group.updated & UPDATED_CLASSVIEW) != 0) {
                    result.classViews.put(group, group.curClassView);
                }

                if (group.curClassView == HIDE) continue;

                // если изменились класс грида или представление
                boolean updateKeys = refresh || (group.updated & (UPDATED_GRIDCLASS | UPDATED_CLASSVIEW)) != 0;

                if (FilterInstance.ignoreInInterface) {
                    updateKeys |= (group.updated & UPDATED_FILTER) != 0;
                    group.filters = group.getSetFilters();
                } else if ((group.updated & UPDATED_FILTER) != 0) {
                    Set<FilterInstance> newFilters = new HashSet<FilterInstance>();
                    for (FilterInstance filt : group.getSetFilters())
                        if (filt.isInInterface(group))
                            newFilters.add(filt);

                    updateKeys |= !newFilters.equals(group.filters);
                    group.filters = newFilters;
                } else // остались те же setFilters
                    for (FilterInstance filt : group.getSetFilters())
                        if (refresh || classUpdated(filt, group))
                            updateKeys |= (filt.isInInterface(group) ? group.filters.add(filt) : group.filters.remove(filt));

                // порядки
                OrderedMap<OrderInstance, Boolean> newOrders = new OrderedMap<OrderInstance, Boolean>();
                if ((group.updated & UPDATED_ORDER) != 0) {
                    for (Entry<OrderInstance, Boolean> setOrder : group.getSetOrders().entrySet())
                        if (setOrder.getKey().isInInterface(group))
                            newOrders.put(setOrder.getKey(), setOrder.getValue());
                    updateKeys |= !group.orders.equals(newOrders);
                } else { // значит setOrders не изменился
                    for (Entry<OrderInstance, Boolean> setOrder : group.getSetOrders().entrySet()) {
                        OrderInstance orderInstance = setOrder.getKey();

                        boolean isInInterface = group.orders.containsKey(orderInstance);
                        if ((refresh || classUpdated(orderInstance, group)) && !(orderInstance.isInInterface(group) == isInInterface)) {
                            isInInterface = !isInInterface;
                            updateKeys = true;
                        }
                        if (isInInterface)
                            newOrders.put(orderInstance, setOrder.getValue());
                    }
                }
                group.orders = newOrders;

                if (!updateKeys) // изменились "верхние" объекты для фильтров
                    for (FilterInstance filt : group.filters)
                        if (objectUpdated(filt, group)) {
                            updateKeys = true;
                            break;
                        }
                if (!updateKeys) // изменились "верхние" объекты для порядков
                    for (OrderInstance order : group.orders.keySet())
                        if (objectUpdated(order, group)) {
                            updateKeys = true;
                            break;
                        }
                if (!updateKeys) // изменились данные по фильтрам
                    for (FilterInstance filt : group.filters)
                        if (dataUpdated(filt, changedProps)) {
                            updateKeys = true;
                            break;
                        }
                if (!updateKeys) // изменились данные по порядкам
                    for (OrderInstance order : group.orders.keySet())
                        if (dataUpdated(order, changedProps)) {
                            updateKeys = true;
                            break;
                        }
                if (!updateKeys) // классы удалились\добавились
                    for (ObjectInstance object : group.objects)
                        if (object.classChanged(changedClasses)) {  // || object.classUpdated() сомнительный or
                            updateKeys = true;
                            break;
                        }

                Map<ObjectInstance, DataObject> currentObject = group.getGroupObjectValue();
                Map<OrderInstance, ObjectValue> orderSeeks = null;
                int direction = DIRECTION_CENTER;

                if(group.isInTree()) {
                    if (!updateKeys && (group.getUpTreeGroup() != null && ((group.getUpTreeGroup().updated & UPDATED_EXPANDS) != 0)) ||
                            (group.parent != null && (group.updated & UPDATED_EXPANDS) != 0))
                        updateKeys = true;
                } else {
                    if (userGroupSeeks.containsKey(group)) { // пользовательский поиск
                        Map<? extends OrderInstance, Object> userSeeks = userGroupSeeks.get(group);
                        if (userSeeks != null) {
                            orderSeeks = new HashMap<OrderInstance, ObjectValue>();
                            for (Entry<? extends OrderInstance, Object> userSeek : userSeeks.entrySet())
                                orderSeeks.put(userSeek.getKey(), session.getObjectValue(userSeek.getValue(), userSeek.getKey().getType()));
                        } else
                            orderSeeks = null;
                        updateKeys = true;
                        currentObject = new HashMap<ObjectInstance, DataObject>();
                    } else if (updateKeys) // изменились фильтры, порядки, вид, ищем текущий объект
                        orderSeeks = new HashMap<OrderInstance, ObjectValue>(currentObject);

                    if (!updateKeys && group.curClassView == GRID && !currentObject.isEmpty() && (group.updated & UPDATED_OBJECT) != 0) { // скроллирование
                        int keyNum = group.keys.indexOf(currentObject);
                        if (group.upKeys && keyNum < group.getPageSize()) { // если меньше PageSize осталось и сверху есть ключи
                            updateKeys = true;

                            int lowestInd = group.getPageSize() * 2 - 1;
                            if (lowestInd >= group.keys.size()) // по сути END
                                orderSeeks = null;
                            else {
                                direction = DIRECTION_UP;
                                orderSeeks = group.keys.getValue(lowestInd);
                            }
                        } else // наоборот вниз
                            if (group.downKeys && keyNum >= group.keys.size() - group.getPageSize()) { // assert что pageSize не null
                                updateKeys = true;

                                int highestInd = group.keys.size() - group.getPageSize() * 2;
                                if (highestInd < 0) // по сути HOME
                                    orderSeeks = new HashMap<OrderInstance, ObjectValue>();
                                else {
                                    direction = DIRECTION_DOWN;
                                    orderSeeks = group.keys.getValue(highestInd);
                                }
                            }
                    }
                }

                if (updateKeys) {
                    if (group.curClassView != GRID) {
                        // панель
                        updateGroupObject(group, result, readKeys(group, orderSeeks));
                    } else {
                        int activeRow = -1; // какой ряд выбранным будем считать
                        group.keys = new OrderedMap<Map<ObjectInstance, DataObject>, Map<OrderInstance, ObjectValue>>();

                        if (group.isInTree()) { // если дерево, то без поиска, но возможно с parent'ами
                            assert orderSeeks == null;

                            List<Map<ObjectInstance, DataObject>> expandParents = new ArrayList<Map<ObjectInstance, DataObject>>();
                            for(Entry<Map<ObjectInstance, DataObject>, Map<ObjectInstance, ObjectValue>> resultRow : executeTree(group).entrySet()) {
                                group.keys.put(resultRow.getKey(), new HashMap<OrderInstance, ObjectValue>());
                                expandParents.add(BaseUtils.filterClass(resultRow.getValue(), DataObject.class));
                            }
                            result.parentObjects.put(group, expandParents);
                        } else {
                            if (orderSeeks != null && !group.orders.starts(orderSeeks.keySet())) // если не "хватает" спереди ключей, дочитываем
                                orderSeeks = readValues(group, orderSeeks);

                            if (direction == DIRECTION_CENTER) { // оптимизируем если HOME\END, то читаем одним запросом
                                if (orderSeeks == null) { // END
                                    direction = DIRECTION_UP;
                                    group.downKeys = false;
                                } else if (orderSeeks.isEmpty()) { // HOME
                                    direction = DIRECTION_DOWN;
                                    group.upKeys = false;
                                }
                            } else {
                                group.downKeys = true;
                                assert !(orderSeeks == null);
                                group.upKeys = true;
                                assert !(orderSeeks != null && orderSeeks.isEmpty());
                            }

                            int readSize = group.getPageSize() * 3 / (direction == DIRECTION_CENTER ? 2 : 1);

                            if (direction == DIRECTION_UP || direction == DIRECTION_CENTER) { // сначала Up
                                group.keys.putAll(executeOrders(group, orderSeeks, readSize, false).reverse());
                                group.upKeys = (group.keys.size() == readSize);
                                activeRow = group.keys.size() - 1;
                            }
                            if (direction == DIRECTION_DOWN || direction == DIRECTION_CENTER) { // затем Down
                                OrderedMap<Map<ObjectInstance, DataObject>, Map<OrderInstance, ObjectValue>> executeList = executeOrders(group, orderSeeks, readSize, true);
                                if (executeList.size() > 0) activeRow = group.keys.size();
                                group.keys.putAll(executeList);
                                group.downKeys = (executeList.size() == readSize);
                            }
                        }

                        // параллельно будем обновлять ключи чтобы JoinSelect'ить
                        GroupObjectTable insertTable = groupTables.get(group);
                        if (insertTable == null) {
                            insertTable = new GroupObjectTable(group, "p", sessionID * RemoteFormInterface.GID_SHIFT + group.getID());
                            session.createTemporaryTable(insertTable.table);
                        }
                        
                        groupTables.put(group, insertTable.writeKeys(session, group.keys.keyList()));
                        result.gridObjects.put(group, group.keys.keyList());

                        if (!group.keys.containsKey(currentObject)) { // если нету currentObject'а, его нужно изменить
                            if(group.keys.isEmpty()) { // если ключей нету и верхняя группа, заменяем на null
                                if(group.getUpTreeGroup()==null)
                                    updateGroupObject = new GroupObjectValue(group, group.getNulls());
                            } else { // если верхняя группа или был объект заменяем на ряд по умолчанию
                                if(group.getUpTreeGroup()==null || !currentObject.isEmpty())
                                    updateGroupObject = new GroupObjectValue(group, group.keys.getKey(activeRow));
                            }
                        } else
                            updateGroupObject = new GroupObjectValue(group, currentObject);
                    }
                    group.updated = (group.updated | UPDATED_KEYS);
                } else
                    if((group.updated & UPDATED_OBJECT)!=0) { // так как объект может меняться скажем в результате ActionProperty и нужно об этом сказать клиенту
                        if(currentObject.isEmpty()) { // если null был выбран
                            if(group.getUpTreeGroup()==null) // если не внутренняя группа
                                updateGroupObject = new GroupObjectValue(group, group.getNulls());
                        } else // иначе выбираем этот объект
                            updateGroupObject = new GroupObjectValue(group, currentObject);
                    }

                if(group.getDownTreeGroups().size()==0 && updateGroupObject !=null) { // так как в tree группе currentObject друг на друга никак не влияют, то можно и нужно делать updateGroupObject в конце
                    updateGroupObject(updateGroupObject.group, result, updateGroupObject.value);
                    updateGroupObject = null;
                }
            }

            final Map<PropertyReadInstance, Set<GroupObjectInstance>> readProperties = new HashMap<PropertyReadInstance, Set<GroupObjectInstance>>();

            for (PropertyDrawInstance<?> drawProperty : properties) {
                if (drawProperty.toDraw != null && drawProperty.toDraw.curClassView == HIDE) continue;

                ClassViewType forceViewType = drawProperty.getForceViewType();
                if (forceViewType != null && forceViewType == HIDE) continue;

                boolean read = refresh || dataUpdated(drawProperty.propertyObject, changedProps);

                Set<GroupObjectInstance> columnGroupGrids = new HashSet<GroupObjectInstance>();
                for (GroupObjectInstance columnGroup : drawProperty.columnGroupObjects)
                    if (columnGroup.curClassView == GRID)
                        columnGroupGrids.add(columnGroup);
                    else
                        read = read || (columnGroup.updated & UPDATED_CLASSVIEW) != 0;

                boolean inInterface = false;
                Set<GroupObjectInstance> keyGridObjects = null;
                if (drawProperty.toDraw != null && drawProperty.toDraw.curClassView == GRID && (forceViewType == null || forceViewType == GRID) &&
                        drawProperty.propertyObject.isInInterface(keyGridObjects = BaseUtils.addSet(columnGroupGrids, drawProperty.toDraw), forceViewType != null)) { // в grid'е
                    inInterface = true;
                    if (read || objectUpdated(drawProperty.propertyObject, keyGridObjects))
                        readProperties.put(drawProperty, keyGridObjects);
                } else if (drawProperty.propertyObject.isInInterface(columnGroupGrids, false)) { // в панели
                    inInterface = true;
                    if (read || (drawProperty.toDraw != null && (drawProperty.toDraw.updated & (UPDATED_CLASSVIEW | UPDATED_GRIDCLASS)) != 0) || objectUpdated(drawProperty.propertyObject, columnGroupGrids)) {
                        readProperties.put(drawProperty, columnGroupGrids);
                        result.panelProperties.add(drawProperty);
                    }
                }

                if (inInterface) {
                    boolean added = isDrawed.add(drawProperty); // читаем title'ы
                    if (drawProperty.propertyCaption != null
                        && (added
                            || refresh
                            || (drawProperty.toDraw != null && (drawProperty.toDraw.updated & UPDATED_CLASSVIEW) != 0) // чтобы на клиента updateCaptions пришли а то они не приходят
                            || dataUpdated(drawProperty.propertyCaption, changedProps) // не было надо title перечитать
                            || objectUpdated(drawProperty.propertyCaption, columnGroupGrids))) {
                        readProperties.put(drawProperty.caption, columnGroupGrids);
                    }
                } else if (isDrawed.remove(drawProperty)) {
                    // вкидываем удаление из интерфейса
                    result.dropProperties.add(drawProperty);
                }
            }

            for (GroupObjectInstance group : groups) // читаем highlight'ы
                if (group.propertyHighlight != null) {
                    Set<GroupObjectInstance> gridGroups = (group.curClassView == GRID ? Collections.singleton(group) : new HashSet<GroupObjectInstance>());
                    if (refresh || (group.updated & UPDATED_CLASSVIEW) != 0 || dataUpdated(group.propertyHighlight, changedProps) || objectUpdated(group.propertyHighlight, gridGroups))
                        readProperties.put(group, gridGroups);
                }

            for (Entry<Set<GroupObjectInstance>, Set<PropertyReadInstance>> entry : BaseUtils.groupSet(readProperties).entrySet()) {
                Set<GroupObjectInstance> keyGroupObjects = entry.getKey();
                Set<PropertyReadInstance> propertyList = entry.getValue();

                Query<ObjectInstance, PropertyReadInstance> selectProps = new Query<ObjectInstance, PropertyReadInstance>(GroupObjectInstance.getObjects(getUpTreeGroups(keyGroupObjects)));
                for (GroupObjectInstance keyGroup : keyGroupObjects) {
                    GroupObjectTable groupTable = groupTables.get(keyGroup);
                    selectProps.and(groupTable.getWhere(selectProps.mapKeys));
                }

                for (PropertyReadInstance propertyDraw : propertyList) {
                    selectProps.properties.put(propertyDraw, propertyDraw.getPropertyObject().getExpr(selectProps.mapKeys, this));
                }

                OrderedMap<Map<ObjectInstance, Object>, Map<PropertyReadInstance, Object>> queryResult = selectProps.execute(session);
                for (PropertyReadInstance propertyDraw : propertyList) {
                    Map<Map<ObjectInstance, DataObject>, Object> propertyValues = new HashMap<Map<ObjectInstance, DataObject>, Object>();
                    for (Entry<Map<ObjectInstance, Object>, Map<PropertyReadInstance, Object>> resultRow : queryResult.entrySet()) {
                        Map<ObjectInstance, Object> keyRow = resultRow.getKey();

                        Map<ObjectInstance, DataObject> row = new HashMap<ObjectInstance, DataObject>();
                        for (GroupObjectInstance keyGroup : keyGroupObjects) {
                            row.putAll(keyGroup.findGroupObjectValue(keyRow));
                        }

                        propertyValues.put(row, resultRow.getValue().get(propertyDraw));
                    }

                    result.properties.put(propertyDraw, propertyValues);
                }
            }

        } catch (ComplexQueryException e) {
            transaction.rollback();
            if (dataChanged) { // если изменились данные cancel'им изменения
                cancelChanges();
                FormChanges cancelResult = endApply();
                cancelResult.message = e.getMessage() + ". Изменения будут отменены";
                return cancelResult;
            } else
                throw e;
        } catch (RuntimeException e) {
            transaction.rollback();
            throw e;
        } catch (SQLException e) {
            transaction.rollback();
            throw e;
        }

        if (dataChanged)
            result.dataChanged = session.changes.hasChanges();

        userGroupSeeks.clear();

        // сбрасываем все пометки
        for (GroupObjectInstance group : groups) {
            for (ObjectInstance object : group.objects)
                object.updated = 0;
            group.updated = 0;
        }
        refresh = false;
        dataChanged = false;

//        result.out(this);

        return result;
    }

    // возвращает какие объекты на форме показываются
    private Set<GroupObjectInstance> getPropertyGroups() {

        Set<GroupObjectInstance> reportObjects = new HashSet<GroupObjectInstance>();
        for (GroupObjectInstance group : groups)
            if (group.curClassView != HIDE)
                reportObjects.add(group);

        return reportObjects;
    }

    // возвращает какие объекты на форме не фиксируются
    private Set<GroupObjectInstance> getClassGroups() {

        Set<GroupObjectInstance> reportObjects = new HashSet<GroupObjectInstance>();
        for (GroupObjectInstance group : groups)
            if (group.curClassView == GRID)
                reportObjects.add(group);

        return reportObjects;
    }

    // считывает все данные с формы
    public FormData getFormData(Collection<PropertyDrawInstance> propertyDraws, Set<GroupObjectInstance> classGroups) throws SQLException {

        applyFilters();
        applyOrders();

        // пока сделаем тупо получаем один большой запрос

        Query<ObjectInstance, Object> query = new Query<ObjectInstance, Object>(GroupObjectInstance.getObjects(classGroups));
        OrderedMap<Object, Boolean> queryOrders = new OrderedMap<Object, Boolean>();

        for (GroupObjectInstance group : groups) {

            if (classGroups.contains(group)) {

                // не фиксированные ключи
                query.and(group.getWhere(query.mapKeys, this));

                // закинем Order'ы
                for (Entry<OrderInstance, Boolean> order : group.orders.entrySet()) {
                    query.properties.put(order.getKey(), order.getKey().getExpr(query.mapKeys, this));
                    queryOrders.put(order.getKey(), order.getValue());
                }

                for (ObjectInstance object : group.objects) {
                    query.properties.put(object, object.getExpr(query.mapKeys, this));
                    queryOrders.put(object, false);
                }
            }
        }

        FormData result = new FormData();

        for (PropertyDrawInstance<?> property : propertyDraws)
            query.properties.put(property, property.propertyObject.getExpr(query.mapKeys, this));

        OrderedMap<Map<ObjectInstance, Object>, Map<Object, Object>> resultSelect = query.execute(session, queryOrders, 0);
        for (Entry<Map<ObjectInstance, Object>, Map<Object, Object>> row : resultSelect.entrySet()) {
            Map<ObjectInstance, Object> groupValue = new HashMap<ObjectInstance, Object>();
            for (GroupObjectInstance group : groups)
                for (ObjectInstance object : group.objects)
                    if (classGroups.contains(group))
                        groupValue.put(object, row.getKey().get(object));
                    else
                        groupValue.put(object, object.getObjectValue().getValue());

            Map<PropertyDrawInstance, Object> propertyValues = new HashMap<PropertyDrawInstance, Object>();
            for (PropertyDrawInstance property : propertyDraws)
                propertyValues.put(property, row.getValue().get(property));

            result.add(groupValue, propertyValues);
        }

        return result;
    }

    public DialogInstance<T> createClassPropertyDialog(int viewID, int value) throws RemoteException, SQLException {
        ClassFormEntity<T> classForm = new ClassFormEntity<T>(BL, getPropertyDraw(viewID).propertyObject.getDialogClass());
        return new DialogInstance<T>(classForm, BL, session, securityPolicy, getFocusListener(), getClassListener(), classForm.object, instanceFactory.computer, value);
    }

    public DialogInstance<T> createEditorPropertyDialog(int viewID) throws SQLException {

        PropertyObjectInstance propertyObject = getPropertyDraw(viewID).propertyObject;
        PropertyObjectInstance<?> change = propertyObject.getChangeInstance();

        DataChangeFormEntity<T> formEntity = new DataChangeFormEntity<T>(BL, change.getDialogClass(), change.getValueImplement());

        ObjectEntity dialogObject = formEntity.object;
        DialogInstance<T> dialog = new DialogInstance<T>(formEntity, BL, session, securityPolicy, getFocusListener(), getClassListener(), dialogObject, instanceFactory.computer, change.read(session, this));

        Property filterProperty = propertyObject.property.getFilterProperty();
        if (filterProperty != null) {
            PropertyDrawEntity filterPropertyDraw = formEntity.getPropertyDraw(filterProperty, dialogObject);
            if (filterPropertyDraw == null) {
                filterPropertyDraw = formEntity.addPropertyDraw(new LP(filterProperty), dialogObject);
            }
            dialog.initFilterPropertyDraw = filterPropertyDraw;
        }

        return dialog;
    }

    private List<ClientAction> executeAutoActions(ObjectInstance object, RemoteForm form) throws SQLException {

        List<ClientAction> actions = new ArrayList<ClientAction>();
        for (Entry<ObjectEntity, List<PropertyObjectEntity>> autoActions : entity.autoActions.entrySet())
            if (object.equals(instanceFactory.getInstance(autoActions.getKey())))
                for (PropertyObjectEntity autoAction : autoActions.getValue()) {
                    PropertyObjectInstance action = instanceFactory.getInstance(autoAction);
                    if (action.isInInterface(null)) {
                        List<ClientAction> change = changeProperty(action, action.getChangeInstance().read(session, this) == null ? true : null, form);
                        if (change != null) {
                            actions.addAll(change);
                        }
                    }
                }
        return actions;
    }
}

