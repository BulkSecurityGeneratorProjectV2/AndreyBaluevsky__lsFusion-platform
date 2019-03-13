package lsfusion.server.form.entity.drilldown;

import lsfusion.base.col.interfaces.immutable.ImRevMap;
import lsfusion.server.form.entity.PropertyDrawEntity;
import lsfusion.server.form.view.ContainerView;
import lsfusion.server.form.view.DefaultFormView;
import lsfusion.server.form.view.FormView;
import lsfusion.server.logics.LogicsModule;
import lsfusion.server.physics.dev.i18n.LocalizedString;
import lsfusion.server.logics.mutables.Version;
import lsfusion.server.logics.property.CalcPropertyMapImplement;
import lsfusion.server.logics.property.ClassPropertyInterface;
import lsfusion.server.logics.property.DataProperty;
import lsfusion.server.logics.property.PropertyInterface;

public class DataDrillDownFormEntity extends DrillDownFormEntity<ClassPropertyInterface, DataProperty> {

    private PropertyDrawEntity implPropertyDraw;
    private PropertyDrawEntity wherePropertyDraw;
    private PropertyDrawEntity writeFromPropertyDraw;

    public DataDrillDownFormEntity(String canonicalName, LocalizedString caption, DataProperty property, LogicsModule LM) {
        super(canonicalName, caption, property, LM);
    }

    @Override
    protected void setupDrillDownForm() {
        Version version = LM.getVersion();

        implPropertyDraw = addPropertyDraw(property, interfaceObjects, version);

        CalcPropertyMapImplement<PropertyInterface, ClassPropertyInterface> where = (CalcPropertyMapImplement<PropertyInterface, ClassPropertyInterface>) property.event.where; //h
        ImRevMap<PropertyInterface, ClassPropertyInterface> whereMapping = where.mapping;
        wherePropertyDraw = addPropertyDraw(where.property, whereMapping.join(interfaceObjects), version);

        CalcPropertyMapImplement<PropertyInterface, ClassPropertyInterface> writeFrom = (CalcPropertyMapImplement<PropertyInterface, ClassPropertyInterface>) property.event.writeFrom; //g
        ImRevMap<PropertyInterface, ClassPropertyInterface> writeFromMapping = writeFrom.mapping;
        writeFromPropertyDraw = addPropertyDraw(writeFrom.property, writeFromMapping.join(interfaceObjects), version);
    }

    @Override
    public FormView createDefaultRichDesign(Version version) {
        DefaultFormView design = (DefaultFormView) super.createDefaultRichDesign(version);
        valueContainer.add(design.get(implPropertyDraw), version);

        ContainerView whereParamsContainer = design.createContainer(LocalizedString.create("{logics.property.drilldown.form.where.params}"), version);
        whereParamsContainer.add(design.get(wherePropertyDraw), version);
        ContainerView expressionParamsContainer = design.createContainer(LocalizedString.create("{logics.property.drilldown.form.expr.params}"), version);
        expressionParamsContainer.add(design.get(writeFromPropertyDraw), version);

        design.mainContainer.addAfter(whereParamsContainer, valueContainer, version);
        design.mainContainer.addAfter(expressionParamsContainer, whereParamsContainer, version);

        return design;
    }
}
