package com.retailpos.service;

import com.retailpos.model.Category;
import com.retailpos.model.Manager;
import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.Supplier;
import com.retailpos.model.enums.PurchaseOrderStatus;
import com.retailpos.testsupport.FakeProductDao;
import com.retailpos.testsupport.FakePurchaseOrderDao;
import com.retailpos.testsupport.FakeReportDao;
import com.retailpos.testsupport.FakeSupplierDao;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {

    private AccessControl accessControl;
    private FakeProductDao productDao;
    private FakePurchaseOrderDao purchaseOrderDao;
    private FakeSupplierDao supplierDao;
    private FakeReportDao reportDao;
    private ReportService reportService;

    private final Manager manager = new Manager(1, "manager", "hash", null, true);
    private final Storekeeper storekeeper = new Storekeeper(2, "storekeeper", "hash", 1, true);

    @BeforeEach
    void setUp() {
        accessControl = new AccessControl(new FakeUserDao());
        productDao = new FakeProductDao();
        purchaseOrderDao = new FakePurchaseOrderDao();
        supplierDao = new FakeSupplierDao();
        reportDao = new FakeReportDao();
        reportService = new ReportService(accessControl, productDao, purchaseOrderDao, supplierDao, reportDao);

        Category groc = new Category(1, "Groceries");
        // p1: current 5, reorder 10 -> LOW STOCK (deficit 5)
        Product p1 = new Product("RICE-5K", "Basmati Rice 5kg", "bag", new BigDecimal("1500.00"), 10, "ACTIVE", 5, groc);
        // p2: current 0, reorder 15 -> OUT OF STOCK & LOW STOCK (deficit 15)
        Product p2 = new Product("SUGAR-1K", "Sugar 1kg", "kg", new BigDecimal("150.00"), 15, "ACTIVE", 0, groc);
        // p3: current 25, reorder 20 -> NOT low stock
        Product p3 = new Product("OIL-1L", "Cooking Oil 1L", "bottle", new BigDecimal("500.00"), 20, "ACTIVE", 25, groc);

        productDao.insert(p1);
        productDao.insert(p2);
        productDao.insert(p3);

        Supplier s1 = new Supplier(1, "Metro Wholesale", "Warehouse 12", "0300-1234567");
        supplierDao.insert(s1);

        PurchaseOrder po = new PurchaseOrder(1, LocalDate.of(2026, 11, 10), PurchaseOrderStatus.ORDERED,
                new BigDecimal("25000.00"), s1, List.of(
                new PurchaseOrderItem(1, 20, new BigDecimal("1250.00"), 10, p1)
        ));
        purchaseOrderDao.insert(po);
    }

    @Test
    @DisplayName("FR31: Low-stock report returns only products at or below reorder level")
    void generateLowStockReportSuccess() {
        ReportService.LowStockReport report = reportService.generateLowStockReport(manager);

        assertNotNull(report);
        assertEquals(2, report.totalItems()); // RICE-5K and SUGAR-1K
        assertEquals(1, report.outOfStockCount()); // SUGAR-1K has 0
        assertEquals(20, report.totalDeficitUnits()); // 5 + 15 = 20

        // Check report audit logged
        assertEquals(1, reportDao.findRecent(10).size());
    }

    @Test
    @DisplayName("FR31: Non-manager cannot generate reports")
    void nonManagerCannotGenerateReport() {
        assertThrows(BusinessException.class, () ->
                reportService.generateLowStockReport(storekeeper));
    }

    @Test
    @DisplayName("FR34: Supplier-wise purchase report aggregates orders correctly")
    void generateSupplierWisePurchaseReportSuccess() {
        ReportService.SupplierWisePurchaseReport report = reportService.generateSupplierWisePurchaseReport(
                manager, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), 1);

        assertNotNull(report);
        assertEquals(1, report.totalOrders());
        assertEquals(0, new BigDecimal("25000.00").compareTo(report.totalSpend()));
        assertEquals(20, report.totalOrderedUnits());
        assertEquals(10, report.totalReceivedUnits());
        assertEquals(1, report.rows().size());
        assertEquals("Metro Wholesale", report.rows().get(0).supplierName());
    }
}
