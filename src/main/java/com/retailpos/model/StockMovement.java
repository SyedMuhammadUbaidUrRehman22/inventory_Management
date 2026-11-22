package com.retailpos.model;

import com.retailpos.model.enums.StockMovementType;

import java.time.LocalDateTime;

/** One entry in a product's stock history. Every stock change creates exactly one movement. */
public class StockMovement {

    private int movementID;
    private StockMovementType movementType;
    private int quantityChange;
    private LocalDateTime dateTime;
    private String sourceReference;
    private int balanceAfter;
    private User responsibleUser;
    private Product product;

    public StockMovement() {
    }

    public StockMovement(int movementID, StockMovementType movementType, int quantityChange,
                         LocalDateTime dateTime, String sourceReference, int balanceAfter,
                         User responsibleUser, Product product) {
        this.movementID = movementID;
        this.movementType = movementType;
        this.quantityChange = quantityChange;
        this.dateTime = dateTime;
        this.sourceReference = sourceReference;
        this.balanceAfter = balanceAfter;
        this.responsibleUser = responsibleUser;
        this.product = product;
    }

    public int getMovementID() {
        return movementID;
    }

    public void setMovementID(int movementID) {
        this.movementID = movementID;
    }

    public StockMovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(StockMovementType movementType) {
        this.movementType = movementType;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(LocalDateTime dateTime) {
        this.dateTime = dateTime;
    }

    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String sourceReference) {
        this.sourceReference = sourceReference;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(int balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public User getResponsibleUser() {
        return responsibleUser;
    }

    public void setResponsibleUser(User responsibleUser) {
        this.responsibleUser = responsibleUser;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    @Override
    public String toString() {
        return "StockMovement{"
                + "movementID=" + movementID
                + ", movementType=" + movementType
                + ", quantityChange=" + quantityChange
                + ", dateTime=" + dateTime
                + ", sourceReference='" + sourceReference + '\''
                + ", balanceAfter=" + balanceAfter
                + ", responsibleUser=" + (responsibleUser == null ? null : responsibleUser.getUsername())
                + ", product=" + (product == null ? null : product.getProductCode())
                + '}';
    }
}
