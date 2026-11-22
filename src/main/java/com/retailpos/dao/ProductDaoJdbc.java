package com.retailpos.dao;

import com.retailpos.model.Category;
import com.retailpos.model.Product;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link ProductDao}. */
public class ProductDaoJdbc extends JdbcDao implements ProductDao {

    /** Product columns joined with their category; also reused by {@link ProductSupplierDaoJdbc}. */
    static final String PRODUCT_COLUMNS =
            "p.product_code, p.name AS product_name, p.unit, p.selling_price, p.reorder_level, "
            + "p.status, p.current_stock_quantity, c.category_id, c.name AS category_name";

    private static final String SELECT = "SELECT " + PRODUCT_COLUMNS
            + " FROM product p JOIN category c ON c.category_id = p.category_id";

    @Override
    public Optional<Product> findByCode(String productCode) {
        return queryOne(SELECT + " WHERE p.product_code = ?",
                ps -> ps.setString(1, productCode), ProductDaoJdbc::map);
    }

    @Override
    public List<Product> search(String keyword, Integer categoryID, String status) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String pattern = containsPattern(keyword.trim());
            sql.append(" AND (p.product_code LIKE ? OR p.name LIKE ? OR c.name LIKE ?)");
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (categoryID != null) {
            sql.append(" AND p.category_id = ?");
            params.add(categoryID);
        }
        if (status != null) {
            sql.append(" AND p.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY p.product_code");
        return queryList(sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }, ProductDaoJdbc::map);
    }

    @Override
    public void insert(Product product) {
        update("INSERT INTO product (product_code, name, unit, selling_price, reorder_level, status, "
                        + "current_stock_quantity, category_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, product.getProductCode());
                    ps.setString(2, product.getName());
                    ps.setString(3, product.getUnit());
                    ps.setBigDecimal(4, product.getSellingPrice());
                    ps.setInt(5, product.getReorderLevel());
                    ps.setString(6, product.getStatus());
                    ps.setInt(7, product.getCurrentStockQuantity());
                    ps.setInt(8, product.getCategory().getCategoryID());
                });
    }

    @Override
    public void update(Product product) {
        update("UPDATE product SET name = ?, unit = ?, selling_price = ?, reorder_level = ?, status = ?, "
                        + "category_id = ? WHERE product_code = ?",
                ps -> {
                    ps.setString(1, product.getName());
                    ps.setString(2, product.getUnit());
                    ps.setBigDecimal(3, product.getSellingPrice());
                    ps.setInt(4, product.getReorderLevel());
                    ps.setString(5, product.getStatus());
                    ps.setInt(6, product.getCategory().getCategoryID());
                    ps.setString(7, product.getProductCode());
                });
    }

    /** Maps a row selected with {@link #PRODUCT_COLUMNS}. */
    static Product map(ResultSet rs) throws SQLException {
        Category category = new Category(rs.getInt("category_id"), rs.getString("category_name"));
        return new Product(
                rs.getString("product_code"),
                rs.getString("product_name"),
                rs.getString("unit"),
                rs.getBigDecimal("selling_price"),
                rs.getInt("reorder_level"),
                rs.getString("status"),
                rs.getInt("current_stock_quantity"),
                category);
    }
}
