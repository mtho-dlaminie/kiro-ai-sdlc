package com.example.fintech.exception;

/**
 * Thrown for semantically invalid requests that pass basic syntactic validation,
 * e.g. a date range where {@code from} is after {@code to}.
 * Translated to HTTP 400 by the centralized exception handler.
 */
public class InvalidRequestException extends RuntimeException {

    private final String field;

    public InvalidRequestException(String message) {
        this(message, null);
    }

    public InvalidRequestException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
