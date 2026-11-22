package com.retailpos.model;

/**
 * A system user. The concrete subclass (Manager, Cashier, Storekeeper) is the user's role.
 */
public abstract class User {

    private int userID;
    private String username;
    private String passwordHash;
    /** The Manager who created this account; null for a Manager (Managers sign up themselves). */
    private Integer createdByID;
    /** A deactivated account cannot sign in. */
    private boolean active = true;

    protected User() {
    }

    protected User(int userID, String username, String passwordHash) {
        this.userID = userID;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    protected User(int userID, String username, String passwordHash, Integer createdByID, boolean active) {
        this(userID, username, passwordHash);
        this.createdByID = createdByID;
        this.active = active;
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

    public Integer getCreatedByID() {
        return createdByID;
    }

    public void setCreatedByID(Integer createdByID) {
        this.createdByID = createdByID;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
