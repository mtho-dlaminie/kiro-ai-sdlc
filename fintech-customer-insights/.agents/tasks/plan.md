# Implementation Plan — fintech-customer-insights baseline

Governed AI-SDLC fintech backend baseline (Deliverable A). Java 25 / Spring Boot 4 / Gradle 9, layered
architecture, deterministic in-memory data, RFC 7807 errors, springdoc Swagger UI, full TDD test suite, CI.

## Resolved versions (from Maven Central / services.gradle.org on this machine)

| Component | Version | Evidence |
|---|---|---|
| JDK (installed) | 25.0.4 (`java -version` → 25.0.4+7-LTS) | local |
| Gradle wrapper | **9.8.2** (current) | `services.gradle.org/versions/current` → 9.8.2, dist URL 200/307 |
| Spring Boot | **4.1.0** | `spring-boot-dependencies-4.1.0.pom` 200; `spring-boot-gradle-plugin-4.1.0.pom` 200 |
| Spring Framework | 7.x (managed transitively by SB 4.1.0 BOM) | via BOM |
| Jakarta EE | 11 (`jakarta.*`) | via SB 4 baseline |
| Jackson | 3 (core) managed by SB 4.1.0 BOM | via BOM |
| JUnit | 6 / Jupiter (managed by SB 4.1.0 BOM via spring-boot-starter-test) | via BOM |
| springdoc-openapi | **3.1.1** (`springdoc-openapi-starter-webmvc-ui:3.1.1`) | metadata 200; parent = spring-boot-starter-parent 4.1.0 |

### Version rationale
- **Spring Boot 4.1.0 (not 4.1.2):** springdoc 3.1.1's build parent is `spring-boot-starter-parent:4.1.0`, so 4.1.0
  is the exact tested baseline for springdoc. There is a known transitive-Jackson quirk
  (springdoc GitHub issue #3373: springdoc 3.1.1 pulls swagger-core 2.2.55 built against Jackson 2.22.x while the
  SB 4.1 BOM manages Jackson 2.21.x). Spring's BOM wins by default in Gradle, which is the desired outcome. Pinning
  SB to 4.1.0 avoids drifting further from springdoc's tested line. If 4.1.0 ever fails to compile/resolve, fall
  back to 4.0.9 (latest 4.0.x GA, also confirmed present in Central metadata). (Content rephrased for compliance
  with licensing restrictions.)
- **Gradle 9.8.2:** current stable; Spring Boot 4 requires Gradle 9. Wrapper is committed so no system Gradle needed.
- **springdoc 3.1.1:** latest 3.x (Spring Boot 4 line). Exposes Swagger UI at `/swagger-ui.html` and OpenAPI JSON at
  `/v3/api-docs` per springdoc docs.

## Wrapper bootstrap approach (no system Gradle)
Hand-write the wrapper files, then let the first `./gradlew` download the distribution:
1. Create `gradle/wrapper/gradle-wrapper.properties` pointing at
   `https://services.gradle.org/distributions/gradle-9.8.2-bin.zip`, with `networkTimeout=600000` and
   `validateDistributionUrl=true`.
2. Fetch `gradle-wrapper.jar` for Gradle 9.8.2 into `gradle/wrapper/gradle-wrapper.jar`. Source it from Maven Central
   (`org.gradle.wrapper:gradle-wrapper` is NOT on Central) — instead fetch it from the Gradle distribution services or
   the GitHub raw `gradle/gradle` tag `v9.8.2` `gradle/wrapper/gradle-wrapper.jar`. If direct fetch of the jar fails,
   bootstrap by downloading `gradle-9.8.2-bin.zip` to a temp dir, unzip, and run
   `<tmp>/gradle-9.8.2/bin/gradle wrapper --gradle-version 9.8.2 --gradle-distribution-sha256-sum <sha>` once inside
   the project dir to let Gradle generate a correct wrapper jar + scripts. (The committed `.gitignore` already
   un-ignores `gradle-wrapper.jar`.)
3. Hand-write/confirm `gradlew` (POSIX, executable `chmod +x`) and `gradlew.bat`.
4. Use generous timeouts (600000ms) and retry once on network timeout.

All work is file creation only under `/home/mtho/workspace/ai sdlc/`. **Do NOT run `git init` or any git command.**

## Deterministic synthetic fixtures (seed data)
All amounts are `BigDecimal` scale 2, `RoundingMode.HALF_EVEN`, explicit ISO-4217 currency. Dates are fixed
(no clocks/randomness). `customerId` format: `CUST-` + 6 digits (regex `^CUST-\d{6}$`).

