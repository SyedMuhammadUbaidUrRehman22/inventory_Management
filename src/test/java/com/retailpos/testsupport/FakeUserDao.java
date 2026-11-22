package com.retailpos.testsupport;

import com.retailpos.dao.UserDao;
import com.retailpos.model.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * In-memory {@link UserDao} for unit tests.
 * Usernames are matched case-insensitively, like the MySQL collation used by schema.sql.
 */
public class FakeUserDao implements UserDao {

    private final Map<String, User> byUsername = new TreeMap<>();
    private int nextId = 1000;

    public FakeUserDao add(User user) {
        byUsername.put(key(user.getUsername()), user);
        return this;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(key(username)));
    }

    @Override
    public Optional<User> findById(int userID) {
        return byUsername.values().stream().filter(u -> u.getUserID() == userID).findFirst();
    }

    @Override
    public void insert(User user) {
        if (byUsername.containsKey(key(user.getUsername()))) {
            throw new IllegalStateException("Duplicate username (the UNIQUE constraint would reject this)");
        }
        user.setUserID(nextId++);
        add(user);
    }

    @Override
    public List<User> findByCreator(int managerID) {
        List<User> result = new ArrayList<>();
        for (User u : byUsername.values()) {
            if (u.getCreatedByID() != null && u.getCreatedByID() == managerID) {
                result.add(u);
            }
        }
        result.sort(Comparator.comparing(User::getUsername));
        return result;
    }

    @Override
    public boolean setActive(int userID, boolean active) {
        Optional<User> user = findById(userID);
        user.ifPresent(u -> u.setActive(active));
        return user.isPresent();
    }

    @Override
    public boolean updatePasswordHash(int userID, String passwordHash) {
        Optional<User> user = findById(userID);
        user.ifPresent(u -> u.setPasswordHash(passwordHash));
        return user.isPresent();
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
