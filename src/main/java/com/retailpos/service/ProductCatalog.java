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
import com.retailpos.util.SpreadsheetUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Products, categories, suppliers and supplier prices (FR1–FR6).
 */
public class ProductCatalog {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISCONTINUED = "DISCONTINUED";
    public static final List<String> STATUSES = List.of(STATUS_ACTIVE, STATUS_DISCONTINUED);

    /** Largest value that fits DECIMAL(12, 2). */
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

    /** Most products one import file may add. */
    public static final int MAX_IMPORT_ROWS = 2000;
    /** Column names in row 1 of an import file, in template order. Stock is deliberately not a column. */
    public static final List<String> IMPORT_COLUMNS = List.of(
            "Product code", "Product name", "Category", "Unit", "Selling price", "Reorder level", "Status");
    private static final List<String> REQUIRED_IMPORT_COLUMNS = IMPORT_COLUMNS.subList(0, 5);
    /** Accepted spellings of each column, compared without case, spaces or punctuation. */
    private static final Map<String, String> IMPORT_HEADER_ALIASES = Map.ofEntries(
            Map.entry("productcode", "Product code"), Map.entry("code", "Product code"),
            Map.entry("productname", "Product name"), Map.entry("name", "Product name"),
            Map.entry("category", "Category"), Map.entry("categoryname", "Category"),
            Map.entry("unit", "Unit"),
            Map.entry("sellingprice", "Selling price"), Map.entry("sellingpricers", "Selling price"),
            Map.entry("price", "Selling price"), Map.entry("pricers", "Selling price"),
            Map.entry("reorderlevel", "Reorder level"), Map.entry("reorder", "Reorder level"),
            Map.entry("status", "Status"), Map.entry("currentstatus", "Status"));
    private static final List<String> STOCK_HEADERS =
            List.of("stock", "currentstock", "stockquantity", "currentstockquantity", "quantity", "qty");

    /**
     * What an import file would add. Nothing is saved until {@link #importProducts} is called.
     *
     * @param products the products that would be added (empty if there are errors in the header row)
     * @param errors   problems that stop the import, e.g. "Row 4: Selling price must be greater than zero."
     * @param notes    things the user should know that do not stop the import (e.g. ignored columns)
     */
    public record ImportPreview(List<Product> products, List<String> errors, List<String> notes) {
        public boolean canImport() {
            return errors.isEmpty() && !products.isEmpty();
        }
    }

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

    // =====================================================================
    // Import products from Excel (Manager and Storekeeper)
    // =====================================================================

    /**
     * FR1 (bulk): Reads new products from the first sheet of an Excel file and checks every row,
     * using the same rules as {@link #registerProduct}. Nothing is saved.
     * Row 1 must hold the column names (see {@link #IMPORT_COLUMNS}); Reorder level and Status are optional.
     * Every category must already exist. Product codes must be new: an import never changes existing products.
     */
    public ImportPreview previewImport(User user, InputStream file) {
        accessControl.requirePermission(user, AccessControl.IMPORT_PRODUCTS);
        SpreadsheetUtil.Table table = SpreadsheetUtil.readFirstSheet(file, MAX_IMPORT_ROWS);
        List<String> errors = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<Product> products = new ArrayList<>();

        Map<String, Integer> columns = importColumns(table.headers(), errors, notes);
        if (!errors.isEmpty()) {
            return new ImportPreview(List.of(), errors, notes);
        }
        if (table.rows().isEmpty()) {
            errors.add("The file has no products below the header row.");
            return new ImportPreview(List.of(), errors, notes);
        }
        if (table.rows().size() > MAX_IMPORT_ROWS) {
            errors.add("The file has more than " + MAX_IMPORT_ROWS + " products. Split it into smaller files.");
            return new ImportPreview(List.of(), errors, notes);
        }

        Map<String, Category> categories = new HashMap<>();
        for (Category c : categoryDao.findAll()) {
            categories.put(c.getName().toLowerCase(Locale.ROOT), c);
        }
        Map<String, Integer> rowOfCode = new HashMap<>();
        for (SpreadsheetUtil.TableRow row : table.rows()) {
            try {
                Product p = validateProduct(productFromRow(row, columns, categories));
                String key = p.getProductCode().toLowerCase(Locale.ROOT);
                Integer firstRow = rowOfCode.putIfAbsent(key, row.rowNumber());
                if (firstRow != null) {
                    throw new BusinessException("Product code " + p.getProductCode() + " is also on row " + firstRow + ".");
                }
                if (productDao.findByCode(p.getProductCode()).isPresent()) {
                    throw new BusinessException("Product code " + p.getProductCode()
                            + " already exists. An import only adds new products.");
                }
                p.setCurrentStockQuantity(0);
                products.add(p);
            } catch (BusinessException e) {
                errors.add("Row " + row.rowNumber() + ": " + e.getMessage());
            }
        }
        return new ImportPreview(errors.isEmpty() ? products : List.of(), errors, notes);
    }

