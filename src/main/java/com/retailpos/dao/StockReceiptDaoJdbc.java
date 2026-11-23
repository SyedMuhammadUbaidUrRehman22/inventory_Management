package com.retailpos.dao;

import com.retailpos.model.Category;
import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.StockReceipt;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** JDBC implementation of {@link StockReceiptDao}. */
public class StockReceiptDaoJdbc extends JdbcDao implements StockReceiptDao {

    @Override
    public int insert(StockReceipt receipt) {
        String sql = "INSERT INTO stock_receipt (receipt_date, quantity_received, purchase_order_item_id) VALUES (?, ?, ?)";
        int id = insertReturningKey(sql, ps -> {
            ps.setDate(1, Date.valueOf(receipt.getReceiptDate()));
            ps.setInt(2, receipt.getQuantityReceived());
            ps.setInt(3, receipt.getPurchaseOrderItem().getPurchaseOrderItemID());
        });
        receipt.setReceiptID(id);
        return id;
    }

    @Override
    public List<StockReceipt> findByPoNumber(int poNumber) {
        String sql = "SELECT sr.receipt_id, sr.receipt_date, sr.quantity_received, "
                + "poi.purchase_order_item_id, poi.ordered_quantity, poi.purchase_price, poi.received_quantity, "
                + "p.product_code, p.name AS product_name, p.unit, p.selling_price, p.reorder_level, p.status, p.current_stock_quantity, "
                + "c.category_id, c.name AS category_name "
                + "FROM stock_receipt sr "
                + "JOIN purchase_order_item poi ON poi.purchase_order_item_id = sr.purchase_order_item_id "
                + "JOIN product p ON p.product_code = poi.product_code "
                + "JOIN category c ON c.category_id = p.category_id "
                + "WHERE poi.po_number = ? "
                + "ORDER BY sr.receipt_id DESC";
        return queryList(sql, ps -> ps.setInt(1, poNumber), StockReceiptDaoJdbc::map);
    }

    @Override
    public List<StockReceipt> findRecent(int limit) {
        String sql = "SELECT sr.receipt_id, sr.receipt_date, sr.quantity_received, "
                + "poi.purchase_order_item_id, poi.ordered_quantity, poi.purchase_price, poi.received_quantity, "
                + "p.product_code, p.name AS product_name, p.unit, p.selling_price, p.reorder_level, p.status, p.current_stock_quantity, "
                + "c.category_id, c.name AS category_name "
                + "FROM stock_receipt sr "
                + "JOIN purchase_order_item poi ON poi.purchase_order_item_id = sr.purchase_order_item_id "
                + "JOIN product p ON p.product_code = poi.product_code "
                + "JOIN category c ON c.category_id = p.category_id "
                + "ORDER BY sr.receipt_id DESC LIMIT ?";
        return queryList(sql, ps -> ps.setInt(1, Math.max(1, limit)), StockReceiptDaoJdbc::map);
    }

    private static StockReceipt map(ResultSet rs) throws SQLException {
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
        PurchaseOrderItem item = new PurchaseOrderItem(
                rs.getInt("purchase_order_item_id"),
                rs.getInt("ordered_quantity"),
                rs.getBigDecimal("purchase_price"),
                rs.getInt("received_quantity"),
                product);
        return new StockReceipt(
                rs.getInt("receipt_id"),
                rs.getDate("receipt_date").toLocalDate(),
                rs.getInt("quantity_received"),
                item);
    }
}
