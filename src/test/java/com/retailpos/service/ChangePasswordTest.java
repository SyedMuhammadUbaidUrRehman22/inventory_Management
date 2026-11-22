package com.retailpos.service;

import com.retailpos.model.Cashier;
import com.retailpos.model.Manager;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.User;
import com.retailpos.testsupport.FakeUserDao;
import com.retailpos.util.BusinessException;
import com.retailpos.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every user can change their own password, and only with the current one. */
class ChangePasswordTest {

    private static final String OLD = "1234";
    private static final String NEW = "newSecret9";

    private FakeUserDao users;
    private AccessControl accessControl;
    private Manager manager;
    private Cashier cashier;
    private Storekeeper storekeeper;

    @BeforeEach
    void setUp() {
        manager = new Manager(1, "manager", PasswordUtil.hash(OLD), null, true);
        cashier = new Cashier(2, "cashier", PasswordUtil.hash(OLD), 1, true);
        storekeeper = new Storekeeper(3, "storekeeper", PasswordUtil.hash(OLD), 1, true);
        users = new FakeUserDao().add(manager).add(cashier).add(storekeeper);
        accessControl = new AccessControl(users);
    }

    @Test
    void newPasswordWorksAndOldOneStops() {
        User signedIn = accessControl.login("cashier", OLD);
        accessControl.changePassword(signedIn, OLD, NEW, NEW);

        assertEquals("cashier", accessControl.login("cashier", NEW).getUsername());
        BusinessException e = assertThrows(BusinessException.class, () -> accessControl.login("cashier", OLD));
        assertEquals("Invalid username or password.", e.getMessage());
    }

    @Test
    void everyRoleCanChangeTheirOwnPassword() {
        for (User u : new User[]{manager, cashier, storekeeper}) {
            accessControl.changePassword(u, OLD, NEW, NEW);
            assertTrue(PasswordUtil.verify(NEW, users.findById(u.getUserID()).orElseThrow().getPasswordHash()));
        }
    }

    @Test
    void wrongCurrentPasswordIsRejectedAndNothingChanges() {
        String before = cashier.getPasswordHash();
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.changePassword(cashier, "wrong", NEW, NEW));
        assertEquals("Your current password is not correct.", e.getMessage());
        assertEquals(before, users.findById(cashier.getUserID()).orElseThrow().getPasswordHash());
    }

    @Test
    void currentPasswordIsRequired() {
        assertThrows(BusinessException.class, () -> accessControl.changePassword(cashier, "", NEW, NEW));
        assertThrows(BusinessException.class, () -> accessControl.changePassword(cashier, null, NEW, NEW));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "onlyletters", "12345678"})
    void newPasswordMustFollowTheRules(String weak) {
        assertThrows(BusinessException.class, () -> accessControl.changePassword(cashier, OLD, weak, weak));
        assertTrue(PasswordUtil.verify(OLD, cashier.getPasswordHash()));
    }

    @Test
    void newPasswordsMustMatch() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.changePassword(cashier, OLD, NEW, NEW + "x"));
        assertEquals("The two passwords do not match.", e.getMessage());
    }

    @Test
    void newPasswordMustDifferFromCurrent() {
        accessControl.changePassword(cashier, OLD, NEW, NEW);
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.changePassword(cashier, NEW, NEW, NEW));
        assertTrue(e.getMessage().contains("different"));
    }

    @Test
    void deactivatedAccountCannotChangePassword() {
        User signedIn = accessControl.login("cashier", OLD);
        accessControl.setStaffActive(manager, cashier.getUserID(), false);
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.changePassword(signedIn, OLD, NEW, NEW));
        assertTrue(e.getMessage().contains("deactivated"));
    }

    @Test
    void notSignedInIsRejected() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.changePassword(null, OLD, NEW, NEW));
        assertEquals("Please sign in first.", e.getMessage());
    }
}
