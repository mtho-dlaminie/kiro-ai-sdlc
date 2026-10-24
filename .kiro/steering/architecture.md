---
inclusion: always
---

# Repository Architecture

This workspace holds two deliverables that must stay cleanly separated.

## Deliverable A: `fintech-customer-insights/`
A Java 25 / Spring Boot 4 fintech backend. Layered architecture:

- `controller/` — thin REST adapters. No business logic. Map requests to service calls, return DTOs.
- `service/` — business logic and aggregation. Depend on repository interfaces, not implementations.
- `repository/` — data-access abstractions (interfaces) + deterministic in-memory implementations.
- `domain/` — core domain models (immutable where practical).
- `dto/` — request/response records at the API boundary. Never leak domain objects directly if a DTO is warranted.
- `exception/` — domain exceptions + a single `@RestControllerAdvice` producing RFC 7807 problem responses.
- `config/` — OpenAPI config, Jackson/precision config.
- `fixtures/` — deterministic synthetic seed data.

No database in the baseline. No distributed orchestration. Keep it understandable.

## Deliverable B: `.kiro/` reusable SDLC system
Workflow, agents, skills, steering, audit tooling. **Must contain zero fintech business rules.**
Fintech specifics live only in Deliverable A and in repo-specific steering that describes A.

## Separation rule
Never implement the `financial-health` feature in the baseline. It is built later through the
`feature-delivery` workflow (Phase D).
