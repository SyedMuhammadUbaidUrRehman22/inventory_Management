package com.retailpos.dao;

import com.retailpos.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared JDBC plumbing for the DAO implementations: every query goes through a
 * PreparedStatement and every resource is closed with try-with-resources.
 * <p>
 * All connections are obtained in {@link #connection()}, so transaction support
 * (Phase 2) can be added in one place without changing the DAO interfaces.
 */
abstract class JdbcDao {

    /** Binds the parameters of a PreparedStatement. */
    @FunctionalInterface
    interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    /** Maps the current row of a ResultSet to an object. */
    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    protected static final Binder NO_PARAMS = ps -> { };

    protected Connection connection() throws SQLException {
        return DBConnection.getConnection();
    }

    protected <T> List<T> queryList(String sql, Binder binder, RowMapper<T> mapper) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    protected <T> Optional<T> queryOne(String sql, Binder binder, RowMapper<T> mapper) {
        List<T> rows = queryList(sql, binder, mapper);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Executes an INSERT/UPDATE/DELETE and returns the number of affected rows. */
    protected int update(String sql, Binder binder) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    /** Executes an INSERT into a table with an AUTO_INCREMENT key and returns the new key. */
    protected int insertReturningKey(String sql, Binder binder) {
        try (Connection c = connection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            binder.bind(ps);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned");
                }
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    /** Turns user input into a LIKE pattern, escaping the wildcards % and _ so they match literally. */
    protected static String containsPattern(String text) {
        String escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
