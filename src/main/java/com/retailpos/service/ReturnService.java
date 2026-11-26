package com.retailpos.service;

import com.retailpos.dao.ProductDao;
import com.retailpos.dao.ReturnDao;
import com.retailpos.dao.SaleDao;
import com.retailpos.dao.StockMovementDao;
import com.retailpos.model.Product;
import com.retailpos.model.Return;
import com.retailpos.model.Sale;
import com.retailpos.model.SaleItem;
import com.retailpos.model.StockMovement;
import com.retailpos.model.User;
import com.retailpos.model.enums.SaleStatus;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.util.BusinessException;
import com.retailpos.util.DBConnection;
import com.retailpos.util.FormatUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for customer returns (FR22–FR24). Restricted to users with {@link AccessControl#RECORD_RETURN}
 * (Cashier role).
 * <p>
 * A return is recorded against one line ({@link SaleItem}) of a COMPLETED sale. Stock goes back up only
 * when the return is accepted AND the item is reusable; that increase and its RETURN stock movement are
 * saved in the same transaction as the return itself.
 * <p>
 * Only accepted returns count as "returned": a rejected return means the customer kept the goods,
 * so those units can still be returned later.
 */
public class ReturnService {

    /** Longest reason the database can store. */
    public static final int REASON_MAX = 255;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final AccessControl accessControl;
    private final SaleDao saleDao;
    private final ReturnDao returnDao;
    private final ProductDao productDao;
    private final StockMovementDao stockMovementDao;

    public ReturnService(AccessControl accessControl, SaleDao saleDao, ReturnDao returnDao,
                         ProductDao productDao, StockMovementDao stockMovementDao) {
        this.accessControl = accessControl;
        this.saleDao = saleDao;
        this.returnDao = returnDao;
        this.productDao = productDao;
        this.stockMovementDao = stockMovementDao;
    }

    /** One line of a sale as seen from the returns screen. */
    public record ReturnableLine(SaleItem item, int returnedQuantity) {
        /** Units of this line that can still be returned. */
        public int returnableQuantity() {
            return item.getQuantity() - returnedQuantity;
        }
    }

    /** A completed sale, how much of each line was already returned, and the returns recorded so far. */
    public record SaleForReturn(Sale sale, List<ReturnableLine> lines, List<Return> returns) {
    }

    /**
     * FR22: Finds a completed sale by the number on its receipt (e.g. "SL-00012") so items can be
     * returned against it. Each line shows how many units were already returned.
     */
    public SaleForReturn findSaleForReturn(User user, String saleNumber) {
        accessControl.requirePermission(user, AccessControl.RECORD_RETURN);
        return loadCompletedSale(FormatUtil.parseSaleId(saleNumber));
    }

    /**
     * FR22, FR23, FR24: Records a return for one or more lines of a completed sale, all with the same
     * reason, date and decision.
     * <ul>
     *   <li>The sale must be COMPLETED (FR22).</li>
     *   <li>Each line's quantity cannot exceed what was sold minus what was already returned (accepted).</li>
     *   <li>A reason and a return date (not in the future, not before the sale) are required (FR23).</li>
     *   <li>Only an accepted return can be marked reusable; accepted AND reusable returns are put back
     *       into stock with one RETURN stock movement each, in the same transaction (FR24).</li>
     * </ul>
     *
     * @param quantities saleItemID → quantity returned now; lines with 0 are skipped
     * @return the returns recorded, one per line, in the order of the sale's lines
     */
    public List<Return> recordReturn(User user, int saleID, Map<Integer, Integer> quantities, String reason,
                                     LocalDate returnDate, boolean accepted, boolean reusable) {
        accessControl.requirePermission(user, AccessControl.RECORD_RETURN);
        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            throw new BusinessException("Enter the reason for the return.");
        }
        if (cleanReason.length() > REASON_MAX) {
            throw new BusinessException("Reason can be at most " + REASON_MAX + " characters.");
        }
        if (returnDate == null) {
            throw new BusinessException("Return date is required.");
        }
        if (returnDate.isAfter(LocalDate.now())) {
            throw new BusinessException("Return date cannot be in the future.");
        }
        if (reusable && !accepted) {
            throw new BusinessException("Only an accepted return can be marked reusable and put back into stock.");
        }
        if (quantities == null || quantities.isEmpty()) {
            throw new BusinessException("Select at least one item and enter the quantity to return.");
        }

        return DBConnection.inTransaction(() -> {
            // Loaded inside the transaction, so two returns recorded together cannot exceed the quantity sold.
            SaleForReturn current = loadCompletedSale(saleID);
            Sale sale = current.sale();
            LocalDate saleDate = sale.getDateTime().toLocalDate();
            if (returnDate.isBefore(saleDate)) {
                throw new BusinessException("Return date cannot be before the sale date (" + saleDate.format(DAY) + ").");
            }

            Map<Integer, ReturnableLine> linesById = new HashMap<>();
            for (ReturnableLine line : current.lines()) {
                linesById.put(line.item().getSaleItemID(), line);
            }
            int totalUnits = 0;
            for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
                ReturnableLine line = linesById.get(entry.getKey());
                if (line == null) {
                    throw new BusinessException("That item is not on sale " + FormatUtil.saleId(saleID) + ".");
                }
                int qty = entry.getValue() == null ? 0 : entry.getValue();
                String name = line.item().getProduct().getName();
                if (qty < 0) {
                    throw new BusinessException("Return quantity for " + name + " cannot be negative.");
                }
                if (qty > line.returnableQuantity()) {
                    throw new BusinessException("Cannot return " + qty + " of " + name + ": " + line.item().getQuantity()
                            + " sold, " + line.returnedQuantity() + " already returned, so at most "
                            + line.returnableQuantity() + " can be returned.");
                }
                totalUnits += qty;
            }
            if (totalUnits == 0) {
                throw new BusinessException("Select at least one item and enter the quantity to return.");
            }

            List<Return> recorded = new ArrayList<>();
            for (ReturnableLine line : current.lines()) {
                Integer qty = quantities.get(line.item().getSaleItemID());
                if (qty == null || qty == 0) {
                    continue;
                }
                Return ret = new Return(0, returnDate, qty, cleanReason, accepted, reusable, line.item());
                returnDao.insert(ret);
                restockIfEligible(user, sale, ret);
                recorded.add(ret);
            }
            return recorded;
        });
    }

    // ---- helpers ----

    /** FR24: puts the returned units back into stock only if the return is accepted and the item is reusable. */
    private void restockIfEligible(User user, Sale sale, Return ret) {
        if (!ret.isAccepted() || !ret.isReusable()) {
            return;
        }
        String code = ret.getSaleItem().getProduct().getProductCode();
        Product product = productDao.findByCode(code)
                .orElseThrow(() -> new BusinessException("Product " + code + " not found."));
        int balanceAfter = product.getCurrentStockQuantity() + ret.getQuantity();
        productDao.updateStock(code, balanceAfter);
        stockMovementDao.insert(new StockMovement(0, StockMovementType.RETURN, ret.getQuantity(), LocalDateTime.now(),
                FormatUtil.saleId(sale.getSaleID()) + "/" + FormatUtil.returnId(ret.getReturnID()),
                balanceAfter, user, product));
    }

    private SaleForReturn loadCompletedSale(int saleID) {
        Sale sale = saleDao.findById(saleID)
                .orElseThrow(() -> new BusinessException("Sale " + FormatUtil.saleId(saleID) + " not found."));
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessException("Returns can only be recorded against a completed sale. Sale "
                    + FormatUtil.saleId(saleID) + " is " + FormatUtil.status(sale.getStatus().name()) + ".");
        }
        List<Return> returns = returnDao.findBySale(saleID);
        Map<Integer, Integer> returned = new HashMap<>();
        for (Return r : returns) {
            if (r.isAccepted()) {
                returned.merge(r.getSaleItem().getSaleItemID(), r.getQuantity(), Integer::sum);
            }
        }
        List<ReturnableLine> lines = new ArrayList<>();
        for (SaleItem item : sale.getItems()) {
            lines.add(new ReturnableLine(item, returned.getOrDefault(item.getSaleItemID(), 0)));
        }
        return new SaleForReturn(sale, lines, returns);
    }
}
