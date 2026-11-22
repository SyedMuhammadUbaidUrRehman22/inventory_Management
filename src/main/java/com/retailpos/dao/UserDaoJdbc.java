package com.retailpos.dao;

import com.retailpos.model.Cashier;
import com.retailpos.model.Manager;
import com.retailpos.model.Storekeeper;
import com.retailpos.model.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** JDBC implementation of {@link UserDao}. The {@code role} column selects the User subclass. */
public class UserDaoJdbc extends JdbcDao implements UserDao {

    private static final String SELECT = "SELECT user_id, username, password_hash, role FROM users";

    @Override
    public Optional<User> findByUsername(String username) {
        return queryOne(SELECT + " WHERE username = ?", ps -> ps.setString(1, username), UserDaoJdbc::map);
    }

    @Override
    public Optional<User> findById(int userID) {
        return queryOne(SELECT + " WHERE user_id = ?", ps -> ps.setInt(1, userID), UserDaoJdbc::map);
    }

    private static User map(ResultSet rs) throws SQLException {
        int id = rs.getInt("user_id");
        String username = rs.getString("username");
        String hash = rs.getString("password_hash");
        String role = rs.getString("role");
        switch (role) {
            case "MANAGER":
                return new Manager(id, username, hash);
            case "CASHIER":
                return new Cashier(id, username, hash);
            case "STOREKEEPER":
                return new Storekeeper(id, username, hash);
            default:
                throw new SQLException("Unknown role '" + role + "' for user " + username);
        }
    }
}
