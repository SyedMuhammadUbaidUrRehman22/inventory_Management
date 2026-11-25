package com.retailpos.dao;

import com.retailpos.model.Cashier;
import com.retailpos.model.Product;
import com.retailpos.model.Sale;
import com.retailpos.model.SaleItem;
import com.retailpos.model.enums.SaleStatus;
import com.retailpos.util.DBConnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JDBC implementation of {@link SaleDao}. */
public class SaleDaoJdbc extends JdbcDao implements SaleDao {

    private static final String SELECT_SALE =
            "SELECT s.sale_id, s.date_time, s.status, s.subtotal, s.discount, s.final_amount, "
            + "u.user_id, u.username, u.password_hash, u.created_by, u.active "
            + "FROM sale s JOIN users u ON u.user_id = s.cashier_id ";

    private static final String SELECT_ITEMS =
            "SELECT si.sale_id, si.sale_item_id, si.quantity, si.selling_price, si.line_total, "
            + ProductDaoJdbc.PRODUCT_COLUMNS + " "
            + "FROM sale_item si "
            + "JOIN product p ON p.product_code = si.product_code "
            + "JOIN category c ON c.category_id = p.category_id ";

    @Override
    public int insert(Sale sale) {
        return DBConnection.inTransaction(() -> {
            int saleID = insertReturningKey(
                    "INSERT INTO sale (date_time, status, subtotal, discount, final_amount, cashier_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
                    ps -> {
                        ps.setTimestamp(1, Timestamp.valueOf(sale.getDateTime()));
                        ps.setString(2, sale.getStatus().name());
                        ps.setBigDecimal(3, sale.getSubtotal());
                        ps.setBigDecimal(4, sale.getDiscount());
                        ps.setBigDecimal(5, sale.getFinalAmount());
                        ps.setInt(6, sale.getCashier().getUserID());
                    });
            sale.setSaleID(saleID);
            for (SaleItem item : sale.getItems()) {
                int itemID = insertReturningKey(
                        "INSERT INTO sale_item (sale_id, product_code, quantity, selling_price, line_total) "
                        + "VALUES (?, ?, ?, ?, ?)",
                        ps -> {
                            ps.setInt(1, saleID);
                            ps.setString(2, item.getProduct().getProductCode());
                            ps.setInt(3, item.getQuantity());
                            ps.setBigDecimal(4, item.getSellingPrice());
                            ps.setBigDecimal(5, item.getLineTotal());
                        });
                item.setSaleItemID(itemID);
            }
            return saleID;
        });
    }

    @Override
    public Optional<Sale> findById(int saleID) {
        Optional<Sale> sale = queryOne(SELECT_SALE + "WHERE s.sale_id = ?",
                ps -> ps.setInt(1, saleID), SaleDaoJdbc::mapSale);
        sale.ifPresent(s -> s.setItems(loadItems(List.of(s.getSaleID())).getOrDefault(s.getSaleID(), new ArrayList<>())));
        return sale;
    }

    @Override
    public List<Sale> findCompleted(LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder(SELECT_SALE).append("WHERE s.status = 'COMPLETED'");
        List<Timestamp> params = new ArrayList<>();
        if (fromDate != null) {
            sql.append(" AND s.date_time >= ?");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            sql.append(" AND s.date_time < ?");
            params.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY s.date_time, s.sale_id");
        List<Sale> sales = queryList(sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) {
                ps.setTimestamp(i + 1, params.get(i));
            }
        }, SaleDaoJdbc::mapSale);
        if (sales.isEmpty()) {
            return sales;
        }
        List<Integer> ids = new ArrayList<>();
        sales.forEach(s -> ids.add(s.getSaleID()));
        Map<Integer, List<SaleItem>> itemsBySale = loadItems(ids);
        for (Sale s : sales) {
            s.setItems(itemsBySale.getOrDefault(s.getSaleID(), new ArrayList<>()));
        }
        return sales;
    }

    /** Loads the lines of several sales with one query, grouped by sale ID. */
    private Map<Integer, List<SaleItem>> loadItems(List<Integer> saleIDs) {
        Map<Integer, List<SaleItem>> result = new HashMap<>();
        // Chunked so a long date range never builds an oversized IN (...) list.
        for (int start = 0; start < saleIDs.size(); start += 500) {
            List<Integer> chunk = saleIDs.subList(start, Math.min(saleIDs.size(), start + 500));
            String placeholders = String.join(", ", java.util.Collections.nCopies(chunk.size(), "?"));
            String sql = SELECT_ITEMS + "WHERE si.sale_id IN (" + placeholders + ") ORDER BY si.sale_id, si.sale_item_id";
            List<Object[]> rows = queryList(sql, ps -> {
                for (int i = 0; i < chunk.size(); i++) {
                    ps.setInt(i + 1, chunk.get(i));
                }
            }, rs -> new Object[]{rs.getInt("sale_id"), mapItem(rs)});
            for (Object[] row : rows) {
                result.computeIfAbsent((Integer) row[0], k -> new ArrayList<>()).add((SaleItem) row[1]);
            }
        }
        return result;
    }

    private static Sale mapSale(ResultSet rs) throws SQLException {
        int createdBy = rs.getInt("created_by");
        Integer createdByID = rs.wasNull() ? null : createdBy;
        Cashier cashier = new Cashier(rs.getInt("user_id"), rs.getString("username"),
                rs.getString("password_hash"), createdByID, rs.getBoolean("active"));
        LocalDateTime dateTime = rs.getTimestamp("date_time").toLocalDateTime();
        return new Sale(rs.getInt("sale_id"), dateTime, SaleStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount"), rs.getBigDecimal("final_amount"),
                cashier, new ArrayList<>());
    }

    private static SaleItem mapItem(ResultSet rs) throws SQLException {
        Product product = ProductDaoJdbc.map(rs);
        return new SaleItem(rs.getInt("sale_item_id"), rs.getInt("quantity"),
                rs.getBigDecimal("selling_price"), rs.getBigDecimal("line_total"), product);
    }
}
