package com.retailpos;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.retailpos.dao.CategoryDaoJdbc;
import com.retailpos.dao.ProductDaoJdbc;
import com.retailpos.dao.ProductSupplierDaoJdbc;
import com.retailpos.dao.SupplierDaoJdbc;
import com.retailpos.dao.UserDaoJdbc;
import com.retailpos.service.AccessControl;
import com.retailpos.service.ProductCatalog;
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
        AccessControl accessControl = new AccessControl(new UserDaoJdbc(),
                AppSettings.get(AppSettings.MANAGER_SIGNUP_CODE));
        ProductCatalog productCatalog = new ProductCatalog(accessControl, new ProductDaoJdbc(),
                new CategoryDaoJdbc(), new SupplierDaoJdbc(), new ProductSupplierDaoJdbc());
        return new AppServices(accessControl, productCatalog);
    }
}
