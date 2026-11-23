package com.retailpos.ui;

import com.retailpos.model.Supplier;
import com.retailpos.model.User;
import com.retailpos.service.ReportService;
import com.retailpos.service.ReportService.LowStockReport;
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
 * Screen for generating and printing store reports (FR31, FR34, FR36).
 * Matches mockups in {@code docs/GUI_Screens/11_Reports.svg}.
 */
public class ReportsPanel extends JPanel implements Refreshable {

    private final AppServices services;
    private final User user;

    private static final String TYPE_LOW_STOCK = "LOW_STOCK";
    private static final String TYPE_SUPPLIER_PURCHASES = "SUPPLIER_PURCHASES";

    private String activeReportType = TYPE_LOW_STOCK;

    // Report selection buttons
    private final JButton lowStockBtn = UiTheme.primaryButton("Low-stock products");
    private final JButton supplierPurchasesBtn = UiTheme.secondaryButton("Supplier-wise purchases");

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

        alignNumericColumns();

        // 1. Left Nav Sidebar
        add(buildLeftSidebar(), BorderLayout.WEST);

        // 2. Right Reports Cards
        reportCards.setOpaque(false);
        reportCards.add(buildLowStockCard(), TYPE_LOW_STOCK);
        reportCards.add(buildSupplierPurchasesCard(), TYPE_SUPPLIER_PURCHASES);
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

        list.add(reportNavButton(lowStockBtn, "Low-stock products", "FR31 · at or below reorder level", () -> switchReport(TYPE_LOW_STOCK)));
        list.add(Box.createVerticalStrut(10));
        list.add(reportNavButton(supplierPurchasesBtn, "Supplier-wise purchases", "FR34 · purchase orders by supplier", () -> switchReport(TYPE_SUPPLIER_PURCHASES)));
        list.add(Box.createVerticalStrut(16));

        // Placeholders for subsequent phases (as depicted in mockup)
        list.add(disabledNavButton("Current inventory", "FR32 · Phase 4"));
        list.add(Box.createVerticalStrut(8));
        list.add(disabledNavButton("Stock movements", "FR32 · Phase 4"));
        list.add(Box.createVerticalStrut(8));
        list.add(disabledNavButton("Sales (daily / date range)", "FR33 · Phase 3"));
        list.add(Box.createVerticalStrut(8));
        list.add(disabledNavButton("Product-wise sales", "FR35 · Phase 4"));

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

    private JPanel disabledNavButton(String title, String subtitle) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JButton btn = UiTheme.secondaryButton(title);
        btn.setEnabled(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        p.add(btn, BorderLayout.NORTH);
        JLabel sub = UiTheme.label(subtitle, Font.PLAIN, 11f, UiTheme.MUTED);
        sub.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        p.add(sub, BorderLayout.CENTER);
        return p;
    }

    private void switchReport(String type) {
        activeReportType = type;
        if (type.equals(TYPE_LOW_STOCK)) {
            lowStockBtn.setBackground(UiTheme.PRIMARY);
            lowStockBtn.setForeground(Color.WHITE);
            supplierPurchasesBtn.setBackground(UiTheme.SECONDARY);
            supplierPurchasesBtn.setForeground(UiTheme.TEXT);
            reportCardLayout.show(reportCards, TYPE_LOW_STOCK);
            loadLowStockReport();
        } else {
            supplierPurchasesBtn.setBackground(UiTheme.PRIMARY);
            supplierPurchasesBtn.setForeground(Color.WHITE);
            lowStockBtn.setBackground(UiTheme.SECONDARY);
            lowStockBtn.setForeground(UiTheme.TEXT);
            reportCardLayout.show(reportCards, TYPE_SUPPLIER_PURCHASES);
            loadSupplierPurchasesReport();
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

        if (activeReportType.equals(TYPE_LOW_STOCK)) {
            loadLowStockReport();
        } else {
            loadSupplierPurchasesReport();
        }
    }

    private record SupplierFilterItem(Supplier supplier, String display) {
        @Override
        public String toString() {
            return display;
        }
    }
}
