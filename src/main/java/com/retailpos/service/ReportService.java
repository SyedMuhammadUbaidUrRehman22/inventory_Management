package com.retailpos.service;

import com.retailpos.dao.ProductDao;
import com.retailpos.dao.PurchaseOrderDao;
import com.retailpos.dao.ReportDao;
import com.retailpos.dao.SaleDao;
import com.retailpos.dao.StockMovementDao;
import com.retailpos.dao.SupplierDao;
import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.Report;
import com.retailpos.model.Sale;
import com.retailpos.model.SaleItem;
import com.retailpos.model.StockMovement;
import com.retailpos.model.User;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.util.BusinessException;

import javax.swing.JTable;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Service generating inventory, purchase, and sales reports (FR31–FR36).
 * Restricted to users with {@link AccessControl#GENERATE_REPORTS} (Manager role).
 */
public class ReportService {

    private final AccessControl accessControl;
    private final ProductDao productDao;
    private final PurchaseOrderDao purchaseOrderDao;
    private final SupplierDao supplierDao;
    private final ReportDao reportDao;
    private final SaleDao saleDao;
    private final StockMovementDao stockMovementDao;

    /** Reports that need sales data (FR33) are not available through this constructor. */
    public ReportService(AccessControl accessControl, ProductDao productDao,
                         PurchaseOrderDao purchaseOrderDao, SupplierDao supplierDao,
                         ReportDao reportDao) {
        this(accessControl, productDao, purchaseOrderDao, supplierDao, reportDao, null);
    }

    /** The stock-movement report (FR32) is not available through this constructor. */
    public ReportService(AccessControl accessControl, ProductDao productDao,
                         PurchaseOrderDao purchaseOrderDao, SupplierDao supplierDao,
                         ReportDao reportDao, SaleDao saleDao) {
        this(accessControl, productDao, purchaseOrderDao, supplierDao, reportDao, saleDao, null);
    }

    public ReportService(AccessControl accessControl, ProductDao productDao,
                         PurchaseOrderDao purchaseOrderDao, SupplierDao supplierDao,
                         ReportDao reportDao, SaleDao saleDao, StockMovementDao stockMovementDao) {
        this.saleDao = saleDao;
        this.stockMovementDao = stockMovementDao;
        this.accessControl = accessControl;
        this.productDao = productDao;
        this.purchaseOrderDao = purchaseOrderDao;
        this.supplierDao = supplierDao;
        this.reportDao = reportDao;
    }

    // =====================================================================
    // FR31: Low-stock report (drives reordering)
    // =====================================================================

    public record LowStockItem(
            String productCode,
            String name,
            String categoryName,
            String unit,
            BigDecimal sellingPrice,
            int currentStock,
            int reorderLevel,
            int deficit,
            String status
    ) {
    }

    public record LowStockReport(
            LocalDateTime generatedAt,
            String generatedBy,
            int totalItems,
            int outOfStockCount,
            int totalDeficitUnits,
            List<LowStockItem> items
    ) {
    }

    /**
     * FR31: Generates a low-stock report of products where current stock <= reorder level.
     */
    public LowStockReport generateLowStockReport(User user) {
        accessControl.requirePermission(user, AccessControl.GENERATE_REPORTS);

        LocalDateTime now = LocalDateTime.now();
        List<Product> lowStockProducts = productDao.findLowStock();

        int outOfStock = 0;
        int totalDeficit = 0;
        List<LowStockItem> items = new ArrayList<>();

        for (Product p : lowStockProducts) {
            int current = p.getCurrentStockQuantity();
            int reorder = p.getReorderLevel();
            int deficit = Math.max(0, reorder - current);
            if (current == 0) {
                outOfStock++;
            }
            totalDeficit += deficit;
            items.add(new LowStockItem(
                    p.getProductCode(),
                    p.getName(),
                    p.getCategory() == null ? "" : p.getCategory().getName(),
                    p.getUnit(),
                    p.getSellingPrice(),
                    current,
                    reorder,
                    deficit,
                    p.getStatus()
            ));
        }

        // Log report generation in audit table
        reportDao.insert(new Report(0, "LOW_STOCK", now));

        return new LowStockReport(now, user.getUsername(), items.size(), outOfStock, totalDeficit, items);
    }

    // =====================================================================
    // FR34: Supplier-wise purchase report
    // =====================================================================

    public record SupplierPurchaseRow(
            int poNumber,
            LocalDate orderDate,
            int supplierId,
            String supplierName,
            String status,
            int lineCount,
            int totalOrderedUnits,
            int totalReceivedUnits,
            BigDecimal totalValue
    ) {
    }

    public record SupplierWisePurchaseReport(
            LocalDateTime generatedAt,
            String generatedBy,
            LocalDate fromDate,
            LocalDate toDate,
            Integer supplierId,
            int totalOrders,
            BigDecimal totalSpend,
            int totalOrderedUnits,
            int totalReceivedUnits,
            List<SupplierPurchaseRow> rows
    ) {
    }

    /**
     * FR34: Generates a supplier-wise purchase report over an optional date range and supplier.
     */
    public SupplierWisePurchaseReport generateSupplierWisePurchaseReport(User user, LocalDate fromDate,
                                                                         LocalDate toDate, Integer supplierId) {
        accessControl.requirePermission(user, AccessControl.GENERATE_REPORTS);

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new BusinessException("From date cannot be after To date.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<PurchaseOrder> orders = purchaseOrderDao.search(supplierId, null, fromDate, toDate);

        // Sort by supplier name, then order date desc
        orders.sort(Comparator.comparing((PurchaseOrder po) -> po.getSupplier() != null ? po.getSupplier().getName() : "")
                .thenComparing(PurchaseOrder::getOrderDate, Comparator.reverseOrder()));

        List<SupplierPurchaseRow> rows = new ArrayList<>();
        BigDecimal totalSpend = BigDecimal.ZERO;
        int totalOrderedUnits = 0;
        int totalReceivedUnits = 0;

        for (PurchaseOrder po : orders) {
            int lineCount = po.getItems() != null ? po.getItems().size() : 0;
            int orderedUnits = po.getTotalOrderedQuantity();
            int receivedUnits = po.getTotalReceivedQuantity();
            BigDecimal val = po.getTotalValue() == null ? BigDecimal.ZERO : po.getTotalValue();

            totalSpend = totalSpend.add(val);
            totalOrderedUnits += orderedUnits;
            totalReceivedUnits += receivedUnits;

            rows.add(new SupplierPurchaseRow(
                    po.getPoNumber(),
                    po.getOrderDate(),
                    po.getSupplier() != null ? po.getSupplier().getSupplierID() : 0,
                    po.getSupplier() != null ? po.getSupplier().getName() : "Unknown",
                    po.getStatus().name(),
                    lineCount,
                    orderedUnits,
                    receivedUnits,
                    val
            ));
        }

        reportDao.insert(new Report(0, "SUPPLIER_PURCHASES", now));

        return new SupplierWisePurchaseReport(
                now,
                user.getUsername(),
                fromDate,
                toDate,
                supplierId,
                rows.size(),
                totalSpend,
                totalOrderedUnits,
                totalReceivedUnits,
                rows
        );
    }

    // =====================================================================
    // FR33: Daily and date-range sales report
    // =====================================================================

    /** One completed sale in the report. */
    public record SalesReportRow(
            int saleID,
            LocalDateTime dateTime,
            String cashier,
            int lineCount,
            int unitsSold,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal finalAmount
    ) {
    }

    /** Totals for one calendar day. */
    public record DailySalesRow(
            LocalDate date,
            int saleCount,
            int unitsSold,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal netSales
    ) {
    }

    public record SalesReport(
            LocalDateTime generatedAt,
            String generatedBy,
            LocalDate fromDate,
            LocalDate toDate,
            int totalSales,
            int totalUnits,
            BigDecimal totalSubtotal,
            BigDecimal totalDiscount,
            BigDecimal totalNetSales,
            List<SalesReportRow> rows,
            List<DailySalesRow> days
    ) {
    }

    /** FR33: Sales report for a single day. */
    public SalesReport generateDailySalesReport(User user, LocalDate date) {
        return generateSalesReport(user, date, date);
    }

    /**
     * FR33: Sales report over a date range, counting COMPLETED sales only.
     * If both dates are empty the report is for today; if only one is given the report is for that single day.
     * Net sales are the final amounts (after discounts).
     */
    public SalesReport generateSalesReport(User user, LocalDate fromDate, LocalDate toDate) {
        accessControl.requirePermission(user, AccessControl.GENERATE_REPORTS);
        if (saleDao == null) {
            throw new BusinessException("Sales reports are not available.");
        }
        if (fromDate == null && toDate == null) {
            fromDate = LocalDate.now();
            toDate = fromDate;
        } else if (fromDate == null) {
            fromDate = toDate;
        } else if (toDate == null) {
            toDate = fromDate;
        }
        if (fromDate.isAfter(toDate)) {
            throw new BusinessException("From date cannot be after To date.");
        }

        LocalDateTime now = LocalDateTime.now();
        List<Sale> sales = saleDao.findCompleted(fromDate, toDate);

        List<SalesReportRow> rows = new ArrayList<>();
        Map<LocalDate, DailyTotals> perDay = new TreeMap<>();
        BigDecimal totalSubtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        int totalUnits = 0;

        for (Sale sale : sales) {
            int units = sale.getTotalQuantity();
            rows.add(new SalesReportRow(sale.getSaleID(), sale.getDateTime(),
                    sale.getCashier() == null ? "" : sale.getCashier().getUsername(),
                    sale.getItems().size(), units, sale.getSubtotal(), sale.getDiscount(), sale.getFinalAmount()));
            perDay.computeIfAbsent(sale.getDateTime().toLocalDate(), d -> new DailyTotals()).add(sale, units);
            totalSubtotal = totalSubtotal.add(sale.getSubtotal());
            totalDiscount = totalDiscount.add(sale.getDiscount());
            totalNet = totalNet.add(sale.getFinalAmount());
            totalUnits += units;
        }

        List<DailySalesRow> days = new ArrayList<>();
        perDay.forEach((date, t) -> days.add(new DailySalesRow(date, t.count, t.units, t.subtotal, t.discount, t.net)));

        reportDao.insert(new Report(0, "SALES", now));

        return new SalesReport(now, user.getUsername(), fromDate, toDate, rows.size(), totalUnits,
                totalSubtotal, totalDiscount, totalNet, rows, days);
    }

    /** Running totals for one day of the sales report. */
    private static final class DailyTotals {
        private int count;
        private int units;
        private BigDecimal subtotal = BigDecimal.ZERO;
        private BigDecimal discount = BigDecimal.ZERO;
        private BigDecimal net = BigDecimal.ZERO;

        void add(Sale sale, int saleUnits) {
            count++;
            units += saleUnits;
            subtotal = subtotal.add(sale.getSubtotal());
            discount = discount.add(sale.getDiscount());
            net = net.add(sale.getFinalAmount());
        }
    }

    // =====================================================================
    // FR32: Current inventory report
    // =====================================================================

    /** One product's stock on hand. Stock value is stock × selling price. */
    public record InventoryRow(
            String productCode,
            String name,
            String categoryName,
            String unit,
            String status,
            int currentStock,
            int reorderLevel,
            boolean lowStock,
            BigDecimal sellingPrice,
            BigDecimal stockValue
    ) {
    }

    public record CurrentInventoryReport(
            LocalDateTime generatedAt,
            String generatedBy,
            int totalProducts,
            int totalUnits,
            int lowStockCount,
            BigDecimal totalStockValue,
            List<InventoryRow> rows
    ) {
    }

    /** FR32: Stock on hand for every product (active and discontinued), by product code. */
    public CurrentInventoryReport generateCurrentInventoryReport(User user) {
        accessControl.requirePermission(user, AccessControl.GENERATE_REPORTS);

        LocalDateTime now = LocalDateTime.now();
        List<InventoryRow> rows = new ArrayList<>();
        int totalUnits = 0;
        int lowStock = 0;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (Product p : productDao.search(null, null, null)) {
            int stock = p.getCurrentStockQuantity();
            boolean low = stock <= p.getReorderLevel();
            BigDecimal value = p.getSellingPrice().multiply(BigDecimal.valueOf(stock));
            rows.add(new InventoryRow(p.getProductCode(), p.getName(),
                    p.getCategory() == null ? "" : p.getCategory().getName(), p.getUnit(), p.getStatus(),
                    stock, p.getReorderLevel(), low, p.getSellingPrice(), value));
            totalUnits += stock;
            totalValue = totalValue.add(value);
            if (low) {
                lowStock++;
            }
        }

        reportDao.insert(new Report(0, "CURRENT_INVENTORY", now));
        return new CurrentInventoryReport(now, user.getUsername(), rows.size(), totalUnits, lowStock, totalValue, rows);
    }

    // =====================================================================
    // FR32: Stock-movement report
    // =====================================================================

    public record StockMovementRow(
            int movementID,
            LocalDateTime dateTime,
            String productCode,
            String productName,
            StockMovementType type,
            int quantityChange,
            int balanceAfter,
            String sourceReference,
            String responsibleUser
    ) {
    }

    public record StockMovementReport(
            LocalDateTime generatedAt,
            String generatedBy,
            LocalDate fromDate,
            LocalDate toDate,
            String productCode,
            StockMovementType type,
            int totalMovements,
            int unitsIn,
            int unitsOut,
            List<StockMovementRow> rows
    ) {
        /** Units in minus units out. */
        public int netChange() {
            return unitsIn - unitsOut;
        }
    }

    /**
     * FR32: Receipts, sales, returns and adjustments over a date range, oldest first.
     * Dates follow the sales report: both empty means today, one empty means that single day.
     *
     * @param productCode only this product, or null for all
     * @param type        only this kind of movement, or null for all
     */
    public StockMovementReport generateStockMovementReport(User user, LocalDate fromDate, LocalDate toDate,
                                                           String productCode, StockMovementType type) {
        accessControl.requirePermission(user, AccessControl.GENERATE_REPORTS);
        if (stockMovementDao == null) {
            throw new BusinessException("Stock-movement reports are not available.");
        }
        DateRange range = DateRange.of(fromDate, toDate);
        String code = productCode == null || productCode.isBlank() ? null : productCode;

        LocalDateTime now = LocalDateTime.now();
        List<StockMovementRow> rows = new ArrayList<>();
        int unitsIn = 0;
        int unitsOut = 0;
        for (StockMovement m : stockMovementDao.search(code, type, range.from(), range.to())) {
            rows.add(new StockMovementRow(m.getMovementID(), m.getDateTime(),
                    m.getProduct().getProductCode(), m.getProduct().getName(), m.getMovementType(),
                    m.getQuantityChange(), m.getBalanceAfter(), m.getSourceReference(),
                    m.getResponsibleUser() == null ? "" : m.getResponsibleUser().getUsername()));
            if (m.getQuantityChange() > 0) {
                unitsIn += m.getQuantityChange();
            } else {
                unitsOut -= m.getQuantityChange();
            }
        }

        reportDao.insert(new Report(0, "STOCK_MOVEMENTS", now));
        return new StockMovementReport(now, user.getUsername(), range.from(), range.to(), code, type,
                rows.size(), unitsIn, unitsOut, rows);
    }

    // =====================================================================
    // FR35: Product-wise sales report
    // =====================================================================

    /** One product's sales. Revenue is the sum of its line totals, before sale-level discounts. */
    public record ProductSalesRow(
            String productCode,
            String name,
            String categoryName,
            int saleCount,
            int unitsSold,
            BigDecimal revenue
    ) {
    }

    public record ProductSalesReport(
            LocalDateTime generatedAt,
            String generatedBy,
            LocalDate fromDate,
            LocalDate toDate,
            int productsSold,
            int totalUnits,
            BigDecimal totalRevenue,
            List<ProductSalesRow> rows
    ) {
    }

    /**
     * FR35: Quantity sold and revenue per product over a date range, counting COMPLETED sales only,
     * highest revenue first. Dates follow the sales report: both empty means today, one empty means that day.
     * Revenue is before the sale's fixed discount, because a discount applies to the whole sale, not one product.
     */
    public ProductSalesReport generateProductWiseSalesReport(User user, LocalDate fromDate, LocalDate toDate) {
        accessControl.requirePermission(user, AccessControl.GENERATE_REPORTS);
        if (saleDao == null) {
            throw new BusinessException("Sales reports are not available.");
        }
        DateRange range = DateRange.of(fromDate, toDate);

        LocalDateTime now = LocalDateTime.now();
        Map<String, ProductTotals> perProduct = new LinkedHashMap<>();
        for (Sale sale : saleDao.findCompleted(range.from(), range.to())) {
            for (SaleItem item : sale.getItems()) {
                Product p = item.getProduct();
                perProduct.computeIfAbsent(p.getProductCode(), k -> new ProductTotals(p)).add(sale.getSaleID(), item);
            }
        }

        List<ProductSalesRow> rows = new ArrayList<>();
        int totalUnits = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (ProductTotals t : perProduct.values()) {
            rows.add(new ProductSalesRow(t.product.getProductCode(), t.product.getName(),
                    t.product.getCategory() == null ? "" : t.product.getCategory().getName(),
                    t.saleIDs.size(), t.units, t.revenue));
            totalUnits += t.units;
            totalRevenue = totalRevenue.add(t.revenue);
        }
        rows.sort(Comparator.comparing(ProductSalesRow::revenue).reversed()
                .thenComparing(ProductSalesRow::productCode));

        reportDao.insert(new Report(0, "PRODUCT_SALES", now));
        return new ProductSalesReport(now, user.getUsername(), range.from(), range.to(), rows.size(),
                totalUnits, totalRevenue, rows);
    }

    /** Running totals for one product of the product-wise sales report. */
    private static final class ProductTotals {
        private final Product product;
        private final Set<Integer> saleIDs = new HashSet<>();
        private int units;
        private BigDecimal revenue = BigDecimal.ZERO;

        ProductTotals(Product product) {
            this.product = product;
        }

        void add(int saleID, SaleItem item) {
            saleIDs.add(saleID);
            units += item.getQuantity();
            revenue = revenue.add(item.getLineTotal());
        }
    }

    /** A report period: both dates empty means today, one empty means that single day. */
    private record DateRange(LocalDate from, LocalDate to) {
        static DateRange of(LocalDate from, LocalDate to) {
            if (from == null && to == null) {
                from = LocalDate.now();
                to = from;
            } else if (from == null) {
                from = to;
            } else if (to == null) {
                to = from;
            }
            if (from.isAfter(to)) {
                throw new BusinessException("From date cannot be after To date.");
            }
            return new DateRange(from, to);
        }
    }

    // =====================================================================
    // FR36: Print selected reports
    // =====================================================================

    /**
     * FR36: Prints a generated report table with formatted header and footer.
     * Permission-checked for Manager.
     */
    public void printReport(User user, JTable table, String reportTitle, String subtitle) {
        accessControl.requirePermission(user, AccessControl.GENERATE_REPORTS);
        if (table == null) {
            throw new BusinessException("No table to print.");
        }

        MessageFormat header = new MessageFormat(reportTitle + (subtitle == null || subtitle.isBlank() ? "" : " - " + subtitle));
        MessageFormat footer = new MessageFormat("Page {0} - Printed by " + user.getUsername());

        try {
            boolean complete = table.print(JTable.PrintMode.FIT_WIDTH, header, footer);
            if (!complete) {
                // User cancelled print dialog
            }
        } catch (PrinterException e) {
            throw new BusinessException("Print failed: " + e.getMessage());
        }
    }
}
