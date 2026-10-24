# Review — fintech-customer-insights baseline (Deliverable A)

The baseline implements a Java 25 / Spring Boot 4.1.0 read-only customer-insights backend with five REST endpoints under `/api/v1/customers/{customerId}` (profile, accounts, transactions, credit-status, financial-overview), a layered architecture (controller → service → repository interface → in-memory impl, with domain/dto/exception/config/fixtures), deterministic synthetic fixtures, a single RFC 7807 exception advice, springdoc OpenAPI, a committed Gradle 9.8.2 wrapper, and a GitHub Actions CI workflow. The implementation follows the approved plan file item-for-item. The forbidden `financial-health` feature is implemented nowhere; the only reference in `src/` is an integration test that asserts its absence from the OpenAPI spec. Build/test evidence was taken from the coder's verification note (58 tests, 0 failures, `BUILD SUCCESSFUL`), per instructions not to re-run the suites.

Watch for: nothing blocking. One minor observation (confirmed) — `financial-overview` fetches transactions with a null date range, which is correct for an aggregate view but means the overview never surfaces date-filtered transactions; this matches the plan and is not a defect.

**Verdict**: APPROVED

## High-level view

All five endpoints are present and correctly shaped: thin `@RestController`s that validate `customerId` with `@Pattern("^CUST-\\d{6}$")` + `@NotBlank`, delegate to a service, and return a DTO record. No business logic leaks into controllers, and `financial-overview` composes the four existing services rather than introducing new orchestration — exactly as the architecture steering requires.

The forbidden `financial-health` endpoint does not exist. A repo-wide search finds it only in steering, the plan, the verification note, and `OpenApiDocsIT`, which actively asserts the string is absent from `/v3/api-docs`. This is the critical gate and it passes.

Money is handled correctly throughout. Domain and fixture amounts are `BigDecimal` at scale 2 with `RoundingMode.HALF_EVEN` and an explicit ISO 4217 `java.util.Currency`; there is no `double`/`float` anywhere in main source. Monetary test assertions use `isEqualByComparingTo` plus an explicit `.scale()` check rather than `equals` on unscaled values.

Error handling is centralized in a single `@RestControllerAdvice` producing `ProblemDetail` responses with `type`, `title`, `status`, `detail`, and a `correlationId`. Unknown customer maps to 404, invalid input / `from > to` to 400 with field-level detail that never echoes submitted values, and the simulated dependency failure to 503 via `DependencyUnavailableException`. Empty collections for a known customer return 200 with an empty list.

Documentation, versions, and CI are in order: every endpoint carries springdoc annotations, the credit schema and DTO carry the synthetic-data disclaimer, the pinned versions (Spring Boot 4.1.0, springdoc 3.1.1, Gradle 9.8.2) match the plan's resolved-version table and are real, and `ci.yml` uses Temurin 25, `./gradlew build`, least-privilege `contents: read`, a JUnit artifact upload, and a non-blocking Playwright placeholder with no deploy or auto-merge.

<details>
<summary>Issues (0)</summary>

No blocking or actionable findings.

</details>

<details>
<summary>Details</summary>

### Five endpoints and the aggregation contract

Each endpoint is a thin adapter. `CustomerProfileController`, `AccountController`, `TransactionController`, `CreditStatusController`, and `FinancialOverviewController` all map `/api/v1/customers/{customerId}/...`, validate the path variable, call a single service method, and map the domain result through `DtoMapper` to a response record. None contain branching business logic.

`FinancialOverviewService` aggregates by composing the four domain services (`CustomerProfileService`, `AccountService`, `TransactionService`, `CreditStatusService`) and assembling a `FinancialOverviewResponse`. No new repository, cache, or orchestration layer is introduced, satisfying the "aggregates via the existing services" requirement. It calls `getTransactions(customerId, null, null)`, so the aggregate view returns all transactions unfiltered — consistent with the plan's design for the overview, not a bug.

Customer-existence is checked through `CustomerRepository` in each service, so a known customer with an empty accounts/transactions list returns 200 with an empty collection (Grace Hopper, `CUST-000003`), while an unknown but well-formed id (`CUST-999999`) raises `CustomerNotFoundException` → 404. This is the correct distinction between "empty" and "absent".

### Validation ordering in transactions

`TransactionService.getTransactions` checks `from.isAfter(to)` and throws `InvalidRequestException` before checking customer existence. The practical effect: a request with an inverted date range returns 400 even for an unknown customer — a fail-fast ordering that rejects a malformed request before touching the data layer. Both the 400 (from>to) and 404 (unknown) paths are covered by tests.

### Money representation

