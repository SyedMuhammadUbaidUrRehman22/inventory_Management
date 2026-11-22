package com.retailpos.model;

import java.math.BigDecimal;

/** One product line of a purchase order. */
public class PurchaseOrderItem {

    private int purchaseOrderItemID;
    private int orderedQuantity;
    private BigDecimal purchasePrice;
    private int receivedQuantity;
    private Product product;

    public PurchaseOrderItem() {
    }

    public PurchaseOrderItem(int purchaseOrderItemID, int orderedQuantity, BigDecimal purchasePrice,
                             int receivedQuantity, Product product) {
        this.purchaseOrderItemID = purchaseOrderItemID;
        this.orderedQuantity = orderedQuantity;
        this.purchasePrice = purchasePrice;
        this.receivedQuantity = receivedQuantity;
        this.product = product;
    }

    public int getPurchaseOrderItemID() {
        return purchaseOrderItemID;
    }

    public void setPurchaseOrderItemID(int purchaseOrderItemID) {
        this.purchaseOrderItemID = purchaseOrderItemID;
    }

    public int getOrderedQuantity() {
        return orderedQuantity;
    }

    public void setOrderedQuantity(int orderedQuantity) {
        this.orderedQuantity = orderedQuantity;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public int getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(int receivedQuantity) {
        this.receivedQuantity = receivedQuantity;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    @Override
    public String toString() {
        return "PurchaseOrderItem{"
                + "purchaseOrderItemID=" + purchaseOrderItemID
                + ", orderedQuantity=" + orderedQuantity
                + ", purchasePrice=" + purchasePrice
                + ", receivedQuantity=" + receivedQuantity
                + ", product=" + (product == null ? null : product.getProductCode())
                + '}';
    }
}
