# Baseline Report — fintech-customer-insights

Deliverable A of the governed AI-SDLC proof of concept: a read-only, synthetic
customer-insights backend built on **Java 25 / Spring Boot 4**. This report records the
final verification evidence, the application architecture, every endpoint, the test
results, how to run and start the service, the repository layout, and the
limitations/assumptions.

> All data served by this application is **synthetic** and does **not** represent real
> people, real creditworthiness, or real banking records.

---

## 1. Application architecture summary

Layered Spring Boot application with a strict separation of concerns and no database.

- **controller/** — thin REST adapters under `/api/v1/customers/{customerId}`. They map
  requests to service calls and return DTO records. No business logic. `customerId` is
  validated with Jakarta Validation (`@NotBlank` + `@Pattern(^CUST-\d{6}$)`).
- **service/** — business logic and aggregation. Services depend on repository
  *interfaces*, translate repository absence (`Optional.empty()`) into typed domain
  exceptions, and compose results (the financial-overview service aggregates the other
  four services).
- **repository/** — data-access abstractions (interfaces) with deterministic in-memory
  implementations in `repository/inmemory/`. A `DependencyFailureSeam` deterministically
  simulates a downstream dependency failure for a reserved id.
- **domain/** — immutable core models (`record` / enums). No persistence or web concerns.
- **dto/** — request/response `record` types at the API boundary, plus a `DtoMapper`.
  Domain objects are not leaked directly.
- **exception/** — domain exceptions and a single `@RestControllerAdvice`
  (`GlobalExceptionHandler`) that produces RFC 7807 `application/problem+json` responses
  with a correlation id. Controllers never build error bodies by hand.
- **config/** — OpenAPI configuration (`OpenApiConfig`) carrying the synthetic-data
  disclaimer in the API description.
- **fixtures/** — deterministic synthetic seed data (`FixtureData`). No clocks, no
  randomness. Money is `BigDecimal` scale 2, `RoundingMode.HALF_EVEN`, with explicit ISO
  4217 currency.

**Monetary rule:** every amount is `BigDecimal` with explicit scale 2 and
`RoundingMode.HALF_EVEN`, carrying an explicit `java.util.Currency`. No `double`/`float`.

**Error model:** expected conditions become typed domain exceptions —
`CustomerNotFoundException` → 404, `InvalidRequestException`/validation failures → 400,
`DependencyUnavailableException` → 503 — all rendered as RFC 7807 problem responses with
`type`, `title`, `status`, `detail`, `instance`, and a `correlationId`. No stack traces or
internal messages are leaked.

---

## 2. Endpoint documentation (all 5 endpoints)

Base path: `/api/v1`. All endpoints are resource-oriented under
`/customers/{customerId}` and return `200` with a body on success, `404` for an unknown
customer, `400` for invalid input, and `503` for a simulated dependency failure.

| # | Method & Path | Summary |
|---|---------------|---------|
| 1 | `GET /api/v1/customers/{customerId}/profile` | Synthetic customer profile (name, email, date of birth, segment, currency). |
| 2 | `GET /api/v1/customers/{customerId}/accounts` | Customer accounts with `BigDecimal` balances and ISO 4217 currency. Empty set returns `200` with an empty collection. |
| 3 | `GET /api/v1/customers/{customerId}/transactions` | Transactions with signed `BigDecimal` amounts. Optional inclusive ISO-8601 `from`/`to` query params; `from > to` → `400`. Empty set returns `200` with an empty collection. |
| 4 | `GET /api/v1/customers/{customerId}/credit-status` | Synthetic credit indicators (score, utilisation, limit, payment status) with an explicit synthetic-data disclaimer. |
| 5 | `GET /api/v1/customers/{customerId}/financial-overview` | Aggregator composing profile + accounts + transactions + credit-status into a single read-only view. |

Common behaviors for every endpoint:

- **Unknown customer** (well-formed id, e.g. `CUST-999999`) → `404`
  `application/problem+json`.
- **Malformed `customerId`** (fails `^CUST-\d{6}$`) → `400` with field-level detail, no
  sensitive values echoed.
- **Empty collections** for a known customer (e.g. `CUST-000003`) → `200` with an empty
  collection, never `404`.
- **Invalid date range** on `/transactions` (`from > to`) → `400`.
- **Simulated dependency failure** (reserved id `CUST-000004`) → `503`.

Each endpoint is documented with springdoc-openapi annotations (`@Operation`,
`@ApiResponse`, `@Schema`, parameter descriptions, and documented 400/404/503 responses).

> The `GET /api/v1/customers/{customerId}/financial-health` endpoint is intentionally
> **NOT** implemented in this baseline. It is the Phase D demonstration feature delivered
> later through the `feature-delivery` workflow, and an integration test asserts it is
> absent from the OpenAPI spec.

### Synthetic fixture ids (for exploration)

| Customer id | Notes |
|-------------|-------|
| `CUST-000001` (Ada Lovelace) | Full data: 2 accounts, 3 transactions, credit status. |
| `CUST-000002` (Alan Turing) | Full data, includes a credit account and a `LATE` payment status. |
| `CUST-000003` (Grace Hopper) | Known customer with **empty** accounts and transactions (exercises the `200` empty-collection path). |
| `CUST-000004` | Reserved id that triggers a simulated dependency failure (`503`). |
| `CUST-999999` | Reserved well-formed id that is never present (exercises `404`). |

---

## 3. Test report

Framework: JUnit Jupiter (JUnit 6 line under Spring Boot 4) + Mockito + Spring Boot Test.

**How to run:** from inside `fintech-customer-insights/`:

```bash
./gradlew build
```

This compiles the project and runs all unit, controller, and integration tests. Results
are written to `build/test-results/test/*.xml` and an HTML report to
`build/reports/tests/test/index.html`.

**Result (final verification run):**

- Command: `./gradlew build` — exit code **0** (`BUILD SUCCESSFUL`); re-confirmed with
  `./gradlew clean build` exit code **0** to force test execution.
- Totals aggregated from `build/test-results/test/*.xml`: **tests = 58, failures = 0,
  errors = 0, skipped = 0** across **13** test classes. **Status: PASSED.**

Test layers:

- **Service unit tests** (Mockito, mocked repositories, positive + negative paths):
  `AccountServiceTest`, `CustomerProfileServiceTest`, `CreditStatusServiceTest`,
  `TransactionServiceTest`, `FinancialOverviewServiceTest`.
- **Controller tests** (`@WebMvcTest`, mocked services, status codes, JSON bodies,
  validation, RFC 7807 error shapes): `CustomerProfileControllerTest`,
  `AccountControllerTest`, `TransactionControllerTest`, `CreditStatusControllerTest`,
  `FinancialOverviewControllerTest`.
- **Integration tests** (`@SpringBootTest` random port, wired app + fixtures):
  `CustomerEndpointsIT`, `FinancialOverviewIT`, `OpenApiDocsIT` (the last asserts
  `/v3/api-docs` is `200`, Swagger UI resolves, and `financial-health` is absent).

Monetary assertions compare `BigDecimal` with `compareTo` / explicit scale. Tests are
deterministic (fixed synthetic fixtures, no clock or randomness).

---

## 4. Running the service locally

Prerequisites: the committed Gradle wrapper handles Gradle (9.8.2) and the Java 25
toolchain; no system Gradle/Maven is required.

Start the application:

```bash
cd fintech-customer-insights
./gradlew bootRun
```

The service listens on **http://localhost:8080**.

- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8080/v3/api-docs

Alternatively, run the packaged jar:

```bash
./gradlew build
java -jar build/libs/fintech-customer-insights-0.0.1-SNAPSHOT.jar
```

Quick smoke checks:

```bash
# 200 — known customer financial overview
curl -i http://localhost:8080/api/v1/customers/CUST-000001/financial-overview

# 404 problem+json — unknown customer
curl -i http://localhost:8080/api/v1/customers/CUST-999999/profile

# 200 JSON — OpenAPI document
curl -i http://localhost:8080/v3/api-docs
```

---

## 5. Repository structure

```
fintech-customer-insights/
├── build.gradle                     # Spring Boot 4 plugin, Java 25 toolchain, springdoc
├── settings.gradle
├── gradle.properties
├── gradlew / gradlew.bat            # committed Gradle wrapper (Gradle 9.8.2)
├── gradle/wrapper/                  # wrapper jar + properties
├── BASELINE-REPORT.md               # this report
├── .agents/tasks/                   # plan / review / verification-note evidence
└── src/
    ├── main/
    │   ├── java/com/example/fintech/
    │   │   ├── FintechCustomerInsightsApplication.java
    │   │   ├── config/              # OpenApiConfig
    │   │   ├── controller/          # 5 thin REST controllers
    │   │   ├── service/             # 5 services (business logic + aggregation)
    │   │   ├── repository/          # repository interfaces
    │   │   │   └── inmemory/        # deterministic in-memory impls + failure seam
    │   │   ├── domain/              # immutable domain models + enums
    │   │   ├── dto/                 # request/response records + DtoMapper
    │   │   ├── exception/           # domain exceptions + GlobalExceptionHandler (RFC 7807)
    │   │   └── fixtures/            # FixtureData (synthetic seed data)
    │   └── resources/
    │       └── application.yml      # port 8080, springdoc paths
    └── test/
        └── java/com/example/fintech/
            ├── service/             # service unit tests (Mockito)
            ├── controller/          # @WebMvcTest controller tests
            └── integration/         # @SpringBootTest integration tests
```

---

## 6. Limitations and assumptions

- **Synthetic data disclaimer.** All customers, accounts, transactions, and credit
  indicators are synthetic, deterministic fixtures. They do **not** represent real people,
  real creditworthiness, or real banking records, and must not be used for any real
  financial decision.
- **Resolved Spring Boot version.** The approved baseline targets the Spring Boot 4 line;
  this build resolves to **Spring Boot 4.1.0** (Spring Framework 7, Jakarta EE 11
  `jakarta.*`, Jackson 3, JUnit Jupiter 6). Under Spring Boot 4, `@WebMvcTest` /
  `@AutoConfigureMockMvc` moved out of `spring-boot-starter-test` into the
  `spring-boot-starter-webmvc-test` module, which is included as a test dependency.
  `springdoc-openapi-starter-webmvc-ui` is on the Spring Boot 4-compatible 3.1.x line.
- **No database.** Persistence is intentionally out of scope. Repositories are in-memory
  and seeded from `FixtureData`; there is no schema, migration, or external datastore.
  State does not persist across restarts.
- **Read-only, no auth.** All endpoints are read-only `GET`s. There is no authentication,
  authorization, rate limiting, or distributed orchestration in the baseline.
- **Deterministic by design.** No clock reads or randomness; timestamps in fixtures are
  fixed ISO-8601 UTC values, so responses are stable and reproducible.
- **`financial-health` excluded.** The `GET .../financial-health` endpoint is not part of
  this baseline by design; it is delivered later through the governed `feature-delivery`
  workflow.
```