    /**
     * FR1 (bulk): Adds the products from {@link #previewImport} in one database transaction,
     * so either all of them are added or none. Every product starts with zero stock.
     *
     * @return the number of products added
     */
    public int importProducts(User user, List<Product> products) {
        accessControl.requirePermission(user, AccessControl.IMPORT_PRODUCTS);
        if (products == null || products.isEmpty()) {
            throw new BusinessException("There are no products to import.");
        }
        if (products.size() > MAX_IMPORT_ROWS) {
            throw new BusinessException("At most " + MAX_IMPORT_ROWS + " products can be imported at once.");
        }
        List<Product> clean = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();
        for (Product p : products) {
            Product c = validateProduct(p);
            if (seen.put(c.getProductCode().toLowerCase(Locale.ROOT), true) != null) {
                throw new BusinessException("Product code " + c.getProductCode() + " appears twice. Nothing was imported.");
            }
            // Checked again: someone may have added the same code since the preview.
            if (productDao.findByCode(c.getProductCode()).isPresent()) {
                throw new BusinessException("Product code " + c.getProductCode()
                        + " was added by someone else in the meantime. Nothing was imported; check the file again.");
            }
            c.setCurrentStockQuantity(0);
            clean.add(c);
        }
        productDao.insertAll(clean);
        return clean.size();
    }

    /**
     * Writes an empty import file: a "Products" sheet with the column names in row 1,
     * and a "Help" sheet with the rules and the categories that can be used.
     */
    public void writeImportTemplate(User user, OutputStream out) {
        accessControl.requirePermission(user, AccessControl.IMPORT_PRODUCTS);
        List<String> help = new ArrayList<>(List.of(
                "How to fill in the Products sheet",
                "",
                "Row 1 holds the column names. Put one product per row below it.",
                "Product code (required): a new, unique code, at most 30 characters, e.g. P-1001.",
                "Product name (required): at most 150 characters.",
                "Category (required): must be one of the categories listed below.",
                "Unit (required): e.g. pcs, kg, litre, box (at most 20 characters).",
                "Selling price (required): in rupees, greater than 0, at most 2 decimals, e.g. 350.50.",
                "Reorder level (optional): a whole number, 0 or more. Empty means 0.",
                "Status (optional): Active or Discontinued. Empty means Active.",
                "",
                "Stock is not imported: new products start with 0 stock, which only changes through stock receipts.",
                "Existing product codes are not changed: an import only adds new products.",
                "If any row has a problem, nothing is imported. Fix the file and import it again.",
                "",
                "Categories you can use:"));
        for (Category c : categoryDao.findAll()) {
            help.add("  " + c.getName());
        }
        try {
            SpreadsheetUtil.writeTemplate(out, "Products", IMPORT_COLUMNS, "Help", help);
        } catch (IOException e) {
            throw new BusinessException("The template could not be saved: " + e.getMessage());
        }
    }

