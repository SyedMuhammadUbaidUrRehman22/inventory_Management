package com.retailpos.dao;

import com.retailpos.model.ProductSupplier;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** JDBC implementation of {@link ProductSupplierDao}. */
public class ProductSupplierDaoJdbc extends JdbcDao implements ProductSupplierDao {

    private static final String SELECT = "SELECT " + ProductDaoJdbc.PRODUCT_COLUMNS + ", "
            + SupplierDaoJdbc.SUPPLIER_COLUMNS + ", ps.purchase_price"
            + " FROM product_supplier ps"
            + " JOIN product p ON p.product_code = ps.product_code"
            + " JOIN category c ON c.category_id = p.category_id"
            + " JOIN supplier s ON s.supplier_id = ps.supplier_id";

    @Override
    public void save(ProductSupplier link) {
        update("INSERT INTO product_supplier (product_code, supplier_id, purchase_price) VALUES (?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE purchase_price = ?",
                ps -> {
                    ps.setString(1, link.getProduct().getProductCode());
                    ps.setInt(2, link.getSupplier().getSupplierID());
                    ps.setBigDecimal(3, link.getPurchasePrice());
                    ps.setBigDecimal(4, link.getPurchasePrice());
                });
    }

    @Override
    public boolean delete(String productCode, int supplierID) {
        return update("DELETE FROM product_supplier WHERE product_code = ? AND supplier_id = ?",
                ps -> {
                    ps.setString(1, productCode);
                    ps.setInt(2, supplierID);
                }) > 0;
    }

    @Override
    public List<ProductSupplier> findBySupplier(int supplierID) {
        return queryList(SELECT + " WHERE ps.supplier_id = ? ORDER BY p.product_code",
                ps -> ps.setInt(1, supplierID), ProductSupplierDaoJdbc::map);
    }

    private static ProductSupplier map(ResultSet rs) throws SQLException {
        return new ProductSupplier(ProductDaoJdbc.map(rs), SupplierDaoJdbc.map(rs), rs.getBigDecimal("purchase_price"));
    }
}
