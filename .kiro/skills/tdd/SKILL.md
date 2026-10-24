---
name: tdd
description: Test-Driven Development procedure for features in this repository. Use when implementing any new behaviour or fixing a bug, to drive code from a failing test in small RED-GREEN-REFACTOR increments. Do not use for pure documentation or config-only changes.
---

# TDD Skill

Drive every behaviour change from a failing test, in the smallest increments that make
sense. **The test comes first — always.** Tests derive from the approved specification.

## The loop

```
1. Pick the SMALLEST behaviour from the approved spec (one AC at a time).
2. Write ONE meaningful failing test expressing that behaviour.
3. Run it. CONFIRM it fails for the RIGHT reason (missing functionality,
   not broken test infra or a compile error elsewhere).  -> record RED_TEST_EXECUTION
4. Write the MINIMUM implementation to make it pass. No extra features.
5. Run it. CONFIRM it passes (GREEN).                     -> record GREEN_TEST_EXECUTION
6. REFACTOR while staying green; improve names/structure, no behaviour change.
7. Run the relevant regression suite before moving on.    -> record REFACTOR_VERIFICATION
```

Repeat for the next AC.

## This is NOT TDD

Do not: write the whole implementation then batch-generate tests; write many tests before
any implementation; or skip confirming the RED state. Never weaken or delete a valid test
to make the build pass.

## Commands in this repo

- Full build + tests: `./gradlew build` (from `fintech-customer-insights/`).
- Single class: `./gradlew test --tests '*FinancialOverviewServiceTest'`.
- Force re-run: `./gradlew test --rerun-tasks`.

## Where tests go

- Service logic -> `src/test/java/.../service/` (Mockito, mocked repositories).
- Controller behaviour -> `.../controller/` (`@WebMvcTest`, status/body/validation/RFC 7807).
- End-to-end wired -> `.../integration/` (`@SpringBootTest`, random port, fixtures).

## Evidence

Record RED and GREEN runs with real exit codes via `tools/audit/record_event.py`. Report
PASSED/FAILED/NOT RUN/BLOCKED honestly; never report an unexecuted test as passing.
