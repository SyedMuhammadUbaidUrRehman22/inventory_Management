package com.retailpos.ui;

import com.retailpos.model.User;
import com.retailpos.service.AccessControl;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
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

/**
 * Staff Accounts screen (Manager only). A Manager creates Cashier and Storekeeper accounts,
 * sees only the accounts they created, and can deactivate or reactivate them.
 */
public class StaffPanel extends JPanel implements Refreshable {

    private static final String ACTIVE = "Active";
    private static final String DEACTIVATED = "Deactivated";

    private final AccessControl accessControl;
    private final User manager;

    // list of this manager's staff
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Username", "Role", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JButton toggleButton = UiTheme.secondaryButton("Deactivate");
    private List<User> staff = new ArrayList<>();

    // create-account form
    private final JTextField usernameField = UiTheme.textField("Choose a username");
    private final JComboBox<String> roleBox = new JComboBox<>(AccessControl.STAFF_ROLES.toArray(new String[0]));
    private final JPasswordField passwordField = UiTheme.passwordField("Password");
    private final JPasswordField confirmField = UiTheme.passwordField("Repeat the password");

    public StaffPanel(AppServices services, User manager) {
        this.accessControl = services.accessControl();
        this.manager = manager;

        setOpaque(false);
        setLayout(new BorderLayout(16, 0));
        add(listCard(), BorderLayout.CENTER);
        JComponent form = formCard();
        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(form, BorderLayout.NORTH);
        east.setPreferredSize(new Dimension(360, 0));
        add(east, BorderLayout.EAST);

        clearForm();
        UiSupport.run(this, this::refresh);
    }

    @Override
    public void refresh() {
        loadStaff(null);
    }

    // =====================================================================
    // My staff
    // =====================================================================

    private JComponent listCard() {
        JPanel card = UiTheme.card(new BorderLayout(0, 12));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(UiTheme.title("My staff"));
        heading.add(Box.createVerticalStrut(4));
        heading.add(UiTheme.label("Accounts you created. Deactivated accounts cannot sign in.",
                Font.PLAIN, 12f, UiTheme.MUTED));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(heading, BorderLayout.WEST);
        toggleButton.addActionListener(e -> UiSupport.run(this, this::toggleSelected));
        JPanel buttonHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonHolder.setOpaque(false);
        buttonHolder.add(toggleButton);
        top.add(buttonHolder, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        UiTheme.styleTable(table);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean sel, boolean focus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, column);
                c.setForeground(ACTIVE.equals(value) ? UiTheme.SUCCESS : UiTheme.MUTED);
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                return c;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateToggleButton();
            }
        });
        card.add(UiTheme.tableScroll(table), BorderLayout.CENTER);
        return card;
    }

    /** Reloads the list and selects the account with this ID (if given). */
    private void loadStaff(Integer selectUserID) {
        staff = accessControl.listMyStaff(manager);
        model.setRowCount(0);
        for (User u : staff) {
            model.addRow(new Object[]{u.getUsername(), AccessControl.roleName(u), u.isActive() ? ACTIVE : DEACTIVATED});
        }
        if (selectUserID != null) {
            for (int i = 0; i < staff.size(); i++) {
                if (staff.get(i).getUserID() == selectUserID) {
                    table.setRowSelectionInterval(i, i);
                }
            }
        }
        updateToggleButton();
    }

    private User selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : staff.get(row);
    }

    private void updateToggleButton() {
        User u = selected();
        toggleButton.setEnabled(u != null);
        toggleButton.setText(u != null && !u.isActive() ? "Reactivate" : "Deactivate");
    }

    private void toggleSelected() {
        User u = selected();
        if (u == null) {
            return;
        }
        boolean activate = !u.isActive();
        String question = activate
                ? "Reactivate " + u.getUsername() + "? They will be able to sign in again."
                : "Deactivate " + u.getUsername() + "? They will not be able to sign in until you reactivate"
                + " the account. Their past sales and stock records are kept.";
        if (!UiSupport.confirm(this, question)) {
            return;
        }
        accessControl.setStaffActive(manager, u.getUserID(), activate);
        loadStaff(u.getUserID());
    }

    // =====================================================================
    // Create staff account
    // =====================================================================

    private JComponent formCard() {
        JPanel card = UiTheme.card(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel title = UiTheme.title("Create staff account");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(4));
        JLabel note = UiTheme.label("New accounts get only their role's permissions.", Font.PLAIN, 12f, UiTheme.MUTED);
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(note);
        card.add(Box.createVerticalStrut(16));

        roleBox.setRenderer(ProductsPanel.renderer(v -> v == null ? "Select role" : v.toString()));
        field(card, "Username", usernameField, 12);
        field(card, "Role", roleBox, 12);
        field(card, "Password", passwordField, 4);
        JLabel rules = UiTheme.label("At least " + AccessControl.PASSWORD_MIN + " characters, with letters and numbers.",
                Font.PLAIN, 11f, UiTheme.MUTED);
        rules.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(rules);
        card.add(Box.createVerticalStrut(12));
        field(card, "Confirm password", confirmField, 20);

        JButton create = UiTheme.primaryButton("Create account");
        create.addActionListener(e -> UiSupport.run(this, this::createAccount));
        JButton clear = UiTheme.secondaryButton("Clear");
        clear.addActionListener(e -> clearForm());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(create);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(clear);
        card.add(UiTheme.stretch(buttons));
        return card;
    }

    private void createAccount() {
        User created;
        try {
            created = accessControl.createStaffAccount(manager, usernameField.getText(),
                    new String(passwordField.getPassword()), new String(confirmField.getPassword()),
                    (String) roleBox.getSelectedItem());
        } finally {
            passwordField.setText("");
            confirmField.setText("");
        }
        clearForm();
        loadStaff(created.getUserID());
        UiSupport.info(this, AccessControl.roleName(created) + " account '" + created.getUsername()
                + "' created. Give them the password you chose.");
    }

    private void clearForm() {
        usernameField.setText("");
        roleBox.setSelectedIndex(-1);
        passwordField.setText("");
        confirmField.setText("");
        usernameField.requestFocusInWindow();
    }

    private static void field(JPanel card, String label, JComponent field, int gapAfter) {
        card.add(UiTheme.stretch(UiTheme.fieldLabel(label)));
        card.add(Box.createVerticalStrut(6));
        card.add(UiTheme.stretch(field));
        card.add(Box.createVerticalStrut(gapAfter));
    }
}
