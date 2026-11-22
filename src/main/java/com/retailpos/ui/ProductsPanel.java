package com.retailpos.ui;

import com.retailpos.model.Category;
import com.retailpos.model.Product;
import com.retailpos.model.User;
import com.retailpos.service.AccessControl;
import com.retailpos.service.ProductCatalog;
import com.retailpos.util.FormatUtil;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Product Management screen.
 * FR6: every role can search products by code, name or category.
 * FR1, FR2: only a Manager sees the form to register/edit products and add categories.
 */
public class ProductsPanel extends JPanel implements Refreshable {

    private static final String[] COLUMNS = {"Code", "Name", "Category", "Price", "Reorder", "Stock", "Status"};
    private static final List<String> UNITS = List.of("pcs", "kg", "g", "litre", "ml", "pack", "dozen");

    private final ProductCatalog catalog;
    private final User user;
    private final boolean canEdit;

    // search bar
    private final JTextField searchField = UiTheme.textField("Search products…");
    private final JComboBox<Object> categoryFilter = new JComboBox<>();
    private final JComboBox<String> statusFilter = new JComboBox<>();

    // table
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private List<Product> rows = new ArrayList<>();

    // form (Manager only)
    private final JLabel formTitle = UiTheme.title("Register product");
    private final JLabel stockInfo = UiTheme.fieldLabel(" ");
    private final JTextField codeField = UiTheme.textField("e.g. P-1009");
    private final JTextField nameField = UiTheme.textField("Enter product name");
    private final JComboBox<Category> categoryBox = new JComboBox<>();
    private final JComboBox<String> unitBox = new JComboBox<>(UNITS.toArray(new String[0]));
    private final JTextField priceField = UiTheme.textField("0.00");
    private final JTextField reorderField = UiTheme.textField("0");
    private final JComboBox<String> statusBox = new JComboBox<>(ProductCatalog.STATUSES.toArray(new String[0]));
    private Product editing;          // null while registering a new product
    private boolean reloading;        // suppresses combo events while lists are refilled

