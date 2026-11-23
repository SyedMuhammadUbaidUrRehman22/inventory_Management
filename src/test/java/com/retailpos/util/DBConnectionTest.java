package com.retailpos.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that DAO code using the shared transaction connection cannot commit, close or
 * switch auto-commit on it, and that a DAO rollback marks the transaction rollback-only.
 */
class DBConnectionTest {

    private final List<String> calls = new ArrayList<>();

    /** A fake Connection that only records which methods reached it. */
    private Connection recordingConnection() {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    return method.getReturnType() == boolean.class ? Boolean.TRUE : null;
                });
    }

    @AfterEach
    void clearRollbackFlag() throws Exception {
        var field = DBConnection.class.getDeclaredField("ROLLBACK_ONLY");
        field.setAccessible(true);
        ((ThreadLocal<?>) field.get(null)).remove();
    }

    @Test
    void daoCannotCommitCloseOrChangeAutoCommit() throws Exception {
        Connection shared = DBConnection.wrapTransactionConnection(recordingConnection());

        shared.setAutoCommit(false);
        shared.commit();
        shared.setAutoCommit(true);
        shared.close();

        assertTrue(calls.isEmpty(), "nothing should reach the real connection, got " + calls);
        assertFalse(shared.getAutoCommit());
        assertFalse(DBConnection.isRollbackOnly());
    }

    @Test
    void daoRollbackMarksTransactionRollbackOnly() throws Exception {
        Connection shared = DBConnection.wrapTransactionConnection(recordingConnection());

        shared.rollback();

        assertTrue(calls.isEmpty(), "rollback should be left to inTransaction, got " + calls);
        assertTrue(DBConnection.isRollbackOnly());
    }

    @Test
    void otherCallsPassThrough() throws Exception {
        Connection shared = DBConnection.wrapTransactionConnection(recordingConnection());

        shared.prepareStatement("SELECT 1");

        assertEquals(List.of("prepareStatement"), calls);
    }
}
