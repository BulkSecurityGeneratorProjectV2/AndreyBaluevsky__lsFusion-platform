package fdk.integration;

import java.util.List;

public class ImportData {
    private List<Item> itemsList;
    private List<ItemGroup> itemGroupsList;
    private List<ItemGroup> parentGroupsList;
    private List<Bank> banksList;
    private List<LegalEntity> legalEntitiesList;
    private List<Warehouse> warehousesList;
    private List<LegalEntity> storesList;
    private List<DepartmentStore> departmentStoresList;
    private List<RateWaste> rateWastesList;
    private List<Ware> waresList;
    private List<Price> pricesList;
    private List<Assortment> assortmentsList;
    private List<StockSupplier> stockSuppliersList;
    private List<UserInvoiceDetail> userInvoicesList;
    private Integer numberOfItemsAtATime;
    private Boolean importInactive;

    public ImportData() {
    }

    public List<Item> getItemsList() {
        return itemsList;
    }

    public void setItemsList(List<Item> itemsList) {
        this.itemsList = itemsList;
    }

    public List<ItemGroup> getItemGroupsList() {
        return itemGroupsList;
    }

    public void setItemGroupsList(List<ItemGroup> itemGroupsList) {
        this.itemGroupsList = itemGroupsList;
    }

    public List<ItemGroup> getParentGroupsList() {
        return parentGroupsList;
    }

    public void setParentGroupsList(List<ItemGroup> parentGroupsList) {
        this.parentGroupsList = parentGroupsList;
    }

    public List<Bank> getBanksList() {
        return banksList;
    }

    public void setBanksList(List<Bank> banksList) {
        this.banksList = banksList;
    }

    public List<LegalEntity> getLegalEntitiesList() {
        return legalEntitiesList;
    }

    public void setLegalEntitiesList(List<LegalEntity> legalEntitiesList) {
        this.legalEntitiesList = legalEntitiesList;
    }

    public List<Warehouse> getWarehousesList() {
        return warehousesList;
    }

    public void setWarehousesList(List<Warehouse> warehousesList) {
        this.warehousesList = warehousesList;
    }

    public List<LegalEntity> getStoresList() {
        return storesList;
    }

    public void setStoresList(List<LegalEntity> storesList) {
        this.storesList = storesList;
    }

    public List<DepartmentStore> getDepartmentStoresList() {
        return departmentStoresList;
    }

    public void setDepartmentStoresList(List<DepartmentStore> departmentStoresList) {
        this.departmentStoresList = departmentStoresList;
    }

    public List<RateWaste> getRateWastesList() {
        return rateWastesList;
    }

    public void setRateWastesList(List<RateWaste> rateWastesList) {
        this.rateWastesList = rateWastesList;
    }

    public List<Ware> getWaresList() {
        return waresList;
    }

    public void setWaresList(List<Ware> waresList) {
        this.waresList = waresList;
    }

    public List<Price> getPricesList() {
        return pricesList;
    }

    public void setPricesList(List<Price> pricesList) {
        this.pricesList = pricesList;
    }

    public List<Assortment> getAssortmentsList() {
        return assortmentsList;
    }

    public void setAssortmentsList(List<Assortment> assortmentsList) {
        this.assortmentsList = assortmentsList;
    }

    public List<StockSupplier> getStockSuppliersList() {
        return stockSuppliersList;
    }

    public void setStockSuppliersList(List<StockSupplier> stockSuppliersList) {
        this.stockSuppliersList = stockSuppliersList;
    }

    public List<UserInvoiceDetail> getUserInvoicesList() {
        return userInvoicesList;
    }

    public void setUserInvoicesList(List<UserInvoiceDetail> userInvoicesList) {
        this.userInvoicesList = userInvoicesList;
    }

    public Integer getNumberOfItemsAtATime() {
        return numberOfItemsAtATime;
    }

    public void setNumberOfItemsAtATime(Integer numberOfItemsAtATime) {
        this.numberOfItemsAtATime = numberOfItemsAtATime;
    }

    public Boolean getImportInactive() {
        return importInactive;
    }

    public void setImportInactive(Boolean importInactive) {
        this.importInactive = importInactive;
    }
}
