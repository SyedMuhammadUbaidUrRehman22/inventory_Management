package com.retailpos.ui;

import com.retailpos.model.Product;
import com.retailpos.model.Supplier;
import com.retailpos.model.User;
import com.retailpos.model.enums.StockMovementType;
import com.retailpos.service.ReportService;
import com.retailpos.service.ReportService.CurrentInventoryReport;
import com.retailpos.service.ReportService.LowStockReport;
import com.retailpos.service.ReportService.ProductSalesReport;
import com.retailpos.service.ReportService.SalesReport;
import com.retailpos.service.ReportService.StockMovementReport;
import com.retailpos.service.ReportService.SupplierWisePurchaseReport;
import com.retailpos.util.BusinessException;
import com.retailpos.util.FormatUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Screen for generating and printing store reports (FR31–FR36).
 * Matches mockups in {@code docs/GUI_Screens/11_Reports.svg}.
 */
public class ReportsPanel extends JPanel implements Refreshable {

    private final AppServices services;
    private final User user;

    private static final String TYPE_LOW_STOCK = "LOW_STOCK";
    private static final String TYPE_INVENTORY = "CURRENT_INVENTORY";
    private static final String TYPE_MOVEMENTS = "STOCK_MOVEMENTS";
    private static final String TYPE_SUPPLIER_PURCHASES = "SUPPLIER_PURCHASES";
    private static final String TYPE_SALES = "SALES";
    private static final String TYPE_PRODUCT_SALES = "PRODUCT_SALES";
    private static final String VIEW_EACH_SALE = "Each sale";
    private static final String VIEW_BY_DAY = "By day";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DateTimeFormatter SHORT_WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private String activeReportType = TYPE_LOW_STOCK;

    // Report selection buttons
    private final JButton lowStockBtn = UiTheme.secondaryButton("Low-stock products");
    private final JButton inventoryBtn = UiTheme.secondaryButton("Current inventory");
    private final JButton movementsBtn = UiTheme.secondaryButton("Stock movements");
    private final JButton supplierPurchasesBtn = UiTheme.secondaryButton("Supplier-wise purchases");
    private final JButton salesBtn = UiTheme.secondaryButton("Sales (daily / date range)");
    private final JButton productSalesBtn = UiTheme.secondaryButton("Product-wise sales");

    // Container for dynamic report view
    private final CardLayout reportCardLayout = new CardLayout();
    private final JPanel reportCards = new JPanel(reportCardLayout);

    // ---- Low stock view components ----
    private final JLabel lowStockCountLabel = UiTheme.label("0", Font.BOLD, 22f, UiTheme.PRIMARY);
    private final JLabel outOfStockCountLabel = UiTheme.label("0", Font.BOLD, 22f, UiTheme.DANGER);
    private final JLabel totalDeficitLabel = UiTheme.label("0", Font.BOLD, 22f, new Color(217, 119, 6));
    private final DefaultTableModel lowStockTableModel;
    private final JTable lowStockTable;
    private final JLabel lowStockFooter = UiTheme.label("", Font.PLAIN, 12f, UiTheme.MUTED);

    // ---- Supplier purchases view components ----
    private final JTextField fromDateField = UiTheme.textField();
    private final JTextField toDateField = UiTheme.textField();
    private final JComboBox<SupplierFilterItem> supplierFilterCombo = new JComboBox<>();
    private final JLabel totalSpendLabel = UiTheme.label("Rs 0", Font.BOLD, 22f, UiTheme.PRIMARY);
    private final JLabel totalOrdersLabel = UiTheme.label("0", Font.BOLD, 22f, UiTheme.TEXT);
    private final JLabel totalUnitsLabel = UiTheme.label("0", Font.BOLD, 22f, new Color(217, 119, 6));
    private final DefaultTableModel supplierTableModel;
    private final JTable supplierTable;
    private final JLabel supplierFooter = UiTheme.label("", Font.PLAIN, 12f, UiTheme.MUTED);

    // ---- Sales view components (FR33) ----
    private final JTextField salesFromField = UiTheme.textField();
    private final JTextField salesToField = UiTheme.textField();
    private final JComboBox<String> salesViewCombo = new JComboBox<>(new String[]{VIEW_EACH_SALE, VIEW_BY_DAY});
    private final JLabel netSalesLabel = UiTheme.label("Rs 0", Font.BOLD, 22f, UiTheme.PRIMARY);
    private final JLabel salesCountLabel = UiTheme.label("0", Font.BOLD, 22f, UiTheme.TEXT);
    private final JLabel unitsSoldLabel = UiTheme.label("0", Font.BOLD, 22f, new Color(217, 119, 6));
    private final JLabel discountsLabel = UiTheme.label("Rs 0", Font.BOLD, 22f, UiTheme.DANGER);
    private final DefaultTableModel salesTableModel;
    private final JTable salesTable;
    private final DefaultTableModel dailyTableModel;
    private final JTable dailyTable;
    private final CardLayout salesViewLayout = new CardLayout();
    private final JPanel salesViewCards = new JPanel(salesViewLayout);
    private final JLabel salesFooter = UiTheme.label("", Font.PLAIN, 12f, UiTheme.MUTED);

