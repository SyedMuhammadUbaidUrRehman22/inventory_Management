package com.retailpos.dao;

import com.retailpos.model.Return;

import java.util.List;

/** Data access for customer returns (table {@code sale_return}). */
public interface ReturnDao {

    /** Inserts the return against its sale item and returns the generated returnID (also set on the return). */
    int insert(Return ret);

    /**
     * Returns every return recorded against the lines of a sale, oldest first.
     * Each return carries its sale item with the product.
     */
    List<Return> findBySale(int saleID);
}
