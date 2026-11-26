package com.retailpos.dao;

import com.retailpos.model.Product;
import com.retailpos.model.Return;
import com.retailpos.model.SaleItem;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** JDBC implementation of {@link ReturnDao}. */
public class ReturnDaoJdbc extends JdbcDao implements ReturnDao {

    private static final String SELECT =
            "SELECT r.return_id, r.return_date, r.quantity AS return_quantity, r.reason, r.is_accepted, r.is_reusable, "
            + "si.sale_item_id, si.quantity AS sold_quantity, si.selling_price, si.line_total, "
            + ProductDaoJdbc.PRODUCT_COLUMNS + " "
            + "FROM sale_return r "
            + "JOIN sale_item si ON si.sale_item_id = r.sale_item_id "
            + "JOIN product p ON p.product_code = si.product_code "
            + "JOIN category c ON c.category_id = p.category_id ";

    @Override
    public int insert(Return ret) {
        int id = insertReturningKey(
                "INSERT INTO sale_return (return_date, quantity, reason, is_accepted, is_reusable, sale_item_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
                ps -> {
                    ps.setDate(1, Date.valueOf(ret.getReturnDate()));
                    ps.setInt(2, ret.getQuantity());
                    ps.setString(3, ret.getReason());
                    ps.setBoolean(4, ret.isAccepted());
                    ps.setBoolean(5, ret.isReusable());
                    ps.setInt(6, ret.getSaleItem().getSaleItemID());
                });
        ret.setReturnID(id);
        return id;
    }

    @Override
    public List<Return> findBySale(int saleID) {
        return queryList(SELECT + "WHERE si.sale_id = ? ORDER BY r.return_date, r.return_id",
                ps -> ps.setInt(1, saleID), ReturnDaoJdbc::map);
    }

    private static Return map(ResultSet rs) throws SQLException {
        Product product = ProductDaoJdbc.map(rs);
        SaleItem item = new SaleItem(rs.getInt("sale_item_id"), rs.getInt("sold_quantity"),
                rs.getBigDecimal("selling_price"), rs.getBigDecimal("line_total"), product);
        return new Return(rs.getInt("return_id"), rs.getDate("return_date").toLocalDate(),
                rs.getInt("return_quantity"), rs.getString("reason"), rs.getBoolean("is_accepted"),
                rs.getBoolean("is_reusable"), item);
    }
}
