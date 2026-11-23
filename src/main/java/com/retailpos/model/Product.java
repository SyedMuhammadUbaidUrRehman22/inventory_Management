package com.retailpos.model;

import java.math.BigDecimal;

/** A product sold in the store. Identified by its unique product code. */
public class Product {

    private String productCode;
    private String name;
    private String unit;
    private BigDecimal sellingPrice;
    private int reorderLevel;
    private String status;
    private int currentStockQuantity;
    private Category category;

    public Product() {
    }

    public Product(String productCode, String name, String unit, BigDecimal sellingPrice,
                   int reorderLevel, String status, int currentStockQuantity, Category category) {
        this.productCode = productCode;
        this.name = name;
        this.unit = unit;
        this.sellingPrice = sellingPrice;
        this.reorderLevel = reorderLevel;
        this.status = status;
        this.currentStockQuantity = currentStockQuantity;
        this.category = category;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(int reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCurrentStockQuantity() {
        return currentStockQuantity;
    }

    public void setCurrentStockQuantity(int currentStockQuantity) {
        this.currentStockQuantity = currentStockQuantity;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    /** Low stock = currentStockQuantity <= reorderLevel (FR31). */
    public boolean isLowStock() {
        return currentStockQuantity <= reorderLevel;
    }

    /** Applies a stock movement change. */
    public void applyStockChange(StockMovement movement) {
        if (movement != null) {
            this.currentStockQuantity += movement.getQuantityChange();
        }
    }

    @Override
    public String toString() {
        return "Product{"
                + "productCode='" + productCode + '\''
                + ", name='" + name + '\''
                + ", unit='" + unit + '\''
                + ", sellingPrice=" + sellingPrice
                + ", reorderLevel=" + reorderLevel
                + ", status='" + status + '\''
                + ", currentStockQuantity=" + currentStockQuantity
                + ", category=" + (category == null ? null : category.getName())
                + '}';
    }
}
