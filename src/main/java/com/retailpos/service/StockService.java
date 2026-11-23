package com.retailpos.service;

import com.retailpos.dao.ProductDao;
import com.retailpos.dao.PurchaseOrderDao;
import com.retailpos.dao.StockMovementDao;
import com.retailpos.dao.StockReceiptDao;
import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.StockMovement;
import com.retailpos.model.StockReceipt;
import com.retailpos.model.User;
import com.retailpos.model.enums.PurchaseOrderStatus;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.util.BusinessException;
import com.retailpos.util.DBConnection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service managing stock receipts, inventory increases, and audit movements (FR11–FR13).
 * Restricted to users with {@link AccessControl#RECORD_STOCK_RECEIPT} (Storekeeper role).
 */
public class StockService {

    private final AccessControl accessControl;
    private final PurchaseOrderDao purchaseOrderDao;
    private final ProductDao productDao;
    private final StockReceiptDao stockReceiptDao;
    private final StockMovementDao stockMovementDao;

    public StockService(AccessControl accessControl, PurchaseOrderDao purchaseOrderDao,
                        ProductDao productDao, StockReceiptDao stockReceiptDao,
                        StockMovementDao stockMovementDao) {
        this.accessControl = accessControl;
        this.purchaseOrderDao = purchaseOrderDao;
        this.productDao = productDao;
        this.stockReceiptDao = stockReceiptDao;
        this.stockMovementDao = stockMovementDao;
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
}
