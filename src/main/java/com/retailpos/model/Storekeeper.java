package com.retailpos.model;

/** A user with the Storekeeper role. */
public class Storekeeper extends User {

    public Storekeeper() {
    }

    public Storekeeper(int userID, String username, String passwordHash) {
        super(userID, username, passwordHash);
    }

    public Storekeeper(int userID, String username, String passwordHash, Integer createdByID, boolean active) {
        super(userID, username, passwordHash, createdByID, active);
    }
}
