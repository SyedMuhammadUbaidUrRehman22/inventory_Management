package com.retailpos.service;

import com.retailpos.dao.ProductDao;
import com.retailpos.dao.SaleDao;
import com.retailpos.dao.SalesReceiptDao;
import com.retailpos.dao.StockMovementDao;
import com.retailpos.model.Cashier;
import com.retailpos.model.Product;
import com.retailpos.model.Sale;
import com.retailpos.model.SaleItem;
import com.retailpos.model.SalesReceipt;
import com.retailpos.model.StockMovement;
import com.retailpos.model.User;
import com.retailpos.model.enums.SaleStatus;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.util.BusinessException;
import com.retailpos.util.DBConnection;
import com.retailpos.util.FormatUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for making sales at the till (FR14–FR21). Restricted to users with
 * {@link AccessControl#CREATE_SALE} (Cashier role).
 * <p>
 * A sale is built in memory as a DRAFT and is written to the database only when it is finalized,
 * so a cancelled or abandoned sale leaves nothing behind. Finalizing saves the sale, its receipt,
 * the stock reductions and one stock movement per line in a single transaction.
 */
public class SaleService {

    private final AccessControl accessControl;
    private final ProductDao productDao;
    private final SaleDao saleDao;
    private final SalesReceiptDao salesReceiptDao;
    private final StockMovementDao stockMovementDao;

    public SaleService(AccessControl accessControl, ProductDao productDao, SaleDao saleDao,
                       SalesReceiptDao salesReceiptDao, StockMovementDao stockMovementDao) {
        this.accessControl = accessControl;
        this.productDao = productDao;
        this.saleDao = saleDao;
        this.salesReceiptDao = salesReceiptDao;
        this.stockMovementDao = stockMovementDao;
    }

    /** FR14, FR20: Starts a new empty DRAFT sale for the signed-in cashier. Nothing is saved yet. */
    public Sale createSale(User user) {
        accessControl.requirePermission(user, AccessControl.CREATE_SALE);
        Sale sale = new Sale();
        sale.setStatus(SaleStatus.DRAFT);
        sale.setDateTime(LocalDateTime.now());
        sale.setSubtotal(BigDecimal.ZERO);
        sale.setDiscount(BigDecimal.ZERO);
        sale.setFinalAmount(BigDecimal.ZERO);
        sale.setCashier((Cashier) user);
        return sale;
    }

    /**
     * FR16: Returns the current product (price and stock as they are now) so the screen can show
     * the available stock before the cashier adds it.
     */
    public Product getProductForSale(User user, String productCode) {
        accessControl.requirePermission(user, AccessControl.CREATE_SALE);
        return loadSellableProduct(productCode);
    }

    /**
     * FR15, FR16, FR17: Adds a product to the draft sale at its current selling price.
     * If the product is already on the sale the quantities are added together; the total
     * for the product may never exceed the stock available now.
     *
     * @return the sale line (new or updated)
     */
    public SaleItem addItem(User user, Sale sale, String productCode, int quantity) {
        accessControl.requirePermission(user, AccessControl.CREATE_SALE);
        requireOwnDraft(user, sale);
        if (quantity < 1) {
            throw new BusinessException("Quantity must be at least 1.");
        }
        Product product = loadSellableProduct(productCode);

        SaleItem existing = sale.findItem(productCode);
        int wanted = quantity + (existing == null ? 0 : existing.getQuantity());
        int available = product.getCurrentStockQuantity();
        if (wanted > available) {
            throw new BusinessException("Quantity " + wanted + " exceeds available stock (" + available
                    + ") for " + product.getName() + ". Reduce the quantity to continue.");
        }

        SaleItem line = new SaleItem(0, quantity, product.getSellingPrice(), BigDecimal.ZERO, product);
        line.recalculateLineTotal();
        sale.addItem(line);
        capDiscount(sale);
        return sale.findItem(productCode);
    }

    /** Removes a line from the draft sale. */
    public void removeItem(User user, Sale sale, String productCode) {
        accessControl.requirePermission(user, AccessControl.CREATE_SALE);
        requireOwnDraft(user, sale);
        if (!sale.removeItem(productCode)) {
            throw new BusinessException("That product is not on the sale.");
        }
        capDiscount(sale);
    }

    /**
     * FR18: Sets the fixed discount amount (zero for none) and recalculates the final amount.
     * The discount cannot be negative or more than the subtotal.
     */
    public void applyDiscount(User user, Sale sale, BigDecimal discount) {
        accessControl.requirePermission(user, AccessControl.CREATE_SALE);
        requireOwnDraft(user, sale);
        BigDecimal value = discount == null ? BigDecimal.ZERO : discount.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            throw new BusinessException("Discount cannot be negative.");
        }
        sale.recalculate();
        if (value.compareTo(sale.getSubtotal()) > 0) {
            throw new BusinessException("Discount cannot be more than the subtotal ("
                    + FormatUtil.money(sale.getSubtotal()) + ").");
        }
        sale.setDiscount(value);
        sale.recalculate();
    }

    /**
     * FR19, FR20, FR21: Finalizes the draft sale.
     * <ul>
     *   <li>Re-checks the stock of every line (it may have changed since the item was added).</li>
     *   <li>Saves the sale as COMPLETED with the cashier and the current date and time.</li>
     *   <li>Reduces each product's stock and records exactly one SALE stock movement per line,
     *       in the same transaction, so all of it is saved or none of it is.</li>
     *   <li>Creates the sales receipt.</li>
     * </ul>
     * A sale that is already COMPLETED is refused, so stock is never reduced twice.
     * If anything fails the draft stays a draft and can be corrected and finalized again.
     *
     * @return the receipt, which holds the completed sale
     */
    public SalesReceipt finalizeSale(User user, Sale sale) {
        accessControl.requirePermission(user, AccessControl.CREATE_SALE);
        requireOwnDraft(user, sale);
        if (sale.getItems().isEmpty()) {
            throw new BusinessException("Add at least one item before finalizing the sale.");
        }
        sale.recalculate();
        if (sale.getDiscount().compareTo(sale.getSubtotal()) > 0) {
            throw new BusinessException("Discount cannot be more than the subtotal ("
                    + FormatUtil.money(sale.getSubtotal()) + ").");
        }

        LocalDateTime now = LocalDateTime.now();
        // Work on a copy, so a failed (rolled back) attempt leaves the draft exactly as it was.
        List<SaleItem> lines = new ArrayList<>();
        for (SaleItem item : sale.getItems()) {
            lines.add(new SaleItem(0, item.getQuantity(), item.getSellingPrice(), item.getLineTotal(),
                    item.getProduct()));
        }
        Sale completed = new Sale(0, now, SaleStatus.COMPLETED, sale.getSubtotal(), sale.getDiscount(),
                sale.getFinalAmount(), sale.getCashier(), lines);

        SalesReceipt receipt = DBConnection.inTransaction(() -> {
            // 1. Re-check stock for every line before changing anything.
            List<Product> current = new ArrayList<>();
            for (SaleItem line : lines) {
                Product product = loadSellableProduct(line.getProduct().getProductCode());
                if (line.getQuantity() > product.getCurrentStockQuantity()) {
                    throw new BusinessException("Not enough stock for " + product.getName() + ": "
                            + line.getQuantity() + " on the sale but only " + product.getCurrentStockQuantity()
                            + " available. Reduce the quantity to continue.");
                }
                current.add(product);
            }

            // 2. Save the completed sale and its lines.
            saleDao.insert(completed);

            // 3. Reduce stock and record one movement per line.
            for (int i = 0; i < lines.size(); i++) {
                SaleItem line = lines.get(i);
                Product product = current.get(i);
                int balanceAfter = product.getCurrentStockQuantity() - line.getQuantity();
                productDao.updateStock(product.getProductCode(), balanceAfter);
                stockMovementDao.insert(new StockMovement(0, StockMovementType.SALE, -line.getQuantity(), now,
                        FormatUtil.saleId(completed.getSaleID()), balanceAfter, user, product));
            }

            // 4. Receipt.
            SalesReceipt created = new SalesReceipt(0, now, completed);
            salesReceiptDao.insert(created);
            return created;
        });

        // Committed: bring the cashier's draft in line with what was saved.
        sale.setSaleID(completed.getSaleID());
        sale.setDateTime(now);
        sale.setStatus(SaleStatus.COMPLETED);
        for (int i = 0; i < lines.size(); i++) {
            sale.getItems().get(i).setSaleItemID(lines.get(i).getSaleItemID());
        }
        return receipt;
    }

    /** FR21: Returns the receipt of a completed sale, for reprinting. */
    public SalesReceipt getReceipt(User user, int saleID) {
        accessControl.requirePermission(user, AccessControl.CREATE_SALE);
        return salesReceiptDao.findBySaleId(saleID)
                .orElseThrow(() -> new BusinessException("No receipt found for sale " + FormatUtil.saleId(saleID) + "."));
    }

    // ---- helpers ----

    private Product loadSellableProduct(String productCode) {
        Product product = productDao.findByCode(productCode)
                .orElseThrow(() -> new BusinessException("Product " + productCode + " not found."));
        if (!ProductCatalog.STATUS_ACTIVE.equals(product.getStatus())) {
            throw new BusinessException(product.getName() + " is not active and cannot be sold.");
        }
        return product;
    }

    /** A cashier may change only their own sale, and only while it is still a draft. */
    private static void requireOwnDraft(User user, Sale sale) {
        if (sale == null) {
            throw new BusinessException("Start a new sale first.");
        }
        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new BusinessException("This sale is already completed and cannot be changed.");
        }
        if (sale.getCashier() == null || sale.getCashier().getUserID() != user.getUserID()) {
            throw new BusinessException("You can only change your own sale.");
        }
    }

    /** If removing items made the discount larger than the subtotal, lower it to the subtotal. */
    private static void capDiscount(Sale sale) {
        sale.recalculate();
        if (sale.getDiscount().compareTo(sale.getSubtotal()) > 0) {
            sale.setDiscount(sale.getSubtotal());
            sale.recalculate();
        }
    }
}
