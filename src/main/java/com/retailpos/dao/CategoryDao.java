package com.retailpos.dao;

import com.retailpos.model.Category;

import java.util.List;
import java.util.Optional;

/** Data access for product categories. */
public interface CategoryDao {

    /** All categories, ordered by name. */
    List<Category> findAll();

    Optional<Category> findById(int categoryID);

    /** Case-insensitive lookup (MySQL default collation). */
    Optional<Category> findByName(String name);

    /** Inserts the category and sets its generated categoryID. */
    void insert(Category category);
}
