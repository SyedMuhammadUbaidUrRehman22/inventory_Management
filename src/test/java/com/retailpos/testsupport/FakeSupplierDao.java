package com.retailpos.testsupport;

import com.retailpos.dao.SupplierDao;
import com.retailpos.model.Supplier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory {@link SupplierDao} for unit tests. */
public class FakeSupplierDao implements SupplierDao {

    private final Map<Integer, Supplier> suppliers = new TreeMap<>();
    private int nextId = 1;

    @Override
    public Optional<Supplier> findById(int supplierID) {
        return Optional.ofNullable(suppliers.get(supplierID)).map(FakeSupplierDao::copy);
    }

    @Override
    public List<Supplier> search(String keyword) {
        List<Supplier> result = new ArrayList<>();
        for (Supplier s : suppliers.values()) {
            if (keyword == null || keyword.isBlank()
                    || s.getName().toLowerCase().contains(keyword.toLowerCase())) {
                result.add(copy(s));
            }
        }
        return result;
    }

    @Override
    public void insert(Supplier supplier) {
        supplier.setSupplierID(nextId++);
        suppliers.put(supplier.getSupplierID(), copy(supplier));
    }

    @Override
    public void update(Supplier supplier) {
        suppliers.put(supplier.getSupplierID(), copy(supplier));
    }

    private static Supplier copy(Supplier s) {
        return new Supplier(s.getSupplierID(), s.getName(), s.getAddress(), s.getContactInfo());
    }
}
