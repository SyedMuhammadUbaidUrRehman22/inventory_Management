package com.retailpos.ui;

import com.retailpos.model.Return;
import com.retailpos.model.Sale;
import com.retailpos.model.User;
import com.retailpos.service.ReturnService.ReturnableLine;
import com.retailpos.service.ReturnService.SaleForReturn;
import com.retailpos.util.BusinessException;
import com.retailpos.util.FormatUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cashier screen for customer returns (FR22–FR24). Matches {@code docs/GUI_Screens/09_Returns.svg}.
 * The screen only collects input and shows results; every rule lives in {@code ReturnService}.
 */
public class ReturnsPanel extends JPanel implements Refreshable {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String[] COMMON_REASONS = {
            "Packaging damaged", "Defective / not working", "Expired", "Wrong item", "Customer changed mind"};

    private static final int COL_SELECT = 0;
    private static final int COL_RETURN_QTY = 5;

    private final AppServices services;
    private final User user;
    private SaleForReturn current;
    private boolean syncing;

    // ---- find sale ----
    private final JTextField saleNumberField = UiTheme.textField("Sale number e.g. SL-00012");
    private final JLabel saleInfo = UiTheme.label("Enter the sale number printed on the customer's receipt.",
            Font.PLAIN, 12f, UiTheme.MUTED);

    // ---- items on the sale ----
    private final DefaultTableModel linesModel = new DefaultTableModel(
            new Object[]{"", "Product", "Sold", "Returned", "Unit price", "Return qty", "Line total"}, 0) {
        @Override
        public Class<?> getColumnClass(int column) {
            return column == COL_SELECT ? Boolean.class : column == COL_RETURN_QTY ? Integer.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return (column == COL_SELECT || column == COL_RETURN_QTY) && returnable(row) > 0;
        }
    };
    private final JTable linesTable = UiTheme.table(linesModel);

    // ---- return details ----
    private final JComboBox<String> reasonCombo = new JComboBox<>(COMMON_REASONS);
    private final JTextField returnDateField = UiTheme.textField("YYYY-MM-DD");
    private final JCheckBox acceptedBox = new JCheckBox("Return accepted", true);
    private final JCheckBox reusableBox = new JCheckBox("Item is reusable (restock)", true);

    // ---- returns already recorded ----
    private final DefaultTableModel historyModel = readOnlyModel("Return", "Date", "Product", "Qty", "Reason", "Outcome");
    private final JTable historyTable = UiTheme.table(historyModel);

    public ReturnsPanel(AppServices services, User user) {
        super(new BorderLayout(0, 16));
        this.services = services;
        this.user = user;
        setOpaque(false);

        add(buildFindCard(), BorderLayout.NORTH);
        // Left: the sale's items above the returns already recorded; right: the return details.
        JPanel left = new JPanel(new GridLayout(2, 1, 0, 16));
        left.setOpaque(false);
        left.add(buildItemsCard());
        left.add(buildHistoryCard());
        JPanel middle = new JPanel(new BorderLayout(20, 0));
        middle.setOpaque(false);
        middle.add(left, BorderLayout.CENTER);
        middle.add(buildDetailsCard(), BorderLayout.EAST);
        add(middle, BorderLayout.CENTER);

        for (int col = 2; col <= 6; col++) {
            if (col != COL_RETURN_QTY) {
                linesTable.getColumnModel().getColumn(col).setCellRenderer(UiTheme.rightAligned());
            }
        }
        linesTable.getColumnModel().getColumn(COL_SELECT).setMaxWidth(44);
        UiTheme.columnWidths(linesTable, 40, 170, 45, 70, 80, 85, 80);
        historyTable.getColumnModel().getColumn(3).setCellRenderer(UiTheme.rightAligned());
        UiTheme.columnWidths(historyTable, 80, 95, 150, 40, 150, 150);
        linesModel.addTableModelListener(this::keepSelectionAndQuantityInStep);

        showSale(null);
        resetDetails();
    }

    // =====================================================================
    // Layout
    // =====================================================================

