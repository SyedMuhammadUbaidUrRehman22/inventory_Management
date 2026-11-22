package com.retailpos.dao;

import com.retailpos.model.ProductSupplier;

import java.util.List;

/** Data access for the product-supplier link and its supplier-specific purchase price. */
public interface ProductSupplierDao {

    /** Creates the link, or updates its purchase price if the product is already linked to the supplier. */
    void save(ProductSupplier link);

    /** Removes the link. Returns false if the product was not linked to the supplier. */
    boolean delete(String productCode, int supplierID);

    /** All products linked to a supplier, ordered by product code. */
    List<ProductSupplier> findBySupplier(int supplierID);
}
