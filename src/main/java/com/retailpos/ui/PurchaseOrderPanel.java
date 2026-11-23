package com.retailpos.ui;

import com.retailpos.model.Product;
import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.Supplier;
import com.retailpos.model.User;
import com.retailpos.model.enums.PurchaseOrderStatus;
import com.retailpos.service.AccessControl;
import com.retailpos.util.BusinessException;
import com.retailpos.util.FormatUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen for managing and creating purchase orders (FR7–FR10).
 * Matches mockups in {@code docs/GUI_Screens/05_Create_Purchase_Order.svg}.
 */
public class PurchaseOrderPanel extends JPanel implements Refreshable {

    private final AppServices services;
    private final User user;

    // ---- Create PO form fields ----
    private final JComboBox<SupplierItem> supplierCombo = new JComboBox<>();
    private final JTextField orderDateField = UiTheme.textField();
    private final JLabel createdByLabel = UiTheme.label("", Font.PLAIN, 13f, UiTheme.TEXT);
    private final List<PurchaseOrderItem> orderItems = new ArrayList<>();
    private final DefaultTableModel orderLinesModel;
    private final JTable orderLinesTable;
    private final JLabel totalValueLabel = UiTheme.label("Rs 0", Font.BOLD, 26f, UiTheme.PRIMARY);
    private final JLabel totalStatsLabel = UiTheme.label("0 lines · 0 units", Font.PLAIN, 12f, UiTheme.MUTED);

    // ---- History Tab fields ----
    private final JComboBox<SupplierItem> filterSupplierCombo = new JComboBox<>();
    private final JComboBox<String> filterStatusCombo = new JComboBox<>();
    private final DefaultTableModel historyTableModel;
    private final JTable historyTable;
    private final JButton markOrderedBtn = UiTheme.primaryButton("Mark as Ordered");
    private final JButton cancelOrderBtn = UiTheme.dangerButton("Cancel order");
    private List<PurchaseOrder> currentOrdersList = new ArrayList<>();

