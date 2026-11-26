package com.retailpos.dao;

import com.retailpos.model.Cashier;
import com.retailpos.model.Manager;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link UserDao}. The {@code role} column selects the User subclass. */
public class UserDaoJdbc extends JdbcDao implements UserDao {

    private static final String SELECT =
            "SELECT user_id, username, password_hash, role, created_by, active FROM users";

    @Override
    public Optional<User> findByUsername(String username) {
        return queryOne(SELECT + " WHERE username = ?", ps -> ps.setString(1, username), UserDaoJdbc::map);
    }

    @Override
    public Optional<User> findById(int userID) {
        return queryOne(SELECT + " WHERE user_id = ?", ps -> ps.setInt(1, userID), UserDaoJdbc::map);
    }

    @Override
    public void insert(User user) {
        int id = insertReturningKey(
                "INSERT INTO users (username, password_hash, role, created_by, active) VALUES (?, ?, ?, ?, ?)",
                ps -> {
                    ps.setString(1, user.getUsername());
                    ps.setString(2, user.getPasswordHash());
                    ps.setString(3, roleOf(user));
                    if (user.getCreatedByID() == null) {
                        ps.setNull(4, Types.INTEGER);
                    } else {
                        ps.setInt(4, user.getCreatedByID());
                    }
                    ps.setBoolean(5, user.isActive());
                });
        user.setUserID(id);
    }

    @Override
    public List<User> findByCreator(int managerID) {
        return queryList(SELECT + " WHERE created_by = ? ORDER BY username",
                ps -> ps.setInt(1, managerID), UserDaoJdbc::map);
    }

    @Override
    public boolean setActive(int userID, boolean active) {
        return update("UPDATE users SET active = ? WHERE user_id = ?", ps -> {
            ps.setBoolean(1, active);
            ps.setInt(2, userID);
        }) > 0;
    }

    @Override
    public boolean updatePasswordHash(int userID, String passwordHash) {
        return update("UPDATE users SET password_hash = ? WHERE user_id = ?", ps -> {
            ps.setString(1, passwordHash);
            ps.setInt(2, userID);
        }) > 0;
    }

    private static String roleOf(User user) {
        if (user instanceof Manager) {
            return "MANAGER";
        }
        if (user instanceof Cashier) {
            return "CASHIER";
        }
        if (user instanceof Storekeeper) {
            return "STOREKEEPER";
        }
        throw new IllegalArgumentException("Unknown user type " + user.getClass().getName());
    }

    /** Maps a row with the users columns; also reused by {@link StockAdjustmentDaoJdbc}. */
    static User map(ResultSet rs) throws SQLException {
        int id = rs.getInt("user_id");
        String username = rs.getString("username");
        String hash = rs.getString("password_hash");
        String role = rs.getString("role");
        int createdBy = rs.getInt("created_by");
        Integer createdByID = rs.wasNull() ? null : createdBy;
        boolean active = rs.getBoolean("active");
        switch (role) {
            case "MANAGER":
                return new Manager(id, username, hash, createdByID, active);
            case "CASHIER":
                return new Cashier(id, username, hash, createdByID, active);
            case "STOREKEEPER":
                return new Storekeeper(id, username, hash, createdByID, active);
            default:
                throw new SQLException("Unknown role '" + role + "' for user " + username);
        }
    }
}
