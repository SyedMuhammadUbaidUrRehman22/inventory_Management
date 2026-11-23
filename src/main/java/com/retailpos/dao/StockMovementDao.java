package com.retailpos.dao;

import com.retailpos.model.StockMovement;

import java.util.List;

/** Data access for audit trail of inventory changes. */
public interface StockMovementDao {

    /** Inserts a stock movement and returns the generated movement_id. */
    int insert(StockMovement movement);

    /** Returns all movements recorded for a product, sorted by date_time descending. */
    List<StockMovement> findByProduct(String productCode);

    /** Returns the most recent movements across all products. */
    List<StockMovement> findRecent(int limit);
}
