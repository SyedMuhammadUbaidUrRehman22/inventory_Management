package com.retailpos.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Utility to automatically initialize the MySQL database and run {@code database/schema.sql}.
 * Can be run directly via:
 * {@code ./mvnw compile exec:java -Dexec.mainClass="com.retailpos.util.DatabaseInit"}
 */
public final class DatabaseInit {

    private DatabaseInit() {
    }

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("Retail POS - Database Initializer");
        System.out.println("=================================================");
        try {
            initDatabase();
            System.out.println("\nSUCCESS: Database 'retail_pos' initialized and seeded!");
            System.out.println("You can now start the application with: ./mvnw compile exec:java");
        } catch (Exception e) {
            System.err.println("\nERROR: Failed to initialize MySQL database: " + e.getMessage());
            System.err.println("Make sure MySQL Server is running on port 3306.");
            System.exit(1);
        }
    }

    public static void initDatabase() throws Exception {
        // 1. Read db.properties
        Properties props = new Properties();
        try (InputStream in = DatabaseInit.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new IllegalStateException("src/main/resources/db.properties not found.");
            }
            props.load(in);
        }

        String url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/retail_pos");
        String user = props.getProperty("db.user", "root");
        String password = props.getProperty("db.password", "");

        // Connect to MySQL server root (without specifying database so we can create it if not exists)
        String serverUrl = url;
        if (serverUrl.contains("/retail_pos")) {
            serverUrl = serverUrl.replace("/retail_pos", "/");
        }

        System.out.println("Connecting to MySQL at " + serverUrl + " as user '" + user + "'...");

        // Find schema.sql file
        Path schemaPath = Path.of("database/schema.sql");
        if (!Files.exists(schemaPath)) {
            schemaPath = Path.of("../database/schema.sql");
        }
        if (!Files.exists(schemaPath)) {
            throw new IllegalStateException("database/schema.sql not found at " + schemaPath.toAbsolutePath());
        }

        List<String> statements = parseSqlStatements(schemaPath);
        System.out.println("Parsed " + statements.size() + " SQL statements from " + schemaPath);

        try (Connection conn = DriverManager.getConnection(serverUrl, user, password);
             Statement stmt = conn.createStatement()) {

            for (int i = 0; i < statements.size(); i++) {
                String sql = statements.get(i).trim();
                if (sql.isEmpty()) continue;
                try {
                    stmt.execute(sql);
                } catch (Exception ex) {
                    System.err.println("Warning on statement " + (i + 1) + ": " + ex.getMessage());
                }
            }
        }
    }

    private static List<String> parseSqlStatements(Path path) throws Exception {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                // Skip comment lines
                if (trimmed.startsWith("--") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                    continue;
                }
                current.append(line).append("\n");
                if (trimmed.endsWith(";")) {
                    statements.add(current.toString().trim());
                    current.setLength(0);
                }
            }
        }
        if (current.length() > 0 && !current.toString().trim().isEmpty()) {
            statements.add(current.toString().trim());
        }
        return statements;
    }
}
