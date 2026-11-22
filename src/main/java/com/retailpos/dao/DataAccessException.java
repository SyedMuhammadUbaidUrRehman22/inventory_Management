package com.retailpos.dao;

/**
 * Thrown by DAO implementations when a database operation fails.
 * Unchecked so service and UI code is not cluttered with SQLException handling.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