Known customers:
- **CUST-000001** "Ada Lovelace", email `ada.lovelace@example.com`, DOB 1985-12-10, segment `RETAIL`, currency GBP.
  - Accounts: `ACC-1001` CHECKING GBP available 1500.00 current 1600.00 ACTIVE; `ACC-1002` SAVINGS GBP
    available 10250.75 current 10250.75 ACTIVE.
  - Transactions (GBP): `TXN-5001` ACC-1001 -45.20 DEBIT category GROCERIES 2025-01-05 "Supermarket";
    `TXN-5002` ACC-1001 +2500.00 CREDIT category SALARY 2025-01-01 "Payroll";
    `TXN-5003` ACC-1002 +250.75 CREDIT category INTEREST 2025-01-31 "Monthly interest".
  - Credit: creditScore 742, creditUtilization 18.50 (%), outstandingDebt 1200.00 GBP, paymentStatus CURRENT,
    indicators: onTimePaymentRate 0.98, openAccounts 2.
- **CUST-000002** "Alan Turing", email `alan.turing@example.com`, DOB 1990-06-23, segment `PREMIER`, currency USD.
  - Accounts: `ACC-2001` CHECKING USD available 320.10 current 320.10 ACTIVE; `ACC-2002` CREDIT USD
    available 4000.00 current -850.00 ACTIVE.
  - Transactions (USD): `TXN-6001` ACC-2002 -850.00 DEBIT category ELECTRONICS 2025-02-14 "Laptop";
    `TXN-6002` ACC-2001 +1800.00 CREDIT category SALARY 2025-02-01 "Payroll".
  - Credit: creditScore 681, creditUtilization 42.75 (%), outstandingDebt 850.00 USD, paymentStatus LATE,
    indicators: onTimePaymentRate 0.90, openAccounts 2.
- **CUST-000003** "Grace Hopper", email `grace.hopper@example.com`, DOB 1979-03-15, segment `RETAIL`, currency EUR.
  - Accounts: **empty list** (exercises empty-accounts 200 path).
  - Transactions: **empty list** (exercises empty-transactions 200 path).
  - Credit: creditScore 705, creditUtilization 0.00, outstandingDebt 0.00 EUR, paymentStatus CURRENT,
    indicators: onTimePaymentRate 1.00, openAccounts 0.

Special IDs for negative tests:
- **CUST-999999** — well-formed but unknown → 404 everywhere (`CustomerNotFoundException`).
- **CUST-000004** — reserved as the dependency-failure trigger: repositories/service seam throws
  `DependencyUnavailableException` → 503 (simulated expected dependency failure; triggerable in tests via the seam).
- Malformed id e.g. `bad id`, `123`, `CUST-1` → 400 (`@Pattern` validation → `ConstraintViolationException`).

Disclaimer string (used in credit-status + credit schema description): synthetic data, does NOT represent real
creditworthiness or real banking records.

---

# Implementation Plan (ordered, each item leaves the project buildable)

- [ ] 1. Create the Gradle project skeleton and committed wrapper (Gradle 9.8.2).
      Create the build script, settings, properties, wrapper files, and make `gradlew` executable. Set Java
      toolchain to 25, apply `java`, `org.springframework.boot` 4.1.0, `io.spring.dependency-management`. Add deps:
      `spring-boot-starter-web`, `spring-boot-starter-validation`, `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1`,
      `spring-boot-starter-test` (testImplementation). Configure `test { useJUnitPlatform() }`. `bootJar` main class
      `com.example.fintech.FintechCustomerInsightsApplication`.
      Files: `fintech-customer-insights/build.gradle`, `fintech-customer-insights/settings.gradle`
      (`rootProject.name = 'fintech-customer-insights'`), `fintech-customer-insights/gradle.properties`
      (`org.gradle.jvmargs`, `org.gradle.caching=true`), `fintech-customer-insights/gradle/wrapper/gradle-wrapper.properties`,
      `fintech-customer-insights/gradle/wrapper/gradle-wrapper.jar`, `fintech-customer-insights/gradlew`,
      `fintech-customer-insights/gradlew.bat`.
      Verify: `cd fintech-customer-insights && ./gradlew --version` prints Gradle 9.8.2 and JVM 25 (download may be
      slow; retry once with 600000ms timeout).