    public PurchaseOrderPanel(AppServices services, User user) {
        super(new BorderLayout());
        this.services = services;
        this.user = user;
        setOpaque(false);

        // Lines table model: #, Product, Ordered Qty, Purchase Price, Line Total, Action
        orderLinesModel = new DefaultTableModel(new Object[]{"#", "Product", "Ordered qty", "Purchase price", "Line total"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        orderLinesTable = UiTheme.table(orderLinesModel);

        // History table model
        historyTableModel = new DefaultTableModel(new Object[]{"PO #", "Date", "Supplier", "Status", "Total value", "Lines", "Units"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable = UiTheme.table(historyTableModel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 13f));
        tabs.addTab("Create Purchase Order", buildCreateTab());
        tabs.addTab("Purchase Order History", buildHistoryTab());

        add(tabs, BorderLayout.CENTER);
        refresh();
    }

    // =====================================================================
    // Create Tab (matches 05_Create_Purchase_Order.svg)
    // =====================================================================

    private JPanel buildCreateTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // 1. Top Card: PO details
        JPanel topCard = UiTheme.card(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel title = UiTheme.title("New Purchase Order");
        JLabel statusBadge = statusBadge(PurchaseOrderStatus.PREPARED);

        JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        headerRow.setOpaque(false);
        headerRow.add(title);
        headerRow.add(statusBadge);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        topCard.add(headerRow, gbc);

        gbc.gridwidth = 1; gbc.gridy = 1;
        gbc.gridx = 0; gbc.weightx = 0.5;
        topCard.add(labeled("Supplier (one per order)", supplierCombo), gbc);

        gbc.gridx = 1; gbc.weightx = 0.25;
        orderDateField.setText(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        topCard.add(labeled("Order date (YYYY-MM-DD)", orderDateField), gbc);

        gbc.gridx = 2; gbc.weightx = 0.25;
        createdByLabel.setText(user.getUsername() + " (" + AccessControl.roleName(user) + ")");
        topCard.add(labeled("Created by", createdByLabel), gbc);

        supplierCombo.addActionListener(e -> {
            if (!orderItems.isEmpty()) {
                int choice = JOptionPane.showConfirmDialog(this,
                        "Changing the supplier will clear the current order lines. Continue?",
                        "Change Supplier", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    orderItems.clear();
                    rebuildLinesTable();
                } else {
                    // revert selection if possible
                }
            }
        });

        // 2. Middle Card: Order lines
        JPanel linesCard = UiTheme.card(new BorderLayout(0, 12));
        JPanel linesHeader = new JPanel(new BorderLayout());
        linesHeader.setOpaque(false);
        linesHeader.add(UiTheme.title("Order lines"), BorderLayout.WEST);

        JPanel linesButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        linesButtons.setOpaque(false);
        JButton addLineBtn = UiTheme.primaryButton("+ Add product line");
        JButton removeLineBtn = UiTheme.dangerButton("Remove selected line");
        linesButtons.add(addLineBtn);
        linesButtons.add(removeLineBtn);
        linesHeader.add(linesButtons, BorderLayout.EAST);

        addLineBtn.addActionListener(e -> openAddLineDialog());
        removeLineBtn.addActionListener(e -> removeSelectedLine());

        linesCard.add(linesHeader, BorderLayout.NORTH);

        // Align numbers right
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        orderLinesTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        orderLinesTable.getColumnModel().getColumn(1).setPreferredWidth(320);
        orderLinesTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        orderLinesTable.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);
        orderLinesTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        orderLinesTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
        orderLinesTable.getColumnModel().getColumn(4).setPreferredWidth(140);
        orderLinesTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);

        JScrollPane tableScroll = new JScrollPane(orderLinesTable);
        tableScroll.setPreferredSize(new Dimension(800, 220));
        linesCard.add(tableScroll, BorderLayout.CENTER);

        // Summary footer
        JPanel summaryBox = UiTheme.card(new BorderLayout());
        summaryBox.setBackground(new Color(248, 250, 252));
        summaryBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)));

        JPanel summaryLeft = new JPanel();
        summaryLeft.setOpaque(false);
        summaryLeft.setLayout(new BoxLayout(summaryLeft, BoxLayout.Y_AXIS));
        JLabel totalCaption = UiTheme.label("Order total value", Font.BOLD, 13f, UiTheme.MUTED);
        summaryLeft.add(totalCaption);
        summaryLeft.add(Box.createVerticalStrut(4));
        summaryLeft.add(totalStatsLabel);

        summaryBox.add(summaryLeft, BorderLayout.WEST);
        summaryBox.add(totalValueLabel, BorderLayout.EAST);
        linesCard.add(summaryBox, BorderLayout.SOUTH);

        // 3. Bottom Card: Action buttons
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftActions.setOpaque(false);
        JButton savePreparedBtn = UiTheme.secondaryButton("Save as Prepared");
        JButton markOrderedSubmitBtn = UiTheme.primaryButton("Mark as Ordered");
        JButton clearBtn = UiTheme.secondaryButton("Clear form");
        leftActions.add(savePreparedBtn);
        leftActions.add(markOrderedSubmitBtn);
        leftActions.add(clearBtn);

        savePreparedBtn.addActionListener(e -> submitOrder(false));
        markOrderedSubmitBtn.addActionListener(e -> submitOrder(true));
        clearBtn.addActionListener(e -> clearCreateForm());

        JLabel statusFlowHint = UiTheme.label(
                "Status flow: Prepared → Ordered → Partially Received → Received (or Cancelled)",
                Font.PLAIN, 12f, UiTheme.MUTED);

        bottomBar.add(leftActions, BorderLayout.WEST);
        bottomBar.add(statusFlowHint, BorderLayout.SOUTH);

