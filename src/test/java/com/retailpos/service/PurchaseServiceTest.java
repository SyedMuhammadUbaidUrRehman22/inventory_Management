package com.retailpos.service;

import com.retailpos.model.Category;
import com.retailpos.model.Manager;
import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.Supplier;
import com.retailpos.model.User;
import com.retailpos.model.enums.PurchaseOrderStatus;
import com.retailpos.testsupport.FakeProductDao;
import com.retailpos.testsupport.FakeProductSupplierDao;
import com.retailpos.testsupport.FakePurchaseOrderDao;
import com.retailpos.testsupport.FakeSupplierDao;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseServiceTest {

    private AccessControl accessControl;
    private FakePurchaseOrderDao purchaseOrderDao;
    private FakeSupplierDao supplierDao;
    private FakeProductDao productDao;
    private FakeProductSupplierDao productSupplierDao;
    private PurchaseService purchaseService;

    private final Manager manager = new Manager(1, "manager", "hash", null, true);
    private final Storekeeper storekeeper = new Storekeeper(2, "storekeeper", "hash", 1, true);

    private Supplier supplier;
    private Product p1;
    private Product p2;

    @BeforeEach
    void setUp() {
        accessControl = new AccessControl(new FakeUserDao());
        purchaseOrderDao = new FakePurchaseOrderDao();
        supplierDao = new FakeSupplierDao();
        productDao = new FakeProductDao();
        productSupplierDao = new FakeProductSupplierDao();
        purchaseService = new PurchaseService(accessControl, purchaseOrderDao, supplierDao, productDao, productSupplierDao);

        supplier = new Supplier(1, "Metro Wholesale", "Warehouse 12", "0300-1234567");
        supplierDao.insert(supplier);

        Category groc = new Category(1, "Groceries");
        p1 = new Product("RICE-5K", "Basmati Rice 5kg", "bag", new BigDecimal("1500.00"), 10, "ACTIVE", 5, groc);
        p2 = new Product("OIL-1L", "Cooking Oil 1L", "bottle", new BigDecimal("500.00"), 20, "ACTIVE", 15, groc);
        productDao.insert(p1);
        productDao.insert(p2);

        productSupplierDao.save(new com.retailpos.model.ProductSupplier(p1, supplier, new BigDecimal("1280.00")));
        productSupplierDao.save(new com.retailpos.model.ProductSupplier(p2, supplier, new BigDecimal("450.00")));
    }

    @Test
    @DisplayName("FR7, FR8, FR9: Manager creates purchase order with items and calculated total")
    void createPurchaseOrderSuccess() {
        List<PurchaseOrderItem> items = new ArrayList<>();
        items.add(new PurchaseOrderItem(0, 20, new BigDecimal("1280.00"), 0, p1));
        items.add(new PurchaseOrderItem(0, 24, new BigDecimal("450.00"), 0, p2));

        PurchaseOrder order = purchaseService.createPurchaseOrder(manager, supplier.getSupplierID(),
                LocalDate.now(), items, false);

        assertNotNull(order);
        assertTrue(order.getPoNumber() > 0);
        assertEquals(PurchaseOrderStatus.PREPARED, order.getStatus());
        assertEquals(supplier.getSupplierID(), order.getSupplier().getSupplierID());
        assertEquals(2, order.getItems().size());

        // 20 * 1280 = 25600; 24 * 450 = 10800; Total = 36400
        BigDecimal expectedTotal = new BigDecimal("36400.00");
        assertEquals(0, expectedTotal.compareTo(order.getTotalValue()));
    }

    @Test
    @DisplayName("FR7: Non-manager cannot create a purchase order")
    void nonManagerCannotCreateOrder() {
        List<PurchaseOrderItem> items = List.of(
                new PurchaseOrderItem(0, 10, new BigDecimal("1280.00"), 0, p1));

        assertThrows(BusinessException.class, () ->
                purchaseService.createPurchaseOrder(storekeeper, supplier.getSupplierID(),
                        LocalDate.now(), items, false));
    }

    @Test
    @DisplayName("FR8: Duplicate products in the same purchase order are rejected")
    void duplicateProductsRejected() {
        List<PurchaseOrderItem> items = List.of(
                new PurchaseOrderItem(0, 10, new BigDecimal("1280.00"), 0, p1),
                new PurchaseOrderItem(0, 5, new BigDecimal("1280.00"), 0, p1));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                purchaseService.createPurchaseOrder(manager, supplier.getSupplierID(),
                        LocalDate.now(), items, false));
        assertTrue(ex.getMessage().contains("Duplicate product"));
    }

    @Test
    @DisplayName("FR8: Non-positive quantity or negative price is rejected")
    void invalidQuantityOrPriceRejected() {
        List<PurchaseOrderItem> zeroQty = List.of(
                new PurchaseOrderItem(0, 0, new BigDecimal("100.00"), 0, p1));
        assertThrows(BusinessException.class, () ->
                purchaseService.createPurchaseOrder(manager, supplier.getSupplierID(),
                        LocalDate.now(), zeroQty, false));

        List<PurchaseOrderItem> negPrice = List.of(
                new PurchaseOrderItem(0, 5, new BigDecimal("-10.00"), 0, p1));
        assertThrows(BusinessException.class, () ->
                purchaseService.createPurchaseOrder(manager, supplier.getSupplierID(),
                        LocalDate.now(), negPrice, false));
    }

    @Test
    @DisplayName("FR10: Marking a PREPARED order as ORDERED updates status")
    void markOrderedTransitionsStatus() {
        List<PurchaseOrderItem> items = List.of(
                new PurchaseOrderItem(0, 10, new BigDecimal("1280.00"), 0, p1));
        PurchaseOrder order = purchaseService.createPurchaseOrder(manager, supplier.getSupplierID(),
                LocalDate.now(), items, false);
        assertEquals(PurchaseOrderStatus.PREPARED, order.getStatus());

        purchaseService.markOrdered(manager, order.getPoNumber());
        PurchaseOrder updated = purchaseService.getOrder(order.getPoNumber());
        assertEquals(PurchaseOrderStatus.ORDERED, updated.getStatus());
    }

    @Test
    @DisplayName("FR10: Cancelling an order before any receipts transitions status to CANCELLED")
    void cancelOrderBeforeReceipts() {
        List<PurchaseOrderItem> items = List.of(
                new PurchaseOrderItem(0, 10, new BigDecimal("1280.00"), 0, p1));
        PurchaseOrder order = purchaseService.createPurchaseOrder(manager, supplier.getSupplierID(),
                LocalDate.now(), items, true);
        assertEquals(PurchaseOrderStatus.ORDERED, order.getStatus());

        purchaseService.cancelOrder(manager, order.getPoNumber());
        PurchaseOrder updated = purchaseService.getOrder(order.getPoNumber());
        assertEquals(PurchaseOrderStatus.CANCELLED, updated.getStatus());
    }

    @Test
    @DisplayName("FR10: Cancelling an order that has already received items is rejected")
    void cancelOrderWithReceivedItemsRejected() {
        List<PurchaseOrderItem> items = List.of(
                new PurchaseOrderItem(0, 10, new BigDecimal("1280.00"), 0, p1));
        PurchaseOrder order = purchaseService.createPurchaseOrder(manager, supplier.getSupplierID(),
                LocalDate.now(), items, true);

        // Simulate receipt on item
        order.getItems().get(0).setReceivedQuantity(5);
        order.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                purchaseService.cancelOrder(manager, order.getPoNumber()));
        assertTrue(ex.getMessage().contains("Cannot cancel"));
    }
}
