package com.retailpos.util;

/**
 * Thrown when a business rule or validation fails. The message is user-readable
 * and is shown to the user in a JOptionPane.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
