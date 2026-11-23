package com.retailpos.model;

import com.retailpos.model.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A purchase order placed with one supplier. Contains one or more items. */
public class PurchaseOrder {

    private int poNumber;
    private LocalDate orderDate;
    private PurchaseOrderStatus status;
    private BigDecimal totalValue;
    private Supplier supplier;
    private List<PurchaseOrderItem> items;

    public PurchaseOrder() {
        this.items = new ArrayList<>();
    }

    public PurchaseOrder(int poNumber, LocalDate orderDate, PurchaseOrderStatus status,
                         BigDecimal totalValue, Supplier supplier, List<PurchaseOrderItem> items) {
        this.poNumber = poNumber;
        this.orderDate = orderDate;
        this.status = status;
        this.totalValue = totalValue;
        this.supplier = supplier;
        this.items = items;
    }

    public int getPoNumber() {
        return poNumber;
    }

    public void setPoNumber(int poNumber) {
        this.poNumber = poNumber;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseOrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public List<PurchaseOrderItem> getItems() {
        return items;
    }

    public void setItems(List<PurchaseOrderItem> items) {
        this.items = items;
    }

    /** Adds an item line to this purchase order and recalculates the total value. */
    public PurchaseOrderItem addItem(Product product, int quantity, BigDecimal price) {
        PurchaseOrderItem item = new PurchaseOrderItem(0, quantity, price, 0, product);
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
        calculateTotal();
        return item;
    }

    /** Calculates and sets totalValue as the sum of all item line totals. */
    public BigDecimal calculateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        if (items != null) {
            for (PurchaseOrderItem item : items) {
                total = total.add(item.lineTotal());
            }
        }
        this.totalValue = total;
        return total;
    }

    /** Marks this order as placed with the supplier (PREPARED -> ORDERED). */
    public void markOrdered() {
        if (this.status != PurchaseOrderStatus.PREPARED) {
            throw new IllegalStateException("Only a PREPARED order can be marked as ORDERED. Current: " + this.status);
        }
        this.status = PurchaseOrderStatus.ORDERED;
    }

    /** Updates status based on items received: PARTIALLY_RECEIVED or RECEIVED. */
    public void updateReceivingStatus() {
        if (items == null || items.isEmpty()) {
            return;
        }
        int totalOrdered = 0;
        int totalReceived = 0;
        boolean allFullyReceived = true;
        for (PurchaseOrderItem item : items) {
            totalOrdered += item.getOrderedQuantity();
            totalReceived += item.getReceivedQuantity();
            if (item.getReceivedQuantity() < item.getOrderedQuantity()) {
                allFullyReceived = false;
            }
        }
        if (totalReceived == 0) {
            // Keep existing status if nothing received yet
            return;
        }
        if (allFullyReceived) {
            this.status = PurchaseOrderStatus.RECEIVED;
        } else {
            this.status = PurchaseOrderStatus.PARTIALLY_RECEIVED;
        }
    }

    /** Cancels the purchase order. Only allowed before any items are received. */
    public void cancel() {
        if (this.status == PurchaseOrderStatus.CANCELLED) {
            return;
        }
        if (this.status == PurchaseOrderStatus.RECEIVED || this.status == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("Cannot cancel an order that has already received items.");
        }
        if (items != null) {
            for (PurchaseOrderItem item : items) {
                if (item.getReceivedQuantity() > 0) {
                    throw new IllegalStateException("Cannot cancel an order that has already received items.");
                }
            }
        }
        this.status = PurchaseOrderStatus.CANCELLED;
    }

    public int getTotalOrderedQuantity() {
        if (items == null) return 0;
        int sum = 0;
        for (PurchaseOrderItem item : items) {
            sum += item.getOrderedQuantity();
        }
        return sum;
    }

    public int getTotalReceivedQuantity() {
        if (items == null) return 0;
        int sum = 0;
        for (PurchaseOrderItem item : items) {
            sum += item.getReceivedQuantity();
        }
        return sum;
    }

    @Override
    public String toString() {
        return "PurchaseOrder{"
                + "poNumber=" + poNumber
                + ", orderDate=" + orderDate
                + ", status=" + status
                + ", totalValue=" + totalValue
                + ", supplier=" + (supplier == null ? null : supplier.getName())
                + ", items=" + (items == null ? 0 : items.size())
                + '}';
    }
}
