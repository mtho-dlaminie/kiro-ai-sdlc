# Acceptance Tests (Playwright Test — HTTP API)

Deterministic black-box HTTP API acceptance tests for `fintech-customer-insights`, using
Playwright Test's `request` fixture (`APIRequestContext`). They run against a **locally
started** app and are wired into CI. They never run against a production environment.

> This is Playwright **Test** (HTTP assertions), distinct from Playwright **MCP** (interactive
> browser/Swagger-UI exploration). The feature-delivery workflow uses MCP only for interactive
> UI checks, and this suite for deterministic API acceptance.

## Install

```bash
cd acceptance-tests
npm install
npx playwright install chromium   # browsers not needed for pure API tests, but installs the runtime
```

## Run (against a locally started app)

```bash
# 1. Start the app (in another terminal, from the repo root)
cd ../fintech-customer-insights && ./gradlew bootRun     # http://localhost:8080

# 2. Run the API acceptance tests
cd ../acceptance-tests && BASE_URL=http://localhost:8080 npx playwright test
```

Override the target with `BASE_URL`. Results: `playwright-report/` (HTML) and, in CI,
`test-results/junit.xml`.

## Layout

```
acceptance-tests/
├── package.json
├── playwright.config.ts          # baseURL, reporters (list/html, junit in CI)
├── tests/
│   └── baseline-smoke.spec.ts     # baseline endpoint acceptance (profile/accounts/.../overview)
└── README.md
```

Feature-specific suites (e.g. `financial-health`) are added by the `api-verifier` agent
during a `feature-delivery` run.
