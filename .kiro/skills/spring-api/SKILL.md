---
name: spring-api
description: Implement a REST endpoint in the Java 25 / Spring Boot 4 fintech service following this repository's layered conventions. Use when adding or changing a controller/service/repository in fintech-customer-insights. Do not use for workflow/skill/steering/tooling changes (those must stay free of fintech business logic).
---

# Spring Boot API Implementation Skill

Implement endpoints the way the baseline already does. Match existing patterns before
inventing new ones.

## Layering (strict)

```
controller/  thin adapter: map request -> service call -> DTO. No business logic.
service/     business logic + aggregation. Depends on repository INTERFACES.
repository/  interface + in-memory impl (repository/inmemory/). Deterministic fixtures.
domain/      immutable records/enums. No web/persistence concerns.
dto/         request/response records at the boundary + DtoMapper.
exception/   typed domain exceptions + single @RestControllerAdvice (RFC 7807).
config/      OpenApiConfig and precision/Jackson config.
```

## Conventions

- Package root `com.example.fintech`. Constructor injection only. Records for DTOs.
- Money: `BigDecimal`, explicit scale 2, `RoundingMode.HALF_EVEN`, explicit ISO 4217 currency. Never double/float.
- Dates/times: `java.time`; serialize ISO-8601 UTC.
- Base path `/api/v1/customers/{customerId}/...`. Validate `customerId` with Jakarta Validation (`@Pattern`).
- Reads return 200 (+ empty collections, never 404 for empty). Unknown customer -> 404. Invalid input -> 400. Dependency failure -> 503. All errors via the single `@RestControllerAdvice` as `application/problem+json`.
- Document every endpoint with springdoc annotations: summary, description, response schemas, example responses, documented error responses. Swagger UI must render it.

## Procedure

1. Add/extend the domain model and DTO records.
2. Add the repository method (interface + in-memory impl + fixtures) if new data is needed.
3. Implement the service logic (translate `Optional.empty()` -> typed domain exception).
4. Add the thin controller method with validation + springdoc annotations.
5. Ensure the exception advice already maps any new exception type.
6. Keep aggregation straightforward — no new orchestration infrastructure.

Follow the `tdd` skill: tests for each behaviour come first.
