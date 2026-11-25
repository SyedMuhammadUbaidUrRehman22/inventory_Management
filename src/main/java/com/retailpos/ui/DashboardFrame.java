package com.retailpos.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.retailpos.model.User;
import com.retailpos.service.AccessControl;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Main window after login: sidebar menu, header with the signed-in user, and the screens.
 * FR30: the menu only contains the screens the user's role is allowed to use.
 */
public class DashboardFrame extends JFrame {

    /** A menu entry. {@code permission == null} means every role; {@code factory == null} means not built yet. */
    private record NavItem(String label, String title, String permission, String phase,
                           Supplier<JComponent> factory) {
    }

    private final AppServices services;
    private final User user;
    private final Map<String, NavItem> items = new LinkedHashMap<>();
    private final Map<String, JComponent> screens = new HashMap<>();
    private final Map<String, JToggleButton> menuButtons = new HashMap<>();
    private final ButtonGroup menuGroup = new ButtonGroup();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JLabel titleLabel = UiTheme.label("", Font.BOLD, 20f, UiTheme.TEXT);

    public DashboardFrame(AppServices services, User user) {
        super("RetailPro – " + user.getUsername() + " (" + AccessControl.roleName(user) + ")");
        this.services = services;
        this.user = user;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        List<NavItem> all = List.of(
                new NavItem("Dashboard", "Dashboard", null, null, this::homeScreen),
                new NavItem("Products", "Product Management", AccessControl.SEARCH_PRODUCTS, null,
                        () -> new ProductsPanel(services, user)),
                new NavItem("Suppliers", "Supplier Management", AccessControl.VIEW_SUPPLIERS, null,
                        () -> new SuppliersPanel(services, user)),
                new NavItem("Purchase Orders", "Purchase Orders", AccessControl.MANAGE_PURCHASE_ORDERS, null,
                        () -> new PurchaseOrderPanel(services, user)),
                new NavItem("Receive Stock", "Receive Stock", AccessControl.RECORD_STOCK_RECEIPT, null,
                        () -> new ReceiveStockPanel(services, user)),
                new NavItem("New Sale", "New Sale", AccessControl.CREATE_SALE, null,
                        () -> new NewSalePanel(services, user)),
                new NavItem("Returns", "Customer Returns", AccessControl.RECORD_RETURN, "Phase 4", null),
                new NavItem("Adjustments", "Stock Adjustments", AccessControl.RECORD_STOCK_ADJUSTMENT, "Phase 4", null),
                new NavItem("Reports", "Reports", AccessControl.GENERATE_REPORTS, null,
                        () -> new ReportsPanel(services, user)),
                new NavItem("Staff", "Staff Accounts", AccessControl.MANAGE_STAFF, null,
                        () -> new StaffPanel(services, user)));
        for (NavItem item : all) {
            if (item.permission() == null || services.accessControl().hasPermission(user, item.permission())) {
                items.put(item.label(), item);
            }
        }

        JPanel main = new JPanel(new BorderLayout());
        main.add(header(), BorderLayout.NORTH);
        content.setBackground(UiTheme.PAGE);
        content.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        main.add(content, BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.add(sidebar(), BorderLayout.WEST);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);

        setSize(1280, 800);
        setMinimumSize(new Dimension(1100, 680));
        setLocationRelativeTo(null);
        setExtendedState(MAXIMIZED_BOTH);   // restores to 1280x800
        showScreen("Dashboard");
    }

    /** Shows the screen behind a menu entry (only entries allowed for this user exist). */
    public void showScreen(String label) {
        NavItem item = items.get(label);
        if (item == null) {
            return;
        }
        JComponent screen = screens.get(label);
        if (screen == null) {
            screen = item.factory() != null ? item.factory().get() : placeholder(item);
            screens.put(label, screen);
            content.add(screen, label);
        } else if (screen instanceof Refreshable refreshable) {
            UiSupport.run(this, refreshable::refresh);
        }
        cards.show(content, label);
        titleLabel.setText(item.title());
        menuButtons.get(label).setSelected(true);
    }

    // ---- layout pieces ----

    private JPanel sidebar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBackground(UiTheme.SIDEBAR);
        bar.setBorder(BorderFactory.createEmptyBorder(24, 12, 20, 12));
        bar.setPreferredSize(new Dimension(240, 0));

        JLabel brand = UiTheme.label("RetailPro", Font.BOLD, 22f, Color.WHITE);
        JLabel tagline = UiTheme.label("Sales & Inventory", Font.PLAIN, 12f, UiTheme.SIDEBAR_MUTED);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        tagline.setBorder(BorderFactory.createEmptyBorder(2, 12, 0, 0));
        bar.add(brand);
        bar.add(tagline);
        bar.add(Box.createVerticalStrut(28));

        for (NavItem item : items.values()) {
            JToggleButton button = menuButton(item.label());
            button.addActionListener(e -> showScreen(item.label()));
            menuGroup.add(button);
            menuButtons.put(item.label(), button);
            bar.add(button);
            bar.add(Box.createVerticalStrut(6));
        }