    // ---- Current inventory view components (FR32) ----
    private final JLabel inventoryUnitsLabel = UiTheme.label("0", Font.BOLD, 22f, new Color(217, 119, 6));
    private final JLabel inventoryValueLabel = UiTheme.label("Rs 0", Font.BOLD, 22f, UiTheme.PRIMARY);
    private final JLabel inventoryLowLabel = UiTheme.label("0", Font.BOLD, 22f, UiTheme.DANGER);
    private final DefaultTableModel inventoryTableModel = readOnlyModel(
            "Code", "Product", "Category", "Unit", "Status", "Stock", "Reorder", "Price", "Value", "Low");
    private final JTable inventoryTable = UiTheme.table(inventoryTableModel);
    private final JLabel inventoryFooter = UiTheme.label("", Font.PLAIN, 12f, UiTheme.MUTED);

    // ---- Stock movements view components (FR32) ----
    private final JTextField movementsFromField = UiTheme.textField();
    private final JTextField movementsToField = UiTheme.textField();
    private final JComboBox<ProductFilterItem> movementsProductCombo = new JComboBox<>();
    private final JComboBox<TypeFilterItem> movementsTypeCombo = new JComboBox<>();
    private final JLabel movementsCountLabel = UiTheme.label("0", Font.BOLD, 22f, UiTheme.TEXT);
    private final JLabel movementsInLabel = UiTheme.label("0", Font.BOLD, 22f, UiTheme.SUCCESS);
    private final JLabel movementsOutLabel = UiTheme.label("0", Font.BOLD, 22f, UiTheme.DANGER);
    private final JLabel movementsNetLabel = UiTheme.label("0", Font.BOLD, 22f, UiTheme.PRIMARY);
    private final DefaultTableModel movementsTableModel = readOnlyModel(
            "Date / time", "Product", "Type", "Change", "Balance", "Reference", "User");
    private final JTable movementsTable = UiTheme.table(movementsTableModel);
    private final JLabel movementsFooter = UiTheme.label("", Font.PLAIN, 12f, UiTheme.MUTED);

    // ---- Product-wise sales view components (FR35) ----
    private final JTextField productSalesFromField = UiTheme.textField();
    private final JTextField productSalesToField = UiTheme.textField();
    private final JLabel productSalesCountLabel = UiTheme.label("0", Font.BOLD, 22f, UiTheme.TEXT);
    private final JLabel productSalesUnitsLabel = UiTheme.label("0", Font.BOLD, 22f, new Color(217, 119, 6));
    private final JLabel productSalesRevenueLabel = UiTheme.label("Rs 0", Font.BOLD, 22f, UiTheme.PRIMARY);
    private final DefaultTableModel productSalesTableModel = readOnlyModel(
            "Product Code", "Name", "Category", "Sales", "Units sold", "Revenue");
    private final JTable productSalesTable = UiTheme.table(productSalesTableModel);
    private final JLabel productSalesFooter = UiTheme.label("", Font.PLAIN, 12f, UiTheme.MUTED);

