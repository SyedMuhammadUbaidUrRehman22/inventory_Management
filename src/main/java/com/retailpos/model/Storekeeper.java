package com.retailpos.model;

/** A user with the Storekeeper role. */
public class Storekeeper extends User {

    public Storekeeper() {
    }

    public Storekeeper(int userID, String username, String passwordHash) {
        super(userID, username, passwordHash);
    }
}
