package com.retailpos.dao;

import com.retailpos.model.Supplier;

import java.util.List;
import java.util.Optional;

/** Data access for suppliers. */
public interface SupplierDao {

    Optional<Supplier> findById(int supplierID);

    /**
     * Suppliers ordered by ID.
     *
     * @param keyword optional; matched against name and contact information (null = all)
     */
    List<Supplier> search(String keyword);

    /** Inserts the supplier and sets its generated supplierID. */
    void insert(Supplier supplier);

    void update(Supplier supplier);
}
