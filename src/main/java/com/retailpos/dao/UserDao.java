package com.retailpos.dao;

import com.retailpos.model.User;

import java.util.Optional;

/** Data access for users (all roles). */
public interface UserDao {

    /** Returns the user with this username, as a Manager, Cashier or Storekeeper. */
    Optional<User> findByUsername(String username);

    Optional<User> findById(int userID);
}
