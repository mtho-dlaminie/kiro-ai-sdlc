---
name: playwright-api-testing
description: Write deterministic HTTP API acceptance tests with Playwright Test against a locally started app. Use when adding black-box API acceptance coverage for an endpoint. Distinguish from Playwright MCP, which is for interactive browser/Swagger-UI exploration, not HTTP test assertions.
---

# Playwright API Testing Skill

Produce repeatable, agent-independent HTTP API acceptance tests using Playwright Test's
`request` fixture (`APIRequestContext`). These run in CI against a locally started app.

## Playwright Test vs Playwright MCP

- **Playwright Test** (this skill): deterministic HTTP API tests. Correct tool for backend API acceptance.
- **Playwright MCP**: interactive browser automation (e.g. clicking around Swagger UI). Do NOT assume it exposes direct HTTP-testing tools. Use it only for interactive UI exploration, never for CI assertions.

## Setup (in `acceptance-tests/`)

- `package.json` with `@playwright/test` as a dev dependency.
- `playwright.config.ts` setting `use.baseURL` (e.g. `http://localhost:8080`) and sensible timeouts.
- Specs under `acceptance-tests/tests/*.spec.ts`.

## Procedure

```
1. Start the app locally in the background (java -jar build/libs/*.jar or ./gradlew bootRun).
2. Poll readiness (GET /actuator/health or /v3/api-docs) before running tests.
3. Run `npx playwright test`.
4. Stop the app afterwards. Never block on a foreground server.
```

## What to assert

- HTTP status codes (200/400/404/503 as specified).
- Response body fields and types (incl. BigDecimal-as-string/number per the API).
- Headers: `content-type: application/problem+json` on error responses.
- Business assertions derived from acceptance criteria.
- Validation failures, negative scenarios, boundary conditions, auth behaviour where applicable.

## Rules

- Local/isolated environments only. **Never run mutating tests against production.**
- Tests must be deterministic (fixed fixtures) and pass independently of any AI agent.
- Wire the suite into GitHub Actions as a job that starts the app, waits, runs tests, uploads the report.
- Report PASSED/FAILED/NOT RUN/BLOCKED with real commands and exit codes.
