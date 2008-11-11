/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package platformlocal;

import java.sql.SQLException;

import java.util.*;
import static java.lang.Thread.sleep;

class TableImplement extends ArrayList<DataPropertyInterface> {
    // заполняются пока автоматически
    Table Table;
    Map<DataPropertyInterface,KeyField> MapFields;

    TableImplement() {
        this(null);
    }

    TableImplement(String iID) {
        ID = iID;
        Childs = new HashSet<TableImplement>();
        Parents = new HashSet<TableImplement>();
    }

    String ID = null;
    String getID() {

        if (ID != null) return ID;

        String result = "";
        for (DataPropertyInterface propint : this) {
            result += "_" + propint.Class.ID.toString();
        }
        return result;
    }

    // кэшированный граф
    Set<TableImplement> Childs;
    Set<TableImplement> Parents;

    // Operation на что сравниваем
    // 0 - не ToParent
    // 1 - ToParent
    // 2 - равно
    
    boolean RecCompare(int Operation,Collection<DataPropertyInterface> ToCompare,ListIterator<DataPropertyInterface> iRec,Map<DataPropertyInterface,DataPropertyInterface> MapTo) {
        if(!iRec.hasNext()) return true;

        DataPropertyInterface ProceedItem = iRec.next();
        for(DataPropertyInterface PairItem : ToCompare) {
            if((Operation==1 && ProceedItem.Class.isParent(PairItem.Class) || (Operation==0 && PairItem.Class.isParent(ProceedItem.Class))) || (Operation==2 && PairItem.Class == ProceedItem.Class)) {
                if(!MapTo.containsKey(PairItem)) {
                    // если parent - есть связь и нету ключа, гоним рекурсию дальше
                    MapTo.put(PairItem, ProceedItem);
                    // если нашли карту выходим
                    if(RecCompare(Operation,ToCompare,iRec,MapTo)) return true;
                    MapTo.remove(PairItem);
                }
            }
        }

        iRec.previous();
        return false;
    }
    // 0 никак не связаны, 1 - параметр снизу в дереве, 2 - параметр сверху в дереве, 3 - равно
    // также возвращает карту если 2
    int Compare(Collection<DataPropertyInterface> ToCompare,Map<KeyField,DataPropertyInterface> MapTo) {
        
        if(ToCompare.size() != size()) return 0;

        // перебором и не будем страдать фигней
        // сначала что не 1 проверим
    
        HashMap<DataPropertyInterface,DataPropertyInterface> MapProceed = new HashMap();
        
        ListIterator<DataPropertyInterface> iRec = (new ArrayList<DataPropertyInterface>(this)).listIterator();
        int Relation = 0;
        if(RecCompare(2,ToCompare,iRec,MapProceed)) Relation = 3;
        if(Relation==0 && RecCompare(0,ToCompare,iRec,MapProceed)) Relation = 2;
        if(Relation>0) {
            if(MapTo!=null) {
                MapTo.clear();
                for(DataPropertyInterface DataInterface : ToCompare)
                    MapTo.put(MapFields.get(MapProceed.get(DataInterface)),DataInterface);
            }
            
            return Relation;
        }

        // MapProceed и так чистый и iRec также в начале
        if(RecCompare(1,ToCompare,iRec,MapProceed)) Relation = 1;
        
        // !!!! должна заполнять MapTo только если уже нашла
        return Relation;
    }

    void RecIncludeIntoGraph(TableImplement IncludeItem,boolean ToAdd,Set<TableImplement> Checks) {
        
        if(Checks.contains(this)) return;
        Checks.add(this);
        
        Iterator<TableImplement> i = Parents.iterator();
        while(i.hasNext()) {
            TableImplement Item = i.next();
            Integer Relation = Item.Compare(IncludeItem,null);
            if(Relation==1) {
                // снизу в дереве
                // добавляем ее как промежуточную
                Item.Childs.add(IncludeItem);
                IncludeItem.Parents.add(Item);
                if(ToAdd) {
                    Item.Childs.remove(this);
                    i.remove();
                }
            } else {
                // сверху в дереве или никак не связаны
                // передаем дальше
                if(Relation!=3) Item.RecIncludeIntoGraph(IncludeItem,Relation==2,Checks);
                if(Relation==2 || Relation==3) ToAdd = false;
            }
        }
        
        // если снизу добавляем Childs
        if(ToAdd) {
            IncludeItem.Childs.add(this);
            Parents.add(IncludeItem);
        }
    }

    Table GetTable(Collection<DataPropertyInterface> FindItem,Map<KeyField,DataPropertyInterface> MapTo) {
        for(TableImplement Item : Parents) {
            int Relation = Item.Compare(FindItem,MapTo);
            if(Relation==2 || Relation==3)
                return Item.GetTable(FindItem,MapTo);
        }
        
        return Table;
    }
    
    void FillSet(Set<TableImplement> TableImplements) {
        if(!TableImplements.add(this)) return;
        for(TableImplement Parent : Parents) Parent.FillSet(TableImplements);
    }

    void OutClasses() {
        for(DataPropertyInterface Interface : this)
            System.out.print(Interface.Class.ID.toString()+" ");
    }
    void Out() {
        //выводим себя
        System.out.print("NODE - ");
        OutClasses();
        System.out.println("");
        
        for(TableImplement Child : Childs) {
            System.out.print("children - ");
            Child.OutClasses();
            System.out.println();
        }

        for(TableImplement Parent : Parents) {
            System.out.print("parents - ");
            Parent.OutClasses();
            System.out.println();
        }
        
        for(TableImplement Parent : Parents) Parent.Out();
    }
}

// таблица в которой лежат объекты
class ObjectTable extends Table {
    
    KeyField Key;
    PropertyField Class;
    
    ObjectTable() {
        super("objects");
        Key = new KeyField("object",Type.Object);
        Keys.add(Key);
        Class = new PropertyField("class",Type.System);
        Properties.add(Class);
    };
    
    Integer GetClassID(DataSession Session,Integer idObject) throws SQLException {
        if(idObject==null) return null;

        JoinQuery<Object,String> Query = new JoinQuery<Object,String>();
        Join<KeyField,PropertyField> JoinTable = new Join<KeyField,PropertyField>(this,true);
        JoinTable.Joins.put(Key,new ValueSourceExpr(idObject,Key.Type));
        Query.add("classid",JoinTable.Exprs.get(Class));
        LinkedHashMap<Map<Object,Integer>,Map<String,Object>> Result = Query.executeSelect(Session);
        if(Result.size()>0)
            return (Integer)Result.values().iterator().next().get("classid");
        else
            return null;        
    }

    JoinQuery<KeyField,PropertyField> getClassJoin(Class ChangeClass) {

        Collection<Integer> IDSet = new ArrayList<Integer>();
        Collection<Class> ClassSet = new ArrayList<Class>();
        ChangeClass.fillChilds(ClassSet);
        for(Class ChildClass : ClassSet)
            IDSet.add(ChildClass.ID);

        JoinQuery<KeyField,PropertyField> ClassQuery = new JoinQuery<KeyField,PropertyField>(Keys);
        ClassQuery.add(new FieldSetValueWhere((new UniJoin<KeyField,PropertyField>(this,ClassQuery,true)).Exprs.get(Class), IDSet));

        return ClassQuery;
    }
}

// таблица счетчика ID
class IDTable extends Table {
    KeyField Key;
    PropertyField Value;
    
    IDTable() {
        super("idtable");
        Key = new KeyField("id",Type.System);
        Keys.add(Key);
        
        Value = new PropertyField("value",Type.System);
        Properties.add(Value);
    }

    public static int OBJECT = 1;
    public static int FORM = 2;

    static List<Integer> Enum = new ArrayList();
    static {
        Enum.add(OBJECT);
        Enum.add(FORM);
    }

    Integer GenerateID(DataSession dataSession, int idType) throws SQLException {

        if(BusinessLogics.AutoFillDB) return BusinessLogics.AutoIDCounter++;
        // читаем
        JoinQuery<KeyField,PropertyField> Query = new JoinQuery<KeyField,PropertyField>(Keys);
        Join<KeyField,PropertyField> JoinTable = new Join<KeyField,PropertyField>(this,true);
        JoinTable.Joins.put(Key,Query.MapKeys.get(Key));
        Query.add(Value,JoinTable.Exprs.get(Value));

        Query.add(new FieldExprCompareWhere(Query.MapKeys.get(Key),idType,FieldExprCompareWhere.EQUALS));

        Integer FreeID = (Integer)Query.executeSelect(dataSession).values().iterator().next().get(Value);

        // замещаем
        reserveID(dataSession, idType, FreeID);
        return FreeID+1;
    }

