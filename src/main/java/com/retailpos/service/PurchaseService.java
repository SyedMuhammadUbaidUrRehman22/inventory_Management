package com.retailpos.service;

import com.retailpos.dao.ProductDao;
import com.retailpos.dao.ProductSupplierDao;
import com.retailpos.dao.PurchaseOrderDao;
import com.retailpos.dao.SupplierDao;
import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.Supplier;
import com.retailpos.model.User;
import com.retailpos.model.enums.PurchaseOrderStatus;
import com.retailpos.util.BusinessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service managing purchase orders and supplier procurement (FR7–FR10).
 * Restricted to users with {@link AccessControl#MANAGE_PURCHASE_ORDERS} (Manager role).
 */
public class PurchaseService {

    private final AccessControl accessControl;
    private final PurchaseOrderDao purchaseOrderDao;
    private final SupplierDao supplierDao;
    private final ProductDao productDao;
    private final ProductSupplierDao productSupplierDao;

    public PurchaseService(AccessControl accessControl, PurchaseOrderDao purchaseOrderDao,
                           SupplierDao supplierDao, ProductDao productDao,
                           ProductSupplierDao productSupplierDao) {
        this.accessControl = accessControl;
        this.purchaseOrderDao = purchaseOrderDao;
        this.supplierDao = supplierDao;
        this.productDao = productDao;
        this.productSupplierDao = productSupplierDao;
    }

    /**
     * FR7, FR8, FR9, FR10: Creates a new purchase order for a single supplier.
     *
     * @param user          the user creating the order (Manager only)
     * @param supplierId    supplier for the order
     * @param orderDate     date of order
     * @param items         items ordered (quantity > 0, price >= 0)
     * @param markAsOrdered if true, saved directly as ORDERED; otherwise PREPARED
     * @return the created purchase order with generated poNumber
     */
    public PurchaseOrder createPurchaseOrder(User user, int supplierId, LocalDate orderDate,
                                             List<PurchaseOrderItem> items, boolean markAsOrdered) {
        accessControl.requirePermission(user, AccessControl.MANAGE_PURCHASE_ORDERS);

        Supplier supplier = supplierDao.findById(supplierId)
                .orElseThrow(() -> new BusinessException("Supplier not found (ID " + supplierId + ")."));

        if (orderDate == null) {
            throw new BusinessException("Order date is required.");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("A purchase order must contain at least one product line.");
        }

        Set<String> seenProducts = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            PurchaseOrderItem item = items.get(i);
            if (item.getProduct() == null || item.getProduct().getProductCode() == null) {
                throw new BusinessException("Item on line " + (i + 1) + " is missing product details.");
            }
            String code = item.getProduct().getProductCode().trim();
            if (!seenProducts.add(code)) {
                throw new BusinessException("Duplicate product on order: " + code + ". Combine quantities into one line.");
            }
            if (item.getOrderedQuantity() <= 0) {
                throw new BusinessException("Ordered quantity must be greater than zero for " + code + ".");
            }
            if (item.getPurchasePrice() == null || item.getPurchasePrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("Purchase price cannot be negative for " + code + ".");
            }
            // Ensure product exists
            Product product = productDao.findByCode(code)
                    .orElseThrow(() -> new BusinessException("Product " + code + " does not exist."));
            item.setProduct(product);
            item.setReceivedQuantity(0);
        }

        PurchaseOrderStatus status = markAsOrdered ? PurchaseOrderStatus.ORDERED : PurchaseOrderStatus.PREPARED;
        PurchaseOrder order = new PurchaseOrder(0, orderDate, status, BigDecimal.ZERO, supplier, items);
        order.calculateTotal();

        int poNumber = purchaseOrderDao.insert(order);
        order.setPoNumber(poNumber);
        return order;
    }

    /**
     * FR10: Marks a PREPARED order as officially ORDERED with the supplier.
     */
    public void markOrdered(User user, int poNumber) {
        accessControl.requirePermission(user, AccessControl.MANAGE_PURCHASE_ORDERS);
        PurchaseOrder order = getOrder(poNumber);
        if (order.getStatus() != PurchaseOrderStatus.PREPARED) {
            throw new BusinessException("Only a PREPARED order can be marked as ORDERED. Current status: " + order.getStatus());
        }
        order.markOrdered();
        purchaseOrderDao.updateStatus(poNumber, PurchaseOrderStatus.ORDERED);
    }

    /**
     * FR10: Cancels a purchase order.
     * Can only be cancelled before any stock has been received against it.
     */
    public void cancelOrder(User user, int poNumber) {
        accessControl.requirePermission(user, AccessControl.MANAGE_PURCHASE_ORDERS);
        PurchaseOrder order = getOrder(poNumber);
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            return;
        }
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED || order.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException("Cannot cancel order " + poNumber + ": stock has already been received.");
        }
        if (order.getItems() != null) {
            for (PurchaseOrderItem item : order.getItems()) {
                if (item.getReceivedQuantity() > 0) {
                    throw new BusinessException("Cannot cancel order " + poNumber + ": stock has already been received.");
                }
            }
        }
        order.cancel();
        purchaseOrderDao.updateStatus(poNumber, PurchaseOrderStatus.CANCELLED);
    }

    /** Finds a purchase order by PO number, or throws BusinessException if not found. */
    public PurchaseOrder getOrder(int poNumber) {
        return purchaseOrderDao.findByNumber(poNumber)
                .orElseThrow(() -> new BusinessException("Purchase order PO-" + poNumber + " not found."));
    }

    /** Returns all purchase orders. */
    public List<PurchaseOrder> getAllOrders() {
        return purchaseOrderDao.findAll();
    }

    /** Searches purchase orders by criteria. */
    public List<PurchaseOrder> searchOrders(Integer supplierId, PurchaseOrderStatus status, LocalDate from, LocalDate to) {
        return purchaseOrderDao.search(supplierId, status, from, to);
    }

    /** Returns orders available for stock receipt (ORDERED or PARTIALLY_RECEIVED). */
    public List<PurchaseOrder> getReceivableOrders() {
        return purchaseOrderDao.findReceivable();
    }

    /** Returns the purchase price linked to this supplier for a product, if registered. */
    public Optional<BigDecimal> getSupplierPrice(String productCode, int supplierId) {
        return productSupplierDao.findBySupplier(supplierId).stream()
                .filter(ps -> ps.getProduct() != null && ps.getProduct().getProductCode().equalsIgnoreCase(productCode))
                .map(com.retailpos.model.ProductSupplier::getPurchasePrice)
                .findFirst();
    }
}