    private JPanel buildFindCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 10));
        card.add(UiTheme.title("Find completed sale"), BorderLayout.NORTH);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);
        saleNumberField.setPreferredSize(new Dimension(260, 36));
        JButton find = UiTheme.primaryButton("Find sale");
        find.addActionListener(e -> UiSupport.run(this, this::findSale));
        saleNumberField.addActionListener(e -> UiSupport.run(this, this::findSale));
        row.add(saleNumberField);
        row.add(find);
        row.add(Box.createHorizontalStrut(10));
        row.add(saleInfo);
        card.add(row, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildItemsCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 10));
        card.add(UiTheme.title("Items on this sale"), BorderLayout.NORTH);
        card.add(UiTheme.tableScroll(linesTable), BorderLayout.CENTER);
        card.add(UiTheme.label("Tick the items being returned and enter how many of each.",
                Font.PLAIN, 12f, UiTheme.MUTED), BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildDetailsCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 12));
        card.setPreferredSize(new Dimension(380, 0));
        card.add(UiTheme.title("Return details"), BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        reasonCombo.setEditable(true);
        reasonCombo.setSelectedIndex(-1);
        form.add(labeled("Reason", reasonCombo));
        form.add(Box.createVerticalStrut(12));
        form.add(labeled("Return date (YYYY-MM-DD)", returnDateField));
        form.add(Box.createVerticalStrut(14));
        acceptedBox.setOpaque(false);
        reusableBox.setOpaque(false);
        acceptedBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        reusableBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Only an accepted return can be restocked.
        acceptedBox.addActionListener(e -> {
            reusableBox.setEnabled(acceptedBox.isSelected());
            if (!acceptedBox.isSelected()) {
                reusableBox.setSelected(false);
            }
        });
        form.add(acceptedBox);
        form.add(Box.createVerticalStrut(6));
        form.add(reusableBox);
        form.add(Box.createVerticalStrut(10));
        JLabel note = UiTheme.label("<html><div style='width:260px'>Stock is increased only when the return is "
                + "accepted and the item is reusable.</div></html>", Font.PLAIN, 12f, UiTheme.MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(note);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(form, BorderLayout.NORTH);
        card.add(top, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = UiTheme.secondaryButton("Cancel");
        JButton record = UiTheme.primaryButton("Record return");
        cancel.addActionListener(e -> clear());
        record.addActionListener(e -> UiSupport.run(this, this::recordReturn));
        actions.add(cancel);
        actions.add(record);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildHistoryCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 10));
        card.add(UiTheme.title("Returns already recorded for this sale"), BorderLayout.NORTH);
        card.add(UiTheme.tableScroll(historyTable), BorderLayout.CENTER);
        return card;
    }

    // =====================================================================
    // Actions
    // =====================================================================

    /** FR22: only a completed sale can be found here. */
    private void findSale() {
        showSale(services.returnService().findSaleForReturn(user, saleNumberField.getText()));
        resetDetails();
    }

    /** FR23, FR24: records the ticked lines with the reason, date and decision entered. */
    private void recordReturn() {
        if (current == null) {
            throw new BusinessException("Find the sale first.");
        }
        if (linesTable.isEditing()) {
            linesTable.getCellEditor().stopCellEditing();
        }
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        for (int row = 0; row < current.lines().size(); row++) {
            if (Boolean.TRUE.equals(linesModel.getValueAt(row, COL_SELECT))) {
                Object qty = linesModel.getValueAt(row, COL_RETURN_QTY);
                quantities.put(current.lines().get(row).item().getSaleItemID(), qty instanceof Integer i ? i : 0);
            }
        }
        LocalDate returnDate = FormatUtil.parseDate(returnDateField.getText(), "Return date");
        Object reason = reasonCombo.getEditor().getItem();
        boolean accepted = acceptedBox.isSelected();
        boolean reusable = reusableBox.isSelected();
        int saleID = current.sale().getSaleID();

        if (!UiSupport.confirm(this, "Record this return against sale " + FormatUtil.saleId(saleID) + "?")) {
            return;
        }
        List<Return> recorded = services.returnService().recordReturn(user, saleID, quantities,
                reason == null ? "" : reason.toString(), returnDate, accepted, reusable);

        int units = recorded.stream().mapToInt(Return::getQuantity).sum();
        String outcome = !accepted ? "The return was rejected, so stock did not change."
                : reusable ? "The items were put back into stock."
                : "The items are not reusable, so stock did not change.";
        UiSupport.info(this, "Return recorded: " + units + " unit(s) on " + recorded.size() + " line(s). " + outcome);
        showSale(services.returnService().findSaleForReturn(user, FormatUtil.saleId(saleID)));
        resetDetails();
    }

    private void clear() {
        saleNumberField.setText("");
        showSale(null);
        resetDetails();
    }

    private void showSale(SaleForReturn sale) {
        current = sale;
        syncing = true;
        linesModel.setRowCount(0);
        historyModel.setRowCount(0);
        if (sale == null) {
            saleInfo.setText("Enter the sale number printed on the customer's receipt.");
        } else {
            Sale s = sale.sale();
            saleInfo.setText("Sale " + FormatUtil.saleId(s.getSaleID()) + " · " + s.getDateTime().format(WHEN)
                    + " · " + FormatUtil.status(s.getStatus().name()) + " · Cashier: " + s.getCashier().getUsername()
                    + " · Total " + FormatUtil.money(s.getFinalAmount()));
            for (ReturnableLine line : sale.lines()) {
                linesModel.addRow(new Object[]{false, line.item().getProduct().getName(), line.item().getQuantity(),
                        line.returnedQuantity(), FormatUtil.money(line.item().getSellingPrice()), 0,
                        FormatUtil.money(line.item().getLineTotal())});
            }
            for (Return r : sale.returns()) {
                historyModel.addRow(new Object[]{FormatUtil.returnId(r.getReturnID()), r.getReturnDate().format(DAY),
                        r.getSaleItem().getProduct().getName(), r.getQuantity(), r.getReason(), outcome(r)});
            }
        }
        syncing = false;
    }

    /** Empties the return details for the next return. */
    private void resetDetails() {
        reasonCombo.setSelectedIndex(-1);
        returnDateField.setText(LocalDate.now().toString());
        acceptedBox.setSelected(true);
        reusableBox.setEnabled(true);
        reusableBox.setSelected(true);
    }

    /** A ticked line gets a quantity of at least 1, and a quantity above 0 ticks its line. */
    private void keepSelectionAndQuantityInStep(TableModelEvent e) {
        if (syncing || e.getType() != TableModelEvent.UPDATE || e.getFirstRow() < 0) {
            return;
        }
        int row = e.getFirstRow();
        syncing = true;
        try {
            Object qtyValue = linesModel.getValueAt(row, COL_RETURN_QTY);
            int qty = qtyValue instanceof Integer i ? i : 0;
            if (e.getColumn() == COL_SELECT) {
                boolean ticked = Boolean.TRUE.equals(linesModel.getValueAt(row, COL_SELECT));
                if (ticked && qty == 0) {
                    linesModel.setValueAt(1, row, COL_RETURN_QTY);
                } else if (!ticked) {
                    linesModel.setValueAt(0, row, COL_RETURN_QTY);
                }
            } else if (e.getColumn() == COL_RETURN_QTY) {
                linesModel.setValueAt(qty > 0, row, COL_SELECT);
            }
        } finally {
            syncing = false;
        }
    }

    private int returnable(int row) {
        return current == null || row >= current.lines().size() ? 0 : current.lines().get(row).returnableQuantity();
    }

    private static String outcome(Return r) {
        if (!r.isAccepted()) {
            return "Rejected";
        }
        return r.isReusable() ? "Accepted · restocked" : "Accepted · not reusable";
    }

    /** Reloads the open sale, so returns recorded elsewhere are counted. */
    @Override
    public void refresh() {
        if (current != null) {
            showSale(services.returnService().findSaleForReturn(user, FormatUtil.saleId(current.sale().getSaleID())));
        }
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
