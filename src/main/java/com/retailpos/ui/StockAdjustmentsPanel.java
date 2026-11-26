package com.retailpos.ui;

import com.retailpos.model.Product;
import com.retailpos.model.StockAdjustment;
import com.retailpos.model.User;
import com.retailpos.model.enums.AdjustmentType;
import com.retailpos.util.BusinessException;
import com.retailpos.util.FormatUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Storekeeper screen for stock adjustments (FR25–FR27). Matches {@code docs/GUI_Screens/10_Stock_Adjustments.svg}.
 * The screen only collects input and shows results; every rule lives in {@code StockService}.
 */
public class StockAdjustmentsPanel extends JPanel implements Refreshable {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final AppServices services;
    private final User user;

    // ---- record adjustment ----
    private final JComboBox<ProductChoice> productCombo = new JComboBox<>();
    private final JLabel stockLabel = UiTheme.label(" ", Font.PLAIN, 12f, UiTheme.MUTED);
    private final JComboBox<TypeChoice> typeCombo = new JComboBox<>();
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
    private final JTextField dateField = UiTheme.textField("YYYY-MM-DD");
    private final JTextField reasonField = UiTheme.textField("Describe what happened…");

    // ---- history ----
    private final DefaultTableModel historyModel = readOnlyModel(
            "Ref", "Date", "Product", "Type", "Qty", "Reason", "By");
    private final JTable historyTable = UiTheme.table(historyModel);

    public StockAdjustmentsPanel(AppServices services, User user) {
        super(new BorderLayout(20, 0));
        this.services = services;
        this.user = user;
        setOpaque(false);

        for (AdjustmentType type : AdjustmentType.values()) {
            typeCombo.addItem(new TypeChoice(type));
        }
        productCombo.addActionListener(e -> showStock());
        historyTable.getColumnModel().getColumn(4).setCellRenderer(UiTheme.rightAligned());
        UiTheme.columnWidths(historyTable, 80, 90, 130, 75, 40, 120, 85);

        add(buildRecordCard(), BorderLayout.WEST);
        add(buildHistoryCard(), BorderLayout.CENTER);

        clearForm();
        UiSupport.run(this, this::refresh);
    }

    // =====================================================================
    // Layout
    // =====================================================================

    private JPanel buildRecordCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 14));
        card.setPreferredSize(new Dimension(360, 0));
        card.add(UiTheme.title("Record adjustment"), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(labeled("Product", productCombo));
        form.add(Box.createVerticalStrut(4));
        stockLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(stockLabel);
        form.add(Box.createVerticalStrut(12));
        form.add(labeled("Adjustment type", typeCombo));
        form.add(Box.createVerticalStrut(12));
        form.add(labeled("Quantity", quantitySpinner));
        form.add(Box.createVerticalStrut(12));
        form.add(labeled("Date (YYYY-MM-DD)", dateField));
        form.add(Box.createVerticalStrut(12));
        form.add(labeled("Reason", reasonField));
        form.add(Box.createVerticalStrut(10));
        JLabel note = UiTheme.label("<html><div style='width:250px'>Recording an adjustment reduces the product's "
                + "stock by the quantity.</div></html>", Font.PLAIN, 12f, UiTheme.MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(note);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(form, BorderLayout.NORTH);
        card.add(top, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton clear = UiTheme.secondaryButton("Clear");
        JButton record = UiTheme.primaryButton("Record adjustment");
        clear.addActionListener(e -> clearForm());
        record.addActionListener(e -> UiSupport.run(this, this::recordAdjustment));
        actions.add(clear);
        actions.add(record);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildHistoryCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 12));
        card.add(UiTheme.title("Adjustment history"), BorderLayout.NORTH);
        card.add(UiTheme.tableScroll(historyTable), BorderLayout.CENTER);
        return card;
    }

    // =====================================================================
    // Actions
    // =====================================================================

    /** FR25, FR26. */
    private void recordAdjustment() {
        ProductChoice product = (ProductChoice) productCombo.getSelectedItem();
        if (product == null) {
            throw new BusinessException("Select a product.");
        }
        TypeChoice type = (TypeChoice) typeCombo.getSelectedItem();
        int quantity = (Integer) quantitySpinner.getValue();
        LocalDate date = FormatUtil.parseDate(dateField.getText(), "Date");
        String typeName = type == null ? "" : type.toString().toLowerCase();

        if (!UiSupport.confirm(this, "Record " + quantity + " unit(s) of " + product.product().getName() + " as "
                + typeName + "? Stock will be reduced by " + quantity + ".")) {
            return;
        }
        StockAdjustment saved = services.stockService().recordAdjustment(user, product.product().getProductCode(),
                type == null ? null : type.type(), quantity, date, reasonField.getText());
        UiSupport.info(this, "Adjustment " + FormatUtil.adjustmentId(saved.getAdjustmentID()) + " recorded. Stock of "
                + product.product().getName() + " reduced by " + quantity + ".");
        clearForm();
        refresh();
    }

    private void clearForm() {
        typeCombo.setSelectedIndex(0);
        quantitySpinner.setValue(1);
        dateField.setText(LocalDate.now().toString());
        reasonField.setText("");
    }

    private void showStock() {
        ProductChoice choice = (ProductChoice) productCombo.getSelectedItem();
        stockLabel.setText(choice == null ? " "
                : "In stock: " + choice.product().getCurrentStockQuantity() + "   ·   Unit: " + choice.product().getUnit());
    }

    /** FR27: reloads the products (stock may have changed elsewhere) and the history. */
    @Override
    public void refresh() {
        ProductChoice selected = (ProductChoice) productCombo.getSelectedItem();
        String keep = selected == null ? null : selected.product().getProductCode();
        List<Product> products = services.productCatalog().searchProducts(user, "", null, null);
        productCombo.removeAllItems();
        for (Product p : products) {
            productCombo.addItem(new ProductChoice(p));
            if (p.getProductCode().equals(keep)) {
                productCombo.setSelectedIndex(productCombo.getItemCount() - 1);
            }
        }
        showStock();

        historyModel.setRowCount(0);
        for (StockAdjustment a : services.stockService().getAdjustmentHistory(user, null)) {
            historyModel.addRow(new Object[]{FormatUtil.adjustmentId(a.getAdjustmentID()), a.getDate().format(DAY),
                    a.getProduct().getName(), FormatUtil.status(a.getAdjustmentType().name()), a.getQuantity(),
                    a.getReason(), a.getRecordedBy() == null ? "" : a.getRecordedBy().getUsername()});
        }
    }

    // ---- helpers ----

    private record ProductChoice(Product product) {
        @Override
        public String toString() {
            return product.getProductCode() + " · " + product.getName();
        }
    }

    private record TypeChoice(AdjustmentType type) {
        @Override
        public String toString() {
            return FormatUtil.status(type.name());
        }
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JPanel labeled(String text, JComponent field) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel label = UiTheme.fieldLabel(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        p.add(label);
        p.add(Box.createVerticalStrut(4));
        p.add(field);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }
}
