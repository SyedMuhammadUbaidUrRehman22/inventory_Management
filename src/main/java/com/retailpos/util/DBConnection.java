package com.retailpos.util;

import com.retailpos.dao.DataAccessException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Opens JDBC connections using the settings in {@code db.properties} on the classpath
 * ({@code src/main/resources/db.properties}). Copy {@code db.properties.example} to create it.
 * <p>
 * Supports both standalone single-query connections and thread-scoped atomic transactions
 * via {@link #inTransaction(TransactionCallback)}.
 */
public final class DBConnection {

    private static final String PROPERTIES_FILE = "db.properties";
    private static final String KEY_URL = "db.url";
    private static final String KEY_USER = "db.user";
    private static final String KEY_PASSWORD = "db.password";

    private static final ThreadLocal<Connection> TRANSACTION_CONN = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> ROLLBACK_ONLY = new ThreadLocal<>();

    private DBConnection() {
    }

    /** Callback executed inside an atomic database transaction. */
    @FunctionalInterface
    public interface TransactionCallback<T> {
        T doInTransaction() throws Exception;
    }

    /** Runnable executed inside an atomic database transaction. */
    @FunctionalInterface
    public interface TransactionRunnable {
        void runInTransaction() throws Exception;
    }

    /**
     * Executes the given action inside an atomic database transaction.
     * All DAO calls within the callback share the same physical connection.
     * Commits on success, rolls back on any exception.
     */
    public static <T> T inTransaction(TransactionCallback<T> action) {
        if (!hasDatabaseConfig()) {
            // Running in unit test environment without db.properties (in-memory fake DAOs)
            try {
                return action.doInTransaction();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new DataAccessException("Transaction failed: " + e.getMessage(), e);
            }
        }

        Connection existing = TRANSACTION_CONN.get();
        if (existing != null) {
            try {
                return action.doInTransaction();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new DataAccessException("Transaction failed: " + e.getMessage(), e);
            }
        }

        try (Connection real = createRawConnection()) {
            real.setAutoCommit(false);
            TRANSACTION_CONN.set(real);
            ROLLBACK_ONLY.remove();
            try {
                T result = action.doInTransaction();
                if (isRollbackOnly()) {
                    throw new DataAccessException("Transaction rolled back: a database step inside it failed.", null);
                }
                real.commit();
                return result;
            } catch (Throwable t) {
                try {
                    real.rollback();
                } catch (SQLException rbEx) {
                    t.addSuppressed(rbEx);
                }
                if (t instanceof RuntimeException re) {
                    throw re;
                }
                throw new DataAccessException("Transaction failed: " + t.getMessage(), t);
            } finally {
                TRANSACTION_CONN.remove();
                ROLLBACK_ONLY.remove();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    /** Executes a runnable inside a transaction. */
    public static void inTransaction(TransactionRunnable runnable) {
        inTransaction(() -> {
            runnable.runInTransaction();
            return null;
        });
    }

    /** Returns a connection: either a non-closing proxy to the active transaction connection, or a new connection. */
    public static Connection getConnection() throws SQLException {
        Connection tx = TRANSACTION_CONN.get();
        if (tx != null) {
            return wrapTransactionConnection(tx);
        }
        return createRawConnection();
    }

    private static Connection createRawConnection() throws SQLException {
        Properties props = loadProperties();
        return DriverManager.getConnection(
                require(props, KEY_URL),
                require(props, KEY_USER),
                props.getProperty(KEY_PASSWORD, ""));
    }

    /**
     * Wraps the shared transaction connection so DAO code cannot end the transaction early:
     * <ul>
     *   <li>{@code close()} is ignored (try-with-resources in DAOs);</li>
     *   <li>{@code commit()} and {@code setAutoCommit(..)} are ignored, because only
     *       {@link #inTransaction} may commit;</li>
     *   <li>{@code rollback()} marks the transaction rollback-only, so {@link #inTransaction}
     *       rolls back instead of committing even if the DAO's exception is caught.</li>
     * </ul>
     * Package-private for tests.
     */
    static Connection wrapTransactionConnection(Connection real) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    int argCount = args == null ? 0 : args.length;
                    if ("close".equals(name)) {
                        return null; // do not close shared transaction connection
                    }
                    if ("commit".equals(name) && argCount == 0) {
                        return null; // only inTransaction commits
                    }
                    if ("setAutoCommit".equals(name)) {
                        return null; // auto-commit stays off until inTransaction ends
                    }
                    if ("getAutoCommit".equals(name)) {
                        return false;
                    }
                    if ("rollback".equals(name) && argCount == 0) {
                        ROLLBACK_ONLY.set(Boolean.TRUE);
                        return null; // inTransaction performs the real rollback
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** True if DAO code inside the current transaction asked for a rollback. */
    static boolean isRollbackOnly() {
        return Boolean.TRUE.equals(ROLLBACK_ONLY.get());
    }

    /** Returns true if {@code db.properties} exists on the classpath. */
    public static boolean hasDatabaseConfig() {
        return DBConnection.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE) != null;
    }

    private static Properties loadProperties() {
        try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (in == null) {
                throw new IllegalStateException(PROPERTIES_FILE + " not found on the classpath. "
                        + "Copy src/main/resources/db.properties.example to db.properties and fill it in.");
            }
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + PROPERTIES_FILE, e);
        }
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing '" + key + "' in " + PROPERTIES_FILE);
        }
        return value.trim();
    }
}
