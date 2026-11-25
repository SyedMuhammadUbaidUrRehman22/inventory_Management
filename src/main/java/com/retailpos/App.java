package com.retailpos;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.retailpos.dao.CategoryDaoJdbc;
import com.retailpos.dao.ProductDaoJdbc;
import com.retailpos.dao.ProductSupplierDaoJdbc;
import com.retailpos.dao.PurchaseOrderDaoJdbc;
import com.retailpos.dao.ReportDaoJdbc;
import com.retailpos.dao.SaleDaoJdbc;
import com.retailpos.dao.SalesReceiptDaoJdbc;
import com.retailpos.dao.StockMovementDaoJdbc;
import com.retailpos.dao.StockReceiptDaoJdbc;
import com.retailpos.dao.SupplierDaoJdbc;
import com.retailpos.dao.UserDaoJdbc;
import com.retailpos.service.AccessControl;
import com.retailpos.service.ProductCatalog;
import com.retailpos.service.PurchaseService;
import com.retailpos.service.ReportService;
import com.retailpos.service.SaleService;
import com.retailpos.service.StockService;
import com.retailpos.ui.AppServices;
import com.retailpos.ui.LoginFrame;
import com.retailpos.util.AppSettings;

import javax.swing.SwingUtilities;
import java.util.Map;

/** Application entry point: sets the look and feel, wires DAOs into services, and opens the login screen. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        setupLookAndFeel();
        AppServices services = createServices();
        SwingUtilities.invokeLater(() -> new LoginFrame(services).setVisible(true));
    }

    /** FlatLaf light theme with the mockups' blue accent, padded table cells and uniform field heights. */
    public static void setupLookAndFeel() {
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@accentColor", "#2563EB",
                "Component.arc", "6",
                "TextComponent.arc", "6",
                "ComboBox.padding", "6,8,6,8",
                "Table.cellMargins", "2,12,2,12",
                "TableHeader.cellMargins", "2,12,2,12"));
        FlatLightLaf.setup();
    }

    /** Builds the service layer on top of the JDBC DAOs. */
    public static AppServices createServices() {
        UserDaoJdbc userDao = new UserDaoJdbc();
        ProductDaoJdbc productDao = new ProductDaoJdbc();
        CategoryDaoJdbc categoryDao = new CategoryDaoJdbc();
        SupplierDaoJdbc supplierDao = new SupplierDaoJdbc();
        ProductSupplierDaoJdbc productSupplierDao = new ProductSupplierDaoJdbc();
        PurchaseOrderDaoJdbc purchaseOrderDao = new PurchaseOrderDaoJdbc();
        StockReceiptDaoJdbc stockReceiptDao = new StockReceiptDaoJdbc();
        StockMovementDaoJdbc stockMovementDao = new StockMovementDaoJdbc();
        ReportDaoJdbc reportDao = new ReportDaoJdbc();
        SaleDaoJdbc saleDao = new SaleDaoJdbc();
        SalesReceiptDaoJdbc salesReceiptDao = new SalesReceiptDaoJdbc(saleDao);

        AccessControl accessControl = new AccessControl(userDao,
                AppSettings.get(AppSettings.MANAGER_SIGNUP_CODE));
        ProductCatalog productCatalog = new ProductCatalog(accessControl, productDao,
                categoryDao, supplierDao, productSupplierDao);
        PurchaseService purchaseService = new PurchaseService(accessControl, purchaseOrderDao,
                supplierDao, productDao, productSupplierDao);
        StockService stockService = new StockService(accessControl, purchaseOrderDao,
                productDao, stockReceiptDao, stockMovementDao);
        ReportService reportService = new ReportService(accessControl, productDao,
                purchaseOrderDao, supplierDao, reportDao, saleDao);
        SaleService saleService = new SaleService(accessControl, productDao, saleDao,
                salesReceiptDao, stockMovementDao);

        return new AppServices(accessControl, productCatalog, purchaseService, stockService, reportService,
                saleService);
    }
}
