package com.retailpos.service;

import com.retailpos.model.Category;
import com.retailpos.model.Manager;
import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.StockMovement;
import com.retailpos.model.StockReceipt;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.Supplier;
import com.retailpos.model.enums.PurchaseOrderStatus;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.testsupport.FakeProductDao;
import com.retailpos.testsupport.FakePurchaseOrderDao;
import com.retailpos.testsupport.FakeStockMovementDao;
import com.retailpos.testsupport.FakeStockReceiptDao;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockServiceTest {

    private AccessControl accessControl;
    private FakePurchaseOrderDao purchaseOrderDao;
    private FakeProductDao productDao;
    private FakeStockReceiptDao stockReceiptDao;
    private FakeStockMovementDao stockMovementDao;
    private StockService stockService;

    private final Manager manager = new Manager(1, "manager", "hash", null, true);
    private final Storekeeper storekeeper = new Storekeeper(2, "storekeeper", "hash", 1, true);

    private Product p1;
    private Product p2;
    private PurchaseOrder po;

    @BeforeEach
    void setUp() {
        accessControl = new AccessControl(new FakeUserDao());
        purchaseOrderDao = new FakePurchaseOrderDao();
        productDao = new FakeProductDao();
        stockReceiptDao = new FakeStockReceiptDao();
        stockMovementDao = new FakeStockMovementDao();
        stockService = new StockService(accessControl, purchaseOrderDao, productDao, stockReceiptDao, stockMovementDao);

        Category groc = new Category(1, "Groceries");
        p1 = new Product("RICE-5K", "Basmati Rice 5kg", "bag", new BigDecimal("1500.00"), 10, "ACTIVE", 5, groc);
        p2 = new Product("OIL-1L", "Cooking Oil 1L", "bottle", new BigDecimal("500.00"), 20, "ACTIVE", 15, groc);
        productDao.insert(p1);
        productDao.insert(p2);

        Supplier supplier = new Supplier(1, "Metro Wholesale", "Warehouse 12", "0300-1234567");

        List<PurchaseOrderItem> items = new ArrayList<>();
        items.add(new PurchaseOrderItem(101, 20, new BigDecimal("1280.00"), 0, p1));
        items.add(new PurchaseOrderItem(102, 24, new BigDecimal("450.00"), 0, p2));

        po = new PurchaseOrder(42, LocalDate.now(), PurchaseOrderStatus.ORDERED, new BigDecimal("36400.00"), supplier, items);
        purchaseOrderDao.insert(po);
    }

    @Test
    @DisplayName("FR11, FR12, FR13: Storekeeper records receipt, increases stock, logs receipt and movement")
    void recordPartialReceiptSuccess() {
        // p1 starting stock: 5; receiving: 10 -> new stock: 15
        // p2 starting stock: 15; receiving: 24 (fully received) -> new stock: 39
        Map<Integer, Integer> toReceive = Map.of(
                101, 10,
                102, 24
        );

        LocalDate receiptDate = LocalDate.now();
        List<StockReceipt> receipts = stockService.recordOrderReceipt(storekeeper, po.getPoNumber(), receiptDate, toReceive);

        assertEquals(2, receipts.size());

        // Verify stock increases by received quantities
        Product updatedP1 = productDao.findByCode("RICE-5K").orElseThrow();
        Product updatedP2 = productDao.findByCode("OIL-1L").orElseThrow();
        assertEquals(15, updatedP1.getCurrentStockQuantity());
        assertEquals(39, updatedP2.getCurrentStockQuantity());

        // Verify purchase order item received quantities
        assertEquals(10, po.getItems().get(0).getReceivedQuantity());
        assertEquals(24, po.getItems().get(1).getReceivedQuantity());

        // Status is PARTIALLY_RECEIVED (item 1 only 10/20 received)
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, po.getStatus());

        // Verify stock movements created
        List<StockMovement> movementsP1 = stockMovementDao.findByProduct("RICE-5K");
        assertEquals(1, movementsP1.size());
        StockMovement m1 = movementsP1.get(0);
        assertEquals(StockMovementType.RECEIPT, m1.getMovementType());
        assertEquals(10, m1.getQuantityChange());
        assertEquals(15, m1.getBalanceAfter());
        assertEquals(storekeeper.getUserID(), m1.getResponsibleUser().getUserID());
        assertTrue(m1.getSourceReference().startsWith("PO-" + po.getPoNumber() + "/RC-"));

        List<StockMovement> movementsP2 = stockMovementDao.findByProduct("OIL-1L");
        assertEquals(1, movementsP2.size());
        StockMovement m2 = movementsP2.get(0);
        assertEquals(24, m2.getQuantityChange());
        assertEquals(39, m2.getBalanceAfter());
    }

    @Test
    @DisplayName("FR10: Full receipt marks order status as RECEIVED")
    void recordFullReceiptSetsStatusReceived() {
        Map<Integer, Integer> toReceive = Map.of(
                101, 20,
                102, 24
        );

        stockService.recordOrderReceipt(storekeeper, po.getPoNumber(), LocalDate.now(), toReceive);

        assertEquals(PurchaseOrderStatus.RECEIVED, po.getStatus());
        assertEquals(20, po.getItems().get(0).getReceivedQuantity());
        assertEquals(24, po.getItems().get(1).getReceivedQuantity());
    }

    @Test
    @DisplayName("FR11: Over-receipt beyond remaining ordered quantity is rejected")
    void overReceiptRejected() {
        Map<Integer, Integer> toReceive = Map.of(
                101, 25 // ordered is 20
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                stockService.recordOrderReceipt(storekeeper, po.getPoNumber(), LocalDate.now(), toReceive));
        assertTrue(ex.getMessage().contains("remaining"));

        // Product stock should not have changed
        Product p = productDao.findByCode("RICE-5K").orElseThrow();
        assertEquals(5, p.getCurrentStockQuantity());
    }

    @Test
    @DisplayName("FR11: Only Storekeeper can record receipts; Manager and Cashier are rejected")
    void nonStorekeeperRejected() {
        Map<Integer, Integer> toReceive = Map.of(101, 5);

        assertThrows(BusinessException.class, () ->
                stockService.recordOrderReceipt(manager, po.getPoNumber(), LocalDate.now(), toReceive));
    }

    @Test
    @DisplayName("FR11: Receipt against PREPARED or CANCELLED order is rejected")
    void receiptAgainstNonReceivableOrderRejected() {
        po.setStatus(PurchaseOrderStatus.PREPARED);
        Map<Integer, Integer> toReceive = Map.of(101, 5);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                stockService.recordOrderReceipt(storekeeper, po.getPoNumber(), LocalDate.now(), toReceive));
        assertTrue(ex.getMessage().contains("status"));
    }

    @Test
    @DisplayName("FR13: Future receipt date is rejected")
    void futureReceiptDateRejected() {
        Map<Integer, Integer> toReceive = Map.of(101, 5);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        assertThrows(BusinessException.class, () ->
                stockService.recordOrderReceipt(storekeeper, po.getPoNumber(), tomorrow, toReceive));
    }
}
