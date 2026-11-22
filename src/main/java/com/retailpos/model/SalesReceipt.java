package com.retailpos.model;

import java.time.LocalDateTime;

/** The receipt generated for a completed sale (at most one per sale). */
public class SalesReceipt {

    private int receiptID;
    private LocalDateTime issuedAt;
    private Sale sale;

    public SalesReceipt() {
    }

    public SalesReceipt(int receiptID, LocalDateTime issuedAt, Sale sale) {
        this.receiptID = receiptID;
        this.issuedAt = issuedAt;
        this.sale = sale;
    }

    public int getReceiptID() {
        return receiptID;
    }

    public void setReceiptID(int receiptID) {
        this.receiptID = receiptID;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public Sale getSale() {
        return sale;
    }

    public void setSale(Sale sale) {
        this.sale = sale;
    }

    @Override
    public String toString() {
        return "SalesReceipt{"
                + "receiptID=" + receiptID
                + ", issuedAt=" + issuedAt
                + ", sale=" + (sale == null ? null : sale.getSaleID())
                + '}';
    }
}