    public ProductsPanel(AppServices services, User user) {
        this.catalog = services.productCatalog();
        this.user = user;
        this.canEdit = services.accessControl().hasPermission(user, AccessControl.REGISTER_PRODUCT);

        setOpaque(false);
        setLayout(new BorderLayout(0, 16));
        add(searchBar(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(16, 0));
        center.setOpaque(false);
        center.add(tableCard(), BorderLayout.CENTER);
        if (canEdit) {
            center.add(formCard(), BorderLayout.EAST);
        }
        add(center, BorderLayout.CENTER);

        UiSupport.run(this, this::refresh);
        if (canEdit) {
            clearForm();
        }
    }

    @Override
    public void refresh() {
        loadCategories();
        search();
    }

    // =====================================================================
    // Search (FR6)
    // =====================================================================

    private JComponent searchBar() {
        JPanel card = UiTheme.card(new BorderLayout(16, 0));

        categoryFilter.setRenderer(renderer(v -> v instanceof Category c ? c.getName() : "All categories"));
        statusFilter.setModel(new DefaultComboBoxModel<>(new String[]{null, ProductCatalog.STATUS_ACTIVE,
                ProductCatalog.STATUS_DISCONTINUED}));
        statusFilter.setRenderer(renderer(v -> v == null ? "All statuses" : FormatUtil.status((String) v)));
        categoryFilter.addActionListener(e -> searchFromUi());
        statusFilter.addActionListener(e -> searchFromUi());
        searchField.addActionListener(e -> searchFromUi());

        JPanel fields = new JPanel(new BorderLayout(12, 0));
        fields.setOpaque(false);
        fields.add(labelled("Search by product code, name or category", searchField), BorderLayout.CENTER);
        JPanel combos = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        combos.setOpaque(false);
        categoryFilter.setPreferredSize(new Dimension(190, categoryFilter.getPreferredSize().height));
        combos.add(labelled("Category", categoryFilter));
        combos.add(labelled("Status", statusFilter));
        fields.add(combos, BorderLayout.EAST);
        card.add(fields, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        buttons.setOpaque(false);
        JButton searchButton = UiTheme.primaryButton("Search");
        searchButton.addActionListener(e -> searchFromUi());
        buttons.add(searchButton);
        if (canEdit) {
            JButton newButton = UiTheme.successButton("+ New Product");
            newButton.addActionListener(e -> clearForm());
            buttons.add(newButton);
        }
        JPanel bottomAligned = new JPanel(new BorderLayout());
        bottomAligned.setOpaque(false);
        bottomAligned.add(buttons, BorderLayout.SOUTH);
        card.add(bottomAligned, BorderLayout.EAST);
        return card;
    }

    private void searchFromUi() {
        if (!reloading) {
            UiSupport.run(this, this::search);
        }
    }

    private void search() {
        Integer categoryID = categoryFilter.getSelectedItem() instanceof Category c ? c.getCategoryID() : null;
        rows = catalog.searchProducts(user, searchField.getText(), categoryID, (String) statusFilter.getSelectedItem());
        tableModel.setRowCount(0);
        for (Product p : rows) {
            tableModel.addRow(new Object[]{p.getProductCode(), p.getName(), p.getCategory().getName(),
                    FormatUtil.money(p.getSellingPrice()), p.getReorderLevel(), p.getCurrentStockQuantity(),
                    FormatUtil.status(p.getStatus())});
        }
    }

    private void loadCategories() {
        List<Category> categories = catalog.listCategories(user);
        reloading = true;
        try {
            Object filterSelection = categoryFilter.getSelectedItem();
            List<Object> filterItems = new ArrayList<>();
            filterItems.add("All categories");
            filterItems.addAll(categories);
            categoryFilter.setModel(new DefaultComboBoxModel<>(filterItems.toArray()));
            reselect(categoryFilter, filterSelection);

            Object formSelection = categoryBox.getSelectedItem();
            categoryBox.setModel(new DefaultComboBoxModel<>(categories.toArray(new Category[0])));
            if (!reselect(categoryBox, formSelection)) {
                categoryBox.setSelectedIndex(-1);
            }
        } finally {
            reloading = false;
        }
    }

    /** Selects the category with the same ID as {@code previous}; returns false if there is none. */
    private static boolean reselect(JComboBox<?> box, Object previous) {
        if (previous instanceof Category old) {
            for (int i = 0; i < box.getItemCount(); i++) {
                if (box.getItemAt(i) instanceof Category c && c.getCategoryID() == old.getCategoryID()) {
                    box.setSelectedIndex(i);
                    return true;
                }
            }
        }
        if (box.getItemCount() > 0 && !(box.getItemAt(0) instanceof Category)) {
            box.setSelectedIndex(0);
        }
        return false;
    }

    // =====================================================================
    // Table
    // =====================================================================

    private JComponent tableCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 12));
        card.add(UiTheme.title("Products"), BorderLayout.NORTH);
        UiTheme.styleTable(table);
        // Name and Category absorb narrow windows; the other columns keep room for their longest value.
        int[] widths = {72, 170, 110, 100, 80, 64, 116};
        int[] minWidths = {64, 80, 70, 96, 76, 62, 112};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            table.getColumnModel().getColumn(i).setMinWidth(minWidths[i]);
        }
        for (int col : new int[]{3, 4, 5}) {
            table.getColumnModel().getColumn(col).setCellRenderer(UiTheme.rightAligned());
        }
        table.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                c.setForeground("Active".equals(value) ? UiTheme.SUCCESS : UiTheme.MUTED);
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                return c;
            }
        });
        if (canEdit) {
            table.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && table.getSelectedRow() >= 0) {
                    editProduct(rows.get(table.getSelectedRow()));
                }
            });
        }
        card.add(UiTheme.tableScroll(table), BorderLayout.CENTER);
        return card;
    }

    // =====================================================================
    // Register / edit form (FR1, FR2) - Manager only
    // =====================================================================

    private JComponent formCard() {
        JPanel card = UiTheme.card(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(310, 0));

        categoryBox.setRenderer(renderer(v -> v instanceof Category c ? c.getName() : "Select category"));
        unitBox.setEditable(true);
        statusBox.setRenderer(renderer(v -> FormatUtil.status((String) v)));

        JButton addCategory = UiTheme.secondaryButton("+");
        addCategory.setToolTipText("Add a new category");
        addCategory.addActionListener(e -> UiSupport.run(this, this::addCategory));
        JPanel categoryRow = new JPanel(new BorderLayout(8, 0));
        categoryRow.setOpaque(false);
        categoryRow.add(categoryBox, BorderLayout.CENTER);
        categoryRow.add(addCategory, BorderLayout.EAST);

        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        stockInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(formTitle);
        card.add(Box.createVerticalStrut(2));
        card.add(stockInfo);
        card.add(Box.createVerticalStrut(10));
        addField(card, "Product code", codeField);
        addField(card, "Product name", nameField);
        addField(card, "Category", categoryRow);
        addField(card, "Unit", unitBox);
        addField(card, "Selling price (Rs)", priceField);
        addField(card, "Reorder level", reorderField);
        addField(card, "Current status", statusBox);
        card.add(Box.createVerticalGlue());

        JButton save = UiTheme.primaryButton("Save product");
        save.addActionListener(e -> UiSupport.run(this, this::saveProduct));
        JButton clear = UiTheme.secondaryButton("Clear");
        clear.addActionListener(e -> clearForm());
        JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
        buttons.setOpaque(false);
        buttons.add(save);
        buttons.add(clear);
        card.add(UiTheme.stretch(buttons));
        return card;
    }

    private void editProduct(Product p) {
        editing = p;
        formTitle.setText("Edit product");
        stockInfo.setText("In stock: " + p.getCurrentStockQuantity() + " " + p.getUnit() + " (read-only)");
        stockInfo.setToolTipText("Stock changes only through receipts, sales, returns and adjustments.");
        codeField.setText(p.getProductCode());
        codeField.setEnabled(false);
        nameField.setText(p.getName());
        reselect(categoryBox, p.getCategory());
        unitBox.setSelectedItem(p.getUnit());
        priceField.setText(p.getSellingPrice().toPlainString());
        reorderField.setText(String.valueOf(p.getReorderLevel()));
        statusBox.setSelectedItem(p.getStatus());
    }

    private void clearForm() {
        editing = null;
        table.clearSelection();
        formTitle.setText("Register product");
        stockInfo.setText("New products start with zero stock.");
        codeField.setText("");
        codeField.setEnabled(true);
        nameField.setText("");
        categoryBox.setSelectedIndex(-1);
        unitBox.setSelectedItem(UNITS.get(0));
        priceField.setText("");
        reorderField.setText("");
        statusBox.setSelectedItem(ProductCatalog.STATUS_ACTIVE);
        codeField.requestFocusInWindow();
    }

    private void saveProduct() {
        Product input = new Product(
                codeField.getText(),
                nameField.getText(),
                String.valueOf(unitBox.getEditor().getItem()),
                FormatUtil.parseMoney(priceField.getText(), "Selling price"),
                FormatUtil.parseWholeNumber(reorderField.getText(), "Reorder level"),
                (String) statusBox.getSelectedItem(),
                0,
                (Category) categoryBox.getSelectedItem());
        Product saved = editing == null ? catalog.registerProduct(user, input) : catalog.updateProduct(user, input);
        search();
        selectRow(saved.getProductCode());
        UiSupport.info(this, "Product " + saved.getProductCode() + " saved.");
    }

    private void addCategory() {
        String name = JOptionPane.showInputDialog(this, "New category name:", "Add category",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null) {
            return;   // cancelled
        }
        Category added = catalog.addCategory(user, name);
        loadCategories();
        reselect(categoryBox, added);
    }

    private void selectRow(String productCode) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getProductCode().equals(productCode)) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
        clearForm();   // saved product is hidden by the current filters
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static void addField(JPanel form, String label, JComponent field) {
        form.add(UiTheme.stretch(UiTheme.fieldLabel(label)));
        form.add(Box.createVerticalStrut(4));
        form.add(UiTheme.stretch(field));
        form.add(Box.createVerticalStrut(10));
    }

    private static JPanel labelled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.add(UiTheme.fieldLabel(label), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    /** Combo renderer that shows {@code text.apply(value)}. */
    static DefaultListCellRenderer renderer(Function<Object, String> text) {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list, text.apply(value), index, selected, focus);
            }
        };
    }
}
