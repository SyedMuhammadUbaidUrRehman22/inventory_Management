package com.retailpos.model;

/** A user with the Manager role. */
public class Manager extends User {

    public Manager() {
    }

    public Manager(int userID, String username, String passwordHash) {
        super(userID, username, passwordHash);
    }

    public Manager(int userID, String username, String passwordHash, Integer createdByID, boolean active) {
        super(userID, username, passwordHash, createdByID, active);
    }
}
