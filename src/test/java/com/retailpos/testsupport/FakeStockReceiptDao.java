package com.retailpos.testsupport;

import com.retailpos.dao.StockReceiptDao;
import com.retailpos.model.StockReceipt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** In-memory {@link StockReceiptDao} for unit tests. */
public class FakeStockReceiptDao implements StockReceiptDao {

    private final Map<Integer, StockReceipt> receipts = new TreeMap<>();
    private int nextId = 1;

    @Override
    public int insert(StockReceipt receipt) {
        int id = nextId++;
        receipt.setReceiptID(id);
        receipts.put(id, receipt);
        return id;
    }

    @Override
    public List<StockReceipt> findByPoNumber(int poNumber) {
        List<StockReceipt> list = new ArrayList<>();
        for (StockReceipt sr : receipts.values()) {
            // Check if item belongs to poNumber (in tests we track it)
            list.add(sr);
        }
        list.sort(Comparator.comparingInt(StockReceipt::getReceiptID).reversed());
        return list;
    }

    @Override
    public List<StockReceipt> findRecent(int limit) {
        List<StockReceipt> list = new ArrayList<>(receipts.values());
        list.sort(Comparator.comparingInt(StockReceipt::getReceiptID).reversed());
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}
