package com.retailpos.dao;

import com.retailpos.model.Category;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link CategoryDao}. */
public class CategoryDaoJdbc extends JdbcDao implements CategoryDao {

    private static final String SELECT = "SELECT category_id, name FROM category";

    @Override
    public List<Category> findAll() {
        return queryList(SELECT + " ORDER BY name", NO_PARAMS, CategoryDaoJdbc::map);
    }

    @Override
    public Optional<Category> findById(int categoryID) {
        return queryOne(SELECT + " WHERE category_id = ?", ps -> ps.setInt(1, categoryID), CategoryDaoJdbc::map);
    }

    @Override
    public Optional<Category> findByName(String name) {
        return queryOne(SELECT + " WHERE name = ?", ps -> ps.setString(1, name), CategoryDaoJdbc::map);
    }

    @Override
    public void insert(Category category) {
        int id = insertReturningKey("INSERT INTO category (name) VALUES (?)",
                ps -> ps.setString(1, category.getName()));
        category.setCategoryID(id);
    }

    private static Category map(ResultSet rs) throws SQLException {
        return new Category(rs.getInt("category_id"), rs.getString("name"));
    }
}
