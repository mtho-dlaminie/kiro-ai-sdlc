# AI SDLC POC — Agent Orientation

This repository is a **governed, auditable AI-assisted SDLC proof of concept** built with Kiro.
It has two deliverables:

- **Deliverable A — `fintech-customer-insights/`**: a Java 25 / Spring Boot 4 fintech backend (the baseline).
- **Deliverable B — `.kiro/workflows/feature-delivery.workflow.json`**: a reusable, governance-aware Kiro
  IDE workflow that takes a feature from requirements → spec → design → TDD → Playwright → review →
  verification → governed GitHub PR, with auditing throughout.

## Hard rules (non-negotiable)

1. **Do NOT implement `GET /api/v1/customers/{customerId}/financial-health` in the baseline.**
   It is the Phase D demonstration feature and must be built *through* the `feature-delivery` workflow.
2. The approved specification is the source of truth. Tests are derived from requirements **before**
   production code. Follow RED → GREEN → REFACTOR.
3. Never weaken or delete valid tests to make a build pass.
4. Git operations are explicitly governed. Never force-push, reset --hard, clean -fd, blanket `git add .`,
   push without approval, or merge/deploy automatically.
5. No hardcoded secrets. Synthetic data only. Never log tokens, keys, cookies, auth headers, or PII.
6. Reusable workflow/skill/agent logic must stay free of fintech business rules.

## Tech baseline (approved in Phase A)

- Java **25** (toolchain). Spring Boot **4.0.x / 4.1.x** (Spring Framework 7, Jakarta EE 11, Jackson 3, JUnit 6).
- **Gradle 9** via the committed wrapper (`./gradlew`). No system Gradle/Maven required.
- `springdoc-openapi` (Spring Boot 4-compatible line) for Swagger UI.
- Monetary values use `BigDecimal` with explicit scale + `RoundingMode.HALF_EVEN`.
- Deterministic in-memory repositories + synthetic fixtures. No database in the baseline.
- Playwright **Test** for deterministic HTTP API acceptance tests (`acceptance-tests/`); Playwright **MCP**
  for interactive Swagger-UI exploration only (never in CI).

## Key paths

- `fintech-customer-insights/` — baseline Spring Boot app (Gradle project).
- `acceptance-tests/` — Playwright Test API suite.
- `.kiro/{agents,skills,steering,workflows}/` — reusable Kiro configuration.
- `tools/audit/` — deterministic audit-event recorder + JSON schemas.
- `audit/<run_id>/` — per-run evidence (gitignored by default).
- `docs/` — developer manual + offline HTML manual.

## Build & test (baseline)

```bash
cd fintech-customer-insights
./gradlew build          # compile + unit + integration tests
./gradlew bootRun        # start on http://localhost:8080 ; Swagger UI at /swagger-ui.html
```
