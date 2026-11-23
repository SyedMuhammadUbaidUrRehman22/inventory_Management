package com.retailpos.dao;

import com.retailpos.model.StockReceipt;

import java.util.List;

/** Data access for stock receipts recorded against purchase order lines. */
public interface StockReceiptDao {

    /** Inserts a new stock receipt and returns the generated receipt_id. */
    int insert(StockReceipt receipt);

    /** Finds all stock receipts recorded for a specific purchase order. */
    List<StockReceipt> findByPoNumber(int poNumber);

    /** Finds the most recent stock receipts across all purchase orders. */
    List<StockReceipt> findRecent(int limit);
}
