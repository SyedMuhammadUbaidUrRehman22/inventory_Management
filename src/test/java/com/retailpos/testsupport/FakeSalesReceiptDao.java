package com.retailpos.testsupport;

import com.retailpos.dao.SalesReceiptDao;
import com.retailpos.model.SalesReceipt;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link SalesReceiptDao} for unit tests. */
public class FakeSalesReceiptDao implements SalesReceiptDao {

    private final Map<Integer, SalesReceipt> bySale = new HashMap<>();
    private int nextId = 1;

    @Override
    public int insert(SalesReceipt receipt) {
        int id = nextId++;
        receipt.setReceiptID(id);
        bySale.put(receipt.getSale().getSaleID(), receipt);
        return id;
    }

    @Override
    public Optional<SalesReceipt> findBySaleId(int saleID) {
        return Optional.ofNullable(bySale.get(saleID));
    }

    public int count() {
        return bySale.size();
    }
}