    void reserveID(DataSession Session, int idType, Integer ID) throws SQLException {
        Map<KeyField,Integer> KeyFields = new HashMap();
        KeyFields.put(Key,idType);
        Map<PropertyField, TypedObject> PropFields = new HashMap();
        PropFields.put(Value,new TypedObject(ID +1,Value.Type));
        Session.UpdateRecords(new ModifyQuery(this,new DumbSource<KeyField,PropertyField>(KeyFields,PropFields)));
    }
}

// таблица куда виды складывают свои объекты
class ViewTable extends SessionTable {
    ViewTable(Integer iObjects) {
        super("viewtable"+iObjects.toString());
        Objects = new ArrayList();
        for(Integer i=0;i<iObjects;i++) {
            KeyField ObjKeyField = new KeyField("object"+i,Type.Object);
            Objects.add(ObjKeyField);
            Keys.add(ObjKeyField);
        }
        
        View = new KeyField("viewid",Type.System);
        Keys.add(View);
    }
            
    List<KeyField> Objects;
    KeyField View;
    
    void DropViewID(DataSession Session,Integer ViewID) throws SQLException {
        Map<KeyField,Integer> ValueKeys = new HashMap();
        ValueKeys.put(View,ViewID);
        Session.deleteKeyRecords(this,ValueKeys);
    }
}

abstract class ChangeTable extends SessionTable {

//    KeyField Session;
    
    ChangeTable(String iName) {
        super(iName);

//        Session = new KeyField("session","integer");
//        Keys.add(Session);
    }
}

class ChangeObjectTable extends ChangeTable {

    Collection<KeyField> Objects;
    KeyField Property;
    PropertyField Value;
    
    ChangeObjectTable(String TablePrefix,Integer iObjects,Integer iDBType) {
        super(TablePrefix+"changetable"+iObjects+"t"+iDBType);

        Objects = new ArrayList();
        for(Integer i=0;i<iObjects;i++) {
            KeyField ObjKeyField = new KeyField("object"+i,Type.Object);
            Objects.add(ObjKeyField);
            Keys.add(ObjKeyField);
        }
        
        Property = new KeyField("property",Type.System);
        Keys.add(Property);
        
        Value = new PropertyField("value",Type.Enum.get(iDBType));
        Properties.add(Value);
    }
}

class DataChangeTable extends ChangeObjectTable {

    DataChangeTable(Integer iObjects,Integer iDBType) {
        super("data",iObjects,iDBType);
    }
}

class IncrementChangeTable extends ChangeObjectTable {

    PropertyField PrevValue;

    IncrementChangeTable(Integer iObjects,Integer iDBType) {
        super("inc",iObjects,iDBType);

        PrevValue = new PropertyField("prevvalue",Type.Enum.get(iDBType));
        Properties.add(PrevValue);
    }
}

// таблица изменений классов
// хранит добавляение\удаление классов
class ChangeClassTable extends ChangeTable {

    KeyField Class;
    KeyField Object;

    ChangeClassTable(String iTable) {
        super(iTable);

        Object = new KeyField("object",Type.Object);
        Keys.add(Object);

        Class = new KeyField("class",Type.System);
        Keys.add(Class);
    }
    
    void changeClass(DataSession ChangeSession, Integer idObject, Collection<Class> Classes,boolean Drop) throws SQLException {

        for(Class Change : Classes) {
            Map<KeyField,Integer> ChangeKeys = new HashMap();
            ChangeKeys.put(Object,idObject);
            ChangeKeys.put(Class,Change.ID);
            if(Drop) {
                if(!BusinessLogics.AutoFillDB)
                    ChangeSession.deleteKeyRecords(this,ChangeKeys);
            } else
                ChangeSession.InsertRecord(this,ChangeKeys,new HashMap());
        }
    }

    void DropSession(DataSession ChangeSession) throws SQLException {
        Map<KeyField,Integer> ValueKeys = new HashMap();
        ChangeSession.deleteKeyRecords(this,ValueKeys);
    }
    
    JoinQuery<KeyField,PropertyField> getClassJoin(DataSession ChangeSession,Class ChangeClass) {

        Collection<KeyField> ObjectKeys = new ArrayList();
        ObjectKeys.add(Object);
        JoinQuery<KeyField,PropertyField> ClassQuery = new JoinQuery<KeyField,PropertyField>(ObjectKeys);

        Join<KeyField,PropertyField> ClassJoin = new Join<KeyField,PropertyField>(this,true);
        ClassJoin.Joins.put(Object,ClassQuery.MapKeys.get(Object));
        ClassJoin.Joins.put(Class,new ValueSourceExpr(ChangeClass.ID,Class.Type));
        ClassQuery.add(ClassJoin);

        return ClassQuery;
    }

}

class AddClassTable extends ChangeClassTable {

    AddClassTable() {
        super("addchange");
    }
}

class RemoveClassTable extends ChangeClassTable {

    RemoveClassTable() {
        super("removechange");
    }

    void excludeJoin(JoinQuery<?,?> Query, DataSession Session,Class ChangeClass,SourceExpr Join) {
        Join<KeyField,PropertyField> ClassJoin = new Join<KeyField,PropertyField>(getClassJoin(Session,ChangeClass),false);
        ClassJoin.Joins.put(Object,Join);
        Query.add(new NotWhere(ClassJoin.InJoin));
    }

}

class TableFactory extends TableImplement{
    
    ObjectTable ObjectTable;
    IDTable idTable;
    List<ViewTable> ViewTables;
    List<List<DataChangeTable>> DataChangeTables;
    List<List<IncrementChangeTable>> ChangeTables;
    
    AddClassTable AddClassTable;
    RemoveClassTable RemoveClassTable;
    
    // для отладки
    boolean ReCalculateAggr = false;
    boolean Crash = false;
    
    IncrementChangeTable GetChangeTable(Integer Objects, Type DBType) {
        return ChangeTables.get(Objects-1).get(Type.Enum.indexOf(DBType));
    }
    
    DataChangeTable GetDataChangeTable(Integer Objects, Type DBType) {
        return DataChangeTables.get(Objects-1).get(Type.Enum.indexOf(DBType));
    }

    int MaxBeanObjects = 3;
    int MaxInterface = 4;
    
    TableFactory() {
        ObjectTable = new ObjectTable();
        idTable = new IDTable();
        ViewTables = new ArrayList();
        ChangeTables = new ArrayList();
        DataChangeTables = new ArrayList();
        
        AddClassTable = new AddClassTable();
        RemoveClassTable = new RemoveClassTable();
        
        for(int i=1;i<=MaxBeanObjects;i++)
            ViewTables.add(new ViewTable(i));

        for(int i=1;i<=MaxInterface;i++) {
            List<IncrementChangeTable> ObjChangeTables = new ArrayList();
            ChangeTables.add(ObjChangeTables);
            for(int j=0;j<Type.Enum.size();j++)
                ObjChangeTables.add(new IncrementChangeTable(i,j));
        }

        for(int i=1;i<=MaxInterface;i++) {
            List<DataChangeTable> ObjChangeTables = new ArrayList();
            DataChangeTables.add(ObjChangeTables);
            for(int j=0;j<Type.Enum.size();j++)
                ObjChangeTables.add(new DataChangeTable(i,j));
        }
    }

    void IncludeIntoGraph(TableImplement IncludeItem) {
        Set<TableImplement> Checks = new HashSet<TableImplement>();
        RecIncludeIntoGraph(IncludeItem,true,Checks);
    }
    
    void FillDB(DataSession Session, boolean createTable) throws SQLException {
        Set<TableImplement> TableImplements = new HashSet<TableImplement>();
        FillSet(TableImplements);
        
        for(TableImplement Node : TableImplements) {
            Node.Table = new Table("table"+Node.getID());
            Node.MapFields = new HashMap<DataPropertyInterface,KeyField>();
            Integer FieldNum = 0;
            for(DataPropertyInterface Interface : Node) {
                FieldNum++;
                KeyField Field = new KeyField("key"+FieldNum.toString(),Type.Object);
                Node.Table.Keys.add(Field);
                Node.MapFields.put(Interface,Field);
            }
        }

        if (createTable) {

            Session.CreateTable(ObjectTable);
            Session.CreateTable(idTable);

            for (Integer idType : IDTable.Enum) {
                // закинем одну запись
                Map<KeyField,Integer> InsertKeys = new HashMap<KeyField,Integer>();
                InsertKeys.put(idTable.Key, idType);
                Map<PropertyField,Object> InsertProps = new HashMap<PropertyField,Object>();
                InsertProps.put(idTable.Value,0);
                Session.InsertRecord(idTable,InsertKeys,InsertProps);
            }
        }

    }