    /** Maps each known column to its index; records missing required columns as errors. */
    private static Map<String, Integer> importColumns(List<String> headers, List<String> errors, List<String> notes) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        List<String> ignored = new ArrayList<>();
        boolean stockIgnored = false;
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).trim();
            if (header.isEmpty()) {
                continue;
            }
            String key = header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            String column = IMPORT_HEADER_ALIASES.get(key);
            if (column != null) {
                if (columns.putIfAbsent(column, i) != null) {
                    errors.add("The column \"" + column + "\" appears twice in row 1.");
                }
            } else if (STOCK_HEADERS.contains(key)) {
                stockIgnored = true;
            } else {
                ignored.add(header);
            }
        }
        for (String required : REQUIRED_IMPORT_COLUMNS) {
            if (!columns.containsKey(required)) {
                errors.add("Row 1 has no \"" + required + "\" column. Use the template: row 1 must hold the column names.");
            }
        }
        if (stockIgnored) {
            notes.add("The stock column was ignored: new products start with 0 stock, "
                    + "which only changes through stock receipts.");
        }
        if (!ignored.isEmpty()) {
            notes.add("Ignored column" + (ignored.size() > 1 ? "s" : "") + ": " + String.join(", ", ignored) + ".");
        }
        return columns;
    }

    /** Turns one row into a Product (not yet validated). */
    private static Product productFromRow(SpreadsheetUtil.TableRow row, Map<String, Integer> columns,
                                          Map<String, Category> categories) {
        String categoryName = cellText(row, columns, "Category");
        if (categoryName == null) {
            throw new BusinessException("Category is required.");
        }
        Category category = categories.get(categoryName.toLowerCase(Locale.ROOT));
        if (category == null) {
            throw new BusinessException("Category \"" + categoryName + "\" does not exist. "
                    + "Use one of the categories on the template's Help sheet.");
        }
        return new Product(
                cellText(row, columns, "Product code"),
                cellText(row, columns, "Product name"),
                cellText(row, columns, "Unit"),
                cellMoney(row, columns, "Selling price"),
                cellWholeNumber(row, columns, "Reorder level"),
                cellStatus(row, columns),
                0,
                category);
    }

    private static Object cell(SpreadsheetUtil.TableRow row, Map<String, Integer> columns, String column) {
        Integer index = columns.get(column);
        return index == null ? null : row.value(index);
    }

    private static String cellText(SpreadsheetUtil.TableRow row, Map<String, Integer> columns, String column) {
        Object v = cell(row, columns, column);
        if (v instanceof BigDecimal number) {
            return number.stripTrailingZeros().toPlainString();   // a code typed as 1001 stays "1001"
        }
        return v == null ? null : v.toString().trim();
    }

    private static BigDecimal cellMoney(SpreadsheetUtil.TableRow row, Map<String, Integer> columns, String column) {
        Object v = cell(row, columns, column);
        if (v == null || v instanceof BigDecimal) {
            return (BigDecimal) v;
        }
        String text = v.toString().replaceFirst("(?i)^rs\\.?", "").replace(",", "").trim();
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new BusinessException(column + " \"" + v + "\" is not a number.");
        }
    }

    private static int cellWholeNumber(SpreadsheetUtil.TableRow row, Map<String, Integer> columns, String column) {
        Object v = cell(row, columns, column);
        if (v == null) {
            return 0;
        }
        try {
            BigDecimal number = v instanceof BigDecimal b ? b : new BigDecimal(v.toString().replace(",", "").trim());
            return number.intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new BusinessException(column + " \"" + v + "\" must be a whole number.");
        }
    }

    private static String cellStatus(SpreadsheetUtil.TableRow row, Map<String, Integer> columns) {
        String text = cellText(row, columns, "Status");
        if (text == null) {
            return STATUS_ACTIVE;
        }
        String status = text.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new BusinessException("Status \"" + text + "\" must be Active or Discontinued.");
        }
        return status;
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
