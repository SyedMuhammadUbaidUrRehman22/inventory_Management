package com.retailpos.ui;

import com.retailpos.model.User;
import com.retailpos.service.AccessControl;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;

/** Lets the signed-in user (any role) change their own password. */
class ChangePasswordDialog extends JDialog {

    private final AccessControl accessControl;
    private final User user;
    private final JPasswordField currentField = UiTheme.passwordField("Your current password");
    private final JPasswordField newField = UiTheme.passwordField("New password");
    private final JPasswordField confirmField = UiTheme.passwordField("Repeat the new password");
    private boolean changed;

    private ChangePasswordDialog(Window owner, AccessControl accessControl, User user) {
        super(owner, "RetailPro – Change password", ModalityType.APPLICATION_MODAL);
        this.accessControl = accessControl;
        this.user = user;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel card = UiTheme.card(null, 32);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        text(card, "Change password", Font.BOLD, 20f, UiTheme.TEXT, 4);
        text(card, "Signed in as " + user.getUsername() + " (" + AccessControl.roleName(user) + ")",
                Font.PLAIN, 12f, UiTheme.MUTED, 20);
        field(card, "Current password", currentField, 14);
        field(card, "New password", newField, 4);
        text(card, "At least " + AccessControl.PASSWORD_MIN + " characters, with letters and numbers.",
                Font.PLAIN, 11f, UiTheme.MUTED, 14);
        field(card, "Confirm new password", confirmField, 24);

        JButton save = UiTheme.primaryButton("Change password");
        save.addActionListener(e -> UiSupport.run(this, this::changePassword));
        JButton cancel = UiTheme.secondaryButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(save);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(cancel);
        card.add(UiTheme.stretch(buttons));

        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        card.setPreferredSize(new Dimension(420, card.getPreferredSize().height));
        setContentPane(card);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Shows the dialog; returns true if the password was changed. */
    static boolean show(Window owner, AccessControl accessControl, User user) {
        ChangePasswordDialog dialog = new ChangePasswordDialog(owner, accessControl, user);
        dialog.setVisible(true);
        return dialog.changed;
    }

    private void changePassword() {
        try {
            accessControl.changePassword(user, new String(currentField.getPassword()),
                    new String(newField.getPassword()), new String(confirmField.getPassword()));
        } catch (RuntimeException e) {
            currentField.setText("");
            currentField.requestFocusInWindow();
            throw e;
        }
        changed = true;
        UiSupport.info(this, "Your password has been changed. Use the new password next time you sign in.");
        dispose();
    }

    private static void text(JPanel card, String text, int style, float size, Color color, int gapAfter) {
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
