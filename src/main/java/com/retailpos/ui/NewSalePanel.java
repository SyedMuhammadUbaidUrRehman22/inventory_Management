package com.retailpos.ui;

import com.retailpos.model.Product;
import com.retailpos.model.Sale;
import com.retailpos.model.SaleItem;
import com.retailpos.model.SalesReceipt;
import com.retailpos.model.User;
import com.retailpos.service.ProductCatalog;
import com.retailpos.util.FormatUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cashier screen for making a sale (FR14–FR20). Matches {@code docs/GUI_Screens/07_New_Sale.svg}.
 * The screen only collects input and shows results; every rule lives in {@code SaleService}.
 */
public class NewSalePanel extends JPanel implements Refreshable {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm");

    private final AppServices services;
    private final User user;
    private Sale sale;

    // ---- find product ----
    private final JTextField searchField = UiTheme.textField("Search by code, name or category");
    private final DefaultTableModel productsModel = readOnlyModel("Code", "Product", "Price", "In stock");
    private final JTable productsTable = UiTheme.table(productsModel);
    private List<Product> found = List.of();
    private final JLabel selectedLabel = UiTheme.label("Select a product to see its stock.", Font.BOLD, 13f, UiTheme.TEXT);
    private final JLabel stockLabel = UiTheme.label(" ", Font.PLAIN, 12f, UiTheme.MUTED);
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100000, 1));

    // ---- current sale ----
    private final JLabel saleTitle = UiTheme.title("New sale");
    private final JLabel saleInfo = UiTheme.label(" ", Font.PLAIN, 12f, UiTheme.MUTED);
    private final DefaultTableModel cartModel = readOnlyModel("Product", "Qty", "Price", "Total");
    private final JTable cartTable = UiTheme.table(cartModel);
    private final JLabel subtotalLabel = UiTheme.label("", Font.PLAIN, 14f, UiTheme.TEXT);
    private final JLabel discountLabel = UiTheme.label("", Font.PLAIN, 14f, UiTheme.TEXT);
    private final JLabel finalLabel = UiTheme.label("", Font.BOLD, 18f, UiTheme.PRIMARY);
    private final JTextField discountField = UiTheme.textField("0");

    public NewSalePanel(AppServices services, User user) {
        super(new BorderLayout(20, 0));
        this.services = services;
        this.user = user;
        setOpaque(false);

        add(buildFindCard(), BorderLayout.CENTER);
        add(buildSaleCard(), BorderLayout.EAST);

        productsTable.getColumnModel().getColumn(2).setCellRenderer(UiTheme.rightAligned());
        productsTable.getColumnModel().getColumn(3).setCellRenderer(UiTheme.rightAligned());
        for (int col = 1; col <= 3; col++) {
            cartTable.getColumnModel().getColumn(col).setCellRenderer(UiTheme.rightAligned());
        }
        productsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedProduct();
            }
        });

        startNewSale();
        UiSupport.run(this, this::search);
    }

    // =====================================================================
    // Layout
    // =====================================================================

    private JPanel buildFindCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 12));
        card.add(UiTheme.title("Find product"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setOpaque(false);

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        JButton searchButton = UiTheme.primaryButton("Search");
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);
        searchButton.addActionListener(e -> UiSupport.run(this, this::search));
        searchField.addActionListener(e -> UiSupport.run(this, this::search));
        center.add(searchRow, BorderLayout.NORTH);
        center.add(UiTheme.tableScroll(productsTable), BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        // FR16: stock is shown for the selected product before it is added.
        JPanel selected = new JPanel();
        selected.setOpaque(false);
        selected.setLayout(new BoxLayout(selected, BoxLayout.Y_AXIS));
        selected.add(selectedLabel);
        selected.add(Box.createVerticalStrut(2));
        selected.add(stockLabel);
        selected.add(Box.createVerticalStrut(10));

        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        addRow.setOpaque(false);
        addRow.add(UiTheme.fieldLabel("Quantity"));
        addRow.add(quantitySpinner);
        JButton addButton = UiTheme.successButton("+ Add to sale");
        addButton.addActionListener(e -> UiSupport.run(this, this::addSelected));
        addRow.add(addButton);
        selected.add(addRow);
        card.add(selected, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildSaleCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 12));
        card.setPreferredSize(new java.awt.Dimension(500, 0));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.add(saleTitle);
        head.add(Box.createVerticalStrut(2));
        head.add(saleInfo);
        card.add(head, BorderLayout.NORTH);

        card.add(UiTheme.tableScroll(cartTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));

        JPanel totals = new JPanel(new GridLayout(3, 2, 0, 4));
        totals.setOpaque(false);
        totals.add(UiTheme.label("Subtotal", Font.PLAIN, 14f, UiTheme.MUTED));
        totals.add(rightAlign(subtotalLabel));
        totals.add(UiTheme.label("Discount", Font.PLAIN, 14f, UiTheme.MUTED));
        totals.add(rightAlign(discountLabel));
        totals.add(UiTheme.label("Final amount", Font.BOLD, 18f, UiTheme.TEXT));
        totals.add(rightAlign(finalLabel));
        bottom.add(totals);
        bottom.add(Box.createVerticalStrut(12));

        JPanel discountRow = new JPanel(new BorderLayout(8, 0));
        discountRow.setOpaque(false);
        discountRow.add(UiTheme.fieldLabel("Discount amount (Rs)"), BorderLayout.WEST);
        discountRow.add(discountField, BorderLayout.CENTER);
        JButton applyDiscount = UiTheme.secondaryButton("Apply");
        applyDiscount.addActionListener(e -> UiSupport.run(this, this::applyDiscount));
        discountField.addActionListener(e -> UiSupport.run(this, this::applyDiscount));
        discountRow.add(applyDiscount, BorderLayout.EAST);
        bottom.add(discountRow);
        bottom.add(Box.createVerticalStrut(12));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = UiTheme.secondaryButton("Cancel sale");
        JButton remove = UiTheme.secondaryButton("Remove line");
        JButton finalize = UiTheme.primaryButton("Finalize sale & print receipt");
        cancel.addActionListener(e -> cancelSale());
        remove.addActionListener(e -> UiSupport.run(this, this::removeSelectedLine));
        finalize.addActionListener(e -> UiSupport.run(this, this::finalizeSale));
        actions.add(cancel);
        actions.add(remove);
        actions.add(finalize);
        bottom.add(actions);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    // =====================================================================
    // Actions
    // =====================================================================

    private void startNewSale() {
        sale = services.saleService().createSale(user);
        discountField.setText("");
        showSale();
    }

    private void search() {
        found = services.productCatalog().searchProducts(user, searchField.getText(), null,
                ProductCatalog.STATUS_ACTIVE);
        productsModel.setRowCount(0);
        for (Product p : found) {
            productsModel.addRow(new Object[]{p.getProductCode(), p.getName(),
                    FormatUtil.money(p.getSellingPrice()), p.getCurrentStockQuantity()});
        }
        showSelectedProduct();
    }

    private Product selectedProduct() {
        int row = productsTable.getSelectedRow();
        return row < 0 || row >= found.size() ? null : found.get(row);
    }

    /** FR16: shows the stock as it is right now, not as it was when the list was loaded. */
    private void showSelectedProduct() {
        Product listed = selectedProduct();
        if (listed == null) {
            selectedLabel.setText("Select a product to see its stock.");
            stockLabel.setText(" ");
            return;
        }
        try {
            Product p = services.saleService().getProductForSale(user, listed.getProductCode());
            selectedLabel.setText("Selected: " + p.getName());
            stockLabel.setText("Current stock: " + p.getCurrentStockQuantity() + " units   ·   Price: "
                    + FormatUtil.money(p.getSellingPrice()));
        } catch (RuntimeException e) {
            selectedLabel.setText("Selected: " + listed.getName());
            stockLabel.setText(e.getMessage());
        }
    }

    private void addSelected() {
        Product p = selectedProduct();
        if (p == null) {
            throw new com.retailpos.util.BusinessException("Select a product first.");
        }
        int quantity = (Integer) quantitySpinner.getValue();
        services.saleService().addItem(user, sale, p.getProductCode(), quantity);
        quantitySpinner.setValue(1);
        showSale();
    }

    private void removeSelectedLine() {
        int row = cartTable.getSelectedRow();
        if (row < 0) {
            throw new com.retailpos.util.BusinessException("Select a line to remove.");
        }
        services.saleService().removeItem(user, sale, sale.getItems().get(row).getProduct().getProductCode());
        showSale();
    }

    /** FR18: the discount is a fixed amount in rupees. */
    private void applyDiscount() {
        String text = discountField.getText().trim();
        BigDecimal discount = text.isEmpty() ? BigDecimal.ZERO : FormatUtil.parseMoney(text, "Discount");
        services.saleService().applyDiscount(user, sale, discount);
        showSale();
    }

    private void finalizeSale() {
        applyDiscount();   // use exactly what is typed in the discount box
        if (!UiSupport.confirm(this, "Finalize this sale for " + FormatUtil.money(sale.getFinalAmount())
                + "? Stock will be reduced.")) {
            return;
        }
        SalesReceipt receipt = services.saleService().finalizeSale(user, sale);
        startNewSale();
        UiSupport.run(this, this::search);
        SalesReceiptDialog.show(this, receipt);
    }

    private void cancelSale() {
        if (!sale.getItems().isEmpty() && !UiSupport.confirm(this, "Cancel this sale and remove all its lines?")) {
            return;
        }
        startNewSale();
    }

    private void showSale() {
        saleTitle.setText("New sale · Draft");
        saleInfo.setText("Cashier: " + sale.getCashier().getUsername() + "   ·   " + sale.getDateTime().format(WHEN));
        cartModel.setRowCount(0);
        for (SaleItem item : sale.getItems()) {
            cartModel.addRow(new Object[]{item.getProduct().getName(), item.getQuantity(),
                    FormatUtil.money(item.getSellingPrice()), FormatUtil.money(item.getLineTotal())});
        }
        subtotalLabel.setText(FormatUtil.money(sale.getSubtotal()));
        BigDecimal discount = sale.getDiscount();
        discountLabel.setText(discount.signum() > 0 ? "− " + FormatUtil.money(discount) : FormatUtil.money(discount));
        finalLabel.setText(FormatUtil.money(sale.getFinalAmount()));
    }

    /** Stock may have changed while another screen was open, so the list is reloaded each time. */
    @Override
    public void refresh() {
        search();
    }

    // ---- helpers ----

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JLabel rightAlign(JLabel label) {
        label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        return label;
    }
}
