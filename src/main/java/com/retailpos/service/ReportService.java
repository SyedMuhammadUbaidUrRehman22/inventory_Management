package com.retailpos.service;

import com.retailpos.dao.ProductDao;
import com.retailpos.dao.PurchaseOrderDao;
import com.retailpos.dao.ReportDao;
import com.retailpos.dao.SupplierDao;
import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.Report;
import com.retailpos.model.User;
import com.retailpos.util.BusinessException;

import javax.swing.JTable;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service generating inventory, purchase, and sales reports (FR31, FR34, FR36).
 * Restricted to users with {@link AccessControl#GENERATE_REPORTS} (Manager role).
 */
public class ReportService {

    private final AccessControl accessControl;
    private final ProductDao productDao;
    private final PurchaseOrderDao purchaseOrderDao;
    private final SupplierDao supplierDao;
    private final ReportDao reportDao;

    public ReportService(AccessControl accessControl, ProductDao productDao,
                         PurchaseOrderDao purchaseOrderDao, SupplierDao supplierDao,
                         ReportDao reportDao) {
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
