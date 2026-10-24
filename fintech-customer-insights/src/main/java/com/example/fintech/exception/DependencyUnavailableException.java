package com.example.fintech.exception;

/**
 * Thrown when an expected downstream dependency is unavailable. In the baseline this is simulated
 * through a deterministic test seam. Translated to HTTP 503 by the centralized exception handler.
 */
public class DependencyUnavailableException extends RuntimeException {

    private final String dependency;

    public DependencyUnavailableException(String dependency) {
        super("Dependency unavailable: " + dependency);
        this.dependency = dependency;
    }

    public String dependency() {
        return dependency;
    }
}
