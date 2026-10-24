---
name: engineering-verification
description: Run a fail-closed verification gate over a change and report machine-checkable results. Use before declaring a feature done or preparing delivery, to capture real commands, exit codes, and test outcomes. Do not use as a substitute for writing tests.
---

# Engineering Verification Skill

Verification proves the change actually works. A recipe validating or a command exiting 0 is
not proof on its own — capture real evidence and report it honestly.

## Procedure

```
1. COMPILE        ./gradlew clean build (force test execution).
2. UNIT+INT       confirm counts from build/test-results/test/*.xml.
3. PLAYWRIGHT     start app, run npx playwright test, stop app.
4. STATIC         any configured static analysis / linters.
5. REGRESSION     re-run the full suite; confirm nothing else broke.
6. RUNTIME SMOKE  curl the key endpoints; record actual HTTP status + content-type.
```

## Status vocabulary (use exactly)

- **PASSED** — executed and succeeded.
- **FAILED** — executed and failed.
- **NOT RUN** — not executed (say why).
- **BLOCKED** — could not execute due to an external blocker (say which).

Never report an unexecuted check as PASSED.

## Evidence to capture

For each check: the exact command, its exit code, and a short result. For tests: totals
(tests/failures/errors/skipped) read from the JUnit XML, not from memory. For runtime: the
real HTTP status codes and content types. Emit a machine-readable `verification.json`:

```json
{
  "build": {"command": "./gradlew clean build", "exitCode": 0, "status": "PASSED"},
  "tests": {"tests": 58, "failures": 0, "errors": 0, "skipped": 0, "status": "PASSED"},
  "playwright": {"command": "npx playwright test", "exitCode": 0, "status": "PASSED"},
  "runtime": [{"path": "/api/v1/customers/CUST-000001/financial-overview", "status": 200}]
}
```

Fail closed: if any required check is FAILED/BLOCKED, the gate does not pass. Record the
outcome via `tools/audit/record_event.py`.
