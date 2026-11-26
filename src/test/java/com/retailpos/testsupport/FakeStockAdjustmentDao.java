package com.retailpos.testsupport;

import com.retailpos.dao.StockAdjustmentDao;
import com.retailpos.model.StockAdjustment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** In-memory {@link StockAdjustmentDao} for unit tests. */
public class FakeStockAdjustmentDao implements StockAdjustmentDao {

    private final Map<Integer, StockAdjustment> adjustments = new TreeMap<>();
    private int nextId = 1;

    @Override
    public int insert(StockAdjustment adjustment) {
        int id = nextId++;
        adjustment.setAdjustmentID(id);
        adjustments.put(id, adjustment);
        return id;
    }

    @Override
    public List<StockAdjustment> findHistory(String productCode) {
        List<StockAdjustment> list = new ArrayList<>();
        for (StockAdjustment a : adjustments.values()) {
            if (productCode == null || productCode.equals(a.getProduct().getProductCode())) {
                list.add(a);
            }
        }
        list.sort(Comparator.comparing(StockAdjustment::getDate)
                .thenComparingInt(StockAdjustment::getAdjustmentID).reversed());
        return list;
    }

    public int count() {
        return adjustments.size();
    }
}
