---
inclusion: always
---

# Error Handling

- One centralized `@RestControllerAdvice` translates exceptions to RFC 7807 responses. Controllers never build error bodies by hand.
- Domain exceptions (examples): `CustomerNotFoundException` → 404, `InvalidRequestException` / validation failures → 400, `DependencyUnavailableException` → 502/503 as appropriate.
- Validation failures from Jakarta Validation (`MethodArgumentNotValidException`, `ConstraintViolationException`) map to 400 with field-level detail (field name + reason), no sensitive values echoed.
- Aggregator endpoints must define explicit behavior for: unknown customer, missing data, empty accounts, empty transactions, invalid request, and expected dependency failure. Each behavior is tested.
- Never swallow exceptions silently. Log at the boundary with correlation context, never logging secrets or PII.
- Fail fast on programmer errors; translate expected conditions into typed domain exceptions.
