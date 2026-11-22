package com.retailpos.model;

/**
 * A system user. The concrete subclass (Manager, Cashier, Storekeeper) is the user's role.
 */
public abstract class User {

    private int userID;
    private String username;
    private String passwordHash;

    protected User() {
    }

    protected User(int userID, String username, String passwordHash) {
        this.userID = userID;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public int getUserID() {
        return userID;
    }

    public void setUserID(int userID) {
        this.userID = userID;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** The password hash is deliberately left out so it never ends up in logs. */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
                + "userID=" + userID
                + ", username='" + username + '\''
                + '}';
    }
}