- [ ] 2. Create the Spring Boot application entry point and base config so the context boots.
      Minimal `@SpringBootApplication` main class plus `application.yml` (server port 8080, application name,
      springdoc paths left default: `/swagger-ui.html`, `/v3/api-docs`).
      Files: `src/main/java/com/example/fintech/FintechCustomerInsightsApplication.java`,
      `src/main/resources/application.yml`.
      Verify: `./gradlew compileJava` succeeds (BUILD SUCCESSFUL).

- [ ] 3. Create domain models (immutable records) and enums.
      Records: `Customer` (customerId, fullName, email, dateOfBirth LocalDate, segment, currency Currency),
      `Account` (accountId, customerId, AccountType, Currency, availableBalance BigDecimal, currentBalance BigDecimal,
      AccountStatus), `Transaction` (transactionId, accountId, customerId, amount BigDecimal signed, Currency,
      TransactionType, category, transactionDate OffsetDateTime, description), `CreditStatus` (customerId,
      creditScore int, creditUtilization BigDecimal, outstandingDebt BigDecimal, Currency, PaymentStatus,
      onTimePaymentRate BigDecimal, openAccounts int). Enums: `AccountType{CHECKING,SAVINGS,CREDIT}`,
      `AccountStatus{ACTIVE,CLOSED,FROZEN}`, `TransactionType{DEBIT,CREDIT}`, `PaymentStatus{CURRENT,LATE}`,
      `Segment{RETAIL,PREMIER}` (or String segment — use enum). Money helper `Money` util is optional; keep scale 2
      HALF_EVEN at construction in fixtures.
      Files: `src/main/java/com/example/fintech/domain/{Customer,Account,Transaction,CreditStatus}.java`,
      `src/main/java/com/example/fintech/domain/{AccountType,AccountStatus,TransactionType,PaymentStatus,Segment}.java`.
      Verify: `./gradlew compileJava` succeeds.

- [ ] 4. Create domain exceptions.
      `CustomerNotFoundException` (RuntimeException, carries customerId), `InvalidRequestException` (RuntimeException,
      message + optional field), `DependencyUnavailableException` (RuntimeException, dependency name).
      Files: `src/main/java/com/example/fintech/exception/{CustomerNotFoundException,InvalidRequestException,DependencyUnavailableException}.java`.
      Verify: `./gradlew compileJava` succeeds.

- [ ] 5. Create repository interfaces + deterministic in-memory implementations seeded from fixtures.
      Interfaces: `CustomerRepository` (`Optional<Customer> findById(String)`), `AccountRepository`
      (`List<Account> findByCustomerId(String)`), `TransactionRepository`
      (`List<Transaction> findByCustomerId(String)` + date-range overload), `CreditStatusRepository`
      (`Optional<CreditStatus> findByCustomerId(String)`). In-memory impls (`@Repository`) load from `FixtureData`.
      Each impl checks the dependency-failure trigger id `CUST-000004` via an injectable `FailureSeam`
      (a `@Component` boolean/predicate) so tests can force `DependencyUnavailableException`. Repos return
      `Optional.empty()`/empty lists for genuinely absent data; they do NOT throw not-found (services translate).
      Files: `src/main/java/com/example/fintech/repository/{CustomerRepository,AccountRepository,TransactionRepository,CreditStatusRepository}.java`,
      `src/main/java/com/example/fintech/repository/inmemory/{InMemoryCustomerRepository,InMemoryAccountRepository,InMemoryTransactionRepository,InMemoryCreditStatusRepository}.java`,
      `src/main/java/com/example/fintech/repository/inmemory/DependencyFailureSeam.java`.
      Verify: `./gradlew compileJava` succeeds.

- [ ] 6. Create deterministic fixture seed data.
      `FixtureData` (`@Component` or static provider) holding the three synthetic customers (CUST-000001/2/3) with the
      accounts/transactions/credit described in the fixtures section above; all BigDecimal at scale 2 HALF_EVEN with
      explicit `Currency`. No randomness, no `now()`.
      Files: `src/main/java/com/example/fintech/fixtures/FixtureData.java`.
      Verify: `./gradlew compileJava` succeeds.

