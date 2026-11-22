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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manager sign-up and Manager-owned staff accounts. */
class UserAccountsTest {

    private static final String CODE = "store-setup-42";
    private static final String GOOD_PASSWORD = "secret123";

    private FakeUserDao users;
    private AccessControl accessControl;
    private Manager alice;   // owns cashierOfAlice
    private Manager bob;
    private Cashier cashierOfAlice;

    @BeforeEach
    void setUp() {
        String hash = PasswordUtil.hash(GOOD_PASSWORD);
        alice = new Manager(1, "alice", hash, null, true);
        bob = new Manager(2, "bob", hash, null, true);
        cashierOfAlice = new Cashier(3, "carol", hash, 1, true);
        users = new FakeUserDao().add(alice).add(bob).add(cashierOfAlice);
        accessControl = new AccessControl(users, CODE);
    }

    // ---- Manager sign-up ----

    @Test
    void signUpIsOffWithoutASetupCode() {
        AccessControl noCode = new AccessControl(users, "  ");
        assertFalse(noCode.isManagerSignUpEnabled());
        assertThrows(BusinessException.class,
                () -> noCode.signUpManager("dave", GOOD_PASSWORD, GOOD_PASSWORD, ""));
        assertTrue(accessControl.isManagerSignUpEnabled());
    }

    @Test
    void signUpCreatesAnActiveManagerThatCanSignIn() {
        Manager dave = accessControl.signUpManager("  dave ", GOOD_PASSWORD, GOOD_PASSWORD, " " + CODE + " ");

        assertEquals("dave", dave.getUsername());
        assertNull(dave.getCreatedByID());
        assertTrue(dave.isActive());
        assertNotEquals(GOOD_PASSWORD, dave.getPasswordHash());
        assertInstanceOf(Manager.class, accessControl.login("dave", GOOD_PASSWORD));
    }

    @Test
    void signUpRejectsAWrongOrMissingCodeAndCreatesNothing() {
        BusinessException wrong = assertThrows(BusinessException.class,
                () -> accessControl.signUpManager("dave", GOOD_PASSWORD, GOOD_PASSWORD, "guess"));
        assertEquals("The setup code is not correct.", wrong.getMessage());
        assertThrows(BusinessException.class,
                () -> accessControl.signUpManager("dave", GOOD_PASSWORD, GOOD_PASSWORD, null));
        assertTrue(users.findByUsername("dave").isEmpty());
    }