    // заполняет временные таблицы
    void fillSession(DataSession Session) throws SQLException {
        
        Session.CreateTemporaryTable(AddClassTable);
        Session.CreateTemporaryTable(RemoveClassTable);

        for(List<IncrementChangeTable> ListTables : ChangeTables)
            for(ChangeObjectTable ChangeTable : ListTables) Session.CreateTemporaryTable(ChangeTable);
        for(List<DataChangeTable> ListTables : DataChangeTables)
            for(ChangeObjectTable DataChangeTable : ListTables) Session.CreateTemporaryTable(DataChangeTable);

        for(ViewTable ViewTable : ViewTables) Session.CreateTemporaryTable(ViewTable);
    }

    // счетчик сессий (пока так потом надо из базы или как-то по другому транзакционность сделать
    void clearSession(DataSession Session) throws SQLException {

        Session.deleteKeyRecords(AddClassTable,new HashMap());
        Session.deleteKeyRecords(RemoveClassTable,new HashMap());

        for(List<IncrementChangeTable> TypeTables : ChangeTables)
            for(ChangeObjectTable Table : TypeTables)
                Session.deleteKeyRecords(Table,new HashMap());
        for(List<DataChangeTable> TypeTables : DataChangeTables)
            for(ChangeObjectTable Table : TypeTables)
                Session.deleteKeyRecords(Table,new HashMap());
    }
}

abstract class BusinessLogics<T extends BusinessLogics<T>> implements PropertyUpdateView {

    // счетчик идентификаторов
    void initBase() {
        TableFactory = new TableFactory();

        objectClass = new ObjectClass(0, "Объект");
        objectClass.addParent(Class.base);

        for(int i=0;i<TableFactory.MaxInterface;i++) {
            TableImplement Include = new TableImplement();
            for(int j=0;j<=i;j++)
                Include.add(new DataPropertyInterface(j,Class.base));
            TableFactory.IncludeIntoGraph(Include);
        }         
        
        baseElement = new NavigatorElement<T>(0, "Base Group");
    }

    // по умолчанию с полным стартом
    BusinessLogics() {
        initBase();

        InitLogics();
        InitImplements();
        InitNavigators();
    }

    public boolean toSave(Property Property) {
        return Persistents.contains(Property);
    }

    public Collection<Property> getNoUpdateProperties() {
        return new ArrayList<Property>();
    }

    static Set<Integer> WereSuspicious = new HashSet();

