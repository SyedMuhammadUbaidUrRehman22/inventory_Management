package com.retailpos.service;

import com.retailpos.model.Cashier;
import com.retailpos.model.Category;
import com.retailpos.model.Manager;
import com.retailpos.model.Product;
import com.retailpos.model.StockAdjustment;
import com.retailpos.model.StockMovement;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.enums.AdjustmentType;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.testsupport.FakeProductDao;
import com.retailpos.testsupport.FakePurchaseOrderDao;
import com.retailpos.testsupport.FakeStockAdjustmentDao;
import com.retailpos.testsupport.FakeStockMovementDao;
import com.retailpos.testsupport.FakeStockReceiptDao;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stock adjustments in {@link StockService} (FR25–FR27). */
class StockAdjustmentTest {

    private static final LocalDate TODAY = LocalDate.now();

    private FakeProductDao productDao;
    private FakeStockMovementDao movementDao;
    private FakeStockAdjustmentDao adjustmentDao;
    private StockService service;
    private AccessControl accessControl;

    private final Manager manager = new Manager(1, "manager", "hash", null, true);
    private final Storekeeper storekeeper = new Storekeeper(2, "usman", "hash", 1, true);
    private final Cashier cashier = new Cashier(3, "bilal", "hash", 1, true);

    @BeforeEach
    void setUp() {
        accessControl = new AccessControl(new FakeUserDao());
        productDao = new FakeProductDao();
        movementDao = new FakeStockMovementDao();
        adjustmentDao = new FakeStockAdjustmentDao();
        service = new StockService(accessControl, new FakePurchaseOrderDao(), productDao, new FakeStockReceiptDao(),
                movementDao, adjustmentDao);

        Category groceries = new Category(1, "Groceries");
        productDao.insert(new Product("MILK", "Milk 1L", "carton", new BigDecimal("280.00"), 5, "ACTIVE", 20, groceries));
        productDao.insert(new Product("TEA", "Tea 500g", "pack", new BigDecimal("950.00"), 2, "ACTIVE", 4, groceries));
        productDao.insert(new Product("OLD", "Old Soap", "pc", new BigDecimal("50.00"), 0, "DISCONTINUED", 3, groceries));
    }

    private int stock(String code) {
        return productDao.findByCode(code).orElseThrow().getCurrentStockQuantity();
    }

