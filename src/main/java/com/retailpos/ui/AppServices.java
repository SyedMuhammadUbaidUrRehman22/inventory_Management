package com.retailpos.ui;

import com.retailpos.service.AccessControl;
import com.retailpos.service.ProductCatalog;
import com.retailpos.service.PurchaseService;
import com.retailpos.service.ReportService;
import com.retailpos.service.StockService;

/**
 * The services the screens use; created once in {@code App.main}.
 */
public record AppServices(
        AccessControl accessControl,
        ProductCatalog productCatalog,
        PurchaseService purchaseService,
        StockService stockService,
        ReportService reportService) {

    /** Backwards-compatible constructor for Phase 1 tests. */
    public AppServices(AccessControl accessControl, ProductCatalog productCatalog) {
        this(accessControl, productCatalog, null, null, null);
    }
}
