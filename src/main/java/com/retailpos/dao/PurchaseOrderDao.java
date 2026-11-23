package com.retailpos.dao;

import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.enums.PurchaseOrderStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Data access for purchase orders and their lines. */
public interface PurchaseOrderDao {

    /** Finds a purchase order by PO number, including its supplier and lines with products. */
    Optional<PurchaseOrder> findByNumber(int poNumber);

    /** Returns all purchase orders ordered by PO number descending. */
    List<PurchaseOrder> findAll();

    /**
     * Searches purchase orders by criteria (all optional).
     *
     * @param supplierId filter by supplier, or null
     * @param status     filter by status, or null
     * @param fromDate   filter by order_date >= fromDate, or null
     * @param toDate     filter by order_date <= toDate, or null
     */
    List<PurchaseOrder> search(Integer supplierId, PurchaseOrderStatus status, LocalDate fromDate, LocalDate toDate);

    /** Returns purchase orders available to receive goods against (status ORDERED or PARTIALLY_RECEIVED). */
    List<PurchaseOrder> findReceivable();

    /**
     * Inserts a new purchase order and all its items in one transaction.
     * Sets the generated poNumber on the order and item IDs on its items.
     *
     * @return the generated poNumber
     */
    int insert(PurchaseOrder order);

    /** Updates the status of an existing purchase order. */
    void updateStatus(int poNumber, PurchaseOrderStatus status);

    /** Updates the received quantity of a specific purchase order item. */
    void updateItemReceivedQuantity(int purchaseOrderItemId, int newReceivedQuantity);

    /** Finds a single purchase order item by ID, including its product. */
    Optional<PurchaseOrderItem> findItemById(int purchaseOrderItemId);

    /** Returns all items for a given purchase order number. */
    List<PurchaseOrderItem> findItemsByPoNumber(int poNumber);
}