- [ ] 7. Create DTO records for API responses/requests.
      `CustomerProfileResponse`, `AccountResponse`, `AccountsResponse` (wrapper `items` + count), `TransactionResponse`,
      `TransactionsResponse` (wrapper `items` + count + echoed from/to), `CreditStatusResponse` (+ disclaimer field),
      `FinancialOverviewResponse` (profile + accounts + transactions + creditStatus composed from the others).
      Mapper(s) from domain → DTO (static methods or a `DtoMapper`). Timestamps serialize ISO-8601 UTC
      (`OffsetDateTime`). Collection empties serialize as empty arrays.
      Files: `src/main/java/com/example/fintech/dto/{CustomerProfileResponse,AccountResponse,AccountsResponse,TransactionResponse,TransactionsResponse,CreditStatusResponse,FinancialOverviewResponse}.java`,
      `src/main/java/com/example/fintech/dto/DtoMapper.java`.
      Verify: `./gradlew compileJava` succeeds.

- [ ] 8. Write service unit tests FIRST (TDD RED) for all services incl. negative paths.
      Mockito tests asserting: profile found / unknown→`CustomerNotFoundException`; accounts for known / empty
      (CUST-000003) / unknown→throws; transactions for known / empty / unknown→throws / date-range filtering /
      from>to→`InvalidRequestException`; credit-status found / unknown→throws; overview aggregation composes all four /
      unknown→throws / dependency seam→`DependencyUnavailableException`. BigDecimal asserted via `compareTo` and
      explicit scale.
      Files: `src/test/java/com/example/fintech/service/{CustomerProfileServiceTest,AccountServiceTest,TransactionServiceTest,CreditStatusServiceTest,FinancialOverviewServiceTest}.java`.
      Verify: `./gradlew test --tests 'com.example.fintech.service.*'` COMPILES and FAILS (RED — services not yet
      written). Report RED honestly.

- [ ] 9. Create services to satisfy the unit tests (TDD GREEN).
      Constructor injection of repositories. `CustomerProfileService.getProfile(id)`; `AccountService.getAccounts(id)`;
      `TransactionService.getTransactions(id, from, to)` (validates from<=to → `InvalidRequestException`, filters by
      range inclusive); `CreditStatusService.getCreditStatus(id)`; `FinancialOverviewService.getOverview(id)`
      aggregating the four services/repos (no new orchestration infra). All translate repo `Optional.empty()` /
      unknown customer → `CustomerNotFoundException`; existence is checked via `CustomerRepository` so empty
      collections for a KNOWN customer return 200 empty (not 404). Dependency seam propagates
      `DependencyUnavailableException`.
      Files: `src/main/java/com/example/fintech/service/{CustomerProfileService,AccountService,TransactionService,CreditStatusService,FinancialOverviewService}.java`.
      Verify: `./gradlew test --tests 'com.example.fintech.service.*'` all PASS (GREEN).

- [ ] 10. Create the centralized RFC 7807 `@RestControllerAdvice`.
      Map `CustomerNotFoundException`→404, `InvalidRequestException`→400, `MethodArgumentNotValidException` &
      `ConstraintViolationException` & `HandlerMethodValidationException`→400 with field-level detail (field+reason,
      no sensitive values), `MethodArgumentTypeMismatchException`/date parse→400,
      `DependencyUnavailableException`→503. Build `ProblemDetail` (Spring `org.springframework.http.ProblemDetail`,
      `application/problem+json`) with `type`, `title`, `status`, `detail`, `instance`; no stack traces/internal
      messages leaked. Log at boundary with a correlation id, never logging PII/secrets.
      Files: `src/main/java/com/example/fintech/exception/GlobalExceptionHandler.java`.
      Verify: `./gradlew compileJava` succeeds.

- [ ] 11. Write controller tests FIRST (TDD RED) with `@WebMvcTest` + mocked services.
      Per endpoint: 200 body shape/JSON fields; unknown customer→404 problem+json; malformed customerId→400;
      transactions from>to→400; empty accounts/transactions→200 empty array; dependency failure→503 problem+json.
      Assert `application/problem+json` content type and RFC 7807 fields (type/title/status/detail/instance).
      BigDecimal JSON asserted as string/number with explicit scale.
      Files: `src/test/java/com/example/fintech/controller/{CustomerProfileControllerTest,AccountControllerTest,TransactionControllerTest,CreditStatusControllerTest,FinancialOverviewControllerTest}.java`.
      Verify: `./gradlew test --tests 'com.example.fintech.controller.*'` COMPILES and FAILS (RED — controllers not
      yet written).

