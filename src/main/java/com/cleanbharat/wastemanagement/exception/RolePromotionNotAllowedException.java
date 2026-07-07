package com.cleanbharat.wastemanagement.exception;

/**
 * Thrown when a user cannot be promoted to the specific role.
 */
public class RolePromotionNotAllowedException extends RuntimeException {
    public RolePromotionNotAllowedException(String message) {
        super(message);
    }
}