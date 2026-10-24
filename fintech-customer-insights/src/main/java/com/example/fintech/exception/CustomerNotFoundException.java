package com.example.fintech.exception;

/**
 * Thrown when a well-formed customer identifier does not match any known customer.
 * Translated to HTTP 404 by the centralized exception handler.
 */
public class CustomerNotFoundException extends RuntimeException {

    private final String customerId;

    public CustomerNotFoundException(String customerId) {
        super("Customer not found: " + customerId);
        this.customerId = customerId;
    }

    public String customerId() {
        return customerId;
    }
}
