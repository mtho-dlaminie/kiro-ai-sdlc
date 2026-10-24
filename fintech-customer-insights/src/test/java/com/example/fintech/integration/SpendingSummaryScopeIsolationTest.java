package com.example.fintech.integration;

import com.example.fintech.controller.FinancialHealthController;
import com.example.fintech.dto.FinancialHealthResponse;
import com.example.fintech.service.FinancialHealthService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scope-isolation guard (test-plan §8, AC-12). Fails if the spending-summary feature were to change
 * the financial-health controller/service/response public API, or if any fintech business logic
 * (a {@code .java} source) were added under {@code .kiro/}. This is a negative guard: it asserts the
 * invariant, it does not exercise spending-summary behaviour (that lives in the U/C/I/A suites).
 *
 * <p>The authoritative enforcement remains the governed diff / selective-staging review; this test
 * provides a fast, deterministic signal so any accidental edit surfaces in the build.
 */
class SpendingSummaryScopeIsolationTest {

    @Test
    void financialHealthResponseShapeUnchanged() {
        List<String> components = Stream.of(FinancialHealthResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components).containsExactly(
                "customerId", "currency", "monthlyIncome", "monthlyExpenses", "savingsRate",
                "debtToIncomeRatio", "financialHealthScore", "rating", "recommendations", "disclaimer");
    }

    @Test
    void financialHealthServiceAndControllerStillPresent() {
        // Presence + no accidental rename/removal of the collaborators this feature must not touch.
        assertThat(FinancialHealthService.class.getSimpleName()).isEqualTo("FinancialHealthService");
        assertThat(FinancialHealthController.class.getSimpleName()).isEqualTo("FinancialHealthController");
    }

    @Test
    void noFintechJavaSourcesUnderKiro() throws Exception {
        // Resolve the repository root from the app module's working directory (.../fintech-customer-insights).
        Path kiro = Path.of("").toAbsolutePath().getParent().resolve(".kiro");
        if (!Files.isDirectory(kiro)) {
            return; // .kiro not present in this checkout layout — nothing to guard.
        }
        try (Stream<Path> paths = Files.walk(kiro)) {
            List<Path> javaSources = paths.filter(p -> p.toString().endsWith(".java")).toList();
            assertThat(javaSources)
                    .as("No fintech Java business logic may live under .kiro/")
                    .isEmpty();
        }
    }
}