        bar.add(Box.createVerticalGlue());
        JSeparator line = new JSeparator();
        line.setForeground(UiTheme.SIDEBAR_HOVER);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        bar.add(line);
        bar.add(Box.createVerticalStrut(12));
        if (services.accessControl().hasPermission(user, AccessControl.CHANGE_OWN_PASSWORD)) {
            JButton changePassword = new JButton("⚿  Change password");
            styleMenuButton(changePassword);
            changePassword.addActionListener(e -> ChangePasswordDialog.show(this, services.accessControl(), user));
            bar.add(changePassword);
            bar.add(Box.createVerticalStrut(6));
        }
        JButton signOut = new JButton("⏻  Sign out");
        styleMenuButton(signOut);
        signOut.addActionListener(e -> signOut());
        bar.add(signOut);
        return bar;
    }

    private static JToggleButton menuButton(String text) {
        JToggleButton b = new JToggleButton(text);
        styleMenuButton(b);
        return b;
    }

    private static void styleMenuButton(javax.swing.AbstractButton b) {
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBackground(UiTheme.SIDEBAR);
        b.setForeground(UiTheme.SIDEBAR_TEXT);
        b.setFont(b.getFont().deriveFont(14f));
        b.setFocusable(false);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 8; borderWidth: 0; focusWidth: 0; innerFocusWidth: 0;"
                + " margin: 10,14,10,14; hoverBackground: #1E293B; pressedBackground: #1E293B;"
                + " selectedBackground: #2563EB; selectedForeground: #FFFFFF");
    }

    private JPanel header() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER),
                BorderFactory.createEmptyBorder(14, 32, 14, 24)));
        header.add(titleLabel, BorderLayout.WEST);

        JPanel who = new JPanel();
        who.setOpaque(false);
        who.setLayout(new BoxLayout(who, BoxLayout.Y_AXIS));
        JLabel name = UiTheme.label(user.getUsername(), Font.BOLD, 13f, UiTheme.TEXT);
        JLabel role = UiTheme.label(AccessControl.roleName(user), Font.PLAIN, 12f, UiTheme.MUTED);
        name.setAlignmentX(Component.RIGHT_ALIGNMENT);
        role.setAlignmentX(Component.RIGHT_ALIGNMENT);
        who.add(name);
        who.add(role);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(who);
        right.add(new Avatar(user.getUsername()));
        header.add(right, BorderLayout.EAST);
        return header;
    }

    /** Phase 1 home: welcome card plus shortcuts to the screens this role can open. */
    private JComponent homeScreen() {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);

        JPanel card = UiTheme.card(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel welcome = UiTheme.label("Welcome, " + user.getUsername(), Font.BOLD, 20f, UiTheme.TEXT);
        JLabel signedIn = UiTheme.label("Signed in as " + AccessControl.roleName(user)
                + ". The menu shows the functions available to your role.", Font.PLAIN, 13f, UiTheme.MUTED);
        welcome.setAlignmentX(Component.LEFT_ALIGNMENT);
        signedIn.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(welcome);
        card.add(Box.createVerticalStrut(6));
        card.add(signedIn);
        card.add(Box.createVerticalStrut(24));
        JLabel quick = UiTheme.title("Quick actions");
        quick.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(quick);
        card.add(Box.createVerticalStrut(12));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        boolean first = true;
        for (NavItem item : items.values()) {
            if (item.label().equals("Dashboard")) {
                continue;
            }
            JButton b = first ? UiTheme.primaryButton(item.label()) : UiTheme.secondaryButton(item.label());
            b.addActionListener(e -> showScreen(item.label()));
            actions.add(b);
            actions.add(Box.createHorizontalStrut(12));
            first = false;
        }
        card.add(actions);

        page.add(card, BorderLayout.NORTH);
        return page;
    }

    private JComponent placeholder(NavItem item) {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        JPanel card = UiTheme.card(new BorderLayout(0, 8));
        card.add(UiTheme.title(item.title()), BorderLayout.NORTH);
        card.add(UiTheme.label("This screen is part of " + item.phase() + " and is not available yet.",
                Font.PLAIN, 13f, UiTheme.MUTED), BorderLayout.CENTER);
        page.add(card, BorderLayout.NORTH);
        return page;
    }

    private void signOut() {
        dispose();
        new LoginFrame(services).setVisible(true);
    }

    /** Round badge with the user's initials, as in the mockups. */
    private static final class Avatar extends JComponent {
        private final String initials;

        Avatar(String username) {
            String clean = username.replaceAll("[^A-Za-z]", "");
            this.initials = clean.isEmpty() ? "?" : clean.substring(0, Math.min(2, clean.length())).toUpperCase();
            setPreferredSize(new Dimension(36, 36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(UiTheme.AVATAR_BG);
            g2.fillOval(0, 0, 36, 36);
            g2.setColor(UiTheme.PRIMARY);
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            Insets in = getInsets();
            int w = g2.getFontMetrics().stringWidth(initials);
            int y = (36 + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
            g2.drawString(initials, (36 - w) / 2 + in.left, y);
            g2.dispose();
        }
    }
}
