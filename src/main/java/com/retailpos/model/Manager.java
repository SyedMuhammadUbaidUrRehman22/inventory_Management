package com.retailpos.model;

/** A user with the Manager role. */
public class Manager extends User {

    public Manager() {
    }

    public Manager(int userID, String username, String passwordHash) {
        super(userID, username, passwordHash);
    }
}