        JPanel contentStack = new JPanel();
        contentStack.setLayout(new BoxLayout(contentStack, BoxLayout.Y_AXIS));
        contentStack.setOpaque(false);
        contentStack.add(topCard);
        contentStack.add(Box.createVerticalStrut(14));
        contentStack.add(linesCard);
        contentStack.add(Box.createVerticalStrut(14));
        contentStack.add(bottomBar);

        panel.add(contentStack, BorderLayout.NORTH);
        return panel;
    }

    // =====================================================================
    // History Tab
    // =====================================================================

    private JPanel buildHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Filter toolbar
        JPanel filterCard = UiTheme.card(new FlowLayout(FlowLayout.LEFT, 12, 6));
        filterCard.add(UiTheme.label("Supplier:", Font.BOLD, 12f, UiTheme.TEXT));
        filterCard.add(filterSupplierCombo);
        filterCard.add(UiTheme.label("Status:", Font.BOLD, 12f, UiTheme.TEXT));

        DefaultComboBoxModel<String> statusModel = new DefaultComboBoxModel<>();
        statusModel.addElement("All statuses");
        for (PurchaseOrderStatus s : PurchaseOrderStatus.values()) {
            statusModel.addElement(s.name());
        }
        filterStatusCombo.setModel(statusModel);
        filterCard.add(filterStatusCombo);

        JButton applyFilterBtn = UiTheme.secondaryButton("Filter");
        JButton refreshBtn = UiTheme.secondaryButton("Refresh");
        filterCard.add(applyFilterBtn);
        filterCard.add(refreshBtn);

        applyFilterBtn.addActionListener(e -> loadHistoryData());
        refreshBtn.addActionListener(e -> refresh());

        panel.add(filterCard, BorderLayout.NORTH);

        // Table
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        historyTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);
        historyTable.getColumnModel().getColumn(5).setCellRenderer(rightRenderer);
        historyTable.getColumnModel().getColumn(6).setCellRenderer(rightRenderer);

        historyTable.getSelectionModel().addListSelectionListener(e -> updateHistoryButtons());

        JScrollPane scroll = new JScrollPane(historyTable);
        panel.add(scroll, BorderLayout.CENTER);

        // Actions on selected order
        JPanel actionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actionsRow.setOpaque(false);
        JButton viewDetailsBtn = UiTheme.secondaryButton("View order details");
        actionsRow.add(viewDetailsBtn);
        actionsRow.add(markOrderedBtn);
        actionsRow.add(cancelOrderBtn);

        markOrderedBtn.setEnabled(false);
        cancelOrderBtn.setEnabled(false);

        viewDetailsBtn.addActionListener(e -> showSelectedOrderDetails());
        markOrderedBtn.addActionListener(e -> markSelectedOrderOrdered());
        cancelOrderBtn.addActionListener(e -> cancelSelectedOrder());

        panel.add(actionsRow, BorderLayout.SOUTH);
        return panel;
    }

    // =====================================================================
    // Dialog to add an order line
    // =====================================================================

    private void openAddLineDialog() {
        SupplierItem selectedSupplier = (SupplierItem) supplierCombo.getSelectedItem();
        if (selectedSupplier == null || selectedSupplier.supplier == null) {
            JOptionPane.showMessageDialog(this, "Please select a supplier first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(JOptionPane.getFrameForComponent(this), "Add Product Line", true);
        dialog.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<ProductItem> productCombo = new JComboBox<>();
        List<Product> products = services.productCatalog().searchProducts(user, null, null, "ACTIVE");
        for (Product p : products) {
            productCombo.addItem(new ProductItem(p));
        }

        JSpinner qtySpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100000, 1));
        JTextField priceField = UiTheme.textField();

        // When product changes, pre-fill supplier's purchase price if available
        productCombo.addActionListener(e -> {
            ProductItem item = (ProductItem) productCombo.getSelectedItem();
            if (item != null && item.product != null) {
                var priceOpt = services.purchaseService().getSupplierPrice(item.product.getProductCode(),
                        selectedSupplier.supplier.getSupplierID());
                if (priceOpt.isPresent()) {
                    priceField.setText(priceOpt.get().toPlainString());
                } else {
                    priceField.setText("");
                }
            }
        });
        if (productCombo.getItemCount() > 0) {
            productCombo.setSelectedIndex(0);
        }

        gbc.gridx = 0; gbc.gridy = 0;
        form.add(UiTheme.label("Product:", Font.BOLD, 13f, UiTheme.TEXT), gbc);
        gbc.gridx = 1;
        form.add(productCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(UiTheme.label("Ordered Quantity:", Font.BOLD, 13f, UiTheme.TEXT), gbc);
        gbc.gridx = 1;
        form.add(qtySpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        form.add(UiTheme.label("Purchase Price (Rs):", Font.BOLD, 13f, UiTheme.TEXT), gbc);
        gbc.gridx = 1;
        form.add(priceField, gbc);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton addBtn = UiTheme.primaryButton("Add line");
        JButton cancelBtn = UiTheme.secondaryButton("Cancel");
        btnPanel.add(addBtn);
        btnPanel.add(cancelBtn);

        cancelBtn.addActionListener(e -> dialog.dispose());
        addBtn.addActionListener(e -> {
            try {
                ProductItem prodItem = (ProductItem) productCombo.getSelectedItem();
                if (prodItem == null || prodItem.product == null) {
                    throw new BusinessException("Select a product.");
                }
                int qty = (Integer) qtySpinner.getValue();
                if (qty <= 0) {
                    throw new BusinessException("Quantity must be greater than zero.");
                }
                String priceText = priceField.getText().trim();
                if (priceText.isEmpty()) {
                    throw new BusinessException("Enter the purchase price.");
                }
                BigDecimal price = new BigDecimal(priceText);
                if (price.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("Purchase price cannot be negative.");
                }

                // Check for duplicate in order lines
                for (PurchaseOrderItem existing : orderItems) {
                    if (existing.getProduct().getProductCode().equals(prodItem.product.getProductCode())) {
                        throw new BusinessException("Product " + prodItem.product.getProductCode()
                                + " is already in the order. Remove or edit the existing line.");
                    }
                }

                PurchaseOrderItem poi = new PurchaseOrderItem(0, qty, price, 0, prodItem.product);
                orderItems.add(poi);
                rebuildLinesTable();
                dialog.dispose();
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(dialog, "Invalid purchase price: enter a valid decimal number.", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (BusinessException be) {
                JOptionPane.showMessageDialog(dialog, be.getMessage(), "Validation Error", JOptionPane.WARNING_MESSAGE);
            }
        });

        dialog.add(form, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void removeSelectedLine() {
        int row = orderLinesTable.getSelectedRow();
        if (row >= 0 && row < orderItems.size()) {
            orderItems.remove(row);
            rebuildLinesTable();
        } else {
            JOptionPane.showMessageDialog(this, "Select a line to remove.", "Notice", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void rebuildLinesTable() {
        orderLinesModel.setRowCount(0);
        BigDecimal total = BigDecimal.ZERO;
        int totalUnits = 0;

        for (int i = 0; i < orderItems.size(); i++) {
            PurchaseOrderItem item = orderItems.get(i);
            BigDecimal lineTotal = item.lineTotal();
            total = total.add(lineTotal);
            totalUnits += item.getOrderedQuantity();

            orderLinesModel.addRow(new Object[]{
                    (i + 1),
                    item.getProduct().getProductCode() + " - " + item.getProduct().getName(),
                    item.getOrderedQuantity(),
                    FormatUtil.money(item.getPurchasePrice()),
                    FormatUtil.money(lineTotal)
            });
        }

        totalValueLabel.setText(FormatUtil.money(total));
        totalStatsLabel.setText(orderItems.size() + " lines · " + totalUnits + " units");
    }

    private void submitOrder(boolean markAsOrdered) {
        SupplierItem sup = (SupplierItem) supplierCombo.getSelectedItem();
        if (sup == null || sup.supplier == null) {
            JOptionPane.showMessageDialog(this, "Select a supplier for the purchase order.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (orderItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Add at least one product line.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate orderDate;
        try {
            orderDate = LocalDate.parse(orderDateField.getText().trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid date format. Use YYYY-MM-DD.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            PurchaseOrder po = services.purchaseService().createPurchaseOrder(
                    user, sup.supplier.getSupplierID(), orderDate, new ArrayList<>(orderItems), markAsOrdered);

            String msg = "Purchase Order PO-" + po.getPoNumber() + " created successfully as " + po.getStatus() + "!";
            JOptionPane.showMessageDialog(this, msg, "Order Created", JOptionPane.INFORMATION_MESSAGE);
            clearCreateForm();
            loadHistoryData();
        } catch (BusinessException be) {
            JOptionPane.showMessageDialog(this, be.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearCreateForm() {
        orderItems.clear();
        rebuildLinesTable();
        orderDateField.setText(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (supplierCombo.getItemCount() > 0) {
            supplierCombo.setSelectedIndex(0);
        }
    }

    // =====================================================================
    // History Actions
    // =====================================================================

    private void updateHistoryButtons() {
        int row = historyTable.getSelectedRow();
        if (row >= 0 && row < currentOrdersList.size()) {
            PurchaseOrder po = currentOrdersList.get(row);
            markOrderedBtn.setEnabled(po.getStatus() == PurchaseOrderStatus.PREPARED);
            boolean cancellable = po.getStatus() == PurchaseOrderStatus.PREPARED
                    || (po.getStatus() == PurchaseOrderStatus.ORDERED && po.getTotalReceivedQuantity() == 0);
            cancelOrderBtn.setEnabled(cancellable);
        } else {
            markOrderedBtn.setEnabled(false);
            cancelOrderBtn.setEnabled(false);
        }
    }

    private void showSelectedOrderDetails() {
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= currentOrdersList.size()) {
            JOptionPane.showMessageDialog(this, "Select an order to view.", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        PurchaseOrder po = currentOrdersList.get(row);

        StringBuilder sb = new StringBuilder();
        sb.append("Purchase Order: PO-").append(po.getPoNumber()).append("\n");
        sb.append("Supplier: ").append(po.getSupplier() != null ? po.getSupplier().getName() : "Unknown").append("\n");
        sb.append("Order Date: ").append(po.getOrderDate()).append("\n");
        sb.append("Status: ").append(po.getStatus()).append("\n");
        sb.append("Total Value: ").append(FormatUtil.money(po.getTotalValue())).append("\n\n");
        sb.append("Items:\n");
        sb.append(String.format("%-12s %-25s %-10s %-10s %-12s\n", "Code", "Name", "Ordered", "Received", "Price"));
        sb.append("-----------------------------------------------------------------------\n");
        if (po.getItems() != null) {
            for (PurchaseOrderItem item : po.getItems()) {
                sb.append(String.format("%-12s %-25s %-10d %-10d %-12s\n",
                        item.getProduct().getProductCode(),
                        item.getProduct().getName(),
                        item.getOrderedQuantity(),
                        item.getReceivedQuantity(),
                        FormatUtil.money(item.getPurchasePrice())));
            }
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "PO-" + po.getPoNumber() + " Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void markSelectedOrderOrdered() {
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= currentOrdersList.size()) return;
        PurchaseOrder po = currentOrdersList.get(row);
        try {
            services.purchaseService().markOrdered(user, po.getPoNumber());
            JOptionPane.showMessageDialog(this, "Order PO-" + po.getPoNumber() + " marked as ORDERED.", "Success", JOptionPane.INFORMATION_MESSAGE);
            loadHistoryData();
        } catch (BusinessException be) {
            JOptionPane.showMessageDialog(this, be.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cancelSelectedOrder() {
        int row = historyTable.getSelectedRow();
        if (row < 0 || row >= currentOrdersList.size()) return;
        PurchaseOrder po = currentOrdersList.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to cancel order PO-" + po.getPoNumber() + "?",
                "Confirm Cancel", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                services.purchaseService().cancelOrder(user, po.getPoNumber());
                JOptionPane.showMessageDialog(this, "Order PO-" + po.getPoNumber() + " cancelled.", "Cancelled", JOptionPane.INFORMATION_MESSAGE);
                loadHistoryData();
            } catch (BusinessException be) {
                JOptionPane.showMessageDialog(this, be.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    public void refresh() {
        // Refresh supplier dropdowns
        List<Supplier> suppliers = services.productCatalog().listSuppliers(user, null);
        supplierCombo.removeAllItems();
        filterSupplierCombo.removeAllItems();

        filterSupplierCombo.addItem(new SupplierItem(null, "All suppliers"));
        for (Supplier s : suppliers) {
            supplierCombo.addItem(new SupplierItem(s, s.getName()));
            filterSupplierCombo.addItem(new SupplierItem(s, s.getName()));
        }

        loadHistoryData();
    }

    private void loadHistoryData() {
        SupplierItem sup = (SupplierItem) filterSupplierCombo.getSelectedItem();
        Integer supplierId = (sup != null && sup.supplier != null) ? sup.supplier.getSupplierID() : null;

        String statusStr = (String) filterStatusCombo.getSelectedItem();
        PurchaseOrderStatus status = null;
        if (statusStr != null && !statusStr.equals("All statuses")) {
            status = PurchaseOrderStatus.valueOf(statusStr);
        }

        currentOrdersList = services.purchaseService().searchOrders(supplierId, status, null, null);
        historyTableModel.setRowCount(0);

        for (PurchaseOrder po : currentOrdersList) {
            int lineCount = po.getItems() != null ? po.getItems().size() : 0;
            int orderedUnits = po.getTotalOrderedQuantity();
            int receivedUnits = po.getTotalReceivedQuantity();

            historyTableModel.addRow(new Object[]{
                    "PO-" + po.getPoNumber(),
                    po.getOrderDate(),
                    po.getSupplier() != null ? po.getSupplier().getName() : "-",
                    po.getStatus(),
                    FormatUtil.money(po.getTotalValue()),
                    lineCount,
                    receivedUnits + " / " + orderedUnits
            });
        }
        updateHistoryButtons();
    }

    // =====================================================================
    // UI Helpers
    // =====================================================================

    private static JPanel labeled(String text, JComponent field) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel label = UiTheme.label(text, Font.PLAIN, 12f, UiTheme.MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(label);
        p.add(Box.createVerticalStrut(4));
        p.add(field);
        return p;
    }

    private static JLabel statusBadge(PurchaseOrderStatus status) {
        JLabel l = new JLabel(" " + status.name() + " ");
        l.setOpaque(true);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        switch (status) {
            case PREPARED -> {
                l.setBackground(new Color(219, 234, 254));
                l.setForeground(new Color(30, 64, 175));
            }
            case ORDERED -> {
                l.setBackground(new Color(224, 231, 255));
                l.setForeground(new Color(67, 56, 202));
            }
            case PARTIALLY_RECEIVED -> {
                l.setBackground(new Color(254, 243, 199));
                l.setForeground(new Color(146, 64, 14));
            }
            case RECEIVED -> {
                l.setBackground(new Color(220, 252, 231));
                l.setForeground(new Color(22, 101, 52));
            }
            case CANCELLED -> {
                l.setBackground(new Color(254, 226, 226));
                l.setForeground(new Color(153, 27, 27));
            }
        }
        return l;
    }

    private record SupplierItem(Supplier supplier, String display) {
        SupplierItem(Supplier supplier) {
            this(supplier, supplier == null ? "" : supplier.getName());
        }

        @Override
        public String toString() {
            return display;
        }
    }

    private record ProductItem(Product product) {
        @Override
        public String toString() {
            return product == null ? "" : product.getProductCode() + " - " + product.getName();
        }
    }
}
