package com.retailpos.dao;

import com.retailpos.model.User;

import java.util.List;
import java.util.Optional;

/** Data access for users (all roles). */
public interface UserDao {

    /** Returns the user with this username, as a Manager, Cashier or Storekeeper. */
    Optional<User> findByUsername(String username);

    Optional<User> findById(int userID);

    /** Saves a new user (role taken from its class) and sets the generated userID on it. */
    void insert(User user);

    /** Returns the accounts created by this Manager, ordered by username. */
    List<User> findByCreator(int managerID);

    /** Activates or deactivates an account; returns false if no such user exists. */
    boolean setActive(int userID, boolean active);

    /** Replaces the stored password hash; returns false if no such user exists. */
    boolean updatePasswordHash(int userID, String passwordHash);
}