    public ReportsPanel(AppServices services, User user) {
        super(new BorderLayout(20, 0));
        this.services = services;
        this.user = user;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // Setup tables
        lowStockTableModel = new DefaultTableModel(
                new Object[]{"Product Code", "Name", "Category", "Unit", "Selling Price", "Stock", "Reorder Level", "Deficit", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        lowStockTable = UiTheme.table(lowStockTableModel);

        supplierTableModel = new DefaultTableModel(
                new Object[]{"PO #", "Date", "Supplier", "Status", "Lines", "Units Ordered", "Units Received", "Total Value"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        supplierTable = UiTheme.table(supplierTableModel);

        salesTableModel = new DefaultTableModel(
                new Object[]{"Sale", "Date / time", "Cashier", "Lines", "Units", "Subtotal", "Discount", "Final amount"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        salesTable = UiTheme.table(salesTableModel);
        dailyTableModel = new DefaultTableModel(
                new Object[]{"Date", "Sales", "Units", "Subtotal", "Discount", "Net sales"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        dailyTable = UiTheme.table(dailyTableModel);

        alignNumericColumns();

        // 1. Left Nav Sidebar
        add(buildLeftSidebar(), BorderLayout.WEST);
        styleNavButton(lowStockBtn, true);

        // 2. Right Reports Cards
        reportCards.setOpaque(false);
        reportCards.add(buildLowStockCard(), TYPE_LOW_STOCK);
        reportCards.add(buildInventoryCard(), TYPE_INVENTORY);
        reportCards.add(buildMovementsCard(), TYPE_MOVEMENTS);
        reportCards.add(buildSupplierPurchasesCard(), TYPE_SUPPLIER_PURCHASES);
        reportCards.add(buildSalesCard(), TYPE_SALES);
        reportCards.add(buildProductSalesCard(), TYPE_PRODUCT_SALES);
        add(reportCards, BorderLayout.CENTER);

        refresh();
    }

    private void alignNumericColumns() {
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);

        lowStockTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
        lowStockTable.getColumnModel().getColumn(5).setCellRenderer(rightRenderer);
        lowStockTable.getColumnModel().getColumn(6).setCellRenderer(rightRenderer);
        lowStockTable.getColumnModel().getColumn(7).setCellRenderer(rightRenderer);

        supplierTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
        supplierTable.getColumnModel().getColumn(5).setCellRenderer(rightRenderer);
        supplierTable.getColumnModel().getColumn(6).setCellRenderer(rightRenderer);
        supplierTable.getColumnModel().getColumn(7).setCellRenderer(rightRenderer);

        for (int col = 3; col <= 7; col++) {
            salesTable.getColumnModel().getColumn(col).setCellRenderer(rightRenderer);
        }
        for (int col = 1; col <= 5; col++) {
            dailyTable.getColumnModel().getColumn(col).setCellRenderer(rightRenderer);
        }
        for (int col = 5; col <= 8; col++) {
            inventoryTable.getColumnModel().getColumn(col).setCellRenderer(rightRenderer);
        }
        movementsTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
        movementsTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
        for (int col = 3; col <= 5; col++) {
            productSalesTable.getColumnModel().getColumn(col).setCellRenderer(rightRenderer);
        }
        UiTheme.columnWidths(inventoryTable, 80, 170, 95, 60, 80, 55, 65, 80, 90, 45);
        UiTheme.columnWidths(movementsTable, 125, 130, 85, 55, 60, 135, 85);
    }

    private JPanel buildLeftSidebar() {
        JPanel bar = UiTheme.card(new BorderLayout(0, 14));
        bar.setPreferredSize(new Dimension(280, 0));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.add(UiTheme.title("Report type"));
        header.add(Box.createVerticalStrut(4));
        header.add(UiTheme.label("Select a report to view and print.", Font.PLAIN, 12f, UiTheme.MUTED));
        bar.add(header, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);

        // Same order as the mockup.
        list.add(reportNavButton(lowStockBtn, "Low-stock products", "FR31 · at or below reorder level", () -> switchReport(TYPE_LOW_STOCK)));
        list.add(Box.createVerticalStrut(10));
        list.add(reportNavButton(inventoryBtn, "Current inventory", "FR32 · stock on hand per product", () -> switchReport(TYPE_INVENTORY)));
        list.add(Box.createVerticalStrut(10));
        list.add(reportNavButton(movementsBtn, "Stock movements", "FR32 · receipts, sales, returns, adjustments", () -> switchReport(TYPE_MOVEMENTS)));
        list.add(Box.createVerticalStrut(10));
        list.add(reportNavButton(salesBtn, "Sales (daily / date range)", "FR33 · completed sales and discounts", () -> switchReport(TYPE_SALES)));
        list.add(Box.createVerticalStrut(10));
        list.add(reportNavButton(supplierPurchasesBtn, "Supplier-wise purchases", "FR34 · purchase orders by supplier", () -> switchReport(TYPE_SUPPLIER_PURCHASES)));
        list.add(Box.createVerticalStrut(10));
        list.add(reportNavButton(productSalesBtn, "Product-wise sales", "FR35 · quantity and revenue per product", () -> switchReport(TYPE_PRODUCT_SALES)));
        list.add(Box.createVerticalStrut(16));
        JPanel printNote = new JPanel(new BorderLayout());
        printNote.setOpaque(false);
        printNote.add(UiTheme.label("Every report can be printed (FR36).", Font.PLAIN, 11f, UiTheme.MUTED));
        list.add(printNote);

        bar.add(list, BorderLayout.CENTER);
        return bar;
    }

    private JPanel reportNavButton(JButton btn, String title, String subtitle, Runnable action) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        btn.setText(title);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.addActionListener(e -> action.run());
        p.add(btn, BorderLayout.NORTH);
        JLabel sub = UiTheme.label(subtitle, Font.PLAIN, 11f, UiTheme.MUTED);
        sub.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        p.add(sub, BorderLayout.CENTER);
        return p;
    }

    private void switchReport(String type) {
        activeReportType = type;
        styleNavButton(lowStockBtn, type.equals(TYPE_LOW_STOCK));
        styleNavButton(inventoryBtn, type.equals(TYPE_INVENTORY));
        styleNavButton(movementsBtn, type.equals(TYPE_MOVEMENTS));
        styleNavButton(supplierPurchasesBtn, type.equals(TYPE_SUPPLIER_PURCHASES));
        styleNavButton(salesBtn, type.equals(TYPE_SALES));
        styleNavButton(productSalesBtn, type.equals(TYPE_PRODUCT_SALES));
        reportCardLayout.show(reportCards, type);
        loadActiveReport();
    }

    private static void styleNavButton(JButton button, boolean selected) {
        button.setBackground(selected ? UiTheme.PRIMARY : UiTheme.SECONDARY);
        button.setForeground(selected ? Color.WHITE : UiTheme.TEXT);
    }

    private void loadActiveReport() {
        switch (activeReportType) {
            case TYPE_LOW_STOCK -> loadLowStockReport();
            case TYPE_INVENTORY -> UiSupport.run(this, this::loadInventoryReport);
            case TYPE_MOVEMENTS -> UiSupport.run(this, this::loadMovementsReport);
            case TYPE_SUPPLIER_PURCHASES -> loadSupplierPurchasesReport();
            case TYPE_PRODUCT_SALES -> UiSupport.run(this, this::loadProductSalesReport);
            default -> loadSalesReport();
        }
    }

    // =====================================================================
    // Low Stock Card
    // =====================================================================

    private JPanel buildLowStockCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 14));

        // Header + actions
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        titles.add(UiTheme.title("Low-stock products report (FR31)"));
        titles.add(Box.createVerticalStrut(2));
        titles.add(UiTheme.label("Products with stock on hand at or below their reorder level.", Font.PLAIN, 12f, UiTheme.MUTED));
        top.add(titles, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton generateBtn = UiTheme.primaryButton("Generate");
        JButton printBtn = UiTheme.secondaryButton("Print report (FR36)");
        actions.add(generateBtn);
        actions.add(printBtn);
        top.add(actions, BorderLayout.EAST);

        generateBtn.addActionListener(e -> loadLowStockReport());
        printBtn.addActionListener(e -> services.reportService().printReport(user, lowStockTable, "Low-Stock Products Report", ""));

        card.add(top, BorderLayout.NORTH);

        // Center: Stats cards + Table
        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);

        // Stat cards
        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        statsRow.setOpaque(false);
        statsRow.add(statBox("Low-stock items", lowStockCountLabel, UiTheme.PRIMARY));
        statsRow.add(statBox("Out of stock (0 units)", outOfStockCountLabel, UiTheme.DANGER));
        statsRow.add(statBox("Total deficit units", totalDeficitLabel, new Color(217, 119, 6)));
        center.add(statsRow, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(lowStockTable);
        center.add(scroll, BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        card.add(lowStockFooter, BorderLayout.SOUTH);
        return card;
    }

    // =====================================================================
    // Supplier Purchases Card
    // =====================================================================

    private JPanel buildSupplierPurchasesCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 14));

        // Header + filters
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        titles.add(UiTheme.title("Supplier-wise purchases report (FR34)"));
        titles.add(Box.createVerticalStrut(2));
        titles.add(UiTheme.label("Purchase orders and procurement volume broken down by supplier.", Font.PLAIN, 12f, UiTheme.MUTED));
        top.add(titles, BorderLayout.NORTH);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filterRow.setOpaque(false);
        filterRow.add(UiTheme.label("From:", Font.BOLD, 12f, UiTheme.TEXT));
        fromDateField.setPreferredSize(new Dimension(110, 32));
        filterRow.add(fromDateField);

        filterRow.add(UiTheme.label("To:", Font.BOLD, 12f, UiTheme.TEXT));
        toDateField.setPreferredSize(new Dimension(110, 32));
        filterRow.add(toDateField);

        filterRow.add(UiTheme.label("Supplier:", Font.BOLD, 12f, UiTheme.TEXT));
        supplierFilterCombo.setPreferredSize(new Dimension(180, 32));
        filterRow.add(supplierFilterCombo);

        JButton generateBtn = UiTheme.primaryButton("Generate");
        JButton printBtn = UiTheme.secondaryButton("Print report (FR36)");
        filterRow.add(generateBtn);
        filterRow.add(printBtn);

        generateBtn.addActionListener(e -> loadSupplierPurchasesReport());
        printBtn.addActionListener(e -> services.reportService().printReport(user, supplierTable, "Supplier-Wise Purchases Report", ""));

        top.add(filterRow, BorderLayout.CENTER);
        card.add(top, BorderLayout.NORTH);

        // Center: Stats cards + Table
        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);

        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        statsRow.setOpaque(false);
        statsRow.add(statBox("Total spend", totalSpendLabel, UiTheme.PRIMARY));
        statsRow.add(statBox("Purchase orders", totalOrdersLabel, UiTheme.TEXT));
        statsRow.add(statBox("Total units (rec / ord)", totalUnitsLabel, new Color(217, 119, 6)));
        center.add(statsRow, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(supplierTable);
        center.add(scroll, BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        card.add(supplierFooter, BorderLayout.SOUTH);
        return card;
    }

    // =====================================================================
    // Sales Card (FR33)
    // =====================================================================

    private JPanel buildSalesCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 14));

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        titles.add(UiTheme.title("Sales report (FR33)"));
        titles.add(Box.createVerticalStrut(2));
        titles.add(UiTheme.label("Completed sales for one day or a date range. Leave both dates empty for today.",
                Font.PLAIN, 12f, UiTheme.MUTED));
        top.add(titles, BorderLayout.NORTH);

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        filterRow.setOpaque(false);
        filterRow.add(UiTheme.label("From:", Font.BOLD, 12f, UiTheme.TEXT));
        salesFromField.setPreferredSize(new Dimension(110, 32));
        filterRow.add(salesFromField);
        filterRow.add(UiTheme.label("To:", Font.BOLD, 12f, UiTheme.TEXT));
        salesToField.setPreferredSize(new Dimension(110, 32));
        filterRow.add(salesToField);
        filterRow.add(UiTheme.label("View:", Font.BOLD, 12f, UiTheme.TEXT));
        salesViewCombo.setPreferredSize(new Dimension(120, 32));
        filterRow.add(salesViewCombo);

        JButton todayBtn = UiTheme.secondaryButton("Today");
        JButton generateBtn = UiTheme.primaryButton("Generate");
        JButton printBtn = UiTheme.secondaryButton("Print report (FR36)");
        filterRow.add(todayBtn);
        filterRow.add(generateBtn);
        filterRow.add(printBtn);

        todayBtn.addActionListener(e -> {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            salesFromField.setText(today);
            salesToField.setText(today);
            loadSalesReport();
        });
        generateBtn.addActionListener(e -> loadSalesReport());
        salesViewCombo.addActionListener(e -> salesViewLayout.show(salesViewCards, (String) salesViewCombo.getSelectedItem()));
        printBtn.addActionListener(e -> {
            boolean byDay = VIEW_BY_DAY.equals(salesViewCombo.getSelectedItem());
            services.reportService().printReport(user, byDay ? dailyTable : salesTable,
                    byDay ? "Daily Sales Report" : "Sales Report", salesFooter.getText());
        });

        top.add(filterRow, BorderLayout.CENTER);
        card.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);

        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        statsRow.setOpaque(false);
        statsRow.add(statBox("Net sales", netSalesLabel, UiTheme.PRIMARY));
        statsRow.add(statBox("Sales", salesCountLabel, UiTheme.TEXT));
        statsRow.add(statBox("Units sold", unitsSoldLabel, new Color(217, 119, 6)));
        statsRow.add(statBox("Discounts given", discountsLabel, UiTheme.DANGER));
        center.add(statsRow, BorderLayout.NORTH);

