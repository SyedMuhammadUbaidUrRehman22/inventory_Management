package com.retailpos.testsupport;

import com.retailpos.dao.StockMovementDao;
import com.retailpos.model.StockMovement;
import com.retailpos.model.enums.StockMovementType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** In-memory {@link StockMovementDao} for unit tests. */
public class FakeStockMovementDao implements StockMovementDao {

    private final Map<Integer, StockMovement> movements = new TreeMap<>();
    private int nextId = 1;

    @Override
    public int insert(StockMovement movement) {
        int id = nextId++;
        movement.setMovementID(id);
        movements.put(id, movement);
        return id;
    }

    @Override
    public List<StockMovement> findByProduct(String productCode) {
        List<StockMovement> list = new ArrayList<>();
        for (StockMovement sm : movements.values()) {
            if (sm.getProduct() != null && productCode.equalsIgnoreCase(sm.getProduct().getProductCode())) {
                list.add(sm);
            }
        }
        list.sort(Comparator.comparing(StockMovement::getDateTime).reversed());
        return list;
    }

    @Override
    public List<StockMovement> findRecent(int limit) {
        List<StockMovement> list = new ArrayList<>(movements.values());
        list.sort(Comparator.comparing(StockMovement::getDateTime).reversed());
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    @Override
    public List<StockMovement> search(String productCode, StockMovementType type, LocalDate fromDate, LocalDate toDate) {
        List<StockMovement> list = new ArrayList<>();
        for (StockMovement sm : movements.values()) {
            LocalDate day = sm.getDateTime().toLocalDate();
            if ((productCode == null || productCode.equals(sm.getProduct().getProductCode()))
                    && (type == null || type == sm.getMovementType())
                    && (fromDate == null || !day.isBefore(fromDate))
                    && (toDate == null || !day.isAfter(toDate))) {
                list.add(sm);
            }
        }
        list.sort(Comparator.comparing(StockMovement::getDateTime).thenComparingInt(StockMovement::getMovementID));
        return list;
    }

    /** All movements recorded, in insertion order. */
    public List<StockMovement> all() {
        return new ArrayList<>(movements.values());
    }
}
