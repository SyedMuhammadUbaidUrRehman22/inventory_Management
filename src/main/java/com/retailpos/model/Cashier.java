package com.retailpos.model;

/** A user with the Cashier role. */
public class Cashier extends User {

    public Cashier() {
    }

    public Cashier(int userID, String username, String passwordHash) {
        super(userID, username, passwordHash);
    }
}
