package com.retailpos.testsupport;

import com.retailpos.dao.PurchaseOrderDao;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.enums.PurchaseOrderStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** In-memory {@link PurchaseOrderDao} for unit tests. */
public class FakePurchaseOrderDao implements PurchaseOrderDao {

    private final Map<Integer, PurchaseOrder> orders = new TreeMap<>();
    private int nextPoNumber = 1;
    private int nextItemId = 1;

    @Override
    public Optional<PurchaseOrder> findByNumber(int poNumber) {
        return Optional.ofNullable(orders.get(poNumber));
    }

    @Override
    public List<PurchaseOrder> findAll() {
        return search(null, null, null, null);
    }

    @Override
    public List<PurchaseOrder> search(Integer supplierId, PurchaseOrderStatus status, LocalDate fromDate, LocalDate toDate) {
        List<PurchaseOrder> result = new ArrayList<>();
        for (PurchaseOrder po : orders.values()) {
            boolean supplierOk = supplierId == null || (po.getSupplier() != null && po.getSupplier().getSupplierID() == supplierId);
            boolean statusOk = status == null || po.getStatus() == status;
            boolean fromOk = fromDate == null || !po.getOrderDate().isBefore(fromDate);
            boolean toOk = toDate == null || !po.getOrderDate().isAfter(toDate);
            if (supplierOk && statusOk && fromOk && toOk) {
                result.add(po);
            }
        }
        result.sort(Comparator.comparingInt(PurchaseOrder::getPoNumber).reversed());
        return result;
    }

    @Override
    public List<PurchaseOrder> findReceivable() {
        List<PurchaseOrder> result = new ArrayList<>();
        for (PurchaseOrder po : orders.values()) {
            if (po.getStatus() == PurchaseOrderStatus.ORDERED || po.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
                result.add(po);
            }
        }
        result.sort(Comparator.comparingInt(PurchaseOrder::getPoNumber));
        return result;
    }

    @Override
    public int insert(PurchaseOrder order) {
        int poNum = nextPoNumber++;
        order.setPoNumber(poNum);
        if (order.getItems() != null) {
            for (PurchaseOrderItem item : order.getItems()) {
                if (item.getPurchaseOrderItemID() == 0) {
                    item.setPurchaseOrderItemID(nextItemId++);
                }
            }
        }
        orders.put(poNum, order);
        return poNum;
    }

    @Override
    public void updateStatus(int poNumber, PurchaseOrderStatus status) {
        PurchaseOrder po = orders.get(poNumber);
        if (po != null) {
            po.setStatus(status);
        }
    }

    @Override
    public void updateItemReceivedQuantity(int purchaseOrderItemId, int newReceivedQuantity) {
        for (PurchaseOrder po : orders.values()) {
            if (po.getItems() != null) {
                for (PurchaseOrderItem item : po.getItems()) {
                    if (item.getPurchaseOrderItemID() == purchaseOrderItemId) {
                        item.setReceivedQuantity(newReceivedQuantity);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public Optional<PurchaseOrderItem> findItemById(int purchaseOrderItemId) {
        for (PurchaseOrder po : orders.values()) {
            if (po.getItems() != null) {
                for (PurchaseOrderItem item : po.getItems()) {
                    if (item.getPurchaseOrderItemID() == purchaseOrderItemId) {
                        return Optional.of(item);
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<PurchaseOrderItem> findItemsByPoNumber(int poNumber) {
        PurchaseOrder po = orders.get(poNumber);
        return po == null || po.getItems() == null ? List.of() : new ArrayList<>(po.getItems());
    }
}
