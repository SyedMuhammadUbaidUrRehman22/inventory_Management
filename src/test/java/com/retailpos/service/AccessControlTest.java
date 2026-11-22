package com.retailpos.service;

import com.retailpos.model.Cashier;
import com.retailpos.model.Manager;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.User;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import com.retailpos.util.PasswordUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessControlTest {

    private static AccessControl accessControl;
    private static final Manager MANAGER = new Manager(1, "manager", PasswordUtil.hash("1234"));
    private static final Cashier CASHIER = new Cashier(2, "cashier", PasswordUtil.hash("1234"));
    private static final Storekeeper STOREKEEPER = new Storekeeper(3, "storekeeper", PasswordUtil.hash("1234"));

    @BeforeAll
    static void setUp() {
        accessControl = new AccessControl(new FakeUserDao().add(MANAGER).add(CASHIER).add(STOREKEEPER));
    }

    // ---- FR29: login ----

    @Test
    void loginReturnsUserWithTheirRole() {
        assertInstanceOf(Manager.class, accessControl.login("manager", "1234"));
        assertInstanceOf(Cashier.class, accessControl.login("cashier", "1234"));
        assertInstanceOf(Storekeeper.class, accessControl.login("storekeeper", "1234"));
    }

    @Test
    void loginTrimsUsername() {
        assertEquals("manager", accessControl.login("  manager ", "1234").getUsername());
    }

    @Test
    void loginRejectsWrongPassword() {
        BusinessException e = assertThrows(BusinessException.class, () -> accessControl.login("manager", "wrong"));
        assertEquals("Invalid username or password.", e.getMessage());
    }

    @Test
    void loginGivesSameMessageForUnknownUser() {
        BusinessException e = assertThrows(BusinessException.class, () -> accessControl.login("nobody", "1234"));
        assertEquals("Invalid username or password.", e.getMessage());
    }

    @Test
    void loginRequiresBothFields() {
        assertThrows(BusinessException.class, () -> accessControl.login("", "1234"));
        assertThrows(BusinessException.class, () -> accessControl.login("manager", ""));
        assertThrows(BusinessException.class, () -> accessControl.login(null, null));
    }

    // ---- FR28 / FR30: role permissions (CLAUDE.md table) ----

    @ParameterizedTest(name = "{0}: manager={1}, cashier={2}, storekeeper={3}")
    @CsvSource({
            "registerProduct,       true,  false, false",
            "updateProduct,         true,  false, false",
            "addCategory,           true,  false, false",
            "registerSupplier,      true,  false, false",
            "updateSupplier,        true,  false, false",
            "viewSuppliers,         true,  false, false",
            "setSupplierPrice,      true,  false, false",
            "removeSupplierPrice,   true,  false, false",
            "managePurchaseOrders,  true,  false, false",
            "generateReports,       true,  false, false",
            "manageStaff,           true,  false, false",
            "importProducts,        true,  false, true",
            "recordStockReceipt,    false, false, true",
            "recordStockAdjustment, false, false, true",
            "createSale,            false, true,  false",
            "recordReturn,          false, true,  false",
            "searchProducts,        true,  true,  true",
            "changeOwnPassword,     true,  true,  true",
    })
    void permissionsFollowRoleTable(String function, boolean manager, boolean cashier, boolean storekeeper) {
        assertEquals(manager, accessControl.hasPermission(MANAGER, function));
        assertEquals(cashier, accessControl.hasPermission(CASHIER, function));
        assertEquals(storekeeper, accessControl.hasPermission(STOREKEEPER, function));
    }

    @Test
    void requirePermissionAllowsPermittedFunction() {
        assertDoesNotThrow(() -> accessControl.requirePermission(MANAGER, AccessControl.REGISTER_PRODUCT));
    }

    @Test
    void requirePermissionRejectsOtherRoles() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.requirePermission(CASHIER, AccessControl.REGISTER_PRODUCT));
        assertTrue(e.getMessage().startsWith("Access denied"));
    }

    @Test
    void requirePermissionRejectsMissingUser() {
        assertThrows(BusinessException.class, () -> accessControl.requirePermission(null, AccessControl.SEARCH_PRODUCTS));
        assertFalse(accessControl.hasPermission(null, AccessControl.SEARCH_PRODUCTS));
    }

    @Test
    void unknownFunctionIsDenied() {
        assertFalse(accessControl.hasPermission(MANAGER, "deleteEverything"));
    }

    @Test
    void roleNameIsTheSubclassName() {
        User u = CASHIER;
        assertEquals("Cashier", AccessControl.roleName(u));
    }
}