        salesViewCards.setOpaque(false);
        salesViewCards.add(new JScrollPane(salesTable), VIEW_EACH_SALE);
        salesViewCards.add(new JScrollPane(dailyTable), VIEW_BY_DAY);
        center.add(salesViewCards, BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        card.add(salesFooter, BorderLayout.SOUTH);
        return card;
    }

    private void loadSalesReport() {
        LocalDate from;
        LocalDate to;
        try {
            String fromStr = salesFromField.getText().trim();
            String toStr = salesToField.getText().trim();
            from = fromStr.isEmpty() ? null : LocalDate.parse(fromStr);
            to = toStr.isEmpty() ? null : LocalDate.parse(toStr);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Dates must be in YYYY-MM-DD format.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            SalesReport report = services.reportService().generateSalesReport(user, from, to);
            DateTimeFormatter when = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

            salesTableModel.setRowCount(0);
            for (ReportService.SalesReportRow row : report.rows()) {
                salesTableModel.addRow(new Object[]{
                        FormatUtil.saleId(row.saleID()),
                        row.dateTime().format(when),
                        row.cashier(),
                        row.lineCount(),
                        row.unitsSold(),
                        FormatUtil.money(row.subtotal()),
                        FormatUtil.money(row.discount()),
                        FormatUtil.money(row.finalAmount())
                });
            }
            dailyTableModel.setRowCount(0);
            for (ReportService.DailySalesRow day : report.days()) {
                dailyTableModel.addRow(new Object[]{
                        day.date(),
                        day.saleCount(),
                        day.unitsSold(),
                        FormatUtil.money(day.subtotal()),
                        FormatUtil.money(day.discount()),
                        FormatUtil.money(day.netSales())
                });
            }

            netSalesLabel.setText(FormatUtil.money(report.totalNetSales()));
            salesCountLabel.setText(String.valueOf(report.totalSales()));
            unitsSoldLabel.setText(String.valueOf(report.totalUnits()));
            discountsLabel.setText(FormatUtil.money(report.totalDiscount()));
            String period = report.fromDate().equals(report.toDate())
                    ? report.fromDate().toString() : report.fromDate() + " to " + report.toDate();
            salesFooter.setText("Sales for " + period + " · generated " + report.generatedAt().format(when)
                    + " by " + report.generatedBy());
        } catch (BusinessException be) {
            JOptionPane.showMessageDialog(this, be.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =====================================================================
    // Current Inventory Card (FR32)
    // =====================================================================

    private JPanel buildInventoryCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 14));

        JButton generateBtn = UiTheme.primaryButton("Generate");
        JButton printBtn = UiTheme.secondaryButton("Print report (FR36)");
        generateBtn.addActionListener(e -> UiSupport.run(this, this::loadInventoryReport));
        printBtn.addActionListener(e -> UiSupport.run(this, () -> services.reportService().printReport(
                user, inventoryTable, "Current Inventory Report", inventoryFooter.getText())));
        card.add(header(titleBlock("Current inventory report (FR32)",
                "Stock on hand for every product. Stock value = stock × selling price."),
                flowRow(generateBtn, printBtn)), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(statsGrid(
                statBox("Units on hand", inventoryUnitsLabel, new Color(217, 119, 6)),
                statBox("Stock value", inventoryValueLabel, UiTheme.PRIMARY),
                statBox("Low-stock products", inventoryLowLabel, UiTheme.DANGER)), BorderLayout.NORTH);
        center.add(new JScrollPane(inventoryTable), BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        card.add(inventoryFooter, BorderLayout.SOUTH);
        return card;
    }

    private void loadInventoryReport() {
        CurrentInventoryReport report = services.reportService().generateCurrentInventoryReport(user);
        inventoryTableModel.setRowCount(0);
        for (ReportService.InventoryRow row : report.rows()) {
            inventoryTableModel.addRow(new Object[]{
                    row.productCode(),
                    row.name(),
                    row.categoryName(),
                    row.unit(),
                    FormatUtil.status(row.status()),
                    row.currentStock(),
                    row.reorderLevel(),
                    FormatUtil.money(row.sellingPrice()),
                    FormatUtil.money(row.stockValue()),
                    row.lowStock() ? "Yes" : ""
            });
        }
        inventoryUnitsLabel.setText(String.valueOf(report.totalUnits()));
        inventoryValueLabel.setText(FormatUtil.money(report.totalStockValue()));
        inventoryLowLabel.setText(String.valueOf(report.lowStockCount()));
        inventoryFooter.setText(report.totalProducts() + " products · generated " + report.generatedAt().format(WHEN)
                + " by " + report.generatedBy());
    }

    // =====================================================================
    // Stock Movements Card (FR32)
    // =====================================================================

    private JPanel buildMovementsCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 14));

