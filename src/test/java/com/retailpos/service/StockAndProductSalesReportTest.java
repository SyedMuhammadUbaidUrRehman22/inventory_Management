package com.retailpos.service;

import com.retailpos.model.Cashier;
import com.retailpos.model.Category;
import com.retailpos.model.Manager;
import com.retailpos.model.Product;
import com.retailpos.model.Report;
import com.retailpos.model.Sale;
import com.retailpos.model.SaleItem;
import com.retailpos.model.StockMovement;
import com.retailpos.model.enums.SaleStatus;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.service.ReportService.CurrentInventoryReport;
import com.retailpos.service.ReportService.ProductSalesReport;
import com.retailpos.service.ReportService.StockMovementReport;
import com.retailpos.testsupport.FakeProductDao;
import com.retailpos.testsupport.FakeReportDao;
import com.retailpos.testsupport.FakeSaleDao;
import com.retailpos.testsupport.FakeStockMovementDao;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR32 (current inventory, stock movements) and FR35 (product-wise sales) in {@link ReportService}. */
class StockAndProductSalesReportTest {

    private static final LocalDate TODAY = LocalDate.now();

    private FakeProductDao productDao;
    private FakeSaleDao saleDao;
    private FakeStockMovementDao movementDao;
    private FakeReportDao reportDao;
    private AccessControl accessControl;
    private ReportService reports;

    private final Manager manager = new Manager(1, "ayesha", "hash", null, true);
    private final Cashier cashier = new Cashier(3, "bilal", "hash", 1, true);

    private Product rice;
    private Product oil;

    @BeforeEach
    void setUp() {
        accessControl = new AccessControl(new FakeUserDao());
        productDao = new FakeProductDao();
        saleDao = new FakeSaleDao();
        movementDao = new FakeStockMovementDao();
        reportDao = new FakeReportDao();
        reports = new ReportService(accessControl, productDao, null, null, reportDao, saleDao, movementDao);

        Category groceries = new Category(1, "Groceries");
        rice = new Product("RICE", "Basmati Rice 5kg", "bag", new BigDecimal("1450.00"), 2, "ACTIVE", 10, groceries);
        oil = new Product("OIL", "Cooking Oil 1L", "bottle", new BigDecimal("520.00"), 2, "ACTIVE", 1, groceries);
        productDao.insert(rice);
        productDao.insert(oil);
        productDao.insert(new Product("OLD", "Old Soap", "pc", new BigDecimal("50.00"), 0, "DISCONTINUED", 0, groceries));
    }

    private String lastReportType() {
        List<Report> recent = reportDao.findRecent(1);
        return recent.isEmpty() ? null : recent.get(0).getReportType();
    }

    // ---- FR32: current inventory ----

    @Test
    @DisplayName("FR32: current inventory lists every product with stock, value and low-stock flag")
    void currentInventory() {
        CurrentInventoryReport report = reports.generateCurrentInventoryReport(manager);

        assertEquals(3, report.totalProducts());
        assertEquals(List.of("OIL", "OLD", "RICE"), report.rows().stream().map(ReportService.InventoryRow::productCode).toList());
        assertEquals(11, report.totalUnits());
        assertEquals(0, new BigDecimal("15020.00").compareTo(report.totalStockValue()), "10 × 1450 + 1 × 520 + 0 × 50");
        assertEquals(2, report.lowStockCount(), "OIL (1 ≤ 2) and OLD (0 ≤ 0)");

        ReportService.InventoryRow riceRow = report.rows().get(2);
        assertEquals(10, riceRow.currentStock());
        assertFalse(riceRow.lowStock());
        assertEquals(0, new BigDecimal("14500.00").compareTo(riceRow.stockValue()));
        assertEquals("DISCONTINUED", report.rows().get(1).status());
        assertEquals("ayesha", report.generatedBy());
        assertEquals("CURRENT_INVENTORY", lastReportType());
    }

    @Test
    @DisplayName("FR30: only a manager generates the new reports")
    void managerOnly() {
        assertThrows(BusinessException.class, () -> reports.generateCurrentInventoryReport(cashier));
        assertThrows(BusinessException.class, () -> reports.generateStockMovementReport(cashier, null, null, null, null));
        assertThrows(BusinessException.class, () -> reports.generateProductWiseSalesReport(cashier, null, null));
        assertTrue(reportDao.findRecent(10).isEmpty());
    }

    // ---- FR32: stock movements ----

    private void move(Product p, StockMovementType type, int change, int balance, LocalDateTime when, String ref) {
        movementDao.insert(new StockMovement(0, type, change, when, ref, balance, manager, p));
    }

    private void addMovements() {
        move(rice, StockMovementType.RECEIPT, 10, 10, TODAY.minusDays(2).atTime(9, 0), "PO-1/RC-1");
        move(rice, StockMovementType.SALE, -2, 8, TODAY.minusDays(1).atTime(15, 0), "SL-00001");
        move(oil, StockMovementType.SALE, -3, 2, TODAY.atTime(10, 0), "SL-00002");
        move(rice, StockMovementType.RETURN, 1, 9, TODAY.atTime(11, 0), "SL-00001/RT-00001");
        move(oil, StockMovementType.ADJUSTMENT, -1, 1, TODAY.atTime(12, 0), "ADJ-00001");
    }

