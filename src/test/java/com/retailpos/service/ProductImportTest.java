package com.retailpos.service;

import com.retailpos.model.Cashier;
import com.retailpos.model.Category;
import com.retailpos.model.Manager;
import com.retailpos.model.Product;
import com.retailpos.model.Storekeeper;
import com.retailpos.testsupport.FakeCategoryDao;
import com.retailpos.testsupport.FakeProductDao;
import com.retailpos.testsupport.FakeProductSupplierDao;
import com.retailpos.testsupport.FakeSupplierDao;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import com.retailpos.util.SpreadsheetUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Adding products from an Excel file (Manager and Storekeeper). */
class ProductImportTest {

    private static final Object[] HEADERS =
            {"Product code", "Product name", "Category", "Unit", "Selling price", "Reorder level", "Status"};

    private final Manager manager = new Manager(1, "manager", "x");
    private final Cashier cashier = new Cashier(2, "cashier", "x");
    private final Storekeeper storekeeper = new Storekeeper(3, "storekeeper", "x");
    private FakeProductDao productDao;
    private ProductCatalog catalog;
    private Category groceries;

    @BeforeEach
    void setUp() {
        productDao = new FakeProductDao();
        catalog = new ProductCatalog(new AccessControl(new FakeUserDao()), productDao,
                new FakeCategoryDao(), new FakeSupplierDao(), new FakeProductSupplierDao());
        groceries = catalog.addCategory(manager, "Groceries");
        catalog.addCategory(manager, "Beverages");
    }

    // ---- reading and checking a file ----

    @Test
    void validFileIsPreviewedButNothingIsSavedUntilImport() {
        ProductCatalog.ImportPreview preview = catalog.previewImport(storekeeper, xlsx(
                HEADERS,
                row("P-1", "Basmati Rice 5kg", "Groceries", "bag", 1450, 10, "Active"),
                row(1001, "Tea 500g", "beverages", "pack", "Rs 1,250.50", null, null),
                row("P-3", "Old Notebook", "Groceries", "pcs", 120.5, 0, "discontinued")));

        assertTrue(preview.canImport(), () -> "errors: " + preview.errors());
        assertEquals(3, preview.products().size());
        Product tea = preview.products().get(1);
        assertEquals("1001", tea.getProductCode());
        assertEquals("Beverages", tea.getCategory().getName());
        assertEquals(new BigDecimal("1250.50"), tea.getSellingPrice());
        assertEquals(0, tea.getReorderLevel());
        assertEquals(ProductCatalog.STATUS_ACTIVE, tea.getStatus());
        assertEquals(ProductCatalog.STATUS_DISCONTINUED, preview.products().get(2).getStatus());
        assertEquals(new BigDecimal("120.50"), preview.products().get(2).getSellingPrice());
        assertTrue(productDao.search(null, null, null).isEmpty(), "preview must not save anything");

        assertEquals(3, catalog.importProducts(storekeeper, preview.products()));
        List<Product> saved = productDao.search(null, null, null);
        assertEquals(3, saved.size());
        assertTrue(saved.stream().allMatch(p -> p.getCurrentStockQuantity() == 0));
        assertEquals(1, productDao.insertAllCalls, "all products go in one transaction");
    }

    @Test
    void columnsCanBeInAnyOrderWithShortNames() {
        ProductCatalog.ImportPreview preview = catalog.previewImport(manager, xlsx(
                new Object[]{"Price", "Code", "Unit", "Name", "category"},
                row(99, "B-1", "pcs", "Biscuits", "Groceries")));
        assertTrue(preview.canImport(), () -> "errors: " + preview.errors());
        Product p = preview.products().get(0);
        assertEquals("B-1", p.getProductCode());
        assertEquals("Biscuits", p.getName());
        assertEquals(new BigDecimal("99.00"), p.getSellingPrice());
    }

    @Test
    void missingRequiredColumnStopsTheImport() {
        ProductCatalog.ImportPreview preview = catalog.previewImport(manager, xlsx(
                new Object[]{"Product code", "Product name", "Unit", "Selling price"},
                row("P-1", "Rice", "kg", 100)));
        assertFalse(preview.canImport());
        assertTrue(preview.products().isEmpty());
        assertEquals(1, preview.errors().size());
        assertTrue(preview.errors().get(0).contains("\"Category\""));
    }