        movementsTypeCombo.addItem(new TypeFilterItem(null));
        for (StockMovementType type : StockMovementType.values()) {
            movementsTypeCombo.addItem(new TypeFilterItem(type));
        }
        movementsFromField.setPreferredSize(new Dimension(110, 32));
        movementsToField.setPreferredSize(new Dimension(110, 32));
        movementsProductCombo.setPreferredSize(new Dimension(240, 32));
        movementsTypeCombo.setPreferredSize(new Dimension(130, 32));

        JButton todayBtn = UiTheme.secondaryButton("Today");
        JButton generateBtn = UiTheme.primaryButton("Generate");
        JButton printBtn = UiTheme.secondaryButton("Print report (FR36)");
        todayBtn.addActionListener(e -> {
            setToday(movementsFromField, movementsToField);
            UiSupport.run(this, this::loadMovementsReport);
        });
        generateBtn.addActionListener(e -> UiSupport.run(this, this::loadMovementsReport));
        printBtn.addActionListener(e -> UiSupport.run(this, () -> services.reportService().printReport(
                user, movementsTable, "Stock Movements Report", movementsFooter.getText())));

        card.add(header(titleBlock("Stock movements report (FR32)",
                        "Receipts, sales, returns and adjustments, oldest first. Empty dates mean today."),
                flowRow(filterLabel("From:"), movementsFromField, filterLabel("To:"), movementsToField, todayBtn),
                flowRow(filterLabel("Product:"), movementsProductCombo, filterLabel("Type:"), movementsTypeCombo),
                flowRow(generateBtn, printBtn)), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(statsGrid(
                statBox("Movements", movementsCountLabel, UiTheme.TEXT),
                statBox("Units in", movementsInLabel, UiTheme.SUCCESS),
                statBox("Units out", movementsOutLabel, UiTheme.DANGER),
                statBox("Net change", movementsNetLabel, UiTheme.PRIMARY)), BorderLayout.NORTH);
        center.add(new JScrollPane(movementsTable), BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        card.add(movementsFooter, BorderLayout.SOUTH);
        return card;
    }

    private void loadMovementsReport() {
        LocalDate from = FormatUtil.parseOptionalDate(movementsFromField.getText(), "From date");
        LocalDate to = FormatUtil.parseOptionalDate(movementsToField.getText(), "To date");
        ProductFilterItem product = (ProductFilterItem) movementsProductCombo.getSelectedItem();
        TypeFilterItem type = (TypeFilterItem) movementsTypeCombo.getSelectedItem();

        StockMovementReport report = services.reportService().generateStockMovementReport(user, from, to,
                product == null || product.product() == null ? null : product.product().getProductCode(),
                type == null ? null : type.type());
        movementsTableModel.setRowCount(0);
        for (ReportService.StockMovementRow row : report.rows()) {
            movementsTableModel.addRow(new Object[]{
                    row.dateTime().format(SHORT_WHEN),
                    row.productName(),
                    FormatUtil.status(row.type().name()),
                    signed(row.quantityChange()),
                    row.balanceAfter(),
                    row.sourceReference(),
                    row.responsibleUser()
            });
        }
        movementsCountLabel.setText(String.valueOf(report.totalMovements()));
        movementsInLabel.setText(String.valueOf(report.unitsIn()));
        movementsOutLabel.setText(String.valueOf(report.unitsOut()));
        movementsNetLabel.setText(signed(report.netChange()));
        movementsFooter.setText("Movements for " + period(report.fromDate(), report.toDate())
                + " · generated " + report.generatedAt().format(WHEN) + " by " + report.generatedBy());
    }

    // =====================================================================
    // Product-wise Sales Card (FR35)
    // =====================================================================

    private JPanel buildProductSalesCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 14));

        productSalesFromField.setPreferredSize(new Dimension(110, 32));
        productSalesToField.setPreferredSize(new Dimension(110, 32));
        JButton todayBtn = UiTheme.secondaryButton("Today");
        JButton generateBtn = UiTheme.primaryButton("Generate");
        JButton printBtn = UiTheme.secondaryButton("Print report (FR36)");
        todayBtn.addActionListener(e -> {
            setToday(productSalesFromField, productSalesToField);
            UiSupport.run(this, this::loadProductSalesReport);
        });
        generateBtn.addActionListener(e -> UiSupport.run(this, this::loadProductSalesReport));
        printBtn.addActionListener(e -> UiSupport.run(this, () -> services.reportService().printReport(
                user, productSalesTable, "Product-Wise Sales Report", productSalesFooter.getText())));

        card.add(header(titleBlock("Product-wise sales report (FR35)",
                        "Units and revenue per product from completed sales. Empty dates mean today."),
                flowRow(filterLabel("From:"), productSalesFromField, filterLabel("To:"), productSalesToField, todayBtn),
                flowRow(generateBtn, printBtn)), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);
        center.add(statsGrid(
                statBox("Products sold", productSalesCountLabel, UiTheme.TEXT),
                statBox("Units sold", productSalesUnitsLabel, new Color(217, 119, 6)),
                statBox("Revenue (before discounts)", productSalesRevenueLabel, UiTheme.PRIMARY)), BorderLayout.NORTH);
        center.add(new JScrollPane(productSalesTable), BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        card.add(productSalesFooter, BorderLayout.SOUTH);
        return card;
    }

    private void loadProductSalesReport() {
        LocalDate from = FormatUtil.parseOptionalDate(productSalesFromField.getText(), "From date");
        LocalDate to = FormatUtil.parseOptionalDate(productSalesToField.getText(), "To date");

        ProductSalesReport report = services.reportService().generateProductWiseSalesReport(user, from, to);
        productSalesTableModel.setRowCount(0);
        for (ReportService.ProductSalesRow row : report.rows()) {
            productSalesTableModel.addRow(new Object[]{
                    row.productCode(),
                    row.name(),
                    row.categoryName(),
                    row.saleCount(),
                    row.unitsSold(),
                    FormatUtil.money(row.revenue())
            });
        }
        productSalesCountLabel.setText(String.valueOf(report.productsSold()));
        productSalesUnitsLabel.setText(String.valueOf(report.totalUnits()));
        productSalesRevenueLabel.setText(FormatUtil.money(report.totalRevenue()));
        productSalesFooter.setText("Sales for " + period(report.fromDate(), report.toDate())
                + " · generated " + report.generatedAt().format(WHEN) + " by " + report.generatedBy());
    }

    // ---- shared helpers for the report cards ----

    /** Title block followed by rows of filters and buttons, stacked vertically. */
    private static JPanel header(JPanel titles, JPanel... rows) {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        titles.setAlignmentX(LEFT_ALIGNMENT);
        header.add(titles);
        for (JPanel row : rows) {
            header.add(Box.createVerticalStrut(10));
            row.setAlignmentX(LEFT_ALIGNMENT);
            header.add(row);
        }
        return header;
    }

    private static JPanel flowRow(java.awt.Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);
        for (java.awt.Component c : components) {
            row.add(c);
        }
        return row;
    }

    private static JLabel filterLabel(String text) {
        return UiTheme.label(text, Font.BOLD, 12f, UiTheme.TEXT);
    }

    /** Stat boxes sharing the width equally, so they fit on smaller screens too. */
    private static JPanel statsGrid(JPanel... boxes) {
        JPanel grid = new JPanel(new java.awt.GridLayout(1, boxes.length, 14, 0));
        grid.setOpaque(false);
        for (JPanel box : boxes) {
            grid.add(box);
        }
        return grid;
    }

    private static JPanel titleBlock(String title, String subtitle) {
        JPanel titles = new JPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.setOpaque(false);
        titles.add(UiTheme.title(title));
        titles.add(Box.createVerticalStrut(2));
        titles.add(UiTheme.label(subtitle, Font.PLAIN, 12f, UiTheme.MUTED));
        return titles;
    }

    private static void setToday(JTextField from, JTextField to) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        from.setText(today);
        to.setText(today);
    }

    private static String period(LocalDate from, LocalDate to) {
        return from.equals(to) ? from.toString() : from + " to " + to;
    }

    /** "+5", "-3" or "0". */
    private static String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JPanel statBox(String title, JLabel valueLabel, Color accent) {
        JPanel box = UiTheme.card(new BorderLayout(0, 4));
        box.setPreferredSize(new Dimension(220, 75));
        box.setBackground(new Color(248, 250, 252));
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));

        JLabel t = UiTheme.label(title, Font.PLAIN, 12f, UiTheme.MUTED);
        box.add(t, BorderLayout.NORTH);
        valueLabel.setForeground(accent);
        box.add(valueLabel, BorderLayout.CENTER);
        return box;
    }

    // =====================================================================
    // Data Loading
    // =====================================================================

    private void loadLowStockReport() {
        try {
            LowStockReport report = services.reportService().generateLowStockReport(user);
            lowStockTableModel.setRowCount(0);

            for (ReportService.LowStockItem item : report.items()) {
                lowStockTableModel.addRow(new Object[]{
                        item.productCode(),
                        item.name(),
                        item.categoryName(),
                        item.unit(),
                        FormatUtil.money(item.sellingPrice()),
                        item.currentStock(),
                        item.reorderLevel(),
                        item.deficit(),
                        item.status()
                });
            }

            lowStockCountLabel.setText(String.valueOf(report.totalItems()));
            outOfStockCountLabel.setText(String.valueOf(report.outOfStockCount()));
            totalDeficitLabel.setText(String.valueOf(report.totalDeficitUnits()));
            lowStockFooter.setText("Generated " + report.generatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
                    + " by " + report.generatedBy());
        } catch (BusinessException be) {
            JOptionPane.showMessageDialog(this, be.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSupplierPurchasesReport() {
        LocalDate from = null;
        LocalDate to = null;
        try {
            String fromStr = fromDateField.getText().trim();
            if (!fromStr.isEmpty()) from = LocalDate.parse(fromStr);
            String toStr = toDateField.getText().trim();
            if (!toStr.isEmpty()) to = LocalDate.parse(toStr);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Dates must be in YYYY-MM-DD format.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SupplierFilterItem supItem = (SupplierFilterItem) supplierFilterCombo.getSelectedItem();
        Integer supplierId = (supItem != null && supItem.supplier != null) ? supItem.supplier.getSupplierID() : null;

        try {
            SupplierWisePurchaseReport report = services.reportService().generateSupplierWisePurchaseReport(
                    user, from, to, supplierId);

            supplierTableModel.setRowCount(0);
            for (ReportService.SupplierPurchaseRow row : report.rows()) {
                supplierTableModel.addRow(new Object[]{
                        "PO-" + row.poNumber(),
                        row.orderDate(),
                        row.supplierName(),
                        row.status(),
                        row.lineCount(),
                        row.totalOrderedUnits(),
                        row.totalReceivedUnits(),
                        FormatUtil.money(row.totalValue())
                });
            }

            totalSpendLabel.setText(FormatUtil.money(report.totalSpend()));
            totalOrdersLabel.setText(String.valueOf(report.totalOrders()));
            totalUnitsLabel.setText(report.totalReceivedUnits() + " / " + report.totalOrderedUnits());
            supplierFooter.setText("Generated " + report.generatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
                    + " by " + report.generatedBy());
        } catch (BusinessException be) {
            JOptionPane.showMessageDialog(this, be.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void refresh() {
        // Refresh supplier filter combo
        List<Supplier> suppliers = services.productCatalog().listSuppliers(user, null);
        supplierFilterCombo.removeAllItems();
        supplierFilterCombo.addItem(new SupplierFilterItem(null, "All suppliers"));
        for (Supplier s : suppliers) {
            supplierFilterCombo.addItem(new SupplierFilterItem(s, s.getName()));
        }

        // Refresh the stock-movement product filter, keeping the current choice
        ProductFilterItem chosen = (ProductFilterItem) movementsProductCombo.getSelectedItem();
        String keep = chosen == null || chosen.product() == null ? null : chosen.product().getProductCode();
        movementsProductCombo.removeAllItems();
        movementsProductCombo.addItem(new ProductFilterItem(null));
        for (Product p : services.productCatalog().searchProducts(user, "", null, null)) {
            movementsProductCombo.addItem(new ProductFilterItem(p));
            if (p.getProductCode().equals(keep)) {
                movementsProductCombo.setSelectedIndex(movementsProductCombo.getItemCount() - 1);
            }
        }

        loadActiveReport();
    }

    private record SupplierFilterItem(Supplier supplier, String display) {
        @Override
        public String toString() {
            return display;
        }
    }

    private record ProductFilterItem(Product product) {
        @Override
        public String toString() {
            return product == null ? "All products" : product.getProductCode() + " · " + product.getName();
        }
    }

    private record TypeFilterItem(StockMovementType type) {
        @Override
        public String toString() {
            return type == null ? "All types" : FormatUtil.status(type.name());
        }
    }
}
