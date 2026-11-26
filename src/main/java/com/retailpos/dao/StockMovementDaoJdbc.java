package com.retailpos.dao;

import com.retailpos.model.Cashier;
import com.retailpos.model.Category;
import com.retailpos.model.Manager;
import com.retailpos.model.Product;
import com.retailpos.model.StockMovement;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.User;
import com.retailpos.model.enums.StockMovementType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** JDBC implementation of {@link StockMovementDao}. */
public class StockMovementDaoJdbc extends JdbcDao implements StockMovementDao {

    private static final String SELECT =
            "SELECT sm.movement_id, sm.movement_type, sm.quantity_change, sm.date_time, sm.source_reference, sm.balance_after, "
            + "p.product_code, p.name AS product_name, p.unit, p.selling_price, p.reorder_level, p.status, p.current_stock_quantity, "
            + "c.category_id, c.name AS category_name, "
            + "u.user_id, u.username, u.password_hash, u.role, u.created_by, u.active "
            + "FROM stock_movement sm "
            + "JOIN product p ON p.product_code = sm.product_code "
            + "JOIN category c ON c.category_id = p.category_id "
            + "JOIN users u ON u.user_id = sm.responsible_user_id ";

    @Override
    public int insert(StockMovement movement) {
        String sql = "INSERT INTO stock_movement (movement_type, quantity_change, date_time, "
                + "source_reference, balance_after, product_code, responsible_user_id) VALUES (?, ?, ?, ?, ?, ?, ?)";
        int id = insertReturningKey(sql, ps -> {
            ps.setString(1, movement.getMovementType().name());
            ps.setInt(2, movement.getQuantityChange());
            ps.setTimestamp(3, Timestamp.valueOf(movement.getDateTime()));
            ps.setString(4, movement.getSourceReference());
            ps.setInt(5, movement.getBalanceAfter());
            ps.setString(6, movement.getProduct().getProductCode());
            ps.setInt(7, movement.getResponsibleUser().getUserID());
        });
        movement.setMovementID(id);
        return id;
    }

    @Override
    public List<StockMovement> findByProduct(String productCode) {
        String sql = SELECT + "WHERE sm.product_code = ? ORDER BY sm.date_time DESC, sm.movement_id DESC";
        return queryList(sql, ps -> ps.setString(1, productCode), StockMovementDaoJdbc::map);
    }

    @Override
    public List<StockMovement> findRecent(int limit) {
        String sql = SELECT + "ORDER BY sm.date_time DESC, sm.movement_id DESC LIMIT ?";
        return queryList(sql, ps -> ps.setInt(1, Math.max(1, limit)), StockMovementDaoJdbc::map);
    }

    @Override
    public List<StockMovement> search(String productCode, StockMovementType type, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (productCode != null) {
            sql.append(" AND sm.product_code = ?");
            params.add(productCode);
        }
        if (type != null) {
            sql.append(" AND sm.movement_type = ?");
            params.add(type.name());
        }
        if (fromDate != null) {
            sql.append(" AND sm.date_time >= ?");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            sql.append(" AND sm.date_time < ?");
            params.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY sm.date_time, sm.movement_id");
        return queryList(sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }, StockMovementDaoJdbc::map);
    }

    private static StockMovement map(ResultSet rs) throws SQLException {
        Category category = new Category(rs.getInt("category_id"), rs.getString("category_name"));
        Product product = new Product(
                rs.getString("product_code"),
                rs.getString("product_name"),
                rs.getString("unit"),
                rs.getBigDecimal("selling_price"),
                rs.getInt("reorder_level"),
                rs.getString("status"),
                rs.getInt("current_stock_quantity"),
                category);

        String role = rs.getString("role");
        int userId = rs.getInt("user_id");
        String username = rs.getString("username");
        String hash = rs.getString("password_hash");
        Integer createdBy = rs.getObject("created_by") == null ? null : rs.getInt("created_by");
        boolean active = rs.getBoolean("active");

        User user = switch (role) {
            case "MANAGER" -> new Manager(userId, username, hash, createdBy, active);
            case "CASHIER" -> new Cashier(userId, username, hash, createdBy, active);
            case "STOREKEEPER" -> new Storekeeper(userId, username, hash, createdBy, active);
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        };

        return new StockMovement(
                rs.getInt("movement_id"),
                StockMovementType.valueOf(rs.getString("movement_type")),
                rs.getInt("quantity_change"),
                rs.getTimestamp("date_time").toLocalDateTime(),
                rs.getString("source_reference"),
                rs.getInt("balance_after"),
                user,
                product);
    }
}