    @Test
    void wrongCodeIsReportedBeforeUsernameIsChecked() {
        // Someone without the code must not learn that "alice" exists.
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.signUpManager("alice", GOOD_PASSWORD, GOOD_PASSWORD, "guess"));
        assertEquals("The setup code is not correct.", e.getMessage());
    }

    @Test
    void signUpRejectsATakenUsernameIgnoringCase() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.signUpManager("ALICE", GOOD_PASSWORD, GOOD_PASSWORD, CODE));
        assertTrue(e.getMessage().contains("already taken"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "onlyletters", "12345678", "a1234567890123456789012345678901234567890123456789012345678901234"})
    void weakOrOverlongPasswordsAreRejected(String password) {
        assertThrows(BusinessException.class,
                () -> accessControl.signUpManager("dave", password, password, CODE));
    }

    @Test
    void passwordsMustMatch() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.signUpManager("dave", GOOD_PASSWORD, "secret124", CODE));
        assertEquals("The two passwords do not match.", e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "has space", "semi;colon", "émile"})
    void invalidUsernamesAreRejected(String username) {
        assertThrows(BusinessException.class,
                () -> accessControl.signUpManager(username, GOOD_PASSWORD, GOOD_PASSWORD, CODE));
    }

    // ---- Staff accounts ----

    @Test
    void managerCreatesCashierAndStorekeeperOwnedByThem() {
        User cashier = accessControl.createStaffAccount(bob, "erin", GOOD_PASSWORD, GOOD_PASSWORD, "Cashier");
        User storekeeper = accessControl.createStaffAccount(bob, "frank", GOOD_PASSWORD, GOOD_PASSWORD, "Storekeeper");

        assertInstanceOf(Cashier.class, cashier);
        assertInstanceOf(Storekeeper.class, storekeeper);
        assertEquals(bob.getUserID(), cashier.getCreatedByID());
        assertEquals(bob.getUserID(), storekeeper.getCreatedByID());
        assertInstanceOf(Cashier.class, accessControl.login("erin", GOOD_PASSWORD));
    }

    @Test
    void newStaffGetOnlyTheirRolesPermissions() {
        User cashier = accessControl.createStaffAccount(bob, "erin", GOOD_PASSWORD, GOOD_PASSWORD, "Cashier");
        assertTrue(accessControl.hasPermission(cashier, AccessControl.CREATE_SALE));
        assertFalse(accessControl.hasPermission(cashier, AccessControl.MANAGE_STAFF));
        assertFalse(accessControl.hasPermission(cashier, AccessControl.REGISTER_PRODUCT));
        assertFalse(accessControl.hasPermission(cashier, AccessControl.RECORD_STOCK_RECEIPT));
    }

    @Test
    void managerCannotCreateAnotherManager() {
        assertThrows(BusinessException.class,
                () -> accessControl.createStaffAccount(bob, "gina", GOOD_PASSWORD, GOOD_PASSWORD, "Manager"));
        assertThrows(BusinessException.class,
                () -> accessControl.createStaffAccount(bob, "gina", GOOD_PASSWORD, GOOD_PASSWORD, null));
        assertTrue(users.findByUsername("gina").isEmpty());
    }

    @Test
    void staffCannotCreateAccounts() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.createStaffAccount(cashierOfAlice, "gina", GOOD_PASSWORD, GOOD_PASSWORD, "Cashier"));
        assertTrue(e.getMessage().startsWith("Access denied"));
        assertThrows(BusinessException.class, () -> accessControl.listMyStaff(cashierOfAlice));
        assertThrows(BusinessException.class, () -> accessControl.createStaffAccount(null, "gina",
                GOOD_PASSWORD, GOOD_PASSWORD, "Cashier"));
    }

    @Test
    void staffAccountsUseTheSameRules() {
        assertThrows(BusinessException.class,
                () -> accessControl.createStaffAccount(bob, "carol", GOOD_PASSWORD, GOOD_PASSWORD, "Cashier"));
        assertThrows(BusinessException.class,
                () -> accessControl.createStaffAccount(bob, "gina", "weak", "weak", "Cashier"));
    }

    @Test
    void eachManagerSeesOnlyTheirOwnStaff() {
        accessControl.createStaffAccount(bob, "erin", GOOD_PASSWORD, GOOD_PASSWORD, "Cashier");

        List<User> alicesStaff = accessControl.listMyStaff(alice);
        List<User> bobsStaff = accessControl.listMyStaff(bob);
        assertEquals(List.of("carol"), alicesStaff.stream().map(User::getUsername).toList());
        assertEquals(List.of("erin"), bobsStaff.stream().map(User::getUsername).toList());
    }

    @Test
    void deactivatedStaffCannotSignInUntilReactivated() {
        accessControl.setStaffActive(alice, cashierOfAlice.getUserID(), false);
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.login("carol", GOOD_PASSWORD));
        assertTrue(e.getMessage().contains("deactivated"));

        accessControl.setStaffActive(alice, cashierOfAlice.getUserID(), true);
        assertEquals("carol", accessControl.login("carol", GOOD_PASSWORD).getUsername());
    }

    @Test
    void deactivatedMessageIsOnlyShownAfterTheRightPassword() {
        accessControl.setStaffActive(alice, cashierOfAlice.getUserID(), false);
        BusinessException e = assertThrows(BusinessException.class,
                () -> accessControl.login("carol", "wrongpass1"));
        assertEquals("Invalid username or password.", e.getMessage());
    }

    @Test
    void managerCannotChangeAnotherManagersStaffOrAManager() {
        assertThrows(BusinessException.class,
                () -> accessControl.setStaffActive(bob, cashierOfAlice.getUserID(), false));
        assertThrows(BusinessException.class, () -> accessControl.setStaffActive(bob, alice.getUserID(), false));
        assertThrows(BusinessException.class, () -> accessControl.setStaffActive(bob, bob.getUserID(), false));
        assertThrows(BusinessException.class, () -> accessControl.setStaffActive(bob, 999, false));
        assertTrue(cashierOfAlice.isActive());
        assertTrue(alice.isActive());
    }
}
