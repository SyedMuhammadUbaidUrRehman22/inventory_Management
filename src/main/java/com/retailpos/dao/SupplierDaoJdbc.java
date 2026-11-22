package com.retailpos.dao;

import com.retailpos.model.Supplier;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link SupplierDao}. */
public class SupplierDaoJdbc extends JdbcDao implements SupplierDao {

    /** Supplier columns; also reused by {@link ProductSupplierDaoJdbc}. */
    static final String SUPPLIER_COLUMNS =
            "s.supplier_id, s.name AS supplier_name, s.address, s.contact_info";

    private static final String SELECT = "SELECT " + SUPPLIER_COLUMNS + " FROM supplier s";

    @Override
    public Optional<Supplier> findById(int supplierID) {
        return queryOne(SELECT + " WHERE s.supplier_id = ?", ps -> ps.setInt(1, supplierID), SupplierDaoJdbc::map);
    }

    @Override
    public List<Supplier> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return queryList(SELECT + " ORDER BY s.supplier_id", NO_PARAMS, SupplierDaoJdbc::map);
        }
        String pattern = containsPattern(keyword.trim());
        return queryList(SELECT + " WHERE s.name LIKE ? OR s.contact_info LIKE ? ORDER BY s.supplier_id",
                ps -> {
                    ps.setString(1, pattern);
                    ps.setString(2, pattern);
                }, SupplierDaoJdbc::map);
    }

    @Override
    public void insert(Supplier supplier) {
        int id = insertReturningKey("INSERT INTO supplier (name, address, contact_info) VALUES (?, ?, ?)",
                ps -> {
                    ps.setString(1, supplier.getName());
                    ps.setString(2, supplier.getAddress());
                    ps.setString(3, supplier.getContactInfo());
                });
        supplier.setSupplierID(id);
    }

    @Override
    public void update(Supplier supplier) {
        update("UPDATE supplier SET name = ?, address = ?, contact_info = ? WHERE supplier_id = ?",
                ps -> {
                    ps.setString(1, supplier.getName());
                    ps.setString(2, supplier.getAddress());
                    ps.setString(3, supplier.getContactInfo());
                    ps.setInt(4, supplier.getSupplierID());
                });
    }

    /** Maps a row selected with {@link #SUPPLIER_COLUMNS}. */
    static Supplier map(ResultSet rs) throws SQLException {
        return new Supplier(rs.getInt("supplier_id"), rs.getString("supplier_name"),
                rs.getString("address"), rs.getString("contact_info"));
    }
}
