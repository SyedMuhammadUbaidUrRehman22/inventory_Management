package com.retailpos.service;

import com.retailpos.model.Cashier;
import com.retailpos.model.Category;
import com.retailpos.model.Manager;
import com.retailpos.model.Product;
import com.retailpos.model.Sale;
import com.retailpos.model.SalesReceipt;
import com.retailpos.model.StockMovement;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.enums.SaleStatus;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.testsupport.FakeProductDao;
import com.retailpos.testsupport.FakeReportDao;
import com.retailpos.testsupport.FakeSaleDao;
import com.retailpos.testsupport.FakeSalesReceiptDao;
import com.retailpos.testsupport.FakeStockMovementDao;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import com.retailpos.util.ReceiptFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleServiceTest {

    private FakeProductDao productDao;
    private FakeSaleDao saleDao;
    private FakeSalesReceiptDao receiptDao;
    private FakeStockMovementDao movementDao;
    private FakeReportDao reportDao;
    private AccessControl accessControl;
    private SaleService service;

    private final Cashier cashier = new Cashier(3, "bilal", "hash", 1, true);
    private final Manager manager = new Manager(1, "manager", "hash", null, true);
    private final Storekeeper storekeeper = new Storekeeper(2, "store", "hash", 1, true);

    @BeforeEach
    void setUp() {
        accessControl = new AccessControl(new FakeUserDao());
        productDao = new FakeProductDao();
        saleDao = new FakeSaleDao();
        receiptDao = new FakeSalesReceiptDao();
        movementDao = new FakeStockMovementDao();
        reportDao = new FakeReportDao();
        service = new SaleService(accessControl, productDao, saleDao, receiptDao, movementDao);

        Category groceries = new Category(1, "Groceries");
        productDao.insert(new Product("RICE", "Basmati Rice 5kg", "bag", new BigDecimal("1450.00"), 2, "ACTIVE", 8, groceries));
        productDao.insert(new Product("OIL", "Cooking Oil 1L", "bottle", new BigDecimal("520.00"), 2, "ACTIVE", 9, groceries));
        productDao.insert(new Product("OLD", "Old Item", "pc", new BigDecimal("10.00"), 0, "DISCONTINUED", 5, groceries));
    }

    @Test
    @DisplayName("FR14, FR20: a cashier starts an empty draft sale under their own name")
    void createSale() {
        Sale sale = service.createSale(cashier);
        assertEquals(SaleStatus.DRAFT, sale.getStatus());
        assertEquals(3, sale.getCashier().getUserID());
        assertTrue(sale.getItems().isEmpty());
        assertEquals(0, sale.getFinalAmount().signum());
        assertEquals(0, saleDao.count(), "nothing is saved until the sale is finalized");
    }

    @Test
    @DisplayName("FR30: only a cashier can sell")
    void onlyCashierCanSell() {
        assertThrows(BusinessException.class, () -> service.createSale(manager));
        assertThrows(BusinessException.class, () -> service.createSale(storekeeper));
        assertThrows(BusinessException.class, () -> service.createSale(null));
    }

    @Test
    @DisplayName("FR15, FR18: line totals, subtotal, discount and final amount")
    void totals() {
        Sale sale = service.createSale(cashier);
        service.addItem(cashier, sale, "RICE", 2);   // 2,900
        service.addItem(cashier, sale, "OIL", 3);    // 1,560
        service.applyDiscount(cashier, sale, new BigDecimal("460"));

        assertEquals(new BigDecimal("2900.00"), sale.findItem("RICE").getLineTotal());
        assertEquals(new BigDecimal("4460.00"), sale.getSubtotal());
        assertEquals(new BigDecimal("460.00"), sale.getDiscount());
        assertEquals(new BigDecimal("4000.00"), sale.getFinalAmount());
    }

    @Test
    @DisplayName("FR17: quantity above available stock is blocked, also across repeated adds")
    void blocksOverStock() {
        Sale sale = service.createSale(cashier);
        BusinessException e = assertThrows(BusinessException.class, () -> service.addItem(cashier, sale, "RICE", 9));
        assertTrue(e.getMessage().contains("exceeds available stock (8)"));

        service.addItem(cashier, sale, "RICE", 5);
        assertThrows(BusinessException.class, () -> service.addItem(cashier, sale, "RICE", 4));
        service.addItem(cashier, sale, "RICE", 3);
        assertEquals(1, sale.getItems().size(), "same product stays on one line");
        assertEquals(8, sale.findItem("RICE").getQuantity());
    }

    @Test
    @DisplayName("Rejects zero quantity, unknown and discontinued products")
    void rejectsBadItems() {
        Sale sale = service.createSale(cashier);
        assertThrows(BusinessException.class, () -> service.addItem(cashier, sale, "RICE", 0));
        assertThrows(BusinessException.class, () -> service.addItem(cashier, sale, "NOPE", 1));
        assertThrows(BusinessException.class, () -> service.addItem(cashier, sale, "OLD", 1));
    }

    @Test
    @DisplayName("FR18: discount cannot be negative or exceed the subtotal")
    void discountLimits() {
        Sale sale = service.createSale(cashier);
        service.addItem(cashier, sale, "OIL", 1);
        assertThrows(BusinessException.class, () -> service.applyDiscount(cashier, sale, new BigDecimal("-1")));
        assertThrows(BusinessException.class, () -> service.applyDiscount(cashier, sale, new BigDecimal("520.01")));
        service.applyDiscount(cashier, sale, new BigDecimal("520"));
        assertEquals(0, sale.getFinalAmount().signum());
    }

    @Test
    @DisplayName("Removing a line lowers an oversized discount to the new subtotal")
    void removeLineCapsDiscount() {
        Sale sale = service.createSale(cashier);
        service.addItem(cashier, sale, "RICE", 1);
        service.addItem(cashier, sale, "OIL", 1);
        service.applyDiscount(cashier, sale, new BigDecimal("1000"));
        service.removeItem(cashier, sale, "RICE");
        assertEquals(new BigDecimal("520.00"), sale.getDiscount());
        assertEquals(0, sale.getFinalAmount().signum());
    }

    @Test
    @DisplayName("FR19, FR20, FR21: finalizing reduces stock, logs one SALE movement per line and creates a receipt")
    void finalizeSale() {
        Sale sale = service.createSale(cashier);
        service.addItem(cashier, sale, "RICE", 2);
        service.addItem(cashier, sale, "OIL", 3);
        service.applyDiscount(cashier, sale, new BigDecimal("460"));

        SalesReceipt receipt = service.finalizeSale(cashier, sale);

        assertEquals(SaleStatus.COMPLETED, sale.getStatus());
        assertTrue(sale.getSaleID() > 0);
        assertEquals(sale.getSaleID(), receipt.getSale().getSaleID());
        assertEquals(1, receiptDao.count());
        assertEquals(6, productDao.findByCode("RICE").orElseThrow().getCurrentStockQuantity());
        assertEquals(6, productDao.findByCode("OIL").orElseThrow().getCurrentStockQuantity());

        List<StockMovement> moves = movementDao.findByProduct("RICE");
        assertEquals(1, moves.size());
        StockMovement m = moves.get(0);
        assertEquals(StockMovementType.SALE, m.getMovementType());
        assertEquals(-2, m.getQuantityChange());
        assertEquals(6, m.getBalanceAfter());
        assertEquals(3, m.getResponsibleUser().getUserID());
        assertEquals("SL-" + String.format("%05d", sale.getSaleID()), m.getSourceReference());
        assertEquals(LocalDate.now(), receipt.getIssuedAt().toLocalDate());
    }

    @Test
    @DisplayName("A completed sale cannot be finalized or changed again, so stock is never reduced twice")
    void noDoubleFinalize() {
        Sale sale = service.createSale(cashier);
        service.addItem(cashier, sale, "RICE", 2);
        service.finalizeSale(cashier, sale);

        assertThrows(BusinessException.class, () -> service.finalizeSale(cashier, sale));
        assertThrows(BusinessException.class, () -> service.addItem(cashier, sale, "OIL", 1));
        assertEquals(6, productDao.findByCode("RICE").orElseThrow().getCurrentStockQuantity());
        assertEquals(1, movementDao.findByProduct("RICE").size());
    }

    @Test
    @DisplayName("FR19: stock is re-checked at finalize and a failed attempt changes nothing")
    void finalizeRechecksStock() {
        Sale sale = service.createSale(cashier);
        service.addItem(cashier, sale, "RICE", 2);
        service.addItem(cashier, sale, "OIL", 3);
        productDao.setStock("OIL", 1);   // someone else sold it in the meantime

        assertThrows(BusinessException.class, () -> service.finalizeSale(cashier, sale));

        assertEquals(SaleStatus.DRAFT, sale.getStatus());
        assertEquals(0, saleDao.count());
        assertEquals(0, receiptDao.count());
        assertEquals(8, productDao.findByCode("RICE").orElseThrow().getCurrentStockQuantity());
        assertTrue(movementDao.findRecent(10).isEmpty());
    }

    @Test
    @DisplayName("An empty sale cannot be finalized")
    void emptySale() {
        Sale sale = service.createSale(cashier);
        assertThrows(BusinessException.class, () -> service.finalizeSale(cashier, sale));
    }

    @Test
    @DisplayName("A cashier cannot change another cashier's sale")
    void otherCashiersSale() {
        Sale sale = service.createSale(cashier);
        Cashier other = new Cashier(9, "other", "hash", 1, true);
        assertThrows(BusinessException.class, () -> service.addItem(other, sale, "RICE", 1));
    }

    @Test
    @DisplayName("FR21: receipt can be fetched again and is laid out for printing")
    void receiptText() {
        Sale sale = service.createSale(cashier);
        service.addItem(cashier, sale, "RICE", 2);
        service.applyDiscount(cashier, sale, new BigDecimal("100"));
        service.finalizeSale(cashier, sale);

        SalesReceipt receipt = service.getReceipt(cashier, sale.getSaleID());
        String text = ReceiptFormatter.format(receipt);
        assertTrue(text.contains("RC-SL-"));
        assertTrue(text.contains("Basmati Rice 5kg"));
        assertTrue(text.contains("Rs 2,800"));
        assertTrue(text.contains("bilal"));
        assertThrows(BusinessException.class, () -> service.getReceipt(cashier, 999));
    }

    @Test
    @DisplayName("FR33: daily and date-range sales reports count completed sales only")
    void salesReport() {
        Sale a = service.createSale(cashier);
        service.addItem(cashier, a, "RICE", 2);
        service.applyDiscount(cashier, a, new BigDecimal("100"));
        service.finalizeSale(cashier, a);
        Sale b = service.createSale(cashier);
        service.addItem(cashier, b, "OIL", 1);
        service.finalizeSale(cashier, b);
        Sale draft = service.createSale(cashier);
        service.addItem(cashier, draft, "OIL", 1);

        ReportService reports = new ReportService(accessControl, productDao, null, null, reportDao, saleDao);
        ReportService.SalesReport today = reports.generateDailySalesReport(manager, LocalDate.now());
        assertEquals(2, today.totalSales());
        assertEquals(3, today.totalUnits());
        assertEquals(new BigDecimal("3320.00"), today.totalNetSales());
        assertEquals(new BigDecimal("100.00"), today.totalDiscount());
        assertEquals(1, today.days().size());

        assertEquals(2, reports.generateSalesReport(manager, null, null).totalSales());
        assertEquals(0, reports.generateSalesReport(manager, LocalDate.now().minusDays(5), LocalDate.now().minusDays(2)).totalSales());
        assertThrows(BusinessException.class, () -> reports.generateSalesReport(manager, LocalDate.now(), LocalDate.now().minusDays(1)));
        assertThrows(BusinessException.class, () -> reports.generateSalesReport(cashier, null, null));
    }
}
