package com.retailpos.dao;

import com.retailpos.model.StockAdjustment;

import java.util.List;

/** Data access for stock adjustments (damaged, expired, missing goods). */
public interface StockAdjustmentDao {

    /** Inserts the adjustment and returns the generated adjustmentID (also set on the adjustment). */
    int insert(StockAdjustment adjustment);

    /**
     * FR27: Returns recorded adjustments, newest first, each with its product and the user who recorded it.
     *
     * @param productCode only this product's adjustments, or null for all products
     */
    List<StockAdjustment> findHistory(String productCode);
}