- [ ] 12. Create thin controllers + springdoc annotations (TDD GREEN for controller tests).
      Five `@RestController`s under base path `/api/v1/customers/{customerId}`:
      `/profile`, `/accounts`, `/transactions` (optional `from`/`to` `OffsetDateTime` query params), `/credit-status`,
      `/financial-overview`. `customerId` `@Pattern("^CUST-\\d{6}$")` + `@NotBlank`; enable method validation
      (`@Validated` on controller). No business logic — delegate to services, return DTOs, 200 on success. Annotate
      each with `@Operation`/`@ApiResponse`/`@Schema` (summary, description, response schema, example responses,
      documented 400/404/503 errors). Credit-status + credit schema carry the synthetic-data disclaimer.
      **Do NOT add any `/financial-health` endpoint.**
      Files: `src/main/java/com/example/fintech/controller/{CustomerProfileController,AccountController,TransactionController,CreditStatusController,FinancialOverviewController}.java`.
      Verify: `./gradlew test --tests 'com.example.fintech.controller.*'` all PASS (GREEN).

- [ ] 13. Create OpenAPI/springdoc config.
      `@Configuration` providing an `OpenAPI` bean (title "Fintech Customer Insights API", version, description with
      synthetic-data disclaimer, contact/license optional). Confirm Swagger UI at `/swagger-ui.html` and docs at
      `/v3/api-docs` (springdoc defaults). Optional Jackson config bean for BigDecimal/`java.time` ISO-8601 UTC
      serialization (register JavaTimeModule if not auto-registered; write dates as ISO-8601, not timestamps).
      Files: `src/main/java/com/example/fintech/config/OpenApiConfig.java`,
      `src/main/java/com/example/fintech/config/JacksonConfig.java`.
      Verify: `./gradlew compileJava` succeeds.

- [ ] 14. Write integration tests (`@SpringBootTest` random port) for all 5 endpoints end-to-end.
      Use `TestRestTemplate`/`WebTestClient` against the wired app + fixtures. Cover per endpoint: known customer 200 +
      body; unknown CUST-999999 → 404 problem+json; empty accounts/transactions (CUST-000003) → 200 empty; invalid
      date range from>to → 400; malformed customerId → 400; simulated dependency failure (CUST-000004 or seam toggle)
      → 503 problem+json. Also assert `/v3/api-docs` returns 200 JSON and Swagger UI resource resolves. BigDecimal via
      `compareTo`/scale. Deterministic, independently runnable.
      Files: `src/test/java/com/example/fintech/integration/{CustomerEndpointsIT,FinancialOverviewIT,OpenApiDocsIT}.java`
      (or combined), plus a test `application-test.yml` if needed.
      Verify: `./gradlew test --tests 'com.example.fintech.integration.*'` all PASS.

- [ ] 15. Run the full build and fix any seams (GREEN whole suite).
      Ensure `financial-health` appears NOWHERE in source. Confirm no field injection / no `@Autowired` on fields,
      records for DTOs, BigDecimal scale/HALF_EVEN, ISO-8601 UTC timestamps.
      Files: (fixes only, as needed)
      Verify: `cd fintech-customer-insights && ./gradlew clean build` → BUILD SUCCESSFUL, all unit + controller +
      integration tests pass; `grep -ri "financial-health" src` returns nothing.

- [ ] 16. Create the GitHub Actions CI workflow.
      `.github/workflows/ci.yml` at the REPO ROOT (`/home/mtho/workspace/ai sdlc/.github/workflows/ci.yml`):
      top-level least-privilege `permissions: { contents: read }`; job adds `checks: write` / `pull-requests: write`
      ONLY if a reporter step needs it. Steps: `actions/checkout`, `actions/setup-java` with Temurin **25**, run
      `./gradlew build` with working-directory `fintech-customer-insights`, then upload
      `fintech-customer-insights/build/test-results` as an artifact (JUnit results fallback — do NOT depend on a
      possibly-unavailable marketplace test-reporter action). Build fails on test failure (default). NO deployment,
      NO auto-merge. Add a clearly-marked, non-blocking placeholder job/step noting Playwright acceptance tests wire
      in later — but do NOT create the acceptance-tests project now.
      Files: `.github/workflows/ci.yml`.
      Verify: YAML parses (`./gradlew` not needed) — e.g. `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ci.yml'))"`;
      manual read confirms Temurin 25, `./gradlew build`, least-privilege permissions, artifact upload, no deploy.

---

## File inventory (full package paths)

Main (`src/main/java/com/example/fintech/`):
- `FintechCustomerInsightsApplication.java`
- `domain/Customer.java`, `domain/Account.java`, `domain/Transaction.java`, `domain/CreditStatus.java`,
  `domain/AccountType.java`, `domain/AccountStatus.java`, `domain/TransactionType.java`, `domain/PaymentStatus.java`,
  `domain/Segment.java`
