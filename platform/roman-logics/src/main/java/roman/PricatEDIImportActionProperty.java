package roman;

import platform.interop.action.ClientAction;
import platform.server.classes.DataClass;
import platform.server.classes.FileActionClass;
import platform.server.classes.ValueClass;
import platform.server.form.instance.PropertyObjectInterfaceInstance;
import platform.server.form.instance.remote.RemoteForm;
import platform.server.integration.*;
import platform.server.logics.DataObject;
import platform.server.logics.ObjectValue;
import platform.server.logics.property.ActionProperty;
import platform.server.logics.property.ClassPropertyInterface;
import platform.server.session.Changes;
import platform.server.session.DataSession;
import platform.server.session.Modifier;

import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.util.*;

public class PricatEDIImportActionProperty extends ActionProperty {
    private RomanLogicsModule LM;

    private final FileActionClass valueClass = FileActionClass.getDefinedInstance(true, "Файлы данных (*.edi, *.txt)", "edi txt *.*");

    public PricatEDIImportActionProperty(String sID, RomanLogicsModule LM, ValueClass supplier) {
        super(sID, "Импортировать прайс (EDI)", new ValueClass[]{supplier});
        this.LM = LM;
    }

    @Override
    public void execute(Map<ClassPropertyInterface, DataObject> keys, ObjectValue value, DataSession session, Modifier<? extends Changes> modifier, List<ClientAction> actions, RemoteForm executeForm, Map<ClassPropertyInterface, PropertyObjectInterfaceInstance> mapObjects, boolean groupLast) throws SQLException {
        Iterator<ClassPropertyInterface> i = interfaces.iterator();
        ClassPropertyInterface supplierInterface = i.next();
        DataObject supplier = keys.get(supplierInterface);

        List<byte[]> fileList = valueClass.getFiles(value.getValue());

        ImportField barcodeField = new ImportField(LM.barcodePricat);
        ImportField articleField = new ImportField(LM.articleNumberPricat);
        ImportField customCategoryOriginalField = new ImportField(LM.customCategoryOriginalPricat);
        ImportField colorCodeField = new ImportField(LM.colorCodePricat);
        ImportField colorField = new ImportField(LM.colorNamePricat);
        ImportField sizeField = new ImportField(LM.sizePricat);
        ImportField originalNameField = new ImportField(LM.originalNamePricat);
        ImportField countryField = new ImportField(LM.countryPricat);
        ImportField netWeightField = new ImportField(LM.netWeightPricat);
        ImportField compositionField = new ImportField(LM.compositionPricat);
        ImportField priceField = new ImportField(LM.pricePricat);
        ImportField rrpField = new ImportField(LM.rrpPricat);
        ImportField seasonField = new ImportField(LM.seasonPricat);
        ImportField genderField = new ImportField(LM.genderPricat);
        ImportField themeCodeField = new ImportField(LM.themeCodePricat);
        ImportField themeNameField = new ImportField(LM.themeNamePricat);

        List<ImportProperty<?>> properties = new ArrayList<ImportProperty<?>>();
        ImportKey<?> pricatKey = new ImportKey(LM.pricat, LM.barcodeToPricat.getMapping(barcodeField));
        properties.add(new ImportProperty(barcodeField, LM.barcodePricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(articleField, LM.articleNumberPricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(customCategoryOriginalField, LM.customCategoryOriginalPricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(colorCodeField, LM.colorCodePricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(colorField, LM.colorNamePricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(sizeField, LM.sizePricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(originalNameField, LM.originalNamePricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(countryField, LM.countryPricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(netWeightField, LM.netWeightPricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(compositionField, LM.compositionPricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(priceField, LM.pricePricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(rrpField, LM.rrpPricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(seasonField, LM.seasonPricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(genderField, LM.genderPricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(themeCodeField, LM.themeCodePricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(themeNameField, LM.themeNamePricat.getMapping(pricatKey)));
        properties.add(new ImportProperty(supplier, LM.supplierPricat.getMapping(pricatKey)));



        for (byte[] file : fileList) {

            ImportKey<?>[] keysArray = {pricatKey};

            try {
                PricatEDIInputTable inputTable = new PricatEDIInputTable(new ByteArrayInputStream(file), supplier);

                ImportTable table = new EDIInvoiceImporter(inputTable, barcodeField, articleField, customCategoryOriginalField, colorCodeField, colorField,
                        sizeField, originalNameField, countryField, netWeightField, compositionField, priceField, rrpField, seasonField, genderField, themeCodeField, themeNameField).getTable();

                new IntegrationService(session, table, Arrays.asList(keysArray), properties).synchronize(true, true, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public DataClass getValueClass() {
        return valueClass;
    }
}