    @Test
    void stockAndUnknownColumnsAreIgnoredWithANote() {
        ProductCatalog.ImportPreview preview = catalog.previewImport(manager, xlsx(
                new Object[]{"Product code", "Product name", "Category", "Unit", "Selling price", "Stock", "Supplier"},
                row("P-1", "Rice", "Groceries", "kg", 100, 500, "Metro")));
        assertTrue(preview.canImport());
        assertEquals(0, preview.products().get(0).getCurrentStockQuantity());
        assertTrue(preview.notes().stream().anyMatch(n -> n.contains("stock column was ignored")));
        assertTrue(preview.notes().contains("Ignored column: Supplier."));
    }

    @Test
    void everyBadRowIsReportedWithItsRowNumberAndNothingCanBeImported() {
        ProductCatalog.ImportPreview preview = catalog.previewImport(manager, xlsx(
                HEADERS,
                row("P-1", "Rice", "Groceries", "kg", 100, 5, "Active"),        // row 2: fine
                row("P-2", "Milk", "Dairy", "litre", 280, 5, "Active"),         // row 3: unknown category
                row("P-3", "Salt", "Groceries", "kg", 0, 5, "Active"),          // row 4: price 0
                row("P-4", "Sugar", "Groceries", "kg", "cheap", 5, "Active"),   // row 5: price not a number
                row("P-5", "Flour", "Groceries", "kg", 90, 2.5, "Active"),      // row 6: reorder not whole
                row("P-6", "Oil", "Groceries", "litre", 520, 5, "Sold out"),    // row 7: bad status
                row("p-1", "Rice again", "Groceries", "kg", 100, 5, null),      // row 8: same code as row 2
                row("P-8", null, "Groceries", "kg", 100, 5, null),              // row 9: no name
                row("P-9", "Dal", "Groceries", "kg", 12.345, 5, null)));        // row 10: 3 decimals

        assertFalse(preview.canImport());
        assertTrue(preview.products().isEmpty());
        List<String> errors = preview.errors();
        assertEquals(8, errors.size(), () -> String.join("\n", errors));
        assertTrue(errors.get(0).startsWith("Row 3: Category \"Dairy\" does not exist"));
        assertTrue(errors.get(1).startsWith("Row 4: Selling price must be greater than zero"));
        assertTrue(errors.get(2).startsWith("Row 5: Selling price \"cheap\" is not a number"));
        assertTrue(errors.get(3).startsWith("Row 6: Reorder level"));
        assertTrue(errors.get(4).startsWith("Row 7: Status \"Sold out\""));
        assertEquals("Row 8: Product code p-1 is also on row 2.", errors.get(5));
        assertTrue(errors.get(6).startsWith("Row 9: Product name is required"));
        assertTrue(errors.get(7).startsWith("Row 10: Selling price can have at most 2 decimal places"));
    }

    @Test
    void existingProductCodesAreNotChanged() {
        catalog.registerProduct(manager, new Product("P-1", "Rice", "kg", new BigDecimal("100"), 5, null, 0, groceries));
        ProductCatalog.ImportPreview preview = catalog.previewImport(manager, xlsx(
                HEADERS, row("P-1", "Rice NEW NAME", "Groceries", "kg", 999, 5, "Active")));
        assertFalse(preview.canImport());
        assertTrue(preview.errors().get(0).contains("already exists"));
        assertEquals("Rice", productDao.findByCode("P-1").orElseThrow().getName());
    }

    @Test
    void blankRowsAreSkippedButRowNumbersStayTrue() {
        ProductCatalog.ImportPreview preview = catalog.previewImport(manager, xlsx(
                HEADERS,
                row("P-1", "Rice", "Groceries", "kg", 100, 5, null),
                row(null, null, null, null, null, null, null),
                row("P-3", "Salt", "Nowhere", "kg", 50, 5, null)));
        assertEquals(List.of("Row 4: Category \"Nowhere\" does not exist. "
                + "Use one of the categories on the template's Help sheet."), preview.errors());
    }

    @Test
    void fileWithOnlyTheHeaderRowHasNothingToImport() {
        ProductCatalog.ImportPreview preview = catalog.previewImport(manager, xlsx(HEADERS));
        assertFalse(preview.canImport());
        assertEquals(List.of("The file has no products below the header row."), preview.errors());
    }

    @Test
    void tooManyRowsAreRefused() {
        Object[][] rows = new Object[ProductCatalog.MAX_IMPORT_ROWS + 2][];
        rows[0] = HEADERS;
        for (int i = 1; i < rows.length; i++) {
            rows[i] = row("P-" + i, "Item " + i, "Groceries", "pcs", 10, 0, null);
        }
        ProductCatalog.ImportPreview preview = catalog.previewImport(manager, xlsx(rows));
        assertFalse(preview.canImport());
        assertTrue(preview.errors().get(0).contains("more than " + ProductCatalog.MAX_IMPORT_ROWS));
    }

