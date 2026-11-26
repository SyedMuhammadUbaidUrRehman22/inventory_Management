package com.retailpos.dao;

import com.retailpos.model.Sale;
import com.retailpos.model.SalesReceipt;

import java.sql.Timestamp;
import java.util.Optional;

/** JDBC implementation of {@link SalesReceiptDao}. */
public class SalesReceiptDaoJdbc extends JdbcDao implements SalesReceiptDao {

    private final SaleDao saleDao;

    public SalesReceiptDaoJdbc() {
        this(new SaleDaoJdbc());
    }

    public SalesReceiptDaoJdbc(SaleDao saleDao) {
        this.saleDao = saleDao;
    }

    @Override
    public int insert(SalesReceipt receipt) {
        int id = insertReturningKey("INSERT INTO sales_receipt (issued_at, sale_id) VALUES (?, ?)", ps -> {
            ps.setTimestamp(1, Timestamp.valueOf(receipt.getIssuedAt()));
            ps.setInt(2, receipt.getSale().getSaleID());
        });
        receipt.setReceiptID(id);
        return id;
    }

    @Override
    public Optional<SalesReceipt> findBySaleId(int saleID) {
        Optional<int[]> row = queryOne("SELECT receipt_id FROM sales_receipt WHERE sale_id = ?",
                ps -> ps.setInt(1, saleID), rs -> new int[]{rs.getInt("receipt_id")});
        if (row.isEmpty()) {
            return Optional.empty();
        }
        Optional<Timestamp> issued = queryOne("SELECT issued_at FROM sales_receipt WHERE receipt_id = ?",
                ps -> ps.setInt(1, row.get()[0]), rs -> rs.getTimestamp("issued_at"));
        Optional<Sale> sale = saleDao.findById(saleID);
        if (issued.isEmpty() || sale.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SalesReceipt(row.get()[0], issued.get().toLocalDateTime(), sale.get()));
    }
}
