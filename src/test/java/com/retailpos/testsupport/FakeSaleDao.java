package com.retailpos.testsupport;

import com.retailpos.dao.SaleDao;
import com.retailpos.model.Sale;
import com.retailpos.model.enums.SaleStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory {@link SaleDao} for unit tests. */
public class FakeSaleDao implements SaleDao {

    private final Map<Integer, Sale> sales = new TreeMap<>();
    private int nextId = 1;
    private int nextItemId = 1;

    @Override
    public int insert(Sale sale) {
        int id = nextId++;
        sale.setSaleID(id);
        sale.getItems().forEach(i -> i.setSaleItemID(nextItemId++));
        sales.put(id, sale);
        return id;
    }

    @Override
    public Optional<Sale> findById(int saleID) {
        return Optional.ofNullable(sales.get(saleID));
    }

    @Override
    public List<Sale> findCompleted(LocalDate fromDate, LocalDate toDate) {
        List<Sale> result = new ArrayList<>();
        for (Sale s : sales.values()) {
            LocalDate d = s.getDateTime().toLocalDate();
            if (s.getStatus() == SaleStatus.COMPLETED
                    && (fromDate == null || !d.isBefore(fromDate))
                    && (toDate == null || !d.isAfter(toDate))) {
                result.add(s);
            }
        }
        return result;
    }

    public int count() {
        return sales.size();
    }
}
