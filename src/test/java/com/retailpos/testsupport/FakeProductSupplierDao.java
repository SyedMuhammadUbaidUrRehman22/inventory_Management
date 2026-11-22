package com.retailpos.testsupport;

import com.retailpos.dao.ProductSupplierDao;
import com.retailpos.model.ProductSupplier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory {@link ProductSupplierDao} for unit tests; key = productCode + supplierID, like the composite PK. */
public class FakeProductSupplierDao implements ProductSupplierDao {

    private final Map<String, ProductSupplier> links = new LinkedHashMap<>();

    @Override
    public void save(ProductSupplier link) {
        links.put(key(link.getProduct().getProductCode(), link.getSupplier().getSupplierID()), link);
    }

    @Override
    public boolean delete(String productCode, int supplierID) {
        return links.remove(key(productCode, supplierID)) != null;
    }

    @Override
    public List<ProductSupplier> findBySupplier(int supplierID) {
        List<ProductSupplier> result = new ArrayList<>();
        for (ProductSupplier link : links.values()) {
            if (link.getSupplier().getSupplierID() == supplierID) {
                result.add(link);
            }
        }
        result.sort(Comparator.comparing(l -> l.getProduct().getProductCode()));
        return result;
    }

    private static String key(String productCode, int supplierID) {
        return productCode + "|" + supplierID;
    }
}
