package com.retailpos.testsupport;

import com.retailpos.dao.UserDao;
import com.retailpos.model.User;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link UserDao} for unit tests. */
public class FakeUserDao implements UserDao {

    private final Map<String, User> byUsername = new HashMap<>();

    public FakeUserDao add(User user) {
        byUsername.put(user.getUsername(), user);
        return this;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(username));
    }

    @Override
    public Optional<User> findById(int userID) {
        return byUsername.values().stream().filter(u -> u.getUserID() == userID).findFirst();
    }
}
