package com.retailpos.ui;

import com.retailpos.model.PurchaseOrder;
import com.retailpos.model.PurchaseOrderItem;
import com.retailpos.model.StockReceipt;
import com.retailpos.model.User;
import com.retailpos.model.enums.PurchaseOrderStatus;
import com.retailpos.util.BusinessException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen for recording received stock against purchase orders (FR11–FR13).
 * Matches mockups in {@code docs/GUI_Screens/06_Receive_Stock.svg}.
 */
public class ReceiveStockPanel extends JPanel implements Refreshable {

    private final AppServices services;
    private final User user;

    private final JComboBox<OrderItem> orderCombo = new JComboBox<>();
    private final JTextField receiptDateField = UiTheme.textField();
    private final JLabel statusBadge = new JLabel();

    private final DefaultTableModel linesTableModel;
    private final JTable linesTable;
    private final List<JSpinner> lineSpinners = new ArrayList<>();
    private final List<PurchaseOrderItem> currentLines = new ArrayList<>();

    private final DefaultTableModel historyTableModel;
    private final JTable historyTable;

    public ReceiveStockPanel(AppServices services, User user) {
        super(new BorderLayout(0, 16));
        this.services = services;
        this.user = user;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // 1. Top Card: Order selector and receipt date
        JPanel topCard = UiTheme.card(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.5;
        topCard.add(labeled("Purchase order", orderCombo), gbc);

        gbc.gridx = 1; gbc.weightx = 0.25;
        receiptDateField.setText(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
        topCard.add(labeled("Receipt date (YYYY-MM-DD)", receiptDateField), gbc);

        gbc.gridx = 2; gbc.weightx = 0.25;
        topCard.add(labeled("Status:", statusBadge), gbc);

        orderCombo.addActionListener(e -> onOrderSelected());

        // 2. Middle Card: Record quantities received table
        JPanel linesCard = UiTheme.card(new BorderLayout(0, 10));
        JPanel linesHeader = new JPanel(new BorderLayout());
        linesHeader.setOpaque(false);
        linesHeader.add(UiTheme.title("Record quantities received"), BorderLayout.WEST);

        linesCard.add(linesHeader, BorderLayout.NORTH);

        linesTableModel = new DefaultTableModel(new Object[]{"Product", "Ordered", "Previously received", "Remaining", "Receive now"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 4; // Only 'Receive now' column is editable
            }
        };
        linesTable = UiTheme.table(linesTableModel);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        linesTable.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);
        linesTable.getColumnModel().getColumn(2).setCellRenderer(rightRenderer);
        linesTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);
        linesTable.getColumnModel().getColumn(4).setCellRenderer(rightRenderer);

        JScrollPane linesScroll = new JScrollPane(linesTable);
        linesScroll.setPreferredSize(new Dimension(800, 180));
        linesCard.add(linesScroll, BorderLayout.CENTER);

        JPanel linesFooter = new JPanel(new BorderLayout());
        linesFooter.setOpaque(false);
        JLabel note = UiTheme.label("Stock increases only by the quantity actually received.",
                Font.PLAIN, 12f, UiTheme.MUTED);
        JButton recordBtn = UiTheme.primaryButton("Record receipt");
        recordBtn.addActionListener(e -> recordReceipt());

        linesFooter.add(note, BorderLayout.WEST);
        linesFooter.add(recordBtn, BorderLayout.EAST);
        linesCard.add(linesFooter, BorderLayout.SOUTH);

        // 3. Bottom Card: Receipt history
        JPanel historyCard = UiTheme.card(new BorderLayout(0, 10));
        historyCard.add(UiTheme.title("Receipt history"), BorderLayout.NORTH);

        historyTableModel = new DefaultTableModel(new Object[]{"Receipt ID", "Date", "Product", "Quantity received", "Purchase Order"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable = UiTheme.table(historyTableModel);
        historyTable.getColumnModel().getColumn(3).setCellRenderer(rightRenderer);

        JScrollPane historyScroll = new JScrollPane(historyTable);
        historyScroll.setPreferredSize(new Dimension(800, 160));
        historyCard.add(historyScroll, BorderLayout.CENTER);

        // Stack cards
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        stack.add(topCard);
        stack.add(Box.createVerticalStrut(14));
        stack.add(linesCard);
        stack.add(Box.createVerticalStrut(14));
        stack.add(historyCard);

        add(stack, BorderLayout.NORTH);
        refresh();
    }

    private void onOrderSelected() {
        OrderItem selected = (OrderItem) orderCombo.getSelectedItem();
        currentLines.clear();
        linesTableModel.setRowCount(0);

        if (selected == null || selected.order == null) {
            updateStatusBadge(null);
            return;
        }

        PurchaseOrder po = services.purchaseService().getOrder(selected.order.getPoNumber());
        updateStatusBadge(po.getStatus());

        if (po.getItems() != null) {
            for (PurchaseOrderItem item : po.getItems()) {
                currentLines.add(item);
                int rem = item.getRemainingQuantity();
                linesTableModel.addRow(new Object[]{
                        item.getProduct().getProductCode() + " - " + item.getProduct().getName(),
                        item.getOrderedQuantity(),
                        item.getReceivedQuantity(),
                        rem,
                        rem > 0 ? rem : 0 // Default to receiving remaining
                });
            }
        }
        loadReceiptHistory(po.getPoNumber());
    }

    private void recordReceipt() {
        OrderItem selected = (OrderItem) orderCombo.getSelectedItem();
        if (selected == null || selected.order == null) {
            JOptionPane.showMessageDialog(this, "Select a purchase order to receive.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate receiptDate;
        try {
            receiptDate = LocalDate.parse(receiptDateField.getText().trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid date format. Use YYYY-MM-DD.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Commit table editing if user is currently typing in cell
        if (linesTable.isEditing()) {
            linesTable.getCellEditor().stopCellEditing();
        }

        Map<Integer, Integer> toReceive = new HashMap<>();
        for (int i = 0; i < currentLines.size(); i++) {
            PurchaseOrderItem item = currentLines.get(i);
            Object val = linesTableModel.getValueAt(i, 4);
            int qty;
            try {
                qty = Integer.parseInt(val.toString().trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid quantity for " + item.getProduct().getName() + ". Must be an integer.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (qty < 0) {
                JOptionPane.showMessageDialog(this, "Quantity cannot be negative.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            toReceive.put(item.getPurchaseOrderItemID(), qty);
        }

        try {
            List<StockReceipt> receipts = services.stockService().recordOrderReceipt(
                    user, selected.order.getPoNumber(), receiptDate, toReceive);

            int totalUnits = receipts.stream().mapToInt(StockReceipt::getQuantityReceived).sum();
            JOptionPane.showMessageDialog(this,
                    "Successfully received " + totalUnits + " units across " + receipts.size() + " product lines!",
                    "Receipt Recorded", JOptionPane.INFORMATION_MESSAGE);

            refresh();
        } catch (BusinessException be) {
            JOptionPane.showMessageDialog(this, be.getMessage(), "Receipt Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadReceiptHistory(int poNumber) {
        historyTableModel.setRowCount(0);
        List<StockReceipt> receipts = services.stockService().getReceiptsForOrder(poNumber);
        for (StockReceipt sr : receipts) {
            String prod = sr.getPurchaseOrderItem() != null && sr.getPurchaseOrderItem().getProduct() != null
                    ? sr.getPurchaseOrderItem().getProduct().getName()
                    : "-";
            historyTableModel.addRow(new Object[]{
                    "RC-" + String.format("%04d", sr.getReceiptID()),
                    sr.getReceiptDate(),
                    prod,
                    sr.getQuantityReceived(),
                    "PO-" + poNumber
            });
        }
    }

    @Override
    public void refresh() {
        List<PurchaseOrder> receivable = services.purchaseService().getReceivableOrders();
        orderCombo.removeAllItems();

        if (receivable.isEmpty()) {
            orderCombo.addItem(new OrderItem(null, "No receivable purchase orders found"));
        } else {
            for (PurchaseOrder po : receivable) {
                orderCombo.addItem(new OrderItem(po, "PO-" + po.getPoNumber() + " — "
                        + (po.getSupplier() != null ? po.getSupplier().getName() : "") + " (" + po.getStatus() + ")"));
            }
        }

        if (orderCombo.getItemCount() > 0) {
            orderCombo.setSelectedIndex(0);
        }
    }

    private void updateStatusBadge(PurchaseOrderStatus status) {
        if (status == null) {
            statusBadge.setText(" - ");
            statusBadge.setOpaque(false);
            return;
        }
        statusBadge.setText(" " + status.name() + " ");
        statusBadge.setOpaque(true);
        statusBadge.setFont(statusBadge.getFont().deriveFont(Font.BOLD, 11f));
        if (status == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            statusBadge.setBackground(new Color(254, 243, 199));
            statusBadge.setForeground(new Color(146, 64, 14));
        } else if (status == PurchaseOrderStatus.ORDERED) {
            statusBadge.setBackground(new Color(224, 231, 255));
            statusBadge.setForeground(new Color(67, 56, 202));
        } else if (status == PurchaseOrderStatus.RECEIVED) {
            statusBadge.setBackground(new Color(220, 252, 231));
            statusBadge.setForeground(new Color(22, 101, 52));
        } else {
            statusBadge.setBackground(new Color(241, 245, 249));
            statusBadge.setForeground(UiTheme.TEXT);
        }
    }

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

    private record OrderItem(PurchaseOrder order, String display) {
        @Override
        public String toString() {
            return display;
        }
    }
}
