package com.retailpos.dao;

import com.retailpos.model.Sale;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Data access for sales and their lines. Each loaded sale carries its cashier and its items with products. */
public interface SaleDao {

    /**
     * Inserts the sale and all its items in one transaction.
     * Sets the generated saleID on the sale and the generated IDs on its items.
     *
     * @return the generated saleID
     */
    int insert(Sale sale);

    Optional<Sale> findById(int saleID);

    /**
     * Returns COMPLETED sales whose date and time fall on or between the two dates (FR33),
     * oldest first. A null date means no limit on that side.
     */
    List<Sale> findCompleted(LocalDate fromDate, LocalDate toDate);
}
