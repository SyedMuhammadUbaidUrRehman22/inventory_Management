package com.retailpos.dao;

import com.retailpos.model.Category;
import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.Supplier;
import com.retailpos.model.enums.PurchaseOrderStatus;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link PurchaseOrderDao}. */
public class PurchaseOrderDaoJdbc extends JdbcDao implements PurchaseOrderDao {

    @Override
    public Optional<PurchaseOrder> findByNumber(int poNumber) {
        String sql = "SELECT po.po_number, po.order_date, po.status, po.total_value, "
                + "s.supplier_id, s.name AS supplier_name, s.address AS supplier_address, s.contact_info AS supplier_contact "
                + "FROM purchase_order po "
                + "JOIN supplier s ON s.supplier_id = po.supplier_id "
                + "WHERE po.po_number = ?";
        Optional<PurchaseOrder> poOpt = queryOne(sql, ps -> ps.setInt(1, poNumber), rs -> {
            Supplier supplier = new Supplier(
                    rs.getInt("supplier_id"),
                    rs.getString("supplier_name"),
                    rs.getString("supplier_address"),
                    rs.getString("supplier_contact"));
            return new PurchaseOrder(
                    rs.getInt("po_number"),
                    rs.getDate("order_date").toLocalDate(),
                    PurchaseOrderStatus.valueOf(rs.getString("status")),
                    rs.getBigDecimal("total_value"),
                    supplier,
                    new ArrayList<>());
        });

        poOpt.ifPresent(po -> po.setItems(findItemsByPoNumber(po.getPoNumber())));
        return poOpt;
    }

    @Override
    public List<PurchaseOrder> findAll() {
        return search(null, null, null, null);
    }

    @Override
    public List<PurchaseOrder> search(Integer supplierId, PurchaseOrderStatus status, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder(
                "SELECT po.po_number, po.order_date, po.status, po.total_value, "
                + "s.supplier_id, s.name AS supplier_name, s.address AS supplier_address, s.contact_info AS supplier_contact "
                + "FROM purchase_order po "
                + "JOIN supplier s ON s.supplier_id = po.supplier_id "
                + "WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (supplierId != null) {
            sql.append(" AND po.supplier_id = ?");
            params.add(supplierId);
        }
        if (status != null) {
            sql.append(" AND po.status = ?");
            params.add(status.name());
        }
        if (fromDate != null) {
            sql.append(" AND po.order_date >= ?");
            params.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append(" AND po.order_date <= ?");
            params.add(Date.valueOf(toDate));
        }
        sql.append(" ORDER BY po.po_number DESC");

        List<PurchaseOrder> orders = queryList(sql.toString(), ps -> {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
        }, rs -> {
            Supplier supplier = new Supplier(
                    rs.getInt("supplier_id"),
                    rs.getString("supplier_name"),
                    rs.getString("supplier_address"),
                    rs.getString("supplier_contact"));
            return new PurchaseOrder(
                    rs.getInt("po_number"),
                    rs.getDate("order_date").toLocalDate(),
                    PurchaseOrderStatus.valueOf(rs.getString("status")),
                    rs.getBigDecimal("total_value"),
                    supplier,
                    new ArrayList<>());
        });

        for (PurchaseOrder po : orders) {
            po.setItems(findItemsByPoNumber(po.getPoNumber()));
        }
        return orders;
    }

    @Override
    public List<PurchaseOrder> findReceivable() {
        String sql = "SELECT po.po_number, po.order_date, po.status, po.total_value, "
                + "s.supplier_id, s.name AS supplier_name, s.address AS supplier_address, s.contact_info AS supplier_contact "
                + "FROM purchase_order po "
                + "JOIN supplier s ON s.supplier_id = po.supplier_id "
                + "WHERE po.status IN ('ORDERED', 'PARTIALLY_RECEIVED') "
                + "ORDER BY po.po_number ASC";
        List<PurchaseOrder> orders = queryList(sql, NO_PARAMS, rs -> {
            Supplier supplier = new Supplier(
                    rs.getInt("supplier_id"),
                    rs.getString("supplier_name"),
                    rs.getString("supplier_address"),
                    rs.getString("supplier_contact"));
            return new PurchaseOrder(
                    rs.getInt("po_number"),
                    rs.getDate("order_date").toLocalDate(),
                    PurchaseOrderStatus.valueOf(rs.getString("status")),
                    rs.getBigDecimal("total_value"),
                    supplier,
                    new ArrayList<>());
        });

        for (PurchaseOrder po : orders) {
            po.setItems(findItemsByPoNumber(po.getPoNumber()));
        }
        return orders;
    }

