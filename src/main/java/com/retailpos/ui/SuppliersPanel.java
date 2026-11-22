package com.retailpos.ui;

import com.retailpos.model.Product;
import com.retailpos.model.ProductSupplier;
import com.retailpos.model.Supplier;
import com.retailpos.model.User;
import com.retailpos.service.ProductCatalog;
import com.retailpos.util.FormatUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Supplier Management screen (Manager only).
 * FR3: register and edit suppliers. FR4, FR5: link products to a supplier with that supplier's purchase price.
 */
public class SuppliersPanel extends JPanel implements Refreshable {

    private static final int REMOVE_COLUMN = 3;

    private final ProductCatalog catalog;
    private final User user;

    // supplier list
    private final JTextField searchField = UiTheme.textField("Search suppliers…");
    private final DefaultTableModel supplierModel = readOnlyModel("ID", "Name", "Contact");
    private final JTable supplierTable = new JTable(supplierModel);
    private List<Supplier> suppliers = new ArrayList<>();

    // supplier form
    private final JLabel formTitle = UiTheme.title("Register supplier");
    private final JTextField idField = UiTheme.textField("");
    private final JTextField nameField = UiTheme.textField("Enter supplier name");
    private final JTextField addressField = UiTheme.textField("Enter address");
    private final JTextField contactField = UiTheme.textField("Phone / email");
    private Supplier selected;   // null while registering a new supplier

    // products supplied by the selected supplier
    private final JLabel linksTitle = UiTheme.title("Products supplied");
    private final JButton linkButton = UiTheme.primaryButton("+ Link product");
    private final DefaultTableModel linkModel = readOnlyModel("Product", "Code", "Purchase price", "");
    private final JTable linkTable = new JTable(linkModel);
    private List<ProductSupplier> links = new ArrayList<>();

    public SuppliersPanel(AppServices services, User user) {
        this.catalog = services.productCatalog();
        this.user = user;

        setOpaque(false);
        setLayout(new BorderLayout(16, 0));
        JComponent list = supplierListCard();
        list.setPreferredSize(new Dimension(440, 0));
        add(list, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(0, 16));
        right.setOpaque(false);
        right.add(formCard(), BorderLayout.NORTH);
        right.add(linksCard(), BorderLayout.CENTER);
        add(right, BorderLayout.CENTER);

        clearForm();
        UiSupport.run(this, this::refresh);
    }

    @Override
    public void refresh() {
        loadSuppliers();
        loadLinks();
    }

    // =====================================================================
    // Supplier list
    // =====================================================================

