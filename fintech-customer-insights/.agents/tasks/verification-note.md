# Verification Note — fintech-customer-insights baseline

Iteration: first (no `review.json` present at start).
Work mode: file-creation only (no git commands run; workspace is intentionally not a git repo).

## FINAL VERIFICATION GATE (fail-closed) — EVIDENCE

Executed inside `fintech-customer-insights/`. No git commands run.

### Check 1 — WRAPPER: `./gradlew --version`
- Gradle **9.8.2**; Launcher JVM 25.0.4 (Oracle). Wrapper bootstrapped and runs.
- Exit code: **0**. Result: **PASSED**.

### Check 2 — BUILD: `./gradlew build`
- `./gradlew build` → `BUILD SUCCESSFUL`, exit code **0**.
- Re-run `./gradlew clean build` to force test execution → `BUILD SUCCESSFUL`, exit code **0**.
- Test totals from `build/test-results/test/*.xml` (13 test classes):
  **tests=58, failures=0, errors=0, skipped=0**.
- Result: **PASSED**.

### Check 3 — RUNTIME (app started in background from built jar, then stopped)
Command: `java -jar build/libs/fintech-customer-insights-0.0.1-SNAPSHOT.jar &` — ready after ~4s.

| Request | Expected | Actual |
|---------|----------|--------|
| `GET /api/v1/customers/CUST-000001/financial-overview` | 200 | **HTTP 200**, `application/json` |
| `GET /api/v1/customers/CUST-999999/profile` | 404 problem+json | **HTTP 404**, `application/problem+json` |
| `GET /v3/api-docs` | 200 JSON | **HTTP 200**, `application/json` (`openapi: 3.1.0`) |

- Known synthetic customer id: `CUST-000001` (Ada Lovelace). Unknown id: `CUST-999999`.
- Background process stopped afterward (verified port 8080 no longer listening).
- Result: **PASSED**.

### Deliverable
- Wrote `BASELINE-REPORT.md` (architecture, 5 endpoints, test report + how to run, start
  instructions, repository structure, limitations/assumptions incl. synthetic-data
  disclaimer, resolved Spring Boot 4.1.0 note, and no-database note).

**Overall: ALL CHECKS PASSED.**

---

## Original evidence (from initial build-out)

## Toolchain (verified)

- `java -version` → `25.0.4` (Oracle, LTS).
- Committed Gradle wrapper: **Gradle 9.8.2** (bootstrapped via `gradle wrapper`, then verified through `./gradlew`).
  - `./gradlew --version` → Gradle 9.8.2, Launcher JVM 25.0.4. Wrapper distribution downloads and runs.
- Spring Boot **4.1.0** (Spring Framework 7.0.8, Jakarta EE 11 `jakarta.*`, Jackson 3, JUnit Jupiter 6).
- springdoc-openapi-starter-webmvc-ui **3.1.1**.
- Added test dependency `spring-boot-starter-webmvc-test` (Spring Boot 4 moved `@WebMvcTest` /
  `@AutoConfigureMockMvc` out of `spring-boot-starter-test` into the `org.springframework.boot.webmvc.test.autoconfigure`
  package / `spring-boot-webmvc-test` module).

## Build command and result

Command (run inside `fintech-customer-insights/`):

```
./gradlew clean build
```

Exit code: **0** — `BUILD SUCCESSFUL`.

Test totals (from `build/test-results/test/*.xml`): **tests=58, failures=0, errors=0, skipped=0** across **13**
test classes:

- Service unit (Mockito): `AccountServiceTest`, `CustomerProfileServiceTest`, `CreditStatusServiceTest`,
  `TransactionServiceTest`, `FinancialOverviewServiceTest` (incl. negative paths, date-range, dependency failure).
- Controller (`@WebMvcTest`, mocked services, RFC 7807 assertions): `CustomerProfileControllerTest`,
  `AccountControllerTest`, `TransactionControllerTest`, `CreditStatusControllerTest`,
  `FinancialOverviewControllerTest`.
- Integration (`@SpringBootTest` random port, wired app + fixtures): `CustomerEndpointsIT`, `FinancialOverviewIT`,
  `OpenApiDocsIT` (asserts `/v3/api-docs` 200, Swagger UI resolves, and `financial-health` absent from the spec).

## Endpoints implemented (all under `/api/v1/customers/{customerId}`)

1. `/profile` — synthetic profile.
2. `/accounts` — accounts (BigDecimal balances, ISO 4217 currency).
3. `/transactions` — signed BigDecimal amounts; optional ISO-8601 `from`/`to`; `from > to` → 400; empty → 200 `[]`.
4. `/credit-status` — synthetic credit indicators + explicit synthetic-data disclaimer.
5. `/financial-overview` — aggregator composing the four services (no new orchestration infra).

## Error / validation behaviors (covered by tests)

- Unknown customer (well-formed id) → **404** `application/problem+json` (`CustomerNotFoundException`).
- Malformed `customerId` (`@Pattern ^CUST-\d{6}$` + `@NotBlank`) → **400**.
- `from > to` → **400** (`InvalidRequestException`).
- Empty accounts / empty transactions for a known customer → **200** with empty collection (`CUST-000003`).
- Simulated dependency failure → **503** via `DependencyUnavailableException`, triggered deterministically through
  `DependencyFailureSeam` (reserved id `CUST-000004`, and a forced-failure toggle used by integration tests).

All errors flow through a single centralized `@RestControllerAdvice` (`GlobalExceptionHandler`) producing RFC 7807
`ProblemDetail` with `type`, `title`, `status`, `detail`, `instance`; a correlation id is attached and logged at the
boundary. No stack traces or internal messages are leaked.

## Convention checks

- `grep -rin "financial-health|financialHealth|FinancialHealth" src` → only the OpenApiDocsIT guard that asserts the
  endpoint is NOT exposed. The forbidden feature is implemented nowhere.
- `grep -rn "@Autowired" src` → only in test classes (field injection of `MockMvc` / test beans, standard for tests).
  Production wiring is constructor-injection only.
- Money: `BigDecimal` at scale 2 with `RoundingMode.HALF_EVEN`, explicit `java.util.Currency`. No `double`/`float`.
  Monetary assertions use `compareTo` / explicit `scale`.
- DTOs are `record` types; timestamps are `OffsetDateTime` serialized ISO-8601 (Jackson 3 defaults in Spring Boot 4).

## OpenAPI / Swagger

- `OpenApiConfig` provides the `OpenAPI` bean (title, version, synthetic-data disclaimer in the description).
- Every endpoint carries springdoc annotations (`@Operation`, `@ApiResponse`, `@Schema`, examples, documented
  400/404/503). `/v3/api-docs` serves 200 JSON and Swagger UI resolves at `/swagger-ui/index.html`
  (both asserted in `OpenApiDocsIT`).

## CI

- `/.github/workflows/ci.yml` (repo root): checkout → setup Temurin Java 25 → `./gradlew build`
  (working-directory `fintech-customer-insights`) → upload `build/test-results/test/*.xml` as an artifact.
  Top-level least-privilege `permissions: { contents: read }`. No deployment, no auto-merge. A clearly-marked
  non-blocking placeholder job notes Playwright acceptance tests wire in later (the acceptance-tests project is NOT
  created now). YAML validated with `yaml.safe_load`.
