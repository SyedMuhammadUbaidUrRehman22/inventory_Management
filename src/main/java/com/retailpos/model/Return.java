package com.retailpos.model;

import java.time.LocalDate;

/** A customer return recorded against one sale item of a completed sale. */
public class Return {

    private int returnID;
    private LocalDate returnDate;
    private int quantity;
    private String reason;
    private boolean isAccepted;
    private boolean isReusable;
    private SaleItem saleItem;

    public Return() {
    }

    public Return(int returnID, LocalDate returnDate, int quantity, String reason,
                  boolean isAccepted, boolean isReusable, SaleItem saleItem) {
        this.returnID = returnID;
        this.returnDate = returnDate;
        this.quantity = quantity;
        this.reason = reason;
        this.isAccepted = isAccepted;
        this.isReusable = isReusable;
        this.saleItem = saleItem;
    }

    public int getReturnID() {
        return returnID;
    }

    public void setReturnID(int returnID) {
        this.returnID = returnID;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isAccepted() {
        return isAccepted;
    }

    public void setAccepted(boolean isAccepted) {
        this.isAccepted = isAccepted;
    }

    public boolean isReusable() {
        return isReusable;
    }

    public void setReusable(boolean isReusable) {
        this.isReusable = isReusable;
    }

    public SaleItem getSaleItem() {
        return saleItem;
    }

    public void setSaleItem(SaleItem saleItem) {
        this.saleItem = saleItem;
    }

    @Override
    public String toString() {
        return "Return{"
                + "returnID=" + returnID
                + ", returnDate=" + returnDate
                + ", quantity=" + quantity
                + ", reason='" + reason + '\''
                + ", isAccepted=" + isAccepted
                + ", isReusable=" + isReusable
                + ", saleItem=" + (saleItem == null ? null : saleItem.getSaleItemID())
                + '}';
    }
}
