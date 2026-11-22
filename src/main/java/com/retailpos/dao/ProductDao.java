package com.retailpos.dao;

import com.retailpos.model.Product;

import java.util.List;
import java.util.Optional;

/** Data access for products. Each loaded product carries its Category. */
public interface ProductDao {

    Optional<Product> findByCode(String productCode);

    /**
     * Searches products. Every argument is optional (null = no filter).
     *
     * @param keyword    matched against product code, product name and category name
     * @param categoryID only products in this category
     * @param status     only products with this status
     */
    List<Product> search(String keyword, Integer categoryID, String status);

    /** Inserts a new product, including its starting currentStockQuantity. */
    void insert(Product product);

    /**
     * Updates the descriptive fields of a product (name, unit, price, reorder level,
     * status, category). Never changes currentStockQuantity: stock only changes
     * through stock movements.
     */
    void update(Product product);
}
