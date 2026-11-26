package com.retailpos.testsupport;

import com.retailpos.dao.ReturnDao;
import com.retailpos.dao.SaleDao;
import com.retailpos.model.Return;
import com.retailpos.model.SaleItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** In-memory {@link ReturnDao} for unit tests. Uses the sale DAO to know which items belong to a sale. */
public class FakeReturnDao implements ReturnDao {

    private final SaleDao saleDao;
    private final Map<Integer, Return> returns = new TreeMap<>();
    private int nextId = 1;

    public FakeReturnDao(SaleDao saleDao) {
        this.saleDao = saleDao;
    }

    @Override
    public int insert(Return ret) {
        int id = nextId++;
        ret.setReturnID(id);
        returns.put(id, ret);
        return id;
    }

    @Override
    public List<Return> findBySale(int saleID) {
        Set<Integer> itemIDs = new HashSet<>();
        saleDao.findById(saleID).ifPresent(s -> s.getItems().forEach(i -> itemIDs.add(i.getSaleItemID())));
        List<Return> result = new ArrayList<>();
        for (Return r : returns.values()) {
            SaleItem item = r.getSaleItem();
            if (item != null && itemIDs.contains(item.getSaleItemID())) {
                result.add(r);
            }
        }
        return result;
    }

    public int count() {
        return returns.size();
    }
}
