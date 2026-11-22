package com.retailpos.ui;

import com.retailpos.model.User;
import com.retailpos.util.BusinessException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;

/** Sign-in screen (FR29). On success opens the DashboardFrame for the user's role. */
public class LoginFrame extends JFrame {

    private final AppServices services;
    private final JTextField usernameField = UiTheme.textField("Username");
    private final JPasswordField passwordField = new JPasswordField();

    public LoginFrame(AppServices services) {
        super("RetailPro – Sign in");
        this.services = services;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JPanel root = new JPanel(new GridLayout(1, 2));
        root.add(brandPanel());
        root.add(formSide());
        setContentPane(root);

        setSize(1000, 620);
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);
    }

    private JPanel brandPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiTheme.PRIMARY);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(UiTheme.label("RetailPro", Font.BOLD, 40f, Color.WHITE));
        text.add(Box.createVerticalStrut(8));
        text.add(UiTheme.label("Retail Store Sales & Inventory Management", Font.PLAIN, 16f, new Color(0xDBEAFE)));
        text.add(Box.createVerticalStrut(6));
        text.add(UiTheme.label("Desktop Application", Font.PLAIN, 14f, new Color(0xBFDBFE)));

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;
        c.insets = new Insets(0, 56, 0, 24);
        panel.add(text, c);
        return panel;
    }

    private JPanel formSide() {
        JPanel side = new JPanel(new GridBagLayout());
        side.setBackground(UiTheme.SIDEBAR);

        JPanel card = UiTheme.card(null, 32);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel heading = UiTheme.label("Sign in", Font.BOLD, 22f, UiTheme.TEXT);
        JLabel subheading = UiTheme.label("Enter your credentials to continue", Font.PLAIN, 13f, UiTheme.MUTED);
        passwordField.putClientProperty("JTextField.placeholderText", "Password");
        passwordField.putClientProperty("FlatLaf.style", "arc: 6; margin: 6,8,6,8; showRevealButton: true");
        JButton signIn = UiTheme.primaryButton("Sign in");
        signIn.addActionListener(e -> signIn());
        getRootPane().setDefaultButton(signIn);
        JLabel hint = UiTheme.label("Access depends on your role: Manager, Cashier or Storekeeper",
                Font.PLAIN, 11f, UiTheme.MUTED);

        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        subheading.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(heading);
        card.add(Box.createVerticalStrut(4));
        card.add(subheading);
        card.add(Box.createVerticalStrut(24));
        card.add(UiTheme.stretch(UiTheme.fieldLabel("Username")));
        card.add(Box.createVerticalStrut(6));
        card.add(UiTheme.stretch(usernameField));
        card.add(Box.createVerticalStrut(16));
        card.add(UiTheme.stretch(UiTheme.fieldLabel("Password")));
        card.add(Box.createVerticalStrut(6));
        card.add(UiTheme.stretch(passwordField));
        card.add(Box.createVerticalStrut(24));
        card.add(UiTheme.stretch(signIn));
        card.add(Box.createVerticalStrut(16));
        card.add(hint);
        card.setPreferredSize(new Dimension(420, card.getPreferredSize().height));

        side.add(card);
        return side;
    }

    private void signIn() {
        try {
            User user = services.accessControl().login(usernameField.getText(), new String(passwordField.getPassword()));
            new DashboardFrame(services, user).setVisible(true);
            dispose();
        } catch (BusinessException e) {
            UiSupport.showError(this, e);
            passwordField.setText("");
            passwordField.requestFocusInWindow();
        } catch (RuntimeException e) {
            UiSupport.showError(this, e);
        }
    }
}
