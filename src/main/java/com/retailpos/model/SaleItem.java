package com.retailpos.model;

import java.math.BigDecimal;

/** One product line of a sale. */
public class SaleItem {

    private int saleItemID;
    private int quantity;
    private BigDecimal sellingPrice;
    private BigDecimal lineTotal;
    private Product product;

    public SaleItem() {
    }

    public SaleItem(int saleItemID, int quantity, BigDecimal sellingPrice, BigDecimal lineTotal,
                    Product product) {
        this.saleItemID = saleItemID;
        this.quantity = quantity;
        this.sellingPrice = sellingPrice;
        this.lineTotal = lineTotal;
        this.product = product;
    }

    public int getSaleItemID() {
        return saleItemID;
    }

    public void setSaleItemID(int saleItemID) {
        this.saleItemID = saleItemID;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    @Override
    public String toString() {
        return "SaleItem{"
                + "saleItemID=" + saleItemID
                + ", quantity=" + quantity
                + ", sellingPrice=" + sellingPrice
                + ", lineTotal=" + lineTotal
                + ", product=" + (product == null ? null : product.getProductCode())
                + '}';
    }
}
