package com.retailpos.dao;

import com.retailpos.model.StockAdjustment;
import com.retailpos.model.enums.AdjustmentType;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** JDBC implementation of {@link StockAdjustmentDao}. Model field {@code date} is stored as {@code adjustment_date}. */
public class StockAdjustmentDaoJdbc extends JdbcDao implements StockAdjustmentDao {

    private static final String SELECT =
            "SELECT a.adjustment_id, a.adjustment_type, a.quantity, a.adjustment_date, a.reason, "
            + ProductDaoJdbc.PRODUCT_COLUMNS + ", "
            + "u.user_id, u.username, u.password_hash, u.role, u.created_by, u.active "
            + "FROM stock_adjustment a "
            + "JOIN product p ON p.product_code = a.product_code "
            + "JOIN category c ON c.category_id = p.category_id "
            + "JOIN users u ON u.user_id = a.recorded_by ";

    @Override
    public int insert(StockAdjustment adjustment) {
        int id = insertReturningKey(
                "INSERT INTO stock_adjustment (adjustment_type, quantity, adjustment_date, reason, product_code, recorded_by) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, adjustment.getAdjustmentType().name());
                    ps.setInt(2, adjustment.getQuantity());
                    ps.setDate(3, Date.valueOf(adjustment.getDate()));
                    ps.setString(4, adjustment.getReason());
                    ps.setString(5, adjustment.getProduct().getProductCode());
                    ps.setInt(6, adjustment.getRecordedBy().getUserID());
                });
        adjustment.setAdjustmentID(id);
        return id;
    }

    @Override
    public List<StockAdjustment> findHistory(String productCode) {
        String order = " ORDER BY a.adjustment_date DESC, a.adjustment_id DESC";
        if (productCode == null) {
            return queryList(SELECT + order, NO_PARAMS, StockAdjustmentDaoJdbc::map);
        }
        return queryList(SELECT + "WHERE a.product_code = ?" + order,
                ps -> ps.setString(1, productCode), StockAdjustmentDaoJdbc::map);
    }

    private static StockAdjustment map(ResultSet rs) throws SQLException {
        return new StockAdjustment(rs.getInt("adjustment_id"),
                AdjustmentType.valueOf(rs.getString("adjustment_type")),
                rs.getInt("quantity"),
                rs.getDate("adjustment_date").toLocalDate(),
                rs.getString("reason"),
                UserDaoJdbc.map(rs),
                ProductDaoJdbc.map(rs));
    }
}
