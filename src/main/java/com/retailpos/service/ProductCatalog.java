package com.retailpos.service;

import com.retailpos.dao.CategoryDao;
import com.retailpos.dao.ProductDao;
import com.retailpos.dao.ProductSupplierDao;
import com.retailpos.dao.SupplierDao;
import com.retailpos.model.Category;
import com.retailpos.model.Product;
import com.retailpos.model.ProductSupplier;
import com.retailpos.model.Supplier;
import com.retailpos.model.User;
import com.retailpos.util.BusinessException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Products, categories, suppliers and supplier prices (FR1–FR6).
 */
public class ProductCatalog {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISCONTINUED = "DISCONTINUED";
    public static final List<String> STATUSES = List.of(STATUS_ACTIVE, STATUS_DISCONTINUED);

    /** Largest value that fits DECIMAL(12, 2). */
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

    private final AccessControl accessControl;
    private final ProductDao productDao;
    private final CategoryDao categoryDao;
    private final SupplierDao supplierDao;
    private final ProductSupplierDao productSupplierDao;

    public ProductCatalog(AccessControl accessControl, ProductDao productDao, CategoryDao categoryDao,
                          SupplierDao supplierDao, ProductSupplierDao productSupplierDao) {
        this.accessControl = accessControl;
        this.productDao = productDao;
        this.categoryDao = categoryDao;
        this.supplierDao = supplierDao;
        this.productSupplierDao = productSupplierDao;
    }

    // =====================================================================
    // Products
    // =====================================================================

    /**
     * FR1: Registers a new product (code, name, unit, selling price, reorder level, status).
     * A new product always starts with zero stock; stock only increases through receipts.
     * A null status defaults to ACTIVE.
     */
    public Product registerProduct(User user, Product product) {
        accessControl.requirePermission(user, AccessControl.REGISTER_PRODUCT);
        Product clean = validateProduct(product);
        if (productDao.findByCode(clean.getProductCode()).isPresent()) {
            throw new BusinessException("Product code " + clean.getProductCode() + " already exists.");
        }
        clean.setCurrentStockQuantity(0);
        productDao.insert(clean);
        return clean;
    }

    /** FR1: Updates an existing product's details. The product code and stock quantity cannot be changed here. */
    public Product updateProduct(User user, Product product) {
        accessControl.requirePermission(user, AccessControl.UPDATE_PRODUCT);
        Product clean = validateProduct(product);
        Product existing = productDao.findByCode(clean.getProductCode())
                .orElseThrow(() -> new BusinessException("Product " + clean.getProductCode() + " does not exist."));
        clean.setCurrentStockQuantity(existing.getCurrentStockQuantity());
        productDao.update(clean);
        return clean;
    }

    /**
     * FR6: Searches products by code, name or category. Every filter is optional.
     *
     * @param keyword    text matched against product code, product name and category name
     * @param categoryID only products in this category (null = all categories)
     * @param status     only products with this status (null = any status)
     */
    public List<Product> searchProducts(User user, String keyword, Integer categoryID, String status) {
        accessControl.requirePermission(user, AccessControl.SEARCH_PRODUCTS);
        if (status != null && !STATUSES.contains(status)) {
            throw new BusinessException("Unknown product status: " + status);
        }
        return productDao.search(keyword == null ? null : keyword.trim(), categoryID, status);
    }

    private Product validateProduct(Product p) {
        if (p == null) {
            throw new BusinessException("Product details are missing.");
        }
        String code = requireText(p.getProductCode(), "Product code", 30);
        String name = requireText(p.getName(), "Product name", 150);
        String unit = requireText(p.getUnit(), "Unit", 20);
        BigDecimal price = requirePositiveMoney(p.getSellingPrice(), "Selling price");
        if (p.getReorderLevel() < 0) {
            throw new BusinessException("Reorder level cannot be negative.");
        }
        String status = p.getStatus() == null ? STATUS_ACTIVE : p.getStatus();
        if (!STATUSES.contains(status)) {
            throw new BusinessException("Unknown product status: " + status);
        }
        if (p.getCategory() == null) {
            throw new BusinessException("Select a category.");
        }
        Category category = categoryDao.findById(p.getCategory().getCategoryID())
                .orElseThrow(() -> new BusinessException("The selected category does not exist."));
        return new Product(code, name, unit, price, p.getReorderLevel(), status,
                p.getCurrentStockQuantity(), category);
    }

    // =====================================================================
    // Categories
    // =====================================================================