    @Test
    @DisplayName("FR32: stock movements default to today, oldest first, with units in and out")
    void movementsToday() {
        addMovements();
        StockMovementReport report = reports.generateStockMovementReport(manager, null, null, null, null);

        assertEquals(TODAY, report.fromDate());
        assertEquals(TODAY, report.toDate());
        assertEquals(3, report.totalMovements());
        assertEquals(List.of("SL-00002", "SL-00001/RT-00001", "ADJ-00001"),
                report.rows().stream().map(ReportService.StockMovementRow::sourceReference).toList());
        assertEquals(1, report.unitsIn());
        assertEquals(4, report.unitsOut());
        assertEquals(-3, report.netChange());
        assertEquals("STOCK_MOVEMENTS", lastReportType());
    }

    @Test
    @DisplayName("FR32: stock movements can be filtered by date range, product and type")
    void movementsFiltered() {
        addMovements();
        StockMovementReport riceOnly = reports.generateStockMovementReport(manager, TODAY.minusDays(2), TODAY, "RICE", null);
        assertEquals(3, riceOnly.totalMovements());
        assertEquals(11, riceOnly.unitsIn());
        assertEquals(2, riceOnly.unitsOut());
        assertTrue(riceOnly.rows().stream().allMatch(r -> r.productCode().equals("RICE")));

        StockMovementReport salesOnly = reports.generateStockMovementReport(manager, TODAY.minusDays(7), TODAY, null,
                StockMovementType.SALE);
        assertEquals(2, salesOnly.totalMovements());
        assertEquals(0, salesOnly.unitsIn());
        assertEquals(5, salesOnly.unitsOut());

        StockMovementReport oneDay = reports.generateStockMovementReport(manager, TODAY.minusDays(1), null, " ", null);
        assertEquals(TODAY.minusDays(1), oneDay.toDate(), "one date means that single day");
        assertEquals(1, oneDay.totalMovements());

        assertThrows(BusinessException.class,
                () -> reports.generateStockMovementReport(manager, TODAY, TODAY.minusDays(1), null, null));
    }

    @Test
    @DisplayName("FR32: the movement report is unavailable without a movement DAO")
    void movementsUnavailable() {
        ReportService old = new ReportService(accessControl, productDao, null, null, reportDao, saleDao);
        assertThrows(BusinessException.class, () -> old.generateStockMovementReport(manager, null, null, null, null));
    }

    // ---- FR35: product-wise sales ----

    private void sale(LocalDateTime when, SaleStatus status, String discount, SaleItem... lines) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SaleItem item : lines) {
            subtotal = subtotal.add(item.getLineTotal());
        }
        BigDecimal disc = new BigDecimal(discount);
        saleDao.insert(new Sale(0, when, status, subtotal, disc, subtotal.subtract(disc), cashier,
                new ArrayList<>(List.of(lines))));
    }

    private static SaleItem line(Product p, int qty) {
        return new SaleItem(0, qty, p.getSellingPrice(), p.getSellingPrice().multiply(BigDecimal.valueOf(qty)), p);
    }

    private void addSales() {
        sale(TODAY.atTime(9, 30), SaleStatus.COMPLETED, "460", line(rice, 2), line(oil, 3));   // 2,900 + 1,560
        sale(TODAY.atTime(13, 0), SaleStatus.COMPLETED, "0", line(oil, 1));                    // 520
        sale(TODAY.minusDays(1).atTime(LocalTime.NOON), SaleStatus.COMPLETED, "0", line(rice, 1)); // 1,450
        sale(TODAY.atTime(14, 0), SaleStatus.DRAFT, "0", line(rice, 5));                        // not counted
    }

    @Test
    @DisplayName("FR35: quantity, number of sales and revenue per product, highest revenue first")
    void productWiseSalesToday() {
        addSales();
        ProductSalesReport report = reports.generateProductWiseSalesReport(manager, null, null);

        assertEquals(2, report.productsSold());
        ReportService.ProductSalesRow first = report.rows().get(0);
        assertEquals("RICE", first.productCode());
        assertEquals(2, first.unitsSold());
        assertEquals(1, first.saleCount());
        assertEquals(0, new BigDecimal("2900.00").compareTo(first.revenue()));

        ReportService.ProductSalesRow second = report.rows().get(1);
        assertEquals("OIL", second.productCode());
        assertEquals(4, second.unitsSold());
        assertEquals(2, second.saleCount());
        assertEquals(0, new BigDecimal("2080.00").compareTo(second.revenue()));

        assertEquals(6, report.totalUnits());
        assertEquals(0, new BigDecimal("4980.00").compareTo(report.totalRevenue()),
                "line totals before the 460 sale discount");
        assertEquals("PRODUCT_SALES", lastReportType());
    }

    @Test
    @DisplayName("FR35: a date range adds the days together; From after To is refused")
    void productWiseSalesRange() {
        addSales();
        ProductSalesReport report = reports.generateProductWiseSalesReport(manager, TODAY.minusDays(1), TODAY);
        ReportService.ProductSalesRow riceRow = report.rows().stream()
                .filter(r -> r.productCode().equals("RICE")).findFirst().orElseThrow();
        assertEquals(3, riceRow.unitsSold());
        assertEquals(2, riceRow.saleCount());
        assertEquals(0, new BigDecimal("4350.00").compareTo(riceRow.revenue()));

        assertTrue(reports.generateProductWiseSalesReport(manager, TODAY.minusDays(9), TODAY.minusDays(8)).rows().isEmpty());
        assertThrows(BusinessException.class, () -> reports.generateProductWiseSalesReport(manager, TODAY, TODAY.minusDays(1)));
    }
}
