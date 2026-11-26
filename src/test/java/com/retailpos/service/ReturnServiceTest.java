package com.retailpos.service;

import com.retailpos.model.Cashier;
import com.retailpos.model.Category;
import com.retailpos.model.Manager;
import com.retailpos.model.Product;
import com.retailpos.model.Return;
import com.retailpos.model.Sale;
import com.retailpos.model.SaleItem;
import com.retailpos.model.StockMovement;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.enums.SaleStatus;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.service.ReturnService.SaleForReturn;
import com.retailpos.testsupport.FakeProductDao;
import com.retailpos.testsupport.FakeReturnDao;
import com.retailpos.testsupport.FakeSaleDao;
import com.retailpos.testsupport.FakeSalesReceiptDao;
import com.retailpos.testsupport.FakeStockMovementDao;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReturnServiceTest {

    private static final LocalDate TODAY = LocalDate.now();

    private FakeProductDao productDao;
    private FakeSaleDao saleDao;
    private FakeReturnDao returnDao;
    private FakeStockMovementDao movementDao;
    private SaleService sales;
    private ReturnService service;

    private final Cashier cashier = new Cashier(3, "bilal", "hash", 1, true);
    private final Manager manager = new Manager(1, "manager", "hash", null, true);
    private final Storekeeper storekeeper = new Storekeeper(2, "store", "hash", 1, true);

    /** Sale with RICE x2 and OIL x3; stock after the sale is RICE 6, OIL 6. */
    private Sale sale;
    private int riceItem;
    private int oilItem;

    @BeforeEach
    void setUp() {
        AccessControl accessControl = new AccessControl(new FakeUserDao());
        productDao = new FakeProductDao();
        saleDao = new FakeSaleDao();
        returnDao = new FakeReturnDao(saleDao);
        movementDao = new FakeStockMovementDao();
        sales = new SaleService(accessControl, productDao, saleDao, new FakeSalesReceiptDao(), movementDao);
        service = new ReturnService(accessControl, saleDao, returnDao, productDao, movementDao);

        Category groceries = new Category(1, "Groceries");
        productDao.insert(new Product("RICE", "Basmati Rice 5kg", "bag", new BigDecimal("1450.00"), 2, "ACTIVE", 8, groceries));
        productDao.insert(new Product("OIL", "Cooking Oil 1L", "bottle", new BigDecimal("520.00"), 2, "ACTIVE", 9, groceries));

        sale = sales.createSale(cashier);
        sales.addItem(cashier, sale, "RICE", 2);
        sales.addItem(cashier, sale, "OIL", 3);
        sales.finalizeSale(cashier, sale);
        riceItem = sale.findItem("RICE").getSaleItemID();
        oilItem = sale.findItem("OIL").getSaleItemID();
    }

    private int stock(String code) {
        return productDao.findByCode(code).orElseThrow().getCurrentStockQuantity();
    }

    private List<StockMovement> returnMovements() {
        return movementDao.all().stream().filter(m -> m.getMovementType() == StockMovementType.RETURN).toList();
    }

    private List<Return> recordRice(int qty, boolean accepted, boolean reusable) {
        return service.recordReturn(cashier, sale.getSaleID(), Map.of(riceItem, qty), "Packaging damaged",
                TODAY, accepted, reusable);
    }

    @Test
    @DisplayName("FR30: only a cashier can record returns")
    void onlyCashierCanRecordReturns() {
        assertThrows(BusinessException.class, () -> service.findSaleForReturn(manager, "SL-00001"));
        assertThrows(BusinessException.class, () -> service.findSaleForReturn(storekeeper, "SL-00001"));
        assertThrows(BusinessException.class, () -> service.recordReturn(manager, sale.getSaleID(),
                Map.of(riceItem, 1), "x", TODAY, true, true));
        assertThrows(BusinessException.class, () -> service.recordReturn(null, sale.getSaleID(),
                Map.of(riceItem, 1), "x", TODAY, true, true));
        assertEquals(0, returnDao.count());
    }

    @Test
    @DisplayName("FR22: the sale is found by its receipt number and shows what can still be returned")
    void findSaleForReturn() {
        SaleForReturn found = service.findSaleForReturn(cashier, "SL-00001");
        assertEquals(sale.getSaleID(), found.sale().getSaleID());
        assertEquals(2, found.lines().size());
        assertEquals(2, found.lines().get(0).returnableQuantity());
        assertEquals(0, found.lines().get(0).returnedQuantity());
        assertTrue(found.returns().isEmpty());

        assertEquals(sale.getSaleID(), service.findSaleForReturn(cashier, " sl-1 ").sale().getSaleID());
        assertEquals(sale.getSaleID(), service.findSaleForReturn(cashier, "1").sale().getSaleID());
        assertThrows(BusinessException.class, () -> service.findSaleForReturn(cashier, "SL-99999"));
        assertThrows(BusinessException.class, () -> service.findSaleForReturn(cashier, "abc"));
        assertThrows(BusinessException.class, () -> service.findSaleForReturn(cashier, ""));
    }

    @Test
    @DisplayName("FR22: returns only against a completed sale")
    void onlyCompletedSale() {
        Sale draft = new Sale(0, LocalDateTime.now(), SaleStatus.DRAFT, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, cashier, new java.util.ArrayList<>(List.of(
                new SaleItem(0, 1, new BigDecimal("520.00"), new BigDecimal("520.00"),
                        productDao.findByCode("OIL").orElseThrow()))));
        saleDao.insert(draft);
        int draftItem = draft.getItems().get(0).getSaleItemID();

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.findSaleForReturn(cashier, String.valueOf(draft.getSaleID())));
        assertTrue(e.getMessage().contains("completed sale"));
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, draft.getSaleID(),
                Map.of(draftItem, 1), "x", TODAY, true, true));
        assertEquals(0, returnDao.count());
    }

    @Test
    @DisplayName("FR23, FR24: an accepted reusable return is recorded and put back into stock with one RETURN movement")
    void acceptedReusableRestocks() {
        List<Return> recorded = recordRice(1, true, true);

        assertEquals(1, recorded.size());
        Return r = recorded.get(0);
        assertEquals(1, r.getQuantity());
        assertEquals("Packaging damaged", r.getReason());
        assertEquals(TODAY, r.getReturnDate());
        assertTrue(r.isAccepted());
        assertTrue(r.isReusable());
        assertEquals(riceItem, r.getSaleItem().getSaleItemID());

        assertEquals(7, stock("RICE"), "6 after the sale + 1 returned");
        List<StockMovement> moves = returnMovements();
        assertEquals(1, moves.size());
        assertEquals(1, moves.get(0).getQuantityChange());
        assertEquals(7, moves.get(0).getBalanceAfter());
        assertEquals("SL-00001/RT-00001", moves.get(0).getSourceReference());
        assertEquals(cashier.getUserID(), moves.get(0).getResponsibleUser().getUserID());
    }

    @Test
    @DisplayName("FR24: an accepted return that is not reusable is recorded but does not change stock")
    void acceptedNotReusableDoesNotRestock() {
        recordRice(2, true, false);
        assertEquals(1, returnDao.count());
        assertEquals(6, stock("RICE"));
        assertTrue(returnMovements().isEmpty());
        // ...but it does count as returned
        assertEquals(0, service.findSaleForReturn(cashier, "1").lines().get(0).returnableQuantity());
    }

    @Test
    @DisplayName("FR23: a rejected return is recorded, does not change stock, and does not count as returned")
    void rejectedReturn() {
        recordRice(2, false, false);
        assertEquals(1, returnDao.count());
        assertEquals(6, stock("RICE"));
        assertTrue(returnMovements().isEmpty());

        SaleForReturn after = service.findSaleForReturn(cashier, "1");
        assertEquals(2, after.lines().get(0).returnableQuantity(), "the customer kept the goods");
        assertEquals(1, after.returns().size());
    }

    @Test
    @DisplayName("Only an accepted return can be marked reusable")
    void reusableNeedsAccepted() {
        assertThrows(BusinessException.class, () -> recordRice(1, false, true));
        assertEquals(0, returnDao.count());
    }

    @Test
    @DisplayName("Return quantity cannot exceed the quantity sold minus what was already returned")
    void cannotReturnMoreThanSold() {
        assertThrows(BusinessException.class, () -> recordRice(3, true, true));
        recordRice(1, true, true);
        BusinessException e = assertThrows(BusinessException.class, () -> recordRice(2, true, true));
        assertTrue(e.getMessage().contains("at most 1"), e.getMessage());
        recordRice(1, true, false);
        assertThrows(BusinessException.class, () -> recordRice(1, true, true));
        assertEquals(2, returnDao.count());
        assertEquals(7, stock("RICE"), "only the first, reusable, unit went back");
    }

    @Test
    @DisplayName("FR23: reason and a valid return date are required")
    void reasonAndDateRequired() {
        int id = sale.getSaleID();
        Map<Integer, Integer> one = Map.of(riceItem, 1);
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, id, one, "  ", TODAY, true, true));
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, id, one, null, TODAY, true, true));
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, id, one, "x".repeat(256), TODAY, true, true));
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, id, one, "x", null, true, true));
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, id, one, "x", TODAY.plusDays(1), true, true));
        BusinessException beforeSale = assertThrows(BusinessException.class,
                () -> service.recordReturn(cashier, id, one, "x", TODAY.minusDays(1), true, true));
        assertTrue(beforeSale.getMessage().contains("before the sale date"));
        assertEquals(0, returnDao.count());
        assertEquals(6, stock("RICE"));
    }

    @Test
    @DisplayName("Items must be on the sale; quantities must be positive; at least one unit is returned")
    void invalidQuantities() {
        int id = sale.getSaleID();
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, id, Map.of(), "x", TODAY, true, true));
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, id, null, "x", TODAY, true, true));
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, id, Map.of(riceItem, 0), "x", TODAY, true, true));
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, id, Map.of(riceItem, -1), "x", TODAY, true, true));
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, id, Map.of(999, 1), "x", TODAY, true, true));
        assertEquals(0, returnDao.count());
    }

    @Test
    @DisplayName("Several lines are returned together, one Return per line; one bad line records nothing")
    void multiLineReturn() {
        Map<Integer, Integer> tooMany = new LinkedHashMap<>();
        tooMany.put(riceItem, 1);
        tooMany.put(oilItem, 4);
        assertThrows(BusinessException.class, () -> service.recordReturn(cashier, sale.getSaleID(), tooMany,
                "Wrong item", TODAY, true, true));
        assertEquals(0, returnDao.count());
        assertEquals(6, stock("RICE"));

        Map<Integer, Integer> both = new LinkedHashMap<>();
        both.put(oilItem, 2);
        both.put(riceItem, 1);
        List<Return> recorded = service.recordReturn(cashier, sale.getSaleID(), both, "Wrong item", TODAY, true, true);
        assertEquals(2, recorded.size());
        assertEquals(riceItem, recorded.get(0).getSaleItem().getSaleItemID(), "in the order of the sale's lines");
        assertEquals(7, stock("RICE"));
        assertEquals(8, stock("OIL"));
        assertEquals(2, returnMovements().size());
        assertFalse(service.findSaleForReturn(cashier, "1").returns().isEmpty());
    }
}