- `exception/CustomerNotFoundException.java`, `exception/InvalidRequestException.java`,
  `exception/DependencyUnavailableException.java`, `exception/GlobalExceptionHandler.java`
- `repository/CustomerRepository.java`, `repository/AccountRepository.java`, `repository/TransactionRepository.java`,
  `repository/CreditStatusRepository.java`
- `repository/inmemory/InMemoryCustomerRepository.java`, `.../InMemoryAccountRepository.java`,
  `.../InMemoryTransactionRepository.java`, `.../InMemoryCreditStatusRepository.java`, `.../DependencyFailureSeam.java`
- `fixtures/FixtureData.java`
- `dto/CustomerProfileResponse.java`, `dto/AccountResponse.java`, `dto/AccountsResponse.java`,
  `dto/TransactionResponse.java`, `dto/TransactionsResponse.java`, `dto/CreditStatusResponse.java`,
  `dto/FinancialOverviewResponse.java`, `dto/DtoMapper.java`
- `service/CustomerProfileService.java`, `service/AccountService.java`, `service/TransactionService.java`,
  `service/CreditStatusService.java`, `service/FinancialOverviewService.java`
- `controller/CustomerProfileController.java`, `controller/AccountController.java`,
  `controller/TransactionController.java`, `controller/CreditStatusController.java`,
  `controller/FinancialOverviewController.java`
- `config/OpenApiConfig.java`, `config/JacksonConfig.java`

Resources: `src/main/resources/application.yml`

Test (`src/test/java/com/example/fintech/`):
- `service/{CustomerProfileServiceTest,AccountServiceTest,TransactionServiceTest,CreditStatusServiceTest,FinancialOverviewServiceTest}.java`
- `controller/{CustomerProfileControllerTest,AccountControllerTest,TransactionControllerTest,CreditStatusControllerTest,FinancialOverviewControllerTest}.java`
- `integration/{CustomerEndpointsIT,FinancialOverviewIT,OpenApiDocsIT}.java`
- `src/test/resources/application-test.yml` (if needed)

Build/wrapper: `build.gradle`, `settings.gradle`, `gradle.properties`,
`gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`

CI (repo root): `.github/workflows/ci.yml`

## Test matrix (endpoint × scenario)

| Endpoint | 200 happy | unknown→404 | malformed id→400 | empty→200 [] | from>to→400 | dep fail→503 |
|---|---|---|---|---|---|---|
| /profile | ✔ | ✔ | ✔ | n/a | n/a | ✔ (seam) |
| /accounts | ✔ | ✔ | ✔ | ✔ (CUST-000003) | n/a | ✔ (seam) |
| /transactions | ✔ (+date filter) | ✔ | ✔ | ✔ (CUST-000003) | ✔ | ✔ (seam) |
| /credit-status | ✔ | ✔ | ✔ | n/a | n/a | ✔ (seam) |
| /financial-overview | ✔ (aggregates) | ✔ | ✔ | ✔ (empty acc+txn) | ✔ (via txn) | ✔ (seam) |

Each scenario covered at the service layer (Mockito), controller layer (`@WebMvcTest`), and key scenarios at the
integration layer (`@SpringBootTest`). Plus `/v3/api-docs` 200 and Swagger UI resolves.

## Assumptions / gaps
- `customerId` regex fixed at `^CUST-\d{6}$` (matches fixtures). If the implementer finds a conflicting convention,
  keep fixtures and regex consistent.
- Dependency-failure simulation uses reserved id `CUST-000004` AND an injectable `DependencyFailureSeam` toggle so
  integration tests can force a 503 deterministically without a real external dependency.
- `transactionDate` modeled as `OffsetDateTime` (ISO-8601 UTC) to satisfy from/to range queries cleanly; `from`/`to`
  query params are `OffsetDateTime`.
- If SB 4.1.0 fails to resolve/compile against Java 25, fall back to SB 4.0.9 (confirmed in Central) and keep
  springdoc 3.x; re-verify Swagger UI.

## Governance / stop contract (unchanged)
Plan written to `fintech-customer-insights/.agents/tasks/plan.md`. The existing implement-and-review loop executes it;
the reviewer writes `fintech-customer-insights/.agents/tasks/review.json` last with `verdict` reaching `APPROVED`, and
the fail-closed verification step (`./gradlew clean build`) remains after the loop. **Do NOT implement
`financial-health`. Do NOT run git commands** (this phase is file-creation only).
