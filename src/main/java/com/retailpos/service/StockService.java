package com.retailpos.service;

import com.retailpos.dao.ProductDao;
import com.retailpos.dao.PurchaseOrderDao;
import com.retailpos.dao.StockAdjustmentDao;
import com.retailpos.dao.StockMovementDao;
import com.retailpos.dao.StockReceiptDao;
import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.StockAdjustment;
import com.retailpos.model.StockMovement;
import com.retailpos.model.StockReceipt;
import com.retailpos.model.User;
import com.retailpos.model.enums.AdjustmentType;
import com.retailpos.model.enums.PurchaseOrderStatus;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.util.BusinessException;
import com.retailpos.util.DBConnection;
import com.retailpos.util.FormatUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service managing stock receipts (FR11–FR13), stock adjustments (FR25–FR27) and their audit movements.
 * Receipts need {@link AccessControl#RECORD_STOCK_RECEIPT} and adjustments need
 * {@link AccessControl#RECORD_STOCK_ADJUSTMENT} (both Storekeeper role).
 */
public class StockService {

    /** Longest adjustment reason the database can store. */
    public static final int REASON_MAX = 255;

    private final AccessControl accessControl;
    private final PurchaseOrderDao purchaseOrderDao;
    private final ProductDao productDao;
    private final StockReceiptDao stockReceiptDao;
    private final StockMovementDao stockMovementDao;
    private final StockAdjustmentDao stockAdjustmentDao;

    /** Stock adjustments (FR25–FR27) are not available through this constructor. */
    public StockService(AccessControl accessControl, PurchaseOrderDao purchaseOrderDao,
                        ProductDao productDao, StockReceiptDao stockReceiptDao,
                        StockMovementDao stockMovementDao) {
        this(accessControl, purchaseOrderDao, productDao, stockReceiptDao, stockMovementDao, null);
    }

    public StockService(AccessControl accessControl, PurchaseOrderDao purchaseOrderDao,
                        ProductDao productDao, StockReceiptDao stockReceiptDao,
                        StockMovementDao stockMovementDao, StockAdjustmentDao stockAdjustmentDao) {
        this.accessControl = accessControl;
        this.purchaseOrderDao = purchaseOrderDao;
        this.productDao = productDao;
        this.stockReceiptDao = stockReceiptDao;
        this.stockMovementDao = stockMovementDao;
        this.stockAdjustmentDao = stockAdjustmentDao;
    }

    /**
     * FR11, FR12, FR13: Records received quantities against a purchase order.
     * <p>
     * Business rules:
     * - Only Storekeeper can record receipt (FR30).
     * - Only against ORDERED or PARTIALLY_RECEIVED orders.
     * - Over-receipt is blocked: receiveNow <= remainingQuantity.
     * - Stock increases ONLY by the quantity actually received (FR12).
     * - Every stock change creates exactly one StockMovement and updates current_stock_quantity
     *   in the SAME database transaction (CLAUDE.md lines 48-49).
     * - Automatically updates order status to PARTIALLY_RECEIVED or RECEIVED (FR10).
     *
     * @param user               the storekeeper user
     * @param poNumber           purchase order number
     * @param receiptDate        date goods were received (cannot be null or future)
     * @param quantitiesToReceive map of purchaseOrderItemId -> quantity received in this delivery
     * @return list of newly created {@link StockReceipt} records
     */
    public List<StockReceipt> recordOrderReceipt(User user, int poNumber, LocalDate receiptDate,
                                                Map<Integer, Integer> quantitiesToReceive) {
        accessControl.requirePermission(user, AccessControl.RECORD_STOCK_RECEIPT);

        if (receiptDate == null) {
            throw new BusinessException("Receipt date is required.");
        }
        if (receiptDate.isAfter(LocalDate.now())) {
            throw new BusinessException("Receipt date cannot be in the future.");
        }

        PurchaseOrder order = purchaseOrderDao.findByNumber(poNumber)
                .orElseThrow(() -> new BusinessException("Purchase order PO-" + poNumber + " not found."));

        if (order.getStatus() != PurchaseOrderStatus.ORDERED && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException("Cannot receive stock against order PO-" + poNumber
                    + " with status " + order.getStatus() + ". Must be ORDERED or PARTIALLY_RECEIVED.");
        }

        if (quantitiesToReceive == null || quantitiesToReceive.isEmpty()) {
            throw new BusinessException("No receipt quantities provided.");
        }

        // Validate quantities first
        int totalReceivedNow = 0;
        List<PurchaseOrderItem> items = order.getItems();
        for (PurchaseOrderItem item : items) {
            int toReceive = quantitiesToReceive.getOrDefault(item.getPurchaseOrderItemID(), 0);
            if (toReceive < 0) {
                throw new BusinessException("Received quantity cannot be negative for " + item.getProduct().getName() + ".");
            }
            int remaining = item.getRemainingQuantity();
            if (toReceive > remaining) {
                throw new BusinessException("Cannot receive " + toReceive + " units of " + item.getProduct().getName()
                        + ": only " + remaining + " remaining to be received.");
            }
            totalReceivedNow += toReceive;
        }

        if (totalReceivedNow <= 0) {
            throw new BusinessException("Enter at least 1 unit to receive across the order lines.");
        }

        // Execute in atomic transaction: commit both StockMovement and Product stock, or roll back both
        return DBConnection.inTransaction(() -> {
            List<StockReceipt> receipts = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (PurchaseOrderItem item : items) {
                int qty = quantitiesToReceive.getOrDefault(item.getPurchaseOrderItemID(), 0);
                if (qty <= 0) {
                    continue;
                }

                // 1. Create StockReceipt
                StockReceipt receipt = new StockReceipt(0, receiptDate, qty, item);
                stockReceiptDao.insert(receipt);
                receipts.add(receipt);

                // 2. Update PurchaseOrderItem received quantity
                int newReceivedTotal = item.getReceivedQuantity() + qty;
                item.setReceivedQuantity(newReceivedTotal);
                purchaseOrderDao.updateItemReceivedQuantity(item.getPurchaseOrderItemID(), newReceivedTotal);

                // 3. Update Product stock and create StockMovement
                String productCode = item.getProduct().getProductCode();
                Product product = productDao.findByCode(productCode)
                        .orElseThrow(() -> new BusinessException("Product " + productCode + " not found."));

                int balanceAfter = product.getCurrentStockQuantity() + qty;
                productDao.updateStock(productCode, balanceAfter);

                StockMovement movement = new StockMovement(
                        0,
                        StockMovementType.RECEIPT,
                        qty,
                        now,
                        "PO-" + poNumber + "/RC-" + receipt.getReceiptID(),
                        balanceAfter,
                        user,
                        product);
                stockMovementDao.insert(movement);
            }

            // 4. Update Purchase Order status
            order.updateReceivingStatus();
            purchaseOrderDao.updateStatus(poNumber, order.getStatus());

            return receipts;
        });
    }

    /** Returns all receipts recorded for a purchase order. */
    public List<StockReceipt> getReceiptsForOrder(int poNumber) {
        return stockReceiptDao.findByPoNumber(poNumber);
    }

    /** Returns recent stock receipts. */
    public List<StockReceipt> getRecentReceipts(int limit) {
        return stockReceiptDao.findRecent(limit);
    }

    /** Returns stock movements for a specific product. */
    public List<StockMovement> getMovementsForProduct(String productCode) {
        return stockMovementDao.findByProduct(productCode);
    }

    /** Returns recent stock movements across all products. */
    public List<StockMovement> getRecentMovements(int limit) {
        return stockMovementDao.findRecent(limit);
    }

    // =====================================================================
    // FR25–FR27: Stock adjustments
    // =====================================================================

    /**
     * FR25, FR26: Records a stock adjustment for damaged, expired or missing goods and reduces the
     * product's stock by its quantity.
     * <ul>
     *   <li>Quantity must be at least 1 and cannot exceed the stock on hand.</li>
     *   <li>Type, date (not in the future) and reason are required.</li>
     *   <li>The adjustment, the stock decrease and one ADJUSTMENT stock movement are saved in the
     *       same transaction, so all of it is saved or none of it is.</li>
     * </ul>
     *
     * @return the saved adjustment, with its generated ID
     */
    public StockAdjustment recordAdjustment(User user, String productCode, AdjustmentType type, int quantity,
                                            LocalDate date, String reason) {
        accessControl.requirePermission(user, AccessControl.RECORD_STOCK_ADJUSTMENT);
        requireAdjustments();
        if (productCode == null || productCode.isBlank()) {
            throw new BusinessException("Select a product.");
        }
        if (type == null) {
            throw new BusinessException("Select the adjustment type (damaged, expired or missing).");
        }
        if (quantity < 1) {
            throw new BusinessException("Quantity must be at least 1.");
        }
        if (date == null) {
            throw new BusinessException("Adjustment date is required.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new BusinessException("Adjustment date cannot be in the future.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new BusinessException("Describe what happened in the reason.");
        }
        if (cleanReason.length() > REASON_MAX) {
            throw new BusinessException("Reason can be at most " + REASON_MAX + " characters.");
        }

        return DBConnection.inTransaction(() -> {
            // Stock is read inside the transaction, so the check uses the quantity as it is now.
            Product product = productDao.findByCode(productCode)
                    .orElseThrow(() -> new BusinessException("Product " + productCode + " not found."));
            int onHand = product.getCurrentStockQuantity();
            if (quantity > onHand) {
                throw new BusinessException("Cannot remove " + quantity + " units of " + product.getName()
                        + ": only " + onHand + " in stock.");
            }

            StockAdjustment adjustment = new StockAdjustment(0, type, quantity, date, cleanReason, user, product);
            stockAdjustmentDao.insert(adjustment);

            int balanceAfter = onHand - quantity;
            productDao.updateStock(productCode, balanceAfter);
            stockMovementDao.insert(new StockMovement(0, StockMovementType.ADJUSTMENT, -quantity,
                    LocalDateTime.now(), FormatUtil.adjustmentId(adjustment.getAdjustmentID()),
                    balanceAfter, user, product));
            return adjustment;
        });
    }

    /**
     * FR27: Adjustment history, newest first.
     *
     * @param productCode only this product's adjustments, or null for all products
     */
    public List<StockAdjustment> getAdjustmentHistory(User user, String productCode) {
        accessControl.requirePermission(user, AccessControl.RECORD_STOCK_ADJUSTMENT);
        requireAdjustments();
        return stockAdjustmentDao.findHistory(productCode == null || productCode.isBlank() ? null : productCode);
    }

    private void requireAdjustments() {
        if (stockAdjustmentDao == null) {
            throw new BusinessException("Stock adjustments are not available.");
        }
    }
}
