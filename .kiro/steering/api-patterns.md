---
inclusion: always
---

# API Patterns

- Base path: `/api/v1`. Resource-oriented URLs under `/customers/{customerId}/...`.
- `customerId` is a path variable; validate format with Jakarta Validation (`@Pattern`/`@NotBlank`) where applicable.
- Return `200` with a body for successful reads. Use `404` for unknown customers, `400` for invalid input.
- All error responses use **RFC 7807 `application/problem+json`** via a single `@RestControllerAdvice`.
  Include `type`, `title`, `status`, `detail`, and `instance`. Do not leak stack traces or internal messages.
- Collection endpoints return an array (or a wrapper with `items` + metadata when pagination/date-range is relevant). Empty results return `200` with an empty collection, never `404`.
- Date-range queries use ISO-8601 `from`/`to` query params; validate `from <= to`.
- Every endpoint is documented with springdoc-openapi annotations: summary, description, response schemas, example responses, and documented error responses. Swagger UI must render them.
- Never claim synthetic data represents real creditworthiness or real banking records.
