package com.retailpos.ui;

import com.retailpos.model.SalesReceipt;
import com.retailpos.util.ReceiptFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.print.PrinterException;

/**
 * Shows a sales receipt and prints it (FR21). Matches {@code docs/GUI_Screens/08_Sales_Receipt.svg}.
 */
final class SalesReceiptDialog {

    private SalesReceiptDialog() {
    }

    static void show(Component parent, SalesReceipt receipt) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Sales Receipt", JDialog.ModalityType.APPLICATION_MODAL);

        JTextArea text = new JTextArea(ReceiptFormatter.format(receipt));
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        text.setBackground(Color.WHITE);
        text.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        text.setCaretPosition(0);

        JButton print = UiTheme.primaryButton("Print receipt");
        print.addActionListener(e -> {
            try {
                text.print();
            } catch (PrinterException ex) {
                UiSupport.showError(dialog, new com.retailpos.util.BusinessException("Print failed: " + ex.getMessage()));
            }
        });
        JButton close = UiTheme.secondaryButton("Close");
        close.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.add(print);
        buttons.add(close);

        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(new JScrollPane(text), BorderLayout.CENTER);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(print);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 460), Math.min(Math.max(dialog.getHeight(), 420), 720));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
