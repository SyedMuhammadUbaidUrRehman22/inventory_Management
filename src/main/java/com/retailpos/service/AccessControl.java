package com.retailpos.service;

import com.retailpos.dao.UserDao;
import com.retailpos.model.Cashier;
import com.retailpos.model.Manager;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.User;
import com.retailpos.util.BusinessException;
import com.retailpos.util.PasswordUtil;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Login and role-based permissions (FR28–FR30).
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
    // Storekeeper
    public static final String RECORD_STOCK_RECEIPT = "recordStockReceipt";
    public static final String RECORD_STOCK_ADJUSTMENT = "recordStockAdjustment";
    // Cashier
    public static final String CREATE_SALE = "createSale";
    public static final String RECORD_RETURN = "recordReturn";
    // Every role
    public static final String SEARCH_PRODUCTS = "searchProducts";

    private static final Map<Class<? extends User>, Set<String>> PERMISSIONS = Map.of(
            Manager.class, Set.of(REGISTER_PRODUCT, UPDATE_PRODUCT, ADD_CATEGORY,
                    REGISTER_SUPPLIER, UPDATE_SUPPLIER, VIEW_SUPPLIERS, SET_SUPPLIER_PRICE, REMOVE_SUPPLIER_PRICE,
                    MANAGE_PURCHASE_ORDERS, GENERATE_REPORTS, SEARCH_PRODUCTS),
            Cashier.class, Set.of(CREATE_SALE, RECORD_RETURN, SEARCH_PRODUCTS),
            Storekeeper.class, Set.of(RECORD_STOCK_RECEIPT, RECORD_STOCK_ADJUSTMENT, SEARCH_PRODUCTS));

    private final UserDao userDao;

    public AccessControl(UserDao userDao) {
        this.userDao = userDao;
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
        return user.get();
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