    private JComponent supplierListCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 12));
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(UiTheme.title("Suppliers"), BorderLayout.NORTH);
        top.add(searchField, BorderLayout.CENTER);
        card.add(top, BorderLayout.NORTH);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { UiSupport.run(SuppliersPanel.this, SuppliersPanel.this::loadSuppliers); }
            @Override public void removeUpdate(DocumentEvent e) { UiSupport.run(SuppliersPanel.this, SuppliersPanel.this::loadSuppliers); }
            @Override public void changedUpdate(DocumentEvent e) { }
        });

        UiTheme.styleTable(supplierTable);
        supplierTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        supplierTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        supplierTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        supplierTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && supplierTable.getSelectedRow() >= 0) {
                selectSupplier(suppliers.get(supplierTable.getSelectedRow()));
            }
        });
        card.add(UiTheme.tableScroll(supplierTable), BorderLayout.CENTER);
        return card;
    }

    private void loadSuppliers() {
        suppliers = catalog.listSuppliers(user, searchField.getText());
        supplierModel.setRowCount(0);
        for (Supplier s : suppliers) {
            supplierModel.addRow(new Object[]{FormatUtil.supplierId(s.getSupplierID()), s.getName(),
                    s.getContactInfo() == null ? "" : s.getContactInfo()});
        }
        if (selected != null) {
            selectRowOf(selected.getSupplierID());
        }
    }

    private void selectRowOf(int supplierID) {
        for (int i = 0; i < suppliers.size(); i++) {
            if (suppliers.get(i).getSupplierID() == supplierID) {
                supplierTable.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    // =====================================================================
    // Register / edit supplier (FR3)
    // =====================================================================

    private JComponent formCard() {
        JPanel card = UiTheme.card(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        idField.setEditable(false);

        JPanel firstRow = new JPanel(new GridLayout(1, 2, 16, 0));
        firstRow.setOpaque(false);
        firstRow.add(labelled("Supplier ID", idField));
        firstRow.add(labelled("Supplier name", nameField));

        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(formTitle);
        card.add(Box.createVerticalStrut(12));
        card.add(UiTheme.stretch(firstRow));
        card.add(Box.createVerticalStrut(10));
        card.add(UiTheme.stretch(labelled("Address", addressField)));
        card.add(Box.createVerticalStrut(10));
        card.add(UiTheme.stretch(labelled("Contact information", contactField)));
        card.add(Box.createVerticalStrut(16));

        JButton save = UiTheme.primaryButton("Save supplier");
        save.addActionListener(e -> UiSupport.run(this, this::saveSupplier));
        JButton clear = UiTheme.secondaryButton("Clear");
        clear.addActionListener(e -> clearForm());
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(save);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(clear);
        card.add(UiTheme.stretch(buttons));
        return card;
    }

    private void selectSupplier(Supplier s) {
        selected = s;
        formTitle.setText("Edit supplier");
        idField.setText(FormatUtil.supplierId(s.getSupplierID()));
        nameField.setText(s.getName());
        addressField.setText(s.getAddress() == null ? "" : s.getAddress());
        contactField.setText(s.getContactInfo() == null ? "" : s.getContactInfo());
        UiSupport.run(this, this::loadLinks);
    }

    private void clearForm() {
        selected = null;
        supplierTable.clearSelection();
        formTitle.setText("Register supplier");
        idField.setText("(assigned on save)");
        nameField.setText("");
        addressField.setText("");
        contactField.setText("");
        UiSupport.run(this, this::loadLinks);
        nameField.requestFocusInWindow();
    }

    private void saveSupplier() {
        Supplier input = new Supplier(selected == null ? 0 : selected.getSupplierID(),
                nameField.getText(), addressField.getText(), contactField.getText());
        Supplier saved = selected == null ? catalog.registerSupplier(user, input) : catalog.updateSupplier(user, input);
        selected = saved;
        loadSuppliers();
        selectSupplier(saved);
        UiSupport.info(this, "Supplier " + FormatUtil.supplierId(saved.getSupplierID()) + " saved.");
    }

    // =====================================================================
    // Products supplied, with purchase prices (FR4, FR5)
    // =====================================================================

    private JComponent linksCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 12));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(linksTitle, BorderLayout.WEST);
        linkButton.addActionListener(e -> UiSupport.run(this, () -> editLink(null)));
        top.add(linkButton, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        UiTheme.styleTable(linkTable);
        linkTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        linkTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        linkTable.getColumnModel().getColumn(1).setMinWidth(76);
        linkTable.getColumnModel().getColumn(2).setPreferredWidth(130);
        linkTable.getColumnModel().getColumn(2).setMinWidth(126);
        linkTable.getColumnModel().getColumn(2).setCellRenderer(UiTheme.rightAligned());
        linkTable.getColumnModel().getColumn(REMOVE_COLUMN).setPreferredWidth(90);
        linkTable.getColumnModel().getColumn(REMOVE_COLUMN).setMinWidth(84);
        linkTable.getColumnModel().getColumn(REMOVE_COLUMN).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, column);
                c.setForeground(UiTheme.DANGER);
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                return c;
            }
        });
        linkTable.setToolTipText("Double-click a row to change its purchase price");
        linkTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = linkTable.rowAtPoint(e.getPoint());
                int col = linkTable.columnAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                ProductSupplier link = links.get(row);
                if (col == REMOVE_COLUMN) {
                    UiSupport.run(SuppliersPanel.this, () -> removeLink(link));
                } else if (e.getClickCount() == 2) {
                    UiSupport.run(SuppliersPanel.this, () -> editLink(link));
                }
            }
        });
        linkTable.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean overRemove = linkTable.columnAtPoint(e.getPoint()) == REMOVE_COLUMN
                        && linkTable.rowAtPoint(e.getPoint()) >= 0;
                linkTable.setCursor(Cursor.getPredefinedCursor(overRemove ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
        card.add(UiTheme.tableScroll(linkTable), BorderLayout.CENTER);
        return card;
    }

    private void loadLinks() {
        linkModel.setRowCount(0);
        links = new ArrayList<>();
        linkButton.setEnabled(selected != null);
        if (selected == null) {
            linksTitle.setText("Products supplied (select a supplier)");
            return;
        }
        linksTitle.setText("Products supplied by " + selected.getName());
        links = catalog.getProductsForSupplier(user, selected.getSupplierID());
        for (ProductSupplier link : links) {
            linkModel.addRow(new Object[]{link.getProduct().getName(), link.getProduct().getProductCode(),
                    FormatUtil.money(link.getPurchasePrice()), "Remove"});
        }
    }

    /** Asks for product and purchase price; {@code existing == null} links a new product. */
    private void editLink(ProductSupplier existing) {
        JComboBox<Product> productBox = new JComboBox<>();
        productBox.setRenderer(ProductsPanel.renderer(v -> v instanceof Product p
                ? p.getProductCode() + " – " + p.getName() : "Select product"));
        JTextField priceField = UiTheme.textField("0.00");
        if (existing == null) {
            for (Product p : catalog.searchProducts(user, null, null, ProductCatalog.STATUS_ACTIVE)) {
                productBox.addItem(p);
            }
            productBox.setSelectedIndex(-1);
        } else {
            productBox.addItem(existing.getProduct());
            productBox.setEnabled(false);
            priceField.setText(existing.getPurchasePrice().toPlainString());
        }

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(UiTheme.fieldLabel("Product"));
        form.add(productBox);
        form.add(UiTheme.fieldLabel("Purchase price from " + selected.getName() + " (Rs)"));
        form.add(priceField);
        form.setPreferredSize(new Dimension(380, form.getPreferredSize().height));

        String title = existing == null ? "Link product" : "Change purchase price";
        if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        Product product = (Product) productBox.getSelectedItem();
        if (product == null) {
            throw new com.retailpos.util.BusinessException("Select a product.");
        }
        BigDecimal price = FormatUtil.parseMoney(priceField.getText(), "Purchase price");
        catalog.setSupplierPrice(user, product.getProductCode(), selected.getSupplierID(), price);
        loadLinks();
    }

    private void removeLink(ProductSupplier link) {
        if (!UiSupport.confirm(this, "Remove " + link.getProduct().getName() + " from " + selected.getName() + "?")) {
            return;
        }
        catalog.removeSupplierPrice(user, link.getProduct().getProductCode(), selected.getSupplierID());
        loadLinks();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JPanel labelled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.add(UiTheme.fieldLabel(label), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }
}