    @Test
    void aFileThatIsNotExcelIsRejected() {
        InputStream notExcel = new ByteArrayInputStream("code,name\nP-1,Rice".getBytes(StandardCharsets.UTF_8));
        BusinessException e = assertThrows(BusinessException.class, () -> catalog.previewImport(manager, notExcel));
        assertTrue(e.getMessage().contains("could not be read"));
    }

    // ---- saving ----

    @Test
    void importIsAllOrNothingWhenACodeWasAddedInTheMeantime() {
        ProductCatalog.ImportPreview preview = catalog.previewImport(manager, xlsx(
                HEADERS,
                row("P-1", "Rice", "Groceries", "kg", 100, 5, null),
                row("P-2", "Salt", "Groceries", "kg", 50, 5, null)));
        assertTrue(preview.canImport());
        catalog.registerProduct(manager, new Product("P-2", "Salt", "kg", new BigDecimal("50"), 5, null, 0, groceries));

        assertThrows(BusinessException.class, () -> catalog.importProducts(manager, preview.products()));
        assertEquals(0, productDao.insertAllCalls);
        assertTrue(productDao.findByCode("P-1").isEmpty(), "nothing may be imported");
    }

    @Test
    void importRevalidatesWhatItIsGiven() {
        Product sneaky = new Product("P-1", "Rice", "kg", new BigDecimal("-5"), 5, null, 99, groceries);
        assertThrows(BusinessException.class, () -> catalog.importProducts(manager, List.of(sneaky)));
        assertThrows(BusinessException.class, () -> catalog.importProducts(manager, List.of()));
        Product a = new Product("P-1", "Rice", "kg", new BigDecimal("5"), 5, null, 99, groceries);
        Product b = new Product("p-1", "Rice", "kg", new BigDecimal("5"), 5, null, 0, groceries);
        assertThrows(BusinessException.class, () -> catalog.importProducts(manager, List.of(a, b)));
        assertEquals(0, productDao.insertAllCalls);
    }

    // ---- who may import ----

    @Test
    void onlyManagerAndStorekeeperMayImport() {
        byte[] file = bytes(HEADERS, row("P-1", "Rice", "Groceries", "kg", 100, 5, null));
        BusinessException e = assertThrows(BusinessException.class,
                () -> catalog.previewImport(cashier, new ByteArrayInputStream(file)));
        assertTrue(e.getMessage().startsWith("Access denied"));
        assertThrows(BusinessException.class, () -> catalog.importProducts(cashier, List.of()));
        assertThrows(BusinessException.class, () -> catalog.writeImportTemplate(cashier, new ByteArrayOutputStream()));
        assertTrue(catalog.previewImport(manager, new ByteArrayInputStream(file)).canImport());
        assertTrue(catalog.previewImport(storekeeper, new ByteArrayInputStream(file)).canImport());
    }

    @Test
    void storekeeperStillCannotUseTheProductForm() {
        Product p = new Product("P-1", "Rice", "kg", new BigDecimal("100"), 5, null, 0, groceries);
        assertThrows(BusinessException.class, () -> catalog.registerProduct(storekeeper, p));
        assertThrows(BusinessException.class, () -> catalog.updateProduct(storekeeper, p));
        assertThrows(BusinessException.class, () -> catalog.addCategory(storekeeper, "Dairy"));
    }

    // ---- template ----

    @Test
    void templateHasTheColumnsAndListsTheCategories() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        catalog.writeImportTemplate(storekeeper, out);

        SpreadsheetUtil.Table table = SpreadsheetUtil.readFirstSheet(new ByteArrayInputStream(out.toByteArray()), 10);
        assertEquals(ProductCatalog.IMPORT_COLUMNS, table.headers());
        assertTrue(table.rows().isEmpty());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet help = wb.getSheet("Help");
            List<String> lines = new ArrayList<>();
            help.forEach(r -> lines.add(r.getCell(0).getStringCellValue()));
            assertTrue(lines.contains("  Groceries"));
            assertTrue(lines.contains("  Beverages"));
        }
    }

    // ---- helpers ----

    private static Object[] row(Object... values) {
        return values;
    }

    private static InputStream xlsx(Object[]... rows) {
        return new ByteArrayInputStream(bytes(rows));
    }

    /** Builds an .xlsx in memory: Strings become text cells, Numbers numeric cells, null an empty cell. */
    private static byte[] bytes(Object[]... rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Products");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else if (v != null) {
                        row.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