    @Override
    public int insert(PurchaseOrder order) {
        String insertPoSql = "INSERT INTO purchase_order (order_date, status, total_value, supplier_id) VALUES (?, ?, ?, ?)";
        String insertPoiSql = "INSERT INTO purchase_order_item (po_number, product_code, ordered_quantity, purchase_price, received_quantity) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection c = connection()) {
            boolean wasAutoCommit = c.getAutoCommit();
            if (wasAutoCommit) {
                c.setAutoCommit(false);
            }
            try {
                int poNumber;
                try (PreparedStatement ps = c.prepareStatement(insertPoSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setDate(1, Date.valueOf(order.getOrderDate()));
                    ps.setString(2, order.getStatus().name());
                    ps.setBigDecimal(3, order.getTotalValue());
                    ps.setInt(4, order.getSupplier().getSupplierID());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Failed to retrieve generated PO number.");
                        }
                        poNumber = keys.getInt(1);
                    }
                }
                order.setPoNumber(poNumber);

                if (order.getItems() != null && !order.getItems().isEmpty()) {
                    try (PreparedStatement psItem = c.prepareStatement(insertPoiSql, Statement.RETURN_GENERATED_KEYS)) {
                        for (PurchaseOrderItem item : order.getItems()) {
                            psItem.setInt(1, poNumber);
                            psItem.setString(2, item.getProduct().getProductCode());
                            psItem.setInt(3, item.getOrderedQuantity());
                            psItem.setBigDecimal(4, item.getPurchasePrice());
                            psItem.setInt(5, item.getReceivedQuantity());
                            psItem.executeUpdate();
                            try (ResultSet keys = psItem.getGeneratedKeys()) {
                                if (keys.next()) {
                                    item.setPurchaseOrderItemID(keys.getInt(1));
                                }
                            }
                        }
                    }
                }

                if (wasAutoCommit) {
                    c.commit();
                }
                return poNumber;
            } catch (SQLException | RuntimeException e) {
                if (wasAutoCommit) {
                    c.rollback();
                }
                throw e;
            } finally {
                if (wasAutoCommit) {
                    c.setAutoCommit(true);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error inserting purchase order: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateStatus(int poNumber, PurchaseOrderStatus status) {
        update("UPDATE purchase_order SET status = ? WHERE po_number = ?", ps -> {
            ps.setString(1, status.name());
            ps.setInt(2, poNumber);
        });
    }

    @Override
    public void updateItemReceivedQuantity(int purchaseOrderItemId, int newReceivedQuantity) {
        update("UPDATE purchase_order_item SET received_quantity = ? WHERE purchase_order_item_id = ?", ps -> {
            ps.setInt(1, newReceivedQuantity);
            ps.setInt(2, purchaseOrderItemId);
        });
    }

    @Override
    public Optional<PurchaseOrderItem> findItemById(int purchaseOrderItemId) {
        String sql = "SELECT poi.purchase_order_item_id, poi.ordered_quantity, poi.purchase_price, poi.received_quantity, "
                + "p.product_code, p.name AS product_name, p.unit, p.selling_price, p.reorder_level, p.status, p.current_stock_quantity, "
                + "c.category_id, c.name AS category_name "
                + "FROM purchase_order_item poi "
                + "JOIN product p ON p.product_code = poi.product_code "
                + "JOIN category c ON c.category_id = p.category_id "
                + "WHERE poi.purchase_order_item_id = ?";
        return queryOne(sql, ps -> ps.setInt(1, purchaseOrderItemId), PurchaseOrderDaoJdbc::mapItem);
    }

    @Override
    public List<PurchaseOrderItem> findItemsByPoNumber(int poNumber) {
        String sql = "SELECT poi.purchase_order_item_id, poi.ordered_quantity, poi.purchase_price, poi.received_quantity, "
                + "p.product_code, p.name AS product_name, p.unit, p.selling_price, p.reorder_level, p.status, p.current_stock_quantity, "
                + "c.category_id, c.name AS category_name "
                + "FROM purchase_order_item poi "
                + "JOIN product p ON p.product_code = poi.product_code "
                + "JOIN category c ON c.category_id = p.category_id "
                + "WHERE poi.po_number = ? "
                + "ORDER BY poi.purchase_order_item_id ASC";
        return queryList(sql, ps -> ps.setInt(1, poNumber), PurchaseOrderDaoJdbc::mapItem);
    }

    private static PurchaseOrderItem mapItem(ResultSet rs) throws SQLException {
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
        return new PurchaseOrderItem(
                rs.getInt("purchase_order_item_id"),
                rs.getInt("ordered_quantity"),
                rs.getBigDecimal("purchase_price"),
                rs.getInt("received_quantity"),
                product);
    }
}