`FixtureData` constructs every amount via `new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN)` and carries an explicit `Currency` (GBP/USD/EUR) on accounts, transactions, and credit status. DTOs expose amounts as `BigDecimal` and currency as the ISO code string. A grep for `double|float|Double|Float` across main source returns nothing. `AccountServiceTest` asserts balances with `isEqualByComparingTo` and verifies `.scale() == 2`, matching the testing-standards rule against `equals` on unscaled values.

### RFC 7807 error surface

`GlobalExceptionHandler` is the sole `@RestControllerAdvice`. It maps:

- `CustomerNotFoundException` → 404
- `InvalidRequestException` → 400 (with optional `field`)
- `MethodArgumentNotValidException` / `ConstraintViolationException` / `HandlerMethodValidationException` → 400 with a list of `{field, reason}` violations
- `MethodArgumentTypeMismatchException` → 400 (malformed query param format)
- `DependencyUnavailableException` → 503

Every response is a `ProblemDetail` with `type` (a `urn:fintech:error:*` URI), `title`, `status`, `detail`, and a `correlationId` property. The validation detail carries field name and reason only — submitted values are never echoed — and dependency failures log the dependency name and correlation id at the boundary without leaking internals to the client. No stack traces are exposed.

### Dependency-failure seam

`DependencyFailureSeam` is a `@Component` the in-memory repositories call before serving data. The reserved id `CUST-000004` always triggers `DependencyUnavailableException`, and `forceFailure(id)` lets integration tests drive the 503 path deterministically. The seam carries no business rules, keeping the simulation isolated from domain logic.

### OpenAPI and the synthetic-data disclaimer

`OpenApiConfig` provides the `OpenAPI` bean with a title, version, and a description stating the data is synthetic and does not represent real creditworthiness or banking records. Each controller carries `@Operation` + `@ApiResponses` documenting 200/400/404/503 with `application/problem+json` schemas for errors. `CreditStatusResponse` is annotated with the synthetic/not-real-creditworthiness disclaimer at the schema level and also carries a runtime `disclaimer` field populated from `DtoMapper.SYNTHETIC_DISCLAIMER`, so the caveat appears in both the spec and the payload.

### Versions, wrapper, and CI

`build.gradle` pins Spring Boot `4.1.0`, `io.spring.dependency-management` `1.1.7`, `springdoc-openapi-starter-webmvc-ui:3.1.1`, and a Java 25 toolchain — all matching the plan's resolved-version evidence table and all real, published artifacts. The committed wrapper (`gradle-wrapper.jar`, `gradle-wrapper.properties`, executable `gradlew`, `gradlew.bat`) targets Gradle 9.8.2. `ci.yml` checks out, sets up Temurin Java 25, runs `./gradlew build --no-daemon` with working-directory `fintech-customer-insights`, uploads `build/test-results/test/*.xml` as an artifact on `always()`, declares top-level least-privilege `permissions: { contents: read }`, has no deploy or auto-merge step, and includes a clearly non-blocking `continue-on-error` placeholder job for Playwright acceptance tests without creating the acceptance-tests project.

### Build/test evidence

Per instructions, the suites were not re-run. The coder's `verification-note.md` records `./gradlew clean build` → exit 0, `BUILD SUCCESSFUL`, with tests=58, failures=0, errors=0, skipped=0 across 13 test classes spanning service-unit (Mockito, negative paths), controller (`@WebMvcTest`, RFC 7807 assertions), and integration (`@SpringBootTest` random port, including the `financial-health` absence guard and `/v3/api-docs` checks). The evidence is present and specific, so it is accepted as the build/test record.

</details>

<details>
<summary>File map</summary>

- `build.gradle`, `settings.gradle`, `gradle.properties` — Gradle project; SB 4.1.0, springdoc 3.1.1, Java 25 toolchain.
- `gradle/wrapper/*`, `gradlew`, `gradlew.bat` — committed Gradle 9.8.2 wrapper.
- `domain/*.java` — immutable records + enums (Customer, Account, Transaction, CreditStatus; type/status/segment enums).
- `exception/*.java` — domain exceptions + single `GlobalExceptionHandler` (RFC 7807).
- `repository/*.java`, `repository/inmemory/*.java` — interfaces + in-memory impls + `DependencyFailureSeam`.
- `fixtures/FixtureData.java` — deterministic synthetic seed data (BigDecimal scale-2 HALF_EVEN, explicit currency).
- `dto/*.java` — 8 response records + `DtoMapper` (holds `SYNTHETIC_DISCLAIMER`).
- `service/*.java` — five services; overview composes the other four.
- `controller/*.java` — five thin controllers under `/api/v1/customers/{customerId}`.
- `config/OpenApiConfig.java` — OpenAPI bean with synthetic-data disclaimer.
- `src/test/java/.../{service,controller,integration}/*` — 13 test classes.
- `../.github/workflows/ci.yml` — Temurin 25, `./gradlew build`, least-privilege, JUnit artifact, non-blocking Playwright placeholder.

</details>
