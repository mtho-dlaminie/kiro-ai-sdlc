---
inclusion: always
---

# Java Conventions

- Target **Java 25** via the Gradle toolchain. Use the committed Gradle wrapper (`./gradlew`); never require a system Gradle/Maven.
- Package root: `com.example.fintech`.
- Prefer `record` types for DTOs and immutable value objects.
- Constructor injection only. No field injection. No `@Autowired` on fields.
- Use `java.util.Optional` for "may be absent" lookups in repositories; translate absence to domain exceptions in services.
- **Money:** always `BigDecimal`. Never `double`/`float` for monetary values. Set an explicit scale and use `RoundingMode.HALF_EVEN`. Carry an explicit currency (ISO 4217 `java.util.Currency` or a `String` code) alongside every amount.
- Dates/times: `java.time` (`LocalDate`, `OffsetDateTime`). Serialize timestamps as ISO-8601 UTC.
- No business logic in controllers. No persistence concerns in the domain layer.
- Keep methods small and named for intent. Avoid premature abstraction.
- Spring Boot 4 baseline: Jakarta EE 11 (`jakarta.*` imports), Jackson 3, JUnit 6 / JUnit Jupiter.
