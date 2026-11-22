package com.retailpos.model;

import java.time.LocalDate;

/** A delivery of goods recorded against one purchase order item. */
public class StockReceipt {

    private int receiptID;
    private LocalDate receiptDate;
    private int quantityReceived;
    private PurchaseOrderItem purchaseOrderItem;

    public StockReceipt() {
    }

    public StockReceipt(int receiptID, LocalDate receiptDate, int quantityReceived,
                        PurchaseOrderItem purchaseOrderItem) {
        this.receiptID = receiptID;
        this.receiptDate = receiptDate;
        this.quantityReceived = quantityReceived;
        this.purchaseOrderItem = purchaseOrderItem;
    }

    public int getReceiptID() {
        return receiptID;
    }

    public void setReceiptID(int receiptID) {
        this.receiptID = receiptID;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public void setReceiptDate(LocalDate receiptDate) {
        this.receiptDate = receiptDate;
    }

    public int getQuantityReceived() {
        return quantityReceived;
    }

    public void setQuantityReceived(int quantityReceived) {
        this.quantityReceived = quantityReceived;
    }

    public PurchaseOrderItem getPurchaseOrderItem() {
        return purchaseOrderItem;
    }

    public void setPurchaseOrderItem(PurchaseOrderItem purchaseOrderItem) {
        this.purchaseOrderItem = purchaseOrderItem;
    }

    @Override
    public String toString() {
        return "StockReceipt{"
                + "receiptID=" + receiptID
                + ", receiptDate=" + receiptDate
                + ", quantityReceived=" + quantityReceived
                + ", purchaseOrderItem="
                + (purchaseOrderItem == null ? null : purchaseOrderItem.getPurchaseOrderItemID())
                + '}';
    }
}
