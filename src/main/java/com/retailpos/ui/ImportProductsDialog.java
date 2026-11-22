package com.retailpos.ui;

import com.retailpos.model.Product;
import com.retailpos.model.User;
import com.retailpos.service.ProductCatalog;
import com.retailpos.util.BusinessException;
import com.retailpos.util.FormatUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Adds new products from an Excel file (Manager and Storekeeper).
 * The file is checked first and shown as a preview; nothing is saved until "Import" is pressed,
 * and then all products are added in one go, or none.
 */
class ImportProductsDialog extends JDialog {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static File lastFolder;   // remembered while the app runs

    private final ProductCatalog catalog;
    private final User user;

    private final JLabel fileLabel = UiTheme.label("No file chosen", Font.PLAIN, 12f, UiTheme.MUTED);
    private final JLabel statusLabel = UiTheme.label(" ", Font.BOLD, 14f, UiTheme.TEXT);
    private final JLabel notesLabel = UiTheme.label(" ", Font.PLAIN, 12f, UiTheme.MUTED);
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Code", "Name", "Category", "Unit", "Price", "Reorder", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTextArea problems = new JTextArea();
    private final JButton importButton = UiTheme.successButton("Import");
    private ProductCatalog.ImportPreview preview;
    private int imported;

    private ImportProductsDialog(Window owner, ProductCatalog catalog, User user) {
        super(owner, "RetailPro – Import products from Excel", ModalityType.APPLICATION_MODAL);
        this.catalog = catalog;
        this.user = user;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel card = UiTheme.card(new BorderLayout(0, 16), 28);
        card.add(header(), BorderLayout.NORTH);
        card.add(centerArea(), BorderLayout.CENTER);
        card.add(footer(), BorderLayout.SOUTH);
        setContentPane(card);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        showEmpty();
        setSize(900, 620);
        setMinimumSize(new Dimension(760, 480));
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns how many products were imported (0 if none). */
    static int show(Window owner, ProductCatalog catalog, User user) {
        ImportProductsDialog dialog = new ImportProductsDialog(owner, catalog, user);
        dialog.setVisible(true);
        return dialog.imported;
    }

    // ---- layout ----

    private JComponent header() {
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        left(top, UiTheme.label("Import products from Excel", Font.BOLD, 20f, UiTheme.TEXT));
        top.add(Box.createVerticalStrut(4));
        left(top, UiTheme.label("Adds new products only. Existing products and stock are never changed. "
                + "If any row has a problem, nothing is imported.", Font.PLAIN, 12f, UiTheme.MUTED));
        top.add(Box.createVerticalStrut(16));

        JButton choose = UiTheme.primaryButton("Choose file…");
        choose.addActionListener(e -> UiSupport.run(this, this::chooseFile));
        JButton template = UiTheme.secondaryButton("Download template");
        template.addActionListener(e -> UiSupport.run(this, this::saveTemplate));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(choose);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(template);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(fileLabel);
        left(top, buttons);
        top.add(Box.createVerticalStrut(16));
        left(top, statusLabel);
        top.add(Box.createVerticalStrut(2));
        left(top, notesLabel);
        return top;
    }

    private JComponent centerArea() {
        center.setOpaque(false);

        JPanel empty = new JPanel(new BorderLayout());
        empty.setOpaque(false);
        JTextArea hint = new JTextArea("Choose an Excel file (.xlsx) with one product per row. Row 1 must hold the "
                + "column names: Product code, Product name, Category, Unit, Selling price, and optionally "
                + "Reorder level and Status.\n\nNot sure? Download the template, fill it in, and choose it here. "
                + "Its Help sheet lists the rules and the categories you can use.");
        hint.setEditable(false);
        hint.setFocusable(false);
        hint.setOpaque(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setForeground(UiTheme.MUTED);
        hint.setFont(hint.getFont().deriveFont(13f));
        hint.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        empty.add(hint, BorderLayout.CENTER);
        center.add(empty, "empty");

        JTable table = new JTable(model);
        UiTheme.styleTable(table);
        int[] widths = {90, 220, 120, 70, 100, 70, 100};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(4).setCellRenderer(UiTheme.rightAligned());
        table.getColumnModel().getColumn(5).setCellRenderer(UiTheme.rightAligned());
        center.add(UiTheme.tableScroll(table), "products");

        problems.setEditable(false);
        problems.setLineWrap(true);
        problems.setWrapStyleWord(true);
        problems.setForeground(UiTheme.DANGER);
        problems.setBackground(new Color(0xFEF2F2));
        problems.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(problems);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        center.add(scroll, "problems");
        return center;
    }

    private JComponent footer() {
        importButton.addActionListener(e -> UiSupport.run(this, this::importProducts));
        JButton close = UiTheme.secondaryButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(close);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(importButton);
        return buttons;
    }

    private static void left(JPanel panel, JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
    }

    // ---- actions ----

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser(lastFolder);
        chooser.setDialogTitle("Choose the Excel file with the products");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel workbook (*.xlsx)", "xlsx"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        lastFolder = file.getParentFile();
        fileLabel.setText(file.getName());
        if (file.length() > MAX_FILE_BYTES) {
            showEmpty();
            throw new BusinessException("The file is larger than 10 MB. Split it into smaller files.");
        }
        try (InputStream in = new FileInputStream(file)) {
            showPreview(catalog.previewImport(user, in));
        } catch (IOException e) {
            showEmpty();
            throw new BusinessException("The file could not be opened: " + e.getMessage());
        } catch (RuntimeException e) {
            showEmpty();
            throw e;
        }
    }

    private void showPreview(ProductCatalog.ImportPreview result) {
        preview = result;
        notesLabel.setText(result.notes().isEmpty() ? " " : String.join("  ", result.notes()));
        if (!result.errors().isEmpty()) {
            int n = result.errors().size();
            statusLabel.setForeground(UiTheme.DANGER);
            statusLabel.setText(n + (n == 1 ? " problem" : " problems")
                    + " found. Fix the file, save it, and choose it again. Nothing has been imported.");
            problems.setText(String.join("\n", result.errors()));
            problems.setCaretPosition(0);
            cards.show(center, "problems");
            importButton.setText("Import");
            importButton.setEnabled(false);
            return;
        }
        List<Product> products = result.products();
        model.setRowCount(0);
        for (Product p : products) {
            model.addRow(new Object[]{p.getProductCode(), p.getName(), p.getCategory().getName(), p.getUnit(),
                    FormatUtil.money(p.getSellingPrice()), p.getReorderLevel(), FormatUtil.status(p.getStatus())});
        }
        statusLabel.setForeground(UiTheme.SUCCESS);
        statusLabel.setText(products.size() + (products.size() == 1 ? " product is" : " products are")
                + " ready to import. Check them, then press Import.");
        cards.show(center, "products");
        importButton.setText("Import " + products.size() + (products.size() == 1 ? " product" : " products"));
        importButton.setEnabled(true);
    }

    private void showEmpty() {
        preview = null;
        statusLabel.setForeground(UiTheme.TEXT);
        statusLabel.setText(" ");
        notesLabel.setText(" ");
        cards.show(center, "empty");
        importButton.setText("Import");
        importButton.setEnabled(false);
    }

    private void importProducts() {
        if (preview == null || !preview.canImport()) {
            return;
        }
        imported = catalog.importProducts(user, preview.products());
        UiSupport.info(this, imported + (imported == 1 ? " product was" : " products were")
                + " added with 0 stock.");
        dispose();
    }

    private void saveTemplate() {
        JFileChooser chooser = new JFileChooser(lastFolder);
        chooser.setDialogTitle("Save the import template");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel workbook (*.xlsx)", "xlsx"));
        chooser.setSelectedFile(new File(lastFolder, "product-import-template.xlsx"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".xlsx")) {
            file = new File(file.getParentFile(), file.getName() + ".xlsx");
        }
        lastFolder = file.getParentFile();
        if (file.exists() && !UiSupport.confirm(this, file.getName() + " already exists. Replace it?")) {
            return;
        }
        try (OutputStream out = new FileOutputStream(file)) {
            catalog.writeImportTemplate(user, out);
        } catch (IOException e) {
            throw new BusinessException("The template could not be saved: " + e.getMessage());
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
                && UiSupport.confirm(this, "Template saved as " + file.getName() + ". Open it now?")) {
            try {
                Desktop.getDesktop().open(file);
            } catch (IOException e) {
                throw new BusinessException("The template was saved but could not be opened: " + e.getMessage());
            }
        }
    }
}
