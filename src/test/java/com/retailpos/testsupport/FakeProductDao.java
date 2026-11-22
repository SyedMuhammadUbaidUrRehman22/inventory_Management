package com.retailpos.testsupport;

import com.retailpos.dao.ProductDao;
import com.retailpos.model.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * In-memory {@link ProductDao} for unit tests. Stores copies, and {@link #update}
 * ignores the stock quantity, matching the JDBC implementation.
 */
public class FakeProductDao implements ProductDao {

    private final Map<String, Product> products = new TreeMap<>();

    @Override
    public Optional<Product> findByCode(String productCode) {
        return Optional.ofNullable(products.get(productCode)).map(FakeProductDao::copy);
    }

    @Override
    public List<Product> search(String keyword, Integer categoryID, String status) {
        List<Product> result = new ArrayList<>();
        for (Product p : products.values()) {
            String kw = keyword == null ? null : keyword.toLowerCase();
            boolean keywordOk = kw == null || kw.isBlank()
                    || p.getProductCode().toLowerCase().contains(kw)
                    || p.getName().toLowerCase().contains(kw)
                    || p.getCategory().getName().toLowerCase().contains(kw);
            boolean categoryOk = categoryID == null || p.getCategory().getCategoryID() == categoryID;
            boolean statusOk = status == null || status.equals(p.getStatus());
            if (keywordOk && categoryOk && statusOk) {
                result.add(copy(p));
            }
        }
        return result;
    }

    @Override
    public void insert(Product product) {
        products.put(product.getProductCode(), copy(product));
    }

    @Override
    public void update(Product product) {
        Product stored = products.get(product.getProductCode());
        Product updated = copy(product);
        updated.setCurrentStockQuantity(stored.getCurrentStockQuantity());
        products.put(product.getProductCode(), updated);
    }

    /** Test helper: sets stock directly, as receipts would in later phases. */
    public void setStock(String productCode, int quantity) {
        products.get(productCode).setCurrentStockQuantity(quantity);
    }

    private static Product copy(Product p) {
        return new Product(p.getProductCode(), p.getName(), p.getUnit(), p.getSellingPrice(),
                p.getReorderLevel(), p.getStatus(), p.getCurrentStockQuantity(), p.getCategory());
    }
}
