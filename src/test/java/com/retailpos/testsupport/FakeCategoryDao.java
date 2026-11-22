package com.retailpos.testsupport;

import com.retailpos.dao.CategoryDao;
import com.retailpos.model.Category;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** In-memory {@link CategoryDao} for unit tests. Name lookup is case-insensitive, like MySQL. */
public class FakeCategoryDao implements CategoryDao {

    private final List<Category> categories = new ArrayList<>();
    private int nextId = 1;

    @Override
    public List<Category> findAll() {
        List<Category> all = new ArrayList<>(categories);
        all.sort(Comparator.comparing(Category::getName));
        return all;
    }

    @Override
    public Optional<Category> findById(int categoryID) {
        return categories.stream().filter(c -> c.getCategoryID() == categoryID).findFirst();
    }

    @Override
    public Optional<Category> findByName(String name) {
        return categories.stream().filter(c -> c.getName().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public void insert(Category category) {
        category.setCategoryID(nextId++);
        categories.add(new Category(category.getCategoryID(), category.getName()));
    }
}
