package com.retailpos.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;

/** Colours and component factories matching the mockups in docs/GUI_Screens. */
final class UiTheme {

    static final Color PRIMARY = new Color(0x2563EB);
    static final Color SUCCESS = new Color(0x16A34A);
    static final Color DANGER = new Color(0xDC2626);
    static final Color SIDEBAR = new Color(0x0F172A);
    static final Color SIDEBAR_HOVER = new Color(0x1E293B);
    static final Color SIDEBAR_TEXT = new Color(0xCBD5E1);
    static final Color SIDEBAR_MUTED = new Color(0x94A3B8);
    static final Color PAGE = new Color(0xF1F5F9);
    static final Color BORDER = new Color(0xE2E8F0);
    static final Color INPUT_BORDER = new Color(0xCBD5E1);
    static final Color TEXT = new Color(0x0F172A);
    static final Color MUTED = new Color(0x64748B);
    static final Color TABLE_HEADER = new Color(0xF8FAFC);
    static final Color SELECTED = new Color(0xEFF6FF);
    static final Color AVATAR_BG = new Color(0xDBEAFE);

    private UiTheme() {
    }

    /** White rounded card with a light border and 20px padding. */
    static JPanel card(LayoutManager layout) {
        return card(layout, 20);
    }

    /**
     * White rounded card with a light border and the given padding.
     * FlatLaf rounds the background itself because the border is a FlatLineBorder with an arc.
     * (Do not also set a FlatLaf "arc" style on the panel: that removes the border.)
     */
    static JPanel card(LayoutManager layout, int padding) {
        JPanel card = new JPanel(layout);
        card.setBackground(Color.WHITE);
        card.setBorder(new FlatLineBorder(new Insets(padding, padding, padding, padding), BORDER, 1f, 12));
        return card;
    }

    static JLabel title(String text) {
        return label(text, Font.BOLD, 15f, TEXT);
    }

    static JLabel fieldLabel(String text) {
        return label(text, Font.PLAIN, 12f, MUTED);
    }

    static JLabel label(String text, int style, float size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(style, size));
        label.setForeground(color);
        return label;
    }

    static JButton primaryButton(String text) {
        return filledButton(text, PRIMARY, "#1D4ED8", "#1E40AF");
    }

    static JButton successButton(String text) {
        return filledButton(text, SUCCESS, "#15803D", "#166534");
    }

    private static JButton filledButton(String text, Color background, String hover, String pressed) {
        JButton b = new JButton(text);
        b.setBackground(background);
        b.setForeground(Color.WHITE);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc: 8; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 8,18,8,18;"
                        + " hoverBackground: " + hover + "; pressedBackground: " + pressed);
        return b;
    }

    static JButton secondaryButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(Color.WHITE);
        b.setForeground(TEXT);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.putClientProperty(FlatClientProperties.STYLE,
                "arc: 8; borderColor: #CBD5E1; focusWidth: 0; innerFocusWidth: 0; margin: 8,18,8,18");
        return b;
    }

    static JTextField textField(String placeholder) {
        JTextField field = new JTextField();
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.STYLE, "arc: 6; margin: 6,8,6,8");
        return field;
    }

    /** Password field styled like {@link #textField}, with an eye button to reveal what was typed. */
    static JPasswordField passwordField(String placeholder) {
        JPasswordField field = new JPasswordField();
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        field.putClientProperty(FlatClientProperties.STYLE, "arc: 6; margin: 6,8,6,8; showRevealButton: true");
        return field;
    }

    /** Text-only button that looks like a link. */
    static JButton linkButton(String text) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        b.putClientProperty(FlatClientProperties.STYLE, "margin: 0,0,0,0; focusWidth: 0; innerFocusWidth: 0;"
                + " hoverBackground: null; pressedBackground: null");
        b.setForeground(PRIMARY);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12f));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Lets a component fill the width of a vertical BoxLayout, left-aligned. */
    static <T extends JComponent> T stretch(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }

    static void styleTable(JTable table) {
        table.setRowHeight(40);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setGridColor(BORDER);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(SELECTED);
        table.setSelectionForeground(TEXT);
        table.setFillsViewportHeight(true);
        table.setBackground(Color.WHITE);
        table.getTableHeader().setReorderingAllowed(false);
        JTableHeader header = table.getTableHeader();
        header.setBackground(TABLE_HEADER);
        header.setForeground(MUTED);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 40));
        header.putClientProperty(FlatClientProperties.STYLE, "separatorColor: #F8FAFC; bottomSeparatorColor: #E2E8F0");

        // Align each header like its column: right for right-aligned (numeric) columns, left otherwise.
        TableCellRenderer base = header.getDefaultRenderer();
        header.setDefaultRenderer((t, value, selected, focus, row, column) -> {
            Component c = base.getTableCellRendererComponent(t, value, selected, focus, row, column);
            if (c instanceof JLabel label) {
                TableCellRenderer cell = t.getColumnModel().getColumn(column).getCellRenderer();
                boolean right = cell instanceof JLabel l && l.getHorizontalAlignment() == SwingConstants.RIGHT;
                label.setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
            }
            return c;
        });
    }

    static JScrollPane tableScroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);
        return scroll;
    }

    /** Right-aligned cell renderer for numbers and money. */
    static DefaultTableCellRenderer rightAligned() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        return r;
    }
}
