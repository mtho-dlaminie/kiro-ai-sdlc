---
inclusion: always
---

# Logging & Observability

- Use SLF4J via Spring Boot's logging. No `System.out`/`System.err` in production code.
- Log at the boundary (controller advice, service entry for notable operations). Include a correlation id where available.
- **Never log**: passwords, tokens, API keys, auth cookies, authorization headers, full PII, or real banking data. Synthetic data only; sanitize tool output and reports.
- Expose Spring Boot Actuator health (`/actuator/health`) where useful; do not expose sensitive actuator endpoints publicly.
- Error responses are RFC 7807 problem documents and must not leak stack traces or internal messages to clients.
- Prefer structured, greppable log messages. Log expected domain exceptions at INFO/WARN, unexpected failures at ERROR.
