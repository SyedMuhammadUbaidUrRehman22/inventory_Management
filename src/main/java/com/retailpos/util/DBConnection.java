package com.retailpos.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Opens JDBC connections using the settings in {@code db.properties} on the classpath
 * ({@code src/main/resources/db.properties}). Copy {@code db.properties.example} to create it.
 * <p>
 * Every call returns a NEW connection; the caller must close it (try-with-resources).
 */
public final class DBConnection {

    private static final String PROPERTIES_FILE = "db.properties";
    private static final String KEY_URL = "db.url";
    private static final String KEY_USER = "db.user";
    private static final String KEY_PASSWORD = "db.password";

    private DBConnection() {
    }

    /** Returns a new connection to the database configured in {@code db.properties}. */
    public static Connection getConnection() throws SQLException {
        Properties props = loadProperties();
        return DriverManager.getConnection(
                require(props, KEY_URL),
                require(props, KEY_USER),
                props.getProperty(KEY_PASSWORD, ""));
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