    @Test
    @DisplayName("FR30: only a storekeeper records adjustments and sees their history")
    void onlyStorekeeper() {
        assertThrows(BusinessException.class, () -> service.recordAdjustment(manager, "MILK", AdjustmentType.DAMAGED, 1, TODAY, "x"));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(cashier, "MILK", AdjustmentType.DAMAGED, 1, TODAY, "x"));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(null, "MILK", AdjustmentType.DAMAGED, 1, TODAY, "x"));
        assertThrows(BusinessException.class, () -> service.getAdjustmentHistory(manager, null));
        assertThrows(BusinessException.class, () -> service.getAdjustmentHistory(cashier, null));
        assertEquals(0, adjustmentDao.count());
        assertEquals(20, stock("MILK"));
    }

    @Test
    @DisplayName("FR25, FR26: an adjustment is recorded, reduces stock and creates one ADJUSTMENT movement")
    void recordsAdjustment() {
        StockAdjustment a = service.recordAdjustment(storekeeper, "MILK", AdjustmentType.EXPIRED, 6, TODAY, "  Past date ");

        assertEquals(1, a.getAdjustmentID());
        assertEquals(AdjustmentType.EXPIRED, a.getAdjustmentType());
        assertEquals(6, a.getQuantity());
        assertEquals(TODAY, a.getDate());
        assertEquals("Past date", a.getReason());
        assertEquals(storekeeper.getUserID(), a.getRecordedBy().getUserID());
        assertEquals("MILK", a.getProduct().getProductCode());

        assertEquals(14, stock("MILK"));
        List<StockMovement> moves = movementDao.all();
        assertEquals(1, moves.size());
        StockMovement m = moves.get(0);
        assertEquals(StockMovementType.ADJUSTMENT, m.getMovementType());
        assertEquals(-6, m.getQuantityChange());
        assertEquals(14, m.getBalanceAfter());
        assertEquals("ADJ-00001", m.getSourceReference());
        assertEquals(storekeeper.getUserID(), m.getResponsibleUser().getUserID());
    }

    @Test
    @DisplayName("FR25: cannot remove more than the stock on hand; removing all of it is allowed")
    void cannotGoBelowZero() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.recordAdjustment(storekeeper, "TEA", AdjustmentType.MISSING, 5, TODAY, "Stock count"));
        assertTrue(e.getMessage().contains("only 4 in stock"), e.getMessage());
        assertEquals(0, adjustmentDao.count());
        assertTrue(movementDao.all().isEmpty());

        service.recordAdjustment(storekeeper, "TEA", AdjustmentType.MISSING, 4, TODAY, "Stock count");
        assertEquals(0, stock("TEA"));
        assertThrows(BusinessException.class,
                () -> service.recordAdjustment(storekeeper, "TEA", AdjustmentType.MISSING, 1, TODAY, "Again"));
    }

    @Test
    @DisplayName("FR26: product, type, quantity, date and reason are validated")
    void validation() {
        AdjustmentType d = AdjustmentType.DAMAGED;
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, "MILK", d, 0, TODAY, "x"));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, "MILK", d, -2, TODAY, "x"));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, "MILK", null, 1, TODAY, "x"));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, "MILK", d, 1, null, "x"));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, "MILK", d, 1, TODAY.plusDays(1), "x"));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, "MILK", d, 1, TODAY, " "));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, "MILK", d, 1, TODAY, null));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, "MILK", d, 1, TODAY, "x".repeat(256)));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, "NOPE", d, 1, TODAY, "x"));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, " ", d, 1, TODAY, "x"));
        assertThrows(BusinessException.class, () -> service.recordAdjustment(storekeeper, null, d, 1, TODAY, "x"));
        assertEquals(0, adjustmentDao.count());
        assertEquals(20, stock("MILK"));
    }

    @Test
    @DisplayName("A discontinued product's remaining stock can still be written off; a past date is allowed")
    void discontinuedAndPastDate() {
        service.recordAdjustment(storekeeper, "OLD", AdjustmentType.DAMAGED, 3, TODAY.minusDays(3), "Water damage");
        assertEquals(0, stock("OLD"));
    }

    @Test
    @DisplayName("FR27: history is newest first and can be filtered by product")
    void history() {
        service.recordAdjustment(storekeeper, "MILK", AdjustmentType.EXPIRED, 4, TODAY.minusDays(2), "Past date");
        service.recordAdjustment(storekeeper, "TEA", AdjustmentType.MISSING, 1, TODAY.minusDays(1), "Shelf audit");
        service.recordAdjustment(storekeeper, "MILK", AdjustmentType.DAMAGED, 2, TODAY, "Leaking");

        List<StockAdjustment> all = service.getAdjustmentHistory(storekeeper, null);
        assertEquals(3, all.size());
        assertEquals("Leaking", all.get(0).getReason());
        assertEquals("Past date", all.get(2).getReason());

        List<StockAdjustment> milk = service.getAdjustmentHistory(storekeeper, "MILK");
        assertEquals(2, milk.size());
        assertTrue(milk.stream().allMatch(a -> a.getProduct().getProductCode().equals("MILK")));
        assertEquals(3, service.getAdjustmentHistory(storekeeper, " ").size(), "blank means all products");
        assertEquals(14, stock("MILK"));
    }

    @Test
    @DisplayName("Without an adjustment DAO the feature reports itself unavailable")
    void notAvailableWithoutDao() {
        StockService receiptsOnly = new StockService(accessControl, new FakePurchaseOrderDao(), productDao,
                new FakeStockReceiptDao(), movementDao);
        assertThrows(BusinessException.class,
                () -> receiptsOnly.recordAdjustment(storekeeper, "MILK", AdjustmentType.DAMAGED, 1, TODAY, "x"));
        assertThrows(BusinessException.class, () -> receiptsOnly.getAdjustmentHistory(storekeeper, null));
    }
}
