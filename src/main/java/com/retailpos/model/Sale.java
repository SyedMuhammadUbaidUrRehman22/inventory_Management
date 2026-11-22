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
