package com.retailpos.model;

import com.retailpos.model.enums.AdjustmentType;

import java.time.LocalDate;

/** A manual stock decrease (damaged, expired, missing) recorded by a user. */
public class StockAdjustment {

    private int adjustmentID;
    private AdjustmentType adjustmentType;
    private int quantity;
    private LocalDate date;
    private String reason;
    private User recordedBy;
    private Product product;

    public StockAdjustment() {
    }

    public StockAdjustment(int adjustmentID, AdjustmentType adjustmentType, int quantity, LocalDate date,
                           String reason, User recordedBy, Product product) {
        this.adjustmentID = adjustmentID;
        this.adjustmentType = adjustmentType;
        this.quantity = quantity;
        this.date = date;
        this.reason = reason;
        this.recordedBy = recordedBy;
        this.product = product;
    }

    public int getAdjustmentID() {
        return adjustmentID;
    }

    public void setAdjustmentID(int adjustmentID) {
        this.adjustmentID = adjustmentID;
    }

    public AdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(AdjustmentType adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public User getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(User recordedBy) {
        this.recordedBy = recordedBy;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    @Override
    public String toString() {
        return "StockAdjustment{"
                + "adjustmentID=" + adjustmentID
                + ", adjustmentType=" + adjustmentType
                + ", quantity=" + quantity
                + ", date=" + date
                + ", reason='" + reason + '\''
                + ", recordedBy=" + (recordedBy == null ? null : recordedBy.getUsername())
                + ", product=" + (product == null ? null : product.getProductCode())
                + '}';
    }
}
