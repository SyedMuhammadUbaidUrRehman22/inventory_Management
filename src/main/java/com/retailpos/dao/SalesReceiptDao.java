package com.retailpos.dao;

import com.retailpos.model.SalesReceipt;

import java.util.Optional;

/** Data access for sales receipts (at most one per sale). */
public interface SalesReceiptDao {

    /** Inserts the receipt for its sale and returns the generated receiptID. */
    int insert(SalesReceipt receipt);

    /** Finds the receipt of a sale, including the sale with its items. */
    Optional<SalesReceipt> findBySaleId(int saleID);
}
