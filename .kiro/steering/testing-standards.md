---
inclusion: always
---

# Testing Standards

- Test framework: JUnit 5 / Jupiter (JUnit 6 under Spring Boot 4) + Mockito + Spring Boot Test.
- **Tests are derived from requirements before production code (TDD: RED → GREEN → REFACTOR).** Never weaken or delete a valid test to make a build pass.
- Layers:
  - **Service unit tests**: pure logic, mock repositories, assert business behavior incl. negative paths.
  - **Controller tests**: `@WebMvcTest` with mocked services; assert status codes, JSON bodies, validation, and RFC 7807 error shapes.
  - **Integration tests**: `@SpringBootTest` (random port) exercising the wired app end-to-end against in-memory fixtures.
- Every endpoint has positive and negative scenarios: unknown customer, empty collections, invalid input, boundary values.
- Tests must be deterministic (fixed synthetic fixtures, no clocks/randomness without injection) and independently runnable.
- Monetary assertions compare `BigDecimal` with `compareTo` / explicit scale, never `equals` on unscaled values.
- Acceptance (black-box HTTP) tests live in `acceptance-tests/` using Playwright Test's `request` fixture; they run against a locally started app and are wired into CI. Never run mutating tests against a real/production environment.
- Report outcomes honestly: PASSED / FAILED / NOT RUN / BLOCKED. Never report an unexecuted test as passing.
