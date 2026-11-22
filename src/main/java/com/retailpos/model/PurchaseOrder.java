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
