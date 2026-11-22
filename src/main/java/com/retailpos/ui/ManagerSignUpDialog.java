package com.retailpos.ui;

import com.retailpos.model.Manager;
import com.retailpos.service.AccessControl;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * Manager sign-up, opened from the login screen. Needs the store's setup code,
 * so only people the store owner trusts can create a Manager account.
 */
class ManagerSignUpDialog extends JDialog {

    private final AccessControl accessControl;
    private final JTextField usernameField = UiTheme.textField("Choose a username");
    private final JPasswordField passwordField = UiTheme.passwordField("Password");
    private final JPasswordField confirmField = UiTheme.passwordField("Repeat the password");
    private final JPasswordField codeField = UiTheme.passwordField("Setup code from the store owner");
    private Manager created;

    private ManagerSignUpDialog(Window owner, AccessControl accessControl) {
        super(owner, "RetailPro – Create manager account", ModalityType.APPLICATION_MODAL);
        this.accessControl = accessControl;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel card = UiTheme.card(null, 32);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        add(card, "Create manager account", Font.BOLD, 20f, UiTheme.TEXT, 4);
        add(card, "Managers create the Cashier and Storekeeper accounts for their staff.",
                Font.PLAIN, 12f, UiTheme.MUTED, 20);
        field(card, "Username", usernameField, 14);
        field(card, "Password", passwordField, 4);
        add(card, "At least " + AccessControl.PASSWORD_MIN + " characters, with letters and numbers.",
                Font.PLAIN, 11f, UiTheme.MUTED, 14);
        field(card, "Confirm password", confirmField, 14);
        field(card, "Setup code", codeField, 24);

        JButton create = UiTheme.primaryButton("Create account");
        create.addActionListener(e -> UiSupport.run(this, this::createAccount));
        JButton cancel = UiTheme.secondaryButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(create);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(cancel);
        card.add(UiTheme.stretch(buttons));

        getRootPane().setDefaultButton(create);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        card.setPreferredSize(new Dimension(440, card.getPreferredSize().height));
        setContentPane(card);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog and returns the new Manager, or null if the user cancelled. */
    static Manager show(Window owner, AccessControl accessControl) {
        ManagerSignUpDialog dialog = new ManagerSignUpDialog(owner, accessControl);
        dialog.setVisible(true);
        return dialog.created;
    }

    private void createAccount() {
        try {
            created = accessControl.signUpManager(usernameField.getText(), new String(passwordField.getPassword()),
                    new String(confirmField.getPassword()), new String(codeField.getPassword()));
        } catch (RuntimeException e) {
            codeField.setText("");
            throw e;
        }
        UiSupport.info(this, "Manager account '" + created.getUsername() + "' created. You can sign in now.");
        dispose();
    }

    private static void add(JPanel card, String text, int style, float size, Color color, int gapAfter) {
        JLabel label = UiTheme.label(text, style, size, color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(label);
        card.add(Box.createVerticalStrut(gapAfter));
    }

    private static void field(JPanel card, String label, JComponent field, int gapAfter) {
        card.add(UiTheme.stretch(UiTheme.fieldLabel(label)));
        card.add(Box.createVerticalStrut(6));
        card.add(UiTheme.stretch(field));
        card.add(Box.createVerticalStrut(gapAfter));
    }
}