    // тестирующий конструктор
    BusinessLogics(int TestType) throws ClassNotFoundException, SQLException, InstantiationException, IllegalAccessException {
        initBase();

        if(TestType>=1) {
            InitLogics();
            if(TestType>=2)
                InitImplements();
        }

        Integer Seed;
        List<Integer> ProceedSeeds = new ArrayList();
        int[] Suspicious = {888,1252,8773,9115,8700,9640,2940,4611,8038};
        if(TestType>=0 || WereSuspicious.size()>=Suspicious.length)
            Seed = (new Random()).nextInt(10000);
        else {
            while(true) {
                Seed = Suspicious[(new Random()).nextInt(Suspicious.length)];
                if(!WereSuspicious.contains(Seed)) {
                    WereSuspicious.add(Seed);
                    break;
                }
            }
        }

//        Seed = 4518;
//        Seed = 3936;
//        Seed = 8907;
//        Seed = 6646;
        System.out.println("Random seed - "+Seed);

        Random Randomizer = new Random(Seed);

        DataAdapter Adapter = Main.getDefault();
        Adapter.createDB();

        if(TestType<1) {
            RandomClasses(Randomizer);
            RandomProperties(Randomizer);
        }

        if(TestType<2) {
            RandomImplement(Randomizer);
            RandomPersistent(Randomizer);
        }

        DataSession Session = createSession(Adapter);
        FillDB(Session, true);
        Session.close();

        // запустить ChangeDBTest
        try {
            ChangeDBTest(Adapter,20,Randomizer);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    abstract void InitGroups();
    abstract void InitClasses();
    abstract void InitProperties();
    abstract void InitConstraints();
    
    // инициализируется логика
    void InitLogics() {
        InitGroups();
        InitClasses();
        InitProperties();
        InitConstraints();
    }
    
    abstract void InitPersistents();
    abstract void InitTables();
    abstract void InitIndexes();
    
    void InitImplements() {
        InitPersistents();
        InitTables();
        InitIndexes();
    }

    NavigatorElement<T> baseElement;
    
    abstract void InitNavigators();
    
    void AddDataProperty(DataProperty Property) {
        Properties.add(Property);
    }

    Integer AddObject(DataSession Session, Class Class) throws SQLException {

        Integer FreeID = TableFactory.idTable.GenerateID(Session, IDTable.OBJECT);

        ChangeClass(Session,FreeID,Class);
        
        return FreeID;
    }
    
    void ChangeClass(DataSession Session, Integer idObject, Class Class) throws SQLException {
        
        // запишем объекты, которые надо будет сохранять
        Session.changeClass(idObject,Class);
    }
    
    // счетчик сессий (пока так потом надо из базы или как-то по другому транзакционность сделать
    int SessionCounter = 0;
    DataSession createSession(DataAdapter Adapter) throws SQLException {
        return new DataSession(Adapter,SessionCounter++,TableFactory,objectClass);
    }

    ObjectClass objectClass;

    TableFactory TableFactory;
    List<Property> Properties = new ArrayList();
    Set<AggregateProperty> Persistents = new HashSet();
    Map<Property,Constraint> Constraints = new HashMap();
    Set<List<Property>> Indexes = new HashSet();

    // проверяет Constraints
    String CheckConstraints(DataSession Session) throws SQLException {

        for(Property Property : Constraints.keySet())
            if(Session.PropertyChanges.containsKey(Property)) {
                String ConstraintResult = Constraints.get(Property).Check(Session,Property);
                if(ConstraintResult!=null) return ConstraintResult;
            }

        return null;
    }

    public Collection<Property> getUpdateProperties() {
        Collection<Property> UpdateList = new HashSet(Persistents);
        UpdateList.addAll(Constraints.keySet());
        for(Property Property : Properties)
            if(Property instanceof DataProperty) UpdateList.add(Property);
        return UpdateList;
    }

    String Apply(DataSession Session) throws SQLException {
        // делается UpdateAggregations (для мн-ва persistent+constraints)
        Session.startTransaction();

        List<Property> ChangedList = Session.update(this,new HashSet());
        Session.IncrementChanges.remove(this);

        // проверим Constraints
        String Constraints = CheckConstraints(Session);
        if(Constraints!=null) {
            // откатим транзакцию
            Session.rollbackTransaction();
            return Constraints;
        }            

        Session.saveClassChanges();

        // сохранить св-ва которые Persistent, те что входят в Persistents и DataProperty
        for(Property Property : ChangedList)
            if(Property instanceof DataProperty || Persistents.contains(Property))
                Session.PropertyChanges.get(Property).apply(Session);
/*
        System.out.println("All Changes");
        for(List<IncrementChangeTable> ListTables : TableFactory.ChangeTables)
           for(ChangeObjectTable ChangeTable : ListTables) ChangeTable.outSelect(Session);
  */      
        Session.commitTransaction();
        Session.restart(false);
        
        return null;        
    }

    void FillDB(DataSession Session, boolean createTable) throws SQLException {

        // инициализируем таблицы
        TableFactory.FillDB(Session, createTable);

        // запишем ID'ки
        int IDPropNum = 0;
        for(Property Property : Properties)
            Property.ID = IDPropNum++;
        
        Set<DataProperty> DataProperties = new HashSet();
        Collection<AggregateProperty> AggrProperties = new ArrayList();
        Map<Table,Integer> Tables = new HashMap<Table,Integer>();
        
        // закинем в таблицы(создав там все что надо) св-ва
        for(Property Property : Properties) {
            // ChangeTable'ы заполним
            Property.FillChangeTable();

            if(Property instanceof DataProperty) {
                DataProperties.add((DataProperty)Property);
                ((DataProperty)Property).FillDataTable();
            }
            
            if(Property instanceof AggregateProperty)
                AggrProperties.add((AggregateProperty)Property);

            if(Property instanceof DataProperty || (Property instanceof AggregateProperty && Persistents.contains(Property))) {
                Table Table = Property.GetTable(null);

                Integer PropNum = Tables.get(Table);
                if(PropNum==null) PropNum = 1;
                PropNum = PropNum + 1;
                Tables.put(Table, PropNum);

                PropertyField PropField = new PropertyField("prop"+PropNum.toString(),Property.getType());
                Table.Properties.add(PropField);
                Property.Field = PropField;
            }
        }

        //закинем индексы
        for (List<Property> index : Indexes) {

            Table table = index.get(0).GetTable(null);

            List<PropertyField> tableIndex = new ArrayList();
            for (Property property : index) {
                tableIndex.add(property.Field);
            }

            table.Indexes.add(tableIndex);
        }

        if (createTable) {

            for(Table Table : Tables.keySet()) Session.CreateTable(Table);

    /*        // построим в нужном порядке AggregateProperty и будем заполнять их
            List<Property> UpdateList = new ArrayList();
            for(AggregateProperty Property : AggrProperties) Property.fillChangedList(UpdateList,null,new HashSet());
            Integer ViewNum = 0;
            for(Property Property : UpdateList) {
    //            if(Property instanceof GroupProperty)
    //                ((GroupProperty)Property).FillDB(Session,ViewNum++);
            }
      */
            // создадим dumb
            Table DumbTable = new Table("dumb");
            DumbTable.Keys.add(new KeyField("dumb",Type.System));
            Session.CreateTable(DumbTable);
            Session.Execute("INSERT INTO dumb (dumb) VALUES (1)");

            Table EmptyTable = new Table("empty");
            EmptyTable.Keys.add(new KeyField("dumb",Type.System));
            Session.CreateTable(EmptyTable);
        }
    }
    
    boolean CheckPersistent(DataSession Session) throws SQLException {
        for(AggregateProperty Property : Persistents) {
            if(!Property.CheckAggregation(Session,Property.caption))
                return false;
//            Property.Out(Adapter);
        }
        
        return true;
    }


    // функционал по заполнению св-в по номерам, нужен для BL

    LDP AddDProp(String caption, Class Value, Class... Params) {
        return AddDProp(null, caption, Value, Params);
    }

    LDP AddDProp(AbstractGroup group, String caption, Class Value, Class... Params) {
        DataProperty Property = new DataProperty(TableFactory,Value);
        Property.caption = caption;
        LDP ListProperty = new LDP(Property);
        for(Class Int : Params) {
            ListProperty.AddInterface(Int);
        }
        AddDataProperty(Property);

        if (group != null)
            group.add(Property);

        return ListProperty;
    }
    
    void setDefProp(LDP Data,LP Default,boolean OnChange) {
        DataProperty Property = ((DataProperty)Data.Property);
        Property.DefaultProperty = Default.Property;
        for(int i=0;i<Data.ListInterfaces.size();i++)
            Property.DefaultMap.put((DataPropertyInterface)Data.ListInterfaces.get(i),Default.ListInterfaces.get(i));
        
        Property.OnDefaultChange = OnChange;
    }

    LCP AddCProp(String caption, Object Value, Class ValueClass, Class... Params) {
        ClassProperty Property = new ClassProperty(TableFactory,ValueClass,Value);
        Property.caption = caption;
        LCP ListProperty = new LCP(Property);
        for(Class Int : Params) {
            ListProperty.AddInterface(Int);
        }
        Properties.add(Property);
        return ListProperty;
    }

    LSFP AddSFProp(String Formula,Class Value,int ParamCount) {

        StringFormulaProperty Property = new StringFormulaProperty(TableFactory,Value,Formula);
        LSFP ListProperty = new LSFP(Property,ParamCount);
        Properties.add(Property);
        return ListProperty;
    }

    LSFP AddWSFProp(String Formula,int ParamCount) {

        WhereStringFormulaProperty Property = new WhereStringFormulaProperty(TableFactory,Formula);
        LSFP ListProperty = new LSFP(Property,ParamCount);
        Properties.add(Property);
        return ListProperty;
    }

    LMFP AddMFProp(Class Value,int ParamCount) {
        MultiplyFormulaProperty Property = new MultiplyFormulaProperty(TableFactory,Value);
        LMFP ListProperty = new LMFP(Property,ParamCount);
        Properties.add(Property);
        return ListProperty;
    }

    
    <T extends PropertyInterface> List<PropertyInterfaceImplement> ReadPropImpl(LP<T,Property<T>> MainProp,Object ...Params) {
        List<PropertyInterfaceImplement> Result = new ArrayList<PropertyInterfaceImplement>();
        int WaitInterfaces = 0, MainInt = 0;
        PropertyMapImplement MapRead = null;
        LP PropRead = null;
        for(Object P : Params) {
            if(P instanceof Integer) {
                // число может быть как ссылкой на родной интерфейс так и 
                PropertyInterface PropInt = MainProp.ListInterfaces.get((Integer)P-1);
                if(WaitInterfaces==0) {
                    // родную берем 
                    Result.add(PropInt);
                } else {
                    // докидываем в маппинг
                    MapRead.Mapping.put(PropRead.ListInterfaces.get(PropRead.ListInterfaces.size()-WaitInterfaces), PropInt);
                    WaitInterfaces--;
                }
            } else {
               // имплементация, типа LP
               PropRead = (LP)P;
               MapRead = new PropertyMapImplement(PropRead.Property);
               WaitInterfaces = PropRead.ListInterfaces.size();
               Result.add(MapRead);
            }
        }
        
        return Result;
    }

    LJP AddJProp(String caption, LP MainProp, int IntNum, Object... Params) {
        return AddJProp(null, caption, MainProp, IntNum, Params);
    }

    LJP AddJProp(AbstractGroup group, String caption, LP MainProp, int IntNum, Object... Params) {
        JoinProperty Property = new JoinProperty(TableFactory,MainProp.Property);
        Property.caption = caption;
        LJP ListProperty = new LJP(Property,IntNum);
        int MainInt = 0;
        List<PropertyInterfaceImplement> PropImpl = ReadPropImpl(ListProperty,Params);
        for(PropertyInterfaceImplement Implement : PropImpl) {
            Property.Implements.Mapping.put(MainProp.ListInterfaces.get(MainInt),Implement);
            MainInt++;
        }
        Properties.add(Property);

        if (group != null)
            group.add(Property);

        return ListProperty;
    }
    
    LGP AddGProp(String caption, LP GroupProp, boolean Sum, Object... Params) {
        return AddGProp(null, caption, GroupProp, Sum, Params);
    }
    LGP AddGProp(AbstractGroup group, String caption, LP GroupProp, boolean Sum, Object... Params) {

        GroupProperty Property;
        if(Sum)
            Property = new SumGroupProperty(TableFactory,GroupProp.Property);
        else
            Property = new MaxGroupProperty(TableFactory,GroupProp.Property);
        Property.caption = caption;

        LGP ListProperty = new LGP(Property,GroupProp);
        List<PropertyInterfaceImplement> PropImpl = ReadPropImpl(GroupProp,Params);
        for(PropertyInterfaceImplement Implement : PropImpl) ListProperty.AddInterface(Implement);

        Properties.add(Property);

        if (group != null)
            group.add(Property);
        
        return ListProperty;
    }

    LUP AddUProp(String caption, int UnionType, int IntNum, Object... Params) {
        return AddUProp(null, caption, UnionType, IntNum, Params);
    }
    
    LUP AddUProp(AbstractGroup group, String caption, int UnionType, int IntNum, Object... Params) {
        UnionProperty Property = null;
        switch(UnionType) {
            case 0:
                Property = new MaxUnionProperty(TableFactory);
                break;
            case 1:
                Property = new SumUnionProperty(TableFactory);
                break;
            case 2:
                Property = new OverrideUnionProperty(TableFactory);
                break;
        }
        Property.caption = caption;
        
        LUP ListProperty = new LUP(Property,IntNum);

        for(int i=0;i<Params.length/(IntNum+2);i++) {
            Integer Offs = i*(IntNum+2);
            LP OpImplement = (LP)Params[Offs+1];
            PropertyMapImplement Operand = new PropertyMapImplement(OpImplement.Property);
            for(int j=0;j<IntNum;j++)
                Operand.Mapping.put(OpImplement.ListInterfaces.get(((Integer)Params[Offs+2+j])-1),ListProperty.ListInterfaces.get(j));
            Property.Operands.add(Operand);
            Property.Coeffs.put(Operand,(Integer)Params[Offs]);
        }
        Properties.add(Property);

        if (group != null)
            group.add(Property);

        return ListProperty;
    }

    void fillData(DataAdapter Adapter) throws SQLException {
    }

    // генерирует белую БЛ
    void OpenTest(DataAdapter Adapter,boolean Classes,boolean Properties,boolean Implements,boolean Persistent,boolean Changes)  throws ClassNotFoundException, SQLException, InstantiationException, IllegalAccessException  {

        if(Classes) {
            InitClasses();

            if(Implements)
                InitImplements();
                
            if(Properties) {
                InitProperties();

                if(Persistent)
                    InitPersistents();

                if(Changes) {
                    DataSession Session = createSession(Adapter);
                    FillDB(Session, true);
                    Session.close();

                    fillData(Adapter);
                }
            }

        }
    }
    
    // случайным образом генерирует классы
    void RandomClasses(Random Randomizer) {
        int CustomClasses = Randomizer.nextInt(20);//
        List<Class> ObjClasses = new ArrayList();
        ObjClasses.add(objectClass);
        for(int i=0;i<CustomClasses;i++) {
            Class Class = new ObjectClass(i+10000, "Случайный класс"+i);
            int Parents = Randomizer.nextInt(2) + 1;
            for(int j=0;j<Parents;j++) {
                Class.addParent(ObjClasses.get(Randomizer.nextInt(ObjClasses.size())));
            }
            ObjClasses.add(Class);
        }
    }

    // случайным образом генерирует св-ва
    void RandomProperties(Random Randomizer) {
        
        List<Class> Classes = new ArrayList();
        objectClass.fillChilds(Classes);
        
        List<Property> RandProps = new ArrayList();
        List<Property> RandObjProps = new ArrayList();
        List<Property> RandIntegralProps = new ArrayList();

        StringFormulaProperty Dirihle = new WhereStringFormulaProperty(TableFactory,"prm1<prm2");
        Dirihle.Interfaces.add(new StringFormulaPropertyInterface(0));
        Dirihle.Interfaces.add(new StringFormulaPropertyInterface(1));
        RandProps.add(Dirihle);

        MultiplyFormulaProperty Multiply = new MultiplyFormulaProperty(TableFactory,Class.integer);
        Multiply.Interfaces.add(new FormulaPropertyInterface(0));
        Multiply.Interfaces.add(new FormulaPropertyInterface(1));
        RandProps.add(Multiply);

        int DataPropCount = Randomizer.nextInt(15)+1;
        for(int i=0;i<DataPropCount;i++) {
            // DataProperty
            DataProperty DataProp = new DataProperty(TableFactory,(i%4==0?Class.integer :Classes.get(Randomizer.nextInt(Classes.size()))));
            DataProp.caption = "Data Property " + i;
            // генерируем классы
            int IntCount = Randomizer.nextInt(TableFactory.MaxInterface)+1;
            for(int j=0;j<IntCount;j++)
                DataProp.Interfaces.add(new DataPropertyInterface(j,Classes.get(Randomizer.nextInt(Classes.size()))));

            RandProps.add(DataProp);
            RandObjProps.add(DataProp);
            if(DataProp.getBaseClass().contains(Class.integral))
                RandIntegralProps.add(DataProp);    
        }

        System.out.print("Создание аггрег. св-в ");
                
        int PropCount = Randomizer.nextInt(1000)+1; //
        for(int i=0;i<PropCount;i++) {
//            int RandClass = Randomizer.nextInt(10);
//            int PropClass = (RandClass>7?0:(RandClass==8?1:2));
            int PropClass = Randomizer.nextInt(6);
//            int PropClass = 5;
            Property GenProp = null;
            String ResType = "";
            if(PropClass==0) {
                // JoinProperty
                JoinProperty RelProp = new JoinProperty(TableFactory,RandProps.get(Randomizer.nextInt(RandProps.size())));
                
                // генерируем случайно кол-во интерфейсов
                List<PropertyInterface> RelPropInt = new ArrayList();
                int IntCount = Randomizer.nextInt(TableFactory.MaxInterface)+1;
                for(int j=0;j<IntCount;j++) {
                    JoinPropertyInterface Interface = new JoinPropertyInterface(j);
                    RelProp.Interfaces.add(Interface);
                    RelPropInt.add(Interface);
                }
                
                // чтобы 2 раза на одну и ту же ветку не натыкаться
                List<PropertyInterface> AvailRelInt = new ArrayList(RelPropInt);
                boolean Correct = true;
                
                for(PropertyInterface Interface : (Collection<PropertyInterface>)RelProp.Implements.Property.Interfaces) {
                    // генерируем случайно map'ы на эти интерфейсы
                    if(!(RelProp.Implements.Property instanceof FormulaProperty) && Randomizer.nextBoolean()) {
                        if(AvailRelInt.size()==0) {
                            Correct = false;
                            break;
                        }
                        PropertyInterface MapInterface = AvailRelInt.get(Randomizer.nextInt(AvailRelInt.size()));
                        RelProp.Implements.Mapping.put(Interface,MapInterface);
                        AvailRelInt.remove(MapInterface);
                    } else {
                        // другое property пока сгенерим на 1
                        PropertyMapImplement ImpProp = new PropertyMapImplement(RandObjProps.get(Randomizer.nextInt(RandObjProps.size())));
                        if(ImpProp.Property.Interfaces.size()>RelPropInt.size()) {
                            Correct = false;
                            break;
                        }
                        
                        List<PropertyInterface> MapRelInt = new ArrayList(RelPropInt);
                        for(PropertyInterface ImpInterface : (Collection<PropertyInterface>)ImpProp.Property.Interfaces) {
                            PropertyInterface MapInterface = MapRelInt.get(Randomizer.nextInt(MapRelInt.size()));
                            ImpProp.Mapping.put(ImpInterface,MapInterface);
                            MapRelInt.remove(MapInterface);
                        }
                        RelProp.Implements.Mapping.put(Interface,ImpProp);
                    }
                }

                if(Correct) {
                    GenProp = RelProp;
                    ResType = "R";
                }
            }
            
            if(PropClass==1 || PropClass==2) {
                // группировочное
                Property GroupProp;
                GroupProperty Property = null;
                if(PropClass==1) {
                    GroupProp = RandIntegralProps.get(Randomizer.nextInt(RandIntegralProps.size()));
                    Property = new SumGroupProperty(TableFactory,GroupProp);
                    ResType = "SG";
                } else {
                    GroupProp = RandObjProps.get(Randomizer.nextInt(RandObjProps.size()));
                    Property = new MaxGroupProperty(TableFactory,GroupProp);
                    ResType = "MG";
                }

                boolean Correct = true;                
                List<PropertyInterface> GroupInt = new ArrayList(GroupProp.Interfaces);
                int GroupCount = Randomizer.nextInt(TableFactory.MaxInterface)+1;
                for(int j=0;j<GroupCount;j++) {
                    PropertyInterfaceImplement Implement;
                    // генерируем случайно map'ы на эти интерфейсы
                    if(Randomizer.nextBoolean()) {
                        Implement = GroupInt.get(Randomizer.nextInt(GroupInt.size()));
                    } else {
                        // другое property пока сгенерим на 1
                        PropertyMapImplement ImpProp = new PropertyMapImplement(RandObjProps.get(Randomizer.nextInt(RandObjProps.size())));
                        if(ImpProp.Property.Interfaces.size()>GroupInt.size()) {
                            Correct = false;
                            break;
                        }

                        List<PropertyInterface> MapRelInt = new ArrayList(GroupInt);
                        for(PropertyInterface ImpInterface : (Collection<PropertyInterface>)ImpProp.Property.Interfaces) {
                            PropertyInterface MapInterface = MapRelInt.get(Randomizer.nextInt(MapRelInt.size()));
                            ImpProp.Mapping.put(ImpInterface,MapInterface);
                            MapRelInt.remove(MapInterface);
                        }
                        Implement = ImpProp;
                    }
                    
                    Property.Interfaces.add(new GroupPropertyInterface(j,Implement));
                }
                
                if(Correct)
                    GenProp = Property;
            }

            if(PropClass==3 || PropClass==4 || PropClass==5) {
                UnionProperty Property = null;
                List<Property> RandValProps = RandObjProps;
                if(PropClass==3) {
                    RandValProps = RandIntegralProps;
                    Property = new SumUnionProperty(TableFactory);
                    ResType = "SL";
                } else {
                if(PropClass==4) {
                    Property = new MaxUnionProperty(TableFactory);
                    ResType = "ML";
                } else {
                    Property = new OverrideUnionProperty(TableFactory);
                    ResType = "OL";
                }
                }

                int OpIntCount = Randomizer.nextInt(TableFactory.MaxInterface)+1;
                for(int j=0;j<OpIntCount;j++)
                    Property.Interfaces.add(new PropertyInterface(j));
        
                boolean Correct = true;
                List<PropertyInterface> OpInt = new ArrayList(Property.Interfaces);
                int OpCount = Randomizer.nextInt(4)+1;
                for(int j=0;j<OpCount;j++) {
                    PropertyMapImplement Operand = new PropertyMapImplement(RandValProps.get(Randomizer.nextInt(RandValProps.size())));
                    if(Operand.Property.Interfaces.size()!=OpInt.size()) {
                        Correct = false;
                        break;
                    }

                    List<PropertyInterface> MapRelInt = new ArrayList(OpInt);
                    for(PropertyInterface ImpInterface : (Collection<PropertyInterface>)Operand.Property.Interfaces) {
                        PropertyInterface MapInterface = MapRelInt.get(Randomizer.nextInt(MapRelInt.size()));
                        Operand.Mapping.put(ImpInterface,MapInterface);
                        MapRelInt.remove(MapInterface);
                    }
                    Property.Operands.add(Operand);
                }
                
                if(Correct)
                    GenProp = Property;
            }
                       

            if(GenProp!=null && !GenProp.getBaseClass().isEmpty()) {
                GenProp.caption = ResType + " " + i;
                // проверим что есть в интерфейсе и покрыты все ключи
                Iterator<InterfaceClass<?>> ic = GenProp.getClassSet(ClassSet.universal).iterator();
                if(ic.hasNext() && ic.next().keySet().size()==GenProp.Interfaces.size()) {
                    System.out.print(ResType+"-");
                    RandProps.add(GenProp);
                    RandObjProps.add(GenProp);
                    if(GenProp.getBaseClass().contains(Class.integral))
                        RandIntegralProps.add(GenProp);
                }
            }
        }
        
        Properties.addAll(RandProps);
        
        System.out.println();
    }
    
    // случайным образом генерирует имплементацию
    void RandomImplement(Random Randomizer) {
        List<Class> Classes = new ArrayList();
        objectClass.fillChilds(Classes);

        // заполнение физ модели
        int ImplementCount = Randomizer.nextInt(8);
        for(int i=0;i<ImplementCount;i++) {
            TableImplement Include = new TableImplement();
            int ObjCount = Randomizer.nextInt(3)+1;
            for(int ioc=0;ioc<ObjCount;ioc++)
                Include.add(new DataPropertyInterface(ioc,Classes.get(Randomizer.nextInt(Classes.size()))));
            TableFactory.IncludeIntoGraph(Include);               
        }        
    }
    
    // случайным образом генерирует постоянные аггрегации
    void RandomPersistent(Random Randomizer) {

        Persistents.clear();

        // сначала список получим
        List<AggregateProperty> AggrProperties = new ArrayList();
        for(Property Property : Properties) {
            if(Property instanceof AggregateProperty && Property.isObject())
                AggrProperties.add((AggregateProperty)Property);
        }
        
        int PersistentNum = Randomizer.nextInt(AggrProperties.size())+1;
        for(int i=0;i<PersistentNum;i++)
            Persistents.add(AggrProperties.get(Randomizer.nextInt(AggrProperties.size())));

//        for(AggregateProperty Property : AggrProperties)
//            if(Property.caption.equals("R 1"))
//            Persistents.add(Property);
     }

    static int ChangeDBIteration = 0;
    void ChangeDBTest(DataAdapter Adapter,Integer MaxIterations,Random Randomizer) throws SQLException {
        
        // сначала список получим
        List<DataProperty> DataProperties = new ArrayList();
        for(Property Property : Properties) {
            if(Property instanceof DataProperty)
                DataProperties.add((DataProperty)Property);
        }

        DataSession Session = createSession(Adapter);

        List<Class> AddClasses = new ArrayList();
        objectClass.fillChilds(AddClasses);
        for(Class AddClass : AddClasses) {
            if(AddClass instanceof ObjectClass) {
                int ObjectAdd = Randomizer.nextInt(10)+1;
                for(int ia=0;ia<ObjectAdd;ia++)
                    AddObject(Session, AddClass);
            }
        }

        Apply(Session);

        long PrevTime = System.currentTimeMillis();
        
//        Randomizer.setSeed(1);
        int Iterations = 1;
        while(Iterations<MaxIterations) {

            long CurrentTime = System.currentTimeMillis();
            if(CurrentTime-PrevTime>=40000)
                break;

            PrevTime = CurrentTime;

            ChangeDBIteration = Iterations;
            System.out.println("Iteration" + Iterations++);

            // будем также рандомно создавать объекты
            AddClasses = new ArrayList();
            objectClass.fillChilds(AddClasses);
            int ObjectAdd = Randomizer.nextInt(5);
            for(int ia=0;ia<ObjectAdd;ia++) {
                Class AddClass = AddClasses.get(Randomizer.nextInt(AddClasses.size()));
                if(AddClass instanceof ObjectClass)
                    AddObject(Session, AddClass);
            }

            int PropertiesChanged = Randomizer.nextInt(8)+1;
            for(int ip=0;ip<PropertiesChanged;ip++) {
                // берем случайные n св-в
                DataProperty<?> ChangeProp = DataProperties.get(Randomizer.nextInt(DataProperties.size()));
                int NumChanges = Randomizer.nextInt(3)+1;
                for(int in=0;in<NumChanges;in++) {                    
/*                    // теперь определяем класс найденного объекта
                    Class ValueClass = null;
                    if(ChangeProp.Value instanceof ObjectClass)
                        ValueClass = objectClass.FindClassID(ValueObject);
                    else
                        ValueClass = ChangeProp.Value;*/

                    // определяем входные классы
                    InterfaceClass<DataPropertyInterface> InterfaceClasses = CollectionExtend.getRandom(ChangeProp.getClassSet(ClassSet.universal),Randomizer);
                    // генерим рандомные объекты этих классов
                    Map<DataPropertyInterface,ObjectValue> Keys = new HashMap();
                    for(DataPropertyInterface Interface : ChangeProp.Interfaces) {
                        Class RandomClass = InterfaceClasses.get(Interface).getRandom(Randomizer);
                        Keys.put(Interface,new ObjectValue((Integer) RandomClass.GetRandomObject(Session,TableFactory,Randomizer,0),RandomClass));
                    }
                    
                    Object ValueObject = null;
                    if(Randomizer.nextInt(10)<8)
                        ValueObject = ChangeProp.Value.GetRandomObject(Session,TableFactory,Randomizer,Iterations);
                    
                    ChangeProp.changeProperty(Keys,ValueObject,Session);
                }
            }
            
/*            for(DataProperty Property : Session.Properties) {
                Property.OutChangesTable(Adapter, Session);
            }*/
                
            Apply(Session);
            CheckPersistent(Session);
        }

        Session.close();
    }

    static List<Property> getChangedList(Collection<? extends Property> UpdateProps,DataChanges Changes,Collection<Property> NoUpdateProps) {
        List<Property> ChangedList = new ArrayList();
        for(Property Property : UpdateProps)
            Property.fillChangedList(ChangedList,Changes,NoUpdateProps);
        return ChangedList;
    }

    // флаг для оптимизации
    Map<DataProperty,Integer> autoQuantity(Integer Quantity,LDP... Properties) {
        Map<DataProperty,Integer> Result = new HashMap<DataProperty,Integer>();
        for(LDP<?> Property : Properties)
            Result.put(Property.Property,Quantity);
        return Result;
    }

    static boolean AutoFillDB = false;
    static int AutoIDCounter = 0;
    void autoFillDB(DataAdapter Adapter, Map<Class, Integer> ClassQuantity, Map<DataProperty, Integer> PropQuantity, Map<DataProperty, Set<DataPropertyInterface>> PropNotNull) throws SQLException {

        AutoFillDB = true;
        DataSession Session = createSession(Adapter);

        // сначала вырубим все аггрегации в конце пересчитаем
        Map<AggregateProperty,PropertyField> SavePersistents = new HashMap();
        for(AggregateProperty Property : Persistents) {
            SavePersistents.put(Property,Property.Field);
            Property.Field = null;
        }
        Persistents.clear();

        // генерируем классы
        Map<Integer,String> ObjectNames = new HashMap();
        Map<Class,List<Integer>> Objects = new HashMap();
        List<Class> Classes = new ArrayList();
        objectClass.fillChilds(Classes);

        for(Class FillClass : Classes)
            Objects.put(FillClass,new ArrayList());

        for(Class FillClass : Classes)
            if(FillClass.Childs.size()==0) {
                System.out.println(FillClass.caption);

                Integer Quantity = ClassQuantity.get(FillClass);
                if(Quantity==null) Quantity = 1;

                List<Integer> ListObjects = new ArrayList();
                for(int i=0;i<Quantity;i++) {
                    Integer idObject = AddObject(Session,FillClass);
                    ListObjects.add(idObject);
                    ObjectNames.put(idObject,FillClass.caption+" "+(i+1));
                }

                Set<ObjectClass> Parents = new HashSet();
                FillClass.fillParents(Parents);

                for(ObjectClass Class : Parents)
                    Objects.get(Class).addAll(ListObjects);
            }

        Random Randomizer = new Random();

        // бежим по св-вам
        for(Property AbstractProperty : Properties)
            if(AbstractProperty instanceof DataProperty) {
                DataProperty<?> Property = (DataProperty)AbstractProperty;

                System.out.println(Property.caption);

                Set<DataPropertyInterface> InterfaceNotNull = PropNotNull.get(Property);
                if(InterfaceNotNull==null) InterfaceNotNull = new HashSet(); 
                Integer Quantity = PropQuantity.get(Property);
                if(Quantity==null) {
                    Quantity = 1;
                    for(DataPropertyInterface Interface : Property.Interfaces)
                        if(!InterfaceNotNull.contains(Interface))
                            Quantity = Quantity * Objects.get(Interface.Class).size();

                    if(Quantity > 1)
                        Quantity = (int)(Quantity * 0.5);
                }

                Map<DataPropertyInterface,Collection<Integer>> MapInterfaces = new HashMap();
                if(PropNotNull.containsKey(Property))
                    for(DataPropertyInterface Interface : InterfaceNotNull)
                        MapInterfaces.put(Interface,Objects.get(Interface.Class));

                // сначала для всех PropNotNull генерируем все возможные Map<ы>
                for(Map<DataPropertyInterface,Integer> NotNulls : MapBuilder.buildCombination(MapInterfaces)) {
                    Set<Map<DataPropertyInterface,Integer>> RandomInterfaces = new HashSet();
                    while(RandomInterfaces.size()<Quantity) {
                        Map<DataPropertyInterface,Integer> RandomIteration = new HashMap();
                        for(DataPropertyInterface Interface : Property.Interfaces)
                            if(!NotNulls.containsKey(Interface)) {
                                List<Integer> ListObjects = Objects.get(Interface.Class);
                                RandomIteration.put(Interface,ListObjects.get(Randomizer.nextInt(ListObjects.size())));
                            }
                        RandomInterfaces.add(RandomIteration);
                    }

                    for(Map<DataPropertyInterface,Integer> RandomIteration : RandomInterfaces) {
                        Map<DataPropertyInterface,ObjectValue> Keys = new HashMap();
                        RandomIteration.putAll(NotNulls);
                        for(Map.Entry<DataPropertyInterface,Integer> InterfaceValue : RandomIteration.entrySet())
                            Keys.put(InterfaceValue.getKey(),new ObjectValue(InterfaceValue.getValue(),InterfaceValue.getKey().Class));

                        Object ValueObject = null;
                        if(Property.Value instanceof StringClass) {
                            String ObjectName = "";
                            for(DataPropertyInterface Interface : Property.Interfaces)
                                ObjectName += ObjectNames.get(RandomIteration.get(Interface)) + " ";
                            ValueObject = ObjectName;
                        } else
                            ValueObject = Property.Value.getRandomObject(Objects,Randomizer,20);
                        Property.changeProperty(Keys,ValueObject,Session);
                    }
                }
            }

        System.out.println("Apply");
        Apply(Session);

        Session.startTransaction();

        // восстановим persistence, пересчитая их
        for(Property DependProperty : getChangedList(SavePersistents.keySet(),null,new HashSet<Property>()))
            if(DependProperty instanceof AggregateProperty && SavePersistents.containsKey(DependProperty)) {
                AggregateProperty Property = (AggregateProperty)DependProperty;

                System.out.println("Recalculate - "+Property.caption);
                
                Property.Field = SavePersistents.get(Property);
                Persistents.add(Property);
                Property.reCalculateAggregation(Session);
            }

        Session.commitTransaction();

        TableFactory.idTable.reserveID(Session,IDTable.OBJECT,AutoIDCounter);
        
        Session.close();

        AutoFillDB = false;
    }

    public void createDefaultClassForms(Class cls, NavigatorElement parent) {

        NavigatorElement node = new ClassNavigatorForm(this, cls);
        parent.addChild(node);

        // Проверим, что такой формы еще не было
        boolean found = false;
        for (NavigatorElement relNode : cls.relevantElements)
            if (relNode.ID == node.ID) { found = true; break; }
        if (!found)
            cls.addRelevantElement(node);

        for (Class child : cls.Childs) {
            createDefaultClassForms(child, node);
        }
    }

    void addPropertyView(NavigatorForm form, ObjectImplement... objects) {
        addPropertyView(form, (AbstractGroup)null, objects);
    }

    void addPropertyView(NavigatorForm form, AbstractGroup group, ObjectImplement... objects) {

        for (Property property : Properties) {

            if (group != null && !group.hasChild(property)) continue;

            if (property.Interfaces.size() == objects.length) {

                addPropertyView(form, property, objects);
            }
        }
    }

    <P extends PropertyInterface<P>> void addPropertyView(NavigatorForm form, Property<P> property, ObjectImplement... objects) {

        Collection<List<P>> permutations = MapBuilder.buildPermutations(property.Interfaces);

        for (List<P> mapping : permutations) {

            InterfaceClass<P> propertyInterface = new InterfaceClass();
            int interfaceCount = 0;
            for (P iface : mapping) {
                propertyInterface.put(iface, ClassSet.getUp(objects[interfaceCount++].BaseClass));
            }

            if (!property.getValueClass(propertyInterface).isEmpty()) {
                addPropertyView(form, new LP<P,Property<P>>(property, mapping), objects);
            }
        }
    }

    PropertyView addPropertyView(NavigatorForm form, LP property, ObjectImplement... objects) {

        PropertyObjectImplement propertyImplement = addPropertyObjectImplement(property, objects);
        return addPropertyView(form, propertyImplement);
    }

    PropertyView addPropertyView(NavigatorForm form, PropertyObjectImplement propertyImplement) {

        PropertyView propertyView = new PropertyView(form.IDShift(1),propertyImplement,propertyImplement.GetApplyObject());
        form.Properties.add(propertyView);
        return propertyView;
    }

    PropertyObjectImplement addPropertyObjectImplement(LP property, ObjectImplement... objects) {

        PropertyObjectImplement propertyImplement = new PropertyObjectImplement(property.Property);

        ListIterator<PropertyInterface> i = property.ListInterfaces.listIterator();
        for(ObjectImplement object : objects) {
            propertyImplement.Mapping.put(i.next(), object);
        }

        return propertyImplement;
    }


    PropertyView getPropertyView(NavigatorForm form, PropertyObjectImplement prop) {

        PropertyView resultPropView = null;
        for (PropertyView propView : (List<PropertyView>)form.Properties) {
            if (propView.View == prop)
                resultPropView = propView;
        }

        return resultPropView;
    }


    PropertyView getPropertyView(NavigatorForm form, Property prop) {

        PropertyView resultPropView = null;
        for (PropertyView propView : (List<PropertyView>)form.Properties) {
            if (propView.View.Property == prop)
                resultPropView = propView;
        }

        return resultPropView;
    }

    PropertyView getPropertyView(NavigatorForm form, Property prop, GroupObjectImplement groupObject) {

        PropertyView resultPropView = null;
        for (PropertyView propView : (List<PropertyView>)form.Properties) {
            if (propView.View.Property == prop && propView.ToDraw == groupObject)
                resultPropView = propView;
        }

        return resultPropView;
    }

    void addHintsNoUpdate(NavigatorForm form, AbstractGroup group) {

        for (Property property : Properties) {
            if (group != null && !group.hasChild(property)) continue;
            addHintsNoUpdate(form, property);
        }
    }

    void addHintsNoUpdate(NavigatorForm form, Property prop) {
        form.hintsNoUpdate.add(prop);
    }

    // -------------------------------------- Старые интерфейсы --------------------------------------------------- //

    Map<String,PropertyObjectImplement> FillSingleViews(ObjectImplement Object,NavigatorForm Form,Set<String> Names) {

        Map<String,PropertyObjectImplement> Result = new HashMap();

        for(Property DrawProp : Properties) {
            if(DrawProp.Interfaces.size() == 1) {
                // проверим что дает хоть одно значение
                InterfaceClass InterfaceClass = new InterfaceClass();
                InterfaceClass.put(((Collection<PropertyInterface>)DrawProp.Interfaces).iterator().next(),ClassSet.getUp(Object.BaseClass));
                if(!DrawProp.getValueClass(InterfaceClass).isEmpty()) {
                    PropertyObjectImplement PropertyImplement = new PropertyObjectImplement(DrawProp);
                    PropertyImplement.Mapping.put((PropertyInterface)DrawProp.Interfaces.iterator().next(),Object);
                    Form.Properties.add(new PropertyView(Form.IDShift(1),PropertyImplement,Object.GroupTo));

                    if(Names!=null && Names.contains(DrawProp.caption))
                        Result.put(DrawProp.caption,PropertyImplement);
                }
            }
        }

        return Result;
    }

    PropertyObjectImplement addPropertyView(NavigatorForm fbv,LP ListProp,GroupObjectImplement gv,ObjectImplement... Params) {
        PropertyObjectImplement PropImpl = new PropertyObjectImplement(ListProp.Property);

        ListIterator<PropertyInterface> i = ListProp.ListInterfaces.listIterator();
        for(ObjectImplement Object : Params) {
            PropImpl.Mapping.put(i.next(),Object);
        }
        fbv.Properties.add(new PropertyView(fbv.IDShift(1),PropImpl,gv));
        return PropImpl;
    }

}

class ClassNavigatorForm extends NavigatorForm {

    ClassNavigatorForm(BusinessLogics BL, Class cls) {
        super(cls.ID + 2134232, cls.caption);

        ObjectImplement object = new ObjectImplement(IDShift(1),cls);
        object.caption = cls.caption;

        GroupObjectImplement groupObject = new GroupObjectImplement(IDShift(1));

        groupObject.addObject(object);
        addGroup(groupObject);

        BL.FillSingleViews(object,this,null);

    }
}

class LP<T extends PropertyInterface,P extends Property<T>> {

    LP(P iProperty) {
        this(iProperty, new ArrayList<T>());
    }

    LP(P iProperty, List<T> iListInterfaces) {
        Property=iProperty;
        ListInterfaces = iListInterfaces;
    }

    P Property;
    List<T> ListInterfaces;
}

class LCP extends LP<DataPropertyInterface,ClassProperty> {

    LCP(ClassProperty iProperty) {super(iProperty);}

    void AddInterface(Class InClass) {
        DataPropertyInterface Interface = new DataPropertyInterface(ListInterfaces.size(),InClass);
        ListInterfaces.add(Interface);
        Property.Interfaces.add(Interface);
    }
}

class LDP<D extends PropertyInterface> extends LP<DataPropertyInterface,DataProperty<D>> {

    LDP(DataProperty<D> iProperty) {super(iProperty);}

    void AddInterface(Class InClass) {
        DataPropertyInterface Interface = new DataPropertyInterface(ListInterfaces.size(),InClass);
        ListInterfaces.add(Interface);
        Property.Interfaces.add(Interface);
    }

    void ChangeProperty(DataSession Session, Object Value, Integer... iParams) throws SQLException {
        Map<DataPropertyInterface,ObjectValue> Keys = new HashMap<DataPropertyInterface,ObjectValue>();
        Integer IntNum = 0;
        for(int i : iParams) {
            DataPropertyInterface Interface = ListInterfaces.get(IntNum);
            Keys.put(Interface,new ObjectValue(i,Interface.Class));
            IntNum++;
        }

        Property.changeProperty(Keys, Value, Session);
    }

    void putNotNulls(Map<DataProperty,Set<DataPropertyInterface>> PropNotNulls,Integer... iParams) {
        Set<DataPropertyInterface> InterfaceNotNulls = new HashSet<DataPropertyInterface>();
        for(Integer Interface : iParams)
            InterfaceNotNulls.add(ListInterfaces.get(Interface));

        PropNotNulls.put(Property,InterfaceNotNulls);
    }
}

class LSFP extends LP<StringFormulaPropertyInterface,StringFormulaProperty> {

    LSFP(StringFormulaProperty iProperty,int ParamCount) {
        super(iProperty);
        for(int i=0;i<ParamCount;i++) {
            StringFormulaPropertyInterface Interface = new StringFormulaPropertyInterface(ListInterfaces.size());
            ListInterfaces.add(Interface);
            Property.Interfaces.add(Interface);
        }
    }
}

class LMFP extends LP<FormulaPropertyInterface,MultiplyFormulaProperty> {

    LMFP(MultiplyFormulaProperty iProperty,int ParamCount) {
        super(iProperty);
        for(int i=0;i<ParamCount;i++) {
            FormulaPropertyInterface Interface = new FormulaPropertyInterface(ListInterfaces.size());
            ListInterfaces.add(Interface);
            Property.Interfaces.add(Interface);
        }
    }
}


class LJP<T extends PropertyInterface> extends LP<JoinPropertyInterface,JoinProperty<T>> {

    LJP(JoinProperty<T> iProperty,int Objects) {
        super(iProperty);
        for(int i=0;i<Objects;i++) {
            JoinPropertyInterface Interface = new JoinPropertyInterface(i);
            ListInterfaces.add(Interface);
            Property.Interfaces.add(Interface);
        }
    }
}

class LUP extends LP<PropertyInterface,UnionProperty> {

    LUP(UnionProperty iProperty,int Objects) {
        super(iProperty);
        for(int i=0;i<Objects;i++) {
            JoinPropertyInterface Interface = new JoinPropertyInterface(i);
            ListInterfaces.add(Interface);
            Property.Interfaces.add(Interface);
        }
    }
}

class LGP<T extends PropertyInterface> extends LP<GroupPropertyInterface<T>,GroupProperty<T>> {

    LP<T,?> GroupProperty;
    LGP(GroupProperty<T> iProperty,LP<T,?> iGroupProperty) {
        super(iProperty);
        GroupProperty = iGroupProperty;
    }

    void AddInterface(PropertyInterfaceImplement<T> Implement) {
        GroupPropertyInterface<T> Interface = new GroupPropertyInterface<T>(ListInterfaces.size(),Implement);
        ListInterfaces.add(Interface);
        Property.Interfaces.add(Interface);
    }
}

/*
    List<PropertyView> GetPropViews(RemoteForm fbv, Property prop) {

        List<PropertyView> result = new ArrayList();

        for (PropertyView propview : fbv.Properties)
            if (propview.View.Property == prop) result.add(propview);

        return result;
    }

    // "СЂРёСЃСѓРµС‚" РєР»Р°СЃСЃ, СЃРѕ РІСЃРµРјРё СЃРІ-РІР°РјРё
    void DisplayClasses(DataAdapter Adapter, DataPropertyInterface[] ToDraw) throws SQLException {

        Map<DataPropertyInterface,SourceExpr> JoinSources = new HashMap<DataPropertyInterface,SourceExpr>();
        SelectQuery SimpleSelect = new SelectQuery(null);
        FromTable PrevSelect = null;
        for(int ic=0;ic<ToDraw.length;ic++) {
            FromTable Select = TableFactory.ObjectTable.ClassSelect(ToDraw[ic].Class);
            Select.JoinType = "FULL";
            if(PrevSelect==null)
                SimpleSelect.From = Select;
            else
                PrevSelect.Joins.add(Select);

            PrevSelect = Select;
            JoinSources.put(ToDraw[ic],new FieldSourceExpr(Select,TableFactory.ObjectTable.Key.Name));
        }

        JoinList Joins=new JoinList();

        Integer SelFields = 0;
        Iterator<Property> i = Properties.iterator();
        while(i.hasNext()) {
            Property Prop = i.next();

            MapBuilder<PropertyInterface,DataPropertyInterface> mb= new MapBuilder<PropertyInterface,DataPropertyInterface>();
            List<Map<PropertyInterface,DataPropertyInterface>> Maps = mb.BuildMap((PropertyInterface[])Prop.Interfaces.toArray(new PropertyInterface[0]), ToDraw);
            // РїРѕРїСЂРѕР±СѓРµРј РІСЃРµ РІР°СЂРёР°РЅС‚С‹ РѕС‚РѕР±СЂР°Р¶РµРЅРёСЏ
            Iterator<Map<PropertyInterface,DataPropertyInterface>> im = Maps.iterator();
            while(im.hasNext()) {
                Map<PropertyInterface,DataPropertyInterface> Impl = im.next();
                Map<PropertyInterface,SourceExpr> JoinImplement = new HashMap();

                ClassInterface ClassImplement = new ClassInterface();
                Iterator<PropertyInterface> ip = Prop.Interfaces.iterator();
                while(ip.hasNext()) {
                    PropertyInterface Interface = ip.next();
                    DataPropertyInterface MapInterface = Impl.get(Interface);
                    ClassImplement.put(Interface,MapInterface.Class);
                    JoinImplement.put(Interface,JoinSources.get(MapInterface));
                }

                if(Prop.GetValueClass(ClassImplement)!=null) {
                    // С‚Рѕ РµСЃС‚СЊ Р°РєС‚СѓР°Р»СЊРЅРѕРµ СЃРІ-РІРѕ
                    SimpleSelect.Expressions.put("test"+(SelFields++).toString(),Prop.JoinSelect(Joins,JoinImplement,false));
                }
            }
        }

        Iterator<From> ij = Joins.iterator();
        while(ij.hasNext()) {
            From Join = ij.next();
            Join.JoinType = "LEFT";
            PrevSelect.Joins.add(Join);
        }

        Adapter.OutSelect(SimpleSelect);
    }
*/
