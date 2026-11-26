package com.retailpos.model;

import com.retailpos.model.enums.SaleStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A sale made by a cashier. Contains one or more sale items. */
public class Sale {

    private int saleID;
    private LocalDateTime dateTime;
    private SaleStatus status;
    private BigDecimal subtotal;
    private BigDecimal discount;
    private BigDecimal finalAmount;
    private Cashier cashier;
    private List<SaleItem> items;

    public Sale() {
        this.items = new ArrayList<>();
    }

    public Sale(int saleID, LocalDateTime dateTime, SaleStatus status, BigDecimal subtotal,
                BigDecimal discount, BigDecimal finalAmount, Cashier cashier, List<SaleItem> items) {
        this.saleID = saleID;
        this.dateTime = dateTime;
        this.status = status;
        this.subtotal = subtotal;
        this.discount = discount;
        this.finalAmount = finalAmount;
        this.cashier = cashier;
        this.items = items;
    }

    public int getSaleID() {
        return saleID;
    }

    public void setSaleID(int saleID) {
        this.saleID = saleID;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public void setStatus(SaleStatus status) {
        this.status = status;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(BigDecimal finalAmount) {
        this.finalAmount = finalAmount;
    }

    public Cashier getCashier() {
        return cashier;
    }

    public void setCashier(Cashier cashier) {
        this.cashier = cashier;
    }

    public List<SaleItem> getItems() {
        return items;
    }

    public void setItems(List<SaleItem> items) {
        this.items = items;
    }

    /** Returns the line for this product code, or null if the product is not on the sale. */
    public SaleItem findItem(String productCode) {
        for (SaleItem item : items) {
            if (item.getProduct() != null && item.getProduct().getProductCode().equals(productCode)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Adds a line (FR15). A product can appear only once on a sale, so if it is already there
     * the quantities are added together and the existing line keeps its selling price.
     */
    public void addItem(SaleItem item) {
        SaleItem existing = findItem(item.getProduct().getProductCode());
        if (existing == null) {
            items.add(item);
        } else {
            existing.setQuantity(existing.getQuantity() + item.getQuantity());
            existing.recalculateLineTotal();
        }
        recalculate();
    }

    /** Removes the line for this product code; returns false if it was not on the sale. */
    public boolean removeItem(String productCode) {
        SaleItem item = findItem(productCode);
        if (item == null) {
            return false;
        }
        items.remove(item);
        recalculate();
        return true;
    }

    /** FR18: subtotal = sum of the line totals. */
    public BigDecimal calculateSubtotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (SaleItem item : items) {
            total = total.add(item.getLineTotal());
        }
        return total;
    }

    /** FR18: final amount = subtotal - fixed discount (never below zero). */
    public BigDecimal calculateFinalAmount() {
        BigDecimal discountValue = discount == null ? BigDecimal.ZERO : discount;
        return calculateSubtotal().subtract(discountValue).max(BigDecimal.ZERO);
    }

    /** Refreshes subtotal and final amount from the lines and the discount. */
    public void recalculate() {
        if (discount == null) {
            discount = BigDecimal.ZERO;
        }
        subtotal = calculateSubtotal();
        finalAmount = calculateFinalAmount();
    }

    /** Units on all lines together. */
    public int getTotalQuantity() {
        int total = 0;
        for (SaleItem item : items) {
            total += item.getQuantity();
        }
        return total;
    }

    @Override
    public String toString() {
        return "Sale{"
                + "saleID=" + saleID
                + ", dateTime=" + dateTime
                + ", status=" + status
                + ", subtotal=" + subtotal
                + ", discount=" + discount
                + ", finalAmount=" + finalAmount
                + ", cashier=" + (cashier == null ? null : cashier.getUsername())
                + ", items=" + (items == null ? 0 : items.size())
                + '}';
    }
}
