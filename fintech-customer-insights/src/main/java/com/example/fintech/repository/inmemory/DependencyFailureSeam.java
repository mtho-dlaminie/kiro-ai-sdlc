package com.example.fintech.repository.inmemory;

import com.example.fintech.exception.DependencyUnavailableException;
import com.example.fintech.fixtures.FixtureData;
import org.springframework.stereotype.Component;

/**
 * Deterministic seam that simulates an expected downstream dependency failure. The reserved customer
 * id {@link FixtureData#DEPENDENCY_FAILURE_CUSTOMER_ID} always triggers failure; tests may additionally
 * force failure for any id via {@link #forceFailure(String)}.
 *
 * <p>This exists only to exercise the 503 path without a real external dependency. It carries no
 * business rules.
 */
@Component
public class DependencyFailureSeam {

    private volatile String forcedFailureId;

    /** Forces the seam to fail for the given customer id. Pass {@code null} to clear. */
    public void forceFailure(String customerId) {
        this.forcedFailureId = customerId;
    }

    /**
     * Throws {@link DependencyUnavailableException} when the given id is the reserved failure id or has
     * been forced to fail. Called by in-memory repositories before serving data.
     */
    public void checkAvailability(String customerId, String dependencyName) {
        if (FixtureData.DEPENDENCY_FAILURE_CUSTOMER_ID.equals(customerId)
                || (forcedFailureId != null && forcedFailureId.equals(customerId))) {
            throw new DependencyUnavailableException(dependencyName);
        }
    }
}