    /** FR2: Adds a product category. Names are unique (case-insensitive). */
    public Category addCategory(User user, String name) {
        accessControl.requirePermission(user, AccessControl.ADD_CATEGORY);
        String clean = requireText(name, "Category name", 100);
        if (categoryDao.findByName(clean).isPresent()) {
            throw new BusinessException("Category \"" + clean + "\" already exists.");
        }
        Category category = new Category(0, clean);
        categoryDao.insert(category);
        return category;
    }

    /** FR2, FR6: All categories, for choosing a product's category and filtering searches. */
    public List<Category> listCategories(User user) {
        accessControl.requirePermission(user, AccessControl.SEARCH_PRODUCTS);
        return categoryDao.findAll();
    }

    // =====================================================================
    // Suppliers
    // =====================================================================

    /** FR3: Registers a supplier (name, address, contact information). */
    public Supplier registerSupplier(User user, Supplier supplier) {
        accessControl.requirePermission(user, AccessControl.REGISTER_SUPPLIER);
        Supplier clean = validateSupplier(supplier);
        supplierDao.insert(clean);
        return clean;
    }

    /** FR3: Updates a supplier's details. */
    public Supplier updateSupplier(User user, Supplier supplier) {
        accessControl.requirePermission(user, AccessControl.UPDATE_SUPPLIER);
        Supplier clean = validateSupplier(supplier);
        clean.setSupplierID(supplier.getSupplierID());
        requireSupplier(clean.getSupplierID());
        supplierDao.update(clean);
        return clean;
    }

    /** FR3: Lists suppliers, optionally filtered by name or contact information. */
    public List<Supplier> listSuppliers(User user, String keyword) {
        accessControl.requirePermission(user, AccessControl.VIEW_SUPPLIERS);
        return supplierDao.search(keyword == null ? null : keyword.trim());
    }

    private Supplier validateSupplier(Supplier s) {
        if (s == null) {
            throw new BusinessException("Supplier details are missing.");
        }
        return new Supplier(0,
                requireText(s.getName(), "Supplier name", 150),
                optionalText(s.getAddress(), "Address", 255),
                optionalText(s.getContactInfo(), "Contact information", 100));
    }

    // =====================================================================
    // Supplier prices (product <-> supplier link)
    // =====================================================================

    /**
     * FR4, FR5: Links a product to a supplier with that supplier's purchase price.
     * If the product is already linked to the supplier, its price is updated.
     */
    public ProductSupplier setSupplierPrice(User user, String productCode, int supplierID, BigDecimal purchasePrice) {
        accessControl.requirePermission(user, AccessControl.SET_SUPPLIER_PRICE);
        Product product = productDao.findByCode(requireText(productCode, "Product", 30))
                .orElseThrow(() -> new BusinessException("Product " + productCode + " does not exist."));
        Supplier supplier = requireSupplier(supplierID);
        BigDecimal price = requirePositiveMoney(purchasePrice, "Purchase price");
        ProductSupplier link = new ProductSupplier(product, supplier, price);
        productSupplierDao.save(link);
        return link;
    }

    /** FR4: Removes the link between a product and a supplier. */
    public void removeSupplierPrice(User user, String productCode, int supplierID) {
        accessControl.requirePermission(user, AccessControl.REMOVE_SUPPLIER_PRICE);
        if (!productSupplierDao.delete(productCode, supplierID)) {
            throw new BusinessException("Product " + productCode + " is not linked to this supplier.");
        }
    }

    /** FR4, FR5: The products a supplier supplies, with that supplier's purchase prices. */
    public List<ProductSupplier> getProductsForSupplier(User user, int supplierID) {
        accessControl.requirePermission(user, AccessControl.VIEW_SUPPLIERS);
        return productSupplierDao.findBySupplier(supplierID);
    }

    private Supplier requireSupplier(int supplierID) {
        return supplierDao.findById(supplierID)
                .orElseThrow(() -> new BusinessException("The selected supplier does not exist."));
    }

    // =====================================================================
    // Validation helpers
    // =====================================================================

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " is required.");
        }
        return checkLength(value.trim(), field, maxLength);
    }

    /** Optional text: blank becomes null. */
    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value.trim(), field, maxLength);
    }

    private static String checkLength(String value, String field, int maxLength) {
        if (value.length() > maxLength) {
            throw new BusinessException(field + " can be at most " + maxLength + " characters.");
        }
        return value;
    }

    private static BigDecimal requirePositiveMoney(BigDecimal value, String field) {
        if (value == null) {
            throw new BusinessException(field + " is required.");
        }
        if (value.signum() <= 0) {
            throw new BusinessException(field + " must be greater than zero.");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(field + " can have at most 2 decimal places.");
        }
        if (value.compareTo(MAX_MONEY) > 0) {
            throw new BusinessException(field + " is too large.");
        }
        return value.setScale(2);
    }
}
