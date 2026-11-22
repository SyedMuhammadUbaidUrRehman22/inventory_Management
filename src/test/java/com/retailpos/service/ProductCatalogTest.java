package com.retailpos.service;

import com.retailpos.model.Cashier;
import com.retailpos.model.Category;
import com.retailpos.model.Manager;
import com.retailpos.model.Product;
import com.retailpos.model.ProductSupplier;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.Supplier;
import com.retailpos.testsupport.FakeCategoryDao;
import com.retailpos.testsupport.FakeProductDao;
import com.retailpos.testsupport.FakeProductSupplierDao;
import com.retailpos.testsupport.FakeSupplierDao;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductCatalogTest {

    private final Manager manager = new Manager(1, "manager", "x");
    private final Cashier cashier = new Cashier(2, "cashier", "x");
    private final Storekeeper storekeeper = new Storekeeper(3, "storekeeper", "x");

    private FakeProductDao productDao;
    private ProductCatalog catalog;
    private Category groceries;
    private Category beverages;

    @BeforeEach
    void setUp() {
        productDao = new FakeProductDao();
        catalog = new ProductCatalog(new AccessControl(new FakeUserDao()), productDao,
                new FakeCategoryDao(), new FakeSupplierDao(), new FakeProductSupplierDao());
        groceries = catalog.addCategory(manager, "Groceries");
        beverages = catalog.addCategory(manager, "Beverages");
    }

    private Product product(String code, String name, String price) {
        return new Product(code, name, "pcs", new BigDecimal(price), 10, null, 0, groceries);
    }

    private BusinessException rejected(Runnable action) {
        return assertThrows(BusinessException.class, action::run);
    }

    // ---- FR1: register / update products ----

    @Test
    void registerProductSavesWithDefaults() {
        Product saved = catalog.registerProduct(manager, product(" P-1001 ", " Basmati Rice 5kg ", "1450"));

        assertEquals("P-1001", saved.getProductCode());
        assertEquals("Basmati Rice 5kg", saved.getName());
        assertEquals(ProductCatalog.STATUS_ACTIVE, saved.getStatus());
        assertEquals(new BigDecimal("1450.00"), saved.getSellingPrice());
        assertEquals(1, catalog.searchProducts(manager, "P-1001", null, null).size());
    }

    @Test
    void newProductAlwaysStartsWithZeroStock() {
        Product p = product("P-1", "Tea", "100");
        p.setCurrentStockQuantity(50);
        assertEquals(0, catalog.registerProduct(manager, p).getCurrentStockQuantity());
    }

    @Test
    void duplicateProductCodeIsRejected() {
        catalog.registerProduct(manager, product("P-1", "Tea", "100"));
        BusinessException e = rejected(() -> catalog.registerProduct(manager, product("P-1", "Other", "50")));
        assertEquals("Product code P-1 already exists.", e.getMessage());
    }

    @Test
    void invalidProductFieldsAreRejected() {
        rejected(() -> catalog.registerProduct(manager, product("", "Tea", "100")));
        rejected(() -> catalog.registerProduct(manager, product("P-1", " ", "100")));
        rejected(() -> catalog.registerProduct(manager, product("P-1", "Tea", "0")));
        rejected(() -> catalog.registerProduct(manager, product("P-1", "Tea", "-5")));
        rejected(() -> catalog.registerProduct(manager, product("P-1", "Tea", "1.005")));
        rejected(() -> catalog.registerProduct(manager, product("P-1", "Tea", "10000000000")));
        rejected(() -> catalog.registerProduct(manager, product("P-1".repeat(11), "Tea", "100")));

        Product noUnit = product("P-1", "Tea", "100");
        noUnit.setUnit(null);
        rejected(() -> catalog.registerProduct(manager, noUnit));

        Product negativeReorder = product("P-1", "Tea", "100");
        negativeReorder.setReorderLevel(-1);
        rejected(() -> catalog.registerProduct(manager, negativeReorder));

        Product badStatus = product("P-1", "Tea", "100");
        badStatus.setStatus("SOLD_OUT");
        rejected(() -> catalog.registerProduct(manager, badStatus));

        Product noCategory = product("P-1", "Tea", "100");
        noCategory.setCategory(null);
        assertEquals("Select a category.", rejected(() -> catalog.registerProduct(manager, noCategory)).getMessage());

        Product unknownCategory = product("P-1", "Tea", "100");
        unknownCategory.setCategory(new Category(99, "Ghost"));
        rejected(() -> catalog.registerProduct(manager, unknownCategory));

        assertTrue(catalog.searchProducts(manager, null, null, null).isEmpty());
    }

    @Test
    void updateProductChangesDetailsButNeverStock() {
        catalog.registerProduct(manager, product("P-1", "Tea", "100"));
        productDao.setStock("P-1", 25);

        Product change = product("P-1", "Green Tea", "120");
        change.setCategory(beverages);
        change.setStatus(ProductCatalog.STATUS_DISCONTINUED);
        change.setCurrentStockQuantity(999);
        catalog.updateProduct(manager, change);

        Product stored = catalog.searchProducts(manager, "P-1", null, null).get(0);
        assertEquals("Green Tea", stored.getName());
        assertEquals(new BigDecimal("120.00"), stored.getSellingPrice());
        assertEquals("Beverages", stored.getCategory().getName());
        assertEquals(ProductCatalog.STATUS_DISCONTINUED, stored.getStatus());
        assertEquals(25, stored.getCurrentStockQuantity());
    }

    @Test
    void updatingUnknownProductIsRejected() {
        rejected(() -> catalog.updateProduct(manager, product("P-404", "Tea", "100")));
    }

    @Test
    void onlyManagerCanRegisterOrUpdateProducts() {
        rejected(() -> catalog.registerProduct(cashier, product("P-1", "Tea", "100")));
        rejected(() -> catalog.registerProduct(storekeeper, product("P-1", "Tea", "100")));
        catalog.registerProduct(manager, product("P-1", "Tea", "100"));
        rejected(() -> catalog.updateProduct(cashier, product("P-1", "Tea", "90")));
    }

    // ---- FR2: categories ----

    @Test
    void categoriesAreListedAndUnique() {
        assertEquals(List.of("Beverages", "Groceries"),
                catalog.listCategories(cashier).stream().map(Category::getName).toList());
        rejected(() -> catalog.addCategory(manager, "groceries"));
        rejected(() -> catalog.addCategory(manager, "  "));
        rejected(() -> catalog.addCategory(cashier, "Dairy"));
    }

    // ---- FR6: search ----

    @Test
    void everyRoleCanSearchByCodeNameOrCategory() {
        catalog.registerProduct(manager, product("P-1", "Basmati Rice", "100"));
        Product tea = product("P-2", "Tea", "50");
        tea.setCategory(beverages);
        catalog.registerProduct(manager, tea);

        assertEquals(1, catalog.searchProducts(cashier, "p-2", null, null).size());
        assertEquals(1, catalog.searchProducts(storekeeper, "rice", null, null).size());
        assertEquals(1, catalog.searchProducts(manager, "beverages", null, null).size());
        assertEquals(1, catalog.searchProducts(manager, null, groceries.getCategoryID(), null).size());
        assertEquals(2, catalog.searchProducts(manager, "", null, ProductCatalog.STATUS_ACTIVE).size());
        rejected(() -> catalog.searchProducts(manager, null, null, "UNKNOWN"));
    }

    // ---- FR3: suppliers ----

    @Test
    void registerSupplierTrimsAndAllowsOptionalFields() {
        Supplier saved = catalog.registerSupplier(manager, new Supplier(0, " Metro Wholesale ", " ", null));
        assertEquals("Metro Wholesale", saved.getName());
        assertNull(saved.getAddress());
        assertEquals(1, catalog.listSuppliers(manager, "metro").size());
    }

    @Test
    void supplierNameIsRequiredAndOnlyManagerManagesSuppliers() {
        rejected(() -> catalog.registerSupplier(manager, new Supplier(0, "", "Lahore", "0300")));
        rejected(() -> catalog.registerSupplier(cashier, new Supplier(0, "Metro", null, null)));
        rejected(() -> catalog.listSuppliers(storekeeper, null));
    }

    @Test
    void updateSupplierChangesDetails() {
        Supplier s = catalog.registerSupplier(manager, new Supplier(0, "Metro", null, null));
        catalog.updateSupplier(manager, new Supplier(s.getSupplierID(), "Metro Wholesale", "Lahore", "0300-1234567"));
        Supplier stored = catalog.listSuppliers(manager, null).get(0);
        assertEquals("Metro Wholesale", stored.getName());
        assertEquals("0300-1234567", stored.getContactInfo());
        rejected(() -> catalog.updateSupplier(manager, new Supplier(99, "Ghost", null, null)));
    }

    // ---- FR4, FR5: supplier prices ----

    @Test
    void supplierPriceLinksProductAndUpdatesOnSecondCall() {
        catalog.registerProduct(manager, product("P-1", "Rice", "1450"));
        Supplier metro = catalog.registerSupplier(manager, new Supplier(0, "Metro", null, null));

        catalog.setSupplierPrice(manager, "P-1", metro.getSupplierID(), new BigDecimal("1280"));
        catalog.setSupplierPrice(manager, "P-1", metro.getSupplierID(), new BigDecimal("1300.50"));

        List<ProductSupplier> links = catalog.getProductsForSupplier(manager, metro.getSupplierID());
        assertEquals(1, links.size());
        assertEquals(new BigDecimal("1300.50"), links.get(0).getPurchasePrice());
    }

    @Test
    void oneProductCanHaveDifferentPricesPerSupplier() {
        catalog.registerProduct(manager, product("P-1", "Rice", "1450"));
        Supplier a = catalog.registerSupplier(manager, new Supplier(0, "A", null, null));
        Supplier b = catalog.registerSupplier(manager, new Supplier(0, "B", null, null));
        catalog.setSupplierPrice(manager, "P-1", a.getSupplierID(), new BigDecimal("1280"));
        catalog.setSupplierPrice(manager, "P-1", b.getSupplierID(), new BigDecimal("1250"));

        assertEquals(new BigDecimal("1280.00"),
                catalog.getProductsForSupplier(manager, a.getSupplierID()).get(0).getPurchasePrice());
        assertEquals(new BigDecimal("1250.00"),
                catalog.getProductsForSupplier(manager, b.getSupplierID()).get(0).getPurchasePrice());
    }

    @Test
    void invalidSupplierPriceIsRejected() {
        catalog.registerProduct(manager, product("P-1", "Rice", "1450"));
        Supplier metro = catalog.registerSupplier(manager, new Supplier(0, "Metro", null, null));
        int id = metro.getSupplierID();

        rejected(() -> catalog.setSupplierPrice(manager, "P-404", id, new BigDecimal("10")));
        rejected(() -> catalog.setSupplierPrice(manager, "P-1", 99, new BigDecimal("10")));
        rejected(() -> catalog.setSupplierPrice(manager, "P-1", id, BigDecimal.ZERO));
        rejected(() -> catalog.setSupplierPrice(manager, "P-1", id, null));
        rejected(() -> catalog.setSupplierPrice(cashier, "P-1", id, new BigDecimal("10")));
    }

    @Test
    void removeSupplierPriceUnlinksProduct() {
        catalog.registerProduct(manager, product("P-1", "Rice", "1450"));
        Supplier metro = catalog.registerSupplier(manager, new Supplier(0, "Metro", null, null));
        catalog.setSupplierPrice(manager, "P-1", metro.getSupplierID(), new BigDecimal("1280"));

        catalog.removeSupplierPrice(manager, "P-1", metro.getSupplierID());

        assertTrue(catalog.getProductsForSupplier(manager, metro.getSupplierID()).isEmpty());
        rejected(() -> catalog.removeSupplierPrice(manager, "P-1", metro.getSupplierID()));
    }
}
