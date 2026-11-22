package com.retailpos.model;

import java.math.BigDecimal;

/** Many-to-many link between a product and a supplier, with that supplier's purchase price. */
public class ProductSupplier {

    private Product product;
    private Supplier supplier;
    private BigDecimal purchasePrice;

    public ProductSupplier() {
    }

    public ProductSupplier(Product product, Supplier supplier, BigDecimal purchasePrice) {
        this.product = product;
        this.supplier = supplier;
        this.purchasePrice = purchasePrice;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    @Override
    public String toString() {
        return "ProductSupplier{"
                + "product=" + (product == null ? null : product.getProductCode())
                + ", supplier=" + (supplier == null ? null : supplier.getSupplierID())
                + ", purchasePrice=" + purchasePrice
                + '}';
    }
}
