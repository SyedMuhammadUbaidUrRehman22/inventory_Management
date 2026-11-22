package com.retailpos.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Optional application settings, read from {@code db.properties} on the classpath
 * (the same git-ignored file that holds the database credentials).
 */
public final class AppSettings {

    /** Code a person must enter to sign up as a Manager. Not set or blank: Manager sign-up is turned off. */
    public static final String MANAGER_SIGNUP_CODE = "app.manager_signup_code";

    private static final String PROPERTIES_FILE = "db.properties";

    private AppSettings() {
    }

    /** Returns the trimmed value of the setting, or null if it is missing, blank, or the file is absent. */
    public static String get(String key) {
        try (InputStream in = AppSettings.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (in == null) {
                return null;
            }
            Properties props = new Properties();
            props.load(in);
            String value = props.getProperty(key);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + PROPERTIES_FILE, e);
        }
    }
}
