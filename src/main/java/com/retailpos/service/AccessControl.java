package com.retailpos.service;

import com.retailpos.dao.UserDao;
import com.retailpos.model.Cashier;
import com.retailpos.model.Manager;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.User;
import com.retailpos.util.BusinessException;
import com.retailpos.util.PasswordUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Login and role-based permissions (FR28–FR30), plus user accounts:
 * Managers sign up with a setup code, and each Manager creates and manages
 * the Cashier and Storekeeper accounts that belong to them.
 * <p>
 * Every restricted service method starts with {@link #requirePermission(User, String)}
 * using one of the function names below.
 */
public class AccessControl {

    // ---- Function names (CLAUDE.md "Role permissions") ----
    // Manager: products, categories, suppliers and supplier prices
    public static final String REGISTER_PRODUCT = "registerProduct";
    public static final String UPDATE_PRODUCT = "updateProduct";
    public static final String ADD_CATEGORY = "addCategory";
    public static final String REGISTER_SUPPLIER = "registerSupplier";
    public static final String UPDATE_SUPPLIER = "updateSupplier";
    public static final String VIEW_SUPPLIERS = "viewSuppliers";
    public static final String SET_SUPPLIER_PRICE = "setSupplierPrice";
    public static final String REMOVE_SUPPLIER_PRICE = "removeSupplierPrice";
    // Manager: purchase orders and reports
    public static final String MANAGE_PURCHASE_ORDERS = "managePurchaseOrders";
    public static final String GENERATE_REPORTS = "generateReports";
    // Manager: create, deactivate and reactivate their own Cashier/Storekeeper accounts
    public static final String MANAGE_STAFF = "manageStaff";
    // Storekeeper
    public static final String RECORD_STOCK_RECEIPT = "recordStockReceipt";
    public static final String RECORD_STOCK_ADJUSTMENT = "recordStockAdjustment";
    // Cashier
    public static final String CREATE_SALE = "createSale";
    public static final String RECORD_RETURN = "recordReturn";
    // Every role
    public static final String SEARCH_PRODUCTS = "searchProducts";
    /** Change the signed-in user's own password (never someone else's). */
    public static final String CHANGE_OWN_PASSWORD = "changeOwnPassword";

    private static final Map<Class<? extends User>, Set<String>> PERMISSIONS = Map.of(
            Manager.class, Set.of(REGISTER_PRODUCT, UPDATE_PRODUCT, ADD_CATEGORY,
                    REGISTER_SUPPLIER, UPDATE_SUPPLIER, VIEW_SUPPLIERS, SET_SUPPLIER_PRICE, REMOVE_SUPPLIER_PRICE,
                    MANAGE_PURCHASE_ORDERS, GENERATE_REPORTS, MANAGE_STAFF, SEARCH_PRODUCTS, CHANGE_OWN_PASSWORD),
            Cashier.class, Set.of(CREATE_SALE, RECORD_RETURN, SEARCH_PRODUCTS, CHANGE_OWN_PASSWORD),
            Storekeeper.class, Set.of(RECORD_STOCK_RECEIPT, RECORD_STOCK_ADJUSTMENT, SEARCH_PRODUCTS,
                    CHANGE_OWN_PASSWORD));

    /** Roles a Manager may give to the accounts they create. A Manager cannot create another Manager. */
    public static final List<String> STAFF_ROLES = List.of("Cashier", "Storekeeper");

    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 50;
    public static final int PASSWORD_MIN = 8;
    /** BCrypt only uses the first 72 bytes, so longer passwords are refused rather than silently cut. */
    public static final int PASSWORD_MAX = 64;
    public static final String PASSWORD_RULES =
            "at least " + PASSWORD_MIN + " characters, with at least one letter and one number";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9._-]+");

    private final UserDao userDao;
    private final String managerSignUpCode;

    /** Manager sign-up is turned off. */
    public AccessControl(UserDao userDao) {
        this(userDao, null);
    }

    /**
     * @param managerSignUpCode the code a person must enter to sign up as a Manager;
     *                          null or blank turns Manager sign-up off
     */
    public AccessControl(UserDao userDao, String managerSignUpCode) {
        this.userDao = userDao;
        this.managerSignUpCode = managerSignUpCode == null || managerSignUpCode.isBlank()
                ? null : managerSignUpCode.trim();
    }

    /**
     * FR29: Logs a user in with username and password.
     * The same message is used for an unknown user and a wrong password,
     * so the login screen does not reveal which usernames exist.
     *
     * @return the user as a Manager, Cashier or Storekeeper (FR28)
     */
    public User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new BusinessException("Enter your username and password.");
        }
        Optional<User> user = userDao.findByUsername(username.trim());
        if (user.isEmpty() || !PasswordUtil.verify(password, user.get().getPasswordHash())) {
            throw new BusinessException("Invalid username or password.");
        }
        if (!user.get().isActive()) {
            throw new BusinessException("This account has been deactivated. Ask your manager to reactivate it.");
        }
        return user.get();
    }

    // =====================================================================
    // Manager sign-up
    // =====================================================================

    /** True if a setup code is configured, so the login screen should offer Manager sign-up. */
    public boolean isManagerSignUpEnabled() {
        return managerSignUpCode != null;
    }

    /**
     * Creates a new Manager account. Only someone who knows the store's setup code can do this,
     * so nobody at the till can make themselves a Manager. The code is checked first, so without it
     * the form does not reveal which usernames exist.
     */
    public Manager signUpManager(String username, String password, String confirmPassword, String setupCode) {
        if (!isManagerSignUpEnabled()) {
            throw new BusinessException("Manager sign-up is turned off. Ask the store owner.");
        }
        if (setupCode == null || setupCode.isBlank()) {
            throw new BusinessException("Enter the setup code.");
        }
        if (!MessageDigest.isEqual(setupCode.trim().getBytes(StandardCharsets.UTF_8),
                managerSignUpCode.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("The setup code is not correct.");
        }
        String name = validateNewAccount(username, password, confirmPassword);
        Manager manager = new Manager(0, name, PasswordUtil.hash(password), null, true);
        userDao.insert(manager);
        return manager;
    }

    // =====================================================================
    // Staff accounts (Manager only; each Manager manages only their own staff)
    // =====================================================================

    /**
     * Creates a Cashier or Storekeeper account that belongs to the signed-in Manager.
     * The new account gets only its role's permissions.
     *
     * @param role "Cashier" or "Storekeeper" (see {@link #STAFF_ROLES})
     */
    public User createStaffAccount(User manager, String username, String password, String confirmPassword,
                                   String role) {
        requirePermission(manager, MANAGE_STAFF);
        if (role == null || !STAFF_ROLES.contains(role)) {
            throw new BusinessException("Choose a role: a Manager can create Cashier and Storekeeper accounts only.");
        }
        String name = validateNewAccount(username, password, confirmPassword);
        String hash = PasswordUtil.hash(password);
        User staff = role.equals("Cashier")
                ? new Cashier(0, name, hash, manager.getUserID(), true)
                : new Storekeeper(0, name, hash, manager.getUserID(), true);
        userDao.insert(staff);
        return staff;
    }

    /** Returns the Cashier and Storekeeper accounts created by the signed-in Manager. */
    public List<User> listMyStaff(User manager) {
        requirePermission(manager, MANAGE_STAFF);
        return userDao.findByCreator(manager.getUserID());
    }

    /**
     * Deactivates (or reactivates) one of the signed-in Manager's own staff accounts.
     * A deactivated account cannot sign in; its past sales and stock movements are kept.
     */
    public User setStaffActive(User manager, int staffUserID, boolean active) {
        requirePermission(manager, MANAGE_STAFF);
        Optional<User> staff = userDao.findById(staffUserID);
        if (staff.isEmpty() || staff.get().getCreatedByID() == null
                || staff.get().getCreatedByID() != manager.getUserID()) {
            throw new BusinessException("You can only change the staff accounts you created.");
        }
        userDao.setActive(staffUserID, active);
        staff.get().setActive(active);
        return staff.get();
    }

    // =====================================================================
    // Change own password (every role)
    // =====================================================================

    /**
     * Changes the signed-in user's own password. The current password must be entered,
     * so someone using an unattended, signed-in till cannot take over the account.
     */
    public void changePassword(User user, String currentPassword, String newPassword, String confirmPassword) {
        requirePermission(user, CHANGE_OWN_PASSWORD);
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new BusinessException("Enter your current password.");
        }
        // Re-read the account: the stored hash and the active flag may have changed since sign-in.
        User stored = userDao.findById(user.getUserID())
                .orElseThrow(() -> new BusinessException("Your account no longer exists. Please sign in again."));
        if (!stored.isActive()) {
            throw new BusinessException("This account has been deactivated. Ask your manager to reactivate it.");
        }
        if (!PasswordUtil.verify(currentPassword, stored.getPasswordHash())) {
            throw new BusinessException("Your current password is not correct.");
        }
        validatePassword(newPassword, confirmPassword);
        if (newPassword.equals(currentPassword)) {
            throw new BusinessException("The new password must be different from the current one.");
        }
        String hash = PasswordUtil.hash(newPassword);
        userDao.updatePasswordHash(user.getUserID(), hash);
        user.setPasswordHash(hash);
    }

    /** Checks a new account's username and password; returns the trimmed username. */
    private String validateNewAccount(String username, String password, String confirmPassword) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            throw new BusinessException("Enter a username.");
        }
        if (name.length() < USERNAME_MIN || name.length() > USERNAME_MAX) {
            throw new BusinessException("Username must be " + USERNAME_MIN + " to " + USERNAME_MAX + " characters.");
        }
        if (!USERNAME_PATTERN.matcher(name).matches()) {
            throw new BusinessException("Username can only contain letters, numbers, dots, dashes and underscores.");
        }
        validatePassword(password, confirmPassword);
        if (userDao.findByUsername(name).isPresent()) {
            throw new BusinessException("The username '" + name + "' is already taken.");
        }
        return name;
    }

    /** Password rules shared by sign-up, new staff accounts and password changes. */
    private static void validatePassword(String password, String confirmPassword) {
        if (password == null || password.length() < PASSWORD_MIN
                || password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw new BusinessException("Password must have " + PASSWORD_RULES + ".");
        }
        if (password.length() > PASSWORD_MAX) {
            throw new BusinessException("Password must be at most " + PASSWORD_MAX + " characters.");
        }
        if (!password.equals(confirmPassword)) {
            throw new BusinessException("The two passwords do not match.");
        }
    }

    /** FR30: Returns true if the user's role may perform the given function. */
    public boolean hasPermission(User user, String functionName) {
        if (user == null) {
            return false;
        }
        Set<String> allowed = PERMISSIONS.get(user.getClass());
        return allowed != null && allowed.contains(functionName);
    }

    /** FR30: Throws a BusinessException unless the user's role may perform the given function. */
    public void requirePermission(User user, String functionName) {
        if (user == null) {
            throw new BusinessException("Please sign in first.");
        }
        if (!hasPermission(user, functionName)) {
            throw new BusinessException("Access denied: a " + roleName(user)
                    + " is not allowed to do this (" + functionName + ").");
        }
    }

    /** FR28: The role name shown in the UI ("Manager", "Cashier" or "Storekeeper"). */
    public static String roleName(User user) {
        return user.getClass().getSimpleName();
    }
}
